/**
 * DownloadsPage — list of past ZIP exports with download/delete actions.
 */
import { storage } from '../modules/storage/StorageService.js';
import { toast } from '../ui/ToastService.js';
import { WebViewBridge } from '../core/WebViewBridge.js';

export class DownloadsPage {
  async init(container) {
    await this.#render(container);
  }

  async #render(container) {
    const exports = await storage.getAll('exports');
    exports.sort((a, b) => b.exportedAt - a.exportedAt);

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title"><span class="material-symbols-outlined">download</span>Downloads & Releases</h1>
          <p class="page-subtitle">Exported project packages and system releases</p>
        </div>

        <!-- Latest System Release Package -->
        <div class="card" style="margin-bottom:var(--sp-6);border:1px solid var(--accent);background:linear-gradient(180deg, var(--bg-surface) 0%, var(--bg-card) 100%)">
          <div class="card-header" style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-2)">
            <div style="display:flex;align-items:center;gap:var(--sp-2)">
              <span class="material-symbols-outlined" style="color:var(--accent);font-size:24px">verified</span>
              <div>
                <span style="font-weight:600;font-size:var(--text-base);color:var(--text-primary)">RONi Android Library Generator</span>
                <span class="badge badge-accent" style="margin-left:var(--sp-2)">v0.1.3.6</span>
              </div>
            </div>
            <a href="./RONiAndroidLibraryGenerator-v0.1.3.6.zip" download="RONiAndroidLibraryGenerator-v0.1.3.6.zip" class="btn btn-primary btn-sm" id="dl-system-zip-btn" style="text-decoration:none">
              <span class="material-symbols-outlined">download</span>Download Release Package ZIP
            </a>
          </div>
          <div class="card-body" style="padding-top:var(--sp-3)">
            <p style="font-size:var(--text-sm);color:var(--text-secondary);margin-bottom:var(--sp-3);line-height:1.5">
              Official release distribution including dynamic library module resolution, companion :app integration, fail-safe JitPack automation, AST brace-aware Gradle patcher, Android WebView compatibility, and comprehensive test suites.
            </p>
            <div style="display:flex;gap:var(--sp-4);flex-wrap:wrap;font-size:var(--text-xs);color:var(--text-muted)">
              <div><strong>Package:</strong> RONiAndroidLibraryGenerator-v0.1.3.6.zip</div>
              <div><strong>Status:</strong> 100% Tested & Verified</div>
              <div><strong>Modules:</strong> 75+ Core & UI Modules</div>
              <div><strong>Compatibility:</strong> Gradle 8.7 · AGP 8.3.2 · JDK 17</div>
            </div>
          </div>
        </div>

        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-4)">
          <h2 style="font-size:var(--text-md);font-weight:600;margin:0">Previously Exported Projects</h2>
          <button class="btn btn-secondary btn-sm" id="dl-clear-btn" ${!exports.length?'disabled':''}>
            <span class="material-symbols-outlined">delete_sweep</span>Clear All
          </button>
        </div>

        ${exports.length === 0
          ? `<div class="card" style="padding:var(--sp-8);text-align:center">
              <span class="material-symbols-outlined" style="font-size:48px;opacity:.3;font-variation-settings:'FILL' 0,'wght' 200">download</span>
              <div style="margin-top:var(--sp-3);color:var(--text-muted)">No exports yet. Use Export ZIP to generate a download.</div>
             </div>`
          : `<div class="card">
              <div class="card-body" style="padding:0">
                ${exports.map(e => `
                  <div class="settings-row">
                    <div style="width:36px;height:36px;background:var(--accent-subtle);border-radius:var(--radius);
                                display:flex;align-items:center;justify-content:center;flex-shrink:0">
                      <span class="material-symbols-outlined" style="font-size:18px;color:var(--accent);
                            font-variation-settings:'FILL' 1,'wght' 400">folder_zip</span>
                    </div>
                    <div class="settings-row-label" style="margin-left:var(--sp-3)">
                      <div class="settings-row-title">${e.filename}</div>
                      <div class="settings-row-desc">${e.name} · ${e.files} files · ${e.sizeKb} KB · ${this.#relTime(e.exportedAt)}</div>
                    </div>
                    <button class="btn btn-ghost btn-sm btn-icon" data-delete="${e.id}" title="Delete record">
                      <span class="material-symbols-outlined">delete</span>
                    </button>
                  </div>`).join('')}
              </div>
             </div>`}
      </div>`;

    container.querySelector('#dl-system-zip-btn')?.addEventListener('click', async (e) => {
      e.preventDefault();
      const filename = 'RONiAndroidLibraryGenerator-v0.1.3.6.zip';
      try {
        toast.info('Downloading…', 'Fetching release package ZIP');
        const response = await fetch(`./${filename}`).catch(() => fetch(`/${filename}`));
        if (response && response.ok) {
          const blob = await response.blob();
          await WebViewBridge.downloadFile(filename, blob, 'application/zip');
          toast.success('Download Complete', filename);
          return;
        }
        window.location.href = `./${filename}`;
      } catch (err) {
        console.warn('[DownloadsPage] Fetch failed, triggering direct fallback:', err);
        window.location.href = `./${filename}`;
      }
    });

    container.querySelector('#dl-clear-btn')?.addEventListener('click', async () => {
      await storage.clear('exports');
      toast.success('Downloads cleared');
      await this.#render(container);
    });

    container.querySelectorAll('[data-delete]').forEach(btn => {
      btn.addEventListener('click', async () => {
        await storage.delete('exports', btn.dataset.delete);
        toast.info('Removed', 'Export record removed');
        await this.#render(container);
      });
    });
  }

  #relTime(ts) {
    if (!ts) return '';
    const diff = Date.now() - ts;
    if (diff < 60000)    return 'just now';
    if (diff < 3600000)  return `${Math.floor(diff/60000)}m ago`;
    if (diff < 86400000) return `${Math.floor(diff/3600000)}h ago`;
    return new Date(ts).toLocaleDateString();
  }
}
