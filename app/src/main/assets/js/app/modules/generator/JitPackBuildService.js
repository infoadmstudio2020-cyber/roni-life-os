/**
 * JitPackBuildService — live build monitoring, log retrieval, error analysis,
 * and automated remediation for JitPack builds.
 */
import { JitPackAnalyzerEngine } from './JitPackAnalyzerEngine.js';
import { GradleAstHelper } from './GradleAstHelper.js';

export class JitPackBuildService {

  /**
   * Fetch build status for all versions of a repository from JitPack API.
   * @param {string} owner
   * @param {string} repo
   * @returns {Promise<{success: boolean, data?: Record<string, string>, error?: string}>}
   */
  static async fetchBuildStatus(owner, repo) {
    if (!owner || !repo) {
      return { success: false, error: 'GitHub owner and repository name are required.' };
    }
    const cleanOwner = encodeURIComponent(owner.trim());
    const cleanRepo = encodeURIComponent(repo.trim());
    const url = `https://jitpack.io/api/builds/${cleanOwner}/${cleanRepo}`;

    try {
      const resp = await fetch(url, { method: 'GET', headers: { Accept: 'application/json' } });
      if (!resp.ok) {
        if (resp.status === 404) {
          return {
            success: true,
            data: {},
            note: 'No builds found yet on JitPack. Build will trigger when requested.'
          };
        }
        return { success: false, error: `JitPack API HTTP ${resp.status}: ${resp.statusText}` };
      }
      const data = await resp.json();
      return { success: true, data };
    } catch (err) {
      return {
        success: false,
        error: `Network or CORS error connecting to JitPack API: ${err.message}`
      };
    }
  }

  /**
   * Fetch the raw build log for a specific tag or commit.
   * @param {string} owner
   * @param {string} repo
   * @param {string} tag
   * @returns {Promise<{success: boolean, log?: string, status?: string, error?: string}>}
   */
  static async fetchBuildLog(owner, repo, tag) {
    if (!owner || !repo || !tag) {
      return { success: false, error: 'Owner, repository, and release tag are required.' };
    }
    const cleanOwner = encodeURIComponent(owner.trim());
    const cleanRepo = encodeURIComponent(repo.trim());
    const cleanTag = encodeURIComponent(tag.trim());
    const logUrl = `https://jitpack.io/com/github/${cleanOwner}/${cleanRepo}/${cleanTag}/build.log`;

    try {
      const resp = await fetch(logUrl, { method: 'GET' });
      if (!resp.ok) {
        if (resp.status === 404) {
          return {
            success: false,
            status: 'not_found',
            error: `Build log not found (HTTP 404). This release has not been built on JitPack yet. Visiting https://jitpack.io/#${cleanOwner}/${cleanRepo}/${cleanTag} will trigger the build.`
          };
        }
        return {
          success: false,
          error: `Failed to fetch build log (HTTP ${resp.status} ${resp.statusText})`
        };
      }
      const log = await resp.text();
      return {
        success: true,
        log,
        status: log.includes('BUILD SUCCESSFUL') ? 'ok' : (log.includes('BUILD FAILED') ? 'error' : 'unknown')
      };
    } catch (err) {
      return {
        success: false,
        error: `Could not retrieve log (CORS/Network error): ${err.message}. You can view it directly at ${logUrl}`
      };
    }
  }

  /**
   * Analyze raw JitPack build log or project configuration to identify failure causes.
   * @param {string} logText
   * @param {object} projectFiles
   * @returns {Array<{id: string, severity: 'error'|'warning', title: string, explanation: string, fixActionLabel: string}>}
   */
  static diagnose(logText = '', projectFiles = {}) {
    const issues = [];
    const log = String(logText || '');
    const files = projectFiles || {};
    const jpContent = String(files['jitpack.yml'] || '');

    // 1. Missing or default Java 8 runtime
    if (
      log.includes('Android Gradle plugin requires Java 17 to run') ||
      log.includes('You are currently using Java 8') ||
      log.includes('compileReleaseJavaWithJavac: invalid source release: 17') ||
      log.includes('source release 17 requires target release 17') ||
      (!files['jitpack.yml'] && log.includes('BUILD FAILED'))
    ) {
      issues.push({
        id: 'jdk_mismatch',
        severity: 'error',
        title: 'Java 17 Requirement Mismatch (Using Java 8)',
        explanation: 'Android Gradle Plugin (AGP 8.x+) requires Java 17. Without a valid jitpack.yml specifying openjdk17, JitPack builds using the default legacy Java 8 container.',
        fixActionLabel: 'Configure openjdk17 in jitpack.yml'
      });
    }

    // 2. Git safe.directory permission error
    if (
      log.includes('detected dubious ownership in repository') ||
      log.includes('safe.directory') ||
      (jpContent.includes('safe.directory /build') && !jpContent.includes("safe.directory '*'"))
    ) {
      issues.push({
        id: 'safe_directory',
        severity: 'error',
        title: 'Git Dubious Ownership / safe.directory Restriction',
        explanation: 'Git 2.35+ restricts operations across different user IDs in Docker containers. The narrow path "/build" fails if JitPack checks out into another temporary directory.',
        fixActionLabel: 'Add safe.directory \'*\' wildcard to jitpack.yml'
      });
    }

    // 3. Application or sample module publishToMavenLocal failure
    if (
      log.includes('Task \':sample:publishToMavenLocal\' not found') ||
      log.includes('Task \':app:publishToMavenLocal\' not found') ||
      (jpContent.includes(':sample:publishToMavenLocal') || jpContent.includes(':app:publishToMavenLocal'))
    ) {
      issues.push({
        id: 'sample_publish',
        severity: 'error',
        title: 'Invalid publishToMavenLocal on Sample/Application Module',
        explanation: 'Application modules (com.android.application) do not produce Maven publications. Running publishToMavenLocal on them crashes the JitPack build.',
        fixActionLabel: 'Remove publishToMavenLocal from application modules'
      });
    }

    // 4. Module task name mismatch
    const taskNotFoundMatch = log.match(/Task '([^']+):publishToMavenLocal' not found in root project/);
    if (taskNotFoundMatch) {
      const missingMod = taskNotFoundMatch[1];
      issues.push({
        id: 'module_task_mismatch',
        severity: 'error',
        title: `Module Task :${missingMod}:publishToMavenLocal Not Found`,
        explanation: `The module :${missingMod} specified in jitpack.yml does not match the actual module names declared in settings.gradle.`,
        fixActionLabel: 'Synchronize jitpack.yml module names with settings.gradle'
      });
    }

    // 5. Javadoc doclint failure
    if (
      log.includes('javaDocReleaseGeneration FAILED') ||
      log.includes('javadoc: error') ||
      (log.includes('javadoc') && log.includes('error: reference not found'))
    ) {
      issues.push({
        id: 'javadoc_failure',
        severity: 'error',
        title: 'Javadoc doclint Validation Crash',
        explanation: 'Java 17 Javadoc compiler enforces strict doclint by default, failing the build on missing parameters, unresolved tags, or syntax warnings.',
        fixActionLabel: 'Add Xdoclint:none protection to library build.gradle'
      });
    }

    // 6. Repository Mode conflict in settings.gradle
    if (
      log.includes('RepositoriesMode.FAIL_ON_PROJECT_REPOS') ||
      log.includes('Build was configured to prefer settings repositories over project repositories') ||
      String(files['settings.gradle'] || '').includes('RepositoriesMode.FAIL_ON_PROJECT_REPOS')
    ) {
      issues.push({
        id: 'repo_mode_conflict',
        severity: 'warning',
        title: 'Strict Repository Mode Conflict in settings.gradle',
        explanation: 'RepositoriesMode.FAIL_ON_PROJECT_REPOS causes Gradle to fail if any dependency or subproject declares custom repositories. Switching to PREFER_SETTINGS prevents this build failure.',
        fixActionLabel: 'Switch to RepositoriesMode.PREFER_SETTINGS'
      });
    }

    // 7. Missing executable permission on gradlew
    if (
      log.includes('Permission denied') ||
      log.includes('gradlew: Permission denied') ||
      (jpContent && !jpContent.includes('chmod +x gradlew'))
    ) {
      issues.push({
        id: 'gradlew_permission',
        severity: 'error',
        title: 'Missing Executable Permissions on gradlew',
        explanation: 'JitPack runs `./gradlew`. If git repository loses executable bit or before_install does not chmod +x, the build fails immediately.',
        fixActionLabel: 'Add chmod +x gradlew to before_install'
      });
    }

    // 8. Variant resolution & publication failure (No matching variant / No variants exist)
    if (
      log.includes('No matching variant') ||
      log.includes('No variants exist') ||
      log.includes("SoftwareComponent 'release' has no variants") ||
      log.includes("Could not get unknown property 'release' for SoftwareComponentContainer") ||
      log.includes('Could not resolve project')
    ) {
      issues.push({
        id: 'variant_resolution_mismatch',
        severity: 'error',
        title: 'Variant Resolution Mismatch (No matching variant / No variants exist)',
        explanation: 'AGP requires explicit variant resolution fallbacks (matchingFallbacks = ["release"]) and singleVariant("release") inside android.publishing for AAR publication.',
        fixActionLabel: 'Apply variant fallbacks and singleVariant publishing configuration'
      });
    }

    return issues;
  }

  /**
   * Apply an automated fix for a recognized diagnosis ID.
   * @param {string} issueId
   * @param {object} projectService
   * @returns {Promise<{success: boolean, message: string}>}
   */
  static async applyAutoFix(issueId, projectService) {
    if (!projectService?.current) {
      return { success: false, message: 'No active project loaded.' };
    }
    const files = projectService.current.files || {};

    switch (issueId) {
      case 'jdk_mismatch':
      case 'gradlew_permission':
      case 'safe_directory':
      case 'sample_publish':
      case 'module_task_mismatch': {
        const freshYml = JitPackAnalyzerEngine.generateDefaultJitpackYmlForFiles(files);
        await projectService.setFile('jitpack.yml', freshYml);
        return {
          success: true,
          message: 'Generated and applied hardened jitpack.yml (Java 17, safe.directory wildcard, verified library modules).'
        };
      }

      case 'repo_mode_conflict': {
        let settings = files['settings.gradle'] || files['settings.gradle.kts'];
        if (settings) {
          const path = files['settings.gradle'] ? 'settings.gradle' : 'settings.gradle.kts';
          settings = settings.replace('RepositoriesMode.FAIL_ON_PROJECT_REPOS', 'RepositoriesMode.PREFER_SETTINGS');
          await projectService.setFile(path, settings);
          return { success: true, message: `Updated ${path} to RepositoriesMode.PREFER_SETTINGS.` };
        }
        return { success: false, message: 'settings.gradle not found.' };
      }

      case 'javadoc_failure': {
        // Find library build.gradle files
        let updatedCount = 0;
        for (const [path, content] of Object.entries(files)) {
          if (path.endsWith('build.gradle') && !path.startsWith('app/') && !path.startsWith('sample/')) {
            if (!content.includes('Xdoclint:none')) {
              const javadocBlock = `
// Protect Javadoc generation from doclint failures on JDK 17
tasks.withType(Javadoc).configureEach {
    options.addStringOption('Xdoclint:none', '-quiet')
    options.encoding = 'UTF-8'
}
`;
              await projectService.setFile(path, `${content}\n${javadocBlock}`);
              updatedCount++;
            }
          }
        }
        return {
          success: true,
          message: `Added Javadoc doclint protection to ${updatedCount} build.gradle file(s).`
        };
      }

      case 'variant_resolution_mismatch': {
        let updatedCount = 0;
        for (const [path, content] of Object.entries(files)) {
          if (path.endsWith('build.gradle') || path.endsWith('build.gradle.kts')) {
            const isKts = path.endsWith('.kts');
            let updated = content;
            if (updated.includes('android {') && !updated.includes('singleVariant("release")') && !updated.includes("singleVariant('release')")) {
              const ensured = GradleAstHelper.ensureAndroidPublishing(updated, isKts);
              if (ensured.modified) updated = ensured.content;
            }
            if (!updated.includes("matchingFallbacks")) {
              if (updated.includes('buildTypes {')) {
                updated = updated.replace(/buildTypes\s*\{/, `buildTypes {\n        debug {\n            matchingFallbacks = ['release']\n        }`);
              }
            }
            if (updated !== content) {
              await projectService.setFile(path, updated);
              updatedCount++;
            }
          }
        }
        return {
          success: true,
          message: `Applied variant resolution fallbacks and publishing configuration to ${updatedCount} file(s).`
        };
      }

      default:
        return { success: false, message: `Unknown issue ID: ${issueId}` };
    }
  }
}
