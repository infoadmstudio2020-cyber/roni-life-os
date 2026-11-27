/**
 * test-environment-and-dependencies.js
 *
 * Comprehensive validation test for:
 * 1. Dark/Light Theme & Color contrast definitions (inputs, selects, options, buttons, cards)
 * 2. EnvironmentManager (Gradle, AGP, JDK, compileSdk, minSdk, targetSdk)
 * 3. DependencyCompatibilityAdvisor (Dependency version suggestions, SDK conflicts, legacy library detection, AST updates)
 */

import assert from 'assert';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

import { EnvironmentManager } from '../js/app/modules/environment/EnvironmentManager.js';
import { DependencyCompatibilityAdvisor, DEPENDENCY_KNOWLEDGE_BASE } from '../js/app/modules/environment/DependencyCompatibilityAdvisor.js';
import { GradleAstHelper } from '../js/app/modules/generator/GradleAstHelper.js';

let passedTests = 0;
let totalTests = 0;

function it(name, fn) {
  totalTests++;
  try {
    fn();
    console.log(`  ✓ PASS: ${name}`);
    passedTests++;
  } catch (err) {
    console.error(`  ✗ FAIL: ${name}`);
    console.error(err);
    process.exitCode = 1;
  }
}

async function itAsync(name, fn) {
  totalTests++;
  try {
    await fn();
    console.log(`  ✓ PASS: ${name}`);
    passedTests++;
  } catch (err) {
    console.error(`  ✗ FAIL: ${name}`);
    console.error(err);
    process.exitCode = 1;
  }
}

console.log('=== Test Suite: Project Environment Manager & Dependency Suggestions ===');

// --- 1. Theme & Color Verification ---
it('CSS variables include card, panel, muted, input, and surface in both themes', () => {
  const varsCss = fs.readFileSync(path.join(rootDir, 'css/base/variables.css'), 'utf-8');
  assert(varsCss.includes('--bg-card:'), 'Must define --bg-card in variables.css');
  assert(varsCss.includes('--bg-panel:'), 'Must define --bg-panel in variables.css');
  assert(varsCss.includes('--bg-muted:'), 'Must define --bg-muted in variables.css');
  assert(varsCss.includes('--bg-input:'), 'Must define --bg-input in variables.css');
});

it('Inputs CSS explicitly handles select, option, input colors in light and dark mode', () => {
  const inputsCss = fs.readFileSync(path.join(rootDir, 'css/components/inputs.css'), 'utf-8');
  assert(inputsCss.includes('[data-theme="light"] .select'), 'Must have light theme select overrides');
  assert(inputsCss.includes('[data-theme="light"] .select option'), 'Must have light theme option background and color');
  assert(inputsCss.includes('[data-theme="dark"] .select option'), 'Must have dark theme option background and color');
  assert(inputsCss.includes('[data-theme="light"] .input'), 'Must have light theme input colors');
  assert(inputsCss.includes('[data-theme="dark"] .input'), 'Must have dark theme input colors');
});

// --- 2. EnvironmentManager Configuration & SDK Analysis ---
it('EnvironmentManager accurately parses Gradle, AGP, JDK, and SDK versions', () => {
  const mockFiles = {
    'settings.gradle': `rootProject.name = "TestLib"\ninclude ':library'`,
    'library/build.gradle': `
      plugins {
        id 'com.android.library' version '8.4.2'
      }
      android {
        namespace 'com.example.test'
        compileSdk 34
        defaultConfig {
          minSdk 21
          targetSdk 34
        }
        compileOptions {
          sourceCompatibility JavaVersion.VERSION_17
          targetCompatibility JavaVersion.VERSION_17
        }
      }
    `,
    'gradle/wrapper/gradle-wrapper.properties': 'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip',
  };

  const projectType = EnvironmentManager.detectProjectType(mockFiles);
  assert.strictEqual(projectType, 'ANDROID_LIBRARY');

  const versions = EnvironmentManager.extractCurrentVersions(mockFiles, projectType);
  assert.strictEqual(versions.agpVersion, '8.4.2');
  assert.strictEqual(versions.compileSdk, 34);
  assert.strictEqual(versions.minSdk, 21);
  assert.strictEqual(versions.targetSdk, 34);
  assert.strictEqual(versions.gradleVersion, '8.7');
  assert.strictEqual(versions.jdkVersion, '17');
  assert.strictEqual(versions.namespace, 'com.example.test');
});

it('EnvironmentManager generates clean build files with targetSdk and toolchain', () => {
  const config = {
    projectType: 'ANDROID_LIBRARY',
    name: 'SampleLib',
    groupId: 'com.example.sample',
    artifactId: 'samplelib',
    version: '1.0.0',
    namespace: 'com.example.sample',
    agpVersion: '8.4.2',
    gradleVersion: '8.7',
    jdkVersion: '17',
    compileSdk: 35,
    minSdk: 21,
    targetSdk: 35,
  };

  const generated = EnvironmentManager.generateConfigurationFiles(config);
  assert(generated['settings.gradle'].includes('SampleLib'));
  assert(generated['build.gradle'].includes("compileSdk 35"));
  assert(generated['build.gradle'].includes("minSdk 21"));
  assert(generated['build.gradle'].includes("targetSdk 35"));
  assert(generated['gradle/wrapper/gradle-wrapper.properties'].includes('gradle-8.7-bin.zip'));
});

// --- 3. DependencyCompatibilityAdvisor Analysis & Suggestions ---
it('DependencyCompatibilityAdvisor suggests compatible versions based on compileSdk', () => {
  const mockFiles = {
    'build.gradle': `
      dependencies {
        implementation 'androidx.core:core-ktx:1.10.1'
        implementation 'androidx.appcompat:appcompat:1.5.1'
        implementation 'com.google.android.material:material:1.8.0'
      }
    `
  };

  // With compileSdk 34:
  const report34 = DependencyCompatibilityAdvisor.analyze(mockFiles, { compileSdk: 34, minSdk: 21 });
  assert.strictEqual(report34.totalDeclared, 3);
  assert.strictEqual(report34.suggestions.length, 3);

  const coreKtx34 = report34.suggestions.find(s => s.groupArtifact === 'androidx.core:core-ktx');
  assert.strictEqual(coreKtx34.recommendedVersion, '1.13.1');

  const appcompat34 = report34.suggestions.find(s => s.groupArtifact === 'androidx.appcompat:appcompat');
  assert.strictEqual(appcompat34.recommendedVersion, '1.7.0');

  // With compileSdk 35:
  const report35 = DependencyCompatibilityAdvisor.analyze(mockFiles, { compileSdk: 35, minSdk: 21 });
  const coreKtx35 = report35.suggestions.find(s => s.groupArtifact === 'androidx.core:core-ktx');
  assert.strictEqual(coreKtx35.recommendedVersion, '1.15.0');
});

it('DependencyCompatibilityAdvisor detects SDK incompatibility when library requires higher compileSdk', () => {
  const mockFiles = {
    'build.gradle': `
      dependencies {
        // 1.15.0 requires compileSdk 35
        implementation 'androidx.core:core-ktx:1.15.0'
      }
    `
  };

  // Current project compileSdk is 33
  const report = DependencyCompatibilityAdvisor.analyze(mockFiles, { compileSdk: 33, minSdk: 21 });
  assert(report.conflicts.length > 0, 'Must detect SDK incompatibility conflict');
  const conflict = report.conflicts[0];
  assert.strictEqual(conflict.type, 'sdk_version_incompatible');
  assert(conflict.message.includes('requires compileSdk 35'));
});

it('DependencyCompatibilityAdvisor detects legacy Android Support Library conflict', () => {
  const mockFiles = {
    'build.gradle': `
      dependencies {
        implementation 'com.android.support:appcompat-v7:28.0.0'
      }
    `
  };

  const report = DependencyCompatibilityAdvisor.analyze(mockFiles, { compileSdk: 34 });
  assert(report.conflicts.length > 0, 'Must detect legacy support library');
  assert.strictEqual(report.conflicts[0].type, 'legacy_support');
});

it('GradleAstHelper accurately updates dependency version without breaking structure', () => {
  const initialGradle = `plugins {
    id 'com.android.library'
}

dependencies {
    implementation 'androidx.core:core-ktx:1.10.1' // Old core
    testImplementation 'junit:junit:4.13.2'
}
`;

  const updated = GradleAstHelper.insertOrUpdateDependency(
    initialGradle,
    'androidx.core:core-ktx:1.13.1',
    'implementation',
    false
  );

  assert(updated.content.includes("implementation 'androidx.core:core-ktx:1.13.1'"));
  assert(!updated.content.includes("1.10.1"));
  assert(updated.content.includes("testImplementation 'junit:junit:4.13.2'"));
});

console.log(`\nResults: ${passedTests}/${totalTests} tests passed.`);
if (passedTests !== totalTests) {
  process.exit(1);
}
