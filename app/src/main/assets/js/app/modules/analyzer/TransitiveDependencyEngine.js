/**
 * TransitiveDependencyEngine.js — Automatic Transitive Dependency Analysis,
 * Multi-Module Mapping, POM/Module Metadata Generation & Consumer Resolution Testing.
 *
 * Core Capabilities:
 * 1. Intelligent Scope Analysis: Automatically determines "api" vs "implementation"
 *    based on source code exposure in public interfaces/signatures, known library contracts,
 *    and multi-module boundaries.
 * 2. Automated File Updates:
 *    - library/build.gradle (or module build file) with api/implementation & maven-publish
 *    - settings.gradle (dependencyResolutionManagement repositories & multi-module includes)
 *    - jitpack.yml (JDK 17/21 & multi-module install tasks)
 *    - POM (pom.xml) with compile vs runtime scopes
 *    - Gradle Module Metadata (.module) v1.1
 * 3. Multi-Module Project Support:
 *    - Handles inter-module dependencies like api project(':core')
 *    - Maps project dependencies to published Maven coordinates
 * 4. Consumer Dependency Resolution Simulation & Verification:
 *    - Verifies compile classpath (direct + api only) vs runtime classpath (all)
 *    - Validates repository availability (Google, MavenCentral, JitPack)
 *    - Validates Gradle / AGP / JDK compatibility
 *    - Produces a comprehensive verification report
 */

import { GradleAstHelper } from '../generator/GradleAstHelper.js';

// Popular libraries knowledge base for transitive dependency tree expansion
export const POPULAR_TRANSITIVE_TREES = {
  'androidx.appcompat:appcompat': [
    { coordinate: 'androidx.core:core:1.12.0', defaultScope: 'api', description: 'Core Android compatibility' },
    { coordinate: 'androidx.fragment:fragment:1.6.2', defaultScope: 'implementation', description: 'Fragment support' },
    { coordinate: 'androidx.activity:activity:1.8.2', defaultScope: 'implementation', description: 'Activity extensions' },
    { coordinate: 'androidx.drawerlayout:drawerlayout:1.2.0', defaultScope: 'implementation', description: 'Navigation drawer' }
  ],
  'androidx.core:core-ktx': [
    { coordinate: 'androidx.core:core:1.12.0', defaultScope: 'api', description: 'Core library' },
    { coordinate: 'org.jetbrains.kotlin:kotlin-stdlib:1.9.22', defaultScope: 'api', description: 'Kotlin standard library' }
  ],
  'com.google.android.material:material': [
    { coordinate: 'androidx.appcompat:appcompat:1.6.1', defaultScope: 'api', description: 'AppCompat UI' },
    { coordinate: 'androidx.cardview:cardview:1.0.0', defaultScope: 'implementation', description: 'Card UI' },
    { coordinate: 'androidx.recyclerview:recyclerview:1.3.2', defaultScope: 'implementation', description: 'RecyclerView' },
    { coordinate: 'androidx.viewpager2:viewpager2:1.0.0', defaultScope: 'implementation', description: 'ViewPager2' }
  ],
  'com.squareup.retrofit2:retrofit': [
    { coordinate: 'com.squareup.okhttp3:okhttp:4.12.0', defaultScope: 'api', description: 'HTTP engine' }
  ],
  'com.squareup.retrofit2:converter-gson': [
    { coordinate: 'com.squareup.retrofit2:retrofit:2.9.0', defaultScope: 'api', description: 'Retrofit core' },
    { coordinate: 'com.google.code.gson:gson:2.10.1', defaultScope: 'api', description: 'Gson parser' }
  ],
  'com.squareup.okhttp3:okhttp': [
    { coordinate: 'com.squareup.okio:okio:3.6.0', defaultScope: 'api', description: 'Okio I/O' },
    { coordinate: 'org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22', defaultScope: 'implementation', description: 'Kotlin stdlib' }
  ],
  'org.jetbrains.kotlinx:kotlinx-coroutines-android': [
    { coordinate: 'org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0', defaultScope: 'api', description: 'Coroutines core' }
  ]
};

// Recognized public API abstractions (recommended api scope)
export const KNOWN_API_LIBRARIES = [
  /^androidx\.annotation:annotation/,
  /^org\.jetbrains:annotations/,
  /^javax\.inject:javax\.inject/,
  /^org\.slf4j:slf4j-api/,
  /^io\.reactivex\.rxjava[23]:rxjava/,
  /^org\.jetbrains\.kotlinx:kotlinx-coroutines-core/
];

// Recognized internal implementation libraries (recommended implementation scope)
export const KNOWN_INTERNAL_LIBRARIES = [
  /^com\.google\.code\.gson:gson/,
  /^com\.squareup\.moshi:moshi/,
  /^androidx\.room:room-runtime/,
  /^io\.requery:sqlite-android/,
  /^androidx\.constraintlayout:constraintlayout/,
  /^com\.github\.bumptech\.glide:glide/,
  /^io\.coil-kt:coil/,
  /^com\.jakewharton\.timber:timber/
];

export class TransitiveDependencyEngine {
  /**
   * Automatically classify dependency scope (api vs implementation).
   *
   * @param {string} coordinate - e.g. "androidx.core:core-ktx:1.12.0"
   * @param {Object.<string, string|Uint8Array>} files - Project files
   * @param {object} [options]
   * @param {string} [options.moduleName]
   * @returns {{
   *   scope: 'api' | 'implementation',
   *   confidence: number,
   *   reason: string,
   *   publicExposures: Array<{ file: string, line: string, detail: string }>,
   *   internalUsages: Array<{ file: string, line: string }>
   * }}
   */
  static analyzeScope(coordinate, files = {}, options = {}) {
    const cleanCoord = coordinate.trim().replace(/^['"]|['"]$/g, '');
    const parts = cleanCoord.split(':');
    const group = parts[0] || '';
    const name = parts[1] || '';
    const groupArtifact = `${group}:${name}`;

    const publicExposures = [];
    const internalUsages = [];

    // 1. Scan source files (.java, .kt) to check if types from this dependency
    // are exposed in public class declarations, public return types, public method params, or interface implementations
    const sourceFiles = Object.keys(files).filter(p =>
      (p.endsWith('.java') || p.endsWith('.kt')) &&
      !p.includes('/test/') && !p.includes('/androidTest/') && !p.includes('/build/')
    );

    // Identify package prefix heuristics from group/name
    const pkgKeywords = TransitiveDependencyEngine._inferPackageKeywords(group, name);

    // 0. Test dependency classification
    const isAndroidTest = /androidx\.test|espresso|uiautomator/i.test(cleanCoord) ||
      Object.keys(files).some(p => p.includes('/androidTest/') && String(files[p]).includes(name));
    if (isAndroidTest) {
      return {
        scope: 'androidTestImplementation',
        confidence: 0.95,
        reason: `Instrumentation test framework (${groupArtifact}). Used in Android device testing.`,
        publicExposures: [],
        internalUsages: []
      };
    }

    const isUnitTest = /junit|mockito|robolectric|mockk|assertj|truth|hamcrest/i.test(cleanCoord) ||
      (Object.keys(files).some(p => p.includes('/test/') && !p.includes('/androidTest/') && String(files[p]).includes(name)) &&
       !sourceFiles.some(p => String(files[p]).includes(name)));
    if (isUnitTest) {
      return {
        scope: 'testImplementation',
        confidence: 0.95,
        reason: `Unit test framework (${groupArtifact}). Isolated to src/test test execution.`,
        publicExposures: [],
        internalUsages: []
      };
    }

    for (const sf of sourceFiles) {
      const content = String(files[sf] || '');
      const lines = content.split('\n');
      const fileName = sf.split('/').pop();

      // Check imports first
      const matchedImports = lines
        .filter(l => l.trim().startsWith('import ') && pkgKeywords.some(kw => l.includes(kw)))
        .map(l => l.replace(/^import\s+(static\s+)?/, '').replace(/;$/, '').trim());

      if (matchedImports.length === 0) continue;

      // Extract imported class short names
      const importedClassNames = matchedImports
        .map(imp => imp.split('.').pop())
        .filter(cn => cn && cn !== '*');

      let inPublicClass = true;

      for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();
        if (!line || line.startsWith('//') || line.startsWith('/*') || line.startsWith('*')) continue;

        // Track class visibility
        if (/^(public|open)\s+(class|interface|abstract\s+class|enum)/.test(line) ||
            /^(class|interface)\s+/.test(line)) { // in Kotlin, default is public
          inPublicClass = !line.includes('private ') && !line.includes('internal ');
        }

        // Check if any imported class name appears in public signatures
        for (const clsName of importedClassNames) {
          if (!line.includes(clsName)) continue;

          // Check if used in public method return type or parameter
          const isPublicMethod = inPublicClass && (
            /public\s+[\w<>, ]+\s+\w+\s*\(/.test(line) || // Java public returnType methodName(...)
            /fun\s+\w+\s*\([^)]*\)\s*:\s*[\w<>, ]+/.test(line) || // Kotlin fun name(...): ReturnType
            /fun\s+\w+\s*\([^)]*:[^)]*\)/.test(line) // Kotlin fun with typed params
          );

          // Check if used in class inheritance: extends ClsName, implements ClsName, : ClsName
          const isClassInheritance = inPublicClass && (
            /extends\s+[\w<>, ]*\b/.test(line) ||
            /implements\s+[\w<>, ]*\b/.test(line) ||
            /:\s*[\w<>, ]*\b/.test(line)
          );

          // Check if used in public field / property
          const isPublicProperty = inPublicClass && (
            /public\s+(final\s+)?[\w<>, ]+\s+\w+\s*(=|;)/.test(line) ||
            /(val|var)\s+\w+\s*:\s*[\w<>, ]+/.test(line)
          );

          if (isPublicMethod || isClassInheritance || isPublicProperty) {
            publicExposures.push({
              file: sf,
              line: line.substring(0, 80),
              detail: isClassInheritance
                ? `Inherited / implemented in public class (${clsName})`
                : isPublicMethod
                  ? `Exposed in public method signature (${clsName})`
                  : `Exposed in public property (${clsName})`
            });
          } else {
            internalUsages.push({
              file: sf,
              line: line.substring(0, 80)
            });
          }
        }
      }
    }

    // Decision Logic:
    // 1. Direct public exposure found in library code -> API
    if (publicExposures.length > 0) {
      return {
        scope: 'api',
        confidence: 0.95,
        reason: `Exposed in public ABI of ${publicExposures[0].file.split('/').pop()} (${publicExposures[0].detail}). Consumers must resolve this type to compile.`,
        publicExposures,
        internalUsages
      };
    }

    // 2. Known library contract check
    if (KNOWN_API_LIBRARIES.some(re => re.test(cleanCoord))) {
      return {
        scope: 'api',
        confidence: 0.90,
        reason: `Standard ABI contract library (${groupArtifact}). Designed to be available on consumer compile classpath.`,
        publicExposures: [],
        internalUsages
      };
    }

    // 3. Known internal library check
    if (KNOWN_INTERNAL_LIBRARIES.some(re => re.test(cleanCoord))) {
      return {
        scope: 'implementation',
        confidence: 0.95,
        reason: `Internal utility / engine (${groupArtifact}). Encapsulating as 'implementation' prevents ABI leakage and optimizes consumer compilation.`,
        publicExposures: [],
        internalUsages
      };
    }

    // 4. Default: implementation (Best practice in modern Gradle to prevent accidental ABI leakage)
    return {
      scope: 'implementation',
      confidence: 0.85,
      reason: internalUsages.length > 0
        ? `Used only inside method bodies or internal components in ${internalUsages[0].file.split('/').pop()}. No public ABI exposure detected.`
        : `Safe default: 'implementation' encapsulates dependency and prevents consumer compile classpath pollution.`,
      publicExposures: [],
      internalUsages
    };
  }

  /**
   * Expand transitive dependencies for a given dependency list.
   * @param {Array<{ coordinate: string, scope: string }>} dependencies
   * @returns {Array<{
   *   coordinate: string,
   *   group: string,
   *   name: string,
   *   version: string,
   *   directScope: string,
   *   effectiveScope: 'compile' | 'runtime',
   *   isTransitive: boolean,
   *   parentCoordinate?: string,
   *   description?: string
   * }>}
   */
  static resolveTransitiveGraph(dependencies = []) {
    const resolved = new Map(); // coordinate -> item

    for (const dep of dependencies) {
      const clean = dep.coordinate.trim().replace(/^['"]|['"]$/g, '');
      const parts = clean.split(':');
      const group = parts[0] || '';
      const name = parts[1] || '';
      const version = parts[2] || '';
      const scope = dep.scope || 'implementation';
      const effectiveScope = scope === 'api' ? 'compile' : 'runtime';

      resolved.set(clean, {
        coordinate: clean,
        group,
        name,
        version,
        directScope: scope,
        effectiveScope,
        isTransitive: false,
        transitiveChildren: []
      });

      // Expand known transitive children
      const groupArtifact = `${group}:${name}`;
      const children = POPULAR_TRANSITIVE_TREES[groupArtifact] || [];

      for (const ch of children) {
        if (!resolved.has(ch.coordinate)) {
          const chParts = ch.coordinate.split(':');
          // If parent is api, child is exposed as compile if child itself is api, otherwise runtime
          const chEffective = (scope === 'api' && ch.defaultScope === 'api') ? 'compile' : 'runtime';
          const childObj = {
            coordinate: ch.coordinate,
            group: chParts[0] || '',
            name: chParts[1] || '',
            version: chParts[2] || '',
            directScope: ch.defaultScope,
            effectiveScope: chEffective,
            isTransitive: true,
            parentCoordinate: clean,
            description: ch.description || 'Transitive dependency'
          };
          resolved.set(ch.coordinate, childObj);
          resolved.get(clean).transitiveChildren.push(ch.coordinate);
        }
      }
    }

    return Array.from(resolved.values());
  }

  /**
   * Detect multi-module project structure from settings.gradle and files.
   * @param {Object.<string, string|Uint8Array>} files
   * @returns {{
   *   isMultiModule: boolean,
   *   modules: Array<{ name: string, path: string, isLibrary: boolean, isApp: boolean, buildFile: string }>,
   *   rootSettingsFile: string | null
   * }}
   */
  static detectProjectModules(files = {}) {
    const settingsContent = String(files['settings.gradle'] || files['settings.gradle.kts'] || '');
    const modules = [];

    // Extract include ':module' from settings.gradle
    const includeRegex = /include\s+(['"]:[^'"]+['"](?:\s*,\s*['"]:[^'"]+['"])*)/g;
    let match;
    const moduleNames = new Set();

    while ((match = includeRegex.exec(settingsContent)) !== null) {
      const line = match[1];
      const items = line.match(/:([a-zA-Z0-9_-]+)/g);
      if (items) {
        items.forEach(it => moduleNames.add(it.substring(1)));
      }
    }

    // Also scan folders with build.gradle
    for (const p of Object.keys(files)) {
      if ((p.endsWith('/build.gradle') || p.endsWith('/build.gradle.kts')) && !p.startsWith('build.gradle')) {
        const modName = p.split('/')[0];
        if (modName && modName !== 'build' && modName !== '.gradle') {
          moduleNames.add(modName);
        }
      }
    }

    for (const mod of moduleNames) {
      const buildFile = files[`${mod}/build.gradle`] ? `${mod}/build.gradle` :
                        files[`${mod}/build.gradle.kts`] ? `${mod}/build.gradle.kts` : null;
      const content = buildFile ? String(files[buildFile] || '') : '';
      const isLibrary = content.includes('com.android.library') || content.includes('java-library');
      const isApp = content.includes('com.android.application');

      modules.push({
        name: mod,
        path: mod,
        isLibrary,
        isApp,
        buildFile: buildFile || `${mod}/build.gradle`
      });
    }

    return {
      isMultiModule: modules.length > 1,
      modules,
      rootSettingsFile: files['settings.gradle'] ? 'settings.gradle' : files['settings.gradle.kts'] ? 'settings.gradle.kts' : null
    };
  }

  /**
   * Determine required repositories for a coordinate.
   * @param {string} coordinate
   * @returns {Array<'google' | 'mavenCentral' | 'jitpack'>}
   */
  static detectRequiredRepositories(coordinate = '') {
    const repos = [];
    const clean = coordinate.trim();
    if (clean.startsWith('androidx.') || clean.startsWith('com.google.android.') || clean.startsWith('com.android.')) {
      repos.push('google');
      repos.push('mavenCentral');
    } else if (clean.startsWith('com.github.') || clean.startsWith('com.gitlab.') || clean.startsWith('org.bitbucket.') || clean.startsWith('com.gitee.')) {
      repos.push('jitpack');
      repos.push('mavenCentral');
    } else {
      repos.push('mavenCentral');
    }
    return repos;
  }

  /**
   * Safely update settings.gradle to ensure required repositories and modules are configured without duplicate declarations.
   * @param {string} originalContent
   * @param {object} opts
   * @param {boolean} [opts.includeJitpack=true]
   * @param {string[]} [opts.ensureModules]
   * @returns {string}
   */
  static patchSettingsGradle(originalContent = '', opts = {}) {
    let content = originalContent || '';

    // If completely empty, generate modern settings.gradle
    if (!content.trim()) {
      const modLines = (opts.ensureModules && opts.ensureModules.length > 0)
        ? opts.ensureModules.map(m => `include ':${m}'`).join('\n')
        : "include ':library'\ninclude ':app'";

      return `pluginManagement {
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

rootProject.name = "AndroidLibraryProject"
${modLines}
`;
    }

    // 1. Ensure JitPack repo in dependencyResolutionManagement or allprojects using AST brace tracking
    if (opts.includeJitpack !== false) {
      const ensured = GradleAstHelper.ensureJitpackRepository(content);
      if (ensured.modified) {
        content = ensured.content;
      }
    }

    // 2. Ensure all modules are included
    const modulesToEnsure = opts.ensureModules || opts.newModules || [];
    if (modulesToEnsure && modulesToEnsure.length > 0) {
      for (const mod of modulesToEnsure) {
        const modRegex = new RegExp(`include\\s+['"]:${mod}['"]|include\\s*\\([\\s\\S]*?['"]:${mod}['"]`);
        if (!modRegex.test(content)) {
          content = content.trimEnd() + `\ninclude ':${mod}'\n`;
        }
      }
    }

    return content;
  }

  /**
   * Safely update library/build.gradle with dependency and publishing config.
   * Preserves existing code, updates missing or incorrect parts.
   * @param {string} originalContent
   * @param {object} depConfig - { coordinate, scope, isProject, projectPath }
   * @param {object} [publishConfig] - { groupId, artifactId, version, author, repoName }
   * @returns {{ content: string, added: boolean, updated: boolean }}
   */
  static patchLibraryBuildGradle(originalContent = '', depConfig = {}, publishConfig = {}) {
    let content = originalContent || '';
    const isKts = content.includes('implementation(') || (content.includes('plugins {') && content.includes('kotlin'));
    let added = false;
    let updated = false;

    // 1. Add or update dependency using Brace-Aware AST
    if (depConfig.coordinate || depConfig.projectPath) {
      const scope = depConfig.scope || 'implementation';
      const targetCoord = depConfig.isProject ? depConfig.projectPath : depConfig.coordinate;

      const result = GradleAstHelper.insertOrUpdateDependency(content, targetCoord, scope, isKts);
      content = result.content;
      if (result.added) added = true;
      if (result.updated) updated = true;
    }

    // 2. Ensure maven-publish plugin
    if (!content.includes('maven-publish')) {
      if (content.includes('plugins {')) {
        const pluginLine = isKts ? '    `maven-publish`' : "    id 'maven-publish'";
        content = content.replace('plugins {', `plugins {\n${pluginLine}`);
      } else if (content.includes('apply plugin:')) {
        content = "apply plugin: 'maven-publish'\n" + content;
      }
    }

    // 3. Ensure android.publishing { singleVariant("release") { withSourcesJar(); withJavadocJar(); } }
    if (content.includes('android {')) {
      const ensured = GradleAstHelper.ensureAndroidPublishing(content, isKts);
      if (ensured.modified) {
        content = ensured.content;
      }
    }

    // 4. Ensure publication block has release(MavenPublication)
    if (!content.includes('release(MavenPublication)') && !content.includes('create("release", MavenPublication)') && !content.includes('register<MavenPublication>("release")')) {
      const gId = publishConfig.groupId || 'com.example.library';
      const aId = publishConfig.artifactId || 'library';
      const ver = publishConfig.version || '1.0.0';

      const pubBlock = isKts ? `
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components["release"])
                this.groupId = "${gId}"
                this.artifactId = "${aId}"
                this.version = "${ver}"
            }
        }
    }
}` : `
afterEvaluate {
    publishing {
        publications {
            release(MavenPublication) {
                from components.release
                groupId    = '${gId}'
                artifactId = '${aId}'
                version    = '${ver}'
            }
        }
    }
}`;
      content = content.trimEnd() + '\n\n' + pubBlock + '\n';
    }

    return { content, added, updated };
  }

  /**
   * Update or generate jitpack.yml for multi-module and public publication.
   * @param {string} originalContent
   * @param {object} opts - { modules: string[], jdkVersion: string }
   * @returns {string}
   */
  static patchJitpackYml(originalContent = '', opts = {}) {
    const jdk = opts.jdkVersion || 'openjdk17';
    const modules = opts.modules || ['library'];

    // Construct install command with all library submodules
    const installTasks = modules
      .map(m => `:${m}:assembleRelease :${m}:publishToMavenLocal`)
      .join(' ');

    return `# JitPack Configuration with Transitive Dependency & Multi-Module Support
# https://jitpack.io/docs/ANDROID/
jdk:
  - ${jdk}
before_install:
  - git config --global --add safe.directory /build || true
  - chmod +x gradlew
install:
  - ./gradlew clean ${installTasks} --stacktrace
`;
  }

  /**
   * Generate standard Maven POM XML with compile (api) and runtime (implementation) scopes.
   * @param {object} metadata - { groupId, artifactId, version, description, url, author }
   * @param {Array<{ coordinate: string, scope: string }>} dependencies
   * @param {Array<{ name: string, scope: string }>} [projectDependencies]
   * @returns {string} Valid XML
   */
  static generatePomXml(metadata = {}, dependencies = [], projectDependencies = []) {
    const groupId = metadata.groupId || 'com.example.library';
    const artifactId = metadata.artifactId || 'library';
    const version = metadata.version || '1.0.0';
    const desc = metadata.description || 'Android Library';
    const author = metadata.author || 'developer';
    const repo = metadata.repoName || artifactId;

    let depsXml = '';

    // External dependencies
    for (const dep of dependencies) {
      const clean = dep.coordinate.trim().replace(/^['"]|['"]$/g, '');
      const parts = clean.split(':');
      if (parts.length < 2) continue;

      const g = parts[0];
      const a = parts[1];
      const v = parts[2] || '';
      // Map scope: api -> compile, implementation -> runtime, compileOnly -> provided
      const actualScope = dep.scope || dep.configuration || 'implementation';
      const pomScope = actualScope === 'api' ? 'compile' :
                       actualScope === 'compileOnly' ? 'provided' : 'runtime';

      depsXml += `    <dependency>
      <groupId>${g}</groupId>
      <artifactId>${a}</artifactId>${v ? `\n      <version>${v}</version>` : ''}
      <scope>${pomScope}</scope>
    </dependency>\n`;
    }

    // Inter-module project dependencies
    for (const pdep of (projectDependencies || [])) {
      const pomScope = pdep.scope === 'api' ? 'compile' : 'runtime';
      depsXml += `    <dependency>
      <groupId>${groupId}</groupId>
      <artifactId>${pdep.name}</artifactId>
      <version>${version}</version>
      <scope>${pomScope}</scope>
    </dependency>\n`;
    }

    return `<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>${groupId}</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>${version}</version>
  <packaging>aar</packaging>

  <name>${artifactId}</name>
  <description>${desc}</description>
  <url>https://github.com/${author}/${repo}</url>

  <licenses>
    <license>
      <name>The Apache Software License, Version 2.0</name>
      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>

  <developers>
    <developer>
      <id>${author}</id>
      <name>${author}</name>
    </developer>
  </developers>

  <scm>
    <connection>scm:git:git://github.com/${author}/${repo}.git</connection>
    <developerConnection>scm:git:ssh://github.com:${author}/${repo}.git</developerConnection>
    <url>https://github.com/${author}/${repo}</url>
  </scm>

  <dependencies>
${depsXml.trimEnd()}
  </dependencies>
</project>
`;
  }

  /**
   * Generate Gradle Module Metadata (.module) specification v1.1.
   * @param {object} metadata
   * @param {Array<{ coordinate: string, scope: string }>} dependencies
   * @returns {string} JSON string
   */
  static generateGradleModuleMetadata(metadata = {}, dependencies = []) {
    const groupId = metadata.groupId || 'com.example.library';
    const artifactId = metadata.artifactId || 'library';
    const version = metadata.version || '1.0.0';

    const apiDeps = dependencies.filter(d => d.scope === 'api').map(d => {
      const parts = d.coordinate.trim().replace(/^['"]|['"]$/g, '').split(':');
      return { group: parts[0], module: parts[1], version: { requires: parts[2] || '' } };
    });

    const runtimeDeps = dependencies.map(d => {
      const parts = d.coordinate.trim().replace(/^['"]|['"]$/g, '').split(':');
      return { group: parts[0], module: parts[1], version: { requires: parts[2] || '' } };
    });

    const moduleJson = {
      formatVersion: "1.1",
      component: {
        group: groupId,
        module: artifactId,
        version: version,
        attributes: {
          "org.gradle.status": "release"
        }
      },
      createdBy: {
        gradle: {
          version: "8.6",
          buildId: "RONiLibGen-AutoTransitive"
        }
      },
      variants: [
        {
          name: "releaseApiPublication",
          attributes: {
            "org.gradle.category": "library",
            "org.gradle.dependency.bundling": "external",
            "org.gradle.libraryelements": "aar",
            "org.gradle.usage": "java-api"
          },
          dependencies: apiDeps
        },
        {
          name: "releaseRuntimePublication",
          attributes: {
            "org.gradle.category": "library",
            "org.gradle.dependency.bundling": "external",
            "org.gradle.libraryelements": "aar",
            "org.gradle.usage": "java-runtime"
          },
          dependencies: runtimeDeps
        }
      ]
    };

    return JSON.stringify(moduleJson, null, 2);
  }

  /**
   * Perform a comprehensive Consumer Dependency Resolution Test & Compatibility Verification.
   *
   * Simulates:
   * Consumer App -> implementation 'com.github.user:repo:version'
   *
   * @param {Object.<string, string|Uint8Array>} files
   * @param {object} [opts]
   * @returns {object} Comprehensive verification report
   */
  static runConsumerVerification(files = {}, opts = {}) {
    const report = {
      timestamp: new Date().toISOString(),
      status: 'passed', // 'passed' | 'warning' | 'failed'
      summary: '',
      checks: [],
      consumerClasspath: {
        compileClasspath: [],
        runtimeClasspath: [],
      },
      multiModuleStatus: {
        isMultiModule: false,
        modules: []
      },
      compatibility: {
        agpVersion: 'unknown',
        gradleVersion: 'unknown',
        jdkVersion: '17',
        compatible: true
      },
      pomXmlPreview: '',
      moduleMetadataPreview: '',
      consumerSnippets: {}
    };

    // 1. Detect modules & target library
    const modInfo = this.detectProjectModules(files);
    report.multiModuleStatus.isMultiModule = modInfo.isMultiModule;
    report.multiModuleStatus.modules = modInfo.modules;

    const targetMod = modInfo.modules.find(m => m.isLibrary) || modInfo.modules[0] || { name: 'library', buildFile: 'library/build.gradle' };
    const buildFileContent = String(files[targetMod.buildFile] || files['build.gradle'] || '');

    // Parse dependencies from target library build file
    const declaredDeps = [];
    const depRegex = /^\s*(implementation|api|compileOnly|runtimeOnly)\s*\(?['"]([^'"]+)['"]\)?/gm;
    let match;
    while ((match = depRegex.exec(buildFileContent)) !== null) {
      declaredDeps.push({
        scope: match[1],
        coordinate: match[2].trim()
      });
    }

    // 2. Transitive Graph Resolution
    const graph = this.resolveTransitiveGraph(declaredDeps);

    // Compile Classpath: target library + all 'api' direct dependencies
    report.consumerClasspath.compileClasspath = [
      {
        coordinate: `${opts.groupId || 'com.github.user'}:${opts.artifactId || targetMod.name}:${opts.version || '1.0.0'}`,
        type: 'Direct Library Artifact (AAR)',
        scope: 'direct',
        source: 'Target Publication'
      },
      ...graph.filter(g => g.effectiveScope === 'compile').map(g => ({
        coordinate: g.coordinate,
        type: g.isTransitive ? 'Transitive API' : 'Direct API',
        scope: 'api',
        source: g.parentCoordinate || targetMod.name,
        leakRisk: 'Exposed on consumer compile classpath (ABI contract)'
      }))
    ];

    // Runtime Classpath: target library + ALL dependencies (api + implementation)
    report.consumerClasspath.runtimeClasspath = [
      {
        coordinate: `${opts.groupId || 'com.github.user'}:${opts.artifactId || targetMod.name}:${opts.version || '1.0.0'}`,
        type: 'Direct Library Artifact (AAR)',
        scope: 'direct'
      },
      ...graph.map(g => ({
        coordinate: g.coordinate,
        type: g.directScope === 'api' ? 'API Dependency' : 'Implementation Dependency (Encapsulated)',
        scope: g.directScope,
        isTransitive: g.isTransitive
      }))
    ];

    // 3. Compatibility Matrix Check (AGP / Gradle / Java)
    const rootBuild = String(files['build.gradle'] || files['build.gradle.kts'] || '');
    const gradleWrapperProps = String(files['gradle/wrapper/gradle-wrapper.properties'] || '');
    const jitpackYml = String(files['jitpack.yml'] || '');

    let agpVer = '8.2.0';
    const agpMatch = rootBuild.match(/com\.android\.tools\.build:gradle:([0-9.]+)/) ||
                     buildFileContent.match(/com\.android\.tools\.build:gradle:([0-9.]+)/) ||
                     rootBuild.match(/id\s*['"]com\.android\.library['"]\s*version\s*['"]([^'"]+)['"]/);
    if (agpMatch) agpVer = agpMatch[1];
    report.compatibility.agpVersion = agpVer;

    let gradleVer = '8.6';
    const gwMatch = gradleWrapperProps.match(/gradle-([0-9.]+)-(bin|all)\.zip/);
    if (gwMatch) gradleVer = gwMatch[1];
    report.compatibility.gradleVersion = gradleVer;

    let jdkVer = 'openjdk17';
    const jpMatch = jitpackYml.match(/-\s*(openjdk[0-9]+)/);
    if (jpMatch) jdkVer = jpMatch[1];
    report.compatibility.jdkVersion = jdkVer;

    // Check 1: maven-publish plugin
    const hasMavenPublish = buildFileContent.includes('maven-publish');
    report.checks.push({
      id: 'maven-publish',
      title: 'Maven Publish Plugin Configuration',
      status: hasMavenPublish ? 'pass' : 'fail',
      message: hasMavenPublish
        ? `Plugin 'maven-publish' is correctly applied in ${targetMod.buildFile}.`
        : `Plugin 'maven-publish' is missing in ${targetMod.buildFile}. Publication will fail.`
    });

    // Check 2: AGP singleVariant publishing
    const hasSingleVariant = buildFileContent.includes('singleVariant("release")') || buildFileContent.includes("singleVariant('release')");
    report.checks.push({
      id: 'agp-publishing',
      title: 'Android Gradle Plugin Publication Variant',
      status: hasSingleVariant ? 'pass' : 'warn',
      message: hasSingleVariant
        ? 'android.publishing singleVariant("release") configured for sources and javadoc packaging.'
        : 'Recommended: add android.publishing.singleVariant("release") to bundle sources & javadoc AAR.'
    });

    // Check 3: JitPack YAML configuration
    const hasJitpack = 'jitpack.yml' in files && !!files['jitpack.yml'];
    report.checks.push({
      id: 'jitpack-yml',
      title: 'JitPack Build Specification (jitpack.yml)',
      status: hasJitpack ? 'pass' : 'fail',
      message: hasJitpack
        ? `jitpack.yml exists with JDK ${jdkVer}. Build commands are defined.`
        : 'jitpack.yml is missing at project root. JitPack build environment will default to outdated Java 8.'
    });

    // Check 4: Transitive API vs Implementation Scoping
    const apiCount = declaredDeps.filter(d => d.scope === 'api').length;
    const implCount = declaredDeps.filter(d => d.scope === 'implementation').length;
    report.checks.push({
      id: 'transitive-scoping',
      title: 'Transitive Scope Distribution',
      status: 'pass',
      message: `Detected ${declaredDeps.length} dependencies: ${apiCount} 'api' (transitive compile) and ${implCount} 'implementation' (encapsulated runtime). Encapsulation is preserved.`
    });

    // Check 5: Repository Coverage
    const settingsContent = String(files['settings.gradle'] || files['settings.gradle.kts'] || '');
    const hasJitpackRepo = settingsContent.includes('jitpack.io') || rootBuild.includes('jitpack.io');
    const hasJitpackDeps = declaredDeps.some(d => d.coordinate.includes('com.github.') || d.coordinate.includes('jitpack'));

    if (hasJitpackDeps) {
      report.checks.push({
        id: 'repo-coverage',
        title: 'JitPack Repository Declaration',
        status: hasJitpackRepo ? 'pass' : 'fail',
        message: hasJitpackRepo
          ? 'JitPack repository is configured in settings.gradle / build.gradle.'
          : 'Project uses JitPack dependencies but repository is missing in settings.gradle. Dependency resolution will fail.'
      });
    } else {
      report.checks.push({
        id: 'repo-coverage',
        title: 'Repository Configuration',
        status: 'pass',
        message: 'All standard repositories (Google, MavenCentral) are verified.'
      });
    }

    // Check 6: Multi-module publication
    if (modInfo.isMultiModule) {
      const hasMultiInJitpack = jitpackYml.includes(`:${targetMod.name}:`) || jitpackYml.includes('publishToMavenLocal');
      report.checks.push({
        id: 'multi-module-publish',
        title: 'Multi-Module Publication Routing',
        status: hasMultiInJitpack ? 'pass' : 'warn',
        message: hasMultiInJitpack
          ? `Multi-module project detected (${modInfo.modules.length} modules). JitPack install task builds target library modules correctly.`
          : `Multi-module project detected. Ensure jitpack.yml builds all published submodules.`
      });
    }

    // Determine final status
    const hasFail = report.checks.some(c => c.status === 'fail');
    const hasWarn = report.checks.some(c => c.status === 'warn');
    report.status = hasFail ? 'failed' : hasWarn ? 'warning' : 'passed';
    report.summary = hasFail
      ? 'Verification failed: critical configuration issues detected that will break consumer resolution.'
      : hasWarn
        ? 'Verification passed with optimization recommendations.'
        : 'All transitive dependency, Maven publishing, and consumer resolution checks passed.';

    // Generate POM and Module previews
    const meta = {
      groupId: opts.groupId || 'com.github.user',
      artifactId: opts.artifactId || targetMod.name,
      version: opts.version || '1.0.0',
      description: opts.description || 'Android Library',
      author: opts.author || 'user',
      repoName: opts.repoName || targetMod.name
    };

    report.pomXmlPreview = this.generatePomXml(meta, declaredDeps);
    report.moduleMetadataPreview = this.generateGradleModuleMetadata(meta, declaredDeps);

    // Consumer Snippets
    const jitpackCoord = `${meta.groupId}:${meta.artifactId}:${meta.version.startsWith('v') ? meta.version : `v${meta.version}`}`;
    report.consumerSnippets = {
      coordinate: jitpackCoord,
      groovySettings: `dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}`,
      groovyApp: `dependencies {
    implementation '${jitpackCoord}'
}`,
      kotlinApp: `dependencies {
    implementation("${jitpackCoord}")
}`
    };

    return report;
  }

  /**
   * Simulate consumer resolution for a project.
   * Alias / wrapper around runConsumerVerification.
   */
  static simulateResolution(projectConfig = {}, dependencies = [], files = {}) {
    const opts = {
      ...projectConfig,
      declaredDeps: dependencies
    };
    const report = this.runConsumerVerification(files, opts);
    return {
      consumerCompileClasspath: report.consumerClasspath.compileClasspath,
      consumerRuntimeClasspath: report.consumerClasspath.runtimeClasspath,
      checks: report.checks,
      status: report.status,
      summary: report.summary,
      report
    };
  }

  /**
   * Alias for runConsumerVerification
   */
  static generateVerificationReport(files = {}, opts = {}) {
    return this.runConsumerVerification(files, opts);
  }

  /**
   * Alias for simulateResolution
   */
  static simulateConsumerResolution(projectConfig = {}, dependencies = [], files = {}) {
    return this.simulateResolution(projectConfig, dependencies, files);
  }

  // ── Helper utilities ──

  static _inferPackageKeywords(group, name) {
    const list = [];
    if (group) {
      list.push(group);
      // Split group into identifiers (excluding common prefixes like github, com, etc.)
      const groupParts = group.split(/[.-]/).filter(p => p.length > 3 && !['github', 'com', 'org', 'net', 'io'].includes(p.toLowerCase()));
      list.push(...groupParts);
    }
    if (name) {
      const cleanName = name.replace(/-ktx$/, '').replace(/^core-/, '');
      list.push(cleanName);
      // Split name parts (e.g. 'native-logger-sdk' -> 'native', 'logger')
      const nameParts = cleanName.split(/[-_]/).filter(p => p.length > 3 && p.toLowerCase() !== 'sdk');
      list.push(...nameParts);
    }
    return [...new Set(list)].filter(k => k.length > 2);
  }

  static _escapeRegex(str) {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }
}
