/**
 * GitHubRepository — repository creation, retrieval, and management.
 */
import { githubApi }  from './GitHubApi.js';
import { githubAuth } from './GitHubAuth.js';
import { storage }    from '../storage/StorageService.js';

const KEY_ACTIVE_REPO = 'gh_active_repo';

export class GitHubRepository {
  /** @type {object|null} */
  #activeRepo = null;

  get activeRepo() { return this.#activeRepo; }

  async loadSaved() {
    const repo = await storage.getSetting(KEY_ACTIVE_REPO, null);
    if (repo) this.#activeRepo = repo;
    return repo;
  }

  async saveActive(repo) {
    this.#activeRepo = repo;
    await storage.setSetting(KEY_ACTIVE_REPO, repo);
  }

  // ── Create ────────────────────────────────────────────────────────────────

  /**
   * Create a new GitHub repository.
   * @param {object} opts
   * @param {string} opts.name
   * @param {string} [opts.description]
   * @param {boolean} [opts.private]
   * @param {string} [opts.homepage]
   * @param {string[]} [opts.topics]
   * @param {string} [opts.license_template]
   * @returns {Promise<object>} repo object
   */
  async create(opts) {
    if (!githubAuth.isAuthenticated) throw new Error('Not authenticated');
    const payload = {
      name:               opts.name,
      description:        opts.description || '',
      private:            opts.private     || false,
      homepage:           opts.homepage    || '',
      // auto_init: true — REQUIRED. Repos created with auto_init:false have zero
      // git objects; GitHub rejects all Git Data API calls (blob/tree/commit) with
      // 409 "Git Repository is empty" until at least one commit exists.
      auto_init:          true,
      has_issues:         true,
      has_projects:       true,
      has_wiki:           true,
    };
    try {
      const repo = await githubApi.createRepo(payload);
      // Add topics separately if provided
      if (opts.topics?.length) {
        await githubApi.put(`/repos/${repo.owner.login}/${repo.name}/topics`, { names: opts.topics })
          .catch(() => {}); // non-critical
      }
      await this.saveActive(repo);
      return repo;
    } catch (err) {
      const errMsg = (err?.message || '') + ' ' + JSON.stringify(err?.errors || err?.data || '');
      if (err?.status === 422 && (errMsg.includes('already exists') || errMsg.includes('name already exists'))) {
        const user = await githubAuth.getUser().catch(() => null);
        const owner = user?.login || '';
        if (owner) {
          const existing = await this.get(owner, opts.name).catch(() => null);
          if (existing) {
            await this.saveActive(existing);
            return existing;
          }
        }
      }
      throw err;
    }
  }

  // ── Get ────────────────────────────────────────────────────────────────────

  async get(owner, name) {
    const repo = await githubApi.getRepo(owner, name);
    await this.saveActive(repo);
    return repo;
  }

  // ── List user repos ────────────────────────────────────────────────────────

  async listOwned(username = null) {
    const targetUser = username ? username.trim() : null;
    if (targetUser) {
      return await githubApi.listUserRepos(targetUser);
    }
    if (githubAuth.isAuthenticated) {
      return await githubApi.listRepos();
    }
    throw new Error('Please enter a GitHub username/organization or authenticate with a token.');
  }

  // ── Update metadata ────────────────────────────────────────────────────────

  async update(owner, name, opts) {
    const repo = await githubApi.updateRepo(owner, name, opts);
    await this.saveActive(repo);
    return repo;
  }

  // ── Delete ─────────────────────────────────────────────────────────────────

  async delete(owner, name) {
    await githubApi.deleteRepo(owner, name);
    if (this.#activeRepo?.name === name) {
      this.#activeRepo = null;
      await storage.setSetting(KEY_ACTIVE_REPO, null);
    }
  }

  // ── Stats ──────────────────────────────────────────────────────────────────

  async getStats(owner, name) {
    const repo = await githubApi.getRepo(owner, name);
    return {
      stars:        repo.stargazers_count,
      forks:        repo.forks_count,
      watchers:     repo.watchers_count,
      openIssues:   repo.open_issues_count,
      size:         repo.size,
      defaultBranch: repo.default_branch,
      htmlUrl:      repo.html_url,
      cloneUrl:     repo.clone_url,
      createdAt:    repo.created_at,
      updatedAt:    repo.updated_at,
    };
  }
}

export const githubRepository = new GitHubRepository();
