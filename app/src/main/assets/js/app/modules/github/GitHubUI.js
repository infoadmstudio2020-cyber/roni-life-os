/**
 * GitHubUI — shared UI helpers for the GitHub Publisher section.
 */
export class GitHubUI {

  /** Render a status badge. */
  static badge(status) {
    const map = {
      success:      { cls: 'badge-success',  icon: 'check_circle',   label: 'Success' },
      failed:       { cls: 'badge-error',    icon: 'error',          label: 'Failed' },
      pending:      { cls: 'badge-warn',     icon: 'hourglass_empty',label: 'Pending' },
      running:      { cls: 'badge-info',     icon: 'sync',           label: 'Running' },
      authenticated:{ cls: 'badge-success',  icon: 'verified_user',  label: 'Authenticated' },
      unauthenticated:{ cls: 'badge-error',  icon: 'no_accounts',    label: 'Not authenticated' },
      public:       { cls: 'badge-success',  icon: 'public',         label: 'Public' },
      private:      { cls: 'badge-warn',     icon: 'lock',           label: 'Private' },
    };
    const d = map[status] || { cls: 'badge', icon: 'info', label: status };
    return `<span class="gh-badge ${d.cls}">
      <span class="material-symbols-outlined">${d.icon}</span>${d.label}
    </span>`;
  }

  /** Render a section card. */
  static card(titleIcon, title, body, extra = '') {
    return `
      <div class="card gh-card${extra ? ' ' + extra : ''}">
        <div class="card-header">
          <span class="material-symbols-outlined">${titleIcon}</span>${title}
        </div>
        <div class="card-body">${body}</div>
      </div>`;
  }

  /** Render a form field row. */
  static field(id, label, type, value = '', placeholder = '', hint = '', required = false) {
    return `
      <div class="form-group">
        <label class="form-label${required ? ' required' : ''}">${label}</label>
        <input class="input" id="${id}" type="${type}" value="${value}" placeholder="${placeholder}" autocomplete="off" />
        ${hint ? `<div class="form-hint">${hint}</div>` : ''}
      </div>`;
  }

  /** Render a textarea field. */
  static textarea(id, label, value = '', placeholder = '', rows = 3) {
    return `
      <div class="form-group">
        <label class="form-label">${label}</label>
        <textarea class="input textarea" id="${id}" rows="${rows}" placeholder="${placeholder}">${value}</textarea>
      </div>`;
  }

  /** Render a select field. */
  static select(id, label, options, selected = '') {
    const opts = options.map(o => {
      const val  = typeof o === 'string' ? o : o.value;
      const text = typeof o === 'string' ? o : o.label;
      return `<option value="${val}"${val === selected ? ' selected' : ''}>${text}</option>`;
    }).join('');
    return `
      <div class="form-group">
        <label class="form-label">${label}</label>
        <div class="select-wrapper"><select class="input select" id="${id}">${opts}</select></div>
      </div>`;
  }

  /** Render a toggle/checkbox row. */
  static toggle(id, label, checked = false, hint = '') {
    return `
      <div class="settings-row">
        <div class="settings-row-label">
          <div class="settings-row-title">${label}</div>
          ${hint ? `<div class="settings-row-hint">${hint}</div>` : ''}
        </div>
        <label class="gh-toggle">
          <input type="checkbox" id="${id}"${checked ? ' checked' : ''} />
          <span class="gh-toggle-track"></span>
        </label>
      </div>`;
  }

  /** Render a progress step row. */
  static progressStep(icon, label, status = 'pending') {
    const statusMap = {
      pending: { cls: '', icon: 'radio_button_unchecked' },
      running: { cls: 'running', icon: 'sync' },
      done:    { cls: 'done',    icon: 'check_circle' },
      error:   { cls: 'error',   icon: 'cancel' },
    };
    const s = statusMap[status] || statusMap.pending;
    return `
      <div class="gh-progress-step ${s.cls}">
        <span class="material-symbols-outlined gh-progress-step-icon">${s.icon}</span>
        <span class="material-symbols-outlined gh-progress-step-task-icon">${icon}</span>
        <span class="gh-progress-step-label">${label}</span>
      </div>`;
  }

  /** Render empty state. */
  static empty(icon, title, subtitle = '') {
    return `
      <div class="gh-empty">
        <span class="material-symbols-outlined gh-empty-icon">${icon}</span>
        <div class="gh-empty-title">${title}</div>
        ${subtitle ? `<div class="gh-empty-subtitle">${subtitle}</div>` : ''}
      </div>`;
  }

  /** Render an alert box. */
  static alert(type, message) {
    const icons = { error: 'error', warning: 'warning', success: 'check_circle', info: 'info' };
    return `
      <div class="gh-alert gh-alert-${type}">
        <span class="material-symbols-outlined">${icons[type] || 'info'}</span>
        <span>${message}</span>
      </div>`;
  }
}
