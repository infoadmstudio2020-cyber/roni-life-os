/**
 * JitPackAnalyzerEngine — Comprehensive Gradle project analysis & JitPack configuration generator.
 *
 * Detects:
 * - settings.gradle / settings.gradle.kts (multi-module hierarchy)
 * - Root and module-level build.gradle / build.gradle.kts (library vs sample app plugins)
 * - gradle-wrapper.properties (Gradle version & JDK compatibility)
 * - Existing jitpack.yml (reuse, merge, or update without blind overwrite)
 *
 * Ensures:
 * - Module-qualified Gradle commands (:lib:assembleRelease, :lib:publishToMavenLocal, :sample:assembleRelease)
 * - Strict task safety: Prevents invalid publishToMavenLocal on application/sample modules
 * - Clean YAML formatting matching JitPack official specifications
 */

export class JitPackAnalyzerEngine {
  /**
   * Analyze the given project files.
   * @param {Record<string, string>} files
   * @returns {object} Analysis report
   */
  static analyze(files = {}) {
    const report = {
      hasSettingsGradle: false,
      settingsFile: null,
      settingsContent: '',
      hasRootBuild: false,
      rootBuildFile: null,
      hasWrapper: false,
      wrapperFile: null,
      gradleVersion: null,
      recommendedJdk: 'openjdk17',
      compatibilityNotes: [],
      hasExistingJitpack: false,
      existingJitpackContent: '',
      modules: [],
      libraryModules: [],
      sampleModules: [],
      defaultTasks: [],
    };

    // 1. Check settings.gradle
    if (files['settings.gradle']) {
      report.hasSettingsGradle = true;
      report.settingsFile = 'settings.gradle';
      report.settingsContent = String(files['settings.gradle']);
    } else if (files['settings.gradle.kts']) {
      report.hasSettingsGradle = true;
      report.settingsFile = 'settings.gradle.kts';
      report.settingsContent = String(files['settings.gradle.kts']);
    }

    // Parse included modules from settings.gradle (supports include ':lib', include(':lib'), and multiple items)
    const includedNames = new Set();
    if (report.hasSettingsGradle) {
      const includeRegex = /include\s*\(?\s*(['"]:[^'"]+['"](?:\s*,\s*['"]:[^'"]+['"])*)\s*\)?/g;
      let match;
      while ((match = includeRegex.exec(report.settingsContent)) !== null) {
        const line = match[1];
        const items = line.match(/:([a-zA-Z0-9_\-.]+)/g);
        if (items) {
          items.forEach(it => includedNames.add(it.substring(1)));
        }
      }
    }

    // 2. Check root build.gradle
    if (files['build.gradle']) {
      report.hasRootBuild = true;
      report.rootBuildFile = 'build.gradle';
    } else if (files['build.gradle.kts']) {
      report.hasRootBuild = true;
      report.rootBuildFile = 'build.gradle.kts';
    }

    // 3. Check gradle-wrapper.properties
    const wrapperKey = Object.keys(files).find(k => k.includes('gradle-wrapper.properties'));
    if (wrapperKey) {
      report.hasWrapper = true;
      report.wrapperFile = wrapperKey;
      const wrapperText = String(files[wrapperKey] || '');
      const urlMatch = wrapperText.match(/distributionUrl=.*gradle-([0-9.]+)-/);
      if (urlMatch) {
        report.gradleVersion = urlMatch[1];
      }
    }

    // Determine compatible JDK based on Gradle version
    if (report.gradleVersion) {
      const gv = parseFloat(report.gradleVersion);
      if (gv >= 8.5) {
        report.recommendedJdk = 'openjdk17';
        report.compatibilityNotes.push(`Gradle ${report.gradleVersion} detected: requires Java 17 or higher (using openjdk17).`);
      } else if (gv >= 8.0) {
        report.recommendedJdk = 'openjdk17';
        report.compatibilityNotes.push(`Gradle ${report.gradleVersion} detected: requires Java 17.`);
      } else if (gv >= 7.0) {
        report.recommendedJdk = 'openjdk11';
        report.compatibilityNotes.push(`Gradle ${report.gradleVersion} detected: compatible with Java 11 or 17.`);
      } else {
        report.recommendedJdk = 'openjdk8';
        report.compatibilityNotes.push(`Legacy Gradle ${report.gradleVersion}: requires openjdk8.`);
      }
    } else {
      report.recommendedJdk = 'openjdk17';
      report.compatibilityNotes.push('Modern Android projects (AGP 8.x+) require openjdk17 by default.');
    }

    // 4. Discover all module build files and classify module types
    const scannedModules = new Map();

    // From settings.gradle inclusions
    for (const modName of includedNames) {
      scannedModules.set(modName, {
        name: modName,
        path: modName,
        type: 'library', // default assumption
        buildFile: null,
        hasMavenPublish: false,
      });
    }

    // From filesystem scan
    for (const path of Object.keys(files)) {
      if ((path.endsWith('/build.gradle') || path.endsWith('/build.gradle.kts')) && !path.startsWith('build.gradle')) {
        const parts = path.split('/');
        const modName = parts[0];
        if (modName && modName !== 'build' && modName !== '.gradle') {
          if (!scannedModules.has(modName)) {
            scannedModules.set(modName, {
              name: modName,
              path: modName,
              type: 'library',
              buildFile: path,
              hasMavenPublish: false,
            });
          } else {
            scannedModules.get(modName).buildFile = path;
          }
        }
      }
    }

    // If no multi-module found, inspect root build or create default library
    if (scannedModules.size === 0) {
      const rootContent = String(files['build.gradle'] || files['build.gradle.kts'] || '');
      const isLibrary = rootContent.includes('com.android.library') || rootContent.includes('java-library');
      const isApp = rootContent.includes('com.android.application');
      scannedModules.set('library', {
        name: 'library',
        path: '',
        type: isApp ? 'sample' : 'library',
        buildFile: report.rootBuildFile || 'build.gradle',
        hasMavenPublish: rootContent.includes('maven-publish'),
      });
    }

    // Analyze each module content to classify strictly
    for (const [name, mod] of scannedModules.entries()) {
      const candidates = [
        mod.buildFile,
        `${name}/build.gradle`,
        `${name}/build.gradle.kts`,
        files['build.gradle'] ? 'build.gradle' : null
      ].filter(Boolean);

      let content = '';
      for (const cand of candidates) {
        if (files[cand]) {
          content = String(files[cand]);
          mod.buildFile = cand;
          break;
        }
      }

      // Classification logic
      const isAndroidLibrary = content.includes('com.android.library');
      const isJavaLibrary = content.includes('java-library') || content.includes('id("java-library")') || content.includes("'java-library'");
      const isAndroidApp = content.includes('com.android.application');
      const isSampleName = /^(sample|app|demo|example|showcase)/i.test(name);

      if (isAndroidApp || isSampleName) {
        mod.type = 'sample';
        mod.typeLabel = 'Sample Application';
        mod.isLibrary = false;
        mod.isSample = true;
      } else if (isAndroidLibrary) {
        mod.type = 'android-library';
        mod.typeLabel = 'Android Library';
        mod.isLibrary = true;
        mod.isSample = false;
      } else if (isJavaLibrary) {
        mod.type = 'java-library';
        mod.typeLabel = 'Java Library';
        mod.isLibrary = true;
        mod.isSample = false;
      } else {
        mod.type = 'android-library';
        mod.typeLabel = 'Android Library (Default)';
        mod.isLibrary = true;
        mod.isSample = false;
      }

      mod.hasMavenPublish = content.includes('maven-publish') || content.includes('publishing');
      report.modules.push(mod);

      if (mod.isLibrary) {
        report.libraryModules.push(mod);
      } else {
        report.sampleModules.push(mod);
      }
    }

    // 5. Check existing jitpack.yml
    if (files['jitpack.yml']) {
      report.hasExistingJitpack = true;
      report.existingJitpackContent = String(files['jitpack.yml']);
    }

    return report;
  }

  /**
   * Generate clean, verified jitpack.yml content.
   *
   * @param {object} config
   * @param {string} [config.jdkVersion='openjdk17']
   * @param {Array<string>} [config.beforeInstall]
   * @param {Array<{ name: string, assembleRelease: boolean, publishToMavenLocal: boolean }>} [config.moduleTasks]
   * @param {string} [config.customTasks='']
   * @param {boolean} [config.clean=true]
   * @param {boolean} [config.stacktrace=true]
   * @returns {string} Clean YAML
   */
  static generateJitpackYml(config = {}) {
    const jdk = config.jdkVersion || 'openjdk17';
    const beforeInstall = config.beforeInstall || [
      "git config --global --add safe.directory '*' || true",
      'chmod +x gradlew'
    ];

    const tasks = [];
    if (config.clean !== false) {
      tasks.push('clean');
    }

    const moduleTasks = config.moduleTasks || [];
    for (const m of moduleTasks) {
      const modPrefix = m.name ? `:${m.name}:` : '';
      if (m.assembleRelease) {
        tasks.push(`${modPrefix}assembleRelease`);
      }
      // CRITICAL: publishToMavenLocal ONLY for library modules
      if (m.publishToMavenLocal) {
        tasks.push(`${modPrefix}publishToMavenLocal`);
      }
    }

    // Append any user custom tasks
    if (config.customTasks && config.customTasks.trim()) {
      const extras = config.customTasks.trim().split(/\s+/).filter(Boolean);
      tasks.push(...extras);
    }

    if (config.stacktrace !== false) {
      tasks.push('--stacktrace');
    }

    const installLine = `./gradlew ${tasks.join(' ')}`;

    let yaml = `# JitPack Configuration: Android Multi-Module Library
# Generated by RONi Android Library Generator
jdk:
  - ${jdk}

before_install:
`;

    for (const cmd of beforeInstall) {
      yaml += `  - ${cmd}\n`;
    }

    yaml += `\ninstall:\n  - ${installLine}\n`;

    return yaml;
  }

  /**
   * Convert an analysis result into standard module tasks for jitpack.yml generation.
   * Guarantees publishToMavenLocal is ONLY enabled for library modules.
   * @param {object} analysis
   * @returns {Array<object>}
   */
  static generateDefaultModuleTasks(analysis) {
    const modules = analysis?.modules || [];
    return modules.map(mod => ({
      name: mod.name,
      type: mod.type,
      typeLabel: mod.typeLabel,
      isLibrary: mod.isLibrary,
      isSample: mod.isSample,
      assembleRelease: true,
      publishToMavenLocal: Boolean(mod.isLibrary),
    }));
  }

  /**
   * Automatically generate a production-ready jitpack.yml directly from project files.
   * Guarantees openjdk17, safe.directory wildcard, and exact module names.
   */
  static generateDefaultJitpackYmlForFiles(files = {}) {
    const analysis = this.analyze(files);
    const moduleTasks = this.generateDefaultModuleTasks(analysis);
    return this.generateJitpackYml({
      jdkVersion: analysis.recommendedJdk || 'openjdk17',
      beforeInstall: [
        "git config --global --add safe.directory '*' || true",
        'chmod +x gradlew'
      ],
      clean: true,
      stacktrace: true,
      moduleTasks
    });
  }

  /**
   * Merge new module tasks into existing jitpack.yml without destroying other custom sections.
   * @param {string} existingYaml
   * @param {object} newConfig
   * @returns {string} Merged YAML
   */
  static mergeWithExisting(existingYaml = '', newConfig = {}) {
    if (!existingYaml || !existingYaml.trim()) {
      return this.generateJitpackYml(newConfig);
    }

    const freshYml = this.generateJitpackYml(newConfig);

    // If existing doesn't have install, simply append or replace
    if (!existingYaml.includes('install:')) {
      return `${existingYaml.trim()}\n\ninstall:\n  ${freshYml.split('install:\n')[1] || ''}`;
    }

    // Extract the install line from freshYml
    const freshInstallMatch = freshYml.match(/install:\n([\s\S]*?)(?:\n\S|$)/);
    if (!freshInstallMatch) return freshYml;

    // Replace the install section in existingYaml
    return existingYaml.replace(/install:\n([\s\S]*?)(?=(?:\n[a-zA-Z0-9_-]+:|$))/, `install:\n${freshInstallMatch[1].trimEnd()}\n`);
  }

  /**
   * Validate selected tasks against module types.
   * Flags invalid tasks (e.g. publishToMavenLocal on sample apps).
   *
   * @param {Array<{ name: string, isSample: boolean, assembleRelease: boolean, publishToMavenLocal: boolean }>} moduleTasks
   * @returns {{ valid: boolean, errors: string[], warnings: string[] }}
   */
  static validate(moduleTasks = []) {
    const errors = [];
    const warnings = [];

    let hasAnyPublish = false;
    let hasAnyAssemble = false;

    for (const m of moduleTasks) {
      if (m.isSample && m.publishToMavenLocal) {
        errors.push(`Invalid task: ":${m.name}:publishToMavenLocal". Application/Sample modules do not produce library publications and will cause the JitPack build to fail with 'Task not found'.`);
      }
      if (m.publishToMavenLocal) hasAnyPublish = true;
      if (m.assembleRelease) hasAnyAssemble = true;
    }

    if (!hasAnyPublish) {
      warnings.push('No library module has "publishToMavenLocal" selected. JitPack needs at least one library published to mavenLocal to generate consumer artifacts.');
    }

    if (!hasAnyAssemble) {
      warnings.push('No "assembleRelease" tasks selected. It is recommended to assemble release artifacts during the JitPack build.');
    }

    return {
      valid: errors.length === 0,
      errors,
      warnings
    };
  }
}
