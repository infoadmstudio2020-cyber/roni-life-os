/**
 * ExplorerClipboard — handles multi-item Copy/Cut/Paste operations in File Explorer
 * with destination folder targeting and automatic conflict resolution.
 */
import { projectService } from '../storage/ProjectService.js';
import { consoleService } from '../storage/ConsoleService.js';
import { toast } from '../../ui/ToastService.js';
import { FileImportService } from './FileImportService.js';

export class ExplorerClipboard {
  /** @type {'copy'|'cut'|null} */
  static mode = null;
  /** @type {Array<{sourcePath: string, type: 'file'|'folder', relName: string, files: Array<{relPath: string, content: string}>}>} */
  static items = [];

  /**
   * Check if clipboard has items.
   */
  static hasItems() {
    return this.mode !== null && this.items.length > 0;
  }

  /**
   * Copy one or multiple paths.
   * @param {string[]} paths
   */
  static copy(paths) {
    this.#populateClipboard(paths, 'copy');
    toast.info('Copied to Clipboard', `${this.items.length} item(s) ready to paste.`);
  }

  /**
   * Cut one or multiple paths.
   * @param {string[]} paths
   */
  static cut(paths) {
    this.#populateClipboard(paths, 'cut');
    toast.info('Cut to Clipboard', `${this.items.length} item(s) ready to move.`);
  }

  /**
   * Populate clipboard items from active project files.
   */
  static #populateClipboard(paths, mode) {
    const project = projectService.current;
    if (!project) return;
    const allFiles = project.files || {};

    this.mode = mode;
    this.items = [];

    // Filter out redundant nested paths if a parent folder is already selected
    const sortedPaths = [...new Set(paths)].sort((a, b) => a.length - b.length);
    const topPaths = [];
    for (const p of sortedPaths) {
      if (!topPaths.some(top => p === top || p.startsWith(top + '/'))) {
        topPaths.push(p);
      }
    }

    for (const p of topPaths) {
      const isFile = p in allFiles;
      const relName = p.split('/').pop();

      if (isFile) {
        this.items.push({
          sourcePath: p,
          type: 'file',
          relName,
          files: [{ relPath: relName, content: allFiles[p] || '' }],
        });
      } else {
        // Folder: collect all descendant files
        const prefix = p + '/';
        const folderFiles = [];
        for (const [filePath, content] of Object.entries(allFiles)) {
          if (filePath.startsWith(prefix)) {
            const relUnderFolder = relName + '/' + filePath.slice(prefix.length);
            folderFiles.push({ relPath: relUnderFolder, content });
          }
        }
        this.items.push({
          sourcePath: p,
          type: 'folder',
          relName,
          files: folderFiles,
        });
      }
    }
  }

  /**
   * Paste clipboard items into the destination folder.
   * @param {string} destinationFolder
   * @returns {Promise<{pasted: number, skipped: number}>}
   */
  static async paste(destinationFolder = '') {
    if (!this.hasItems()) {
      toast.info('Clipboard Empty', 'No items copied or cut.');
      return { pasted: 0, skipped: 0 };
    }

    const project = projectService.current;
    if (!project) {
      toast.error('No Project Open', 'Open a project to paste files.');
      return { pasted: 0, skipped: 0 };
    }

    // Flatten all files to import
    const incomingItems = [];
    const sourcePathsToDelete = [];

    for (const item of this.items) {
      // Prevent pasting a folder into itself or its own subfolder
      if (item.type === 'folder' && (destinationFolder === item.sourcePath || destinationFolder.startsWith(item.sourcePath + '/'))) {
        toast.error('Invalid Paste Target', `Cannot paste folder "${item.relName}" into itself or its subfolder.`);
        continue;
      }

      for (const f of item.files) {
        incomingItems.push({
          relPath: f.relPath,
          content: f.content,
        });
      }

      if (this.mode === 'cut') {
        sourcePathsToDelete.push(item.sourcePath);
      }
    }

    if (!incomingItems.length) {
      return { pasted: 0, skipped: 0 };
    }

    const result = await FileImportService.processIncomingFiles(incomingItems, destinationFolder);

    // If cut mode was active and files were imported, delete originals
    if (this.mode === 'cut' && result.imported > 0) {
      const allFiles = project.files || {};
      const filesToRemove = [];
      for (const src of sourcePathsToDelete) {
        if (src in allFiles) {
          filesToRemove.push(src);
        } else {
          const prefix = src + '/';
          for (const k of Object.keys(allFiles)) {
            if (k.startsWith(prefix)) filesToRemove.push(k);
          }
        }
      }

      if (filesToRemove.length > 0) {
        await projectService.deleteFiles(filesToRemove);
      }
      this.clear();
    }

    return { pasted: result.imported, skipped: result.skipped };
  }

  /**
   * Clear clipboard state.
   */
  static clear() {
    this.mode = null;
    this.items = [];
  }
}
