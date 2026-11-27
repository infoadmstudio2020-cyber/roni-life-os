/**
 * AndroidProjectWorkflowAnalyzer.js
 *
 * Deeply analyzes actual Android project files to extract:
 * - Android Gradle Plugin (AGP) version
 * - Gradle wrapper version & wrapper properties
 * - Java/JDK compatibility requirement (8, 11, 17, 21)
 * - compileSdk, targetSdk, minSdk, buildToolsVersion
 * - Project Type: Library, Application, or Multi-Module (e.g. Library + App Sample)
 * - Detected module names (e.g. :app, :library, :core)
 * - Build variants and Gradle tasks (assembleRelease, bundleRelease, test, lint, check)
 * - Dependencies & Plugins (Kotlin, Compose, Hilt, Maven-Publish, JitPack)
 * - Artifact output requirements (AAR for Library, APK/AAB for App, or both)
 */

export class AndroidProjectWorkflowAnalyzer {
  /**
   * Analyze all project files and produce a comprehensive workflow configuration profile.
   * @param {Object.<string, string|Uint8Array>} files - flat path -> content map
   * @param {object} [extraMeta] - optional existing project metadata
   * @returns {object} Comprehensive Android project workflow profile
   */
  static analyze(files = {}, extraMeta = {}) {
    const paths = Object.keys(files || {});

    const profile = {
      isAndroid: false,
      projectType: 'library', // 'library' | 'app' | 'multi-module'
      projectName: extraMeta?.name || 'AndroidProject',
      packageName: extraMeta?.packageName || 'com.example.android',
      version: extraMeta?.version || '1.0.0',

      // Build Tooling Versions
      agpVersion: '',
      gradleVersion: '',
      targetJdk: '17', // Resolved JDK version (11, 17, 21)
      compileSdk: 34,
      minSdk: 21,
      targetSdk: 34,
      buildToolsVersion: '34.0.0',

      // Modules
      modules: [],
      primaryLibraryModule: null,
      primaryAppModule: null,

      // Capabilities & Features
      hasKotlin: false,
      kotlinVersion: '',
      hasCompose: false,
      hasViewBinding: false,
      hasMavenPublish: false,
      hasJitPack: false,
      hasUnitTests: false,
      hasInstrumentedTests: false,

      // Gradle Tasks
      buildTasks: {
        assembleRelease: 'assembleRelease',
        bundleRelease: '',
        testTask: 'test',
        verificationTask: 'check',
        publishTask: ''
      },

      // Artifact Requirements
      artifacts: {
        type: 'AAR', // 'AAR' | 'APK/AAB' | 'AAR_AND_APK'
        paths: ['**/build/outputs/aar/*.aar'],
        labels: ['AAR Library Archive']
      },

      // Detected Details
      diagnostics: []
    };

    if (paths.length === 0) {
      return profile;
    }

    // ── 1. Settings.gradle & Root detection ────────────────────────────────
    const settingsPath = paths.find(p => p === 'settings.gradle' || p === 'settings.gradle.kts');
    const settingsContent = settingsPath ? this.#readText(files[settingsPath]) : '';

    if (settingsContent) {
      profile.isAndroid = true;
      const rootMatch = settingsContent.match(/rootProject\.name\s*=\s*['"]([^'"]+)['"]/);
      if (rootMatch) {
        profile.projectName = rootMatch[1];
      }

      // Detect subproject modules (handles multi-line, comma-separated, Groovy and Kotlin DSL)
      const lines = settingsContent.split('\n');
      for (const line of lines) {
        if (/^\s*include\b/.test(line)) {
          const stringTokens = line.match(/(?:['"])([^'"]+)(?:['"])/g) || [];
          for (let tok of stringTokens) {
            const rawMod = tok.replace(/^['":]+|['"]+$/g, '').trim();
            if (rawMod && !profile.modules.includes(rawMod)) {
              profile.modules.push(rawMod);
            }
          }
        }
      }
    }

    // ── 2. Gradle Wrapper Version Detection ───────────────────────────────
    const wrapperPropPath = paths.find(p => p.endsWith('gradle-wrapper.properties'));
    if (wrapperPropPath) {
      profile.isAndroid = true;
      const propText = this.#readText(files[wrapperPropPath]);
      const gMatch = propText.match(/gradle-([0-9.]+)-(bin|all)\.zip/);
      if (gMatch) {
        profile.gradleVersion = gMatch[1];
      }
    }

    // ── 3. Version Catalog (libs.versions.toml) Detection ─────────────────
    const tomlPath = paths.find(p => p.endsWith('libs.versions.toml'));
    if (tomlPath) {
      const tomlText = this.#readText(files[tomlPath]);
      const agpMatch = tomlText.match(/agp\s*=\s*['"]([^'"]+)['"]/i);
      if (agpMatch) {
        profile.agpVersion = agpMatch[1];
      }
      const kotlinMatch = tomlText.match(/kotlin\s*=\s*['"]([^'"]+)['"]/i);
      if (kotlinMatch) {
        profile.hasKotlin = true;
        profile.kotlinVersion = kotlinMatch[1];
      }
    }

    // ── 4. Root build.gradle & build.gradle.kts ───────────────────────────
    const rootBuildPath = paths.find(p => p === 'build.gradle' || p === 'build.gradle.kts');
    const rootBuildContent = rootBuildPath ? this.#readText(files[rootBuildPath]) : '';

    if (rootBuildContent) {
      profile.isAndroid = true;

      // Extract AGP if not found yet
      if (!profile.agpVersion) {
        const agpMatch = rootBuildContent.match(/(?:id\s*\(?\s*['"]com\.android\.(?:application|library)['"]\s*\)?\s*version\s*['"]([^'"]+)['"]|agp_version\s*=\s*['"]([^'"]+)['"]|com\.android\.tools\.build:gradle:([^'"]+)['"])/);
        if (agpMatch) {
          profile.agpVersion = agpMatch[1] || agpMatch[2] || agpMatch[3];
        }
      }

      // Check Kotlin
      if (rootBuildContent.includes('org.jetbrains.kotlin') || rootBuildContent.includes('kotlin-gradle-plugin')) {
        profile.hasKotlin = true;
      }
    }

    // ── 5. Module Level build.gradle Analysis ──────────────────────────────
    // If no modules in settings.gradle, look for directories with build.gradle
    if (profile.modules.length === 0) {
      const moduleBuilds = paths.filter(p => p.includes('/') && (p.endsWith('/build.gradle') || p.endsWith('/build.gradle.kts')));
      for (const mb of moduleBuilds) {
        const modName = mb.split('/')[0];
        if (modName && !profile.modules.includes(modName)) {
          profile.modules.push(modName);
        }
      }
    }

    let libraryModules = [];
    let appModules = [];

    const moduleCandidates = profile.modules.length > 0 ? profile.modules : [''];

    for (const mod of moduleCandidates) {
      const buildKey = mod
        ? paths.find(p => p === `${mod}/build.gradle` || p === `${mod}/build.gradle.kts`)
        : rootBuildPath;

      if (!buildKey) continue;
      const content = this.#readText(files[buildKey]);
      if (!content) continue;

      const isLib = content.includes('com.android.library') || content.includes("'com.android.library'") || content.includes('"com.android.library"');
      const isApp = content.includes('com.android.application') || content.includes("'com.android.application'") || content.includes('"com.android.application"');

      if (isLib) {
        libraryModules.push(mod);
      }
      if (isApp) {
        appModules.push(mod);
      }

      // compileSdk / compileSdkVersion
      const cSdkMatch = content.match(/(?:compileSdk|compileSdkVersion)\s*(?:=\s*)?([0-9]+)/);
      if (cSdkMatch) {
        const val = parseInt(cSdkMatch[1], 10);
        if (!isNaN(val) && val > 0) profile.compileSdk = Math.max(profile.compileSdk, val);
      }

      // minSdk / minSdkVersion
      const mSdkMatch = content.match(/(?:minSdk|minSdkVersion)\s*(?:=\s*)?([0-9]+)/);
      if (mSdkMatch) {
        const val = parseInt(mSdkMatch[1], 10);
        if (!isNaN(val) && val > 0) profile.minSdk = val;
      }

      // targetSdk / targetSdkVersion
      const tSdkMatch = content.match(/(?:targetSdk|targetSdkVersion)\s*(?:=\s*)?([0-9]+)/);
      if (tSdkMatch) {
        const val = parseInt(tSdkMatch[1], 10);
        if (!isNaN(val) && val > 0) profile.targetSdk = val;
      }

      // Java version compatibility in compileOptions
      const javaMatch = content.match(/(?:sourceCompatibility|targetCompatibility)\s*=\s*(?:JavaVersion\.VERSION_([0-9_]+)|['"]?([0-9]+)['"]?)/);
      if (javaMatch) {
        const ver = (javaMatch[1] || javaMatch[2] || '').replace('_', '.');
        if (ver === '1.8' || ver === '8') profile.targetJdk = '17'; // Modern runners require at least 17 for AGP 8
        else if (ver === '11') profile.targetJdk = '11';
        else if (ver === '17') profile.targetJdk = '17';
        else if (ver === '21') profile.targetJdk = '21';
      }

      // Features
      if (content.includes('maven-publish') || content.includes('publishing {')) {
        profile.hasMavenPublish = true;
      }
      if (content.includes('compose = true') || content.includes('androidx.compose')) {
        profile.hasCompose = true;
      }
      if (content.includes('viewBinding = true') || content.includes('viewBinding.enabled = true')) {
        profile.hasViewBinding = true;
      }
    }

    // ── 6. Determine Project Type ──────────────────────────────────────────
    const hasAndroid = libraryModules.length > 0 || appModules.length > 0 || paths.some(p => p.includes('AndroidManifest.xml'));
    const isExplicitJava = extraMeta?.projectType === 'java-library' || extraMeta?.template === 'pure-java-library';
    const isDetectedJava = !hasAndroid && (rootBuildContent.includes('java-library') || rootBuildContent.includes("id 'java'"));

    if (isExplicitJava || isDetectedJava) {
      profile.isAndroid = false;
      profile.projectType = 'java-library';
      profile.primaryLibraryModule = libraryModules[0] || '';
      profile.primaryAppModule = '';
    } else if (libraryModules.length > 0 && appModules.length > 0) {
      const preferredApp = appModules.find(m => m === 'app') || appModules.find(m => m === 'sample') || appModules[0];
      const isExplicitLib = extraMeta?.projectType === 'library' || extraMeta?.template?.includes('library') || !extraMeta?.projectType;
      if (isExplicitLib) {
        profile.projectType = 'library';
        profile.primaryLibraryModule = libraryModules[0];
        profile.primaryAppModule = preferredApp;
      } else {
        profile.projectType = 'multi-module';
        profile.primaryLibraryModule = libraryModules[0];
        profile.primaryAppModule = preferredApp;
      }
    } else if (appModules.length > 0) {
      profile.projectType = 'app';
      profile.primaryAppModule = appModules.find(m => m === 'app') || appModules[0];
    } else {
      profile.projectType = 'library';
      // If root project is the library (no submodules), primaryLibraryModule is ''
      profile.primaryLibraryModule = (profile.modules.length > 0 && libraryModules.length > 0) ? libraryModules[0] : '';
    }

    // ── 7. Automatic JDK Requirement Resolution based on AGP / Gradle ──────
    // AGP 8.3+: Requires JDK 17, supports JDK 21
    // AGP 8.0 - 8.2: Requires JDK 17
    // AGP 7.0 - 7.4: Requires JDK 11 (JDK 17 supported on 7.3+)
    // AGP 4.x / 3.x: Supports JDK 8 / 11
    if (profile.agpVersion) {
      const agpMajor = parseFloat(profile.agpVersion);
      if (agpMajor >= 8.3) {
        profile.targetJdk = '17';
      } else if (agpMajor >= 8.0) {
        profile.targetJdk = '17';
      } else if (agpMajor >= 7.0) {
        profile.targetJdk = '17'; // JDK 17 is standard and fully backward compatible
      }
    } else {
      // Default to JDK 17 for modern Android development
      profile.targetJdk = '17';
    }

    profile.buildToolsVersion = `${profile.compileSdk}.0.0`;

    // ── 8. Resolve Tasks & Dynamic Artifacts ──────────────────────────────
    // Detect AAB support: apps support bundleRelease by default in AGP 3.2+
    // Check if gradle files mention bundle, play publishing, or if requested in meta
    const hasAabInGradle = paths.some(p => {
      if (!p.endsWith('.gradle') && !p.endsWith('.gradle.kts')) return false;
      const c = this.#readText(files[p]);
      return c.includes('bundle') || c.includes('playPublishing') || c.includes('assetPacks') || c.includes('aab');
    });

    profile.supportsAab = (profile.projectType === 'app' || profile.projectType === 'multi-module' || hasAabInGradle || !!extraMeta?.requireAab);

    if (profile.projectType === 'java-library') {
      const modPrefix = profile.primaryLibraryModule ? `:${profile.primaryLibraryModule}:` : '';
      profile.buildTasks.assembleRelease = `${modPrefix}build`;
      profile.buildTasks.bundleRelease = '';
      profile.buildTasks.testTask = `${modPrefix}test`;
      profile.buildTasks.verificationTask = `${modPrefix}check`;
      profile.buildTasks.publishTask = 'publishToMavenLocal';

      profile.artifacts = {
        type: 'JAR',
        isLibrary: true,
        isApp: false,
        supportsAab: false,
        paths: ['build/libs/*.jar', '**/build/libs/*.jar'],
        releaseAssetPatterns: ['build/libs/*.jar', '**/build/libs/*.jar'],
        labels: ['Pure Java Library Archive (.jar)']
      };
    } else if (profile.projectType === 'library') {
      const modPrefix = profile.primaryLibraryModule ? `:${profile.primaryLibraryModule}:` : '';
      profile.buildTasks.assembleRelease = `${modPrefix}assembleRelease`;
      profile.buildTasks.bundleRelease = '';
      profile.buildTasks.testTask = `${modPrefix}testReleaseUnitTest`;
      profile.buildTasks.verificationTask = `${modPrefix}check`;
      profile.buildTasks.publishTask = profile.hasMavenPublish ? `${modPrefix}publish` : '';

      const libDir = profile.primaryLibraryModule || '';
      const aarGlob = libDir ? `${libDir}/build/outputs/aar/*.aar` : '**/build/outputs/aar/*.aar';

      profile.artifacts = {
        type: 'AAR',
        isLibrary: true,
        isApp: false,
        supportsAab: false,
        paths: [aarGlob],
        releaseAssetPatterns: [aarGlob],
        labels: ['AAR Library Archive (.aar)']
      };
    } else if (profile.projectType === 'app') {
      const modPrefix = profile.primaryAppModule ? `:${profile.primaryAppModule}:` : '';
      profile.buildTasks.assembleRelease = `${modPrefix}assembleRelease`;
      profile.buildTasks.bundleRelease = profile.supportsAab ? `${modPrefix}bundleRelease` : '';
      profile.buildTasks.testTask = `${modPrefix}testReleaseUnitTest`;
      profile.buildTasks.verificationTask = `${modPrefix}lint`;

      const appDir = profile.primaryAppModule || '';
      const apkGlob = appDir ? `${appDir}/build/outputs/apk/release/*.apk` : '**/build/outputs/apk/release/*.apk';
      const aabGlob = appDir ? `${appDir}/build/outputs/bundle/release/*.aab` : '**/build/outputs/bundle/release/*.aab';

      const artPaths = [apkGlob];
      const artLabels = ['Release APK Binary (.apk)'];
      if (profile.supportsAab) {
        artPaths.push(aabGlob);
        artLabels.push('Release App Bundle (.aab)');
      }

      profile.artifacts = {
        type: profile.supportsAab ? 'APK/AAB' : 'APK',
        isLibrary: false,
        isApp: true,
        supportsAab: profile.supportsAab,
        paths: artPaths,
        releaseAssetPatterns: artPaths,
        labels: artLabels
      };
    } else {
      // Multi-module: Library + Sample App
      const libPrefix = profile.primaryLibraryModule ? `:${profile.primaryLibraryModule}:` : '';
      const appPrefix = profile.primaryAppModule ? `:${profile.primaryAppModule}:` : '';

      profile.buildTasks.assembleRelease = `${libPrefix}assembleRelease ${appPrefix}assembleRelease`;
      profile.buildTasks.bundleRelease = profile.supportsAab ? `${appPrefix}bundleRelease` : '';
      profile.buildTasks.testTask = 'test';
      profile.buildTasks.verificationTask = 'check';

      const libDir = profile.primaryLibraryModule || '';
      const appDir = profile.primaryAppModule || '';

      const aarGlob = libDir ? `${libDir}/build/outputs/aar/*.aar` : '**/build/outputs/aar/*.aar';
      const apkGlob = appDir ? `${appDir}/build/outputs/apk/release/*.apk` : '**/build/outputs/apk/release/*.apk';
      const aabGlob = appDir ? `${appDir}/build/outputs/bundle/release/*.aab` : '**/build/outputs/bundle/release/*.aab';

      const artPaths = [aarGlob, apkGlob];
      const artLabels = ['AAR Library Archive (.aar)', 'Sample App APK (.apk)'];
      if (profile.supportsAab) {
        artPaths.push(aabGlob);
        artLabels.push('Sample App Bundle (.aab)');
      }

      profile.artifacts = {
        type: profile.supportsAab ? 'AAR_APK_AAB' : 'AAR_AND_APK',
        isLibrary: true,
        isApp: true,
        supportsAab: profile.supportsAab,
        paths: artPaths,
        releaseAssetPatterns: artPaths,
        labels: artLabels
      };
    }

    return profile;
  }

  static #readText(val) {
    if (typeof val === 'string') return val;
    if (val instanceof Uint8Array) {
      try {
        return new TextDecoder().decode(val);
      } catch {
        return '';
      }
    }
    return '';
  }
}
