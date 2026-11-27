/**
 * ModalService — manages modal dialogs.
 * Mobile: modals render as bottom sheets (CSS in responsive/mobile.css).
 * Desktop: centered dialogs.
 */
export class ModalService {
  #container = null;
  #stack     = [];

  init() {
    this.#container = document.getElementById('modal-layer');
    document.addEventListener('keydown', e => {
      if (e.key === 'Escape') this.closeTop();
      if (e.key === 'Tab') this.#trapFocus(e);
    });
  }

  /** Keep keyboard focus inside the top-most modal (accessibility). */
  #trapFocus(e) {
    const top = this.#stack.at(-1);
    if (!top) return;
    const focusable = top.querySelectorAll(
      'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [href], [tabindex]:not([tabindex="-1"])');
    if (!focusable.length) return;
    const first = focusable[0];
    const last  = focusable[focusable.length - 1];
    if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
    else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
  }

  /**
   * Open a modal dialog with flexible options.
   * @param {object} opts
   * @param {string} [opts.icon]
   * @param {string} opts.title
   * @param {string} [opts.body]   — HTML string for modal body
   * @param {string} [opts.content] — Alias for opts.body
   * @param {Array}  [opts.footer] — [{label, type, action, close, onClick}]
   * @param {Array}  [opts.buttons] — [{label, variant, type, action, onClick, close}]
   * @param {'sm'|''|'lg'|'xl'} [opts.size]
   * @param {Function} [opts.onClose]
   * @returns {HTMLElement} modal backdrop element
   */
  open({ icon = 'info', title = '', body = '', content = '', footer = [], buttons = [], size = '', onClose = null }) {
    if (!this.#container) {
      this.#container = document.getElementById('modal-layer');
    }
    const htmlBody = content || body || '';
    const backdrop = document.createElement('div');
    backdrop.className = 'modal-backdrop';
    backdrop.dataset.opener = ''; // remember focus target below
    backdrop.__onClose = onClose;

    // Combine footer and buttons cleanly
    const combinedButtons = [];
    if (Array.isArray(footer)) {
      combinedButtons.push(...footer);
    }
    if (Array.isArray(buttons)) {
      for (const btn of buttons) {
        combinedButtons.push({
          id: btn.id || '',
          label: btn.label || 'OK',
          type: btn.variant || btn.type || 'primary',
          action: btn.action || '',
          disabled: !!btn.disabled,
          close: btn.close,
          onClick: btn.onClick || (typeof btn.action === 'function' ? btn.action : null),
        });
      }
    }

    const footerBtns = combinedButtons.map((btn, idx) =>
      `<button type="button" ${btn.id ? `id="${btn.id}"` : ''} class="btn btn-${btn.type || 'secondary'} ${btn.close ? 'modal-close-btn' : ''}" ${btn.disabled ? 'disabled' : ''} data-idx="${idx}" data-action="${typeof btn.action === 'string' ? btn.action : ''}">${btn.label}</button>`
    ).join('');

    backdrop.innerHTML = `
      <div class="modal ${size}" role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <div class="modal-header">
          <span class="material-symbols-outlined modal-icon">${icon}</span>
          <h3 class="modal-title" id="modal-title">${title}</h3>
          <button type="button" class="modal-close modal-close-btn" aria-label="Close">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>
        <div class="modal-body">${htmlBody}</div>
        ${combinedButtons.length ? `<div class="modal-footer">${footerBtns}</div>` : ''}
      </div>
    `;

    // Bind custom button click handlers
    backdrop.querySelectorAll('.modal-footer button').forEach(btnEl => {
      const idx = parseInt(btnEl.dataset.idx, 10);
      const btnDef = combinedButtons[idx];
      if (btnDef?.onClick) {
        btnEl.addEventListener('click', async e => {
          try {
            await btnDef.onClick(e, backdrop);
          } catch (err) {
            console.error('[ModalService] button onClick error:', err);
          }
        });
      }
    });

    // Close on backdrop click
    backdrop.addEventListener('click', e => {
      if (e.target === backdrop) this.close(backdrop);
    });

    // Close buttons
    backdrop.querySelectorAll('.modal-close-btn').forEach(btn => {
      btn.addEventListener('click', () => this.close(backdrop));
    });

    // Remember previously focused element to restore on close (a11y)
    backdrop.__restoreFocus = document.activeElement;

    if (this.#container) {
      this.#container.appendChild(backdrop);
      this.#stack.push(backdrop);

      // Show the layer (was permanently aria-hidden="true" — now managed)
      this.#container.setAttribute('aria-hidden', 'false');

      // Lock background scroll while a modal is open
      if (this.#stack.length === 1) document.body.classList.add('modal-open');

      // Focus trap
      const focusable = backdrop.querySelectorAll('button, input, select, textarea, [tabindex]');
      if (focusable.length) focusable[0].focus();
    }

    backdrop.close = () => this.close(backdrop);
    return backdrop;
  }

  /**
   * Display modal dialog. Compatible alias for .open(opts)
   * Supports: modal.show({ title, content, buttons, ... })
   */
  show(opts = {}) {
    return this.open(opts);
  }

  /**
   * Open a confirmation dialog.
   * @returns {Promise<boolean>}
   */
  confirm({ icon = 'help', title, message, confirmLabel = 'Confirm', confirmText, danger = false, onConfirm = null }) {
    const label = confirmText || confirmLabel;
    return new Promise(resolve => {
      let settled = false;
      const safeResolve = async val => {
        if (!settled) {
          settled = true;
          if (val && typeof onConfirm === 'function') {
            try {
              await onConfirm();
            } catch (err) {
              console.error('[ModalService] onConfirm error:', err);
            }
          }
          resolve(val);
        }
      };

      const modal = this.open({
        icon,
        title,
        body: `<p>${message}</p>`,
        footer: [
          { label: 'Cancel', type: 'secondary', close: true },
          { label: label, type: danger ? 'danger' : 'primary', action: 'confirm' },
        ],
      });

      modal.querySelector('[data-action="confirm"]')?.addEventListener('click', () => {
        this.close(modal);
        safeResolve(true);
      });

      // Cancel resolves false
      const cancelBtns = modal.querySelectorAll('.modal-close-btn');
      cancelBtns.forEach(btn => {
        btn.addEventListener('click', () => safeResolve(false));
      });

      // If closed through Escape or backdrop click
      const observer = new MutationObserver(() => {
        if (!document.body.contains(modal)) {
          observer.disconnect();
          safeResolve(false);
        }
      });
      observer.observe(document.body, { childList: true, subtree: true });
    });
  }
  /**
   * Open an alert dialog.
   * @returns {Promise<void>}
   */
  alert({ icon = 'info', title = 'Alert', message = '', okLabel = 'OK' }) {
    return new Promise(resolve => {
      const modal = this.open({
        icon,
        title,
        body: `<p>${message}</p>`,
        footer: [
          { label: okLabel, type: 'primary', action: 'ok', close: true }
        ],
        onClose: () => resolve()
      });

      modal.querySelector('[data-action="ok"]')?.addEventListener('click', () => {
        this.close(modal);
        resolve();
      });
    });
  }


  /**
   * Open a prompt dialog.
   * @returns {Promise<string|null>}
   */
  prompt({ icon = 'edit', title, placeholder = '', defaultValue = '', label = 'Value' }) {
    return new Promise(resolve => {
      const modal = this.open({
        icon,
        title,
        body: `
          <div class="form-group">
            <label class="form-label">${label}</label>
            <input class="input" id="modal-prompt-input" type="text" 
                   placeholder="${placeholder}" value="${defaultValue}" autocomplete="off" />
          </div>`,
        footer: [
          { label: 'Cancel', type: 'secondary', close: true },
          { label: 'OK', type: 'primary', action: 'ok' },
        ],
      });

      const input = modal.querySelector('#modal-prompt-input');
      input?.focus();
      input?.select();

      const submit = () => {
        const val = input?.value?.trim() ?? null;
        this.close(modal);
        resolve(val || null);
      };

      input?.addEventListener('keydown', e => { if (e.key === 'Enter') submit(); });
      modal.querySelector('[data-action="ok"]')?.addEventListener('click', submit);
    });
  }

  close(modal) {
    if (!modal) {
      this.closeTop();
      return;
    }
    try {
      modal.__onClose?.();
    } catch (err) {
      console.error('[ModalService] onClose error:', err);
    }
    modal.remove();
    this.#stack = this.#stack.filter(m => m !== modal);

    // Restore focus & scroll state when the last modal closes
    if (!this.#stack.length) {
      this.#container?.setAttribute('aria-hidden', 'true');
      document.body.classList.remove('modal-open');
    }
    modal.__restoreFocus?.focus?.();
  }

  closeTop() {
    const top = this.#stack.at(-1);
    if (top) this.close(top);
  }

  closeAll() {
    this.#stack.forEach(m => m.remove());
    this.#stack = [];
  }
}

export const modal = new ModalService();
