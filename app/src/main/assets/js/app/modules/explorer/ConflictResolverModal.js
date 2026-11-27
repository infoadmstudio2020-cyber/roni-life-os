/**
 * ConflictResolverModal — dialog for resolving duplicate and colliding files.
 * Provides Replace, Skip, Keep Both, Cancel, and Apply to All options.
 */

export class ConflictResolverModal {
  /**
   * Resolve a single file collision.
   * @param {object} params
   * @param {string} params.targetPath - Target relative path in project
   * @param {string} params.incomingContent - Incoming file content
   * @param {string} params.existingContent - Existing file content in project
   * @param {number} params.conflictIndex - Current conflict count
   * @param {number} params.totalConflicts - Total files in conflict queue
   * @returns {Promise<{action: 'replace'|'skip'|'keep-both'|'cancel', applyToAll: boolean}>}
   */
  static async resolve({ targetPath, incomingContent, existingContent, conflictIndex = 1, totalConflicts = 1 }) {
    const isIdentical = String(incomingContent) === String(existingContent);
    const fileName = targetPath.split('/').pop();
    const dir = targetPath.includes('/') ? targetPath.slice(0, targetPath.lastIndexOf('/')) : 'root';

    const existingSize = new Blob([existingContent || '']).size;
    const incomingSize = new Blob([incomingContent || '']).size;

    return new Promise(resolve => {
      const backdrop = document.createElement('div');
      backdrop.className = 'modal-backdrop';
      backdrop.style.zIndex = '10050';

      const previewExisting = String(existingContent || '').slice(0, 150);
      const previewIncoming = String(incomingContent || '').slice(0, 150);

      backdrop.innerHTML = `
        <div class="modal md" role="dialog" aria-modal="true" style="max-width:540px;width:95%">
          <div class="modal-header">
            <span class="material-symbols-outlined modal-icon" style="color:var(--warning)">warning</span>
            <div style="flex:1;min-width:0">
              <h3 class="modal-title" style="margin:0;font-size:var(--text-base)">File Collision Detected</h3>
              <div style="font-size:var(--text-xs);color:var(--text-muted)">
                Conflict ${conflictIndex} of ${totalConflicts} in <code>${dir}</code>
              </div>
            </div>
            <button class="modal-close conflict-cancel-btn" title="Cancel operation">
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>

          <div class="modal-body" style="display:flex;flex-direction:column;gap:12px;padding:16px">
            <div style="display:flex;align-items:center;gap:8px;padding:8px 12px;background:var(--bg-elevated);border-radius:var(--radius);border:1px solid var(--border)">
              <span class="material-symbols-outlined" style="color:var(--accent)">description</span>
              <div style="flex:1;min-width:0">
                <div style="font-weight:600;font-family:var(--font-mono);font-size:var(--text-sm);overflow:hidden;text-overflow:ellipsis">${fileName}</div>
                <div style="font-size:var(--text-xs);color:var(--text-secondary)">${targetPath}</div>
              </div>
              ${isIdentical
                ? `<span class="badge" style="background:var(--accent-glow);color:var(--accent);border:1px solid var(--accent);font-weight:600">Identical Content</span>`
                : `<span class="badge" style="background:rgba(234,179,8,0.15);color:var(--warning);border:1px solid var(--warning);font-weight:600">Content Modified</span>`
              }
            </div>

            <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;font-size:var(--text-xs)">
              <div style="padding:10px;background:var(--bg-surface);border:1px solid var(--border);border-radius:var(--radius)">
                <div style="font-weight:600;color:var(--text-muted);margin-bottom:4px">Existing in Project</div>
                <div style="color:var(--text-secondary);margin-bottom:6px">Size: ${existingSize} bytes</div>
                <pre style="font-family:var(--font-mono);font-size:11px;color:var(--text-muted);background:var(--bg-primary);padding:6px;border-radius:4px;overflow:hidden;max-height:80px;margin:0;white-space:pre-wrap">${previewExisting || '(empty file)'}</pre>
              </div>
              <div style="padding:10px;background:var(--bg-surface);border:1px solid var(--border);border-radius:var(--radius)">
                <div style="font-weight:600;color:var(--accent);margin-bottom:4px">Incoming File</div>
                <div style="color:var(--text-secondary);margin-bottom:6px">Size: ${incomingSize} bytes</div>
                <pre style="font-family:var(--font-mono);font-size:11px;color:var(--text-primary);background:var(--bg-primary);padding:6px;border-radius:4px;overflow:hidden;max-height:80px;margin:0;white-space:pre-wrap">${previewIncoming || '(empty file)'}</pre>
              </div>
            </div>

            <label style="display:flex;align-items:center;gap:8px;font-size:var(--text-xs);color:var(--text-secondary);cursor:pointer;margin-top:4px">
              <input type="checkbox" id="conflict-apply-all" style="accent-color:var(--accent)">
              <span>Apply this choice to all remaining conflicts</span>
            </label>
          </div>

          <div class="modal-footer" style="display:flex;justify-content:flex-end;gap:8px;padding:12px 16px;background:var(--bg-elevated);border-top:1px solid var(--border)">
            <button type="button" class="btn btn-ghost btn-sm conflict-cancel-btn">Cancel</button>
            <button type="button" class="btn btn-secondary btn-sm" id="conflict-skip-btn" title="Keep existing file and do not import this one">
              <span class="material-symbols-outlined" style="font-size:16px">skip_next</span>Skip
            </button>
            <button type="button" class="btn btn-secondary btn-sm" id="conflict-keep-both-btn" title="Import with an auto-renamed filename">
              <span class="material-symbols-outlined" style="font-size:16px">file_copy</span>Keep Both
            </button>
            <button type="button" class="btn btn-primary btn-sm" id="conflict-replace-btn" title="Overwrite the existing file with the incoming content">
              <span class="material-symbols-outlined" style="font-size:16px">change_circle</span>Replace
            </button>
          </div>
        </div>`;

      const cleanup = () => {
        backdrop.remove();
      };

      const getApplyToAll = () => {
        const chk = backdrop.querySelector('#conflict-apply-all');
        return chk ? chk.checked : false;
      };

      backdrop.querySelector('#conflict-replace-btn')?.addEventListener('click', () => {
        const applyToAll = getApplyToAll();
        cleanup();
        resolve({ action: 'replace', applyToAll });
      });

      backdrop.querySelector('#conflict-skip-btn')?.addEventListener('click', () => {
        const applyToAll = getApplyToAll();
        cleanup();
        resolve({ action: 'skip', applyToAll });
      });

      backdrop.querySelector('#conflict-keep-both-btn')?.addEventListener('click', () => {
        const applyToAll = getApplyToAll();
        cleanup();
        resolve({ action: 'keep-both', applyToAll });
      });

      backdrop.querySelectorAll('.conflict-cancel-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          cleanup();
          resolve({ action: 'cancel', applyToAll: false });
        });
      });

      document.body.appendChild(backdrop);
    });
  }
}
