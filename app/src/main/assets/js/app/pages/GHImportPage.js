/**
 * GHImportPage — dedicated full-page GitHub Repository Importer.
 * Allows importing any public or private Android project from GitHub.
 */
import { gitHubImporter } from '../modules/github/GitHubImporter.js';
import { githubAuth }     from '../modules/github/GitHubAuth.js';
import { GitHubUI }       from '../modules/github/GitHubUI.js';
import { toast }          from '../ui/ToastService.js';
import { eventBus }       from '../core/EventBus.js';

export class GHImportPage {
  #container = null;
  #parsed = null;
  #branches = [];
  #selectedBranch = '';
  #isImporting = false;

  async init(container) {
    this.#container = container;
    this.#render();
  }

  #render() {
    const hasAuth = githubAuth.isAuthenticated;
    const defaultUser = githubAuth.user?.login || '';

    this.#container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title">
            <span class="material-symbols-outlined">cloud_download</span>GitHub Repository Import
          </h1>
          <p class="page-subtitle">Import an Android library or application repository from GitHub directly into the workspace</p>
        </div>

        <div class="gh-two-col">
          <!-- Step 1 & 2: Repository info -->
          <div class="card gh-card">
            <div class="card-header">
              <span class="material-symbols-outlined">link</span>Repository Details
            </div>
            <div class="card-body">
              <div class="form-group">
                <label class="form-label required">GitHub Repository URL or owner/repo</label>
                <div style="display:flex; gap:var(--sp-2)">
                  <input type="text" class="input" id="ghpage-url" placeholder="https://github.com/owner/repository" style="flex:1;" />
                  <button type="button" class="btn btn-primary" id="ghpage-inspect-btn">
                    <span class="material-symbols-outlined">search</span>Inspect
                  </button>
                </div>
                <div class="form-help">HTTPS, HTTP, SSH (git@github.com:owner/repo), or shorthand "owner/repo"</div>
              </div>

              <div style="margin-top:var(--sp-2);">
                <label class="form-label" style="font-size:11px; margin-bottom:4px;">Popular Android Samples:</label>
                <div style="display:flex; flex-wrap:wrap; gap:6px;">
                  <button type="button" class="btn btn-ghost btn-sm gh-sample-chip" data-sample="https://github.com/jitpack/android-example" style="font-size:11px; padding:2px 8px; border:1px solid var(--border);">jitpack/android-example</button>
                  <button type="button" class="btn btn-ghost btn-sm gh-sample-chip" data-sample="https://github.com/android/nowinandroid" style="font-size:11px; padding:2px 8px; border:1px solid var(--border);">android/nowinandroid</button>
                  <button type="button" class="btn btn-ghost btn-sm gh-sample-chip" data-sample="https://github.com/square/retrofit" style="font-size:11px; padding:2px 8px; border:1px solid var(--border);">square/retrofit</button>
                </div>
              </div>

              <div class="form-group" style="margin-top:var(--sp-3)">
                <label class="form-label">Personal Access Token (optional for public repos)</label>
                <input type="password" class="input" id="ghpage-token" placeholder="${hasAuth ? 'Using active GitHub authentication token' : 'ghp_xxxxxxxxxxxxxxxxxxxx'}" value="" autocomplete="new-password" />
                <div class="form-help">${hasAuth ? `Authenticated as <strong>@${defaultUser}</strong>. Custom token optional.` : 'Required only for private repositories or higher rate limits.'}</div>
              </div>

              <div id="ghpage-repo-meta" style="display:none; margin-top:var(--sp-3);">
                <!-- Populated after inspect -->
              </div>
            </div>
          </div>

          <!-- Step 3 & 4: Branch & Import Execution -->
          <div class="card gh-card">
            <div class="card-header">
              <span class="material-symbols-outlined">fork_right</span>Branch & Options
            </div>
            <div class="card-body">
              <div class="form-group">
                <label class="form-label">Branch</label>
                <select class="input" id="ghpage-branch" disabled>
                  <option value="">Inspect repository first</option>
                </select>
                <div class="form-help">Branch to import Android project files from</div>
              </div>

              <div class="form-group" style="margin-top:var(--sp-3)">
                <label class="form-label">Project Name Override (optional)</label>
                <input type="text" class="input" id="ghpage-name-override" placeholder="Defaults to repository name" />
              </div>

              <div id="ghpage-progress-wrap" style="display:none; margin-top:var(--sp-4);">
                <div style="display:flex; justify-content:space-between; font-size:var(--text-xs); margin-bottom:4px;">
                  <span id="ghpage-progress-label">Preparing…</span>
                  <span id="ghpage-progress-percent">0%</span>
                </div>
                <div style="height:6px; background:var(--bg-surface); border-radius:3px; overflow:hidden; border:1px solid var(--border);">
                  <div id="ghpage-progress-bar" style="height:100%; width:0%; background:var(--accent); transition:width 0.2s ease;"></div>
                </div>
              </div>

              <div id="ghpage-result-card" style="display:none; margin-top:var(--sp-4);"></div>

              <div class="gh-action-row" style="margin-top:var(--sp-4)">
                <button type="button" class="btn btn-primary" id="ghpage-import-btn" disabled>
                  <span class="material-symbols-outlined">cloud_download</span>Import Repository
                </button>
                <button type="button" class="btn btn-secondary" id="ghpage-open-modal-btn">
                  <span class="material-symbols-outlined">open_in_new</span>Wizard Dialog
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;

    this.#bindEvents();
  }

  #bindEvents() {
    const urlInput    = this.#container.querySelector('#ghpage-url');
    const tokenInput  = this.#container.querySelector('#ghpage-token');
    const inspectBtn  = this.#container.querySelector('#ghpage-inspect-btn');
    const branchSelect = this.#container.querySelector('#ghpage-branch');
    const importBtn   = this.#container.querySelector('#ghpage-import-btn');
    const wizardBtn   = this.#container.querySelector('#ghpage-open-modal-btn');

    // Samples
    this.#container.querySelectorAll('.gh-sample-chip').forEach(btn => {
      btn.addEventListener('click', () => {
        if (urlInput) {
          urlInput.value = btn.dataset.sample;
          inspectBtn?.click();
        }
      });
    });

    wizardBtn?.addEventListener('click', () => {
      const url = urlInput?.value?.trim() || '';
      eventBus.emit('action:importGitHub', { initialUrl: url });
    });

    inspectBtn?.addEventListener('click', async () => {
      const rawUrl = urlInput?.value?.trim();
      if (!rawUrl) {
        toast.warning('Input Required', 'Please enter a GitHub repository URL');
        return;
      }

      inspectBtn.disabled = true;
      inspectBtn.innerHTML = '<span class="material-symbols-outlined spin">sync</span>';

      try {
        const token = tokenInput?.value?.trim() || undefined;
        const res = await gitHubImporter.validateRepo(rawUrl, token);
        this.#parsed = res.parsed;
        this.#branches = res.branches || [];
        this.#selectedBranch = res.defaultBranch || (this.#branches[0]?.name) || 'main';

        // Render meta
        const metaEl = this.#container.querySelector('#ghpage-repo-meta');
        if (metaEl) {
          metaEl.style.display = 'block';
          metaEl.innerHTML = `
            <div style="background:var(--bg-surface); padding:var(--sp-3); border-radius:var(--radius-sm); border:1px solid var(--border);">
              <div style="font-weight:600; font-size:var(--text-sm); display:flex; align-items:center; gap:6px;">
                <span class="material-symbols-outlined" style="color:var(--accent); font-size:18px;">folder_special</span>
                ${res.repo.full_name}
                ${res.repo.private ? '<span class="gh-badge gh-badge-private">private</span>' : '<span class="gh-badge gh-badge-public">public</span>'}
              </div>
              <div style="font-size:var(--text-xs); color:var(--text-secondary); margin-top:4px;">
                ${res.repo.description || 'No description provided'}
              </div>
              <div style="display:flex; gap:12px; font-size:11px; color:var(--text-muted); margin-top:8px;">
                <span>⭐ ${res.repo.stargazers_count ?? 0}</span>
                <span>🍴 ${res.repo.forks_count ?? 0}</span>
                <span>Default: <code>${res.repo.default_branch}</code></span>
              </div>
            </div>`;
        }

        // Populate branches
        if (branchSelect) {
          branchSelect.disabled = false;
          branchSelect.innerHTML = this.#branches.map(b =>
            `<option value="${b.name}" ${b.name === this.#selectedBranch ? 'selected' : ''}>${b.name}${b.name === res.repo.default_branch ? ' (default)' : ''}</option>`
          ).join('');
        }

        importBtn.disabled = false;
        toast.success('Repository verified', `${res.repo.full_name} is accessible`);
      } catch (err) {
        toast.error('Validation failed', err.message);
      } finally {
        inspectBtn.disabled = false;
        inspectBtn.innerHTML = '<span class="material-symbols-outlined">search</span>Inspect';
      }
    });

    branchSelect?.addEventListener('change', (e) => {
      this.#selectedBranch = e.target.value;
    });

    importBtn?.addEventListener('click', async () => {
      if (!this.#parsed) return;

      const token = tokenInput?.value?.trim() || undefined;
      const branch = branchSelect?.value || this.#selectedBranch;
      const nameOverride = this.#container.querySelector('#ghpage-name-override')?.value?.trim();

      const progressWrap = this.#container.querySelector('#ghpage-progress-wrap');
      const progressLabel = this.#container.querySelector('#ghpage-progress-label');
      const progressPercent = this.#container.querySelector('#ghpage-progress-percent');
      const progressBar = this.#container.querySelector('#ghpage-progress-bar');
      const resultCard = this.#container.querySelector('#ghpage-result-card');

      if (progressWrap) progressWrap.style.display = 'block';
      if (resultCard) resultCard.style.display = 'none';
      importBtn.disabled = true;

      try {
        const result = await gitHubImporter.importRepository({
          owner: this.#parsed.owner,
          repo: this.#parsed.repo,
          branch,
          token,
          projectName: nameOverride || undefined,
          onProgress: (p) => {
            if (progressLabel) progressLabel.textContent = p.stage || 'Importing…';
            if (progressPercent) progressPercent.textContent = `${p.percent}%`;
            if (progressBar) progressBar.style.width = `${p.percent}%`;
          }
        });

        if (resultCard) {
          resultCard.style.display = 'block';
          resultCard.innerHTML = `
            <div style="background:var(--bg-surface); border:1px solid var(--accent); padding:var(--sp-3); border-radius:var(--radius-sm);">
              <div style="color:var(--accent); font-weight:600; display:flex; align-items:center; gap:6px;">
                <span class="material-symbols-outlined">check_circle</span>Import Complete!
              </div>
              <div style="font-size:var(--text-xs); margin-top:4px; color:var(--text-secondary);">
                Project <strong>${result.project.name}</strong> loaded with <strong>${result.stats.totalFiles}</strong> files.
              </div>
              <div style="display:flex; gap:8px; margin-top:12px;">
                <button type="button" class="btn btn-primary btn-sm" id="ghpage-goto-explorer">
                  <span class="material-symbols-outlined">account_tree</span>Open in Explorer
                </button>
                <button type="button" class="btn btn-secondary btn-sm" id="ghpage-goto-editor">
                  <span class="material-symbols-outlined">code</span>Open Editor
                </button>
              </div>
            </div>`;

          resultCard.querySelector('#ghpage-goto-explorer')?.addEventListener('click', () => {
            eventBus.emit('router:navigate', { pageId: 'explorer' });
          });
          resultCard.querySelector('#ghpage-goto-editor')?.addEventListener('click', () => {
            eventBus.emit('router:navigate', { pageId: 'editor' });
          });
        }

        toast.success('Import Successful', `${result.project.name} imported from GitHub`);
      } catch (err) {
        toast.error('Import Error', err.message);
      } finally {
        importBtn.disabled = false;
      }
    });
  }
}
