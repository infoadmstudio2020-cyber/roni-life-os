/**
 * D8PackagingGuard — prevents packaging duplicate classes and guarantees
 * that unhandled duplicate classes are never sent together into D8 dexing.
 */

export class D8PackagingGuard {
  /**
   * Pre-flight inspection for D8 dexer inputs.
   * Checks for duplicate classes across all input archives/files.
   * 
   * @param {Array<Object>} classEntries - list of { className, archiveName, path, ... }
   * @returns {Object} Pre-flight audit status ('PASSED' | 'BLOCKED') and collision details
   */
  static preflightCheck(classEntries = []) {
    const classCountMap = new Map();

    for (const entry of classEntries) {
      const cls = entry.className;
      if (!cls) continue;

      if (!classCountMap.has(cls)) {
        classCountMap.set(cls, []);
      }
      classCountMap.get(cls).push(entry);
    }

    const collisions = [];
    for (const [className, entries] of classCountMap.entries()) {
      if (entries.length > 1) {
        collisions.push({
          className,
          occurrences: entries.length,
          sources: entries.map(e => e.archiveName || e.source || 'unknown-input')
        });
      }
    }

    const isSafe = collisions.length === 0;

    return {
      status: isSafe ? 'PASSED' : 'BLOCKED',
      totalClassesChecked: classCountMap.size,
      duplicateClassesCount: collisions.length,
      isSafeForD8: isSafe,
      message: isSafe
        ? 'D8 Pre-flight verification passed: Zero duplicate classes found. Safe for dexing.'
        : `D8 Pre-flight BLOCKED: Found ${collisions.length} duplicate class(es). Sending these to D8 will cause "Type is defined multiple times" fatal dex failure.`,
      collisions
    };
  }

  /**
   * Sanitize and filter duplicate classes before passing to D8.
   * 
   * @param {Array<Object>} classEntries - input entries
   * @param {Object} [options]
   * @param {'PICK_FIRST'|'PICK_LATEST'|'EXCLUDE_ARCHIVE'} [options.strategy='PICK_FIRST']
   * @param {Array<string>} [options.excludeArchives] - archives to drop
   * @param {Array<RegExp|string>} [options.excludePatterns] - pattern strings or RegExps to exclude
   * @returns {Object} Sanitized class entries, dropped classes report, and certified D8 payload
   */
  static sanitizeForD8(classEntries = [], options = {}) {
    const strategy = options.strategy || 'PICK_FIRST';
    const excludeArchives = new Set(options.excludeArchives || []);
    const excludePatterns = options.excludePatterns || [];

    const seenClasses = new Map();
    const sanitizedList = [];
    const droppedList = [];

    for (const entry of classEntries) {
      const cls = entry.className;
      if (!cls) continue;

      const archiveName = entry.archiveName || entry.source || '';

      // Check if source archive is excluded
      if (excludeArchives.has(archiveName)) {
        droppedList.push({
          ...entry,
          reason: `Archive '${archiveName}' is excluded from D8 packaging`
        });
        continue;
      }

      // Check exclusion patterns
      const isPatternExcluded = excludePatterns.some(pattern => {
        if (typeof pattern === 'string') return cls.includes(pattern) || entry.path?.includes(pattern);
        if (pattern instanceof RegExp) return pattern.test(cls) || pattern.test(entry.path || '');
        return false;
      });

      if (isPatternExcluded) {
        droppedList.push({
          ...entry,
          reason: 'Excluded by packaging filter pattern'
        });
        continue;
      }

      // Handle duplicate class
      if (seenClasses.has(cls)) {
        const existing = seenClasses.get(cls);
        if (strategy === 'PICK_FIRST') {
          droppedList.push({
            ...entry,
            reason: `Duplicate of ${existing.archiveName} dropped by PICK_FIRST strategy`
          });
          continue;
        }
      }

      seenClasses.set(cls, entry);
      sanitizedList.push(entry);
    }

    // Re-verify sanitized output
    const postCheck = this.preflightCheck(sanitizedList);

    return {
      status: postCheck.status,
      originalCount: classEntries.length,
      sanitizedCount: sanitizedList.length,
      droppedCount: droppedList.length,
      strategyUsed: strategy,
      sanitizedClasses: sanitizedList,
      droppedClasses: droppedList,
      certifiedSafeForD8: postCheck.isSafeForD8,
      packagingOptionsGradle: this.generatePackagingOptions(droppedList)
    };
  }

  /**
   * Generate Android Gradle packaging / packagingOptions DSL block.
   */
  static generatePackagingOptions(droppedClasses = []) {
    const pickFirstSet = new Set();
    for (const d of droppedClasses) {
      if (d.className) {
        pickFirstSet.add(`            pickFirsts += '**/` + d.className.split('.').pop() + `.class'`);
      }
    }
    const samplePicks = Array.from(pickFirstSet).slice(0, 5);

    return `android {
    packaging {
        resources {
            excludes += '/META-INF/{AL2.0,LGPL2.1}'
            excludes += 'META-INF/DEPENDENCIES'
${samplePicks.length ? samplePicks.join('\n') : ''}
        }
    }
}`;
  }
}
