/**
 * JitPackClient — production API client for JitPack build monitoring and log analysis.
 *
 * Capabilities:
 * - Query builds via https://jitpack.io/api/builds/{owner}/{repo}
 * - Parse dictionary or array response formats
 * - Resilient version matching (supports both 'v1.0.0' and '1.0.0')
 * - Fetch build logs from https://jitpack.io/com/github/{owner}/{repo}/{version}/build.log
 * - Diagnose build completion, failure causes, stack traces, and completion timestamps
 * - Generate direct web links for user verification
 * - Resilient offline and network error fallbacks
 */

export class JitPackClient {
  static BASE_API = 'https://jitpack.io/api/builds';
  static BASE_RAW = 'https://jitpack.io/com/github';
  static BASE_WEB = 'https://jitpack.io';

  /**
   * Get direct URL to JitPack repository page.
   * @param {string} owner
   * @param {string} repo
   * @returns {string}
   */
  static getRepoWebUrl(owner, repo) {
    if (!owner || !repo) return this.BASE_WEB;
    const cleanOwner = encodeURIComponent(owner.trim());
    const cleanRepo = encodeURIComponent(repo.trim());
    return `${this.BASE_WEB}/#com.github.${cleanOwner}/${cleanRepo}`;
  }

  /**
   * Get direct URL to build log for specific version.
   * @param {string} owner
   * @param {string} repo
   * @param {string} version
   * @returns {string}
   */
  static getBuildLogUrl(owner, repo, version) {
    if (!owner || !repo || !version) return '';
    const cleanOwner = encodeURIComponent(owner.trim());
    const cleanRepo = encodeURIComponent(repo.trim());
    const cleanVersion = encodeURIComponent(String(version).trim());
    return `${this.BASE_RAW}/${cleanOwner}/${cleanRepo}/${cleanVersion}/build.log`;
  }

  /**
   * Query all builds for repository from JitPack.
   *
   * @param {string} owner
   * @param {string} repo
   * @returns {Promise<{
   *   success: boolean,
   *   statusList: Record<string, string>,
   *   latestSuccessVersion: string|null,
   *   failedVersions: string[],
   *   allVersions: string[],
   *   repoUrl: string,
   *   error?: string
   * }>}
   */
  static async getBuilds(owner, repo) {
    if (!owner || !repo) {
      return {
        success: false,
        statusList: {},
        latestSuccessVersion: null,
        failedVersions: [],
        allVersions: [],
        repoUrl: '',
        error: 'Owner and repository name are required.'
      };
    }

    const cleanOwner = owner.trim();
    const cleanRepo = repo.trim();
    const apiUrl = `${this.BASE_API}/${encodeURIComponent(cleanOwner)}/${encodeURIComponent(cleanRepo)}`;
    const repoUrl = this.getRepoWebUrl(cleanOwner, cleanRepo);

    try {
      const resp = await fetch(apiUrl, {
        method: 'GET',
        headers: { Accept: 'application/json' }
      });

      if (!resp.ok) {
        if (resp.status === 404) {
          return {
            success: true,
            statusList: {},
            latestSuccessVersion: null,
            failedVersions: [],
            allVersions: [],
            repoUrl,
            error: null,
            note: 'No builds recorded on JitPack yet.'
          };
        }
        return {
          success: false,
          statusList: {},
          latestSuccessVersion: null,
          failedVersions: [],
          allVersions: [],
          repoUrl,
          error: `JitPack HTTP ${resp.status}: ${resp.statusText}`
        };
      }

      const data = await resp.json();
      const statusList = {};
      const failedVersions = [];
      let latestSuccessVersion = null;

      if (data && typeof data === 'object') {
        if (Array.isArray(data)) {
          // Format: [{ version: '1.0.0', status: 'ok' }, ...]
          for (const item of data) {
            const v = String(item.version || item.tag || '').trim();
            const s = String(item.status || '').toLowerCase();
            if (v) {
              statusList[v] = s;
              if (s === 'ok' || s === 'success') {
                if (!latestSuccessVersion) latestSuccessVersion = v;
              } else if (s === 'error' || s === 'failed') {
                failedVersions.push(v);
              }
            }
          }
        } else {
          // Format: { "1.0.0": "ok", "v1.0.1": "error" } or nested builds
          for (const [key, val] of Object.entries(data)) {
            const v = String(key).trim();
            let s = '';
            if (typeof val === 'string') {
              s = val.toLowerCase();
            } else if (val && typeof val === 'object' && val.status) {
              s = String(val.status).toLowerCase();
            }
            if (v && s) {
              statusList[v] = s;
              if (s === 'ok' || s === 'success') {
                if (!latestSuccessVersion) latestSuccessVersion = v;
              } else if (s === 'error' || s === 'failed') {
                failedVersions.push(v);
              }
            }
          }
        }
      }

      const allVersions = Object.keys(statusList);

      return {
        success: true,
        statusList,
        latestSuccessVersion,
        failedVersions,
        allVersions,
        repoUrl
      };
    } catch (err) {
      return {
        success: false,
        statusList: {},
        latestSuccessVersion: null,
        failedVersions: [],
        allVersions: [],
        repoUrl,
        error: `Network/CORS error querying JitPack: ${err.message}`
      };
    }
  }

  /**
   * Resolve build status for a specific target version.
   * Matches both 'v1.0.0' and '1.0.0'.
   *
   * @param {Record<string, string>} statusList
   * @param {string} targetVersion
   * @returns {{ status: 'ok'|'error'|'building'|'not_found'|'unknown', matchedVersion: string|null }}
   */
  static resolveVersionStatus(statusList, targetVersion) {
    if (!statusList || typeof statusList !== 'object' || !targetVersion) {
      return { status: 'unknown', matchedVersion: null };
    }

    const clean = String(targetVersion).trim();
    const withV = clean.startsWith('v') || clean.startsWith('V') ? clean : `v${clean}`;
    const withoutV = clean.replace(/^v/i, '');

    // 1. Direct match
    if (statusList[clean] !== undefined) {
      return { status: this.#normalizeStatus(statusList[clean]), matchedVersion: clean };
    }
    // 2. Try with v prefix
    if (statusList[withV] !== undefined) {
      return { status: this.#normalizeStatus(statusList[withV]), matchedVersion: withV };
    }
    // 3. Try without v prefix
    if (statusList[withoutV] !== undefined) {
      return { status: this.#normalizeStatus(statusList[withoutV]), matchedVersion: withoutV };
    }

    return { status: 'not_found', matchedVersion: null };
  }

  static #normalizeStatus(raw) {
    const s = String(raw || '').toLowerCase().trim();
    if (s === 'ok' || s === 'success') return 'ok';
    if (s === 'error' || s === 'failed') return 'error';
    if (s === 'building' || s === 'pending') return 'building';
    return s || 'unknown';
  }

  /**
   * Fetch and analyze the raw build log for a version.
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} version
   * @returns {Promise<{
   *   success: boolean,
   *   log: string,
   *   status: 'ok'|'error'|'building'|'not_found'|'unknown',
   *   errorSnippet: string|null,
   *   logUrl: string,
   *   error?: string
   * }>}
   */
  static async fetchBuildLog(owner, repo, version) {
    const logUrl = this.getBuildLogUrl(owner, repo, version);
    if (!logUrl) {
      return {
        success: false,
        log: '',
        status: 'unknown',
        errorSnippet: null,
        logUrl: '',
        error: 'Missing parameters for build log.'
      };
    }

    try {
      const resp = await fetch(logUrl, { method: 'GET' });
      if (!resp.ok) {
        if (resp.status === 404) {
          return {
            success: false,
            log: '',
            status: 'not_found',
            errorSnippet: 'Build log not found on JitPack. The release has not been built yet or the tag does not exist.',
            logUrl,
            error: 'Build log not found (HTTP 404).'
          };
        }
        return {
          success: false,
          log: '',
          status: 'unknown',
          errorSnippet: null,
          logUrl,
          error: `HTTP ${resp.status} ${resp.statusText}`
        };
      }

      const log = await resp.text();
      let status = 'unknown';
      let errorSnippet = null;

      if (log.includes('BUILD SUCCESSFUL')) {
        status = 'ok';
      } else if (log.includes('BUILD FAILED')) {
        status = 'error';
        // Extract error lines
        const lines = log.split(/\r?\n/);
        const errLines = [];
        let capturing = false;
        for (const line of lines) {
          if (line.includes('* What went wrong:') || line.includes('FAILURE: Build failed')) {
            capturing = true;
          }
          if (capturing) {
            errLines.push(line);
            if (errLines.length >= 15) break;
          }
        }
        errorSnippet = errLines.length > 0 ? errLines.join('\n') : 'Build failed on JitPack. Check full log.';
      }

      return {
        success: true,
        log,
        status,
        errorSnippet,
        logUrl
      };
    } catch (err) {
      return {
        success: false,
        log: '',
        status: 'unknown',
        errorSnippet: null,
        logUrl,
        error: `Could not retrieve log: ${err.message}`
      };
    }
  }
}
