/**
 * KotlinRuntimeAlignmentService — audits and aligns kotlin-stdlib, kotlin-stdlib-jdk7,
 * and kotlin-stdlib-jdk8 versions, eliminating Kotlin 1.8+ duplicate class merge conflicts.
 */

export class KotlinRuntimeAlignmentService {
  static KOTLIN_STDLIB_GROUP = 'org.jetbrains.kotlin';
  static MERGED_THRESHOLD_VERSION = '1.8.0';

  /**
   * Known standard library artifacts in Kotlin runtime
   */
  static KOTLIN_STDLIB_MODULES = [
    'kotlin-stdlib',
    'kotlin-stdlib-jdk7',
    'kotlin-stdlib-jdk8',
    'kotlin-stdlib-common'
  ];

  /**
   * Audit dependencies list or project files for Kotlin stdlib version mismatches and duplicate class hazards.
   * 
   * @param {Array<string|Object>} dependencies - array of Maven coordinates or dependency lines
   * @param {string} [targetKotlinVersion='1.9.22'] - Target project Kotlin version
   * @returns {Object} Audit report with detected versions, hazard analysis, and alignment fixes
   */
  static auditKotlinRuntime(dependencies = [], targetKotlinVersion = '1.9.22') {
    const detectedModules = [];
    const versionsSet = new Set();

    for (const dep of dependencies) {
      const raw = typeof dep === 'string' ? dep : dep.raw || '';
      const match = raw.match(/(?:org\.jetbrains\.kotlin:)?(kotlin-stdlib(?:-[a-z0-9]+)?):([0-9a-zA-Z._-]+)/);
      if (match) {
        const moduleName = match[1];
        const version = match[2];
        if (this.KOTLIN_STDLIB_MODULES.includes(moduleName)) {
          detectedModules.push({
            group: this.KOTLIN_STDLIB_GROUP,
            module: moduleName,
            version,
            raw
          });
          versionsSet.add(version);
        }
      }
    }

    const versions = Array.from(versionsSet);
    const hasMultipleVersions = versions.length > 1;

    // Check if any module is >= 1.8.0 while jdk7/jdk8 has an older version (< 1.8.0)
    let hasMergedCollisionRisk = false;
    let collisionExplanation = '';

    const stdlibBase = detectedModules.find(m => m.module === 'kotlin-stdlib');
    const stdlibJdk7 = detectedModules.find(m => m.module === 'kotlin-stdlib-jdk7');
    const stdlibJdk8 = detectedModules.find(m => m.module === 'kotlin-stdlib-jdk8');

    const baseVersion = stdlibBase?.version || targetKotlinVersion;
    const isBaseMerged = this.isVersionAtLeast(baseVersion, this.MERGED_THRESHOLD_VERSION);

    if (isBaseMerged) {
      if (stdlibJdk7 && !this.isVersionAtLeast(stdlibJdk7.version, this.MERGED_THRESHOLD_VERSION)) {
        hasMergedCollisionRisk = true;
        collisionExplanation = `kotlin-stdlib:${baseVersion} contains merged classes, but kotlin-stdlib-jdk7:${stdlibJdk7.version} brings duplicate classes (e.g. CollectionsJDK7Kt)`;
      } else if (stdlibJdk8 && !this.isVersionAtLeast(stdlibJdk8.version, this.MERGED_THRESHOLD_VERSION)) {
        hasMergedCollisionRisk = true;
        collisionExplanation = `kotlin-stdlib:${baseVersion} contains merged classes, but kotlin-stdlib-jdk8:${stdlibJdk8.version} brings duplicate classes (e.g. CollectionsJDK8Kt)`;
      } else if (hasMultipleVersions) {
        hasMergedCollisionRisk = true;
        collisionExplanation = `Mixed Kotlin stdlib versions detected across dependencies (${versions.join(', ')}). In Kotlin 1.8+, versions must be aligned.`;
      }
    }

    const alignedVersion = targetKotlinVersion || baseVersion;

    return {
      totalKotlinModulesFound: detectedModules.length,
      detectedModules,
      versions,
      hasMultipleVersions,
      hasMergedCollisionRisk,
      collisionExplanation,
      recommendedTargetVersion: alignedVersion,
      gradleAlignmentPatch: this.generateGradleAlignmentPatch(alignedVersion),
      gradleConstraintsPatch: this.generateDependencyConstraintsPatch(alignedVersion),
      gradleExclusionPatch: this.generateExclusionPatch()
    };
  }

  /**
   * Helper to compare semantic versions >= threshold
   */
  static isVersionAtLeast(version, threshold) {
    if (!version) return false;
    const cleanV = version.replace(/^v/i, '').split(/[.-]/).map(Number);
    const cleanT = threshold.replace(/^v/i, '').split(/[.-]/).map(Number);

    for (let i = 0; i < Math.max(cleanV.length, cleanT.length); i++) {
      const segV = cleanV[i] || 0;
      const segT = cleanT[i] || 0;
      if (segV > segT) return true;
      if (segV < segT) return false;
    }
    return true; // equal
  }

  /**
   * Generate Gradle resolutionStrategy to align all kotlin-stdlib modules.
   */
  static generateGradleAlignmentPatch(kotlinVersion = '1.9.22') {
    return `// Align Kotlin standard library runtime across all configurations
configurations.all {
    resolutionStrategy.eachDependency { DependencyResolveDetails details ->
        if (details.requested.group == 'org.jetbrains.kotlin' &&
            details.requested.name.startsWith('kotlin-stdlib')) {
            details.useVersion('${kotlinVersion}')
            details.because('Align Kotlin stdlib versions to prevent Kotlin 1.8+ duplicate classes in D8')
        }
    }
}`;
  }

  /**
   * Generate Gradle dependency constraints block.
   */
  static generateDependencyConstraintsPatch(kotlinVersion = '1.9.22') {
    return `dependencies {
    // Kotlin runtime version alignment constraints
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:${kotlinVersion}") {
            because("Enforce aligned Kotlin stdlib version")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${kotlinVersion}") {
            because("Prevent older unmerged kotlin-stdlib-jdk7 duplicates")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}") {
            because("Prevent older unmerged kotlin-stdlib-jdk8 duplicates")
        }
    }
}`;
  }

  /**
   * Generate exclusion rules for older projects using Kotlin 1.8+
   */
  static generateExclusionPatch() {
    return `configurations.all {
    // For Kotlin 1.8+, exclude legacy separate jdk7/jdk8 modules
    exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk7'
    exclude group: 'org.jetbrains.kotlin', module: 'kotlin-stdlib-jdk8'
}`;
  }
}
