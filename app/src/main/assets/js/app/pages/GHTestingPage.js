/**
 * GHTestingPage.js — Real End-to-End GitHub Testing & Diagnostics Page for RONi.
 *
 * Implements:
 * - [ Connect GitHub ]: Validates connection, repository, actions, and packages access.
 * - [ Run Full Test ]: Real E2E pipeline execution (generate, push, trigger, monitor, verify).
 * - Exact Stage Status Table with PASS, FAIL, NOT TESTED, NOT AVAILABLE states.
 * - Source generator tracing & error diagnostics.
 * - Secure runtime-only credential handling (no persistence, disconnect action).
 *
 * Layout uses the app design system (card / form / btn classes).
 * All styling lives in css/pages/ghtesting.css — no inline styles in this file.
 */

import { githubAuth } from '../modules/github/GitHubAuth.js';
import { githubApi } from '../modules/github/GitHubApi.js';
import { projectService } from '../modules/storage/ProjectService.js';
import { gitHubTestRunner, TEST_STATUS } from '../modules/github/GitHubTestRunner.js';
import { toast } from '../ui/ToastService.js';
import { modal } from '../ui/ModalService.js';

export class GHTestingPage {
  #container = null;
  #currentStages = {};
  #activeRun = null;
  #lastFailure = null;
  #lastRawLogs = null;
  #isRunning = false;

  constructor() {
    this.#resetStages();
  }

  #resetStages() {
    this.#currentStages = {
      connection:     { name: 'GitHub Connection', status: githubAuth.isAuthenticated ? TEST_STATUS.PASS : TEST_STATUS.NOT_AVAILABLE, details: githubAuth.isAuthenticated ? `Connected as @${githubAuth.user?.login}` : 'Credentials not configured' },
      repoAccess:     { name: 'Repository Access', status: TEST_STATUS.NOT_TESTED, details: '' },
      actionsAccess:  { name: 'Actions Access',    status: TEST_STATUS.NOT_TESTED, details: '' },
      generateProject:{ name: 'Generate Project',  status: TEST_STATUS.NOT_TESTED, details: '' },
      androidSdkSetup:{ name: 'Android SDK Setup', status: TEST_STATUS.NOT_TESTED, details: '' },
      gradleBuild:    { name: 'Gradle Build',      status: TEST_STATUS.NOT_TESTED, details: '' },
      assembleRelease:{ name: 'assembleRelease',   status: TEST_STATUS.NOT_TESTED, details: '' },
      mavenPublish:   { name: 'Maven Publish',     status: TEST_STATUS.NOT_TESTED, details: '' },
      overallResult:  { name: 'Overall Result',    status: TEST_STATUS.NOT_TESTED, details: '' }
    };
  }

  // ── Init & Render ──────────────────────────────────────────────────────────

  async init(container, params) {
    return this.render(container, params);
  }

  async render(container) {
    this.#container = container;
    const project = projectService.current;

    // Default repo information from active project if available
    const defaultOwner = project?.author || (githubAuth.user ? githubAuth.user.login : '');
    const defaultRepo  = project?.repoName || (project?.name ? project.name.toLowerCase().replace(/[^a-z0-9_-]/g, '-') : '');

    this.#container.innerHTML = `
      <div class="page-container gh-testing-page">

        <!-- Header -->
        <div class="page-header">
          <div class="ght-header-main">
            <span class="material-symbols-outlined ght-header-icon">fact_check</span>
            <div class="ght-header-text">
              <h1 class="page-title">RONi Full Library Test</h1>
              <p class="page-subtitle">Real end-to-end GitHub Actions pipeline verification &amp; source generator diagnostics</p>
            </div>
          </div>
          <div class="ght-header-actions">
            <button type="button" class="btn btn-secondary" id="btn-gh-disconnect"${githubAuth.isAuthenticated ? '' : ' hidden'}>
              <span class="material-symbols-outlined">link_off</span> Disconnect / Clear Credential
            </button>
          </div>
        </div>

        <!-- Configuration & Credential Card -->
        <div class="card ght-config-card">
          <div class="card-header">
            <span class="material-symbols-outlined">lock</span>
            GitHub Connection &amp; Test Target
          </div>
          <div class="card-body">

            <div class="ght-security-note">
              <span class="material-symbols-outlined">verified_user</span>
              <div>
                <strong>Secure Runtime Authentication:</strong>
                Personal Access Tokens are held strictly in active memory. They are <em>never</em> committed, never saved to localStorage/IndexedDB, never included in generated projects, and never bundled into ZIP exports. Required scopes: <code>repo</code>, <code>workflow</code> (plus <code>read:packages</code> if testing Maven).
              </div>
            </div>

            <div class="ght-form-grid">
              <div class="form-group">
                <label class="form-label" for="input-gh-token">GitHub Personal Access Token</label>
                <input type="password" id="input-gh-token" class="input" placeholder="${githubAuth.isAuthenticated ? '••••••••••••••••••••••••' : 'ghp_xxxxxxxxxxxxxxxxxxxx'}" value="" autocomplete="off" />
                <div class="form-hint" id="token-status-hint">
                  ${githubAuth.isAuthenticated ? `<span class="ght-hint-connected">✓ Connected as @${githubAuth.user?.login}</span>` : 'Enter token for real testing'}
                </div>
              </div>

              <div class="form-group">
                <label class="form-label">Target Repository (Owner / Repo)</label>
                <div class="ght-repo-row">
                  <input type="text" id="input-gh-owner" class="input" placeholder="Owner (e.g. Octocat)" value="${this.#escAttr(defaultOwner)}" />
                  <span class="ght-repo-slash">/</span>
                  <input type="text" id="input-gh-repo" class="input" placeholder="Repository Name" value="${this.#escAttr(defaultRepo)}" />
                </div>
              </div>

              <div class="form-group">
                <label class="form-label" for="input-gh-branch">Branch &amp; Options</label>
                <input type="text" id="input-gh-branch" class="input" placeholder="Branch" value="main" />
                <label class="ght-check-row">
                  <input type="checkbox" id="check-enable-maven" />
                  <span>Enable Maven / GitHub Packages Publishing Test</span>
                </label>
              </div>
            </div>

            <!-- Primary Actions -->
            <div class="ght-actions">
              <button type="button" class="btn btn-secondary" id="btn-connect-gh">
                <span class="material-symbols-outlined">cable</span> Connect GitHub
              </button>
              <button type="button" class="btn btn-primary" id="btn-run-full-test">
                <span class="material-symbols-outlined">play_arrow</span> Run Full Test
              </button>
              <button type="button" class="btn btn-secondary ght-btn-danger" id="btn-cancel-test" hidden>
                <span class="material-symbols-outlined">stop</span> Stop Test
              </button>
              <span class="ght-runner-indicator" id="test-runner-indicator" hidden>
                <span class="spinner ght-spinner"></span>
                <span id="test-runner-msg">Running pipeline...</span>
              </span>
            </div>
          </div>
        </div>

        <!-- Failure & Diagnostics Panel (Conditional) -->
        <div id="diagnostics-panel" class="ght-diagnostics" hidden></div>

        <!-- Real Test Results Table -->
        <div class="card ght-results-card">
          <div class="card-header ght-results-header">
            <h2 class="ght-results-title">
              <span class="material-symbols-outlined">terminal</span>
              RONi Full Library Test
            </h2>
            <div class="ght-results-sub">
              Strict Real Results (No Simulation)
            </div>
          </div>

          <div class="ght-table-wrap">
            <table class="ght-table" id="table-test-stages">
              <thead>
                <tr>
                  <th class="ght-col-stage">Stage</th>
                  <th class="ght-col-status">Status</th>
                  <th class="ght-col-details">Details / Evidence</th>
                </tr>
              </thead>
              <tbody id="stages-table-body">
                ${this.#renderStageRows()}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    `;

    this.#bindEvents();
  }

  // ── Render Stage Rows ──────────────────────────────────────────────────────

  #renderStageRows() {
    const stageOrder = [
      'connection',
      'repoAccess',
      'actionsAccess',
      'generateProject',
      'androidSdkSetup',
      'gradleBuild',
      'assembleRelease',
      'mavenPublish',
      'overallResult'
    ];

    return stageOrder.map(key => {
      const stage = this.#currentStages[key] || { name: key, status: TEST_STATUS.NOT_TESTED, details: '' };
      const isOverall = key === 'overallResult';

      return `
        <tr class="${isOverall ? 'ght-row-overall' : ''}" id="row-${key}">
          <td class="ght-col-stage">${isOverall ? '<strong>Overall Result</strong>' : stage.name}</td>
          <td class="ght-col-status">${this.#badgeHtml(stage.status)}</td>
          <td class="ght-col-details ght-stage-details" id="details-${key}">
            ${stage.details || (stage.status === TEST_STATUS.NOT_TESTED ? '—' : '')}
          </td>
        </tr>
      `;
    }).join('');
  }

  #badgeHtml(status) {
    switch (status) {
      case TEST_STATUS.PASS:
        return '<span class="ght-badge ght-badge-pass">✓ PASS</span>';
      case TEST_STATUS.FAIL:
        return '<span class="ght-badge ght-badge-fail">✗ FAIL</span>';
      case TEST_STATUS.RUNNING:
        return '<span class="ght-badge ght-badge-running">⏳ RUNNING</span>';
      case TEST_STATUS.NOT_AVAILABLE:
        return '<span class="ght-badge ght-badge-notavail">⚠ NOT AVAILABLE</span>';
      case TEST_STATUS.NOT_TESTED:
      default:
        return '<span class="ght-badge ght-badge-nottested">○ NOT TESTED</span>';
    }
  }

  // ── Event Bindings ─────────────────────────────────────────────────────────

  #bindEvents() {
    const btnConnect    = this.#container.querySelector('#btn-connect-gh');
    const btnRunTest    = this.#container.querySelector('#btn-run-full-test');
    const btnDisconnect = this.#container.querySelector('#btn-gh-disconnect');
    const btnCancel     = this.#container.querySelector('#btn-cancel-test');

    btnConnect?.addEventListener('click', () => this.#handleConnect());
    btnRunTest?.addEventListener('click', () => this.#handleRunFullTest());
    btnDisconnect?.addEventListener('click', () => this.#handleDisconnect());
    btnCancel?.addEventListener('click', () => {
      gitHubTestRunner.cancel();
      toast.info('Cancelling test...');
    });
  }

  // ── Connect GitHub Handler ─────────────────────────────────────────────────

  async #handleConnect() {
    const tokenInput = this.#container.querySelector('#input-gh-token');
    const ownerInput = this.#container.querySelector('#input-gh-owner');
    const repoInput  = this.#container.querySelector('#input-gh-repo');
    const mavenCheck = this.#container.querySelector('#check-enable-maven');

    const rawToken = tokenInput?.value?.trim();
    const owner    = ownerInput?.value?.trim();
    const repo     = repoInput?.value?.trim();
    const enablePackages = mavenCheck?.checked || false;

    if (rawToken) {
      try {
        await githubAuth.setRuntimeToken(rawToken);
        toast.success(`Connected as @${githubAuth.user.login}`);
      } catch (err) {
        toast.error(`Authentication failed: ${err.message}`);
        this.#updateStageUI('connection', TEST_STATUS.FAIL, err.message);
        return;
      }
    } else if (!githubAuth.token) {
      toast.warning('Please enter a GitHub Personal Access Token');
      this.#updateStageUI('connection', TEST_STATUS.NOT_AVAILABLE, 'No token entered');
      return;
    }

    toast.info('Validating GitHub access...');
    const result = await gitHubTestRunner.validateConnection({
      owner,
      repo,
      enablePackages
    });

    this.#updateStageUI('connection', result.connection.status, result.connection.message);
    this.#updateStageUI('repoAccess', result.repoAccess.status, result.repoAccess.message);
    this.#updateStageUI('actionsAccess', result.actionsAccess.status, result.actionsAccess.message);

    if (result.user) {
      const hint = this.#container.querySelector('#token-status-hint');
      if (hint) hint.innerHTML = `<span class="ght-hint-connected">✓ Connected as @${result.user.login}</span>`;
      const btnDisconnect = this.#container.querySelector('#btn-gh-disconnect');
      if (btnDisconnect) btnDisconnect.hidden = false;
    }

    if (result.connection.status === TEST_STATUS.PASS && result.repoAccess.status === TEST_STATUS.PASS) {
      toast.success('GitHub connection & repository access verified!');
    } else {
      toast.error('Validation completed with errors — check stage table');
    }
  }

  // ── Run Full Test Handler ──────────────────────────────────────────────────

  async #handleRunFullTest() {
    if (this.#isRunning) return;

    const tokenInput = this.#container.querySelector('#input-gh-token');
    const ownerInput = this.#container.querySelector('#input-gh-owner');
    const repoInput  = this.#container.querySelector('#input-gh-repo');
    const branchInput= this.#container.querySelector('#input-gh-branch');
    const mavenCheck = this.#container.querySelector('#check-enable-maven');

    const rawToken = tokenInput?.value?.trim();
    const owner    = ownerInput?.value?.trim();
    const repo     = repoInput?.value?.trim();
    const branch   = branchInput?.value?.trim() || 'main';
    const enableMavenPublish = mavenCheck?.checked || false;

    if (rawToken) {
      try {
        await githubAuth.setRuntimeToken(rawToken);
      } catch (err) {
        toast.error(`Authentication error: ${err.message}`);
        this.#updateStageUI('connection', TEST_STATUS.FAIL, err.message);
        return;
      }
    }

    if (!githubAuth.token && !githubApi._token) {
      toast.warning('GitHub credentials not configured');
      this.#updateStageUI('connection', TEST_STATUS.NOT_AVAILABLE, 'No GitHub token provided');
      this.#updateStageUI('repoAccess', TEST_STATUS.NOT_AVAILABLE, 'Requires GitHub credentials');
      this.#updateStageUI('actionsAccess', TEST_STATUS.NOT_AVAILABLE, 'Requires GitHub credentials');
      this.#updateStageUI('overallResult', TEST_STATUS.NOT_AVAILABLE, 'Testing requires valid GitHub credentials');
      return;
    }

    if (!owner || !repo) {
      toast.warning('Please specify both Repository Owner and Repository Name');
      return;
    }

    // Reset stages for fresh run
    this.#resetStages();
    this.#updateStageUI('overallResult', TEST_STATUS.RUNNING, 'Executing test pipeline...');
    this.#hideDiagnostics();

    this.#setRunningState(true);

    try {
      const result = await gitHubTestRunner.runFullTest({
        owner,
        repo,
        branch,
        enableMavenPublish,
        onStageUpdate: (key, stage) => {
          this.#updateStageUI(key, stage.status, stage.details);
        }
      });

      if (result.success) {
        toast.success('Full Library Test PASSED! Real GitHub Actions workflow succeeded.');
        this.#updateStageUI('overallResult', TEST_STATUS.PASS, 'All pipeline stages verified and passed!');
      } else {
        toast.error('Test pipeline failed — see diagnostics below');
        this.#showDiagnostics(result.failure);
      }
    } catch (err) {
      toast.error(`Pipeline execution failed: ${err.message}`);
      this.#updateStageUI('overallResult', TEST_STATUS.FAIL, err.message);
    } finally {
      this.#setRunningState(false);
    }
  }

  // ── Disconnect Handler ─────────────────────────────────────────────────────

  async #handleDisconnect() {
    await githubAuth.clearCredentials();
    const tokenInput = this.#container.querySelector('#input-gh-token');
    if (tokenInput) tokenInput.value = '';
    const hint = this.#container.querySelector('#token-status-hint');
    if (hint) hint.textContent = 'Credentials cleared. Connect again to test.';
    const btnDisconnect = this.#container.querySelector('#btn-gh-disconnect');
    if (btnDisconnect) btnDisconnect.hidden = true;

    this.#resetStages();
    const tbody = this.#container.querySelector('#stages-table-body');
    if (tbody) tbody.innerHTML = this.#renderStageRows();
    this.#hideDiagnostics();
    toast.info('GitHub credentials disconnected and cleared from memory');
  }

  // ── Diagnostics Display ────────────────────────────────────────────────────

  #showDiagnostics(failure) {
    if (!failure) return;
    this.#lastFailure = failure;
    this.#lastRawLogs = failure.diagnostics?.logs || failure.error;

    const panel = this.#container.querySelector('#diagnostics-panel');
    if (!panel) return;

    const workflowName = failure.diagnostics?.workflow || 'CI / Build Pipeline';
    const stepName     = failure.diagnostics?.step || failure.stage;
    const errorText    = failure.error || 'Execution encountered an unrecoverable failure';
    const sourceFile   = failure.diagnostics?.sourceTemplate || 'AndroidLibraryTemplate.js';
    const sourcePath   = failure.diagnostics?.sourcePath || 'js/app/modules/generator/templates/AndroidLibraryTemplate.js';
    const advice       = failure.diagnostics?.advice || 'Diagnose the source generator error, apply the fix to the source template, regenerate a fresh project, and re-run test.';

    panel.hidden = false;
    panel.innerHTML = `
      <div class="ght-diag-panel">
        <div class="ght-diag-title">
          <span class="material-symbols-outlined">error</span>
          ❌ ${this.#escapeHtml(failure.stage)} FAILED
        </div>

        <div class="ght-diag-meta">
          <div><strong>Workflow:</strong> <code>${this.#escapeHtml(workflowName)}</code></div>
          <div><strong>Step:</strong> <code>${this.#escapeHtml(stepName)}</code></div>
          <div><strong>Source Generator:</strong> <code>${this.#escapeHtml(sourceFile)}</code></div>
          <div><strong>Template Path:</strong> <code>${this.#escapeHtml(sourcePath)}</code></div>
        </div>

        <div class="ght-diag-error">
          <div class="ght-diag-error-label">Actual Error:</div>
          <pre class="ght-diag-error-text">${this.#escapeHtml(errorText)}</pre>
        </div>

        <div class="ght-diag-advice">
          <strong>Source-Fix Guidance:</strong> ${this.#escapeHtml(advice)}
        </div>

        <div class="ght-diag-actions">
          <button type="button" class="btn btn-secondary ght-btn-danger" id="btn-view-logs">
            <span class="material-symbols-outlined">description</span> View Logs
          </button>
          <button type="button" class="btn btn-primary ght-btn-retry" id="btn-run-again">
            <span class="material-symbols-outlined">refresh</span> Run Test Again
          </button>
        </div>
      </div>
    `;

    panel.querySelector('#btn-view-logs')?.addEventListener('click', () => this.#showLogsModal());
    panel.querySelector('#btn-run-again')?.addEventListener('click', () => this.#handleRunFullTest());
  }

  #hideDiagnostics() {
    const panel = this.#container?.querySelector('#diagnostics-panel');
    if (panel) panel.hidden = true;
  }

  #showLogsModal() {
    const content = `
      <div class="ght-logs-modal">
        <h3 class="ght-logs-title">Workflow Job Execution Logs</h3>
        <pre class="ght-logs-text">${this.#escapeHtml(this.#lastRawLogs || 'No log details available.')}</pre>
      </div>
    `;

    modal.open({
      title: 'Workflow Execution Logs',
      content,
      size: 'large',
      buttons: [{ label: 'Close', variant: 'secondary', onClick: () => modal.close() }]
    });
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  #updateStageUI(key, status, details = '') {
    if (!this.#currentStages[key]) {
      this.#currentStages[key] = { name: key, status, details };
    } else {
      this.#currentStages[key].status = status;
      if (details) this.#currentStages[key].details = details;
    }

    const row = this.#container?.querySelector(`#row-${key}`);
    if (row) {
      const badgeTd = row.children[1];
      const detailsTd = row.children[2];
      if (badgeTd) badgeTd.innerHTML = this.#badgeHtml(status);
      if (detailsTd) detailsTd.textContent = details || '—';
    }
  }

  #setRunningState(running) {
    this.#isRunning = running;
    const btnRun   = this.#container?.querySelector('#btn-run-full-test');
    const btnCancel= this.#container?.querySelector('#btn-cancel-test');
    const indicator= this.#container?.querySelector('#test-runner-indicator');

    if (btnRun) btnRun.disabled = running;
    if (btnCancel) btnCancel.hidden = !running;
    if (indicator) indicator.hidden = !running;
  }

  #escapeHtml(str) {
    return String(str || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  #escAttr(str) {
    return String(str || '')
      .replace(/&/g, '&amp;')
      .replace(/"/g, '&quot;')
      .replace(/</g, '&lt;');
  }
}

export default GHTestingPage;
