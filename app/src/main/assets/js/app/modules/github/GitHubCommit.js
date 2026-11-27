/**
 * GitHubCommit — helpers for commit message building and history.
 */
import { githubApi } from './GitHubApi.js';

export class GitHubCommit {

  /**
   * Build a conventional-commit style message.
   * @param {string} type  — 'feat'|'fix'|'chore'|'docs'
   * @param {string} scope
   * @param {string} subject
   * @param {string} body
   */
  buildMessage(type, scope, subject, body = '') {
    let msg = scope ? `${type}(${scope}): ${subject}` : `${type}: ${subject}`;
    if (body) msg += `\n\n${body}`;
    return msg;
  }

  /** Fetch recent commits for a repo/branch. */
  async listRecent(owner, repo, branch = 'main', limit = 20) {
    return githubApi.listCommits(owner, repo, branch, limit);
  }

  /** Format a commit object for display. */
  formatCommit(c) {
    return {
      sha:     c.sha.slice(0, 7),
      fullSha: c.sha,
      message: c.commit.message.split('\n')[0],
      author:  c.commit.author.name,
      date:    new Date(c.commit.author.date).toLocaleString(),
      url:     c.html_url,
    };
  }
}

export const githubCommit = new GitHubCommit();
