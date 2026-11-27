/**
 * App.js — Main application bootstrap and orchestrator.
 * Initializes all modules, wires events, and starts routing.
 */
import { eventBus } from './app/core/EventBus.js';
import { Router }   from './app/core/Router.js';
import { i18n }     from './app/core/I18n.js';
import { storage }  from './app/modules/storage/StorageService.js';
import { projectService } from './app/modules/storage/ProjectService.js';
import { consoleService } from './app/modules/storage/ConsoleService.js';
import { Toolbar }        from './app/ui/Toolbar.js';
import { Sidebar }        from './app/ui/Sidebar.js';
import { BottomNav }      from './app/ui/BottomNav.js';
import { ConsolePanel }   from './app/ui/ConsolePanel.js';
import { StatusBar }      from './app/ui/StatusBar.js';
import { PropertyPanel }  from './app/ui/PropertyPanel.js';
import { toast }          from './app/ui/ToastService.js';
import { modal }          from './app/ui/ModalService.js';
import { zipService }     from './app/modules/zip/ZipService.js';
import { gitHubImportModal } from './app/modules/github/GitHubImportModal.js';
import { importChooserModal } from './app/modules/github/ImportChooserModal.js';

// ── Page imports (lazy-loaded through Router) ─────────────────────────────
const PAGE_MAP = {
  dashboard:  () => import('./app/pages/DashboardPage.js').then(m => new m.DashboardPage()),
  analyzer:   () => import('./app/pages/AnalyzerPage.js').then(m => new m.AnalyzerPage()),
  project:    () => import('./app/pages/ProjectPage.js').then(m => new m.ProjectPage()),
  templates:  () => import('./app/pages/TemplatesPage.js').then(m => new m.TemplatesPage()),
  explorer:   () => import('./app/pages/ExplorerPage.js').then(m => new m.ExplorerPage()),
  editor:     () => import('./app/pages/EditorPage.js').then(m => new m.EditorPage()),
  generator:  () => import('./app/pages/GeneratorPage.js').then(m => new m.GeneratorPage()),
  'conflict-resolver': () => import('./app/pages/ConflictResolverPage.js').then(m => new m.ConflictResolverPage()),
  gradle:     () => import('./app/pages/GradlePage.js').then(m => new m.GradlePage()),
  manifest:   () => import('./app/pages/ManifestPage.js').then(m => new m.ManifestPage()),
  environment:() => import('./app/pages/EnvironmentPage.js').then(m => new m.EnvironmentPage()),
  github:      () => import('./app/pages/GitHubPage.js').then(m => new m.GitHubPage()),
  'gh-auth':   () => import('./app/pages/GHAuthPage.js').then(m => new m.GHAuthPage()),
  'gh-import': () => import('./app/pages/GHImportPage.js').then(m => new m.GHImportPage()),
  'gh-repo':   () => import('./app/pages/GHRepoPage.js').then(m => new m.GHRepoPage()),
  'gh-files':  () => import('./app/pages/GHFilesPage.js').then(m => new m.GHFilesPage()),
  'gh-commit': () => import('./app/pages/GHCommitPage.js').then(m => new m.GHCommitPage()),
  'gh-tags':   () => import('./app/pages/GHTagsPage.js').then(m => new m.GHTagsPage()),
  'gh-release':() => import('./app/pages/GHReleasePage.js').then(m => new m.GHReleasePage()),
  'gh-publish':() => import('./app/pages/GHPublishPage.js').then(m => new m.GHPublishPage()),
  'gh-wizard': () => import('./app/pages/GHActionsWizardPage.js').then(m => new m.GHActionsWizardPage()),
  'gh-actions':() => import('./app/pages/GHActionsWizardPage.js').then(m => new m.GHActionsWizardPage()),
  'gh-testing':() => import('./app/pages/GHTestingPage.js').then(m => new m.GHTestingPage()),
  'gh-history':() => import('./app/pages/GHHistoryPage.js').then(m => new m.GHHistoryPage()),
  'gh-settings':() => import('./app/pages/GHSettingsPage.js').then(m => new m.GHSettingsPage()),
  jitpack:     () => import('./app/pages/JitPackPage.js').then(m => new m.JitPackPage()),
  'version-manager': () => import('./app/pages/VersionManagerPage.js').then(m => new m.VersionManagerPage()),
  'ecosystem-manager': () => import('./app/pages/EcosystemManagerPage.js').then(m => new m.EcosystemManagerPage()),
  'ecosystem-version-manager': () => import('./app/pages/EcosystemManagerPage.js').then(m => new m.EcosystemManagerPage()),
  build:      () => import('./app/pages/BuildPage.js').then(m => new m.BuildPage()),
  publish:    () => import('./app/pages/PublishPage.js').then(m => new m.PublishPage()),
  downloads:  () => import('./app/pages/DownloadsPage.js').then(m => new m.DownloadsPage()),
  logs:       () => import('./app/pages/LogsPage.js').then(m => new m.LogsPage()),
  settings:   () => import('./app/pages/SettingsPage.js').then(m => new m.SettingsPage()),
};

export class App {
  #router        = null;
  #toolbar       = null;
  #sidebar       = null;
  #bottomNav     = null;
  #consolePanel  = null;
  #statusBar     = null;
  #propertyPanel = null;
  #initialized   = false;
  #themeMode     = 'dark';
  #systemMq      = null;

  /**
   * Bootstrap the entire application.
   */
  async start() {
    try {
      consoleService.info('App: starting…');

      // Initialize i18n FIRST so all subsequently rendered UI is localized
      i18n.init();

      // Initialize IndexedDB
      await storage.init();
      consoleService.info('Storage: initialized');

      // Load saved theme: localStorage (fast boot) → IndexedDB → default.
      // Supports 'light' | 'dark' | 'system'.
      let theme = null;
      try { theme = localStorage.getItem('libgen.ui.theme'); } catch { /* private mode */ }
      if (theme !== 'light' && theme !== 'dark' && theme !== 'system') {
        theme = await storage.getSetting('theme', 'dark');
      }
      this.#applyTheme(theme);

      // Wire up UI layout panels (each module grabs its own element by ID)
      this.#toolbar       = new Toolbar();       this.#toolbar.init();
      this.#sidebar       = new Sidebar();       this.#sidebar.init();
      this.#bottomNav     = new BottomNav();     this.#bottomNav.init();
      this.#consolePanel  = new ConsolePanel();  await this.#consolePanel.init();
      this.#statusBar     = new StatusBar();     this.#statusBar.init();
      this.#propertyPanel = new PropertyPanel(); this.#propertyPanel.init();

      // Initialize toast and modal overlay services
      toast.init();
      modal.init();

      // Wire global event actions
      this.#wireEvents();

      // Start router
      this.#router = new Router('main-content', PAGE_MAP);
      this.#router.start('dashboard');

      // Load most-recent project (if any) — after router is ready
      await projectService.loadLast();

      this.#initialized = true;
      consoleService.success('App: ready');

    } catch (err) {
      console.error('[App] Fatal startup error:', err);
      const el = document.getElementById('app-error');
      if (el) { el.style.display = 'flex'; el.textContent = `Fatal error: ${err.message}`; }
    }
  }

  // ── Event wiring ──────────────────────────────────────────────────────────

  #wireEvents() {
    // Navigate to generator for new project
    eventBus.on('action:newProject', () => {
      this.#router?.navigate('generator');
    });

    // Open project — navigate to dashboard where user can pick from recents
    eventBus.on('action:openProject', () => {
      this.#router?.navigate('dashboard');
    });

    // Save current file / project
    eventBus.on('action:saveProject', () => {
      eventBus.emit('editor:save');
    });

    // Import project chooser (GitHub or Local ZIP)
    eventBus.on('action:importProject', () => {
      importChooserModal.open();
    });

    // Direct GitHub Import
    eventBus.on('action:importGitHub', (options = {}) => {
      gitHubImportModal.open(options);
    });

    // Direct Local ZIP Import (preserved)
    eventBus.on('action:importZip', () => {
      zipService.pickAndImport();
    });

    // Export project as ZIP (Toolbar emits action:exportZip)
    eventBus.on('action:exportZip', () => {
      zipService.exportProject();
    });
    // Also handle the generic name used by some page modules
    eventBus.on('action:exportProject', () => {
      zipService.exportProject();
    });

    // Navigate
    eventBus.on('router:navigate', ({ pageId, params }) => {
      this.#router?.navigate(pageId, params);
    });

    // Project loaded → log it
    eventBus.on('project:loaded', p => {
      consoleService.info(`Project: ${p?.name || 'none'}`);
    });

    // Theme change from Settings page
    eventBus.on('settings:themeChanged', ({ theme }) => {
      this.#applyTheme(theme);
    });

    // Theme cycle toggle from the app bar: light → dark → system → light
    eventBus.on('ui:themeToggle', () => {
      const order   = ['light', 'dark', 'system'];
      const labels  = { light: 'Light', dark: 'Dark', system: 'System' };
      const next    = order[(order.indexOf(this.#themeMode) + 1) % order.length];
      this.#applyTheme(next);
      storage.setSetting('theme', next).catch(() => {});
      toast.info(`Theme: ${labels[next]}`);
    });

    // Language toggle from the app bar: en ↔ bn
    eventBus.on('ui:langToggle', () => {
      const next = i18n.lang === 'bn' ? 'en' : 'bn';
      i18n.setLanguage(next);
      toast.info(next === 'bn' ? 'ভাষা: বাংলা' : 'Language: English');
    });

    // Prevent accidental rapid double-clicks on buttons and action triggers
    document.addEventListener('click', e => {
      const btn = e.target.closest('.btn, .toolbar-btn, .tab-btn');
      if (!btn) return;

      // Allow controls explicitly marked for multi-click
      if (btn.dataset.allowMultiClick === 'true') return;

      if (btn.classList.contains('is-debouncing')) {
        e.preventDefault();
        e.stopPropagation();
        return;
      }

      btn.classList.add('is-debouncing');
      setTimeout(() => {
        btn.classList.remove('is-debouncing');
      }, 350);
    }, true);

    // Keyboard shortcuts
    document.addEventListener('keydown', e => {
      // Ctrl+S / Cmd+S — save current file
      if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key === 's') {
        e.preventDefault();
        eventBus.emit('editor:save');
        return;
      }
      // Ctrl+Shift+P — go to generator
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'P') {
        e.preventDefault();
        this.#router?.navigate('generator');
        return;
      }
      // Ctrl+Shift+E — go to explorer
      if ((e.ctrlKey || e.metaKey) && e.shiftKey && e.key === 'E') {
        e.preventDefault();
        this.#router?.navigate('explorer');
      }
    });

    // Before unload — warn on unsaved changes
    window.addEventListener('beforeunload', e => {
      if (projectService.hasUnsavedChanges()) {
        e.preventDefault();
        e.returnValue = '';
      }
    });
  }

  /**
   * Apply a theme mode ('light' | 'dark' | 'system'), persist it, and keep
   * the icon in the app bar in sync. 'system' follows the OS color scheme
   * live via matchMedia.
   * @param {string} mode
   */
  #applyTheme(mode) {
    this.#themeMode = ['light', 'dark', 'system'].includes(mode) ? mode : 'dark';

    const resolved = this.#themeMode === 'system'
      ? (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark')
      : this.#themeMode;

    document.documentElement.classList.add('theme-transition');
    document.documentElement.dataset.theme = resolved;
    setTimeout(() => {
      document.documentElement.classList.remove('theme-transition');
    }, 250);

    try { localStorage.setItem('libgen.ui.theme', this.#themeMode); } catch { /* private mode */ }

    // Live-follow OS scheme changes while in System mode
    if (this.#themeMode === 'system' && !this.#systemMq) {
      this.#systemMq = window.matchMedia('(prefers-color-scheme: light)');
      this.#systemMq.addEventListener('change', () => {
        if (this.#themeMode === 'system') this.#applyTheme('system');
      });
    }

    eventBus.emit('app:themeApplied', { mode: this.#themeMode, resolved });
  }
}

// ── Boot ─────────────────────────────────────────────────────────────────
const app = new App();
app.start().catch(err => console.error('[App] Unhandled startup error:', err));
