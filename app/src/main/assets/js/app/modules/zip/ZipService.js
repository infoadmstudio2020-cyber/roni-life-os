/**
 * ZipService — export project to ZIP and import from ZIP using JSZip.
 *
 * Binary file handling:
 *   Files stored as Uint8Array (e.g. gradle-wrapper.jar) are added as binary.
 *   'gradlew' receives unixPermissions: 0o755 so it is executable on Linux/macOS
 *   without a manual chmod step, preventing GitHub Actions exit-code 126.
 */
import { projectService } from '../storage/ProjectService.js';
import { consoleService } from '../storage/ConsoleService.js';
import { eventBus } from '../../core/EventBus.js';
import { toast } from '../../ui/ToastService.js';
import { storage } from '../storage/StorageService.js';
import { ProjectAnalyzer } from '../analyzer/ProjectAnalyzer.js';
import { getGradleWrapperFiles } from '../generator/GradleWrapperAssets.js';
import { WebViewBridge } from '../../core/WebViewBridge.js';

// Files that must be stored as binary (Uint8Array) in the ZIP.
const BINARY_FILES = new Set(['gradle/wrapper/gradle-wrapper.jar']);

// Files that need Unix executable permission 0755 in the ZIP.
const EXECUTABLE_FILES = new Set(['gradlew']);

export class ZipService {
  /**
   * Export the current project as a downloadable ZIP.
   * @returns {Promise<void>}
   */
  async exportProject() {
    const project = projectService.current;
    if (!project) { toast.error('No project', 'Open a project first'); return; }

    if (typeof JSZip === 'undefined') {
      toast.error('JSZip not loaded', 'Check your internet connection');
      return;
    }

    // ── Gradle Wrapper pre-flight validation ────────────────────────────────
    const files = project.files || {};
    const REQUIRED_WRAPPER = [
      'gradlew',
      'gradlew.bat',
      'gradle/wrapper/gradle-wrapper.jar',
      'gradle/wrapper/gradle-wrapper.properties',
    ];
    const missingWrapper = REQUIRED_WRAPPER.filter(f => !(f in files));
    if (missingWrapper.length > 0) {
      consoleService.warn(`Gradle Wrapper files missing: ${missingWrapper.join(', ')} — auto-generating complete Gradle 8.7 wrapper...`);
      const wrapperFiles = getGradleWrapperFiles('8.7');
      for (const [wPath, wContent] of Object.entries(wrapperFiles)) {
        if (!files[wPath]) {
          files[wPath] = wContent;
        }
      }
      try {
        await projectService.save(project);
      } catch (e) {
        // continue export
      }
    }

    eventBus.emit('action:exporting');
    consoleService.info(`Exporting project "${project.name}"…`);

    try {
      const zip   = new JSZip();
      let   count = 0;

      for (const [path, content] of Object.entries(files)) {
        if (BINARY_FILES.has(path)) {
          // Binary file — content is a Uint8Array
          if (content instanceof Uint8Array) {
            zip.file(path, content, { binary: true });
          } else if (typeof content === 'string') {
            // Fallback: decode base64 string if somehow stored as string
            try {
              const binaryStr = atob(content);
              const bytes = new Uint8Array(binaryStr.length);
              for (let i = 0; i < binaryStr.length; i++) bytes[i] = binaryStr.charCodeAt(i);
              zip.file(path, bytes, { binary: true });
            } catch {
              zip.file(path, content);
            }
          } else {
            zip.file(path, content ?? '');
          }
        } else if (EXECUTABLE_FILES.has(path)) {
          // gradlew: LF line endings + Unix executable permission 0755
          const lf = (content ?? '').replace(/\r\n/g, '\n').replace(/\r/g, '\n');
          zip.file(path, lf, { unixPermissions: 0o755 });
        } else {
          zip.file(path, content ?? '');
        }
        count++;
      }

      consoleService.info(`  Packed ${count} files (gradle-wrapper.jar as binary, gradlew as 0755)`);

      const blob     = await zip.generateAsync({
        type: 'blob',
        compression: 'DEFLATE',
        compressionOptions: { level: 6 },
        platform: 'UNIX',  // preserve unixPermissions in ZIP metadata
      });
      const safeName = (project.name || 'project').replace(/[^a-zA-Z0-9_-]/g, '_');
      const filename = `${safeName}-${project.version || '1.0.0'}.zip`;

      const downloadResult = await WebViewBridge.downloadFile(filename, blob, 'application/zip');
      if (!downloadResult.success) {
        throw new Error(downloadResult.error || 'Failed triggering download in this environment');
      }

      // Save export record
      const sizeKb = (blob.size / 1024).toFixed(1);
      await storage.put('exports', {
        id:        `exp_${Date.now()}`,
        projectId: project.id,
        name:      project.name,
        filename,
        sizeKb,
        files:     count,
        exportedAt: Date.now(),
      });

      consoleService.success(`Exported: ${filename} (${sizeKb} KB) via ${downloadResult.method}`);
      toast.success('Export Complete', `${filename} (${sizeKb} KB)`);
      eventBus.emit('action:exported');

    } catch (err) {
      consoleService.error(`Export failed: ${err.message}`);
      toast.error('Export Failed', err.message);
    }
  }

  /**
   * Export custom files map directly as a downloadable ZIP.
   * @param {Record<string, string|Uint8Array>} files
   * @param {string} filename
   * @returns {Promise<void>}
   */
  async exportCustomFiles(files = {}, filename = 'project_export.zip') {
    if (typeof JSZip === 'undefined') {
      toast.error('JSZip not loaded', 'Check your internet connection');
      return;
    }

    try {
      const zip = new JSZip();
      for (const [path, content] of Object.entries(files)) {
        if (content instanceof Uint8Array) {
          zip.file(path, content, { binary: true });
        } else {
          zip.file(path, content ?? '');
        }
      }

      const blob = await zip.generateAsync({
        type: 'blob',
        compression: 'DEFLATE',
        compressionOptions: { level: 6 }
      });

      const downloadResult = await WebViewBridge.downloadFile(filename, blob, 'application/zip');
      if (!downloadResult.success) {
        throw new Error(downloadResult.error || 'Failed triggering download in this environment');
      }

      toast.success('Export Complete', `${filename} (${downloadResult.method})`);
    } catch (err) {
      toast.error('Export Failed', err.message);
    }
  }

  /**
   * Import a project from a ZIP file.
   * @param {File} file
   * @returns {Promise<object|null>}
   */
  async importFromZip(file) {
    if (typeof JSZip === 'undefined') {
      toast.error('JSZip not loaded', 'Check your internet connection');
      return null;
    }

    consoleService.info(`Importing ZIP: ${file.name}`);

    try {
      const zip     = await JSZip.loadAsync(file);
      const entries = Object.entries(zip.files).filter(([, f]) => !f.dir);
      const files   = {};

      // 1. Filter out junk and directory entries
      const validEntries = entries.filter(([p]) => {
        const norm = p.replace(/\\/g, '/');
        return !norm.startsWith('__MACOSX/') &&
               !norm.includes('/.DS_Store') &&
               norm !== '.DS_Store' &&
               !norm.includes('/._') &&
               !norm.startsWith('._') &&
               !norm.endsWith('/');
      });

      const rawPaths = validEntries.map(([p]) => p.replace(/\\/g, '/'));

      // 2. Identify the true Android project root prefix
      let prefixToStrip = '';
      const rootIndicatorFiles = ['settings.gradle', 'settings.gradle.kts', 'build.gradle', 'build.gradle.kts', 'gradlew'];

      // Check if project root is already at the top level
      const hasTopLevelIndicator = rawPaths.some(p => rootIndicatorFiles.includes(p));

      if (!hasTopLevelIndicator) {
        // Find paths containing root indicators
        const indicatorPaths = rawPaths.filter(p => {
          const parts = p.split('/');
          const filename = parts[parts.length - 1];
          return rootIndicatorFiles.includes(filename);
        });

        if (indicatorPaths.length > 0) {
          // Sort by path depth (shallowest first)
          indicatorPaths.sort((a, b) => a.split('/').length - b.split('/').length);

          for (const ip of indicatorPaths) {
            const parts = ip.split('/');
            parts.pop(); // remove indicator filename
            const candidate = parts.join('/') + '/';
            // Verify candidate contains settings.gradle or build.gradle
            const hasSettings = rawPaths.some(p => p === `${candidate}settings.gradle` || p === `${candidate}settings.gradle.kts`);
            const hasBuild = rawPaths.some(p => p === `${candidate}build.gradle` || p === `${candidate}build.gradle.kts`);
            const hasGradlew = rawPaths.some(p => p === `${candidate}gradlew`);

            if (hasSettings || (hasBuild && hasGradlew) || hasBuild) {
              prefixToStrip = candidate;
              break;
            }
          }
        }

        // Fallback: common directory prefix if all files are inside a single folder
        if (!prefixToStrip) {
          const firstParts = rawPaths.map(p => p.split('/')[0]);
          if (firstParts.length > 0 && firstParts.every(part => part === firstParts[0])) {
            prefixToStrip = firstParts[0] + '/';
          }
        }
      }

      // 3. Extract and normalize files
      for (const [rawPath, zipFile] of validEntries) {
        const norm = rawPath.replace(/\\/g, '/');
        let cleanPath = norm;
        if (prefixToStrip && cleanPath.startsWith(prefixToStrip)) {
          cleanPath = cleanPath.slice(prefixToStrip.length);
        } else if (prefixToStrip && !cleanPath.includes('/')) {
          // Keep top-level files (e.g. root README or LICENSE outside subfolder)
          cleanPath = norm;
        }
        cleanPath = cleanPath.replace(/^\/+/, '');
        if (!cleanPath) continue;

        const isBinary = BINARY_FILES.has(cleanPath) ||
          /\.(jar|aar|so|png|jpg|jpeg|webp|bin|keystore|jks)$/i.test(cleanPath);

        if (isBinary) {
          files[cleanPath] = await zipFile.async('uint8array');
        } else {
          files[cleanPath] = await zipFile.async('string');
        }
      }

      // Deep project analysis on imported files
      const analysis = ProjectAnalyzer.analyze(files);
      const name = analysis.rootProjectName || file.name.replace(/\.zip$/i, '');
      const primary = analysis.primaryModule || {};

      const project = await projectService.create({
        name,
        projectType: analysis.projectType,
        packageName: analysis.detectedPackageName || primary.namespace || primary.applicationId || 'com.example.project',
        version:     analysis.publishing?.version || primary.versionName || '1.0.0',
        repoName:    analysis.detectedRepoName || '',
        author:      analysis.detectedAuthor || '',
        description: `Imported from ${file.name} (${analysis.projectType})`,
        compileSdk:  primary.compileSdk ? parseInt(primary.compileSdk, 10) : 34,
        minSdk:      primary.minSdk ? parseInt(primary.minSdk, 10) : 21,
        targetSdk:   primary.targetSdk ? parseInt(primary.targetSdk, 10) : 34,
        jdkRequirement: analysis.jdkRequirement || '17',
      });

      // Batch save all imported files into project in a single transaction
      await projectService.setFiles(files);

      consoleService.success(`Imported: ${name} (${Object.keys(files).length} files, type: ${analysis.projectType})`);
      toast.success('Import Complete', `${name} — ${Object.keys(files).length} files (${analysis.projectType})`);
      eventBus.emit('router:navigate', { pageId: 'analyzer' });
      return project;

    } catch (err) {
      consoleService.error(`Import failed: ${err.message}`);
      toast.error('Import Failed', err.message);
      return null;
    }
  }

  /**
   * Show a file-picker and import a ZIP.
   * Utilizes WebViewBridge for DOM attachment and Android WebChromeClient compatibility.
   */
  pickAndImport() {
    WebViewBridge.openFilePicker('.zip,application/zip', async (file) => {
      if (file) await this.importFromZip(file);
    });
  }
}

export const zipService = new ZipService();
