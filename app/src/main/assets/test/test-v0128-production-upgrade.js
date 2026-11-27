/**
 * test-v0128-production-upgrade.js
 * Comprehensive automated verification for RONi Android Library Generator v0.1.2.8
 */
import assert from 'assert';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

console.log('🧪 Starting RONi Android Library Generator v0.1.2.8 Production Upgrade Test Suite...\n');

// ── Test 1: Version Consistency & Release Changelog ──
console.log('▶ Test 1: Version Consistency & Changelog Verification');
const vJson = JSON.parse(fs.readFileSync(path.join(rootDir, 'version.json'), 'utf8'));
const currentVer = vJson.currentVersion;
assert(['0.1.2.8', '0.1.2.9', '0.1.3.0', '0.1.3.1', '0.1.3.2', '0.1.3.3', '0.1.3.4', '0.1.3.5', '0.1.3.6'].includes(currentVer), 'currentVersion must be valid release');
assert(Array.isArray(vJson.changelog[currentVer]), `changelog array for ${currentVer} must exist`);
assert(vJson.changelog[currentVer].length >= 4, `changelog must contain detailed release notes`);

const indexHtml = fs.readFileSync(path.join(rootDir, 'index.html'), 'utf8');
assert(indexHtml.includes(`v${currentVer}`), `index.html must reference v${currentVer}`);

const sidebar = fs.readFileSync(path.join(rootDir, 'js/app/ui/Sidebar.js'), 'utf8');
assert(sidebar.includes(`v${currentVer}`), `Sidebar.js must reference v${currentVer}`);

const toolbar = fs.readFileSync(path.join(rootDir, 'js/app/ui/Toolbar.js'), 'utf8');
assert(toolbar.includes(`v${currentVer}`), `Toolbar.js must reference v${currentVer}`);

const downloads = fs.readFileSync(path.join(rootDir, 'js/app/pages/DownloadsPage.js'), 'utf8');
assert(downloads.includes(`v${currentVer}`), `DownloadsPage.js must reference v${currentVer}`);
assert(downloads.includes(`RONiAndroidLibraryGenerator-v${currentVer}.zip`), `DownloadsPage.js must link to release ZIP`);

const versionManager = fs.readFileSync(path.join(rootDir, 'js/app/pages/VersionManagerPage.js'), 'utf8');
assert(versionManager.includes(`RONiAndroidLibraryGenerator-v${currentVer}.zip`), `VersionManagerPage.js must link to release ZIP`);
console.log('✅ Test 1 Passed: Version metadata and release references 100% verified.\n');

// ── Test 2: Language Switcher & Bengali I18n Integration ──
console.log('▶ Test 2: Language Switcher UI & Bengali I18n Coverage');
assert(toolbar.includes('btn-lang-toggle'), 'Toolbar must have language toggle button #btn-lang-toggle');
assert(toolbar.includes('toolbar-lang-indicator'), 'Toolbar must render dynamic lang indicator tag');
assert(toolbar.includes('i18n.setLanguage'), 'Toolbar must wire language switching via i18n.setLanguage');

const i18nJs = fs.readFileSync(path.join(rootDir, 'js/app/core/I18n.js'), 'utf8');
assert(i18nJs.includes("Toggle Language (English / বাংলা)"), 'I18n must include language toggle string');
assert(i18nJs.includes("'ড্যাশবোর্ড'"), 'I18n must translate Dashboard to Bengali');
assert(i18nJs.includes("'এক্সপ্লোরার'"), 'I18n must translate Explorer to Bengali');
assert(i18nJs.includes("'জেনারেটর'"), 'I18n must translate Generator to Bengali');
console.log('✅ Test 2 Passed: Language Switcher & Bengali translations verified.\n');

// ── Test 3: AST Gradle Helper Brace Balance & Safety ──
console.log('▶ Test 3: AST Gradle Helper Brace Balance & Safety Guard');
const { GradleAstHelper } = await import('../js/app/modules/generator/GradleAstHelper.js');
const sampleBuildGradle = `
plugins {
    id 'com.android.library'
    id 'maven-publish'
}

android {
    namespace 'com.example.lib'
    compileSdk 34

    defaultConfig {
        minSdk 21
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    publishing {
        singleVariant('release') {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
}
`;

const analysis = GradleAstHelper.validateBraces(sampleBuildGradle);
assert.strictEqual(analysis.balanced, true, 'Sample build.gradle must be balanced');
assert.strictEqual(analysis.openCount, analysis.closeCount, 'Open and close braces count must match');

const depsClosure = GradleAstHelper.findBlock(sampleBuildGradle, 'dependencies');
assert(depsClosure !== null, 'dependencies closure must be located');
assert(depsClosure.bodyText.includes('androidx.core:core-ktx'), 'bodyText must have existing dependencies');

const result = GradleAstHelper.insertOrUpdateDependency(sampleBuildGradle, 'com.google.code.gson:gson:2.10.1', 'implementation');
const patched = result.content;
const patchedAnalysis = GradleAstHelper.validateBraces(patched);
assert.strictEqual(patchedAnalysis.balanced, true, 'Patched build.gradle must maintain perfect brace balance');
assert(patched.includes('gson:2.10.1'), 'Patched build.gradle must contain added dependency');
console.log('✅ Test 3 Passed: AST Gradle Helper verified with zero brace corruption.\n');

// ── Test 4: ModalService Backward Compatibility Contracts ──
console.log('▶ Test 4: ModalService Compatibility Contracts');
const modalJs = fs.readFileSync(path.join(rootDir, 'js/app/ui/ModalService.js'), 'utf8');
assert(modalJs.includes('show('), 'ModalService must expose .show(opts)');
assert(modalJs.includes('close('), 'ModalService must expose .close()');
assert(modalJs.includes('confirm('), 'ModalService must expose .confirm()');
assert(modalJs.includes('alert('), 'ModalService must expose .alert()');
assert(modalJs.includes('prompt('), 'ModalService must expose .prompt()');
console.log('✅ Test 4 Passed: ModalService contracts verified.\n');

console.log('🎉 ALL v0.1.2.8 PRODUCTION UPGRADE TESTS PASSED 100%!');
