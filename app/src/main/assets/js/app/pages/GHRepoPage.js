/**
 * GHRepoPage.js — GitHub repository creation, discovery, and management.
 */
import { githubAuth }       from '../modules/github/GitHubAuth.js';
import { githubRepository } from '../modules/github/GitHubRepository.js';
import { githubSettings }   from '../modules/github/GitHubSettings.js';
import { projectService }   from '../modules/storage/ProjectService.js';
import { GitHubUI }         from '../modules/github/GitHubUI.js';
import { toast }            from '../ui/ToastService.js';
import { consoleService }   from '../modules/storage/ConsoleService.js';
import { eventBus }         from '../core/EventBus.js';

export class GHRepoPage {
  #container = null;
  #loadedRepos = [];
  #filterQuery = '';
  #isLoadingRepos = false;

  async init(container) {
    this.#container = container;
    await githubSettings.load();
    await githubRepository.loadSaved();
    this.#render();
  }

  #render() {
    if (!this.#container) return;
    const isAuth = githubAuth.isAuthenticated;
    const proj   = projectService.current;
    const s      = githubSettings.get();
    const active = githubRepository.activeRepo;
    const defaultOwner = s.defaultUsername || githubAuth.user?.login || '';

    this.#container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-2)">
            <div>
              <h1 class="page-title">
                <span class="material-symbols-outlined">folder_special</span>Repository Management
              </h1>
              <p class="page-subtitle">Create, load, browse, and import GitHub repositories into your workspace</p>
            </div>
            ${isAuth ? `
              <div class="badge badge-success" style="display:flex;align-items:center;gap:4px">
                <span class="material-symbols-outlined" style="font-size:14px">verified_user</span>
                Connected as @${githubAuth.user?.login || 'user'}
              </div>
            ` : `
              <button class="btn btn-secondary btn-sm" id="gh-goto-auth-btn">
                <span class="material-symbols-outlined">key</span>Connect GitHub Token
              </button>
            `}
          </div>
        </div>

        ${!isAuth ? `
          <div class="gh-alert gh-alert-info" style="margin-bottom:var(--sp-4)">
            <span class="material-symbols-outlined">info</span>
            <div style="flex:1">
              <strong>Public Mode Active:</strong> You can load, view, and import public repositories from any GitHub user or organization. To create new repositories or access private repositories, please <a href="#gh-auth" id="gh-alert-auth-link" style="color:var(--accent);text-decoration:underline">connect a Personal Access Token</a>.
            </div>
          </div>
        ` : ''}

        <div class="gh-two-col">

          <!-- 1. Create or Load Repository -->
          ${GitHubUI.card('add_circle', 'Create or Load Repository', `
            <div class="form-group" style="margin-bottom:var(--sp-3)">
              <label class="form-label" for="gh-repo-owner">
                Owner / Username <span style="color:var(--error)">*</span>
              </label>
              <input type="text" id="gh-repo-owner" class="input" value="${this.#escape(defaultOwner)}" placeholder="e.g. your-username or org" />
              <div id="gh-repo-owner-error" class="gh-field-error-msg" style="display:none"></div>
            </div>

            <div class="form-group" style="margin-bottom:var(--sp-3)">
              <label class="form-label" for="gh-repo-name">
                Repository Name <span style="color:var(--error)">*</span>
              </label>
              <input type="text" id="gh-repo-name" class="input" value="${this.#escape(proj?.repoName || '')}" placeholder="e.g. my-android-library" />
              <div id="gh-repo-name-error" class="gh-field-error-msg" style="display:none"></div>
            </div>

            <div class="form-group" style="margin-bottom:var(--sp-3)">
              <label class="form-label" for="gh-repo-desc">Description</label>
              <input type="text" id="gh-repo-desc" class="input" value="${this.#escape(proj?.description || '')}" placeholder="A short description of the library" />
            </div>

            <div class="form-group" style="margin-bottom:var(--sp-3)">
              <label class="form-label" for="gh-repo-homepage">Homepage URL</label>
              <input type="url" id="gh-repo-homepage" class="input" placeholder="https://example.com (optional)" />
            </div>

            <div class="form-group" style="margin-bottom:var(--sp-3)">
              <label class="form-label" for="gh-repo-vis">Visibility</label>
              <select id="gh-repo-vis" class="input">
                <option value="false">Public</option>
                <option value="true">Private</option>
              </select>
            </div>

            <div class="gh-action-row" style="margin-top:var(--sp-3);display:flex;gap:var(--sp-2);flex-wrap:wrap">
              <button class="btn btn-primary" id="gh-create-repo-btn">
                <span class="material-symbols-outlined">add</span>Create Repository
              </button>
              <button class="btn btn-secondary" id="gh-load-repo-btn">
                <span class="material-symbols-outlined">search</span>Load Existing
              </button>
            </div>
          `)}

          <!-- 2. Active Repository & Import Quick Card -->
          <div style="display:flex;flex-direction:column;gap:var(--sp-4)">

            <!-- Active Repository Info -->
            ${GitHubUI.card('hub', 'Active Repository', active ? `
              <div class="gh-repo-info">
                <div class="gh-repo-name">
                  <span class="material-symbols-outlined">folder_special</span>
                  <a href="${active.html_url}" target="_blank" class="gh-link">${this.#escape(active.full_name)}</a>
                  ${GitHubUI.badge(active.private ? 'private' : 'public')}
                </div>
                ${active.description ? `<div class="gh-repo-desc">${this.#escape(active.description)}</div>` : ''}
                <div class="gh-repo-stats">
                  <span><span class="material-symbols-outlined">star</span>${active.stargazers_count ?? 0}</span>
                  <span><span class="material-symbols-outlined">fork_right</span>${active.forks_count ?? 0}</span>
                  <span><span class="material-symbols-outlined">bug_report</span>${active.open_issues_count ?? 0}</span>
                  <span><span class="material-symbols-outlined">storage</span>${active.size ?? 0} KB</span>
                </div>
                <div class="gh-repo-url">
                  <span class="material-symbols-outlined">link</span>
                  <code>${this.#escape(active.clone_url || active.html_url)}</code>
                </div>
                <div class="gh-action-row" style="margin-top:var(--sp-3);display:flex;gap:var(--sp-2);flex-wrap:wrap">
                  <a href="${active.html_url}" target="_blank" class="btn btn-secondary">
                    <span class="material-symbols-outlined">open_in_new</span>Open on GitHub
                  </a>
                  <button class="btn btn-ghost" id="gh-refresh-repo-btn">
                    <span class="material-symbols-outlined">refresh</span>Refresh
                  </button>
                  <button class="btn btn-primary" id="gh-import-active-btn" title="Import active repository files into workspace">
                    <span class="material-symbols-outlined">cloud_download</span>Import Files
                  </button>
                </div>
              </div>
            ` : GitHubUI.empty('folder_off','No active repository selected','Create a new repo, load one, or pick from the list below')
            )}

            <!-- Quick Import Card -->
            ${GitHubUI.card('cloud_download', 'Import Remote Repository', `
              <div style="font-size:var(--text-xs); color:var(--text-secondary); margin-bottom:var(--sp-3); line-height:1.5;">
                Directly import any Android library or application repository (public or private) into the generator workspace:
              </div>
              <div class="form-group" style="margin-bottom:var(--sp-2)">
                <input type="text" id="gh-import-repo-url" class="input" placeholder="e.g. jitpack/android-example or https://github.com/..." />
                <div id="gh-import-repo-url-error" class="gh-field-error-msg" style="display:none"></div>
              </div>
              <div class="gh-action-row" style="margin-top:var(--sp-3)">
                <button class="btn btn-primary" id="gh-import-repo-btn">
                  <span class="material-symbols-outlined">cloud_download</span>Start Import
                </button>
              </div>
            `)}

          </div>

        </div>

        <!-- 3. Your Repositories Explorer -->
        <div class="card gh-card" style="margin-top:var(--sp-4)">
          <div class="card-header" style="display:flex;align-items:center;gap:var(--sp-2);flex-wrap:wrap">
            <span class="material-symbols-outlined">list</span>
            <span style="font-weight:600">Repositories</span>
            <span id="gh-repos-count" class="gh-repos-count-badge" style="${this.#loadedRepos.length ? '' : 'display:none'}">
              ${this.#loadedRepos.length} repositories
            </span>
            <div class="flex-1"></div>

            <div class="gh-repos-header-actions" style="display:flex;align-items:center;gap:var(--sp-2);flex-wrap:wrap">
              <input type="text" id="gh-repos-target-user" class="input input-sm" style="width:180px;height:30px;font-size:var(--text-xs)"
                     placeholder="Username / Org" value="${this.#escape(defaultOwner)}" />
              <button class="btn btn-secondary btn-sm" id="gh-list-repos-btn" ${this.#isLoadingRepos ? 'disabled' : ''}>
                <span class="material-symbols-outlined ${this.#isLoadingRepos ? 'spin' : ''}">refresh</span>
                ${this.#isLoadingRepos ? 'Loading…' : 'Load Repositories'}
              </button>
            </div>
          </div>

          <!-- Real-time Filter Bar (visible when repos are loaded) -->
          <div id="gh-repos-filter-bar" class="gh-repos-filter-bar" style="${this.#loadedRepos.length > 0 ? '' : 'display:none'}">
            <span class="material-symbols-outlined" style="font-size:16px;color:var(--text-muted)">search</span>
            <input type="text" id="gh-repos-filter-input" class="input" placeholder="Search loaded repositories by name or description…" value="${this.#escape(this.#filterQuery)}" />
            ${this.#filterQuery ? `
              <button class="btn btn-ghost btn-sm" id="gh-repos-clear-filter-btn" style="padding:2px 6px">
                <span class="material-symbols-outlined" style="font-size:14px">close</span>Clear
              </button>
            ` : ''}
          </div>

          <div class="card-body" id="gh-repos-list" style="padding:0">
            ${this.#renderReposList()}
          </div>
        </div>

      </div>
    `;

    this.#bindEvents();
  }

  #renderReposList() {
    if (this.#isLoadingRepos) {
      return `
        <div style="padding:var(--sp-8);text-align:center;color:var(--text-muted)">
          <span class="material-symbols-outlined spin" style="font-size:32px;color:var(--accent);display:block;margin-bottom:8px">sync</span>
          Loading repositories from GitHub…
        </div>`;
    }

    if (!this.#loadedRepos.length) {
      return GitHubUI.empty('cloud', 'Click "Load Repositories" to fetch repositories', 'Enter any GitHub username or organization above to explore public repositories');
    }

    const q = this.#filterQuery.toLowerCase().trim();
    const filtered = q
      ? this.#loadedRepos.filter(r => (r.name && r.name.toLowerCase().includes(q)) || (r.description && r.description.toLowerCase().includes(q)) || (r.full_name && r.full_name.toLowerCase().includes(q)))
      : this.#loadedRepos;

    if (!filtered.length) {
      return `
        <div style="padding:var(--sp-6);text-align:center;color:var(--text-muted)">
          <span class="material-symbols-outlined" style="font-size:28px;opacity:0.5;display:block;margin-bottom:6px">filter_list_off</span>
          No repositories match "${this.#escape(this.#filterQuery)}"
        </div>`;
    }

    return filtered.map(r => {
      const owner = r.owner?.login || '';
      const name  = r.name || '';
      const fullName = r.full_name || `${owner}/${name}`;
      const isPrivate = !!r.private;
      const stars = r.stargazers_count ?? 0;
      const forks = r.forks_count ?? 0;
      const lang  = r.language || '';
      const updatedAt = r.updated_at ? new Date(r.updated_at).toLocaleDateString() : '';

      return `
        <div class="gh-repo-row" data-owner="${this.#escape(owner)}" data-name="${this.#escape(name)}" data-url="${this.#escape(r.html_url)}">
          <div class="gh-repo-row-main">
            <div class="gh-repo-row-header">
              <span class="material-symbols-outlined gh-repo-icon" style="color:var(--accent);flex-shrink:0">folder</span>
              <div class="gh-repo-title-wrap">
                <span class="gh-repo-row-name">${this.#escape(fullName)}</span>
                ${owner ? `<span class="gh-repo-owner-label mobile-only">owner: <strong>${this.#escape(owner)}</strong></span>` : ''}
              </div>
              <div class="gh-repo-badges">
                ${GitHubUI.badge(isPrivate ? 'private' : 'public')}
                ${lang ? `<span class="badge gh-repo-lang-badge">${this.#escape(lang)}</span>` : ''}
              </div>
            </div>

            <div class="gh-repo-row-info">
              ${r.description ? `<div class="gh-repo-row-desc">${this.#escape(r.description)}</div>` : '<div class="gh-repo-row-desc gh-repo-no-desc">No description provided</div>'}
              <div class="gh-repo-row-meta">
                ${stars > 0 ? `<span class="gh-repo-meta-item"><span class="material-symbols-outlined">star</span>${stars}</span>` : ''}
                ${forks > 0 ? `<span class="gh-repo-meta-item"><span class="material-symbols-outlined">fork_right</span>${forks}</span>` : ''}
                ${lang ? `<span class="gh-repo-meta-item gh-repo-meta-lang mobile-only"><span class="material-symbols-outlined">code</span>${this.#escape(lang)}</span>` : ''}
                ${updatedAt ? `<span class="gh-repo-meta-item"><span class="material-symbols-outlined">schedule</span>Updated ${updatedAt}</span>` : ''}
              </div>
            </div>
          </div>

          <div class="gh-repo-row-actions">
            <button type="button" class="btn btn-sm btn-secondary gh-select-repo-btn" title="Set as active repository">
              <span class="material-symbols-outlined">check_circle</span>Select
            </button>
            <button type="button" class="btn btn-sm btn-primary gh-import-row-btn" title="Import this repository into Project workspace">
              <span class="material-symbols-outlined">cloud_download</span>Import
            </button>
            <a href="${r.html_url}" target="_blank" rel="noopener noreferrer" class="btn btn-sm btn-outline gh-view-row-btn" title="Open repository on GitHub">
              <span class="material-symbols-outlined">open_in_new</span>
              <span class="mobile-only">View</span>
            </a>
          </div>
        </div>
      `;
    }).join('');
  }

  #bindEvents() {
    if (!this.#container) return;
    const c = this.#container;

    // Navigation links
    c.querySelector('#gh-goto-auth-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'gh-auth' });
    });
    c.querySelector('#gh-alert-auth-link')?.addEventListener('click', (e) => {
      e.preventDefault();
      eventBus.emit('router:navigate', { pageId: 'gh-auth' });
    });

    // ── Create Repo ──
    c.querySelector('#gh-create-repo-btn')?.addEventListener('click', async () => {
      const ownerEl = c.querySelector('#gh-repo-owner');
      const nameEl  = c.querySelector('#gh-repo-name');
      const ownerErr = c.querySelector('#gh-repo-owner-error');
      const nameErr  = c.querySelector('#gh-repo-name-error');

      this.#clearInputError(ownerEl, ownerErr);
      this.#clearInputError(nameEl, nameErr);

      const owner = ownerEl?.value?.trim();
      const name  = nameEl?.value?.trim();
      let hasError = false;

      if (!owner) {
        this.#setInputError(ownerEl, ownerErr, 'Owner / username is required');
        hasError = true;
      }
      if (!name) {
        this.#setInputError(nameEl, nameErr, 'Repository name is required');
        hasError = true;
      }
      if (hasError) return;

      if (!githubAuth.isAuthenticated) {
        toast.error('Authentication Required', 'A GitHub Personal Access Token is required to create repositories. Go to GitHub › Authentication.');
        eventBus.emit('router:navigate', { pageId: 'gh-auth' });
        return;
      }

      const btn = c.querySelector('#gh-create-repo-btn');
      const desc = c.querySelector('#gh-repo-desc')?.value?.trim();
      const homepage = c.querySelector('#gh-repo-homepage')?.value?.trim();
      const isPrivate = c.querySelector('#gh-repo-vis')?.value === 'true';

      btn.disabled = true;
      btn.innerHTML = '<span class="material-symbols-outlined spin">sync</span>Creating…';

      try {
        const repo = await githubRepository.create({ name, description: desc, homepage, private: isPrivate });
        toast.success('Repository created', repo.html_url);
        consoleService.success(`GitHub: created repository ${repo.full_name}`);
        await githubSettings.save({ defaultUsername: owner });
        this.#render();
      } catch (e) {
        toast.error('Create failed', e.message);
        btn.disabled = false;
        btn.innerHTML = '<span class="material-symbols-outlined">add</span>Create Repository';
      }
    });

    // ── Load Existing Repo ──
    c.querySelector('#gh-load-repo-btn')?.addEventListener('click', async () => {
      const ownerEl = c.querySelector('#gh-repo-owner');
      const nameEl  = c.querySelector('#gh-repo-name');
      const ownerErr = c.querySelector('#gh-repo-owner-error');
      const nameErr  = c.querySelector('#gh-repo-name-error');

      this.#clearInputError(ownerEl, ownerErr);
      this.#clearInputError(nameEl, nameErr);

      const owner = ownerEl?.value?.trim();
      const name  = nameEl?.value?.trim();
      let hasError = false;

      if (!owner) {
        this.#setInputError(ownerEl, ownerErr, 'Enter repository owner/username');
        hasError = true;
      }
      if (!name) {
        this.#setInputError(nameEl, nameErr, 'Enter repository name');
        hasError = true;
      }
      if (hasError) return;

      const btn = c.querySelector('#gh-load-repo-btn');
      btn.disabled = true;
      btn.innerHTML = '<span class="material-symbols-outlined spin">sync</span>Loading…';

      try {
        await githubRepository.get(owner, name);
        toast.success('Repository loaded', `${owner}/${name}`);
        this.#render();
      } catch (e) {
        toast.error('Load failed', e.message);
        this.#setInputError(nameEl, nameErr, `Failed to load: ${e.message}`);
        btn.disabled = false;
        btn.innerHTML = '<span class="material-symbols-outlined">search</span>Load Existing';
      }
    });

    // ── Active Repo Actions ──
    c.querySelector('#gh-refresh-repo-btn')?.addEventListener('click', async () => {
      const r = githubRepository.activeRepo;
      if (!r) return;
      try {
        await githubRepository.get(r.owner.login, r.name);
        this.#render();
        toast.info('Refreshed', r.name);
      } catch (e) {
        toast.error('Refresh failed', e.message);
      }
    });

    c.querySelector('#gh-import-active-btn')?.addEventListener('click', () => {
      const r = githubRepository.activeRepo;
      if (r?.html_url) {
        eventBus.emit('action:importGitHub', { initialUrl: r.html_url });
      }
    });

    // ── Import Repository Quick Card ──
    c.querySelector('#gh-import-repo-btn')?.addEventListener('click', () => {
      const urlEl = c.querySelector('#gh-import-repo-url');
      const errEl = c.querySelector('#gh-import-repo-url-error');
      this.#clearInputError(urlEl, errEl);

      const url = urlEl?.value?.trim() || '';
      if (!url) {
        this.#setInputError(urlEl, errEl, 'Please enter a GitHub repository URL or "owner/repo"');
        return;
      }
      eventBus.emit('action:importGitHub', { initialUrl: url });
    });

    // ── Your Repositories: Load & Search ──
    c.querySelector('#gh-list-repos-btn')?.addEventListener('click', async () => {
      await this.#loadRepositoriesList();
    });

    // Filter input
    const filterInput = c.querySelector('#gh-repos-filter-input');
    filterInput?.addEventListener('input', (e) => {
      this.#filterQuery = e.target.value;
      const listEl = c.querySelector('#gh-repos-list');
      if (listEl) {
        listEl.innerHTML = this.#renderReposList();
        this.#bindListActions(listEl);
      }
    });

    c.querySelector('#gh-repos-clear-filter-btn')?.addEventListener('click', () => {
      this.#filterQuery = '';
      if (filterInput) filterInput.value = '';
      const listEl = c.querySelector('#gh-repos-list');
      if (listEl) {
        listEl.innerHTML = this.#renderReposList();
        this.#bindListActions(listEl);
      }
    });

    // Bind item actions in the repository list
    const listEl = c.querySelector('#gh-repos-list');
    if (listEl) {
      this.#bindListActions(listEl);
    }
  }

  async #loadRepositoriesList() {
    if (!this.#container) return;
    const c = this.#container;
    const userEl = c.querySelector('#gh-repos-target-user');
    const targetUser = userEl?.value?.trim() || c.querySelector('#gh-repo-owner')?.value?.trim() || '';

    this.#isLoadingRepos = true;
    const btn = c.querySelector('#gh-list-repos-btn');
    if (btn) {
      btn.disabled = true;
      btn.innerHTML = '<span class="material-symbols-outlined spin">sync</span> Loading…';
    }

    const listEl = c.querySelector('#gh-repos-list');
    if (listEl) {
      listEl.innerHTML = `
        <div style="padding:var(--sp-8);text-align:center;color:var(--text-muted)">
          <span class="material-symbols-outlined spin" style="font-size:32px;color:var(--accent);display:block;margin-bottom:8px">sync</span>
          Fetching repositories from GitHub…
        </div>`;
    }

    try {
      let repos = [];
      if (targetUser) {
        repos = await githubRepository.listOwned(targetUser);
      } else if (githubAuth.isAuthenticated) {
        repos = await githubRepository.listOwned();
      } else {
        throw new Error('Please enter a GitHub username or connect a Personal Access Token to load repositories.');
      }

      this.#loadedRepos = Array.isArray(repos) ? repos : [];
      this.#filterQuery = '';
      this.#isLoadingRepos = false;

      // Update badge count
      const countEl = c.querySelector('#gh-repos-count');
      if (countEl) {
        countEl.textContent = `${this.#loadedRepos.length} repositories`;
        countEl.style.display = '';
      }

      // Show/hide filter bar
      const filterBar = c.querySelector('#gh-repos-filter-bar');
      if (filterBar) {
        filterBar.style.display = this.#loadedRepos.length > 0 ? '' : 'none';
      }

      if (listEl) {
        listEl.innerHTML = this.#renderReposList();
        this.#bindListActions(listEl);
      }

      toast.success('Repositories Loaded', `Found ${this.#loadedRepos.length} repositories`);
    } catch (e) {
      this.#isLoadingRepos = false;
      if (listEl) {
        listEl.innerHTML = GitHubUI.alert('error', `Failed to load repositories: ${e.message}`);
      }
      toast.error('Load Repositories Failed', e.message);
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.innerHTML = '<span class="material-symbols-outlined">refresh</span>Load Repositories';
      }
    }
  }

  #bindListActions(listEl) {
    listEl.querySelectorAll('.gh-import-row-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const row = e.target.closest('.gh-repo-row');
        const url = row.dataset.url || `https://github.com/${row.dataset.owner}/${row.dataset.name}`;
        eventBus.emit('action:importGitHub', { initialUrl: url });
      });
    });

    listEl.querySelectorAll('.gh-select-repo-btn').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        const row   = e.target.closest('.gh-repo-row');
        const owner = row.dataset.owner;
        const name  = row.dataset.name;
        btn.disabled = true;
        btn.innerHTML = '<span class="material-symbols-outlined spin">sync</span>';
        try {
          await githubRepository.get(owner, name);
          toast.success('Repository selected', `${owner}/${name}`);
          this.#render();
        } catch (err) {
          toast.error('Select failed', err.message);
          btn.disabled = false;
          btn.textContent = 'Select';
        }
      });
    });
  }

  #setInputError(inputEl, errorEl, message) {
    if (inputEl) {
      inputEl.classList.add('gh-input-invalid');
      inputEl.focus();
    }
    if (errorEl) {
      errorEl.textContent = message;
      errorEl.style.display = 'flex';
    }
  }

  #clearInputError(inputEl, errorEl) {
    if (inputEl) {
      inputEl.classList.remove('gh-input-invalid');
    }
    if (errorEl) {
      errorEl.textContent = '';
      errorEl.style.display = 'none';
    }
  }

  #escape(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
}
