/**
 * ProjectService — CRUD for projects stored in IndexedDB.
 * A "project" contains metadata + a flat file-path→content map.
 */
import { storage } from './StorageService.js';
import { eventBus } from '../../core/EventBus.js';

export class ProjectService {
  #current       = null;
  #unsaved       = false;
  #lastBackup    = null;
  #saveDebounceTimer = null;

  // ── Accessors ──────────────────────────────────────────────────────────

  get current() { return this.#current; }
  set current(val) { this.#current = val; }

  get lastBackup() { return this.#lastBackup; }

  hasUnsavedChanges() { return this.#unsaved; }

  getFile(path) { return this.#current?.files?.[path] ?? null; }

  // ── Create ─────────────────────────────────────────────────────────────

  async create(meta) {
    const project = {
      id:          `proj_${Date.now()}`,
      name:        meta.name        || 'Untitled',
      projectType: meta.projectType || (meta.template === 'pure-java-library' ? 'java-library' : (meta.template?.includes('app') ? 'app' : 'library')),
      packageName: meta.packageName || 'com.example.library',
      version:     meta.version     || '1.0.0',
      minSdk:      meta.minSdk      || '21',
      targetSdk:   meta.targetSdk   || '34',
      compileSdk:  meta.compileSdk  || '34',
      language:    meta.language    || 'java',
      license:     meta.license     || 'Apache-2.0',
      repoName:    meta.repoName    || '',
      company:     meta.company     || '',
      author:      meta.author      || '',
      description: meta.description || '',
      files:       {},
      createdAt:   Date.now(),
      updatedAt:   Date.now(),
    };
    await storage.put('projects', project);
    this.#current = project;
    this.#unsaved = false;
    await this.#addRecent(project.id);
    eventBus.emit('project:loaded', project);
    return project;
  }

  // ── Load ───────────────────────────────────────────────────────────────

  async load(id) {
    const project = await storage.get('projects', id);
    if (!project) throw new Error(`Project not found: ${id}`);
    this.#current = project;
    this.#unsaved = false;
    await this.#addRecent(id);
    eventBus.emit('project:loaded', project);
    return project;
  }

  /** Load the most recent project (if any). */
  async loadLast() {
    const recentIds = await storage.getSetting('recentProjects', []);
    if (recentIds.length) {
      try { return await this.load(recentIds[0]); } catch { /* try all */ }
    }
    try {
      const all = await storage.getAll('projects');
      if (all && all.length > 0) {
        all.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
        return await this.load(all[0].id);
      }
    } catch { /* ignore */ }
  }

  /** Close (unload) the current project without deleting it. */
  close() {
    this.#current = null;
    this.#unsaved = false;
    eventBus.emit('project:loaded', null);
  }

  // ── Save ───────────────────────────────────────────────────────────────

  async save(project = this.#current) {
    if (!project) return;
    if (this.#saveDebounceTimer) {
      clearTimeout(this.#saveDebounceTimer);
      this.#saveDebounceTimer = null;
    }
    project.updatedAt = Date.now();
    await storage.put('projects', project);
    this.#unsaved = false;
    eventBus.emit('project:saved', project);
  }

  // ── File operations ────────────────────────────────────────────────────

  async setFile(path, content, immediate = false) {
    if (!this.#current) {
      this.#current = {
        id:          `proj_${Date.now()}`,
        name:        'Android Project',
        projectType: 'library',
        files:       {},
        createdAt:   Date.now(),
        updatedAt:   Date.now(),
      };
    }
    this.#current.files[path] = content;
    this.#current.updatedAt   = Date.now();
    this.#unsaved = true;

    if (immediate) {
      if (this.#saveDebounceTimer) {
        clearTimeout(this.#saveDebounceTimer);
        this.#saveDebounceTimer = null;
      }
      await storage.put('projects', this.#current);
    } else {
      if (this.#saveDebounceTimer) clearTimeout(this.#saveDebounceTimer);
      this.#saveDebounceTimer = setTimeout(async () => {
        this.#saveDebounceTimer = null;
        if (this.#current) {
          try { await storage.put('projects', this.#current); }
          catch (e) { console.warn('[ProjectService] Debounced save failed:', e); }
        }
      }, 150);
    }

    eventBus.emit('project:fileChanged', { path, content });
    return this.#current;
  }

  /**
   * Batch update/add multiple files in a single storage transaction.
   * @param {Record<string, string>} fileMap - { [path]: content }
   */
  async setFiles(fileMap) {
    if (!this.#current) throw new Error('No project loaded');
    const entries = Object.entries(fileMap);
    if (!entries.length) return this.#current;
    for (const [path, content] of entries) {
      this.#current.files[path] = content;
    }
    this.#current.updatedAt = Date.now();
    this.#unsaved = true;
    await storage.put('projects', this.#current);
    eventBus.emit('project:fileChanged', { batch: true, count: entries.length });
    return this.#current;
  }

  async deleteFile(path) {
    if (!this.#current) return;
    delete this.#current.files[path];
    this.#current.updatedAt = Date.now();
    await storage.put('projects', this.#current);
    eventBus.emit('project:fileDeleted', { path });
  }

  /**
   * Batch delete multiple files in a single storage transaction.
   * @param {string[]} paths
   */
  async deleteFiles(paths) {
    if (!this.#current || !paths.length) return;
    for (const path of paths) {
      delete this.#current.files[path];
    }
    this.#current.updatedAt = Date.now();
    await storage.put('projects', this.#current);
    eventBus.emit('project:fileDeleted', { batch: true, count: paths.length });
  }

  async renameFile(oldPath, newPath) {
    if (!this.#current) return;
    const content = this.#current.files[oldPath] ?? '';
    delete this.#current.files[oldPath];
    this.#current.files[newPath] = content;
    this.#current.updatedAt = Date.now();
    await storage.put('projects', this.#current);
    eventBus.emit('project:fileRenamed', { oldPath, newPath });
  }

  // ── Backup & Rollback ──────────────────────────────────────────────────

  /**
   * Take a full snapshot of all project files before mutations.
   * @param {string} [label='mutation']
   * @returns {{ label: string, timestamp: string, files: Record<string, string> } | null}
   */
  createBackup(label = 'mutation') {
    if (!this.#current || !this.#current.files) return null;
    const filesSnapshot = {};
    for (const [k, v] of Object.entries(this.#current.files)) {
      filesSnapshot[k] = typeof v === 'string' ? v : (v instanceof Uint8Array ? new Uint8Array(v) : v);
    }
    this.#lastBackup = {
      label,
      timestamp: new Date().toISOString(),
      files: filesSnapshot
    };
    return this.#lastBackup;
  }

  /**
   * Restore all project files from the last snapshot.
   * @returns {Promise<boolean>}
   */
  async restoreBackup() {
    if (!this.#lastBackup || !this.#current) return false;
    this.#current.files = {};
    for (const [k, v] of Object.entries(this.#lastBackup.files)) {
      this.#current.files[k] = typeof v === 'string' ? v : (v instanceof Uint8Array ? new Uint8Array(v) : v);
    }
    this.#current.updatedAt = Date.now();
    await storage.put('projects', this.#current);
    eventBus.emit('project:fileChanged', { batch: true, restoredFromBackup: true });
    return true;
  }

  // ── List ───────────────────────────────────────────────────────────────

  async getAll() {
    return storage.getAll('projects');
  }

  async getRecent(limit = 5) {
    const ids     = await storage.getSetting('recentProjects', []);
    const projects = [];
    for (const id of ids.slice(0, limit)) {
      const p = await storage.get('projects', id);
      if (p) projects.push(p);
    }
    return projects;
  }

  // ── Delete ─────────────────────────────────────────────────────────────

  async deleteProject(id) {
    await storage.delete('projects', id);
    if (this.#current?.id === id) this.close();
    // Remove from recents
    const ids = await storage.getSetting('recentProjects', []);
    await storage.setSetting('recentProjects', ids.filter(x => x !== id));
  }

  // ── Helpers ────────────────────────────────────────────────────────────

  async #addRecent(id) {
    const ids = await storage.getSetting('recentProjects', []);
    const updated = [id, ...ids.filter(x => x !== id)].slice(0, 10);
    await storage.setSetting('recentProjects', updated);
  }
}

export const projectService = new ProjectService();
