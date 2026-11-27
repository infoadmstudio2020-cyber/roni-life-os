/**
 * GitHubRelease — create and list GitHub releases.
 */
import { githubApi } from './GitHubApi.js';

export class GitHubRelease {

  /**
   * Create a new release.
   * @param {string} owner
   * @param {string} repo
   * @param {object} opts
   * @param {string} opts.tag_name
   * @param {string} opts.name
   * @param {string} opts.body
   * @param {boolean} opts.draft
   * @param {boolean} opts.prerelease
   * @param {string} opts.target_commitish  — branch or SHA
   */
  async create(owner, repo, opts) {
    return githubApi.createRelease(owner, repo, opts);
  }

  /** List releases. */
  async list(owner, repo) {
    return githubApi.listReleases(owner, repo);
  }

  /** Get latest release. */
  async getLatest(owner, repo) {
    try { return await githubApi.getLatestRelease(owner, repo); }
    catch (e) { if (e.status === 404) return null; throw e; }
  }

  /**
   * Auto-generate release notes from commit messages.
   * @param {Array<{commit:{message:string},html_url:string,sha:string}>} commits
   */
  generateChangelog(commits, version) {
    if (!commits?.length) return `## ${version}\n\n- Initial release`;
    const lines = commits.slice(0, 20).map(c => {
      const msg = c.commit.message.split('\n')[0];
      const sha = c.sha.slice(0, 7);
      return `- ${msg} ([${sha}](${c.html_url}))`;
    });
    return `## What's Changed in ${version}\n\n${lines.join('\n')}\n\n**Full Changelog**: https://github.com`;
  }
}

export const githubRelease = new GitHubRelease();
