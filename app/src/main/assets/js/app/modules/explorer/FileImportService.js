/**
 * FileImportService — handles Single/Multiple File Import and Recursive Directory Import.
 * Preserves nested directory structure, relative file paths, text/binary integrity,
 * and detects duplicates with interactive conflict resolution.
 */
import { projectService } from '../storage/ProjectService.js';
import { consoleService } from '../storage/ConsoleService.js';
import { toast } from '../../ui/ToastService.js';
import { ConflictResolverModal } from './ConflictResolverModal.js';

const BINARY_EXTENSIONS = new Set([
  'png', 'jpg', 'jpeg', 'gif', 'webp', 'ico', 'svgz',
  'jar', 'aar', 'dex', 'so', 'class', 'zip', 'tar', 'gz', 'apk', 'aab', 'pdf'
]);

export class FileImportService {
  /**
   * Check if a file extension represents binary data.
   * @param {string} filename
   * @returns {boolean}
   */
  static isBinaryFile(filename) {
    const ext = filename.split('.').pop()?.toLowerCase();
    return BINARY_EXTENSIONS.has(ext);
  }

  /**
   * Read file content safely without corruption.
   * Text is read as UTF-8 string; binary is read as Data URL.
   * @param {File} file
   * @returns {Promise<string>}
   */
  static async readFileContent(file) {
    if (this.isBinaryFile(file.name)) {
      return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(reader.error || new Error('Failed to read binary file'));
        reader.readAsDataURL(file);
      });
    }

    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = () => reject(reader.error || new Error('Failed to read text file'));
      reader.readAsText(file, 'UTF-8');
    });
  }

  /**
   * Sanitize relative path to prevent directory traversal or null characters.
   * Resolves relative '.' and '..' segments cleanly.
   * @param {string} path
   * @returns {string}
   */
  static sanitizePath(path) {
    const rawSegments = path
      .replace(/\\/g, '/')
      .replace(/\0/g, '')
      .split('/');
    const stack = [];
    for (const seg of rawSegments) {
      if (!seg || seg === '.') continue;
      if (seg === '..') {
        if (stack.length > 0) stack.pop();
      } else {
        stack.push(seg);
      }
    }
    return stack.join('/');
  }

  /**
   * Compute auto-renamed path for 'keep-both' option without collision.
   * e.g., 'src/main/Test.java' -> 'src/main/Test (1).java'
   * @param {string} path
   * @param {Set<string>|Record<string, any>} existingPaths
   * @returns {string}
   */
  static generateUniquePath(path, existingPaths) {
    const hasPath = p => existingPaths instanceof Set ? existingPaths.has(p) : (p in existingPaths);
    if (!hasPath(path)) return path;

    const lastSlash = path.lastIndexOf('/');
    const dir = lastSlash !== -1 ? path.slice(0, lastSlash) : '';
    const file = lastSlash !== -1 ? path.slice(lastSlash + 1) : path;

    const dotIndex = file.lastIndexOf('.');
    const base = dotIndex > 0 ? file.slice(0, dotIndex) : file;
    const ext = dotIndex > 0 ? file.slice(dotIndex) : '';

    let counter = 1;
    let candidate = '';
    do {
      const newFile = `${base} (${counter})${ext}`;
      candidate = dir ? `${dir}/${newFile}` : newFile;
      counter++;
    } while (hasPath(candidate));

    return candidate;
  }

  /**
   * Import multiple files using file input picker.
   * @param {string} [destinationFolder='']
   * @returns {Promise<{imported: number, skipped: number, total: number}>}
   */
  static async selectAndImportFiles(destinationFolder = '') {
    if (!projectService.current) {
      toast.error('No Project Open', 'Please create or load a project before importing files.');
      return { imported: 0, skipped: 0, total: 0 };
    }

    // Modern showOpenFilePicker with fallback
    if (window.showOpenFilePicker && typeof window.showOpenFilePicker === 'function') {
      try {
        const fileHandles = await window.showOpenFilePicker({
          multiple: true,
          excludeAcceptAllOption: false,
        });
        const files = [];
        for (const handle of fileHandles) {
          const file = await handle.getFile();
          files.push({ file, relPath: file.name });
        }
        return await this.processIncomingFiles(files, destinationFolder);
      } catch (err) {
        if (err.name === 'AbortError') return { imported: 0, skipped: 0, total: 0 };
        consoleService.warn(`showOpenFilePicker failed (${err.message}), falling back to input element.`);
      }
    }

    // Fallback to standard input element
    return new Promise(resolve => {
      const input = document.createElement('input');
      input.type = 'file';
      input.multiple = true;
      input.style.display = 'none';

      input.onchange = async () => {
        try {
          const files = Array.from(input.files || []).map(file => ({
            file,
            relPath: file.name,
          }));
          const result = await this.processIncomingFiles(files, destinationFolder);
          resolve(result);
        } catch (e) {
          toast.error('Import Failed', e.message);
          resolve({ imported: 0, skipped: 0, total: 0 });
        } finally {
          input.remove();
        }
      };

      document.body.appendChild(input);
      input.click();
    });
  }

  /**
   * Import entire directory recursively.
   * Tries File System Access API first, then falls back to input[webkitdirectory].
   * @param {string} [destinationFolder='']
   * @returns {Promise<{imported: number, skipped: number, total: number}>}
   */
  static async selectAndImportDirectory(destinationFolder = '') {
    if (!projectService.current) {
      toast.error('No Project Open', 'Please create or load a project before importing directories.');
      return { imported: 0, skipped: 0, total: 0 };
    }

    // Try window.showDirectoryPicker first if supported
    if (window.showDirectoryPicker && typeof window.showDirectoryPicker === 'function') {
      try {
        const dirHandle = await window.showDirectoryPicker();
        const files = [];
        await this.#traverseDirectoryHandle(dirHandle, dirHandle.name, files);
        return await this.processIncomingFiles(files, destinationFolder);
      } catch (err) {
        if (err.name === 'AbortError') return { imported: 0, skipped: 0, total: 0 };
        consoleService.warn(`showDirectoryPicker failed (${err.message}), falling back to webkitdirectory.`);
      }
    }

    // Fallback to input[webkitdirectory]
    return new Promise(resolve => {
      const input = document.createElement('input');
      input.type = 'file';
      input.webkitdirectory = true;
      input.directory = true;
      input.multiple = true;
      input.style.display = 'none';

      input.onchange = async () => {
        try {
          const files = Array.from(input.files || []).map(file => {
            const relPath = file.webkitRelativePath || file.name;
            return { file, relPath };
          });
          const result = await this.processIncomingFiles(files, destinationFolder);
          resolve(result);
        } catch (e) {
          toast.error('Directory Import Failed', e.message);
          resolve({ imported: 0, skipped: 0, total: 0 });
        } finally {
          input.remove();
        }
      };

      document.body.appendChild(input);
      input.click();
    });
  }

  /**
   * Recursively traverse modern FileSystemDirectoryHandle.
   */
  static async #traverseDirectoryHandle(dirHandle, currentPath, accumulator) {
    for await (const [name, handle] of dirHandle.entries()) {
      const nextPath = `${currentPath}/${name}`;
      if (handle.kind === 'file') {
        const file = await handle.getFile();
        accumulator.push({ file, relPath: nextPath });
      } else if (handle.kind === 'directory') {
        await this.#traverseDirectoryHandle(handle, nextPath, accumulator);
      }
    }
  }

  /**
   * Parse drag-and-drop items (supports dropped folders recursively via webkitGetAsEntry).
   * @param {DataTransfer} dataTransfer
   * @param {string} [destinationFolder='']
   * @returns {Promise<{imported: number, skipped: number, total: number}>}
   */
  static async parseAndImportDrop(dataTransfer, destinationFolder = '') {
    if (!projectService.current) {
      toast.error('No Project Open', 'Please create or load a project before dropping files.');
      return { imported: 0, skipped: 0, total: 0 };
    }

    const items = dataTransfer.items;
    const fileEntries = [];

    if (items && items.length > 0) {
      for (let i = 0; i < items.length; i++) {
        const item = items[i];
        if (typeof item.webkitGetAsEntry === 'function') {
          const entry = item.webkitGetAsEntry();
          if (entry) {
            await this.#traverseWebkitEntry(entry, '', fileEntries);
          }
        } else if (item.kind === 'file') {
          const file = item.getAsFile();
          if (file) fileEntries.push({ file, relPath: file.name });
        }
      }
    } else if (dataTransfer.files && dataTransfer.files.length > 0) {
      for (const file of Array.from(dataTransfer.files)) {
        fileEntries.push({ file, relPath: file.name });
      }
    }

    return await this.processIncomingFiles(fileEntries, destinationFolder);
  }

  /**
   * Recursively read FileSystemEntry / DirectoryEntry from drag-and-drop.
   */
  static async #traverseWebkitEntry(entry, parentPath, accumulator) {
    const currentPath = parentPath ? `${parentPath}/${entry.name}` : entry.name;
    if (entry.isFile) {
      await new Promise(resolve => {
        entry.file(file => {
          accumulator.push({ file, relPath: currentPath });
          resolve();
        }, () => resolve());
      });
    } else if (entry.isDirectory) {
      const reader = entry.createReader();
      const readAllEntries = async () => {
        return new Promise(resolve => {
          reader.readEntries(async entries => {
            if (!entries.length) {
              resolve();
            } else {
              for (const child of entries) {
                await this.#traverseWebkitEntry(child, currentPath, accumulator);
              }
              await readAllEntries();
              resolve();
            }
          }, () => resolve());
        });
      };
      await readAllEntries();
    }
  }

  /**
   * Process and write incoming files with duplicate and identical content collision handling.
   * @param {Array<{file?: File, content?: string, relPath: string}>} incomingItems
   * @param {string} destinationFolder
   * @returns {Promise<{imported: number, skipped: number, total: number}>}
   */
  static async processIncomingFiles(incomingItems, destinationFolder = '') {
    if (!incomingItems || !incomingItems.length) {
      return { imported: 0, skipped: 0, total: 0 };
    }

    const project = projectService.current;
    if (!project) {
      throw new Error('No active project to receive imported files.');
    }

    const existingFiles = project.files || {};
    const sanitizedDest = destinationFolder ? this.sanitizePath(destinationFolder) : '';

    // First, resolve final target paths
    const queue = [];
    for (const item of incomingItems) {
      const sanitizedRel = this.sanitizePath(item.relPath);
      if (!sanitizedRel) continue;
      const targetPath = sanitizedDest ? `${sanitizedDest}/${sanitizedRel}` : sanitizedRel;
      queue.push({
        ...item,
        targetPath,
      });
    }

    if (!queue.length) return { imported: 0, skipped: 0, total: 0 };

    // Identify conflicts
    const conflicts = [];
    const nonConflicts = [];

    for (const item of queue) {
      if (item.targetPath in existingFiles) {
        conflicts.push(item);
      } else {
        nonConflicts.push(item);
      }
    }

    const filesToWrite = {};
    const activePathsInSession = new Set(Object.keys(existingFiles));
    let importedCount = 0;
    let skippedCount = 0;

    // Process non-conflicting files
    for (const item of nonConflicts) {
      const content = item.content !== undefined ? item.content : await this.readFileContent(item.file);
      filesToWrite[item.targetPath] = content;
      activePathsInSession.add(item.targetPath);
      importedCount++;
    }

    // Process conflicts with interactive user resolution
    let autoChoice = null; // remembers choice if applyToAll is set

    for (let i = 0; i < conflicts.length; i++) {
      const item = conflicts[i];
      const incomingContent = item.content !== undefined ? item.content : await this.readFileContent(item.file);
      const existingContent = existingFiles[item.targetPath];

      let choice = autoChoice;
      if (!choice) {
        const resolution = await ConflictResolverModal.resolve({
          targetPath: item.targetPath,
          incomingContent,
          existingContent,
          conflictIndex: i + 1,
          totalConflicts: conflicts.length,
        });

        if (resolution.action === 'cancel') {
          consoleService.warn(`File import cancelled by user at ${item.targetPath}`);
          toast.info('Import Cancelled', `Cancelled after processing ${importedCount} file(s).`);
          break;
        }

        if (resolution.applyToAll) {
          autoChoice = resolution.action;
        }
        choice = resolution.action;
      }

      switch (choice) {
        case 'replace':
          filesToWrite[item.targetPath] = incomingContent;
          importedCount++;
          break;
        case 'skip':
          skippedCount++;
          break;
        case 'keep-both': {
          const uniquePath = this.generateUniquePath(item.targetPath, activePathsInSession);
          filesToWrite[uniquePath] = incomingContent;
          activePathsInSession.add(uniquePath);
          importedCount++;
          break;
        }
      }
    }

    // Commit to IndexedDB in one batch
    if (Object.keys(filesToWrite).length > 0) {
      await projectService.setFiles(filesToWrite);
      consoleService.success(`Imported ${importedCount} file(s) into project (${skippedCount} skipped)`);
      toast.success('Import Complete', `Successfully added ${importedCount} file(s).`);
    } else if (skippedCount > 0) {
      toast.info('Import Skipped', `${skippedCount} conflicting file(s) were skipped.`);
    }

    return {
      imported: importedCount,
      skipped: skippedCount,
      total: queue.length,
    };
  }
}
