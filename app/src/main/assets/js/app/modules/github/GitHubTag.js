/**
 * GitHubTag — create, get, list, and delete version tags.
 *
 * Production-safe behavior:
 * - Creates annotated version tags.
 * - Can resolve an existing tag to its actual commit SHA.
 * - Supports lightweight and annotated tags.
 * - Never overwrites an existing tag.
 * - Existing tag can safely be reused when it points to the same commit.
 *
 * Compatibility:
 * - No JavaScript private fields (#...)
 * - Browser-safe ES module
 * - Runtime token handled by GitHubApi
 */

import { githubApi } from './GitHubApi.js';

export class GitHubTag {

  /**
   * Create an annotated tag pointing to a commit.
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} version — e.g. '1.2.3'
   * @param {string} sha — 40-character commit SHA
   * @param {string} message — annotation message
   * @returns {Promise<string>} normalized tag name
   */
  async create(
    owner,
    repo,
    version,
    sha,
    message = ''
  ) {

    const tag =
      this._normalize(version);

    if (!this._valid(tag)) {
      throw new Error(
        `Invalid semver: ${version}`
      );
    }

    if (!this._validSha(sha)) {
      throw new Error(
        `Invalid commit SHA for tag ${tag}: ${sha}`
      );
    }

    /*
     * Create annotated tag object.
     *
     * Endpoint:
     * POST /repos/{owner}/{repo}/git/tags
     */
    const tagObj =
      await githubApi.createTagObject(
        owner,
        repo,
        tag,
        message || `Release ${tag}`,
        sha
      );

    if (!tagObj?.sha) {
      throw new Error(
        `GitHub did not return a tag object SHA for ${tag}`
      );
    }

    if (!this._validSha(tagObj.sha)) {
      throw new Error(
        `GitHub returned an invalid tag object SHA for ${tag}`
      );
    }

    /*
     * Create the actual Git reference.
     *
     * refs/tags/v1.2.3
     */
    await githubApi.createTagRef(
      owner,
      repo,
      tag,
      tagObj.sha
    );

    return tag;
  }

  /**
   * Get an existing tag and resolve its actual commit target.
   *
   * Lightweight tag:
   *
   * refs/tags/v1.0.0
   *        ↓
   * commit SHA
   *
   * Annotated tag:
   *
   * refs/tags/v1.0.0
   *        ↓
   * tag object SHA
   *        ↓
   * target commit SHA
   *
   * IMPORTANT:
   * Do NOT use githubApi.getRef() here.
   *
   * getRef() is specifically for:
   *
   *   refs/heads/{branch}
   *
   * This method requires:
   *
   *   refs/tags/{tag}
   *
   * Therefore it uses:
   *
   *   githubApi.getTagRef()
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} version
   * @returns {Promise<object>}
   */
  async get(
    owner,
    repo,
    version
  ) {

    const tag =
      this._normalize(version);

    if (!this._valid(tag)) {
      throw new Error(
        `Invalid semver: ${version}`
      );
    }

    /*
     * Read:
     *
     * GET /repos/{owner}/{repo}/git/ref/tags/{tag}
     *
     * This is intentionally NOT getRef().
     */
    const ref =
      await githubApi.getTagRef(
        owner,
        repo,
        tag
      );

    const refObject =
      ref?.object;

    if (!refObject?.sha) {
      throw new Error(
        `GitHub returned an invalid tag reference for ${tag}`
      );
    }

    const refSha =
      String(
        refObject.sha
      ).trim();

    const refType =
      String(
        refObject.type || ''
      ).trim();

    /*
     * Lightweight tag.
     *
     * GitHub returns:
     *
     * object.type = "commit"
     * object.sha  = commit SHA
     */
    if (
      refType === 'commit' ||
      !refType
    ) {

      if (this._validSha(refSha)) {

        return {
          name:
            tag,

          ref:
            `refs/tags/${tag}`,

          sha:
            refSha,

          commit: {
            sha:
              refSha,
          },

          object: {
            sha:
              refSha,

            type:
              'commit',
          },

          annotated:
            false,
        };
      }
    }

    /*
     * Annotated tag.
     *
     * GitHub returns:
     *
     * object.type = "tag"
     * object.sha  = tag object SHA
     *
     * Resolve the tag object to its actual target.
     *
     * GET /repos/{owner}/{repo}/git/tags/{tag-object-sha}
     */
    if (!this._validSha(refSha)) {
      throw new Error(
        `GitHub returned an invalid tag object SHA for ${tag}`
      );
    }

    const tagObject =
      await githubApi.get(
        `/repos/${encodeURIComponent(owner)}` +
        `/${encodeURIComponent(repo)}` +
        `/git/tags/${encodeURIComponent(refSha)}`
      );

    if (!tagObject?.object?.sha) {
      throw new Error(
        `Unable to resolve target commit for tag ${tag}`
      );
    }

    const targetSha =
      String(
        tagObject.object.sha
      ).trim();

    const targetType =
      String(
        tagObject.object.type || ''
      ).trim();

    if (!this._validSha(targetSha)) {
      throw new Error(
        `GitHub returned an invalid target SHA for tag ${tag}`
      );
    }

    /*
     * A version release tag must ultimately resolve to
     * a commit.
     */
    if (
      targetType &&
      targetType !== 'commit'
    ) {
      throw new Error(
        `Tag ${tag} points to unsupported Git object type: ${targetType}`
      );
    }

    return {
      name:
        tag,

      ref:
        `refs/tags/${tag}`,

      sha:
        targetSha,

      commit: {
        sha:
          targetSha,
      },

      object: {
        sha:
          targetSha,

        type:
          'commit',
      },

      tagObject: {
        sha:
          refSha,

        type:
          refType || 'tag',
      },

      annotated:
        true,
    };
  }

  /**
   * List all repository tags.
   *
   * @param {string} owner
   * @param {string} repo
   * @returns {Promise<Array>}
   */
  async list(
    owner,
    repo
  ) {

    return githubApi.listTags(
      owner,
      repo
    );
  }

  /**
   * Delete a version tag.
   *
   * This removes the tag reference only.
   *
   * It does not delete the commit or tag object itself.
   *
   * @param {string} owner
   * @param {string} repo
   * @param {string} version
   */
  async delete(
    owner,
    repo,
    version
  ) {

    const tag =
      this._normalize(version);

    if (!this._valid(tag)) {
      throw new Error(
        `Invalid semver: ${version}`
      );
    }

    await githubApi.deleteTagRef(
      owner,
      repo,
      tag
    );
  }

  /**
   * Increment a semantic version.
   *
   * Examples:
   *
   * increment('1.2.3', 'patch') -> '1.2.4'
   * increment('1.2.3', 'minor') -> '1.3.0'
   * increment('1.2.3', 'major') -> '2.0.0'
   *
   * @param {string} version
   * @param {'major'|'minor'|'patch'} type
   * @returns {string}
   */
  increment(
    version,
    type = 'patch'
  ) {

    const clean =
      String(
        version || ''
      )
        .trim()
        .replace(/^v/, '');

    const parts =
      clean.split('.');

    if (parts.length !== 3) {
      throw new Error(
        `Invalid semver: ${version}`
      );
    }

    const major =
      Number(parts[0]);

    const minor =
      Number(parts[1]);

    const patch =
      Number(parts[2]);

    if (
      !Number.isInteger(major) ||
      !Number.isInteger(minor) ||
      !Number.isInteger(patch) ||
      major < 0 ||
      minor < 0 ||
      patch < 0
    ) {
      throw new Error(
        `Invalid semver: ${version}`
      );
    }

    switch (type) {

      case 'major':
        return `${major + 1}.0.0`;

      case 'minor':
        return `${major}.${minor + 1}.0`;

      case 'patch':
      default:
        return `${major}.${minor}.${patch + 1}`;
    }
  }

  /**
   * Normalize version into Git tag format.
   *
   * 1.2.3   -> v1.2.3
   * v1.2.3  -> v1.2.3
   */
  _normalize(v) {

    const value =
      String(
        v || ''
      ).trim();

    return value.startsWith('v')
      ? value
      : `v${value}`;
  }

  /**
   * Validate normalized semver tag.
   *
   * Only exact:
   *
   * vMAJOR.MINOR.PATCH
   */
  _valid(v) {

    return /^v\d+\.\d+\.\d+$/.test(
      v
    );
  }

  /**
   * Validate a full 40-character Git SHA.
   */
  _validSha(value) {

    return (
      typeof value === 'string' &&
      /^[a-f0-9]{40}$/i.test(
        value.trim()
      )
    );
  }
}

export const githubTag =
  new GitHubTag();