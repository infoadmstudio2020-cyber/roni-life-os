/**
 * GitHubPublisher — orchestrates the full publish workflow:
 *
 * Validate
 *   → Generate Files
 *   → Create/Get Repo
 *   → Ensure Initialized
 *   → Version/Tag Preflight
 *   → Upload
 *   → Commit
 *   → Tag
 *   → Release
 *   → Record History
 *
 * Production-safe behavior:
 * - Existing version tags are never overwritten.
 * - Existing version tags are checked BEFORE repository mutation.
 * - A conflicting existing tag stops the publish before upload.
 * - Same-version publishing is intentionally rejected when the tag
 *   already exists. Use a new semantic version instead.
 * - REAL mode never pretends that a publish succeeded.
 */
import { eventBus }            from '../../core/EventBus.js';
import { projectService }      from '../storage/ProjectService.js';
import { consoleService }      from '../storage/ConsoleService.js';
import { githubApi }            from './GitHubApi.js';
import { githubAuth }          from './GitHubAuth.js';
import { githubRepository }    from './GitHubRepository.js';
import { githubUploader }      from './GitHubUploader.js';
import { githubTag }           from './GitHubTag.js';
import { githubRelease }       from './GitHubRelease.js';
import { githubHistory }       from './GitHubHistory.js';
import { githubSettings }      from './GitHubSettings.js';
import { githubValidator }     from './GitHubValidator.js';
import { githubFileGenerator } from './GitHubFileGenerator.js';
import { JitPackAnalyzerEngine } from '../generator/JitPackAnalyzerEngine.js';

export class GitHubPublisher {

  #running = false;
  #aborted = false;

  #checkAbort() {
    if (this.#aborted) {
      throw new Error('Publish aborted by user');
    }
  }

  get isRunning() {
    return this.#running;
  }

  abort() {
    this.#aborted = true;
  }

  /**
   * Run the full publish pipeline.
   *
   * @param {object} opts
   * @param {string} opts.owner
   * @param {string} opts.repoName
   * @param {string} opts.description
   * @param {boolean} opts.isPrivate
   * @param {string} opts.branch
   * @param {string} opts.version
   * @param {string} opts.commitMessage
   * @param {string} opts.releaseNotes
   * @param {boolean} opts.createRelease
   * @param {boolean} opts.generateFiles
   * @param {boolean} opts.useExistingRepo
   */
  async publish(opts) {

    if (this.#running) {
      throw new Error(
        'Publish already in progress'
      );
    }

    this.#running = true;
    this.#aborted = false;

    const emit = (
      step,
      detail = ''
    ) => {

      consoleService.info(
        `GitHub: ${step}${
          detail
            ? ' — ' + detail
            : ''
        }`
      );

      eventBus.emit(
        'github:publishStep',
        {
          step,
          detail,
        }
      );
    };

    const progress = (pct) => {

      eventBus.emit(
        'github:publishProgress',
        {
          pct,
        }
      );
    };

    let repoUrl = '';
    let commitSha = '';

    try {

      // ─────────────────────────────────────────────────────────────
      // Step 1: Validate
      // ─────────────────────────────────────────────────────────────

      emit(
        'Validating project'
      );

      progress(5);

      const report =
        githubValidator.validate();

      if (!report.passed) {

        throw new Error(
          report.errors[0]
        );
      }

      this.#checkAbort();

      // ─────────────────────────────────────────────────────────────
      // Step 2: Generate GitHub files
      // ─────────────────────────────────────────────────────────────

      if (
        opts.generateFiles !== false
      ) {

        emit(
          'Generating GitHub files'
        );

        progress(15);

        const settings =
          githubSettings.get();

        const proj =
          projectService.current;

        const ghFiles =
          githubFileGenerator.generate(
            proj,
            opts.owner,
            settings
          );

        for (
          const [path, content]
          of Object.entries(ghFiles)
        ) {

          // Preserve existing customized files in project (e.g. template workflows, custom README)
          if (!projectService.getFile(path)) {
            await projectService.setFile(
              path,
              content
            );
          }
        }

        // Clean up legacy duplicate build.yml if ci.yml is present
        if (
          projectService.getFile('.github/workflows/ci.yml') &&
          projectService.getFile('.github/workflows/build.yml')
        ) {
          delete projectService.current.files['.github/workflows/build.yml'];
          await projectService.save();
        }

        this.#checkAbort();
      }

      // Hardening check: Ensure valid jitpack.yml always exists before uploading
      if (!projectService.getFile('jitpack.yml')) {
        const defaultJp = JitPackAnalyzerEngine.generateDefaultJitpackYmlForFiles(projectService.current?.files || {});
        await projectService.setFile('jitpack.yml', defaultJp);
      } else {
        // Upgrade safe.directory to wildcard if present with narrow path
        let currentJp = projectService.getFile('jitpack.yml');
        if (typeof currentJp === 'string' && currentJp.includes('safe.directory /build') && !currentJp.includes("safe.directory '*'")) {
          currentJp = currentJp.replace("safe.directory /build", "safe.directory '*'");
          await projectService.setFile('jitpack.yml', currentJp);
        }
      }

      // ─────────────────────────────────────────────────────────────
      // Step 3: Create or get repository
      // ─────────────────────────────────────────────────────────────

      emit(
        'Connecting to GitHub'
      );

      progress(25);

      let repo;
      const targetOwner = opts.owner;
      const targetRepoName = opts.repoName;

      if (
        opts.useExistingRepo
      ) {

        repo =
          await githubRepository.get(
            targetOwner,
            targetRepoName
          );

      } else {

        emit(
          'Creating repository',
          targetRepoName
        );

        try {
          repo =
            await githubRepository.create({
              name:
                targetRepoName,

              description:
                opts.description ||
                projectService.current?.description ||
                '',

              private:
                opts.isPrivate || false,
            });
        } catch (createErr) {
          const errMsg = (createErr?.message || '') + ' ' + JSON.stringify(createErr?.errors || createErr?.data || '');
          if (createErr?.status === 422 && (errMsg.includes('already exists') || errMsg.includes('name already exists'))) {
            consoleService.warn(
              `GitHub: Repository "${targetOwner}/${targetRepoName}" already exists on account. Using existing repository...`
            );
            repo = await githubRepository.get(targetOwner, targetRepoName);
          } else {
            throw createErr;
          }
        }

        /*
         * Requirement 6: If the repository was just created,
         * wait/verify repository availability through GET repository
         * before continuing to ensure replication propagation.
         */
        consoleService.info(
          `GitHub: Verifying newly created repository availability — ${repo.owner?.login || targetOwner}/${repo.name}...`
        );

        let verifiedRepo = null;
        for (let attempt = 1; attempt <= 10; attempt++) {
          try {
            verifiedRepo = await githubApi.getRepo(
              repo.owner?.login || targetOwner,
              repo.name
            );
            if (verifiedRepo && verifiedRepo.id) {
              repo = verifiedRepo;
              break;
            }
          } catch {
            consoleService.info(
              `GitHub: Waiting for repository propagation (attempt ${attempt}/10)...`
            );
          }
          await new Promise(r => setTimeout(r, 1000 * Math.min(attempt, 3)));
        }
      }

      /*
       * Requirement 10: Ensure the code uses the newly created/resolved
       * repository owner/name, not an old or stale repository context.
       */
      const owner = repo.owner?.login || opts.owner;
      const repoName = repo.name || opts.repoName;

      await githubRepository.saveActive(repo);

      repoUrl =
        repo.html_url;

      consoleService.info(
        `GitHub: Repository obtained & verified — ${
          owner
        }/${
          repoName
        } (${
          repo.html_url
        })`
      );

      this.#checkAbort();

      // ─────────────────────────────────────────────────────────────
      // Step 3b: Resolve branch & version
      // ─────────────────────────────────────────────────────────────

      const branch =
        opts.branch ||
        repo.default_branch ||
        'main';

      // ─────────────────────────────────────────────────────────────
      // Step 3c: Resolve version
      // ─────────────────────────────────────────────────────────────

      const version =
        String(
          opts.version ||
          projectService.current?.version ||
          '1.0.0'
        )
          .trim()
          .replace(/^v/, '');

      const tagName =
        `v${version}`;

      emit(
        'Checking version tag',
        tagName
      );

      progress(31);

      await this.#preflightVersionTag(
        owner,
        repoName,
        version
      );

      this.#checkAbort();

      // ─────────────────────────────────────────────────────────────
      // Step 4: Upload files
      // ─────────────────────────────────────────────────────────────

      emit(
        'Uploading files'
      );

      progress(35);

      const files =
        projectService.current?.files ||
        {};

      const message =
        opts.commitMessage ||
        `feat: initial release ${
          version
        }`;

      /*
       * Expected Flow:
       * Validate authentication
       * → Resolve target repository
       * → Verify repository exists/access
       * → Resolve branch
       * → Resolve current commit/tree
       * → Create blobs
       * → Create tree
       * → Create commit
       * → Update branch/ref
       *
       * Notice: Pass null for parentSha so uploadAll dynamically and authoritatively
       * resolves HEAD and base tree for (owner, repoName, branch).
       * NEVER reuse a tree SHA from another repository (e.g. joni-app), previous publish session,
       * or cached state.
       */
      const result =
        await githubUploader.uploadAll(
          owner,
          repoName,
          branch,
          files,
          message,
          null,
          (
            current,
            total,
            path
          ) => {

            const pct =
              35 +
              Math.round(
                (current / total) * 40
              );

            progress(pct);

            emit(
              `Uploading ${current}/${total}`,
              path
                .split('/')
                .pop()
            );
          }
        );

      commitSha =
        result.commitSha;

      const skippedWorkflows = !!result.skippedWorkflows;
      const skippedWorkflowFiles = result.skippedWorkflowFiles || [];

      this.#checkAbort();

      // ─────────────────────────────────────────────────────────────
      // Step 5: Create tag
      // ─────────────────────────────────────────────────────────────

      emit(
        'Creating tag',
        tagName
      );

      progress(80);

      const tag =
        await this.#createTag(
          owner,
          repoName,
          version,
          commitSha,
          `Release ${tagName}`
        );

      this.#checkAbort();

      // ─────────────────────────────────────────────────────────────
      // Step 6: Create release
      // ─────────────────────────────────────────────────────────────

      if (
        opts.createRelease !== false
      ) {

        emit(
          'Creating release',
          tag
        );

        progress(90);

        let notes =
          opts.releaseNotes || '';

        if (!notes) {

          try {

            await githubRepository.get(
              owner,
              repoName
            );

            notes =
              `## ${tag}\n\n` +
              `Initial release of ${
                projectService.current?.name ||
                repoName
              }.`;

          } catch {

            notes =
              `## ${tag}\n\n` +
              `Release ${tag}`;
          }
        }

        await githubRelease.create(
          owner,
          repoName,
          {
            tag_name:
              tag,

            name:
              `${repoName} ${tag}`,

            body:
              notes,

            draft:
              false,

            prerelease:
              false,

            target_commitish:
              branch,
          }
        );
      }

      // ─────────────────────────────────────────────────────────────
      // Step 7: Record history
      // ─────────────────────────────────────────────────────────────

      emit(
        'Finalizing'
      );

      progress(97);

      await githubHistory.add({
        owner:
          owner,

        repo:
          repoName,

        version:
          tag,

        commitSha:
          commitSha ? String(commitSha).slice(0, 7) : '—',

        repoUrl:
          repoUrl,

        status:
          'success',
      });

      progress(100);

      emit(
        'Completed successfully',
        repoUrl
      );

      eventBus.emit(
        'github:publishDone',
        {
          repoUrl,
          commitSha,
          version: tag,
          tag,
          skippedWorkflows,
          skippedWorkflowFiles,
        }
      );

      consoleService.success(
        `Published to GitHub: ${repoUrl}`
      );

    } catch (err) {

      consoleService.error(
        `GitHub publish failed: ${
          err?.message ||
          'Unknown error'
        }`
      );

      await githubHistory.add({
        owner:
          opts.owner,

        repo:
          opts.repoName,

        version:
          opts.version
            ? `v${String(
                opts.version
              ).replace(/^v/, '')}`
            : '—',

        commitSha:
          commitSha ? String(commitSha).slice(0, 7) : '—',

        repoUrl:
          repoUrl ||
          '—',

        status:
          'failed',

        error:
          err?.message ||
          'Unknown error',
      });

      eventBus.emit(
        'github:publishError',
        {
          error:
            err?.message ||
            'Unknown error',
        }
      );

      throw err;

    } finally {

      this.#running = false;
    }
  }

  /**
   * Version tag preflight.
   *
   * IMPORTANT:
   *
   * This executes BEFORE upload/commit.
   *
   * If the requested version tag already exists,
   * publishing is stopped safely.
   *
   * We intentionally do NOT reuse an existing tag here.
   *
   * Reason:
   *
   * The files being published may differ from the commit
   * currently referenced by the existing tag.
   *
   * Reusing the tag after creating a new commit would make
   * the version semantics ambiguous.
   *
   * Use a new version such as 1.0.1 instead.
   */
  async #preflightVersionTag(
    owner,
    repo,
    version
  ) {

    const tagName =
      `v${version}`;

    try {

      const existing =
        await githubTag.get(
          owner,
          repo,
          version
        );

      const existingSha =
        this.#extractTagCommitSha(
          existing
        );

      if (existingSha) {

        consoleService.error(
          `GitHub: Version tag "${tagName}" already exists on commit ${existingSha}`
        );

        throw new Error(
          `Version ${tagName} already exists and points to commit ${existingSha}. ` +
          `The existing tag was not overwritten. ` +
          `Use a new version such as ${this.#suggestNextVersion(version)}.`
        );
      }

      throw new Error(
        `Version ${tagName} already exists, but its target could not be verified. ` +
        `The existing tag was not overwritten.`
      );

    } catch (err) {

      /*
       * 404 means the tag does not exist.
       *
       * That is the expected successful preflight result.
       */
      const status =
        Number(
          err?.status ||
          err?.statusCode ||
          err?.response?.status ||
          0
        );

      if (
        status === 404
      ) {

        consoleService.success(
          `GitHub: Version tag "${tagName}" is available`
        );

        return {
          exists:
            false,

          tag:
            tagName,
        };
      }

      /*
       * Do not swallow any other error.
       *
       * In particular:
       * - 401
       * - 403
       * - 429
       * - 500+
       * - invalid tag response
       * - permission problems
       *
       * must stop the publish.
       */
      throw err;
    }
  }

  /**
   * Create the tag after the upload/commit has succeeded.
   *
   * The preflight already guaranteed that the version tag
   * did not exist at the beginning of this publish operation.
   *
   * If another process creates the same tag between preflight
   * and this call, GitHub will return 422 and the publish fails.
   *
   * We never overwrite that tag.
   */
  async #createTag(
    owner,
    repo,
    version,
    commitSha,
    message
  ) {

    const tagName =
      `v${version}`;

    try {

      const created =
        await githubTag.create(
          owner,
          repo,
          version,
          commitSha,
          message
        );

      consoleService.success(
        `GitHub: Tag created — ${tagName}`
      );

      return created;

    } catch (err) {

      const status =
        Number(
          err?.status ||
          err?.statusCode ||
          err?.response?.status ||
          0
        );

      const errorMessage =
        String(
          err?.message ||
          ''
        );

      if (
        status === 422 &&
        /reference already exists/i.test(
          errorMessage
        )
      ) {

        throw new Error(
          `Version ${tagName} was created by another process while publishing. ` +
          `The existing tag was not overwritten. ` +
          `Please use a new version.`
        );
      }

      throw err;
    }
  }

  /**
   * Suggest the next patch version.
   *
   * @private
   */
  #suggestNextVersion(
    version
  ) {

    try {

      return githubTag.increment(
        version,
        'patch'
      );

    } catch {

      return 'next version';
    }
  }

  /**
   * Extract commit SHA from possible GitHub tag responses.
   *
   * @private
   */
  #extractTagCommitSha(
    tag
  ) {

    if (!tag) {
      return '';
    }

    if (
      typeof tag === 'string'
    ) {

      return this.#isSha(tag)
        ? tag.trim()
        : '';
    }

    const candidates = [
      tag.sha,
      tag.object?.sha,
      tag.commit?.sha,
      tag.target?.sha,
      tag.tag?.object?.sha,
      tag.ref?.object?.sha,
    ];

    for (
      const value
      of candidates
    ) {

      if (
        typeof value === 'string' &&
        this.#isSha(value)
      ) {

        return value.trim();
      }
    }

    return '';
  }

  /**
   * Validate a full Git SHA-1.
   *
   * @private
   */
  #isSha(
    value
  ) {

    return (
      typeof value === 'string' &&
      /^[a-f0-9]{40}$/i.test(
        value.trim()
      )
    );
  }
}

export const githubPublisher =
  new GitHubPublisher();