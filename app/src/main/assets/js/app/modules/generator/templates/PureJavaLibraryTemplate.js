/**
 * PureJavaLibraryTemplate.js — Pure Java Library Template producing JAR artifact.
 *
 * Generates:
 * - settings.gradle (rootProject.name)
 * - build.gradle (java-library, maven-publish, java 17, sourcesJar, javadocJar)
 * - gradle.properties
 * - gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties, gradle-wrapper.jar
 * - src/main/java/<package>/<Class>.java
 * - src/main/java/<package>/<Class>Config.java
 * - src/main/java/<package>/<Class>Utils.java
 * - src/test/java/<package>/<Class>Test.java
 * - jitpack.yml (JDK 17, install: ./gradlew build publishToMavenLocal)
 * - .gitignore, README.md, LICENSE
 * - .github/workflows/ci.yml, release.yml
 *
 * Output Artifact: build/libs/*.jar
 */

import { getGradleWrapperFiles } from '../GradleWrapperAssets.js';
import { githubFileGenerator } from '../../github/GitHubFileGenerator.js';

export const PureJavaLibraryTemplate = {
  id:          'pure-java-library',
  name:        'Pure Java Library (JAR)',
  description: 'Pure Java standard library for JVM/Android backend with JAR artifact, tests, and Maven/JitPack publishing',
  icon:        'coffee',
  color:       'accent',
  projectType: 'java-library',
  language:    'java',
  tags:        ['java', 'jar', 'library', 'jitpack', 'maven'],

  generate(meta = {}) {
    const {
      name        = 'MyJavaLibrary',
      packageName = 'com.example.javalib',
      version     = '1.0.0',
      author      = 'Developer',
      company     = '',
      description = 'A pure Java library producing JAR artifact',
      repoName    = 'my-java-library',
      license     = 'Apache-2.0',
    } = meta;

    const pkg       = packageName.split('.');
    const pkgPath   = pkg.join('/');
    const className = name.replace(/[^a-zA-Z0-9]/g, '') || 'JavaLibrary';
    const gradleVer = '8.7';
    const files     = {};

    // ── settings.gradle ───────────────────────────────────────────────────
    files['settings.gradle'] = `pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = '${name}'
`;

    // ── build.gradle ──────────────────────────────────────────────────────
    files['build.gradle'] = `plugins {
    id 'java-library'
    id 'maven-publish'
}

group = '${packageName}'
version = '${version}'
description = '${description.replace(/'/g, "\\'")}'

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Core Dependencies
    api 'org.slf4j:slf4j-api:2.0.12'

    // Testing
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
    testLogging {
        events 'passed', 'skipped', 'failed'
    }
}

tasks.named('jar') {
    manifest {
        attributes(
            'Implementation-Title': project.name,
            'Implementation-Version': project.version,
            'Automatic-Module-Name': '${packageName}'
        )
    }
}

publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
            groupId = '${packageName}'
            artifactId = '${repoName}'
            version = '${version}'

            pom {
                name = '${name}'
                description = '${description.replace(/'/g, "\\'")}'
                url = 'https://github.com/${author || 'owner'}/${repoName}'
                licenses {
                    license {
                        name = '${license}'
                        url = 'https://opensource.org/licenses/${license}'
                    }
                }
            }
        }
    }
}
`;

    // ── gradle.properties ─────────────────────────────────────────────────
    files['gradle.properties'] = `# Project-wide Gradle settings
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
VERSION_NAME=${version}
GROUP=${packageName}
POM_ARTIFACT_ID=${repoName}
`;

    // ── jitpack.yml ───────────────────────────────────────────────────────
    files['jitpack.yml'] = `jdk:
  - openjdk17
before_install:
  - git config --global --add safe.directory '*' || true
  - chmod +x gradlew
install:
  - ./gradlew clean build test publishToMavenLocal --stacktrace
`;

    // ── .gitignore ────────────────────────────────────────────────────────
    files['.gitignore'] = `*.iml
.gradle
/.idea
.DS_Store
/build
out/
`;

    // ── gradlew wrapper scripts ───────────────────────────────────────────
    Object.assign(files, getGradleWrapperFiles(gradleVer));

    // ── Java Source Files ─────────────────────────────────────────────────
    files[`src/main/java/${pkgPath}/${className}.java`] = `package ${packageName};

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for ${name}.
 * Pure Java library producing standard JAR artifact.
 */
public class ${className} {

    private static final Logger logger = LoggerFactory.getLogger(${className}.class);
    private final ${className}Config config;

    public ${className}() {
        this(new ${className}Config.Builder().build());
    }

    public ${className}(${className}Config config) {
        this.config = config != null ? config : new ${className}Config.Builder().build();
        logger.info("${className} initialized with version: {}", getVersion());
    }

    /**
     * Returns current library version.
     */
    public String getVersion() {
        return "${version}";
    }

    /**
     * Executes the primary action of the library.
     * @param input Input message
     * @return Formatted response
     */
    public String execute(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Input string cannot be null or blank");
        }
        logger.debug("Executing ${className} with input: {}", input);
        return "[" + config.getPrefix() + "] " + input.trim();
    }

    public ${className}Config getConfig() {
        return config;
    }
}
`;

    files[`src/main/java/${pkgPath}/${className}Config.java`] = `package ${packageName};

/**
 * Configuration options for ${name}.
 */
public class ${className}Config {

    private final String prefix;
    private final boolean debugEnabled;

    private ${className}Config(Builder builder) {
        this.prefix = builder.prefix;
        this.debugEnabled = builder.debugEnabled;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static class Builder {
        private String prefix = "${className}";
        private boolean debugEnabled = false;

        public Builder setPrefix(String prefix) {
            if (prefix != null && !prefix.trim().isEmpty()) {
                this.prefix = prefix.trim();
            }
            return this;
        }

        public Builder setDebugEnabled(boolean debugEnabled) {
            this.debugEnabled = debugEnabled;
            return this;
        }

        public ${className}Config build() {
            return new ${className}Config(this);
        }
    }
}
`;

    files[`src/main/java/${pkgPath}/${className}Utils.java`] = `package ${packageName};

/**
 * Utility helper methods for ${name}.
 */
public final class ${className}Utils {

    private ${className}Utils() {
        // Utility class
    }

    public static boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static String sanitize(String input) {
        return input == null ? "" : input.trim();
    }
}
`;

    // ── Unit Tests ────────────────────────────────────────────────────────
    files[`src/test/java/${pkgPath}/${className}Test.java`] = `package ${packageName};

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ${className}Test {

    private ${className} instance;

    @BeforeEach
    void setUp() {
        instance = new ${className}();
    }

    @Test
    void testVersion() {
        assertEquals("${version}", instance.getVersion());
    }

    @Test
    void testExecuteValid() {
        String result = instance.execute("Hello World");
        assertNotNull(result);
        assertTrue(result.contains("Hello World"));
    }

    @Test
    void testExecuteBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> instance.execute("   "));
    }
}
`;

    // ── Documentation ─────────────────────────────────────────────────────
    files['README.md'] = `# ${name}

[![JitPack](https://jitpack.io/v/${author || 'owner'}/${repoName}.svg)](https://jitpack.io/#${author || 'owner'}/${repoName})
[![License](https://img.shields.io/badge/License-${license}-blue.svg)](LICENSE)

${description}

## Artifact Details
- **Type**: Pure Java Library (JAR)
- **Artifact**: \`${repoName}-${version}.jar\`
- **JDK Compatibility**: Java 17+ (compatible with modern JVM and Android apps)

## Installation (Gradle)

Add JitPack repository to your root \`build.gradle\` or \`settings.gradle\`:

\`\`\`groovy
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
\`\`\`

Add the dependency:

\`\`\`groovy
dependencies {
    implementation 'com.github.${author || 'owner'}:${repoName}:${version}'
}
\`\`\`

## Quick Start

\`\`\`java
import ${packageName}.${className};

public class Main {
    public static void main(String[] args) {
        ${className} client = new ${className}();
        String result = client.execute("Hello Java!");
        System.out.println(result);
    }
}
\`\`\`

## Building from Source

\`\`\`bash
# Build JAR and run tests
./gradlew clean build test

# Generated JAR artifact is located at:
# build/libs/${repoName}-${version}.jar
\`\`\`

## License
${license}
`;

    files['LICENSE'] = `Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/

Copyright ${new Date().getFullYear()} ${company || author || 'Developer'}
`;

    // ── CI/CD GitHub Actions Workflows ────────────────────────────────────
    const ghProj = {
      name,
      description,
      packageName,
      version,
      repoName,
      license,
      projectType: 'java-library',
      files
    };
    const ghFiles = githubFileGenerator.generate(ghProj, author || 'owner');
    Object.assign(files, ghFiles);

    return files;
  }
};
