/**
 * GHActionsWizardPage.js
 * 
 * Intelligent GitHub Actions Build & Release Wizard for RONi.
 * 
 * Full End-to-End Workflow:
 * Real Project Analysis
 * -> Web Tool Environment Analysis
 * -> Compatibility Comparison
 * -> Best Workflow YAML Generation
 * -> GitHub Publish / Commit
 * -> Actual GitHub Actions Execution
 * -> Live Verification
 * -> Wizard Result (7 Stages)
 * -> Artifact (AAR for Library / APK & AAB for App)
 * -> Release
 * 
 * 7 Exact Stages:
 * Environment -> Dependency -> Build -> Test -> Verification -> Artifact -> Release
 * 
 * 5 Statuses:
 * 🟢 PASS | 🔴 FAILED | 🟡 RUNNING | 🔵 WAITING | ⏭ SKIPPED
 * 
 * Failure Cascade Rule:
 * When any step fails, subsequent dependent steps are automatically SKIPPED,
 * and real error logs and failed step details are inspectable.
 */

import { githubAuth } from '../modules/github/GitHubAuth.js';
import { githubApi } from '../modules/github/GitHubApi.js';
import { githubRepository } from '../modules/github/GitHubRepository.js';
import { githubSettings } from '../modules/github/GitHubSettings.js';
import { projectService } from '../modules/storage/ProjectService.js';
import { consoleService } from '../modules/storage/ConsoleService.js';
import { toast } from '../ui/ToastService.js';
import { modal } from '../ui/ModalService.js';
import {
  GitHubActionsWizardTracker,
  WIZARD_STATUS,
  WIZARD_STATUS_META
} from '../modules/github/GitHubActionsWizardTracker.js';
import { AndroidProjectWorkflowAnalyzer } from '../modules/github/AndroidProjectWorkflowAnalyzer.js';
import { WebToolEnvironmentCompatibility } from '../modules/github/WebToolEnvironmentCompatibility.js';
import { AdaptiveWorkflowGenerator } from '../modules/github/AdaptiveWorkflowGenerator.js';
import { ClipboardUtil } from '../core/ClipboardUtil.js';

export class GHActionsWizardPage {
  #container = null;
  #owner = '';
  #repo = '';
  #projectProfile = null;
  #compatibility = null;
  #selectedWorkflowFile = '.github/workflows/ci.yml';
  #runs = [];
  #selectedRunId = null;
  #pipelineData = null;
  #activeStageIndex = 0;
  #activeTab = 'pipeline'; // 'pipeline' | 'analysis' | 'workflow_yaml'
  #isPolling = false;
  #pollTimer = null;
  #pollCountdown = 5;
  #logContent = '';
  #isLoadingLogs = false;

  async init(container) {
    this.#container = container;
    await githubSettings.load();

    const proj = projectService.current;
    const settings = githubSettings.get();
    const activeRepo = githubRepository.activeRepo;

    // Run real Android Project Analysis
    this.#analyzeProject();

    // Auto-detect owner and repository name
    this.#owner = activeRepo?.owner?.login || settings.defaultUsername || githubAuth.user?.login || proj?.author || '';
    this.#repo = activeRepo?.name || proj?.repoName || (proj?.name ? proj.name.toLowerCase().replace(/[^a-z0-9_-]/g, '-') : '');

    await this.#fetchRuns();
    this.#render();
  }

  destroy() {
    this.#stopPolling();
  }

  #analyzeProject() {
    const proj = projectService.current;
    const settings = githubSettings.get();
    const files = proj?.files || {};

    this.#projectProfile = AndroidProjectWorkflowAnalyzer.analyze(files, proj);
    this.#compatibility = WebToolEnvironmentCompatibility.assess(this.#projectProfile, settings, {
      isAuthenticated: githubAuth.isAuthenticated,
      user: githubAuth.user
    });

    // Sync generated workflows into project if not yet present
    this.#ensureGeneratedWorkflows(files, proj);
  }

  #ensureGeneratedWorkflows(files, proj) {
    if (!proj) return;
    let modified = false;

    if (!files['.github/workflows/ci.yml'] && !files['.github/workflows/build.yml']) {
      files['.github/workflows/ci.yml'] = AdaptiveWorkflowGenerator.generateCIWorkflow(this.#projectProfile, this.#compatibility);
      modified = true;
    }
    if (!files['.github/workflows/release.yml']) {
      files['.github/workflows/release.yml'] = AdaptiveWorkflowGenerator.generateReleaseWorkflow(this.#projectProfile, this.#compatibility);
      modified = true;
    }

    if (modified) {
      projectService.save();
    }
  }

  async #fetchRuns() {
    if (!this.#owner || !this.#repo) return;

    try {
      this.#runs = await GitHubActionsWizardTracker.getWorkflowRuns(this.#owner, this.#repo, { per_page: 20 });
      if (this.#runs.length > 0 && !this.#selectedRunId) {
        this.#selectedRunId = this.#runs[0].id;
      }
      if (this.#selectedRunId) {
        await this.#fetchCurrentRunDetails();
      }
    } catch (err) {
      console.warn('Failed to fetch runs:', err);
    }
  }

  async #fetchCurrentRunDetails() {
    if (!this.#owner || !this.#repo || !this.#selectedRunId) return;

    try {
      const details = await GitHubActionsWizardTracker.getRunDetails(this.#owner, this.#repo, this.#selectedRunId);
      this.#pipelineData = GitHubActionsWizardTracker.mapToWizardStages(
        details.run,
        details.jobs,
        this.#projectProfile?.projectType || 'library',
        details.artifacts,
        details.releases
      );

      // Auto-select failing stage or running stage if available
      if (this.#pipelineData.failedStageIndex >= 0) {
        this.#activeStageIndex = this.#pipelineData.failedStageIndex;
      } else {
        const runningIdx = this.#pipelineData.stages.findIndex(s => s.status === WIZARD_STATUS.RUNNING);
        if (runningIdx >= 0) {
          this.#activeStageIndex = runningIdx;
        }
      }
    } catch (err) {
      console.warn('Failed to fetch run details:', err);
    }
  }

  #render() {
    if (!this.#container) return;

    const authed = githubAuth.isAuthenticated;
    const p = this.#pipelineData;
    const prof = this.#projectProfile;
    const compat = this.#compatibility;

    this.#container.innerHTML = `
      <div class="page-container ghw-container">
        
        <!-- Header -->
        <div class="page-header" style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-2)">
          <div>
            <h1 class="page-title" style="display:flex;align-items:center;gap:8px">
              <span class="material-symbols-outlined" style="color:#38BDF8">smart_toy</span>
              Intelligent GitHub Actions Build &amp; Release Wizard
            </h1>
            <p class="page-subtitle">Adaptive Workflow Engine &bull; Live Execution Verification &bull; 7-Stage Pipeline Tracker</p>
          </div>
          <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
            <span class="badge ${authed ? 'badge-success' : 'badge-neutral'}">
              <span class="material-symbols-outlined" style="font-size:14px">${authed ? 'verified_user' : 'link_off'}</span>
              ${authed ? `@${githubAuth.user?.login || 'Connected'}` : 'Guest Mode'}
            </span>
            <div class="ghw-chip" style="cursor:pointer" id="ghw-toggle-type-btn" title="Click to toggle Project Type">
              <span class="material-symbols-outlined" style="font-size:14px;color:#38BDF8">settings_suggest</span>
              Type: <strong style="color:#38BDF8">${prof?.projectType ? prof.projectType.toUpperCase() : 'LIBRARY'} (${prof?.artifacts?.type || 'AAR'})</strong>
            </div>
          </div>
        </div>

        <!-- Real Android Project & Environment Analysis Card -->
        <div class="ghw-header-card">
          <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px">
            <div style="display:flex;align-items:center;gap:8px">
              <span class="material-symbols-outlined" style="color:#38BDF8">query_stats</span>
              <strong style="font-size:14px">Real Android Project &amp; Environment Intelligence</strong>
            </div>
            <div style="display:flex;align-items:center;gap:6px">
              <button type="button" class="btn btn-secondary btn-xs" id="ghw-reanalyze-btn">
                <span class="material-symbols-outlined" style="font-size:14px">refresh</span> Re-Analyze Project
              </button>
              <button type="button" class="btn btn-primary btn-xs" id="ghw-regenerate-workflows-btn">
                <span class="material-symbols-outlined" style="font-size:14px">auto_fix_high</span> Update Workflows
              </button>
            </div>
          </div>

          <div class="ghw-intel-grid">
            <div class="ghw-intel-pill">
              <span class="material-symbols-outlined ghw-intel-icon">developer_board</span>
              <div>
                <div class="ghw-intel-label">AGP &amp; Gradle</div>
                <div class="ghw-intel-value">AGP ${prof?.agpVersion || '8.x'} | Gradle ${prof?.gradleVersion || '8.x'}</div>
              </div>
            </div>

            <div class="ghw-intel-pill">
              <span class="material-symbols-outlined ghw-intel-icon">terminal</span>
              <div>
                <div class="ghw-intel-label">JDK Requirement</div>
                <div class="ghw-intel-value">Java ${prof?.targetJdk || '17'} (Temurin Distribution)</div>
              </div>
            </div>

            <div class="ghw-intel-pill">
              <span class="material-symbols-outlined ghw-intel-icon">android</span>
              <div>
                <div class="ghw-intel-label">SDK Configuration</div>
                <div class="ghw-intel-value">compileSdk: ${prof?.compileSdk || 34} | minSdk: ${prof?.minSdk || 21}</div>
              </div>
            </div>

            <div class="ghw-intel-pill">
              <span class="material-symbols-outlined ghw-intel-icon">package_2</span>
              <div>
                <div class="ghw-intel-label">Artifact Strategy</div>
                <div class="ghw-intel-value">${prof?.artifacts?.type || 'AAR'} (${prof?.projectType === 'app' ? 'APK/AAB Generate' : 'AAR Generate'})</div>
              </div>
            </div>
          </div>

          ${compat ? `
            <div style="font-size:12px;color:var(--text-secondary);display:flex;align-items:center;gap:6px;background:rgba(15, 23, 42, 0.4);padding:6px 10px;border-radius:6px">
              <span class="material-symbols-outlined" style="font-size:16px;color:#10B981">verified</span>
              <span><strong>Compatibility Status:</strong> ${this.#esc(compat.summary)}</span>
            </div>
          ` : ''}
        </div>

        <!-- Repository & Run Selector Toolbar -->
        <div class="ghw-header-card">
          <div class="ghw-toolbar">
            <div class="ghw-controls">
              <label style="font-size:12px;font-weight:600;color:var(--text-muted)">Repository:</label>
              <input type="text" id="ghw-owner-input" class="ghw-select" style="min-width:110px;width:120px" value="${this.#esc(this.#owner)}" placeholder="owner" />
              <span style="color:var(--text-muted)">/</span>
              <input type="text" id="ghw-repo-input" class="ghw-select" style="min-width:140px;width:160px" value="${this.#esc(this.#repo)}" placeholder="repository" />
              <button type="button" class="btn btn-secondary btn-sm" id="ghw-connect-repo-btn">
                <span class="material-symbols-outlined" style="font-size:16px">sync</span> Load Runs
              </button>
            </div>

            <div class="ghw-controls">
              <label style="font-size:12px;font-weight:600;color:var(--text-muted)">Workflow Run:</label>
              <select id="ghw-run-select" class="ghw-select" style="min-width:240px">
                ${this.#runs.length === 0 ? '<option value="">No workflow runs found</option>' : ''}
                ${this.#runs.map(r => {
                  const sel = r.id === this.#selectedRunId ? 'selected' : '';
                  const statusIcon = r.conclusion === 'success' ? '🟢' : r.conclusion === 'failure' ? '🔴' : r.status === 'in_progress' ? '🟡' : '🔵';
                  const msg = (r.head_commit?.message || r.name || 'Run').substring(0, 30);
                  return `<option value="${r.id}" ${sel}>${statusIcon} #${r.run_number} (${r.head_branch}) — ${this.#esc(msg)}</option>`;
                }).join('')}
              </select>

              <button type="button" class="btn btn-primary btn-sm" id="ghw-trigger-btn">
                <span class="material-symbols-outlined" style="font-size:16px">play_arrow</span> Trigger Run
              </button>

              <button type="button" class="btn ${this.#isPolling ? 'btn-danger' : 'btn-secondary'} btn-sm" id="ghw-poll-toggle-btn">
                <span class="material-symbols-outlined" style="font-size:16px">${this.#isPolling ? 'pause' : 'autorenew'}</span>
                ${this.#isPolling ? `Polling (${this.#pollCountdown}s)` : 'Live Poll'}
              </button>

              <button type="button" class="btn btn-secondary btn-sm" id="ghw-refresh-btn" title="Refresh Now">
                <span class="material-symbols-outlined" style="font-size:16px">refresh</span>
              </button>
            </div>
          </div>

          <!-- Active Run Meta Chips & Live Verification Result -->
          ${p ? `
            <div class="ghw-verify-banner ${this.#getVerificationClass(p)}">
              <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
                <span class="material-symbols-outlined" style="font-size:22px">
                  ${p.overallStatus === WIZARD_STATUS.PASS ? 'verified' : p.overallStatus === WIZARD_STATUS.FAILED ? 'error' : 'sync'}
                </span>
                <div>
                  <strong style="font-size:13px">
                    ${p.overallStatus === WIZARD_STATUS.PASS ? 'Live Verification Passed (Production Ready)' : p.overallStatus === WIZARD_STATUS.FAILED ? 'Live Verification Failed on Runner' : 'Workflow Running on Runner'}
                  </strong>
                  <span style="font-size:12px;margin-left:6px;opacity:0.9">
                    Run #${p.runNumber} on branch <strong>${this.#esc(p.branch)}</strong> [${p.commitSha}]
                  </span>
                </div>
              </div>
              <div style="display:flex;align-items:center;gap:8px">
                <button type="button" class="btn btn-secondary btn-xs" id="ghw-view-yaml-btn">
                  <span class="material-symbols-outlined" style="font-size:14px">code</span> Inspect Workflow YAML
                </button>
                ${p.htmlUrl ? `
                  <a href="${p.htmlUrl}" target="_blank" rel="noopener noreferrer" class="btn btn-secondary btn-xs">
                    <span class="material-symbols-outlined" style="font-size:14px">open_in_new</span> GitHub Run
                  </a>
                ` : ''}
              </div>
            </div>
          ` : ''}
        </div>

        <!-- View Navigation Tabs: Pipeline Tracker vs Workflow YAML Editor -->
        <div class="tabs-nav" style="margin-bottom:-4px">
          <button class="tab-btn ${this.#activeTab === 'pipeline' ? 'active' : ''}" id="ghw-tab-pipeline">
            <span class="material-symbols-outlined">linear_scale</span> 7-Stage Pipeline Tracker
          </button>
          <button class="tab-btn ${this.#activeTab === 'workflow_yaml' ? 'active' : ''}" id="ghw-tab-yaml">
            <span class="material-symbols-outlined">description</span> Workflow YAML &amp; Editor
          </button>
        </div>

        ${this.#activeTab === 'pipeline' ? this.#renderPipelineView(p) : this.#renderYamlEditorView()}

      </div>
    `;

    this.#bindEvents();
  }

  #getVerificationClass(p) {
    if (!p) return 'verify-waiting';
    if (p.overallStatus === WIZARD_STATUS.PASS) return 'verify-pass';
    if (p.overallStatus === WIZARD_STATUS.FAILED) return 'verify-failed';
    if (p.overallStatus === WIZARD_STATUS.RUNNING) return 'verify-running';
    return 'verify-waiting';
  }

  #renderPipelineView(p) {
    return `
      <!-- 7-Stage Real-Time Wizard Stepper -->
      <div class="card" style="padding:var(--sp-4)">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px">
          <h3 style="font-size:14px;font-weight:700;margin:0;display:flex;align-items:center;gap:6px">
            <span class="material-symbols-outlined" style="color:#38BDF8;font-size:18px">linear_scale</span>
            Workflow Execution Pipeline (7 Stages)
          </h3>
          <span style="font-size:12px;color:var(--text-muted)">
            Environment &rarr; Dependency &rarr; Build &rarr; Test &rarr; Verification &rarr; Artifact Generation &rarr; Release
          </span>
        </div>

        ${p ? this.#renderStepper(p) : `
          <div class="empty-state" style="padding:32px 16px;text-align:center">
            <span class="material-symbols-outlined" style="font-size:40px;color:var(--text-muted)">cloud_sync</span>
            <p style="margin:10px 0;font-size:13px;color:var(--text-muted)">
              ${this.#owner && this.#repo ? 'No workflow run loaded. Select or trigger a run above.' : 'Enter a GitHub Owner and Repository to monitor GitHub Actions.'}
            </p>
          </div>
        `}
      </div>

      <!-- Stage Inspector & Logs -->
      ${p ? this.#renderStageInspector(p) : ''}
    `;
  }

  #renderStepper(p) {
    return `
      <div class="ghw-stepper-wrapper">
        <div class="ghw-stepper">
          ${p.stages.map((stage, idx) => {
            const meta = WIZARD_STATUS_META[stage.status] || WIZARD_STATUS_META[WIZARD_STATUS.WAITING];
            const isActive = idx === this.#activeStageIndex;
            const cardClass = `ghw-stage-card stage-${stage.status.toLowerCase()} ${isActive ? 'active' : ''}`;

            return `
              <div class="${cardClass}" data-stage-index="${idx}" title="Click to view ${this.#esc(stage.label)} details and logs">
                <div class="ghw-stage-top">
                  <span class="ghw-stage-index">
                    <span class="material-symbols-outlined" style="font-size:14px">${stage.icon}</span>
                    #${idx + 1}
                  </span>
                  <span class="ghw-badge ${meta.badgeClass}">
                    <span>${meta.emoji}</span> ${meta.label}
                  </span>
                </div>

                <div class="ghw-stage-name" title="${this.#esc(stage.label)}">
                  ${this.#esc(stage.label)}
                </div>

                <div class="ghw-stage-desc">
                  ${this.#esc(stage.details || meta.description)}
                </div>
              </div>
            `;
          }).join('')}
        </div>
      </div>
    `;
  }

  #renderStageInspector(p) {
    const stage = p.stages[this.#activeStageIndex] || p.stages[0];
    const meta = WIZARD_STATUS_META[stage.status];
    const isFailed = stage.status === WIZARD_STATUS.FAILED;
    const isArtifact = stage.key === 'Artifact Generation' || stage.key === 'Artifact';
    const isRelease = stage.key === 'Release';

    return `
      <div class="ghw-detail-grid">
        
        <!-- Left: Stage Execution Summary -->
        <div class="card" style="padding:var(--sp-4);display:flex;flex-direction:column;gap:12px">
          <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px;border-bottom:1px solid var(--border-color);padding-bottom:10px">
            <div style="display:flex;align-items:center;gap:8px">
              <span class="material-symbols-outlined" style="font-size:22px;color:${meta.color}">${stage.icon}</span>
              <div>
                <h3 style="font-size:15px;font-weight:700;margin:0">${stage.index}. ${this.#esc(stage.label)}</h3>
                <span style="font-size:11px;color:var(--text-muted)">Stage ${stage.index} of 7 in pipeline</span>
              </div>
            </div>
            <span class="ghw-badge ${meta.badgeClass}">
              <span>${meta.emoji}</span> ${meta.label}
            </span>
          </div>

          <!-- Failure Alert Banner -->
          ${isFailed ? `
            <div class="alert alert-error" style="display:flex;align-items:flex-start;gap:10px;padding:12px;background:rgba(239, 68, 68, 0.15);border:1px solid #EF4444;border-radius:6px">
              <span class="material-symbols-outlined" style="color:#EF4444;font-size:20px;flex-shrink:0">error</span>
              <div style="flex:1">
                <strong style="color:#F87171">Stage Execution Failed!</strong>
                <p style="margin:4px 0 0;font-size:12px;color:#FCA5A5">
                  ${stage.errorStep ? `Step <strong>"${this.#esc(stage.errorStep.name)}"</strong> exited with failure.` : this.#esc(stage.details)}
                  All subsequent dependent steps have been automatically <strong>SKIPPED</strong>.
                </p>
              </div>
            </div>
          ` : ''}

          <!-- Details & Steps -->
          <div>
            <label style="font-size:12px;font-weight:600;color:var(--text-muted)">Status Information:</label>
            <p style="margin:4px 0 8px;font-size:13px;color:var(--text-primary)">${this.#esc(stage.details)}</p>
          </div>

          <!-- Matched GitHub Actions Steps -->
          ${stage.matchedSteps && stage.matchedSteps.length > 0 ? `
            <div>
              <label style="font-size:12px;font-weight:600;color:var(--text-muted);display:block;margin-bottom:6px">
                GitHub Actions Step Executions (${stage.matchedSteps.length}):
              </label>
              <div style="display:flex;flex-direction:column;gap:6px">
                ${stage.matchedSteps.map(s => {
                  const sIcon = s.conclusion === 'success' ? '🟢' : s.conclusion === 'failure' ? '🔴' : s.status === 'in_progress' ? '🟡' : '⚪';
                  return `
                    <div style="display:flex;align-items:center;justify-content:space-between;padding:6px 10px;background:rgba(15, 23, 42, 0.6);border:1px solid var(--border-color);border-radius:6px;font-size:12px">
                      <span style="display:flex;align-items:center;gap:6px">
                        <span>${sIcon}</span>
                        <strong>${this.#esc(s.name)}</strong>
                      </span>
                      <span style="color:var(--text-muted);font-size:11px">
                        ${s.conclusion || s.status}
                      </span>
                    </div>
                  `;
                }).join('')}
              </div>
            </div>
          ` : ''}

          <!-- Artifact Stage Specifics (AAR vs APK/AAB) -->
          ${isArtifact ? `
            <div style="margin-top:8px;padding:12px;background:rgba(14, 165, 233, 0.08);border:1px solid rgba(14, 165, 233, 0.3);border-radius:8px">
              <div style="display:flex;align-items:center;justify-content:space-between">
                <span class="ghw-artifact-badge">
                  <span class="material-symbols-outlined" style="font-size:16px">package_2</span>
                  Target: ${p.artifactType} Generate
                </span>
                <span style="font-size:11px;color:var(--text-muted)">
                  ${this.#projectProfile?.projectType === 'app' ? 'Android Application Binary' : 'Android Library Archive'}
                </span>
              </div>

              ${stage.artifacts && stage.artifacts.length > 0 ? `
                <div style="margin-top:8px">
                  ${stage.artifacts.map(a => `
                    <div class="ghw-artifact-item">
                      <span style="font-size:12px;display:flex;align-items:center;gap:6px">
                        <span class="material-symbols-outlined" style="font-size:16px;color:#38BDF8">description</span>
                        <strong>${this.#esc(a.name)}</strong>
                        <span style="color:var(--text-muted);font-size:11px">(${Math.round((a.size_in_bytes || 0) / 1024)} KB)</span>
                      </span>
                      ${a.archive_download_url ? `
                        <a href="${a.archive_download_url}" target="_blank" rel="noopener noreferrer" class="btn btn-primary btn-xs">
                          <span class="material-symbols-outlined" style="font-size:13px">download</span> Download
                        </a>
                      ` : ''}
                    </div>
                  `).join('')}
                </div>
              ` : `
                <p style="margin:8px 0 0;font-size:12px;color:var(--text-muted)">
                  ${stage.status === WIZARD_STATUS.PASS ? `Artifacts were generated and processed.` : `Awaiting artifact generation from build step.`}
                </p>
              `}
            </div>
          ` : ''}

          <!-- Release Stage Specifics -->
          ${isRelease ? `
            <div style="margin-top:8px;padding:12px;background:rgba(16, 185, 129, 0.08);border:1px solid rgba(16, 185, 129, 0.3);border-radius:8px">
              <div style="display:flex;align-items:center;gap:6px">
                <span class="material-symbols-outlined" style="color:#10B981">rocket_launch</span>
                <strong style="font-size:13px">GitHub Release Publication</strong>
              </div>
              ${stage.release ? `
                <div style="margin-top:8px;font-size:12px">
                  <div>Tag: <strong>${this.#esc(stage.release.tag_name || 'v1.0.0')}</strong></div>
                  <div>Name: ${this.#esc(stage.release.name || '')}</div>
                  ${stage.release.html_url ? `
                    <a href="${stage.release.html_url}" target="_blank" rel="noopener noreferrer" class="btn btn-primary btn-xs" style="margin-top:6px">
                      <span class="material-symbols-outlined" style="font-size:14px">new_releases</span> View Release
                    </a>
                  ` : ''}
                </div>
              ` : `
                <p style="margin:6px 0 0;font-size:12px;color:var(--text-muted)">
                  ${stage.status === WIZARD_STATUS.PASS ? 'Release deployed to GitHub.' : 'Release will publish automatically once verification passes.'}
                </p>
              `}
            </div>
          ` : ''}

          <!-- Action buttons -->
          <div style="display:flex;align-items:center;gap:8px;margin-top:auto;padding-top:10px">
            <button type="button" class="btn btn-secondary btn-sm" id="ghw-fetch-logs-btn">
              <span class="material-symbols-outlined" style="font-size:16px">terminal</span>
              ${this.#isLoadingLogs ? 'Loading Logs...' : 'View GitHub Actions Logs'}
            </button>
          </div>
        </div>

        <!-- Right: Terminal Log Viewer -->
        <div class="ghw-log-card">
          <div class="ghw-log-header">
            <span class="ghw-log-title">
              <span class="material-symbols-outlined" style="font-size:16px;color:#38BDF8">terminal</span>
              Runner Execution Logs — ${this.#esc(stage.label)}
            </span>
            <div style="display:flex;align-items:center;gap:6px">
              <button type="button" class="btn btn-ghost btn-xs" id="ghw-copy-log-btn" title="Copy raw logs">
                <span class="material-symbols-outlined" style="font-size:14px">content_copy</span>
              </button>
            </div>
          </div>

          <div class="ghw-terminal" id="ghw-terminal-output">
            ${this.#renderLogOutput(stage)}
          </div>
        </div>

      </div>
    `;
  }

  #renderYamlEditorView() {
    const proj = projectService.current;
    const files = proj?.files || {};
    const content = files[this.#selectedWorkflowFile] || '';

    return `
      <div class="card" style="padding:var(--sp-4);display:flex;flex-direction:column;gap:12px">
        <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px">
          <div style="display:flex;align-items:center;gap:10px">
            <span class="material-symbols-outlined" style="color:#38BDF8">code</span>
            <div>
              <h3 style="font-size:15px;font-weight:700;margin:0">Workflow YAML Configuration</h3>
              <span style="font-size:11px;color:var(--text-muted)">Edit and push customized GitHub Actions workflows</span>
            </div>
          </div>

          <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
            <select id="ghw-workflow-picker" class="ghw-select">
              <option value=".github/workflows/ci.yml" ${this.#selectedWorkflowFile === '.github/workflows/ci.yml' ? 'selected' : ''}>ci.yml (CI Pipeline)</option>
              <option value=".github/workflows/release.yml" ${this.#selectedWorkflowFile === '.github/workflows/release.yml' ? 'selected' : ''}>release.yml (Release Pipeline)</option>
            </select>
            <button type="button" class="btn btn-secondary btn-sm" id="ghw-reset-yaml-btn">
              <span class="material-symbols-outlined" style="font-size:16px">restart_alt</span> Regenerate
            </button>
            <button type="button" class="btn btn-primary btn-sm" id="ghw-save-push-yaml-btn">
              <span class="material-symbols-outlined" style="font-size:16px">publish</span> Save &amp; Push to GitHub
            </button>
          </div>
        </div>

        <textarea id="ghw-yaml-textarea" class="ghw-yaml-editor" spellcheck="false">${this.#esc(content)}</textarea>
      </div>
    `;
  }

  #renderLogOutput(stage) {
    if (this.#isLoadingLogs) {
      return '<span style="color:#38BDF8">Fetching step logs from GitHub Actions API...</span>';
    }

    if (this.#logContent) {
      return this.#formatLogTerminal(this.#logContent);
    }

    if (stage.status === WIZARD_STATUS.FAILED) {
      return `<span class="log-err">🔴 STAGE FAILED</span>\n\n${this.#esc(stage.details)}\n\nClick "View GitHub Actions Logs" to load the full runner log trace from GitHub API.`;
    }

    if (stage.status === WIZARD_STATUS.SKIPPED) {
      return `<span style="color:#94A3B8">⏭ STAGE SKIPPED</span>\n\nThis step was skipped because a preceding dependent step failed or conditions were not met.`;
    }

    if (stage.status === WIZARD_STATUS.WAITING) {
      return `<span style="color:#60A5FA">🔵 STAGE WAITING</span>\n\nAwaiting runner assignment and completion of preceding stages.`;
    }

    if (stage.status === WIZARD_STATUS.RUNNING) {
      return `<span class="log-warn">🟡 STAGE CURRENTLY RUNNING</span>\n\n${this.#esc(stage.details)}\n\nClick "View GitHub Actions Logs" to stream available logs.`;
    }

    return `<span class="log-success">🟢 STAGE COMPLETED</span>\n\n${this.#esc(stage.details)}\n\nClick "View GitHub Actions Logs" to inspect detailed runner step output.`;
  }

  #formatLogTerminal(raw) {
    const lines = raw.split('\n');
    return lines.map(line => {
      const escaped = this.#esc(line);
      if (/error|failed|failure|exception|fatal/i.test(line)) {
        return `<span class="log-err">${escaped}</span>`;
      }
      if (/warn|warning/i.test(line)) {
        return `<span class="log-warn">${escaped}</span>`;
      }
      if (/success|passed|completed|built/i.test(line)) {
        return `<span class="log-success">${escaped}</span>`;
      }
      if (/^##\[group\]|^##\[section\]/i.test(line)) {
        return `<span class="log-info" style="font-weight:700">${escaped}</span>`;
      }
      return escaped;
    }).join('\n');
  }

  #bindEvents() {
    const c = this.#container;
    if (!c) return;

    // View Navigation Tabs
    c.querySelector('#ghw-tab-pipeline')?.addEventListener('click', () => {
      this.#activeTab = 'pipeline';
      this.#render();
    });
    c.querySelector('#ghw-tab-yaml')?.addEventListener('click', () => {
      this.#activeTab = 'workflow_yaml';
      this.#render();
    });

    // Re-analyze Project
    c.querySelector('#ghw-reanalyze-btn')?.addEventListener('click', () => {
      this.#analyzeProject();
      toast.success('Project Analyzed', 'Updated build tooling, AGP, Gradle, and SDK specifications.');
      this.#render();
    });

    // Update / Regenerate Workflows
    c.querySelector('#ghw-regenerate-workflows-btn')?.addEventListener('click', () => {
      this.#analyzeProject();
      const proj = projectService.current;
      if (proj) {
        proj.files['.github/workflows/ci.yml'] = AdaptiveWorkflowGenerator.generateCIWorkflow(this.#projectProfile, this.#compatibility);
        proj.files['.github/workflows/release.yml'] = AdaptiveWorkflowGenerator.generateReleaseWorkflow(this.#projectProfile, this.#compatibility);
        projectService.save();
        toast.success('Workflows Updated', 'Generated best compatible CI and Release workflows.');
        this.#render();
      }
    });

    // Workflow YAML Picker
    c.querySelector('#ghw-workflow-picker')?.addEventListener('change', (e) => {
      this.#selectedWorkflowFile = e.target.value;
      this.#render();
    });

    // Reset Workflow YAML to Dynamic Generator
    c.querySelector('#ghw-reset-yaml-btn')?.addEventListener('click', () => {
      const proj = projectService.current;
      if (!proj) return;
      if (this.#selectedWorkflowFile.includes('ci.yml')) {
        proj.files['.github/workflows/ci.yml'] = AdaptiveWorkflowGenerator.generateCIWorkflow(this.#projectProfile, this.#compatibility);
      } else {
        proj.files['.github/workflows/release.yml'] = AdaptiveWorkflowGenerator.generateReleaseWorkflow(this.#projectProfile, this.#compatibility);
      }
      projectService.save();
      toast.success('Workflow Reset', 'Reset to adaptive generator specification.');
      this.#render();
    });

    // Save & Push Workflow YAML
    c.querySelector('#ghw-save-push-yaml-btn')?.addEventListener('click', async () => {
      const proj = projectService.current;
      const ta = c.querySelector('#ghw-yaml-textarea');
      const val = ta?.value;
      if (!proj || !val) return;

      proj.files[this.#selectedWorkflowFile] = val;
      await projectService.save();
      toast.success('Saved to Project', `Updated ${this.#selectedWorkflowFile}`);

      // Push to GitHub if configured
      if (this.#owner && this.#repo && githubAuth.isAuthenticated) {
        try {
          toast.info('Pushing to GitHub', `Committing ${this.#selectedWorkflowFile}...`);
          let sha = null;
          try {
            const existing = await githubApi.getFileContents(this.#owner, this.#repo, this.#selectedWorkflowFile);
            sha = existing.sha;
          } catch {
            // New file
          }
          await githubApi.createOrUpdateFileContents(
            this.#owner,
            this.#repo,
            this.#selectedWorkflowFile,
            `ci: update ${this.#selectedWorkflowFile} via Build & Release Wizard`,
            val,
            sha
          );
          toast.success('Pushed to GitHub', `Successfully committed ${this.#selectedWorkflowFile} to repository.`);
          consoleService.success(`Pushed updated workflow ${this.#selectedWorkflowFile} to ${this.#owner}/${this.#repo}`);
        } catch (err) {
          toast.error('GitHub Push Error', err.message || 'Check write permissions for workflow scope.');
        }
      }
    });

    // Inspect Workflow YAML shortcut
    c.querySelector('#ghw-view-yaml-btn')?.addEventListener('click', () => {
      this.#activeTab = 'workflow_yaml';
      this.#render();
    });

    // Stage Selection
    c.querySelectorAll('.ghw-stage-card').forEach(card => {
      card.addEventListener('click', () => {
        const idx = parseInt(card.getAttribute('data-stage-index'), 10);
        if (!isNaN(idx)) {
          this.#activeStageIndex = idx;
          this.#logContent = '';
          this.#render();
        }
      });
    });

    // Run Selector Change
    c.querySelector('#ghw-run-select')?.addEventListener('change', async (e) => {
      this.#selectedRunId = e.target.value ? parseInt(e.target.value, 10) : null;
      this.#logContent = '';
      await this.#fetchCurrentRunDetails();
      this.#render();
    });

    // Connect / Reload Repository
    c.querySelector('#ghw-connect-repo-btn')?.addEventListener('click', async () => {
      const ownerVal = c.querySelector('#ghw-owner-input')?.value.trim();
      const repoVal = c.querySelector('#ghw-repo-input')?.value.trim();

      if (!ownerVal || !repoVal) {
        toast.error('Missing fields', 'Please enter both owner and repository name');
        return;
      }

      this.#owner = ownerVal;
      this.#repo = repoVal;
      this.#selectedRunId = null;
      this.#logContent = '';

      toast.info('Loading runs', `Fetching GitHub Actions runs for ${ownerVal}/${repoVal}...`);
      await this.#fetchRuns();
      this.#render();
    });

    // Project Type Toggle
    c.querySelector('#ghw-toggle-type-btn')?.addEventListener('click', async () => {
      const proj = projectService.current;
      const files = proj?.files || {};
      const newType = this.#projectProfile?.projectType === 'library' ? 'app' : 'library';
      this.#projectProfile = AndroidProjectWorkflowAnalyzer.analyze(files, {
        ...proj,
        projectType: newType,
        requireAab: newType === 'app'
      });
      // Force user-selected override
      this.#projectProfile.projectType = newType;
      if (newType === 'app') {
        this.#projectProfile.supportsAab = true;
        this.#projectProfile.artifacts.type = 'APK/AAB';
      } else {
        this.#projectProfile.supportsAab = false;
        this.#projectProfile.artifacts.type = 'AAR';
      }
      toast.info('Target Switched', `Artifact target set to: ${this.#projectProfile.projectType.toUpperCase()} (${this.#projectProfile.artifacts.type})`);
      if (this.#selectedRunId) {
        await this.#fetchCurrentRunDetails();
      }
      this.#render();
    });

    // Trigger Run Modal
    c.querySelector('#ghw-trigger-btn')?.addEventListener('click', () => {
      this.#showTriggerModal();
    });

    // Polling Toggle
    c.querySelector('#ghw-poll-toggle-btn')?.addEventListener('click', () => {
      if (this.#isPolling) {
        this.#stopPolling();
      } else {
        this.#startPolling();
      }
      this.#render();
    });

    // Refresh Now Button
    c.querySelector('#ghw-refresh-btn')?.addEventListener('click', async () => {
      await this.#fetchRuns();
      toast.success('Refreshed', 'GitHub Actions workflow data updated');
      this.#render();
    });

    // Fetch Logs Button
    c.querySelector('#ghw-fetch-logs-btn')?.addEventListener('click', async () => {
      await this.#fetchLogsForActiveStage();
    });

    // Copy Log Button
    c.querySelector('#ghw-copy-log-btn')?.addEventListener('click', async () => {
      if (!this.#logContent) {
        toast.info('No logs loaded', 'Click "View GitHub Actions Logs" first');
        return;
      }
      const ok = await ClipboardUtil.copyText(this.#logContent);
      if (ok) {
        toast.success('Copied', 'Runner logs copied to clipboard');
      } else {
        toast.error('Copy failed', 'Could not copy to clipboard');
      }
    });
  }

  async #fetchLogsForActiveStage() {
    const stage = this.#pipelineData?.stages?.[this.#activeStageIndex];
    if (!stage) return;

    this.#isLoadingLogs = true;
    this.#render();

    try {
      const jobId = stage.jobId;
      if (!jobId) {
        this.#logContent = `No specific GitHub Actions runner job found for stage: ${stage.label}.\nStatus: ${stage.status}`;
      } else {
        const raw = await GitHubActionsWizardTracker.getJobLogs(this.#owner, this.#repo, jobId);
        this.#logContent = raw || 'Log trace is empty or not yet published by runner.';
      }
    } catch (err) {
      this.#logContent = `Failed to fetch logs from GitHub API: ${err.message}`;
    } finally {
      this.#isLoadingLogs = false;
      this.#render();
    }
  }

  #startPolling() {
    this.#isPolling = true;
    this.#pollCountdown = 5;
    this.#pollTimer = setInterval(async () => {
      this.#pollCountdown--;
      if (this.#pollCountdown <= 0) {
        this.#pollCountdown = 5;
        await this.#fetchCurrentRunDetails();
        this.#render();
      } else {
        const btn = this.#container?.querySelector('#ghw-poll-toggle-btn');
        if (btn) btn.innerHTML = `<span class="material-symbols-outlined" style="font-size:16px">pause</span> Polling (${this.#pollCountdown}s)`;
      }
    }, 1000);
  }

  #stopPolling() {
    this.#isPolling = false;
    if (this.#pollTimer) {
      clearInterval(this.#pollTimer);
      this.#pollTimer = null;
    }
  }

  #showTriggerModal() {
    const p = this.#pipelineData;
    const defaultBranch = p?.branch || 'main';

    modal.show({
      title: 'Dispatch GitHub Actions Workflow',
      content: `
        <div style="display:flex;flex-direction:column;gap:12px">
          <p style="font-size:13px;color:var(--text-secondary)">
            Trigger a workflow on <strong>${this.#esc(this.#owner)}/${this.#esc(this.#repo)}</strong> with parameterized inputs.
          </p>
          <div class="form-group">
            <label class="form-label">Branch / Ref:</label>
            <input type="text" id="ghw-modal-branch" class="form-input" value="${this.#esc(defaultBranch)}" />
          </div>
          <div class="form-group">
            <label class="form-label">Workflow File:</label>
            <select id="ghw-modal-wf" class="form-input">
              <option value="ci.yml">ci.yml (CI Pipeline)</option>
              <option value="release.yml">release.yml (Build & Release Pipeline)</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">Build Variant:</label>
            <select id="ghw-modal-variant" class="form-input">
              <option value="release">release (Production)</option>
              <option value="debug">debug (Development)</option>
            </select>
          </div>
        </div>
      `,
      buttons: [
        { label: 'Cancel', variant: 'secondary', onClick: () => modal.close() },
        {
          label: 'Dispatch Workflow',
          variant: 'primary',
          onClick: async () => {
            const branch = document.getElementById('ghw-modal-branch')?.value.trim() || 'main';
            const wf = document.getElementById('ghw-modal-wf')?.value.trim() || 'ci.yml';
            const variant = document.getElementById('ghw-modal-variant')?.value || 'release';
            modal.close();

            try {
              toast.info('Dispatching', `Triggering ${wf} on ${branch}...`);
              await GitHubActionsWizardTracker.triggerWorkflow(this.#owner, this.#repo, wf, branch, {
                build_variant: variant
              });
              toast.success('Dispatched', 'Workflow run triggered successfully! Starting live monitor...');
              consoleService.success(`Dispatched workflow ${wf} on ${this.#owner}/${this.#repo} (${branch})`);
              
              setTimeout(async () => {
                await this.#fetchRuns();
                this.#startPolling();
                this.#render();
              }, 2500);
            } catch (err) {
              toast.error('Dispatch failed', err.message || 'Check repository write permissions');
            }
          }
        }
      ]
    });
  }

  #esc(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
}

export default GHActionsWizardPage;
