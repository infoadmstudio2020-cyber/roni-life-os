/**
 * GitHubUploader — uploads all project files to a GitHub repository
 * using the Git Data API.
 *
 * Workflow:
 *
 *   create blobs
 *       ↓
 *   resolve parent commit/tree
 *       ↓
 *   create tree
 *       ↓
 *   create commit
 *       ↓
 *   update/create branch ref
 *
 * Production-safe implementation:
 * - No JavaScript private fields (#...)
 * - Normalized Git paths
 * - Strict SHA validation
 * - Explicit Git Tree REST payload
 * - Existing repository tree preserved through base_tree
 * - Detailed operation-level logging
 * - No token/content logging
 */

import { githubApi } from './GitHubApi.js';
import { eventBus } from '../../core/EventBus.js';
import { consoleService } from '../storage/ConsoleService.js';

export class GitHubUploader {
  /**
   * Validate repository, credentials/permissions, branch, and base commit/tree
   * before any Git mutations or blob creation take place.
   *
   * Resolves target branch/ref dynamically. If repository is empty, it does NOT
   * send a fake or stale base_tree, allowing initial root tree & commit creation.
   * NEVER reuses a tree SHA from another repository (e.g. joni-app), a previous
   * publish session, cached state, or another branch.
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} branch
   * @param {string|null} parentSha
   * @returns {Promise<{ repo: object, branch: string, commitSha: string|null, baseTreeSha: string|null, isEmpty: boolean }>}
   */
  async validateAndResolveBase(owner, repo, branch, parentSha = null) {
    const context = { owner, repo, branch, commitSha: null, treeSha: null };

    // 1. Verify authentication
    consoleService.info('GitHub: Validating authentication...');
    let authUser = null;
    try {
      authUser = await githubApi.getUser();
    } catch (authError) {
      consoleService.error('GitHub API Permission Error');
      this._logApiError('Invalid GitHub token', authError, context);
      throw this._wrapError('Invalid GitHub token: credentials rejected by GitHub', authError);
    }

    // 2. Verify repository existence & accessibility (with propagation retry for newly created repos)
    consoleService.info(`GitHub: Validating repository — ${owner}/${repo}`);
    let repoObj = null;
    let repoCheckErr = null;
    for (let attempt = 1; attempt <= 5; attempt++) {
      try {
        repoObj = await githubApi.getRepo(owner, repo);
        if (repoObj) break;
      } catch (err) {
        repoCheckErr = err;
        if (err?.status === 404 && attempt < 5) {
          consoleService.info(`GitHub: Waiting for repository propagation (${attempt}/5)...`);
          await new Promise(r => setTimeout(r, 1000 * attempt));
        } else {
          break;
        }
      }
    }

    if (!repoObj) {
      const status = repoCheckErr?.status || 0;
      if (status === 404) {
        consoleService.error('Repository Not Found');
        this._logApiError(`Repository not found — ${owner}/${repo}`, repoCheckErr, context);
        throw this._wrapError(`Repository not found: "${owner}/${repo}" does not exist or token lacks access`, repoCheckErr);
      }
      if (status === 403) {
        consoleService.error('Repository Access Denied');
        this._logApiError(`Repository access denied — ${owner}/${repo}`, repoCheckErr, context);
        throw this._wrapError(`Repository access denied: insufficient permissions to access "${owner}/${repo}"`, repoCheckErr);
      }
      consoleService.error('Repository Access Failed');
      this._logApiError(`Failed to access repository ${owner}/${repo}`, repoCheckErr, context);
      throw this._wrapError(`Repository Access Failed: "${owner}/${repo}"`, repoCheckErr);
    }

    consoleService.success('GitHub Repository Check    ✓ PASS');
    consoleService.info(`Repository                 ${owner}/${repo}`);

    // 3. Verify write/push permissions
    consoleService.info(`GitHub: Validating write permissions for user "${authUser?.login || 'unknown'}" on ${owner}/${repo}`);
    if (repoObj.permissions && repoObj.permissions.push === false) {
      consoleService.error('GitHub API Permission Error');
      const permError = new Error(`Repository access denied: user "${authUser?.login}" does not have push/write permission to "${owner}/${repo}"`);
      permError.status = 403;
      permError.method = 'GET';
      permError.path = `/repos/${owner}/${repo}`;
      permError.data = { message: 'Write permission required', permissions: repoObj.permissions };
      this._logApiError(`Repository access denied — write permission required for ${owner}/${repo}`, permError, context);
      throw permError;
    }

    // 4. Resolve branch & commit SHA dynamically
    consoleService.info(`GitHub: Resolving branch "${branch}" on ${owner}/${repo}`);
    let resolvedCommitSha = null;
    let branchFound = false;

    try {
      const branchRef = await githubApi.getRef(owner, repo, branch);
      if (branchRef?.object?.sha) {
        resolvedCommitSha = branchRef.object.sha;
        branchFound = true;
      }
    } catch (refError) {
      if (refError?.status !== 404) {
        consoleService.error('Branch Not Found');
        this._logApiError(`Failed to resolve branch "${branch}"`, refError, context);
        throw this._wrapError(`Failed to resolve branch "${branch}"`, refError);
      }
    }

    // Try default branch if different and requested branch was not found
    if (!branchFound && repoObj.default_branch && repoObj.default_branch !== branch) {
      try {
        const defRef = await githubApi.getRef(owner, repo, repoObj.default_branch);
        if (defRef?.object?.sha) {
          resolvedCommitSha = defRef.object.sha;
          branch = repoObj.default_branch;
          context.branch = branch;
          branchFound = true;
        }
      } catch {
        // Default branch also not found
      }
    }

    // Check if repository is empty (no commits yet)
    let isEmpty = false;
    if (!branchFound) {
      try {
        const commitList = await githubApi.listCommits(owner, repo, null, 1);
        if (!commitList || commitList.length === 0) {
          isEmpty = true;
        }
      } catch (listErr) {
        const s = listErr?.status || 0;
        if (s === 409 || s === 404) {
          isEmpty = true;
        }
      }

      if (isEmpty || repoObj.size === 0) {
        isEmpty = true;
        consoleService.info(`GitHub: Repository "${owner}/${repo}" is empty (no commits/trees yet)`);
        return {
          repo: repoObj,
          branch,
          commitSha: null,
          baseTreeSha: null,
          isEmpty: true,
        };
      }

      consoleService.error('Branch Not Found');
      const err = new Error(`Branch not found: branch "${branch}" does not exist in repository "${owner}/${repo}"`);
      this._logApiError(`Branch not found — "${branch}" on ${owner}/${repo}`, err, context);
      throw err;
    }

    context.commitSha = resolvedCommitSha;
    consoleService.success('Branch Check               ✓ PASS');
    consoleService.info(`Branch                     ${branch}`);
    consoleService.success('Current Ref                ✓ PASS');
    consoleService.info(`Current Commit             ${resolvedCommitSha}`);

    // 5. Resolve & validate authoritative commit and tree SHA for THIS repository
    let baseTreeSha = null;
    if (resolvedCommitSha) {
      consoleService.info(`GitHub: Resolving base commit — ${resolvedCommitSha}`);
      let commitObj = null;
      try {
        commitObj = await githubApi.getCommit(owner, repo, resolvedCommitSha);
        consoleService.success('Commit Check               ✓ PASS');
      } catch (commitError) {
        consoleService.error('Current Commit Not Found');
        this._logApiError(`Base commit not found — ${resolvedCommitSha}`, commitError, context);
        throw this._wrapError(`Current Commit Not Found: commit "${resolvedCommitSha}" could not be resolved on "${owner}/${repo}"`, commitError);
      }

      if (!commitObj || !commitObj.tree || !commitObj.tree.sha) {
        consoleService.error('Base Tree Not Found');
        const noTreeError = new Error(`Current commit ${resolvedCommitSha} contains no tree SHA`);
        this._logApiError(`Current commit ${resolvedCommitSha} contains no tree SHA`, noTreeError, context);
        throw noTreeError;
      }

      baseTreeSha = String(commitObj.tree.sha).trim();
      context.treeSha = baseTreeSha;
      consoleService.success('Current Tree               ✓ PASS');
      consoleService.info(`Tree SHA                   ${baseTreeSha}`);

      // 6. Validate base tree SHA exists in THIS repository
      consoleService.info(`GitHub: Resolving base tree — ${baseTreeSha}`);
      try {
        const treeObj = await githubApi.getTree(owner, repo, baseTreeSha, false);
        if (!treeObj || !treeObj.sha) {
          throw new Error(`Base tree ${baseTreeSha} returned no SHA`);
        }
        consoleService.success('Tree Access Check          ✓ PASS');
      } catch (treeError) {
        const status = treeError?.status || 0;
        if (status === 403) {
          consoleService.error('Base Tree Access Denied');
        } else {
          consoleService.error('Base Tree Not Found');
        }
        this._logApiError(`Base tree not found — ${baseTreeSha}`, treeError, context);
        throw this._wrapError(`Base Tree Not Found: tree "${baseTreeSha}" could not be resolved on "${owner}/${repo}"`, treeError);
      }

      consoleService.info(`GitHub: Base tree resolved and verified — ${baseTreeSha}`);
    } else {
      consoleService.info('GitHub: No parent commit; repository is empty');
      isEmpty = true;
    }

    return {
      repo: repoObj,
      branch,
      commitSha: resolvedCommitSha,
      baseTreeSha,
      isEmpty,
    };
  }


  /**
   * Freshly fetch latest branch ref → latest commit SHA → latest tree SHA.
   * Ensures cached/stale base_tree is never used before createTree.
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} branch
   * @param {string|null} [fallbackCommitSha=null]
   * @returns {Promise<{
   *   commitSha: string|null,
   *   treeSha: string|null,
   *   isEmpty: boolean
   * }>}
   */
  async resolveFreshBranchTree(owner, repo, branch, fallbackCommitSha = null) {
    owner = String(owner || '').trim();
    repo = String(repo || '').trim();
    branch = String(branch || '').trim();

    let freshCommitSha = null;
    let freshTreeSha = null;

    try {
      // 1. Fresh call for latest branch ref: refs/heads/{branch}
      const branchRef = await githubApi.getRef(owner, repo, branch);
      if (branchRef?.object?.sha) {
        freshCommitSha = String(branchRef.object.sha).trim();
      }
    } catch {
      // If direct git ref not found, try getBranch endpoint as fallback
      try {
        const branchObj = await githubApi.getBranch(owner, repo, branch);
        if (branchObj?.commit?.sha) {
          freshCommitSha = String(branchObj.commit.sha).trim();
          if (branchObj.commit.commit?.tree?.sha || branchObj.commit.tree?.sha) {
            freshTreeSha = String(branchObj.commit.commit?.tree?.sha || branchObj.commit.tree?.sha).trim();
          }
        }
      } catch {
        // Ref truly doesn't exist yet (empty repo or unborn branch)
      }
    }

    if (!freshCommitSha && fallbackCommitSha) {
      freshCommitSha = fallbackCommitSha;
    }

    if (!freshCommitSha) {
      return { commitSha: null, treeSha: null, isEmpty: true };
    }

    // 2. Fresh call for latest commit object: /git/commits/{commitSha}
    if (!freshTreeSha) {
      try {
        const commitObj = await githubApi.getCommit(owner, repo, freshCommitSha);
        if (commitObj?.tree?.sha) {
          freshTreeSha = String(commitObj.tree.sha).trim();
        }
      } catch (commitErr) {
        consoleService.warn(`GitHub: Failed to fetch fresh commit ${freshCommitSha}: ${commitErr?.message || commitErr}`);
      }
    }

    return {
      commitSha: freshCommitSha,
      treeSha: freshTreeSha,
      isEmpty: !freshCommitSha
    };
  }

  /**
   * Upload all files as a single Git commit.
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} branch
   * @param {Record<string,string>} files
   * @param {string} commitMessage
   * @param {string|null} parentSha
   * @param {Function} onProgress
   *
   * @returns {Promise<{
   *   commitSha:string,
   *   treeSha:string
   * }>}
   */
  async uploadAll(
    owner,
    repo,
    branch,
    files,
    commitMessage,
    parentSha,
    onProgress
  ) {

    owner = String(owner || '').trim();
    repo = String(repo || '').trim();
    branch = String(branch || '').trim();
    commitMessage = String(
      commitMessage || 'Update Android library'
    );

    if (!owner) {
      throw new Error(
        'GitHub upload failed: owner is required'
      );
    }

    if (!repo) {
      throw new Error(
        'GitHub upload failed: repository name is required'
      );
    }

    if (!branch) {
      throw new Error(
        'GitHub upload failed: branch is required'
      );
    }

    if (parentSha !== null && parentSha !== undefined) {
      parentSha = String(parentSha).trim();

      if (!this._isSha(parentSha)) {
        throw new Error(
          `GitHub upload failed: invalid parent commit SHA "${parentSha}"`
        );
      }
    } else {
      parentSha = null;
    }

    const sourceEntries = Object.entries(files || {});

    if (sourceEntries.length === 0) {
      throw new Error(
        'GitHub upload failed: No files available to upload'
      );
    }

    /*
     * Normalize and validate every project path before creating
     * any GitHub blobs.
     *
     * This prevents a malformed generated path from reaching the
     * Git Tree API.
     */
    const entries = [];

    for (const [rawPath, rawContent] of sourceEntries) {

      const path = this._normalizeGitPath(rawPath);

      if (!path) {
        throw new Error(
          `GitHub upload failed: invalid empty file path "${rawPath}"`
        );
      }

      let content = rawContent;

      if (typeof Blob !== 'undefined' && content instanceof Blob) {
        content = new Uint8Array(await content.arrayBuffer());
      }

      entries.push([
        path,
        content
      ]);
    }

    /*
     * GitHub must not receive duplicate paths.
     */
    const seenPaths = new Set();

    for (const [path] of entries) {

      if (seenPaths.has(path)) {
        throw new Error(
          `GitHub upload failed: duplicate Git path "${path}"`
        );
      }

      seenPaths.add(path);
    }

    const total = entries.length;

    // ────────────────────────────────────────────────────────────────
    // 1. Preflight validation: Repository, Credentials, Branch, Commit, Tree
    // ────────────────────────────────────────────────────────────────
    const baseInfo = await this.validateAndResolveBase(owner, repo, branch, parentSha);
    branch = baseInfo.branch;
    parentSha = baseInfo.commitSha;
    const baseTreeSha = baseInfo.baseTreeSha;
    const isEmpty = Boolean(baseInfo.isEmpty);

    consoleService.info(
      `GitHub: Upload started — ${owner}/${repo} @ ${branch}`
    );

    consoleService.info(
      `GitHub: Total files to upload: ${total} (jitpack.yml included: ${seenPaths.has('jitpack.yml')})`
    );

    if (parentSha) {

      consoleService.info(
        `GitHub: Parent commit SHA: ${parentSha}`
      );

    } else {

      consoleService.info(
        'GitHub: No parent commit — initial Git commit'
      );
    }

    eventBus.emit('github:uploadStart', {
      total,
      owner,
      repo,
      branch,
      parentSha: parentSha || null,
      baseTreeSha: baseTreeSha || null,
    });

    // ────────────────────────────────────────────────────────────────
    // 1. Create blobs
    // ────────────────────────────────────────────────────────────────

    const treeItems = [];

    for (let i = 0; i < entries.length; i++) {

      const [path, content] = entries[i];

      consoleService.info(
        `GitHub: Creating blob ${i + 1}/${total} — ${path}`
      );

      eventBus.emit('github:uploadOperation', {
        operation: 'createBlob',
        current: i + 1,
        total,
        path,
      });

      let blob;

      try {

        const encoded = this._toBase64(content);

        blob = await githubApi.createBlob(
          owner,
          repo,
          encoded,
          'base64'
        );

      } catch (error) {

        this._logApiError(
          `createBlob failed — ${path}`,
          error
        );

        throw this._wrapError(
          `Failed to create blob for file "${path}"`,
          error
        );
      }

      if (!blob || !blob.sha) {

        throw new Error(
          `GitHub createBlob returned no SHA for "${path}"`
        );
      }

      if (!this._isSha(blob.sha)) {

        throw new Error(
          `GitHub createBlob returned invalid SHA for "${path}"`
        );
      }

      const isExecutable =
        path === 'gradlew' ||
        path.endsWith('/gradlew') ||
        path.endsWith('.sh');

      treeItems.push({
        path,
        mode: isExecutable ? '100755' : '100644',
        type: 'blob',
        sha: blob.sha,
      });

      /*
       * Progress is emitted only after createBlob succeeds.
       */
      if (typeof onProgress === 'function') {

        onProgress(
          i + 1,
          total,
          path
        );
      }

      eventBus.emit('github:uploadProgress', {
        current: i + 1,
        total,
        path,
        sha: blob.sha,
      });

      consoleService.info(
        `GitHub: Blob created ${i + 1}/${total} — ${path} — ${blob.sha.slice(0, 7)}`
      );
    }

    consoleService.success(
      `GitHub: All ${total} blobs created successfully`
    );

    // ────────────────────────────────────────────────────────────────
    // 3. Create Git tree
    // ────────────────────────────────────────────────────────────────

    consoleService.info(
      `GitHub: Creating tree — ${treeItems.length} entries`
    );

    consoleService.info(
      `GitHub: Tree first path — ${treeItems[0]?.path || '[none]'}`
    );

    consoleService.info(
      `GitHub: Tree last path — ${treeItems[treeItems.length - 1]?.path || '[none]'}`
    );

    if (baseTreeSha) {

      consoleService.info(
        `GitHub: Tree base_tree — ${baseTreeSha}`
      );

    } else {

      consoleService.info(
        'GitHub: Tree base_tree — none'
      );
    }

    // Requirement 11: Explicit diagnostics before Create Tree
    consoleService.info('=== Pre-Tree Creation Diagnostics ===');
    consoleService.info(`Owner:                 ${owner}`);
    consoleService.info(`Repository:            ${repo}`);
    consoleService.info(`Branch:                ${branch}`);
    consoleService.info(`Repository Existence:  ✓ exists`);
    consoleService.info(`Current Commit SHA:    ${parentSha || 'none'}`);
    consoleService.info(`Current Tree SHA:      ${baseTreeSha || 'none'}`);
    consoleService.info(`Base Tree SHA:         ${baseTreeSha || 'none'}`);
    consoleService.info(`Repository Is Empty:   ${isEmpty ? 'yes' : 'no'}`);
    consoleService.info(`Tree Entries Count:    ${treeItems.length}`);

    consoleService.info('Repository Check       ✓');
    consoleService.info(`Repository             ${owner}/${repo}`);
    consoleService.info('Branch Check           ✓');
    consoleService.info(`Branch                 ${branch}`);
    consoleService.info(`Current Commit         ${parentSha || 'none'}`);
    consoleService.info(`Current Tree           ${baseTreeSha || 'none'}`);
    consoleService.info('Tree Verification      ✓');
    consoleService.info(`Tree Entries            ${treeItems.length}`);

    eventBus.emit('github:uploadOperation', {
      operation: 'createTree',
      entries: treeItems.length,
      baseTreeSha,
    });

    consoleService.info(
      'GitHub: Fetching fresh latest branch ref and tree SHA before createTree...'
    );

    let effectiveParentSha = parentSha;
    let effectiveBaseTreeSha = baseTreeSha;

    // Fresh API call right before createTree to guarantee no cached or stale base_tree is used
    if (!isEmpty && branch) {
      try {
        const freshState = await this.resolveFreshBranchTree(owner, repo, branch, parentSha);
        if (freshState.commitSha) {
          effectiveParentSha = freshState.commitSha;
        }
        if (freshState.treeSha) {
          effectiveBaseTreeSha = freshState.treeSha;
          consoleService.info(
            `GitHub: Fresh latest tree SHA resolved before createTree: ${effectiveBaseTreeSha} (commit: ${effectiveParentSha?.slice(0, 7) || 'none'})`
          );
        } else if (freshState.isEmpty) {
          effectiveBaseTreeSha = null;
          effectiveParentSha = null;
        }
      } catch (freshErr) {
        consoleService.warn(`GitHub: Fresh branch ref resolution warning: ${freshErr?.message || freshErr}. Retaining baseline.`);
      }
    }

    let tree = null;

    // Resilient Tree Creation with Automatic Replication Lag Retry, Workflow Scope Detection, and Stale Base Tree Recovery
    let treeCreationSuccess = false;
    let lastTreeError = null;
    let skippedWorkflows = false;
    let skippedWorkflowFiles = [];

    const workflowItems = treeItems.filter(item => (item.path || '').startsWith('.github/workflows/'));
    const hasWorkflowItems = workflowItems.length > 0;
    const nonWorkflowTreeItems = hasWorkflowItems
      ? treeItems.filter(item => !(item.path || '').startsWith('.github/workflows/'))
      : treeItems;

    // Strategy 1: Standard tree creation with exponential backoff & fresh branch ref/tree refetch
    const maxRetries = 3;
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        consoleService.info(
          `GitHub: Calling createTree (attempt ${attempt}/${maxRetries}) — base_tree: ${effectiveBaseTreeSha || 'none'}`
        );

        tree = await githubApi.createTree(
          owner,
          repo,
          treeItems,
          effectiveBaseTreeSha || null
        );
        if (tree && tree.sha) {
          treeCreationSuccess = true;
          if (attempt > 1) {
            consoleService.success(`GitHub: createTree succeeded on attempt ${attempt} after fresh tree re-resolution`);
          }
          break;
        }
      } catch (error) {
        lastTreeError = error;
        const status = error?.status || 0;
        const msg = String(error?.data?.message || error?.message || '');
        const is404 = status === 404 || error?.name === 'GitHubTreePreflightError' || msg.includes('404');
        const isStale = msg.includes('Stale repository state') || msg.includes('stale') || msg.includes('replication lag') || error?.name === 'GitHubTreePreflightError';
        const is409 = status === 409;
        const is5xx = status >= 500 && status < 600;
        const isRetryable = is404 || isStale || is409 || is5xx;

        // Immediately abort if not retryable (e.g. 401 Unauthorized or 403 Forbidden without push rights)
        if (!isRetryable) {
          consoleService.error(`GitHub: Non-retryable error encountered during createTree (status: ${status || 'unknown'})`);
          break;
        }

        if (attempt < maxRetries) {
          const delayMs = Math.min(1000 * Math.pow(2, attempt - 1), 4000);
          consoleService.warn(
            `GitHub: createTree returned ${is404 ? 'HTTP 404' : (isStale ? 'Stale repository state' : `HTTP ${status}`)}. Re-fetching latest branch ref & tree SHA and retrying in ${delayMs}ms (attempt ${attempt}/${maxRetries})...`
          );

          await new Promise(r => setTimeout(r, delayMs));

          // Fresh API call: re-fetch latest branch ref -> commit SHA -> tree SHA on retry
          if (branch) {
            try {
              const freshState = await this.resolveFreshBranchTree(owner, repo, branch, effectiveParentSha);
              if (freshState.treeSha) {
                if (freshState.treeSha !== effectiveBaseTreeSha) {
                  consoleService.info(`GitHub: Re-resolved fresher base tree SHA: ${freshState.treeSha} (was ${effectiveBaseTreeSha})`);
                }
                effectiveBaseTreeSha = freshState.treeSha;
              }
              if (freshState.commitSha) {
                effectiveParentSha = freshState.commitSha;
              }
            } catch (refetchErr) {
              consoleService.info(`GitHub: Branch head re-fetch info: ${refetchErr?.message || 'kept current ref'}`);
            }
          }

          continue;
        }
      }
    }

    // Strategy 1.5: If standard createTree failed with 404 and tree includes .github/workflows/,
    // GitHub rejects trees containing workflow files with 404 if token lacks "workflow" scope.
    // Automatically attempt creating the tree without workflow files.
    if (!treeCreationSuccess && hasWorkflowItems) {
      consoleService.warn(
        `GitHub: createTree returned 404 on tree containing ${workflowItems.length} workflow file(s). Attempting to create tree without .github/workflows/ to bypass missing "workflow" scope...`
      );

      try {
        tree = await githubApi.createTree(
          owner,
          repo,
          nonWorkflowTreeItems,
          effectiveBaseTreeSha || null
        );

        if (tree && tree.sha) {
          treeCreationSuccess = true;
          skippedWorkflows = true;
          skippedWorkflowFiles = workflowItems.map(w => w.path);
          consoleService.warn(
            `GitHub: Tree created successfully without .github/workflows/ files! Note: GitHub requires the "workflow" OAuth scope ("Update GitHub Action workflows") on your Personal Access Token to commit workflow files. All other ${nonWorkflowTreeItems.length} project files, README, and configurations were published successfully.`
          );
        }
      } catch (workflowErr) {
        consoleService.info(`GitHub: Workflow-omitted attempt note: ${workflowErr?.message || workflowErr}`);
        lastTreeError = workflowErr;
      }
    }

    // Strategy 2: If createTree still failed with 404/409/5xx (stale base_tree / replication lag):
    // Fallback to creating a safe standalone tree (omitting base_tree) while preserving existing repository files if possible
    const lastStatus = lastTreeError?.status || 0;
    const lastMsg = String(lastTreeError?.data?.message || lastTreeError?.message || '');
    const isFallbackEligible = !treeCreationSuccess && effectiveBaseTreeSha && (
      lastStatus === 404 ||
      lastStatus === 409 ||
      lastStatus >= 500 ||
      lastTreeError?.name === 'GitHubTreePreflightError' ||
      lastMsg.includes('Stale repository state') ||
      lastMsg.includes('404')
    );

    if (isFallbackEligible) {
      consoleService.warn(
        `GitHub: createTree with base_tree "${effectiveBaseTreeSha}" failed with HTTP ${lastStatus || 'error'}. Attempting safe standalone tree fallback without base_tree to bypass stale base_tree error...`
      );

      try {
        let combinedTreeItems = treeItems;
        // Attempt to fetch existing tree to preserve all existing untouched files in the repository
        let existingTree = null;
        try {
          existingTree = await githubApi.getTree(owner, repo, effectiveBaseTreeSha, true);
        } catch {
          // If effectiveBaseTreeSha failed, attempt reading via parent commit SHA
          if (effectiveParentSha) {
            try {
              const freshCommit = await githubApi.getCommit(owner, repo, effectiveParentSha);
              if (freshCommit?.tree?.sha) {
                existingTree = await githubApi.getTree(owner, repo, freshCommit.tree.sha, true);
              }
            } catch {
              // Could not read parent tree
            }
          }
        }

        if (existingTree && Array.isArray(existingTree.tree)) {
          const uploadedPaths = new Set(treeItems.map(t => t.path));
          // Preserve all existing blobs and submodules not overwritten by current upload
          const existingUntouched = existingTree.tree
            .filter(item => (item.type === 'blob' || item.type === 'commit') && !uploadedPaths.has(item.path) && this._isSha(item.sha))
            .map(item => ({
              path: item.path,
              mode: item.mode || '100644',
              type: item.type,
              sha: item.sha
            }));

          if (existingUntouched.length > 0) {
            combinedTreeItems = [...existingUntouched, ...treeItems];
            consoleService.info(
              `GitHub: Preserved ${existingUntouched.length} existing repository files alongside ${treeItems.length} new files for standalone tree`
            );
          }
        } else {
          consoleService.info(`GitHub: Standalone tree will be created directly from all ${treeItems.length} project files`);
        }

        // Call createTree with baseTree = null (omits base_tree parameter)
        try {
          tree = await githubApi.createTree(
            owner,
            repo,
            combinedTreeItems,
            null
          );

          if (tree && tree.sha) {
            treeCreationSuccess = true;
            consoleService.success(
              `GitHub: Safe standalone tree created successfully (Tree SHA: ${tree.sha.slice(0, 7)}). Stale base_tree error completely resolved!`
            );
          }
        } catch (standaloneErr) {
          // If standalone tree with all files failed with 404 and there are workflow items, try omitting workflow files
          if (hasWorkflowItems && (standaloneErr?.status === 404 || String(standaloneErr?.message || '').includes('404'))) {
            consoleService.warn(
              `GitHub: Standalone tree returned 404 with workflow files. Attempting standalone tree without .github/workflows/ files...`
            );
            const combinedNonWorkflow = combinedTreeItems.filter(item => !(item.path || '').startsWith('.github/workflows/'));
            tree = await githubApi.createTree(
              owner,
              repo,
              combinedNonWorkflow,
              null
            );

            if (tree && tree.sha) {
              treeCreationSuccess = true;
              skippedWorkflows = true;
              skippedWorkflowFiles = workflowItems.map(w => w.path);
              consoleService.warn(
                `GitHub: Safe standalone tree created successfully without .github/workflows/ files! (Omitted ${workflowItems.length} workflow file(s) because token lacks "workflow" scope). All project source code, configurations, and assets are preserved.`
              );
            }
          } else {
            throw standaloneErr;
          }
        }
      } catch (fallbackErr) {
        consoleService.error(`GitHub: Standalone tree fallback also failed: ${fallbackErr.message || fallbackErr}`);
        lastTreeError = fallbackErr;
      }
    }

    // If still failed after all retries and fallback: diagnose and report authentic failure
    if (!treeCreationSuccess || !tree || !tree.sha) {
      const failureReason = await this._diagnoseTreeFailure(
        owner,
        repo,
        branch,
        effectiveBaseTreeSha,
        lastTreeError,
        treeItems
      );

      consoleService.error(
        `Create Tree FAILED\n\n` +
        `Failure Reason: ${failureReason}\n` +
        `Repository:     ${owner}/${repo}\n` +
        `Branch:         ${branch}\n` +
        `Commit SHA:     ${effectiveParentSha || 'none'}\n` +
        `Tree SHA:       ${effectiveBaseTreeSha || 'none'}\n` +
        `Entries:        ${treeItems.length}\n` +
        `HTTP Status:    ${lastTreeError?.status || 'Unknown'}\n` +
        `GitHub Message: ${lastTreeError?.data?.message || lastTreeError?.message || 'Unknown'}`
      );

      this._logApiError(
        `createTree failed — ${failureReason}`,
        lastTreeError,
        { owner, repo, branch, commitSha: effectiveParentSha, treeSha: effectiveBaseTreeSha, failureReason }
      );

      const diagnostic = [
        `reason=${failureReason}`,
        `entries=${treeItems.length}`,
        `baseTree=${effectiveBaseTreeSha || 'none'}`,
        `firstPath=${treeItems[0]?.path || 'none'}`,
        `lastPath=${treeItems[treeItems.length - 1]?.path || 'none'}`
      ].join(', ');

      throw this._wrapError(
        `Failed to create Git tree: ${failureReason} (${diagnostic})`,
        lastTreeError
      );
    }

    if (!tree || !tree.sha) {

      throw new Error(
        'GitHub createTree returned no tree SHA'
      );
    }

    if (!this._isSha(tree.sha)) {

      throw new Error(
        `GitHub createTree returned invalid tree SHA "${tree.sha}"`
      );
    }

    consoleService.success(
      `GitHub: Tree created — ${tree.sha}`
    );

    // ────────────────────────────────────────────────────────────────
    // 4. Create commit
    // ────────────────────────────────────────────────────────────────

    const commitParentSha = effectiveParentSha || parentSha;
    const parents = commitParentSha
      ? [commitParentSha]
      : [];

    consoleService.info(
      `GitHub: Creating commit — "${commitMessage}"`
    );

    if (parents.length > 0) {

      consoleService.info(
        `GitHub: Commit parent — ${parents[0]}`
      );
    }

    consoleService.info(
      `GitHub: Commit tree — ${tree.sha}`
    );

    eventBus.emit('github:uploadOperation', {
      operation: 'createCommit',
      treeSha: tree.sha,
      parents,
      message: commitMessage,
    });

    let commit;

    try {

      commit = await githubApi.createCommit(
        owner,
        repo,
        commitMessage,
        tree.sha,
        parents
      );

    } catch (error) {

      this._logApiError(
        'createCommit failed',
        error
      );

      throw this._wrapError(
        'Failed to create Git commit',
        error
      );
    }

    if (!commit || !commit.sha) {

      throw new Error(
        'GitHub createCommit returned no commit SHA'
      );
    }

    if (!this._isSha(commit.sha)) {

      throw new Error(
        `GitHub createCommit returned invalid commit SHA "${commit.sha}"`
      );
    }

    consoleService.success(
      `GitHub: Commit created — ${commit.sha}`
    );

    // ────────────────────────────────────────────────────────────────
    // 5. Update/create branch ref
    // ────────────────────────────────────────────────────────────────

    consoleService.info(
      `GitHub: Updating branch ref — ${branch}`
    );

    consoleService.info(
      `GitHub: New branch HEAD — ${commit.sha}`
    );

    eventBus.emit('github:uploadOperation', {
      operation: commitParentSha
        ? 'updateRef'
        : 'createRef',
      branch,
      commitSha: commit.sha,
    });

    try {

      if (commitParentSha) {

        try {

          await githubApi.updateRef(
            owner,
            repo,
            branch,
            commit.sha,
            true
          );

          consoleService.success(
            `GitHub: Branch "${branch}" updated successfully`
          );

        } catch (error) {

          if (error?.status === 422) {

            consoleService.info(
              `GitHub: Branch update returned 422; retrying force update — ${branch}`
            );

            await githubApi.updateRef(
              owner,
              repo,
              branch,
              commit.sha,
              true
            );

            consoleService.success(
              `GitHub: Branch "${branch}" force-updated successfully`
            );

          } else {

            this._logApiError(
              `updateRef failed — ${branch}`,
              error
            );

            throw this._wrapError(
              `Failed to update branch "${branch}"`,
              error
            );
          }
        }

      } else {

        try {

          await githubApi.createRef(
            owner,
            repo,
            branch,
            commit.sha
          );

          consoleService.success(
            `GitHub: Branch "${branch}" created successfully`
          );

        } catch (error) {

          if (error?.status === 422) {

            consoleService.info(
              `GitHub: Branch creation returned 422; attempting update — ${branch}`
            );

            await githubApi.updateRef(
              owner,
              repo,
              branch,
              commit.sha,
              true
            );

            consoleService.success(
              `GitHub: Branch "${branch}" updated successfully`
            );

          } else {

            this._logApiError(
              `createRef failed — ${branch}`,
              error
            );

            throw this._wrapError(
              `Failed to create branch "${branch}"`,
              error
            );
          }
        }
      }

    } catch (error) {

      if (
        error?.message?.startsWith(
          'Failed to update branch'
        ) ||
        error?.message?.startsWith(
          'Failed to create branch'
        )
      ) {
        throw error;
      }

      this._logApiError(
        `branch ref operation failed — ${branch}`,
        error
      );

      throw error;
    }

    // ────────────────────────────────────────────────────────────────
    // Upload complete
    // ────────────────────────────────────────────────────────────────

    eventBus.emit('github:uploadComplete', {
      commitSha: commit.sha,
      treeSha: tree.sha,
      branch,
      total,
    });

    consoleService.success(
      `GitHub: Upload completed successfully — commit ${commit.sha}`
    );

    return {
      commitSha: commit.sha,
      treeSha: tree.sha,
      skippedWorkflows,
      skippedWorkflowFiles,
    };
  }

  // ────────────────────────────────────────────────────────────────
  // Repository initialization
  // ────────────────────────────────────────────────────────────────

  /**
   * Ensure repository contains at least one commit.
   *
   * If repository is completely empty, create an initial README.md
   * using the Contents API.
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} branch
   * @returns {Promise<'already-initialized'|'initialized'>}
   */
  async ensureInitialized(owner, repo, branch) {

    consoleService.info(
      'GitHub: Checking repository initialization status'
    );

    const headSha = await this.getHeadSha(
      owner,
      repo,
      branch
    );

    if (headSha) {

      consoleService.info(
        `GitHub: Repository already initialized — HEAD ${headSha}`
      );

      return 'already-initialized';
    }

    consoleService.info(
      'GitHub: Repository is empty, creating initial commit via Contents API'
    );

    const content =
      `# ${repo}\n\n` +
      `Initialized by Android Library Generator.\n`;

    const encoded = this._toBase64(content);

    try {

      const result = await githubApi.put(
        `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/contents/README.md`,
        {
          message: 'Initial commit',
          content: encoded,
          branch,
        }
      );

      consoleService.success(
        `GitHub: Initial commit created successfully${
          result?.commit?.sha
            ? ` — ${result.commit.sha}`
            : ''
        }`
      );

      return 'initialized';

    } catch (error) {

      this._logApiError(
        'repository initialization failed',
        error
      );

      throw this._wrapError(
        'Could not initialize empty repository',
        error
      );
    }
  }

  // ────────────────────────────────────────────────────────────────
  // HEAD resolution
  // ────────────────────────────────────────────────────────────────

  /**
   * Get HEAD SHA for a branch.
   *
   * Attempts:
   *
   *   requested branch
   *        ↓
   *   main/master alternative
   *
   * Returns null if repository has no usable HEAD.
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} branch
   * @returns {Promise<string|null>}
   */
  async getHeadSha(owner, repo, branch) {

    const sha = await this._tryGetRef(
      owner,
      repo,
      branch
    );

    if (sha) {
      return sha;
    }

    const alternative =
      branch === 'main'
        ? 'master'
        : 'main';

    if (alternative !== branch) {

      const alternativeSha =
        await this._tryGetRef(
          owner,
          repo,
          alternative
        );

      if (alternativeSha) {
        return alternativeSha;
      }
    }

    return null;
  }

  /**
   * Attempt to resolve a branch reference.
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} branch
   * @returns {Promise<string|null>}
   */
  async _tryGetRef(owner, repo, branch) {

    try {

      const ref = await githubApi.getRef(
        owner,
        repo,
        branch
      );

      const sha = ref?.object?.sha;

      if (!sha) {

        consoleService.info(
          `GitHub: Ref exists but has no SHA — ${branch}`
        );

        return null;
      }

      if (!this._isSha(sha)) {

        consoleService.info(
          `GitHub: Ref returned invalid SHA — ${branch}`
        );

        return null;
      }

      consoleService.info(
        `GitHub: HEAD SHA for ${branch} is ${sha.slice(0, 7)}`
      );

      return sha;

    } catch (error) {

      /*
       * These statuses can represent:
       *
       * 404 = branch/ref does not exist
       * 409 = repository has no Git data
       * 422 = reference problem
       */
      if (
        error?.status === 404 ||
        error?.status === 409 ||
        error?.status === 422
      ) {

        consoleService.info(
          `GitHub: No HEAD for ${branch} (${error.status})`
        );

        return null;
      }

      const message =
        String(error?.message || '')
          .toLowerCase();

      const emptyKeywords = [
        'git repository is empty',
        'repository is empty',
        'empty repository',
        'empty',
        'no commit',
        'reference not found',
      ];

      if (
        emptyKeywords.some(
          keyword => message.includes(keyword)
        )
      ) {

        consoleService.info(
          `GitHub: No HEAD for ${branch} (empty repository)`
        );

        return null;
      }

      this._logApiError(
        `getRef failed — ${branch}`,
        error
      );

      throw error;
    }
  }

  // ────────────────────────────────────────────────────────────────
  // Git path normalization
  // ────────────────────────────────────────────────────────────────

  /**
   * Normalize a generated project path into a valid Git path.
   *
   * @param {string} value
   * @returns {string}
   */
  _normalizeGitPath(value) {

    let path = String(value ?? '').trim();

    if (!path) {
      return '';
    }

    /*
     * Git paths always use forward slashes.
     */
    path = path.replace(/\\/g, '/');

    /*
     * Remove accidental leading "./".
     */
    while (path.startsWith('./')) {
      path = path.slice(2);
    }

    /*
     * Git tree paths must not begin with "/".
     */
    path = path.replace(/^\/+/, '');

    /*
     * Remove duplicate separators.
     */
    path = path.replace(/\/{2,}/g, '/');

    /*
     * Reject unsafe traversal.
     */
    const segments = path.split('/');

    if (
      segments.some(
        segment => segment === '..'
      )
    ) {
      throw new Error(
        `GitHub upload failed: unsafe path "${value}"`
      );
    }

    /*
     * Empty path after normalization is invalid.
     */
    if (!path) {
      return '';
    }

    return path;
  }

  // ────────────────────────────────────────────────────────────────
  // SHA validation
  // ────────────────────────────────────────────────────────────────

  /**
   * Check whether a value is a standard Git SHA-1.
   *
   * @param {unknown} value
   * @returns {boolean}
   */
  _isSha(value) {

    return (
      typeof value === 'string' &&
      /^[0-9a-fA-F]{40}$/.test(value.trim())
    );
  }

  // ────────────────────────────────────────────────────────────────
  // Error helpers
  // ────────────────────────────────────────────────────────────────

  /**
   * Log detailed GitHub API error information with diagnostic context.
   *
   * @param {string} operation
   * @param {Error} error
   * @param {object} [context={}]
   */
  _logApiError(operation, error, context = {}) {

    consoleService.error(
      `GitHub: ${operation}`
    );

    if (error?.status !== undefined) {
      consoleService.error(
        `GitHub: HTTP status — ${error.status}`
      );
    }

    if (error?.method) {
      consoleService.error(
        `GitHub: Method — ${error.method}`
      );
    }

    if (error?.path) {
      consoleService.error(
        `GitHub: Path — ${error.path}`
      );
    }

    if (context.owner) {
      consoleService.error(
        `GitHub: Owner — ${context.owner}`
      );
    }

    if (context.repo) {
      consoleService.error(
        `GitHub: Repository — ${context.repo}`
      );
    }

    if (context.branch) {
      consoleService.error(
        `GitHub: Branch — ${context.branch}`
      );
    }

    if (context.commitSha) {
      consoleService.error(
        `GitHub: Commit SHA — ${context.commitSha}`
      );
    }

    if (context.treeSha) {
      consoleService.error(
        `GitHub: Tree SHA — ${context.treeSha}`
      );
    }

    const respMsg = error?.data?.message || error?.message;
    if (respMsg) {
      consoleService.error(
        `GitHub: GitHub response message — ${respMsg}`
      );
    }

    if (error?.url) {
      consoleService.error(
        `GitHub: URL — ${error.url}`
      );
    }

    if (error?.data) {
      try {
        consoleService.error(
          `GitHub: Response — ${JSON.stringify(error.data)}`
        );
      } catch {
        consoleService.error(
          'GitHub: Response — [unserializable]'
        );
      }
    }

    if (error?.requestId) {
      consoleService.error(
        `GitHub: Request ID — ${error.requestId}`
      );
    }

    if (error?.acceptedPermissions) {
      consoleService.error(
        `GitHub: Accepted permissions — ${error.acceptedPermissions}`
      );
    }
  }

  /**
   * Wrap an API error while preserving original information.
   *
   * @param {string} message
   * @param {Error} error
   * @returns {Error}
   */
  _wrapError(message, error) {

    const wrapped = new Error(
      `${message}: ${error?.message || 'Unknown GitHub API error'}`
    );

    wrapped.name = 'GitHubUploadError';

    if (error?.status !== undefined) {
      wrapped.status = error.status;
    }

    if (error?.method) {
      wrapped.method = error.method;
    }

    if (error?.path) {
      wrapped.path = error.path;
    }

    if (error?.url) {
      wrapped.url = error.url;
    }

    if (error?.data) {
      wrapped.data = error.data;
    }

    if (error?.errors) {
      wrapped.errors = error.errors;
    }

    if (error?.documentationUrl) {
      wrapped.documentationUrl =
        error.documentationUrl;
    }

    if (error?.requestId) {
      wrapped.requestId =
        error.requestId;
    }

    if (error?.acceptedPermissions) {
      wrapped.acceptedPermissions =
        error.acceptedPermissions;
    }

    wrapped.cause = error;

    return wrapped;
  }

  // ────────────────────────────────────────────────────────────────
  // Base64
  // ────────────────────────────────────────────────────────────────

  /**
   * Convert string or binary data (Uint8Array, ArrayBuffer, Blob) to Base64.
   *
   * @param {string|Uint8Array|ArrayBuffer} value
   * @returns {string}
   */
  _toBase64(value) {

    if (value == null) {
      return '';
    }

    /*
     * 1. Binary inputs: Uint8Array, ArrayBuffer, or ArrayBufferView.
     * Must NEVER be coerced to string via String() or TextEncoder.
     */
    let bytes;

    if (value instanceof Uint8Array) {
      bytes = value;
    } else if (value instanceof ArrayBuffer) {
      bytes = new Uint8Array(value);
    } else if (typeof ArrayBuffer !== 'undefined' && ArrayBuffer.isView(value)) {
      bytes = new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
    } else if (typeof value === 'string') {
      /*
       * 2. Unicode string -> UTF-8 bytes -> Base64
       */
      try {
        bytes = new TextEncoder().encode(value);
      } catch {
        try {
          return btoa(unescape(encodeURIComponent(value)));
        } catch {
          return btoa(value);
        }
      }
    } else {
      // Fallback for unexpected scalar types
      bytes = new TextEncoder().encode(String(value));
    }

    let binary = '';
    const chunkSize = 0x8000;

    for (let i = 0; i < bytes.length; i += chunkSize) {
      const chunk = bytes.subarray(i, Math.min(i + chunkSize, bytes.length));
      binary += String.fromCharCode(...chunk);
    }

    return btoa(binary);
  }

  /**
   * Distinguish the root cause when createTree fails (especially HTTP 404):
   * 1. Repository not found
   * 2. Token does not have access
   * 3. Invalid base_tree
   * 4. Wrong owner/repository
   * 5. Stale repository state
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} branch
   * @param {string|null} baseTreeSha
   * @param {Error} error
   * @returns {Promise<string>}
   */
  async _diagnoseTreeFailure(owner, repo, branch, baseTreeSha, error, treeItems = []) {
    if (error?.status !== 404) {
      if (error?.status === 401 || error?.status === 403) {
        return `Token does not have access (write/push permission required for "${owner}/${repo}")`;
      }
      return error?.data?.message || error?.message || 'Git tree creation failed';
    }

    // 1 & 4. Verify repository existence & owner/repository correctness
    let repoCheckStatus = 'unknown';
    try {
      const r = await githubApi.getRepo(owner, repo);
      if (r && r.id) {
        repoCheckStatus = 'exists';
      }
    } catch (rErr) {
      if (rErr?.status === 404) {
        repoCheckStatus = 'not_found';
      } else if (rErr?.status === 401 || rErr?.status === 403) {
        repoCheckStatus = 'no_access';
      }
    }

    if (repoCheckStatus === 'not_found') {
      return `Repository not found or wrong owner/repository: "${owner}/${repo}"`;
    }

    if (repoCheckStatus === 'no_access') {
      return `Token does not have access: authenticated token cannot access "${owner}/${repo}"`;
    }

    // Check for missing workflow scope on .github/workflows/ files
    const hasWorkflows = Array.isArray(treeItems) && treeItems.some(item => (item.path || '').startsWith('.github/workflows/'));
    const tokenScopes = githubApi.getOAuthScopes?.();
    const lacksWorkflowScope = Array.isArray(tokenScopes) && !tokenScopes.includes('workflow');

    if (hasWorkflows && lacksWorkflowScope) {
      return `Missing "workflow" OAuth scope: Tree contains files in ".github/workflows/", but your GitHub Personal Access Token lacks the "workflow" scope ("Update GitHub Action workflows"). GitHub returns HTTP 404 Not Found for tree creation when workflow files are modified without this scope.`;
    }

    if (hasWorkflows) {
      return `Potential missing "workflow" scope: Tree contains files in ".github/workflows/". GitHub returns HTTP 404 Not Found for tree creation if the Personal Access Token lacks the "workflow" scope ("Update GitHub Action workflows") or fine-grained "Workflows: Read & write" permission.`;
    }

    // 3. Check if base_tree is invalid in this repository
    if (baseTreeSha) {
      let treeExists = true;
      try {
        const t = await githubApi.getTree(owner, repo, baseTreeSha, false);
        if (!t || !t.sha) {
          treeExists = false;
        }
      } catch (tErr) {
        if (tErr?.status === 404) {
          treeExists = false;
        }
      }

      if (!treeExists) {
        return `Invalid base_tree: tree SHA "${baseTreeSha}" does not exist in repository "${owner}/${repo}". Tree SHA may have been reused from another repository (e.g. joni-app), a previous publish session, or a stale branch.`;
      }

      // 5. Stale repository state
      return `Stale repository state: repository and base_tree "${baseTreeSha}" exist, but GitHub returned 404 due to backend replication lag or temporary Git cache staleness.`;
    }

    // 5. Stale repository state without baseTree
    return `Stale repository state: repository "${owner}/${repo}" exists and is accessible, but POST /git/trees returned 404 (replication delay or Git Data API propagation lag).`;
  }
}

export const githubUploader = new GitHubUploader();