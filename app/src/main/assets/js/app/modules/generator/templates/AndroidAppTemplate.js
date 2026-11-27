/**
 * AndroidAppTemplate.js — Complete Android App Project Template for Android Studio.
 *
 * Generates:
 * - settings.gradle (include ':app')
 * - root build.gradle
 * - gradle.properties
 * - gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties
 * - app/build.gradle (com.android.application, defaultConfig, compileOptions, dependencies)
 * - app/proguard-rules.pro
 * - app/src/main/AndroidManifest.xml
 * - app/src/main/java/<package>/MainActivity.java (or .kt)
 * - app/src/main/res/layout/activity_main.xml
 * - app/src/main/res/values/strings.xml, colors.xml, themes.xml
 * - app/src/test/java/ExampleUnitTest.java
 * - .gitignore, README.md
 * - .github/workflows/ci.yml, release.yml
 */

import { githubFileGenerator } from '../../github/GitHubFileGenerator.js';
import { getLauncherIconFiles } from './LauncherIconAssets.js';
import { getGradleWrapperFiles } from '../GradleWrapperAssets.js';

export const AndroidAppTemplate = {
  id: 'android-app',
  name: 'Android App',
  description: 'Full Android Studio application project with MainActivity, layout, and CI/CD',
  icon: 'smartphone',
  color: 'success',
  projectType: 'app',
  language: 'java',
  tags: ['android', 'app', 'apk', 'aab'],

  generate(meta = {}) {
    const name = meta.name || 'MyAndroidApp';
    const pkg = meta.packageName || 'com.example.app';
    const ver = meta.version || '1.0.0';
    const desc = meta.description || 'Android Studio Application';
    const minSdk = meta.minSdk || 24;
    const targetSdk = meta.targetSdk || 34;
    const compileSdk = meta.compileSdk || 34;
    const lang = meta.language || 'java';
    const isKotlin = lang === 'kotlin';
    const jdkVer = meta.jdkRequirement || '17';
    const pkgPath = pkg.replace(/\./g, '/');

    const files = {};

    // ── settings.gradle ───────────────────────────────────────────────────
    files['settings.gradle'] = `pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\\\.android.*")
                includeGroupByRegex("com\\\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "${name}"
include(":app")
`;

    // ── root build.gradle ─────────────────────────────────────────────────
    files['build.gradle'] = `// Top-level build file
plugins {
    id 'com.android.application' version '8.3.2' apply false
    ${isKotlin ? "id 'org.jetbrains.kotlin.android' version '1.9.22' apply false" : ''}
}

tasks.register('clean', Delete) {
    delete rootProject.layout.buildDirectory
}
`;

    // ── gradle.properties ─────────────────────────────────────────────────
    files['gradle.properties'] = `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
`;

    // ── gradle wrapper ────────────────────────────────────────────────────
    Object.assign(files, getGradleWrapperFiles('8.7'));

    // ── app/build.gradle ──────────────────────────────────────────────────
    files['app/build.gradle'] = `plugins {
    id 'com.android.application'
    ${isKotlin ? "id 'org.jetbrains.kotlin.android'" : ''}
}

android {
    namespace '${pkg}'
    compileSdk ${compileSdk}

    defaultConfig {
        applicationId "${pkg}"
        minSdk ${minSdk}
        targetSdk ${targetSdk}
        versionCode 1
        versionName "${ver}"

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
        sourceCompatibility JavaVersion.VERSION_${jdkVer}
        targetCompatibility JavaVersion.VERSION_${jdkVer}
    }
    ${isKotlin ? `kotlinOptions {\n        jvmTarget = '${jdkVer}'\n    }` : ''}
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.2.1'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.6.1'
}
`;

    files['app/proguard-rules.pro'] = `# ProGuard rules for ${name}
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
`;

    // ── app/src/main/AndroidManifest.xml ──────────────────────────────────
    files['app/src/main/AndroidManifest.xml'] = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="${name}"
        android:roundIcon="@mipmap/ic_launcher"
        android:supportsRtl="true"
        android:theme="@style/Theme.${name.replace(/[^a-zA-Z0-9]/g, '')}">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
`;

    // ── Source code (Java or Kotlin) ──────────────────────────────────────
    if (isKotlin) {
      files[`app/src/main/java/${pkgPath}/MainActivity.kt`] = `package ${pkg}

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
`;
    } else {
      files[`app/src/main/java/${pkgPath}/MainActivity.java`] = `package ${pkg};

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
`;
    }

    // ── Layout & Resources ────────────────────────────────────────────────
    files['app/src/main/res/layout/activity_main.xml'] = `<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="24dp">

    <TextView
        android:id="@+id/tv_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="${name}"
        android:textSize="24sp"
        android:textStyle="bold"
        app:layout_constraintBottom_toTopOf="@id/tv_subtitle"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintVertical_chainStyle="packed" />

    <TextView
        android:id="@+id/tv_subtitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="${desc}"
        android:textSize="14sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/tv_title" />

</androidx.constraintlayout.widget.ConstraintLayout>
`;

    files['app/src/main/res/values/strings.xml'] = `<resources>
    <string name="app_name">${name}</string>
</resources>
`;

    files['app/src/main/res/values/colors.xml'] = `<resources>
    <color name="primary">#6200EE</color>
    <color name="primary_variant">#3700B3</color>
    <color name="secondary">#03DAC6</color>
</resources>
`;

    files['app/src/main/res/values/themes.xml'] = `<resources>
    <style name="Theme.${name.replace(/[^a-zA-Z0-9]/g, '')}" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryVariant">@color/primary_variant</item>
        <item name="colorSecondary">@color/secondary</item>
    </style>
</resources>
`;

    // ── Launcher icon resources (@mipmap/ic_launcher) ───────────────────────
    // Adaptive icon (API 26+) + legacy vector fallback (API 21–25) — a real
    // vector launcher icon instead of the Android system default icon.
    Object.assign(files, getLauncherIconFiles('app/src/main/res'));

    // ── Tests ─────────────────────────────────────────────────────────────
    files[`app/src/test/java/${pkgPath}/ExampleUnitTest.java`] = `package ${pkg};

import org.junit.Test;
import static org.junit.Assert.*;

public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }
}
`;

    // ── gitignore & readme ────────────────────────────────────────────────
    files['.gitignore'] = `*.iml
.gradle
/local.properties
/.idea/
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
*.apk
*.aab
*.aar
`;

    files['README.md'] = `# ${name}

${desc}

## Overview
Android Studio application project configured for automated builds via GitHub Actions.

## Building Locally
\`\`\`bash
./gradlew assembleDebug
./gradlew assembleRelease
\`\`\`

## Generated Artifacts
- APK: \`app/build/outputs/apk/release/app-release.apk\`
- AAB: \`app/build/outputs/bundle/release/app-release.aab\`
`;

    // ── GitHub Workflows (App-specific) ────────────────────────────────────
    files['.github/workflows/ci.yml'] = githubFileGenerator.generateCIWorkflow({
      projectType: 'app',
      jdkVersion: jdkVer,
      compileSdk,
      branch: 'main'
    });

    files['.github/workflows/release.yml'] = githubFileGenerator.generateReleaseWorkflow({
      projectType: 'app',
      jdkVersion: jdkVer,
      compileSdk,
      publishToPackages: false
    });

    return files;
  }
};
