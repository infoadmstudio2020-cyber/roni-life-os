/**
 * GitHubActionsWizardTracker.js
 * 
 * Engine for GitHub Actions Build & Release Wizard.
 * Fetches actual GitHub Actions workflow runs, jobs, steps, artifacts, and releases via GitHub API,
 * and maps them into the exact 7-stage pipeline:
 * Environment → Dependency → Build → Test → Verification → Artifact → Release
 * 
 * Supported Statuses:
 * - PASS (🟢)
 * - FAILED (🔴)
 * - RUNNING (🟡)
 * - WAITING (🔵)
 * - SKIPPED (⏭)
 * 
 * Artifact Handling:
 * - Android Library: AAR Generate
 * - Android App: APK/AAB Generate
 * 
 * Cascade Rule:
 * When any stage fails, all dependent subsequent stages become SKIPPED,
 * and error/log viewing options are prepared for the failed step.
 */

import { githubApi } from './GitHubApi.js';

export const WIZARD_STATUS = Object.freeze({
  PASS:    'PASS',
  FAILED:  'FAILED',
  RUNNING: 'RUNNING',
  WAITING: 'WAITING',
  SKIPPED: 'SKIPPED'
});

export const WIZARD_STATUS_META = Object.freeze({
  [WIZARD_STATUS.PASS]: {
    label: 'PASS',
    emoji: '🟢',
    badgeClass: 'ghw-status-pass',
    icon: 'check_circle',
    color: '#10B981',
    description: 'Stage completed successfully'
  },
  [WIZARD_STATUS.FAILED]: {
    label: 'FAILED',
    emoji: '🔴',
    badgeClass: 'ghw-status-failed',
    icon: 'error',
    color: '#EF4444',
    description: 'Stage encountered failure or errors'
  },
  [WIZARD_STATUS.RUNNING]: {
    label: 'RUNNING',
    emoji: '🟡',
    badgeClass: 'ghw-status-running',
    icon: 'sync',
    color: '#F59E0B',
    description: 'Stage currently executing on runner'
  },
  [WIZARD_STATUS.WAITING]: {
    label: 'WAITING',
    emoji: '🔵',
    badgeClass: 'ghw-status-waiting',
    icon: 'hourglass_empty',
    color: '#3B82F6',
    description: 'Stage queued or awaiting preceding steps'
  },
  [WIZARD_STATUS.SKIPPED]: {
    label: 'SKIPPED',
    emoji: '⏭',
    badgeClass: 'ghw-status-skipped',
    icon: 'fast_forward',
    color: '#94A3B8',
    description: 'Stage skipped due to failure or condition'
  }
});

export const STAGE_KEYS = [
  'Environment',
  'Dependency',
  'Build',
  'Test',
  'Verification',
  'Artifact Generation',
  'Release'
];

export class GitHubActionsWizardTracker {
  /**
   * List workflows available for a repository.
   */
  static async getWorkflows(owner, repo) {
    try {
      const res = await githubApi.listWorkflows(owner, repo);
      return res.workflows || [];
    } catch (err) {
      console.warn('Failed to list workflows:', err);
      return [];
    }
  }

  /**
   * List workflow runs with optional filters (workflowId, branch, status).
   */
  static async getWorkflowRuns(owner, repo, options = {}) {
    try {
      const res = await githubApi.listWorkflowRuns(owner, repo, {
        per_page: options.per_page || 15,
        branch: options.branch,
        event: options.event
      });
      let runs = res.workflow_runs || [];
      if (options.workflowId) {
        runs = runs.filter(r => String(r.workflow_id) === String(options.workflowId));
      }
      return runs;
    } catch (err) {
      console.warn('Failed to list workflow runs:', err);
      return [];
    }
  }

  /**
   * Fetch complete run execution details: Run object, Jobs list, and Artifacts.
   */
  static async getRunDetails(owner, repo, runId) {
    if (!owner || !repo || !runId) {
      throw new Error('Owner, repo, and runId are required');
    }

    const [run, jobsData, artifactsData] = await Promise.all([
      githubApi.getWorkflowRun(owner, repo, runId).catch(err => ({ error: err.message })),
      githubApi.listWorkflowJobs(owner, repo, runId).catch(err => ({ jobs: [], error: err.message })),
      githubApi.listWorkflowRunArtifacts(owner, repo, runId).catch(() => ({ artifacts: [] }))
    ]);

    let releasesData = [];
    if (run && (run.conclusion === 'success' || run.status === 'completed')) {
      try {
        releasesData = await githubApi.getReleases(owner, repo).catch(() => []);
      } catch {
        releasesData = [];
      }
    }

    return {
      run,
      jobs: jobsData.jobs || [],
      artifacts: artifactsData.artifacts || [],
      releases: releasesData || []
    };
  }

  /**
   * Trigger a new GitHub Actions workflow dispatch.
   */
  static async triggerWorkflow(owner, repo, workflowId, ref = 'main', inputs = {}) {
    return await githubApi.dispatchWorkflow(owner, repo, workflowId, ref, inputs);
  }

  /**
   * Fetch raw logs for a specific job.
   */
  static async getJobLogs(owner, repo, jobId) {
    return await githubApi.getJobLogs(owner, repo, jobId);
  }

  /**
   * Maps actual GitHub Actions execution data to the exact 7 Wizard stages:
   * Environment → Dependency → Build → Test → Verification → Artifact → Release
   * 
   * @param {Object} run GitHub Workflow Run object
   * @param {Array} jobs List of GitHub Actions Jobs
   * @param {String} projectType 'library' | 'app'
   * @param {Array} artifacts List of GitHub Actions Artifacts
   * @param {Array} releases List of GitHub Releases
   * @returns {Object} Structured pipeline mapping with stage results and summary
   */
  static mapToWizardStages(run, jobs = [], projectType = 'library', artifacts = [], releases = []) {
    const isApp = projectType === 'app';
    const isLibrary = !isApp;

    // Collect all steps flattened across jobs (primary job first)
    const allSteps = [];
    const mainJob = jobs[0] || null;

    jobs.forEach(job => {
      (job.steps || []).forEach(step => {
        allSteps.push({
          ...step,
          jobId: job.id,
          jobName: job.name,
          jobStatus: job.status,
          jobConclusion: job.conclusion
        });
      });
    });

    // Helper: find matching steps by keyword patterns
    const matchSteps = (keywords) => {
      return allSteps.filter(s => {
        const name = (s.name || '').toLowerCase();
        return keywords.some(kw => name.includes(kw));
      });
    };

    // 1. Group steps into the 7 stages based on real runner steps
    const stageDefinitions = [
      {
        key: 'Environment',
        label: 'Environment',
        icon: 'terminal',
        keywords: ['checkout', 'setup java', 'setup jdk', 'setup android', 'android sdk', 'chmod', 'check environment', 'grant execute', 'set up'],
        defaultDesc: 'Set up JDK, Android SDK & runner environment'
      },
      {
        key: 'Dependency',
        label: 'Dependency',
        icon: 'account_tree',
        keywords: ['cache', 'gradle packages', 'dependency', 'dependencies', 'resolve', 'download', 'gradle-wrapper'],
        defaultDesc: 'Resolve & cache Gradle dependencies & wrapper'
      },
      {
        key: 'Build',
        label: 'Build',
        icon: 'construction',
        keywords: ['build', 'compile', 'assemble', 'gradlew build', 'assembledebug', 'assemblerelease', 'bundlerelease', 'build release'],
        defaultDesc: isLibrary ? 'Compile source & build Android Library' : 'Compile source & build Android Application'
      },
      {
        key: 'Test',
        label: 'Test',
        icon: 'checklist',
        keywords: ['test', 'unit test', 'check', 'instrumented', 'coverage', 'junit', 'testdebugunittest'],
        defaultDesc: 'Execute unit tests & code quality checks'
      },
      {
        key: 'Verification',
        label: 'Verification',
        icon: 'verified',
        keywords: ['verif', 'lint', 'validate', 'analyze', 'detekt', 'ktlint', 'sanity', 'verify generated artifacts'],
        defaultDesc: 'Verify build integrity, manifest & code compliance'
      },
      {
        key: 'Artifact Generation',
        label: isLibrary ? 'Artifact Generation (.aar)' : 'Artifact Generation (.apk / .aab)',
        artifactType: isLibrary ? 'AAR' : 'APK/AAB',
        icon: 'inventory_2',
        keywords: ['artifact generation', 'artifact', 'aar', 'apk', 'aab', 'upload-artifact', 'distribution', 'output', 'package'],
        defaultDesc: isLibrary ? 'Package and generate release AAR library (.aar)' : 'Package and generate release APK / AAB binaries'
      },
      {
        key: 'Release',
        label: 'Release',
        icon: 'rocket_launch',
        keywords: ['release', 'publish', 'gh-release', 'softprops', 'tag', 'distribute', 'deploy', 'upload release asset', 'packages', 'maven'],
        defaultDesc: 'Attach generated artifacts to GitHub Release (e.g. v1.0.3)'
      }
    ];

    // Evaluate preliminary raw status for each stage based on actual GitHub steps
    const rawStages = stageDefinitions.map((def, idx) => {
      const matched = matchSteps(def.keywords);
      let status = WIZARD_STATUS.WAITING;
      let details = def.defaultDesc;
      let matchedStep = null;
      let errorStep = null;

      if (matched.length > 0) {
        // Look for any failure first
        const failed = matched.find(s => s.conclusion === 'failure' || s.conclusion === 'timed_out');
        const running = matched.find(s => s.status === 'in_progress');
        const queued = matched.find(s => s.status === 'queued');
        const skipped = matched.every(s => s.conclusion === 'skipped');
        const success = matched.every(s => s.conclusion === 'success');

        if (failed) {
          status = WIZARD_STATUS.FAILED;
          errorStep = failed;
          matchedStep = failed;
          details = `Step failed: "${failed.name}" (Job: ${failed.jobName})`;
        } else if (running) {
          status = WIZARD_STATUS.RUNNING;
          matchedStep = running;
          details = `Executing: "${running.name}"...`;
        } else if (skipped) {
          status = WIZARD_STATUS.SKIPPED;
          details = 'Step was skipped by GitHub Actions';
        } else if (success) {
          status = WIZARD_STATUS.PASS;
          matchedStep = matched[matched.length - 1];
          const dur = this.#calcDuration(matched);
          details = dur ? `Passed (${dur})` : 'Completed successfully';
        } else if (queued) {
          status = WIZARD_STATUS.WAITING;
          details = 'Queued on runner...';
        } else {
          // Mixed: if at least one passed and none running/failed
          const anySuccess = matched.some(s => s.conclusion === 'success');
          if (anySuccess) {
            status = WIZARD_STATUS.PASS;
            details = 'Completed';
          }
        }
      }

      // Special handling for Artifact Generation stage
      let stageArtifacts = [];
      if (def.key === 'Artifact Generation' || def.key === 'Artifact') {
        stageArtifacts = (artifacts || []).filter(art => {
          const name = (art.name || '').toLowerCase();
          if (isLibrary) {
            return name.includes('aar') || name.includes('library') || name.includes('release') || name.includes('artifact');
          } else {
            return name.includes('apk') || name.includes('aab') || name.includes('app') || name.includes('bundle') || name.includes('release');
          }
        });
        if (stageArtifacts.length > 0 && status === WIZARD_STATUS.WAITING && (run?.conclusion === 'success' || mainJob?.conclusion === 'success')) {
          status = WIZARD_STATUS.PASS;
          details = `Generated ${stageArtifacts.length} artifact(s): ${stageArtifacts.map(a => a.name).join(', ')}`;
        }
      }

      // Special handling for Release stage
      let matchedRelease = null;
      if (def.key === 'Release') {
        if (releases && releases.length > 0) {
          matchedRelease = releases[0];
          if (status === WIZARD_STATUS.WAITING && (run?.conclusion === 'success')) {
            status = WIZARD_STATUS.PASS;
            details = `Published release: ${matchedRelease.tag_name || matchedRelease.name || 'v1.0.0'}`;
          }
        }
      }

      return {
        key: def.key,
        label: def.label,
        index: idx + 1,
        icon: def.icon,
        status,
        details,
        matchedSteps: matched,
        errorStep,
        artifacts: stageArtifacts,
        release: matchedRelease,
        jobId: matchedStep?.jobId || mainJob?.id || null,
        jobName: matchedStep?.jobName || mainJob?.name || ''
      };
    });

    // ── Apply Cascading Rules (Deterministic Progression) ───────────────────
    // RULE 1: If run is queued or no steps yet, everything is WAITING
    // RULE 2: If a stage is FAILED, ALL subsequent stages MUST be SKIPPED!
    // RULE 3: If a stage is RUNNING, ALL subsequent stages MUST be WAITING (unless explicitly skipped)
    // RULE 4: If overall run completed with success, ensure passed stages that had no explicit step name
    //         are marked PASS if prior stages passed.
    let failureEncountered = false;
    let failedIndex = -1;
    let runningEncountered = false;

    const finalStages = rawStages.map((stage, idx) => {
      // If a previous step failed:
      if (failureEncountered) {
        return {
          ...stage,
          status: WIZARD_STATUS.SKIPPED,
          details: `Skipped (preceding stage ${rawStages[failedIndex].key} failed)`
        };
      }

      // If current stage is FAILED:
      if (stage.status === WIZARD_STATUS.FAILED) {
        failureEncountered = true;
        failedIndex = idx;
        return stage;
      }

      // If previous step is RUNNING:
      if (runningEncountered) {
        return {
          ...stage,
          status: WIZARD_STATUS.WAITING,
          details: 'Waiting for preceding stage to complete...'
        };
      }

      // If current stage is RUNNING:
      if (stage.status === WIZARD_STATUS.RUNNING) {
        runningEncountered = true;
        return stage;
      }

      // Fallback inference if run is finished with success
      if (run?.conclusion === 'success' && stage.status === WIZARD_STATUS.WAITING) {
        return {
          ...stage,
          status: WIZARD_STATUS.PASS,
          details: 'Verified successfully'
        };
      }

      return stage;
    });

    // Check overall pipeline state
    const overallStatus = failureEncountered
      ? WIZARD_STATUS.FAILED
      : runningEncountered
        ? WIZARD_STATUS.RUNNING
        : finalStages.every(s => s.status === WIZARD_STATUS.PASS)
          ? WIZARD_STATUS.PASS
          : run?.status === 'completed' && run?.conclusion === 'failure'
            ? WIZARD_STATUS.FAILED
            : WIZARD_STATUS.RUNNING;

    return {
      runId: run?.id || null,
      runNumber: run?.run_number || null,
      workflowName: run?.name || 'CI/CD Workflow',
      branch: run?.head_branch || 'main',
      commitSha: (run?.head_sha || '').substring(0, 7),
      commitMessage: run?.head_commit?.message || 'Workflow dispatch',
      htmlUrl: run?.html_url || '',
      projectType,
      artifactType: isLibrary ? 'AAR' : 'APK/AAB',
      overallStatus,
      failedStageIndex: failedIndex,
      stages: finalStages,
      artifacts: artifacts || [],
      releases: releases || []
    };
  }

  static #calcDuration(steps) {
    let totalMs = 0;
    steps.forEach(s => {
      if (s.started_at && s.completed_at) {
        const diff = new Date(s.completed_at) - new Date(s.started_at);
        if (diff > 0) totalMs += diff;
      }
    });
    if (totalMs === 0) return null;
    const sec = Math.round(totalMs / 1000);
    return sec < 60 ? `${sec}s` : `${Math.floor(sec / 60)}m ${sec % 60}s`;
  }
}
