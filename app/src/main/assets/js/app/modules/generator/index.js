/**
 * Generator Module Entry Point
 * Exports all Android library/app generators, AST patchers, JitPack engines, and templates.
 */

export { GeneratorModule } from './GeneratorModule.js';
export { JitPackAnalyzerEngine } from './JitPackAnalyzerEngine.js';
export { JitPackBuildService } from './JitPackBuildService.js';
export { AIProjectSpecGenerator } from './AIProjectSpecGenerator.js';
export { GradleAstHelper } from './GradleAstHelper.js';
export { GradlePatcher } from './GradlePatcher.js';
export { GRADLE_WRAPPER_JAR_B64, decodeWrapperJar } from './GradleWrapperAssets.js';
export { ManifestManager } from './ManifestManager.js';
export { MavenPublishGenerator } from './MavenPublishGenerator.js';
export * as templates from './templates/index.js';
