/**
 * JitPackPage — Comprehensive JitPack File Generator & Manager.
 *
 * Detects & Analyzes:
 * - settings.gradle / settings.gradle.kts (module hierarchy)
 * - root & module-level build.gradle / build.gradle.kts (library vs sample app)
 * - gradle-wrapper.properties (Gradle version & JDK compatibility)
 * - existing jitpack.yml (reuse, merge, or update without blind overwrite)
 *
 * Features:
 * - Module-qualified Gradle command generation (:lib:assembleRelease :lib:publishToMavenLocal)
 * - Strict task safety: Prevents invalid publishToMavenLocal on sample/application modules
 * - Interactive module task selection matrix
 * - Gradle / AGP / JDK compatibility validator
 * - Live YAML preview, in-place editor, copy, download, and ZIP export
 * - Retains Maven publication blocks, consumer snippets, and badges
 */
import { projectService } from '../modules/storage/ProjectService.js';
import { githubHistory } from '../modules/github/GitHubHistory.js';
import { toast } from '../ui/ToastService.js';
import { WebViewBridge } from '../core/WebViewBridge.js';
import { consoleService } from '../modules/storage/ConsoleService.js';
import { MavenPublishGenerator } from '../modules/generator/MavenPublishGenerator.js';
import { JitPackAnalyzerEngine } from '../modules/generator/JitPackAnalyzerEngine.js';
import { JitPackBuildService } from '../modules/generator/JitPackBuildService.js';
import { zipService } from '../modules/zip/ZipService.js';
import { ClipboardUtil } from '../core/ClipboardUtil.js';
import { eventBus } from '../core/EventBus.js';

export class JitPackPage {
  #container = null;
  #analysis = null;
  #moduleTasks = [];
  #state = {
    groupId: '',
    artifactId: '',
    version: '1.0.0',
    author: '',
    repoName: '',
    tag: 'v1.0.0',
    targetModule: 'library',
    activeTab: 'jitpack', // Default tab is now JitPack Generator!
    dslMode: 'groovy',
    jdkVersion: 'openjdk17',
    clean: true,
    stacktrace: true,
    customTasks: '',
    beforeInstallCommands: [
      'git config --global --add safe.directory /build || true',
      'chmod +x gradlew'
    ],
    yamlContent: '',
    existingStatus: 'new', // 'new' | 'existing_detected' | 'reused' | 'merged'
    monitorOwner: '',
    monitorRepo: '',
    monitorTag: '',
    buildLog: '',
    buildStatus: null, // null | 'ok' | 'error' | 'building' | 'not_found'
    buildStatusList: {},
    diagnosedIssues: [],
    isFetchingLog: false,
    isFetchingStatus: false,
    logFilter: '',
    retryCount: 0,
    autoRetryActive: false,
    autoRetryTimer: null,
  };

  async init(container, params = {}) {
    this.#container = container;
    const p = projectService.current;
    let files = p?.files || {};

    // Auto-detect metadata
    const detected = MavenPublishGenerator.detectMetadata(files, p);
    this.#state.groupId = detected.groupId;
    this.#state.artifactId = detected.artifactId;
    this.#state.version = detected.version;
    this.#state.author = detected.author || (p?.author || 'USERNAME');
    this.#state.repoName = detected.repoName || (p?.repoName || 'REPO');
    this.#state.tag = detected.tag;
    this.#state.targetModule = detected.targetModule;

    // Prefill monitor targets
    this.#state.monitorOwner = params?.owner || this.#state.author;
    this.#state.monitorRepo = params?.repo || this.#state.repoName;
    this.#state.monitorTag = params?.tag || this.#state.tag;
    if (params?.activeTab) {
      this.#state.activeTab = params.activeTab;
    }

    // Load existing publish records
    await githubHistory.load();

    // Run deep Gradle analysis
    this.#runAnalysis(files);

    // Initial check on project files for potential JitPack issues
    this.#state.diagnosedIssues = JitPackBuildService.diagnose('', files);

    this.#render();
  }

  /**
   * Run Gradle project analysis and configure module task models.
   */
  #runAnalysis(files) {
    // If no project is open or project is empty, generate baseline multi-module analysis
    if (!files || Object.keys(files).length === 0) {
      files = {
        'settings.gradle': "include ':library', ':app'\nrootProject.name = 'MyAndroidLib'",
        'build.gradle': "plugins {\n  id 'com.android.library' version '8.2.0' apply false\n  id 'com.android.application' version '8.2.0' apply false\n}",
        'library/build.gradle': "plugins {\n  id 'com.android.library'\n  id 'maven-publish'\n}\n\nandroid {\n  namespace 'com.example.lib'\n  compileSdk 34\n}",
        'app/build.gradle': "plugins {\n  id 'com.android.application'\n}\n\nandroid {\n  namespace 'com.example.app'\n  compileSdk 34\n}",
        'gradle/wrapper/gradle-wrapper.properties': "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip",
      };
    }

    this.#analysis = JitPackAnalyzerEngine.analyze(files);
    this.#state.jdkVersion = this.#analysis.recommendedJdk;

    // Build module task matrix
    this.#moduleTasks = this.#analysis.modules.map(mod => {
      return {
        name: mod.name,
        type: mod.type,
        typeLabel: mod.typeLabel,
        isLibrary: mod.isLibrary,
        isSample: mod.isSample,
        assembleRelease: true,
        // CRITICAL: publishToMavenLocal ONLY for library modules
        publishToMavenLocal: mod.isLibrary,
      };
    });

    // Check existing jitpack.yml
    if (this.#analysis.hasExistingJitpack) {
      this.#state.existingStatus = 'existing_detected';
      this.#state.yamlContent = this.#analysis.existingJitpackContent;
    } else {
      this.#state.existingStatus = 'new';
      this.#state.yamlContent = this.#generateFreshYaml();
    }
  }

  #generateFreshYaml() {
    return JitPackAnalyzerEngine.generateJitpackYml({
      jdkVersion: this.#state.jdkVersion,
      beforeInstall: this.#state.beforeInstallCommands,
      moduleTasks: this.#moduleTasks,
      customTasks: this.#state.customTasks,
      clean: this.#state.clean,
      stacktrace: this.#state.stacktrace,
    });
  }

  #render() {
    const container = this.#container;
    const p = projectService.current;
    const files = p?.files || {};
    const state = this.#state;
    const analysis = this.#analysis;

    // Validate module tasks
    const validation = JitPackAnalyzerEngine.validate(this.#moduleTasks);

    // Generate consumer & publishing snippets for companion tabs
    const snippets = MavenPublishGenerator.generateConsumerSnippets(state);
    const groovyPublishCode = MavenPublishGenerator.generateGroovyPublishingBlock(state);
    const kotlinPublishCode = MavenPublishGenerator.generateKotlinPublishingBlock(state);
    const records = githubHistory.records || [];

    container.innerHTML = `
      <div class="page-container jitpack-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="jitpack-action-bar">
            <div>
              <h1 class="page-title">
                <span class="material-symbols-outlined">terminal</span>
                JitPack File Generator &amp; Manager
              </h1>
              <p class="page-subtitle">Analyze Gradle project structure, generate module-qualified build commands, and manage jitpack.yml</p>
            </div>
            <div class="jitpack-action-group">
              <button type="button" class="btn btn-secondary" id="jp-reanalyze-btn" title="Re-scan settings.gradle and build.gradle files">
                <span class="material-symbols-outlined">sync</span>
                Analyze Structure
              </button>
              <button type="button" class="btn btn-primary" id="jp-apply-btn" ${!p ? 'disabled' : ''} title="Save jitpack.yml directly into active project">
                <span class="material-symbols-outlined">auto_fix_high</span>
                Apply to Project
              </button>
              <button type="button" class="btn btn-secondary" id="jp-download-yml-btn" title="Download single jitpack.yml file">
                <span class="material-symbols-outlined">file_download</span>
                Download jitpack.yml
              </button>
              <button type="button" class="btn btn-secondary" id="jp-export-zip-btn" title="Export project and configs as ZIP">
                <span class="material-symbols-outlined">folder_zip</span>
                Export ZIP
              </button>
            </div>
          </div>
        </div>

        <div class="jitpack-stack">

          <!-- 1. Project & Gradle Analysis Card -->
          <div class="card">
            <div class="card-header">
              <span class="material-symbols-outlined">analytics</span>
              <span>Gradle Project Structure Analysis</span>
              <span class="badge ${analysis.hasSettingsGradle ? 'badge-success' : 'badge-warning'}" style="margin-left:auto">
                ${analysis.modules.length} Module(s) Detected
              </span>
            </div>
            <div class="card-body">
              <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(220px, 1fr));gap:var(--sp-3)">
                <div style="padding:10px 14px;background:var(--bg-muted);border:1px solid var(--border);border-radius:6px">
                  <div style="font-size:11px;color:var(--text-secondary);text-transform:uppercase;font-weight:600">settings.gradle</div>
                  <div style="font-weight:600;margin-top:2px;display:flex;align-items:center;gap:6px">
                    <span class="material-symbols-outlined" style="font-size:16px;color:${analysis.hasSettingsGradle ? '#22c55e' : '#eab308'}">
                      ${analysis.hasSettingsGradle ? 'check_circle' : 'help'}
                    </span>
                    <span>${analysis.settingsFile || 'Standard Inferred'}</span>
                  </div>
                </div>

                <div style="padding:10px 14px;background:var(--bg-muted);border:1px solid var(--border);border-radius:6px">
                  <div style="font-size:11px;color:var(--text-secondary);text-transform:uppercase;font-weight:600">Gradle Wrapper</div>
                  <div style="font-weight:600;margin-top:2px;display:flex;align-items:center;gap:6px">
                    <span class="material-symbols-outlined" style="font-size:16px;color:#22c55e">check_circle</span>
                    <span>Gradle ${analysis.gradleVersion || '8.7 (Modern)'}</span>
                  </div>
                </div>

                <div style="padding:10px 14px;background:var(--bg-muted);border:1px solid var(--border);border-radius:6px">
                  <div style="font-size:11px;color:var(--text-secondary);text-transform:uppercase;font-weight:600">Compatible JDK</div>
                  <div style="font-weight:600;margin-top:2px;display:flex;align-items:center;gap:6px">
                    <span class="material-symbols-outlined" style="font-size:16px;color:#0284c7">coffee</span>
                    <span>${state.jdkVersion}</span>
                  </div>
                </div>

                <div style="padding:10px 14px;background:var(--bg-muted);border:1px solid var(--border);border-radius:6px">
                  <div style="font-size:11px;color:var(--text-secondary);text-transform:uppercase;font-weight:600">Existing jitpack.yml</div>
                  <div style="font-weight:600;margin-top:2px;display:flex;align-items:center;gap:6px">
                    <span class="material-symbols-outlined" style="font-size:16px;color:${analysis.hasExistingJitpack ? '#0284c7' : '#94a3b8'}">
                      ${analysis.hasExistingJitpack ? 'description' : 'fiber_new'}
                    </span>
                    <span>${analysis.hasExistingJitpack ? 'Existing File Found' : 'Create New'}</span>
                  </div>
                </div>
              </div>

              ${analysis.compatibilityNotes.length > 0 ? `
                <div style="margin-top:var(--sp-3);padding:8px 12px;background:rgba(2,132,199,0.08);border:1px solid rgba(2,132,199,0.25);border-radius:6px;font-size:12px;display:flex;align-items:center;gap:8px">
                  <span class="material-symbols-outlined" style="font-size:16px;color:#0284c7">info</span>
                  <span>${this.#esc(analysis.compatibilityNotes.join(' '))}</span>
                </div>
              ` : ''}

              <!-- Existing jitpack.yml Action Banner (if found) -->
              ${analysis.hasExistingJitpack ? `
                <div style="margin-top:var(--sp-3);padding:12px;background:rgba(234,179,8,0.1);border:1px solid rgba(234,179,8,0.3);border-radius:6px;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-2)">
                  <div style="display:flex;align-items:center;gap:8px">
                    <span class="material-symbols-outlined" style="color:#eab308">inventory_2</span>
                    <div>
                      <strong>Existing <code>jitpack.yml</code> detected:</strong>
                      <span style="font-size:12px;color:var(--text-secondary)">You can reuse your current custom file, merge new module tasks, or generate fresh.</span>
                    </div>
                  </div>
                  <div style="display:flex;gap:6px">
                    <button type="button" class="btn btn-secondary btn-sm" id="jp-reuse-btn">Reuse Existing</button>
                    <button type="button" class="btn btn-secondary btn-sm" id="jp-merge-btn">Merge Tasks</button>
                    <button type="button" class="btn btn-primary btn-sm" id="jp-regen-btn">Regenerate Fresh</button>
                  </div>
                </div>
              ` : ''}
            </div>
          </div>

          <!-- 2. Interactive Module & Task Selection Table -->
          <div class="card">
            <div class="card-header">
              <span class="material-symbols-outlined">checklist</span>
              <span>Module Structure &amp; Gradle Task Allocation</span>
              <div style="margin-left:auto;display:flex;align-items:center;gap:var(--sp-2)">
                <label style="font-size:12px;color:var(--text-secondary);display:flex;align-items:center;gap:4px">
                  <span class="material-symbols-outlined" style="font-size:16px">coffee</span> JDK:
                </label>
                <select class="input select" id="jp-jdk-select" style="width:130px;height:30px;padding:2px 8px;font-size:12px">
                  <option value="openjdk17" ${state.jdkVersion === 'openjdk17' ? 'selected' : ''}>openjdk17 (Rec.)</option>
                  <option value="openjdk21" ${state.jdkVersion === 'openjdk21' ? 'selected' : ''}>openjdk21</option>
                  <option value="openjdk11" ${state.jdkVersion === 'openjdk11' ? 'selected' : ''}>openjdk11</option>
                  <option value="openjdk8"  ${state.jdkVersion === 'openjdk8' ? 'selected' : ''}>openjdk8</option>
                </select>
              </div>
            </div>
            <div class="card-body">
              <div style="font-size:12px;color:var(--text-secondary);margin-bottom:var(--sp-2)">
                Select Gradle build tasks for each module. <em>Notice: Sample and Application modules are automatically barred from <code>publishToMavenLocal</code> to prevent JitPack build crashes.</em>
              </div>

              <div style="overflow-x:auto">
                <table class="version-diff-table">
                  <thead>
                    <tr>
                      <th style="width:160px">Module</th>
                      <th style="width:160px">Module Type</th>
                      <th style="width:180px">assembleRelease</th>
                      <th style="width:260px">publishToMavenLocal</th>
                      <th>Command Preview</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${this.#moduleTasks.map((mod, idx) => `
                      <tr>
                        <td>
                          <strong>:${this.#esc(mod.name)}</strong>
                        </td>
                        <td>
                          <span class="badge ${mod.isLibrary ? 'badge-accent' : 'badge-info'}" style="font-size:11px">
                            ${this.#esc(mod.typeLabel || mod.type)}
                          </span>
                        </td>
                        <td>
                          <label style="display:flex;align-items:center;gap:6px;cursor:pointer">
                            <input type="checkbox" class="jp-task-assemble" data-index="${idx}" ${mod.assembleRelease ? 'checked' : ''} />
                            <span>:${this.#esc(mod.name)}:assembleRelease</span>
                          </label>
                        </td>
                        <td>
                          ${mod.isSample ? `
                            <div style="display:flex;align-items:center;gap:6px">
                              <input type="checkbox" disabled title="Disabled for sample app" />
                              <span style="font-size:11px;color:var(--text-secondary);opacity:0.8">
                                <span class="material-symbols-outlined" style="font-size:13px;vertical-align:middle;color:#eab308">block</span>
                                Not Applicable (Sample App)
                              </span>
                            </div>
                          ` : `
                            <label style="display:flex;align-items:center;gap:6px;cursor:pointer">
                              <input type="checkbox" class="jp-task-publish" data-index="${idx}" ${mod.publishToMavenLocal ? 'checked' : ''} />
                              <span>:${this.#esc(mod.name)}:publishToMavenLocal</span>
                            </label>
                          `}
                        </td>
                        <td>
                          <code style="font-size:11px;background:var(--bg-muted);padding:2px 6px;border-radius:4px">
                            ${[
                              mod.assembleRelease ? `:${mod.name}:assembleRelease` : null,
                              (mod.publishToMavenLocal && !mod.isSample) ? `:${mod.name}:publishToMavenLocal` : null
                            ].filter(Boolean).join(' ') || '(none)'}
                          </code>
                        </td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              </div>

              <!-- General Task Controls -->
              <div style="display:flex;flex-wrap:wrap;gap:var(--sp-4);margin-top:var(--sp-3);align-items:center;padding-top:var(--sp-3);border-top:1px solid var(--border)">
                <label style="display:flex;align-items:center;gap:6px;cursor:pointer;font-size:13px">
                  <input type="checkbox" id="jp-flag-clean" ${state.clean ? 'checked' : ''} />
                  <span>Execute <code>clean</code> before build</span>
                </label>

                <label style="display:flex;align-items:center;gap:6px;cursor:pointer;font-size:13px">
                  <input type="checkbox" id="jp-flag-stacktrace" ${state.stacktrace ? 'checked' : ''} />
                  <span>Include <code>--stacktrace</code> diagnostic flag</span>
                </label>

                <div style="display:flex;align-items:center;gap:6px;flex:1;min-width:240px">
                  <label style="font-size:13px;white-space:nowrap">Custom Tasks:</label>
                  <input class="input" type="text" id="jp-custom-tasks" value="${this.#escAttr(state.customTasks)}" placeholder="e.g. test --info" style="height:32px;font-size:12px" />
                </div>
              </div>
            </div>
          </div>

          <!-- 3. Validation Feedback Alert (if any issues) -->
          ${validation.errors.length > 0 ? `
            <div style="padding:12px;background:rgba(239,68,68,0.1);border:1px solid rgba(239,68,68,0.3);border-radius:6px;display:flex;align-items:flex-start;gap:8px">
              <span class="material-symbols-outlined" style="color:#ef4444">error</span>
              <div>
                <strong style="color:#ef4444">Task Safety Error:</strong>
                <ul style="margin:4px 0 0 16px;padding:0;font-size:12px">
                  ${validation.errors.map(err => `<li>${this.#esc(err)}</li>`).join('')}
                </ul>
              </div>
            </div>
          ` : ''}

          <!-- 4. Tabbed Manager & Editor -->
          <div class="card">
            <div class="jitpack-tabs-header">
              <nav class="jitpack-tabs-nav" role="tablist">
                <button type="button" class="jitpack-tab-btn ${state.activeTab === 'jitpack' ? 'active' : ''}" data-tab="jitpack" role="tab">
                  <span class="material-symbols-outlined">settings</span>
                  jitpack.yml Editor &amp; Preview
                </button>
                <button type="button" class="jitpack-tab-btn ${state.activeTab === 'gradle' ? 'active' : ''}" data-tab="gradle" role="tab">
                  <span class="material-symbols-outlined">build</span>
                  build.gradle Publication Block
                </button>
                <button type="button" class="jitpack-tab-btn ${state.activeTab === 'consumer' ? 'active' : ''}" data-tab="consumer" role="tab">
                  <span class="material-symbols-outlined">code</span>
                  Consumer Setup
                </button>
                <button type="button" class="jitpack-tab-btn ${state.activeTab === 'badge' ? 'active' : ''}" data-tab="badge" role="tab">
                  <span class="material-symbols-outlined">verified</span>
                  Badge &amp; README
                </button>
                <button type="button" class="jitpack-tab-btn ${state.activeTab === 'monitor' ? 'active' : ''}" data-tab="monitor" role="tab">
                  <span class="material-symbols-outlined">monitoring</span>
                  Live Build Monitor &amp; Error Diagnosis
                </button>
              </nav>

              <div style="margin-left:auto;display:flex;gap:6px">
                <button type="button" class="btn btn-secondary btn-sm" id="jp-copy-active-btn">
                  <span class="material-symbols-outlined">content_copy</span>Copy Code
                </button>
              </div>
            </div>

            <!-- Tab 1: jitpack.yml Live Editor -->
            <div class="jitpack-tab-content ${state.activeTab === 'jitpack' ? 'active' : ''}" id="tab-pane-jitpack">
              <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-2);flex-wrap:wrap;gap:var(--sp-2)">
                <span class="jitpack-step-label">Root <code>jitpack.yml</code> configuration file (Live Editor):</span>
                <div style="display:flex;gap:var(--sp-2)">
                  <button type="button" class="btn btn-ghost btn-sm" id="jp-reset-yml-btn" title="Reset YAML content to standard generated template">
                    <span class="material-symbols-outlined">restart_alt</span>Regenerate Default
                  </button>
                  <button type="button" class="btn btn-primary btn-sm" id="jp-save-yml-btn" ${!p ? 'disabled' : ''}>
                    <span class="material-symbols-outlined">save</span>Save to Project
                  </button>
                </div>
              </div>
              <div class="jitpack-yml-body">
                <textarea id="jp-yml-editor" class="editor-textarea" spellcheck="false" style="min-height:220px;font-family:var(--font-mono);font-size:13px">${this.#esc(state.yamlContent)}</textarea>
              </div>
            </div>

            <!-- Tab 2: build.gradle Publication Block -->
            <div class="jitpack-tab-content ${state.activeTab === 'gradle' ? 'active' : ''}" id="tab-pane-gradle">
              <div class="jitpack-snippets">
                <div class="jitpack-step-header">
                  <span class="jitpack-step-label">
                    Generated <code>release(MavenPublication)</code> block for <code>${this.#esc(state.targetModule || 'library')}/build.gradle</code>
                  </span>
                  <div style="display:flex;gap:var(--sp-2)">
                    <button type="button" class="btn btn-primary btn-sm" id="jp-inject-gradle-btn" ${!p ? 'disabled' : ''}>
                      <span class="material-symbols-outlined">file_open</span>Apply to ${this.#esc(state.targetModule || 'library')}
                    </button>
                  </div>
                </div>
                <div class="code-block" id="jp-code-gradle">${this.#esc(state.dslMode === 'kotlin' ? kotlinPublishCode : groovyPublishCode)}</div>
              </div>
            </div>

            <!-- Tab 3: Consumer Integration -->
            <div class="jitpack-tab-content ${state.activeTab === 'consumer' ? 'active' : ''}" id="tab-pane-consumer">
              <div class="jitpack-snippets">
                <div>
                  <div class="jitpack-step-header">
                    <span class="jitpack-step-label">Step 1 — Add JitPack Repository (settings.gradle)</span>
                  </div>
                  <div class="code-block" id="jp-code-step1">${this.#esc(snippets.settingsGroovy)}</div>
                </div>

                <div>
                  <div class="jitpack-step-header">
                    <span class="jitpack-step-label">Step 2 — Add Dependency (app/build.gradle)</span>
                  </div>
                  <div class="code-block" id="jp-code-step2">${this.#esc(snippets.appBuildGroovy)}</div>
                </div>
              </div>
            </div>

            <!-- Tab 4: Badge & README -->
            <div class="jitpack-tab-content ${state.activeTab === 'badge' ? 'active' : ''}" id="tab-pane-badge">
              <div class="jitpack-badge-row">
                <img src="https://jitpack.io/v/${this.#escAttr(state.author || 'username')}/${this.#escAttr(state.repoName || 'repo')}.svg" alt="JitPack"
                     onerror="this.style.display='none'" class="jitpack-badge-img" />
                <div class="jitpack-badge-help">
                  <div class="jitpack-badge-help-label">Markdown Badge for README.md:</div>
                  <div class="code-block" id="jp-code-badge-md">${this.#esc(snippets.badgeMarkdown)}</div>
                </div>
              </div>
            </div>

            <!-- Tab 5: Live Build Monitor & Error Diagnosis -->
            <div class="jitpack-tab-content ${state.activeTab === 'monitor' ? 'active' : ''}" id="tab-pane-monitor">
              <!-- Monitor Configuration Row -->
              <div style="display:flex;flex-wrap:wrap;gap:var(--sp-3);align-items:flex-end;margin-bottom:var(--sp-4);padding:var(--sp-3);background:var(--bg-muted);border-radius:6px;border:1px solid var(--border)">
                <div style="flex:1;min-width:140px">
                  <label style="font-size:12px;font-weight:600;display:block;margin-bottom:4px">GitHub Owner</label>
                  <input type="text" id="jp-mon-owner" class="input" value="${this.#escAttr(state.monitorOwner)}" placeholder="e.g. username" style="height:34px;font-size:13px" />
                </div>
                <div style="flex:1;min-width:160px">
                  <label style="font-size:12px;font-weight:600;display:block;margin-bottom:4px">Repository Name</label>
                  <input type="text" id="jp-mon-repo" class="input" value="${this.#escAttr(state.monitorRepo)}" placeholder="e.g. library-name" style="height:34px;font-size:13px" />
                </div>
                <div style="flex:1;min-width:120px">
                  <label style="font-size:12px;font-weight:600;display:block;margin-bottom:4px">Release Tag / Version</label>
                  <input type="text" id="jp-mon-tag" class="input" value="${this.#escAttr(state.monitorTag)}" placeholder="e.g. v1.0.0 or 1.0.0" style="height:34px;font-size:13px" />
                </div>
                <div style="display:flex;gap:var(--sp-2)">
                  <button type="button" class="btn btn-secondary" id="jp-fetch-status-btn" ${state.isFetchingStatus ? 'disabled' : ''}>
                    <span class="material-symbols-outlined">${state.isFetchingStatus ? 'hourglass_top' : 'refresh'}</span>
                    Check Status
                  </button>
                  <button type="button" class="btn btn-primary" id="jp-fetch-log-btn" ${state.isFetchingLog ? 'disabled' : ''}>
                    <span class="material-symbols-outlined">${state.isFetchingLog ? 'hourglass_top' : 'terminal'}</span>
                    Fetch Live Log
                  </button>
                  <a href="https://jitpack.io/#${encodeURIComponent(state.monitorOwner || 'user')}/${encodeURIComponent(state.monitorRepo || 'repo')}/${encodeURIComponent(state.monitorTag || 'v1.0.0')}" target="_blank" rel="noopener" class="btn btn-ghost" title="Open on JitPack Website">
                    <span class="material-symbols-outlined">open_in_new</span>
                  </a>
                </div>
              </div>

              <!-- Status Summary Card -->
              <div style="margin-bottom:var(--sp-4);padding:var(--sp-3);background:var(--bg-card);border:1px solid var(--border);border-radius:6px;display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-3)">
                <div>
                  <div style="font-size:11px;color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.5px">JitPack Build Status</div>
                  <div style="display:flex;align-items:center;gap:8px;margin-top:4px">
                    ${this.#renderBuildStatusBadge(state.buildStatus)}
                    <code style="font-size:12px">com.github.${this.#esc(state.monitorOwner || 'USER')}:${this.#esc(state.monitorRepo || 'REPO')}:${this.#esc(state.monitorTag || 'TAG')}</code>
                  </div>
                </div>
                <div style="display:flex;gap:var(--sp-2)">
                  <a href="https://jitpack.io/com/github/${encodeURIComponent(state.monitorOwner || 'user')}/${encodeURIComponent(state.monitorRepo || 'repo')}/${encodeURIComponent(state.monitorTag || 'v1.0.0')}/build.log" target="_blank" rel="noopener" class="btn btn-ghost btn-sm">
                    <span class="material-symbols-outlined" style="font-size:16px">description</span>Raw Log
                  </a>
                  <a href="https://jitpack.io/com/github/${encodeURIComponent(state.monitorOwner || 'user')}/${encodeURIComponent(state.monitorRepo || 'repo')}/${encodeURIComponent(state.monitorTag || 'v1.0.0')}/${encodeURIComponent(state.monitorRepo || 'repo')}-${encodeURIComponent(state.monitorTag || 'v1.0.0')}.pom" target="_blank" rel="noopener" class="btn btn-ghost btn-sm">
                    <span class="material-symbols-outlined" style="font-size:16px">data_object</span>POM Artifact
                  </a>
                </div>
              </div>

              <!-- Automated Diagnosis & 1-Click Auto-Fix Section -->
              <div style="margin-bottom:var(--sp-4)">
                <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:var(--sp-2)">
                  <h3 style="font-size:14px;font-weight:600;display:flex;align-items:center;gap:6px">
                    <span class="material-symbols-outlined" style="color:var(--primary)">auto_fix_high</span>
                    Automated Error Diagnosis &amp; Remediation
                  </h3>
                  <button type="button" class="btn btn-ghost btn-sm" id="jp-reanalyze-issues-btn">
                    <span class="material-symbols-outlined" style="font-size:16px">sync</span>Re-evaluate
                  </button>
                </div>
                ${this.#renderDiagnosedIssues(state.diagnosedIssues)}
              </div>

              <!-- Live Log Viewer -->
              <div>
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-2);flex-wrap:wrap;gap:var(--sp-2)">
                  <div style="display:flex;align-items:center;gap:8px">
                    <span style="font-size:13px;font-weight:600">Build Output Log</span>
                    <input type="text" id="jp-log-filter" class="input" placeholder="Filter log (e.g. error, FAILED, java)..." value="${this.#escAttr(state.logFilter)}" style="height:28px;font-size:12px;width:240px" />
                  </div>
                  <div style="display:flex;gap:var(--sp-2);flex-wrap:wrap">
                    <button type="button" class="btn btn-secondary btn-sm" id="jp-retry-log-btn" ${state.isFetchingLog ? 'disabled' : ''} title="Retry fetching live JitPack build log">
                      <span class="material-symbols-outlined" style="font-size:16px">${state.isFetchingLog ? 'hourglass_top' : 'refresh'}</span>
                      Retry Log ${state.retryCount > 0 ? `<span class="badge" style="font-size:10px;padding:1px 5px;margin-left:4px">${state.retryCount}</span>` : ''}
                    </button>
                    <button type="button" class="btn btn-secondary btn-sm" id="jp-copy-log-btn" ${!state.buildLog ? 'disabled' : ''}>
                      <span class="material-symbols-outlined">content_copy</span>Copy Log
                    </button>
                    <button type="button" class="btn btn-secondary btn-sm" id="jp-download-log-btn" ${!state.buildLog ? 'disabled' : ''}>
                      <span class="material-symbols-outlined">download</span>Download
                    </button>
                  </div>
                </div>

                <!-- JitPack Log Retry System Banner -->
                ${(state.buildStatus === 'error' || state.buildStatus === 'building' || state.retryCount > 0) ? `
                  <div class="jp-retry-alert-banner" style="display:flex;align-items:center;justify-content:space-between;gap:var(--sp-2);padding:10px 14px;background:var(--bg-card);border:1px solid ${state.buildStatus === 'error' ? 'var(--error, #EF4444)' : 'var(--accent, #0284C7)'};border-radius:6px;margin-bottom:var(--sp-2);flex-wrap:wrap">
                    <div style="display:flex;align-items:center;gap:8px">
                      <span class="material-symbols-outlined" style="color:${state.buildStatus === 'error' ? 'var(--error, #EF4444)' : '#38BDF8'}">
                        ${state.buildStatus === 'error' ? 'error' : (state.buildStatus === 'building' ? 'sync' : 'info')}
                      </span>
                      <span style="font-size:12px;font-weight:500">
                        ${state.buildStatus === 'building' 
                          ? 'JitPack is currently building this artifact. Log may be in progress.' 
                          : (state.buildStatus === 'error' 
                            ? 'Build failed or log unavailable from JitPack CDN.' 
                            : 'Log loaded. Retry available if upstream repository has updated.')}
                        ${state.retryCount > 0 ? ` (Retried ${state.retryCount} times)` : ''}
                      </span>
                    </div>
                    <div style="display:flex;align-items:center;gap:8px">
                      <button type="button" class="btn btn-primary btn-sm" id="jp-retry-banner-btn" ${state.isFetchingLog ? 'disabled' : ''}>
                        <span class="material-symbols-outlined" style="font-size:16px">refresh</span>
                        Retry Now
                      </button>
                      <button type="button" class="btn btn-secondary btn-sm" id="jp-auto-retry-btn">
                        <span class="material-symbols-outlined" style="font-size:16px">${state.autoRetryActive ? 'stop_circle' : 'schedule'}</span>
                        ${state.autoRetryActive ? 'Stop Auto-Retry' : 'Auto-Retry (5s)'}
                      </button>
                    </div>
                  </div>
                ` : ''}

                <div class="code-block" style="max-height:420px;overflow-y:auto;background:#0d1117;color:#c9d1d9;font-family:var(--font-mono);font-size:12px;line-height:1.5;padding:12px;border-radius:6px;border:1px solid #30363d">
                  ${this.#renderFilteredLog(state.buildLog, state.logFilter)}
                </div>
              </div>
            </div>
          </div>

          <!-- Published History Card -->
          <div class="card">
            <div class="card-header">
              <span class="material-symbols-outlined">library_books</span>
              <span>Published Releases</span>
              <span class="badge badge-accent jitpack-versions-count">${records.length}</span>
            </div>
            <div class="card-body">
              ${records.length === 0
                ? `<div style="text-align:center;padding:var(--sp-4);color:var(--text-secondary);font-size:13px">
                     No releases published yet. Use GitHub → Publish to create releases that automatically show up here.
                   </div>`
                : `<div class="jitpack-versions-list">
                     ${records.map(r => this.#renderVersionRow(r, state.author, state.repoName)).join('')}
                   </div>`
              }
            </div>
          </div>
        </div>
      </div>
    `;

    this.#bindEvents();
  }

  #bindEvents() {
    const container = this.#container;

    // Tab buttons
    container.querySelectorAll('.jitpack-tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        this.#state.activeTab = btn.dataset.tab;
        this.#render();
      });
    });

    // JDK selector
    container.querySelector('#jp-jdk-select')?.addEventListener('change', e => {
      this.#state.jdkVersion = e.target.value;
      this.#updateYaml();
    });

    // Clean flag
    container.querySelector('#jp-flag-clean')?.addEventListener('change', e => {
      this.#state.clean = e.target.checked;
      this.#updateYaml();
    });

    // Stacktrace flag
    container.querySelector('#jp-flag-stacktrace')?.addEventListener('change', e => {
      this.#state.stacktrace = e.target.checked;
      this.#updateYaml();
    });

    // Custom Tasks input
    container.querySelector('#jp-custom-tasks')?.addEventListener('input', e => {
      this.#state.customTasks = e.target.value;
      this.#updateYaml();
    });

    // Module task checkboxes
    container.querySelectorAll('.jp-task-assemble').forEach(cb => {
      cb.addEventListener('change', () => {
        const idx = parseInt(cb.dataset.index, 10);
        if (this.#moduleTasks[idx]) {
          this.#moduleTasks[idx].assembleRelease = cb.checked;
          this.#updateYaml();
        }
      });
    });

    container.querySelectorAll('.jp-task-publish').forEach(cb => {
      cb.addEventListener('change', () => {
        const idx = parseInt(cb.dataset.index, 10);
        if (this.#moduleTasks[idx]) {
          this.#moduleTasks[idx].publishToMavenLocal = cb.checked;
          this.#updateYaml();
        }
      });
    });

    // YAML Editor input
    container.querySelector('#jp-yml-editor')?.addEventListener('input', e => {
      this.#state.yamlContent = e.target.value;
    });

    // Existing jitpack.yml action buttons
    container.querySelector('#jp-reuse-btn')?.addEventListener('click', () => {
      this.#state.yamlContent = this.#analysis.existingJitpackContent;
      const editor = container.querySelector('#jp-yml-editor');
      if (editor) editor.value = this.#state.yamlContent;
      toast.info('Reused', 'Loaded existing jitpack.yml into editor');
    });

    container.querySelector('#jp-merge-btn')?.addEventListener('click', () => {
      this.#state.yamlContent = JitPackAnalyzerEngine.mergeWithExisting(
        this.#analysis.existingJitpackContent,
        {
          jdkVersion: this.#state.jdkVersion,
          beforeInstall: this.#state.beforeInstallCommands,
          moduleTasks: this.#moduleTasks,
          customTasks: this.#state.customTasks,
          clean: this.#state.clean,
          stacktrace: this.#state.stacktrace,
        }
      );
      const editor = container.querySelector('#jp-yml-editor');
      if (editor) editor.value = this.#state.yamlContent;
      toast.success('Merged', 'Merged Gradle module tasks into existing jitpack.yml');
    });

    container.querySelector('#jp-regen-btn')?.addEventListener('click', () => {
      this.#state.yamlContent = this.#generateFreshYaml();
      const editor = container.querySelector('#jp-yml-editor');
      if (editor) editor.value = this.#state.yamlContent;
      toast.info('Regenerated', 'Fresh jitpack.yml generated from project analysis');
    });

    container.querySelector('#jp-reset-yml-btn')?.addEventListener('click', () => {
      this.#state.yamlContent = this.#generateFreshYaml();
      const editor = container.querySelector('#jp-yml-editor');
      if (editor) editor.value = this.#state.yamlContent;
      toast.info('Reset', 'jitpack.yml reset to recommended template');
    });

    // Reanalyze button
    container.querySelector('#jp-reanalyze-btn')?.addEventListener('click', () => {
      const p = projectService.current;
      this.#runAnalysis(p?.files || {});
      this.#render();
      toast.success('Analysis Complete', 'Gradle structure re-analyzed');
    });

    // Copy active button
    container.querySelector('#jp-copy-active-btn')?.addEventListener('click', async () => {
      const editor = container.querySelector('#jp-yml-editor');
      const text = editor ? editor.value : this.#state.yamlContent;
      const ok = await ClipboardUtil.copyText(text);
      if (ok) {
        toast.success('Copied', 'Configuration copied to clipboard');
      } else {
        toast.info('Copy Error', 'Please select text and copy manually');
      }
    });

    // Download jitpack.yml button
    container.querySelector('#jp-download-yml-btn')?.addEventListener('click', () => {
      const editor = container.querySelector('#jp-yml-editor');
      const content = editor ? editor.value : this.#state.yamlContent;
      this.#downloadFile('jitpack.yml', content);
      toast.success('Downloaded', 'jitpack.yml downloaded');
    });

    // Save/Apply to Project
    const handleApply = async () => {
      const p = projectService.current;
      if (!p) {
        toast.error('No Project', 'Open or create a project first');
        return;
      }
      const editor = container.querySelector('#jp-yml-editor');
      const content = editor ? editor.value : this.#state.yamlContent;
      await projectService.setFile('jitpack.yml', content);
      consoleService.success('JitPack: jitpack.yml saved to project root');
      toast.success('Applied to Project', 'Saved jitpack.yml to project root');
    };

    container.querySelector('#jp-apply-btn')?.addEventListener('click', handleApply);
    container.querySelector('#jp-save-yml-btn')?.addEventListener('click', handleApply);

    // Export ZIP button
    container.querySelector('#jp-export-zip-btn')?.addEventListener('click', async () => {
      const editor = container.querySelector('#jp-yml-editor');
      const content = editor ? editor.value : this.#state.yamlContent;

      const p = projectService.current;
      if (p && p.files) {
        await projectService.setFile('jitpack.yml', content);
        await zipService.exportProject();
      } else {
        // Export standalone config package
        const zipFiles = {
          'jitpack.yml': content,
          'README_JITPACK.md': '# JitPack Build Configuration\nPlace `jitpack.yml` in your GitHub repository root.\n',
        };
        await zipService.exportCustomFiles(zipFiles, 'jitpack-configuration.zip');
      }
      toast.success('ZIP Exported', 'Configuration archive downloaded');
    });

    // Apply Gradle snippet to module
    container.querySelector('#jp-inject-gradle-btn')?.addEventListener('click', async () => {
      const p = projectService.current;
      if (!p) return;
      const targetMod = this.#state.targetModule || 'library';
      const targetPath = `${targetMod}/build.gradle`;
      const currentBuild = p.files?.[targetPath] || '';
      const snippet = MavenPublishGenerator.generateGroovyPublishingBlock(this.#state);
      await projectService.setFile(targetPath, `${currentBuild}\n\n// Maven Publication Configuration\n${snippet}`);
      toast.success('Applied', `Added publication block to ${targetPath}`);
    });

    // ── Live Build Monitor & Diagnostics Handlers ──────────────────────────
    container.querySelector('#jp-mon-owner')?.addEventListener('input', e => {
      this.#state.monitorOwner = e.target.value;
    });
    container.querySelector('#jp-mon-repo')?.addEventListener('input', e => {
      this.#state.monitorRepo = e.target.value;
    });
    container.querySelector('#jp-mon-tag')?.addEventListener('input', e => {
      this.#state.monitorTag = e.target.value;
    });

    // Fetch Build Status button
    container.querySelector('#jp-fetch-status-btn')?.addEventListener('click', async () => {
      const owner = this.#state.monitorOwner || this.#state.author;
      const repo = this.#state.monitorRepo || this.#state.repoName;
      const tag = this.#state.monitorTag || this.#state.tag;

      this.#state.isFetchingStatus = true;
      this.#render();

      const res = await JitPackBuildService.fetchBuildStatus(owner, repo);
      this.#state.isFetchingStatus = false;

      if (res.success) {
        this.#state.buildStatusList = res.data || {};
        this.#state.buildStatus = res.data?.[tag] || 'not_found';
        toast.success('Status Updated', `JitPack status for ${tag}: ${this.#state.buildStatus}`);
      } else {
        toast.error('JitPack API Error', res.error || 'Failed to fetch status');
      }
      this.#render();
    });

    // Fetch Live Log button & Retry System
    const handleLogFetch = (isRetry = false) => this.#fetchLogWithRetry(isRetry);

    container.querySelector('#jp-fetch-log-btn')?.addEventListener('click', () => handleLogFetch(false));
    container.querySelector('#jp-retry-log-btn')?.addEventListener('click', () => handleLogFetch(true));
    container.querySelector('#jp-retry-banner-btn')?.addEventListener('click', () => handleLogFetch(true));

    container.querySelector('#jp-auto-retry-btn')?.addEventListener('click', () => {
      this.#state.autoRetryActive = !this.#state.autoRetryActive;
      if (this.#state.autoRetryActive) {
        toast.info('Auto-Retry Active', 'Will re-fetch build log every 5 seconds');
        handleLogFetch(true);
      } else {
        if (this.#state.autoRetryTimer) clearTimeout(this.#state.autoRetryTimer);
        this.#state.autoRetryTimer = null;
        toast.info('Auto-Retry Stopped', 'Manual retry is still available');
        this.#render();
      }
    });

    // Re-evaluate issues button
    container.querySelector('#jp-reanalyze-issues-btn')?.addEventListener('click', () => {
      const p = projectService.current;
      this.#state.diagnosedIssues = JitPackBuildService.diagnose(this.#state.buildLog, p?.files || {});
      this.#render();
      toast.info('Re-evaluated', `Identified ${this.#state.diagnosedIssues.length} potential issue(s)`);
    });

    // Filter log input
    container.querySelector('#jp-log-filter')?.addEventListener('input', e => {
      this.#state.logFilter = e.target.value;
      const logBox = container.querySelector('#jp-filtered-log-box');
      if (logBox) {
        logBox.innerHTML = this.#renderFilteredLog(this.#state.buildLog, this.#state.logFilter);
      }
    });

    // Copy log button
    container.querySelector('#jp-copy-log-btn')?.addEventListener('click', async () => {
      if (!this.#state.buildLog) return;
      const ok = await ClipboardUtil.copyText(this.#state.buildLog);
      if (ok) {
        toast.success('Copied', 'Build log copied to clipboard');
      } else {
        toast.info('Copy Error', 'Please select and copy manually');
      }
    });

    // Download log button
    container.querySelector('#jp-download-log-btn')?.addEventListener('click', () => {
      if (!this.#state.buildLog) return;
      this.#downloadFile(`jitpack-${this.#state.monitorTag || 'build'}.log`, this.#state.buildLog);
      toast.success('Downloaded', 'Build log downloaded');
    });

    // 1-Click Auto-Fix buttons
    container.querySelectorAll('.jp-autofix-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        const issueId = btn.dataset.issueId;
        btn.disabled = true;
        btn.textContent = 'Applying...';

        const res = await JitPackBuildService.applyAutoFix(issueId, projectService);
        if (res.success) {
          toast.success('Auto-Fix Applied', res.message);
          // Re-run analysis and update YAML editor
          const p = projectService.current;
          this.#runAnalysis(p?.files || {});
          this.#state.yamlContent = p?.files?.['jitpack.yml'] || this.#generateFreshYaml();
          this.#state.diagnosedIssues = JitPackBuildService.diagnose(this.#state.buildLog, p?.files || {});
          this.#render();
        } else {
          toast.error('Auto-Fix Failed', res.message);
          btn.disabled = false;
        }
      });
    });
  }

  destroy() {
    if (this.#state.autoRetryTimer) {
      clearTimeout(this.#state.autoRetryTimer);
      this.#state.autoRetryTimer = null;
    }
    if (this.#container) {
      this.#container.innerHTML = '';
      this.#container = null;
    }
  }

  async #fetchLogWithRetry(isRetry = false) {
    if (isRetry) {
      this.#state.retryCount++;
      toast.info('Retrying Log', `Fetching build log (attempt #${this.#state.retryCount})...`);
    }

    const owner = this.#state.monitorOwner || this.#state.author;
    const repo = this.#state.monitorRepo || this.#state.repoName;
    const tag = this.#state.monitorTag || this.#state.tag;

    this.#state.isFetchingLog = true;
    this.#render();

    const res = await JitPackBuildService.fetchBuildLog(owner, repo, tag);
    this.#state.isFetchingLog = false;

    if (res.success) {
      this.#state.buildLog = res.log || '';
      this.#state.buildStatus = res.status || 'unknown';
      const p = projectService.current;
      this.#state.diagnosedIssues = JitPackBuildService.diagnose(res.log, p?.files || {});
      toast.success('Log Loaded', `Retrieved ${res.log.length} bytes of build log`);

      if (this.#state.buildStatus === 'ok' || this.#state.buildStatus === 'error') {
        if (this.#state.autoRetryActive) {
          this.#state.autoRetryActive = false;
          if (this.#state.autoRetryTimer) clearTimeout(this.#state.autoRetryTimer);
          toast.info('Auto-Retry Stopped', `Build reached state: ${this.#state.buildStatus}`);
        }
      }
    } else {
      this.#state.buildLog = res.error || '';
      this.#state.buildStatus = res.status || 'error';
      const p = projectService.current;
      this.#state.diagnosedIssues = JitPackBuildService.diagnose(res.error, p?.files || {});
      toast.info('Log Notice', res.error);
    }

    if (this.#state.autoRetryActive && (this.#state.buildStatus === 'building' || !res.success)) {
      if (this.#state.autoRetryTimer) clearTimeout(this.#state.autoRetryTimer);
      this.#state.autoRetryTimer = setTimeout(() => {
        if (this.#container && document.body.contains(this.#container) && this.#state.autoRetryActive) {
          this.#fetchLogWithRetry(true);
        }
      }, 5000);
    }

    this.#render();
  }


  #renderBuildStatusBadge(status) {
    if (!status) {
      return `<span class="badge" style="background:#6b7280;color:#fff;font-size:11px">UNCHECKED</span>`;
    }
    if (status === 'ok') {
      return `<span class="badge" style="background:#10b981;color:#fff;font-size:11px"><span class="material-symbols-outlined" style="font-size:12px;vertical-align:middle">check_circle</span> SUCCESS</span>`;
    }
    if (status === 'building') {
      return `<span class="badge" style="background:#3b82f6;color:#fff;font-size:11px"><span class="material-symbols-outlined" style="font-size:12px;vertical-align:middle">sync</span> BUILDING</span>`;
    }
    if (status === 'error') {
      return `<span class="badge" style="background:#ef4444;color:#fff;font-size:11px"><span class="material-symbols-outlined" style="font-size:12px;vertical-align:middle">cancel</span> FAILED</span>`;
    }
    return `<span class="badge" style="background:#f59e0b;color:#fff;font-size:11px">${this.#esc(status).toUpperCase()}</span>`;
  }

  #renderDiagnosedIssues(issues = []) {
    if (!issues || issues.length === 0) {
      return `
        <div style="padding:12px;background:rgba(16,185,129,0.1);border:1px solid rgba(16,185,129,0.3);border-radius:6px;display:flex;align-items:center;gap:8px">
          <span class="material-symbols-outlined" style="color:#10b981">verified</span>
          <span style="font-size:13px;color:var(--text-foreground)">No JitPack build or configuration issues detected. Ready for JitPack.</span>
        </div>`;
    }

    return `
      <div style="display:flex;flex-direction:column;gap:8px">
        ${issues.map(iss => `
          <div style="padding:10px 14px;background:var(--bg-card);border:1px solid ${iss.severity === 'error' ? 'rgba(239,68,68,0.4)' : 'rgba(245,158,11,0.4)'};border-radius:6px;display:flex;align-items:flex-start;justify-content:space-between;gap:12px;flex-wrap:wrap">
            <div style="flex:1;min-width:260px">
              <div style="display:flex;align-items:center;gap:6px">
                <span class="material-symbols-outlined" style="font-size:18px;color:${iss.severity === 'error' ? '#ef4444' : '#f59e0b'}">
                  ${iss.severity === 'error' ? 'error' : 'warning'}
                </span>
                <strong style="font-size:13px">${this.#esc(iss.title)}</strong>
              </div>
              <p style="margin:4px 0 0 24px;font-size:12px;color:var(--text-secondary);line-height:1.4">
                ${this.#esc(iss.explanation)}
              </p>
            </div>
            <button type="button" class="btn btn-primary btn-sm jp-autofix-btn" data-issue-id="${this.#escAttr(iss.id)}" style="align-self:center">
              <span class="material-symbols-outlined" style="font-size:14px">auto_fix_high</span>
              ${this.#esc(iss.fixActionLabel || 'Auto-Fix')}
            </button>
          </div>
        `).join('')}
      </div>`;
  }

  #renderFilteredLog(log = '', filter = '') {
    if (!log) {
      return '<span style="color:#8b949e">// Click "Fetch Live Log" above to retrieve and stream live JitPack build output.</span>';
    }
    const lines = log.split('\n');
    let displayLines = lines;
    if (filter && filter.trim()) {
      const q = filter.trim().toLowerCase();
      displayLines = lines.filter(l => l.toLowerCase().includes(q));
    }
    return `
      <pre id="jp-filtered-log-box" style="margin:0;white-space:pre-wrap;word-break:break-all">${displayLines.map(l => {
        const escLine = this.#esc(l);
        if (/error|failed|exception|cannot find/i.test(l)) {
          return `<span style="color:#ff7b72;font-weight:600">${escLine}</span>`;
        }
        if (/warning|deprecated/i.test(l)) {
          return `<span style="color:#d29922">${escLine}</span>`;
        }
        if (/success|build successful/i.test(l)) {
          return `<span style="color:#7ee787;font-weight:600">${escLine}</span>`;
        }
        return escLine;
      }).join('\n')}</pre>`;
  }

  #updateYaml() {
    this.#state.yamlContent = this.#generateFreshYaml();
    const editor = this.#container.querySelector('#jp-yml-editor');
    if (editor) editor.value = this.#state.yamlContent;
  }

  #downloadFile(filename, content) {
    WebViewBridge.downloadFile(filename, content, 'text/plain;charset=utf-8');
  }

  #renderVersionRow(record, author, repo) {
    const tag = record.tag || 'v1.0.0';
    const coord = `com.github.${author || 'USER'}:${repo || 'REPO'}:${tag}`;
    return `
      <div style="display:flex;align-items:center;justify-content:space-between;padding:8px 12px;background:var(--bg-muted);border-radius:6px;margin-bottom:6px">
        <div>
          <strong>${this.#esc(tag)}</strong>
          <code style="margin-left:8px;font-size:12px">${this.#esc(coord)}</code>
        </div>
        <a href="https://jitpack.io/#${encodeURIComponent(author || 'user')}/${encodeURIComponent(repo || 'repo')}/${encodeURIComponent(tag)}" target="_blank" rel="noopener" class="btn btn-ghost btn-sm">
          <span class="material-symbols-outlined" style="font-size:16px">open_in_new</span>
        </a>
      </div>
    `;
  }

  #esc(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  #escAttr(str) {
    return this.#esc(str);
  }
}
