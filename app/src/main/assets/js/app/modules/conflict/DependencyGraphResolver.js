/**
 * DependencyGraphResolver — resolves Maven coordinates and versions,
 * eliminates duplicate artifact dependencies across modules and configurations.
 */

export class DependencyGraphResolver {
  /**
   * Parse a Maven coordinate string or Gradle dependency string into structured object.
   * e.g. "com.google.guava:guava:31.0-android"
   * e.g. "implementation 'com.google.guava:guava:31.0-android'"
   * e.g. 'api("com.squareup.okhttp3:okhttp:4.12.0")'
   */
  static parseCoordinate(raw) {
    if (!raw || typeof raw !== 'string') return null;
    const trimmed = raw.trim();

    // Check configuration prefix if present
    const configMatch = trimmed.match(/^(implementation|api|compileOnly|runtimeOnly|testImplementation|androidTestImplementation)\s*[\(\s]?['"]([^'"]+)['"]\)?/);
    let configuration = null;
    let coordinate = trimmed;

    if (configMatch) {
      configuration = configMatch[1];
      coordinate = configMatch[2];
    } else {
      // Clean quotes
      coordinate = coordinate.replace(/^[('"]+|[)'"]+$/g, '');
    }

    const parts = coordinate.split(':');
    if (parts.length < 2) return null;

    const group = parts[0].trim();
    const artifact = parts[1].trim();
    const version = parts[2]?.trim() || '';
    const classifier = parts[3]?.trim() || null;

    if (!group || !artifact) return null;

    return {
      raw: trimmed,
      group,
      artifact,
      version,
      classifier,
      moduleKey: `${group}:${artifact}`,
      configuration: configuration || 'implementation'
    };
  }

  /**
   * Compare two semantic or build version strings.
   * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal.
   */
  static compareVersions(v1, v2) {
    if (!v1 && !v2) return 0;
    if (!v1) return -1;
    if (!v2) return 1;
    if (v1 === v2) return 0;

    // Normalize versions (strip 'v' prefix, qualifiers)
    const clean1 = v1.replace(/^v/i, '');
    const clean2 = v2.replace(/^v/i, '');

    const p1 = clean1.split(/[.-]/).map(x => isNaN(Number(x)) ? x : Number(x));
    const p2 = clean2.split(/[.-]/).map(x => isNaN(Number(x)) ? x : Number(x));

    const maxLen = Math.max(p1.length, p2.length);
    for (let i = 0; i < maxLen; i++) {
      const seg1 = p1[i] ?? 0;
      const seg2 = p2[i] ?? 0;

      if (typeof seg1 === 'number' && typeof seg2 === 'number') {
        if (seg1 > seg2) return 1;
        if (seg1 < seg2) return -1;
      } else {
        const s1 = String(seg1);
        const s2 = String(seg2);
        const cmp = s1.localeCompare(s2);
        if (cmp !== 0) return cmp > 0 ? 1 : -1;
      }
    }
    return 0;
  }

  /**
   * Resolve and deduplicate a list of dependencies.
   * 
   * @param {Array<string|Object>} dependencies
   * @param {Object} [options]
   * @param {'HIGHEST_VERSION'|'FIRST_WINS'|'CUSTOM_FORCE'} [options.strategy='HIGHEST_VERSION']
   * @param {Record<string, string>} [options.forceVersions] - moduleKey -> forcedVersion
   * @returns {Object} Resolution result with resolved list, eliminated duplicates, and patch instructions
   */
  static resolveDependencies(dependencies = [], options = {}) {
    const strategy = options.strategy || 'HIGHEST_VERSION';
    const forceVersions = options.forceVersions || {};

    const parsedList = [];
    for (const dep of dependencies) {
      const parsed = typeof dep === 'string' ? this.parseCoordinate(dep) : dep;
      if (parsed) parsedList.push(parsed);
    }

    // Group by moduleKey (group:artifact)
    const grouped = new Map();
    for (const dep of parsedList) {
      if (!grouped.has(dep.moduleKey)) {
        grouped.set(dep.moduleKey, []);
      }
      grouped.get(dep.moduleKey).push(dep);
    }

    const resolved = [];
    const duplicatesRemoved = [];
    const conflicts = [];

    for (const [moduleKey, list] of grouped.entries()) {
      if (list.length === 1) {
        resolved.push(list[0]);
        continue;
      }

      // Check distinct versions
      const distinctVersions = Array.from(new Set(list.map(d => d.version).filter(Boolean)));
      const hasConflict = distinctVersions.length > 1;

      let highestItem = list[0];
      let winningDep = null;

      if (forceVersions[moduleKey]) {
        // Forced version from user
        const forcedVer = forceVersions[moduleKey];
        winningDep = {
          ...list[0],
          version: forcedVer,
          raw: `${list[0].configuration} '${moduleKey}:${forcedVer}'`,
          resolvedReason: `Forced by rule to ${forcedVer}`
        };
      } else if (strategy === 'FIRST_WINS') {
        highestItem = list[0];
        winningDep = { ...list[0], resolvedReason: 'First occurrence preserved' };
      } else {
        // HIGHEST_VERSION (Gradle default)
        for (let i = 1; i < list.length; i++) {
          if (this.compareVersions(list[i].version, highestItem.version) > 0) {
            highestItem = list[i];
          }
        }
        winningDep = {
          ...highestItem,
          resolvedReason: distinctVersions.length > 1 
            ? `Upgraded to highest version ${highestItem.version}` 
            : 'Duplicate redundant declaration removed'
        };
      }

      resolved.push(winningDep);

      // Collect dropped duplicates
      for (const item of list) {
        if (item !== highestItem) {
          duplicatesRemoved.push({
            moduleKey,
            droppedCoordinate: `${item.moduleKey}:${item.version}`,
            droppedConfiguration: item.configuration,
            winningCoordinate: `${winningDep.moduleKey}:${winningDep.version}`,
            reason: winningDep.resolvedReason
          });
        }
      }

      if (hasConflict) {
        conflicts.push({
          moduleKey,
          versionsFound: distinctVersions,
          resolvedVersion: winningDep.version,
          instancesCount: list.length
        });
      }
    }

    return {
      inputCount: parsedList.length,
      resolvedCount: resolved.length,
      duplicatesRemovedCount: duplicatesRemoved.length,
      conflictsCount: conflicts.length,
      resolved,
      duplicatesRemoved,
      conflicts,
      gradleResolutionScript: this.generateResolutionStrategyBlock(conflicts)
    };
  }

  /**
   * Generate Gradle resolutionStrategy block to enforce resolved versions.
   */
  static generateResolutionStrategyBlock(conflicts = []) {
    if (!conflicts.length) return '';
    const forceLines = conflicts.map(c => `            force '${c.moduleKey}:${c.resolvedVersion}'`).join('\n');
    return `configurations.all {
    resolutionStrategy {
        // Automated conflict resolution by RONi LibGen
${forceLines}
        failOnVersionConflict()
    }
}`;
  }
}
