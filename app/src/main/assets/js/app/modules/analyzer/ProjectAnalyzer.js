/**
 * ProjectAnalyzer.js — Comprehensive Android Project Analyzer for Web Tool.
 *
 * Inspects all project files to detect:
 * - Project type (Android Library vs Android App vs Multi-module)
 * - Gradle wrapper version & AGP version
 * - Required JDK version (JDK 17 vs 21) based on AGP and compileOptions
 * - compileSdk, targetSdk, minSdk
 * - Modules list (:app, :library, etc.)
 * - namespace and applicationId per module
 * - Language (Java vs Kotlin)
 * - AndroidX, ViewBinding, Compose, Material
 * - Existing dependencies (preserved without automatic graph guessing)
 * - Repositories (google, mavenCentral, JitPack, GitHub Packages, and machine-specific local Maven paths)
 * - Publishing configuration (maven-publish, release publication, POM)
 * - Test configuration (Unit tests, AndroidJUnitRunner)
 * - Diagnostics & warnings (e.g. computer-specific paths like E:/...)
 */

import { SourceDependencyAnalyzer } from './SourceDependencyAnalyzer.js';

export class ProjectAnalyzer {
  /**
   * Analyze the files of an Android project.
   * @param {Object.<string, string|Uint8Array>} files - flat path -> content map
   * @param {object} [existingMeta] - optional existing metadata
   * @returns {object} Analysis result
   */
  static analyze(files = {}, existingMeta = {}) {
    const paths = Object.keys(files);

    const result = {
      isAndroidProject: false,
      projectType: 'unknown', // 'library' | 'app' | 'multi-module' | 'unknown'
      rootProjectName: existingMeta.name || '',
      agpVersion: '',
      gradleVersion: '',
      jdkRequirement: '17', // default minimum for AGP 8.x
      modules: [],
      primaryModule: null,
      hasWrapper: false,
      hasWrapperJar: false,
      wrapperExecPermissionOk: true,
      technologies: {
        language: 'java',
        hasKotlin: false,
        hasJava: false,
        useAndroidX: true,
        viewBinding: false,
        dataBinding: false,
        compose: false,
        material: false,
      },
      repositories: {
        google: false,
        mavenCentral: false,
        jitpack: false,
        githubPackages: false,
        localMachineRepos: [], // e.g. E:/AndroidStudioProjects2/...
        projectRelativeLocalRepo: false, // layout.buildDirectory.dir("local-maven-repo")
      },
      publishing: {
        hasMavenPublish: false,
        hasReleasePublication: false,
        hasSingleVariantRelease: false,
        groupId: '',
        artifactId: '',
        version: '',
      },
      tests: {
        hasUnitTests: false,
        hasInstrumentedTests: false,
        testRunner: '',
      },
      warnings: [],
      errors: [],
      summary: '',
    };

    if (paths.length === 0) {
      result.errors.push('No files found in project.');
      return result;
    }

    // ── 1. Settings.gradle & Root detection ────────────────────────────────
    const settingsPath = paths.find(p => p === 'settings.gradle' || p === 'settings.gradle.kts');
    const settingsContent = settingsPath ? String(files[settingsPath] || '') : '';

    if (settingsContent) {
      result.isAndroidProject = true;
      const rootNameMatch = settingsContent.match(/rootProject\.name\s*=\s*['"]([^'"]+)['"]/);
      if (rootNameMatch) {
        result.rootProjectName = rootNameMatch[1];
      }

      // Detect subproject modules (handles multi-line, comma-separated, Groovy and Kotlin DSL)
      const lines = settingsContent.split('\n');
      for (const line of lines) {
        if (/^\s*include\b/.test(line)) {
          const stringTokens = line.match(/(?:['"])([^'"]+)(?:['"])/g) || [];
          for (let tok of stringTokens) {
            const rawMod = tok.replace(/^['":]+|['"]+$/g, '').trim();
            if (rawMod && !result.modules.includes(rawMod)) {
              result.modules.push(rawMod);
            }
          }
        }
      }
    }

    // Check wrapper
    const wrapperPropPath = paths.find(p => p.endsWith('gradle-wrapper.properties'));
    if (wrapperPropPath) {
      result.hasWrapper = true;
      const propContent = String(files[wrapperPropPath] || '');
      const verMatch = propContent.match(/gradle-([0-9.]+)-(bin|all)\.zip/);
      if (verMatch) {
        result.gradleVersion = verMatch[1];
      }
    }
    const wrapperJarPath = paths.find(p => p.endsWith('gradle-wrapper.jar'));
    if (wrapperJarPath) {
      result.hasWrapperJar = true;
    }

    // ── 2. Root build.gradle analysis ──────────────────────────────────────
    const rootBuildPath = paths.find(p => p === 'build.gradle' || p === 'build.gradle.kts');
    const rootBuildContent = rootBuildPath ? String(files[rootBuildPath] || '') : '';

    if (rootBuildContent) {
      result.isAndroidProject = true;

      // Detect AGP version (supports both id '...' and id("...") and classpath and agp_version)
      const agpMatch = rootBuildContent.match(/(?:id\s*\(?\s*['"]com\.android\.(?:application|library)['"]\s*\)?\s*version\s*['"]([^'"]+)['"]|agp_version\s*=\s*['"]([^'"]+)['"]|classpath\s*\(?\s*['"]com\.android\.tools\.build:gradle:([^'"]+)['"]\)?)/);
      if (agpMatch) {
        result.agpVersion = agpMatch[1] || agpMatch[2] || agpMatch[3];
      }

      // Detect Kotlin
      if (rootBuildContent.includes('org.jetbrains.kotlin') || rootBuildContent.includes('kotlin_version')) {
        result.technologies.hasKotlin = true;
      }
    }

    // ── 3. Find and Analyze Modules ─────────────────────────────────────────
    // If no modules in settings.gradle, look for directories with build.gradle
    if (result.modules.length === 0) {
      const moduleBuilds = paths.filter(p => p.includes('/') && (p.endsWith('/build.gradle') || p.endsWith('/build.gradle.kts')));
      for (const mb of moduleBuilds) {
        const modName = mb.split('/')[0];
        if (modName && !result.modules.includes(modName)) {
          result.modules.push(modName);
        }
      }
    }

    // Module-level detection
    let libraryCount = 0;
    let appCount = 0;
    const parsedModules = [];

    const moduleCandidates = result.modules.length > 0 ? result.modules : ['app'];

    for (const mod of moduleCandidates) {
      const modBuildKey = paths.find(p => p === `${mod}/build.gradle` || p === `${mod}/build.gradle.kts`);
      const modContent = modBuildKey ? String(files[modBuildKey] || '') : '';

      const modInfo = {
        name: mod,
        type: 'unknown',
        namespace: '',
        applicationId: '',
        compileSdk: '',
        minSdk: '',
        targetSdk: '',
        versionCode: '',
        versionName: '',
        dependencies: [],
        hasPublishing: false,
        viewBinding: false,
        dataBinding: false,
      };

      if (modContent) {
        result.isAndroidProject = true;

        if (modContent.includes("com.android.library") || modContent.includes("'com.android.library'") || modContent.includes('"com.android.library"')) {
          modInfo.type = 'library';
          libraryCount++;
        } else if (modContent.includes("com.android.application") || modContent.includes("'com.android.application'") || modContent.includes('"com.android.application"')) {
          modInfo.type = 'application';
          appCount++;
        }

        // namespace
        const nsMatch = modContent.match(/namespace\s*=?\s*['"]([^'"]+)['"]/);
        if (nsMatch) modInfo.namespace = nsMatch[1];

        // applicationId
        const appIdMatch = modContent.match(/applicationId\s*=?\s*['"]([^'"]+)['"]/);
        if (appIdMatch) modInfo.applicationId = appIdMatch[1];

        // SDKs
        const compileSdkMatch = modContent.match(/(?:compileSdk|compileSdkVersion)\s*=?\s*([0-9]+)/);
        if (compileSdkMatch) modInfo.compileSdk = compileSdkMatch[1];

        const minSdkMatch = modContent.match(/(?:minSdk|minSdkVersion)\s*=?\s*([0-9]+)/);
        if (minSdkMatch) modInfo.minSdk = minSdkMatch[1];

        const targetSdkMatch = modContent.match(/(?:targetSdk|targetSdkVersion)\s*=?\s*([0-9]+)/);
        if (targetSdkMatch) modInfo.targetSdk = targetSdkMatch[1];

        const vcMatch = modContent.match(/versionCode\s*=?\s*([0-9]+)/);
        if (vcMatch) modInfo.versionCode = vcMatch[1];

        const vnMatch = modContent.match(/versionName\s*=?\s*['"]?([^'"\r\n\s]+)['"]?/);
        if (vnMatch) modInfo.versionName = vnMatch[1].replace(/["']/g, '');

        // ViewBinding / Compose
        if (modContent.includes('viewBinding true') || modContent.includes('viewBinding = true')) {
          modInfo.viewBinding = true;
          result.technologies.viewBinding = true;
        }
        if (modContent.includes('dataBinding true') || modContent.includes('dataBinding = true')) {
          modInfo.dataBinding = true;
          result.technologies.dataBinding = true;
        }
        if (modContent.includes('compose true') || modContent.includes('compose = true')) {
          result.technologies.compose = true;
        }

        // Publishing
        if (modContent.includes('maven-publish') || modContent.includes('publishing {')) {
          modInfo.hasPublishing = true;
          result.publishing.hasMavenPublish = true;

          const gIdMatch = modContent.match(/(?:libraryGroupId|groupId)\s*=\s*['"]([^'"]+)['"]/);
          if (gIdMatch) result.publishing.groupId = gIdMatch[1];

          const artMatch = modContent.match(/(?:libraryName|artifactId)\s*=\s*['"]([^'"]+)['"]/);
          if (artMatch) result.publishing.artifactId = artMatch[1];

          const verMatch = modContent.match(/(?:libraryVersion|version)\s*=\s*['"]([^'"]+)['"]/);
          if (verMatch) result.publishing.version = verMatch[1];

          if (modContent.includes('create("release"') || modContent.includes("create('release'") || modContent.includes('release(MavenPublication)') || /release\s*\(\s*MavenPublication\s*\)/.test(modContent) || /register\s*<\s*MavenPublication\s*>\s*\(\s*['"]release['"]\s*\)/.test(modContent)) {
            result.publishing.hasReleasePublication = true;
          }
          if (modContent.includes('singleVariant("release")') || modContent.includes("singleVariant('release')")) {
            result.publishing.hasSingleVariantRelease = true;
          }
        }

        // Dependencies extractor (preserves developer's exact configurations)
        const depBlockMatch = modContent.match(/dependencies\s*\{([\s\S]*?)\n\}/);
        if (depBlockMatch) {
          const depLines = depBlockMatch[1].split('\n')
            .map(l => l.trim())
            .filter(l => l && !l.startsWith('//') && !l.startsWith('/*'));
          modInfo.dependencies = depLines;
        }

        parsedModules.push(modInfo);
      }
    }

    // Determine overall project type
    if (libraryCount > 0 && appCount > 0) {
      result.projectType = 'multi-module';
    } else if (libraryCount > 0) {
      result.projectType = 'library';
    } else if (appCount > 0) {
      result.projectType = 'app';
    } else {
      // Check files for manifests or indicators
      if (paths.some(p => p.includes('AndroidManifest.xml'))) {
        result.isAndroidProject = true;
        result.projectType = 'library'; // default fallback
      }
    }

    result.parsedModules = parsedModules;
    result.primaryModule = parsedModules.find(m => m.name === 'app') ||
                           parsedModules.find(m => m.type === 'library') ||
                           parsedModules.find(m => m.type === 'application') ||
                           parsedModules[0] || null;

    // ── 4. Source Languages & Tests ─────────────────────────────────────────
    const hasKtFiles = paths.some(p => p.endsWith('.kt'));
    const hasJavaFiles = paths.some(p => p.endsWith('.java'));
    result.technologies.hasKotlin = result.technologies.hasKotlin || hasKtFiles;
    result.technologies.hasJava = hasJavaFiles;
    result.technologies.language = hasKtFiles && !hasJavaFiles ? 'kotlin' : 'java';

    // Tests detection
    result.tests.hasUnitTests = paths.some(p => p.includes('/src/test/') && (p.endsWith('.java') || p.endsWith('.kt')));
    result.tests.hasInstrumentedTests = paths.some(p => p.includes('/src/androidTest/') && (p.endsWith('.java') || p.endsWith('.kt')));

    // ── 5. Repositories & Machine-Specific Path Detection ────────────────────
    const allGradleTexts = [
      settingsContent,
      rootBuildContent,
      ...parsedModules.map(m => {
        const k = paths.find(p => p.startsWith(`${m.name}/build.gradle`));
        return k ? String(files[k] || '') : '';
      })
    ].join('\n');

    result.repositories.google = allGradleTexts.includes('google()');
    result.repositories.mavenCentral = allGradleTexts.includes('mavenCentral()');
    result.repositories.jitpack = allGradleTexts.includes('jitpack.io');
    result.repositories.githubPackages = allGradleTexts.includes('maven.pkg.github.com');

    if (allGradleTexts.includes('layout.buildDirectory') && allGradleTexts.includes('local-maven-repo')) {
      result.repositories.projectRelativeLocalRepo = true;
    }

    // CRITICAL: Detect machine-specific local Maven paths such as E:/AndroidStudioProjects2/... or C:/Users/...
    // Strictly ignore remote repository URLs (https://, http://, etc.)
    const machinePathRegex = /(?:url\s*=?\s*(?:uri\()?['"]([A-Za-z]:[\\/][^'"]+|\/(?:home|Users|root)\/[^'"]+)['"]\)?)/g;
    let match;
    while ((match = machinePathRegex.exec(allGradleTexts)) !== null) {
      let matchedPath = match[1];
      if (!matchedPath) continue;
      // Exclude any standard URL protocol schemes (e.g. https://jitpack.io)
      if (/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//i.test(matchedPath)) continue;
      if (matchedPath.includes('jitpack.io') || matchedPath.includes('mavenCentral')) continue;

      if (!result.repositories.localMachineRepos.includes(matchedPath)) {
        result.repositories.localMachineRepos.push(matchedPath);
      }
    }

    if (result.repositories.localMachineRepos.length > 0) {
      result.warnings.push({
        code: 'MACHINE_SPECIFIC_LOCAL_MAVEN_PATH',
        message: `Found computer-specific local Maven path(s): ${result.repositories.localMachineRepos.join(', ')}. These will cause GitHub Actions builds to fail on cloud runners!`,
        paths: result.repositories.localMachineRepos,
      });
    }

    // ── 6. JDK Requirement Calculation ──────────────────────────────────────
    // AGP 8.0+ requires JDK 17. AGP 8.8+/9.0+ supports JDK 21.
    if (result.agpVersion) {
      const major = parseFloat(result.agpVersion);
      if (major >= 8.8) {
        result.jdkRequirement = '17'; // Temurin 17 is standard and stable across all actions runners
      } else {
        result.jdkRequirement = '17';
      }
    }

    // ── 6b. Deep Package Name & Repository Auto-Analysis ───────────────────
    let detectedPackage = '';

    // 1. Module namespace or applicationId
    if (result.primaryModule?.namespace) {
      detectedPackage = result.primaryModule.namespace;
    } else if (result.primaryModule?.applicationId) {
      detectedPackage = result.primaryModule.applicationId;
    }

    if (!detectedPackage) {
      for (const mod of parsedModules) {
        if (mod.namespace) { detectedPackage = mod.namespace; break; }
        if (mod.applicationId) { detectedPackage = mod.applicationId; break; }
      }
    }

    // 2. AndroidManifest.xml package attribute
    if (!detectedPackage) {
      const manifestPaths = paths.filter(p => p.endsWith('AndroidManifest.xml'));
      for (const mp of manifestPaths) {
        const content = String(files[mp] || '');
        const pkgMatch = content.match(/package\s*=\s*['"]([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)['"]/);
        if (pkgMatch) {
          detectedPackage = pkgMatch[1];
          break;
        }
      }
    }

    // 3. Source directory hierarchy under src/main/java or src/main/kotlin
    if (!detectedPackage) {
      const srcFiles = paths.filter(p => p.includes('/src/main/java/') || p.includes('/src/main/kotlin/'));
      for (const sp of srcFiles) {
        const match = sp.match(/\/src\/main\/(?:java|kotlin)\/([a-zA-Z0-9_]+(?:\/[a-zA-Z0-9_]+)+)\//);
        if (match && match[1]) {
          const candidate = match[1].replace(/\//g, '.');
          const candidateParts = candidate.split('.');
          if (candidateParts.length >= 2 && !candidateParts.some(part => ['src', 'main', 'java', 'kotlin'].includes(part))) {
            detectedPackage = candidate;
            break;
          }
        }
      }
    }

    // 4. Source code "package foo.bar.baz;" declaration in Java/Kotlin
    if (!detectedPackage) {
      const srcCodeFiles = paths.filter(p => p.endsWith('.java') || p.endsWith('.kt'));
      for (const sc of srcCodeFiles) {
        const content = String(files[sc] || '');
        const pkgStmt = content.match(/^\s*package\s+([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/m);
        if (pkgStmt) {
          detectedPackage = pkgStmt[1];
          break;
        }
      }
    }

    result.detectedPackageName = detectedPackage || '';

    // Detected repository name & author
    let detectedRepo = result.publishing.artifactId || '';
    let detectedAuthor = result.publishing.groupId || '';

    if (!detectedRepo && result.rootProjectName) {
      detectedRepo = result.rootProjectName.toLowerCase().replace(/[^a-z0-9_-]/g, '-');
    }

    // Git config if available
    const gitConfigFile = paths.find(p => p.endsWith('.git/config'));
    if (gitConfigFile && files[gitConfigFile]) {
      const gitContent = String(files[gitConfigFile]);
      const urlMatch = gitContent.match(/url\s*=\s*.*github\.com[:/]([^/]+)\/([^/\s.]+)/i);
      if (urlMatch) {
        if (!detectedAuthor) detectedAuthor = urlMatch[1];
        if (!detectedRepo) detectedRepo = urlMatch[2];
      }
    }

    result.detectedRepoName = detectedRepo;
    result.detectedAuthor = detectedAuthor;

    // ── 7. Automatic Source Code & External Dependency Analysis ────────────
    result.sourceDependencies = SourceDependencyAnalyzer.analyze(files);

    // ── 8. Generate Formatted Diagnostic Summary ───────────────────────────
    const primary = result.primaryModule;
    result.summary = `Detected Android ${result.projectType === 'library' ? 'Library' : result.projectType === 'app' ? 'App' : 'Project'}` +
      (primary ? ` (${primary.namespace || primary.applicationId || primary.name})` : '') +
      ` | AGP: ${result.agpVersion || 'auto'} | Gradle: ${result.gradleVersion || '8.7'} | JDK: ${result.jdkRequirement}` +
      (primary ? ` | compileSdk: ${primary.compileSdk || '34'} | minSdk: ${primary.minSdk || '21'}` : '');

    return result;
  }
}
