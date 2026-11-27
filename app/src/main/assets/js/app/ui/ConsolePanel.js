/**
 * ConsolePanel UI module — renders live log entries in the bottom console.
 * Always visible: collapsed shows 28px header bar, expanded shows full body.
 */
import { eventBus }       from '../core/EventBus.js';
import { consoleService } from '../modules/storage/ConsoleService.js';
import { storage }        from '../modules/storage/StorageService.js';

const STORAGE_KEY = 'console_expanded';

export class ConsolePanel {
  #el        = null;
  #body      = null;
  #collapsed = false;   // false = expanded (visible body)

  async init() {
    this.#el = document.getElementById('console-panel');
    // Restore last state; default = expanded (false = not collapsed)
    const saved = await storage.getSetting(STORAGE_KEY, false);
    this.#collapsed = !!saved;
    this.#render();
    this.#bindEvents();
    eventBus.on('console:entry',   e => this.#appendEntry(e));
    eventBus.on('console:cleared', () => this.#clearBody());
    // Toolbar toggle event
    eventBus.on('toolbar:toggleConsole', () => this.#toggle());
    // Apply initial state without animation
    this.#applyState(false);
  }

  #render() {
    this.#el.innerHTML = `
      <div class="console-header" id="console-header">
        <span class="console-header-icon material-symbols-outlined">terminal</span>
        <span class="console-title">Console</span>
        <div class="console-actions">
          <button class="console-btn" id="console-export-btn" title="Export logs">
            <span class="material-symbols-outlined">download</span>
          </button>
          <button class="console-btn" id="console-clear-btn" title="Clear console">
            <span class="material-symbols-outlined">delete_sweep</span>
          </button>
          <button class="console-btn console-btn-toggle" id="console-toggle-btn" title="Expand / Collapse">
            <span class="material-symbols-outlined" id="console-toggle-icon">expand_less</span>
          </button>
        </div>
      </div>
      <div class="console-body" id="console-body"></div>
    `;
    this.#body = this.#el.querySelector('#console-body');
  }

  #bindEvents() {
    // Clicking the header bar (not a button) also toggles
    this.#el.querySelector('#console-header').addEventListener('click', e => {
      if (!e.target.closest('button')) this.#toggle();
    });

    this.#el.querySelector('#console-toggle-btn').addEventListener('click', e => {
      e.stopPropagation();
      this.#toggle();
    });

    this.#el.querySelector('#console-clear-btn').addEventListener('click', e => {
      e.stopPropagation();
      consoleService.clear();
    });

    this.#el.querySelector('#console-export-btn').addEventListener('click', e => {
      e.stopPropagation();
      consoleService.exportLogs();
    });
  }

  #toggle() {
    this.#collapsed = !this.#collapsed;
    this.#applyState(true);
    storage.setSetting(STORAGE_KEY, this.#collapsed).catch(() => {});
    eventBus.emit('console:stateChanged', { collapsed: this.#collapsed });
  }

  /** Apply collapsed/expanded state. @param {boolean} animate */
  #applyState(animate) {
    if (!animate) this.#el.classList.add('no-transition');
    this.#el.classList.toggle('collapsed', this.#collapsed);
    const icon = this.#el.querySelector('#console-toggle-icon');
    if (icon) icon.textContent = this.#collapsed ? 'expand_more' : 'expand_less';
    if (!animate) {
      // Force reflow then re-enable transitions
      this.#el.offsetHeight; // eslint-disable-line no-unused-expressions
      this.#el.classList.remove('no-transition');
    }
  }

  /** @param {{id,level,message,time}} entry */
  #appendEntry(entry) {
    if (!this.#body) return;
    const div = document.createElement('div');
    div.className = `console-entry ${entry.level}`;
    div.innerHTML = `
      <span class="console-time">${entry.time}</span>
      <span class="console-level ${entry.level}">${entry.level}</span>
      <span class="console-message">${this.#escapeHtml(entry.message)}</span>
    `;
    this.#body.appendChild(div);

    // Virtual scroll — keep max 300 visible entries
    const children = this.#body.children;
    if (children.length > 300) children[0].remove();

    // Auto-scroll to bottom
    this.#body.scrollTop = this.#body.scrollHeight;
  }

  #clearBody() {
    if (this.#body) this.#body.innerHTML = '';
  }

  #escapeHtml(str) {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }
}
