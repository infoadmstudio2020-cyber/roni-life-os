/**
 * SettingsPage — application settings with toggle, select, and number inputs.
 */
import { storage } from '../modules/storage/StorageService.js';
import { toast } from '../ui/ToastService.js';
import { modal } from '../ui/ModalService.js';
import { projectService } from '../modules/storage/ProjectService.js';
import { i18n } from '../core/I18n.js';
import { eventBus } from '../core/EventBus.js';
import { WebViewBridge } from '../core/WebViewBridge.js';
import { ClipboardUtil } from '../core/ClipboardUtil.js';

const DEFAULTS = {
  theme:            'dark',
  fontSize:         14,
  tabSize:          4,
  autoSaveInterval: 30,
  lineWrap:         false,
  autoLoadProjectFiles: true,
  defaultMinSdk:    '21',
  defaultTargetSdk: '34',
  defaultCompileSdk:'34',
  defaultLicense:   'Apache-2.0',
  defaultAuthor:    '',
};

export class SettingsPage {
  #settings = { ...DEFAULTS };

  async init(container) {
    const saved = await storage.getAllSettings();
    this.#settings = { ...DEFAULTS, ...saved };
    // Sanitize theme to the supported modes
    if (!['light', 'dark', 'system'].includes(this.#settings.theme)) {
      this.#settings.theme = 'dark';
    }
    this.#render(container);
  }

  #render(container) {
    const s = this.#settings;
    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title"><span class="material-symbols-outlined">settings</span>Settings</h1>
          <p class="page-subtitle">Configure editor, defaults, and storage preferences</p>
        </div>

        <!-- Appearance -->
        <div class="card" style="margin-bottom:var(--sp-4)" id="sect-appearance">
          <div class="card-header"><span class="material-symbols-outlined">palette</span>Appearance</div>
          <div class="card-body" style="padding:0">
            ${this.#row('Theme', 'Current color scheme',
              `<div class="select-wrapper" style="width:140px">
                <select class="input select" data-key="theme" id="settings-theme-select">
                  <option value="light"  ${s.theme==='light' ?'selected':''}>Light</option>
                  <option value="dark"   ${s.theme==='dark'  ?'selected':''}>Dark</option>
                  <option value="system" ${s.theme==='system'?'selected':''}>System</option>
                </select>
              </div>`)}
            ${this.#row('Language', 'Interface language',
              `<div class="select-wrapper" style="width:140px">
                <select class="input select" id="settings-lang-select" aria-label="Language">
                  <option value="en" ${i18n.lang==='en'?'selected':''}>English</option>
                  <option value="bn" ${i18n.lang==='bn'?'selected':''}>বাংলা</option>
                </select>
              </div>`)}
          </div>
        </div>

        <!-- Editor -->
        <div class="card" style="margin-bottom:var(--sp-4)" id="sect-editor">
          <div class="card-header"><span class="material-symbols-outlined">code</span>Editor</div>
          <div class="card-body" style="padding:0">
            ${this.#row('Font Size', 'Editor font size in pixels',
              `<input class="input" type="number" data-key="fontSize" value="${s.fontSize}" min="10" max="24" style="width:80px" />`)}
            ${this.#row('Tab Size', 'Spaces per tab indent',
              `<input class="input" type="number" data-key="tabSize" value="${s.tabSize}" min="2" max="8" style="width:80px" />`)}
            ${this.#row('Auto-save Interval', 'Seconds between auto-saves (0 = off)',
              `<input class="input" type="number" data-key="autoSaveInterval" value="${s.autoSaveInterval}" min="0" max="300" style="width:80px" />`)}
            ${this.#row('Line Wrap', 'Wrap long lines in editor',
              `<label class="toggle"><input type="checkbox" data-key="lineWrap" ${s.lineWrap?'checked':''}><span class="toggle-track"></span></label>`)}
            ${this.#row('Auto Load Project Files', 'Automatically open primary project file in Editor on launch (Auto mode)',
              `<label class="toggle"><input type="checkbox" data-key="autoLoadProjectFiles" id="settings-auto-load-toggle" ${s.autoLoadProjectFiles !== false ? 'checked' : ''}><span class="toggle-track"></span></label>`)}
          </div>
        </div>

        <!-- Default Project -->
        <div class="card" style="margin-bottom:var(--sp-4)" id="sect-defaults">
          <div class="card-header"><span class="material-symbols-outlined">tune</span>Default Project Values</div>
          <div class="card-body" style="padding:0">
            ${this.#row('Default Min SDK', '',
              `<input class="input" type="number" data-key="defaultMinSdk" value="${s.defaultMinSdk}" min="16" max="34" style="width:80px" />`)}
            ${this.#row('Default Target SDK', '',
              `<input class="input" type="number" data-key="defaultTargetSdk" value="${s.defaultTargetSdk}" min="21" max="34" style="width:80px" />`)}
            ${this.#row('Default Compile SDK', '',
              `<input class="input" type="number" data-key="defaultCompileSdk" value="${s.defaultCompileSdk}" min="21" max="34" style="width:80px" />`)}
            ${this.#row('Default License', '',
              `<div class="select-wrapper" style="width:150px">
                <select class="input select" data-key="defaultLicense">
                  ${['Apache-2.0','MIT','GPL-3.0','LGPL-2.1','BSD-3-Clause','ISC','Unlicense'].map(l =>
                    `<option${s.defaultLicense===l?' selected':''}>${l}</option>`).join('')}
                </select>
              </div>`)}
            ${this.#row('Default Author', '',
              `<input class="input" data-key="defaultAuthor" value="${s.defaultAuthor || ''}" placeholder="YourGitHubUsername" style="width:200px" />`)}
          </div>
        </div>

        <!-- Project Actions -->
        <div class="card" style="margin-bottom:var(--sp-4)" id="sect-project-actions">
          <div class="card-header"><span class="material-symbols-outlined">swap_vert</span>Project Actions</div>
          <div class="card-body" style="padding:0">
            ${this.#row('Import Project', 'Import an existing project from a ZIP archive',
              `<button type="button" class="btn btn-primary btn-sm" id="settings-import-btn">
                 <span class="material-symbols-outlined">upload</span>Import
               </button>`)}
            ${this.#row('Export Project', 'Download the active project as a ZIP archive',
              `<button type="button" class="btn btn-secondary btn-sm" id="settings-export-btn">
                 <span class="material-symbols-outlined">download</span>Export
               </button>`)}
          </div>
        </div>

        <!-- Browser & Android WebView Compatibility -->
        <div class="card" style="margin-bottom:var(--sp-4)" id="sect-webview-compat">
          <div class="card-header"><span class="material-symbols-outlined">devices</span>Browser & Android WebView Compatibility</div>
          <div class="card-body" style="padding:0">
            ${this.#row('Runtime Environment', 'Detected host browser / container',
              `<span class="badge badge-accent" id="env-runtime-badge">${WebViewBridge.getEnvironment().isWebView ? 'Android WebView' : 'Standard Web Browser'}</span>`)}
            ${this.#row('Serving Protocol', 'Web origin / protocol scheme',
              `<span style="font-family:var(--font-mono);font-size:var(--text-xs)">${WebViewBridge.getEnvironment().protocol}</span>`)}
            ${this.#row('ES Modules & Fetch', 'Native JavaScript ES6 Modules & Fetch API',
              `<span class="badge badge-success">Fully Supported</span>`)}
            ${this.#row('IndexedDB Storage', 'Offline database persistence status',
              `<span class="badge ${WebViewBridge.getEnvironment().hasIndexedDB ? 'badge-success' : 'badge-warning'}">${WebViewBridge.getEnvironment().hasIndexedDB ? 'Active (Operational)' : 'In-Memory Fallback'}</span>`)}
            ${this.#row('JSZip Engine', '100% Offline client-side archive packaging',
              `<span class="badge badge-success">Local v3.10.1 Active</span>`)}
            ${this.#row('Android Native Bridge', 'window.AndroidBridge or window.Android interface',
              `<span class="badge ${WebViewBridge.getEnvironment().hasBridge ? 'badge-success' : 'badge-secondary'}">${WebViewBridge.getEnvironment().hasBridge ? 'Connected' : 'Web Fallback'}</span>`)}
            ${this.#row('WebView Integration Guide', 'AndroidX WebViewAssetLoader & Native Bridge setup',
              `<button type="button" class="btn btn-secondary btn-sm" id="btn-webview-guide">
                 <span class="material-symbols-outlined">menu_book</span>View Integration Guide
               </button>`)}
            ${this.#row('Diagnostics & Self-Test', 'Verify download and clipboard mechanisms',
              `<div style="display:flex;gap:var(--sp-2)">
                 <button type="button" class="btn btn-secondary btn-sm" id="btn-test-download">
                   <span class="material-symbols-outlined">download</span>Test Download
                 </button>
                 <button type="button" class="btn btn-secondary btn-sm" id="btn-test-clipboard">
                   <span class="material-symbols-outlined">content_copy</span>Test Copy
                 </button>
               </div>`)}
          </div>
        </div>

        <!-- Storage -->
        <div class="card" style="margin-bottom:var(--sp-4)" id="sect-storage">
          <div class="card-header"><span class="material-symbols-outlined">storage</span>Storage Management</div>
          <div class="card-body" style="padding:0">
            ${this.#row('Storage Usage', 'IndexedDB usage estimate',
              `<span id="storage-usage-text" style="font-family:var(--font-mono);font-size:var(--text-xs);color:var(--text-muted)">Calculating…</span>`)}
            ${this.#row('Clear All Projects', 'Delete all projects from storage',
              `<button class="btn btn-danger btn-sm" id="clear-projects-btn"><span class="material-symbols-outlined">delete</span>Clear Projects</button>`)}
            ${this.#row('Clear Exports', 'Delete all export records',
              `<button class="btn btn-secondary btn-sm" id="clear-exports-btn"><span class="material-symbols-outlined">delete_sweep</span>Clear Exports</button>`)}
            ${this.#row('Clear Logs', 'Remove all stored log entries',
              `<button class="btn btn-secondary btn-sm" id="clear-logs-btn"><span class="material-symbols-outlined">delete_sweep</span>Clear Logs</button>`)}
          </div>
        </div>

        <div style="display:flex;gap:var(--sp-3)">
          <button class="btn btn-primary" id="settings-save-btn">
            <span class="material-symbols-outlined">save</span>Save Settings
          </button>
          <button class="btn btn-secondary" id="settings-reset-btn">
            <span class="material-symbols-outlined">refresh</span>Reset to Defaults
          </button>
        </div>
      </div>`;

    this.#bindControls(container);
    this.#loadStorageUsage(container);
  }

  #row(title, desc, control) {
    return `<div class="settings-row">
      <div class="settings-row-label">
        <div class="settings-row-title">${title}</div>
        ${desc ? `<div class="settings-row-desc">${desc}</div>` : ''}
      </div>
      <div class="settings-row-control">${control}</div>
    </div>`;
  }

  #bindControls(container) {
    // Import/Export reuse the same toolbar event flow (ZipService)
    container.querySelector('#settings-import-btn')?.addEventListener('click', () =>
      eventBus.emit('action:importProject'));
    container.querySelector('#settings-export-btn')?.addEventListener('click', () =>
      eventBus.emit('action:exportZip'));

    // WebView & Compatibility Action Triggers
    container.querySelector('#btn-webview-guide')?.addEventListener('click', () => {
      modal.show({
        title: 'Android WebView & WebViewAssetLoader Integration Guide',
        content: `
          <div style="font-size:var(--text-sm);line-height:1.6;color:var(--text-secondary);max-height:60vh;overflow-y:auto;padding-right:var(--sp-2)">
            <p style="margin-bottom:var(--sp-3)">
              To host this offline Vanilla JS (ES6 Modules) web application inside a native Android application without <code>file://</code> CORS restrictions:
            </p>
            <div style="background:var(--bg-elevated);border:1px solid var(--border);border-radius:var(--radius-md);padding:var(--sp-3);margin-bottom:var(--sp-3);font-family:var(--font-mono);font-size:11px">
              <strong>1. Add AndroidX WebKit dependency:</strong><br>
              implementation 'androidx.webkit:webkit:1.11.0'
            </div>
            <div style="background:var(--bg-elevated);border:1px solid var(--border);border-radius:var(--radius-md);padding:var(--sp-3);margin-bottom:var(--sp-3);font-family:var(--font-mono);font-size:11px">
              <strong>2. Use WebViewAssetLoader:</strong><br>
              val assetLoader = WebViewAssetLoader.Builder()<br>
              &nbsp;&nbsp;.addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))<br>
              &nbsp;&nbsp;.build()<br><br>
              // In WebViewClient.shouldInterceptRequest:<br>
              return assetLoader.shouldInterceptRequest(request.url)<br><br>
              // Load via virtual secure HTTPS origin:<br>
              webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
            </div>
            <div style="background:var(--bg-elevated);border:1px solid var(--border);border-radius:var(--radius-md);padding:var(--sp-3);margin-bottom:var(--sp-3);font-family:var(--font-mono);font-size:11px">
              <strong>3. Optional Native Bridge (window.AndroidBridge):</strong><br>
              webView.addJavascriptInterface(object {<br>
              &nbsp;&nbsp;@JavascriptInterface<br>
              &nbsp;&nbsp;fun saveFile(filename: String, base64: String, mime: String) { /* write to storage */ }<br>
              &nbsp;&nbsp;@JavascriptInterface<br>
              &nbsp;&nbsp;fun showToast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }<br>
              }, "AndroidBridge")
            </div>
            <p style="font-size:11px;color:var(--text-muted)">
              Full documentation with complete Kotlin and Java source code is available in <code>docs/android-webview-integration.md</code>.
            </p>
          </div>
        `,
        buttons: [
          {
            label: 'Copy Full Guide Path',
            variant: 'secondary',
            action: async () => {
              await ClipboardUtil.copyText('docs/android-webview-integration.md');
              toast.success('Path Copied to Clipboard');
            }
          },
          { label: 'Close', variant: 'primary', action: () => modal.close() }
        ]
      });
    });

    container.querySelector('#btn-test-download')?.addEventListener('click', async () => {
      const content = `RONi Android Library Generator Diagnostics\nTimestamp: ${new Date().toISOString()}\nEnvironment: ${JSON.stringify(WebViewBridge.getEnvironment(), null, 2)}`;
      const res = await WebViewBridge.downloadFile('diagnostic-test.txt', content, 'text/plain;charset=utf-8');
      if (res.success) {
        toast.success('Test Download Triggered', `Method: ${res.method}`);
      } else {
        toast.error('Test Download Failed', res.error || 'Unknown error');
      }
    });

    container.querySelector('#btn-test-clipboard')?.addEventListener('click', async () => {
      const ok = await ClipboardUtil.copyText(`RONi-LibGen-Test-${Date.now()}`);
      if (ok) {
        toast.success('Clipboard Test Passed', 'Test text copied via dual-mode ClipboardUtil');
      } else {
        toast.error('Clipboard Test Failed');
      }
    });

    // Sync inputs to #settings
    container.querySelectorAll('[data-key]').forEach(el => {
      const key = el.dataset.key;
      const handler = () => {
        this.#settings[key] = el.type === 'checkbox' ? el.checked
          : el.type === 'number' ? Number(el.value) : el.value;
        // Theme applies live (still persisted by Save)
        if (key === 'theme' && this.#settings.theme !== el.dataset.lastTheme) {
          el.dataset.lastTheme = this.#settings.theme;
          eventBus.emit('settings:themeChanged', { theme: this.#settings.theme });
        }
      };
      el.addEventListener('change', handler);
      el.addEventListener('input', handler);
    });

    // Language applies live and persists immediately
    container.querySelector('#settings-lang-select')?.addEventListener('change', e => {
      i18n.setLanguage(e.target.value);
      toast.info(e.target.value === 'bn' ? 'ভাষা: বাংলা' : 'Language: English');
    });

    container.querySelector('#settings-save-btn')?.addEventListener('click', async () => {
      for (const [k, v] of Object.entries(this.#settings)) {
        await storage.setSetting(k, v);
      }
      // Keep the UI-language preference mirrored for consistency
      await storage.setSetting('uiLanguage', i18n.lang);
      toast.success('Settings Saved');
    });

    container.querySelector('#settings-reset-btn')?.addEventListener('click', async () => {
      const ok = await modal.confirm({ icon: 'refresh', title: 'Reset Settings', message: 'Reset all settings to defaults?', confirmLabel: 'Reset' });
      if (!ok) return;
      this.#settings = { ...DEFAULTS };
      for (const [k, v] of Object.entries(DEFAULTS)) await storage.setSetting(k, v);
      eventBus.emit('settings:themeChanged', { theme: DEFAULTS.theme });
      toast.success('Settings reset to defaults');
      this.#render(container);
    });

    container.querySelector('#clear-projects-btn')?.addEventListener('click', async () => {
      const ok = await modal.confirm({ icon: 'delete', title: 'Clear Projects', message: 'This will delete ALL projects permanently. Are you sure?', confirmLabel: 'Delete All', danger: true });
      if (!ok) return;
      await storage.clear('projects');
      await storage.setSetting('recentProjects', []);
      projectService.close();
      toast.warn('All projects cleared');
    });

    container.querySelector('#clear-exports-btn')?.addEventListener('click', async () => {
      await storage.clear('exports');
      toast.success('Export records cleared');
    });

    container.querySelector('#clear-logs-btn')?.addEventListener('click', async () => {
      await storage.clear('logs');
      toast.success('Logs cleared');
    });
  }

  async #loadStorageUsage(container) {
    const el = container.querySelector('#storage-usage-text');
    if (!el) return;
    try {
      const { usage, quota } = await storage.estimateUsage();
      const usedKb  = (usage / 1024).toFixed(0);
      const quotaMb = (quota / 1024 / 1024).toFixed(0);
      el.textContent = `${usedKb} KB used of ${quotaMb} MB`;
    } catch { el.textContent = 'Unknown'; }
  }
}
