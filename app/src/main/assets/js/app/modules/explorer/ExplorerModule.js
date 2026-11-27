/**
 * ExplorerModule — project file-tree with CRUD, context menu, search, drag-awareness.
 */
import { eventBus } from '../../core/EventBus.js';
import { projectService } from '../storage/ProjectService.js';
import { consoleService } from '../storage/ConsoleService.js';
import { modal } from '../../ui/ModalService.js';
import { toast } from '../../ui/ToastService.js';
import { FileImportService } from './FileImportService.js';
import { ExplorerClipboard } from './ExplorerClipboard.js';

/** Build a nested tree structure from flat file paths. */
function buildTree(paths) {
  const root = { name: '', children: {}, type: 'folder', path: '' };
  paths.forEach(path => {
    const parts = path.split('/');
    let node = root;
    parts.forEach((part, i) => {
      if (!node.children[part]) {
        node.children[part] = {
          name: part,
          path: parts.slice(0, i + 1).join('/'),
          type: i === parts.length - 1 ? 'file' : 'folder',
          children: {},
        };
      }
      node = node.children[part];
    });
  });
  return root;
}

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

export class ExplorerModule {
  #container       = null;
  #tree            = null;
  #expandedSet     = new Set();
  #selected        = null;
  #selectedSet     = new Set();
  #lastSelectedPath = null;
  #destinationFolder = '';
  #searchTerm      = '';
  #globalEventsBound = false;
  #treeEventsBound = false;

  init(container) {
    this.#container = container;
    this.#render();
    this.#bindGlobalEvents();
  }

  #refreshTimer = null;

  destroy() {
    this.#treeEventsBound = false;
    if (this.#refreshTimer) {
      clearTimeout(this.#refreshTimer);
      this.#refreshTimer = null;
    }
  }

  #bindGlobalEvents() {
    if (this.#globalEventsBound) return;
    this.#globalEventsBound = true;

    eventBus.on('project:created',     () => this.refresh(true));
    eventBus.on('project:loaded',      () => this.refresh(true));
    eventBus.on('project:fileChanged', () => this.refresh(false));
    eventBus.on('project:fileDeleted', () => this.refresh(false));
    eventBus.on('project:closed',      () => { this.#tree = null; this.refresh(true); });
    eventBus.on('explorer:refresh',    () => this.refresh(true));

    // Global keyboard shortcuts for Explorer
    document.addEventListener('keydown', e => {
      // Only trigger if focus is inside explorer or not inside an input/textarea
      const activeTag = document.activeElement?.tagName?.toLowerCase();
      if (activeTag === 'input' || activeTag === 'textarea') return;

      if ((e.ctrlKey || e.metaKey) && e.key === 'c' && this.#selectedSet.size > 0) {
        e.preventDefault();
        this.#copySelected();
      } else if ((e.ctrlKey || e.metaKey) && e.key === 'x' && this.#selectedSet.size > 0) {
        e.preventDefault();
        this.#cutSelected();
      } else if ((e.ctrlKey || e.metaKey) && e.key === 'v' && ExplorerClipboard.hasItems()) {
        e.preventDefault();
        this.#pasteClipboard();
      } else if ((e.key === 'Delete' || e.key === 'Backspace') && (this.#selectedSet.size > 0 || this.#selected || this.#destinationFolder)) {
        e.preventDefault();
        this.#deleteSelected();
      }
    });
  }

  refresh(immediate = false) {
    if (immediate) {
      if (this.#refreshTimer) {
        clearTimeout(this.#refreshTimer);
        this.#refreshTimer = null;
      }
      this.#doRefresh();
      return;
    }
    if (this.#refreshTimer) clearTimeout(this.#refreshTimer);
    this.#refreshTimer = setTimeout(() => {
      this.#refreshTimer = null;
      this.#doRefresh();
    }, 40);
  }

  #doRefresh() {
    const proj = projectService.current;
    if (!proj) {
      this.#tree = null;
      this.#selected = null;
      this.#selectedSet.clear();
      this.#destinationFolder = '';
    } else {
      this.#tree = buildTree(Object.keys(proj.files || {}));
    }
    this.#renderTree();
    this.#updateClipboardBar();
    this.#updateDestIndicator();
  }

  #render() {
    this.#container.innerHTML = `
      <div class="explorer-panel" id="exp-panel">
        <div class="explorer-toolbar">
          <span class="explorer-toolbar-title">Explorer</span>
          <button class="btn btn-ghost btn-sm btn-icon" id="exp-new-file" title="New File">
            <span class="material-symbols-outlined" style="font-size:16px">add</span>
          </button>
          <button class="btn btn-ghost btn-sm btn-icon" id="exp-new-folder" title="New Folder">
            <span class="material-symbols-outlined" style="font-size:16px">create_new_folder</span>
          </button>
          <button class="btn btn-ghost btn-sm btn-icon" id="exp-import-file" title="Import File(s)">
            <span class="material-symbols-outlined" style="font-size:16px">upload_file</span>
          </button>
          <button class="btn btn-ghost btn-sm btn-icon" id="exp-import-folder" title="Import Directory">
            <span class="material-symbols-outlined" style="font-size:16px">drive_folder_upload</span>
          </button>
          <div style="width:1px;height:16px;background:var(--border);margin:0 2px"></div>
          <button class="btn btn-ghost btn-sm btn-icon" id="exp-copy" title="Copy (Ctrl+C)" disabled>
            <span class="material-symbols-outlined" style="font-size:16px">content_copy</span>
          </button>
          <button class="btn btn-ghost btn-sm btn-icon" id="exp-cut" title="Cut (Ctrl+X)" disabled>
            <span class="material-symbols-outlined" style="font-size:16px">content_cut</span>
          </button>
          <button class="btn btn-ghost btn-sm btn-icon" id="exp-paste" title="Paste (Ctrl+V)" disabled>
            <span class="material-symbols-outlined" style="font-size:16px">content_paste</span>
          </button>
          <button class="btn btn-ghost btn-sm btn-icon" id="exp-delete" title="Delete Selected (Del)" disabled style="color:var(--error, #ef4444)">
            <span class="material-symbols-outlined" style="font-size:16px">delete</span>
          </button>
          <div style="width:1px;height:16px;background:var(--border);margin:0 2px"></div>
          <button class="btn btn-ghost btn-sm btn-icon" id="exp-expand-all" title="Expand All">
            <span class="material-symbols-outlined" style="font-size:16px">unfold_more</span>
          </button>
          <button class="btn btn-ghost btn-sm btn-icon" id="exp-collapse-all" title="Collapse All">
            <span class="material-symbols-outlined" style="font-size:16px">unfold_less</span>
          </button>
        </div>

        <div class="explorer-dest-indicator" id="exp-dest-indicator">
          <span class="material-symbols-outlined" style="font-size:14px;color:var(--accent)">folder_special</span>
          <span style="overflow:hidden;text-overflow:ellipsis">Target: <strong id="exp-dest-label">Root (/)</strong></span>
        </div>

        <div class="explorer-clipboard-bar" id="exp-clipboard-bar" style="display:none">
          <div class="explorer-clipboard-info" id="exp-clipboard-info"></div>
          <div class="explorer-clipboard-actions">
            <button class="btn btn-primary btn-sm" id="exp-clipboard-paste-btn" style="padding:2px 8px;font-size:11px">Paste</button>
            <button class="btn btn-ghost btn-sm" id="exp-clipboard-clear-btn" style="padding:2px 8px;font-size:11px">Clear</button>
          </div>
        </div>

        <div class="explorer-search">
          <div class="search-input">
            <span class="material-symbols-outlined">search</span>
            <input id="exp-search" placeholder="Search files…" autocomplete="off" />
          </div>
        </div>
        <div class="explorer-tree" id="exp-tree" tabindex="0"></div>
      </div>`;

    this.#container.querySelector('#exp-new-file')?.addEventListener('click', () => this.#createFile(this.#destinationFolder));
    this.#container.querySelector('#exp-new-folder')?.addEventListener('click', () => this.#createFolder(this.#destinationFolder));
    this.#container.querySelector('#exp-import-file')?.addEventListener('click', () => this.#importFiles());
    this.#container.querySelector('#exp-import-folder')?.addEventListener('click', () => this.#importDirectory());
    this.#container.querySelector('#exp-copy')?.addEventListener('click', () => this.#copySelected());
    this.#container.querySelector('#exp-cut')?.addEventListener('click', () => this.#cutSelected());
    this.#container.querySelector('#exp-paste')?.addEventListener('click', () => this.#pasteClipboard());
    this.#container.querySelector('#exp-delete')?.addEventListener('click', () => this.#deleteSelected());
    this.#container.querySelector('#exp-expand-all')?.addEventListener('click', () => this.#expandAll());
    this.#container.querySelector('#exp-collapse-all')?.addEventListener('click', () => this.#collapseAll());

    this.#container.querySelector('#exp-clipboard-paste-btn')?.addEventListener('click', () => this.#pasteClipboard());
    this.#container.querySelector('#exp-clipboard-clear-btn')?.addEventListener('click', () => {
      ExplorerClipboard.clear();
      this.#updateClipboardBar();
      this.#renderTree();
    });

    this.#container.querySelector('#exp-search')?.addEventListener('input', e => {
      this.#searchTerm = e.target.value.toLowerCase();
      this.#renderTree();
    });

    this.#bindDragDrop();
    this.refresh();
  }

  #bindDragDrop() {
    const panel = this.#container.querySelector('#exp-panel');
    if (!panel) return;

    panel.addEventListener('dragover', e => {
      e.preventDefault();
      e.stopPropagation();
      panel.classList.add('drag-over');
    });

    panel.addEventListener('dragleave', e => {
      e.preventDefault();
      e.stopPropagation();
      if (!panel.contains(e.relatedTarget)) {
        panel.classList.remove('drag-over');
      }
    });

    panel.addEventListener('drop', async e => {
      e.preventDefault();
      e.stopPropagation();
      panel.classList.remove('drag-over');

      const targetNode = e.target.closest('.tree-node');
      let targetFolder = this.#destinationFolder;
      if (targetNode) {
        targetFolder = targetNode.dataset.type === 'folder'
          ? targetNode.dataset.path
          : (targetNode.dataset.path.includes('/') ? targetNode.dataset.path.slice(0, targetNode.dataset.path.lastIndexOf('/')) : '');
      }

      await FileImportService.parseAndImportDrop(e.dataTransfer, targetFolder);
      this.refresh();
    });
  }

  #updateDestIndicator() {
    const label = this.#container.querySelector('#exp-dest-label');
    if (label) {
      label.textContent = this.#destinationFolder ? `/${this.#destinationFolder}` : 'Root (/)';
    }
  }

  #updateClipboardBar() {
    const bar = this.#container.querySelector('#exp-clipboard-bar');
    const info = this.#container.querySelector('#exp-clipboard-info');
    const pasteBtn = this.#container.querySelector('#exp-paste');
    const copyBtn = this.#container.querySelector('#exp-copy');
    const cutBtn = this.#container.querySelector('#exp-cut');

    const hasSelection = this.#selectedSet.size > 0;
    if (copyBtn) copyBtn.disabled = !hasSelection;
    if (cutBtn) cutBtn.disabled = !hasSelection;

    const deleteBtn = this.#container.querySelector('#exp-delete');
    const canDelete = hasSelection || !!this.#selected || !!this.#destinationFolder;
    if (deleteBtn) deleteBtn.disabled = !canDelete;

    if (ExplorerClipboard.hasItems()) {
      if (bar) bar.style.display = 'flex';
      if (pasteBtn) pasteBtn.disabled = false;
      const count = ExplorerClipboard.items.length;
      const modeText = ExplorerClipboard.mode === 'cut' ? 'Cut' : 'Copied';
      if (info) {
        info.innerHTML = `
          <span class="material-symbols-outlined" style="font-size:15px;color:var(--accent)">
            ${ExplorerClipboard.mode === 'cut' ? 'content_cut' : 'content_copy'}
          </span>
          <span>${count} item(s) ${modeText}</span>
        `;
      }
    } else {
      if (bar) bar.style.display = 'none';
      if (pasteBtn) pasteBtn.disabled = true;
    }
  }

  async #importFiles(destFolder = this.#destinationFolder) {
    await FileImportService.selectAndImportFiles(destFolder);
    this.refresh();
  }

  async #importDirectory(destFolder = this.#destinationFolder) {
    await FileImportService.selectAndImportDirectory(destFolder);
    this.refresh();
  }

  #copySelected() {
    const paths = Array.from(this.#selectedSet);
    if (!paths.length && this.#selected) paths.push(this.#selected);
    if (!paths.length) return;
    ExplorerClipboard.copy(paths);
    this.#updateClipboardBar();
    this.#renderTree();
  }

  #cutSelected() {
    const paths = Array.from(this.#selectedSet);
    if (!paths.length && this.#selected) paths.push(this.#selected);
    if (!paths.length) return;
    ExplorerClipboard.cut(paths);
    this.#updateClipboardBar();
    this.#renderTree();
  }

  async #pasteClipboard(destFolder = this.#destinationFolder) {
    if (!ExplorerClipboard.hasItems()) return;
    await ExplorerClipboard.paste(destFolder);
    this.#updateClipboardBar();
    this.refresh();
  }

  #renderTree() {
    const treeEl = this.#container.querySelector('#exp-tree');
    if (!treeEl) return;

    if (!this.#tree) {
      treeEl.innerHTML = `
        <div class="explorer-empty">
          <span class="material-symbols-outlined">folder_off</span>
          <div class="explorer-empty-text">No project open.<br>Create or import a project to begin.</div>
          <div style="display:flex; flex-direction:column; gap:8px; margin-top:16px; width:100%; max-width:200px;">
            <button type="button" class="btn btn-primary btn-sm" id="exp-empty-gh-btn" style="justify-content:center;">
              <span class="material-symbols-outlined" style="font-size:16px;">hub</span>Import GitHub Repo
            </button>
            <button type="button" class="btn btn-secondary btn-sm" id="exp-empty-zip-btn" style="justify-content:center;">
              <span class="material-symbols-outlined" style="font-size:16px;">folder_zip</span>Import Local ZIP
            </button>
          </div>
        </div>`;
      treeEl.querySelector('#exp-empty-gh-btn')?.addEventListener('click', () =>
        eventBus.emit('action:importGitHub'));
      treeEl.querySelector('#exp-empty-zip-btn')?.addEventListener('click', () =>
        eventBus.emit('action:importZip'));
      return;
    }

    const paths = Object.keys(projectService.current?.files || {});
    if (!paths.length) {
      treeEl.innerHTML = `
        <div class="explorer-empty">
          <span class="material-symbols-outlined">folder_open</span>
          <div class="explorer-empty-text">Project is empty.<br>Import files or generate template to get started.</div>
          <div style="display:flex; flex-direction:column; gap:8px; margin-top:16px; width:100%; max-width:200px;">
            <button type="button" class="btn btn-primary btn-sm" id="exp-empty-import-file-btn" style="justify-content:center;">
              <span class="material-symbols-outlined" style="font-size:16px;">upload_file</span>Import Files
            </button>
            <button type="button" class="btn btn-secondary btn-sm" id="exp-empty-import-folder-btn" style="justify-content:center;">
              <span class="material-symbols-outlined" style="font-size:16px;">drive_folder_upload</span>Import Directory
            </button>
          </div>
        </div>`;
      treeEl.querySelector('#exp-empty-import-file-btn')?.addEventListener('click', () => this.#importFiles());
      treeEl.querySelector('#exp-empty-import-folder-btn')?.addEventListener('click', () => this.#importDirectory());
      return;
    }

    treeEl.innerHTML = this.#renderNode(this.#tree, 0);
    this.#bindTreeEvents(treeEl);
  }

  #renderNode(node, depth) {
    if (depth === 0) {
      return Object.values(node.children)
        .sort((a, b) => {
          if (a.type !== b.type) return a.type === 'folder' ? -1 : 1;
          return a.name.localeCompare(b.name);
        })
        .map(child => this.#renderNode(child, 1))
        .join('');
    }

    const indent  = (depth - 1) * 16;
    const isOpen  = this.#expandedSet.has(node.path);
    const search  = this.#searchTerm;
    const matches = !search || node.name.toLowerCase().includes(search);

    if (!matches && node.type === 'file') return '';

    const ic = node.type === 'folder'
      ? `<span class="material-symbols-outlined tree-icon folder${isOpen ? ' folder-open' : ''}" style="font-variation-settings:'FILL' 1,'wght' 400">${isOpen ? 'folder_open' : 'folder'}</span>`
      : `<span class="material-symbols-outlined tree-icon ${iconClass(node.name)}" style="font-size:15px;font-variation-settings:'FILL' 1,'wght' 300">${fileIcon(node.name)}</span>`;

    const isSelected = this.#selectedSet.has(node.path) || this.#selected === node.path;
    const isMultiSelected = this.#selectedSet.size > 1 && this.#selectedSet.has(node.path);
    const isCut = ExplorerClipboard.mode === 'cut' && ExplorerClipboard.items.some(it => it.sourcePath === node.path);

    let classes = 'tree-node';
    if (node.type === 'file') classes += ' leaf';
    if (isOpen) classes += ' open';
    if (isSelected) classes += ' selected';
    if (isMultiSelected) classes += ' multi-selected';
    if (isCut) classes += ' cut-pending';

    let html = `
      <div class="${classes}"
           data-path="${node.path}" data-type="${node.type}" style="padding-left:${indent + 8}px"
           tabindex="0" role="${node.type === 'folder' ? 'treeitem' : 'button'}" aria-label="${node.name}"
           ${node.type === 'folder' ? `aria-expanded="${isOpen}"` : ''}>
        <span class="tree-toggle" data-action="toggle-folder" title="${isOpen ? 'Collapse' : 'Expand'}">
          <span class="material-symbols-outlined" style="font-size:14px">chevron_right</span>
        </span>
        ${ic}
        <span class="tree-name">${this.#searchTerm ? this.#highlight(node.name, this.#searchTerm) : node.name}</span>
        <div class="tree-node-actions">
          ${node.type === 'folder' ? `
          <button type="button" class="tree-node-action" data-action="new-file-in" data-path="${node.path}" title="New File Inside">
            <span class="material-symbols-outlined">add</span>
          </button>
          <button type="button" class="tree-node-action" data-action="new-folder-in" data-path="${node.path}" title="New Folder Inside">
            <span class="material-symbols-outlined">create_new_folder</span>
          </button>
          ` : ''}
          <button type="button" class="tree-node-action tree-node-action-delete" data-action="delete-node" data-path="${node.path}" data-type="${node.type}" title="Delete ${node.type}">
            <span class="material-symbols-outlined">delete</span>
          </button>
        </div>
      </div>`;

    if (node.type === 'folder') {
      const children = Object.values(node.children)
        .sort((a, b) => { if (a.type !== b.type) return a.type==='folder'?-1:1; return a.name.localeCompare(b.name); })
        .map(c => this.#renderNode(c, depth + 1))
        .join('');
      html += `<div class="tree-children" style="${isOpen ? 'display:block' : 'display:none'}">${children}</div>`;
    }
    return html;
  }

  #highlight(text, term) {
    const idx = text.toLowerCase().indexOf(term);
    if (idx === -1) return text;
    return text.slice(0, idx) +
      `<mark style="background:var(--accent-glow);color:var(--accent);border-radius:2px">${text.slice(idx, idx + term.length)}</mark>` +
      text.slice(idx + term.length);
  }

  #getAllVisiblePaths() {
    const treeEl = this.#container.querySelector('#exp-tree');
    if (!treeEl) return [];
    return Array.from(treeEl.querySelectorAll('.tree-node')).map(n => n.dataset.path);
  }

  #bindTreeEvents(treeEl) {
    if (treeEl._treeEventsBound) return;
    treeEl._treeEventsBound = true;

    treeEl.addEventListener('click', e => {
      // 1. Quick action button clicks on tree node
      const delBtn = e.target.closest('[data-action="delete-node"]');
      if (delBtn) {
        e.preventDefault();
        e.stopPropagation();
        this.#deletePath(delBtn.dataset.path, delBtn.dataset.type);
        return;
      }

      const newFileBtn = e.target.closest('[data-action="new-file-in"]');
      if (newFileBtn) {
        e.preventDefault();
        e.stopPropagation();
        this.#createFile(newFileBtn.dataset.path);
        return;
      }

      const newFolderBtn = e.target.closest('[data-action="new-folder-in"]');
      if (newFolderBtn) {
        e.preventDefault();
        e.stopPropagation();
        this.#createFolder(newFolderBtn.dataset.path);
        return;
      }

      const node = e.target.closest('.tree-node');
      if (!node) {
        this.#destinationFolder = '';
        this.#updateDestIndicator();
        this.#updateClipboardBar();
        return;
      }

      const path = node.dataset.path;
      const type = node.dataset.type;

      // 2. Folder toggle button directly clicked
      if (type === 'folder' && (e.target.closest('.tree-toggle') || e.target.closest('[data-action="toggle-folder"]'))) {
        e.preventDefault();
        e.stopPropagation();
        this.#toggleFolder(path, node);
        this.#selected = path;
        this.#selectedSet.clear();
        this.#selectedSet.add(path);
        this.#destinationFolder = path;
        this.#updateDestIndicator();
        this.#updateClipboardBar();
        this.#updateSelectionVisuals();
        return;
      }

      // 3. Multi-select logic with Ctrl / Shift
      if (e.ctrlKey || e.metaKey) {
        if (this.#selectedSet.has(path)) {
          this.#selectedSet.delete(path);
          if (this.#selected === path) this.#selected = this.#selectedSet.values().next().value || null;
        } else {
          this.#selectedSet.add(path);
          this.#selected = path;
        }
      } else if (e.shiftKey && this.#lastSelectedPath) {
        const visiblePaths = this.#getAllVisiblePaths();
        const startIdx = visiblePaths.indexOf(this.#lastSelectedPath);
        const endIdx = visiblePaths.indexOf(path);
        if (startIdx !== -1 && endIdx !== -1) {
          const [min, max] = startIdx < endIdx ? [startIdx, endIdx] : [endIdx, startIdx];
          this.#selectedSet.clear();
          for (let i = min; i <= max; i++) {
            this.#selectedSet.add(visiblePaths[i]);
          }
          this.#selected = path;
        }
      } else {
        // Normal single select
        this.#selectedSet.clear();
        this.#selectedSet.add(path);
        this.#selected = path;
      }

      this.#lastSelectedPath = path;

      // Update target destination folder
      if (type === 'folder') {
        this.#destinationFolder = path;
      } else {
        this.#destinationFolder = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '';
      }
      this.#updateDestIndicator();
      this.#updateClipboardBar();

      if (type === 'folder') {
        // Toggle this specific folder independently
        if (!e.ctrlKey && !e.metaKey && !e.shiftKey) {
          this.#toggleFolder(path, node);
        }
        this.#updateSelectionVisuals();
      } else {
        // Open file in editor if not multi-selecting
        if (!e.ctrlKey && !e.metaKey && !e.shiftKey) {
          const content = projectService.getFile(path) || '';
          const proj    = projectService.current;
          const fileData = {
            name: path.split('/').pop(),
            path,
            type: 'file',
            content,
            modifiedAt: proj?.updatedAt,
          };
          eventBus.emit('explorer:selected', fileData);
          eventBus.emit('editor:openFile', { path, content });
          eventBus.emit('router:navigate', { pageId: 'editor', params: { path, content } });
        } else {
          this.#updateSelectionVisuals();
        }
      }
    });

    treeEl.addEventListener('dblclick', e => {
      const node = e.target.closest('.tree-node[data-type="file"]');
      if (node) {
        const path    = node.dataset.path;
        const content = projectService.getFile(path) || '';
        eventBus.emit('editor:openFile', { path, content });
        eventBus.emit('router:navigate', { pageId: 'editor', params: { path, content } });
      }
    });

    treeEl.addEventListener('contextmenu', e => {
      e.preventDefault();
      const node = e.target.closest('.tree-node');
      if (node) {
        const path = node.dataset.path;
        if (!this.#selectedSet.has(path)) {
          this.#selectedSet.clear();
          this.#selectedSet.add(path);
          this.#selected = path;
          this.#lastSelectedPath = path;
          this.#renderTree();
        }
        this.#showContextMenu(e.clientX, e.clientY, path, node.dataset.type);
      }
    });

    treeEl.addEventListener('keydown', e => {
      const node = e.target.closest('.tree-node');
      if (!node) return;
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); node.click(); }
      if (e.key === 'F2') { e.preventDefault(); this.#renameNode(node.dataset.path); }
      if (e.key === 'Delete') { e.preventDefault(); this.#deleteSelected(); }
    });
  }

  #showContextMenu(x, y, path, type) {
    const menu = document.getElementById('context-menu');
    if (!menu) return;
    const isFile = type === 'file';
    const isGradle = isFile && (path.endsWith('.gradle') || path.endsWith('.gradle.kts') || path.includes('gradle'));
    const targetFolder = isFile ? (path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '') : path;
    const canPaste = ExplorerClipboard.hasItems();
    const selCount = this.#selectedSet.size;

    menu.innerHTML = `
      ${isFile ? `<div class="context-item" data-action="open" data-path="${path}">
        <span class="material-symbols-outlined">open_in_new</span>Open in Editor</div>` : ''}
      ${isGradle ? `<div class="context-item" data-action="smart-gradle" data-path="${path}" style="color:var(--accent);font-weight:600">
        <span class="material-symbols-outlined" style="color:var(--accent)">analytics</span>Smart Gradle Analysis</div>` : ''}

      <div class="context-separator"></div>

      <div class="context-item" data-action="copy" data-path="${path}">
        <span class="material-symbols-outlined">content_copy</span>Copy ${selCount > 1 ? `(${selCount})` : ''}
        <span class="context-item-shortcut">Ctrl+C</span></div>
      <div class="context-item" data-action="cut" data-path="${path}">
        <span class="material-symbols-outlined">content_cut</span>Cut ${selCount > 1 ? `(${selCount})` : ''}
        <span class="context-item-shortcut">Ctrl+X</span></div>
      <div class="context-item${!canPaste ? ' disabled' : ''}" data-action="paste" data-path="${targetFolder}" style="${!canPaste ? 'opacity:0.5;pointer-events:none' : ''}">
        <span class="material-symbols-outlined">content_paste</span>Paste Here
        <span class="context-item-shortcut">Ctrl+V</span></div>

      <div class="context-separator"></div>

      <div class="context-item" data-action="import-files-here" data-path="${targetFolder}">
        <span class="material-symbols-outlined">upload_file</span>Import Files Here…</div>
      <div class="context-item" data-action="import-folder-here" data-path="${targetFolder}">
        <span class="material-symbols-outlined">drive_folder_upload</span>Import Folder Here…</div>
      <div class="context-item" data-action="set-dest" data-path="${targetFolder}">
        <span class="material-symbols-outlined">folder_special</span>Set as Target Directory</div>

      <div class="context-separator"></div>

      <div class="context-item" data-action="rename" data-path="${path}">
        <span class="material-symbols-outlined">edit</span>Rename
        <span class="context-item-shortcut">F2</span></div>
      <div class="context-item" data-action="duplicate" data-path="${path}">
        <span class="material-symbols-outlined">file_copy</span>Duplicate</div>
      ${!isFile ? `<div class="context-item" data-action="new-file" data-path="${path}">
        <span class="material-symbols-outlined">add</span>New File</div>
      <div class="context-item" data-action="new-folder" data-path="${path}">
        <span class="material-symbols-outlined">create_new_folder</span>New Folder</div>` : ''}
      <div class="context-separator"></div>
      <div class="context-item danger" data-action="delete" data-path="${path}">
        <span class="material-symbols-outlined">delete</span>Delete ${selCount > 1 ? `(${selCount})` : ''}
        <span class="context-item-shortcut">Del</span></div>
    `;

    menu.style.cssText = `display:block;left:${Math.min(x, window.innerWidth-220)}px;top:${Math.min(y, window.innerHeight-320)}px`;

    const handler = e => {
      const item = e.target.closest('.context-item');
      if (item && !item.classList.contains('disabled')) {
        const { action, path: p } = item.dataset;
        this.#handleContextAction(action, p || path);
      }
      menu.style.display = 'none';
      document.removeEventListener('click', handler, true);
    };
    setTimeout(() => document.addEventListener('click', handler, true), 50);
  }

  #handleContextAction(action, path) {
    switch (action) {
      case 'open':         this.#openFile(path); break;
      case 'smart-gradle':
        eventBus.emit('router:navigate', { pageId: 'analyzer', filePath: path });
        break;
      case 'copy':         this.#copySelected(); break;
      case 'cut':          this.#cutSelected(); break;
      case 'paste':        this.#pasteClipboard(path); break;
      case 'import-files-here':
        this.#importFiles(path); break;
      case 'import-folder-here':
        this.#importDirectory(path); break;
      case 'set-dest':
        this.#destinationFolder = path;
        this.#updateDestIndicator();
        toast.info('Target Directory Set', path ? `/${path}` : 'Root (/)');
        break;
      case 'rename':       this.#renameNode(path); break;
      case 'duplicate':    this.#duplicateNode(path); break;
      case 'delete':
        if (this.#selectedSet.size > 1 && this.#selectedSet.has(path)) {
          this.#deleteSelected();
        } else {
          this.#deletePath(path);
        }
        break;
      case 'new-file':     this.#createFile(path); break;
      case 'new-folder':   this.#createFolder(path); break;
    }
  }

  #openFile(path) {
    const content = projectService.getFile(path) || '';
    eventBus.emit('editor:openFile', { path, content });
    eventBus.emit('router:navigate', { pageId: 'editor' });
  }

  async #createFile(parentPath = '') {
    const name = await modal.prompt({ icon: 'add', title: 'New File', label: 'File name', placeholder: 'MyClass.java' });
    if (!name) return;
    const path = parentPath ? `${parentPath}/${name}` : name;
    await projectService.setFile(path, '');
    if (parentPath) this.#expandedSet.add(parentPath);
    consoleService.info(`Created file: ${path}`);
    toast.success('File created', path);
    this.refresh();
  }

  async #createFolder(parentPath = '') {
    const name = await modal.prompt({ icon: 'create_new_folder', title: 'New Folder', label: 'Folder name', placeholder: 'myfolder' });
    if (!name) return;
    const path     = parentPath ? `${parentPath}/${name}` : name;
    const sentinel = `${path}/.gitkeep`;
    await projectService.setFile(sentinel, '');
    if (parentPath) this.#expandedSet.add(parentPath);
    consoleService.info(`Created folder: ${path}`);
    toast.success('Folder created', path);
    this.refresh();
  }

  async #renameNode(path) {
    const oldName = path.split('/').pop();
    const newName = await modal.prompt({ icon: 'edit', title: 'Rename', label: 'New name', defaultValue: oldName });
    if (!newName || newName === oldName) return;

    const proj    = projectService.current;
    if (!proj) return;
    const prefix  = path.slice(0, path.length - oldName.length);

    const affected = Object.keys(proj.files).filter(p => p === path || p.startsWith(path + '/'));
    for (const oldPath of affected) {
      const newPath    = prefix + newName + oldPath.slice(path.length);
      const content    = projectService.getFile(oldPath) || '';
      await projectService.setFile(newPath, content);
      await projectService.deleteFile(oldPath);
    }
    consoleService.info(`Renamed: ${oldName} → ${newName}`);
    toast.success('Renamed', `${oldName} → ${newName}`);
    this.refresh();
  }

  async #duplicateNode(path) {
    const name  = path.split('/').pop();
    const base  = name.includes('.') ? name.slice(0, name.lastIndexOf('.')) : name;
    const ext   = name.includes('.') ? name.slice(name.lastIndexOf('.')) : '';
    const dir   = path.slice(0, path.length - name.length - 1);
    const copy  = (dir ? dir + '/' : '') + base + '_copy' + ext;
    const content = projectService.getFile(path) || '';
    await projectService.setFile(copy, content);
    consoleService.info(`Duplicated: ${name}`);
    toast.success('Duplicated', copy);
    this.refresh();
  }

  #updateSelectionVisuals() {
    const treeEl = this.#container?.querySelector('#exp-tree');
    if (!treeEl) return;
    const isMulti = this.#selectedSet.size > 1;
    treeEl.querySelectorAll('.tree-node').forEach(nodeEl => {
      const p = nodeEl.dataset.path;
      const isSel = this.#selectedSet.has(p) || this.#selected === p;
      nodeEl.classList.toggle('selected', isSel);
      nodeEl.classList.toggle('multi-selected', isMulti && isSel);
    });
  }

  #toggleFolder(path, nodeEl) {
    const isNowOpen = !this.#expandedSet.has(path);
    if (isNowOpen) {
      this.#expandedSet.add(path);
    } else {
      this.#expandedSet.delete(path);
    }

    if (!nodeEl) {
      const treeEl = this.#container?.querySelector('#exp-tree');
      nodeEl = treeEl?.querySelector(`.tree-node[data-path="${CSS.escape(path)}"]`);
    }

    if (nodeEl) {
      nodeEl.classList.toggle('open', isNowOpen);
      nodeEl.setAttribute('aria-expanded', String(isNowOpen));
      const toggleSpan = nodeEl.querySelector('.tree-toggle');
      if (toggleSpan) {
        toggleSpan.title = isNowOpen ? 'Collapse' : 'Expand';
      }
      const icon = nodeEl.querySelector('.tree-icon.folder, .tree-icon.folder-open');
      if (icon) {
        icon.className = `material-symbols-outlined tree-icon folder${isNowOpen ? ' folder-open' : ''}`;
        icon.textContent = isNowOpen ? 'folder_open' : 'folder';
      }
      const childrenEl = nodeEl.nextElementSibling;
      if (childrenEl && childrenEl.classList.contains('tree-children')) {
        childrenEl.style.display = isNowOpen ? 'block' : 'none';
      }
    }
  }

  #escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  async #deletePath(path, type) {
    if (!path) return;
    const proj = projectService.current;
    if (!proj) return;

    const allFiles = proj.files || {};
    const isFolder = type === 'folder' || (!allFiles[path] && Object.keys(allFiles).some(k => k.startsWith(path + '/')));
    const itemName = path.split('/').pop() || path;

    // Collect exact files to delete
    const toDelete = [];
    if (allFiles[path]) {
      toDelete.push(path);
    }
    const prefix = path.endsWith('/') ? path : path + '/';
    for (const k of Object.keys(allFiles)) {
      if (k.startsWith(prefix) && !toDelete.includes(k)) {
        toDelete.push(k);
      }
    }

    if (!toDelete.length && !isFolder) {
      toast.warn('Nothing to delete', `File not found: ${path}`);
      return;
    }

    let title, message;
    if (isFolder) {
      title = 'Delete Folder';
      const fileCountText = toDelete.length > 0 ? ` (${toDelete.length} file${toDelete.length > 1 ? 's' : ''})` : '';
      message = `Are you sure you want to delete folder "<strong>${this.#escapeHtml(itemName)}</strong>"${fileCountText}? All contents will be permanently deleted.`;
    } else {
      title = 'Delete File';
      message = `Are you sure you want to delete file "<strong>${this.#escapeHtml(itemName)}</strong>"? This action cannot be undone.`;
    }

    const ok = await modal.confirm({
      icon: 'delete',
      title,
      message,
      confirmLabel: 'Delete',
      danger: true
    });
    if (!ok) return;

    if (toDelete.length) {
      await projectService.deleteFiles(toDelete);
    }

    // Clean up internal selection and expanded states
    for (const p of toDelete) {
      this.#selectedSet.delete(p);
    }
    this.#selectedSet.delete(path);
    if (this.#selected === path || toDelete.includes(this.#selected)) {
      this.#selected = this.#selectedSet.values().next().value || null;
    }

    this.#expandedSet.delete(path);
    for (const expPath of Array.from(this.#expandedSet)) {
      if (expPath === path || expPath.startsWith(prefix)) {
        this.#expandedSet.delete(expPath);
      }
    }

    if (this.#destinationFolder === path || this.#destinationFolder.startsWith(prefix)) {
      this.#destinationFolder = '';
      this.#updateDestIndicator();
    }

    eventBus.emit('editor:filesDeleted', { paths: toDelete.length ? toDelete : [path] });
    consoleService.warn(`Deleted ${isFolder ? 'folder' : 'file'}: ${path} (${toDelete.length} file(s))`);
    toast.warn('Deleted', `${itemName} removed`);
    this.refresh();
  }

  async #deleteSelected() {
    const paths = Array.from(this.#selectedSet);
    if (!paths.length && this.#selected) paths.push(this.#selected);
    if (!paths.length && this.#destinationFolder) paths.push(this.#destinationFolder);
    if (!paths.length) {
      toast.info('No selection', 'Select a file or folder to delete');
      return;
    }

    if (paths.length === 1) {
      return this.#deletePath(paths[0]);
    }

    const proj = projectService.current;
    if (!proj) return;
    const allFiles = proj.files || {};
    const toDelete = [];

    for (const p of paths) {
      if (allFiles[p] && !toDelete.includes(p)) {
        toDelete.push(p);
      }
      const prefix = p.endsWith('/') ? p : p + '/';
      for (const k of Object.keys(allFiles)) {
        if (k.startsWith(prefix) && !toDelete.includes(k)) {
          toDelete.push(k);
        }
      }
    }

    const ok = await modal.confirm({
      icon: 'delete',
      title: `Delete ${paths.length} Items`,
      message: `Are you sure you want to delete ${paths.length} selected items (${toDelete.length} total file${toDelete.length > 1 ? 's' : ''})? This action cannot be undone.`,
      confirmLabel: 'Delete All',
      danger: true
    });
    if (!ok) return;

    if (toDelete.length) {
      await projectService.deleteFiles(toDelete);
    }

    this.#selectedSet.clear();
    this.#selected = null;
    this.#destinationFolder = '';
    this.#updateDestIndicator();

    for (const p of paths) {
      this.#expandedSet.delete(p);
      const prefix = p.endsWith('/') ? p : p + '/';
      for (const expPath of Array.from(this.#expandedSet)) {
        if (expPath.startsWith(prefix)) this.#expandedSet.delete(expPath);
      }
    }

    eventBus.emit('editor:filesDeleted', { paths: toDelete });
    consoleService.warn(`Deleted ${paths.length} items (${toDelete.length} files)`);
    toast.warn('Deleted', `${paths.length} item(s) removed`);
    this.refresh();
  }

  #expandAll() {
    const treeEl = this.#container.querySelector('#exp-tree');
    treeEl?.querySelectorAll('.tree-node:not(.leaf)').forEach(n => {
      this.#expandedSet.add(n.dataset.path);
    });
    this.#renderTree();
  }

  #collapseAll() {
    this.#expandedSet.clear();
    this.#renderTree();
  }
}
