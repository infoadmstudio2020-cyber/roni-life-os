import assert from 'node:assert';
import { BinaryClassInspector } from '../js/app/modules/conflict/BinaryClassInspector.js';
import { DependencyGraphResolver } from '../js/app/modules/conflict/DependencyGraphResolver.js';
import { KotlinRuntimeAlignmentService } from '../js/app/modules/conflict/KotlinRuntimeAlignmentService.js';
import { D8PackagingGuard } from '../js/app/modules/conflict/D8PackagingGuard.js';
import { PreValidatorService } from '../js/app/modules/build/PreValidatorService.js';

console.log('🧪 Starting Duplicate Class, Dependency Resolution & D8 Packaging Guard Test Suite...\n');

// 1. BinaryClassInspector Tests
console.log('▶ Test 1: BinaryClassInspector detects classes in classes.jar, embedded JARs & identifies collisions');
const archives = [
  {
    id: 'aar-alpha',
    name: 'alpha-release.aar',
    type: 'aar',
    classes: [
      'com.example.common.Utils',
      'com.example.alpha.AlphaService',
      'com.shared.DuplicateClass'
    ],
    embeddedJars: [
      {
        name: 'embedded-helper.jar',
        classes: [
          'com.example.internal.Helper',
          'com.shared.InternalConflict'
        ]
      }
    ]
  },
  {
    id: 'jar-beta',
    name: 'beta-lib.jar',
    type: 'jar',
    classes: [
      'com.example.beta.BetaService',
      'com.shared.DuplicateClass', // Duplicate with aar-alpha!
      'com.shared.InternalConflict' // Duplicate with embedded jar!
    ]
  }
];

const report = BinaryClassInspector.inspectArchives(archives);
assert.strictEqual(report.totalArchives, 2, 'Inspected 2 archives');
assert.strictEqual(report.hasConflicts, true, 'Conflicts detected');
assert.strictEqual(report.totalDuplicatesCount, 2, 'Detected exactly 2 duplicate classes');

const dupNames = report.duplicates.map(d => d.className);
assert.ok(dupNames.includes('com.shared.DuplicateClass'), 'Detected com.shared.DuplicateClass duplicate');
assert.ok(dupNames.includes('com.shared.InternalConflict'), 'Detected com.shared.InternalConflict duplicate from embedded jar');

// Path normalization
assert.strictEqual(BinaryClassInspector.pathToClassName('com/test/Sample.class'), 'com.test.Sample');
assert.strictEqual(BinaryClassInspector.pathToClassName('classes.jar!com/test/Nested.class'), 'com.test.Nested');
assert.strictEqual(BinaryClassInspector.pathToClassName('META-INF/MANIFEST.MF'), null);
console.log('✅ Test 1 Passed: BinaryClassInspector verified.\n');

// 2. DependencyGraphResolver Tests
console.log('▶ Test 2: DependencyGraphResolver parses Maven coordinates and eliminates duplicate artifacts');
const rawDeps = [
  "implementation 'com.google.guava:guava:31.0-android'",
  "api 'com.google.guava:guava:32.1.2-jre'", // Duplicate artifact with different version
  "implementation 'com.squareup.okhttp3:okhttp:4.9.0'",
  "implementation 'com.squareup.okhttp3:okhttp:4.9.0'", // Exact duplicate
  "implementation 'androidx.annotation:annotation:1.7.0'"
];

const resolved = DependencyGraphResolver.resolveDependencies(rawDeps, { strategy: 'HIGHEST_VERSION' });
assert.strictEqual(resolved.inputCount, 5, '5 input dependencies parsed');
assert.strictEqual(resolved.resolvedCount, 3, '3 unique artifacts resolved');
assert.strictEqual(resolved.duplicatesRemovedCount, 2, '2 duplicate declarations eliminated');
assert.strictEqual(resolved.conflictsCount, 1, '1 version conflict detected on guava');

const guavaResolved = resolved.resolved.find(d => d.moduleKey === 'com.google.guava:guava');
assert.strictEqual(guavaResolved.version, '32.1.2-jre', 'Highest guava version chosen');
assert.ok(resolved.gradleResolutionScript.includes("force 'com.google.guava:guava:32.1.2-jre'"), 'resolutionStrategy includes forced winning version');
console.log('✅ Test 2 Passed: DependencyGraphResolver verified.\n');

// 3. KotlinRuntimeAlignmentService Tests
console.log('▶ Test 3: KotlinRuntimeAlignmentService detects Kotlin 1.8+ merged stdlib collision hazard');
const mixedKotlinDeps = [
  "implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.22'",
  "implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20'", // Hazard: older unmerged jdk7
  "implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20'"  // Hazard: older unmerged jdk8
];

const kotlinAudit = KotlinRuntimeAlignmentService.auditKotlinRuntime(mixedKotlinDeps, '1.9.22');
assert.strictEqual(kotlinAudit.hasMultipleVersions, true, 'Multiple kotlin stdlib versions detected');
assert.strictEqual(kotlinAudit.hasMergedCollisionRisk, true, 'Kotlin 1.8+ merged collision risk flagged');
assert.ok(kotlinAudit.collisionExplanation.includes('contains merged classes'), 'Explanation accurately describes Kotlin 1.8+ merge');
assert.ok(kotlinAudit.gradleAlignmentPatch.includes("details.useVersion('1.9.22')"), 'Alignment patch aligns to target 1.9.22');
assert.ok(kotlinAudit.gradleConstraintsPatch.includes('constraints {'), 'Constraints patch generated');
console.log('✅ Test 3 Passed: KotlinRuntimeAlignmentService verified.\n');

// 4. D8PackagingGuard Tests
console.log('▶ Test 4: D8PackagingGuard blocks duplicate classes from reaching D8 and sanitizes payload');
const classEntries = [
  { className: 'com.app.MainActivity', archiveName: 'app.jar' },
  { className: 'com.shared.CommonUtil', archiveName: 'lib-a.jar' },
  { className: 'com.shared.CommonUtil', archiveName: 'lib-b.jar' }, // Duplicate class!
  { className: 'kotlin.collections.jdk8.CollectionsJDK8Kt', archiveName: 'kotlin-stdlib.jar' },
  { className: 'kotlin.collections.jdk8.CollectionsJDK8Kt', archiveName: 'kotlin-stdlib-jdk8.jar' } // Duplicate class!
];

// Pre-flight check MUST block unhandled duplicates
const preflight = D8PackagingGuard.preflightCheck(classEntries);
assert.strictEqual(preflight.status, 'BLOCKED', 'D8 pre-flight check BLOCKED due to duplicate classes');
assert.strictEqual(preflight.isSafeForD8, false, 'Payload is marked unsafe for D8');
assert.strictEqual(preflight.duplicateClassesCount, 2, '2 duplicate class collisions detected');

// Sanitization MUST filter duplicates and produce certified safe D8 payload
const sanitized = D8PackagingGuard.sanitizeForD8(classEntries, { strategy: 'PICK_FIRST' });
assert.strictEqual(sanitized.status, 'PASSED', 'Sanitized payload status is PASSED');
assert.strictEqual(sanitized.certifiedSafeForD8, true, 'Certified 100% safe for D8');
assert.strictEqual(sanitized.sanitizedCount, 3, '3 unique classes preserved');
assert.strictEqual(sanitized.droppedCount, 2, '2 duplicate classes cleanly dropped');
assert.ok(sanitized.packagingOptionsGradle.includes('pickFirsts'), 'packagingOptions Gradle block generated');

// Re-running preflight on sanitizedClasses must pass
const sanitizedPreflight = D8PackagingGuard.preflightCheck(sanitized.sanitizedClasses);
assert.strictEqual(sanitizedPreflight.status, 'PASSED', 'Sanitized classes pass preflight with zero collisions');
console.log('✅ Test 4 Passed: D8PackagingGuard verified.\n');

// 5. PreValidatorService End-to-End Validation
console.log('▶ Test 5: PreValidatorService integrates dependency and packaging safety audit');
const mockFiles = {
  'build.gradle': `
    plugins { id 'com.android.library' }
    dependencies {
        implementation 'com.google.guava:guava:30.0'
        implementation 'com.google.guava:guava:31.1-android'
        implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.0'
        implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.0'
    }
  `
};

const preValReport = PreValidatorService.validateDependenciesAndPackaging(mockFiles);
assert.strictEqual(preValReport.dependencyReport.conflictsCount, 1, 'Dependency conflict flagged on guava');
assert.strictEqual(preValReport.kotlinReport.hasMergedCollisionRisk, true, 'Kotlin merge hazard flagged in validator');
assert.ok(preValReport.warnings.length >= 2, 'Warnings generated for user visibility');
console.log('✅ Test 5 Passed: PreValidatorService integration verified.\n');

console.log('🎉 ALL DUPLICATE CLASS & D8 PACKAGING SAFETY TESTS PASSED 100%!');
