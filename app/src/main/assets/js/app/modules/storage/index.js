/**
 * Storage Module Entry Point
 * Exports IndexedDB storage, project service, and runtime console persistence.
 */

export { StorageService, storage, storage as storageService } from './StorageService.js';
export { ProjectService, projectService } from './ProjectService.js';
export { ConsoleService, consoleService } from './ConsoleService.js';
