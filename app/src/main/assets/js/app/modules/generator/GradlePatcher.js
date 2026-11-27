/**
 * GradlePatcher.js — Safe patching of settings.gradle and build.gradle files.
 *
 * Adheres strictly to the principles:
 * - Read -> Analyze -> Patch -> Validate
 * - Preserves existing developer dependencies and configurations
 * - Removes or converts machine-specific computer paths (e.g. E:/...)
 * - Supports GitHub Packages when configured
 * - Adapts to Android Library vs Android App
 */

import { GradleAstHelper } from './GradleAstHelper.js';

export class GradlePatcher {
  /**
   * Patch settings.gradle according to developer configuration.
   * @param {string} originalContent
   * @param {object} opts
   * @param {string} [opts.projectName]
   * @param {string[]} [opts.modules]
   * @param {boolean} [opts.cleanLocalMachinePaths] - remove paths like E:/...
   * @param {object} [opts.githubPackages] - { owner, repo }
   * @returns {string} Patched content
   */
  static patchSettings(originalContent = '', opts = {}) {
    let content = originalContent;

    // If completely empty, generate standard template
    if (!content.trim()) {
      const modIncludes = (opts.modules && opts.modules.length > 0)
        ? opts.modules.map(m => `include ':${m}'`).join('\n')
        : "include ':app'";

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
${opts.githubPackages?.owner ? `        maven {
            url = uri("https://maven.pkg.github.com/${opts.githubPackages.owner}/${opts.githubPackages.repo || '*'}")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }\n` : ''}    }
}

rootProject.name = "${opts.projectName || 'MyAndroidProject'}"
${modIncludes}
`;
    }

    // 1. Clean machine-specific local paths if requested
    if (opts.cleanLocalMachinePaths !== false) {
      // Matches blocks like:
      // maven {
      //     url = uri("E:/AndroidStudioProjects2/...")
      // }
      const machineBlockRegex = /\s*\/\/[^\n]*\n\s*maven\s*\{\s*url\s*=?\s*uri\(\s*['"][A-Za-z]:\/[^'"]+['"]\s*\)\s*\}/g;
      content = content.replace(machineBlockRegex, '');

      const directMachineBlockRegex = /\s*maven\s*\{\s*url\s*=?\s*uri\(\s*['"][A-Za-z]:\/[^'"]+['"]\s*\)\s*\}/g;
      content = content.replace(directMachineBlockRegex, '');
    }

    // 2. Add GitHub Packages repository if configured and not present
    if (opts.githubPackages && opts.githubPackages.owner && !content.includes('maven.pkg.github.com')) {
      const ghRepoBlock = `
        // GitHub Packages Maven Repository
        maven {
            url = uri("https://maven.pkg.github.com/${opts.githubPackages.owner}/${opts.githubPackages.repo || '*'}")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }`;

      if (content.includes('repositories {')) {
        // Insert inside the second repositories block (dependencyResolutionManagement) or the first
        const lastReposIdx = content.lastIndexOf('repositories {');
        content = content.slice(0, lastReposIdx + 'repositories {'.length) +
                  ghRepoBlock +
                  content.slice(lastReposIdx + 'repositories {'.length);
      }
    }

    return content;
  }

  /**
   * Patch root build.gradle safely.
   * @param {string} originalContent
   * @param {object} opts
   * @returns {string} Patched content
   */
  static patchRootBuild(originalContent = '', opts = {}) {
    let content = originalContent;

    if (!content.trim()) {
      const agpVer = opts.agpVersion || '8.3.2';
      const isApp = opts.projectType === 'app';
      return `// Top-level build file
plugins {
    id '${isApp ? 'com.android.application' : 'com.android.library'}' version '${agpVer}' apply false
}

tasks.register('clean', Delete) {
    delete rootProject.layout.buildDirectory
}
`;
    }

    // Ensure clean task uses layout.buildDirectory if modern AGP
    if (content.includes('delete rootProject.buildDir')) {
      content = content.replace('delete rootProject.buildDir', 'delete rootProject.layout.buildDirectory');
    }

    return content;
  }

  /**
   * Patch module build.gradle (e.g. app/build.gradle or library/build.gradle).
   * Preserves all developer dependencies and configurations.
   * @param {string} originalContent
   * @param {object} opts
   * @returns {string} Patched content
   */
  static patchModuleBuild(originalContent = '', opts = {}) {
    let content = originalContent;

    if (!content.trim()) {
      // Fallback: generate basic build.gradle according to type
      return opts.projectType === 'app'
        ? GradlePatcher.#generateBasicAppBuild(opts)
        : GradlePatcher.#generateBasicLibraryBuild(opts);
    }

    // 1. Machine-specific local paths in module build.gradle
    if (opts.cleanLocalMachinePaths !== false) {
      const machineBlockRegex = /\s*maven\s*\{\s*url\s*=?\s*uri\(\s*['"][A-Za-z]:\/[^'"]+['"]\s*\)\s*\}/g;
      content = content.replace(machineBlockRegex, '');
    }

    // 2. For Android Library: ensure publishing config is present if requested
    if (opts.projectType === 'library' && opts.configurePublishing) {
      if (!content.includes('maven-publish')) {
        // Add plugin id 'maven-publish'
        if (content.includes('plugins {')) {
          content = content.replace('plugins {', "plugins {\n    id 'maven-publish'");
        }
      }

      // Ensure publishing block inside android { ... } safely with brace awareness
      if (content.includes('android {')) {
        const ensured = GradleAstHelper.ensureAndroidPublishing(content, false);
        if (ensured.modified) {
          content = ensured.content;
        }
      }

      // Add afterEvaluate publishing configuration if not present
      if (!content.includes('publications {') && !content.includes('MavenPublication')) {
        const groupId = opts.groupId || opts.packageName || 'com.example.library';
        const artifactId = opts.artifactId || opts.name || 'library';
        const version = opts.version || '1.0.0';

        const afterEvaluateBlock = `
// =====================================================
// MAVEN PUBLISHING CONFIGURATION
// =====================================================
afterEvaluate {
    publishing {
        publications {
            release(MavenPublication) {
                from components.release
                groupId = "${groupId}"
                artifactId = "${artifactId}"
                version = "${version}"

                pom {
                    name = "${artifactId}"
                    description = "${opts.description || 'Android Library'}"
                    url = "${opts.repoUrl || ''}"
                }
            }
        }
    }
}
`;
        content += '\n' + afterEvaluateBlock;
      }
    }

    return content;
  }

  static #generateBasicAppBuild(opts) {
    const compileSdk = opts.compileSdk || '34';
    const minSdk = opts.minSdk || '24';
    const targetSdk = opts.targetSdk || '34';
    const namespace = opts.namespace || opts.packageName || 'com.example.app';
    const javaVer = opts.jdkRequirement || '17';

    return `plugins {
    id 'com.android.application'
}

android {
    namespace '${namespace}'
    compileSdk ${compileSdk}

    defaultConfig {
        applicationId "${namespace}"
        minSdk ${minSdk}
        targetSdk ${targetSdk}
        versionCode 1
        versionName "${opts.version || '1.0.0'}"

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
        debug {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_${javaVer}
        targetCompatibility JavaVersion.VERSION_${javaVer}
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.2.1'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.6.1'
}
`;
  }

  static #generateBasicLibraryBuild(opts) {
    const compileSdk = opts.compileSdk || '34';
    const minSdk = opts.minSdk || '21';
    const targetSdk = opts.targetSdk || '34';
    const namespace = opts.namespace || opts.packageName || 'com.example.library';
    const name = opts.name || 'library';
    const artifactId = opts.repoName || opts.artifactId || name.toLowerCase().replace(/[^a-z0-9_-]/g, '-');
    const version = opts.version || '1.0.0';
    const javaVer = opts.jdkRequirement || '17';
    const author = opts.author || 'Developer';
    const description = opts.description || 'Android Library';
    const license = opts.license || 'Apache-2.0';
    const repoUrl = opts.repoUrl || `https://github.com/${author}/${artifactId}`;
    const authorId = author.toLowerCase().replace(/[^a-z0-9]/g, '') || 'developer';

    if (opts.isKts) {
      return `plugins {
    id("com.android.library")
    \`maven-publish\`
}

val libraryGroupId = "${namespace}"
val libraryArtifactId = "${artifactId}"
val libraryVersion = "${version}"

android {
    namespace = "${namespace}"
    compileSdk = ${compileSdk}

    defaultConfig {
        minSdk = ${minSdk}
        targetSdk = ${targetSdk}
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            matchingFallbacks += listOf("release")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_${javaVer}
        targetCompatibility = JavaVersion.VERSION_${javaVer}
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
    implementation("androidx.core:core:1.12.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components.findByName("release") ?: components.findByName("java") ?: components.findByName("default")!!)
                groupId = libraryGroupId
                artifactId = libraryArtifactId
                version = libraryVersion

                pom {
                    name.set("${name}")
                    description.set("${description}")
                    url.set("${repoUrl}")
                    licenses {
                        license {
                            name.set("${license}")
                            url.set("https://opensource.org/licenses/${license}")
                        }
                    }
                    developers {
                        developer {
                            id.set("${authorId}")
                            name.set("${author}")
                        }
                    }
                    scm {
                        connection.set("scm:git:${repoUrl}.git")
                        developerConnection.set("scm:git:${repoUrl}.git")
                        url.set("${repoUrl}")
                    }
                }
            }
        }
    }

    tasks.matching { it.name in listOf("publishToMavenLocal", "publishReleasePublicationToMavenLocal") }.configureEach {
        mustRunAfter(tasks.matching { it.name == "assembleRelease" })
    }

    tasks.withType<Javadoc>().configureEach {
        (options as? StandardJavadocDocletOptions)?.apply {
            addStringOption("Xdoclint:none", "-quiet")
            encoding = "UTF-8"
            charSet = "UTF-8"
        }
        isFailOnError = false
    }
}
`;
    }

    return `plugins {
    id 'com.android.library'
    id 'maven-publish'
}

def libraryGroupId = "${namespace}"
def libraryArtifactId = "${artifactId}"
def libraryVersion = "${version}"

android {
    namespace '${namespace}'
    compileSdk ${compileSdk}

    defaultConfig {
        minSdk ${minSdk}
        targetSdk ${targetSdk}
        versionCode 1
        versionName libraryVersion

        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
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
        sourceCompatibility JavaVersion.VERSION_${javaVer}
        targetCompatibility JavaVersion.VERSION_${javaVer}
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

afterEvaluate {
    publishing {
        publications {
            release(MavenPublication) {
                from components.findByName('release') ?: components.findByName('java') ?: components.findByName('default')
                groupId = libraryGroupId
                artifactId = libraryArtifactId
                version = libraryVersion

                pom {
                    name        = '${name}'
                    description = '${description.replace(/'/g, "\\'")}'
                    url         = '${repoUrl}'
                    licenses {
                        license {
                            name = '${license}'
                            url  = 'https://opensource.org/licenses/${license}'
                        }
                    }
                    developers {
                        developer {
                            id   = '${authorId}'
                            name = '${author}'
                        }
                    }
                    scm {
                        connection          = 'scm:git:${repoUrl}.git'
                        developerConnection  = 'scm:git:${repoUrl}.git'
                        url                 = '${repoUrl}'
                    }
                }
            }
        }
    }

    // Configure task ordering without circular or recursive execution
    tasks.matching { it.name in ['publishToMavenLocal', 'publishReleasePublicationToMavenLocal'] }.configureEach {
        mustRunAfter(tasks.matching { it.name == 'assembleRelease' })
    }

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

  /**
   * Safely add or update dependencies in a build.gradle file using Brace-Aware AST engine.
   * Prevents syntax errors, unmatched closing braces, and handles scope changes.
   * @param {string} originalContent
   * @param {string|string[]} dependencies - Maven coordinate(s) e.g. 'com.github.infoadmstudio2020-cyber:native-logger-sdk:v1.0.1'
   * @param {string} [configuration='implementation'] - 'implementation' | 'api' | 'compileOnly' | 'testImplementation' | 'androidTestImplementation'
   * @returns {{ content: string, added: string[], updated: string[], skipped: string[] }}
   */
  static addDependencies(originalContent = '', dependencies = [], configuration = 'implementation') {
    let content = originalContent || '';
    const deps = Array.isArray(dependencies) ? dependencies : [dependencies];
    const added = [];
    const updated = [];
    const skipped = [];
    const isKts = content.includes('implementation(') || (content.includes('plugins {') && content.includes('kotlin'));

    for (const d of deps) {
      if (!d || !d.trim()) continue;
      const cleanNotation = d.trim().replace(/^['"]|['"]$/g, '');

      const result = GradleAstHelper.insertOrUpdateDependency(content, cleanNotation, configuration, isKts);
      content = result.content;

      if (result.added) {
        added.push(cleanNotation);
      } else if (result.updated) {
        updated.push(cleanNotation);
      } else if (result.skipped) {
        skipped.push(cleanNotation);
      }
    }

    return { content, added, updated, skipped };
  }

  /**
   * Safely remove a dependency from Gradle content without corrupting braces.
   * @param {string} originalContent
   * @param {string} coordinateOrName
   * @returns {{ content: string, removed: boolean }}
   */
  static removeDependency(originalContent = '', coordinateOrName = '') {
    if (!originalContent || !coordinateOrName) {
      return { content: originalContent, removed: false };
    }
    return GradleAstHelper.removeDependency(originalContent, coordinateOrName);
  }

  /**
   * Parse declared dependencies from Gradle content.
   * @param {string} content
   * @returns {Array<{ configuration: string, coordinate: string, group: string, name: string, version: string, rawLine: string }>}
   */
  static parseDeclaredDependencies(content = '') {
    if (!content) return [];
    const results = [];
    const regex = /^\s*(implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly|androidTestImplementation)\s*\(?['"]([^'"]+)['"]\)?/gm;
    let match;

    while ((match = regex.exec(content)) !== null) {
      const configuration = match[1];
      const coordinate = match[2].trim();
      const parts = coordinate.split(':');

      results.push({
        configuration,
        coordinate,
        group: parts[0] || '',
        name: parts[1] || '',
        version: parts[2] || '',
        rawLine: match[0].trim()
      });
    }

    return results;
  }

  /**
   * Generate standard Gradle build configuration based on project type.
   * @param {'pure-java'|'android-library'|'android-app'|'root'} type
   * @param {object} opts
   * @returns {string}
   */
  static generateStandardBuild(type, opts = {}) {
    if (type === 'pure-java') {
      const name = opts.name || 'PureJavaLib';
      const pkg = opts.packageName || 'com.example.javalib';
      const version = opts.version || '1.0.0';
      return `plugins {
    id 'java-library'
    id 'maven-publish'
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}

publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
            groupId = '${pkg}'
            artifactId = '${name.toLowerCase()}'
            version = '${version}'
        }
    }
}
`;
    }

    if (type === 'android-library') {
      return GradlePatcher.#generateBasicLibraryBuild(opts);
    }

    if (type === 'android-app') {
      return GradlePatcher.#generateBasicAppBuild(opts);
    }

    // Root build file
    const agp = opts.agpVersion || '8.3.2';
    return `// Top-level build file
plugins {
    id 'com.android.application' version '${agp}' apply false
    id 'com.android.library' version '${agp}' apply false
}

tasks.register('clean', Delete) {
    delete rootProject.layout.buildDirectory
}
`;
  }
}
