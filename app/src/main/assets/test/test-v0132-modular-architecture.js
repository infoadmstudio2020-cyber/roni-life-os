/**
 * Automated Verification Suite for RONi Android Library Generator v0.1.3.2
 * Tests modular architecture, file ownership, zero-dependency package, offline assets, and regressions.
 */
import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

console.log('\n======================================================');
console.log(' RONi Android Library Generator v0.1.3.2 Architecture Verification ');
console.log('======================================================\n');

let passed = 0;
let total = 0;

function test(name, fn) {
  total++;
  try {
    fn();
    console.log(`  ✅ PASS: ${name}`);
    passed++;
  } catch (err) {
    console.error(`  ❌ FAIL: ${name}`);
    console.error(`     Error: ${err.message}`);
    process.exitCode = 1;
  }
}

async function runAsyncTest(name, fn) {
  total++;
  try {
    await fn();
    console.log(`  ✅ PASS: ${name}`);
    passed++;
  } catch (err) {
    console.error(`  ❌ FAIL: ${name}`);
    console.error(`     Error: ${err.message}`);
    process.exitCode = 1;
  }
}

// 1. Version 0.1.3.2 Metadata
test('1.1 version.json has currentVersion 0.1.3.x and release notes', () => {
  const vData = JSON.parse(fs.readFileSync(path.join(rootDir, 'version.json'), 'utf8'));
  assert(['0.1.3.2', '0.1.3.3', '0.1.3.4', '0.1.3.5', '0.1.3.6'].includes(vData.currentVersion), 'currentVersion must be valid');
  assert(Array.isArray(vData.changelog[vData.currentVersion]), 'Changelog must contain release notes');
  assert(vData.changelog[vData.currentVersion].length >= 3, 'Must have comprehensive release notes');
});

test('1.2 index.html title, meta, and favicon match current version', () => {
  const indexHtml = fs.readFileSync(path.join(rootDir, 'index.html'), 'utf8');
  assert(indexHtml.includes('RONi Android LibGen v0.1.3.'), 'index.html title must match version');
  assert(indexHtml.includes('RONi Android Library Project Generator & JitPack Manager (v0.1.3.'), 'index.html meta description must match version');
  assert(indexHtml.includes('assets/favicon.png'), 'index.html must reference assets/favicon.png');
});

test('1.3 UI badges and download links reference current version', () => {
  const sidebarJs = fs.readFileSync(path.join(rootDir, 'js/app/ui/Sidebar.js'), 'utf8');
  assert(sidebarJs.includes('v0.1.3.'), 'Sidebar must display version badge');

  const toolbarJs = fs.readFileSync(path.join(rootDir, 'js/app/ui/Toolbar.js'), 'utf8');
  assert(toolbarJs.includes('v0.1.3.'), 'Toolbar must display version badge');

  const downloadsJs = fs.readFileSync(path.join(rootDir, 'js/app/pages/DownloadsPage.js'), 'utf8');
  assert(downloadsJs.includes('v0.1.3.'), 'DownloadsPage must display version');
  assert(downloadsJs.includes('RONiAndroidLibraryGenerator-v0.1.3.'), 'DownloadsPage must link to package');

  const versionMgrJs = fs.readFileSync(path.join(rootDir, 'js/app/pages/VersionManagerPage.js'), 'utf8');
  assert(versionMgrJs.includes('RONiAndroidLibraryGenerator-v0.1.3.'), 'VersionManagerPage must link to package');
});

// 2. Modular Architecture Directory Structure
test('2.1 Required architectural directories exist with clear ownership', () => {
  const requiredDirs = [
    'assets',
    'css',
    'js',
    'js/app/core',
    'js/app/ui',
    'js/app/pages',
    'js/app/modules/storage',
    'js/app/modules/generator',
    'js/app/modules/analyzer',
    'js/app/modules/github',
    'js/app/modules/zip',
    'js/app/modules/conflict',
    'js/app/modules/environment',
    'js/app/modules/editor',
    'js/app/modules/explorer',
    'js/app/modules/build',
    'js/app/modules/version',
    'test',
    'tests',
    'docs'
  ];

  for (const d of requiredDirs) {
    assert(fs.existsSync(path.join(rootDir, d)), `Directory ${d} must exist`);
  }
});

// 3. Elimination of Dead Code & Orphaned React Scaffold
test('3.1 No orphaned src/ directory or React boilerplate in project distribution', () => {
  assert(!fs.existsSync(path.join(rootDir, 'src')), 'src/ directory must be completely removed');
});

test('3.2 package.json is lightweight with zero unused React dependencies', () => {
  const pkg = JSON.parse(fs.readFileSync(path.join(rootDir, 'package.json'), 'utf8'));
  assert(['0.1.3.2', '0.1.3.3', '0.1.3.4', '0.1.3.5', '0.1.3.6'].includes(pkg.version));
  assert(!pkg.dependencies || Object.keys(pkg.dependencies).length === 0, 'Must have zero runtime npm dependencies');
  assert(!pkg.dependencies?.react, 'No React in runtime dependencies');
  assert(!pkg.dependencies?.['@radix-ui/react-dialog'], 'No Radix in runtime dependencies');
});

// 4. Asset Completeness
test('4.1 Offline assets directory contains branding and icon resources', () => {
  assert(fs.existsSync(path.join(rootDir, 'assets/favicon.png')), 'assets/favicon.png must exist');
  assert(fs.existsSync(path.join(rootDir, 'assets/images/favicon.ico')), 'assets/images/favicon.ico must exist');
  assert(fs.existsSync(path.join(rootDir, 'assets/images/logo/logo-dark.svg')), 'assets/images/logo/logo-dark.svg must exist');
});

// 5. Offline JSZip and ES6 Module Readiness
test('5.1 Offline JSZip vendor file is bundled', () => {
  const jszipPath = path.join(rootDir, 'js/vendor/jszip.min.js');
  assert(fs.existsSync(jszipPath), 'Local JSZip vendor file must exist');
  const stat = fs.statSync(jszipPath);
  assert(stat.size > 90000, 'JSZip file size should be valid minified bundle (>90KB)');
});

// 6. AI Spec Generator & Schema compliance
await runAsyncTest('6.1 AIProjectSpecGenerator exports specifications', async () => {
  const { AIProjectSpecGenerator } = await import('../js/app/modules/generator/AIProjectSpecGenerator.js');
  const spec = AIProjectSpecGenerator.generateSpec('android-java-library', {
    name: 'ArchVerificationLib',
    package: 'com.test.arch',
    template: 'android-java-library'
  });
  assert(spec.generator.includes('RONi Android Library Generator v0.1.3.'), 'Spec generator must match version');

  const schema = AIProjectSpecGenerator.generateRequiredInformationSchema('android-java-library');
  assert(schema.generator.includes('RONi Android Library Generator v0.1.3.'), 'Schema generator must match version');
});

console.log(`\n======================================================`);
console.log(` Result: ${passed}/${total} Tests Passed`);
console.log(`======================================================\n`);
