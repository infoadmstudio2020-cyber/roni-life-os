/**
 * PropertyPanel UI module — shows metadata for the selected file/node.
 */
import { eventBus } from '../core/EventBus.js';
import { projectService } from '../modules/storage/ProjectService.js';

export class PropertyPanel {
  #el      = null;
  #current = null;

  init() {
    this.#el = document.getElementById('property-panel');
    this.#render(null);
    eventBus.on('explorer:selected', node => { this.#current = node; this.#render(node); });
    eventBus.on('project:closed',    ()   => this.#render(null));
  }

  #render(node) {
    if (!node) {
      this.#el.innerHTML = `
        <div style="padding:var(--sp-4)">
          <div class="section-title" style="margin-bottom:var(--sp-3)">Properties</div>
          <div style="color:var(--text-muted);font-size:var(--text-xs);padding:var(--sp-2)">
            Select a file or folder to view properties.
          </div>
        </div>`;
      return;
    }

    const size    = node.content ? new Blob([node.content]).size : 0;
    const sizeStr = size > 1024 ? `${(size / 1024).toFixed(1)} KB` : `${size} B`;
    const lines   = node.content ? node.content.split('\n').length : 0;
    const ext     = node.name.includes('.') ? node.name.split('.').pop() : '—';

    this.#el.innerHTML = `
      <div style="padding:var(--sp-4);display:flex;flex-direction:column;gap:var(--sp-3)">
        <div class="section-title">Properties</div>

        <div style="display:flex;align-items:center;gap:var(--sp-3);padding:var(--sp-3);
                    background:var(--bg-elevated);border:1px solid var(--border);border-radius:var(--radius)">
          <span class="material-symbols-outlined" style="font-size:28px;color:var(--accent);
                font-variation-settings:'FILL' 1,'wght' 400">
            ${node.type === 'folder' ? 'folder' : 'description'}
          </span>
          <div style="flex:1;min-width:0">
            <div style="font-size:var(--text-sm);font-weight:600;color:var(--text-primary);
                        overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${node.name}</div>
            <div style="font-size:var(--text-xs);color:var(--text-muted)">${node.type}</div>
          </div>
        </div>

        ${this.#propRow('Name',       node.name)}
        ${this.#propRow('Type',       node.type === 'folder' ? 'Folder' : `${ext.toUpperCase()} File`)}
        ${node.type === 'file' ? this.#propRow('Size', sizeStr) : ''}
        ${node.type === 'file' ? this.#propRow('Lines', String(lines)) : ''}
        ${this.#propRow('Path',       node.path || '—')}
        ${this.#propRow('Modified',   node.modifiedAt
            ? new Date(node.modifiedAt).toLocaleString() : '—')}

        ${node.type === 'file' && node.content ? `
          <div style="margin-top:var(--sp-2)">
            <div class="form-label" style="margin-bottom:var(--sp-2)">Preview</div>
            <pre style="background:var(--bg-elevated);border:1px solid var(--border);
                        border-radius:var(--radius);padding:var(--sp-3);font-size:10px;
                        font-family:var(--font-mono);color:var(--text-secondary);
                        max-height:160px;overflow:auto;white-space:pre-wrap;word-break:break-all">${
              this.#escape(node.content.slice(0, 600))
            }${node.content.length > 600 ? '\n…' : ''}</pre>
          </div>` : ''}
      </div>`;
  }

  #propRow(label, value) {
    return `
      <div style="display:flex;justify-content:space-between;gap:var(--sp-3);
                  padding:var(--sp-2) 0;border-bottom:1px solid var(--border)">
        <span style="font-size:var(--text-xs);color:var(--text-muted)">${label}</span>
        <span style="font-size:var(--text-xs);color:var(--text-secondary);
                     font-family:var(--font-mono);text-align:right;
                     word-break:break-all;max-width:140px">${value}</span>
      </div>`;
  }

  #escape(str) {
    return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }
}
