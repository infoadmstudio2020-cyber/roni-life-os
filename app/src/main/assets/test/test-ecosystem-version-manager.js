import assert from 'node:assert';
import { VersionComparator } from '../js/app/modules/ecosystem/VersionComparator.js';
import { JitPackClient } from '../js/app/modules/ecosystem/JitPackClient.js';
import { EcosystemScanner } from '../js/app/modules/ecosystem/EcosystemScanner.js';
import { EcosystemDependencyGraph } from '../js/app/modules/ecosystem/EcosystemDependencyGraph.js';
import { EcosystemRepositoryService } from '../js/app/modules/ecosystem/EcosystemRepositoryService.js';
import { storage } from '../js/app/modules/storage/StorageService.js';

console.log('🧪 Starting NativeRuntime Ecosystem & Version Manager Test Suite...\n');

// ============================================================================
// TEST 1: Version Comparison Engine
// ============================================================================
console.log('▶ Test 1: VersionComparator SemVer Parsing, Normalization & Precedence');

// 1.1 Equal versions with and without 'v' prefix
assert.strictEqual(VersionComparator.compare('1.0.0', '1.0.0'), 0, '1.0.0 should equal 1.0.0');
assert.strictEqual(VersionComparator.compare('v1.0.0', '1.0.0'), 0, 'v1.0.0 should equal 1.0.0');
assert.strictEqual(VersionComparator.compare('V2.3.4', 'v2.3.4'), 0, 'V2.3.4 should equal v2.3.4');
assert.strictEqual(VersionComparator.isEqual('v1.0.2', '1.0.2'), true, 'isEqual with v prefix');

// 1.2 Older and newer versions (major, minor, patch)
assert.strictEqual(VersionComparator.compare('1.0.1', '1.0.0'), 1, '1.0.1 > 1.0.0');
assert.strictEqual(VersionComparator.compare('1.0.0', '1.0.1'), -1, '1.0.0 < 1.0.1');
assert.strictEqual(VersionComparator.compare('1.10.0', '1.2.0'), 1, '1.10.0 must be greater than 1.2.0 (not alphabetical)');
assert.strictEqual(VersionComparator.compare('2.0.0', '1.9.9'), 1, '2.0.0 > 1.9.9');
assert.strictEqual(VersionComparator.isGreater('1.2.5', '1.2.4'), true);
assert.strictEqual(VersionComparator.isLess('0.9.1', '1.0.0'), true);

// 1.3 Pre-release identifiers
assert.strictEqual(VersionComparator.compare('1.0.0', '1.0.0-alpha'), 1, 'Normal version > pre-release');
assert.strictEqual(VersionComparator.compare('1.0.0-alpha', '1.0.0-beta'), -1, 'alpha < beta');
assert.strictEqual(VersionComparator.compare('1.0.0-beta.2', '1.0.0-beta.1'), 1, 'beta.2 > beta.1');
assert.strictEqual(VersionComparator.compare('1.0.0-rc.1', '1.0.0'), -1, 'rc.1 < 1.0.0');

// 1.4 Build metadata
assert.strictEqual(VersionComparator.compare('1.0.0+build1', '1.0.0+build2'), 0, 'Build metadata ignored in precedence');

// 1.5 Missing and invalid versions handled without throwing
assert.doesNotThrow(() => VersionComparator.parse(null));
assert.doesNotThrow(() => VersionComparator.parse(''));
assert.doesNotThrow(() => VersionComparator.parse('invalid-version-string'));
assert.strictEqual(VersionComparator.parse(null).isValid, false);
assert.strictEqual(VersionComparator.parse('not.a.version').isValid, false);
assert.strictEqual(VersionComparator.compare('1.0.0', null), 1, 'Valid > null');
assert.strictEqual(VersionComparator.compare(null, '1.0.0'), -1, 'Null < valid');

// 1.6 Sorting lists of versions
const unsorted = ['1.0.0', '1.10.0', 'v1.2.0', '2.0.0', '0.9.0', '1.2.0-alpha'];
const sortedDesc = VersionComparator.sortVersions(unsorted, 'desc');
assert.deepStrictEqual(sortedDesc, ['2.0.0', '1.10.0', 'v1.2.0', '1.2.0-alpha', '1.0.0', '0.9.0']);

console.log('✅ Test 1 Passed: VersionComparator verified.');

// ============================================================================
// TEST 2: Multi-Source Status Determination
// ============================================================================
console.log('▶ Test 2: Multi-Source Status Determination (Release vs Project vs Tag vs JitPack)');

// 2.1 Latest (all synchronized, JitPack ok)
const stLatest = VersionComparator.determineStatus({
  projectVersion: '1.0.1',
  githubRelease: '1.0.1',
  gitTag: '1.0.1',
  jitpackVersion: '1.0.1',
  jitpackStatus: 'ok'
});
assert.strictEqual(stLatest.status, 'Latest');
assert.strictEqual(stLatest.severity, 'success');

// 2.2 Update Available (Release > Project Version)
const stUpdate = VersionComparator.determineStatus({
  projectVersion: '1.0.0',
  githubRelease: '1.0.2',
  gitTag: '1.0.2',
  jitpackVersion: '1.0.2',
  jitpackStatus: 'ok'
});
assert.strictEqual(stUpdate.status, 'Update Available');
assert.strictEqual(stUpdate.code, 'update_available');

// 2.3 Project Version Ahead (Local changes ready for release)
const stAhead = VersionComparator.determineStatus({
  projectVersion: '1.0.3',
  githubRelease: '1.0.2',
  gitTag: '1.0.2',
  jitpackVersion: '1.0.2',
  jitpackStatus: 'ok'
});
assert.strictEqual(stAhead.status, 'Project Version Ahead');
assert.strictEqual(stAhead.severity, 'warning');

// 2.4 Release Missing (Tag exists, but Release not created)
const stRelMissing = VersionComparator.determineStatus({
  projectVersion: '1.0.2',
  githubRelease: null,
  gitTag: '1.0.2',
  jitpackStatus: 'ok'
});
assert.strictEqual(stRelMissing.status, 'Release Missing');
assert.strictEqual(stRelMissing.severity, 'warning');

// 2.5 JitPack Build Failed
const stJpFailed = VersionComparator.determineStatus({
  projectVersion: '1.0.1',
  githubRelease: '1.0.1',
  gitTag: '1.0.1',
  jitpackVersion: '1.0.1',
  jitpackStatus: 'error'
});
assert.strictEqual(stJpFailed.status, 'JitPack Build Failed');
assert.strictEqual(stJpFailed.severity, 'error');

// 2.6 Version Mismatch (Release tag differs from Git tag)
const stMismatch = VersionComparator.determineStatus({
  projectVersion: '1.0.1',
  githubRelease: '1.0.1',
  gitTag: '1.0.2',
  jitpackStatus: 'ok'
});
assert.strictEqual(stMismatch.status, 'Version Mismatch');

// 2.7 JitPack Not Verified
const stUnverified = VersionComparator.determineStatus({
  projectVersion: '1.0.1',
  githubRelease: '1.0.1',
  gitTag: '1.0.1',
  jitpackStatus: 'unknown'
});
assert.strictEqual(stUnverified.status, 'JitPack Not Verified');

console.log('✅ Test 2 Passed: Multi-source status determination verified.');

// ============================================================================
// TEST 3: JitPackClient Build Parsing & Version Resolution
// ============================================================================
console.log('▶ Test 3: JitPackClient Build Parsing & Resolution');

const mockDictBuilds = {
  '1.0.0': 'ok',
  'v1.0.1': 'ok',
  '1.0.2': 'error',
  'v1.0.3-SNAPSHOT': 'building'
};

// 3.1 Resolving with 'v' and without 'v'
const r1 = JitPackClient.resolveVersionStatus(mockDictBuilds, '1.0.1');
assert.strictEqual(r1.status, 'ok', '1.0.1 should resolve against v1.0.1 in dict');
assert.strictEqual(r1.matchedVersion, 'v1.0.1');

const r2 = JitPackClient.resolveVersionStatus(mockDictBuilds, 'v1.0.0');
assert.strictEqual(r2.status, 'ok', 'v1.0.0 should resolve against 1.0.0 in dict');
assert.strictEqual(r2.matchedVersion, '1.0.0');

const r3 = JitPackClient.resolveVersionStatus(mockDictBuilds, '1.0.2');
assert.strictEqual(r3.status, 'error', '1.0.2 should match failed build status');

const r4 = JitPackClient.resolveVersionStatus(mockDictBuilds, '9.9.9');
assert.strictEqual(r4.status, 'not_found', 'Unpublished version should return not_found');

// 3.2 URL builders
const webUrl = JitPackClient.getRepoWebUrl('infoadmstudio2020-cyber', 'native-logger-sdk');
assert(webUrl.includes('https://jitpack.io/#com.github.infoadmstudio2020-cyber/native-logger-sdk'));

const logUrl = JitPackClient.getBuildLogUrl('infoadmstudio2020-cyber', 'native-logger-sdk', 'v1.0.1');
assert(logUrl.includes('https://jitpack.io/com/github/infoadmstudio2020-cyber/native-logger-sdk/v1.0.1/build.log'));

console.log('✅ Test 3 Passed: JitPackClient resolution verified.');

// ============================================================================
// TEST 4: Ecosystem Repository Discovery & Candidate Evaluation
// ============================================================================
console.log('▶ Test 4: EcosystemScanner Android Library Detection & Heuristics');

// 4.1 Android library repo with keywords and topic
const repo1 = {
  name: 'native-logger-sdk',
  language: 'Java',
  description: 'Android logging library SDK for NativeRuntime ecosystem',
  topics: ['android-library', 'jitpack']
};
const eval1 = EcosystemScanner.evaluateAndroidLibrary(repo1);
assert.strictEqual(eval1.isLibrary, true);
assert.strictEqual(eval1.confidence, 'high');

// 4.2 Kotlin Android library
const repo2 = {
  name: 'camera-view-aar',
  language: 'Kotlin',
  description: 'Custom Camera View module',
  topics: ['android']
};
const eval2 = EcosystemScanner.evaluateAndroidLibrary(repo2);
assert.strictEqual(eval2.isLibrary, true);

// 4.3 General web repository (not an Android library)
const repo3 = {
  name: 'my-react-website',
  language: 'JavaScript',
  description: 'Personal homepage portfolio',
  topics: ['react', 'web']
};
const eval3 = EcosystemScanner.evaluateAndroidLibrary(repo3);
assert.strictEqual(eval3.isLibrary, false);
assert.strictEqual(eval3.confidence, 'low');

console.log('✅ Test 4 Passed: EcosystemScanner heuristics verified.');

// ============================================================================
// TEST 5: Ecosystem Dependency Graph, Conflict & Cycle Detection
// ============================================================================
console.log('▶ Test 5: EcosystemDependencyGraph Matrix, Outdated Detection & Cycle Guard');

const sampleLibraries = [
  {
    id: 'owner__lib-a',
    name: 'Lib A',
    owner: 'owner',
    repo: 'lib-a',
    groupId: 'com.github.owner',
    artifactId: 'lib-a',
    projectVersion: '2.0.0',
    dependencies: [
      { groupId: 'com.github.owner', artifactId: 'lib-b', name: 'lib-b', version: '1.0.0', scope: 'implementation' } // Outdated! Lib B is at 1.5.0
    ]
  },
  {
    id: 'owner__lib-b',
    name: 'Lib B',
    owner: 'owner',
    repo: 'lib-b',
    groupId: 'com.github.owner',
    artifactId: 'lib-b',
    projectVersion: '1.5.0',
    dependencies: [
      { groupId: 'com.github.owner', artifactId: 'lib-c', name: 'lib-c', version: '1.0.0', scope: 'api' }
    ]
  },
  {
    id: 'owner__lib-c',
    name: 'Lib C',
    owner: 'owner',
    repo: 'lib-c',
    groupId: 'com.github.owner',
    artifactId: 'lib-c',
    projectVersion: '1.0.0',
    dependencies: [
      { groupId: 'com.github.owner', artifactId: 'lib-a', name: 'lib-a', version: '1.0.0', scope: 'implementation' } // Cyclic! C -> A -> B -> C
    ]
  },
  {
    id: 'owner__lib-d',
    name: 'Lib D',
    owner: 'owner',
    repo: 'lib-d',
    groupId: 'com.github.owner',
    artifactId: 'lib-d',
    projectVersion: '1.0.0',
    dependencies: [
      { groupId: 'com.github.owner', artifactId: 'lib-b', name: 'lib-b', version: '1.2.0', scope: 'implementation' } // Conflict: Lib A uses Lib B 1.0.0, Lib D uses 1.2.0
    ]
  }
];

const analysis = EcosystemDependencyGraph.analyze(sampleLibraries);

// 5.1 Outdated dependency detected
assert.strictEqual(analysis.outdatedCount >= 1, true, 'Must detect outdated Lib B in Lib A');
const outdatedItem = analysis.outdatedList.find(o => o.targetLib === 'Lib B' && o.sourceLib === 'Lib A');
assert(outdatedItem, 'Outdated item must be Lib B referenced by Lib A');
assert.strictEqual(outdatedItem.declaredVersion, '1.0.0');
assert.strictEqual(outdatedItem.latestVersion, '1.5.0');

// 5.2 Version conflict detected
assert.strictEqual(analysis.conflictCount >= 1, true, 'Must detect version conflict for Lib B');
const conflictItem = analysis.conflictList.find(c => c.targetId === 'owner__lib-b');
assert(conflictItem, 'Conflict item must be Lib B');
assert(conflictItem.conflictingVersions.includes('1.0.0'));
assert(conflictItem.conflictingVersions.includes('1.2.0'));

// 5.3 Cycle detected
assert.strictEqual(analysis.cycleCount >= 1, true, 'Must detect circular dependency A -> B -> C -> A');

// 5.4 SVG Graph rendering
const svg = EcosystemDependencyGraph.renderSvgGraph(analysis);
assert(svg.includes('<svg'), 'Should render SVG tag');
assert(svg.includes('marker id="arrow-error"'), 'Should render error arrow marker');
assert(svg.includes('Lib A'), 'Should include node labels');

console.log('✅ Test 5 Passed: EcosystemDependencyGraph verified.');

// ============================================================================
// TEST 6: Storage Schema Migration & Data Preservation
// ============================================================================
console.log('▶ Test 6: Storage Migration & Repository Service Persistence');

await storage.init();

// Verify new object stores exist in memory store
const libStore = await storage.getAll('ecosystem_libraries');
assert(Array.isArray(libStore), 'ecosystem_libraries store must exist');

const groupStore = await storage.getAll('ecosystem_groups');
assert(Array.isArray(groupStore), 'ecosystem_groups store must exist');

const cacheStore = await storage.getAll('ecosystem_cache');
assert(Array.isArray(cacheStore), 'ecosystem_cache store must exist');

// Test EcosystemRepositoryService CRUD
const repoService = new EcosystemRepositoryService();
await repoService.init();

const defaultLibs = await repoService.list();
assert(defaultLibs.length >= 3, 'Default libraries must be initialized');

// Test registering a new repository
const newLib = await repoService.register({
  owner: 'test-org',
  repo: 'test-sdk',
  name: 'Test SDK',
  groupId: 'com.github.test-org',
  artifactId: 'test-sdk',
  projectVersion: '1.0.0',
  ecosystemGroup: 'Utilities'
});
assert.strictEqual(newLib.id, 'test-org__test-sdk');

// Verify duplicate prevention
await assert.rejects(
  async () => {
    await repoService.register({
      owner: 'test-org',
      repo: 'test-sdk'
    });
  },
  /already registered/i
);

// Verify update
await repoService.update(newLib.id, { projectVersion: '1.0.1' });
const updated = await repoService.get(newLib.id);
assert.strictEqual(updated.projectVersion, '1.0.1');

// Verify removal
await repoService.remove(newLib.id);
const removed = await repoService.get(newLib.id);
assert.strictEqual(removed, null);

console.log('✅ Test 6 Passed: Storage migration and repository service persistence verified.');

console.log('\n🎉 ALL 6 ECOSYSTEM VERSION MANAGER TESTS PASSED 100%!');
