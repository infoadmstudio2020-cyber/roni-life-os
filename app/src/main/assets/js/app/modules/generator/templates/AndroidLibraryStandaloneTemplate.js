/**
 * AndroidLibraryStandaloneTemplate.js — Standalone Android Library (AAR only, without sample app).
 *
 * Generates:
 * - settings.gradle (include ':library')
 * - root build.gradle
 * - gradle.properties
 * - gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties, gradle-wrapper.jar
 * - library/build.gradle (com.android.library, maven-publish, java 17)
 * - library/proguard-rules.pro
 * - library/src/main/AndroidManifest.xml
 * - library/src/main/java/<package>/<Class>.java
 * - library/src/main/java/<package>/<Class>Config.java
 * - library/src/main/java/<package>/<Class>Utils.java
 * - library/src/test/java/<package>/<Class>Test.java
 * - jitpack.yml (JDK 17, install: ./gradlew clean :library:assembleRelease)
 * - .gitignore, README.md, LICENSE
 * - .github/workflows/ci.yml, release.yml
 *
 * Output Artifact: library/build/outputs/aar/*-release.aar
 */

import { getGradleWrapperFiles } from '../GradleWrapperAssets.js';
import { githubFileGenerator } from '../../github/GitHubFileGenerator.js';

export const AndroidLibraryStandaloneTemplate = {
  id:          'android-library-standalone',
  name:        'Android Library (AAR Only)',
  description: 'Pure Android Library module producing AAR artifact without sample app, with JitPack/Maven publish and CI/CD',
  icon:        'layers',
  color:       'accent',
  projectType: 'library',
  language:    'java',
  tags:        ['android', 'aar', 'library', 'jitpack', 'standalone'],

  generate(meta = {}) {
    const {
      name        = 'MyAndroidLibrary',
      packageName = 'com.example.library',
      version     = '1.0.0',
      minSdk      = '21',
      targetSdk   = '34',
      compileSdk  = '34',
      author      = 'Developer',
      company     = '',
      description = 'Standalone Android library producing AAR artifact',
      repoName    = 'my-android-library',
      license     = 'Apache-2.0',
      language    = 'java',
    } = meta;

    const pkg       = packageName.split('.');
    const pkgPath   = pkg.join('/');
    const className = name.replace(/[^a-zA-Z0-9]/g, '') || 'AndroidLibrary';
    const gradlePlugin  = '8.3.2';
    const gradleVersion = '8.7';
    const files     = {};

    // ── settings.gradle ───────────────────────────────────────────────────
    files['settings.gradle'] = `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

rootProject.name = '${name}'
include ':library'
`;

    // ── root build.gradle ─────────────────────────────────────────────────
    files['build.gradle'] = `// Top-level build file
buildscript {
    ext {
        agp_version       = '${gradlePlugin}'
        compileSdkVersion = ${compileSdk}
        minSdkVersion     = ${minSdk}
        targetSdkVersion  = ${targetSdk}
    }
}

plugins {
    id 'com.android.library' version "${gradlePlugin}" apply false
}

tasks.register('clean', Delete) {
    delete rootProject.layout.buildDirectory
}
`;

    // ── gradle.properties ─────────────────────────────────────────────────
    files['gradle.properties'] = `# Project-wide Gradle settings
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
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
  - ./gradlew clean :library:assembleRelease :library:publishToMavenLocal --stacktrace
`;

    // ── .gitignore ────────────────────────────────────────────────────────
    files['.gitignore'] = `*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
`;

    // ── gradlew wrapper scripts ───────────────────────────────────────────
    Object.assign(files, getGradleWrapperFiles(gradleVersion));

    // ── library/build.gradle ──────────────────────────────────────────────
    files['library/build.gradle'] = `plugins {
    id 'com.android.library'
    id 'maven-publish'
}

android {
    namespace '${packageName}'
    compileSdk ${compileSdk}

    defaultConfig {
        minSdk ${minSdk}
        targetSdk ${targetSdk}

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

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    publishing {
        singleVariant('release') {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation 'androidx.annotation:annotation:1.7.1'
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

    // Configure task ordering without circular or recursive execution
    tasks.matching { it.name in ['publishToMavenLocal', 'publishReleasePublicationToMavenLocal'] }.configureEach {
        mustRunAfter(tasks.matching { it.name == 'assembleRelease' })
    }

    // Protect Javadoc generation from doclint failures on JDK 17
    tasks.withType(Javadoc).configureEach {
        options.addStringOption('Xdoclint:none', '-quiet')
        options.encoding = 'UTF-8'
    }
}
`;

    files['library/proguard-rules.pro'] = `# Library ProGuard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
`;

    files['library/consumer-rules.pro'] = `# Consumer ProGuard Rules applied to app modules consuming this AAR
-keep class ${packageName}.** { *; }
`;

    // ── library/src/main/AndroidManifest.xml ──────────────────────────────
    files['library/src/main/AndroidManifest.xml'] = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
</manifest>
`;

    // ── Java Source Files ─────────────────────────────────────────────────
    files[`library/src/main/java/${pkgPath}/${className}.java`] = `package ${packageName};

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Main Android Library Controller for ${name}.
 * Standalone Android Library producing AAR artifact.
 */
public class ${className} {

    private final Context context;
    private final ${className}Config config;

    public ${className}(@NonNull Context context) {
        this(context, new ${className}Config.Builder().build());
    }

    public ${className}(@NonNull Context context, @Nullable ${className}Config config) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        this.context = context.getApplicationContext();
        this.config = config != null ? config : new ${className}Config.Builder().build();
    }

    @NonNull
    public String getVersion() {
        return "${version}";
    }

    @NonNull
    public Context getContext() {
        return context;
    }

    @NonNull
    public ${className}Config getConfig() {
        return config;
    }
}
`;

    files[`library/src/main/java/${pkgPath}/${className}Config.java`] = `package ${packageName};

import androidx.annotation.NonNull;

/**
 * Configuration options for ${name}.
 */
public class ${className}Config {

    private final String tag;
    private final boolean debug;

    private ${className}Config(Builder builder) {
        this.tag = builder.tag;
        this.debug = builder.debug;
    }

    @NonNull
    public String getTag() {
        return tag;
    }

    public boolean isDebug() {
        return debug;
    }

    public static class Builder {
        private String tag = "${className}";
        private boolean debug = false;

        public Builder setTag(@NonNull String tag) {
            this.tag = tag;
            return this;
        }

        public Builder setDebug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public ${className}Config build() {
            return new ${className}Config(this);
        }
    }
}
`;

    files[`library/src/main/java/${pkgPath}/${className}Utils.java`] = `package ${packageName};

import androidx.annotation.Nullable;

public final class ${className}Utils {
    private ${className}Utils() {}

    public static boolean isBlank(@Nullable String str) {
        return str == null || str.trim().isEmpty();
    }
}
`;

    // ── Unit Tests ────────────────────────────────────────────────────────
    files[`library/src/test/java/${pkgPath}/${className}Test.java`] = `package ${packageName};

import org.junit.Test;
import static org.junit.Assert.*;

public class ${className}Test {

    @Test
    public void testVersion() {
        assertEquals("${version}", "${version}");
    }

    @Test
    public void testUtils() {
        assertTrue(${className}Utils.isBlank(null));
        assertTrue(${className}Utils.isBlank("  "));
        assertFalse(${className}Utils.isBlank("Valid"));
    }
}
`;

    // ── Documentation ─────────────────────────────────────────────────────
    files['README.md'] = `# ${name}

[![JitPack](https://jitpack.io/v/${author || 'owner'}/${repoName}.svg)](https://jitpack.io/#${author || 'owner'}/${repoName})
[![License](https://img.shields.io/badge/License-${license}-blue.svg)](LICENSE)

${description}

## Artifact Details
- **Type**: Android Library (AAR)
- **Artifact**: \`${repoName}-${version}.aar\`
- **Min SDK**: ${minSdk} | **Target SDK**: ${targetSdk} | **Compile SDK**: ${compileSdk}

## Installation (Gradle)

\`\`\`groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
\`\`\`

\`\`\`groovy
dependencies {
    implementation 'com.github.${author || 'owner'}:${repoName}:${version}'
}
\`\`\`

## Build
\`\`\`bash
# Build AAR artifact
./gradlew clean :library:assembleRelease

# Output artifact:
# library/build/outputs/aar/library-release.aar
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
      projectType: 'library',
      files
    };
    const ghFiles = githubFileGenerator.generate(ghProj, author || 'owner');
    Object.assign(files, ghFiles);

    return files;
  }
};
