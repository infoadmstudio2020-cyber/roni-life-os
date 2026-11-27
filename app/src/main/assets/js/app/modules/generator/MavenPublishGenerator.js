/**
 * MavenPublishGenerator.js — Comprehensive Maven Publish & JitPack Support.
 *
 * Capabilities:
 * - Dynamically extracts & configures groupId, artifactId, version, GitHub repo, tag
 * - Generates standard Android library publishing { publications { release(MavenPublication) { ... } } }
 * - Generates android.publishing { singleVariant("release") { ... } } and plugins { id 'maven-publish' }
 * - Generates JitPack-compatible jitpack.yml with Gradle publish command
 * - Generates Consumer snippets for Groovy and Kotlin DSL (settings.gradle & build.gradle)
 * - Safe non-destructive patching of existing library / app build.gradle
 * - Real-time validation & error reporting
 */

import { TransitiveDependencyEngine } from '../analyzer/TransitiveDependencyEngine.js';

import { GradleAstHelper } from './GradleAstHelper.js';

export class MavenPublishGenerator {
  /**
   * Find the most appropriate library or module build file in the project.
   * @param {Object.<string, string|Uint8Array>} files
   * @returns {{ path: string, moduleName: string, isKts: boolean, isApp: boolean } | null}
   */
  static detectTargetModule(files = {}) {
    const paths = Object.keys(files);

    // Candidates priority:
    // 1. Explicit library build files
    const libBuilds = paths.filter(p =>
      (p.includes('library/build.gradle') || p.includes('-library/build.gradle')) &&
      !p.includes('/sample/') && !p.includes('/app/')
    );
    if (libBuilds.length > 0) {
      const p = libBuilds[0];
      const modName = p.split('/')[0];
      return { path: p, moduleName: modName, isKts: p.endsWith('.kts'), isApp: false };
    }

    // 2. Any module with com.android.library plugin
    for (const p of paths) {
      if (p.endsWith('build.gradle') || p.endsWith('build.gradle.kts')) {
        if (p === 'build.gradle' || p === 'build.gradle.kts') continue; // skip root
        const content = String(files[p] || '');
        if (content.includes('com.android.library')) {
          const modName = p.split('/')[0];
          return { path: p, moduleName: modName, isKts: p.endsWith('.kts'), isApp: false };
        }
      }
    }

    // 3. app/build.gradle if this is an app or single module
    const appBuild = paths.find(p => p === 'app/build.gradle' || p === 'app/build.gradle.kts');
    if (appBuild) {
      return { path: appBuild, moduleName: 'app', isKts: appBuild.endsWith('.kts'), isApp: true };
    }

    // 4. Any subfolder build.gradle
    const anySubBuild = paths.find(p => p.includes('/') && (p.endsWith('build.gradle') || p.endsWith('build.gradle.kts')));
    if (anySubBuild) {
      const modName = anySubBuild.split('/')[0];
      return { path: anySubBuild, moduleName: modName, isKts: anySubBuild.endsWith('.kts'), isApp: false };
    }

    // 5. Root build.gradle as fallback
    const rootBuild = paths.find(p => p === 'build.gradle' || p === 'build.gradle.kts');
    if (rootBuild) {
      return { path: rootBuild, moduleName: '', isKts: rootBuild.endsWith('.kts'), isApp: false };
    }

    return null;
  }

  /**
   * Extract or infer current metadata from project files and project object.
   * @param {Object.<string, string|Uint8Array>} files
   * @param {object} [project]
   * @returns {object}
   */
  static detectMetadata(files = {}, project = {}) {
    let groupId = project?.packageName || '';
    let artifactId = project?.repoName || project?.name || '';
    let version = project?.version || '1.0.0';
    let author = project?.author || '';
    let description = project?.description || 'Android Library';
    let license = project?.license || 'Apache-2.0';
    let repoName = project?.repoName || '';

    // Check gradle.properties for standard Maven coordinates
    const gradleProps = String(files['gradle.properties'] || '');
    if (gradleProps) {
      const gMatch = gradleProps.match(/^GROUP\s*=\s*(.+)$/m) || gradleProps.match(/^groupId\s*=\s*(.+)$/m);
      if (gMatch) groupId = gMatch[1].trim();

      const aMatch = gradleProps.match(/^POM_ARTIFACT_ID\s*=\s*(.+)$/m) || gradleProps.match(/^artifactId\s*=\s*(.+)$/m);
      if (aMatch) artifactId = aMatch[1].trim();

      const vMatch = gradleProps.match(/^VERSION_NAME\s*=\s*(.+)$/m) || gradleProps.match(/^version\s*=\s*(.+)$/m);
      if (vMatch) version = vMatch[1].trim();
    }

    // Check build.gradle files for publishing configuration
    for (const [p, content] of Object.entries(files)) {
      if (typeof content !== 'string') continue;
      if (p.endsWith('.gradle') || p.endsWith('.gradle.kts')) {
        const gMatch = content.match(/groupId\s*=?\s*['"]([^'"]+)['"]/);
        if (gMatch && !groupId) groupId = gMatch[1];

        const aMatch = content.match(/artifactId\s*=?\s*['"]([^'"]+)['"]/);
        if (aMatch && !artifactId) artifactId = aMatch[1];

        const vMatch = content.match(/version\s*=?\s*['"]([^'"]+)['"]/);
        if (vMatch && !version) version = vMatch[1];

        const descMatch = content.match(/description\s*=?\s*['"]([^'"]+)['"]/);
        if (descMatch && descMatch[1] && descMatch[1] !== 'Android Library') description = descMatch[1];
      }
    }

    // Check Git config for GitHub username / repo
    const gitConfig = String(files['.git/config'] || '');
    if (gitConfig) {
      const ghMatch = gitConfig.match(/github\.com[:/]([^/]+)\/([^/\s.]+)/i);
      if (ghMatch) {
        if (!author) author = ghMatch[1];
        if (!repoName) repoName = ghMatch[2];
      }
    }

    if (!artifactId && repoName) artifactId = repoName;
    if (!artifactId && project?.name) artifactId = project.name.toLowerCase().replace(/[^a-z0-9_-]/g, '-');
    if (!repoName && artifactId) repoName = artifactId;

    // Fallbacks
    if (!groupId) groupId = 'com.github.' + (author || 'username');
    if (!artifactId) artifactId = 'library';
    if (!version) version = '1.0.0';
    if (!author) author = 'username';

    const target = this.detectTargetModule(files);

    return {
      groupId,
      artifactId,
      version,
      author,
      description,
      license,
      repoName,
      tag: version.startsWith('v') ? version : `v${version}`,
      targetModule: target?.moduleName || 'library',
      targetFilePath: target?.path || 'library/build.gradle',
      isKts: !!target?.isKts,
    };
  }

  /**
   * Validate configuration fields and return errors and warnings.
   * @param {object} cfg
   * @param {Object.<string, string|Uint8Array>} [files]
   * @returns {{ valid: boolean, errors: string[], warnings: string[], info: string[] }}
   */
  static validate(cfg = {}, files = {}) {
    const errors = [];
    const warnings = [];
    const info = [];

    // 1. Group ID validation
    if (!cfg.groupId || !cfg.groupId.trim()) {
      errors.push('Group ID is required (e.g. "com.github.username" or "com.example.lib").');
    } else {
      const g = cfg.groupId.trim();
      if (!/^[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$/.test(g)) {
        errors.push(`Group ID "${g}" format is invalid. Must be dot-separated (e.g. com.github.username).`);
      } else if (!g.startsWith('com.github.') && !g.includes('.')) {
        warnings.push(`For JitPack, the standard Group ID is usually "com.github.${cfg.author || 'username'}".`);
      }
    }

    // 2. Artifact ID validation
    if (!cfg.artifactId || !cfg.artifactId.trim()) {
      errors.push('Artifact ID is required (e.g. "my-android-library").');
    } else {
      const a = cfg.artifactId.trim();
      if (!/^[a-zA-Z0-9_.-]+$/.test(a)) {
        errors.push(`Artifact ID "${a}" contains invalid characters. Use letters, numbers, hyphens, and underscores.`);
      }
    }

    // 3. Version validation
    if (!cfg.version || !cfg.version.trim()) {
      errors.push('Version is required (e.g. "1.0.0").');
    } else {
      const v = cfg.version.trim();
      if (!/^[a-zA-Z0-9_.-]+$/.test(v)) {
        errors.push(`Version "${v}" format is invalid.`);
      }
    }

    // 4. GitHub repo & owner check
    if (!cfg.author || !cfg.author.trim() || cfg.author === 'USERNAME' || cfg.author === 'username') {
      warnings.push('GitHub Owner/Username is not specified. JitPack requires a public GitHub repository.');
    }
    if (!cfg.repoName || !cfg.repoName.trim() || cfg.repoName === 'REPO') {
      warnings.push('GitHub Repository name is not set.');
    }

    // 5. Existing project inspection
    if (files && Object.keys(files).length > 0) {
      const target = this.detectTargetModule(files);
      if (!target) {
        warnings.push('No Android library module build.gradle was detected in project files.');
      } else {
        const modContent = String(files[target.path] || '');
        if (!modContent.includes('maven-publish')) {
          warnings.push(`Target ${target.path} is missing plugin id 'maven-publish'.`);
        }
        if (!modContent.includes('singleVariant("release")') && !modContent.includes("singleVariant('release')")) {
          warnings.push(`Target ${target.path} is missing android.publishing singleVariant("release") configuration.`);
        }
        if (!modContent.includes('release(MavenPublication)') && !modContent.includes('create("release", MavenPublication)')) {
          warnings.push(`Target ${target.path} is missing release(MavenPublication) configuration.`);
        }
      }

      if (!('jitpack.yml' in files) || !files['jitpack.yml']) {
        warnings.push('jitpack.yml is missing in the project root.');
      } else {
        const yml = String(files['jitpack.yml']);
        if (!yml.includes('openjdk17') && !yml.includes('openjdk21') && !yml.includes('jdk:')) {
          warnings.push('jitpack.yml should specify JDK 17 (openjdk17) for modern AGP 8.x compatibility.');
        }
        if (!yml.includes('assembleRelease') && !yml.includes('publishToMavenLocal')) {
          warnings.push('jitpack.yml install instruction should run assembleRelease and/or publishToMavenLocal.');
        }
      }
    }

    if (errors.length === 0 && warnings.length === 0) {
      info.push('Maven Publish & JitPack configuration is valid and ready.');
    }

    return {
      valid: errors.length === 0,
      errors,
      warnings,
      info,
    };
  }

  /**
   * Generate the standalone Gradle Maven Publication code block for Groovy DSL.
   * @param {object} cfg
   * @returns {string}
   */
  static generateGroovyPublishingBlock(cfg) {
    const groupId = cfg.groupId || 'com.example.library';
    const artifactId = cfg.artifactId || 'library';
    const version = cfg.version || '1.0.0';
    const author = cfg.author || 'Developer';
    const repo = cfg.repoName || artifactId;
    const desc = cfg.description || 'Android Library';
    const license = cfg.license || 'Apache-2.0';

    return `// =====================================================
// MAVEN PUBLISH CONFIGURATION (JitPack & Maven Central)
// =====================================================
afterEvaluate {
    publishing {
        publications {
            release(MavenPublication) {
                from components.findByName('release') ?: components.findByName('java') ?: components.findByName('default')
                groupId    = '${groupId}'
                artifactId = '${artifactId}'
                version    = '${version}'

                pom {
                    name        = '${artifactId}'
                    description = '${desc.replace(/'/g, "\\'")}'
                    url         = 'https://github.com/${author}/${repo}'
                    licenses {
                        license {
                            name = '${license}'
                            url  = 'https://opensource.org/licenses/${license}'
                        }
                    }
                    developers {
                        developer {
                            id   = '${author}'
                            name = '${author}'
                        }
                    }
                    scm {
                        connection         = 'scm:git:github.com/${author}/${repo}.git'
                        developerConnection = 'scm:git:ssh://github.com/${author}/${repo}.git'
                        url                = 'https://github.com/${author}/${repo}/tree/main'
                    }
                }
            }
        }
    }

    // Configure task ordering without circular or recursive execution
    tasks.matching { it.name in ['publishToMavenLocal', 'publishReleasePublicationToMavenLocal'] }.configureEach {
        mustRunAfter(tasks.matching { it.name == 'assembleRelease' })
    }
}`;
  }

  /**
   * Generate the standalone Gradle Maven Publication code block for Kotlin DSL (.kts).
   * @param {object} cfg
   * @returns {string}
   */
  static generateKotlinPublishingBlock(cfg) {
    const groupId = cfg.groupId || 'com.example.library';
    const artifactId = cfg.artifactId || 'library';
    const version = cfg.version || '1.0.0';
    const author = cfg.author || 'Developer';
    const repo = cfg.repoName || artifactId;
    const desc = cfg.description || 'Android Library';
    const license = cfg.license || 'Apache-2.0';

    return `// =====================================================
// MAVEN PUBLISH CONFIGURATION (JitPack & Maven Central)
// =====================================================
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                val releaseComponent = components.findByName("release") ?: components.findByName("java") ?: components.findByName("default")
                if (releaseComponent != null) {
                    from(releaseComponent)
                }
                this.groupId = "${groupId}"
                this.artifactId = "${artifactId}"
                this.version = "${version}"

                pom {
                    name.set("${artifactId}")
                    description.set("${desc.replace(/"/g, '\\"')}")
                    url.set("https://github.com/${author}/${repo}")
                    licenses {
                        license {
                            name.set("${license}")
                            url.set("https://opensource.org/licenses/${license}")
                        }
                    }
                    developers {
                        developer {
                            id.set("${author}")
                            name.set("${author}")
                        }
                    }
                    scm {
                        connection.set("scm:git:github.com/${author}/${repo}.git")
                        developerConnection.set("scm:git:ssh://github.com/${author}/${repo}.git")
                        url.set("https://github.com/${author}/${repo}/tree/main")
                    }
                }
            }
        }
    }

    // Configure task ordering without circular or recursive execution
    tasks.matching { it.name in listOf("publishToMavenLocal", "publishReleasePublicationToMavenLocal") }.configureEach {
        mustRunAfter(tasks.matching { it.name == "assembleRelease" })
    }
}`;
  }

  /**
   * Generate JitPack configuration YAML file.
   * Supports multi-module builds automatically.
   * @param {object} cfg
   * @param {Object.<string, string|Uint8Array>} [files]
   * @returns {string}
   */
  static generateJitpackYml(cfg, files = {}) {
    const jdk = cfg.jdkVersion || 'openjdk17';
    let modules = [cfg.targetModule || 'library'];

    if (files && Object.keys(files).length > 0) {
      const modInfo = TransitiveDependencyEngine.detectProjectModules(files);
      if (modInfo.isMultiModule) {
        modules = modInfo.modules.filter(m => m.isLibrary).map(m => m.name);
        if (modules.length === 0) modules = modInfo.modules.map(m => m.name);
      }
    }

    return TransitiveDependencyEngine.patchJitpackYml('', {
      modules,
      jdkVersion: jdk
    });
  }

  /**
   * Generate consumer integration snippets.
   * @param {object} cfg
   * @returns {object}
   */
  static generateConsumerSnippets(cfg) {
    const author = cfg.author || 'USERNAME';
    const repo = cfg.repoName || cfg.artifactId || 'REPO';
    const version = cfg.version || '1.0.0';
    const tag = cfg.tag || (version.startsWith('v') ? version : `v${version}`);

    // JitPack coordinate formats:
    // Standard: com.github.User:Repo:Tag
    // If custom Group ID is used on JitPack, JitPack matches with com.github.User:Repo:Tag
    const jitpackCoord = `com.github.${author}:${repo}:${tag}`;

    return {
      jitpackCoord,
      badgeMarkdown: `[![JitPack](https://jitpack.io/v/${author}/${repo}.svg)](https://jitpack.io/#${author}/${repo})`,
      badgeHtml: `<a href="https://jitpack.io/#${author}/${repo}"><img src="https://jitpack.io/v/${author}/${repo}.svg" alt="JitPack" /></a>`,
      jitpackUrl: `https://jitpack.io/#${author}/${repo}`,
      jitpackVersionUrl: `https://jitpack.io/#${author}/${repo}/${tag}`,

      // 1. Modern settings.gradle (Groovy)
      settingsGroovy: `dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}`,

      // 2. Modern settings.gradle.kts (Kotlin DSL)
      settingsKotlin: `dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}`,

      // 3. Legacy root build.gradle (Groovy)
      rootBuildGroovy: `allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}`,

      // 4. Consumer app/build.gradle (Groovy)
      appBuildGroovy: `dependencies {
    implementation '${jitpackCoord}'
}`,

      // 5. Consumer app/build.gradle.kts (Kotlin DSL)
      appBuildKotlin: `dependencies {
    implementation("${jitpackCoord}")
}`,
    };
  }

  /**
   * Safely patch a module's build.gradle with Maven Publish configuration.
   * Preserves developer's existing plugins, dependencies, and android config.
   * @param {string} originalContent
   * @param {object} cfg
   * @returns {string}
   */
  static patchBuildGradle(originalContent = '', cfg = {}) {
    let content = originalContent.trim();
    const isKts = cfg.isKts || false;

    // Fallback if empty
    if (!content) {
      return isKts
        ? this.#generateBasicKtsLibraryBuild(cfg)
        : this.#generateBasicGroovyLibraryBuild(cfg);
    }

    // 1. Ensure 'maven-publish' plugin is applied
    if (!content.includes('maven-publish')) {
      if (content.includes('plugins {')) {
        const pluginLine = isKts ? '    `maven-publish`' : "    id 'maven-publish'";
        content = content.replace('plugins {', `plugins {\n${pluginLine}`);
      } else if (content.includes('apply plugin:')) {
        content = "apply plugin: 'maven-publish'\n" + content;
      } else {
        const pluginHeader = isKts ? 'plugins {\n    `maven-publish`\n}\n\n' : "plugins {\n    id 'maven-publish'\n}\n\n";
        content = pluginHeader + content;
      }
    }

    // 2. Ensure publishing singleVariant("release") block is inside android { ... }
    if (content.includes('android {')) {
      const ensured = GradleAstHelper.ensureAndroidPublishing(content, isKts);
      if (ensured.modified) {
        content = ensured.content;
      }
    }

    // 3. Remove existing afterEvaluate / publishing { publications { release ... } } cleanly using AST brace tracking
    // We replace it cleanly with the updated dynamic block without leaving any orphan braces
    const afterEvalBlock = GradleAstHelper.findBlock(content, 'afterEvaluate');
    if (afterEvalBlock && (afterEvalBlock.bodyText.includes('publishing') || afterEvalBlock.bodyText.includes('publications') || afterEvalBlock.bodyText.includes('MavenPublication'))) {
      const removed = GradleAstHelper.removeBlock(content, 'afterEvaluate', { includeBannerComments: true });
      if (removed.removed) {
        content = removed.content;
      }
    } else {
      // Also check standalone publishing block if present
      const standalonePub = GradleAstHelper.findBlock(content, 'publishing');
      if (standalonePub && (standalonePub.bodyText.includes('publications') || standalonePub.bodyText.includes('release'))) {
        const removed = GradleAstHelper.removeBlock(content, 'publishing', { includeBannerComments: true });
        if (removed.removed) {
          content = removed.content;
        }
      }
    }

    // 4. Append the newly generated Maven Publication block
    const pubBlock = isKts
      ? this.generateKotlinPublishingBlock(cfg)
      : this.generateGroovyPublishingBlock(cfg);

    content = content.trimEnd() + '\n\n' + pubBlock + '\n';

    return content;
  }

  /**
   * Generate POM XML for current project and dependencies.
   * @param {object} cfg
   * @param {Object.<string, string|Uint8Array>} [files]
   * @returns {string}
   */
  static generatePomXml(cfg = {}, files = {}) {
    if (Array.isArray(files)) {
      return TransitiveDependencyEngine.generatePomXml(cfg, files);
    }
    const target = this.detectTargetModule(files);
    const targetPath = target?.path || `${cfg.targetModule || 'library'}/build.gradle`;
    const buildContent = String(files[targetPath] || '');

    const declaredDeps = [];
    const depRegex = /^\s*(implementation|api|compileOnly|runtimeOnly)\s*\(?['"]([^'"]+)['"]\)?/gm;
    let match;
    while ((match = depRegex.exec(buildContent)) !== null) {
      declaredDeps.push({ scope: match[1], coordinate: match[2].trim() });
    }

    return TransitiveDependencyEngine.generatePomXml(cfg, declaredDeps);
  }

  /**
   * Generate Gradle Module Metadata (.module) for current project.
   * @param {object} cfg
   * @param {Object.<string, string|Uint8Array>|Array} [files]
   * @returns {string}
   */
  static generateGradleModuleMetadata(cfg = {}, files = {}) {
    if (Array.isArray(files)) {
      return TransitiveDependencyEngine.generateGradleModuleMetadata(cfg, files);
    }
    const target = this.detectTargetModule(files);
    const targetPath = target?.path || `${cfg.targetModule || 'library'}/build.gradle`;
    const buildContent = String(files[targetPath] || '');

    const declaredDeps = [];
    const depRegex = /^\s*(implementation|api|compileOnly|runtimeOnly)\s*\(?['"]([^'"]+)['"]\)?/gm;
    let match;
    while ((match = depRegex.exec(buildContent)) !== null) {
      declaredDeps.push({ scope: match[1], coordinate: match[2].trim() });
    }

    return TransitiveDependencyEngine.generateGradleModuleMetadata(cfg, declaredDeps);
  }

  /**
   * Apply Maven Publish & JitPack files directly to the projectService current project.
   * Updates library/build.gradle, settings.gradle, jitpack.yml, pom.xml, and module metadata.
   * @param {object} projectServiceInstance
   * @param {object} cfg
   * @returns {Promise<{ success: boolean, updatedFiles: string[], targetPath: string, jitpackPath: string }>}
   */
  static async applyToProject(projectServiceInstance, cfg) {
    const project = projectServiceInstance.current;
    if (!project) throw new Error('No project loaded in ProjectService');

    const files = project.files || {};
    const target = this.detectTargetModule(files);
    const targetPath = target?.path || `${cfg.targetModule || 'library'}/build.gradle`;
    const isKts = targetPath.endsWith('.kts');

    const originalContent = files[targetPath] || '';
    const patchedBuild = this.patchBuildGradle(originalContent, { ...cfg, isKts });

    const jitpackContent = this.generateJitpackYml(cfg, files);

    const updatedFiles = [targetPath, 'jitpack.yml'];

    // 1. Save patched module build.gradle
    await projectServiceInstance.setFile(targetPath, patchedBuild);

    // 2. Save jitpack.yml
    await projectServiceInstance.setFile('jitpack.yml', jitpackContent);

    // 3. Patch settings.gradle to ensure JitPack repository and all modules are included
    const settingsPath = files['settings.gradle.kts'] ? 'settings.gradle.kts' : 'settings.gradle';
    const originalSettings = String(files[settingsPath] || '');
    const modInfo = TransitiveDependencyEngine.detectProjectModules(files);
    const allModuleNames = modInfo.modules.map(m => m.name);
    if (!allModuleNames.includes(cfg.targetModule || 'library')) {
      allModuleNames.push(cfg.targetModule || 'library');
    }
    const patchedSettings = TransitiveDependencyEngine.patchSettingsGradle(originalSettings, {
      includeJitpack: true,
      ensureModules: allModuleNames
    });
    if (patchedSettings !== originalSettings) {
      await projectServiceInstance.setFile(settingsPath, patchedSettings);
      updatedFiles.push(settingsPath);
    }

    // 4. Generate and save pom.xml in project
    const pomXml = this.generatePomXml(cfg, { ...files, [targetPath]: patchedBuild });
    await projectServiceInstance.setFile('pom.xml', pomXml);
    updatedFiles.push('pom.xml');

    // 5. Generate and save Gradle Module Metadata
    const moduleJson = this.generateGradleModuleMetadata(cfg, { ...files, [targetPath]: patchedBuild });
    const moduleMetadataPath = `${cfg.targetModule || 'library'}.module`;
    await projectServiceInstance.setFile(moduleMetadataPath, moduleJson);
    updatedFiles.push(moduleMetadataPath);

    // Also update project metadata so project properties stay in sync
    if (cfg.version && project.version !== cfg.version) {
      project.version = cfg.version;
    }
    if (cfg.repoName && project.repoName !== cfg.repoName) {
      project.repoName = cfg.repoName;
    }
    if (cfg.author && project.author !== cfg.author) {
      project.author = cfg.author;
    }
    await projectServiceInstance.save(project);

    return {
      success: true,
      updatedFiles,
      targetPath,
      jitpackPath: 'jitpack.yml',
    };
  }

  static #generateBasicGroovyLibraryBuild(cfg) {
    const pubBlock = this.generateGroovyPublishingBlock(cfg);
    const compileSdk = cfg.compileSdk || 34;
    const minSdk = cfg.minSdk || 21;
    const targetSdk = cfg.targetSdk || 34;
    return `plugins {
    id 'com.android.library'
    id 'maven-publish'
}

android {
    namespace '${cfg.groupId || 'com.example.library'}'
    compileSdk ${compileSdk}

    defaultConfig {
        minSdk ${minSdk}
        targetSdk ${targetSdk}
        versionCode 1
        versionName "${cfg.version || '1.0.0'}"
        consumerProguardFiles "consumer-rules.pro"
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            matchingFallbacks = ['release']
        }
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    lint {
        abortOnError false
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation 'androidx.annotation:annotation:1.7.0'
    implementation 'androidx.core:core:1.12.0'
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}

${pubBlock}

afterEvaluate {
    // Protect Javadoc generation from doclint failures on JDK 17
    tasks.withType(Javadoc).configureEach {
        options.addStringOption('Xdoclint:none', '-quiet')
        options.encoding = 'UTF-8'
        options.charSet = 'UTF-8'
        failOnError = false
    }
}
`;
  }

  static #generateBasicKtsLibraryBuild(cfg) {
    const pubBlock = this.generateKotlinPublishingBlock(cfg);
    return `plugins {
    id("com.android.library")
    \`maven-publish\`
}

android {
    namespace = "${cfg.groupId || 'com.example.library'}"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "${cfg.version || '1.0.0'}"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.7.0")
}

${pubBlock}
`;
  }
}
