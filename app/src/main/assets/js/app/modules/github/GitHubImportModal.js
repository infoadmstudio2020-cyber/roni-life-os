/**
 * GitHubImportModal.js — Interactive dialog flow for importing a GitHub repository.
 *
 * Flow:
 * 1. URL Entry & Validation (with PAT support for private repos)
 * 2. Repository Metadata & Branch Selection
 * 3. Real-time Download Progress (fetching tree, downloading files)
 * 4. Android Project Model Analysis & Completion summary
 * 5. Direct launch actions: Open in Explorer, Open in Editor, Analyze
 */

import { modal } from '../../ui/ModalService.js';
import { toast } from '../../ui/ToastService.js';
import { eventBus } from '../../core/EventBus.js';
import { githubAuth } from './GitHubAuth.js';
import { GitHubImporter } from './GitHubImporter.js';

export class GitHubImportModal {
  #backdrop = null;
  #state = {
    step: 1, // 1: url, 2: branch, 3: progress, 4: complete
    parsed: null,
    repoData: null,
    branches: [],
    selectedBranch: 'main',
    token: '',
    files: null,
    importedProject: null,
    analysis: null,
  };

  /**
   * Open the GitHub Repository Import modal.
   * @param {object} [options]
   * @param {string} [options.initialUrl] - pre-populate URL
   */
  open(options = {}) {
    this.#state = {
      step: 1,
      parsed: null,
      repoData: null,
      branches: [],
      selectedBranch: 'main',
      token: '',
      files: null,
      importedProject: null,
      analysis: null,
    };

    const initialUrl = options.initialUrl || '';

    this.#backdrop = modal.open({
      icon: 'cloud_download',
      title: 'Import GitHub Repository',
      size: 'lg',
      body: this.#renderBody(initialUrl),
      footer: this.#renderFooterButtons(),
    });

    this.#bindEvents();
    if (initialUrl) {
      setTimeout(() => this.#handleValidate(), 100);
    }
  }

  close() {
    if (this.#backdrop) {
      modal.close(this.#backdrop);
      this.#backdrop = null;
    }
  }

  #renderBody(initialUrl = '') {
    const isAuth = githubAuth.isAuthenticated;
    const authUser = githubAuth.user?.login || '';

    return `
      <div class="gh-import-container" id="gh-import-root">
        <!-- Step Indicator -->
        <div class="gh-import-steps">
          <div class="gh-import-step-dot ${this.#state.step >= 1 ? 'active' : ''}" data-step="1">
            <span class="step-num">1</span>
            <span class="step-label">Repository</span>
          </div>
          <div class="gh-import-step-line ${this.#state.step >= 2 ? 'active' : ''}"></div>
          <div class="gh-import-step-dot ${this.#state.step >= 2 ? 'active' : ''}" data-step="2">
            <span class="step-num">2</span>
            <span class="step-label">Branch</span>
          </div>
          <div class="gh-import-step-line ${this.#state.step >= 3 ? 'active' : ''}"></div>
          <div class="gh-import-step-dot ${this.#state.step >= 3 ? 'active' : ''}" data-step="3">
            <span class="step-num">3</span>
            <span class="step-label">Download</span>
          </div>
          <div class="gh-import-step-line ${this.#state.step >= 4 ? 'active' : ''}"></div>
          <div class="gh-import-step-dot ${this.#state.step >= 4 ? 'active' : ''}" data-step="4">
            <span class="step-num">4</span>
            <span class="step-label">Ready</span>
          </div>
        </div>

        <div id="gh-import-content" style="margin-top: var(--sp-4);">
          ${this.#renderStepContent(initialUrl)}
        </div>
      </div>
    `;
  }

  #renderStepContent(initialUrl = '') {
    const isAuth = githubAuth.isAuthenticated;
    const authUser = githubAuth.user?.login || '';

    if (this.#state.step === 1) {
      return `
        <div class="gh-import-panel">
          <p class="gh-import-desc" style="color:var(--text-secondary); margin-bottom:var(--sp-3); font-size:var(--text-sm);">
            Import an Android library or application project directly from GitHub. Enter the repository URL to begin.
          </p>

          <div class="form-group" style="margin-bottom:var(--sp-3);">
            <label class="form-label" style="font-weight:600; display:flex; justify-content:space-between;">
              <span>GitHub Repository URL or "owner/repo"</span>
              <span style="font-weight:normal; font-size:var(--text-xs); color:var(--text-muted);">Required</span>
            </label>
            <div class="input-with-icon" style="position:relative;">
              <span class="material-symbols-outlined" style="position:absolute; left:10px; top:50%; transform:translateY(-50%); font-size:18px; color:var(--text-muted); pointer-events:none;">link</span>
              <input class="input" id="gh-import-url-input" type="text"
                     placeholder="https://github.com/owner/repository or owner/repo"
                     value="${initialUrl}"
                     style="padding-left:36px; width:100%;" />
            </div>
          </div>

          <!-- Quick Samples -->
          <div style="margin-bottom:var(--sp-4); display:flex; gap:var(--sp-2); align-items:center; flex-wrap:wrap;">
            <span style="font-size:var(--text-xs); color:var(--text-muted);">Try example:</span>
            <button type="button" class="btn btn-ghost btn-xs gh-sample-chip" data-sample="jitpack/android-example" style="font-size:11px; padding:2px 8px; border:1px solid var(--border); border-radius:12px;">jitpack/android-example</button>
            <button type="button" class="btn btn-ghost btn-xs gh-sample-chip" data-sample="https://github.com/google/iosched" style="font-size:11px; padding:2px 8px; border:1px solid var(--border); border-radius:12px;">google/iosched</button>
          </div>

          <!-- Authentication & Private Repo Section -->
          <div class="card" style="padding:var(--sp-3); background:var(--bg-surface); border:1px solid var(--border); margin-bottom:var(--sp-3);">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:var(--sp-2);">
              <div style="display:flex; align-items:center; gap:var(--sp-2);">
                <span class="material-symbols-outlined" style="font-size:16px; color:${isAuth ? 'var(--success)' : 'var(--text-muted)'};">
                  ${isAuth ? 'verified_user' : 'lock'}
                </span>
                <span style="font-size:var(--text-sm); font-weight:500;">
                  ${isAuth ? `Authenticated as <strong>@${authUser}</strong>` : 'Authentication & Private Repositories'}
                </span>
              </div>
              <button type="button" class="btn btn-ghost btn-xs" id="gh-toggle-token-btn" style="font-size:var(--text-xs); color:var(--accent);">
                ${this.#state.token ? 'Edit Token' : '+ Add Token'}
              </button>
            </div>

            <div id="gh-token-input-wrapper" style="display:${this.#state.token ? 'block' : 'none'}; margin-top:var(--sp-2); border-top:1px dashed var(--border); padding-top:var(--sp-2);">
              <label class="form-label" style="font-size:var(--text-xs); color:var(--text-secondary);">
                Personal Access Token (classic with <code>repo</code> scope or fine-grained):
              </label>
              <input class="input input-sm" id="gh-import-token-input" type="password"
                     placeholder="ghp_xxxxxxxxxxxxxxxxxxxx"
                     value="${this.#state.token}"
                     style="width:100%; font-family:var(--font-mono); font-size:var(--text-xs);" />
              <div style="font-size:11px; color:var(--text-muted); margin-top:4px;">
                Token is only used in this browser session. It is never stored permanently or sent elsewhere.
              </div>
            </div>
          </div>

          <div id="gh-validate-error-box" style="display:none; margin-top:var(--sp-2);"></div>
        </div>
      `;
    }

    if (this.#state.step === 2) {
      const repo = this.#state.repoData || {};
      const branches = this.#state.branches || [];

      return `
        <div class="gh-import-panel">
          <!-- Repo Summary Card -->
          <div class="card" style="padding:var(--sp-3); background:var(--bg-surface); border:1px solid var(--border); margin-bottom:var(--sp-3);">
            <div style="display:flex; justify-content:space-between; align-items:flex-start;">
              <div>
                <div style="font-size:var(--text-base); font-weight:600; display:flex; align-items:center; gap:var(--sp-2);">
                  <span class="material-symbols-outlined" style="color:var(--accent); font-size:18px;">book</span>
                  <span>${repo.full_name || repo.name}</span>
                  <span class="badge ${repo.private ? 'badge-warning' : 'badge-secondary'}" style="font-size:10px;">
                    ${repo.private ? 'Private' : 'Public'}
                  </span>
                </div>
                <div style="font-size:var(--text-xs); color:var(--text-secondary); margin-top:4px;">
                  ${repo.description || 'No description provided'}
                </div>
              </div>
              <div style="display:flex; gap:var(--sp-2); font-size:var(--text-xs); color:var(--text-muted);">
                <span>★ ${repo.stargazers_count ?? 0}</span>
                <span>⑂ ${repo.forks_count ?? 0}</span>
              </div>
            </div>
          </div>

          <!-- Branch Selection -->
          <div class="form-group" style="margin-bottom:var(--sp-3);">
            <label class="form-label" style="font-weight:600; display:flex; justify-content:space-between;">
              <span>Select Branch to Import</span>
              <span style="font-size:var(--text-xs); color:var(--text-muted);">${branches.length} branches available</span>
            </label>
            <div class="select-wrapper">
              <select class="select" id="gh-branch-select" style="width:100%;">
                ${branches.map(b => `
                  <option value="${b.name}" ${b.name === this.#state.selectedBranch ? 'selected' : ''}>
                    ${b.name} ${b.isDefault ? '(default)' : ''}
                  </option>
                `).join('')}
              </select>
            </div>
          </div>

          <div class="card" style="padding:var(--sp-3); background:rgba(59,130,246,0.06); border:1px solid rgba(59,130,246,0.2); border-radius:var(--radius-md);">
            <div style="display:flex; gap:var(--sp-2); align-items:flex-start;">
              <span class="material-symbols-outlined" style="color:var(--accent); font-size:18px; margin-top:2px;">info</span>
              <div style="font-size:var(--text-xs); color:var(--text-secondary); line-height:1.5;">
                Files will be imported directly into the Project Model. All Gradle build files, Kotlin/Java sources, manifests, and resources will be available in the Explorer and Editor.
              </div>
            </div>
          </div>
        </div>
      `;
    }

    if (this.#state.step === 3) {
      return `
        <div class="gh-import-panel" style="text-align:center; padding:var(--sp-6) var(--sp-2);">
          <div class="spinner spinner-lg" style="margin:0 auto var(--sp-4);"></div>
          <h4 style="font-size:var(--text-base); font-weight:600; margin-bottom:var(--sp-2);" id="gh-import-progress-title">
            Downloading Repository Files...
          </h4>
          <p style="font-size:var(--text-xs); color:var(--text-secondary); margin-bottom:var(--sp-4);" id="gh-import-progress-status">
            Connecting to GitHub API...
          </p>

          <!-- Progress Bar -->
          <div style="background:var(--bg-surface); border:1px solid var(--border); border-radius:var(--radius-full); height:12px; overflow:hidden; margin-bottom:var(--sp-3); position:relative;">
            <div id="gh-import-progress-bar" style="background:var(--accent); height:100%; width:5%; transition:width 0.2s ease;"></div>
          </div>

          <div style="display:flex; justify-content:space-between; font-size:11px; color:var(--text-muted);">
            <span id="gh-import-progress-file">Preparing...</span>
            <span id="gh-import-progress-percent">0%</span>
          </div>
        </div>
      `;
    }

    if (this.#state.step === 4) {
      const proj = this.#state.importedProject || {};
      const analysis = this.#state.analysis || {};
      const fileCount = Object.keys(proj.files || {}).length;
      const modules = analysis.modules || [];

      return `
        <div class="gh-import-panel">
          <div style="text-align:center; margin-bottom:var(--sp-4);">
            <div style="width:52px; height:52px; border-radius:50%; background:rgba(34,197,94,0.15); border:2px solid var(--success); display:flex; align-items:center; justify-content:center; margin:0 auto var(--sp-3);">
              <span class="material-symbols-outlined" style="color:var(--success); font-size:28px;">check_circle</span>
            </div>
            <h3 style="font-size:var(--text-lg); font-weight:600; color:var(--text-primary); margin-bottom:4px;">
              Repository Imported Successfully!
            </h3>
            <p style="font-size:var(--text-xs); color:var(--text-secondary);">
              ${proj.name} is now loaded in your workspace.
            </p>
          </div>

          <!-- Project Stats Grid -->
          <div style="display:grid; grid-template-columns:repeat(2, 1fr); gap:var(--sp-2); margin-bottom:var(--sp-4);">
            <div class="card" style="padding:var(--sp-2) var(--sp-3); background:var(--bg-surface); border:1px solid var(--border);">
              <div style="font-size:11px; color:var(--text-muted);">Project Type</div>
              <div style="font-weight:600; font-size:var(--text-sm); text-transform:capitalize;">${proj.projectType || 'library'}</div>
            </div>
            <div class="card" style="padding:var(--sp-2) var(--sp-3); background:var(--bg-surface); border:1px solid var(--border);">
              <div style="font-size:11px; color:var(--text-muted);">Total Files Loaded</div>
              <div style="font-weight:600; font-size:var(--text-sm);">${fileCount} files</div>
            </div>
            <div class="card" style="padding:var(--sp-2) var(--sp-3); background:var(--bg-surface); border:1px solid var(--border);">
              <div style="font-size:11px; color:var(--text-muted);">Package Name</div>
              <div style="font-weight:600; font-size:var(--text-xs); word-break:break-all;">${proj.packageName || 'com.example'}</div>
            </div>
            <div class="card" style="padding:var(--sp-2) var(--sp-3); background:var(--bg-surface); border:1px solid var(--border);">
              <div style="font-size:11px; color:var(--text-muted);">Modules Detected</div>
              <div style="font-weight:600; font-size:var(--text-sm);">${modules.length > 0 ? modules.map(m => m.name).join(', ') : 'Root module'}</div>
            </div>
          </div>

          <!-- Quick Navigation Actions -->
          <div style="display:flex; flex-direction:column; gap:var(--sp-2);">
            <button type="button" class="btn btn-primary btn-block" id="gh-action-open-explorer" style="justify-content:center;">
              <span class="material-symbols-outlined">folder_open</span>
              Explore Project in File Explorer
            </button>
            <div style="display:flex; gap:var(--sp-2);">
              <button type="button" class="btn btn-secondary" id="gh-action-open-editor" style="flex:1; justify-content:center;">
                <span class="material-symbols-outlined">code</span>
                Open in Editor
              </button>
              <button type="button" class="btn btn-secondary" id="gh-action-open-analyzer" style="flex:1; justify-content:center;">
                <span class="material-symbols-outlined">analytics</span>
                Analyze Project
              </button>
            </div>
          </div>
        </div>
      `;
    }

    return '';
  }

  #renderFooterButtons() {
    if (this.#state.step === 1) {
      return [
        { label: 'Cancel', type: 'secondary', close: true },
        { label: 'Validate & Continue', type: 'primary', action: 'validate' },
      ];
    }
    if (this.#state.step === 2) {
      return [
        { label: 'Back', type: 'secondary', action: 'step1' },
        { label: 'Import Project', type: 'primary', action: 'import' },
      ];
    }
    if (this.#state.step === 3) {
      return []; // Progress in motion, no footer buttons
    }
    if (this.#state.step === 4) {
      return [
        { label: 'Done', type: 'secondary', close: true },
      ];
    }
    return [];
  }

  #updateStep(step) {
    this.#state.step = step;
    if (!this.#backdrop) return;

    // Update body
    const contentEl = this.#backdrop.querySelector('#gh-import-content');
    if (contentEl) {
      contentEl.innerHTML = this.#renderStepContent();
    }

    // Update step dots
    this.#backdrop.querySelectorAll('.gh-import-step-dot').forEach(dot => {
      const s = parseInt(dot.dataset.step, 10);
      dot.classList.toggle('active', this.#state.step >= s);
    });
    this.#backdrop.querySelectorAll('.gh-import-step-line').forEach((line, idx) => {
      line.classList.toggle('active', this.#state.step >= idx + 2);
    });

    // Update footer
    const footerEl = this.#backdrop.querySelector('.modal-footer');
    if (footerEl) {
      const buttons = this.#renderFooterButtons();
      footerEl.innerHTML = buttons.map(btn =>
        `<button class="btn btn-${btn.type || 'secondary'} ${btn.close ? 'modal-close-btn' : ''}" data-action="${btn.action || ''}">${btn.label}</button>`
      ).join('');
    }

    this.#bindEvents();
  }

  #bindEvents() {
    if (!this.#backdrop) return;

    // Close button handlers
    this.#backdrop.querySelectorAll('.modal-close-btn').forEach(btn => {
      btn.addEventListener('click', () => this.close());
    });

    // Step 1: Sample Chips
    this.#backdrop.querySelectorAll('.gh-sample-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        const urlInput = this.#backdrop.querySelector('#gh-import-url-input');
        if (urlInput) {
          urlInput.value = chip.dataset.sample;
          urlInput.focus();
        }
      });
    });

    // Step 1: Token toggle
    const toggleTokenBtn = this.#backdrop.querySelector('#gh-toggle-token-btn');
    const tokenWrapper = this.#backdrop.querySelector('#gh-token-input-wrapper');
    if (toggleTokenBtn && tokenWrapper) {
      toggleTokenBtn.addEventListener('click', () => {
        const isHidden = tokenWrapper.style.display === 'none';
        tokenWrapper.style.display = isHidden ? 'block' : 'none';
        if (isHidden) {
          this.#backdrop.querySelector('#gh-import-token-input')?.focus();
        }
      });
    }

    // Step 1: URL input enter key
    const urlInput = this.#backdrop.querySelector('#gh-import-url-input');
    urlInput?.addEventListener('keydown', e => {
      if (e.key === 'Enter') {
        this.#handleValidate();
      }
    });

    // Footer actions
    this.#backdrop.querySelector('[data-action="validate"]')?.addEventListener('click', () => {
      this.#handleValidate();
    });

    this.#backdrop.querySelector('[data-action="step1"]')?.addEventListener('click', () => {
      this.#updateStep(1);
    });

    this.#backdrop.querySelector('[data-action="import"]')?.addEventListener('click', () => {
      this.#handleImport();
    });

    // Step 2: Branch selector change
    const branchSelect = this.#backdrop.querySelector('#gh-branch-select');
    branchSelect?.addEventListener('change', e => {
      this.#state.selectedBranch = e.target.value;
    });

    // Step 4: Completion actions
    this.#backdrop.querySelector('#gh-action-open-explorer')?.addEventListener('click', () => {
      this.close();
      eventBus.emit('router:navigate', { pageId: 'explorer' });
    });

    this.#backdrop.querySelector('#gh-action-open-editor')?.addEventListener('click', () => {
      this.close();
      // Try to open root build.gradle or settings.gradle
      const files = this.#state.importedProject?.files || {};
      const candidate = Object.keys(files).find(p => p === 'build.gradle' || p === 'build.gradle.kts' || p === 'settings.gradle') || Object.keys(files)[0];
      if (candidate) {
        eventBus.emit('editor:openFile', { path: candidate, content: files[candidate] });
      }
      eventBus.emit('router:navigate', { pageId: 'editor' });
    });

    this.#backdrop.querySelector('#gh-action-open-analyzer')?.addEventListener('click', () => {
      this.close();
      eventBus.emit('router:navigate', { pageId: 'analyzer' });
    });
  }

  async #handleValidate() {
    const urlInput = this.#backdrop.querySelector('#gh-import-url-input');
    const tokenInput = this.#backdrop.querySelector('#gh-import-token-input');
    const errorBox = this.#backdrop.querySelector('#gh-validate-error-box');
    const validateBtn = this.#backdrop.querySelector('[data-action="validate"]');

    const rawUrl = urlInput?.value?.trim() || '';
    const rawToken = tokenInput?.value?.trim() || this.#state.token || '';

    if (!rawUrl) {
      toast.error('URL Required', 'Please enter a GitHub repository URL');
      urlInput?.focus();
      return;
    }

    if (errorBox) errorBox.style.display = 'none';

    // Show loading on button
    if (validateBtn) {
      validateBtn.disabled = true;
      validateBtn.innerHTML = '<span class="spinner spinner-xs" style="margin-right:6px;"></span> Validating...';
    }

    try {
      // 1. Parse URL
      const parsed = GitHubImporter.parseRepoUrl(rawUrl);
      this.#state.parsed = parsed;
      this.#state.token = rawToken;

      // 2. Validate Access
      const { repo } = await GitHubImporter.validateRepository(parsed.owner, parsed.repo, rawToken);
      this.#state.repoData = repo;

      // 3. Fetch Branches
      const { defaultBranch, branches } = await GitHubImporter.getBranches(parsed.owner, parsed.repo, rawToken);
      this.#state.branches = branches;
      this.#state.selectedBranch = parsed.branch || defaultBranch || 'main';

      toast.success('Repository Found', `${repo.full_name} (${branches.length} branches)`);
      this.#updateStep(2);
    } catch (err) {
      if (errorBox) {
        errorBox.style.display = 'block';
        errorBox.innerHTML = `
          <div class="card" style="padding:var(--sp-3); background:rgba(239,68,68,0.08); border:1px solid rgba(239,68,68,0.25); border-radius:var(--radius-md);">
            <div style="display:flex; gap:var(--sp-2); align-items:flex-start;">
              <span class="material-symbols-outlined" style="color:var(--error); font-size:18px;">error</span>
              <div style="font-size:var(--text-xs); color:var(--error); white-space:pre-wrap; line-height:1.5;">${err.message || 'Validation failed'}</div>
            </div>
          </div>
        `;
      }
      toast.error('Validation Failed', err.message || 'Could not access repository');
    } finally {
      if (validateBtn) {
        validateBtn.disabled = false;
        validateBtn.innerHTML = 'Validate & Continue';
      }
    }
  }

  async #handleImport() {
    this.#updateStep(3);

    const titleEl = this.#backdrop.querySelector('#gh-import-progress-title');
    const statusEl = this.#backdrop.querySelector('#gh-import-progress-status');
    const barEl = this.#backdrop.querySelector('#gh-import-progress-bar');
    const fileEl = this.#backdrop.querySelector('#gh-import-progress-file');
    const percentEl = this.#backdrop.querySelector('#gh-import-progress-percent');

    const updateProgress = ({ stage, current, total, percent, file, message }) => {
      if (statusEl && message) statusEl.textContent = message;
      if (barEl) barEl.style.width = `${Math.max(5, Math.min(100, percent))}%`;
      if (percentEl) percentEl.textContent = `${percent}%`;
      if (fileEl && file) {
        const short = file.length > 35 ? '...' + file.slice(-32) : file;
        fileEl.textContent = short;
      }
    };

    try {
      const { owner, repo } = this.#state.parsed;
      const branch = this.#state.selectedBranch;
      const token = this.#state.token;

      // 1. Fetch files
      const { files } = await GitHubImporter.fetchRepositoryFiles({
        owner,
        repo,
        branch,
        token,
        onProgress: updateProgress,
      });

      this.#state.files = files;

      if (statusEl) statusEl.textContent = 'Analyzing Android project model and modules...';
      if (barEl) barEl.style.width = '95%';

      // 2. Import into Project Model
      const { project, analysis } = await GitHubImporter.importToProjectModel({
        files,
        repoData: this.#state.repoData,
        branch,
      });

      this.#state.importedProject = project;
      this.#state.analysis = analysis;

      if (barEl) barEl.style.width = '100%';
      if (percentEl) percentEl.textContent = '100%';

      toast.success('Import Complete', `Loaded ${Object.keys(files).length} files from ${owner}/${repo}`);
      this.#updateStep(4);
    } catch (err) {
      toast.error('Import Failed', err.message || 'Failed to download repository files');
      this.#updateStep(2);

      const contentEl = this.#backdrop.querySelector('#gh-import-content');
      if (contentEl) {
        const errDiv = document.createElement('div');
        errDiv.style.marginTop = 'var(--sp-3)';
        errDiv.innerHTML = `
          <div class="card" style="padding:var(--sp-3); background:rgba(239,68,68,0.08); border:1px solid rgba(239,68,68,0.25); border-radius:var(--radius-md);">
            <div style="display:flex; gap:var(--sp-2); align-items:flex-start;">
              <span class="material-symbols-outlined" style="color:var(--error); font-size:18px;">error</span>
              <div style="font-size:var(--text-xs); color:var(--error); white-space:pre-wrap;">${err.message}</div>
            </div>
          </div>
        `;
        contentEl.appendChild(errDiv);
      }
    }
  }
}

export const gitHubImportModal = new GitHubImportModal();
