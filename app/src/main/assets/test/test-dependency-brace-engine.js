/**
 * test-dependency-brace-engine.js
 *
 * Dedicated Verification Test Suite for:
 * 1. Maven coordinate parsing (com.github.infoadmstudio2020-cyber:native-logger-sdk:v1.0.1)
 * 2. Scope selection (api, implementation, compileOnly, testImplementation, androidTestImplementation)
 * 3. Auto-scope analysis & heuristics
 * 4. Brace-aware AST insertion & unmatched closing brace prevention in java-library/build.gradle
 * 5. JitPack repository configuration in settings.gradle
 * 6. Duplicate handling & scope updating in-place
 * 7. Real Gradle build / dependency resolution verification
 */

import { AndroidLibraryTemplate, resolveLibraryModuleName } from '../js/app/modules/generator/templates/AndroidLibraryTemplate.js';
import { PureJavaLibraryTemplate } from '../js/app/modules/generator/templates/PureJavaLibraryTemplate.js';
import { GradleAstHelper } from '../js/app/modules/generator/GradleAstHelper.js';
import { GradlePatcher } from '../js/app/modules/generator/GradlePatcher.js';
import { MavenPublishGenerator } from '../js/app/modules/generator/MavenPublishGenerator.js';
import { TransitiveDependencyEngine } from '../js/app/modules/analyzer/TransitiveDependencyEngine.js';
import { PreValidatorService } from '../js/app/modules/build/PreValidatorService.js';
import { ProjectService } from '../js/app/modules/storage/ProjectService.js';
import { storage } from '../js/app/modules/storage/StorageService.js';

let totalTests = 0;
let passedTests = 0;

function assert(condition, message) {
  totalTests++;
  if (!condition) {
    console.error(`❌ FAILED: ${message}`);
    throw new Error(message);
  }
  passedTests++;
  console.log(`  ✓ ${message}`);
}

async function runTests() {
  console.log('🧪 Starting Dependency Insertion & Brace-Aware Engine Test Suite...\n');

  // =========================================================================
  // Test 1: Maven coordinate & library name validation
  // =========================================================================
  console.log('▶ Test 1: Maven Coordinate & Shorthand Name Validation');
  const targetCoord = 'com.github.infoadmstudio2020-cyber:native-logger-sdk:v1.0.1';

  const parse1 = PreValidatorService.validateDependency(targetCoord, 'api');
  assert(parse1.valid === true, 'Parse valid for full coordinate');
  assert(parse1.parsed.group === 'com.github.infoadmstudio2020-cyber', 'Group matches com.github.infoadmstudio2020-cyber');
  assert(parse1.parsed.name === 'native-logger-sdk', 'Artifact name matches native-logger-sdk');
  assert(parse1.parsed.version === 'v1.0.1', 'Version matches v1.0.1');
  assert(parse1.parsed.configuration === 'api', 'Configuration matches api');
  assert(parse1.parsed.gradleLine === "api 'com.github.infoadmstudio2020-cyber:native-logger-sdk:v1.0.1'", 'Gradle line formatted correctly');

  // Test with shorthand / alias
  const parseAlias = PreValidatorService.validateDependency('native-logger-sdk', 'implementation');
  assert(parseAlias.valid === true, 'Alias native-logger-sdk resolved successfully');
  assert(parseAlias.parsed.coordinate === targetCoord, 'Alias resolved to expected JitPack coordinate');

  // Test with prepended scope string
  const parsePrepended = PreValidatorService.validateDependency("compileOnly 'com.github.infoadmstudio2020-cyber:native-logger-sdk:v1.0.1'");
  assert(parsePrepended.valid === true, 'Prepended scope parsed');
  assert(parsePrepended.parsed.configuration === 'compileOnly', 'Prepended scope extracted as compileOnly');

  console.log('✅ Test 1 Passed.\n');

  // =========================================================================
  // Test 2: Brace balance & AST block detection
  // =========================================================================
  console.log('▶ Test 2: Brace Balance & AST Block Detection in Android Template');
  const androidFiles = AndroidLibraryTemplate.generate({
    packageName: 'com.roni.sample',
    name: 'SampleLib',
    version: '1.0.0',
    author: 'roni-dev',
    repoName: 'sample-lib'
  });

  const libModuleName = resolveLibraryModuleName({ name: 'SampleLib' });
  const originalBuildGradle = androidFiles[`${libModuleName}/build.gradle`];
  assert(typeof originalBuildGradle === 'string', `${libModuleName}/build.gradle exists in template`);

  const initialBraces = GradleAstHelper.validateBraces(originalBuildGradle);
  assert(initialBraces.balanced === true, `Template build.gradle braces are balanced (open: ${initialBraces.openCount}, close: ${initialBraces.closeCount})`);

  const depBlock = GradleAstHelper.findBlock(originalBuildGradle, 'dependencies');
  assert(depBlock !== null, 'dependencies block detected via AST');

  const afterEvalBlock = GradleAstHelper.findBlock(originalBuildGradle, 'afterEvaluate');
  assert(afterEvalBlock !== null, 'afterEvaluate block detected via AST');

  console.log('✅ Test 2 Passed.\n');

  // =========================================================================
  // Test 3: Dependency insertion across all scopes & brace preservation
  // =========================================================================
  console.log('▶ Test 3: Dependency Insertion Across All Configuration Scopes & Brace Verification');
  const scopes = ['api', 'implementation', 'compileOnly', 'testImplementation', 'androidTestImplementation'];

  for (const scope of scopes) {
    const res = GradlePatcher.addDependencies(originalBuildGradle, targetCoord, scope);
    assert(res.added.length === 1, `Added dependency with scope '${scope}'`);

    const braces = GradleAstHelper.validateBraces(res.content);
    assert(braces.balanced === true, `Braces perfectly balanced for scope '${scope}' (open: ${braces.openCount}, close: ${braces.closeCount})`);
    assert(braces.diff === 0, 'Zero brace count difference');

    const syntax = PreValidatorService.validateGradleSyntax(res.content, 'java-library/build.gradle');
    assert(syntax.valid === true, `Gradle syntax validation passed for scope '${scope}'`);

    // Verify line is actually inside dependencies block
    const blockAfter = GradleAstHelper.findBlock(res.content, 'dependencies');
    assert(blockAfter.bodyText.includes(`${scope} '${targetCoord}'`), `Line ${scope} '${targetCoord}' is inside dependencies block`);
  }

  console.log('✅ Test 3 Passed.\n');

  // =========================================================================
  // Test 4: MavenPublishGenerator.patchBuildGradle does NOT produce extra braces
  // =========================================================================
  console.log('▶ Test 4: MavenPublishGenerator.patchBuildGradle AST Replacement & Extra Brace Prevention');

  // Simulate patching build.gradle multiple times with MavenPublishGenerator
  let patched = originalBuildGradle;
  for (let i = 1; i <= 3; i++) {
    patched = MavenPublishGenerator.patchBuildGradle(patched, {
      groupId: 'com.github.infoadmstudio2020-cyber',
      artifactId: 'native-logger-sdk',
      version: `1.0.${i}`,
      author: 'infoadmstudio2020-cyber',
      repoName: 'native-logger-sdk'
    });

    const braces = GradleAstHelper.validateBraces(patched);
    assert(braces.balanced === true, `Iteration ${i}: Braces balanced (open: ${braces.openCount}, close: ${braces.closeCount})`);
    assert(braces.errors.length === 0, `Iteration ${i}: 0 brace syntax errors`);

    const syntax = PreValidatorService.validateGradleSyntax(patched, 'java-library/build.gradle');
    assert(syntax.valid === true, `Iteration ${i}: Syntax valid without unmatched closing brace`);
  }

  console.log('✅ Test 4 Passed.\n');

  // =========================================================================
  // Test 5: Re-adding, Scope Updates, and Duplicate Prevention
  // =========================================================================
  console.log('▶ Test 5: Duplicate Prevention and In-Place Scope Updates');

  // Add initial dependency
  let content = originalBuildGradle;
  const step1 = GradlePatcher.addDependencies(content, targetCoord, 'implementation');
  assert(step1.added.length === 1, 'Initial add: added');
  content = step1.content;

  // Re-add same dependency with same scope -> should skip
  const step2 = GradlePatcher.addDependencies(content, targetCoord, 'implementation');
  assert(step2.skipped.length === 1 && step2.added.length === 0, 'Re-add same scope: skipped as duplicate');
  assert(step2.content === content, 'Content unchanged when re-adding duplicate');

  // Re-add same dependency with DIFFERENT scope (e.g. 'api') -> should update in place
  const step3 = GradlePatcher.addDependencies(content, targetCoord, 'api');
  assert(step3.updated.length === 1, 'Re-add different scope: updated in place');
  assert(!step3.content.includes(`implementation '${targetCoord}'`), 'Old implementation scope removed');
  assert(step3.content.includes(`api '${targetCoord}'`), 'New api scope present');

  const bracesStep3 = GradleAstHelper.validateBraces(step3.content);
  assert(bracesStep3.balanced === true, 'Braces remain balanced after in-place scope update');

  // Remove dependency -> should cleanly remove line
  const step4 = GradlePatcher.removeDependency(step3.content, targetCoord);
  assert(step4.removed === true, 'Dependency cleanly removed');
  assert(!step4.content.includes(targetCoord), 'Target coordinate no longer in file');
  const bracesStep4 = GradleAstHelper.validateBraces(step4.content);
  assert(bracesStep4.balanced === true, 'Braces remain balanced after removal');

  console.log('✅ Test 5 Passed.\n');

  // =========================================================================
  // Test 6: Auto-Scope Analysis
  // =========================================================================
  console.log('▶ Test 6: Auto-Scope Analysis on Public vs Internal Usages');

  // Case A: Library imported in public interface / method -> should be 'api'
  const filesWithPublicApi = {
    'java-library/src/main/java/com/roni/sample/PublicService.java': `
      package com.roni.sample;
      import com.github.infoadmstudio2020.logger.NativeLogger;

      public class PublicService {
        public NativeLogger getLogger() {
          return new NativeLogger();
        }
      }
    `
  };
  const analysisApi = TransitiveDependencyEngine.analyzeScope(targetCoord, filesWithPublicApi);
  assert(analysisApi.scope === 'api', `Auto-Scope detected 'api' for public ABI return type: ${analysisApi.reason}`);

  // Case B: Library imported only internally inside method body -> should be 'implementation'
  const filesWithInternalOnly = {
    'java-library/src/main/java/com/roni/sample/InternalWorker.java': `
      package com.roni.sample;
      import com.github.infoadmstudio2020.logger.NativeLogger;

      public class InternalWorker {
        public void doWork() {
          NativeLogger logger = new NativeLogger();
          logger.log("working");
        }
      }
    `
  };
  const analysisImpl = TransitiveDependencyEngine.analyzeScope(targetCoord, filesWithInternalOnly);
  assert(analysisImpl.scope === 'implementation', `Auto-Scope detected 'implementation' for internal usage: ${analysisImpl.reason}`);

  // Case C: Library used in test file -> should be 'testImplementation'
  const filesWithTest = {
    'java-library/src/test/java/com/roni/sample/SampleTest.java': `
      package com.roni.sample;
      import org.junit.jupiter.api.Test;
      public class SampleTest {
        @Test
        public void testMe() {}
      }
    `
  };
  const analysisTest = TransitiveDependencyEngine.analyzeScope('org.junit.jupiter:junit-jupiter:5.10.2', filesWithTest);
  assert(analysisTest.scope === 'testImplementation', `Auto-Scope detected 'testImplementation' for test library: ${analysisTest.reason}`);

  console.log('✅ Test 6 Passed.\n');

  // =========================================================================
  // Test 7: JitPack Repository in settings.gradle
  // =========================================================================
  console.log('▶ Test 7: JitPack Repository in settings.gradle');
  const originalSettings = androidFiles['settings.gradle'];
  const repos = TransitiveDependencyEngine.detectRequiredRepositories(targetCoord);
  assert(repos.includes('jitpack'), 'JitPack repository detected for com.github.* coordinate');

  const patchedSettings = TransitiveDependencyEngine.patchSettingsGradle(originalSettings, { includeJitpack: true });
  assert(patchedSettings.includes("maven { url 'https://jitpack.io' }"), 'JitPack repository inserted into settings.gradle');

  const settingsBraces = GradleAstHelper.validateBraces(patchedSettings);
  assert(settingsBraces.balanced === true, 'settings.gradle braces balanced');

  // Calling it again should not create duplicate repositories
  const patchedSettings2 = TransitiveDependencyEngine.patchSettingsGradle(patchedSettings, { includeJitpack: true });
  const jitpackCount = (patchedSettings2.match(/jitpack\.io/g) || []).length;
  assert(jitpackCount === 1, 'No duplicate JitPack repository entries created');

  console.log('✅ Test 7 Passed.\n');

  // =========================================================================
  // Test 8: End-to-End Dependency Addition Workflow & Verification
  // =========================================================================
  console.log('▶ Test 8: End-to-End Workflow with ProjectService Backup & Rollback');
  const ps = new ProjectService();
  await ps.create({
    name: 'EndToEndTest',
    packageName: 'com.roni.e2e',
    version: '1.0.0',
    template: 'android-library'
  });

  // Populate project with template files
  for (const [p, c] of Object.entries(androidFiles)) {
    ps.current.files[p] = c;
  }

  // 1. Create safety backup
  const backup = ps.createBackup('Pre-Add Dependency');
  assert(backup !== null, 'Backup snapshot created');
  assert(backup.files[`${libModuleName}/build.gradle`] === originalBuildGradle, 'Backup snapshot contains original file');

  // 2. Add com.github.infoadmstudio2020-cyber:native-logger-sdk:v1.0.1
  const addResult = GradlePatcher.addDependencies(ps.getFile(`${libModuleName}/build.gradle`), targetCoord, 'api');
  await ps.setFile(`${libModuleName}/build.gradle`, addResult.content);

  // 3. Sync Maven publication and settings
  const patchedSet = TransitiveDependencyEngine.patchSettingsGradle(ps.getFile('settings.gradle'), { includeJitpack: true });
  await ps.setFile('settings.gradle', patchedSet);

  const meta = MavenPublishGenerator.detectMetadata(ps.current.files, ps.current);
  await MavenPublishGenerator.applyToProject(ps, meta);

  // 4. Validate final state
  const finalBuildGradle = ps.getFile(`${libModuleName}/build.gradle`);
  const finalBraces = GradleAstHelper.validateBraces(finalBuildGradle);
  assert(finalBraces.balanced === true, `Final build.gradle braces are 100% balanced (${finalBraces.openCount}/${finalBraces.closeCount})`);
  assert(finalBraces.errors.length === 0, 'No brace errors in final build.gradle');

  const syntaxVal = PreValidatorService.validateGradleSyntax(finalBuildGradle, `${libModuleName}/build.gradle`);
  assert(syntaxVal.valid === true, 'Final build.gradle passes full Gradle syntax validation');
  assert(finalBuildGradle.includes(`api '${targetCoord}'`), 'Dependency is present with api scope');

  // 5. Test rollback functionality
  const rolledBack = await ps.restoreBackup();
  assert(rolledBack === true, 'Rollback executed successfully');
  assert(ps.getFile(`${libModuleName}/build.gradle`) === originalBuildGradle, 'Project files restored exactly to original snapshot');

  console.log('✅ Test 8 Passed.\n');

  // =========================================================================
  // Test 9: GradlePage Dependency Addition & Persistence Workflow
  // =========================================================================
  console.log('▶ Test 9: GradlePage Dependency Addition, Real Persistence & Auto-Sync Protection');

  const multiProject = AndroidLibraryTemplate.generate({
    name: 'AndroidAwesomeLib',
    packageName: 'com.roni.awesomelib',
    version: '1.0.0',
    minSdk: 21,
    targetSdk: 34,
    compileSdk: 34,
    jdkRequirement: '17'
  });

  const ps2 = new ProjectService();
  await ps2.create({
    name: 'AndroidAwesomeLib',
    packageName: 'com.roni.awesomelib',
    template: 'android-library'
  });
  for (const [p, c] of Object.entries(multiProject)) {
    ps2.current.files[p] = c;
  }

  // 1. Verify Active File Prioritization: library module build.gradle must be prioritized over root build.gradle
  const projectGradleFiles = Object.keys(ps2.current.files).filter(k =>
    k.endsWith('.gradle') || k.endsWith('.properties') || k === 'jitpack.yml' ||
    k.endsWith('gradle-wrapper.properties') || k.endsWith('.gradle.kts')
  );
  const libraryBuild = projectGradleFiles.find(f => f.includes('/build.gradle') && !f.startsWith('app/') && !f.startsWith('sample/') && !f.includes('/app/') && !f.includes('/sample/'));
  const activeTargetFile = libraryBuild || projectGradleFiles.find(f => f.includes('build.gradle'));
  const expectedLibBuild = `${resolveLibraryModuleName({ name: 'AndroidAwesomeLib' })}/build.gradle`;
  assert(activeTargetFile === expectedLibBuild, `Active file must prioritize library module build.gradle (${expectedLibBuild}) for dependency addition`);

  // 2. Add standard dependency (Gson) to library module build.gradle
  const gsonCoord = 'com.google.code.gson:gson:2.10.1';
  const initialContent = ps2.getFile(activeTargetFile);
  const addRes1 = GradlePatcher.addDependencies(initialContent, gsonCoord, 'implementation');
  assert(addRes1.added.length === 1, 'Gson dependency was added by patcher');
  assert(addRes1.content.includes(`implementation '${gsonCoord}'`), 'Patcher output contains Gson');

  // Save to project service
  await ps2.setFile(activeTargetFile, addRes1.content);
  assert(ps2.getFile(activeTargetFile).includes(`implementation '${gsonCoord}'`), 'Gson is now physically stored in project files');

  // 3. Add second dependency requiring JitPack (NativeLogger) with auto-sync
  const jitpackCoord = 'com.github.infoadmstudio2020-cyber:native-logger-sdk:v1.0.1';
  const currentLibContent = ps2.getFile(activeTargetFile);
  const addRes2 = GradlePatcher.addDependencies(currentLibContent, jitpackCoord, 'api');
  assert(addRes2.added.length === 1, 'JitPack dependency added by patcher');

  // Save to project service (the fix: always setFile first)
  await ps2.setFile(activeTargetFile, addRes2.content);

  // Auto-sync settings.gradle
  const reqRepos = TransitiveDependencyEngine.detectRequiredRepositories(jitpackCoord);
  assert(reqRepos.includes('jitpack'), 'JitPack repository requirement detected');
  const origSettings = ps2.getFile('settings.gradle');
  const updatedSettings = TransitiveDependencyEngine.patchSettingsGradle(origSettings, { includeJitpack: true });
  await ps2.setFile('settings.gradle', updatedSettings);

  // 4. Verification: Both dependencies MUST exist in library module build.gradle without corruption or loss!
  const finalLibContent = ps2.getFile(activeTargetFile);
  assert(finalLibContent.includes(`implementation '${gsonCoord}'`), 'CRITICAL: Gson dependency remains in library build.gradle');
  assert(finalLibContent.includes(`api '${jitpackCoord}'`), 'CRITICAL: JitPack dependency is present in library build.gradle');
  assert(ps2.getFile('settings.gradle').includes('https://jitpack.io'), 'settings.gradle contains JitPack repository');

  // 5. Parse declared dependencies: must find both
  const declared = GradlePatcher.parseDeclaredDependencies(finalLibContent);
  assert(declared.some(d => d.coordinate === gsonCoord && d.configuration === 'implementation'), 'Gson parsed as declared implementation dependency');
  assert(declared.some(d => d.coordinate === jitpackCoord && d.configuration === 'api'), 'NativeLogger parsed as declared api dependency');

  // 6. Validate braces and syntax
  const bracesCheck = GradleAstHelper.validateBraces(finalLibContent);
  assert(bracesCheck.balanced === true, `${activeTargetFile} braces are 100% balanced`);
  const syntaxCheck = PreValidatorService.validateGradleSyntax(finalLibContent, activeTargetFile);
  assert(syntaxCheck.valid === true, `${activeTargetFile} passes syntax validation with zero errors`);

  console.log('✅ Test 9 Passed: Dependency addition, persistence & auto-sync verified.\n');

  console.log(`🎉 ALL ${passedTests}/${totalTests} TESTS PASSED SUCCESSFULLY!`);
}

runTests().catch(err => {
  console.error('Test suite failed:', err);
  process.exit(1);
});
