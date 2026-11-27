/**
 * GitHubImporter.js — Imports remote Android projects from GitHub repositories into RONi Android Library Generator.
 *
 * Capabilities:
 * - URL Parsing & Validation (HTTPS, HTTP, SSH, short 'owner/repo', tree/branch URLs)
 * - Repository Access Verification (Public or authenticated/private repositories via PAT)
 * - Branch Discovery & Selection (Default branch pre-selection)
 * - Full Recursive Tree Resolution (Git Tree API)
 * - Multi-threaded File Fetching (raw.githubusercontent.com + Git Blobs API fallback)
 * - Binary & Text Content Handling (Uint8Array for jars/images, UTF-8 strings for code/xml/gradle)
 * - Root Prefix Detection (Auto-detects nested Android project folders)
 * - Android Project Model Import (settings.gradle, build.gradle, manifests, sources, resources)
 * - Integration with ProjectService, ProjectAnalyzer, Explorer, and Editor
 */

import { githubApi } from './GitHubApi.js';
import { githubAuth } from './GitHubAuth.js';
import { githubRepository } from './GitHubRepository.js';
import { projectService } from '../storage/ProjectService.js';
import { consoleService } from '../storage/ConsoleService.js';
import { ProjectAnalyzer } from '../analyzer/ProjectAnalyzer.js';
import { eventBus } from '../../core/EventBus.js';

// Binary file extensions requiring Uint8Array storage
const BINARY_EXTS = new Set([
  'jar', 'aar', 'so', 'png', 'jpg', 'jpeg', 'webp', 'gif',
  'bin', 'keystore', 'jks', 'ico', 'zip', 'class', 'dex'
]);

// Android project root indicator files
const ROOT_INDICATOR_FILES = [
  'settings.gradle',
  'settings.gradle.kts',
  'build.gradle',
  'build.gradle.kts',
  'gradlew',
  'gradlew.bat'
];

export class GitHubImporter {

  /**
   * Parse various GitHub repository URL formats.
   * Supports:
   * - https://github.com/owner/repo
   * - https://github.com/owner/repo.git
   * - http://github.com/owner/repo
   * - github.com/owner/repo
   * - owner/repo
   * - git@github.com:owner/repo.git
   * - https://github.com/owner/repo/tree/branch-name
   * - https://github.com/owner/repo/tree/branch-name/sub/path
   *
   * @param {string} inputUrl
   * @returns {{ owner: string, repo: string, branch: string|null, originalUrl: string }}
   */
  static parseRepoUrl(inputUrl) {
    if (!inputUrl || typeof inputUrl !== 'string') {
      throw new Error('Please enter a GitHub repository URL or "owner/repo"');
    }

    let url = inputUrl.trim();

    // Remove quotes if pasted with quotes
    url = url.replace(/^['"]|['"]$/g, '');

    let owner = '';
    let repo = '';
    let branch = null;

    // Pattern 1: git@github.com:owner/repo(.git)?
    const sshMatch = url.match(/^git@github\.com:([a-zA-Z0-9_.-]+)\/([a-zA-Z0-9_.-]+?)(?:\.git)?$/);
    if (sshMatch) {
      owner = sshMatch[1];
      repo = sshMatch[2].replace(/\.git$/i, '');
      return { owner, repo, branch, originalUrl: inputUrl };
    }

    // Pattern 2: HTTP/HTTPS or plain domain URL
    let cleanUrl = url;
    if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) {
      if (cleanUrl.startsWith('github.com/')) {
        cleanUrl = 'https://' + cleanUrl;
      }
    }

    try {
      if (cleanUrl.startsWith('http://') || cleanUrl.startsWith('https://')) {
        const parsed = new URL(cleanUrl);
        if (!parsed.hostname.includes('github.com')) {
          throw new Error('Only GitHub repositories are supported (hostname must be github.com)');
        }
        const parts = parsed.pathname.replace(/^\/+|\/+$/g, '').split('/');
        if (parts.length >= 2) {
          owner = parts[0];
          repo = parts[1].replace(/\.git$/i, '');

          // Check if URL includes /tree/{branch}
          if (parts.length >= 4 && parts[2] === 'tree') {
            branch = parts.slice(3).join('/');
          }
        }
      }
    } catch (e) {
      if (e.message && e.message.includes('Only GitHub repositories')) throw e;
    }

    // Pattern 3: Short form "owner/repo" or "owner/repo@branch"
    if (!owner || !repo) {
      const shortMatch = url.match(/^([a-zA-Z0-9_.-]+)\/([a-zA-Z0-9_.-]+?)(?:@([a-zA-Z0-9_./-]+))?$/);
      if (shortMatch) {
        owner = shortMatch[1];
        repo = shortMatch[2].replace(/\.git$/i, '');
        branch = shortMatch[3] || null;
      }
    }

    if (!owner || !repo) {
      throw new Error(
        'Invalid repository format. Please provide a valid GitHub URL (e.g., https://github.com/owner/repository) or "owner/repository".'
      );
    }

    // Validate characters
    if (!/^[a-zA-Z0-9_.-]+$/.test(owner)) {
      throw new Error(`Invalid GitHub username or organization name: "${owner}"`);
    }
    if (!/^[a-zA-Z0-9_.-]+$/.test(repo)) {
      throw new Error(`Invalid repository name: "${repo}"`);
    }

    return { owner, repo, branch, originalUrl: inputUrl };
  }

  /**
   * Validate repository access and fetch repository metadata.
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string|null} [token] - Optional user-provided token (for private repos or rate limit)
   * @returns {Promise<{ repo: object, tokenUsed: boolean }>}
   */
  static async validateRepository(owner, repo, token = null) {
    const activeToken = token ? token.trim() : (githubAuth.token || null);
    const prevToken = githubApi._token;

    try {
      if (activeToken) {
        githubApi.setToken(activeToken);
      }

      consoleService.info(`GitHub Importer: Verifying repository ${owner}/${repo}...`);
      const repoData = await githubApi.getRepo(owner, repo);
      consoleService.success(`GitHub Importer: Found ${repoData.full_name} (${repoData.private ? 'Private' : 'Public'})`);

      return {
        repo: repoData,
        tokenUsed: !!activeToken
      };
    } catch (err) {
      // Restore previous token if this check failed
      if (token && prevToken !== token) {
        githubApi.setToken(prevToken);
      }

      const status = err.status || (err.cause?.status);
      if (status === 404) {
        throw new Error(
          `Repository "${owner}/${repo}" was not found or is private.\n` +
          `If this is a private repository, please provide a Personal Access Token.`
        );
      } else if (status === 401) {
        throw new Error(
          `Authentication failed: Personal Access Token is invalid or expired.\n` +
          `Please check your GitHub token.`
        );
      } else if (status === 403) {
        throw new Error(
          `Access forbidden or GitHub API rate limit exceeded.\n` +
          `Please provide a Personal Access Token for higher rate limits.`
        );
      }
      throw new Error(`Failed to access repository "${owner}/${repo}": ${err.message || 'Unknown error'}`);
    }
  }

  /**
   * Retrieve all available branches for a repository.
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string|null} [token]
   * @returns {Promise<{ defaultBranch: string, branches: Array<{ name: string, isDefault: boolean, commitSha: string }> }>}
   */
  static async getBranches(owner, repo, token = null) {
    if (token) githubApi.setToken(token.trim());

    try {
      const branchesData = await githubApi.listBranches(owner, repo, 100);
      const branches = Array.isArray(branchesData) ? branchesData : [];

      // Determine default branch from repo info
      let defaultBranch = 'main';
      try {
        const repoData = await githubApi.getRepo(owner, repo);
        if (repoData?.default_branch) defaultBranch = repoData.default_branch;
      } catch {
        /* fallback */
      }

      const formatted = branches.map(b => ({
        name: b.name,
        isDefault: b.name === defaultBranch,
        commitSha: b.commit?.sha || null,
        protected: !!b.protected,
      }));

      // Sort default branch to the top, then alphabetically
      formatted.sort((a, b) => {
        if (a.isDefault) return -1;
        if (b.isDefault) return 1;
        return a.name.localeCompare(b.name);
      });

      // If no branches returned (e.g. empty repo), provide at least the default branch
      if (formatted.length === 0) {
        formatted.push({ name: defaultBranch, isDefault: true, commitSha: null });
      }

      return {
        defaultBranch,
        branches: formatted
      };
    } catch (err) {
      consoleService.warn(`GitHub Importer: Failed to list branches (${err.message}). Using fallback 'main'`);
      return {
        defaultBranch: 'main',
        branches: [
          { name: 'main', isDefault: true, commitSha: null },
          { name: 'master', isDefault: false, commitSha: null }
        ]
      };
    }
  }

  /**
   * Fetch all files from a repository branch.
   *
   * @param {object} opts
   * @param {string} opts.owner
   * @param {string} opts.repo
   * @param {string} opts.branch
   * @param {string|null} [opts.token]
   * @param {Function} [opts.onProgress] - ({ stage, current, total, percent, file, message })
   * @returns {Promise<{ files: Object.<string, string|Uint8Array>, commitSha: string, treeSha: string, fileCount: number }>}
   */
  static async fetchRepositoryFiles({ owner, repo, branch, token = null, onProgress = null }) {
    const notify = (stage, current, total, file, message) => {
      if (typeof onProgress === 'function') {
        const percent = total > 0 ? Math.round((current / total) * 100) : 0;
        onProgress({ stage, current, total, percent, file, message });
      }
    };

    if (token) githubApi.setToken(token.trim());
    const authToken = token ? token.trim() : (githubAuth.token || null);

    notify('resolving', 0, 0, '', `Resolving branch "${branch}" commit & tree...`);
    consoleService.info(`GitHub Importer: Resolving branch "${branch}" for ${owner}/${repo}...`);

    // 1. Resolve branch commit & tree SHA
    let commitSha = null;
    let treeSha = null;

    try {
      const branchInfo = await githubApi.getBranch(owner, repo, branch);
      commitSha = branchInfo.commit?.sha || null;
      treeSha = branchInfo.commit?.commit?.tree?.sha || branchInfo.commit?.sha || null;
    } catch {
      // Try ref
      try {
        const refInfo = await githubApi.getRef(owner, repo, branch);
        commitSha = refInfo?.object?.sha || null;
      } catch {
        /* fallback */
      }
    }

    if (!treeSha && commitSha) {
      try {
        const commitInfo = await githubApi.getCommit(owner, repo, commitSha);
        treeSha = commitInfo?.tree?.sha || null;
      } catch {
        /* fallback */
      }
    }

    // Fallback treeSha to branch name if SHA resolution was inconclusive
    if (!treeSha) {
      treeSha = branch;
    }

    notify('tree', 0, 0, '', 'Fetching repository file tree...');
    consoleService.info(`GitHub Importer: Fetching recursive tree (${treeSha})...`);

    // 2. Fetch recursive tree
    let treeResponse = null;
    try {
      treeResponse = await githubApi.getTree(owner, repo, treeSha, true);
    } catch (treeErr) {
      // If treeSha failed, try branch name directly
      if (treeSha !== branch) {
        treeResponse = await githubApi.getTree(owner, repo, branch, true);
      } else {
        throw treeErr;
      }
    }

    const rawTree = treeResponse?.tree || [];
    if (!rawTree.length) {
      throw new Error(`Repository branch "${branch}" contains no files or is empty.`);
    }

    // 3. Filter blob files and remove unnecessary artifacts
    const blobEntries = rawTree.filter(item => {
      if (item.type !== 'blob') return false;
      const p = item.path.replace(/\\/g, '/');
      if (p.startsWith('.git/')) return false;
      if (p.includes('/.DS_Store') || p === '.DS_Store') return false;
      if (p.startsWith('__MACOSX/')) return false;
      if (p.startsWith('._') || p.includes('/._')) return false;
      return true;
    });

    const totalFiles = blobEntries.length;
    consoleService.info(`GitHub Importer: Discovered ${totalFiles} repository files to download.`);
    notify('downloading', 0, totalFiles, '', `Downloading ${totalFiles} files...`);

    // 4. Download file contents with concurrency pool
    const files = {};
    const CONCURRENCY = 6;
    let completedCount = 0;

    const downloadItem = async (item) => {
      const path = item.path;
      const ext = path.split('.').pop().toLowerCase();
      const isBinary = BINARY_EXTS.has(ext) || path.includes('gradle-wrapper.jar');

      // Strategy A: Fast raw.githubusercontent.com download (saves API rate limits & allows streaming)
      let downloaded = false;
      try {
        const rawUrl = `https://raw.githubusercontent.com/${owner}/${repo}/${encodeURIComponent(branch)}/${path.split('/').map(encodeURIComponent).join('/')}`;
        const headers = {};
        if (authToken) {
          headers['Authorization'] = `token ${authToken}`;
        }

        const res = await fetch(rawUrl, { headers });
        if (res.ok) {
          if (isBinary) {
            const buf = await res.arrayBuffer();
            files[path] = new Uint8Array(buf);
          } else {
            files[path] = await res.text();
          }
          downloaded = true;
        }
      } catch {
        downloaded = false;
      }

      // Strategy B: Git Blobs API fallback (Guaranteed base64 payload via GitHub API)
      if (!downloaded && item.sha) {
        try {
          const blobData = await githubApi.getBlob(owner, repo, item.sha);
          const rawBase64 = (blobData.content || '').replace(/\s+/g, '');

          if (isBinary) {
            const binaryStr = atob(rawBase64);
            const bytes = new Uint8Array(binaryStr.length);
            for (let i = 0; i < binaryStr.length; i++) {
              bytes[i] = binaryStr.charCodeAt(i);
            }
            files[path] = bytes;
          } else {
            // Decode UTF-8 string safely from Base64
            const binaryStr = atob(rawBase64);
            const bytes = new Uint8Array(binaryStr.length);
            for (let i = 0; i < binaryStr.length; i++) {
              bytes[i] = binaryStr.charCodeAt(i);
            }
            files[path] = new TextDecoder('utf-8').decode(bytes);
          }
          downloaded = true;
        } catch (blobErr) {
          consoleService.warn(`GitHub Importer: Failed to fetch "${path}" via blob: ${blobErr.message}`);
        }
      }

      if (!downloaded) {
        // Fallback placeholder to prevent losing the file entry
        files[path] = isBinary ? new Uint8Array(0) : '';
      }

      completedCount++;
      notify('downloading', completedCount, totalFiles, path, `Downloading (${completedCount}/${totalFiles}): ${path.split('/').pop()}`);
    };

    // Run queue in concurrency chunks
    for (let i = 0; i < blobEntries.length; i += CONCURRENCY) {
      const chunk = blobEntries.slice(i, i + CONCURRENCY);
      await Promise.all(chunk.map(downloadItem));
    }

    notify('normalizing', totalFiles, totalFiles, '', 'Normalizing Android project structure...');

    // 5. Detect and normalize project root prefix if project is in a subdirectory
    const normalizedFiles = this.normalizeProjectStructure(files);

    return {
      files: normalizedFiles,
      commitSha: commitSha || '',
      treeSha: treeSha || '',
      fileCount: Object.keys(normalizedFiles).length,
    };
  }

  /**
   * Check if Android project files are placed in a subdirectory (e.g. android/, app/, library/)
   * and normalize paths so root files (settings.gradle, build.gradle, gradlew) reside at root.
   *
   * @param {Object.<string, string|Uint8Array>} files
   * @returns {Object.<string, string|Uint8Array>}
   */
  static normalizeProjectStructure(files) {
    const rawPaths = Object.keys(files);

    // Check if indicators already exist at top level
    const hasTopLevel = rawPaths.some(p => ROOT_INDICATOR_FILES.includes(p));
    if (hasTopLevel) {
      return files;
    }

    // Find paths containing root indicators
    let prefixToStrip = '';
    const indicatorPaths = rawPaths.filter(p => {
      const parts = p.split('/');
      const filename = parts[parts.length - 1];
      return ROOT_INDICATOR_FILES.includes(filename);
    });

    if (indicatorPaths.length > 0) {
      indicatorPaths.sort((a, b) => a.split('/').length - b.split('/').length);

      for (const ip of indicatorPaths) {
        const parts = ip.split('/');
        parts.pop();
        const candidate = parts.join('/') + '/';

        const hasSettings = rawPaths.some(p => p === `${candidate}settings.gradle` || p === `${candidate}settings.gradle.kts`);
        const hasBuild = rawPaths.some(p => p === `${candidate}build.gradle` || p === `${candidate}build.gradle.kts`);
        const hasGradlew = rawPaths.some(p => p === `${candidate}gradlew`);

        if (hasSettings || (hasBuild && hasGradlew) || hasBuild) {
          prefixToStrip = candidate;
          break;
        }
      }
    }

    if (!prefixToStrip) {
      // Check if all files reside in a single common parent folder
      const firstParts = rawPaths.map(p => p.split('/')[0]);
      if (firstParts.length > 0 && firstParts.every(p => p === firstParts[0])) {
        prefixToStrip = firstParts[0] + '/';
      }
    }

    if (!prefixToStrip) {
      return files;
    }

    const normalized = {};
    for (const [rawPath, content] of Object.entries(files)) {
      let cleanPath = rawPath;
      if (cleanPath.startsWith(prefixToStrip)) {
        cleanPath = cleanPath.slice(prefixToStrip.length);
      }
      cleanPath = cleanPath.replace(/^\/+/, '');
      if (cleanPath) {
        normalized[cleanPath] = content;
      }
    }

    return normalized;
  }

  /**
   * Import the loaded files into ProjectService, run ProjectAnalyzer,
   * update metadata, and set active GitHub repository.
   *
   * @param {object} opts
   * @param {Object.<string, string|Uint8Array>} opts.files
   * @param {object} opts.repoData
   * @param {string} opts.branch
   * @returns {Promise<{ project: object, analysis: object, fileCount: number }>}
   */
  static async importToProjectModel({ files, repoData, branch }) {
    consoleService.info(`GitHub Importer: Analyzing ${Object.keys(files).length} files...`);

    // Run Android Project Analyzer
    const analysis = ProjectAnalyzer.analyze(files);
    const primary = analysis.primaryModule || {};

    const projectName = repoData.name || analysis.rootProjectName || 'ImportedLibrary';
    const packageName = analysis.detectedPackageName || primary.namespace || primary.applicationId || 'com.example.library';
    const version = analysis.publishing?.version || primary.versionName || '1.0.0';

    // Create the project in ProjectService (IndexedDB)
    const project = await projectService.create({
      name: projectName,
      projectType: analysis.projectType || 'library',
      packageName: packageName,
      version: version,
      repoName: repoData.name || projectName,
      company: repoData.owner?.login || '',
      author: repoData.owner?.login || '',
      description: repoData.description || `Imported from GitHub: ${repoData.full_name} (${branch})`,
      compileSdk: primary.compileSdk ? parseInt(primary.compileSdk, 10) : 34,
      minSdk: primary.minSdk ? parseInt(primary.minSdk, 10) : 21,
      targetSdk: primary.targetSdk ? parseInt(primary.targetSdk, 10) : 34,
      language: analysis.technologies?.hasKotlin ? 'kotlin' : 'java',
      jdkRequirement: analysis.jdkRequirement || '17',
      license: repoData.license?.spdx_id || 'Apache-2.0',
      source: 'github',
      githubRepo: repoData.full_name,
      githubBranch: branch,
    });

    // Populate all project files in a single batch transaction
    await projectService.setFiles(files);

    // Save as active GitHub repository in the GitHub module so all GitHub pages stay in sync
    await githubRepository.saveActive(repoData);

    consoleService.success(
      `GitHub Importer: Successfully imported "${projectName}" (${Object.keys(files).length} files, type: ${analysis.projectType}, branch: ${branch})`
    );

    // Notify the rest of the application
    eventBus.emit('project:loaded', project);

    return {
      project,
      analysis,
      fileCount: Object.keys(files).length,
    };
  }

  parseRepoUrl(url) {
    return GitHubImporter.parseRepoUrl(url);
  }

  parseGitHubUrl(url) {
    return GitHubImporter.parseRepoUrl(url);
  }

  /**
   * Validate a raw repository URL or owner/repo string and return parsed info + branches.
   */
  async validateRepo(inputUrl, token = null) {
    const parsed = GitHubImporter.parseRepoUrl(inputUrl);
    const repoRes = await GitHubImporter.validateRepository(parsed.owner, parsed.repo, token);
    const branchRes = await GitHubImporter.getBranches(parsed.owner, parsed.repo, token);

    let chosenBranch = parsed.branch;
    if (chosenBranch) {
      const exact = branchRes.branches.find(b => b.name === chosenBranch);
      if (!exact) {
        const prefixMatch = branchRes.branches.find(b => chosenBranch.startsWith(b.name + '/'));
        if (prefixMatch) {
          chosenBranch = prefixMatch.name;
        }
      }
    }

    return {
      parsed,
      repo: repoRes.repo,
      tokenUsed: repoRes.tokenUsed,
      defaultBranch: branchRes.defaultBranch,
      branches: branchRes.branches,
      selectedBranch: chosenBranch || branchRes.defaultBranch
    };
  }

  async fetchRepositoryFiles(opts) {
    return GitHubImporter.fetchRepositoryFiles(opts);
  }

  async importRepository(opts) {
    let { owner, repo, branch, token, onProgress, projectName } = opts;
    if (!branch) {
      const branches = await GitHubImporter.getBranches(owner, repo, token);
      branch = branches.defaultBranch || 'main';
    }

    const repoRes = await GitHubImporter.validateRepository(owner, repo, token);
    const fetched = await GitHubImporter.fetchRepositoryFiles({
      owner,
      repo,
      branch,
      token,
      onProgress
    });

    const repoData = {
      ...repoRes.repo,
      name: projectName || repoRes.repo.name
    };

    return GitHubImporter.importToProjectModel({
      files: fetched.files,
      repoData,
      branch
    });
  }
}

export const gitHubImporter = new GitHubImporter();

