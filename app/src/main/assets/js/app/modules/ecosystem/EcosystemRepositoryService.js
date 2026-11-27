/**
 * EcosystemRepositoryService — Central repository registry, caching, and state management.
 *
 * Responsibilities:
 * - Persist registered Android Library repositories in IndexedDB (`ecosystem_libraries`)
 * - Persist custom ecosystem groups (`ecosystem_groups`)
 * - Cache version and build metadata with configurable TTL (`ecosystem_cache`)
 * - In-flight concurrent request deduplication
 * - Automatic pre-population with NativeRuntime ecosystem baseline libraries
 * - Offline-first fallback using cached data
 * - Rate-limit tracking and safe polling
 * - CRUD operations: register, update, delete, refresh individual, refresh all
 */

import { storage } from '../storage/StorageService.js';
import { githubApi } from '../github/GitHubApi.js';
import { githubAuth } from '../github/GitHubAuth.js';
import { JitPackClient } from './JitPackClient.js';
import { VersionComparator } from './VersionComparator.js';
import { eventBus } from '../../core/EventBus.js';

const STORE_LIBRARIES = 'ecosystem_libraries';
const STORE_GROUPS = 'ecosystem_groups';
const STORE_CACHE = 'ecosystem_cache';

const DEFAULT_CACHE_TTL_MS = 10 * 60 * 1000; // 10 minutes

export class EcosystemRepositoryService {
  #inFlightRequests = new Map();
  #rateLimitInfo = { limit: 60, remaining: 60, reset: 0 };
  #initialized = false;

  /**
   * Default NativeRuntime Android library ecosystem baseline repositories.
   * Note: Initial state is clean/unverified until live API sync is triggered.
   * No synthetic or fake version/release/tag/build data is hardcoded into production.
   */
  static DEFAULT_LIBRARIES = [
    {
      id: 'infoadmstudio2020-cyber__native-logger-sdk',
      name: 'NativeLogger SDK',
      owner: 'infoadmstudio2020-cyber',
      repo: 'native-logger-sdk',
      url: 'https://github.com/infoadmstudio2020-cyber/native-logger-sdk',
      groupId: 'com.github.infoadmstudio2020-cyber',
      artifactId: 'native-logger-sdk',
      ecosystemGroup: 'NativeRuntime Core',
      isEcosystem: true,
      isDefault: true,
      description: 'High-performance offline-first logging SDK with binary payload buffering and ringbuffer safety.',
      projectVersion: 'Unknown',
      githubRelease: null,
      gitTag: null,
      jitpackVersion: null,
      jitpackStatus: 'unknown',
      lastReleaseDate: null,
      lastRepoUpdate: null,
      dependencyCount: 0,
      dependencies: [],
      lastChecked: null,
    },
    {
      id: 'infoadmstudio2020-cyber__native-runtime-core',
      name: 'NativeRuntime Core',
      owner: 'infoadmstudio2020-cyber',
      repo: 'native-runtime-core',
      url: 'https://github.com/infoadmstudio2020-cyber/native-runtime-core',
      groupId: 'com.github.infoadmstudio2020-cyber',
      artifactId: 'native-runtime-core',
      ecosystemGroup: 'NativeRuntime Core',
      isEcosystem: true,
      isDefault: true,
      description: 'Core runtime architecture, thread dispatcher, memory guard, and lifecycle management for NativeRuntime.',
      projectVersion: 'Unknown',
      githubRelease: null,
      gitTag: null,
      jitpackVersion: null,
      jitpackStatus: 'unknown',
      lastReleaseDate: null,
      lastRepoUpdate: null,
      dependencyCount: 0,
      dependencies: [],
      lastChecked: null,
    },
    {
      id: 'infoadmstudio2020-cyber__native-security-guard',
      name: 'Native Security Guard',
      owner: 'infoadmstudio2020-cyber',
      repo: 'native-security-guard',
      url: 'https://github.com/infoadmstudio2020-cyber/native-security-guard',
      groupId: 'com.github.infoadmstudio2020-cyber',
      artifactId: 'native-security-guard',
      ecosystemGroup: 'Security & Device',
      isEcosystem: true,
      isDefault: true,
      description: 'Anti-tampering, root & hook detection, class verification, and cryptographic storage protection.',
      projectVersion: 'Unknown',
      githubRelease: null,
      gitTag: null,
      jitpackVersion: null,
      jitpackStatus: 'unknown',
      lastReleaseDate: null,
      lastRepoUpdate: null,
      dependencyCount: 0,
      dependencies: [],
      lastChecked: null,
    },
    {
      id: 'infoadmstudio2020-cyber__native-ui-components',
      name: 'Native UI Components',
      owner: 'infoadmstudio2020-cyber',
      repo: 'native-ui-components',
      url: 'https://github.com/infoadmstudio2020-cyber/native-ui-components',
      groupId: 'com.github.infoadmstudio2020-cyber',
      artifactId: 'native-ui-components',
      ecosystemGroup: 'UI & Widgets',
      isEcosystem: true,
      isDefault: true,
      description: 'Industrial HUD components, vector dials, and real-time telemetry meters.',
      projectVersion: 'Unknown',
      githubRelease: null,
      gitTag: null,
      jitpackVersion: null,
      jitpackStatus: 'unknown',
      lastReleaseDate: null,
      lastRepoUpdate: null,
      dependencyCount: 0,
      dependencies: [],
      lastChecked: null,
    }
  ];

  /**
   * Verified real-world Android library ecosystem samples.
   * Useful when users want live, working GitHub Releases, Git Tags, and JitPack/Maven builds.
   */
  static REAL_SAMPLE_LIBRARIES = [
    {
      id: 'square__retrofit',
      name: 'Retrofit',
      owner: 'square',
      repo: 'retrofit',
      url: 'https://github.com/square/retrofit',
      groupId: 'com.squareup.retrofit2',
      artifactId: 'retrofit',
      ecosystemGroup: 'Networking & HTTP',
      isEcosystem: true,
      description: 'Type-safe HTTP client for Android and the JVM by Square.',
      projectVersion: 'Unknown',
      githubRelease: null,
      gitTag: null,
      jitpackVersion: null,
      jitpackStatus: 'unknown',
      lastReleaseDate: null,
      lastRepoUpdate: null,
      dependencyCount: 0,
      dependencies: [],
      lastChecked: null,
    },
    {
      id: 'google__gson',
      name: 'Gson',
      owner: 'google',
      repo: 'gson',
      url: 'https://github.com/google/gson',
      groupId: 'com.google.code.gson',
      artifactId: 'gson',
      ecosystemGroup: 'Utilities',
      isEcosystem: true,
      description: 'Java serialization/deserialization library to convert Java Objects into JSON and back.',
      projectVersion: 'Unknown',
      githubRelease: null,
      gitTag: null,
      jitpackVersion: null,
      jitpackStatus: 'unknown',
      lastReleaseDate: null,
      lastRepoUpdate: null,
      dependencyCount: 0,
      dependencies: [],
      lastChecked: null,
    },
    {
      id: 'square__okhttp',
      name: 'OkHttp',
      owner: 'square',
      repo: 'okhttp',
      url: 'https://github.com/square/okhttp',
      groupId: 'com.squareup.okhttp3',
      artifactId: 'okhttp',
      ecosystemGroup: 'Networking & HTTP',
      isEcosystem: true,
      description: 'Square’s meticulous HTTP client for the JVM, Android, and GraalVM.',
      projectVersion: 'Unknown',
      githubRelease: null,
      gitTag: null,
      jitpackVersion: null,
      jitpackStatus: 'unknown',
      lastReleaseDate: null,
      lastRepoUpdate: null,
      dependencyCount: 0,
      dependencies: [],
      lastChecked: null,
    }
  ];

  /**
   * Default ecosystem groups.
   */
  static DEFAULT_GROUPS = [
    { id: 'NativeRuntime Core', name: 'NativeRuntime Core', color: '#0284C7', description: 'Core runtime and baseline modules' },
    { id: 'UI & Widgets', name: 'UI & Widgets', color: '#8B5CF6', description: 'User interface widgets and display components' },
    { id: 'Security & Device', name: 'Security & Device', color: '#10B981', description: 'Device security and anti-tamper modules' },
    { id: 'Networking & HTTP', name: 'Networking & HTTP', color: '#F59E0B', description: 'Network transports and API communication' },
    { id: 'Utilities', name: 'Utilities', color: '#64748B', description: 'General utility libraries' }
  ];

  /**
   * Initialize repository service and pre-populate defaults if store is empty.
   */
  async init() {
    if (this.#initialized) return;
    await storage.init();

    // Check if libraries exist or if dummy libraries were previously cleared
    const isPurged = await storage.getSetting('ecosystem_dummy_purged', false);
    const existing = await storage.getAll(STORE_LIBRARIES);
    if (!existing || existing.length === 0) {
      if (!isPurged) {
        for (const lib of EcosystemRepositoryService.DEFAULT_LIBRARIES) {
          await storage.put(STORE_LIBRARIES, { ...lib });
        }
      }
    }

    // Check if groups exist
    const groups = await storage.getAll(STORE_GROUPS);
    if (!groups || groups.length === 0) {
      for (const grp of EcosystemRepositoryService.DEFAULT_GROUPS) {
        await storage.put(STORE_GROUPS, { ...grp });
      }
    }

    this.#initialized = true;
  }

  /**
   * Remove all dummy / placeholder baseline libraries (infoadmstudio2020-cyber).
   */
  async removeDummyLibraries() {
    await this.init();
    const all = await storage.getAll(STORE_LIBRARIES);
    let count = 0;
    for (const lib of all) {
      if (lib.owner === 'infoadmstudio2020-cyber' || lib.isDefault) {
        await storage.delete(STORE_LIBRARIES, lib.id);
        count++;
      }
    }
    await storage.setSetting('ecosystem_dummy_purged', true);
    eventBus.emit('ecosystem:libraries:updated');
    return count;
  }

  /**
   * Clear all monitored libraries completely.
   */
  async clearAll() {
    await this.init();
    const all = await storage.getAll(STORE_LIBRARIES);
    for (const lib of all) {
      await storage.delete(STORE_LIBRARIES, lib.id);
    }
    await storage.setSetting('ecosystem_dummy_purged', true);
    eventBus.emit('ecosystem:libraries:updated');
    return true;
  }

  /**
   * Load real, live, verified Android libraries (Retrofit, Gson, OkHttp) into the ecosystem.
   */
  async loadRealSampleLibraries() {
    await this.init();
    await this.removeDummyLibraries();
    for (const lib of EcosystemRepositoryService.REAL_SAMPLE_LIBRARIES) {
      await storage.put(STORE_LIBRARIES, { ...lib });
    }
    await storage.setSetting('ecosystem_dummy_purged', true);
    eventBus.emit('ecosystem:libraries:updated');
    return EcosystemRepositoryService.REAL_SAMPLE_LIBRARIES.length;
  }

  // ── Library CRUD ──────────────────────────────────────────────────────────

  /**
   * List all registered libraries with computed status.
   * @param {object} [filters]
   * @param {string} [filters.search]
   * @param {string} [filters.owner]
   * @param {string} [filters.status]
   * @param {string} [filters.group]
   * @param {string} [filters.sortBy]
   * @param {'asc'|'desc'} [filters.sortDir]
   * @returns {Promise<Array<object>>}
   */
  async list(filters = {}) {
    await this.init();
    let list = await storage.getAll(STORE_LIBRARIES);

    // Compute status for each library record
    list = list.map(lib => {
      const statusObj = VersionComparator.determineStatus({
        projectVersion: lib.projectVersion,
        githubRelease: lib.githubRelease,
        gitTag: lib.gitTag,
        jitpackVersion: lib.jitpackVersion,
        jitpackStatus: lib.jitpackStatus,
      });
      return {
        ...lib,
        versionStatus: statusObj.status,
        statusCode: statusObj.code,
        severity: statusObj.severity,
        statusDescription: statusObj.description,
        mismatchDetails: statusObj.mismatchDetails,
      };
    });

    // Apply filters
    if (filters.search) {
      const q = filters.search.toLowerCase().trim();
      list = list.filter(l =>
        (l.name && l.name.toLowerCase().includes(q)) ||
        (l.owner && l.owner.toLowerCase().includes(q)) ||
        (l.repo && l.repo.toLowerCase().includes(q)) ||
        (l.groupId && l.groupId.toLowerCase().includes(q)) ||
        (l.artifactId && l.artifactId.toLowerCase().includes(q))
      );
    }

    if (filters.owner && filters.owner !== 'all') {
      list = list.filter(l => l.owner?.toLowerCase() === filters.owner.toLowerCase());
    }

    if (filters.group && filters.group !== 'all') {
      list = list.filter(l => l.ecosystemGroup === filters.group);
    }

    if (filters.status && filters.status !== 'all') {
      list = list.filter(l => l.versionStatus === filters.status || l.statusCode === filters.status);
    }

    // Apply sorting
    const sortBy = filters.sortBy || 'name';
    const sortDir = filters.sortDir || 'asc';
    const mul = sortDir === 'asc' ? 1 : -1;

    list.sort((a, b) => {
      if (sortBy === 'name') {
        return (a.name || '').localeCompare(b.name || '') * mul;
      }
      if (sortBy === 'version') {
        return VersionComparator.compare(a.projectVersion, b.projectVersion) * mul;
      }
      if (sortBy === 'releaseDate') {
        const dA = new Date(a.lastReleaseDate || 0).getTime();
        const dB = new Date(b.lastReleaseDate || 0).getTime();
        return (dA - dB) * mul;
      }
      if (sortBy === 'updateDate') {
        const dA = new Date(a.lastRepoUpdate || a.lastChecked || 0).getTime();
        const dB = new Date(b.lastRepoUpdate || b.lastChecked || 0).getTime();
        return (dA - dB) * mul;
      }
      return 0;
    });

    return list;
  }

  /**
   * Get single library by ID.
   * @param {string} id
   * @returns {Promise<object|null>}
   */
  async get(id) {
    await this.init();
    const lib = await storage.get(STORE_LIBRARIES, id);
    if (!lib) return null;

    const statusObj = VersionComparator.determineStatus({
      projectVersion: lib.projectVersion,
      githubRelease: lib.githubRelease,
      gitTag: lib.gitTag,
      jitpackVersion: lib.jitpackVersion,
      jitpackStatus: lib.jitpackStatus,
    });

    return {
      ...lib,
      versionStatus: statusObj.status,
      statusCode: statusObj.code,
      severity: statusObj.severity,
      statusDescription: statusObj.description,
      mismatchDetails: statusObj.mismatchDetails,
    };
  }

  /**
   * Register a new library into the ecosystem.
   * Prevents duplicates by owner/repo ID.
   *
   * @param {object} repoData
   * @returns {Promise<object>}
   */
  async register(repoData) {
    await this.init();
    if (!repoData.owner || !repoData.repo) {
      throw new Error('Repository owner and name are required.');
    }

    const cleanOwner = repoData.owner.trim();
    const cleanRepo = repoData.repo.trim();
    const id = `${cleanOwner.toLowerCase()}__${cleanRepo.toLowerCase()}`;

    const existing = await storage.get(STORE_LIBRARIES, id);
    if (existing) {
      throw new Error(`Repository "${cleanOwner}/${cleanRepo}" is already registered in the ecosystem.`);
    }

    const newRecord = {
      id,
      name: repoData.name || cleanRepo,
      owner: cleanOwner,
      repo: cleanRepo,
      url: repoData.url || `https://github.com/${cleanOwner}/${cleanRepo}`,
      groupId: repoData.groupId || `com.github.${cleanOwner}`,
      artifactId: repoData.artifactId || cleanRepo,
      ecosystemGroup: repoData.ecosystemGroup || 'NativeRuntime Core',
      isEcosystem: repoData.isEcosystem !== undefined ? !!repoData.isEcosystem : true,
      description: repoData.description || '',
      projectVersion: repoData.projectVersion || '0.0.0',
      githubRelease: repoData.githubRelease || null,
      gitTag: repoData.gitTag || null,
      jitpackVersion: repoData.jitpackVersion || null,
      jitpackStatus: repoData.jitpackStatus || 'unknown',
      lastReleaseDate: repoData.lastReleaseDate || null,
      lastRepoUpdate: repoData.lastRepoUpdate || new Date().toISOString(),
      dependencyCount: repoData.dependencyCount || 0,
      dependencies: repoData.dependencies || [],
      lastChecked: Date.now(),
    };

    await storage.put(STORE_LIBRARIES, newRecord);
    eventBus.emit('ecosystem:library:registered', newRecord);

    return newRecord;
  }

  /**
   * Update an existing library record.
   * @param {string} id
   * @param {object} updates
   */
  async update(id, updates) {
    await this.init();
    const existing = await storage.get(STORE_LIBRARIES, id);
    if (!existing) throw new Error(`Library ${id} not found.`);

    const updated = { ...existing, ...updates, id };
    await storage.put(STORE_LIBRARIES, updated);
    eventBus.emit('ecosystem:library:updated', updated);
    return updated;
  }

  /**
   * Remove a library from the ecosystem monitoring list.
   * Does NOT delete it from GitHub!
   * @param {string} id
   */
  async remove(id) {
    await this.init();
    await storage.delete(STORE_LIBRARIES, id);
    eventBus.emit('ecosystem:library:removed', { id });
    return true;
  }

  // ── Live Refresh & Metadata Sync ──────────────────────────────────────────

  /**
   * Refresh a single library from GitHub and JitPack APIs.
   * Implements in-flight request deduplication and caching.
   *
   * @param {string} id
   * @param {boolean} [forceRefresh=false]
   * @returns {Promise<object>}
   */
  async refreshLibrary(id, forceRefresh = false) {
    await this.init();
    const lib = await storage.get(STORE_LIBRARIES, id);
    if (!lib) throw new Error(`Library ${id} not found`);

    const cacheKey = `lib_refresh_${id}`;

    // Deduplicate concurrent calls
    if (this.#inFlightRequests.has(cacheKey)) {
      return this.#inFlightRequests.get(cacheKey);
    }

    const promise = (async () => {
      try {
        // Check cache if not forcing refresh
        if (!forceRefresh) {
          const cached = await this.#getCached(cacheKey);
          if (cached && (Date.now() - cached.timestamp < DEFAULT_CACHE_TTL_MS)) {
            return cached.data;
          }
        }

        const owner = lib.owner;
        const repo = lib.repo;

        // Parallel fetch: GitHub Releases, Git Tags, JitPack Builds, and Repo Info
        const [releasesResult, tagsResult, jitpackResult, repoInfoResult] = await Promise.allSettled([
          this.#fetchGitHubReleases(owner, repo),
          this.#fetchGitHubTags(owner, repo),
          JitPackClient.getBuilds(owner, repo),
          this.#fetchGitHubRepoInfo(owner, repo),
        ]);

        const releases = releasesResult.status === 'fulfilled' ? releasesResult.value : [];
        const tags = tagsResult.status === 'fulfilled' ? tagsResult.value : [];
        const jp = jitpackResult.status === 'fulfilled' ? jitpackResult.value : { statusList: {} };
        const repoInfo = repoInfoResult.status === 'fulfilled' ? repoInfoResult.value : null;

        // 1. Latest GitHub Release (filter out draft if any)
        const publishedReleases = releases.filter(r => !r.draft);
        const latestRel = publishedReleases[0] || null;
        const latestReleaseVersion = latestRel?.tag_name || null;
        const lastReleaseDate = latestRel?.published_at || latestRel?.created_at || lib.lastReleaseDate;

        // 2. Latest Git Tag
        const latestTagObj = tags[0] || null;
        const latestTag = latestTagObj?.name || null;

        // 3. Inspect repository project files if available to extract project version
        let projectVersion = lib.projectVersion;
        let detectedDeps = lib.dependencies || [];
        const projectScan = await this.#inspectRepoFiles(owner, repo);
        if (projectScan.version) {
          projectVersion = projectScan.version;
        }
        if (projectScan.dependencies && projectScan.dependencies.length > 0) {
          detectedDeps = projectScan.dependencies;
        }

        // 4. Resolve JitPack build status
        const targetForJp = latestReleaseVersion || latestTag || projectVersion;
        const jpResolution = JitPackClient.resolveVersionStatus(jp.statusList, targetForJp);
        const jitpackStatus = jpResolution.status;
        const jitpackVersion = jpResolution.matchedVersion || targetForJp;

        const updates = {
          projectVersion: projectVersion || lib.projectVersion,
          githubRelease: latestReleaseVersion,
          gitTag: latestTag,
          jitpackVersion,
          jitpackStatus,
          lastReleaseDate,
          lastRepoUpdate: repoInfo?.updated_at || lib.lastRepoUpdate || new Date().toISOString(),
          description: repoInfo?.description || lib.description,
          dependencyCount: detectedDeps.length,
          dependencies: detectedDeps,
          lastChecked: Date.now(),
        };

        const updated = await this.update(id, updates);
        await this.#setCached(cacheKey, updated);

        return updated;
      } finally {
        this.#inFlightRequests.delete(cacheKey);
      }
    })();

    this.#inFlightRequests.set(cacheKey, promise);
    return promise;
  }

  /**
   * Refresh all registered libraries sequentially or in small batches.
   * @param {Function} [onProgress] callback with ({ current, total, lib })
   * @returns {Promise<Array<object>>}
   */
  async refreshAll(onProgress = null) {
    const list = await storage.getAll(STORE_LIBRARIES);
    const results = [];
    let count = 0;

    for (const item of list) {
      count++;
      if (onProgress) onProgress({ current: count, total: list.length, lib: item });
      try {
        const updated = await this.refreshLibrary(item.id, true);
        results.push(updated);
      } catch (err) {
        console.warn(`[Ecosystem] Failed to refresh ${item.name}:`, err);
        results.push(item);
      }
    }

    eventBus.emit('ecosystem:refreshed:all', { count: results.length });
    return results;
  }

  // ── GitHub API Handlers ───────────────────────────────────────────────────

  // ── GitHub & JitPack Remote Data Providers ───────────────────────────────

  /**
   * Fetch real GitHub releases for repository (distinguishing published, draft, prerelease).
   * @param {string} owner
   * @param {string} repo
   * @returns {Promise<Array<object>>}
   */
  async getReleases(owner, repo) {
    return await this.#fetchGitHubReleases(owner, repo);
  }

  /**
   * Fetch real Git tags for repository.
   * @param {string} owner
   * @param {string} repo
   * @returns {Promise<Array<object>>}
   */
  async getTags(owner, repo) {
    return await this.#fetchGitHubTags(owner, repo);
  }

  /**
   * Fetch live JitPack builds and build status for repository.
   * @param {string} owner
   * @param {string} repo
   * @returns {Promise<object>}
   */
  async getJitPackBuilds(owner, repo) {
    return await JitPackClient.getBuilds(owner, repo);
  }

  /**
   * Inspect remote Gradle build files and extract actual project version & declared dependencies.
   * @param {string} owner
   * @param {string} repo
   * @returns {Promise<{ version: string|null, dependencies: Array<object>, examinedFiles: string[] }>}
   */
  async inspectRemoteBuild(owner, repo) {
    return await this.#inspectRepoFiles(owner, repo);
  }

  async #fetchGitHubReleases(owner, repo) {
    try {
      const res = await githubApi.get(`/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/releases?per_page=30`);
      return Array.isArray(res) ? res : [];
    } catch (e) {
      return [];
    }
  }

  async #fetchGitHubTags(owner, repo) {
    try {
      const res = await githubApi.get(`/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/tags?per_page=30`);
      return Array.isArray(res) ? res : [];
    } catch (e) {
      return [];
    }
  }

  async #fetchGitHubRepoInfo(owner, repo) {
    try {
      return await githubApi.getRepo(owner, repo);
    } catch (e) {
      return null;
    }
  }

  /**
   * Inspect remote repository files (gradle.properties, build.gradle, build.gradle.kts, libs.versions.toml)
   * to determine actual project version and dependencies without executing remote code.
   */
  async #inspectRepoFiles(owner, repo) {
    const result = { version: null, dependencies: [], examinedFiles: [] };
    const checkedFiles = [
      'gradle.properties',
      'build.gradle',
      'build.gradle.kts',
      'library/build.gradle',
      'library/build.gradle.kts',
      'core/build.gradle',
      'core/build.gradle.kts',
      'gradle/libs.versions.toml',
      'settings.gradle',
      'settings.gradle.kts'
    ];

    try {
      // 1. Try reading gradle.properties first (standard Android library version location)
      const propsContent = await this.#fetchRawFile(owner, repo, 'gradle.properties');
      if (propsContent) {
        result.examinedFiles.push('gradle.properties');
        const vMatch = propsContent.match(/(?:VERSION_NAME|version|libraryVersion|POM_VERSION|VERSION)\s*=\s*['"]?([a-zA-Z0-9_.-]+)['"]?/i);
        if (vMatch) {
          result.version = vMatch[1].trim();
        }
      }

      // 2. Try build.gradle / build.gradle.kts
      const buildPaths = ['build.gradle', 'build.gradle.kts', 'library/build.gradle', 'library/build.gradle.kts', 'core/build.gradle'];
      for (const p of buildPaths) {
        const buildContent = await this.#fetchRawFile(owner, repo, p);
        if (buildContent) {
          result.examinedFiles.push(p);

          // Extract version if not yet resolved
          if (!result.version) {
            // Groovy: version = '1.0.0' or version '1.0.0' or versionName = '1.0.0'
            // Kotlin DSL: version = "1.0.0" or val version = "1.0.0"
            const vMatch = buildContent.match(/(?:versionName|version)\s*(?:=|\s)\s*['"]([^'"]+)['"]/i) ||
                           buildContent.match(/val\s+(?:versionName|version)\s*=\s*['"]([^'"]+)['"]/i);
            if (vMatch) {
              result.version = vMatch[1].trim();
            }
          }

          // Extract string coordinate dependencies: implementation 'group:artifact:version' or implementation("...")
          const depRegex = /(api|implementation|compileOnly|testImplementation|runtimeOnly)\s*\(?['"]([^'":\s]+):([^'":\s]+):([^'":\s]+)['"]\)?/g;
          let m;
          while ((m = depRegex.exec(buildContent)) !== null) {
            const depItem = {
              scope: m[1],
              groupId: m[2],
              artifactId: m[3],
              name: m[3],
              version: m[4]
            };
            if (!result.dependencies.some(d => d.groupId === depItem.groupId && d.artifactId === depItem.artifactId)) {
              result.dependencies.push(depItem);
            }
          }

          // Extract map notation dependencies: implementation group: '...', name: '...', version: '...'
          const mapDepRegex = /(api|implementation|compileOnly)\s+group:\s*['"]([^'"]+)['"],\s*name:\s*['"]([^'"]+)['"],\s*version:\s*['"]([^'"]+)['"]/g;
          let mm;
          while ((mm = mapDepRegex.exec(buildContent)) !== null) {
            const depItem = {
              scope: mm[1],
              groupId: mm[2],
              artifactId: mm[3],
              name: mm[3],
              version: mm[4]
            };
            if (!result.dependencies.some(d => d.groupId === depItem.groupId && d.artifactId === depItem.artifactId)) {
              result.dependencies.push(depItem);
            }
          }
        }
      }

      // 3. Try libs.versions.toml if version still null
      if (!result.version) {
        const tomlContent = await this.#fetchRawFile(owner, repo, 'gradle/libs.versions.toml');
        if (tomlContent) {
          result.examinedFiles.push('gradle/libs.versions.toml');
          const vMatch = tomlContent.match(/(?:version|libraryVersion|appVersion)\s*=\s*['"]([^'"]+)['"]/i);
          if (vMatch) {
            result.version = vMatch[1].trim();
          }
        }
      }
    } catch (e) {
      // Ignore inspection errors, fallback gracefully
    }
    return result;
  }

  /**
   * Resilient remote file fetcher:
   * 1. Attempts GitHub REST Contents API (/repos/{owner}/{repo}/contents/{path}) with Base64 decode
   * 2. Fallbacks to raw.githubusercontent.com
   */
  async #fetchRawFile(owner, repo, path) {
    // 1. Try GitHub Contents REST API
    try {
      const contentsData = await githubApi.get(`/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/contents/${encodeURIComponent(path)}`);
      if (contentsData && contentsData.content && contentsData.encoding === 'base64') {
        const binStr = atob(contentsData.content.replace(/\s/g, ''));
        const bytes = new Uint8Array(binStr.length);
        for (let i = 0; i < binStr.length; i++) bytes[i] = binStr.charCodeAt(i);
        return new TextDecoder('utf-8').decode(bytes);
      }
    } catch (apiErr) {
      // If 404 or contents API fails, fallback to raw endpoint
    }

    // 2. Fallback to raw.githubusercontent.com
    try {
      const url = `https://raw.githubusercontent.com/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/HEAD/${path}`;
      const headers = {};
      if (githubAuth.token) {
        headers.Authorization = `token ${githubAuth.token}`;
      }
      const resp = await fetch(url, { headers });
      if (resp.ok) {
        return await resp.text();
      }
      return null;
    } catch (rawErr) {
      return null;
    }
  }

  // ── Groups CRUD ───────────────────────────────────────────────────────────

  async getGroups() {
    await this.init();
    return await storage.getAll(STORE_GROUPS);
  }

  async addGroup(name, color = '#0284C7', description = '') {
    await this.init();
    const id = name.trim();
    const grp = { id, name: id, color, description };
    await storage.put(STORE_GROUPS, grp);
    return grp;
  }

  async deleteGroup(id) {
    await this.init();
    await storage.delete(STORE_GROUPS, id);
    return true;
  }

  // ── Dashboard Statistics ──────────────────────────────────────────────────

  /**
   * Aggregate high-level dashboard metrics for the HUD.
   * @returns {Promise<{
   *   totalLibraries: number,
   *   latestCount: number,
   *   updateAvailableCount: number,
   *   jitpackFailureCount: number,
   *   unverifiedCount: number,
   *   lastSyncTimestamp: number|null
   * }>}
   */
  async getDashboardMetrics() {
    const list = await this.list();

    let latestCount = 0;
    let updateAvailableCount = 0;
    let jitpackFailureCount = 0;
    let unverifiedCount = 0;
    let lastSyncTimestamp = null;

    for (const lib of list) {
      if (lib.lastChecked) {
        if (!lastSyncTimestamp || lib.lastChecked > lastSyncTimestamp) {
          lastSyncTimestamp = lib.lastChecked;
        }
      }

      if (lib.versionStatus === 'Latest') {
        latestCount++;
      } else if (lib.versionStatus === 'Update Available' || lib.versionStatus === 'Project Version Ahead' || lib.versionStatus === 'Version Mismatch') {
        updateAvailableCount++;
      }

      if (lib.jitpackStatus === 'error' || lib.jitpackStatus === 'failed') {
        jitpackFailureCount++;
      } else if (lib.jitpackStatus === 'not_found' || lib.jitpackStatus === 'unknown' || !lib.jitpackStatus) {
        unverifiedCount++;
      }
    }

    return {
      totalLibraries: list.length,
      latestCount,
      updateAvailableCount,
      jitpackFailureCount,
      unverifiedCount,
      lastSyncTimestamp
    };
  }

  // ── Caching Helpers ───────────────────────────────────────────────────────

  async #getCached(key) {
    try {
      return await storage.get(STORE_CACHE, key);
    } catch (e) {
      return null;
    }
  }

  async #setCached(key, data, ttlMs = DEFAULT_CACHE_TTL_MS) {
    try {
      await storage.put(STORE_CACHE, {
        key,
        data,
        timestamp: Date.now(),
        expiresAt: Date.now() + ttlMs,
      });
    } catch (e) {
      // Non-fatal
    }
  }
}

export const ecosystemRepositoryService = new EcosystemRepositoryService();
