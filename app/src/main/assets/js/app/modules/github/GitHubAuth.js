/**
 * GitHubAuth — manages GitHub Personal Access Token storage and validation.
 * Token is stored ONLY in IndexedDB (settings store), never in project files.
 */
import { storage }  from '../storage/StorageService.js';
import { githubApi } from './GitHubApi.js';

const KEY_TOKEN   = 'gh_token';
const KEY_USER    = 'gh_user';

export class GitHubAuth {
  /** @type {{login:string, name:string, avatar_url:string, html_url:string}|null} */
  #user  = null;
  #token = null;

  // ── Accessors ─────────────────────────────────────────────────────────────

  get user()         { return this.#user; }
  get token()        { return this.#token; }
  get isAuthenticated() { return !!this.#token && !!this.#user; }

  // ── Load saved session ────────────────────────────────────────────────────

  async loadSaved() {
    const token = await storage.getSetting(KEY_TOKEN, null);
    const user  = await storage.getSetting(KEY_USER,  null);
    if (token && user) {
      this.#token = token;
      this.#user  = user;
      githubApi.setToken(token);
      return true;
    }
    return false;
  }

  // ── Login with PAT ────────────────────────────────────────────────────────

  /**
   * Validate and store a Personal Access Token.
   * @param {string} token
   * @returns {Promise<{login:string, name:string, avatar_url:string, html_url:string}>}
   */
  async loginWithToken(token) {
    if (!token || token.trim().length < 10) throw new Error('Token is too short');
    githubApi.setToken(token.trim());
    const user = await githubApi.getUser();
    this.#token = token.trim();
    this.#user  = { login: user.login, name: user.name, avatar_url: user.avatar_url, html_url: user.html_url, public_repos: user.public_repos };
    await storage.setSetting(KEY_TOKEN, this.#token);
    await storage.setSetting(KEY_USER,  this.#user);
    return this.#user;
  }

  // ── Verify existing token ─────────────────────────────────────────────────

  async verify() {
    if (!this.#token) throw new Error('No token stored');
    const user = await githubApi.getUser();
    this.#user = { login: user.login, name: user.name, avatar_url: user.avatar_url, html_url: user.html_url, public_repos: user.public_repos };
    await storage.setSetting(KEY_USER, this.#user);
    return this.#user;
  }

  // ── Runtime-only Authentication (Secure / No persistence) ─────────────────

  /**
   * Set token strictly in volatile memory for active session.
   * NEVER stored in IndexedDB or localStorage.
   */
  async setRuntimeToken(token, user = null) {
    if (!token || token.trim().length < 10) throw new Error('Token is too short');
    const cleanToken = token.trim();
    githubApi.setToken(cleanToken);
    if (!user) {
      const apiUser = await githubApi.getUser();
      this.#user = {
        login: apiUser.login,
        name: apiUser.name,
        avatar_url: apiUser.avatar_url,
        html_url: apiUser.html_url,
        public_repos: apiUser.public_repos
      };
    } else {
      this.#user = user;
    }
    this.#token = cleanToken;
    return this.#user;
  }

  // ── Logout & Clear Credentials ───────────────────────────────────────────

  async logout() {
    this.#token = null;
    this.#user  = null;
    githubApi.clearToken();
    try {
      await storage.setSetting(KEY_TOKEN, null);
      await storage.setSetting(KEY_USER,  null);
    } catch (e) {
      // Ignored if storage unavailable
    }
  }

  clearCredentials() {
    return this.logout();
  }
}

export const githubAuth = new GitHubAuth();
