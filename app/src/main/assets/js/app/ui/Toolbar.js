/**
 * Toolbar UI module — renders and manages the top app bar.
 * Mobile: compact app bar (menu · brand/title · quick actions · notifications · logs · profile).
 * Desktop: brand + project info + full action set.
 */
import { eventBus } from '../core/EventBus.js';
import { projectService } from '../modules/storage/ProjectService.js';
import { githubAuth } from '../modules/github/GitHubAuth.js';
import { githubRepository } from '../modules/github/GitHubRepository.js';
import { i18n, t } from '../core/I18n.js';
import { toast } from './ToastService.js';
import { ConsumerResolutionModal } from '../modules/analyzer/ConsumerResolutionModal.js';

/** Page id → title shown in the mobile app bar. */
const PAGE_TITLES = {
  dashboard: 'Dashboard', analyzer: 'JitPack Analyzer', project: 'Project', templates: 'Templates',
  environment: 'Environment', explorer: 'Explorer', editor: 'Editor', generator: 'Generator',
  'conflict-resolver': 'Class & D8 Guard',
  gradle: 'Gradle', manifest: 'Android Manifest', github: 'GitHub Integration', 'gh-auth': 'Authentication',
  'gh-import': 'Import Repo', 'gh-repo': 'Repository', 'gh-files': 'Files', 'gh-commit': 'Commit',
  'gh-tags': 'Tags', 'gh-release': 'Release', 'gh-publish': 'Publish', 'gh-wizard': 'Actions Wizard',
  'gh-testing': 'RONi Full Library Test', 'gh-history': 'History', 'gh-settings': 'Settings',
  jitpack: 'Maven Publish + JitPack', 'version-manager': 'Version Manager',
  'ecosystem-manager': 'Ecosystem Version Manager', 'ecosystem-version-manager': 'Ecosystem Version Manager',
  build: 'Build', publish: 'Publish', downloads: 'Downloads',
  logs: 'Logs', settings: 'Settings',
};

const THEME_ICONS = { light: 'light_mode', dark: 'dark_mode', system: 'contrast' };

export class Toolbar {
  #el = null;
  #currentPage = 'dashboard';
  #notifs = [
    { id: 1, title: 'Android Library Generator', text: 'System ready for JitPack publication', time: 'Just now', icon: 'check_circle' },
    { id: 2, title: 'GitHub Integration', text: 'Tags & releases verified with safe preflight', time: '1m ago', icon: 'verified' }
  ];

  init() {
    this.#el = document.getElementById('toolbar');
    this.#render();
    this.#bindEvents();
    eventBus.on('project:created', () => this.#updateProjectInfo());
    eventBus.on('project:loaded',  () => this.#updateProjectInfo());
    eventBus.on('project:closed',  () => this.#updateProjectInfo());
    eventBus.on('router:changed', ({ pageId }) => this.#setPageTitle(pageId));
    eventBus.on('app:themeApplied', ({ mode }) => this.#updateThemeIcon(mode));
    eventBus.on('github:authenticated', () => this.#updateProfileInfo());
    eventBus.on('github:loggedOut', () => this.#updateProfileInfo());
  }

  #render() {
    this.#el.innerHTML = `
      <div class="toolbar-left">
        <!-- Navigation Toggle (Drawer on mobile, Collapse/Expand on desktop) -->
        <button type="button" class="toolbar-btn toolbar-sidebar-toggle" id="sidebar-toggle-btn" aria-label="Toggle sidebar navigation" title="Toggle Navigation">
          <span class="material-symbols-outlined">menu</span>
        </button>
        <div class="toolbar-brand" id="toolbar-brand-home" role="button" tabindex="0" title="Home">
          <span class="material-symbols-outlined toolbar-brand-icon">android</span>
          <div class="toolbar-brand-text">
            <span class="toolbar-brand-name">RONi LibGen</span>
            <span class="toolbar-brand-version desktop-only" id="toolbar-version-badge" title="Open Version Update Manager" style="cursor:pointer">v0.1.3.6</span>
          </div>
        </div>
        <div class="toolbar-divider desktop-only"></div>
        <div class="toolbar-page-badge desktop-only" id="toolbar-page-indicator" title="Active Section">
          <span class="toolbar-page-title" id="toolbar-page-title">Dashboard</span>
        </div>
        <div class="toolbar-divider desktop-only"></div>
        <div class="toolbar-project-info desktop-only">
          <span class="material-symbols-outlined toolbar-project-icon">folder_open</span>
          <span class="toolbar-project-name" id="toolbar-project-name">No project open</span>
        </div>
      </div>

      <div class="toolbar-actions">
        <!-- Desktop Quick Actions -->
        <button type="button" class="toolbar-btn primary desktop-only" id="btn-new-project" title="New Project" aria-label="New Project">
          <span class="material-symbols-outlined">add</span>
          <span class="toolbar-btn-label">New</span>
        </button>
        <button type="button" class="toolbar-btn desktop-only" id="btn-save-project" title="Save Project (Ctrl+S)" aria-label="Save">
          <span class="material-symbols-outlined">save</span>
          <span class="toolbar-btn-label">Save</span>
        </button>
        <button type="button" class="toolbar-btn desktop-only" id="btn-transitive-audit" title="Simulate Consumer Dependency Resolution & Classpath" aria-label="Resolution Test">
          <span class="material-symbols-outlined" style="color:var(--accent)">verified</span>
          <span class="toolbar-btn-label">Resolution Test</span>
        </button>

        <!-- Original Toolbar Action Icons -->
        <button type="button" class="toolbar-btn" id="btn-import-project" title="Import Project (GitHub or ZIP)" aria-label="Import Project">
          <span class="material-symbols-outlined">upload</span>
        </button>
        <button type="button" class="toolbar-btn" id="btn-export-zip" title="Export Project" aria-label="Export Project">
          <span class="material-symbols-outlined">download</span>
        </button>
        <button type="button" class="toolbar-btn desktop-only" id="btn-console-toggle" title="Toggle Console" aria-label="Toggle Console">
          <span class="material-symbols-outlined">terminal</span>
        </button>
        <button type="button" class="toolbar-btn" id="btn-lang-toggle" title="Toggle Language (English / বাংলা)" aria-label="Toggle Language">
          <span class="material-symbols-outlined">translate</span>
          <span class="toolbar-lang-tag" id="toolbar-lang-indicator" style="font-size:10px;font-weight:700;margin-left:2px;border:1px solid var(--border);border-radius:3px;padding:1px 3px;line-height:1">${this.#currentLangTag()}</span>
        </button>
        <button type="button" class="toolbar-btn" id="btn-settings" title="Settings" aria-label="Settings">
          <span class="material-symbols-outlined">settings</span>
        </button>
        <button type="button" class="toolbar-btn" id="btn-theme-toggle" title="Toggle Theme" aria-label="Toggle Theme">
          <span class="material-symbols-outlined" id="theme-toggle-icon">${THEME_ICONS[this.#currentThemeMode()] || 'dark_mode'}</span>
        </button>
      </div>
    `;
    this.#updateProjectInfo();
  }

  #bindEvents() {
    this.#el.querySelector('#sidebar-toggle-btn')?.addEventListener('click', () => {
      if (window.innerWidth < 768) {
        eventBus.emit('sidebar:mobileToggle');
      } else {
        eventBus.emit('sidebar:desktopToggle');
      }
    });

    this.#el.querySelector('#toolbar-brand-home')?.addEventListener('click', () =>
      eventBus.emit('router:navigate', { pageId: 'dashboard' }));

    this.#el.querySelector('#toolbar-version-badge')?.addEventListener('click', (e) => {
      e.stopPropagation();
      eventBus.emit('router:navigate', { pageId: 'version-manager' });
    });

    this.#el.querySelector('#btn-new-project')?.addEventListener('click', () =>
      eventBus.emit('action:newProject'));

    this.#el.querySelector('#btn-save-project')?.addEventListener('click', () =>
      eventBus.emit('action:saveProject'));

    this.#el.querySelector('#btn-transitive-audit')?.addEventListener('click', () => {
      ConsumerResolutionModal.show();
    });

    this.#el.querySelector('#btn-import-project')?.addEventListener('click', () =>
      eventBus.emit('action:importProject'));

    this.#el.querySelector('#btn-export-zip')?.addEventListener('click', () =>
      eventBus.emit('action:exportZip'));

    this.#el.querySelector('#btn-console-toggle')?.addEventListener('click', () =>
      eventBus.emit('toolbar:toggleConsole'));

    this.#el.querySelector('#btn-lang-toggle')?.addEventListener('click', () => {
      const nextLang = i18n.lang === 'bn' ? 'en' : 'bn';
      i18n.setLanguage(nextLang);
      const indicator = this.#el.querySelector('#toolbar-lang-indicator');
      if (indicator) indicator.textContent = nextLang === 'bn' ? 'বাং' : 'EN';
      toast.info(nextLang === 'bn' ? 'ভাষা: বাংলা' : 'Language: English');
    });

    eventBus.on('i18n:changed', ({ lang }) => {
      const indicator = this.#el.querySelector('#toolbar-lang-indicator');
      if (indicator) indicator.textContent = lang === 'bn' ? 'বাং' : 'EN';
    });

    this.#el.querySelector('#btn-settings')?.addEventListener('click', () =>
      eventBus.emit('router:navigate', { pageId: 'settings' }));

    this.#el.querySelector('#btn-theme-toggle')?.addEventListener('click', () =>
      eventBus.emit('ui:themeToggle'));

    // Keyboard shortcut Ctrl+S
    document.addEventListener('keydown', e => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        eventBus.emit('action:saveProject');
      }
    });
  }

  #currentThemeMode() {
    try {
      const m = localStorage.getItem('libgen.ui.theme');
      if (m === 'light' || m === 'dark' || m === 'system') return m;
    } catch { /* private mode */ }
    return 'dark';
  }

  #currentLangTag() {
    return i18n.lang === 'bn' ? 'বাং' : 'EN';
  }

  #setPageTitle(pageId) {
    if (!pageId) return;
    this.#currentPage = pageId;
    const el = this.#el.querySelector('#toolbar-page-title');
    if (el) el.textContent = t(PAGE_TITLES[pageId] || pageId);
    this.#updateProjectInfo();
  }

  #updateThemeIcon(mode) {
    const icon = this.#el?.querySelector('#theme-toggle-icon');
    if (icon) icon.textContent = THEME_ICONS[mode] || 'dark_mode';
  }

  #updateProjectInfo() {
    const nameEl   = this.#el.querySelector('#toolbar-project-name');
    const pillEl   = this.#el.querySelector('#toolbar-status-pill');
    const proj     = projectService.current;
    const repo     = githubRepository.activeRepo;

    if (nameEl) {
      if (proj) {
        nameEl.textContent = `${proj.name} (v${proj.version || '1.0.0'})`;
        nameEl.classList.add('has-project');
      } else {
        nameEl.textContent = t('No project open');
        nameEl.classList.remove('has-project');
      }
    }

    if (pillEl) {
      if (repo) {
        pillEl.textContent = `GitHub: ${repo.name}`;
        pillEl.className = 'toolbar-status-pill pill-online';
      } else if (proj) {
        pillEl.textContent = 'JitPack Ready';
        pillEl.className = 'toolbar-status-pill pill-ready';
      } else {
        pillEl.textContent = 'Standby';
        pillEl.className = 'toolbar-status-pill';
      }
    }
  }

  #updateProfileInfo() {
    const btn = this.#el.querySelector('#btn-profile-quick');
    const nameEl = this.#el.querySelector('#toolbar-profile-name');
    if (!btn) return;

    if (githubAuth.isAuthenticated && githubAuth.user) {
      const u = githubAuth.user;
      if (u.avatar_url) {
        btn.innerHTML = `
          <img src="${u.avatar_url}" class="toolbar-avatar" alt="${u.login}" />
          <span class="toolbar-profile-label desktop-only">${u.login}</span>`;
      } else {
        btn.innerHTML = `
          <span class="material-symbols-outlined" style="font-size:18px;color:var(--success)">verified_user</span>
          <span class="toolbar-profile-label desktop-only">${u.login}</span>`;
      }
    } else {
      if (nameEl) nameEl.textContent = 'Sign In';
    }
  }
}
