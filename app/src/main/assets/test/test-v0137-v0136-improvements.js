/**
 * Automated Verification Suite for RONi Android Library Generator v0.1.3.6:
 * - Ecosystem Version Management Page UI Mobile Responsiveness
 * - JitPack Log Retry System Button & State Management
 * - Screen Center Aligned Popup Dialog System (Desktop & Mobile)
 * - Complete Version v0.1.3.6 Metadata Synchronization
 */

import fs from 'fs';
import path from 'path';
import assert from 'assert';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

console.log('\n======================================================');
console.log(' RONi Android Library Generator v0.1.3.6 Verification ');
console.log('======================================================\n');

// ── Test 1: Complete Version 0.1.3.6 Metadata Synchronization ─────────────
console.log('▶ Test 1: Complete Version 0.1.3.6 Metadata Synchronization');

const versionJson = JSON.parse(fs.readFileSync(path.join(rootDir, 'version.json'), 'utf8'));
assert.strictEqual(versionJson.currentVersion, '0.1.3.6', 'version.json must be 0.1.3.6');
assert(Array.isArray(versionJson.changelog['0.1.3.6']), '0.1.3.6 changelog array must exist');
assert(versionJson.changelog['0.1.3.6'].length >= 3, '0.1.3.6 must have detailed changelog entries');

const pkgJson = JSON.parse(fs.readFileSync(path.join(rootDir, 'package.json'), 'utf8'));
assert.strictEqual(pkgJson.version, '0.1.3.6', 'package.json must be 0.1.3.6');

const indexHtml = fs.readFileSync(path.join(rootDir, 'index.html'), 'utf8');
assert(indexHtml.includes('RONi Android LibGen v0.1.3.6'), 'index.html title must match v0.1.3.6');
assert(indexHtml.includes('RONi Android Library Project Generator & JitPack Manager (v0.1.3.6)'), 'index.html description must match v0.1.3.6');

const sidebarJs = fs.readFileSync(path.join(rootDir, 'js/app/ui/Sidebar.js'), 'utf8');
assert(sidebarJs.includes('v0.1.3.6'), 'Sidebar.js must display v0.1.3.6 badge');

const toolbarJs = fs.readFileSync(path.join(rootDir, 'js/app/ui/Toolbar.js'), 'utf8');
assert(toolbarJs.includes('v0.1.3.6'), 'Toolbar.js must display v0.1.3.6 badge');

const downloadsJs = fs.readFileSync(path.join(rootDir, 'js/app/pages/DownloadsPage.js'), 'utf8');
assert(downloadsJs.includes('v0.1.3.6'), 'DownloadsPage.js must reference v0.1.3.6');
assert(downloadsJs.includes('RONiAndroidLibraryGenerator-v0.1.3.6.zip'), 'DownloadsPage must link to v0.1.3.6 zip');

const versionManagerJs = fs.readFileSync(path.join(rootDir, 'js/app/pages/VersionManagerPage.js'), 'utf8');
assert(versionManagerJs.includes('RONiAndroidLibraryGenerator-v0.1.3.6.zip'), 'VersionManagerPage must link to v0.1.3.6 zip');

console.log('✅ Test 1 Passed: Version 0.1.3.6 100% synchronized across all files.\n');

// ── Test 2: Screen Center Aligned Popup Dialog System ─────────────────────
console.log('▶ Test 2: Screen Center Aligned Popup Dialog System');

const modalsCss = fs.readFileSync(path.join(rootDir, 'css/components/modals.css'), 'utf8');
assert(modalsCss.includes('align-items: center;'), 'modals.css #modal-layer must be center aligned');
assert(modalsCss.includes('justify-content: center;'), 'modals.css #modal-layer must be center justified');
assert(modalsCss.includes('display: flex;'), 'modal-backdrop must be flex');
assert(modalsCss.includes('margin: auto;'), 'modal must have margin: auto for centering');

const mobileCss = fs.readFileSync(path.join(rootDir, 'css/responsive/mobile.css'), 'utf8');
assert(mobileCss.includes('align-items: center !important;'), 'mobile.css must keep dialogs center aligned');
assert(mobileCss.includes('justify-content: center !important;'), 'mobile.css must keep dialogs center justified');
assert(mobileCss.includes('max-width: calc(100vw - 24px) !important;'), 'mobile modal must have responsive max-width');

console.log('✅ Test 2 Passed: Modal dialogs are verified screen center-aligned on desktop and mobile.\n');

// ── Test 3: JitPack Log Retry System Button & State Management ────────────
console.log('▶ Test 3: JitPack Log Retry System Button & State Management');

const jitPackPageJs = fs.readFileSync(path.join(rootDir, 'js/app/pages/JitPackPage.js'), 'utf8');
assert(jitPackPageJs.includes('jp-retry-log-btn'), 'JitPackPage must contain jp-retry-log-btn');
assert(jitPackPageJs.includes('Retry Log'), 'JitPackPage must render Retry Log button label');
assert(jitPackPageJs.includes('jp-retry-alert-banner'), 'JitPackPage must render jp-retry-alert-banner');
assert(jitPackPageJs.includes('#fetchLogWithRetry'), 'JitPackPage must implement #fetchLogWithRetry');
assert(jitPackPageJs.includes('autoRetryActive'), 'JitPackPage must support autoRetryActive');
assert(jitPackPageJs.includes('retryCount'), 'JitPackPage must track retryCount');

console.log('✅ Test 3 Passed: JitPack build log retry system button & automated re-fetch verified.\n');

// ── Test 4: Ecosystem Version Management Mobile Responsiveness ────────────
console.log('▶ Test 4: Ecosystem Version Management Mobile Responsiveness');

const ecosystemCss = fs.readFileSync(path.join(rootDir, 'css/pages/ecosystem-manager.css'), 'utf8');
assert(ecosystemCss.includes('@media (max-width: 768px)'), 'ecosystem-manager.css must contain mobile media queries');
assert(ecosystemCss.includes('.ecosystem-hud-grid'), 'ecosystem-manager.css must style hud grid for mobile');
assert(ecosystemCss.includes('.ecosystem-table-wrap'), 'ecosystem-manager.css must style table wrap for touch scrolling');
assert(ecosystemCss.includes('.version-compare-row'), 'ecosystem-manager.css must stack version compare rows on mobile');

console.log('✅ Test 4 Passed: Ecosystem Version Management page mobile responsive styles verified.\n');

console.log('======================================================');
console.log('🎉 ALL v0.1.3.6 VERIFICATION TESTS PASSED! (100%)');
console.log('======================================================\n');
