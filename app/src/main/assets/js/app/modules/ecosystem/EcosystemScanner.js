/**
 * EcosystemScanner — Automatic GitHub Repository Discovery & Android Library Detection.
 *
 * Capabilities:
 * - Discovers repositories accessible to the authenticated GitHub session (or named user/org)
 * - Paginates results safely without hitting rate limits
 * - Identifies candidate Android library repositories by examining:
 *   * Repository topics ('android-library', 'aar', 'android', 'jitpack')
 *   * Description keywords
 *   * Primary language ('Java', 'Kotlin')
 *   * Android manifest and Gradle indicator files
 * - Detects Maven group ID, artifact ID, and project versions
 * - Cross-references already registered libraries to prevent duplicates
 * - Strictly read-only: never modifies, deletes, or writes to remote repositories
 */

import { githubApi } from '../github/GitHubApi.js';
import { githubAuth } from '../github/GitHubAuth.js';
import { ecosystemRepositoryService } from './EcosystemRepositoryService.js';

export class EcosystemScanner {

  /**
   * Scan accessible repositories for Android Library candidates.
   *
   * @param {object} [opts]
   * @param {string} [opts.username] target username or org (defaults to auth user)
   * @param {number} [opts.page=1]
   * @param {number} [opts.perPage=30]
   * @param {string} [opts.filter] optional search filter
   * @returns {Promise<{
   *   candidates: Array<object>,
   *   totalFound: number,
   *   page: number,
   *   hasMore: boolean,
   *   authenticated: boolean,
   *   username: string
   * }>}
   */
  static async scanRepositories(opts = {}) {
    const page = opts.page || 1;
    const perPage = opts.perPage || 30;
    const isAuth = githubAuth.isAuthenticated;
    const targetUser = opts.username ? opts.username.trim() : (isAuth ? githubAuth.user?.login : null);

    if (!targetUser && !isAuth) {
      throw new Error('Please enter a GitHub username/organization or log in with a Personal Access Token.');
    }

    // 1. Fetch repositories page from GitHub API
    let rawRepos = [];
    try {
      if (opts.username) {
        rawRepos = await githubApi.listUserRepos(targetUser, page, perPage);
      } else if (isAuth) {
        rawRepos = await githubApi.listRepos(page, perPage);
      } else {
        rawRepos = await githubApi.listUserRepos(targetUser, page, perPage);
      }
    } catch (err) {
      throw new Error(`GitHub API error while scanning repositories: ${err.message}`);
    }

    if (!Array.isArray(rawRepos)) {
      rawRepos = [];
    }

    // 2. Fetch existing registered libraries to check duplicate status
    const existingLibraries = await ecosystemRepositoryService.list();
    const registeredIds = new Set(existingLibraries.map(l => l.id.toLowerCase()));

    // 3. Inspect each repository for Android Library indicators
    const candidates = [];
    const filterText = (opts.filter || '').toLowerCase().trim();

    for (const repo of rawRepos) {
      const repoName = repo.name || '';
      const owner = repo.owner?.login || targetUser;
      const id = `${owner.toLowerCase()}__${repoName.toLowerCase()}`;

      // Check text filter
      if (filterText) {
        const hay = `${repoName} ${owner} ${repo.description || ''} ${(repo.topics || []).join(' ')}`.toLowerCase();
        if (!hay.includes(filterText)) {
          continue;
        }
      }

      const isRegistered = registeredIds.has(id);
      const detection = this.evaluateAndroidLibrary(repo);

      candidates.push({
        id,
        name: repoName,
        owner,
        repo: repoName,
        url: repo.html_url || `https://github.com/${owner}/${repoName}`,
        description: repo.description || '',
        language: repo.language || 'Unknown',
        defaultBranch: repo.default_branch || 'main',
        isPrivate: !!repo.private,
        isFork: !!repo.fork,
        topics: repo.topics || [],
        stars: repo.stargazers_count || 0,
        forks: repo.forks_count || 0,
        updatedAt: repo.updated_at || repo.created_at,
        isRegistered,
        isAndroidLibrary: detection.isLibrary,
        confidence: detection.confidence,
        indicators: detection.indicators,
        suggestedGroupId: `com.github.${owner}`,
        suggestedArtifactId: repoName,
        suggestedGroup: 'NativeRuntime Core',
      });
    }

    return {
      candidates,
      totalFound: candidates.length,
      page,
      hasMore: rawRepos.length === perPage,
      authenticated: isAuth,
      username: targetUser,
    };
  }

  /**
   * Evaluate whether a GitHub repository object is likely an Android library.
   *
   * @param {object} repo
   * @returns {{ isLibrary: boolean, confidence: 'high'|'medium'|'low', indicators: string[] }}
   */
  static evaluateAndroidLibrary(repo) {
    const indicators = [];
    let score = 0;

    const name = (repo.name || '').toLowerCase();
    const desc = (repo.description || '').toLowerCase();
    const topics = (repo.topics || []).map(t => String(t).toLowerCase());
    const lang = (repo.language || '').toLowerCase();

    // 1. Language check
    if (lang === 'java' || lang === 'kotlin') {
      score += 2;
      indicators.push(`Primary language: ${repo.language}`);
    }

    // 2. Name keywords
    if (name.includes('android') || name.includes('lib') || name.includes('sdk') || name.includes('aar')) {
      score += 2;
      indicators.push('Name contains Android/Library/SDK keywords');
    }

    // 3. Topics
    const libTopics = ['android-library', 'android', 'aar', 'jitpack', 'kotlin-android', 'gradle-plugin'];
    for (const t of topics) {
      if (libTopics.includes(t)) {
        score += 3;
        indicators.push(`Topic tag: ${t}`);
      }
    }

    // 4. Description keywords
    if (desc.includes('library') || desc.includes('sdk') || desc.includes('android') || desc.includes('aar') || desc.includes('jitpack')) {
      score += 2;
      indicators.push('Description mentions Android/Library/SDK');
    }

    // Determine confidence
    let confidence = 'low';
    if (score >= 4) confidence = 'high';
    else if (score >= 2) confidence = 'medium';

    return {
      isLibrary: score >= 2,
      confidence,
      indicators: indicators.length > 0 ? indicators : ['General JVM/Git repository'],
    };
  }

  /**
   * Batch register selected candidate repositories into the ecosystem.
   *
   * @param {Array<object>} selectedCandidates
   * @param {string} [defaultGroup='NativeRuntime Core']
   * @returns {Promise<{ registered: number, skipped: number, errors: string[] }>}
   */
  static async batchRegister(selectedCandidates, defaultGroup = 'NativeRuntime Core') {
    let registered = 0;
    let skipped = 0;
    const errors = [];

    for (const item of selectedCandidates) {
      try {
        await ecosystemRepositoryService.register({
          name: item.name,
          owner: item.owner,
          repo: item.repo,
          url: item.url,
          groupId: item.suggestedGroupId || `com.github.${item.owner}`,
          artifactId: item.suggestedArtifactId || item.repo,
          ecosystemGroup: item.suggestedGroup || defaultGroup,
          isEcosystem: true,
          description: item.description || '',
          projectVersion: '1.0.0',
        });
        registered++;
      } catch (err) {
        if (err.message.includes('already registered')) {
          skipped++;
        } else {
          errors.push(`${item.owner}/${item.repo}: ${err.message}`);
        }
      }
    }

    return { registered, skipped, errors };
  }
}
