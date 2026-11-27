import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

console.log('🧪 Starting Templates Page, AI-Ready JSON & Editor Default State Test Suite...\n');

// ============================================================================
// TEST 1: Editor Page Project Files Default Collapsed State
// ============================================================================
console.log('▶ Test 1: Editor Page Project Files Default Collapsed State');

const editorModuleCode = fs.readFileSync(path.join(rootDir, 'js/app/modules/editor/EditorModule.js'), 'utf8');
assert(editorModuleCode.includes('#filesPanelOpen = false;'), 'EditorModule must default #filesPanelOpen to false');
assert(!editorModuleCode.includes('class="btn btn-ghost btn-sm btn-icon active" id="ed-filetree-toggle"'), 'Toggle button must NOT have hardcoded active class');
assert(editorModuleCode.includes('${this.#filesPanelOpen ? \'\' : \'collapsed\'}'), 'Files panel must include collapsed class by default');
assert(editorModuleCode.includes('this.#filesPanelOpen = false;'), 'destroy() or reset must restore collapsed state');

console.log('✅ Test 1 Passed: Project Files editor section defaults to collapsed/hidden.\n');

// ============================================================================
// TEST 2: Templates Catalog Verification
// ============================================================================
console.log('▶ Test 2: Templates Catalog Verification');

import { TEMPLATES_CATALOG, AIProjectSpecGenerator } from '../js/app/modules/generator/AIProjectSpecGenerator.js';

assert(Array.isArray(TEMPLATES_CATALOG), 'TEMPLATES_CATALOG must be an array');
assert(TEMPLATES_CATALOG.length >= 5, 'Must contain at least 5 reusable templates');

const expectedTemplates = [
  'android-java-library',
  'android-app',
  'multi-module-android-sdk',
  'pure-java-library',
  'android-library-standalone'
];

expectedTemplates.forEach(id => {
  const tmpl = TEMPLATES_CATALOG.find(t => t.id === id);
  assert(tmpl, `Template '${id}' must be present in catalog`);
  assert(tmpl.name, `Template '${id}' must have a name`);
  assert(Array.isArray(tmpl.dependencies), `Template '${id}' must list dependencies`);
  assert(tmpl.dependencies.length > 0, `Template '${id}' dependencies cannot be empty`);
  assert(Array.isArray(tmpl.fileStructure), `Template '${id}' must declare fileStructure`);
  assert(tmpl.fileStructure.length > 0, `Template '${id}' fileStructure cannot be empty`);
  assert(tmpl.recommendedBuildEnvironment, `Template '${id}' must define recommendedBuildEnvironment`);
  assert(tmpl.defaultConfig, `Template '${id}' must define defaultConfig`);
});

console.log('✅ Test 2 Passed: Templates Catalog contains all required Android/JVM templates.\n');

// ============================================================================
// TEST 3: AI-Ready JSON Specification Generation
// ============================================================================
console.log('▶ Test 3: AI-Ready JSON Specification Generation & Structure');

const customConfig = {
  projectName: 'MyCustomLib',
  packageName: 'com.cyber.nativeguard',
  groupId: 'com.github.cyberdev',
  artifactId: 'native-guard',
  version: '2.1.0',
  compileSdk: 36,
  minSdk: 24,
  targetSdk: 36,
  description: 'Advanced Android security and native guard library'
};

const spec = AIProjectSpecGenerator.generateSpec('android-java-library', customConfig);

// Validate AI-Ready JSON schema & structural integrity
assert.strictEqual(spec.format, 'AI_READY_PROJECT_SPECIFICATION', 'Format must be AI_READY_PROJECT_SPECIFICATION');
assert(typeof spec.ai_prompt_instruction === 'string' && spec.ai_prompt_instruction.length > 100, 'Must have clear AI prompt instructions');
assert.strictEqual(spec.project_metadata.project_name, 'MyCustomLib', 'Project name must match custom input');
assert.strictEqual(spec.project_metadata.package_name, 'com.cyber.nativeguard', 'Package name must match custom input');
assert.strictEqual(spec.project_metadata.group_id, 'com.github.cyberdev', 'Group ID must match custom input');
assert.strictEqual(spec.project_metadata.version, '2.1.0', 'Version must match custom input');
assert.strictEqual(spec.build_environment.compile_sdk, 36, 'Compile SDK must match custom input');

// Check publishing metadata
assert(spec.publishing_and_distribution.jitpack_coordinate.includes('com.github.cyberdev:native-guard:2.1.0'), 'JitPack coordinate must be formed accurately');

// Check dependencies
assert(Array.isArray(spec.dependencies), 'Dependencies must be an array');
assert(spec.dependencies.some(d => d.coordinate.includes('androidx.core')), 'AndroidX Core dependency must be included');

// Check file structure paths adaptation
assert(Array.isArray(spec.file_structure), 'file_structure must be an array');
assert(spec.file_structure.some(f => f.path.includes('com/cyber/nativeguard')), 'File structure must adapt Java source paths to package name');

// Check customizable parameters
assert(Array.isArray(spec.customizable_parameters), 'customizable_parameters must be present');
assert(spec.customizable_parameters.length > 0, 'Must document customizable fields');

// Validate serialize/parse roundtrip without loss
const serialized = JSON.stringify(spec, null, 2);
const deserialized = JSON.parse(serialized);
assert.deepStrictEqual(deserialized.project_metadata, spec.project_metadata, 'JSON serialization must be 100% lossless');

console.log('✅ Test 3 Passed: AI-Ready JSON specification generator produces compliant structured spec.\n');

// ============================================================================
// TEST 4: Navigation, Router, and UI Integration
// ============================================================================
console.log('▶ Test 4: Navigation, Router, and UI Integration');

const routerJs = fs.readFileSync(path.join(rootDir, 'js/app.js'), 'utf8');
assert(routerJs.includes("templates:  () => import('./app/pages/TemplatesPage.js')"), 'app.js PAGE_MAP must include templates');

const sidebarJs = fs.readFileSync(path.join(rootDir, 'js/app/ui/Sidebar.js'), 'utf8');
assert(sidebarJs.includes("{ id: 'templates',  label: 'Templates'"), 'Sidebar.js NAV_ITEMS must include templates');

const indexHtml = fs.readFileSync(path.join(rootDir, 'index.html'), 'utf8');
assert(indexHtml.includes('css/pages/templates.css'), 'index.html must link templates.css');

const templatesPageJs = fs.readFileSync(path.join(rootDir, 'js/app/pages/TemplatesPage.js'), 'utf8');
assert(templatesPageJs.includes('AIProjectSpecGenerator'), 'TemplatesPage must import AIProjectSpecGenerator');
assert(templatesPageJs.includes('#tmpl-copy-json-btn'), 'TemplatesPage must have copy JSON handler');
assert(templatesPageJs.includes('#tmpl-download-json-btn'), 'TemplatesPage must have download JSON handler');
assert(templatesPageJs.includes('#tmpl-use-generator-btn'), 'TemplatesPage must have generator integration');

console.log('✅ Test 4 Passed: Navigation, Router, and UI integration verified.\n');

// ============================================================================
// TEST 5: Version Synchronization Verification
// ============================================================================
console.log('▶ Test 5: Version Synchronization Verification');

const versionJson = JSON.parse(fs.readFileSync(path.join(rootDir, 'version.json'), 'utf8'));
assert(['0.1.3.0', '0.1.3.1', '0.1.3.2', '0.1.3.3', '0.1.3.4', '0.1.3.5', '0.1.3.6'].includes(versionJson.currentVersion), 'version.json currentVersion must be valid');
assert(Array.isArray(versionJson.changelog[versionJson.currentVersion]), 'Changelog must exist');
assert(sidebarJs.includes(`v${versionJson.currentVersion}`), 'Sidebar.js must show current version');
assert(indexHtml.includes(`v${versionJson.currentVersion}`), 'index.html must show current version');

console.log(`✅ Test 5 Passed: All project metadata synchronized to v${versionJson.currentVersion}.\n`);

console.log('🎉 ALL TEMPLATES & AI SPEC TESTS PASSED 100%!\n');
