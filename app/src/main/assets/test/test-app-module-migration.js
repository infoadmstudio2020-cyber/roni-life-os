import assert from 'node:assert';
import { AndroidLibraryTemplate } from '../js/app/modules/generator/templates/AndroidLibraryTemplate.js';
import { MultiModuleTemplate } from '../js/app/modules/generator/templates/MultiModuleTemplate.js';
import { GeneratorModule } from '../js/app/modules/generator/GeneratorModule.js';
import { JitPackAnalyzerEngine } from '../js/app/modules/generator/JitPackAnalyzerEngine.js';
import { AdaptiveWorkflowGenerator } from '../js/app/modules/github/AdaptiveWorkflowGenerator.js';
import { AndroidProjectWorkflowAnalyzer } from '../js/app/modules/github/AndroidProjectWorkflowAnalyzer.js';
import { VersionUpdateEngine } from '../js/app/modules/version/VersionUpdateEngine.js';

console.log('🧪 Starting App Module Migration Test Suite...\n');

// 1. AndroidLibraryTemplate Generation
console.log('▶ Test 1: AndroidLibraryTemplate generates :app companion module');
const files = AndroidLibraryTemplate.generate({
  name: 'MyAwesomeLibrary',
  packageName: 'com.roni.library',
  version: '1.0.0',
  language: 'java'
});

assert.ok(files['settings.gradle'].includes("include ':app'"), 'settings.gradle includes :app');
assert.ok(!files['settings.gradle'].includes("include ':sample'"), 'settings.gradle does NOT include :sample');
assert.ok(files['app/build.gradle'], 'app/build.gradle exists');
assert.ok(!files['sample/build.gradle'], 'sample/build.gradle does not exist');
assert.ok(files['app/src/main/AndroidManifest.xml'], 'app/src/main/AndroidManifest.xml exists');
assert.ok(files['app/src/main/res/layout/activity_main.xml'], 'app layout exists');
assert.ok(files['app/src/main/res/values/strings.xml'].includes('MyAwesomeLibrary App'), 'app strings.xml has MyAwesomeLibrary App');
assert.ok(files['app/build.gradle'].includes("applicationId 'com.roni.library.app'"), 'applicationId is com.roni.library.app');
assert.ok(files['app/build.gradle'].includes("namespace 'com.roni.library.app'"), 'namespace is com.roni.library.app');
assert.ok(files['app/build.gradle'].includes("implementation project(':my-awesome-library')"), 'app module depends on dynamic library module :my-awesome-library');
console.log('✅ Test 1 Passed.\n');

// 2. JitPack Configuration Safety
console.log('▶ Test 2: JitPack Configuration Safety & Task Separation');
assert.ok(files['jitpack.yml'].includes(':app:assembleRelease'), 'jitpack.yml builds :app:assembleRelease');
assert.ok(!files['jitpack.yml'].includes(':app:publishToMavenLocal'), 'jitpack.yml NEVER runs publishToMavenLocal on :app');
assert.ok(files['jitpack.yml'].includes(':my-awesome-library:publishToMavenLocal'), 'jitpack.yml publishes library module');

const analysis = JitPackAnalyzerEngine.analyze(files);
const appMod = analysis.modules.find(m => m.name === 'app');
assert.ok(appMod, 'app module detected by JitPackAnalyzerEngine');
assert.strictEqual(appMod.isSample, true, 'app module recognized as application/sample type');
assert.strictEqual(appMod.isLibrary, false, 'app module is not library');

const defaultTasks = JitPackAnalyzerEngine.generateDefaultModuleTasks(analysis);
const appTask = defaultTasks.find(t => t.name === 'app');
assert.strictEqual(appTask.publishToMavenLocal, false, 'Safety: app module has publishToMavenLocal: false');
console.log('✅ Test 2 Passed.\n');

// 3. GitHub Actions Workflows
console.log('▶ Test 3: GitHub Actions CI & Release Workflows build :app module safely');
assert.ok(files['.github/workflows/ci.yml'].includes(':app:assembleDebug'), 'ci.yml builds :app:assembleDebug');

const profile = AndroidProjectWorkflowAnalyzer.analyze(files);
assert.strictEqual(profile.primaryAppModule, 'app', 'primaryAppModule is detected as app');
assert.strictEqual(profile.primaryLibraryModule, 'my-awesome-library', 'primaryLibraryModule is dynamically detected as my-awesome-library');
const ciWorkflow = AdaptiveWorkflowGenerator.generateCIWorkflow(profile);
assert.ok(ciWorkflow.includes('Building companion app (app)'), 'Adaptive CI workflow builds companion app');
assert.ok(ciWorkflow.includes(':app:assembleRelease') || ciWorkflow.includes(':app:assembleDebug'), 'Adaptive CI workflow includes app build tasks');

const releaseWorkflow = AdaptiveWorkflowGenerator.generateReleaseWorkflow(profile);
assert.ok(releaseWorkflow.includes('Building companion app (app)'), 'Adaptive Release workflow builds companion app');
console.log('✅ Test 3 Passed.\n');

// 4. VersionUpdateEngine on app module
console.log('▶ Test 4: VersionUpdateEngine updates app module and protects toolchain');
const engine = new VersionUpdateEngine();
const updateRes = engine.applyUpdate(files, ['app/build.gradle', 'my-awesome-library/build.gradle'], '1.0.0', '1.0.1');
assert.ok(updateRes.updatedFiles['app/build.gradle'].includes('versionName "1.0.1"'), 'versionName updated in app/build.gradle');
assert.ok(updateRes.updatedFiles['app/build.gradle'].includes("applicationId 'com.roni.library.app'"), 'applicationId preserved and protected');
console.log('✅ Test 4 Passed.\n');

// 5. Explicit user input custom library module name
console.log('▶ Test 5: Dynamic library module name via user input (custom name)');
const customFiles = AndroidLibraryTemplate.generate({
  name: 'MyAwesomeLibrary',
  libraryModuleName: 'super-core-sdk',
  packageName: 'com.roni.library',
  version: '1.0.0',
  language: 'java'
});
assert.ok(customFiles['settings.gradle'].includes("include ':super-core-sdk'"), 'settings.gradle includes custom :super-core-sdk');
assert.ok(customFiles['super-core-sdk/build.gradle'], 'custom library build.gradle exists');
assert.ok(customFiles['super-core-sdk/src/main/AndroidManifest.xml'], 'custom library manifest exists');
assert.ok(customFiles['app/build.gradle'].includes("implementation project(':super-core-sdk')"), 'app module depends on custom :super-core-sdk');
assert.ok(customFiles['jitpack.yml'].includes(':super-core-sdk:publishToMavenLocal'), 'jitpack.yml publishes custom :super-core-sdk');
assert.ok(customFiles['.github/workflows/ci.yml'].includes(':super-core-sdk:assembleRelease'), 'ci.yml targets :super-core-sdk');
assert.ok(customFiles['.github/workflows/release.yml'].includes(':super-core-sdk:assembleRelease'), 'release.yml targets :super-core-sdk');

const customProfile = AndroidProjectWorkflowAnalyzer.analyze(customFiles);
assert.strictEqual(customProfile.primaryLibraryModule, 'super-core-sdk', 'primaryLibraryModule dynamically detected as super-core-sdk');
console.log('✅ Test 5 Passed.\n');

// 6. MultiModuleTemplate Dynamic Library Module Generation
console.log('▶ Test 6: MultiModuleTemplate dynamic library module generation and CI workflows');
const mmFiles = MultiModuleTemplate.generate({
  name: 'MultiPlatformCore',
  packageName: 'com.roni.multi',
  version: '1.2.0',
  libraryModuleName: 'core-platform-kit'
});

assert.ok(mmFiles['settings.gradle'].includes("include ':core-platform-kit'"), 'settings.gradle includes :core-platform-kit');
assert.ok(mmFiles['settings.gradle'].includes("include ':app'"), 'settings.gradle includes :app');
assert.ok(mmFiles['core-platform-kit/build.gradle'], 'core-platform-kit/build.gradle exists');
assert.ok(mmFiles['core-platform-kit/src/main/AndroidManifest.xml'], 'core-platform-kit manifest exists');
assert.ok(mmFiles['app/build.gradle'].includes("implementation project(':core-platform-kit')"), 'app depends on :core-platform-kit');
assert.ok(mmFiles['jitpack.yml'].includes(':core-platform-kit:publishToMavenLocal'), 'jitpack.yml publishes :core-platform-kit');
assert.ok(mmFiles['.github/workflows/ci.yml'].includes(':core-platform-kit:assembleRelease'), 'ci.yml targets :core-platform-kit');
assert.ok(mmFiles['.github/workflows/release.yml'].includes(':core-platform-kit:assembleRelease'), 'release.yml targets :core-platform-kit');
console.log('✅ Test 6 Passed.\n');

console.log('🎉 ALL APP MODULE MIGRATION TESTS PASSED 100%!');
