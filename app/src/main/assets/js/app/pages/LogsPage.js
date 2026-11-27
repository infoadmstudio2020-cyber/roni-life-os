/**
 * LogsPage
 *
 * Features:
 * - Display logs
 * - Filter by level
 * - Search logs
 * - Copy single log
 * - Copy all filtered logs
 * - Download logs as .txt
 * - Clear logs
 * - Auto scroll toggle
 * - Scroll to top
 * - Live updates
 * - Responsive desktop / tablet / mobile UI
 *
 * NOTE: All styling lives in css/pages/logs.css — no CSS in this file.
 */

import { consoleService } from '../modules/storage/ConsoleService.js';
import { eventBus } from '../core/EventBus.js';
import { ClipboardUtil } from '../core/ClipboardUtil.js';

export class LogsPage {
  #container = null;
  #filter = 'all';
  #search = '';
  #unsub = null;
  #copyTimers = new Map();
  #autoScroll = true;

  async init(container) {
    this.#container = container;

    this.#render();

    this.#unsub = eventBus.on('console:entry', () => {
      this.#renderEntries();
    });
  }

  destroy() {
    this.#unsub?.();
    this.#unsub = null;

    for (const timer of this.#copyTimers.values()) {
      clearTimeout(timer);
    }

    this.#copyTimers.clear();
    this.#container = null;
  }

  /* =========================================================
     Main Render
     ========================================================= */

  #render() {
    if (!this.#container) return;

    this.#container.innerHTML = `
      <div class="page-container logs-page">

        <!-- Header -->
        <div class="page-header logs-page-header">

          <div class="logs-title-area">

            <div class="logs-title-icon">
              <span class="material-symbols-outlined">
                receipt_long
              </span>
            </div>

            <div class="logs-title-content">

              <h1 class="page-title logs-title">
                Logs
              </h1>

              <p class="page-subtitle logs-subtitle">
                Application activity and diagnostic logs
              </p>

            </div>

          </div>

          <div class="logs-header-status">
            <span class="logs-live-dot"></span>
            <span>Live</span>
          </div>

        </div>


        <!-- Main Card -->
        <div class="card logs-card">

          <!-- Toolbar -->
          <div class="logs-toolbar">

            <!-- Row 1: Filter -->
            <div class="logs-toolbar-row">

              <div class="logs-filter-section">

                <div class="logs-section-label">
                  Filter
                </div>

                <div
                  class="logs-filters"
                  role="group"
                  aria-label="Log filters">

                  ${this.#renderFilters()}

                </div>

              </div>

            </div>

            <!-- Row 2: Search + Actions -->
            <div class="logs-toolbar-row">

              <div class="logs-toolbar-actions">

                <!-- Search -->
                <div class="logs-search-wrapper">

                <span
                  class="material-symbols-outlined logs-search-icon">
                  search
                </span>

                <input
                  id="logs-search"
                  class="input logs-search"
                  type="search"
                  autocomplete="off"
                  spellcheck="false"
                  placeholder="Search logs..."
                  value="${this.#esc(this.#search)}"
                  aria-label="Search logs"
                />

                ${
                  this.#search
                    ? `
                      <button
                        id="logs-search-clear"
                        class="logs-search-clear"
                        type="button"
                        title="Clear search"
                        aria-label="Clear search">

                        <span class="material-symbols-outlined">
                          close
                        </span>

                      </button>
                    `
                    : ''
                }

              </div>


              <!-- Copy -->
              <button
                class="btn btn-ghost logs-tool-btn"
                id="logs-copy-all-btn"
                type="button"
                title="Copy all filtered logs"
                aria-label="Copy all filtered logs">

                <span class="material-symbols-outlined logs-tool-icon">
                  content_copy
                </span>

                <span class="logs-tool-label">
                  Copy
                </span>

              </button>


              <!-- Download -->
              <button
                class="btn btn-ghost logs-tool-btn"
                id="logs-export-btn"
                type="button"
                title="Download logs as .txt"
                aria-label="Download logs as .txt">

                <span class="material-symbols-outlined logs-tool-icon">
                  download
                </span>

                <span class="logs-tool-label">
                  Download
                </span>

              </button>


              <!-- Clear -->
              <button
                class="btn btn-ghost logs-tool-btn logs-danger-btn"
                id="logs-clear-btn"
                type="button"
                title="Clear logs"
                aria-label="Clear logs">

                <span class="material-symbols-outlined logs-tool-icon">
                  delete_sweep
                </span>

                <span class="logs-tool-label">
                  Clear
                </span>

              </button>


              <!-- Auto Scroll -->
              <button
                class="btn btn-ghost logs-tool-btn"
                id="logs-autoscroll-btn"
                type="button"
                title="Toggle auto scroll"
                aria-label="Toggle auto scroll"
                aria-pressed="true">

                <span class="material-symbols-outlined logs-tool-icon">
                  keyboard_double_arrow_down
                </span>

                <span class="logs-tool-label">
                  Auto Scroll
                </span>

              </button>


              <!-- Top -->
              <button
                class="btn btn-ghost logs-tool-btn"
                id="logs-top-btn"
                type="button"
                title="Scroll to top"
                aria-label="Scroll to top">

                <span class="material-symbols-outlined logs-tool-icon">
                  keyboard_double_arrow_up
                </span>

                <span class="logs-tool-label">
                  Top
                </span>

              </button>

            </div>

          </div>


          <!-- Stats -->
          <div class="logs-stats">

            <div class="logs-stat">

              <span class="logs-stat-label">
                Showing
              </span>

              <strong
                class="logs-stat-value"
                id="logs-visible-count">
                0
              </strong>

            </div>


            <div class="logs-stat-separator"></div>


            <div class="logs-stat">

              <span class="logs-stat-label">
                Filter
              </span>

              <strong
                class="logs-stat-value"
                id="logs-active-filter">
                All
              </strong>

            </div>


            <div class="logs-stat-separator"></div>


            <div class="logs-stat">

              <span class="logs-stat-label">
                Status
              </span>

              <strong
                class="logs-status-text"
                id="logs-status">
                Live
              </strong>

            </div>

          </div>


          <!-- Log Body -->
          <div
            id="logs-body"
            class="console-body logs-body"
            role="log"
            aria-live="polite"
            aria-label="Application logs">
          </div>

        </div>

      </div>
    `;

    this.#bindEvents();
    this.#updateAutoScrollButton();
    this.#renderEntries();
  }

  /* =========================================================
     Filters
     ========================================================= */

  #renderFilters() {
    const filters = [
      ['all', 'All', 'list'],
      ['info', 'Info', 'info'],
      ['success', 'Success', 'check_circle'],
      ['warning', 'Warning', 'warning'],
      ['error', 'Error', 'error']
    ];

    return filters
      .map(([value, label, icon]) => {
        const active =
          value === this.#filter ? 'active' : '';

        return `
          <button
            class="logs-filter-btn ${active}"
            data-filter="${this.#esc(value)}"
            type="button"
            aria-pressed="${value === this.#filter}"
            title="Show ${this.#esc(label)} logs">

            <span class="material-symbols-outlined">
              ${this.#esc(icon)}
            </span>

            <span>
              ${this.#esc(label)}
            </span>

          </button>
        `;
      })
      .join('');
  }

  /* =========================================================
     Event Binding
     ========================================================= */

  #bindEvents() {
    if (!this.#container) return;

    /*
     * Filters
     */
    this.#container
      .querySelectorAll('[data-filter]')
      .forEach(button => {
        button.addEventListener('click', () => {
          this.#filter = button.dataset.filter || 'all';

          this.#updateFilterButtons();
          this.#renderEntries();
        });
      });


    /*
     * Search
     */
    this.#container
      .querySelector('#logs-search')
      ?.addEventListener('input', event => {
        this.#search = String(
          event.target.value || ''
        )
          .toLowerCase()
          .trim();

        this.#renderEntries();
        this.#updateSearchClearButton();
      });


    /*
     * Search clear
     */
    this.#container
      .querySelector('#logs-search-clear')
      ?.addEventListener('click', () => {
        this.#search = '';

        this.#render();

        requestAnimationFrame(() => {
          this.#container
            ?.querySelector('#logs-search')
            ?.focus();
        });
      });


    /*
     * Copy all
     */
    this.#container
      .querySelector('#logs-copy-all-btn')
      ?.addEventListener('click', () => {
        this.#copyAllLogs();
      });


    /*
     * Export
     */
    this.#container
      .querySelector('#logs-export-btn')
      ?.addEventListener('click', () => {
        this.#exportLogs();
      });


    /*
     * Clear
     */
    this.#container
      .querySelector('#logs-clear-btn')
      ?.addEventListener('click', () => {
        this.#clearLogs();
      });


    /*
     * Auto scroll toggle
     */
    this.#container
      .querySelector('#logs-autoscroll-btn')
      ?.addEventListener('click', () => {
        this.#toggleAutoScroll();
      });


    /*
     * Scroll to top
     */
    this.#container
      .querySelector('#logs-top-btn')
      ?.addEventListener('click', () => {
        this.#scrollToTop();
      });


    /*
     * Single log copy
     *
     * Event delegation keeps this working even after
     * #renderEntries() replaces the log HTML.
     */
    this.#container
      .querySelector('#logs-body')
      ?.addEventListener('click', event => {
        const button =
          event.target.closest('[data-copy-log]');

        if (!button) return;

        const index =
          Number(button.dataset.copyLog);

        if (!Number.isInteger(index)) return;

        this.#copySingleLog(index, button);
      });
  }

  /* =========================================================
     Update Filter Buttons
     ========================================================= */

  #updateFilterButtons() {
    if (!this.#container) return;

    this.#container
      .querySelectorAll('[data-filter]')
      .forEach(button => {
        const active =
          button.dataset.filter === this.#filter;

        button.classList.toggle('active', active);

        button.setAttribute(
          'aria-pressed',
          String(active)
        );
      });
  }

  /* =========================================================
     Search Clear Button
     ========================================================= */

  #updateSearchClearButton() {
    if (!this.#container) return;

    const wrapper =
      this.#container.querySelector(
        '.logs-search-wrapper'
      );

    if (!wrapper) return;

    let button =
      wrapper.querySelector(
        '#logs-search-clear'
      );

    if (this.#search && !button) {
      button =
        document.createElement('button');

      button.id = 'logs-search-clear';
      button.className =
        'logs-search-clear';

      button.type = 'button';
      button.title = 'Clear search';
      button.setAttribute(
        'aria-label',
        'Clear search'
      );

      button.innerHTML = `
        <span class="material-symbols-outlined">
          close
        </span>
      `;

      button.addEventListener('click', () => {
        this.#search = '';

        const input =
          this.#container?.querySelector(
            '#logs-search'
          );

        if (input) {
          input.value = '';
          input.focus();
        }

        this.#renderEntries();
        this.#updateSearchClearButton();
      });

      wrapper.appendChild(button);
    }

    if (!this.#search && button) {
      button.remove();
    }
  }

  /* =========================================================
     Get Filtered Entries
     ========================================================= */

  #getFilteredEntries() {
    let entries =
      consoleService.getEntries(
        this.#filter === 'all'
          ? null
          : this.#filter
      );

    if (!Array.isArray(entries)) {
      entries = [];
    }

    if (this.#search) {
      entries = entries.filter(entry => {
        const message =
          String(entry?.message || '')
            .toLowerCase();

        const time =
          String(entry?.time || '')
            .toLowerCase();

        const level =
          String(entry?.level || '')
            .toLowerCase();

        return (
          message.includes(this.#search) ||
          time.includes(this.#search) ||
          level.includes(this.#search)
        );
      });
    }

    /*
     * Keep maximum 500 visible entries.
     *
     * Reverse so newest log appears first.
     */
    return entries
      .slice(-500)
      .reverse();
  }

  /* =========================================================
     Render Entries
     ========================================================= */

  #renderEntries() {
    const body =
      this.#container?.querySelector(
        '#logs-body'
      );

    if (!body) return;

    const entries =
      this.#getFilteredEntries();


    /*
     * Stats
     */
    const countElement =
      this.#container.querySelector(
        '#logs-visible-count'
      );

    if (countElement) {
      countElement.textContent =
        entries.length.toLocaleString();
    }


    const filterElement =
      this.#container.querySelector(
        '#logs-active-filter'
      );

    if (filterElement) {
      filterElement.textContent =
        this.#capitalize(this.#filter);
    }


    const statusElement =
      this.#container.querySelector(
        '#logs-status'
      );

    if (statusElement) {
      statusElement.textContent = 'Live';
    }


    /*
     * Empty state
     */
    if (!entries.length) {
      body.innerHTML = `
        <div class="logs-empty">

          <div class="logs-empty-icon-wrapper">

            <span class="material-symbols-outlined">
              ${
                this.#search ||
                this.#filter !== 'all'
                  ? 'search_off'
                  : 'receipt_long'
              }
            </span>

          </div>

          <div class="logs-empty-title">
            ${
              this.#search ||
              this.#filter !== 'all'
                ? 'No logs found'
                : 'No logs yet'
            }
          </div>

          <p class="logs-empty-subtitle">
            ${this.#getEmptyMessage()}
          </p>

        </div>
      `;

      return;
    }


    /*
     * Render logs
     */
    body.innerHTML =
      entries
        .map((entry, index) =>
          this.#renderEntry(
            entry,
            index
          )
        )
        .join('');

    /*
     * Keep the newest entry in view when
     * auto scroll is enabled.
     */
    if (this.#autoScroll) {
      this.#scrollToBottom(true);
    }
  }

  /* =========================================================
     Render Single Entry
     ========================================================= */

  #renderEntry(entry, index) {
    const level =
      this.#normalizeLevel(
        entry?.level
      );

    const message =
      String(entry?.message || '');

    const time =
      String(entry?.time || '');

    return `
      <article
        class="console-entry logs-entry ${this.#esc(level)}"
        data-level="${this.#esc(level)}">

        <!-- Level indicator -->
        <div
          class="logs-entry-indicator"
          aria-hidden="true">
        </div>


        <!-- Main -->
        <div class="logs-entry-main">

          <!-- Meta -->
          <div class="logs-entry-meta">

            <span class="console-time">
              ${this.#esc(time)}
            </span>

            <span
              class="console-level ${this.#esc(level)}">

              <span
                class="material-symbols-outlined"
                aria-hidden="true">
                ${this.#levelIcon(level)}
              </span>

              ${this.#esc(level)}

            </span>

          </div>


          <!-- Message -->
          <div class="console-message logs-message">
            ${this.#formatMessage(message)}
          </div>

        </div>


        <!-- Copy -->
        <button
          class="logs-copy-btn"
          type="button"
          data-copy-log="${index}"
          title="Copy this log"
          aria-label="Copy this log">

          <span class="material-symbols-outlined">
            content_copy
          </span>

        </button>

      </article>
    `;
  }

  /* =========================================================
     Level Helpers
     ========================================================= */

  #normalizeLevel(level) {
    const value =
      String(level || 'info')
        .toLowerCase()
        .trim();

    const allowed = [
      'info',
      'success',
      'warning',
      'error'
    ];

    return allowed.includes(value)
      ? value
      : 'info';
  }

  #levelIcon(level) {
    switch (level) {
      case 'success':
        return 'check_circle';

      case 'warning':
        return 'warning';

      case 'error':
        return 'error';

      case 'info':
      default:
        return 'info';
    }
  }

  /* =========================================================
     Format Message
     ========================================================= */

  #formatMessage(message) {
    let escaped =
      this.#esc(message);

    /*
     * Convert URLs into clickable-looking text.
     *
     * We intentionally do not create <a> elements here
     * because application logs may contain untrusted URLs.
     */
    escaped =
      escaped.replace(
        /(https?:\/\/[^\s<]+)/g,
        '<span class="logs-url">$1</span>'
      );

    /*
     * Preserve line breaks.
     */
    escaped =
      escaped.replace(/\r?\n/g, '<br>');

    return escaped;
  }

  /* =========================================================
     Copy Single Log
     ========================================================= */

  async #copySingleLog(index, button) {
    const entries =
      this.#getFilteredEntries();

    const entry =
      entries[index];

    if (!entry || !button) return;

    const text =
      this.#formatLogForClipboard(entry);

    const success =
      await this.#writeClipboard(text);

    if (!success) {
      return;
    }

    const original =
      button.innerHTML;

    button.innerHTML = `
      <span class="material-symbols-outlined">
        check
      </span>
    `;

    button.classList.add('copied');

    const oldTimer =
      this.#copyTimers.get(button);

    if (oldTimer) {
      clearTimeout(oldTimer);
    }

    const timer =
      setTimeout(() => {
        if (!button.isConnected) {
          this.#copyTimers.delete(button);
          return;
        }

        button.innerHTML = original;
        button.classList.remove('copied');

        this.#copyTimers.delete(button);
      }, 1200);

    this.#copyTimers.set(
      button,
      timer
    );
  }

  /* =========================================================
     Copy All Filtered Logs
     ========================================================= */

  async #copyAllLogs() {
    const entries =
      this.#getFilteredEntries();

    if (!entries.length) {
      return;
    }

    const text =
      entries
        .map(entry =>
          this.#formatLogForClipboard(
            entry
          )
        )
        .join('\n');

    const success =
      await this.#writeClipboard(text);

    if (!success) {
      return;
    }

    const button =
      this.#container?.querySelector(
        '#logs-copy-all-btn'
      );

    if (!button) return;

    const original =
      button.innerHTML;

    button.innerHTML = `
      <span class="material-symbols-outlined logs-tool-icon">
        check
      </span>

      <span class="logs-tool-label">
        Copied
      </span>
    `;

    button.classList.add('copied');

    const timer =
      setTimeout(() => {
        if (!button.isConnected) {
          return;
        }

        button.innerHTML = original;
        button.classList.remove('copied');
      }, 1400);

    this.#copyTimers.set(
      button,
      timer
    );
  }

  /* =========================================================
     Clipboard Format
     ========================================================= */

  #formatLogForClipboard(entry) {
    const time =
      String(entry?.time || '');

    const level =
      String(
        entry?.level || 'info'
      ).toUpperCase();

    const message =
      String(
        entry?.message || ''
      );

    return `[${time}] [${level}] ${message}`;
  }

  /* =========================================================
     Clipboard
     ========================================================= */

  async #writeClipboard(text) {
    if (!text) {
      return false;
    }
    return ClipboardUtil.copyText(text);
  }

  /* =========================================================
     Export
     ========================================================= */

  #exportLogs() {
    try {
      if (
        typeof consoleService.exportLogs ===
        'function'
      ) {
        consoleService.exportLogs();
      }
    } catch (error) {
      console.error(
        'Failed to export logs:',
        error
      );
    }
  }

  /* =========================================================
     Clear Logs
     ========================================================= */

  #clearLogs() {
    try {
      if (
        typeof consoleService.clear !==
        'function'
      ) {
        return;
      }

      consoleService.clear();

      /*
       * Render immediately in case clear()
       * does not emit console:entry.
       */
      this.#renderEntries();

    } catch (error) {
      console.error(
        'Failed to clear logs:',
        error
      );
    }
  }

  /* =========================================================
     Auto Scroll Toggle
     ========================================================= */

  #toggleAutoScroll() {
    this.#autoScroll = !this.#autoScroll;

    this.#updateAutoScrollButton();

    if (this.#autoScroll) {
      this.#scrollToBottom();
    }
  }

  /* =========================================================
     Update Auto Scroll Button State
     ========================================================= */

  #updateAutoScrollButton() {
    const button =
      this.#container?.querySelector(
        '#logs-autoscroll-btn'
      );

    if (!button) return;

    button.classList.toggle(
      'active',
      this.#autoScroll
    );

    button.setAttribute(
      'aria-pressed',
      String(this.#autoScroll)
    );
  }

  /* =========================================================
     Scroll To Top
     ========================================================= */

  #scrollToTop() {
    const body =
      this.#container?.querySelector(
        '#logs-body'
      );

    if (!body) return;

    body.scrollTo({
      top:     0,
      behavior: 'smooth',
    });
  }

  /* =========================================================
     Scroll To Bottom
     ========================================================= */

  #scrollToBottom(instant = false) {
    const body =
      this.#container?.querySelector(
        '#logs-body'
      );

    if (!body || !this.#autoScroll) {
      return;
    }

    body.scrollTo({
      top:     body.scrollHeight,
      behavior: instant ? 'auto' : 'smooth',
    });
  }

  /* =========================================================
     Empty State Message
     ========================================================= */

  #getEmptyMessage() {
    if (this.#search) {
      return `
        No logs matching
        "<strong>${this.#esc(this.#search)}</strong>".
        Try a different search term.
      `;
    }

    if (this.#filter !== 'all') {
      return `
        No ${this.#esc(this.#filter)}
        logs are currently available.
      `;
    }

    return `
      Application logs will appear here
      when the application generates activity.
    `;
  }

  /* =========================================================
     Capitalize
     ========================================================= */

  #capitalize(value) {
    const text =
      String(value || '');

    if (!text) {
      return '';
    }

    return (
      text.charAt(0).toUpperCase() +
      text.slice(1)
    );
  }

  /* =========================================================
     HTML Escape
     ========================================================= */

  #esc(value) {
    return String(
      value ?? ''
    )
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
}