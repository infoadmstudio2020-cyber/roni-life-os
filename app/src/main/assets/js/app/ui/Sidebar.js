/**
 * Sidebar UI module — navigation and page routing.
 */
import { eventBus } from '../core/EventBus.js';

const NAV_ITEMS = [
  { id: 'dashboard',  label: 'Dashboard',  icon: 'dashboard',        section: 'main' },
  { id: 'analyzer',   label: 'Analyzer',   icon: 'analytics',        section: 'main' },
  { id: 'project',    label: 'Project',    icon: 'folder_special',   section: 'main' },
  { id: 'templates',  label: 'Templates',  icon: 'grid_view',        section: 'main' },
  { id: 'environment',label: 'Environment',icon: 'tune',             section: 'main' },
  { id: 'explorer',   label: 'Explorer',   icon: 'account_tree',     section: 'files' },
  { id: 'editor',     label: 'Editor',     icon: 'code',             section: 'files' },
  { id: 'generator',  label: 'Generator',  icon: 'auto_fix_high',    section: 'generate' },
  { id: 'conflict-resolver', label: 'Class & D8 Guard', icon: 'shield_with_heart', section: 'generate' },
  { id: 'gradle',     label: 'Gradle',     icon: 'build',            section: 'generate' },
  { id: 'manifest',   label: 'Manifest',   icon: 'description',      section: 'generate' },
  { id: 'jitpack',    label: 'JitPack Generator', icon: 'terminal', section: 'publish' },
  { id: 'build',      label: 'Build',      icon: 'verified',         section: 'publish' },
  { id: 'publish',    label: 'Publish',    icon: 'rocket_launch',    section: 'publish' },
  { id: 'gh-auth',    label: 'Authentication', icon: 'verified_user', section: 'github' },
  { id: 'gh-import',  label: 'Import Repo',    icon: 'cloud_download', section: 'github' },
  { id: 'gh-repo',    label: 'Repository',    icon: 'folder_special', section: 'github' },
  { id: 'gh-files',   label: 'Files',         icon: 'folder_zip',     section: 'github' },
  { id: 'gh-commit',  label: 'Commit',         icon: 'commit',         section: 'github' },
  { id: 'gh-tags',    label: 'Tags',           icon: 'sell',           section: 'github' },
  { id: 'gh-release', label: 'Release',        icon: 'new_releases',   section: 'github' },
  { id: 'gh-publish', label: 'Publish',        icon: 'rocket_launch',  section: 'github' },
  { id: 'gh-wizard',  label: 'Actions Wizard', icon: 'smart_toy',      section: 'github' },
  { id: 'gh-testing', label: 'E2E Testing',    icon: 'fact_check',     section: 'github' },
  { id: 'gh-history', label: 'History',        icon: 'history',        section: 'github' },
  { id: 'gh-settings',label: 'Settings',       icon: 'tune',           section: 'github' },
  { id: 'ecosystem-manager', label: 'Ecosystem Version Manager', icon: 'hub', section: 'github' },
  { id: 'downloads',  label: 'Downloads',  icon: 'download',         section: 'tools' },
  { id: 'version-manager', label: 'Version Manager', icon: 'system_update', section: 'tools' },
  { id: 'logs',       label: 'Logs',       icon: 'receipt_long',     section: 'tools' },
  { id: 'settings',   label: 'Settings',   icon: 'settings',         section: 'tools' },
];

const SECTIONS = {
  main:     'Main',
  files:    'Files',
  generate: 'Generate',
  publish:  'Publish',
  github:   'GitHub',
  tools:    'Tools',
};

export class Sidebar {
  #el          = null;
  #activePage  = 'dashboard';
  #expanded    = false;
  #overlay     = null;
  #badges      = {};

  init() {
    this.#el = document.getElementById('sidebar');
    this.#createOverlay();
    this.#render();
    this.#bindEvents();

    // Keep active item in sync with actual navigation (programmatic routes
    // like keyboard shortcuts emit router:changed only).
    eventBus.on('router:changed', ({ pageId }) => this.setActive(pageId));
    eventBus.on('router:navigate', ({ pageId }) => this.setActive(pageId));
  }

  #createOverlay() {
    this.#overlay = document.createElement('div');
    this.#overlay.className = 'sidebar-overlay';
    this.#overlay.addEventListener('click', () => this.closeMobile());
    document.body.appendChild(this.#overlay);
  }

  #render() {
    const grouped = {};
    NAV_ITEMS.forEach(item => {
      if (!grouped[item.section]) grouped[item.section] = [];
      grouped[item.section].push(item);
    });

    let html = `
      <div class="sidebar-drawer-header mobile-only">
        <div class="sidebar-drawer-brand" id="sidebar-drawer-brand-home" role="button" tabindex="0" title="RONi Android Library Generator">
          <span class="material-symbols-outlined sidebar-brand-icon">android</span>
          <div class="sidebar-drawer-meta">
            <div class="sidebar-drawer-title-row">
              <span class="sidebar-drawer-title">RONi LibGen</span>
              <span class="sidebar-version-badge" id="sidebar-version-badge" role="button" tabindex="0" title="Open Version Update Manager">v0.1.3.6</span>
            </div>
            <span class="sidebar-drawer-sub">Android Library Generator</span>
          </div>
        </div>
        <button type="button" class="sidebar-drawer-close" id="sidebar-close-btn" aria-label="Close navigation">
          <span class="material-symbols-outlined">close</span>
        </button>
      </div>
      <nav class="sidebar-nav">`;
    Object.entries(grouped).forEach(([sectionId, items]) => {
      html += `<div class="sidebar-nav-section">
        <div class="sidebar-nav-label">${SECTIONS[sectionId]}</div>`;
      items.forEach(item => {
        const badge = this.#badges[item.id]
          ? `<span class="sidebar-item-badge">${this.#badges[item.id]}</span>` : '';
        html += `
          <div class="sidebar-item${item.id === this.#activePage ? ' active' : ''}"
               data-page="${item.id}" data-tooltip="${item.label}" role="button" tabindex="0"
               aria-label="${item.label}${item.id === this.#activePage ? ' (current)' : ''}">
            <span class="material-symbols-outlined sidebar-item-icon">${item.icon}</span>
            <span class="sidebar-item-label">${item.label}</span>
            ${badge}
          </div>`;
      });
      html += '</div>';
    });

    html += `</nav>
      <div class="sidebar-footer desktop-only">
        <div class="sidebar-toggle" id="sidebar-expand-btn" title="${this.#expanded ? 'Collapse' : 'Expand'}" role="button" tabindex="0">
          <span class="material-symbols-outlined">${this.#expanded ? 'chevron_left' : 'chevron_right'}</span>
        </div>
      </div>`;

    this.#el.innerHTML = html;
    if (this.#expanded) this.#el.classList.add('expanded');
  }

  #bindEvents() {
    this.#el.addEventListener('click', e => {
      if (e.target.closest('#sidebar-close-btn')) {
        this.closeMobile();
        return;
      }
      if (e.target.closest('#sidebar-version-badge')) {
        this.setActive('version-manager');
        eventBus.emit('router:navigate', { pageId: 'version-manager' });
        this.closeMobile();
        return;
      }
      if (e.target.closest('#sidebar-drawer-brand-home')) {
        this.setActive('dashboard');
        eventBus.emit('router:navigate', { pageId: 'dashboard' });
        this.closeMobile();
        return;
      }
      const item = e.target.closest('.sidebar-item');
      if (item) {
        const pageId = item.dataset.page;
        this.setActive(pageId);
        eventBus.emit('router:navigate', { pageId });
        this.closeMobile();
        return;
      }
      const toggle = e.target.closest('#sidebar-expand-btn');
      if (toggle) this.toggleExpand();
    });

    // Mobile touch swipe left to close drawer
    let touchStartX = 0;
    this.#el.addEventListener('touchstart', e => {
      if (e.touches.length === 1) touchStartX = e.touches[0].clientX;
    }, { passive: true });

    this.#el.addEventListener('touchmove', e => {
      if (e.touches.length === 1) {
        const diffX = e.touches[0].clientX - touchStartX;
        if (diffX < -50) {
          this.closeMobile();
        }
      }
    }, { passive: true });

    this.#el.addEventListener('keydown', e => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.target.click();
      }
    });

    eventBus.on('sidebar:mobileToggle', () => this.toggleMobile());
    eventBus.on('sidebar:desktopToggle', () => this.toggleCollapse());
  }

  /** @param {string} pageId */
  setActive(pageId) {
    this.#activePage = pageId;
    this.#el.querySelectorAll('.sidebar-item').forEach(el => {
      const active = el.dataset.page === pageId;
      el.classList.toggle('active', active);
      el.setAttribute('aria-label',
        `${NAV_ITEMS.find(i => i.id === el.dataset.page)?.label}${active ? ' (current)' : ''}`);
    });
  }

  toggleExpand() {
    this.#expanded = !this.#expanded;
    this.#el.classList.remove('collapsed');
    this.#el.classList.toggle('expanded', this.#expanded);
    const btn  = this.#el.querySelector('#sidebar-expand-btn');
    const icon = btn?.querySelector('.material-symbols-outlined');
    if (icon) icon.textContent = this.#expanded ? 'chevron_left' : 'chevron_right';
    eventBus.emit('sidebar:expandedChanged', this.#expanded);
  }

  toggleCollapse() {
    if (this.#el.classList.contains('collapsed')) {
      this.#el.classList.remove('collapsed');
    } else if (this.#el.classList.contains('expanded')) {
      this.#el.classList.remove('expanded');
      this.#expanded = false;
    } else {
      this.#el.classList.add('collapsed');
    }
  }

  toggleMobile() {
    const open = this.#el.classList.toggle('mobile-open');
    this.#overlay.classList.toggle('visible', open);
  }

  closeMobile() {
    this.#el.classList.remove('mobile-open');
    this.#overlay.classList.remove('visible');
  }

  /**
   * Set a badge count on a nav item.
   * @param {string} pageId
   * @param {number|null} count  null = remove
   */
  setBadge(pageId, count) {
    if (count === null || count === 0) {
      delete this.#badges[pageId];
    } else {
      this.#badges[pageId] = count;
    }
    const item = this.#el.querySelector(`[data-page="${pageId}"] .sidebar-item-badge`);
    if (item) {
      if (count) { item.textContent = count; item.style.display = ''; }
      else item.style.display = 'none';
    }
  }
}
