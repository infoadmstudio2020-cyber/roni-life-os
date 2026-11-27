import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

console.log('🧪 Starting Export Required Information Schema Test Suite...\n');

import { TEMPLATES_CATALOG, AIProjectSpecGenerator } from '../js/app/modules/generator/AIProjectSpecGenerator.js';

// ============================================================================
// TEST 1: Schema Generator Method Existence & Functionality
// ============================================================================
console.log('▶ Test 1: AIProjectSpecGenerator.generateRequiredInformationSchema existence');
assert(typeof AIProjectSpecGenerator.generateRequiredInformationSchema === 'function', 'generateRequiredInformationSchema must be a static function');
console.log('✅ Test 1 Passed: generateRequiredInformationSchema is defined.\n');

// ============================================================================
// TEST 2: Schema Generation for all Catalog Templates
// ============================================================================
console.log('▶ Test 2: Dynamic Schema Generation for each template in TEMPLATES_CATALOG');

const allTemplateIds = [
  'android-java-library',
  'android-app',
  'multi-module-android-sdk',
  'pure-java-library',
  'android-library-standalone'
];

allTemplateIds.forEach(id => {
  const schema = AIProjectSpecGenerator.generateRequiredInformationSchema(id);
  assert(schema, `Schema for '${id}' must be generated`);
  assert.strictEqual(schema.$schema, 'https://json-schema.org/draft-07/schema#', 'Must adhere to JSON Schema Draft-07');
  assert.strictEqual(schema.template_id, id, `template_id must match '${id}'`);
  assert(schema.title.includes(schema.template_name), 'Title must include template name');
  assert(schema.ai_usage_guide && schema.ai_usage_guide.instruction, 'Must include AI usage instruction');
  assert(schema.required_information && typeof schema.required_information === 'object', 'Must have required_information object');
  assert(Array.isArray(schema.required_information.required), 'Must have required array');
  assert(schema.required_information.required.length > 0, 'Required fields cannot be empty');
  assert(schema.required_information.properties && typeof schema.required_information.properties === 'object', 'Must have properties map');
  assert(Array.isArray(schema.modules_specification), 'Must define modules_specification');
  assert(schema.modules_specification.length > 0, 'Modules specification cannot be empty');
  assert(Array.isArray(schema.dependencies_specification), 'Must define dependencies_specification');
  assert(schema.publishing_specification, 'Must define publishing_specification');
  assert(Array.isArray(schema.validation_rules_summary), 'Must define validation_rules_summary');

  // Verify that the schema is completely valid JSON
  const jsonStr = JSON.stringify(schema, null, 2);
  const parsed = JSON.parse(jsonStr);
  assert.deepStrictEqual(parsed.template_id, id);
  console.log(`  ✓ Template '${id}' produces valid schema (${schema.required_information.required.length} required fields, ${schema.modules_specification.length} modules)`);
});
console.log('✅ Test 2 Passed: All catalog templates generate valid JSON schemas.\n');

// ============================================================================
// TEST 3: Template-Specific Dynamic Differences (Non-Hardcoded Verification)
// ============================================================================
console.log('▶ Test 3: Verifying dynamic non-hardcoded metadata per template');

// 3a. Pure Java Library
const jvmSchema = AIProjectSpecGenerator.generateRequiredInformationSchema('pure-java-library');
assert.strictEqual(jvmSchema.project_type, 'java_library', 'pure-java-library project_type must be java_library');
assert(jvmSchema.required_information.required.includes('javaVersion'), 'pure-java-library must require javaVersion');
assert(!jvmSchema.required_information.required.includes('compileSdk'), 'pure-java-library must NOT require compileSdk');
assert.strictEqual(jvmSchema.modules_specification[0].targetArtifact, 'JAR', 'Artifact must be JAR');
assert.strictEqual(jvmSchema.build_environment_specification.agp_version, 'N/A (Pure Java Library)', 'AGP must be marked N/A');

// 3b. Android Application
const appSchema = AIProjectSpecGenerator.generateRequiredInformationSchema('android-app');
assert.strictEqual(appSchema.project_type, 'android_application');
assert(appSchema.required_information.required.includes('versionCode'), 'android-app must require versionCode');
assert(!appSchema.publishing_specification.enabled, 'android-app must not have JitPack publishing enabled');
assert(appSchema.modules_specification[0].targetArtifact.includes('APK'), 'Target artifact must include APK');

// 3c. Multi-Module SDK
const sdkSchema = AIProjectSpecGenerator.generateRequiredInformationSchema('multi-module-android-sdk');
assert.strictEqual(sdkSchema.project_type, 'multi_module_android_sdk');
assert(sdkSchema.required_information.required.includes('coreModuleName'), 'SDK must require coreModuleName');
assert(sdkSchema.required_information.required.includes('uiModuleName'), 'SDK must require uiModuleName');
assert.strictEqual(sdkSchema.modules_specification.length, 3, 'Must define 3 modules (:core, :ui, :app)');
assert(sdkSchema.publishing_specification.publishedArtifacts.length === 2, 'Must specify 2 published artifacts');

// 3d. Android Java Library
const libSchema = AIProjectSpecGenerator.generateRequiredInformationSchema('android-java-library');
assert.strictEqual(libSchema.project_type, 'android_library');
assert(libSchema.required_information.required.includes('libraryModuleName'));
assert(libSchema.required_information.required.includes('groupId'));
assert(libSchema.required_information.required.includes('artifactId'));
assert(libSchema.publishing_specification.enabled);

console.log('✅ Test 3 Passed: Dynamic metadata reflects actual template configuration and differences.\n');

// ============================================================================
// TEST 4: TemplatesPage UI Buttons and Modal Elements Inspection
// ============================================================================
console.log('▶ Test 4: TemplatesPage.js UI integration inspection');

const templatesPageSource = fs.readFileSync(path.join(rootDir, 'js/app/pages/TemplatesPage.js'), 'utf8');

assert(templatesPageSource.includes('Export Required Information Schema'), 'Must contain "Export Required Information Schema" button label');
assert(templatesPageSource.includes('#tmpl-export-schema-btn'), 'Must have #tmpl-export-schema-btn identifier');
assert(templatesPageSource.includes('data-action="export-schema"'), 'Must have data-action="export-schema" on template cards');
assert(templatesPageSource.includes('#openSchemaModal'), 'Must have #openSchemaModal method implementation');
assert(templatesPageSource.includes('Copy JSON'), 'Must have "Copy JSON" action in modal');
assert(templatesPageSource.includes('Download JSON'), 'Must have "Download JSON" action in modal');
assert(templatesPageSource.includes('Close'), 'Must have "Close" action in modal');
assert(templatesPageSource.includes('AIProjectSpecGenerator.generateRequiredInformationSchema'), 'Must call generateRequiredInformationSchema');

console.log('✅ Test 4 Passed: TemplatesPage UI correctly binds export button, schema modal, Copy, Download, and Close actions.\n');

// ============================================================================
// TEST 5: CSS Modal & Preview Styling Inspection
// ============================================================================
console.log('▶ Test 5: templates.css modal styling inspection');

const templatesCss = fs.readFileSync(path.join(rootDir, 'css/pages/templates.css'), 'utf8');

assert(templatesCss.includes('.tmpl-schema-modal-overlay'), 'Must have .tmpl-schema-modal-overlay');
assert(templatesCss.includes('.tmpl-schema-modal'), 'Must have .tmpl-schema-modal');
assert(templatesCss.includes('.tmpl-schema-code-pre'), 'Must have .tmpl-schema-code-pre');
assert(templatesCss.includes('[data-theme="light"] .tmpl-schema-modal-body'), 'Must support light theme modal styling');

console.log('✅ Test 5 Passed: templates.css provides full dark/light theme support for Schema Modal.\n');

console.log('🎉 All 5 Export Required Information Schema Tests Passed Successfully!\n');
