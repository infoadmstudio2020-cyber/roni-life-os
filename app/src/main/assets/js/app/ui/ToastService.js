/**
 * ToastService — show transient notifications in #toast-layer.
 */
export class ToastService {
  #container = null;
  #recentMap = new Map();

  init() {
    this.#container = document.getElementById('toast-layer');
  }

  /**
   * Show a toast notification.
   * @param {object} opts
   * @param {'info'|'success'|'warning'|'error'} opts.type
   * @param {string} opts.title
   * @param {string} [opts.message]
   * @param {number} [opts.duration=3500]
   */
  show({ type = 'info', title, message = '', duration = 3500 }) {
    if (!this.#container) return;

    // Prevent duplicate toast spam within 1 second
    const key = `${type}:${title}:${message}`;
    const now = Date.now();
    if (this.#recentMap.has(key) && (now - this.#recentMap.get(key) < 1000)) {
      return;
    }
    this.#recentMap.set(key, now);

    const icons = { info: 'info', success: 'check_circle', warning: 'warning', error: 'error' };
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `
      <span class="material-symbols-outlined toast-icon">${icons[type] || 'info'}</span>
      <div class="toast-body">
        <div class="toast-title">${title}</div>
        ${message ? `<div class="toast-message">${message}</div>` : ''}
      </div>
      <button class="toast-close" aria-label="Dismiss">
        <span class="material-symbols-outlined">close</span>
      </button>
    `;

    let dismissed = false;
    const dismiss = () => {
      if (dismissed) return;
      dismissed = true;
      toast.classList.add('removing', 'toast-leaving');
      setTimeout(() => toast.remove(), 220);
    };

    toast.querySelector('.toast-close').addEventListener('click', (e) => {
      e.stopPropagation();
      dismiss();
    });

    this.#container.appendChild(toast);

    if (duration > 0) {
      setTimeout(dismiss, duration);
    }
  }

  info(title, message)    { this.show({ type: 'info',    title, message }); }
  success(title, message) { this.show({ type: 'success', title, message }); }
  warn(title, message)    { this.show({ type: 'warning', title, message }); }
  error(title, message)   { this.show({ type: 'error',   title, message, duration: 5000 }); }
}

export const toast = new ToastService();
