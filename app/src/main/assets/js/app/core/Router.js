/**
 * Router — maps pageId → page factory, renders into a container element.
 * Supports lazy-loading page modules.
 */
import { eventBus } from './EventBus.js';

export class Router {
  #containerId = '';
  #pageMap     = {};
  #pageCache   = new Map();
  #currentPage = null;
  #currentId   = null;
  #navSequence = 0;

  /**
   * @param {string}  containerId  ID of the DOM element to render pages into
   * @param {Object}  pageMap      { pageId: () => Promise<PageInstance> }
   */
  constructor(containerId, pageMap) {
    this.#containerId = containerId;
    this.#pageMap     = pageMap;
  }

  /** Start the router and navigate to the default page. */
  start(defaultPage = 'dashboard') {
    // Listen for sidebar nav clicks
    eventBus.on('sidebar:navigate', ({ pageId, params }) => {
      this.navigate(pageId, params);
    });
    eventBus.on('router:navigate', ({ pageId, params }) => {
      this.navigate(pageId, params);
    });
    this.navigate(defaultPage);
  }

  /**
   * Navigate to a page.
   * @param {string} pageId
   * @param {object} [params]
   */
  async navigate(pageId, params = {}) {
    const container = document.getElementById(this.#containerId);
    if (!container) return;

    const factory = this.#pageMap[pageId];
    if (!factory) {
      console.warn(`[Router] Unknown page: ${pageId}`);
      return;
    }

    const seq = ++this.#navSequence;

    // Tear down previous page cleanly
    try {
      if (this.#currentPage?.destroy) {
        this.#currentPage.destroy();
      }
    } catch (e) {
      console.warn('[Router] Error destroying previous page:', e);
    }
    this.#currentPage = null;

    // Show loading state ONLY if page takes more than 120ms (prevents screen flicker)
    let loadingTimer = setTimeout(() => {
      if (seq === this.#navSequence) {
        container.innerHTML = `
          <div style="display:flex;align-items:center;justify-content:center;height:100%;color:var(--text-muted);gap:var(--sp-3)">
            <span class="material-symbols-outlined rotating" style="font-size:24px">sync</span>
            <span style="font-size:var(--text-sm)">Loading…</span>
          </div>`;
      }
    }, 120);

    try {
      // Reuse cached page instance if available to avoid repeated module imports & allocations
      let page = this.#pageCache.get(pageId);
      if (!page) {
        page = await factory();
        this.#pageCache.set(pageId, page);
      }
      clearTimeout(loadingTimer);
      if (seq !== this.#navSequence) return;

      this.#currentPage = page;
      this.#currentId   = pageId;
      container.innerHTML = '';
      container.scrollTop = 0;
      await page.init(container, params);
      if (seq !== this.#navSequence) return;

      // Add smooth entry animation without layout jumps
      container.classList.remove('page-enter');
      void container.offsetWidth; // trigger reflow
      container.classList.add('page-enter');
      setTimeout(() => container.classList.remove('page-enter'), 250);

      eventBus.emit('router:changed', { pageId, params });
    } catch (err) {
      clearTimeout(loadingTimer);
      if (seq !== this.#navSequence) return;
      console.error(`[Router] Error loading page "${pageId}":`, err);
      container.innerHTML = `
        <div class="page-container">
          <div class="card" style="padding:var(--sp-8);text-align:center">
            <span class="material-symbols-outlined" style="font-size:48px;color:var(--error)">error</span>
            <div style="margin-top:var(--sp-3);color:var(--text-primary);font-weight:600">Failed to load "${pageId}"</div>
            <div style="margin-top:var(--sp-2);color:var(--text-muted);font-size:var(--text-sm)">${err.message}</div>
          </div>
        </div>`;
    }
  }

  /** Current active page ID. */
  get currentId() { return this.#currentId; }

  /** Clear cached page instances (e.g. on project change) */
  clearCache() {
    this.#pageCache.clear();
  }
}
