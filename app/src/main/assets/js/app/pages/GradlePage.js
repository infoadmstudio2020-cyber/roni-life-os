/**
 * GradlePage — View, edit, validate, and manage Gradle configurations and dependencies.
 *
 * Capabilities:
 * - Live editing & pre-save syntax validation (unclosed braces, quotes, machine paths)
 * - Gradle configuration generation & scaffolding (Pure Java, Android Lib, Android App, Root)
 * - Custom Dependency Management: add/remove/edit dependencies with Maven coordinate validation
 * - Source-based dependency automation: scans .java/.kt files and highlights missing dependencies
 * - File Content Viewer: full content view, copy full content to clipboard, and file download
 */

import { projectService }           from '../modules/storage/ProjectService.js';
import { SourceDependencyAnalyzer } from '../modules/analyzer/SourceDependencyAnalyzer.js';
import { GradlePatcher }            from '../modules/generator/GradlePatcher.js';
import { PreValidatorService }      from '../modules/build/PreValidatorService.js';
import { eventBus }                 from '../core/EventBus.js';
import { toast }                    from '../ui/ToastService.js';
import { consoleService }           from '../modules/storage/ConsoleService.js';
import { modal }                    from '../ui/ModalService.js';
import { TransitiveDependencyEngine } from '../modules/analyzer/TransitiveDependencyEngine.js';
import { WebViewBridge } from '../core/WebViewBridge.js';
import { ConsumerResolutionModal }  from '../modules/analyzer/ConsumerResolutionModal.js';
import { MavenPublishGenerator }    from '../modules/generator/MavenPublishGenerator.js';
import { ClipboardUtil }             from '../core/ClipboardUtil.js';

export class GradlePage {
  #container = null;
  #activeFile = null;
  #sourceDeps = null;
  #selectedDepCoord = null;
  #selectedSourceFile = null;
  #showDepsPanel = true;
  #validationReport = null; // { valid: boolean, errors: [], warnings: [] }

  async init(container) {
    this.#container = container;
    const p = projectService.current;
    if (!p) {
      container.innerHTML = this.#noProject();
      container.querySelector('#gradle-goto-gen-btn')?.addEventListener('click', () => {
        eventBus.emit('router:navigate', { pageId: 'generator' });
      });
      return;
    }

    const gradleFiles = Object.keys(p.files || {}).filter(k =>
      k.endsWith('.gradle') || k.endsWith('.properties') || k === 'jitpack.yml' ||
      k.endsWith('gradle-wrapper.properties') || k.endsWith('.gradle.kts')
    );

    // Prioritize library module build.gradle over root build.gradle for dependency addition
    const libraryBuild = gradleFiles.find(f => f.includes('/build.gradle') && !f.startsWith('app/') && !f.startsWith('sample/') && !f.includes('/app/') && !f.includes('/sample/'));
    const anyModuleBuild = gradleFiles.find(f => f.includes('/build.gradle'));
    this.#activeFile = libraryBuild || anyModuleBuild || gradleFiles.find(f => f.includes('build.gradle')) || gradleFiles[0] || null;
    this.#sourceDeps = SourceDependencyAnalyzer.analyze(p.files || {});
    this.#validationReport = null;

    this.#render(gradleFiles);
  }

  #render(gradleFiles) {
    const p = projectService.current;
    const activeFile = this.#activeFile;
    const isBuildGradle = activeFile && (activeFile.endsWith('.gradle') || activeFile.endsWith('.gradle.kts'));
    const srcDeps = this.#sourceDeps || SourceDependencyAnalyzer.analyze(p.files || {});
    const activeContent = (p?.files?.[activeFile]) || '';
    const declaredDeps = isBuildGradle ? GradlePatcher.parseDeclaredDependencies(activeContent) : [];

    this.#container.innerHTML = `
      <div class="page-container">
        <!-- Page Header -->
        <div class="page-header">
          <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-2)">
            <div>
              <h1 class="page-title"><span class="material-symbols-outlined" style="color:var(--accent)">build</span>Gradle Management</h1>
              <p class="page-subtitle">Configure build scripts, validate syntax, and manage dependencies</p>
            </div>
            <div style="display:flex;gap:var(--sp-2);flex-wrap:wrap">
              <button type="button" class="btn btn-secondary btn-sm" id="gradle-validate-btn" title="Validate Gradle script syntax and configurations">
                <span class="material-symbols-outlined" style="font-size:16px">check_circle</span>Validate Syntax
              </button>
              <button type="button" class="btn btn-secondary btn-sm" id="gradle-test-consumer-btn" title="Simulate Consumer Dependency Resolution & Classpath">
                <span class="material-symbols-outlined" style="font-size:16px">verified</span>Resolution Test
              </button>
              <button type="button" class="btn btn-secondary btn-sm" id="gradle-scaffold-btn" title="Generate or reset standard Gradle build script">
                <span class="material-symbols-outlined" style="font-size:16px">auto_fix_normal</span>Scaffold Gradle
              </button>
              <button type="button" class="btn btn-secondary btn-sm" id="gradle-copy-btn" title="Copy full content to clipboard">
                <span class="material-symbols-outlined" style="font-size:16px">content_copy</span>Copy Full Content
              </button>
              <button type="button" class="btn btn-secondary btn-sm" id="gradle-download-btn" title="Download active Gradle file">
                <span class="material-symbols-outlined" style="font-size:16px">download</span>Download
              </button>
              <button type="button" class="btn btn-primary btn-sm" id="gradle-save-btn">
                <span class="material-symbols-outlined" style="font-size:16px">save</span>Save Gradle
              </button>
            </div>
          </div>
        </div>

        <!-- ── Validation Status Banner (if validated or errors exist) ── -->
        <div id="gradle-validation-banner" style="margin-bottom:var(--sp-3)">
          ${this.#renderValidationBanner()}
        </div>

        <!-- ── Custom Dependency Input & Management Section ── -->
        ${isBuildGradle ? `
        <div class="card" style="margin-bottom:var(--sp-4);background:var(--bg-surface);border:1px solid var(--border)">
          <div class="card-header" style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px">
            <div style="display:flex;align-items:center;gap:8px">
              <span class="material-symbols-outlined" style="color:var(--accent)">library_add</span>
              <strong style="font-size:13px">Custom Dependency Management</strong>
              <span class="badge badge-info" style="font-size:11px">${declaredDeps.length} Declared in ${this.#escHtml(activeFile.split('/').pop())}</span>
            </div>
            <div style="font-size:11px;color:var(--text-muted)">
              Add by Maven coordinate (e.g. <code>group:name:version</code>) or library name
            </div>
          </div>

          <div class="card-body" style="padding:var(--sp-3)">
            <!-- Add Dependency Form -->
            <div style="display:flex;flex-wrap:wrap;gap:8px;align-items:flex-end">
              <div style="flex:2;min-width:240px">
                <label style="font-size:11px;font-weight:600;color:var(--text-secondary);display:block;margin-bottom:4px">
                  Library Name or Maven Coordinate
                </label>
                <input type="text" id="gradle-custom-dep-input" class="input input-sm" style="width:100%"
                       placeholder="e.g. com.google.code.gson:gson:2.10.1 or retrofit" />
              </div>

              <div style="width:240px">
                <label style="font-size:11px;font-weight:600;color:var(--text-secondary);display:block;margin-bottom:4px">
                  Configuration Scope
                </label>
                <select id="gradle-custom-dep-config" class="input input-sm" style="width:100%;height:32px">
                  <option value="auto" selected>⚡ Auto Scope (api vs implementation)</option>
                  <option value="implementation">implementation (Internal, runtime-only)</option>
                  <option value="api">api (Transitive compile)</option>
                  <option value="compileOnly">compileOnly (Provided)</option>
                  <option value="testImplementation">testImplementation (Unit test)</option>
                  <option value="androidTestImplementation">androidTestImplementation (Instrumentation)</option>
                </select>
              </div>

              <button type="button" class="btn btn-primary btn-sm" id="gradle-add-dep-btn" style="height:32px">
                <span class="material-symbols-outlined" style="font-size:16px">add</span>Validate & Add
              </button>
            </div>

            <!-- Auto-Sync Checkbox -->
            <div style="margin-top:8px;display:flex;align-items:center;gap:6px">
              <input type="checkbox" id="gradle-auto-sync-transitive" checked style="cursor:pointer" />
              <label for="gradle-auto-sync-transitive" style="font-size:11px;color:var(--text-secondary);cursor:pointer">
                <strong>Automatic Transitive & Publish Sync</strong>: Auto-update <code>settings.gradle</code> repositories (JitPack/Google), <code>jitpack.yml</code>, POM &amp; Gradle Module Metadata
              </label>
            </div>

            <!-- Live Scope & Transitive Analysis Recommendation Box -->
            <div id="gradle-scope-recommendation-box" style="margin-top:10px;padding:8px 12px;background:var(--bg-muted);border:1px solid var(--border);border-radius:4px;display:none"></div>

            <!-- Quick Preset Chips -->
            <div style="display:flex;align-items:center;gap:6px;margin-top:10px;flex-wrap:wrap">
              <span style="font-size:11px;color:var(--text-muted);font-weight:600">Quick Presets:</span>
              <button type="button" class="badge gradle-preset-btn" data-coord="com.google.code.gson:gson:2.10.1" style="cursor:pointer">Gson 2.10.1</button>
              <button type="button" class="badge gradle-preset-btn" data-coord="com.squareup.okhttp3:okhttp:4.12.0" style="cursor:pointer">OkHttp 4.12.0</button>
              <button type="button" class="badge gradle-preset-btn" data-coord="org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0" style="cursor:pointer">Coroutines 1.8.0</button>
              <button type="button" class="badge gradle-preset-btn" data-coord="com.google.android.material:material:1.12.0" style="cursor:pointer">Material 1.12.0</button>
              <button type="button" class="badge gradle-preset-btn" data-coord="androidx.core:core-ktx:1.12.0" style="cursor:pointer">Core-KTX 1.12.0</button>
              <button type="button" class="badge gradle-preset-btn" data-coord="org.junit.jupiter:junit-jupiter:5.10.2" data-config="testImplementation" style="cursor:pointer">JUnit 5 (test)</button>
              <button type="button" class="badge gradle-preset-btn" data-coord="androidx.room:room-runtime:2.6.1" style="cursor:pointer">Room 2.6.1</button>
            </div>

            <!-- Declared Dependencies in this file -->
            ${declaredDeps.length > 0 ? `
              <div style="margin-top:12px;border-top:1px solid var(--border);padding-top:10px">
                <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:6px">
                  <div style="font-size:11px;font-weight:600;color:var(--text-secondary)">
                    Active Declared Dependencies in ${this.#escHtml(activeFile.split('/').pop())}:
                  </div>
                  <button type="button" class="btn btn-ghost btn-xs" id="gradle-quick-audit-btn" style="font-size:11px">
                    <span class="material-symbols-outlined" style="font-size:13px">fact_check</span>Audit Consumer Classpath
                  </button>
                </div>
                <div style="display:flex;flex-direction:column;gap:4px;max-height:160px;overflow-y:auto">
                  ${declaredDeps.map(d => {
                    const isApi = d.configuration === 'api';
                    const badgeClass = isApi ? 'badge-primary' : 'badge';
                    const targetToggle = isApi ? 'implementation' : 'api';
                    return `
                    <div style="display:flex;align-items:center;justify-content:space-between;padding:4px 8px;background:var(--bg-muted);border-radius:4px;font-size:12px">
                      <div style="display:flex;align-items:center;gap:6px;min-width:0;overflow:hidden">
                        <span class="${badgeClass}" style="font-size:10px;padding:1px 5px" title="${isApi ? 'Exposed to consumer compile classpath (Transitive API)' : 'Hidden from consumer compile classpath (Encapsulated Runtime)'}">${this.#escHtml(d.configuration)}</span>
                        <code style="color:var(--accent);font-size:11px">${this.#escHtml(d.coordinate)}</code>
                      </div>
                      <div style="display:flex;align-items:center;gap:4px">
                        <button type="button" class="btn btn-ghost btn-xs gradle-toggle-scope-btn" data-coord="${this.#escAttr(d.coordinate)}" data-target="${targetToggle}" title="Switch scope to ${targetToggle}" style="font-size:10px;padding:1px 6px">
                          to ${targetToggle}
                        </button>
                        <button type="button" class="btn btn-ghost btn-xs gradle-remove-dep-btn" data-coord="${this.#escAttr(d.coordinate)}" title="Remove dependency from Gradle" style="color:var(--error);padding:2px 6px">
                          <span class="material-symbols-outlined" style="font-size:14px">delete</span>
                        </button>
                      </div>
                    </div>
                    `;
                  }).join('')}
                </div>
              </div>
            ` : ''}
          </div>
        </div>
        ` : ''}

        <!-- ── Source-Based External Dependency Automation Panel ── -->
        <div class="gradle-deps-panel" id="gradle-source-deps-panel" style="${this.#showDepsPanel ? '' : 'display:none'};margin-bottom:var(--sp-4)">
          <div class="gradle-deps-header">
            <div style="display:flex;align-items:center;gap:8px">
              <span class="material-symbols-outlined" style="color:var(--accent)">auto_fix_high</span>
              <span style="font-weight:600">Source Code External Dependency Automation</span>
              <span class="badge ${srcDeps.missingCount > 0 ? 'badge-warning' : 'badge-success'}" style="font-size:11px">
                ${srcDeps.missingCount > 0 ? `${srcDeps.missingCount} Missing from Gradle` : 'All Dependencies Declared'}
              </span>
            </div>

            <div style="display:flex;align-items:center;gap:var(--sp-2);flex-wrap:wrap">
              ${srcDeps.missingCount > 0 ? `
                <button type="button" class="btn btn-primary btn-xs" id="gradle-inject-all-missing-btn" title="Inject all missing dependencies into ${isBuildGradle ? this.#escAttr(activeFile) : 'build.gradle'}">
                  <span class="material-symbols-outlined" style="font-size:14px">auto_fix_high</span>Auto-Inject All Missing to ${isBuildGradle ? this.#escHtml(activeFile.split('/').pop()) : 'build.gradle'}
                </button>
              ` : ''}
              <button type="button" class="btn btn-ghost btn-xs" id="gradle-toggle-deps-inner-btn">
                <span class="material-symbols-outlined" style="font-size:14px">close</span>
              </button>
            </div>
          </div>

          <div class="gradle-deps-body">
            <!-- Left: Dependencies Column -->
            <div class="gradle-deps-list-col">
              <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:4px">
                <span style="font-size:var(--text-xs);font-weight:600;color:var(--text-secondary)">
                  Detected External Dependencies (${srcDeps.totalCount})
                </span>
                <span style="font-size:11px;color:var(--text-muted)">Click to select source files</span>
              </div>

              <div id="gradle-deps-items" style="display:flex;flex-direction:column;gap:6px;max-height:220px;overflow-y:auto;padding-right:2px">
                ${this.#renderDepsList(srcDeps, activeFile)}
              </div>
            </div>

            <!-- Right: Source Files Column -->
            <div class="gradle-deps-list-col">
              <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:4px">
                <span style="font-size:var(--text-xs);font-weight:600;color:var(--text-secondary)">
                  ${this.#selectedDepCoord ? `Source Files using "${this.#selectedDepCoord.split(':')[1] || this.#selectedDepCoord}"` : 'Source Files in Project'}
                </span>
                ${this.#selectedDepCoord ? `
                  <button type="button" class="btn btn-ghost btn-xs" id="gradle-clear-dep-selection" style="padding:1px 6px">
                    Show All (${srcDeps.sourceFiles.length})
                  </button>
                ` : ''}
              </div>

              <!-- Source file selector dropdown -->
              <div style="margin-bottom:6px">
                <select id="gradle-source-file-select" class="input input-sm" style="height:30px;font-size:var(--text-xs)">
                  <option value="">— Auto-Select Source File to Inspect Dependencies —</option>
                  ${srcDeps.sourceFiles.map(sf => `
                    <option value="${this.#escAttr(sf.path)}" ${this.#selectedSourceFile === sf.path ? 'selected' : ''}>
                      ${this.#escHtml(sf.fileName)} (${sf.dependencies.length} deps) - ${this.#escHtml(sf.path)}
                    </option>
                  `).join('')}
                </select>
              </div>

              <div id="gradle-source-files-items" style="display:flex;flex-direction:column;gap:4px;max-height:180px;overflow-y:auto;padding-right:2px">
                ${this.#renderSourceFilesList(srcDeps)}
              </div>
            </div>
          </div>
        </div>

        <!-- ── Main Gradle Layout ── -->
        <div class="gradle-layout">
          <div class="card gradle-files-card">
            <div class="card-header" style="display:flex;align-items:center;justify-content:space-between">
              <div style="display:flex;align-items:center;gap:6px">
                <span class="material-symbols-outlined">description</span>Gradle Files
              </div>
              <span class="badge badge-neutral" style="font-size:10px">${gradleFiles.length} files</span>
            </div>
            <div id="gradle-file-list" class="gradle-file-list">
              ${gradleFiles.length === 0
                ? '<div class="gradle-file-list-empty">No Gradle files found</div>'
                : gradleFiles.map(f => `
                  <div class="settings-nav-item${f === activeFile ? ' active' : ''}" data-file="${this.#escAttr(f)}" role="button" tabindex="0">
                    <span class="material-symbols-outlined" style="font-size:16px">description</span>
                    <span class="gradle-file-name" title="${this.#escAttr(f)}">${this.#escHtml(f)}</span>
                  </div>`).join('')}
            </div>
          </div>

          <div class="card gradle-editor-card">
            <div class="card-header gradle-editor-header">
              <div style="display:flex;align-items:center;gap:6px;min-width:0;overflow:hidden">
                <span class="material-symbols-outlined" style="color:var(--accent)">code</span>
                <span class="gradle-active-file" id="gradle-active-file" title="${this.#escAttr(activeFile || '—')}">${this.#escHtml(activeFile || '—')}</span>
              </div>
              <div class="gradle-editor-actions">
                <button type="button" class="btn btn-ghost btn-sm btn-icon" id="gradle-open-editor-btn" title="Open in Editor" aria-label="Open in Editor">
                  <span class="material-symbols-outlined">open_in_new</span>
                </button>
              </div>
            </div>
            <div class="gradle-editor-body" style="height:480px">
              <textarea id="gradle-editor-textarea" class="editor-textarea" spellcheck="false" style="width:100%;height:100%;font-family:var(--font-mono, monospace);font-size:13px;line-height:1.5;padding:12px;background:var(--bg-card);color:var(--text-primary);border:none;resize:none;outline:none">${this.#escHtml(activeContent)}</textarea>
            </div>
          </div>
        </div>
      </div>`;

    this.#bindEvents(gradleFiles);
  }

  #renderValidationBanner() {
    if (!this.#validationReport) return '';

    if (this.#validationReport.valid && this.#validationReport.warnings.length === 0) {
      return `
        <div style="padding:10px 14px;background:rgba(16, 185, 129, 0.12);border:1px solid rgba(16, 185, 129, 0.35);border-radius:6px;display:flex;align-items:center;justify-content:space-between">
          <div style="display:flex;align-items:center;gap:8px;color:#10B981;font-size:13px">
            <span class="material-symbols-outlined" style="font-size:18px">check_circle</span>
            <strong>Gradle Syntax Valid:</strong> ${this.#escHtml(this.#activeFile)} has no syntax or configuration errors.
          </div>
          <button type="button" class="btn btn-ghost btn-xs" id="gradle-dismiss-val-btn" style="padding:2px 4px">
            <span class="material-symbols-outlined" style="font-size:14px">close</span>
          </button>
        </div>`;
    }

    const hasErrors = this.#validationReport.errors.length > 0;
    const bgColor = hasErrors ? 'rgba(239, 68, 68, 0.12)' : 'rgba(245, 158, 11, 0.12)';
    const borderColor = hasErrors ? 'rgba(239, 68, 68, 0.35)' : 'rgba(245, 158, 11, 0.35)';
    const textColor = hasErrors ? '#EF4444' : '#F59E0B';
    const icon = hasErrors ? 'error' : 'warning';

    return `
      <div style="padding:12px 14px;background:${bgColor};border:1px solid ${borderColor};border-radius:6px">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:6px">
          <div style="display:flex;align-items:center;gap:8px;color:${textColor};font-size:13px">
            <span class="material-symbols-outlined" style="font-size:18px">${icon}</span>
            <strong>${hasErrors ? 'Gradle Configuration Errors Detected' : 'Gradle Configuration Warnings'} (${this.#escHtml(this.#activeFile)}):</strong>
          </div>
          <button type="button" class="btn btn-ghost btn-xs" id="gradle-dismiss-val-btn" style="padding:2px 4px">
            <span class="material-symbols-outlined" style="font-size:14px">close</span>
          </button>
        </div>
        <ul style="margin:0;padding-left:24px;font-size:12px;color:var(--text-primary);line-height:1.5">
          ${this.#validationReport.errors.map(e => `<li style="color:#EF4444;font-weight:500">${this.#escHtml(e)}</li>`).join('')}
          ${this.#validationReport.warnings.map(w => `<li style="color:#F59E0B">${this.#escHtml(w)}</li>`).join('')}
        </ul>
      </div>`;
  }

  #renderDepsList(srcDeps, activeFile) {
    const list = srcDeps.dependencies || [];
    const activeContent = projectService.current?.files?.[activeFile] || '';

    if (!list.length) {
      return `<div style="padding:var(--sp-4);text-align:center;color:var(--text-muted);font-size:var(--text-xs)">No external dependencies detected in source files</div>`;
    }

    return list.map(d => {
      const isSelected = this.#selectedDepCoord === d.coordinate;
      const isPresentInActiveFile = activeContent.includes(d.coordinate) ||
        (d.coordinate.split(':').length >= 2 && activeContent.includes(`${d.coordinate.split(':')[0]}:${d.coordinate.split(':')[1]}`));

      return `
        <div class="gradle-dep-card ${isSelected ? 'selected' : ''} ${d.isDeclared ? 'declared' : 'missing'}" data-coord="${this.#escAttr(d.coordinate)}">
          <div style="display:flex;align-items:center;justify-content:space-between;gap:6px">
            <span style="font-family:var(--font-mono, monospace);font-size:11px;font-weight:600;color:var(--text-primary);overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${this.#escAttr(d.coordinate)}">
              ${this.#escHtml(d.coordinate)}
            </span>
            <span class="badge ${d.isDeclared ? 'badge-success' : 'badge-warning'}" style="font-size:9px;padding:1px 5px;flex-shrink:0">
              ${d.isDeclared ? 'Declared' : 'Missing'}
            </span>
          </div>
          <div style="display:flex;align-items:center;justify-content:space-between;margin-top:4px">
            <span style="font-size:10px;color:var(--text-muted)">${this.#escHtml(d.category)}</span>
            <span style="font-size:10px;color:var(--accent);font-weight:500">${d.sourceFiles.length} file${d.sourceFiles.length === 1 ? '' : 's'} using</span>
          </div>
          <div style="margin-top:6px;display:flex;justify-content:flex-end;gap:6px">
            <button type="button" class="btn btn-xs ${isPresentInActiveFile ? 'btn-ghost' : 'btn-primary'} gradle-insert-dep-btn"
                    data-coord="${this.#escAttr(d.coordinate)}"
                    title="Insert implementation '${this.#escAttr(d.coordinate)}' into active Gradle file">
              <span class="material-symbols-outlined" style="font-size:13px">add</span>
              ${isPresentInActiveFile ? 'Insert Again' : 'Insert implementation'}
            </button>
          </div>
        </div>
      `;
    }).join('');
  }

  #renderSourceFilesList(srcDeps) {
    let files = srcDeps.sourceFiles || [];

    if (this.#selectedDepCoord) {
      files = files.filter(f => f.dependencies.includes(this.#selectedDepCoord));
    }

    if (!files.length) {
      return `<div style="padding:var(--sp-4);text-align:center;color:var(--text-muted);font-size:var(--text-xs)">No source files matching</div>`;
    }

    return files.map(f => {
      const isSelected = this.#selectedSourceFile === f.path;
      return `
        <div class="gradle-source-file-row ${isSelected ? 'active' : ''}" data-path="${this.#escAttr(f.path)}">
          <span class="material-symbols-outlined" style="font-size:14px;color:var(--accent);flex-shrink:0">
            ${f.fileName.endsWith('.kt') ? 'terminal' : 'code'}
          </span>
          <div class="gradle-source-file-path" title="${this.#escAttr(f.path)}">
            <strong>${this.#escHtml(f.fileName)}</strong>
            <span style="opacity:0.7;font-size:10px;display:block">${this.#escHtml(f.path)}</span>
          </div>
          <div style="display:flex;align-items:center;gap:4px;flex-shrink:0">
            <span class="badge" style="font-size:9px;padding:1px 5px">${f.dependencies.length} deps</span>
            <button type="button" class="btn btn-ghost btn-xs gradle-open-src-btn" data-path="${this.#escAttr(f.path)}" title="Open file in Editor" style="padding:2px 4px">
              <span class="material-symbols-outlined" style="font-size:14px">open_in_new</span>
            </button>
          </div>
        </div>
      `;
    }).join('');
  }

  #bindEvents(gradleFiles) {
    const c = this.#container;
    if (!c) return;

    // Dismiss validation banner
    c.querySelector('#gradle-dismiss-val-btn')?.addEventListener('click', () => {
      this.#validationReport = null;
      const banner = c.querySelector('#gradle-validation-banner');
      if (banner) banner.innerHTML = '';
    });

    // Validate Syntax Button
    c.querySelector('#gradle-validate-btn')?.addEventListener('click', () => {
      this.#validateCurrentFile();
    });

    // Scaffold Gradle Button
    c.querySelector('#gradle-scaffold-btn')?.addEventListener('click', () => {
      this.#showScaffoldModal(gradleFiles);
    });

    // Copy Full Content Button
    c.querySelector('#gradle-copy-btn')?.addEventListener('click', async () => {
      const textarea = c.querySelector('#gradle-editor-textarea');
      const content = textarea ? textarea.value : (projectService.current?.files?.[this.#activeFile] || '');
      if (!content) {
        toast.info('File is empty', 'Nothing to copy');
        return;
      }
      const ok = await ClipboardUtil.copyText(content);
      if (ok) {
        toast.success('Copied Full Content', `${this.#activeFile} copied to clipboard`);
      } else {
        toast.error('Copy failed', 'Please select text and copy manually');
      }
    });

    // Download Button
    c.querySelector('#gradle-download-btn')?.addEventListener('click', () => {
      const textarea = c.querySelector('#gradle-editor-textarea');
      const content = textarea ? textarea.value : (projectService.current?.files?.[this.#activeFile] || '');
      const filename = this.#activeFile ? this.#activeFile.split('/').pop() : 'build.gradle';
      WebViewBridge.downloadFile(filename, content, 'text/plain;charset=utf-8');
      toast.info('Downloaded', `${filename} saved.`);
    });

    // Save active file with Pre-Validation Check
    c.querySelector('#gradle-save-btn')?.addEventListener('click', async () => {
      await this.#saveActiveFile(gradleFiles);
    });

    // Open active file in Editor
    c.querySelector('#gradle-open-editor-btn')?.addEventListener('click', () => {
      if (!this.#activeFile) return;
      const content = c.querySelector('#gradle-editor-textarea')?.value || '';
      eventBus.emit('editor:openFile', { path: this.#activeFile, content });
      eventBus.emit('router:navigate', { pageId: 'editor' });
    });

    // File loading in sidebar
    const loadFile = (file) => {
      this.#activeFile = file;
      this.#validationReport = null;
      this.#render(gradleFiles);
    };

    c.querySelector('#gradle-file-list')?.addEventListener('click', e => {
      const item = e.target.closest('[data-file]');
      if (item) loadFile(item.dataset.file);
    });

    // Consumer Resolution Test Buttons
    c.querySelector('#gradle-test-consumer-btn')?.addEventListener('click', () => {
      ConsumerResolutionModal.show();
    });
    c.querySelector('#gradle-quick-audit-btn')?.addEventListener('click', () => {
      ConsumerResolutionModal.show();
    });

    // Live Scope Recommendation on input & Enter key support
    const depInput = c.querySelector('#gradle-custom-dep-input');
    if (depInput) {
      depInput.addEventListener('input', () => this.#updateScopeRecommendation());
      depInput.addEventListener('keydown', async (e) => {
        if (e.key === 'Enter') {
          e.preventDefault();
          await this.#addCustomDependency(gradleFiles);
        }
      });
    }

    // Quick Presets
    c.querySelectorAll('.gradle-preset-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const input = c.querySelector('#gradle-custom-dep-input');
        const configSel = c.querySelector('#gradle-custom-dep-config');
        if (input) input.value = btn.dataset.coord || '';
        if (configSel && btn.dataset.config) configSel.value = btn.dataset.config;
        this.#updateScopeRecommendation();
      });
    });

    // Toggle Dependency Scope Button (api <-> implementation)
    c.querySelectorAll('.gradle-toggle-scope-btn').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const coord = btn.dataset.coord;
        const targetScope = btn.dataset.target;
        await this.#toggleDependencyScope(coord, targetScope, gradleFiles);
      });
    });

    // Add Dependency Button
    c.querySelector('#gradle-add-dep-btn')?.addEventListener('click', async () => {
      await this.#addCustomDependency(gradleFiles);
    });

    // Remove Dependency Button
    c.querySelectorAll('.gradle-remove-dep-btn').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const coord = btn.dataset.coord;
        await this.#removeDependency(coord, gradleFiles);
      });
    });

    // Toggle Source Dependencies panel
    c.querySelector('#gradle-toggle-deps-inner-btn')?.addEventListener('click', () => {
      this.#showDepsPanel = false;
      const panel = c.querySelector('#gradle-source-deps-panel');
      if (panel) panel.style.display = 'none';
    });

    // ── Interactive Dependency & Source File Automation ──
    this.#bindDepsPanelEvents();
  }

  #bindDepsPanelEvents() {
    const c = this.#container;
    if (!c) return;

    // Automatic file selection: clicking a dependency card
    c.querySelectorAll('.gradle-dep-card').forEach(card => {
      card.addEventListener('click', (e) => {
        if (e.target.closest('.gradle-insert-dep-btn')) return;
        const coord = card.dataset.coord;
        this.#selectedDepCoord = (this.#selectedDepCoord === coord) ? null : coord;
        this.#updateDepsPanel();
      });
    });

    // Clicking a source file row
    c.querySelectorAll('.gradle-source-file-row').forEach(row => {
      row.addEventListener('click', (e) => {
        if (e.target.closest('.gradle-open-src-btn')) return;
        const filePath = row.dataset.path;
        this.#selectedSourceFile = filePath;
        const selectEl = c.querySelector('#gradle-source-file-select');
        if (selectEl) selectEl.value = filePath;

        const fileEntry = this.#sourceDeps?.sourceFiles?.find(f => f.path === filePath);
        if (fileEntry && fileEntry.dependencies.length > 0) {
          this.#selectedDepCoord = fileEntry.dependencies[0];
        }
        this.#updateDepsPanel();
      });
    });

    // Source file dropdown selection
    c.querySelector('#gradle-source-file-select')?.addEventListener('change', (e) => {
      const filePath = e.target.value;
      this.#selectedSourceFile = filePath || null;
      if (filePath) {
        const fileEntry = this.#sourceDeps?.sourceFiles?.find(f => f.path === filePath);
        if (fileEntry && fileEntry.dependencies.length > 0) {
          this.#selectedDepCoord = fileEntry.dependencies[0];
        }
      } else {
        this.#selectedDepCoord = null;
      }
      this.#updateDepsPanel();
    });

    // Clear dependency selection
    c.querySelector('#gradle-clear-dep-selection')?.addEventListener('click', () => {
      this.#selectedDepCoord = null;
      this.#selectedSourceFile = null;
      const selectEl = c.querySelector('#gradle-source-file-select');
      if (selectEl) selectEl.value = '';
      this.#updateDepsPanel();
    });

    // Open source file in Editor
    c.querySelectorAll('.gradle-open-src-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const path = btn.dataset.path;
        const content = projectService.current?.files?.[path] || '';
        eventBus.emit('editor:openFile', { path, content });
        eventBus.emit('router:navigate', { pageId: 'editor' });
      });
    });

    // ── Single Dependency Insertion Automation ──
    c.querySelectorAll('.gradle-insert-dep-btn').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const coord = btn.dataset.coord;
        await this.#insertDependencyIntoActiveFile(coord);
      });
    });

    // ── Auto-Inject All Missing into Active Gradle File ──
    c.querySelector('#gradle-inject-all-missing-btn')?.addEventListener('click', async () => {
      await this.#injectAllMissingIntoActiveFile();
    });
  }

  #validateCurrentFile() {
    if (!this.#activeFile) return;
    const textarea = this.#container.querySelector('#gradle-editor-textarea');
    const content = textarea ? textarea.value : (projectService.current?.files?.[this.#activeFile] || '');

    this.#validationReport = PreValidatorService.validateGradleSyntax(content, this.#activeFile);

    const banner = this.#container.querySelector('#gradle-validation-banner');
    if (banner) {
      banner.innerHTML = this.#renderValidationBanner();
      this.#container.querySelector('#gradle-dismiss-val-btn')?.addEventListener('click', () => {
        this.#validationReport = null;
        banner.innerHTML = '';
      });
    }

    if (this.#validationReport.valid) {
      toast.success('Validation Passed', `${this.#activeFile} syntax is valid.`);
    } else {
      toast.error('Validation Errors', `${this.#validationReport.errors.length} error(s) found in ${this.#activeFile}`);
    }
  }

  async #saveActiveFile(gradleFiles) {
    if (!this.#activeFile) return;
    const content = this.#container.querySelector('#gradle-editor-textarea')?.value || '';

    // Validate before saving — do not silently save invalid code!
    const check = PreValidatorService.validateGradleSyntax(content, this.#activeFile);
    this.#validationReport = check;

    const banner = this.#container.querySelector('#gradle-validation-banner');
    if (banner) banner.innerHTML = this.#renderValidationBanner();

    if (!check.valid) {
      toast.error('Cannot Save: Syntax Errors', check.errors[0] || 'Fix syntax errors before saving');
      return;
    }

    await projectService.setFile(this.#activeFile, content);
    this.#sourceDeps = SourceDependencyAnalyzer.analyze(projectService.current?.files || {});
    toast.success('Saved Successfully', this.#activeFile.split('/').pop());
    consoleService.success(`Saved Gradle file: ${this.#activeFile}`);

    this.#render(gradleFiles);
  }

  #updateScopeRecommendation() {
    const input = this.#container.querySelector('#gradle-custom-dep-input');
    const box = this.#container.querySelector('#gradle-scope-recommendation-box');
    if (!box) return;

    const rawVal = input?.value.trim() || '';
    if (!rawVal || rawVal.length < 3) {
      box.style.display = 'none';
      box.innerHTML = '';
      return;
    }

    const files = projectService.current?.files || {};
    const analysis = TransitiveDependencyEngine.analyzeScope(rawVal, files);
    const repos = TransitiveDependencyEngine.detectRequiredRepositories(rawVal);
    const transitiveChildren = TransitiveDependencyEngine.resolveTransitiveGraph([
      { coordinate: rawVal, scope: analysis.scope }
    ]).filter(g => g.isTransitive);

    box.style.display = 'block';
    box.innerHTML = `
      <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:8px">
        <div style="flex:1">
          <div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap">
            <span class="badge ${analysis.scope === 'api' ? 'badge-primary' : 'badge-success'}" style="font-size:11px;font-weight:700">
              ⚡ Recommended Scope: ${analysis.scope.toUpperCase()}
            </span>
            <span style="font-size:11px;color:var(--text-muted)">Confidence: ${Math.round(analysis.confidence * 100)}%</span>
            ${repos.map(r => `<span class="badge" style="font-size:9px;background:var(--bg-surface)">${r} repo</span>`).join('')}
          </div>
          <div style="font-size:11px;color:var(--text-secondary);margin-top:4px">
            ${analysis.reason}
          </div>
          ${transitiveChildren.length > 0 ? `
            <div style="font-size:10px;color:var(--text-muted);margin-top:4px">
              <strong>Transitive Dependencies:</strong> ${transitiveChildren.map(tc => `${tc.coordinate} (${tc.effectiveScope})`).join(', ')}
            </div>
          ` : ''}
        </div>
      </div>
    `;
  }

  async #toggleDependencyScope(coord, targetScope, gradleFiles) {
    if (!this.#activeFile || !coord) return;
    const textarea = this.#container.querySelector('#gradle-editor-textarea');
    const content = textarea ? textarea.value : (projectService.current?.files?.[this.#activeFile] || '');

    projectService.createBackup(`Before updating scope of ${coord} to ${targetScope}`);

    const res = GradlePatcher.addDependencies(content, coord, targetScope);
    if (res.updated.length > 0 || res.added.length > 0) {
      const syntaxCheck = PreValidatorService.validateGradleSyntax(res.content, this.#activeFile);
      if (!syntaxCheck.valid) {
        await projectService.restoreBackup();
        toast.error('Gradle Syntax Error', syntaxCheck.errors[0] || 'Brace mismatch detected');
        return;
      }

      if (textarea) textarea.value = res.content;
      await projectService.setFile(this.#activeFile, res.content);

      toast.success('Scope Updated', `Changed ${coord} to ${targetScope}`);
      consoleService.success(`Updated dependency scope: ${coord} -> ${targetScope}`);

      this.#sourceDeps = SourceDependencyAnalyzer.analyze(projectService.current?.files || {});
      this.#render(gradleFiles);
    } else {
      toast.info('No Change', `${coord} is already configured with scope '${targetScope}'`);
    }
  }

  async #addCustomDependency(gradleFiles) {
    const input = this.#container.querySelector('#gradle-custom-dep-input');
    const configSel = this.#container.querySelector('#gradle-custom-dep-config');
    const autoSyncCb = this.#container.querySelector('#gradle-auto-sync-transitive');
    const rawVal = input?.value.trim();
    let config = configSel?.value || 'auto';

    if (!rawVal) {
      toast.error('Missing Dependency', 'Enter a Maven coordinate or library name');
      return;
    }

    const valResult = PreValidatorService.validateDependency(rawVal, config === 'auto' ? 'implementation' : config);
    if (!valResult.valid) {
      toast.error('Invalid Dependency Syntax', valResult.error);
      return;
    }

    const { coordinate } = valResult.parsed;
    const p = projectService.current;
    const files = p?.files || {};

    // Auto-detect scope if 'auto' chosen
    if (config === 'auto') {
      const scopeAnalysis = TransitiveDependencyEngine.analyzeScope(coordinate, files);
      config = scopeAnalysis.scope;
      consoleService.info(`Automatic Transitive Scope for ${coordinate}: ${config} (${scopeAnalysis.reason})`);
    }

    // Take a safety backup snapshot before applying modifications
    projectService.createBackup(`Before adding dependency ${coordinate}`);

    const textarea = this.#container.querySelector('#gradle-editor-textarea');
    const currentContent = textarea ? textarea.value : (files[this.#activeFile] || '');

    const res = GradlePatcher.addDependencies(currentContent, coordinate, config);
    if (res.skipped.length > 0 && res.added.length === 0 && res.updated.length === 0) {
      toast.info('Already Declared', `${coordinate} is already present with scope '${config}'`);
      return;
    }

    let updatedFileContent = res.content;

    // Validate Gradle syntax and brace balance on the resulting file
    const syntaxCheck = PreValidatorService.validateGradleSyntax(updatedFileContent, this.#activeFile);
    if (!syntaxCheck.valid) {
      await projectService.restoreBackup();
      toast.error('Gradle Syntax Error', syntaxCheck.errors[0] || 'Unmatched braces detected. Rolled back to safety backup.');
      consoleService.error(`Gradle syntax validation failed: ${syntaxCheck.errors.join('; ')}. Rolled back.`);
      return;
    }

    // 1. ALWAYS SAVE TO PROJECTSERVICE IMMEDIATELY FOR ACTIVE FILE
    await projectService.setFile(this.#activeFile, updatedFileContent);

    // Auto-sync transitive repositories (e.g. JitPack) into settings.gradle if enabled
    let syncedMsg = '';
    if (autoSyncCb && autoSyncCb.checked) {
      try {
        const requiredRepos = TransitiveDependencyEngine.detectRequiredRepositories(coordinate);
        const settingsPath = files['settings.gradle.kts'] ? 'settings.gradle.kts' : 'settings.gradle';
        const originalSettings = String(projectService.getFile(settingsPath) || files[settingsPath] || '');
        const needsJitpack = requiredRepos.includes('jitpack');

        if (needsJitpack) {
          const patchedSettings = TransitiveDependencyEngine.patchSettingsGradle(originalSettings, { includeJitpack: true });
          if (patchedSettings !== originalSettings) {
            await projectService.setFile(settingsPath, patchedSettings);
            syncedMsg += ', settings.gradle (JitPack repo)';
          }
        }
      } catch (err) {
        consoleService.warn(`Transitive auto-sync notice: ${err.message}`);
      }
    }

    if (textarea) textarea.value = updatedFileContent;
    if (input) input.value = '';
    const box = this.#container.querySelector('#gradle-scope-recommendation-box');
    if (box) box.style.display = 'none';

    const actionText = res.updated.length > 0 ? 'Scope Updated' : 'Dependency Added';
    toast.success(actionText, `${config} '${coordinate}'${syncedMsg ? ` (${syncedMsg})` : ''}`);
    consoleService.success(`${actionText}: ${config} '${coordinate}' to ${this.#activeFile}`);

    this.#sourceDeps = SourceDependencyAnalyzer.analyze(projectService.current?.files || {});
    this.#render(gradleFiles);
  }

  async #removeDependency(coord, gradleFiles) {
    if (!this.#activeFile || !coord) return;
    const textarea = this.#container.querySelector('#gradle-editor-textarea');
    const currentContent = textarea ? textarea.value : (projectService.current?.files?.[this.#activeFile] || '');

    projectService.createBackup(`Before removing dependency ${coord}`);

    const res = GradlePatcher.removeDependency(currentContent, coord);
    if (!res.removed) {
      toast.info('Not Found', `Dependency ${coord} not found in this file`);
      return;
    }

    const syntaxCheck = PreValidatorService.validateGradleSyntax(res.content, this.#activeFile);
    if (!syntaxCheck.valid) {
      await projectService.restoreBackup();
      toast.error('Syntax Error', 'Removing dependency caused syntax issues. Rolled back.');
      return;
    }

    if (textarea) textarea.value = res.content;
    await projectService.setFile(this.#activeFile, res.content);
    toast.info('Dependency Removed', `Removed ${coord} from ${this.#activeFile.split('/').pop()}`);
    consoleService.info(`Removed dependency ${coord} from ${this.#activeFile}`);

    this.#sourceDeps = SourceDependencyAnalyzer.analyze(projectService.current?.files || {});
    this.#render(gradleFiles);
  }

  #showScaffoldModal(gradleFiles) {
    const p = projectService.current;
    const isKts = Boolean(this.#activeFile?.endsWith('.kts'));
    modal.show({
      title: 'Scaffold Gradle Configuration',
      content: `
        <div style="display:flex;flex-direction:column;gap:12px">
          <p style="font-size:13px;color:var(--text-secondary);margin:0">
            Generate standard, production-ready Gradle build configuration for your project type:
          </p>
          <div class="form-group">
            <label class="form-label">Select Project / Build Type:</label>
            <select id="gradle-scaffold-type-select" class="form-input">
              <option value="android-library" selected>Android Library (AAR) — com.android.library, maven-publish, Java 17</option>
              <option value="pure-java">Pure Java Library (JAR) — java-library, maven-publish, Java 17, JUnit 5</option>
              <option value="android-app">Android Application (APK+AAB) — com.android.application, Java 17</option>
              <option value="root">Root build.gradle — plugins block, clean task</option>
            </select>
          </div>
          <div style="font-size:11px;color:var(--text-muted);display:flex;align-items:center;gap:4px">
            <span class="material-symbols-outlined" style="font-size:14px;color:var(--accent)">info</span>
            Format: <strong>${isKts ? 'Kotlin DSL (.gradle.kts)' : 'Groovy DSL (.gradle)'}</strong> based on active file <code>${this.#escHtml(this.#activeFile || 'build.gradle')}</code>
          </div>
          <div style="font-size:11px;color:var(--warning);display:flex;align-items:center;gap:4px">
            <span class="material-symbols-outlined" style="font-size:14px">warning</span>
            This will update the active editor with the chosen standard scaffold. You can review and save.
          </div>
        </div>
      `,
      buttons: [
        { label: 'Cancel', variant: 'secondary', onClick: () => modal.close() },
        {
          label: 'Generate Scaffold',
          variant: 'primary',
          onClick: async () => {
            const type = document.getElementById('gradle-scaffold-type-select')?.value || 'android-library';
            modal.close();

            const generated = GradlePatcher.generateStandardBuild(type, {
              name: p?.name || 'MyLibrary',
              packageName: p?.packageName || 'com.example.library',
              version: p?.version || '1.0.0',
              compileSdk: p?.compileSdk || '34',
              minSdk: p?.minSdk || '21',
              targetSdk: p?.targetSdk || '34',
              author: p?.author || 'Developer',
              repoName: p?.repoName || (p?.name || 'library').toLowerCase().replace(/[^a-z0-9_-]/g, '-'),
              description: p?.description || 'Android Library',
              license: p?.license || 'Apache-2.0',
              isKts: isKts,
            });

            const textarea = this.#container.querySelector('#gradle-editor-textarea');
            if (textarea) {
              textarea.value = generated;
              this.#validateCurrentFile();
              if (this.#activeFile) {
                await projectService.setFile(this.#activeFile, generated);
              }
              toast.success('Scaffold Generated', `Standard ${type} configuration loaded in editor`);
            }
          }
        }
      ]
    });
  }

  #updateDepsPanel() {
    const c = this.#container;
    if (!c) return;

    const p = projectService.current;
    this.#sourceDeps = SourceDependencyAnalyzer.analyze(p?.files || {});

    const depsItems = c.querySelector('#gradle-deps-items');
    const srcItems  = c.querySelector('#gradle-source-files-items');

    if (depsItems) {
      depsItems.innerHTML = this.#renderDepsList(this.#sourceDeps, this.#activeFile);
    }
    if (srcItems) {
      srcItems.innerHTML = this.#renderSourceFilesList(this.#sourceDeps);
    }

    this.#bindDepsPanelEvents();
  }

  async #insertDependencyIntoActiveFile(coord) {
    if (!this.#activeFile) {
      toast.error('No file open', 'Select a build.gradle file first');
      return;
    }

    const files = projectService.current?.files || {};
    const scopeAnalysis = TransitiveDependencyEngine.analyzeScope(coord, files);
    const scope = scopeAnalysis.scope;

    const textarea = this.#container.querySelector('#gradle-editor-textarea');
    const currentContent = textarea ? textarea.value : (files[this.#activeFile] || '');

    const res = GradlePatcher.addDependencies(currentContent, coord, scope);

    if (textarea) {
      textarea.value = res.content;
    }

    await projectService.setFile(this.#activeFile, res.content);
    consoleService.success(`Gradle Automation: inserted ${scope} '${coord}' (${scopeAnalysis.reason}) into ${this.#activeFile}`);
    toast.success('Inserted into Gradle', `${scope} '${coord}' added`);

    const p = projectService.current;
    const gradleFiles = Object.keys(p?.files || {}).filter(k =>
      k.endsWith('.gradle') || k.endsWith('.properties') || k === 'jitpack.yml' ||
      k.endsWith('gradle-wrapper.properties') || k.endsWith('.gradle.kts')
    );
    this.#sourceDeps = SourceDependencyAnalyzer.analyze(p?.files || {});
    this.#render(gradleFiles);
  }

  async #injectAllMissingIntoActiveFile() {
    const p = projectService.current;
    if (!p) return;

    let targetFile = this.#activeFile;
    if (!targetFile || !targetFile.includes('build.gradle')) {
      targetFile = Object.keys(p.files || {}).find(k => k.includes('build.gradle')) || this.#activeFile;
    }

    if (!targetFile) {
      toast.error('No build.gradle found', 'Could not locate a Gradle build file');
      return;
    }

    const textarea = this.#container.querySelector('#gradle-editor-textarea');
    let currentContent = (targetFile === this.#activeFile && textarea)
      ? textarea.value
      : (p.files?.[targetFile] || '');

    const srcDeps = this.#sourceDeps || SourceDependencyAnalyzer.analyze(p.files || {});
    const missing = srcDeps.dependencies.filter(d => !d.isDeclared).map(d => d.coordinate);

    if (missing.length === 0) {
      toast.info('No missing dependencies', 'All detected libraries are already declared');
      return;
    }

    // Classify each missing dependency into api or implementation
    let addedCount = 0;
    for (const coord of missing) {
      const scopeAnalysis = TransitiveDependencyEngine.analyzeScope(coord, p.files || {});
      const res = GradlePatcher.addDependencies(currentContent, coord, scopeAnalysis.scope);
      currentContent = res.content;
      addedCount += res.added.length;
    }

    if (targetFile === this.#activeFile && textarea) {
      textarea.value = currentContent;
    }

    await projectService.setFile(targetFile, currentContent);
    consoleService.success(`Gradle Automation: injected ${addedCount} dependencies with auto-transitive scoping into ${targetFile}`);
    toast.success('Dependencies Injected', `Added ${addedCount} dependencies with auto api/implementation scopes`);

    const gradleFiles = Object.keys(p?.files || {}).filter(k =>
      k.endsWith('.gradle') || k.endsWith('.properties') || k === 'jitpack.yml' ||
      k.endsWith('gradle-wrapper.properties') || k.endsWith('.gradle.kts')
    );
    this.#sourceDeps = SourceDependencyAnalyzer.analyze(p?.files || {});
    this.#render(gradleFiles);
  }

  #noProject() {
    return `<div class="page-container"><div class="card empty-state-card" style="padding:48px 24px;text-align:center;display:flex;flex-direction:column;align-items:center;gap:12px">
      <span class="material-symbols-outlined empty-state-icon" style="font-size:48px;color:var(--text-muted)">build</span>
      <div class="empty-state-text" style="font-size:16px;font-weight:600">No project open. Generate or import a project first.</div>
      <button type="button" class="btn btn-primary" id="gradle-goto-gen-btn" style="margin-top:12px">
        <span class="material-symbols-outlined">auto_fix_high</span> Go to Project Generator
      </button>
    </div></div>`;
  }

  #escHtml(s) {
    if (!s) return '';
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }

  #escAttr(s) {
    if (!s) return '';
    return String(s).replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;');
  }
}
