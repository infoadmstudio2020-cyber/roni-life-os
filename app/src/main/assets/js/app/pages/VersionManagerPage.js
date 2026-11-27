/**
 * VersionManagerPage — Dedicated Project Version Update Manager.
 *
 * Supports:
 * - Version management for ANY Android project (active workspace, uploaded ZIP, or sample baseline)
 * - Arbitrary Target Version (e.g. 1.0.1 -> 1.0.4)
 * - Complete scanner across root & module build.gradle/kts, gradle.properties, settings.gradle,
 *   Java/Kotlin source files (Constants.java, LibraryInfo.java, BuildConfig),
 *   README.md, CHANGELOG.md, AndroidManifest.xml, package.json, publishing & JitPack configs, and workflows
 * - Strict third-party dependency and toolchain protection (AGP, Wrapper, SDKs, namespace, applicationId, AndroidX)
 * - File-by-file preview with line-level diffs
 * - Select All / Deselect All
 * - "Update Selected" (updates only selected files)
 * - "Update All" / "Update All Project Versions" (updates all detected files)
 * - Pre-update automatic backup & full rollback on failure
 * - Post-update automatic rescan, UI/state refresh, and consistency verification
 * - Export full updated project as ZIP archive
 */
import { projectService } from '../modules/storage/ProjectService.js';
import { VersionUpdateEngine } from '../modules/version/VersionUpdateEngine.js';
import { toast } from '../ui/ToastService.js';
import { modal } from '../ui/ModalService.js';
import { consoleService } from '../modules/storage/ConsoleService.js';
import { eventBus } from '../core/EventBus.js';
import { WebViewBridge } from '../core/WebViewBridge.js';

export class VersionManagerPage {
  #container = null;
  #engine = new VersionUpdateEngine();
  #scanReport = null;
  #consistencyReport = null;

  // Active files being inspected { [path]: string | Uint8Array }
  #files = {};
  #sourceType = 'active'; // 'active' | 'upload' | 'sample'
  #uploadedProjectName = '';
  #isCorruptedUpload = false;

  #currentVersion = '1.0.1';
  #targetVersion = '1.0.4';
  #versionDetectionResult = null;
  #activeTab = 'report'; // 'report' | 'consistency' | 'changelog'
  #versionUpdateListenerBound = false;
  #unsubs = [];

  async init(container) {
    this.#container = container;
    if (!this.#versionUpdateListenerBound) {
      this.#versionUpdateListenerBound = true;
      const unsub = eventBus.on('version:updated', async ({ path, version }) => {
        if (path && projectService.current?.files?.[path]) {
          this.#files[path] = projectService.current.files[path];
        }
        await this.#runScan(false);
        if (this.#container && document.body.contains(this.#container)) {
          this.#render();
        }
      });
      this.#unsubs.push(unsub);
    }
    await this.#loadInitialProject();
    await this.#runScan();
    this.#render();
  }

  destroy() {
    this.#unsubs.forEach(u => u());
    this.#unsubs = [];
    this.#versionUpdateListenerBound = false;
    if (this.#container) {
      this.#container.innerHTML = '';
      this.#container = null;
    }
  }

  /**
   * Load project files according to active source.
   */
  async #loadInitialProject() {
    const activeProject = projectService.current;
    if (activeProject?.files && Object.keys(activeProject.files).length > 0) {
      this.#sourceType = 'active';
      this.#files = { ...activeProject.files };
      this.#uploadedProjectName = activeProject.name || 'Active Project';
    } else {
      this.#sourceType = 'sample';
      this.#files = await this.#loadBaselineFiles();
      this.#uploadedProjectName = 'Android Library Project';
    }

    // Auto-detect version
    this.#autoDetectVersions();
  }

  /**
   * Auto-detect versions from current #files
   */
  #autoDetectVersions() {
    this.#versionDetectionResult = VersionUpdateEngine.detectVersion(this.#files);
    if (this.#versionDetectionResult.primaryVersion) {
      this.#currentVersion = this.#versionDetectionResult.primaryVersion;
      // If targetVersion is not set or equals current, set to suggested
      if (!this.#targetVersion || this.#targetVersion === this.#currentVersion) {
        this.#targetVersion = this.#versionDetectionResult.suggestedTarget || '1.0.4';
      }
    }
  }

  /**
   * Baseline files for demonstration or when no external project is active.
   * Includes Java source files (LibraryInfo.java, Constants.java) for comprehensive scanning.
   */
  async #loadBaselineFiles() {
    const files = {};
    files['settings.gradle'] = `include ':library', ':sample'\nrootProject.name = 'RONiAndroidLibrary'`;
    files['gradle.properties'] = `VERSION_NAME=1.0.1\nPOM_VERSION=1.0.1\nPOM_GROUP_ID=com.github.roni\nPOM_ARTIFACT_ID=android-library\norg.gradle.jvmargs=-Xmx2048m`;
    files['build.gradle'] = `plugins {\n  id 'com.android.library' version '8.2.0' apply false\n  id 'com.android.application' version '8.2.0' apply false\n  id 'org.jetbrains.kotlin.android' version '1.9.22' apply false\n}\n\next {\n  libraryVersion = '1.0.1'\n}`;
    files['library/build.gradle'] = `plugins {\n  id 'com.android.library'\n  id 'maven-publish'\n}\n\nandroid {\n  namespace 'com.roni.library'\n  compileSdk 34\n  defaultConfig {\n    minSdk 21\n    targetSdk 34\n    versionName "1.0.1"\n  }\n}\n\ndependencies {\n  implementation 'androidx.core:core:1.0.1'\n  implementation 'androidx.appcompat:appcompat:1.6.1'\n  implementation 'com.google.android.material:material:1.11.0'\n}`;
    files['sample/build.gradle'] = `plugins {\n  id 'com.android.application'\n}\n\nandroid {\n  namespace 'com.roni.sample'\n  compileSdk 34\n  defaultConfig {\n    applicationId 'com.roni.sample'\n    minSdk 21\n    versionName "1.0.1"\n  }\n}\n\ndependencies {\n  implementation 'com.github.roni:android-library:1.0.1'\n  implementation 'androidx.appcompat:appcompat:1.6.1'\n}`;
    files['library/src/main/java/com/roni/library/LibraryInfo.java'] = `package com.roni.library;\n\npublic final class LibraryInfo {\n    public static final String LIBRARY_NAME = "RONi Android Library";\n    public static final String VERSION = "1.0.1";\n    public static final String VERSION_NAME = "1.0.1";\n\n    public static String getVersion() {\n        return "1.0.1";\n    }\n}`;
    files['library/src/main/java/com/roni/library/Constants.java'] = `package com.roni.library;\n\npublic final class Constants {\n    public static final String API_VERSION = "1.0.1";\n}`;
    files['README.md'] = `# RONi Android Library v1.0.1\n\n[![Release](https://jitpack.io/v/roni/android-library.svg)](https://jitpack.io/#roni/android-library)\n\nLatest release: 1.0.1\n\n\`\`\`groovy\nimplementation 'com.github.roni:android-library:1.0.1'\n\`\`\``;
    files['CHANGELOG.md'] = `# Changelog\n\n## [1.0.1] - 2026-10-20\n- Initial release with Android library and sample app.\n- JitPack publishing support.`;
    files['library/src/main/AndroidManifest.xml'] = `<?xml version="1.0" encoding="utf-8"?>\n<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n    package="com.roni.library"\n    android:versionName="1.0.1">\n</manifest>`;
    files['.github/workflows/release.yml'] = `name: Release\non:\n  push:\n    tags:\n      - 'v1.0.1'\njobs:\n  publish:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v4\n      - name: Build Library 1.0.1\n        run: ./gradlew assembleRelease`;
    files['package.json'] = `{\n  "name": "android-library",\n  "version": "1.0.1"\n}`;
    files['version.json'] = `{\n  "name": "RONi Android Library",\n  "currentVersion": "1.0.1"\n}`;
    files['scripts/publish.sh'] = `#!/bin/bash\necho "Publishing RONi Android Library version 1.0.1..."\n./gradlew publishToMavenLocal`;
    return files;
  }

  /**
   * Run scanner on current active files with current inputs.
   */
  async #runScan(syncFromDom = true) {
    if (syncFromDom) {
      this.#syncInputsFromDOM();
    }
    this.#scanReport = VersionUpdateEngine.scan(this.#files, this.#currentVersion, this.#targetVersion);
  }

  /**
   * Sync private version fields directly from input fields if they exist in DOM.
   */
  #syncInputsFromDOM() {
    if (!this.#container) return;
    const currentInput = this.#container.querySelector('#vm-current-ver-input');
    const targetInput = this.#container.querySelector('#vm-target-ver-input');

    if (currentInput && currentInput.value.trim()) {
      this.#currentVersion = currentInput.value.trim();
    }
    if (targetInput && targetInput.value.trim()) {
      this.#targetVersion = targetInput.value.trim();
    }
  }

  /**
   * Handle user uploading a project ZIP archive.
   */
  async #handleZipUpload(file) {
    if (!file) return;

    if (typeof JSZip === 'undefined') {
      toast.error('JSZip Unavailable', 'JSZip library is not loaded. Please verify scripts.');
      return;
    }

    consoleService.info(`VersionManager: Reading uploaded ZIP "${file.name}"...`);

    try {
      const zip = new JSZip();
      let zipContent;

      try {
        zipContent = await zip.loadAsync(file);
      } catch (zipErr) {
        this.#isCorruptedUpload = true;
        toast.error('Corrupted ZIP Archive', `Failed to extract "${file.name}". The file is invalid, corrupted, or not a valid ZIP.`);
        consoleService.error(`VersionManager: Invalid ZIP: ${zipErr.message}`);
        this.#render();
        return;
      }

      this.#isCorruptedUpload = false;
      const extractedFiles = {};

      const entries = Object.keys(zipContent.files);
      for (const relativePath of entries) {
        const zipEntry = zipContent.files[relativePath];
        if (zipEntry.dir) continue;

        const normalizedPath = relativePath.replace(/^[^/]+\//, '');
        const targetPath = normalizedPath.length > 0 ? normalizedPath : relativePath;

        if (relativePath.endsWith('.jar') || relativePath.endsWith('.png') || relativePath.endsWith('.so')) {
          extractedFiles[targetPath] = await zipEntry.async('uint8array');
        } else {
          const text = await zipEntry.async('text');
          extractedFiles[targetPath] = text;
        }
      }

      if (Object.keys(extractedFiles).length === 0) {
        toast.error('Empty Archive', 'The provided ZIP archive contains no files.');
        return;
      }

      this.#files = extractedFiles;
      this.#sourceType = 'upload';
      this.#uploadedProjectName = file.name.replace(/\.zip$/i, '');

      this.#autoDetectVersions();
      await this.#runScan();
      this.#render();

      toast.success('Project Loaded', `Successfully loaded "${file.name}" (${Object.keys(extractedFiles).length} files). Current Version: ${this.#currentVersion}`);
      consoleService.success(`VersionManager: Imported ${Object.keys(extractedFiles).length} files. Current Version: ${this.#currentVersion}, Suggested Target: ${this.#targetVersion}`);
    } catch (err) {
      console.error('[VersionManager] ZIP import error:', err);
      toast.error('Import Failed', err.message);
    }
  }

  #render() {
    const container = this.#container;
    const r = this.#scanReport || { mismatchCount: 0, upToDateCount: 0, protectedSkippedCount: 0, ambiguousCount: 0, fileResults: [] };
    const hasMismatches = r.mismatchCount > 0;
    const det = this.#versionDetectionResult;

    container.innerHTML = `
      <div class="page-container version-page">
        <!-- Page Header -->
        <div class="page-header">
          <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:var(--sp-3)">
            <div>
              <h1 class="page-title">
                <span class="material-symbols-outlined">system_update</span>
                Project Version Manager
              </h1>
              <p class="page-subtitle">Current Project Version: <strong style="color:var(--accent)">${this.#currentVersion}</strong> | Target Version: <strong style="color:#22c55e">${this.#targetVersion}</strong></p>
            </div>
            <div class="vm-header-actions">
              <button type="button" class="btn btn-secondary" id="vm-scan-btn" title="Scan all project files for version references">
                <span class="material-symbols-outlined">search</span>Scan Versions
              </button>
              <button type="button" class="btn btn-secondary" id="vm-rollback-btn" ${!this.#engine.lastBackup ? 'disabled' : ''} title="Restore files from pre-update backup snapshot">
                <span class="material-symbols-outlined">undo</span>Rollback
              </button>
              <button type="button" class="btn btn-outline" id="vm-update-selected-btn" title="Update only currently checked files from ${this.#currentVersion} to ${this.#targetVersion}">
                <span class="material-symbols-outlined">done_all</span>Update Selected
              </button>
              <button type="button" class="btn btn-primary" id="vm-apply-btn" title="Update all detected project-version references from ${this.#currentVersion} to ${this.#targetVersion}">
                <span class="material-symbols-outlined">upgrade</span>Update All Project Versions
              </button>
              <button type="button" class="btn btn-secondary" id="vm-export-zip-btn" title="Export complete updated project as downloadable ZIP archive">
                <span class="material-symbols-outlined">download</span>Download Updated Project ZIP
              </button>
            </div>
          </div>
        </div>

        <!-- Project Source Selector & ZIP Upload Panel -->
        <div class="version-source-panel">
          <div class="version-source-header">
            <div style="display:flex;align-items:center;gap:var(--sp-2);flex-wrap:wrap">
              <span class="material-symbols-outlined" style="color:var(--accent)">folder_zip</span>
              <strong style="color:var(--text-primary)">Active Project:</strong>
              <span class="ref-tag" style="background:var(--bg-card);font-weight:600">${this.#uploadedProjectName}</span>
              <span class="version-badge version-badge-info">${Object.keys(this.#files).length} Files</span>
              <span class="version-badge version-badge-success">Current Version: ${this.#currentVersion}</span>
              ${this.#sourceType === 'active' ? `<span class="version-badge version-badge-info">Active Workspace</span>` : ''}
              ${this.#sourceType === 'upload' ? `<span class="version-badge version-badge-info">Imported Archive</span>` : ''}
            </div>

            <div style="display:flex;align-items:center;gap:var(--sp-2)">
              <a href="./RONiAndroidLibraryGenerator-v0.1.3.6.zip" download="RONiAndroidLibraryGenerator-v0.1.3.6.zip" class="btn btn-ghost btn-sm" id="vm-dl-release-btn" title="Download latest RONi Android Library Generator release package" style="text-decoration:none">
                <span class="material-symbols-outlined">download</span> Release ZIP
              </a>
              <label class="btn btn-secondary btn-sm" style="cursor:pointer">
                <span class="material-symbols-outlined">upload_file</span>
                Upload Android Project ZIP
                <input type="file" id="vm-zip-file-input" accept=".zip,application/zip" style="display:none" />
              </label>
              ${projectService.current ? `
                <button type="button" class="btn btn-ghost btn-sm" id="vm-switch-active-btn" title="Switch back to current active workspace project">
                  Use Active Workspace
                </button>
              ` : `
                <button type="button" class="btn btn-ghost btn-sm" id="vm-switch-sample-btn" title="Load sample Android library project">
                  Load Baseline Project
                </button>
              `}
            </div>
          </div>

          <!-- Drag and drop zone -->
          <div class="version-dropzone" id="vm-dropzone">
            <span class="material-symbols-outlined" style="font-size:32px;color:var(--text-muted)">cloud_upload</span>
            <div style="font-size:var(--fs-sm);font-weight:500;color:var(--text-primary)">
              Drop any Android Project ZIP here, or click to browse
            </div>
            <div style="font-size:var(--fs-xs);color:var(--text-muted)">
              Extracts and scans build.gradle/kts, gradle.properties, Java source files, manifest, README, CHANGELOG, and workflows
            </div>
          </div>

          ${this.#isCorruptedUpload ? `
            <div style="padding:var(--sp-3);background:rgba(239,68,68,0.1);border:1px solid rgba(239,68,68,0.3);border-radius:var(--radius-md);color:#ef4444;font-size:var(--fs-sm);display:flex;align-items:center;gap:var(--sp-2)">
              <span class="material-symbols-outlined">error</span>
              <span><strong>Error:</strong> The uploaded file could not be unzipped. It may be corrupted or not a valid ZIP file.</span>
            </div>
          ` : ''}
        </div>

        <!-- Version Input Control Card -->
        <div class="version-inputs-card">
          <!-- Current Version Input -->
          <div class="version-input-group">
            <div class="version-input-label">
              <span>Current Project Version</span>
              ${det?.hasMismatches ? `<span class="version-badge version-badge-warning" title="Multiple version candidates found across project files">Mismatch Detected</span>` : ''}
            </div>
            <div class="version-input-wrapper">
              <input type="text" id="vm-current-ver-input" class="input" value="${this.#esc(this.#currentVersion)}" placeholder="e.g. 1.0.1" />
              <button type="button" class="btn btn-secondary btn-sm" id="vm-autodetect-btn" title="Re-detect from project files">
                <span class="material-symbols-outlined">magic_button</span> Auto Detect
              </button>
            </div>
          </div>

          <!-- Arrow -->
          <div class="vm-input-arrow-wrap">
            <span class="material-symbols-outlined vm-input-arrow-icon">arrow_forward</span>
          </div>

          <!-- Target Version Input -->
          <div class="version-input-group">
            <div class="version-input-label">
              <span>Target Project Version</span>
              <span style="font-size:11px;color:var(--text-muted)">Editable (e.g. 1.0.4)</span>
            </div>
            <div class="version-input-wrapper">
              <input type="text" id="vm-target-ver-input" class="input" value="${this.#esc(this.#targetVersion)}" placeholder="e.g. 1.0.4" />
              <button type="button" class="btn btn-ghost btn-sm" id="vm-patch-btn" title="Increment patch version (+0.0.1)">Patch (+1)</button>
              <button type="button" class="btn btn-ghost btn-sm" id="vm-minor-btn" title="Increment minor version (+0.1.0)">Minor (+1)</button>
              <button type="button" class="btn btn-ghost btn-sm" id="vm-set-104-btn" title="Quickly set Target to 1.0.4">v1.0.4</button>
            </div>
          </div>

          <!-- Rescan trigger -->
          <div class="vm-scan-btn-wrap">
            <button type="button" class="btn btn-primary" id="vm-scan-versions-btn">
              <span class="material-symbols-outlined">search</span> Scan Versions
            </button>
          </div>
        </div>

        <!-- Metric Summary Cards -->
        <div class="version-summary-card">
          <div class="version-metric">
            <span class="version-metric-label">Current Project Version</span>
            <div class="version-metric-value">
              <span>${this.#currentVersion}</span>
              <span class="version-badge version-badge-info">Target: ${this.#targetVersion}</span>
            </div>
          </div>

          <div class="version-metric">
            <span class="version-metric-label">Update Status</span>
            <div class="version-metric-value">
              ${hasMismatches
                ? `<span class="version-badge version-badge-warning"><span class="material-symbols-outlined" style="font-size:14px">warning</span> ${r.mismatchCount} File(s) Ready to Update</span>`
                : `<span class="version-badge version-badge-success"><span class="material-symbols-outlined" style="font-size:14px">check_circle</span> Up to Date</span>`
              }
            </div>
          </div>

          <div class="version-metric">
            <span class="version-metric-label">Synchronized Files</span>
            <div class="version-metric-value">
              <span>${r.upToDateCount} / ${r.scannedFilesCount || r.fileResults.length}</span>
            </div>
          </div>

          <div class="version-metric">
            <span class="version-metric-label">Toolchain & Dependency Guard</span>
            <div class="version-metric-value" style="font-size:var(--fs-base)">
              <span class="version-badge version-badge-success" title="AndroidX, Google, Kotlin dependencies, AGP, Wrapper, SDK versions">
                <span class="material-symbols-outlined" style="font-size:14px">shield</span>
                ${r.protectedSkippedCount} Protected
              </span>
            </div>
          </div>

          ${r.ambiguousCount > 0 ? `
            <div class="version-metric">
              <span class="version-metric-label">Review Warning</span>
              <div class="version-metric-value" style="font-size:var(--fs-base)">
                <span class="version-badge version-badge-warning">
                  <span class="material-symbols-outlined" style="font-size:14px">help</span>
                  ${r.ambiguousCount} Ambiguous
                </span>
              </div>
            </div>
          ` : ''}
        </div>

        <!-- Tab Navigation -->
        <div class="card vm-tabs-card" style="margin-bottom:var(--sp-4)">
          <div class="card-header vm-tabs-card-header" style="border-bottom:1px solid var(--border);padding:0 var(--sp-4)">
            <div class="vm-tabs-nav" role="tablist" aria-label="Version Manager Sections">
              <button type="button" class="tab-btn vm-tab-btn ${this.#activeTab === 'report' ? 'active' : ''}" data-tab="report" role="tab" aria-selected="${this.#activeTab === 'report' ? 'true' : 'false'}">
                <span class="material-symbols-outlined vm-tab-icon">summarize</span>
                <span class="vm-tab-text-full">Mismatch Report & File-by-File Preview</span>
                <span class="vm-tab-text-short">Mismatch</span>
              </button>
              <button type="button" class="tab-btn vm-tab-btn ${this.#activeTab === 'consistency' ? 'active' : ''}" data-tab="consistency" role="tab" aria-selected="${this.#activeTab === 'consistency' ? 'true' : 'false'}">
                <span class="material-symbols-outlined vm-tab-icon">verified</span>
                <span class="vm-tab-text-full">Consistency Verification & Tests</span>
                <span class="vm-tab-text-short">Tests</span>
              </button>
              <button type="button" class="tab-btn vm-tab-btn ${this.#activeTab === 'changelog' ? 'active' : ''}" data-tab="changelog" role="tab" aria-selected="${this.#activeTab === 'changelog' ? 'true' : 'false'}">
                <span class="material-symbols-outlined vm-tab-icon">history</span>
                <span class="vm-tab-text-full">Changelog (v${this.#targetVersion})</span>
                <span class="vm-tab-text-short">Changelog</span>
              </button>
            </div>
          </div>

          <div class="card-body">
            <!-- Tab 1: Mismatch Report & File Preview -->
            <div id="vm-tab-report" style="${this.#activeTab === 'report' ? '' : 'display:none'}">
              <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-3);flex-wrap:wrap;gap:var(--sp-2)">
                <div>
                  <strong>Files Requiring Version Synchronization (${this.#currentVersion} → ${this.#targetVersion})</strong>
                  <div style="font-size:var(--fs-xs);color:var(--text-secondary)">
                    Verified project version references ready for update. Third-party dependencies, AGP, Gradle wrapper, and SDK settings are 100% protected.
                  </div>
                </div>
                <div style="display:flex;gap:var(--sp-2)">
                  <button type="button" class="btn btn-ghost btn-sm" id="vm-select-all-btn">
                    <span class="material-symbols-outlined" style="font-size:16px">select_all</span> Select All
                  </button>
                  <button type="button" class="btn btn-ghost btn-sm" id="vm-deselect-all-btn">
                    <span class="material-symbols-outlined" style="font-size:16px">deselect</span> Deselect All
                  </button>
                </div>
              </div>

              ${r.fileResults.length === 0 ? `
                <div style="padding:var(--sp-6);text-align:center;color:var(--text-secondary)">
                  <span class="material-symbols-outlined" style="font-size:48px;opacity:0.4">fact_check</span>
                  <div>No pending version references matching "${this.#currentVersion}". Project is completely up to date!</div>
                </div>
              ` : `
                <!-- Desktop Table View (>=768px) -->
                <div class="version-diff-table-wrap desktop-only" style="overflow-x:auto">
                  <table class="version-diff-table">
                    <thead>
                      <tr>
                        <th style="width:40px">Select</th>
                        <th style="width:240px">File Path</th>
                        <th style="width:140px">Status</th>
                        <th>Differences, Reference Types & Protection</th>
                      </tr>
                    </thead>
                    <tbody>
                      ${r.fileResults.map((fr, idx) => `
                        <tr>
                          <td style="text-align:center">
                            <input type="checkbox" class="vm-file-checkbox" data-index="${idx}" ${fr.selected ? 'checked' : ''} ${!fr.needsUpdate ? 'disabled' : ''} />
                          </td>
                          <td>
                            <strong>${this.#esc(fr.path)}</strong>
                          </td>
                          <td>
                            ${fr.needsUpdate
                              ? `<span class="version-badge version-badge-warning">Needs Update</span>`
                              : fr.isUpToDate
                              ? `<span class="version-badge version-badge-success">Up to Date</span>`
                              : `<span class="version-badge version-badge-info">Clean</span>`
                            }
                          </td>
                          <td>
                            <div style="display:flex;flex-direction:column;gap:6px">
                              ${fr.matches.map(m => `
                                <div style="display:flex;align-items:flex-start;gap:8px;font-size:12px;flex-wrap:wrap">
                                  <span style="color:var(--text-secondary);min-width:32px;font-family:var(--font-mono)">L${m.lineNumber}:</span>
                                  <span class="ref-tag">${this.#esc(m.referenceType || 'Reference')}</span>
                                  ${m.isProtected ? `
                                    <span class="diff-line-protected">${this.#esc(m.oldLine)}</span>
                                    <span class="version-badge version-badge-secondary" style="font-size:10px">
                                      <span class="material-symbols-outlined" style="font-size:12px">shield</span>
                                      ${this.#esc(m.reason || 'Protected')}
                                    </span>
                                  ` : `
                                    <span class="diff-line-old">${this.#esc(m.oldLine)}</span>
                                    <span class="material-symbols-outlined" style="font-size:14px;color:var(--text-secondary);align-self:center">arrow_forward</span>
                                    <span class="diff-line-new">${this.#esc(m.newLine)}</span>
                                    ${m.isAmbiguous ? `<span class="version-badge version-badge-warning" style="font-size:10px">Approval Needed</span>` : ''}
                                    <span style="font-size:11px;color:var(--text-muted);align-self:center">(${this.#esc(m.reason || 'Update')})</span>
                                    ${fr.needsUpdate ? `
                                      <button type="button" class="btn btn-ghost btn-sm vm-update-line-btn" 
                                              data-path="${this.#esc(fr.path)}" 
                                              data-line="${m.lineNumber}" 
                                              data-old="${this.#esc(m.oldLine)}" 
                                              data-new="${this.#esc(m.newLine)}"
                                              style="padding:2px 8px;margin-left:auto;color:var(--accent);font-weight:600"
                                              title="Open in File Editor, highlight line ${m.lineNumber}, and apply Auto Fix">
                                        <span class="material-symbols-outlined" style="font-size:14px">edit_note</span> Update Line
                                      </button>
                                    ` : ''}
                                  `}
                                </div>
                              `).join('')}
                            </div>
                          </td>
                        </tr>
                      `).join('')}
                    </tbody>
                  </table>
                </div>

                <!-- Mobile Responsive Cards View (<768px) -->
                <div class="version-diff-cards-wrap mobile-only">
                  ${r.fileResults.map((fr, idx) => `
                    <div class="vm-mobile-file-card ${fr.needsUpdate ? 'needs-update' : ''}">
                      <div class="vm-mobile-card-header">
                        <label class="vm-mobile-checkbox-label">
                          <input type="checkbox" class="vm-file-checkbox" data-index="${idx}" ${fr.selected ? 'checked' : ''} ${!fr.needsUpdate ? 'disabled' : ''} />
                          <span class="vm-mobile-file-path">${this.#esc(fr.path)}</span>
                        </label>
                        <div class="vm-mobile-status">
                          ${fr.needsUpdate
                            ? `<span class="version-badge version-badge-warning">Needs Update</span>`
                            : fr.isUpToDate
                            ? `<span class="version-badge version-badge-success">Up to Date</span>`
                            : `<span class="version-badge version-badge-info">Clean</span>`
                          }
                        </div>
                      </div>

                      <div class="vm-mobile-card-diffs">
                        ${fr.matches.map(m => `
                          <div class="vm-mobile-diff-item">
                            <div class="vm-mobile-diff-meta">
                              <span class="vm-mobile-line-badge">L${m.lineNumber}</span>
                              <span class="ref-tag">${this.#esc(m.referenceType || 'Reference')}</span>
                              ${m.isAmbiguous ? `<span class="version-badge version-badge-warning" style="font-size:10px">Approval Needed</span>` : ''}
                              ${m.reason ? `<span class="vm-mobile-diff-desc">(${this.#esc(m.reason)})</span>` : ''}
                            </div>

                            <div class="vm-mobile-diff-body">
                              ${m.isProtected ? `
                                <div class="vm-mobile-diff-stack">
                                  <div class="vm-diff-stack-row vm-diff-stack-protected">
                                    <span class="diff-tag diff-tag-shield"><span class="material-symbols-outlined" style="font-size:12px">shield</span> Protected</span>
                                    <span class="diff-line-protected">${this.#esc(m.oldLine)}</span>
                                  </div>
                                  ${m.reason ? `<div class="vm-diff-reason-note">${this.#esc(m.reason)}</div>` : ''}
                                </div>
                              ` : `
                                <div class="vm-mobile-diff-stack">
                                  <div class="vm-diff-stack-row vm-diff-stack-old">
                                    <span class="diff-tag diff-tag-del">OLD</span>
                                    <span class="diff-line-old">${this.#esc(m.oldLine)}</span>
                                  </div>
                                  <div class="vm-diff-stack-arrow">
                                    <span class="material-symbols-outlined" style="font-size:14px">arrow_downward</span>
                                  </div>
                                  <div class="vm-diff-stack-row vm-diff-stack-new">
                                    <span class="diff-tag diff-tag-add">NEW</span>
                                    <span class="diff-line-new">${this.#esc(m.newLine)}</span>
                                  </div>
                                </div>
                                ${fr.needsUpdate ? `
                                  <button type="button" class="btn btn-secondary btn-sm vm-update-line-btn vm-mobile-update-btn" 
                                          data-path="${this.#esc(fr.path)}" 
                                          data-line="${m.lineNumber}" 
                                          data-old="${this.#esc(m.oldLine)}" 
                                          data-new="${this.#esc(m.newLine)}">
                                    <span class="material-symbols-outlined" style="font-size:14px">edit_note</span> Update Line ${m.lineNumber}
                                  </button>
                                ` : ''}
                              `}
                            </div>
                          </div>
                        `).join('')}
                      </div>
                    </div>
                  `).join('')}
                </div>
              `}
            </div>

            <!-- Tab 2: Consistency Verification -->
            <div id="vm-tab-consistency" style="${this.#activeTab === 'consistency' ? '' : 'display:none'}">
              <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-4);flex-wrap:wrap;gap:var(--sp-2)">
                <div>
                  <strong>Automated Consistency Test Suite</strong>
                  <div style="font-size:var(--fs-xs);color:var(--text-secondary)">
                    Verifies that project version references are 100% consistent and zero third-party dependency coordinates were mutated.
                  </div>
                </div>
                <button type="button" class="btn btn-secondary btn-sm" id="vm-run-tests-btn">
                  <span class="material-symbols-outlined">play_arrow</span>Run Verification Tests
                </button>
              </div>

              <div id="vm-test-results-container">
                ${this.#renderConsistencyResults()}
              </div>
            </div>

            <!-- Tab 3: Changelog -->
            <div id="vm-tab-changelog" style="${this.#activeTab === 'changelog' ? '' : 'display:none'}">
              <div class="version-changelog-item">
                <div class="version-changelog-title">
                  <span class="version-badge version-badge-success">v${this.#targetVersion}</span>
                  <span>Release ${this.#targetVersion} — ${this.#uploadedProjectName}</span>
                  <span style="margin-left:auto;font-size:var(--fs-xs);color:var(--text-secondary)">${new Date().toISOString().split('T')[0]}</span>
                </div>
                <ul class="version-changelog-list">
                  <li><strong>Project Version Synchronization:</strong> Upgraded primary project version from <code>${this.#currentVersion}</code> to <code>${this.#targetVersion}</code>.</li>
                  <li><strong>Gradle Build Scripts:</strong> Synchronized <code>gradle.properties</code>, root, and module <code>build.gradle/kts</code> declarations.</li>
                  <li><strong>Source Code Constants:</strong> Updated Java / Kotlin version constants (e.g. <code>LibraryInfo.java</code>, <code>Constants.java</code>).</li>
                  <li><strong>Manifest & Metadata:</strong> Aligned <code>AndroidManifest.xml</code>, <code>package.json</code>, and publishing descriptors.</li>
                  <li><strong>Documentation:</strong> Updated installation guides, badges, and release references in <code>README.md</code>.</li>
                  <li><strong>Dependency & Toolchain Guard:</strong> Strictly protected all third-party dependencies, AGP, Gradle Wrapper, and SDK configurations.</li>
                </ul>
              </div>

              <div class="version-changelog-item" style="border-left-color:var(--border)">
                <div class="version-changelog-title">
                  <span class="version-badge version-badge-info">v${this.#currentVersion}</span>
                  <span>Previous Release</span>
                </div>
                <ul class="version-changelog-list">
                  <li>Previous version baseline before update operation.</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;

    this.#bindEvents();
  }

  #renderConsistencyResults() {
    const report = this.#consistencyReport || VersionUpdateEngine.verifyConsistency(this.#files, this.#targetVersion, this.#currentVersion);

    return `
      <div style="display:flex;flex-direction:column;gap:var(--sp-2)">
        ${report.checks.map(c => `
          <div style="display:flex;align-items:flex-start;gap:var(--sp-3);padding:var(--sp-3);background:var(--bg-muted);border-radius:var(--radius-md);border:1px solid ${c.passed ? 'rgba(34,197,94,0.2)' : 'rgba(239,68,68,0.2)'}">
            <span class="material-symbols-outlined" style="color:${c.passed ? '#22c55e' : '#ef4444'}">
              ${c.passed ? 'check_circle' : 'cancel'}
            </span>
            <div style="flex:1">
              <div style="font-weight:600;font-size:var(--fs-sm);color:var(--text-primary)">${this.#esc(c.name)}</div>
              <div style="font-size:var(--fs-xs);color:var(--text-secondary);margin-top:2px">${this.#esc(c.details)}</div>
            </div>
            <span class="version-badge ${c.passed ? 'version-badge-success' : 'version-badge-warning'}">
              ${c.passed ? 'Passed' : 'Failed'}
            </span>
          </div>
        `).join('')}
      </div>
    `;
  }

  #switchTab(tabId) {
    if (!tabId) return;
    this.#activeTab = tabId;
    const container = this.#container;
    if (!container) return;

    // Update tab buttons
    container.querySelectorAll('.vm-tab-btn').forEach(btn => {
      const isActive = btn.dataset.tab === tabId;
      btn.classList.toggle('active', isActive);
      btn.setAttribute('aria-selected', isActive ? 'true' : 'false');
    });

    // Update tab content panels without re-rendering entire page
    const reportPanel = container.querySelector('#vm-tab-report');
    const consistencyPanel = container.querySelector('#vm-tab-consistency');
    const changelogPanel = container.querySelector('#vm-tab-changelog');

    if (reportPanel) reportPanel.style.display = tabId === 'report' ? '' : 'none';
    if (consistencyPanel) consistencyPanel.style.display = tabId === 'consistency' ? '' : 'none';
    if (changelogPanel) changelogPanel.style.display = tabId === 'changelog' ? '' : 'none';
  }

  #bindEvents() {
    const container = this.#container;

    // Tab buttons with instant switching (no page destruction, no scroll jump)
    container.querySelectorAll('.vm-tab-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.#switchTab(btn.dataset.tab);
      });
    });

    // Version input fields
    const currentInput = container.querySelector('#vm-current-ver-input');
    const targetInput = container.querySelector('#vm-target-ver-input');

    const onCurrentChange = () => {
      const val = currentInput?.value?.trim();
      if (val) this.#currentVersion = val;
    };
    currentInput?.addEventListener('input', onCurrentChange);
    currentInput?.addEventListener('change', onCurrentChange);

    const onTargetChange = () => {
      const val = targetInput?.value?.trim();
      if (val) this.#targetVersion = val;
    };
    targetInput?.addEventListener('input', onTargetChange);
    targetInput?.addEventListener('change', onTargetChange);

    // Auto-detect button
    container.querySelector('#vm-autodetect-btn')?.addEventListener('click', async () => {
      this.#autoDetectVersions();
      if (currentInput) currentInput.value = this.#currentVersion;
      if (targetInput) targetInput.value = this.#targetVersion;
      await this.#runScan();
      this.#render();
      toast.info('Auto-detected', `Detected version: ${this.#currentVersion}`);
    });

    // Quick increment buttons
    container.querySelector('#vm-patch-btn')?.addEventListener('click', async () => {
      this.#syncInputsFromDOM();
      this.#targetVersion = VersionUpdateEngine.calculateNextVersion(this.#currentVersion);
      if (targetInput) targetInput.value = this.#targetVersion;
      await this.#runScan();
      this.#render();
    });

    container.querySelector('#vm-minor-btn')?.addEventListener('click', async () => {
      this.#syncInputsFromDOM();
      const parts = this.#currentVersion.split('.');
      if (parts.length >= 2) {
        const minor = parseInt(parts[1], 10);
        if (!isNaN(minor)) {
          parts[1] = (minor + 1).toString();
          if (parts.length >= 3) parts[2] = '0';
          this.#targetVersion = parts.join('.');
        }
      } else {
        this.#targetVersion = this.#currentVersion + '.1';
      }
      if (targetInput) targetInput.value = this.#targetVersion;
      await this.#runScan();
      this.#render();
    });

    // Quick set 1.0.4 button
    container.querySelector('#vm-set-104-btn')?.addEventListener('click', async () => {
      this.#targetVersion = '1.0.4';
      if (targetInput) targetInput.value = '1.0.4';
      await this.#runScan();
      this.#render();
      toast.info('Target Version Set', 'Target version set to 1.0.4');
    });

    // Scan Versions triggers (both buttons)
    const doScanVersions = async () => {
      this.#syncInputsFromDOM();
      await this.#runScan();
      this.#render();
      toast.info('Scan Completed', `Identified references for ${this.#currentVersion} → ${this.#targetVersion}`);
    };

    container.querySelector('#vm-scan-btn')?.addEventListener('click', doScanVersions);
    container.querySelector('#vm-scan-versions-btn')?.addEventListener('click', doScanVersions);

    // Checkbox toggles
    container.querySelectorAll('.vm-file-checkbox').forEach(cb => {
      cb.addEventListener('change', () => {
        const idx = parseInt(cb.dataset.index, 10);
        if (this.#scanReport?.fileResults[idx]) {
          this.#scanReport.fileResults[idx].selected = cb.checked;
        }
      });
    });

    // Select All / Deselect All
    container.querySelector('#vm-select-all-btn')?.addEventListener('click', () => {
      this.#scanReport?.fileResults.forEach(fr => {
        if (fr.needsUpdate) fr.selected = true;
      });
      this.#render();
      toast.info('Selected All', 'All files selected for update.');
    });

    container.querySelector('#vm-deselect-all-btn')?.addEventListener('click', () => {
      this.#scanReport?.fileResults.forEach(fr => {
        fr.selected = false;
      });
      this.#render();
      toast.info('Deselected All', 'All files deselected.');
    });

    // Update Line in Editor & Auto Fix buttons
    container.querySelectorAll('.vm-update-line-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const filePath = btn.dataset.path;
        const lineNum = parseInt(btn.dataset.line, 10);
        const oldLine = btn.dataset.old;
        const newLine = btn.dataset.new;

        if (projectService.current) {
          projectService.current.files[filePath] = this.#files[filePath];
        } else {
          projectService.current = {
            id: `proj_${Date.now()}`,
            name: this.#uploadedProjectName || 'Android Project',
            projectType: 'library',
            files: { ...this.#files },
            createdAt: Date.now(),
            updatedAt: Date.now()
          };
        }

        const fixParams = {
          path: filePath,
          lineNumber: lineNum,
          fromVersion: this.#currentVersion,
          toVersion: this.#targetVersion,
          oldLine,
          newLine,
          content: this.#files[filePath]
        };

        eventBus.emit('router:navigate', {
          pageId: 'editor',
          params: fixParams
        });

        eventBus.emit('editor:openWithFix', fixParams);
      });
    });

    // Update Selected button
    container.querySelector('#vm-update-selected-btn')?.addEventListener('click', () => {
      this.#confirmAndApplyUpdate({ onlySelected: true });
    });

    // Update All Project Versions button
    container.querySelector('#vm-apply-btn')?.addEventListener('click', () => {
      this.#confirmAndApplyUpdate({ onlySelected: false });
    });

    // Rollback button
    container.querySelector('#vm-rollback-btn')?.addEventListener('click', () => {
      this.#rollback();
    });

    // Run Consistency Tests button
    container.querySelector('#vm-run-tests-btn')?.addEventListener('click', () => {
      this.#consistencyReport = VersionUpdateEngine.verifyConsistency(this.#files, this.#targetVersion, this.#currentVersion);
      const targetDiv = container.querySelector('#vm-test-results-container');
      if (targetDiv) {
        targetDiv.innerHTML = this.#renderConsistencyResults();
      }
      toast.success('Tests Completed', this.#consistencyReport.isConsistent ? 'Consistency verification passed 100%' : 'Consistency warnings detected');
    });

    // Export ZIP button
    container.querySelector('#vm-export-zip-btn')?.addEventListener('click', () => {
      this.#exportUpdatedZip();
    });

    // Release ZIP download button
    container.querySelector('#vm-dl-release-btn')?.addEventListener('click', async (e) => {
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
        window.location.href = `./${filename}`;
      }
    });

    // File input for ZIP upload
    const fileInput = container.querySelector('#vm-zip-file-input');
    fileInput?.addEventListener('change', (e) => {
      const file = e.target.files?.[0];
      if (file) this.#handleZipUpload(file);
    });

    // Drag and drop zone
    const dropzone = container.querySelector('#vm-dropzone');
    if (dropzone) {
      dropzone.addEventListener('click', () => fileInput?.click());
      dropzone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropzone.classList.add('dragover');
      });
      dropzone.addEventListener('dragleave', () => {
        dropzone.classList.remove('dragover');
      });
      dropzone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropzone.classList.remove('dragover');
        const file = e.dataTransfer.files?.[0];
        if (file) this.#handleZipUpload(file);
      });
    }

    // Switch to active project
    container.querySelector('#vm-switch-active-btn')?.addEventListener('click', async () => {
      await this.#loadInitialProject();
      await this.#runScan();
      this.#render();
      toast.info('Active Project', 'Loaded current workspace project.');
    });

    // Switch to sample project
    container.querySelector('#vm-switch-sample-btn')?.addEventListener('click', async () => {
      this.#files = await this.#loadBaselineFiles();
      this.#sourceType = 'sample';
      this.#uploadedProjectName = 'Android Library Project';
      this.#autoDetectVersions();
      await this.#runScan();
      this.#render();
      toast.info('Baseline Project', 'Loaded baseline Android library project.');
    });
  }

  /**
   * Apply version updates to selected files or all files.
   * @param {{ onlySelected?: boolean }} options
   */
  async #confirmAndApplyUpdate({ onlySelected = false } = {}) {
    this.#syncInputsFromDOM();

    // Ensure scan is performed for the current input pair
    await this.#runScan(true);

    const filesToConsider = onlySelected
      ? this.#scanReport.fileResults.filter(f => f.selected && f.needsUpdate)
      : this.#scanReport.fileResults.filter(f => f.needsUpdate);

    if (filesToConsider.length === 0) {
      if (this.#scanReport.mismatchCount === 0) {
        toast.info('Already Up to Date', `All project files are already updated to ${this.#targetVersion}.`);
        return;
      }
      toast.warning('No Files Selected', 'Please select at least one file requiring update.');
      return;
    }

    const targetVer = this.#targetVersion;
    const currentVer = this.#currentVersion;
    const actionLabel = onlySelected ? 'Update Selected Files' : 'Update All Project Versions';

    const confirmed = await modal.confirm({
      title: `${actionLabel} to ${targetVer}?`,
      message: `A backup will be automatically created before updating ${filesToConsider.length} file(s) from ${currentVer} to ${targetVer}. Third-party dependencies and toolchain settings are completely protected. Proceed?`,
      confirmLabel: `Update to v${targetVer}`,
      confirmText: `Update to v${targetVer}`,
    });

    if (!confirmed) return;

    try {
      const selectedPaths = filesToConsider.map(f => f.path);
      const result = this.#engine.applyUpdate(this.#files, selectedPaths, currentVer, targetVer);

      this.#files = result.updatedFiles;

      // If active workspace project, also persist to ProjectService
      if (this.#sourceType === 'active' && projectService.current) {
        projectService.current.files = { ...result.updatedFiles };
        projectService.current.version = targetVer;
        await projectService.save(projectService.current);
      }

      consoleService.success(`VersionUpdate: Successfully updated ${result.modifiedCount} files from ${currentVer} to ${targetVer}. Backup created at ${result.backupTimestamp}`);
      toast.success('Version Updated', `Successfully updated ${result.modifiedCount} files to v${targetVer}!`);

      // Post-update: update current version to new target version, and compute next suggestion
      this.#currentVersion = targetVer;
      this.#targetVersion = VersionUpdateEngine.calculateNextVersion(targetVer);

      // Automatic post-update rescan to refresh state (do NOT sync from stale DOM inputs!)
      await this.#runScan(false);
      this.#consistencyReport = VersionUpdateEngine.verifyConsistency(this.#files, targetVer, currentVer);

      // Refresh UI with new state
      this.#render();

      // Broadcast version update event
      eventBus.emit('version:updated', { version: this.#currentVersion });
    } catch (err) {
      console.error('[VersionManager] Update error, rolling back:', err);
      toast.error('Update Failed', `Error: ${err.message}. Changes have been safely rolled back.`);
    }
  }

  /**
   * Rollback to last backup snapshot.
   */
  async #rollback() {
    const confirmed = await modal.confirm({
      title: 'Rollback Version Update?',
      message: 'Restore all project files to the state prior to the last update operation?',
      confirmLabel: 'Rollback Now',
      confirmText: 'Rollback Now',
      danger: true,
    });

    if (!confirmed) return;

    const restored = this.#engine.rollback();
    if (!restored) {
      toast.error('No Backup', 'No previous backup available.');
      return;
    }

    this.#files = restored;

    if (this.#sourceType === 'active' && projectService.current) {
      projectService.current.files = { ...restored };
      await projectService.save(projectService.current);
    }

    this.#autoDetectVersions();
    await this.#runScan(false);
    this.#consistencyReport = VersionUpdateEngine.verifyConsistency(this.#files, this.#targetVersion, this.#currentVersion);
    this.#render();

    toast.info('Rollback Complete', 'Files restored from pre-update backup snapshot.');
  }

  /**
   * Package updated project into ZIP archive and trigger download.
   */
  async #exportUpdatedZip() {
    try {
      if (typeof JSZip === 'undefined') {
        toast.error('JSZip Unavailable', 'JSZip is required to export ZIP archives.');
        return;
      }

      consoleService.info('Exporting updated project ZIP...');
      const zip = new JSZip();
      let fileCount = 0;

      for (const [path, content] of Object.entries(this.#files)) {
        if (content instanceof Uint8Array) {
          zip.file(path, content, { binary: true });
        } else if (path === 'gradlew') {
          const lf = (content ?? '').replace(/\r\n/g, '\n').replace(/\r/g, '\n');
          zip.file(path, lf, { unixPermissions: 0o755 });
        } else {
          zip.file(path, content ?? '');
        }
        fileCount++;
      }

      const blob = await zip.generateAsync({
        type: 'blob',
        compression: 'DEFLATE',
        compressionOptions: { level: 6 }
      });

      const fileName = `${this.#uploadedProjectName || 'AndroidProject'}_v${this.#currentVersion}.zip`;
      await WebViewBridge.downloadFile(fileName, blob, 'application/zip');

      const sizeKb = (blob.size / 1024).toFixed(1);
      consoleService.success(`Exported ${fileName} (${fileCount} files, ${sizeKb} KB)`);
      toast.success('ZIP Exported', `Downloaded ${fileName} (${fileCount} files, ${sizeKb} KB)`);
    } catch (err) {
      console.error('[VersionManager] Export ZIP error:', err);
      toast.error('Export Failed', err.message);
    }
  }

  #esc(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
}
