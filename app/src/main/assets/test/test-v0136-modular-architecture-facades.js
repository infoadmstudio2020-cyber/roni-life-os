/**
 * Test Suite: Architecture Modernization & Modular Facades
 * Validates ES Module barrel index exports, layer boundaries, and zero circular dependencies.
 */

import { strict as assert } from 'assert';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

console.log('\n======================================================');
console.log(' RONi Android Library Generator Architecture Facades ');
console.log('======================================================\n');

let passedTests = 0;
let totalTests = 0;

function test(name, fn) {
  totalTests++;
  try {
    fn();
    console.log(`  ✅ PASS: ${name}`);
    passedTests++;
  } catch (err) {
    console.error(`  ❌ FAIL: ${name}`);
    console.error(`     Error: ${err.message}`);
  }
}

// 1. Core Layer Facade & Utilities
test('1.1 Core Layer index.js and DomUtil.js exports', async () => {
  const coreIndexPath = path.join(rootDir, 'js/app/core/index.js');
  const domUtilPath = path.join(rootDir, 'js/app/core/DomUtil.js');
  assert(fs.existsSync(coreIndexPath), 'Core index.js must exist');
  assert(fs.existsSync(domUtilPath), 'DomUtil.js must exist');

  const { escapeHtml, stripHtml } = await import('../js/app/core/DomUtil.js');
  assert.equal(escapeHtml('<script>alert("xss")</script>'), '&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;');
  assert.equal(stripHtml('<p>Hello <b>World</b></p>'), 'Hello World');

  const core = await import('../js/app/core/index.js');
  assert(core.eventBus !== undefined, 'eventBus must be exported from core index');
  assert(core.Router !== undefined, 'Router must be exported from core index');
  assert(core.i18n !== undefined, 'i18n must be exported from core index');
  assert(core.WebViewBridge !== undefined, 'WebViewBridge must be exported from core index');
  assert(core.escapeHtml !== undefined, 'escapeHtml must be exported from core index');
});

// 2. UI Layer Facade
test('2.1 UI Layer index.js exports layout shells and services', async () => {
  const uiIndexPath = path.join(rootDir, 'js/app/ui/index.js');
  assert(fs.existsSync(uiIndexPath), 'UI index.js must exist');

  const ui = await import('../js/app/ui/index.js');
  assert(ui.Sidebar !== undefined, 'Sidebar must be exported');
  assert(ui.Toolbar !== undefined, 'Toolbar must be exported');
  assert(ui.StatusBar !== undefined, 'StatusBar must be exported');
  assert(ui.BottomNav !== undefined, 'BottomNav must be exported');
  assert(ui.ModalService !== undefined, 'ModalService must be exported');
  assert(ui.ToastService !== undefined, 'ToastService must be exported');
});

// 3. Storage & Domain Module Facades
test('3.1 Storage, Zip, and Generator Module facades', async () => {
  const storage = await import('../js/app/modules/storage/index.js');
  assert(storage.StorageService !== undefined, 'StorageService must be exported');
  assert(storage.storage !== undefined, 'storage instance must be exported');
  assert(storage.projectService !== undefined, 'projectService must be exported');

  const zip = await import('../js/app/modules/zip/index.js');
  assert(zip.ZipService !== undefined, 'ZipService must be exported');
  assert(zip.zipService !== undefined, 'zipService instance must be exported');

  const generator = await import('../js/app/modules/generator/index.js');
  assert(generator.GeneratorModule !== undefined, 'GeneratorModule must be exported');
  assert(generator.JitPackAnalyzerEngine !== undefined, 'JitPackAnalyzerEngine must be exported');
  assert(generator.AIProjectSpecGenerator !== undefined, 'AIProjectSpecGenerator must be exported');
});

// 4. Ecosystem, Analyzer, Environment, Conflict, and GitHub Facades
test('4.1 Advanced Domain Module facades export their respective tools', async () => {
  const ecosystem = await import('../js/app/modules/ecosystem/index.js');
  assert(ecosystem.EcosystemScanner !== undefined, 'EcosystemScanner must be exported');
  assert(ecosystem.VersionComparator !== undefined, 'VersionComparator must be exported');

  const analyzer = await import('../js/app/modules/analyzer/index.js');
  assert(analyzer.ProjectAnalyzer !== undefined, 'ProjectAnalyzer must be exported');
  assert(analyzer.SourceDependencyAnalyzer !== undefined, 'SourceDependencyAnalyzer must be exported');

  const environment = await import('../js/app/modules/environment/index.js');
  assert(environment.EnvironmentManager !== undefined, 'EnvironmentManager must be exported');

  const conflict = await import('../js/app/modules/conflict/index.js');
  assert(conflict.D8PackagingGuard !== undefined, 'D8PackagingGuard must be exported');
  assert(conflict.BinaryClassInspector !== undefined, 'BinaryClassInspector must be exported');

  const github = await import('../js/app/modules/github/index.js');
  assert(github.GitHubApi !== undefined, 'GitHubApi must be exported');
  assert(github.GitHubPublisher !== undefined, 'GitHubPublisher must be exported');
});

// 5. Unified Root Modules Facade
test('5.1 Root Modules Facade (js/app/modules/index.js)', async () => {
  const rootModules = await import('../js/app/modules/index.js');
  assert(rootModules.storage !== undefined, 'storage namespace must exist');
  assert(rootModules.generator !== undefined, 'generator namespace must exist');
  assert(rootModules.ecosystem !== undefined, 'ecosystem namespace must exist');
  assert(rootModules.conflict !== undefined, 'conflict namespace must exist');
  assert(rootModules.analyzer !== undefined, 'analyzer namespace must exist');
});

// 6. Zero Circular Dependencies Verification
test('6.1 Zero circular dependencies across entire codebase', () => {
  function getFiles(dir) {
    let results = [];
    const list = fs.readdirSync(dir);
    for (const file of list) {
      const full = path.join(dir, file);
      const stat = fs.statSync(full);
      if (stat.isDirectory()) {
        results = results.concat(getFiles(full));
      } else if (file.endsWith('.js')) {
        results.push(full);
      }
    }
    return results;
  }

  const jsFiles = getFiles(path.join(rootDir, 'js'));
  const graph = {};

  for (const file of jsFiles) {
    const content = fs.readFileSync(file, 'utf8');
    const imports = [];
    const regex = /(?:import|export)\s+(?:.*?from\s+)?["\x27]([^"\x27]+)["\x27]/g;
    let match;
    while ((match = regex.exec(content)) !== null) {
      const importPath = match[1];
      if (importPath.startsWith('.')) {
        const resolved = path.resolve(path.dirname(file), importPath);
        imports.push(resolved);
        assert(fs.existsSync(resolved) || fs.existsSync(resolved + '.js'), `Broken import found in ${file}: ${importPath}`);
      }
    }
    graph[file] = imports;
  }

  function findCycles(node, visited = new Set(), stack = []) {
    visited.add(node);
    stack.push(node);
    for (const neighbor of graph[node] || []) {
      if (!visited.has(neighbor)) {
        const cycle = findCycles(neighbor, visited, stack);
        if (cycle) return cycle;
      } else if (stack.includes(neighbor)) {
        return [...stack.slice(stack.indexOf(neighbor)), neighbor];
      }
    }
    stack.pop();
    return null;
  }

  for (const file of jsFiles) {
    const cycle = findCycles(file);
    assert.equal(cycle, null, `Circular dependency detected: ${cycle ? cycle.map(f => path.basename(f)).join(' -> ') : ''}`);
  }
});

// 7. Pages Layer Unified Registry
test('7.1 Pages Layer index.js exports all SPA page classes', async () => {
  const pages = await import('../js/app/pages/index.js');
  assert(pages.DashboardPage !== undefined, 'DashboardPage must be exported');
  assert(pages.GeneratorPage !== undefined, 'GeneratorPage must be exported');
  assert(pages.EditorPage !== undefined, 'EditorPage must be exported');
  assert(pages.GradlePage !== undefined, 'GradlePage must be exported');
  assert(pages.DownloadsPage !== undefined, 'DownloadsPage must be exported');
  assert(pages.VersionManagerPage !== undefined, 'VersionManagerPage must be exported');
});

console.log('\n======================================================');
console.log(` Result: ${passedTests}/${totalTests} Tests Passed`);
console.log('======================================================\n');

if (passedTests !== totalTests) {
  process.exit(1);
}
