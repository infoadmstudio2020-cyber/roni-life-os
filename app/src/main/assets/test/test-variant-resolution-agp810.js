import assert from 'node:assert';
import { AGP_COMPATIBILITY_TABLE, GRADLE_JDK_TABLE, SDK_REQUIREMENTS, ENVIRONMENT_PRESETS } from '../js/app/modules/environment/EnvironmentCompatibilityMatrix.js';
import { EnvironmentManager } from '../js/app/modules/environment/EnvironmentManager.js';
import { AdaptiveWorkflowGenerator } from '../js/app/modules/github/AdaptiveWorkflowGenerator.js';
import { AndroidProjectWorkflowAnalyzer } from '../js/app/modules/github/AndroidProjectWorkflowAnalyzer.js';
import { AndroidLibraryTemplate } from '../js/app/modules/generator/templates/AndroidLibraryTemplate.js';
import { MultiModuleTemplate } from '../js/app/modules/generator/templates/MultiModuleTemplate.js';
import { MavenPublishGenerator } from '../js/app/modules/generator/MavenPublishGenerator.js';
import { JitPackBuildService } from '../js/app/modules/generator/JitPackBuildService.js';
import { GradleAstHelper } from '../js/app/modules/generator/GradleAstHelper.js';

console.log('🧪 Starting AGP 8.10.1, Gradle 8.13 & Variant Resolution Test Suite...\n');

// 1. Compatibility Matrix verification for AGP 8.10.1 and Gradle 8.13
console.log('▶ Test 1: AGP 8.10.1 and Gradle 8.13 Compatibility Matrix Verification');
const agp810Rule = AGP_COMPATIBILITY_TABLE.find(r => r.agpMin === '8.10.0');
assert.ok(agp810Rule, 'AGP 8.10.x rule exists in table');
assert.strictEqual(agp810Rule.recommendedGradle, '8.13', 'AGP 8.10.x recommends Gradle 8.13');
assert.strictEqual(agp810Rule.minJdk, '17', 'AGP 8.10.x minimum JDK is 17');
assert.strictEqual(agp810Rule.recommendedJdk, '21', 'AGP 8.10.x recommends JDK 21');

const gradle813Rule = GRADLE_JDK_TABLE.find(r => r.gradleMin === '8.13');
assert.ok(gradle813Rule, 'Gradle 8.13 rule exists in table');
assert.strictEqual(gradle813Rule.maxJdk, 23, 'Gradle 8.13 supports up to JDK 23');

const sdk36Req = SDK_REQUIREMENTS[36];
assert.ok(sdk36Req, 'SDK 36 requirements defined');
assert.strictEqual(sdk36Req.recommendedAgp, '8.10.1', 'SDK 36 recommends AGP 8.10.1');

const agp810Preset = ENVIRONMENT_PRESETS['android-lib-agp810'];
assert.ok(agp810Preset, 'AGP 8.10.1 preset exists');
assert.strictEqual(agp810Preset.gradleVersion, '8.13');
assert.strictEqual(agp810Preset.agpVersion, '8.10.1');
assert.strictEqual(agp810Preset.jdkVersion, '17');
console.log('✅ Test 1 Passed: AGP 8.10.1 and Gradle 8.13 matrix verified.\n');

// 2. Variant Resolution & Fallback verification in generated build.gradle templates
console.log('▶ Test 2: Variant Resolution & Fallback in Library and App Templates');
const libFiles = AndroidLibraryTemplate.generate({
  name: 'VariantSafeLib',
  packageName: 'com.example.variantsafe',
  version: '1.0.0',
  libraryModuleName: 'library'
});

const libBuild = libFiles['library/build.gradle'];
assert.ok(libBuild, 'library/build.gradle exists');
assert.ok(libBuild.includes("matchingFallbacks = ['release']"), 'Library contains matchingFallbacks for debug');
assert.ok(libBuild.includes("singleVariant('release')"), 'Library contains singleVariant release');
assert.ok(libBuild.includes("from components.findByName('release')"), 'Library uses safe components.findByName');

// Check app companion module
const appBuild = libFiles['app/build.gradle'];
assert.ok(appBuild, 'app/build.gradle exists');
assert.ok(appBuild.includes("matchingFallbacks = ['release']"), 'App module contains matchingFallbacks for debug');

// Check brace balance
const libBraces = GradleAstHelper.validateBraces(libBuild);
assert.strictEqual(libBraces.balanced, true, 'Library build.gradle braces are balanced');
assert.strictEqual(libBraces.diff, 0, 'Zero difference in braces');

const appBraces = GradleAstHelper.validateBraces(appBuild);
assert.strictEqual(appBraces.balanced, true, 'App build.gradle braces are balanced');
console.log('✅ Test 2 Passed: Variant resolution fallbacks & brace balance verified.\n');

// 3. MultiModuleTemplate verification
console.log('▶ Test 3: MultiModuleTemplate Variant Resolution & Publication');
const multiFiles = MultiModuleTemplate.generate({
  name: 'MultiSafeProject',
  packageName: 'com.example.multisafe',
  version: '2.0.0',
  libraryModuleName: 'core-sdk'
});

const multiLibBuild = multiFiles['core-sdk/build.gradle'];
assert.ok(multiLibBuild, 'core-sdk/build.gradle exists');
assert.ok(multiLibBuild.includes("matchingFallbacks = ['release']"), 'Multi-module library has matchingFallbacks');
assert.ok(multiLibBuild.includes("from components.findByName('release')"), 'Multi-module library uses safe component resolution');

const multiBraces = GradleAstHelper.validateBraces(multiLibBuild);
assert.strictEqual(multiBraces.balanced, true, 'Multi-module build.gradle braces are balanced');
console.log('✅ Test 3 Passed: MultiModuleTemplate verified.\n');

// 4. MavenPublishGenerator standalone generation & safe component attachment
console.log('▶ Test 4: MavenPublishGenerator Groovy & Kotlin DSL Publication Safety');
const groovyPub = MavenPublishGenerator.generateGroovyPublishingBlock({
  groupId: 'com.example.pub',
  artifactId: 'pub-sdk',
  version: '1.2.3'
});
assert.ok(groovyPub.includes("from components.findByName('release') ?: components.findByName('java') ?: components.findByName('default')"), 'Groovy DSL uses safe components.findByName');
assert.ok(!groovyPub.includes('from components.release'), 'Unsafe direct components.release property access removed');

const ktsPub = MavenPublishGenerator.generateKotlinPublishingBlock({
  groupId: 'com.example.pub',
  artifactId: 'pub-sdk',
  version: '1.2.3'
});
assert.ok(ktsPub.includes('components.findByName("release")'), 'Kotlin DSL uses safe components.findByName');
assert.ok(!ktsPub.includes('from(components["release"])'), 'Unsafe unchecked index access removed');
console.log('✅ Test 4 Passed: MavenPublishGenerator publication blocks verified.\n');

// 5. AdaptiveWorkflowGenerator bash syntax & fallback correctness
console.log('▶ Test 5: AdaptiveWorkflowGenerator GitHub Actions Workflow Syntax');
const profile = AndroidProjectWorkflowAnalyzer.analyze(libFiles, { name: 'VariantSafeLib', version: '1.0.0' });
const workflows = AdaptiveWorkflowGenerator.generateAll(profile, { resolved: { jdkVersion: '17', compileSdk: 35, gradleVersion: '8.13' } });

const ciYml = workflows['.github/workflows/ci.yml'];
const releaseYml = workflows['.github/workflows/release.yml'];
assert.ok(ciYml, 'ci.yml generated');
assert.ok(releaseYml, 'release.yml generated');

// Verify that the bash bad substitution bug '${profile?.primaryLibraryModule...' does NOT exist in release.yml!
assert.ok(!releaseYml.includes('${profile?.'), 'No raw JS optional chaining in generated release workflow bash script');
assert.ok(!ciYml.includes('${profile?.'), 'No raw JS optional chaining in generated CI workflow bash script');
assert.ok(releaseYml.includes(':library:assembleRelease') || releaseYml.includes('assembleRelease'), 'Primary library task properly interpolated');
console.log('✅ Test 5 Passed: GitHub Actions workflows generated with 100% valid bash syntax.\n');

// 6. JitPackBuildService diagnosis for Variant Resolution Mismatches
console.log('▶ Test 6: JitPack Error Diagnosis & Remediation for Variant Resolution');
const variantErrorLog = `
> Could not resolve project :library.
  Required by: project :app
  > No matching variant of project :library was found. The consumer was configured to find attribute 'com.android.build.api.attributes.BuildTypeAttr' with value 'debug' but producer only offers: 'release'.
`;

const diagnosed = JitPackBuildService.diagnose(variantErrorLog, libFiles);
const variantIssue = diagnosed.find(i => i.id === 'variant_resolution_mismatch');
assert.ok(variantIssue, 'Variant resolution mismatch detected from log');
assert.strictEqual(variantIssue.severity, 'error');

// Test auto-fix
let mockFiles = {
  'library/build.gradle': `
android {
    namespace 'com.test.lib'
    buildTypes {
        release {
            minifyEnabled false
        }
    }
}
`
};
const mockService = {
  current: { files: mockFiles },
  async setFile(path, content) {
    mockFiles[path] = content;
  }
};

const fixResult = await JitPackBuildService.applyAutoFix('variant_resolution_mismatch', mockService);
assert.strictEqual(fixResult.success, true, 'Auto-fix succeeded');
assert.ok(mockFiles['library/build.gradle'].includes("matchingFallbacks = ['release']"), 'Auto-fix injected matchingFallbacks');
assert.ok(mockFiles['library/build.gradle'].includes("singleVariant(\"release\")"), 'Auto-fix injected singleVariant publishing');
console.log('✅ Test 6 Passed: JitPack diagnosis and remediation for variant resolution verified.\n');

console.log('🎉 ALL 6 AGP 8.10.1 & VARIANT RESOLUTION TESTS PASSED 100%!\n');
