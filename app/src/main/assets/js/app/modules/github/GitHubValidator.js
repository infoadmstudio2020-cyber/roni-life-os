/**
 * GitHubValidator — validates project structure, GitHub config, and publishing readiness.
 */
import { projectService } from '../storage/ProjectService.js';
import { githubAuth }     from './GitHubAuth.js';
import { githubSettings } from './GitHubSettings.js';

export class GitHubValidator {

  /**
   * Run all validations and return a report.
   * @returns {{errors:string[], warnings:string[], passed:boolean}}
   */
  validate(explicitProj = null) {
    const errors   = [];
    const warnings = [];
    const proj     = explicitProj || projectService.current;
    const settings = githubSettings.get();

    // ── Authentication ───────────────────────────────────────────────────────
    if (!explicitProj && !githubAuth.isAuthenticated) {
      errors.push('Not authenticated — add a GitHub Personal Access Token first');
    }

    // ── Project ──────────────────────────────────────────────────────────────
    if (!proj) {
      errors.push('No project loaded — generate or open a project first');
      return { errors, warnings, passed: false };
    }

    const files = proj.files || {};
    const paths = Object.keys(files);

    if (!paths.length) {
      errors.push('Project has no files — run the Generator first');
    }

    // ── Repository config ────────────────────────────────────────────────────
    const owner = settings.defaultUsername || githubAuth.user?.login || proj.author;
    const repo  = settings.activeRepo?.name || proj.repoName;

    if (!explicitProj && !owner) errors.push('No GitHub username configured — set it in GitHub Settings');
    if (!repo)  errors.push('No repository name configured — check Project or GitHub Settings');
    if (repo && !/^[a-zA-Z0-9._-]+$/.test(repo)) {
      errors.push(`Repository name "${repo}" contains invalid characters`);
    }

    // ── Version ──────────────────────────────────────────────────────────────
    const version = proj.version || '1.0.0';
    if (!/^\d+\.\d+\.\d+/.test(version)) {
      errors.push(`Version "${version}" is not valid semver (expected x.y.z)`);
    }

    // ── Package name ─────────────────────────────────────────────────────────
    if (proj.packageName && !/^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$/.test(proj.packageName)) {
      warnings.push(`Package name "${proj.packageName}" may not be JitPack-compatible`);
    }

    // ── Key files presence ───────────────────────────────────────────────────
    if (!paths.some(p => p.toLowerCase() === 'readme.md')) {
      warnings.push('README.md is missing — generate GitHub files to add it');
    }
    if (!paths.some(p => p === 'LICENSE')) {
      warnings.push('LICENSE file is missing');
    }
    if (!paths.some(p => p === '.gitignore')) {
      warnings.push('.gitignore is missing');
    }

    // ── Gradle ───────────────────────────────────────────────────────────────
    const hasRootGradle = paths.some(p => p === 'build.gradle' || p === 'build.gradle.kts');
    if (!hasRootGradle) {
      warnings.push('Root build.gradle not found — publish may fail on JitPack');
    }
    const hasSettings = paths.some(p => p === 'settings.gradle' || p === 'settings.gradle.kts');
    if (!hasSettings) {
      warnings.push('settings.gradle not found');
    }

    // ── Duplicate files ──────────────────────────────────────────────────────
    const normalized = paths.map(p => p.toLowerCase());
    const dupes = normalized.filter((v, i) => normalized.indexOf(v) !== i);
    dupes.forEach(d => warnings.push(`Duplicate file path (case-insensitive): ${d}`));

    // ── JitPack yml ──────────────────────────────────────────────────────────
    const jpPath = paths.find(p => p === 'jitpack.yml');
    if (!jpPath) {
      warnings.push('jitpack.yml not found — JitPack may use default build');
    } else {
      const jpContent = proj?.files?.[jpPath] || '';
      const isLibrary = proj?.projectType !== 'app' && paths.some(p => p.includes('build.gradle') && !p.startsWith('app/'));
      if (isLibrary && !jpContent.includes('publishToMavenLocal') && !jpContent.includes('publishReleasePublicationToMavenLocal')) {
        warnings.push('jitpack.yml should include publishToMavenLocal so JitPack generates and detects pom.xml/AAR artifacts');
      }
    }

    return { errors, warnings, passed: errors.length === 0 };
  }
}

export const githubValidator = new GitHubValidator();
