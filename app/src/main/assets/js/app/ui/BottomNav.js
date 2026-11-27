/**
 * BottomNav — mobile bottom navigation with a center FAB (New Project).
 * Rendered into #bottom-nav; hidden on tablet/desktop via CSS.
 */
import { eventBus } from '../core/EventBus.js';

const ITEMS = [
  { id: 'dashboard', label: 'Home',     icon: 'dashboard' },
  { id: 'explorer',  label: 'Files',    icon: 'folder_open' },
  { id: 'generator', label: 'New Project', icon: 'add', fab: true },
  { id: 'github',    label: 'GitHub',   icon: 'hub' },
  { id: 'settings',  label: 'Settings', icon: 'settings' },
];

/** Map any page id to its bottom-nav root tab (null = keep current). */
function rootTab(pageId) {
  if (!pageId) return null;
  if (pageId === 'github' || pageId.startsWith('gh-') || pageId.startsWith('ecosystem')) return 'github';
  if (['explorer', 'editor', 'project'].includes(pageId)) return 'explorer';
  if (['generator', 'templates', 'gradle', 'manifest', 'environment', 'jitpack', 'build', 'publish'].includes(pageId)) return 'generator';
  if (['settings', 'logs', 'downloads', 'version-manager'].includes(pageId)) return 'settings';
  if (pageId === 'dashboard' || pageId === 'analyzer') return 'dashboard';
  return null;
}

export class BottomNav {
  #el = null;
  #active = 'dashboard';

  init() {
    this.#el = document.getElementById('bottom-nav');
    if (!this.#el) return;
    this.#render();
    this.#bind();
    // Reflect actual navigation (Router emits router:changed after mount)
    eventBus.on('router:changed', ({ pageId }) => this.setActive(pageId));
  }

  #render() {
    this.#el.innerHTML = `<div class="bottom-nav-items" role="tablist" aria-label="Main">` +
      ITEMS.map(item => {
        if (item.fab) {
          return `
          <button class="bottom-nav-item bottom-nav-fab" data-page="${item.id}"
                  aria-label="${item.label}" title="${item.label}">
            <span class="bottom-nav-fab-btn"><span class="material-symbols-outlined">${item.icon}</span></span>
          </button>`;
        }
        const active = item.id === this.#active;
        return `
          <button class="bottom-nav-item${active ? ' active' : ''}" data-page="${item.id}"
                  aria-label="${item.label}" aria-current="${active ? 'page' : 'false'}">
            <span class="material-symbols-outlined">${item.icon}</span>
            <span class="bottom-nav-label">${item.label}</span>
          </button>`;
      }).join('') + `</div>`;
  }

  #bind() {
    this.#el.addEventListener('click', e => {
      const item = e.target.closest('.bottom-nav-item');
      if (!item) return;
      const pageId = item.dataset.page;
      this.setActive(pageId);
      eventBus.emit('router:navigate', { pageId });
    });
  }

  /** @param {string} pageId */
  setActive(pageId) {
    const root = rootTab(pageId);
    if (root) this.#active = root;
    this.#el?.querySelectorAll('.bottom-nav-item').forEach(el => {
      const active = !el.classList.contains('bottom-nav-fab') && el.dataset.page === this.#active;
      el.classList.toggle('active', active);
      el.setAttribute('aria-current', active ? 'page' : 'false');
    });
  }
}
