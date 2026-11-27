/**
 * EditorModule — tabbed code editor with syntax highlighting, line numbers, undo/redo, search/replace, autosave.
 */
import { storage } from '../storage/StorageService.js';
import { eventBus } from '../../core/EventBus.js';
import { projectService } from '../storage/ProjectService.js';
import { consoleService } from '../storage/ConsoleService.js';
import { SyntaxHighlighter } from './SyntaxHighlighter.js';
import { toast } from '../../ui/ToastService.js';
import { PreValidatorService } from '../build/PreValidatorService.js';
import { WebViewBridge } from '../../core/WebViewBridge.js';
import { VersionUpdateEngine } from '../version/VersionUpdateEngine.js';
import { ClipboardUtil } from '../../core/ClipboardUtil.js';

/** Get Material icon for a filename. */
function fileIcon(name) {
  const ext = name.split('.').pop().toLowerCase();
  const map = {
    java:'code', kt:'code', kts:'settings', xml:'data_object',
    gradle:'build', json:'data_object', yml:'settings', yaml:'settings',
    md:'description', txt:'article', gitignore:'visibility_off',
    sh:'terminal', bat:'terminal', properties:'tune',
    png:'image', jpg:'image', svg:'image',
    LICENSE:'gavel', CHANGELOG:'history', README:'auto_stories',
  };
  if (map[ext]) return map[ext];
  if (map[name]) return map[name];
  return 'insert_drive_file';
}

function iconClass(name) {
  const ext = name.split('.').pop().toLowerCase();
  const classes = { java:'java', kt:'kotlin', kts:'kotlin', xml:'xml',
    gradle:'gradle', json:'json', yml:'yml', yaml:'yml',
    md:'md', sh:'sh', bat:'sh', properties:'properties' };
  return classes[ext] || '';
}

export class EditorModule {
  /** @type {Map<string, {content:string, dirty:boolean, lang:string, undoStack:string[], redoStack:string[], scrollTop?:number, scrollLeft?:number}>} */
  #tabs       = new Map();
  #activeTab  = null;
  #container  = null;
  #autoSaveTimer = null;
  #globalEventsBound = false;
  #autoFixState = null;
  #lastUndoPushTime = 0;
  #renderedLineCount = 0;
  #filesPanelOpen = false;
  #fileFilterTerm = '';
  #autoLoadFiles = true;

  constructor() {
    this.#bindGlobalEvents();
  }

  async init(container, params = {}) {
    this.#container = container;
    this.#render();
    this.#bindGlobalEvents();
    this.#renderFilesList();

    try {
      const savedMode = await storage.getSetting('autoLoadProjectFiles');
      if (savedMode !== undefined && savedMode !== null) {
        this.#autoLoadFiles = Boolean(savedMode);
      }
    } catch {
      this.#autoLoadFiles = true;
    }
    this.#updateAutoModeUI();

    // Restore existing tabs after re-mounting
    if (this.#tabs.size > 0) {
      this.#renderTabs();
      this.#renderEditor();
    }
    const targetPath = params?.path || params?.openFile;
    if (targetPath) {
      if (params.fromVersion || params.toVersion || params.highlightLine || params.lineNumber || params.oldLine || params.newLine) {
        this.openWithVersionFix({
          path: targetPath,
          content: params.content,
          lineNumber: params.lineNumber || params.highlightLine,
          fromVersion: params.fromVersion,
          toVersion: params.toVersion,
          oldLine: params.oldLine,
          newLine: params.newLine
        });
      } else {
        this.openFile(targetPath, params.content);
      }
    } else if (this.#tabs.size === 0 && this.#autoLoadFiles) {
      // Auto-mode: automatically load primary project file
      this.#autoOpenPrimaryFile();
    }
  }

  /**
   * Auto-open primary project file when in Auto mode.
   */
  #autoOpenPrimaryFile() {
    const files = projectService.current?.files || {};
    const paths = Object.keys(files);
    if (!paths.length) return;

    // Prefer library build.gradle, then settings.gradle, then root build.gradle, then first file
    const primary = paths.find(p => p.includes('build.gradle') && (p.includes('lib') || p.includes('/')))
      || paths.find(p => p.endsWith('settings.gradle') || p.endsWith('settings.gradle.kts'))
      || paths.find(p => p.endsWith('build.gradle') || p.endsWith('build.gradle.kts'))
      || paths.find(p => p.endsWith('.java') || p.endsWith('.kt'))
      || paths[0];

    if (primary) {
      this.openFile(primary);
    }
  }

  destroy() {
    if (this.#autoSaveTimer) {
      clearTimeout(this.#autoSaveTimer);
      this.#autoSaveTimer = null;
    }
    if (this.#activeTab && this.#tabs.get(this.#activeTab)?.dirty) {
      this.saveActive().catch(() => {});
    }
    if (this.#container) {
      this.#container.innerHTML = '';
      this.#container = null;
    }
    this.#filesPanelOpen = false;
  }

  #render() {
    this.#container.innerHTML = `
      <div class="editor-area">
        <div class="editor-toolbar">
          <button type="button" class="btn btn-ghost btn-sm btn-icon ${this.#filesPanelOpen ? 'active' : ''}" id="ed-filetree-toggle" title="Toggle Project Files (Click to show/hide)">
            <span class="material-symbols-outlined">folder_open</span>
          </button>
          <button type="button" class="btn btn-ghost btn-sm" id="ed-auto-mode-toggle" title="Toggle Auto/Manual Project Files loading mode" style="display:flex;align-items:center;gap:4px;padding:2px 8px;border-radius:4px">
            <span class="material-symbols-outlined" id="ed-auto-mode-icon" style="font-size:16px;color:var(--accent)">bolt</span>
            <span id="ed-auto-mode-label" style="font-size:11px;font-weight:600">Auto Mode</span>
          </button>
          <div style="width:1px;height:20px;background:var(--border);margin:0 4px"></div>
          <button type="button" class="btn btn-ghost btn-sm btn-icon" id="ed-undo" title="Undo (Ctrl+Z)">
            <span class="material-symbols-outlined">undo</span>
          </button>
          <button type="button" class="btn btn-ghost btn-sm btn-icon" id="ed-redo" title="Redo (Ctrl+Y)">
            <span class="material-symbols-outlined">redo</span>
          </button>
          <div style="width:1px;height:20px;background:var(--border);margin:0 4px"></div>
          <button type="button" class="btn btn-ghost btn-sm btn-icon" id="ed-search-toggle" title="Search (Ctrl+F)">
            <span class="material-symbols-outlined">search</span>
          </button>
          <button type="button" class="btn btn-ghost btn-sm btn-icon" id="ed-save-btn" title="Save (Ctrl+S)">
            <span class="material-symbols-outlined">save</span>
          </button>
          <button type="button" class="btn btn-ghost btn-sm btn-icon" id="ed-copy-btn" title="Copy Full Content">
            <span class="material-symbols-outlined">content_copy</span>
          </button>
          <button type="button" class="btn btn-ghost btn-sm btn-icon" id="ed-download-btn" title="Download File">
            <span class="material-symbols-outlined">download</span>
          </button>
          <button type="button" class="btn btn-ghost btn-sm btn-icon" id="ed-validate-btn" title="Pre-Validate File Syntax">
            <span class="material-symbols-outlined">verified</span>
          </button>
          <div style="width:1px;height:20px;background:var(--border);margin:0 4px"></div>
          <button type="button" class="btn btn-ghost btn-sm btn-icon" id="ed-wrap-toggle" title="Toggle Soft Line Wrap">
            <span class="material-symbols-outlined">wrap_text</span>
          </button>
          <button type="button" class="btn btn-ghost btn-sm btn-icon" id="ed-height-toggle" title="Cycle Editor Height (100% Full / 75% / 50%)">
            <span class="material-symbols-outlined">fit_screen</span>
          </button>
          <button type="button" class="btn btn-ghost btn-sm" id="ed-smart-gradle-btn" title="Smart Gradle Project Analysis" style="display:none;color:var(--accent);font-weight:600">
            <span class="material-symbols-outlined" style="font-size:16px;color:var(--accent)">analytics</span>
            <span class="desktop-only" style="margin-left:4px">Smart Gradle</span>
          </button>
          <div class="flex-1"></div>
          <span class="editor-lang-badge" id="ed-lang-badge">—</span>
        </div>
        <div class="editor-tabs-bar" id="ed-tabs-bar"></div>
        <div id="ed-search-bar" class="editor-search-bar" style="display:none">
          <input id="ed-search-input" placeholder="Search…" autocomplete="off" />
          <input id="ed-replace-input" placeholder="Replace…" autocomplete="off" />
          <button type="button" class="btn btn-secondary btn-sm" id="ed-find-btn">Find</button>
          <button type="button" class="btn btn-secondary btn-sm" id="ed-replace-btn">Replace</button>
          <button type="button" class="btn btn-ghost btn-sm btn-icon" id="ed-search-close">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>
        <div id="ed-autofix-container"></div>
        <div class="editor-body" id="ed-body">
          <aside class="editor-files-panel ${this.#filesPanelOpen ? '' : 'collapsed'}" id="ed-files-panel">
            <div class="editor-files-header">
              <div class="editor-files-title">
                <span class="material-symbols-outlined" style="font-size:16px;color:var(--accent)">folder_special</span>
                <span>Project Files</span>
              </div>
              <button type="button" class="btn btn-ghost btn-xs btn-icon" id="ed-files-refresh-btn" title="Refresh Files">
                <span class="material-symbols-outlined" style="font-size:16px">refresh</span>
              </button>
            </div>
            <div class="editor-files-search">
              <input type="text" id="ed-files-search-input" placeholder="Filter files…" autocomplete="off" />
            </div>
            <div class="editor-files-list" id="ed-files-list"></div>
          </aside>
          <div class="editor-main-pane" id="ed-main-pane">
            <div class="editor-empty" id="ed-empty">
              <span class="material-symbols-outlined editor-empty-icon">code_off</span>
              <div class="editor-empty-text">Open a file to start editing</div>
              <div style="font-size:var(--text-xs);color:var(--text-disabled)">
                Click a file on the left panel or in the Explorer
              </div>
            </div>
          </div>
        </div>
        <div class="editor-resize-handle" id="ed-resize-handle" title="Drag to adjust editor height">
          <div class="editor-resize-line"></div>
        </div>
        <div class="editor-info-bar" id="ed-info-bar" style="display:none">
          <span id="ed-cursor-pos">Ln 1, Col 1</span>
          <span id="ed-char-count">0 chars</span>
          <div class="flex-1"></div>
          <span id="ed-file-name" style="color:var(--text-muted)"></span>
        </div>
      </div>`;

    this.#bindEditorControls();
  }

  #bindEditorControls() {
    const c = this.#container;
    const editorArea = c.querySelector('.editor-area');
    const editorBody = c.querySelector('#ed-body');
    const resizeHandle = c.querySelector('#ed-resize-handle');
    const wrapBtn = c.querySelector('#ed-wrap-toggle');
    const heightBtn = c.querySelector('#ed-height-toggle');
    const filetreeToggle = c.querySelector('#ed-filetree-toggle');
    const filesPanel = c.querySelector('#ed-files-panel');
    const filesRefreshBtn = c.querySelector('#ed-files-refresh-btn');
    const filesSearchInput = c.querySelector('#ed-files-search-input');

    // Toggle File Explorer Panel
    filetreeToggle?.addEventListener('click', () => {
      if (filesPanel) {
        const isCollapsed = filesPanel.classList.toggle('collapsed');
        filetreeToggle.classList.toggle('active', !isCollapsed);
        this.#filesPanelOpen = !isCollapsed;
      }
    });

    // Auto / Manual Project Files Mode Toggle
    const autoModeToggle = c.querySelector('#ed-auto-mode-toggle');
    autoModeToggle?.addEventListener('click', async () => {
      this.#autoLoadFiles = !this.#autoLoadFiles;
      try {
        await storage.setSetting('autoLoadProjectFiles', this.#autoLoadFiles);
      } catch (err) {
        console.warn('[EditorModule] failed to persist autoLoadProjectFiles:', err);
      }
      this.#updateAutoModeUI();
      toast.info(
        this.#autoLoadFiles ? 'Auto Mode Enabled' : 'Manual Mode Enabled',
        this.#autoLoadFiles ? 'Primary project files open automatically' : 'Manual file selection active'
      );
      if (this.#autoLoadFiles && this.#tabs.size === 0) {
        this.#autoOpenPrimaryFile();
      }
    });

    // Refresh File Explorer Panel
    filesRefreshBtn?.addEventListener('click', () => {
      this.#renderFilesList();
      toast.info('File List Refreshed');
    });

    // Filter File Explorer Panel
    filesSearchInput?.addEventListener('input', (e) => {
      this.#fileFilterTerm = e.target.value;
      this.#renderFilesList();
    });

    c.querySelector('#ed-undo')?.addEventListener('click', () => this.undo());
    c.querySelector('#ed-redo')?.addEventListener('click', () => this.redo());
    c.querySelector('#ed-save-btn')?.addEventListener('click', () => this.saveActive());
    c.querySelector('#ed-copy-btn')?.addEventListener('click', () => this.copyActive());
    c.querySelector('#ed-download-btn')?.addEventListener('click', () => this.downloadActive());
    c.querySelector('#ed-validate-btn')?.addEventListener('click', () => this.validateActive());
    c.querySelector('#ed-smart-gradle-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'analyzer', filePath: this.#activeTab });
    });
    c.querySelector('#ed-search-toggle')?.addEventListener('click', () => this.#toggleSearch());
    c.querySelector('#ed-search-close')?.addEventListener('click', () => this.#toggleSearch(false));
    c.querySelector('#ed-find-btn')?.addEventListener('click', () => this.#doSearch());
    c.querySelector('#ed-replace-btn')?.addEventListener('click', () => this.#doReplace());

    c.querySelector('#ed-search-input')?.addEventListener('keydown', e => {
      if (e.key === 'Enter') this.#doSearch();
      if (e.key === 'Escape') this.#toggleSearch(false);
    });

    // Toggle word wrap
    wrapBtn?.addEventListener('click', () => {
      const isWrapped = editorArea?.classList.toggle('wrap-enabled');
      wrapBtn.classList.toggle('active', isWrapped);
    });

    // Height presets cycle: 100% full, 75%, 50%
    const heightPresets = ['100%', '75%', '50%'];
    let currentPresetIdx = 0;
    heightBtn?.addEventListener('click', () => {
      currentPresetIdx = (currentPresetIdx + 1) % heightPresets.length;
      const targetPct = heightPresets[currentPresetIdx];
      if (targetPct === '100%') {
        if (editorBody) {
          editorBody.style.flex = '1 1 auto';
          editorBody.style.height = '100%';
        }
        if (editorArea) {
          editorArea.style.height = '100%';
        }
      } else {
        const availableH = c.clientHeight || window.innerHeight;
        const calcH = Math.max(220, Math.round(availableH * (parseInt(targetPct, 10) / 100)));
        if (editorBody) {
          editorBody.style.flex = 'none';
          editorBody.style.height = `${calcH}px`;
        }
      }
    });

    // Drag / Touch resize handle
    if (resizeHandle && editorBody) {
      let isDragging = false;
      let startY = 0;
      let startH = 0;

      const onStart = (clientY) => {
        isDragging = true;
        startY = clientY;
        startH = editorBody.getBoundingClientRect().height;
        resizeHandle.classList.add('active');
        document.body.style.userSelect = 'none';
        document.body.style.cursor = 'row-resize';
      };

      const onMove = (clientY) => {
        if (!isDragging) return;
        const delta = clientY - startY;
        const newH = Math.max(200, Math.min(window.innerHeight - 100, startH + delta));
        editorBody.style.flex = 'none';
        editorBody.style.height = `${newH}px`;
      };

      const onEnd = () => {
        if (!isDragging) return;
        isDragging = false;
        resizeHandle.classList.remove('active');
        document.body.style.userSelect = '';
        document.body.style.cursor = '';
      };

      resizeHandle.addEventListener('mousedown', e => {
        e.preventDefault();
        onStart(e.clientY);
      });

      window.addEventListener('mousemove', e => {
        if (isDragging) onMove(e.clientY);
      });

      window.addEventListener('mouseup', () => {
        if (isDragging) onEnd();
      });

      // Mobile touch support
      resizeHandle.addEventListener('touchstart', e => {
        if (e.touches.length === 1) {
          onStart(e.touches[0].clientY);
        }
      }, { passive: true });

      window.addEventListener('touchmove', e => {
        if (isDragging && e.touches.length === 1) {
          onMove(e.touches[0].clientY);
        }
      }, { passive: true });

      window.addEventListener('touchend', () => {
        if (isDragging) onEnd();
      });
    }
  }

  #bindGlobalEvents() {
    if (this.#globalEventsBound) return;
    this.#globalEventsBound = true;
    document.addEventListener('keydown', e => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'z') { e.preventDefault(); this.undo(); }
      if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.shiftKey && e.key === 'z'))) { e.preventDefault(); this.redo(); }
      if ((e.ctrlKey || e.metaKey) && e.key === 'f') { e.preventDefault(); this.#toggleSearch(true); }
    });
    eventBus.on('editor:openFile', ({ path, content, lang }) => this.openFile(path, content, lang));
    eventBus.on('editor:filesDeleted', ({ paths }) => {
      if (!paths || !Array.isArray(paths)) return;
      for (const p of paths) {
        if (this.#tabs.has(p)) this.closeTab(p);
      }
      this.#renderFilesList();
    });
    eventBus.on('project:fileDeleted', ({ path }) => {
      if (path && this.#tabs.has(path)) this.closeTab(path);
      this.#renderFilesList();
    });
    eventBus.on('project:loaded', () => this.#renderFilesList());
    eventBus.on('project:fileCreated', () => this.#renderFilesList());
    eventBus.on('project:filesImported', () => this.#renderFilesList());
    eventBus.on('project:saved', () => this.#renderFilesList());
    eventBus.on('action:saveProject', () => this.saveActive());
    eventBus.on('editor:openWithFix', (params) => {
      if (params) this.openWithVersionFix(params);
    });
    eventBus.on('settings:updated', (s) => {
      if (s && s.autoLoadProjectFiles !== undefined) {
        this.#autoLoadFiles = Boolean(s.autoLoadProjectFiles);
        this.#updateAutoModeUI();
      }
    });
  }

  /**
   * Update Auto / Manual mode UI indicator on editor toolbar.
   */
  #updateAutoModeUI() {
    if (!this.#container) return;
    const btn = this.#container.querySelector('#ed-auto-mode-toggle');
    const icon = this.#container.querySelector('#ed-auto-mode-icon');
    const label = this.#container.querySelector('#ed-auto-mode-label');
    if (!btn || !icon || !label) return;

    if (this.#autoLoadFiles) {
      btn.classList.add('active');
      btn.title = 'Project Files Mode: Auto (Files open automatically on project load). Click to switch to Manual.';
      icon.textContent = 'bolt';
      icon.style.color = 'var(--accent)';
      label.textContent = 'Auto Mode';
    } else {
      btn.classList.remove('active');
      btn.title = 'Project Files Mode: Manual (Manual file selection). Click to switch to Auto.';
      icon.textContent = 'touch_app';
      icon.style.color = 'var(--text-muted)';
      label.textContent = 'Manual Mode';
    }
  }

  /**
   * Render the embedded Project Files List for instant single-click loading.
   */
  #renderFilesList() {
    if (!this.#container) return;
    const listEl = this.#container.querySelector('#ed-files-list');
    if (!listEl) return;

    const files = projectService.current?.files || {};
    const paths = Object.keys(files).sort();

    if (paths.length === 0) {
      listEl.innerHTML = `
        <div style="padding:16px 12px;text-align:center;color:var(--text-muted);font-size:var(--text-xs)">
          No files in project yet.
        </div>`;
      return;
    }

    const term = (this.#fileFilterTerm || '').toLowerCase().trim();
    const filtered = term ? paths.filter(p => p.toLowerCase().includes(term)) : paths;

    if (filtered.length === 0) {
      listEl.innerHTML = `
        <div style="padding:16px 12px;text-align:center;color:var(--text-muted);font-size:var(--text-xs)">
          No matching files
        </div>`;
      return;
    }

    listEl.innerHTML = filtered.map(path => {
      const name = path.split('/').pop();
      const dir = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '';
      const isActive = path === this.#activeTab;
      const ic = fileIcon(name);
      const cls = iconClass(name);
      return `
        <div class="editor-file-item${isActive ? ' active' : ''}" data-path="${path}" title="${path}">
          <span class="material-symbols-outlined file-icon ${cls}">${ic}</span>
          <span class="file-name">${name}</span>
          ${dir ? `<span class="file-dir">${dir}</span>` : ''}
        </div>`;
    }).join('');

    listEl.querySelectorAll('.editor-file-item').forEach(item => {
      item.addEventListener('click', () => {
        const path = item.dataset.path;
        if (path) {
          this.openFile(path);
        }
      });
    });
  }

  /**
   * Synchronize active selection highlighting in the files panel.
   */
  #updateFileHighlighting() {
    if (!this.#container) return;
    const listEl = this.#container.querySelector('#ed-files-list');
    if (!listEl) return;
    listEl.querySelectorAll('.editor-file-item').forEach(item => {
      if (item.dataset.path === this.#activeTab) {
        item.classList.add('active');
      } else {
        item.classList.remove('active');
      }
    });
  }

  /**
   * Open a file in the editor with instant single-click loading.
   * @param {string} path
   * @param {string} [content]
   * @param {string} [lang]
   */
  openFile(path, content, lang) {
    if (!path) return;
    if (content === undefined || content === null) {
      content = projectService.getFile(path) ?? '';
    }
    if (typeof content !== 'string') {
      if (content instanceof Uint8Array) {
        content = '[Binary file — cannot edit]';
      } else {
        content = String(content ?? '');
      }
    }
    if (!this.#tabs.has(path)) {
      this.#tabs.set(path, {
        content,
        lang: lang || SyntaxHighlighter.detectLang(path),
        dirty: false,
        undoStack: [],
        redoStack: [],
        scrollTop: 0,
        scrollLeft: 0,
      });
    } else if (!this.#tabs.get(path).dirty && content) {
      this.#tabs.get(path).content = content;
    }
    this.#activateTab(path);
    if (this.#container) {
      this.#renderTabs();
      this.#renderEditor();
      this.#updateFileHighlighting();
    }
  }

  #activateTab(path) {
    this.#activeTab = path;
    this.#updateFileHighlighting();
  }

  #renderTabs() {
    if (!this.#container) return;
    const bar = this.#container.querySelector('#ed-tabs-bar');
    if (!bar) return;
    bar.innerHTML = [...this.#tabs.entries()].map(([path, tab]) => {
      const name  = path.split('/').pop();
      const active = path === this.#activeTab;
      return `
        <div class="editor-tab${active ? ' active' : ''}" data-path="${path}" title="${path}">
          ${tab.dirty ? '<span class="editor-tab-dirty"></span>' : ''}
          <span class="editor-tab-name">${name}</span>
          <span class="editor-tab-close" data-close="${path}"
                aria-label="Close ${name}">✕</span>
        </div>`;
    }).join('');

    bar.querySelectorAll('.editor-tab').forEach(el => {
      el.addEventListener('click', e => {
        if (e.target.closest('[data-close]')) {
          const p = e.target.closest('[data-close]').dataset.close;
          e.stopPropagation();
          this.closeTab(p);
        } else {
          this.#activateTab(el.dataset.path);
          this.#renderTabs();
          this.#renderEditor();
        }
      });
    });
    this.#updateFileHighlighting();
  }

  #renderEditor() {
    if (!this.#container) return;
    const mainPane = this.#container.querySelector('#ed-main-pane') || this.#container.querySelector('#ed-body');
    const empty = this.#container.querySelector('#ed-empty');
    const info  = this.#container.querySelector('#ed-info-bar');
    const badge = this.#container.querySelector('#ed-lang-badge');
    if (!mainPane) return;

    if (!this.#activeTab || !this.#tabs.has(this.#activeTab)) {
      if (empty) empty.style.display = '';
      mainPane.querySelector('#ed-editor-wrap')?.remove();
      if (info) info.style.display = 'none';
      this.#updateFileHighlighting();
      return;
    }

    if (empty) empty.style.display = 'none';
    if (info)  info.style.display  = '';

    const tab  = this.#tabs.get(this.#activeTab);
    const name = this.#activeTab.split('/').pop();
    if (badge) badge.textContent = tab.lang.toUpperCase();

    const smartGradleBtn = this.#container.querySelector('#ed-smart-gradle-btn');
    const isGradle = this.#activeTab && (this.#activeTab.endsWith('.gradle') || this.#activeTab.endsWith('.gradle.kts') || this.#activeTab.includes('gradle'));
    if (smartGradleBtn) {
      smartGradleBtn.style.display = isGradle ? 'inline-flex' : 'none';
    }

    const el = this.#container.querySelector('#ed-file-name');
    if (el) el.textContent = name;

    let wrap = mainPane.querySelector('#ed-editor-wrap');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.id = 'ed-editor-wrap';
      wrap.style.cssText = 'display:flex;flex:1;min-height:0;overflow:hidden;width:100%;height:100%';
      mainPane.appendChild(wrap);
    }

    wrap.innerHTML = `
      <div class="editor-gutter" id="ed-gutter">
        <div class="editor-line-numbers" id="ed-line-nums"></div>
      </div>
      <div class="editor-content-area" id="ed-content-area">
        <textarea class="editor-textarea" id="ed-textarea" spellcheck="false"
                  autocorrect="off" autocapitalize="off"></textarea>
      </div>`;

    // CRITICAL: Reset renderedLineCount so line numbers are guaranteed to render
    this.#renderedLineCount = 0;

    const textarea = wrap.querySelector('#ed-textarea');
    if (textarea) {
      textarea.value = tab.content;
      this.#updateLineNumbers(tab.content);
      this.#updateCursorInfo(textarea);
      this.#bindTextarea(textarea, this.#activeTab);
      // Restore tab scroll position
      if (tab.scrollTop !== undefined) {
        textarea.scrollTop = tab.scrollTop;
      }
      if (tab.scrollLeft !== undefined) {
        textarea.scrollLeft = tab.scrollLeft;
      }
      const nums = wrap.querySelector('#ed-line-nums');
      if (nums) {
        nums.style.transform = `translate3d(0, -${Math.round(textarea.scrollTop)}px, 0)`;
      }
    }

    this.#updateFileHighlighting();
  }

  #bindTextarea(textarea, path) {
    const gutter = this.#container?.querySelector('#ed-gutter');
    const nums = this.#container?.querySelector('#ed-line-nums');

    // Smooth scroll synchronization using translate3d
    const syncScroll = () => {
      const tab = this.#tabs.get(path);
      if (tab) {
        tab.scrollTop = textarea.scrollTop;
        tab.scrollLeft = textarea.scrollLeft;
      }
      if (nums) {
        nums.style.transform = `translate3d(0, -${Math.round(textarea.scrollTop)}px, 0)`;
      }
    };

    textarea.addEventListener('scroll', syncScroll);

    // Forward mousewheel events on gutter directly to textarea
    if (gutter) {
      gutter.addEventListener('wheel', e => {
        textarea.scrollTop += e.deltaY;
        textarea.scrollLeft += e.deltaX;
        syncScroll();
        e.preventDefault();
      }, { passive: false });
    }

    const updateCursor = () => {
      this.#updateCursorInfo(textarea);
      const curLine = this.#getCurrentLine(textarea);
      if (nums) {
        nums.querySelector('.editor-line-num.active')?.classList.remove('active');
        nums.querySelector(`.editor-line-num[data-line="${curLine}"]`)?.classList.add('active');
      }
    };

    textarea.addEventListener('input', () => {
      const tab = this.#tabs.get(path);
      if (!tab) return;
      const now = Date.now();
      // Coalesce undo pushes so rapid typing within 350ms forms a single undo step
      if (now - this.#lastUndoPushTime > 350 || tab.undoStack.length === 0) {
        tab.undoStack.push(tab.content);
        if (tab.undoStack.length > 80) tab.undoStack.shift();
        this.#lastUndoPushTime = now;
      }
      tab.redoStack = [];
      tab.content = textarea.value;
      tab.dirty   = true;
      this.#renderTabDirty(path, true);
      const curLine = this.#getCurrentLine(textarea);
      this.#updateLineNumbers(textarea.value, curLine);
      updateCursor();
      this.#scheduleAutoSave(path);
      syncScroll();
    });

    textarea.addEventListener('keydown', e => {
      if (e.key === 'Tab') {
        e.preventDefault();
        const start = textarea.selectionStart;
        const end   = textarea.selectionEnd;
        textarea.value = textarea.value.slice(0, start) + '    ' + textarea.value.slice(end);
        textarea.selectionStart = textarea.selectionEnd = start + 4;
        textarea.dispatchEvent(new Event('input'));
      }
    });

    textarea.addEventListener('keyup',   updateCursor);
    textarea.addEventListener('click',   updateCursor);
    textarea.addEventListener('select',  updateCursor);

    // Initial sync
    syncScroll();
  }

  #getCurrentLine(textarea) {
    if (!textarea) return 1;
    const before = textarea.value.slice(0, textarea.selectionStart);
    return (before.match(/\n/g) || []).length + 1;
  }

  #updateLineNumbers(content, currentLine = 1) {
    if (!this.#container) return;
    const lines = Math.max(1, (content.match(/\n/g) || []).length + 1);
    const nums  = this.#container.querySelector('#ed-line-nums');
    const gutter = this.#container.querySelector('#ed-gutter');
    if (!nums) return;

    // Dynamically adjust gutter width based on digit count
    const digits = String(lines).length;
    const gutterWidth = Math.max(52, digits * 10 + 26);
    if (gutter) {
      gutter.style.width = `${gutterWidth}px`;
      gutter.style.minWidth = `${gutterWidth}px`;
      gutter.style.flexShrink = '0';
      gutter.style.display = 'block';
      gutter.style.visibility = 'visible';
    }

    // Always rebuild DOM if line count changed OR if nums element is empty
    if (this.#renderedLineCount !== lines || nums.children.length === 0) {
      this.#renderedLineCount = lines;
      let html = '';
      for (let i = 1; i <= lines; i++) {
        const isActive = i === currentLine;
        html += `<div class="editor-line-num${isActive ? ' active' : ''}" data-line="${i}">${i}</div>`;
      }
      nums.innerHTML = html;
    } else {
      nums.querySelector('.editor-line-num.active')?.classList.remove('active');
      nums.querySelector(`.editor-line-num[data-line="${currentLine}"]`)?.classList.add('active');
    }
  }

  #updateCursorInfo(textarea) {
    if (!this.#container || !textarea) return;
    const before = textarea.value.slice(0, textarea.selectionStart);
    const lines  = before.split('\n');
    const ln     = lines.length;
    const col    = lines.at(-1).length + 1;
    const posEl  = this.#container.querySelector('#ed-cursor-pos');
    const lenEl  = this.#container.querySelector('#ed-char-count');
    if (posEl) posEl.textContent = `Ln ${ln}, Col ${col}`;
    if (lenEl) lenEl.textContent = `${textarea.value.length} chars`;
  }

  #renderTabDirty(path, dirty) {
    if (!this.#container) return;
    const tab = this.#container.querySelector(`.editor-tab[data-path="${path}"]`);
    if (!tab) return;
    const existing = tab.querySelector('.editor-tab-dirty');
    if (dirty && !existing) {
      const span = document.createElement('span');
      span.className = 'editor-tab-dirty';
      tab.prepend(span);
    } else if (!dirty && existing) {
      existing.remove();
    }
  }

  #toggleSearch(force) {
    if (!this.#container) return;
    const bar = this.#container.querySelector('#ed-search-bar');
    if (!bar) return;
    const show = force !== undefined ? force : bar.style.display === 'none';
    bar.style.display = show ? '' : 'none';
    if (show) this.#container.querySelector('#ed-search-input')?.focus();
  }

  #doSearch() {
    if (!this.#container) return;
    const term = this.#container.querySelector('#ed-search-input')?.value;
    if (!term || !this.#activeTab) return;
    const textarea = this.#container.querySelector('#ed-textarea');
    if (!textarea) return;
    const idx = textarea.value.indexOf(term, textarea.selectionEnd || 0);
    if (idx !== -1) {
      textarea.focus();
      textarea.setSelectionRange(idx, idx + term.length);
    } else {
      // wrap around
      const idx2 = textarea.value.indexOf(term);
      if (idx2 !== -1) { textarea.focus(); textarea.setSelectionRange(idx2, idx2 + term.length); }
    }
  }

  #doReplace() {
    if (!this.#container) return;
    const term    = this.#container.querySelector('#ed-search-input')?.value;
    const replace = this.#container.querySelector('#ed-replace-input')?.value ?? '';
    const textarea = this.#container.querySelector('#ed-textarea');
    if (!term || !textarea) return;
    const start = textarea.selectionStart, end = textarea.selectionEnd;
    const selected = textarea.value.slice(start, end);
    if (selected === term) {
      textarea.value = textarea.value.slice(0, start) + replace + textarea.value.slice(end);
      textarea.dispatchEvent(new Event('input'));
      textarea.setSelectionRange(start, start + replace.length);
    }
    this.#doSearch();
  }

  #scheduleAutoSave(path) {
    clearTimeout(this.#autoSaveTimer);
    this.#autoSaveTimer = setTimeout(() => this.saveTab(path), 30000);
  }

  async saveTab(path) {
    const tab = this.#tabs.get(path);
    if (!tab) return;

    // Pre-validation before save — do not silently save invalid content
    if (path.endsWith('.gradle') || path.endsWith('.gradle.kts')) {
      const check = PreValidatorService.validateGradleSyntax(tab.content, path);
      if (!check.valid) {
        toast.error('Syntax Error in Gradle', check.errors[0] || 'Fix syntax errors before saving');
        consoleService.error(`Pre-validation failed for ${path}: ${check.errors.join('; ')}`);
        return;
      }
    } else if (path.endsWith('AndroidManifest.xml')) {
      const isApp = path.includes('app/') || path.includes('sample/');
      const check = PreValidatorService.validateManifestXml(tab.content, path, isApp);
      if (!check.valid) {
        toast.error('XML Error in Manifest', check.errors[0] || 'Fix XML errors before saving');
        consoleService.error(`Pre-validation failed for ${path}: ${check.errors.join('; ')}`);
        return;
      }
    }

    tab.dirty = false;
    this.#renderTabDirty(path, false);
    await projectService.setFile(path, tab.content);
    toast.success('Saved', path.split('/').pop());
    consoleService.success(`Saved: ${path.split('/').pop()}`);
  }

  async saveActive() {
    if (this.#activeTab) await this.saveTab(this.#activeTab);
  }

  async copyActive() {
    if (!this.#activeTab) {
      toast.info('No file open', 'Open a file to copy its content');
      return;
    }
    const tab = this.#tabs.get(this.#activeTab);
    const content = tab ? tab.content : (projectService.getFile(this.#activeTab) ?? '');
    const ok = await ClipboardUtil.copyText(content);
    if (ok) {
      toast.success('Copied Full Content', `${this.#activeTab.split('/').pop()} copied to clipboard`);
    } else {
      toast.error('Copy failed', 'Please select text and copy manually');
    }
  }

  downloadActive() {
    if (!this.#activeTab) {
      toast.info('No file open', 'Open a file to download');
      return;
    }
    const tab = this.#tabs.get(this.#activeTab);
    const content = tab ? tab.content : (projectService.getFile(this.#activeTab) ?? '');
    const filename = this.#activeTab.split('/').pop() || 'file.txt';
    WebViewBridge.downloadFile(filename, content, 'text/plain;charset=utf-8');
    toast.info('Downloaded', filename);
  }

  validateActive() {
    if (!this.#activeTab) return;
    const tab = this.#tabs.get(this.#activeTab);
    const content = tab ? tab.content : (projectService.getFile(this.#activeTab) ?? '');

    if (this.#activeTab.endsWith('.gradle') || this.#activeTab.endsWith('.gradle.kts')) {
      const res = PreValidatorService.validateGradleSyntax(content, this.#activeTab);
      if (res.valid) {
        toast.success('Gradle Syntax Valid', 'No syntax or structural errors detected.');
      } else {
        toast.error('Gradle Syntax Error', res.errors[0] || 'Errors detected in Gradle script');
      }
    } else if (this.#activeTab.endsWith('.xml')) {
      const isApp = this.#activeTab.includes('app/') || this.#activeTab.includes('sample/');
      const res = PreValidatorService.validateManifestXml(content, this.#activeTab, isApp);
      if (res.valid) {
        toast.success('XML Syntax Valid', 'No XML syntax errors detected.');
      } else {
        toast.error('XML Syntax Error', res.errors[0] || 'Errors detected in XML file');
      }
    } else {
      const res = PreValidatorService.validateFilePath(this.#activeTab);
      if (res.valid) {
        toast.success('File Valid', 'File path and structure are valid.');
      } else {
        toast.error('Invalid File', res.error);
      }
    }
  }

  undo() {
    const tab = this.#tabs.get(this.#activeTab);
    if (!tab || !tab.undoStack.length) return;
    tab.redoStack.push(tab.content);
    tab.content = tab.undoStack.pop();
    if (!this.#container) return;
    const textarea = this.#container.querySelector('#ed-textarea');
    if (textarea) { textarea.value = tab.content; textarea.dispatchEvent(new Event('input')); }
  }

  redo() {
    const tab = this.#tabs.get(this.#activeTab);
    if (!tab || !tab.redoStack.length) return;
    tab.undoStack.push(tab.content);
    tab.content = tab.redoStack.pop();
    if (!this.#container) return;
    const textarea = this.#container.querySelector('#ed-textarea');
    if (textarea) { textarea.value = tab.content; textarea.dispatchEvent(new Event('input')); }
  }

  closeTab(path) {
    this.#tabs.delete(path);
    if (this.#activeTab === path) {
      const keys = [...this.#tabs.keys()];
      this.#activeTab = keys.length ? keys.at(-1) : null;
    }
    if (this.#container) {
      this.#renderTabs();
      this.#renderEditor();
    }
  }

  // ── Auto Fix & Update Line in File Editor ────────────────────────────────

  /**
   * Opens a file, highlights the targeted version line, and shows the Auto Fix banner.
   * @param {object} params
   * @param {string} params.path - File path
   * @param {string} [params.content] - Optional file content
   * @param {number} [params.lineNumber] - Line number to highlight
   * @param {string} [params.fromVersion] - Old version string (e.g. "1.0.8" or "1.0.0")
   * @param {string} [params.toVersion] - Target version string (e.g. "1.0.4")
   * @param {string} [params.oldLine] - Optional original line text
   * @param {string} [params.newLine] - Optional replacement line text
   */
  openWithVersionFix(params) {
    const { path, content, lineNumber, fromVersion, toVersion, oldLine, newLine } = params;
    if (!path) return;

    // Open the file tab
    this.openFile(path, content);

    const tab = this.#tabs.get(path);
    if (!tab) return;

    const fileContent = tab.content;
    const lines = fileContent.split('\n');

    const fromVer = fromVersion || '1.0.8';
    const toVer = toVersion || '1.0.4';

    // Scan for all occurrences of fromVersion in the file
    const matches = [];
    lines.forEach((line, idx) => {
      if (line.includes(fromVer)) {
        // Protection check against toolchains and 3rd party dependencies
        let isProtected = false;
        let reason = '';
        for (const pattern of (VersionUpdateEngine?.PROTECTED_PATTERNS || [])) {
          if (pattern.test(line)) {
            isProtected = true;
            reason = 'Protected toolchain or third-party dependency';
            break;
          }
        }

        const repLine = !isProtected ? line.split(fromVer).join(toVer) : line;
        matches.push({
          lineNumber: idx + 1,
          oldLine: line,
          newLine: repLine,
          isProtected,
          reason,
          applied: false
        });
      }
    });

    // Fallback: if fromVersion wasn't matched directly in lines, but oldLine/lineNumber was provided
    if (matches.length === 0 && lineNumber && lines[lineNumber - 1] !== undefined) {
      matches.push({
        lineNumber: Number(lineNumber),
        oldLine: lines[lineNumber - 1],
        newLine: newLine || lines[lineNumber - 1].replace(fromVer, toVer),
        isProtected: false,
        reason: 'Specified line',
        applied: false
      });
    }

    // Determine target index
    let currentIdx = 0;
    if (lineNumber) {
      const foundIdx = matches.findIndex(m => m.lineNumber === Number(lineNumber));
      if (foundIdx !== -1) currentIdx = foundIdx;
    }

    this.#autoFixState = {
      path,
      fromVersion: fromVer,
      toVersion: toVer,
      matches,
      currentIndex: currentIdx,
      backupContent: fileContent,
      hasUndo: false
    };

    // Render banner and highlight the line
    this.#renderAutoFixBar();
    const targetMatch = matches[currentIdx];
    if (targetMatch) {
      this.#highlightFixLine(targetMatch.lineNumber, fromVer);
    }
  }

  /**
   * Highlights a line and scrolls it into view.
   */
  #highlightFixLine(lineNum, verToken) {
    if (!this.#container) return;
    const textarea = this.#container.querySelector('#ed-textarea');
    const nums = this.#container.querySelector('#ed-line-nums');

    if (nums) {
      nums.querySelectorAll('.editor-line-num.highlight-fix').forEach(el => el.classList.remove('highlight-fix'));
      const activeLineEl = nums.querySelector(`.editor-line-num[data-line="${lineNum}"]`);
      if (activeLineEl) {
        activeLineEl.classList.add('highlight-fix');
      }
    }

    if (textarea) {
      const lines = textarea.value.split('\n');
      if (lineNum > 0 && lineNum <= lines.length) {
        let charStart = 0;
        for (let i = 0; i < lineNum - 1; i++) {
          charStart += lines[i].length + 1;
        }
        const currentLineText = lines[lineNum - 1];
        const tokenIdx = verToken ? currentLineText.indexOf(verToken) : -1;

        const selStart = tokenIdx !== -1 ? charStart + tokenIdx : charStart;
        const selEnd = tokenIdx !== -1 ? selStart + verToken.length : charStart + currentLineText.length;

        textarea.focus();
        textarea.setSelectionRange(selStart, selEnd);

        // Center line smoothly in editor
        const approxLineH = 20;
        const scrollTarget = Math.max(0, (lineNum - 4) * approxLineH);
        textarea.scrollTop = scrollTarget;
        if (nums) nums.style.transform = `translateY(-${textarea.scrollTop}px)`;
      }
    }
  }

  /**
   * Renders the interactive Auto Fix banner.
   */
  #renderAutoFixBar() {
    if (!this.#container) return;
    const host = this.#container.querySelector('#ed-autofix-container');
    if (!host) return;

    if (!this.#autoFixState || !this.#autoFixState.matches.length) {
      host.innerHTML = '';
      return;
    }

    const s = this.#autoFixState;
    const match = s.matches[s.currentIndex];
    const total = s.matches.length;
    const isProtected = match?.isProtected;
    const isApplied = match?.applied;

    const esc = str => String(str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

    host.innerHTML = `
      <div class="editor-autofix-bar">
        <div style="display:flex;align-items:center;gap:var(--sp-2);flex-wrap:wrap">
          <span class="version-badge version-badge-warning" style="display:inline-flex;align-items:center;gap:4px">
            <span class="material-symbols-outlined" style="font-size:15px">auto_fix_high</span>
            Auto Fix
          </span>
          <span style="font-weight:600;font-size:var(--fs-sm);color:var(--text-primary)">
            Line ${match.lineNumber}: <span style="color:var(--error);font-family:var(--font-mono)">${esc(s.fromVersion)}</span>
            <span style="color:var(--text-secondary)">→</span>
            <span style="color:var(--success);font-family:var(--font-mono)">${esc(s.toVersion)}</span>
          </span>

          ${total > 1 ? `
            <div style="display:flex;align-items:center;gap:4px;background:var(--bg-muted);padding:2px 8px;border-radius:var(--radius-sm);border:1px solid var(--border);font-size:var(--fs-xs)">
              <span style="color:var(--text-secondary)">Match <strong>${s.currentIndex + 1}</strong> of <strong>${total}</strong></span>
              <button type="button" class="btn btn-ghost btn-sm" id="ed-autofix-prev" style="padding:1px 6px" ${s.currentIndex === 0 ? 'disabled' : ''} title="Previous match">◀</button>
              <button type="button" class="btn btn-ghost btn-sm" id="ed-autofix-next" style="padding:1px 6px" ${s.currentIndex === total - 1 ? 'disabled' : ''} title="Next match">▶</button>
            </div>
          ` : ''}

          ${isProtected ? `
            <span class="version-badge version-badge-secondary" style="font-size:11px" title="Protected dependency or toolchain">
              <span class="material-symbols-outlined" style="font-size:13px">shield</span> Protected (Cannot Mutate)
            </span>
          ` : ''}

          ${isApplied ? `
            <span class="version-badge version-badge-success" style="font-size:11px">
              <span class="material-symbols-outlined" style="font-size:13px">check</span> Applied
            </span>
          ` : ''}
        </div>

        <div class="editor-autofix-diff">
          <span class="diff-line-old" title="Current line in editor">${esc(match.oldLine.trim())}</span>
          <span class="material-symbols-outlined" style="font-size:13px;color:var(--text-secondary)">arrow_forward</span>
          <span class="diff-line-new" title="Replacement after update">${esc(match.newLine.trim())}</span>
        </div>

        <div class="editor-autofix-actions">
          <button type="button" class="btn btn-primary btn-sm" id="ed-apply-fix-btn" ${isProtected || isApplied ? 'disabled' : ''}>
            <span class="material-symbols-outlined" style="font-size:15px">done_all</span> Apply Update
          </button>

          ${total > 1 ? `
            <button type="button" class="btn btn-secondary btn-sm" id="ed-apply-all-fixes-btn" title="Update all ${total} matching lines in this file">
              Apply All (${total})
            </button>
          ` : ''}

          <button type="button" class="btn btn-ghost btn-sm" id="ed-undo-fix-btn" style="${s.hasUndo ? '' : 'display:none'}" title="Undo changes made in this file">
            <span class="material-symbols-outlined" style="font-size:15px">undo</span> Undo
          </button>

          <button type="button" class="btn btn-ghost btn-sm" id="ed-return-vm-btn" title="Return to Version Manager">
            <span class="material-symbols-outlined" style="font-size:15px">arrow_back</span> Version Manager
          </button>

          <button type="button" class="btn btn-ghost btn-sm btn-icon" id="ed-close-autofix-btn" title="Close Auto Fix banner">
            <span class="material-symbols-outlined" style="font-size:16px">close</span>
          </button>
        </div>
      </div>
    `;

    // Bind banner buttons
    host.querySelector('#ed-autofix-prev')?.addEventListener('click', () => {
      if (s.currentIndex > 0) {
        s.currentIndex--;
        this.#renderAutoFixBar();
        this.#highlightFixLine(s.matches[s.currentIndex].lineNumber, s.fromVersion);
      }
    });

    host.querySelector('#ed-autofix-next')?.addEventListener('click', () => {
      if (s.currentIndex < s.matches.length - 1) {
        s.currentIndex++;
        this.#renderAutoFixBar();
        this.#highlightFixLine(s.matches[s.currentIndex].lineNumber, s.fromVersion);
      }
    });

    host.querySelector('#ed-apply-fix-btn')?.addEventListener('click', () => {
      this.#applySingleAutoFix();
    });

    host.querySelector('#ed-apply-all-fixes-btn')?.addEventListener('click', () => {
      this.#applyAllAutoFixes();
    });

    host.querySelector('#ed-undo-fix-btn')?.addEventListener('click', () => {
      this.#undoAutoFix();
    });

    host.querySelector('#ed-return-vm-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'version-manager' });
    });

    host.querySelector('#ed-close-autofix-btn')?.addEventListener('click', () => {
      this.#autoFixState = null;
      host.innerHTML = '';
      const nums = this.#container?.querySelector('#ed-line-nums');
      nums?.querySelectorAll('.editor-line-num.highlight-fix').forEach(el => el.classList.remove('highlight-fix'));
    });
  }

  /**
   * Applies the fix for the current match occurrence.
   */
  async #applySingleAutoFix() {
    if (!this.#autoFixState) return;
    const s = this.#autoFixState;
    const match = s.matches[s.currentIndex];
    if (!match || match.isProtected || match.applied) return;

    const tab = this.#tabs.get(s.path);
    if (!tab) return;

    // Backup for undo
    tab.undoStack.push(tab.content);
    if (tab.undoStack.length > 100) tab.undoStack.shift();

    const lines = tab.content.split('\n');
    if (lines[match.lineNumber - 1] !== undefined) {
      lines[match.lineNumber - 1] = match.newLine;
    }
    const newContent = lines.join('\n');
    tab.content = newContent;
    tab.dirty = false;

    const textarea = this.#container?.querySelector('#ed-textarea');
    if (textarea) {
      textarea.value = newContent;
      this.#updateLineNumbers(newContent, match.lineNumber);
    }

    match.applied = true;
    s.hasUndo = true;

    // Save tab to ProjectService immediately
    await this.saveTab(s.path);

    // Notify VersionManager to refresh status and scan results
    eventBus.emit('version:updated', { path: s.path, version: s.toVersion });

    toast.success('Line Updated & Saved', `${s.path.split('/').pop()}:L${match.lineNumber} updated to ${s.toVersion}`);

    // If more unapplied matches exist, advance to next match
    const nextUnapplied = s.matches.findIndex(m => !m.applied && !m.isProtected);
    if (nextUnapplied !== -1) {
      s.currentIndex = nextUnapplied;
    }

    this.#renderAutoFixBar();
    const cur = s.matches[s.currentIndex];
    if (cur) {
      this.#highlightFixLine(cur.lineNumber, cur.applied ? s.toVersion : s.fromVersion);
    }
  }

  /**
   * Applies all eligible matches in the file.
   */
  async #applyAllAutoFixes() {
    if (!this.#autoFixState) return;
    const s = this.#autoFixState;
    const tab = this.#tabs.get(s.path);
    if (!tab) return;

    tab.undoStack.push(tab.content);
    const lines = tab.content.split('\n');

    let count = 0;
    for (const match of s.matches) {
      if (!match.isProtected && !match.applied) {
        if (lines[match.lineNumber - 1] !== undefined) {
          lines[match.lineNumber - 1] = match.newLine;
          match.applied = true;
          count++;
        }
      }
    }

    const newContent = lines.join('\n');
    tab.content = newContent;
    tab.dirty = false;

    const textarea = this.#container?.querySelector('#ed-textarea');
    if (textarea) {
      textarea.value = newContent;
      this.#updateLineNumbers(newContent, s.matches[0]?.lineNumber || 1);
    }

    s.hasUndo = true;
    await this.saveTab(s.path);
    eventBus.emit('version:updated', { path: s.path, version: s.toVersion });

    toast.success('All Lines Updated & Saved', `${count} occurrence(s) in ${s.path.split('/').pop()} updated to ${s.toVersion}`);
    this.#renderAutoFixBar();
  }

  /**
   * Undoes the auto fix changes and restores backup content.
   */
  async #undoAutoFix() {
    if (!this.#autoFixState || !this.#autoFixState.backupContent) return;
    const s = this.#autoFixState;
    const tab = this.#tabs.get(s.path);
    if (!tab) return;

    tab.content = s.backupContent;
    tab.dirty = false;

    const textarea = this.#container?.querySelector('#ed-textarea');
    if (textarea) {
      textarea.value = tab.content;
      this.#updateLineNumbers(tab.content, 1);
    }

    for (const m of s.matches) {
      m.applied = false;
    }
    s.hasUndo = false;

    await this.saveTab(s.path);
    eventBus.emit('version:updated', { path: s.path, version: s.fromVersion });

    toast.info('Changes Reverted', `Restored ${s.path.split('/').pop()} to original version ${s.fromVersion}`);
    this.#renderAutoFixBar();
    const cur = s.matches[s.currentIndex];
    if (cur) {
      this.#highlightFixLine(cur.lineNumber, s.fromVersion);
    }
  }

  #escapeHtml(s) {
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }
}
