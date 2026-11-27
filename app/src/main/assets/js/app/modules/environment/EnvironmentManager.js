/**
 * EnvironmentManager.js
 *
 * Core service for inspecting, diagnosing, and auto-fixing project environments:
 * - Determines project type (Java Library, Android Library, Android App)
 * - Identifies missing, incompatible, or conflicting versions
 * - Provides 1-click automatic fix generator
 * - Manages settings.gradle, root build.gradle, module build.gradle, wrapper, and manifests
 * - Generates README and JitPack publication workflows strictly separated by project type
 */

import {
  PROJECT_TYPES,
  AGP_COMPATIBILITY_TABLE,
  SDK_REQUIREMENTS,
  ENVIRONMENT_PRESETS,
  compareSemver,
} from './EnvironmentCompatibilityMatrix.js';
import { DependencyCompatibilityAdvisor } from './DependencyCompatibilityAdvisor.js';
import { projectService } from '../storage/ProjectService.js';
import { eventBus } from '../../core/EventBus.js';
import { consoleService } from '../storage/ConsoleService.js';

export class EnvironmentManager {
  /**
   * Inspect current project files and return a comprehensive analysis.
   * @param {Object.<string, string>} [files]
   * @returns {object} Analysis result
   */
  static analyze(files = null) {
    const proj = projectService.current;
    const fileMap = files || proj?.files || {};
    const fileKeys = Object.keys(fileMap);

    // 1. Detect project type
    const detectedType = this.detectProjectType(fileMap);

    // 2. Extract current versions & settings
    const current = this.extractCurrentVersions(fileMap, detectedType);

    // 3. Diagnose issues & conflicts
    const issues = this.diagnoseIssues(fileMap, detectedType, current);

    // 4. Calculate health score
    const errorCount = issues.filter(i => i.severity === 'error').length;
    const warningCount = issues.filter(i => i.severity === 'warning').length;
    let healthScore = 100 - (errorCount * 25) - (warningCount * 10);
    if (healthScore < 0) healthScore = 0;
    if (fileKeys.length === 0) healthScore = 0;

    return {
      projectType: detectedType,
      projectTypeInfo: PROJECT_TYPES[detectedType] || PROJECT_TYPES.ANDROID_LIBRARY,
      current,
      issues,
      healthScore,
      isClean: errorCount === 0 && warningCount === 0,
      timestamp: Date.now(),
    };
  }

  /**
   * Detect project type from Gradle plugins and manifests.
   */
  static detectProjectType(files) {
    let hasAppPlugin = false;
    let hasLibPlugin = false;
    let hasJavaPlugin = false;

    for (const [path, content] of Object.entries(files)) {
      if (typeof content !== 'string') continue;

      if (path.endsWith('.gradle') || path.endsWith('.gradle.kts')) {
        if (content.includes('com.android.application')) hasAppPlugin = true;
        if (content.includes('com.android.library')) hasLibPlugin = true;
        if (content.includes('java-library') || content.includes('id "java"') || content.includes("id 'java'")) hasJavaPlugin = true;
      }
    }

    if (hasLibPlugin) return 'ANDROID_LIBRARY';
    if (hasAppPlugin) return 'ANDROID_APP';
    if (hasJavaPlugin) return 'JAVA_LIBRARY';

    // Fallback: check if AndroidManifest.xml exists
    const hasManifest = Object.keys(files).some(k => k.toLowerCase().includes('androidmanifest.xml'));
    if (hasManifest) return 'ANDROID_LIBRARY';

    return 'JAVA_LIBRARY';
  }

  /**
   * Detect hardcoded computer-specific local filesystem paths.
   * Strictly distinguishes valid URLs (https://, http://, ftp://, etc.) from Windows drive letters
   * (e.g. C:\Users, E:/Android) and Unix user homes (/home/..., /Users/...).
   * @param {string} content
   * @returns {string[]} Array of detected local machine paths
   */
  static findLocalMachinePaths(content) {
    if (typeof content !== 'string') return [];

    const found = [];
    const lines = content.split('\n');

    for (const rawLine of lines) {
      const line = rawLine.trim();
      // Skip pure comment lines
      if (line.startsWith('//') || line.startsWith('#') || line.startsWith('*')) continue;

      // Tokenize by quotes or whitespace to inspect string literals and parameters
      const tokens = rawLine.match(/(?:['"])([^'"]+)(?:['"])|(?:[^\s"'=,()]+)/g) || [];

      for (let token of tokens) {
        // Strip surrounding quotes
        token = token.replace(/^['"]|['"]$/g, '').trim();
        if (!token) continue;

        // Skip any valid protocol URLs or web repositories (https://, http://, file://, etc.)
        if (/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//i.test(token)) continue;
        if (token.includes('://')) continue;
        if (token.includes('jitpack.io') || token.includes('mavenCentral') || token.includes('google.com') || token.includes('github.com')) continue;

        // 1. Windows drive path: e.g. C:\Users\..., E:/AndroidStudioProjects/...
        // Must be preceded by word boundary / start of token, single drive letter, :, single / or \ (NOT //)
        const winMatch = token.match(/^[a-zA-Z]:[\\/](?!\/)[^\s"'<>|?*]+/);
        if (winMatch) {
          found.push(winMatch[0]);
          continue;
        }

        // 2. Unix user home / local machine paths: e.g. /home/user/..., /Users/john/..., /root/...
        const unixMatch = token.match(/^\/(?:home|Users|root)\/[^\s"'<>|?*]+/);
        if (unixMatch) {
          found.push(unixMatch[0]);
          continue;
        }
      }
    }

    return [...new Set(found)];
  }

  /**
   * Extract versions and configurations from Gradle and wrapper files.
   */
  static extractCurrentVersions(files, projectType) {
    const res = {
      jdkVersion: '17',
      gradleVersion: '8.7',
      agpVersion: projectType === 'JAVA_LIBRARY' ? null : '8.4.2',
      compileSdk: projectType === 'JAVA_LIBRARY' ? null : 34,
      minSdk: projectType === 'JAVA_LIBRARY' ? null : 21,
      targetSdk: projectType === 'JAVA_LIBRARY' ? null : 34,
      namespace: '',
      applicationId: '',
      moduleName: 'library',
      hasSettingsGradle: false,
      hasRootBuildGradle: false,
      hasModuleBuildGradle: false,
      hasWrapperProperties: false,
      hasManifest: false,
      hasMavenPublish: false,
      hasJitPackYml: false,
      hasJitPackWorkflow: false,
      hasLocalMachinePaths: false,
      localMachinePaths: [],
    };

    // 1. Check file existence
    for (const path of Object.keys(files)) {
      if (path === 'settings.gradle' || path === 'settings.gradle.kts') res.hasSettingsGradle = true;
      if (path === 'build.gradle' || path === 'build.gradle.kts') res.hasRootBuildGradle = true;
      if (path.includes('/') && (path.endsWith('build.gradle') || path.endsWith('build.gradle.kts'))) res.hasModuleBuildGradle = true;
      if (path.includes('gradle-wrapper.properties')) res.hasWrapperProperties = true;
      if (path.toLowerCase().includes('androidmanifest.xml')) res.hasManifest = true;
      if (path === 'jitpack.yml') res.hasJitPackYml = true;
      if (path.includes('.github/workflows/')) res.hasJitPackWorkflow = true;
    }

    // 2. Parse wrapper properties and scan for local machine paths
    for (const [path, content] of Object.entries(files)) {
      if (typeof content !== 'string') continue;

      if (path.includes('gradle-wrapper.properties')) {
        const match = content.match(/gradle-([0-9.]+)-(all|bin)\.zip/);
        if (match) res.gradleVersion = match[1];
      }

      // Check for hardcoded local machine paths (strictly filtering out URLs like https://jitpack.io)
      const localMatches = EnvironmentManager.findLocalMachinePaths(content);
      if (localMatches.length > 0) {
        res.hasLocalMachinePaths = true;
        res.localMachinePaths.push(...localMatches);
      }
    }

    // 3. Parse Gradle files
    for (const [path, content] of Object.entries(files)) {
      if (typeof content !== 'string') continue;

      if (path.endsWith('.gradle') || path.endsWith('.gradle.kts')) {
        // AGP
        const agpMatch = content.match(/com\.android\.tools\.build:gradle:([0-9.]+)/) ||
                         content.match(/id\s+['"]com\.android\.(?:library|application)['"]\s+version\s+['"]([0-9.]+)['"]/);
        if (agpMatch) res.agpVersion = agpMatch[1];

        // compileSdk
        const compileSdkMatch = content.match(/compileSdk(?:Version)?\s*=?\s*([0-9]+)/);
        if (compileSdkMatch) res.compileSdk = parseInt(compileSdkMatch[1], 10);

        // minSdk
        const minSdkMatch = content.match(/minSdk(?:Version)?\s*=?\s*([0-9]+)/);
        if (minSdkMatch) res.minSdk = parseInt(minSdkMatch[1], 10);

        // targetSdk
        const targetSdkMatch = content.match(/targetSdk(?:Version)?\s*=?\s*([0-9]+)/);
        if (targetSdkMatch) res.targetSdk = parseInt(targetSdkMatch[1], 10);

        // namespace
        const nsMatch = content.match(/namespace\s*=?\s*['"]([^'"]+)['"]/);
        if (nsMatch) res.namespace = nsMatch[1];

        // applicationId
        const appIdMatch = content.match(/applicationId\s*=?\s*['"]([^'"]+)['"]/);
        if (appIdMatch) res.applicationId = appIdMatch[1];

        // JDK / JavaVersion
        const javaMatch = content.match(/JavaVersion\.VERSION_([0-9_]+)/i) ||
                          content.match(/jvmTarget\s*=\s*['"]([0-9.]+)['"]/);
        if (javaMatch) {
          const raw = javaMatch[1].replace('_', '.');
          res.jdkVersion = raw === '1.8' ? '8' : raw;
        }

        // maven-publish
        if (content.includes('maven-publish')) res.hasMavenPublish = true;
      }
    }

    return res;
  }

  /**
   * Diagnose all missing files, incompatibility issues, and configuration bugs.
   */
  static diagnoseIssues(files, projectType, current) {
    const issues = [];
    const isAndroid = projectType === 'ANDROID_LIBRARY' || projectType === 'ANDROID_APP';
    const isLibrary = projectType === 'JAVA_LIBRARY' || projectType === 'ANDROID_LIBRARY';

    // 1. Settings.gradle existence
    if (!current.hasSettingsGradle) {
      issues.push({
        id: 'missing-settings-gradle',
        severity: 'error',
        title: 'Missing settings.gradle',
        message: 'Project root requires settings.gradle to define the project name and include modules.',
        file: 'settings.gradle',
        fixAvailable: true,
      });
    }

    // 2. Root build.gradle existence
    if (!current.hasRootBuildGradle) {
      issues.push({
        id: 'missing-root-build-gradle',
        severity: 'error',
        title: 'Missing root build.gradle',
        message: 'Project is missing root build.gradle for repository declarations and plugin configurations.',
        file: 'build.gradle',
        fixAvailable: true,
      });
    }

    // 3. Gradle wrapper properties
    if (!current.hasWrapperProperties) {
      issues.push({
        id: 'missing-wrapper',
        severity: 'error',
        title: 'Missing gradle-wrapper.properties',
        message: 'No Gradle Wrapper configuration found. Remote CI, JitPack, and developers need gradle-wrapper.properties.',
        file: 'gradle/wrapper/gradle-wrapper.properties',
        fixAvailable: true,
      });
    }

    // 4. Android specific checks
    if (isAndroid) {
      if (!current.hasManifest) {
        issues.push({
          id: 'missing-manifest',
          severity: 'error',
          title: 'Missing AndroidManifest.xml',
          message: 'Android projects must have at least one AndroidManifest.xml specifying package/namespace and components.',
          file: 'src/main/AndroidManifest.xml',
          fixAvailable: true,
        });
      }

      if (!current.namespace) {
        issues.push({
          id: 'missing-namespace',
          severity: 'error',
          title: 'Missing namespace in build.gradle',
          message: 'AGP 8.0+ strictly requires a `namespace` declaration inside the android { } block.',
          file: 'build.gradle',
          fixAvailable: true,
        });
      }

      if (projectType === 'ANDROID_APP' && !current.applicationId) {
        issues.push({
          id: 'missing-application-id',
          severity: 'warning',
          title: 'Missing applicationId in Android App',
          message: 'Android Applications should define an applicationId in defaultConfig { }.',
          file: 'build.gradle',
          fixAvailable: true,
        });
      }

      // Check AGP vs Gradle compatibility
      if (current.agpVersion && current.gradleVersion) {
        for (const rule of AGP_COMPATIBILITY_TABLE) {
          if (compareSemver(current.agpVersion, rule.agpMin) >= 0 && compareSemver(current.agpVersion, rule.agpMax) <= 0) {
            if (compareSemver(current.gradleVersion, rule.minGradle) < 0) {
              issues.push({
                id: 'incompatible-agp-gradle',
                severity: 'error',
                title: 'Incompatible AGP and Gradle versions',
                message: `AGP ${current.agpVersion} requires Gradle ${rule.minGradle}+, but current Gradle version is ${current.gradleVersion}. Build will fail.`,
                file: 'gradle/wrapper/gradle-wrapper.properties',
                fixAvailable: true,
                suggestedGradle: rule.recommendedGradle,
              });
            }

            if (parseInt(current.jdkVersion, 10) < parseInt(rule.minJdk, 10)) {
              issues.push({
                id: 'incompatible-agp-jdk',
                severity: 'error',
                title: 'Incompatible JDK version for AGP',
                message: `AGP ${current.agpVersion} requires JDK ${rule.minJdk}+, but current JDK is ${current.jdkVersion}.`,
                file: 'build.gradle',
                fixAvailable: true,
                suggestedJdk: rule.recommendedJdk,
              });
            }
            break;
          }
        }
      }

      // Check compileSdk compatibility
      if (current.compileSdk && SDK_REQUIREMENTS[current.compileSdk]) {
        const req = SDK_REQUIREMENTS[current.compileSdk];
        if (current.agpVersion && compareSemver(current.agpVersion, req.minAgp) < 0) {
          issues.push({
            id: 'incompatible-sdk-agp',
            severity: 'warning',
            title: `compileSdk ${current.compileSdk} requires newer AGP`,
            message: `Targeting compileSdk ${current.compileSdk} is best supported by AGP ${req.minAgp}+ (currently ${current.agpVersion}).`,
            file: 'build.gradle',
            fixAvailable: true,
            suggestedAgp: req.recommendedAgp,
          });
        }
      }
    }

    // 5. Library publishing checks
    if (isLibrary) {
      if (!current.hasMavenPublish) {
        issues.push({
          id: 'missing-maven-publish',
          severity: 'error',
          title: 'Missing maven-publish plugin',
          message: 'Library projects published to JitPack must apply the "maven-publish" plugin and configure publications.',
          file: 'build.gradle',
          fixAvailable: true,
        });
      }

      if (!current.hasJitPackYml) {
        issues.push({
          id: 'missing-jitpack-yml',
          severity: 'warning',
          title: 'Missing jitpack.yml',
          message: 'JitPack needs jitpack.yml to specify the correct JDK environment for remote builds.',
          file: 'jitpack.yml',
          fixAvailable: true,
        });
      }
    } else if (projectType === 'ANDROID_APP') {
      // Check if Android Application is incorrectly trying to publish as a library
      if (current.hasMavenPublish) {
        issues.push({
          id: 'app-has-library-publish',
          severity: 'warning',
          title: 'Android Application configured for library publishing',
          message: 'Android Applications (APK/AAB) produce executable binaries, not reusable library dependencies. Remove maven-publish from App module.',
          file: 'build.gradle',
          fixAvailable: true,
        });
      }
    }

    // 6. Check for local machine path leaks
    if (current.hasLocalMachinePaths) {
      issues.push({
        id: 'local-machine-paths',
        severity: 'error',
        title: 'Hardcoded local machine paths detected',
        message: `Found computer-specific paths (e.g. ${current.localMachinePaths[0]}). These will break GitHub Actions and JitPack remote builds!`,
        file: 'build.gradle',
        fixAvailable: true,
      });
    }

    // 7. Check for hardcoded or nonexistent module tasks in workflows and jitpack
    const workflowFiles = Object.keys(files).filter(k => k.startsWith('.github/workflows/') && (k.endsWith('.yml') || k.endsWith('.yaml')));
    if (files['jitpack.yml']) workflowFiles.push('jitpack.yml');

    const declaredModules = [];
    const settingsContent = files['settings.gradle'] || files['settings.gradle.kts'] || '';
    if (settingsContent) {
      for (const line of settingsContent.split('\n')) {
        if (/^\s*include\b/.test(line)) {
          const tokens = line.match(/(?:['"])([^'"]+)(?:['"])/g) || [];
          for (let t of tokens) {
            t = t.replace(/^['":]+|['"]+$/g, '').trim();
            if (t && !declaredModules.includes(t)) declaredModules.push(t);
          }
        }
      }
    }

    for (const wfPath of workflowFiles) {
      const content = String(files[wfPath] || '');
      // Inspect line-by-line, filtering out shell pipes (grep, echo), task listings, and comments
      for (const rawLine of content.split('\n')) {
        const trimmed = rawLine.trim();
        if (!trimmed || trimmed.startsWith('#')) continue;
        const cmdPart = trimmed.split(/[|&;]/)[0].trim();
        if (!/(?:^|\s)(?:\.\/)?gradlew\b/.test(cmdPart)) continue;
        if (/\b(?:tasks|dependencies|help)\b/.test(cmdPart)) continue;

        const taskMatches = cmdPart.matchAll(/(?:\s|^)((?::([a-zA-Z0-9_-]+):|(?<![a-zA-Z0-9_\\/-])([a-zA-Z0-9_-]+):)(assembleRelease|assembleDebug|build|jar))\b/g);
        for (const tm of taskMatches) {
          const fullTask = tm[1];
          const modName = tm[2] || tm[3];
          const gradleAction = tm[4];

          const folderExists = Object.keys(files).some(k => k.startsWith(`${modName}/`));
          const modExists = declaredModules.includes(modName) || folderExists;

          if (!modExists) {
            issues.push({
              id: 'invalid-workflow-task',
              severity: 'error',
              title: `Invalid module in workflow build task (${modName})`,
              message: `Task "${fullTask}" in "${wfPath}" references project "${modName}" which is not found in root project "${current.rootProjectName || 'root'}". Build will fail with "project '${modName}' not found".`,
              file: wfPath,
              invalidTask: fullTask,
              invalidModule: modName,
              fixAvailable: true,
            });
            break;
          } else if (projectType === 'JAVA_LIBRARY' && (gradleAction === 'assembleRelease' || gradleAction === 'assembleDebug')) {
            issues.push({
              id: 'invalid-workflow-task',
              severity: 'error',
              title: 'Java Library does not support assembleRelease',
              message: `Task "${fullTask}" in "${wfPath}" calls "${gradleAction}", but Pure Java Libraries do not have Android assemble tasks. Use "build" or "jar" instead.`,
              file: wfPath,
              invalidTask: fullTask,
              fixAvailable: true,
            });
            break;
          }
        }
        if (issues.some(i => i.id === 'invalid-workflow-task' && i.file === wfPath)) break;
      }

      // Check if single root project has tasks with unnecessary phantom module prefix
      if (declaredModules.length === 0 && !issues.some(i => i.id === 'invalid-workflow-task')) {
        const phantomMatches = content.match(/(?:(?::([a-zA-Z0-9_-]+):|(?<!\w)([a-zA-Z0-9_-]+):)(assembleRelease|assembleDebug|build|jar))/);
        if (phantomMatches) {
          const phantomMod = phantomMatches[1] || phantomMatches[2];
          issues.push({
            id: 'invalid-workflow-task',
            severity: 'error',
            title: `Hardcoded module prefix in single-module project`,
            message: `Project is a single root project, but "${wfPath}" calls task with module ":${phantomMod}:". Use "assembleRelease" or "build" directly without module prefix.`,
            file: wfPath,
            invalidModule: phantomMod,
            fixAvailable: true,
          });
        }
      }

      // Check if Java Library incorrectly references Android assemble tasks
      if (projectType === 'JAVA_LIBRARY' && !issues.some(i => i.id === 'invalid-workflow-task')) {
        const javaAssembleMatch = content.match(/\b(assembleRelease|assembleDebug)\b/);
        if (javaAssembleMatch) {
          issues.push({
            id: 'invalid-workflow-task',
            severity: 'error',
            title: 'Java Library does not support assembleRelease',
            message: `Task "${javaAssembleMatch[1]}" in "${wfPath}" is invalid for Pure Java Libraries. Pure Java Libraries do not produce Android APK/AAR artifacts; use "build" or "jar" instead.`,
            file: wfPath,
            invalidTask: javaAssembleMatch[1],
            fixAvailable: true,
          });
        }
      }
    }

    // 8. Analyze Dependency Version Compatibility & Conflicts
    try {
      const depAnalysis = DependencyCompatibilityAdvisor.analyze(files, current);
      for (const conf of depAnalysis.conflicts) {
        issues.push({
          id: `dep-conflict-${conf.coordinate.replace(/[^a-zA-Z0-9_-]/g, '_')}`,
          severity: conf.severity,
          title: conf.title,
          message: conf.message,
          file: conf.filePath,
          fixAvailable: conf.canAutoFix,
          fixAction: conf.fixAction,
        });
      }
    } catch (e) {
      console.warn('[EnvironmentManager] Dependency analysis warning:', e);
    }

    return issues;
  }

  /**
   * Apply an automatic fix for a specific issue.
   */
  static async applyFix(issueId, currentConfig = null) {
    const proj = projectService.current;
    if (!proj) return { success: false, message: 'No project open' };

    const files = { ...(proj.files || {}) };
    const analysis = this.analyze(files);
    const config = currentConfig || this.resolveTargetConfig(analysis);

    const generated = this.generateConfigurationFiles(config, files);

    switch (issueId) {
      case 'missing-settings-gradle':
        await projectService.setFile('settings.gradle', generated['settings.gradle']);
        break;
      case 'missing-root-build-gradle':
        await projectService.setFile('build.gradle', generated['build.gradle']);
        break;
      case 'missing-wrapper':
      case 'incompatible-agp-gradle':
        await projectService.setFile('gradle/wrapper/gradle-wrapper.properties', generated['gradle/wrapper/gradle-wrapper.properties']);
        break;
      case 'missing-manifest':
        await projectService.setFile('src/main/AndroidManifest.xml', generated['src/main/AndroidManifest.xml'] || generated['AndroidManifest.xml']);
        break;
      case 'missing-namespace':
      case 'incompatible-agp-jdk':
      case 'incompatible-sdk-agp':
      case 'missing-maven-publish':
      case 'missing-application-id':
      case 'local-machine-paths':
      case 'app-has-library-publish':
        await projectService.setFile('build.gradle', generated['build.gradle']);
        break;
      case 'missing-jitpack-yml':
        await projectService.setFile('jitpack.yml', generated['jitpack.yml']);
        break;
      case 'invalid-workflow-task': {
        const correctTask = this.resolveCorrectBuildTask(files, analysis.projectType);
        for (const wfPath of Object.keys(files)) {
          if ((wfPath.startsWith('.github/workflows/') && (wfPath.endsWith('.yml') || wfPath.endsWith('.yaml'))) || wfPath === 'jitpack.yml') {
            let content = String(files[wfPath] || '');
            // Replace hardcoded :java-library:assembleRelease or :xyz:assembleRelease with correctTask
            content = content.replace(/(?::[a-zA-Z0-9_-]+:|(?<!\w)[a-zA-Z0-9_-]+:)(assembleRelease|assembleDebug|build|jar)/g, correctTask);
            // Replace standalone assembleRelease / assembleDebug if pure Java Library
            if (analysis.projectType === 'JAVA_LIBRARY') {
              content = content.replace(/\b(?:clean\s+)?assembleRelease\b/g, 'clean build');
              content = content.replace(/\b(?:clean\s+)?assembleDebug\b/g, 'clean build');
            }
            await projectService.setFile(wfPath, content);
          }
        }
        break;
      }
      default:
        if (issueId.startsWith('dep-conflict-')) {
          const issue = analysis.issues.find(i => i.id === issueId);
          if (issue?.fixAction) {
            const { filePath, targetCoordinate, scope } = issue.fixAction;
            DependencyCompatibilityAdvisor.applyUpdate(filePath, targetCoordinate, scope);
          }
          break;
        }

        // Default: sync all core files
        for (const [p, content] of Object.entries(generated)) {
          if (p === 'settings.gradle' || p === 'build.gradle' || p.includes('gradle-wrapper.properties') || p === 'jitpack.yml') {
            await projectService.setFile(p, content);
          }
        }
    }

    eventBus.emit('project:fileChanged');
    consoleService.info(`Environment: applied fix for "${issueId}"`);
    return { success: true, message: `Fixed ${issueId}` };
  }

  /**
   * Apply all automatic fixes across the entire project.
   */
  static async applyAllFixes(targetConfig = null) {
    const proj = projectService.current;
    if (!proj) return { success: false, message: 'No project open' };

    const files = { ...(proj.files || {}) };
    const analysis = this.analyze(files);
    const config = targetConfig || this.resolveTargetConfig(analysis);

    const generated = this.generateConfigurationFiles(config, files);

    for (const [path, content] of Object.entries(generated)) {
      await projectService.setFile(path, content);
    }

    eventBus.emit('project:fileChanged');
    consoleService.info('Environment: applied all fixes successfully');
    return { success: true, message: 'All environment files synchronized and fixed' };
  }

  /**
   * Resolve an optimal target configuration based on analysis.
   */
  static resolveTargetConfig(analysis) {
    const pType = analysis.projectType;
    const cur = analysis.current;
    const proj = projectService.current;

    let preset = ENVIRONMENT_PRESETS['android-lib-lts'];
    if (pType === 'ANDROID_APP') {
      preset = ENVIRONMENT_PRESETS['android-app-standard'];
    } else if (pType === 'JAVA_LIBRARY') {
      preset = ENVIRONMENT_PRESETS['java-lib-modern'];
    }

    return {
      projectType: pType,
      name: proj?.name || 'MyLibrary',
      groupId: proj?.groupId || 'com.example',
      artifactId: proj?.artifactId || proj?.name?.toLowerCase() || 'mylibrary',
      version: proj?.version || '1.0.0',
      namespace: cur.namespace || `${proj?.groupId || 'com.example'}.${proj?.name?.toLowerCase() || 'mylibrary'}`,
      applicationId: cur.applicationId || `${proj?.groupId || 'com.example'}.${proj?.name?.toLowerCase() || 'myapp'}`,
      jdkVersion: cur.jdkVersion || preset.jdkVersion,
      gradleVersion: preset.gradleVersion,
      agpVersion: pType === 'JAVA_LIBRARY' ? null : preset.agpVersion,
      compileSdk: pType === 'JAVA_LIBRARY' ? null : preset.compileSdk,
      minSdk: pType === 'JAVA_LIBRARY' ? null : preset.minSdk,
      targetSdk: pType === 'JAVA_LIBRARY' ? null : preset.targetSdk,
      githubOwner: proj?.author || 'USERNAME',
      githubRepo: proj?.repoName || proj?.name || 'repository',
    };
  }

  /**
   * Generate complete, clean, production-ready configuration files.
   */
  static generateConfigurationFiles(config, existingFiles = {}) {
    const pType = config.projectType;
    const isAndroid = pType === 'ANDROID_LIBRARY' || pType === 'ANDROID_APP';
    const isLibrary = pType === 'ANDROID_LIBRARY' || pType === 'JAVA_LIBRARY';
    const isApp = pType === 'ANDROID_APP';

    const files = {};

    // 1. settings.gradle
    const rootName = config.name || 'MyProject';
    files['settings.gradle'] = `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

rootProject.name = "${rootName}"
`;

    // 2. gradle-wrapper.properties
    files['gradle/wrapper/gradle-wrapper.properties'] = `distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-${config.gradleVersion}-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
`;

    // 3. build.gradle
    if (pType === 'JAVA_LIBRARY') {
      files['build.gradle'] = `plugins {
    id 'java-library'
    id 'maven-publish'
}

group = '${config.groupId}'
version = '${config.version}'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(${config.jdkVersion})
    }
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
}

tasks.named('test') {
    useJUnitPlatform()
}

publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
            groupId = '${config.groupId}'
            artifactId = '${config.artifactId}'
            version = '${config.version}'
        }
    }
}
`;
    } else if (pType === 'ANDROID_LIBRARY') {
      files['build.gradle'] = `plugins {
    id 'com.android.library' version '${config.agpVersion}'
    id 'maven-publish'
}

android {
    namespace '${config.namespace}'
    compileSdk ${config.compileSdk}

    defaultConfig {
        minSdk ${config.minSdk}
        ${config.targetSdk ? `targetSdk ${config.targetSdk}` : ''}
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_${config.jdkVersion === '8' ? '1_8' : config.jdkVersion}
        targetCompatibility JavaVersion.VERSION_${config.jdkVersion === '8' ? '1_8' : config.jdkVersion}
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}

afterEvaluate {
    publishing {
        publications {
            release(MavenPublication) {
                from components.release
                groupId = '${config.groupId}'
                artifactId = '${config.artifactId}'
                version = '${config.version}'
            }
        }
    }
}
`;
    } else {
      // ANDROID_APP
      files['build.gradle'] = `plugins {
    id 'com.android.application' version '${config.agpVersion}'
}

android {
    namespace '${config.namespace}'
    compileSdk ${config.compileSdk}

    defaultConfig {
        applicationId '${config.applicationId}'
        minSdk ${config.minSdk}
        targetSdk ${config.targetSdk}
        versionCode 1
        versionName '${config.version}'
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_${config.jdkVersion === '8' ? '1_8' : config.jdkVersion}
        targetCompatibility JavaVersion.VERSION_${config.jdkVersion === '8' ? '1_8' : config.jdkVersion}
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.12.0'
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}
`;
    }

    // 4. AndroidManifest.xml
    if (isAndroid) {
      if (isApp) {
        files['src/main/AndroidManifest.xml'] = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="${config.name}"
        android:roundIcon="@android:drawable/sym_def_app_icon"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>`;
      } else {
        files['src/main/AndroidManifest.xml'] = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Android Library Manifest -->
</manifest>`;
      }
    }

    // 5. jitpack.yml (for libraries)
    if (isLibrary) {
      files['jitpack.yml'] = `jdk:
  - openjdk${config.jdkVersion || '17'}
before_install:
  - chmod +x gradlew
install:
  - ./gradlew clean assembleRelease publishToMavenLocal --stacktrace
`;
    }

    // 6. README.md
    files['README.md'] = this.generateReadme(config);

    // 7. GitHub Actions workflow
    files['.github/workflows/build.yml'] = this.generateWorkflow(config, existingFiles);

    return files;
  }

  /**
   * Generate dynamic, highly accurate README with actual coordinates.
   */
  static generateReadme(config) {
    const isLib = config.projectType === 'JAVA_LIBRARY' || config.projectType === 'ANDROID_LIBRARY';
    const owner = config.githubOwner || 'YOUR_GITHUB_USERNAME';
    const repo = config.githubRepo || 'YOUR_REPOSITORY';
    const tag = config.version ? `v${config.version}` : 'v1.0.0';
    const coordinate = `com.github.${owner}:${repo}:${tag}`;

    if (config.projectType === 'ANDROID_APP') {
      return `# ${config.name}

[![Build Status](https://github.com/${owner}/${repo}/actions/workflows/build.yml/badge.svg)](https://github.com/${owner}/${repo}/actions)

An Android application built with modern Android toolchain:
- **Compile SDK**: ${config.compileSdk}
- **Min SDK**: ${config.minSdk}
- **Target SDK**: ${config.targetSdk}
- **AGP**: ${config.agpVersion}
- **JDK**: ${config.jdkVersion}

## 🚀 Building & Running

\`\`\`bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test
\`\`\`

## 📦 Releases

Download the latest APK releases directly from the [GitHub Releases](https://github.com/${owner}/${repo}/releases) page.
`;
    }

    // Library README
    return `# ${config.name}

[![](https://jitpack.io/v/${owner}/${repo}.svg)](https://jitpack.io/#${owner}/${repo})
[![Build Status](https://github.com/${owner}/${repo}/actions/workflows/build.yml/badge.svg)](https://github.com/${owner}/${repo}/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

${config.projectType === 'ANDROID_LIBRARY' ? 'A lightweight and modular Android Library (AAR).' : 'A modern Java Library.'}

## 📥 Installation

Add the JitPack repository to your build file.

### Gradle (Groovy DSL)

Add it in your root \`settings.gradle\` at the end of repositories:

\`\`\`groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
\`\`\`

Step 2: Add the dependency to your module \`build.gradle\`:

\`\`\`groovy
dependencies {
    implementation '${coordinate}'
}
\`\`\`

### Gradle (Kotlin DSL)

In \`settings.gradle.kts\`:

\`\`\`kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
\`\`\`

In \`build.gradle.kts\`:

\`\`\`kotlin
dependencies {
    implementation("${coordinate}")
}
\`\`\`

### Maven

\`\`\`xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.${owner}</groupId>
    <artifactId>${repo}</artifactId>
    <version>${tag}</version>
</dependency>
\`\`\`

## ⚙️ Requirements & Compatibility
- **JDK**: ${config.jdkVersion}+
- **Gradle**: ${config.gradleVersion}+
${config.projectType === 'ANDROID_LIBRARY' ? `- **Android compileSdk**: ${config.compileSdk}+\n- **minSdk**: ${config.minSdk}+` : ''}

## 📄 License
Released under the [MIT License](LICENSE).
`;
  }

  /**
   * Resolve the dynamically correct Gradle build task based on actual project modules and type.
   */
  static resolveCorrectBuildTask(files = {}, projectType = 'ANDROID_LIBRARY') {
    const declaredModules = [];
    const settingsContent = files['settings.gradle'] || files['settings.gradle.kts'] || '';
    if (settingsContent) {
      for (const line of settingsContent.split('\n')) {
        if (/^\s*include\b/.test(line)) {
          const tokens = line.match(/(?:['"])([^'"]+)(?:['"])/g) || [];
          for (let t of tokens) {
            t = t.replace(/^['":]+|['"]+$/g, '').trim();
            if (t && !declaredModules.includes(t)) declaredModules.push(t);
          }
        }
      }
    }

    if (projectType === 'JAVA_LIBRARY') {
      const javaMod = declaredModules.find(m => {
        const bg = files[`${m}/build.gradle`] || files[`${m}/build.gradle.kts`] || '';
        return bg.includes('java-library') || bg.includes("'java'") || bg.includes('"java"');
      });
      return javaMod ? `:${javaMod}:build` : 'build';
    }

    if (projectType === 'ANDROID_APP') {
      const appMod = declaredModules.find(m => {
        const bg = files[`${m}/build.gradle`] || files[`${m}/build.gradle.kts`] || '';
        return bg.includes('com.android.application');
      }) || declaredModules.find(m => m === 'app') || declaredModules[0];
      return appMod ? `:${appMod}:assembleRelease` : 'assembleRelease';
    }

    // Android Library
    const libMod = declaredModules.find(m => {
      const bg = files[`${m}/build.gradle`] || files[`${m}/build.gradle.kts`] || '';
      return bg.includes('com.android.library');
    }) || declaredModules.find(m => m !== 'app' && m !== 'sample') || declaredModules[0];

    return libMod ? `:${libMod}:assembleRelease` : 'assembleRelease';
  }

  /**
   * Generate GitHub Actions workflow.
   */
  static generateWorkflow(config, files = {}) {
    const isJava = config.projectType === 'JAVA_LIBRARY';
    const isLib = config.projectType === 'JAVA_LIBRARY' || config.projectType === 'ANDROID_LIBRARY';
    const primaryTask = this.resolveCorrectBuildTask(files, config.projectType);
    const testTask = isJava ? 'test' : 'test';

    return `name: Build & Verify

on:
  push:
    branches: [ main, master ]
    tags: [ 'v*' ]
  pull_request:
    branches: [ main, master ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - name: Checkout source code
      uses: actions/checkout@v4
      with:
        fetch-depth: 0

    - name: Set up JDK ${config.jdkVersion}
      uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: '${config.jdkVersion}'
        cache: 'gradle'

    - name: Grant execute permission for gradlew
      run: chmod +x gradlew

    - name: Build and verify with Gradle
      run: |
        echo "Executing build task: ${primaryTask}"
        ./gradlew clean ${primaryTask} --stacktrace || {
          echo "Specific module task failed. Falling back to project-level build tasks..."
          if ./gradlew tasks --all 2>/dev/null | grep -E "\\bassembleRelease\\b"; then
            ./gradlew clean assembleRelease --stacktrace
          elif ./gradlew tasks --all 2>/dev/null | grep -E "\\bbuild\\b"; then
            ./gradlew clean build --stacktrace
          else
            ./gradlew clean assemble --stacktrace
          fi
        }
`;
  }
}
