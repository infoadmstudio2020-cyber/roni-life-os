/**
 * GitHubApi — low-level GitHub REST API v3 client.
 *
 * Responsibilities:
 * - Authentication headers
 * - GitHub API version
 * - Timeout handling
 * - Network retry
 * - Rate-limit back-off
 * - Server-error retry
 * - Detailed API error reporting
 * - Git tree payload validation
 * - GitHub permission/request diagnostics
 * - Git tree preflight diagnostics
 * - Repository / Git Data / Tag / Release helpers
 *
 * Compatibility:
 * - No JavaScript private fields (#...)
 * - Browser-safe ES module
 * - Runtime token only
 */

const BASE_URL = 'https://api.github.com';

const TIMEOUT_MS = 30000;
const MAX_RETRY = 3;
const RETRY_DELAY_MS = 1500;

const SHA40_REGEX = /^[0-9a-f]{40}$/i;

export class GitHubApi {

  constructor() {
    /*
     * Runtime-only authentication token.
     *
     * Never persisted to localStorage/sessionStorage.
     * Never included in logs.
     */
    this._token = null;
    this._baseUrl = BASE_URL;
    this._oauthScopes = null;
  }

  setBaseUrl(url) {
    this._baseUrl = url ? String(url).replace(/\/+$/, '') : BASE_URL;
  }

  getBaseUrl() {
    return this._baseUrl || BASE_URL;
  }

  // ────────────────────────────────────────────────────────────────
  // Authentication
  // ────────────────────────────────────────────────────────────────

  setToken(token) {
    this._token =
      String(token || '').trim() || null;
  }

  clearToken() {
    this._token = null;
    this._oauthScopes = null;
  }

  getOAuthScopes() {
    return Array.isArray(this._oauthScopes) ? [...this._oauthScopes] : null;
  }

  hasOAuthScope(scope) {
    if (!this._oauthScopes) return null;
    return this._oauthScopes.includes(String(scope || '').trim().toLowerCase());
  }

  // ────────────────────────────────────────────────────────────────
  // Headers
  // ────────────────────────────────────────────────────────────────

  _headers(extra = {}) {

    const headers = {
      'Accept': 'application/vnd.github+json',
      'Content-Type': 'application/json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'RONi-Android-Library-Publisher',
      ...extra,
    };

    if (this._token) {
      headers.Authorization =
        `Bearer ${this._token}`;
    }

    return headers;
  }

  // ────────────────────────────────────────────────────────────────
  // URL
  // ────────────────────────────────────────────────────────────────

  _url(path) {

    const base = this._baseUrl || BASE_URL;

    if (!path) {
      return base;
    }

    if (
      /^https?:\/\//i.test(path)
    ) {
      return path;
    }

    return (
      `${base}` +
      `${path.startsWith('/') ? path : `/${path}`}`
    );
  }

  // ────────────────────────────────────────────────────────────────
  // Response parsing
  // ────────────────────────────────────────────────────────────────

  async _parseResponse(response) {

    const contentType =
      response.headers.get(
        'content-type'
      ) || '';

    if (
      contentType
        .toLowerCase()
        .includes('application/json')
    ) {

      try {
        return await response.json();
      } catch {
        return null;
      }
    }

    try {

      const text =
        await response.text();

      return text || null;

    } catch {

      return null;
    }
  }

  // ────────────────────────────────────────────────────────────────
  // Response diagnostics
  // ────────────────────────────────────────────────────────────────

  _getResponseDiagnostics(response) {

    return {

      requestId:
        response.headers.get(
          'X-GitHub-Request-Id'
        ) ||
        response.headers.get(
          'x-github-request-id'
        ) ||
        null,

      acceptedPermissions:
        response.headers.get(
          'X-Accepted-GitHub-Permissions'
        ) ||
        response.headers.get(
          'x-accepted-github-permissions'
        ) ||
        null,

      rateLimitRemaining:
        response.headers.get(
          'X-RateLimit-Remaining'
        ) || null,

      rateLimitReset:
        response.headers.get(
          'X-RateLimit-Reset'
        ) || null,

      retryAfter:
        response.headers.get(
          'Retry-After'
        ) || null,

      oauthScopes:
        response.headers.get(
          'X-OAuth-Scopes'
        ) ||
        response.headers.get(
          'x-oauth-scopes'
        ) ||
        null,
    };
  }

  // ────────────────────────────────────────────────────────────────
  // Rate-limit delay
  // ────────────────────────────────────────────────────────────────

  _getRateLimitWaitMs(response) {

    const retryAfter =
      response.headers.get(
        'Retry-After'
      );

    if (retryAfter) {

      const seconds =
        Number.parseInt(
          retryAfter,
          10
        );

      if (
        Number.isFinite(seconds) &&
        seconds >= 0
      ) {

        return seconds * 1000;
      }
    }

    const reset =
      response.headers.get(
        'X-RateLimit-Reset'
      );

    if (reset) {

      const resetSeconds =
        Number.parseInt(
          reset,
          10
        );

      if (
        Number.isFinite(resetSeconds)
      ) {

        return Math.max(
          0,
          resetSeconds * 1000 -
            Date.now()
        );
      }
    }

    return 60000;
  }

  // ────────────────────────────────────────────────────────────────
  // Retry policy
  // ────────────────────────────────────────────────────────────────

  _isRetryableStatus(status) {

    return (
      status === 403 ||
      status === 408 ||
      status === 429 ||
      status >= 500
    );
  }

  // ────────────────────────────────────────────────────────────────
  // Error creation
  // ────────────────────────────────────────────────────────────────

  _createApiError(
    method,
    path,
    url,
    status,
    data,
    diagnostics = null
  ) {

    const apiMessage =
      typeof data === 'string'
        ? data
        : data?.message ||
          `HTTP ${status}`;

    const error =
      new Error(
        `GitHub API ${status}: ${apiMessage}`
      );

    error.name =
      'GitHubApiError';

    error.status =
      status;

    error.data =
      data;

    error.method =
      method;

    error.path =
      path;

    error.url =
      url;

    if (
      data &&
      typeof data === 'object'
    ) {

      if (
        Array.isArray(
          data.errors
        )
      ) {

        error.errors =
          data.errors;
      }

      if (
        data.documentation_url
      ) {

        error.documentationUrl =
          data.documentation_url;
      }

      if (
        data.request_id
      ) {

        error.requestId =
          data.request_id;
      }
    }

    if (diagnostics) {

      if (
        diagnostics.requestId &&
        !error.requestId
      ) {

        error.requestId =
          diagnostics.requestId;
      }

      if (
        diagnostics.acceptedPermissions
      ) {

        error.acceptedGitHubPermissions =
          diagnostics.acceptedPermissions;
      }

      if (
        diagnostics.rateLimitRemaining !==
        null
      ) {

        error.rateLimitRemaining =
          diagnostics.rateLimitRemaining;
      }

      if (
        diagnostics.rateLimitReset !==
        null
      ) {

        error.rateLimitReset =
          diagnostics.rateLimitReset;
      }

      if (
        diagnostics.retryAfter !==
        null
      ) {

        error.retryAfter =
          diagnostics.retryAfter;
      }

      if (
        diagnostics.oauthScopes !==
        null
      ) {

        error.oauthScopes =
          diagnostics.oauthScopes;
      }
    }

    return error;
  }

  // ────────────────────────────────────────────────────────────────
  // Core request
  // ────────────────────────────────────────────────────────────────

  async _request(
    method,
    path,
    body = null,
    attempt = 1
  ) {

    const normalizedMethod =
      String(
        method || 'GET'
      ).toUpperCase();

    const url =
      this._url(path);

    const controller =
      new AbortController();

    const timer =
      setTimeout(
        () => {
          controller.abort();
        },
        TIMEOUT_MS
      );

    let response;

    try {

      response =
        await fetch(
          url,
          {
            method:
              normalizedMethod,

            headers:
              this._headers(),

            body:
              body !== null &&
              body !== undefined
                ? JSON.stringify(body)
                : undefined,

            signal:
              controller.signal,
          }
        );

    } catch (err) {

      clearTimeout(timer);

      if (
        err?.name ===
        'AbortError'
      ) {

        const timeoutError =
          new Error(
            `GitHub API request timed out after ${TIMEOUT_MS}ms`
          );

        timeoutError.name =
          'GitHubApiTimeoutError';

        timeoutError.method =
          normalizedMethod;

        timeoutError.path =
          path;

        timeoutError.url =
          url;

        throw timeoutError;
      }

      if (
        attempt < MAX_RETRY
      ) {

        await this._delay(
          RETRY_DELAY_MS *
            attempt
        );

        return this._request(
          normalizedMethod,
          path,
          body,
          attempt + 1
        );
      }

      const networkError =
        new Error(
          `GitHub API network error: ${
            err?.message ||
            'Unknown network error'
          }`
        );

      networkError.name =
        'GitHubApiNetworkError';

      networkError.method =
        normalizedMethod;

      networkError.path =
        path;

      networkError.url =
        url;

      networkError.cause =
        err;

      throw networkError;
    }

    clearTimeout(timer);

    // ────────────────────────────────────────────────────────────
    // Retry temporary errors
    // ────────────────────────────────────────────────────────────

    if (
      this._isRetryableStatus(
        response.status
      )
    ) {

      const remaining =
        response.headers.get(
          'X-RateLimit-Remaining'
        );

      const shouldRetry403 =
        response.status !== 403 ||
        remaining === '0' ||
        response.headers.has(
          'Retry-After'
        ) ||
        response.headers.has(
          'X-RateLimit-Reset'
        );

      if (
        shouldRetry403 &&
        attempt < MAX_RETRY
      ) {

        const waitMs =
          this._getRateLimitWaitMs(
            response
          );

        await this._delay(
          Math.min(
            waitMs,
            10000
          )
        );

        return this._request(
          normalizedMethod,
          path,
          body,
          attempt + 1
        );
      }
    }

    // ────────────────────────────────────────────────────────────
    // Diagnostics before body consumption
    // ────────────────────────────────────────────────────────────

    const diagnostics =
      this._getResponseDiagnostics(
        response
      );

    if (diagnostics.oauthScopes) {
      this._oauthScopes = String(diagnostics.oauthScopes)
        .split(',')
        .map(s => s.trim().toLowerCase())
        .filter(Boolean);
    }

    const data =
      await this._parseResponse(
        response
      );

    if (!response.ok) {

      throw this._createApiError(
        normalizedMethod,
        path,
        url,
        response.status,
        data,
        diagnostics
      );
    }

    return data;
  }

  // ────────────────────────────────────────────────────────────────
  // HTTP verbs
  // ────────────────────────────────────────────────────────────────

  get(path) {
    return this._request(
      'GET',
      path
    );
  }

  post(path, body) {
    return this._request(
      'POST',
      path,
      body
    );
  }

  patch(path, body) {
    return this._request(
      'PATCH',
      path,
      body
    );
  }

  put(path, body) {
    return this._request(
      'PUT',
      path,
      body
    );
  }

  delete(path) {
    return this._request(
      'DELETE',
      path
    );
  }

  // ────────────────────────────────────────────────────────────────
  // Authentication / User
  // ────────────────────────────────────────────────────────────────

  getUser() {
    return this.get('/user');
  }

  // ────────────────────────────────────────────────────────────────
  // Repository
  // ────────────────────────────────────────────────────────────────

  listRepos(
    page = 1,
    perPage = 100
  ) {

    return this.get(
      `/user/repos?per_page=${encodeURIComponent(
        perPage
      )}&page=${encodeURIComponent(
        page
      )}&sort=updated`
    );
  }

  listUserRepos(
    username,
    page = 1,
    perPage = 100
  ) {

    return this.get(
      `/users/${encodeURIComponent(
        username
      )}/repos?per_page=${encodeURIComponent(
        perPage
      )}&page=${encodeURIComponent(
        page
      )}&sort=updated`
    );
  }

  getRepo(
    owner,
    repo
  ) {

    return this.get(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}`
    );
  }

  createRepo(payload) {

    return this.post(
      '/user/repos',
      payload
    );
  }

  updateRepo(
    owner,
    repo,
    payload
  ) {

    return this.patch(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}`,
      payload
    );
  }

  deleteRepo(
    owner,
    repo
  ) {

    return this.delete(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}`
    );
  }

  // ────────────────────────────────────────────────────────────────
  // Git Blobs
  // ────────────────────────────────────────────────────────────────

  createBlob(
    owner,
    repo,
    content,
    enc = 'base64'
  ) {

    return this.post(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/blobs`,
      {
        content,
        encoding: enc,
      }
    );
  }

  // ────────────────────────────────────────────────────────────────
  // Git References
  // ────────────────────────────────────────────────────────────────

  getRef(
    owner,
    repo,
    branch
  ) {

    return this.get(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/ref/heads/${encodeURIComponent(
        branch
      )}`
    );
  }

  updateRef(
    owner,
    repo,
    branch,
    sha,
    force = false
  ) {

    return this.patch(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/refs/heads/${encodeURIComponent(
        branch
      )}`,
      {
        sha,
        force,
      }
    );
  }

  createRef(
    owner,
    repo,
    branch,
    sha
  ) {

    return this.post(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/refs`,
      {
        ref:
          `refs/heads/${branch}`,
        sha,
      }
    );
  }

  // ────────────────────────────────────────────────────────────────
  // Git Tree diagnostics
  // ────────────────────────────────────────────────────────────────

  /**
   * Read an existing Git tree.
   *
   * This is intentionally separate from createTree().
   *
   * It allows the publisher to verify that the base_tree SHA is
   * actually readable before attempting POST /git/trees.
   */
  getTree(
    owner,
    repo,
    treeSha,
    recursive = false
  ) {

    const normalizedSha =
      String(
        treeSha || ''
      ).trim();

    if (!normalizedSha) {
      const error =
        new Error(
          `GitHub getTree: invalid tree SHA: ${treeSha}`
        );

      error.name =
        'GitHubTreeValidationError';

      throw error;
    }

    const recursiveQuery =
      recursive
        ? '?recursive=1'
        : '';

    const path =
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/trees/${encodeURIComponent(
        normalizedSha
      )}${recursiveQuery}`;

    return this.get(path);
  }

  getBlob(
    owner,
    repo,
    sha
  ) {
    const normalizedSha = String(sha || '').trim();
    if (!normalizedSha) {
      throw new Error('GitHub getBlob: sha is required');
    }
    return this.get(
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/git/blobs/${encodeURIComponent(normalizedSha)}`
    );
  }

  listBranches(
    owner,
    repo,
    perPage = 100
  ) {
    return this.get(
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/branches?per_page=${encodeURIComponent(perPage)}`
    );
  }

  getBranch(
    owner,
    repo,
    branch
  ) {
    const normalizedBranch = String(branch || '').trim();
    if (!normalizedBranch) {
      throw new Error('GitHub getBranch: branch name is required');
    }
    return this.get(
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/branches/${encodeURIComponent(normalizedBranch)}`
    );
  }

  /**
   * Preflight diagnostics for Git tree creation.
   *
   * Checks:
   * 1. Repository access.
   * 2. Base tree readability.
   *
   * This does NOT modify GitHub.
   */
  async diagnoseTreeCreation(
    owner,
    repo,
    baseTree
  ) {

    const result = {
      owner,
      repo,
      baseTreeSha: baseTree,
      repository: {
        ok: false,
        status: null,
        message: null,
      },
      baseTree: {
        ok: false,
        status: null,
        message: null,
      },
    };

    console.info(
      '[GitHubApi] Tree creation preflight started',
      {
        owner,
        repo,
        baseTree,
      }
    );

    // ────────────────────────────────────────────────────────────
    // 1. Repository access
    // ────────────────────────────────────────────────────────────

    try {

      const repository =
        await this.getRepo(
          owner,
          repo
        );

      result.repository.ok =
        true;

      result.repository.status =
        200;

      result.repository.message =
        'Repository is accessible';

      result.repository.private =
        repository?.private ?? null;

      result.repository.defaultBranch =
        repository?.default_branch || null;

      result.repository.permissions =
        repository?.permissions || null;

      if (repository?.permissions && repository.permissions.push === false) {
        const permError = new Error(
          `Repository access denied: Authenticated user does not have push/write permission to ${owner}/${repo}`
        );
        permError.status = 403;
        permError.method = 'GET';
        permError.path = `/repos/${owner}/${repo}`;
        permError.data = { message: 'Write permission required', permissions: repository.permissions };
        throw permError;
      }

      console.info(
        '[GitHubApi] Tree preflight repository check: PASS',
        {
          owner,
          repo,
          private:
            repository?.private ?? null,
          defaultBranch:
            repository?.default_branch || null,
          permissions:
            repository?.permissions || null,
        }
      );

    } catch (error) {

      result.repository.ok =
        false;

      result.repository.status =
        error?.status ?? null;

      result.repository.message =
        error?.message ||
        'Repository access failed';

      console.error(
        '[GitHubApi] Tree preflight repository check: FAILED',
        {
          owner,
          repo,
          status:
            error?.status ?? null,
          requestId:
            error?.requestId ?? null,
          acceptedGitHubPermissions:
            error?.acceptedGitHubPermissions ??
            null,
          message:
            error?.message ||
            'Repository access failed',
        }
      );

      /*
       * Do not hide the original API error.
       *
       * If repository access itself fails, the tree endpoint
       * cannot be trusted for further diagnostics.
       */
      throw error;
    }

    // ────────────────────────────────────────────────────────────
    // 2. Base tree readability
    // ────────────────────────────────────────────────────────────

    try {

      const tree =
        await this.getTree(
          owner,
          repo,
          baseTree,
          false
        );

      result.baseTree.ok =
        true;

      result.baseTree.status =
        200;

      result.baseTree.message =
        'Base tree is readable';

      result.baseTree.sha =
        tree?.sha || baseTree;

      result.baseTree.entryCount =
        Array.isArray(tree?.tree)
          ? tree.tree.length
          : null;

      console.info(
        '[GitHubApi] Tree preflight base_tree check: PASS',
        {
          baseTree,
          returnedSha:
            tree?.sha || baseTree,
          entryCount:
            Array.isArray(tree?.tree)
              ? tree.tree.length
              : null,
        }
      );

    } catch (error) {

      result.baseTree.ok =
        false;

      result.baseTree.status =
        error?.status ?? null;

      result.baseTree.message =
        error?.message ||
        'Base tree is not readable';

      console.error(
        '[GitHubApi] Tree preflight base_tree check: FAILED',
        {
          owner,
          repo,
          baseTree,
          status:
            error?.status ?? null,
          requestId:
            error?.requestId ?? null,
          acceptedGitHubPermissions:
            error?.acceptedGitHubPermissions ??
            null,
          message:
            error?.message ||
            'Base tree is not readable',
        }
      );

      /*
       * Add explicit diagnostic information while preserving
       * the original GitHub API error.
       */
      error.treePreflight = result;

      throw error;
    }

    console.info(
      '[GitHubApi] Tree creation preflight completed',
      {
        repository:
          result.repository.ok,
        baseTree:
          result.baseTree.ok,
      }
    );

    return result;
  }

 // ────────────────────────────────────────────────────────────────
  // Git Trees
  // ────────────────────────────────────────────────────────────────

  _validateTreePayload(
    tree,
    baseTree
  ) {

    if (!Array.isArray(tree)) {

      throw new Error(
        'GitHub createTree: tree must be an array'
      );
    }

    if (tree.length === 0) {

      throw new Error(
        'GitHub createTree: tree must contain at least one entry'
      );
    }

    if (
      baseTree !== null &&
      baseTree !== undefined &&
      !SHA40_REGEX.test(
        String(baseTree)
      )
    ) {

      throw new Error(
        `GitHub createTree: invalid base_tree SHA: ${baseTree}`
      );
    }

    const allowedModes =
      new Set([
        '100644',
        '100755',
        '040000',
        '160000',
        '120000',
      ]);

    const allowedTypes =
      new Set([
        'blob',
        'tree',
        'commit',
      ]);

    const seenPaths =
      new Set();

    const invalidEntries =
      [];

    for (
      let index = 0;
      index < tree.length;
      index++
    ) {

      const entry =
        tree[index];

      if (
        !entry ||
        typeof entry !== 'object'
      ) {

        invalidEntries.push({
          index,
          reason:
            'entry must be an object',
        });

        continue;
      }

      const path =
        typeof entry.path === 'string'
          ? entry.path
          : '';

      const mode =
        String(
          entry.mode || ''
        );

      const type =
        String(
          entry.type || ''
        );

      const sha =
        entry.sha === null ||
        entry.sha === undefined
          ? null
          : String(entry.sha);

      if (!path) {

        invalidEntries.push({
          index,
          reason:
            'missing path',
        });

      } else if (
        path.startsWith('/')
      ) {

        invalidEntries.push({
          index,
          path,
          reason:
            'path must not start with "/"',
        });
      }

      if (
        !allowedModes.has(mode)
      ) {

        invalidEntries.push({
          index,
          path,
          reason:
            `invalid mode: ${mode}`,
        });
      }

      if (
        !allowedTypes.has(type)
      ) {

        invalidEntries.push({
          index,
          path,
          reason:
            `invalid type: ${type}`,
        });
      }

      if (
        type === 'blob' &&
        !SHA40_REGEX.test(
          sha || ''
        )
      ) {

        invalidEntries.push({
          index,
          path,
          reason:
            'blob SHA must be a 40-character hexadecimal SHA',
        });
      }

      if (path) {

        if (
          seenPaths.has(path)
        ) {

          invalidEntries.push({
            index,
            path,
            reason:
              'duplicate path',
          });
        }

        seenPaths.add(path);
      }
    }

    if (
      invalidEntries.length > 0
    ) {

      const preview =
        invalidEntries
          .slice(0, 10)
          .map(item => {

            const location =
              item.path
                ? `path="${item.path}"`
                : `index=${item.index}`;

            return (
              `${location}: ${item.reason}`
            );
          })
          .join('; ');

      const error =
        new Error(
          `GitHub createTree payload validation failed: ${invalidEntries.length} invalid entr${
            invalidEntries.length === 1
              ? 'y'
              : 'ies'
          }`
        );

      error.name =
        'GitHubTreeValidationError';

      error.invalidEntries =
        invalidEntries;

      error.preview =
        preview;

      throw error;
    }
  }

  /**
   * Create Git tree.
   *
   * GitHub endpoint:
   *
   * POST /repos/{owner}/{repo}/git/trees
   *
   * Payload:
   *
   * {
   *   base_tree: "...",
   *   tree: [...]
   * }
   */
  async createTree(
    owner,
    repo,
    tree,
    baseTree = null
  ) {

    this._validateTreePayload(
      tree,
      baseTree
    );

    const body = {
      tree,
    };

    const normalizedBaseTree =
      baseTree !== null &&
      baseTree !== undefined
        ? String(baseTree).trim()
        : '';

    if (
      normalizedBaseTree
    ) {

      body.base_tree =
        normalizedBaseTree;
    }

    console.info(
      '[GitHubApi] createTree request validation passed',
      {
        owner,
        repo,
        entryCount:
          tree.length,
        baseTree:
          body.base_tree || null,
        firstPath:
          tree[0]?.path || null,
        lastPath:
          tree[tree.length - 1]?.path || null,
      }
    );

    /*
     * IMPORTANT:
     *
     * Run a READ-ONLY diagnostic before POST /git/trees.
     *
     * This does not modify the repository.
     *
     * If baseTree exists, verify that:
     *
     *   GET /repos/{owner}/{repo}/git/trees/{baseTree}
     *
     * succeeds before trying:
     *
     *   POST /repos/{owner}/{repo}/git/trees
     *
     * This lets us distinguish:
     *
     *   Repository access problem
     *   Base tree access problem
     *   Git tree creation/write problem
     */
    if (
      normalizedBaseTree
    ) {

      try {

        await this.diagnoseTreeCreation(
          owner,
          repo,
          normalizedBaseTree
        );

      } catch (error) {

        /*
         * Do not attempt POST if the base tree itself cannot
         * be read. That would hide the real cause.
         */
        const diagnosticError =
          new Error(
            `Git tree preflight failed before createTree: ${
              error?.message ||
              'Unknown GitHub API error'
            }`
          );

        diagnosticError.name =
          'GitHubTreePreflightError';

        diagnosticError.status =
          error?.status ?? null;

        diagnosticError.requestId =
          error?.requestId ?? null;

        diagnosticError.acceptedGitHubPermissions =
          error?.acceptedGitHubPermissions ??
          null;

        diagnosticError.documentationUrl =
          error?.documentationUrl ??
          null;

        diagnosticError.data =
          error?.data ??
          null;

        diagnosticError.method =
          error?.method ??
          null;

        diagnosticError.path =
          error?.path ??
          null;

        diagnosticError.url =
          error?.url ??
          null;

        diagnosticError.cause =
          error;

        diagnosticError.treePreflight =
          error?.treePreflight ??
          null;

        throw diagnosticError;
      }
    }

    /*
     * Canonical GitHub REST endpoint.
     *
     * No /api/
     * No /v3/
     * No .git
     */
    const path =
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/trees`;

    console.info(
      '[GitHubApi] Sending Git Tree POST',
      {
        method: 'POST',
        path,
        owner,
        repo,
        entryCount:
          tree.length,
        baseTree:
          body.base_tree || null,
      }
    );

    try {

      const result =
        await this.post(
          path,
          body
        );

      console.info(
        '[GitHubApi] Git Tree POST: SUCCESS',
        {
          owner,
          repo,
          treeSha:
            result?.sha || null,
          entryCount:
            tree.length,
        }
      );

      return result;

    } catch (error) {

      console.error(
        '[GitHubApi] Git Tree POST: FAILED',
        {
          owner,
          repo,
          status:
            error?.status ?? null,
          requestId:
            error?.requestId ?? null,
          acceptedGitHubPermissions:
            error?.acceptedGitHubPermissions ??
            null,
          documentationUrl:
            error?.documentationUrl ??
            null,
          method:
            error?.method ??
            'POST',
          path:
            error?.path ??
            path,
          message:
            error?.message ||
            'GitHub Git Tree creation failed',
          response:
            error?.data ??
            null,
        }
      );

      throw error;
    }
  }

  // ────────────────────────────────────────────────────────────────
  // Git Commits
  // ────────────────────────────────────────────────────────────────

  /**
   * Get a Git commit object.
   *
   * GitHub endpoint:
   * GET /repos/{owner}/{repo}/git/commits/{commit_sha}
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} commitSha
   * @returns {Promise<object>}
   */
  getCommit(
    owner,
    repo,
    commitSha
  ) {

    const normalizedSha =
      String(commitSha || '').trim();

    if (!normalizedSha) {

      throw new Error(
        'GitHub getCommit: commitSha is required'
      );
    }

    return this.get(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/commits/${encodeURIComponent(
        normalizedSha
      )}`
    );
  }

  createCommit(
    owner,
    repo,
    message,
    treeSha,
    parents = []
  ) {

    return this.post(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/commits`,
      {
        message,
        tree:
          treeSha,
        parents,
      }
    );
  }

  // ────────────────────────────────────────────────────────────────
  // Git Tags
  // ────────────────────────────────────────────────────────────────

  createTagObject(
    owner,
    repo,
    tag,
    message,
    sha,
    type = 'commit'
  ) {

    return this.post(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/tags`,
      {
        tag,
        message,
        object:
          sha,
        type,
      }
    );
  }

  createTagRef(
    owner,
    repo,
    tag,
    sha
  ) {

    return this.post(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/refs`,
      {
        ref:
          `refs/tags/${tag}`,
        sha,
      }
    );
  }
  
  /**
   * Get a Git tag reference.
   *
   * GitHub endpoint:
   *
   * GET /repos/{owner}/{repo}/git/ref/tags/{tag}
   *
   * IMPORTANT:
   * This is intentionally separate from getRef().
   *
   * getRef() resolves:
   *   refs/heads/{branch}
   *
   * getTagRef() resolves:
   *   refs/tags/{tag}
   */
  getTagRef(
    owner,
    repo,
    tag
  ) {

    const normalizedTag =
      String(tag || '').trim();

    if (!normalizedTag) {

      throw new Error(
        'GitHub getTagRef: tag is required'
      );
    }

    return this.get(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/ref/tags/${encodeURIComponent(
        normalizedTag
      )}`
    );
  }

  listTags(
    owner,
    repo
  ) {

    return this.get(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/tags`
    );
  }

  deleteTagRef(
    owner,
    repo,
    tag
  ) {

    return this.delete(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/git/refs/tags/${encodeURIComponent(
        tag
      )}`
    );
  }

  // ────────────────────────────────────────────────────────────────
  // Releases
  // ────────────────────────────────────────────────────────────────

  createRelease(
    owner,
    repo,
    payload
  ) {

    return this.post(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/releases`,
      payload
    );
  }

  listReleases(
    owner,
    repo
  ) {

    return this.get(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/releases?per_page=20`
    );
  }

  getLatestRelease(
    owner,
    repo
  ) {

    return this.get(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/releases/latest`
    );
  }

  // ────────────────────────────────────────────────────────────────
  // Commits
  // ────────────────────────────────────────────────────────────────

  listCommits(
    owner,
    repo,
    branch = 'main',
    perPage = 20
  ) {

    return this.get(
      `/repos/${encodeURIComponent(
        owner
      )}/${encodeURIComponent(
        repo
      )}/commits?sha=${encodeURIComponent(
        branch
      )}&per_page=${encodeURIComponent(
        perPage
      )}`
    );
  }

  // ────────────────────────────────────────────────────────────────
  // GitHub Actions & Packages Integration
  // ────────────────────────────────────────────────────────────────

  listWorkflows(owner, repo) {
    return this.get(
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/actions/workflows`
    );
  }

  dispatchWorkflow(owner, repo, workflowId, ref = 'main', inputs = {}) {
    return this.post(
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/actions/workflows/${encodeURIComponent(workflowId)}/dispatches`,
      { ref, inputs }
    );
  }

  listWorkflowRuns(owner, repo, options = {}) {
    const query = new URLSearchParams();
    if (options.branch) query.append('branch', options.branch);
    if (options.event) query.append('event', options.event);
    if (options.status) query.append('status', options.status);
    if (options.per_page) query.append('per_page', String(options.per_page));
    const qs = query.toString() ? `?${query.toString()}` : '';

    return this.get(
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/actions/runs${qs}`
    );
  }

  getWorkflowRun(owner, repo, runId) {
    return this.get(
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/actions/runs/${encodeURIComponent(runId)}`
    );
  }

  listWorkflowJobs(owner, repo, runId) {
    return this.get(
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/actions/runs/${encodeURIComponent(runId)}/jobs`
    );
  }

  async getJobLogs(owner, repo, jobId) {
    try {
      const response = await fetch(
        `https://api.github.com/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/actions/jobs/${encodeURIComponent(jobId)}/logs`,
        {
          headers: this._headers({ Accept: 'application/vnd.github.v3+json' }),
        }
      );
      if (!response.ok) {
        return `Failed to fetch logs (HTTP ${response.status})`;
      }
      return await response.text();
    } catch (err) {
      return `Error fetching logs: ${err.message}`;
    }
  }

  checkActionsPermissions(owner, repo) {
    return this.get(
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/actions/permissions`
    );
  }

  listWorkflowRunArtifacts(owner, repo, runId) {
    return this.get(
      `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/actions/runs/${encodeURIComponent(runId)}/artifacts`
    );
  }

  listUserPackages(username) {
    return this.get(
      `/users/${encodeURIComponent(username)}/packages?package_type=maven`
    );
  }

  // ────────────────────────────────────────────────────────────────
  // Delay
  // ────────────────────────────────────────────────────────────────

  _delay(ms) {

    return new Promise(
      resolve => {
        setTimeout(
          resolve,
          ms
        );
      }
    );
  }
}

export const githubApi =
  new GitHubApi();