/**
 * EnvironmentCompatibilityMatrix.js
 *
 * Authoritative compatibility matrices and presets for:
 * - Java Library
 * - Android Library
 * - Android Application
 *
 * Covers:
 * - JDK versions (8, 11, 17, 21)
 * - Gradle Wrapper versions (7.x, 8.x)
 * - Android Gradle Plugin (AGP) versions (7.x, 8.x)
 * - Android compileSdk (30–35), minSdk (21–26), targetSdk (30–35)
 * - Plugin types & publishing requirements
 */

export const PROJECT_TYPES = {
  JAVA_LIBRARY: {
    id: 'java-library',
    name: 'Java Library',
    icon: 'code_blocks',
    description: 'Pure JVM library published to JitPack / Maven Central. No Android SDK dependency.',
    isAndroid: false,
    isLibrary: true,
    canPublishJitPack: true,
    defaultPlugin: 'java-library',
  },
  ANDROID_LIBRARY: {
    id: 'android-library',
    name: 'Android Library (AAR)',
    icon: 'library_books',
    description: 'Android Archive (AAR) library with Android resources and APIs, published to JitPack.',
    isAndroid: true,
    isLibrary: true,
    canPublishJitPack: true,
    defaultPlugin: 'com.android.library',
  },
  ANDROID_APP: {
    id: 'android-app',
    name: 'Android Application (APK/AAB)',
    icon: 'smartphone',
    description: 'Standalone Android Application. Must NOT be published as a library dependency on JitPack.',
    isAndroid: true,
    isLibrary: false,
    canPublishJitPack: false,
    defaultPlugin: 'com.android.application',
  },
};

/**
 * AGP to minimum Gradle and minimum JDK mapping rules.
 */
export const AGP_COMPATIBILITY_TABLE = [
  { agpMin: '8.10.0', agpMax: '8.10.99', minGradle: '8.11', recommendedGradle: '8.13', minJdk: '17', recommendedJdk: '21', compileSdkMin: 34, compileSdkMax: 36 },
  { agpMin: '8.8.0',  agpMax: '8.9.99',  minGradle: '8.10', recommendedGradle: '8.11', minJdk: '17', recommendedJdk: '21', compileSdkMin: 34, compileSdkMax: 35 },
  { agpMin: '8.7.0',  agpMax: '8.7.99',  minGradle: '8.9',  recommendedGradle: '8.9',  minJdk: '17', recommendedJdk: '21', compileSdkMin: 34, compileSdkMax: 35 },
  { agpMin: '8.5.0',  agpMax: '8.6.99',  minGradle: '8.7',  recommendedGradle: '8.7',  minJdk: '17', recommendedJdk: '17', compileSdkMin: 34, compileSdkMax: 35 },
  { agpMin: '8.4.0',  agpMax: '8.4.99',  minGradle: '8.6',  recommendedGradle: '8.7',  minJdk: '17', recommendedJdk: '17', compileSdkMin: 33, compileSdkMax: 34 },
  { agpMin: '8.2.0',  agpMax: '8.3.99',  minGradle: '8.2',  recommendedGradle: '8.4',  minJdk: '17', recommendedJdk: '17', compileSdkMin: 33, compileSdkMax: 34 },
  { agpMin: '8.1.0',  agpMax: '8.1.99',  minGradle: '8.0',  recommendedGradle: '8.2.1',minJdk: '17', recommendedJdk: '17', compileSdkMin: 33, compileSdkMax: 34 },
  { agpMin: '8.0.0',  agpMax: '8.0.99',  minGradle: '8.0',  recommendedGradle: '8.0.2',minJdk: '17', recommendedJdk: '17', compileSdkMin: 33, compileSdkMax: 33 },
  { agpMin: '7.4.0',  agpMax: '7.4.99',  minGradle: '7.5',  recommendedGradle: '7.6.4',minJdk: '11', recommendedJdk: '17', compileSdkMin: 31, compileSdkMax: 33 },
  { agpMin: '7.2.0',  agpMax: '7.3.99',  minGradle: '7.3.3',recommendedGradle: '7.4.2',minJdk: '11', recommendedJdk: '11', compileSdkMin: 31, compileSdkMax: 33 },
];

/**
 * Gradle version to supported JDK range.
 */
export const GRADLE_JDK_TABLE = [
  { gradleMin: '8.13', maxJdk: 23, minJdk: 8, recommendedJdk: '21' },
  { gradleMin: '8.9', maxJdk: 23, minJdk: 8, recommendedJdk: '21' },
  { gradleMin: '8.5', maxJdk: 21, minJdk: 8, recommendedJdk: '17' },
  { gradleMin: '8.0', maxJdk: 19, minJdk: 8, recommendedJdk: '17' },
  { gradleMin: '7.6', maxJdk: 17, minJdk: 8, recommendedJdk: '17' },
  { gradleMin: '7.3', maxJdk: 17, minJdk: 8, recommendedJdk: '11' },
];

/**
 * compileSdk requirements.
 */
export const SDK_REQUIREMENTS = {
  36: { minAgp: '8.9.0', recommendedAgp: '8.10.1', minJdk: '17', recommendedGradle: '8.13' },
  35: { minAgp: '8.5.0', recommendedAgp: '8.7.0', minJdk: '17', recommendedGradle: '8.9' },
  34: { minAgp: '8.1.1', recommendedAgp: '8.4.2', minJdk: '17', recommendedGradle: '8.7' },
  33: { minAgp: '7.4.0', recommendedAgp: '8.2.2', minJdk: '11', recommendedGradle: '8.2.1' },
  31: { minAgp: '7.1.0', recommendedAgp: '7.4.2', minJdk: '11', recommendedGradle: '7.6.4' },
};

/**
 * Recommended Presets.
 */
export const ENVIRONMENT_PRESETS = {
  'android-lib-agp810': {
    id: 'android-lib-agp810',
    name: 'Android Library AGP 8.10.1 / Gradle 8.13 (Modern)',
    projectType: 'android-library',
    jdkVersion: '17',
    gradleVersion: '8.13',
    agpVersion: '8.10.1',
    compileSdk: 35,
    minSdk: 24,
    targetSdk: 35,
    description: 'Cutting-edge Android library with AGP 8.10.1, Gradle 8.13, Java 17 LTS, and JitPack AAR publication.',
  },
  'android-lib-lts': {
    id: 'android-lib-lts',
    name: 'Android Library LTS (Recommended)',
    projectType: 'android-library',
    jdkVersion: '17',
    gradleVersion: '8.7',
    agpVersion: '8.4.2',
    compileSdk: 34,
    minSdk: 21,
    targetSdk: 34,
    description: 'Most stable & widely compatible setup for Android libraries published to JitPack with JDK 17.',
  },
  'android-lib-latest': {
    id: 'android-lib-latest',
    name: 'Android Library Latest (Android 15 / SDK 35)',
    projectType: 'android-library',
    jdkVersion: '21',
    gradleVersion: '8.9',
    agpVersion: '8.7.0',
    compileSdk: 35,
    minSdk: 24,
    targetSdk: 35,
    description: 'Latest Android 15 SDK 35 support with AGP 8.7.0 and JDK 21.',
  },
  'android-app-standard': {
    id: 'android-app-standard',
    name: 'Android Application Standard',
    projectType: 'android-app',
    jdkVersion: '17',
    gradleVersion: '8.7',
    agpVersion: '8.4.2',
    compileSdk: 34,
    minSdk: 24,
    targetSdk: 34,
    description: 'Standard modern Android Application configuration with compileSdk 34 and JDK 17.',
  },
  'java-lib-modern': {
    id: 'java-lib-modern',
    name: 'Java Library Modern (JDK 17 LTS)',
    projectType: 'java-library',
    jdkVersion: '17',
    gradleVersion: '8.7',
    agpVersion: null,
    compileSdk: null,
    minSdk: null,
    targetSdk: null,
    description: 'Pure Java Library with Gradle 8.7, JDK 17 LTS, and maven-publish plugin.',
  },
  'java-lib-legacy': {
    id: 'java-lib-legacy',
    name: 'Java Library Multi-Release (Java 8/11/17)',
    projectType: 'java-library',
    jdkVersion: '11',
    gradleVersion: '7.6.4',
    agpVersion: null,
    compileSdk: null,
    minSdk: null,
    targetSdk: null,
    description: 'Java 8/11 compatible library for maximum legacy ecosystem support.',
  },
};

/**
 * Version comparison utility: returns > 0 if v1 > v2, < 0 if v1 < v2, 0 if equal.
 */
export function compareSemver(v1, v2) {
  if (!v1 && !v2) return 0;
  if (!v1) return -1;
  if (!v2) return 1;

  const clean = v => String(v).replace(/^v/i, '').split('-')[0].split('.').map(n => parseInt(n, 10) || 0);
  const p1 = clean(v1);
  const p2 = clean(v2);
  const len = Math.max(p1.length, p2.length);

  for (let i = 0; i < len; i++) {
    const num1 = p1[i] || 0;
    const num2 = p2[i] || 0;
    if (num1 > num2) return 1;
    if (num1 < num2) return -1;
  }
  return 0;
}
