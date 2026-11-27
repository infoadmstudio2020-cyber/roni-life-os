/**
 * VersionComparator — Production Semantic Version Comparison Engine.
 *
 * Capabilities:
 * - Semantic versions (major.minor.patch)
 * - Optional 'v' / 'V' prefix (v1.0.1 == 1.0.1)
 * - Pre-release identifiers (alpha, beta, rc, snapshot) with proper precedence
 * - Build metadata (+build123)
 * - Handles missing, null, or invalid version values without throwing
 * - Reliable integer/semver comparison without alphabetical string sorting
 * - Independent multi-source status determination:
 *   * Latest
 *   * Update Available
 *   * Project Version Ahead
 *   * Release Missing
 *   * JitPack Build Failed
 *   * JitPack Not Verified
 *   * Version Mismatch
 *   * Unknown
 */

export class VersionComparator {

  /**
   * Parse a version string into structured SemVer tokens.
   * @param {string|number|null|undefined} versionStr
   * @returns {{ raw: string, clean: string, major: number, minor: number, patch: number, prerelease: string|null, prereleaseTokens: (string|number)[], build: string|null, isValid: boolean }}
   */
  static parse(versionStr) {
    if (versionStr === null || versionStr === undefined) {
      return {
        raw: '',
        clean: '',
        major: 0,
        minor: 0,
        patch: 0,
        prerelease: null,
        prereleaseTokens: [],
        build: null,
        isValid: false,
      };
    }

    const raw = String(versionStr).trim();
    if (!raw) {
      return {
        raw: '',
        clean: '',
        major: 0,
        minor: 0,
        patch: 0,
        prerelease: null,
        prereleaseTokens: [],
        build: null,
        isValid: false,
      };
    }

    // Strip leading 'v' or 'V'
    let clean = raw.replace(/^v/i, '').trim();

    // Separate build metadata (+...)
    let build = null;
    const plusIdx = clean.indexOf('+');
    if (plusIdx !== -1) {
      build = clean.slice(plusIdx + 1);
      clean = clean.slice(0, plusIdx);
    }

    // Separate pre-release (-...)
    let prerelease = null;
    let prereleaseTokens = [];
    const hyphenIdx = clean.indexOf('-');
    if (hyphenIdx !== -1) {
      prerelease = clean.slice(hyphenIdx + 1);
      clean = clean.slice(0, hyphenIdx);
      prereleaseTokens = prerelease.split('.').map(tok => {
        return /^\d+$/.test(tok) ? parseInt(tok, 10) : tok.toLowerCase();
      });
    }

    // Split major.minor.patch
    const parts = clean.split('.');
    const majorStr = parts[0] ?? '';
    const minorStr = parts[1] ?? '0';
    const patchStr = parts[2] ?? '0';

    const isNum = (s) => /^\d+$/.test(s);
    const isValid = parts.length >= 1 && parts.length <= 4 && isNum(majorStr);

    const major = isNum(majorStr) ? parseInt(majorStr, 10) : 0;
    const minor = isNum(minorStr) ? parseInt(minorStr, 10) : 0;
    const patch = isNum(patchStr) ? parseInt(patchStr, 10) : 0;

    return {
      raw,
      clean,
      major,
      minor,
      patch,
      prerelease,
      prereleaseTokens,
      build,
      isValid,
    };
  }

  /**
   * Compare two versions.
   * Returns:
   *   1 if v1 > v2
   *  -1 if v1 < v2
   *   0 if v1 == v2
   *
   * @param {string} v1
   * @param {string} v2
   * @returns {number}
   */
  static compare(v1, v2) {
    const p1 = this.parse(v1);
    const p2 = this.parse(v2);

    // If both invalid, compare raw strings safely
    if (!p1.isValid && !p2.isValid) {
      return p1.raw.localeCompare(p2.raw);
    }
    if (!p1.isValid) return -1;
    if (!p2.isValid) return 1;

    // Compare Major
    if (p1.major !== p2.major) return p1.major > p2.major ? 1 : -1;
    // Compare Minor
    if (p1.minor !== p2.minor) return p1.minor > p2.minor ? 1 : -1;
    // Compare Patch
    if (p1.patch !== p2.patch) return p1.patch > p2.patch ? 1 : -1;

    // Pre-release precedence:
    // A normal version has higher precedence than a pre-release version.
    // e.g. 1.0.0 > 1.0.0-alpha
    if (!p1.prerelease && p2.prerelease) return 1;
    if (p1.prerelease && !p2.prerelease) return -1;
    if (!p1.prerelease && !p2.prerelease) return 0;

    // Both have pre-releases: compare tokens
    const t1 = p1.prereleaseTokens;
    const t2 = p2.prereleaseTokens;
    const maxLen = Math.max(t1.length, t2.length);

    for (let i = 0; i < maxLen; i++) {
      const a = t1[i];
      const b = t2[i];

      if (a === undefined) return -1; // shorter pre-release has lower precedence
      if (b === undefined) return 1;

      if (typeof a === 'number' && typeof b === 'number') {
        if (a !== b) return a > b ? 1 : -1;
      } else if (typeof a === 'string' && typeof b === 'string') {
        const cmp = a.localeCompare(b);
        if (cmp !== 0) return cmp > 0 ? 1 : -1;
      } else {
        // Numeric identifiers always have lower precedence than non-numeric identifiers
        return typeof a === 'number' ? -1 : 1;
      }
    }

    return 0;
  }

  static isEqual(v1, v2) {
    return this.compare(v1, v2) === 0;
  }

  static isGreater(v1, v2) {
    return this.compare(v1, v2) > 0;
  }

  static isLess(v1, v2) {
    return this.compare(v1, v2) < 0;
  }

  /**
   * Sort a list of version strings or objects containing version keys.
   * @param {Array<string|object>} list
   * @param {'asc'|'desc'} direction
   * @param {string} [key] property name if list contains objects
   * @returns {Array} sorted copy
   */
  static sortVersions(list, direction = 'desc', key = null) {
    const copy = [...list];
    copy.sort((a, b) => {
      const vA = key ? a?.[key] : a;
      const vB = key ? b?.[key] : b;
      const res = this.compare(vA, vB);
      return direction === 'asc' ? res : -res;
    });
    return copy;
  }

  /**
   * Determine meaningful status for a library comparing its multi-source versions:
   * 1. Project Version (from source configuration)
   * 2. GitHub Release (published release)
   * 3. Git Tag (git tag)
   * 4. JitPack Build Status and version
   *
   * Returned statuses:
   * - 'Latest': all available sources match and JitPack build is successful/ok
   * - 'Update Available': latest GitHub release or tag is newer than project version
   * - 'Project Version Ahead': local project version is newer than published release/tag
   * - 'Release Missing': Git tag exists but no GitHub release has been published for it
   * - 'JitPack Build Failed': JitPack build failed for this version
   * - 'JitPack Not Verified': JitPack has not verified or built this version yet
   * - 'Version Mismatch': discrepancy between project version, release, tag, or JitPack
   * - 'Unknown': insufficient or unparseable version metadata
   *
   * @param {object} params
   * @param {string} [params.projectVersion]
   * @param {string} [params.githubRelease]
   * @param {string} [params.gitTag]
   * @param {string} [params.jitpackVersion]
   * @param {string} [params.jitpackStatus] 'ok' | 'error' | 'building' | 'not_found' | 'unknown'
   * @returns {{ status: string, code: string, severity: 'success'|'info'|'warning'|'error'|'muted', description: string, mismatchDetails: string[] }}
   */
  static determineStatus({ projectVersion, githubRelease, gitTag, jitpackVersion, jitpackStatus }) {
    const pProj = this.parse(projectVersion);
    const pRel  = this.parse(githubRelease);
    const pTag  = this.parse(gitTag);

    const hasProj = pProj.isValid;
    const hasRel  = pRel.isValid;
    const hasTag  = pTag.isValid;

    const mismatchDetails = [];

    if (!hasProj && !hasRel && !hasTag) {
      return {
        status: 'Unknown',
        code: 'unknown',
        severity: 'muted',
        description: 'No valid version metadata found across project, releases, or git tags.',
        mismatchDetails: ['No version data available']
      };
    }

    // 1. Check JitPack failure first
    const jpStat = String(jitpackStatus || '').toLowerCase();
    if (jpStat === 'error' || jpStat === 'failed') {
      return {
        status: 'JitPack Build Failed',
        code: 'jitpack_failed',
        severity: 'error',
        description: `JitPack build failed for version ${jitpackVersion || gitTag || projectVersion || 'unknown'}. Check JitPack build log for details.`,
        mismatchDetails: ['JitPack build error detected']
      };
    }

    // 2. Check if Git Tag exists but Release is missing
    if (hasTag && !hasRel) {
      mismatchDetails.push(`Git tag ${pTag.raw} exists but no GitHub Release was published`);
      return {
        status: 'Release Missing',
        code: 'release_missing',
        severity: 'warning',
        description: `Git tag ${pTag.raw} exists on remote repository, but no GitHub Release has been created.`,
        mismatchDetails
      };
    }

    // 3. Compare GitHub Release and Project Version
    if (hasProj && hasRel) {
      const cmpProjRel = this.compare(pProj.raw, pRel.raw);
      if (cmpProjRel < 0) {
        // GitHub release is newer than project version
        mismatchDetails.push(`GitHub Release (${pRel.raw}) is newer than Project Version (${pProj.raw})`);
        return {
          status: 'Update Available',
          code: 'update_available',
          severity: 'info',
          description: `A newer GitHub Release (${pRel.raw}) is available. Current project version is ${pProj.raw}.`,
          mismatchDetails
        };
      }
      if (cmpProjRel > 0) {
        // Local project version ahead of GitHub release
        mismatchDetails.push(`Project Version (${pProj.raw}) is ahead of GitHub Release (${pRel.raw})`);
        return {
          status: 'Project Version Ahead',
          code: 'project_ahead',
          severity: 'warning',
          description: `Project source configuration (${pProj.raw}) is ahead of the latest published GitHub release (${pRel.raw}). Ready for release.`,
          mismatchDetails
        };
      }
    }

    // 4. Compare Git Tag and Project Version if no release
    if (hasProj && hasTag && !hasRel) {
      const cmpProjTag = this.compare(pProj.raw, pTag.raw);
      if (cmpProjTag < 0) {
        return {
          status: 'Update Available',
          code: 'update_available',
          severity: 'info',
          description: `Git Tag (${pTag.raw}) is ahead of project version (${pProj.raw}).`,
          mismatchDetails: [`Git tag ${pTag.raw} is newer than project version ${pProj.raw}`]
        };
      }
      if (cmpProjTag > 0) {
        return {
          status: 'Project Version Ahead',
          code: 'project_ahead',
          severity: 'warning',
          description: `Project version (${pProj.raw}) is ahead of latest Git tag (${pTag.raw}).`,
          mismatchDetails: [`Project version ${pProj.raw} is ahead of tag ${pTag.raw}`]
        };
      }
    }

    // 5. Check tag vs release discrepancy
    if (hasRel && hasTag && !this.isEqual(pRel.raw, pTag.raw)) {
      mismatchDetails.push(`Release tag (${pRel.raw}) differs from latest Git tag (${pTag.raw})`);
      return {
        status: 'Version Mismatch',
        code: 'version_mismatch',
        severity: 'warning',
        description: `Latest GitHub Release is ${pRel.raw} but latest Git Tag is ${pTag.raw}.`,
        mismatchDetails
      };
    }

    // 6. Check JitPack verification
    if (jpStat === 'not_found' || jpStat === 'unknown' || !jpStat) {
      return {
        status: 'JitPack Not Verified',
        code: 'jitpack_not_verified',
        severity: 'muted',
        description: `Version ${pRel.raw || pProj.raw} is published, but JitPack build status has not been verified yet.`,
        mismatchDetails: ['JitPack build status unverified']
      };
    }

    // 7. If all match and JitPack is OK
    if (jpStat === 'ok' || jpStat === 'success') {
      return {
        status: 'Latest',
        code: 'latest',
        severity: 'success',
        description: `All versions in sync (${pProj.raw || pRel.raw}) and verified on JitPack.`,
        mismatchDetails: []
      };
    }

    return {
      status: 'Latest',
      code: 'latest',
      severity: 'success',
      description: `Versions synchronized (${pProj.raw || pRel.raw}).`,
      mismatchDetails: []
    };
  }
}
