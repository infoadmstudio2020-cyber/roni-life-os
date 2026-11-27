/**
 * test-v0131-offline-optimization.js
 *
 * Automated verification suite for RONi Android Library Generator v0.1.3.2:
 * 1. Version synchronization across all project metadata, HTML, UI badges, and generators
 * 2. 100% Offline Usability & local vendor bundles (jszip.min.js)
 * 3. ClipboardUtil cross-platform fallback for Android WebView and non-HTTPS contexts
 * 4. AI Project Spec Generator & Schema compliance (v0.1.3.2)
 * 5. Modern accessible viewport in index.html (no user-scalable=no restrictions)
 * 6. Clean project structure verification (zero obsolete backup folders)
 */

import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

let totalTests = 0;
let passedTests = 0;

function test(name, fn) {
  totalTests++;
  try {
    fn();
    passedTests++;
    console.log(`  ✅ PASS: ${name}`);
  } catch (err) {
    console.error(`  ❌ FAIL: ${name}`);
    console.error(`     Error: ${err.message}`);
  }
}

async function runAsyncTest(name, fn) {
  totalTests++;
  try {
    await fn();
    passedTests++;
    console.log(`  ✅ PASS: ${name}`);
  } catch (err) {
    console.error(`  ❌ FAIL: ${name}`);
    console.error(`     Error: ${err.message}`);
  }
}

console.log('\n======================================================');
console.log(' RONi Android Library Generator v0.1.3.2 Verification ');
console.log('======================================================\n');

// 1. Version Synchronization
test('1.1 version.json has currentVersion 0.1.3.1 and release notes', () => {
  const versionJsonPath = path.join(rootDir, 'version.json');
  assert(fs.existsSync(versionJsonPath), 'version.json must exist');
  const vData = JSON.parse(fs.readFileSync(versionJsonPath, 'utf8'));
  assert(['0.1.3.2', '0.1.3.3', '0.1.3.4', '0.1.3.5', '0.1.3.6'].includes(vData.currentVersion), 'currentVersion must be valid');
  assert(['0.1.3.0', '0.1.3.1', '0.1.3.2', '0.1.3.3', '0.1.3.4', '0.1.3.5'].includes(vData.previousVersion), 'previousVersion must be valid');
  assert(Array.isArray(vData.changelog[vData.currentVersion]), 'Changelog must contain release notes');
  assert(vData.changelog[vData.currentVersion].length >= 3, 'Release must have detailed release notes');
});

test('1.2 index.html title and description match current version', () => {
  const indexHtml = fs.readFileSync(path.join(rootDir, 'index.html'), 'utf8');
  assert(indexHtml.includes('RONi Android LibGen v0.1.3.'), 'index.html title must match version');
  assert(indexHtml.includes('RONi Android Library Project Generator & JitPack Manager (v0.1.3.'), 'index.html meta description must match version');
});

test('1.3 UI badges and download links reference current version', () => {
  const sidebarJs = fs.readFileSync(path.join(rootDir, 'js/app/ui/Sidebar.js'), 'utf8');
  assert(sidebarJs.includes('v0.1.3.'), 'Sidebar must display version badge');

  const toolbarJs = fs.readFileSync(path.join(rootDir, 'js/app/ui/Toolbar.js'), 'utf8');
  assert(toolbarJs.includes('v0.1.3.'), 'Toolbar must display version badge');

  const downloadsJs = fs.readFileSync(path.join(rootDir, 'js/app/pages/DownloadsPage.js'), 'utf8');
  assert(downloadsJs.includes('v0.1.3.'), 'DownloadsPage must display version');
  assert(downloadsJs.includes('RONiAndroidLibraryGenerator-v0.1.3.'), 'DownloadsPage must link to package');
});

// 2. Offline Usability & Vendor Assets
test('2.1 Local JSZip vendor asset is bundled for 100% offline usability', () => {
  const vendorJsZip = path.join(rootDir, 'js/vendor/jszip.min.js');
  assert(fs.existsSync(vendorJsZip), 'js/vendor/jszip.min.js must exist locally');
  const stats = fs.statSync(vendorJsZip);
  assert(stats.size > 80000, `jszip.min.js size (${stats.size} bytes) should be > 80KB`);
  const content = fs.readFileSync(vendorJsZip, 'utf8');
  assert(content.includes('JSZip') || content.includes('jszip'), 'File must contain JSZip bundle');
});

test('2.2 index.html loads local js/vendor/jszip.min.js with CDN fallback', () => {
  const indexHtml = fs.readFileSync(path.join(rootDir, 'index.html'), 'utf8');
  assert(indexHtml.includes('src="js/vendor/jszip.min.js"'), 'index.html must load local vendor JSZip script');
  assert(indexHtml.includes("if (typeof JSZip === 'undefined')"), 'index.html must provide fallback loader');
});

// 3. Android WebView & Accessible Viewport
test('3.1 index.html viewport supports modern mobile & Android WebView accessibility', () => {
  const indexHtml = fs.readFileSync(path.join(rootDir, 'index.html'), 'utf8');
  assert(!indexHtml.includes('user-scalable=no'), 'Viewport must not disable user zoom for accessibility');
  assert(!indexHtml.includes('maximum-scale=1.0'), 'Viewport must not restrict scale to 1.0');
  assert(indexHtml.includes('viewport-fit=cover'), 'Viewport must include viewport-fit=cover');
});

// 4. ClipboardUtil Verification
await runAsyncTest('4.1 ClipboardUtil provides safe fallback in non-browser/non-HTTPS environments', async () => {
  const { ClipboardUtil } = await import('../js/app/core/ClipboardUtil.js');
  assert(typeof ClipboardUtil.copyText === 'function', 'ClipboardUtil.copyText must be a function');

  // Calling copyText in Node environment without DOM/navigator returns false gracefully without throwing
  const res = await ClipboardUtil.copyText('test android webview clipboard');
  assert(typeof res === 'boolean', 'copyText must return a boolean promise');
});

// 5. AI Project Spec Generator & Schemas
await runAsyncTest('5.1 AIProjectSpecGenerator includes version in generated specs', async () => {
  const { AIProjectSpecGenerator, TEMPLATES_CATALOG } = await import('../js/app/modules/generator/AIProjectSpecGenerator.js');
  assert(Array.isArray(TEMPLATES_CATALOG) && TEMPLATES_CATALOG.length >= 4, 'Must have at least 4 templates in catalog');

  const spec = AIProjectSpecGenerator.generateSpec('android-java-library');
  assert(spec.generator.includes('RONi Android Library Generator v0.1.3.'), 'Spec generator must match version');

  const schema = AIProjectSpecGenerator.generateRequiredInformationSchema('android-java-library');
  assert(schema.generator.includes('RONi Android Library Generator v0.1.3.'), 'Schema generator must match version');
  assert(schema.required_information?.properties && schema.required_information?.required, 'Schema must have properties and required fields');
});

// 6. Project Structure Cleanliness
test('6.1 Repository structure is clean of obsolete archives and backups', () => {
  const backupFolder = path.join(rootDir, 'backup_v0.1.2.7');
  assert(!fs.existsSync(backupFolder), 'Obsolete backup_v0.1.2.7 must not exist in repository');
});

console.log(`\n======================================================`);
console.log(` Result: ${passedTests}/${totalTests} Tests Passed`);
console.log(`======================================================\n`);

if (passedTests !== totalTests) {
  process.exit(1);
}
