/**
 * Environment Module Entry Point
 * Exports Gradle, AGP, SDK environment managers and compatibility advisors.
 */

export { EnvironmentManager } from './EnvironmentManager.js';
export {
  PROJECT_TYPES,
  AGP_COMPATIBILITY_TABLE,
  GRADLE_JDK_TABLE,
  SDK_REQUIREMENTS,
  ENVIRONMENT_PRESETS,
  compareSemver
} from './EnvironmentCompatibilityMatrix.js';
export { DependencyCompatibilityAdvisor, DEPENDENCY_KNOWLEDGE_BASE } from './DependencyCompatibilityAdvisor.js';
export { PublishValidationWizard } from './PublishValidationWizard.js';
