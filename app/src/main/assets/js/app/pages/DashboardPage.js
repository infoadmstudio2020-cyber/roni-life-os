/**
 * DashboardPage — landing page with stats, recent projects, and template gallery.
 */
import { eventBus } from '../core/EventBus.js';
import { projectService } from '../modules/storage/ProjectService.js';
import { storage } from '../modules/storage/StorageService.js';
import { consoleService } from '../modules/storage/ConsoleService.js';

export class DashboardPage {
  #container = null;

  async init(container) {
    this.#container = container;
    await this.#render();
  }

  async #render() {
    const recent   = await projectService.getRecent();
    const allProjs = await projectService.getAll();
    const exports  = await storage.getAll('exports');

    this.#container.innerHTML = `
      <div class="page-container dashboard">

        <!-- Welcome Banner -->
        <div class="dashboard-welcome">
          <div class="dashboard-welcome-icon">
            <span class="material-symbols-outlined">android</span>
          </div>
          <div class="dashboard-welcome-body">
            <div class="dashboard-welcome-title">Android Library Generator</div>
            <div class="dashboard-welcome-sub">Generate fully structured, JitPack-ready Android libraries in seconds.</div>
          </div>
          <div class="dashboard-welcome-actions">
            <button type="button" class="btn btn-primary" id="dash-new-btn">
              <span class="material-symbols-outlined">add</span>New Project
            </button>
            <button type="button" class="btn btn-secondary dash-btn-accent" id="dash-gh-import-btn" title="Import repository from GitHub">
              <span class="material-symbols-outlined">hub</span>GitHub Import
            </button>
            <button type="button" class="btn btn-secondary dash-btn-accent" id="dash-analyzer-btn">
              <span class="material-symbols-outlined">analytics</span>Analyze Project
            </button>
            <button type="button" class="btn btn-secondary dash-btn-primary" id="dash-test-btn">
              <span class="material-symbols-outlined">fact_check</span>Full E2E Test
            </button>
            <button type="button" class="btn btn-secondary" id="dash-import-btn">
              <span class="material-symbols-outlined">upload</span>Import
            </button>
          </div>
        </div>

        <!-- Stats -->
        <div class="dashboard-stats">
          ${this.#statCard('folder_special', 'Projects',       allProjs.length,        'total created')}
          ${this.#statCard('download',       'Exports',        exports.length,          'ZIP exports')}
          ${this.#statCard('description',    'Total Files',    allProjs.reduce((s,p) => s + Object.keys(p.files||{}).length, 0), 'across all projects')}
          ${this.#statCard('verified',       'JitPack Ready',  allProjs.length,        'projects ready')}
        </div>

        <!-- Recent Projects -->
        <div>
          <div class="dashboard-section-header">
            <div class="dashboard-section-title">Recent Projects</div>
            <button class="btn btn-ghost btn-sm" id="dash-clear-recent">Clear</button>
          </div>
          ${recent.length === 0
            ? `<div class="card empty-state-card dashboard-empty">
                <span class="material-symbols-outlined empty-state-icon">folder_off</span>
                <div class="empty-state-text">No recent projects. Create one to get started.</div>
               </div>`
            : `<div class="recent-projects-list">
                ${recent.map(p => `
                  <div class="recent-project-item" data-id="${p.id}">
                    <div class="recent-project-icon">
                      <span class="material-symbols-outlined">android</span>
                    </div>
                    <div class="recent-project-body">
                      <div class="recent-project-name">${p.name}</div>
                      <div class="recent-project-meta">${p.id} · ${this.#relTime(p.updatedAt)}</div>
                    </div>
                    <button class="btn btn-ghost btn-sm btn-icon" data-open="${p.id}" title="Open project">
                      <span class="material-symbols-outlined">arrow_forward</span>
                    </button>
                  </div>`).join('')}
               </div>`}
        </div>

        <!-- Template Gallery -->
        <div>
          <div class="dashboard-section-header">
            <div class="dashboard-section-title">Template Gallery</div>
            <button class="btn btn-ghost btn-sm" id="dash-all-templates">View All</button>
          </div>
          <div class="template-gallery">
            ${this.#templateCards()}
          </div>
        </div>

      </div>`;

    this.#bindEvents();
  }

  #statCard(icon, label, value, sub) {
    return `
      <div class="stat-card">
        <div class="stat-card-icon">
          <span class="material-symbols-outlined">${icon}</span>
        </div>
        <div class="stat-card-value">${value}</div>
        <div class="stat-card-label">${label}</div>
        <div class="stat-card-sub">${sub}</div>
      </div>`;
  }

  #templateCards() {
    const templates = [
      { id: 'android-java-library', icon: 'android',          name: 'Android Java Library',   desc: 'Full JitPack-compatible Java library',       color: '#22D3EE' },
      { id: 'android-kotlin-library',icon: 'android',         name: 'Android Kotlin Library',  desc: 'Kotlin-first library with coroutines',        color: '#818CF8' },
      { id: 'crash-monitor',        icon: 'bug_report',       name: 'Crash Monitor',          desc: 'Uncaught exception monitoring library',       color: '#EF4444' },
      { id: 'floating-overlay',     icon: 'picture_in_picture',name: 'Floating Overlay',      desc: 'System-window overlay manager',               color: '#F59E0B' },
      { id: 'webview-library',      icon: 'language',         name: 'WebView Library',        desc: 'Enhanced WebView with JS bridge',             color: '#10B981' },
      { id: 'custom',               icon: 'tune',             name: 'Custom Template',        desc: 'Start from minimal scaffold',                 color: '#6B7280' },
    ];
    return templates.map(t => `
      <div class="template-card" data-template="${t.id}" role="button" tabindex="0">
        <span class="material-symbols-outlined template-card-icon" style="color:${t.color};font-variation-settings:'FILL' 1,'wght' 400">${t.icon}</span>
        <div class="template-card-name">${t.name}</div>
        <div class="template-card-desc">${t.desc}</div>
      </div>`).join('');
  }

  #bindEvents() {
    this.#container.querySelector('#dash-new-btn')?.addEventListener('click', () =>
      eventBus.emit('action:newProject'));

    this.#container.querySelector('#dash-gh-import-btn')?.addEventListener('click', () =>
      eventBus.emit('action:importGitHub'));

    this.#container.querySelector('#dash-analyzer-btn')?.addEventListener('click', () =>
      eventBus.emit('router:navigate', { pageId: 'analyzer' }));

    this.#container.querySelector('#dash-test-btn')?.addEventListener('click', () =>
      eventBus.emit('router:navigate', { pageId: 'gh-testing' }));

    this.#container.querySelector('#dash-import-btn')?.addEventListener('click', () =>
      eventBus.emit('action:importProject'));

    this.#container.querySelector('#dash-all-templates')?.addEventListener('click', () =>
      eventBus.emit('router:navigate', { pageId: 'templates' }));

    this.#container.querySelector('#dash-clear-recent')?.addEventListener('click', async () => {
      await storage.setSetting('recentProjects', []);
      await this.#render();
    });

    // Recent project open
    this.#container.querySelectorAll('[data-open]').forEach(btn => {
      btn.addEventListener('click', async e => {
        e.stopPropagation();
        const id = btn.dataset.open;
        await projectService.load(id);
        eventBus.emit('router:navigate', { pageId: 'explorer' });
      });
    });

    this.#container.querySelectorAll('.recent-project-item').forEach(item => {
      item.addEventListener('click', async () => {
        await projectService.load(item.dataset.id);
        eventBus.emit('router:navigate', { pageId: 'explorer' });
      });
    });

    // Template cards
    this.#container.querySelectorAll('.template-card[data-template]').forEach(card => {
      card.addEventListener('click', () => {
        eventBus.emit('router:navigate', { pageId: 'generator', params: { template: card.dataset.template } });
      });
      card.addEventListener('keydown', e => { if (e.key === 'Enter') card.click(); });
    });
  }

  #relTime(ts) {
    if (!ts) return 'Unknown';
    const diff = Date.now() - ts;
    if (diff < 60000)      return 'just now';
    if (diff < 3600000)    return `${Math.floor(diff/60000)}m ago`;
    if (diff < 86400000)   return `${Math.floor(diff/3600000)}h ago`;
    return new Date(ts).toLocaleDateString();
  }
}
