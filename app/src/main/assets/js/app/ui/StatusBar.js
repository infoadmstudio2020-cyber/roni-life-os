/**
 * StatusBar UI module — bottom status bar with live info.
 */
import { eventBus } from '../core/EventBus.js';
import { projectService } from '../modules/storage/ProjectService.js';
import { t } from '../core/I18n.js';

export class StatusBar {
  #el = null;

  init() {
    this.#el = document.getElementById('status-bar');
    this.#render();
    this.#bindEvents();
    this.#startClock();
  }

  #render() {
    this.#el.innerHTML = `
      <div class="status-item">
        <span class="status-dot" id="status-dot"></span>
        <span id="status-label">Ready</span>
      </div>
      <div class="status-item desktop-only" id="status-project">
        <span class="material-symbols-outlined">folder</span>
        <span id="status-project-name">No project</span>
      </div>
      <div class="status-item desktop-only" id="status-files">
        <span class="material-symbols-outlined">description</span>
        <span id="status-file-count">0 files</span>
      </div>
      <div class="status-spacer"></div>
      <div class="status-item desktop-only" id="status-storage">
        <span class="material-symbols-outlined">storage</span>
        <span id="status-storage-text">—</span>
      </div>
      <div class="status-item">
        <span id="status-clock"></span>
      </div>
    `;
    this.#tick();
  }

  #bindEvents() {
    eventBus.on('project:created', () => this.#updateProject());
    eventBus.on('project:loaded',  () => this.#updateProject());
    eventBus.on('project:closed',  () => this.#updateProject());
    eventBus.on('project:saved',   () => this.#setStatus(t('Saved'), false));
    eventBus.on('project:fileChanged', () => this.#updateFileCount());
    eventBus.on('project:fileDeleted', () => this.#updateFileCount());
    eventBus.on('action:generating',   () => this.#setStatus(t('Generating…'), true));
    eventBus.on('action:generated',    () => this.#setStatus(t('Done'), false));
    eventBus.on('action:exporting',    () => this.#setStatus(t('Exporting…'), true));
    eventBus.on('action:exported',     () => this.#setStatus(t('Exported'), false));
  }

  #startClock() {
    setInterval(() => this.#tick(), 1000);
  }

  #tick() {
    const el = this.#el?.querySelector('#status-clock');
    if (el) el.textContent = new Date().toLocaleTimeString('en-US', { hour12: false });
  }

  #setStatus(text, syncing = false) {
    const dot   = this.#el?.querySelector('#status-dot');
    const label = this.#el?.querySelector('#status-label');
    if (dot)   dot.className   = `status-dot${syncing ? ' syncing' : ''}`;
    if (label) label.textContent = text;
    if (!syncing) setTimeout(() => this.#setStatus(t('Ready'), false), 3000);
  }

  #updateProject() {
    const proj = projectService.current;
    const name = this.#el?.querySelector('#status-project-name');
    if (name) name.textContent = proj?.name ?? t('No project');
    this.#updateFileCount();
  }

  #updateFileCount() {
    const proj  = projectService.current;
    const count = proj ? Object.keys(proj.files || {}).length : 0;
    const el    = this.#el?.querySelector('#status-file-count');
    if (el) el.textContent = t('{n} files', { n: count });
  }

  async updateStorage() {
    const el = this.#el?.querySelector('#status-storage-text');
    if (!el) return;
    try {
      const { storage: s } = await import('../modules/storage/StorageService.js');
      const { usage } = await s.estimateUsage();
      el.textContent = usage ? `${(usage / 1024).toFixed(0)} KB` : '—';
    } catch { el.textContent = '—'; }
  }
}
