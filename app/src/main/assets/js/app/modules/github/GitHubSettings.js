/**
 * GitHubSettings — persists GitHub Publisher defaults in IndexedDB.
 */
import { storage } from '../storage/StorageService.js';

const KEY = 'gh_settings';

const DEFAULTS = {
  defaultUsername:    '',
  defaultOrg:         '',
  defaultLicense:     'Apache-2.0',
  defaultBranch:      'main',
  defaultVisibility:  'public',
  defaultVersion:     '1.0.0',
  autoCommit:         true,
  autoRelease:        true,
  autoReadme:         true,
  autoLicense:        true,
  autoWorkflows:      true,
  autoGitignore:      true,
  activeRepo:         null,
};

export class GitHubSettings {
  /** @type {object} */
  #data = { ...DEFAULTS };

  get() { return { ...this.#data }; }

  async load() {
    const saved = await storage.getSetting(KEY, {});
    this.#data  = { ...DEFAULTS, ...saved };
    return this.#data;
  }

  async save(partial) {
    this.#data = { ...this.#data, ...partial };
    await storage.setSetting(KEY, this.#data);
    return this.#data;
  }

  async reset() {
    this.#data = { ...DEFAULTS };
    await storage.setSetting(KEY, this.#data);
    return this.#data;
  }

  /** Quick getter for a single key. */
  getValue(key, fallback = null) {
    return this.#data[key] ?? fallback;
  }
}

export const githubSettings = new GitHubSettings();
