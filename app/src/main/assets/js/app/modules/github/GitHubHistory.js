/**
 * GitHubHistory — records publish events in IndexedDB (settings store with prefix).
 */
import { storage } from '../storage/StorageService.js';

const KEY = 'gh_history';

export class GitHubHistory {
  /** @type {Array<object>} */
  #records = [];

  async load() {
    this.#records = await storage.getSetting(KEY, []);
    return this.#records;
  }

  get records() { return [...this.#records]; }

  /**
   * Add a history entry.
   * @param {object} entry
   * @param {string} entry.owner
   * @param {string} entry.repo
   * @param {string} entry.version
   * @param {string} entry.commitSha
   * @param {string} entry.repoUrl
   * @param {string} entry.status  — 'success'|'failed'
   * @param {string} [entry.error]
   */
  async add(entry) {
    await this.load();
    const record = {
      id:        `gh_hist_${Date.now()}`,
      timestamp: Date.now(),
      date:      new Date().toLocaleString(),
      ...entry,
    };
    this.#records.unshift(record);
    // Keep last 50
    this.#records = this.#records.slice(0, 50);
    await storage.setSetting(KEY, this.#records);
    return record;
  }

  async delete(id) {
    await this.load();
    this.#records = this.#records.filter(r => r.id !== id);
    await storage.setSetting(KEY, this.#records);
  }

  async clear() {
    this.#records = [];
    await storage.setSetting(KEY, []);
  }
}

export const githubHistory = new GitHubHistory();
