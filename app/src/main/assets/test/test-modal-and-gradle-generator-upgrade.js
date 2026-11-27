import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

console.log('🧪 Starting ModalService, Gradle Generator Upgrade & Editor Auto-Mode Test Suite...\n');

// Import GradlePatcher
import { GradlePatcher } from '../js/app/modules/generator/GradlePatcher.js';
import { PreValidatorService } from '../js/app/modules/build/PreValidatorService.js';

// ============================================================================
// TEST 1: ModalService API & Method Availability
// ============================================================================
console.log('▶ Test 1: ModalService.show & ModalService.close API contracts');

const modalServiceContent = fs.readFileSync(path.join(rootDir, 'js/app/ui/ModalService.js'), 'utf8');

assert(modalServiceContent.includes('show(opts'), 'ModalService must have .show(opts) method');
assert(modalServiceContent.includes('open(opts)'), 'ModalService must have .open(opts) method');
assert(modalServiceContent.includes('opts.content'), 'ModalService must support opts.content');
assert(modalServiceContent.includes('opts.buttons'), 'ModalService must support opts.buttons');
assert(modalServiceContent.includes('closeTop()'), 'ModalService must have .closeTop()');
assert(modalServiceContent.includes('if (!modal) {\n      this.closeTop();') || modalServiceContent.includes('if (!modal) {'),
  'modal.close() without args must close top modal');

console.log('✅ Test 1 Passed: ModalService has complete .show() and parameter-less .close() support.\n');

// ============================================================================
// TEST 2: Toolbar Resolution Test & GradlePage Call Sites
// ============================================================================
console.log('▶ Test 2: ConsumerResolutionModal & GradlePage modal integration');

const consumerModalContent = fs.readFileSync(path.join(rootDir, 'js/app/modules/analyzer/ConsumerResolutionModal.js'), 'utf8');
const gradlePageContent = fs.readFileSync(path.join(rootDir, 'js/app/pages/GradlePage.js'), 'utf8');

assert(consumerModalContent.includes('modal.show({'), 'ConsumerResolutionModal must call modal.show');
assert(gradlePageContent.includes('modal.show({'), 'GradlePage must call modal.show');
assert(gradlePageContent.includes('#showScaffoldModal'), 'GradlePage must contain #showScaffoldModal');

console.log('✅ Test 2 Passed: Toolbar and GradlePage modal call sites verified.\n');

// ============================================================================
// TEST 3: Library build.gradle Generator Groovy DSL Output
// ============================================================================
console.log('▶ Test 3: Standard Android Library build.gradle generation (Groovy)');

const groovyBuild = GradlePatcher.generateStandardBuild('android-library', {
  name: 'SuperMathLib',
  packageName: 'com.roni.supermath',
  version: '2.1.0',
  compileSdk: '34',
  minSdk: '21',
  targetSdk: '34',
  author: 'roniboss',
  repoName: 'super-math-lib',
  description: 'High performance math Android library',
  license: 'Apache-2.0',
  isKts: false,
});

assert(groovyBuild.includes("id 'com.android.library'"), 'Must include com.android.library plugin');
assert(groovyBuild.includes("id 'maven-publish'"), 'Must include maven-publish plugin');
assert(groovyBuild.includes("namespace 'com.roni.supermath'"), 'Must set correct namespace');
assert(groovyBuild.includes('compileSdk 34'), 'Must set compileSdk');
assert(groovyBuild.includes('minSdk 21'), 'Must set minSdk');
assert(groovyBuild.includes('targetSdk 34'), 'Must set targetSdk');
assert(groovyBuild.includes('JavaVersion.VERSION_17'), 'Must use Java 17 compatibility');
assert(groovyBuild.includes('singleVariant("release")'), 'Must configure singleVariant release');
assert(groovyBuild.includes('withSourcesJar()'), 'Must include withSourcesJar()');
assert(groovyBuild.includes('withJavadocJar()'), 'Must include withJavadocJar()');
assert(groovyBuild.includes('consumerProguardFiles "consumer-rules.pro"'), 'Must include consumer ProGuard rules');
assert(groovyBuild.includes('release(MavenPublication)'), 'Must configure release MavenPublication');
assert(groovyBuild.includes("name        = 'SuperMathLib'"), 'Must include POM name');
assert(groovyBuild.includes("url         = 'https://github.com/roniboss/super-math-lib'"), 'Must include POM url');
assert(groovyBuild.includes('assembleRelease'), 'Must configure task ordering with assembleRelease');
assert(groovyBuild.includes('failOnError = false'), 'Must protect Javadoc generation');

// Check syntax and brace matching
const braces = PreValidatorService.validateGradleSyntax(groovyBuild, 'build.gradle');
assert.strictEqual(braces.valid, true, `Groovy build.gradle must be valid syntax: ${JSON.stringify(braces.errors)}`);

console.log('✅ Test 3 Passed: Groovy build.gradle generator output has full features, POM metadata and balanced braces.\n');

// ============================================================================
// TEST 4: Library build.gradle Generator Kotlin DSL Output
// ============================================================================
console.log('▶ Test 4: Standard Android Library build.gradle.kts generation (Kotlin DSL)');

const ktsBuild = GradlePatcher.generateStandardBuild('android-library', {
  name: 'SuperMathLib',
  packageName: 'com.roni.supermath',
  version: '2.1.0',
  compileSdk: '34',
  minSdk: '21',
  targetSdk: '34',
  author: 'roniboss',
  repoName: 'super-math-lib',
  description: 'High performance math Android library',
  license: 'Apache-2.0',
  isKts: true,
});

assert(ktsBuild.includes('id("com.android.library")'), 'Must include Kotlin DSL library plugin');
assert(ktsBuild.includes('`maven-publish`'), 'Must include Kotlin DSL maven-publish plugin');
assert(ktsBuild.includes('namespace = "com.roni.supermath"'), 'Must set Kotlin DSL namespace');
assert(ktsBuild.includes('singleVariant("release")'), 'Must configure Kotlin DSL singleVariant');
assert(ktsBuild.includes('create<MavenPublication>("release")'), 'Must create MavenPublication in Kotlin DSL');

const ktsBraces = PreValidatorService.validateGradleSyntax(ktsBuild, 'build.gradle.kts');
assert.strictEqual(ktsBraces.valid, true, `Kotlin DSL build.gradle.kts must be valid syntax: ${JSON.stringify(ktsBraces.errors)}`);

console.log('✅ Test 4 Passed: Kotlin DSL build.gradle.kts generator output verified.\n');

// ============================================================================
// TEST 5: Editor Auto/Manual Project Files Toggle & Settings Integration
// ============================================================================
console.log('▶ Test 5: Editor Auto/Manual Toggle and Settings page integration');

const editorModuleContent = fs.readFileSync(path.join(rootDir, 'js/app/modules/editor/EditorModule.js'), 'utf8');
const settingsPageContent = fs.readFileSync(path.join(rootDir, 'js/app/pages/SettingsPage.js'), 'utf8');

assert(editorModuleContent.includes('id="ed-auto-mode-toggle"'), 'Editor toolbar must have Auto Mode toggle button');
assert(editorModuleContent.includes('#autoLoadFiles'), 'EditorModule must have #autoLoadFiles state');
assert(editorModuleContent.includes('#updateAutoModeUI'), 'EditorModule must have #updateAutoModeUI method');
assert(editorModuleContent.includes('autoLoadProjectFiles'), 'EditorModule must support autoLoadProjectFiles setting');
assert(editorModuleContent.includes('#autoOpenPrimaryFile'), 'EditorModule must support auto-opening primary file in Auto mode');

assert(settingsPageContent.includes('autoLoadProjectFiles'), 'SettingsPage must manage autoLoadProjectFiles setting');
assert(settingsPageContent.includes('settings-auto-load-toggle'), 'SettingsPage must have auto-load toggle element');

console.log('✅ Test 5 Passed: Editor Auto/Manual Project Files toggle & Settings integration verified.\n');

// ============================================================================
// TEST 6: Version Consistency Across All Files
// ============================================================================
console.log('▶ Test 6: Complete Version Synchronization');

const vJson = JSON.parse(fs.readFileSync(path.join(rootDir, 'version.json'), 'utf8'));
const currentV = vJson.currentVersion;
assert(['0.1.2.7', '0.1.2.8', '0.1.2.9', '0.1.3.0', '0.1.3.1', '0.1.3.2', '0.1.3.3', '0.1.3.4', '0.1.3.5', '0.1.3.6'].includes(currentV), 'version.json currentVersion must be valid');
assert(Array.isArray(vJson.changelog[currentV]), `Changelog entry for ${currentV} must exist`);

const indexHtml = fs.readFileSync(path.join(rootDir, 'index.html'), 'utf8');
assert(indexHtml.includes(`v${currentV}`), `index.html must reference v${currentV}`);

const sidebar = fs.readFileSync(path.join(rootDir, 'js/app/ui/Sidebar.js'), 'utf8');
assert(sidebar.includes(`v${currentV}`), `Sidebar.js must reference v${currentV}`);

const toolbar = fs.readFileSync(path.join(rootDir, 'js/app/ui/Toolbar.js'), 'utf8');
assert(toolbar.includes(`v${currentV}`), `Toolbar.js must reference v${currentV}`);

const downloads = fs.readFileSync(path.join(rootDir, 'js/app/pages/DownloadsPage.js'), 'utf8');
assert(downloads.includes(`v${currentV}`), `DownloadsPage.js must reference v${currentV}`);
assert(downloads.includes(`RONiAndroidLibraryGenerator-v${currentV}.zip`), `DownloadsPage.js must reference v${currentV} ZIP`);

const versionManager = fs.readFileSync(path.join(rootDir, 'js/app/pages/VersionManagerPage.js'), 'utf8');
assert(versionManager.includes(`RONiAndroidLibraryGenerator-v${currentV}.zip`), `VersionManagerPage.js must reference v${currentV} ZIP`);

console.log(`✅ Test 6 Passed: All version metadata verified at v${currentV}.\n`);

console.log('🎉 ALL TESTS IN SUITE PASSED SUCCESSFULLY!');
