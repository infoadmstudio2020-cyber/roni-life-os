/**
 * AnalyzerPage.js — Project Analyzer, Developer Configuration & Source Dependency Automation.
 *
 * Implements:
 * 1. Project Analyzer -> Detect Android Project (Library / App / Multi-module)
 * 2. Source Code & External Dependency Automation:
 *    - Automatic file selection based on external dependencies
 *    - Detection of external libraries used in source files (.java, .kt)
 *    - Cross-referencing with build.gradle (Declared vs Missing)
 *    - 1-click automated implementation injection into Gradle build scripts
 * 3. Developer Configuration & safe Gradle patching
 */

import { projectService }          from '../modules/storage/ProjectService.js';
import { ProjectAnalyzer }         from '../modules/analyzer/ProjectAnalyzer.js';
import { SourceDependencyAnalyzer } from '../modules/analyzer/SourceDependencyAnalyzer.js';
import { GradlePatcher }           from '../modules/generator/GradlePatcher.js';
import { githubFileGenerator }     from '../modules/github/GitHubFileGenerator.js';
import { consoleService }          from '../modules/storage/ConsoleService.js';
import { toast }                   from '../ui/ToastService.js';
import { eventBus }                from '../core/EventBus.js';
import { TransitiveDependencyEngine } from '../modules/analyzer/TransitiveDependencyEngine.js';
import { ConsumerResolutionModal }  from '../modules/analyzer/ConsumerResolutionModal.js';

export class AnalyzerPage {
  #container = null;
  #analysis = null;
  #selectedDepCoord = null;
  #selectedSourcePath = null;
  #depFilterMode = 'all'; // 'all' | 'missing' | 'declared'
  #depSearchQuery = '';
  #sourceSearchQuery = '';

  async init(container) {
    this.#container = container;
    const project = projectService.current;

    if (!project) {
      this.#renderEmpty();
      return;
    }

    // Run deep analysis
    this.#analysis = ProjectAnalyzer.analyze(project.files || {}, project);
    this.#render();
  }

  #renderEmpty() {
    this.#container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title"><span class="material-symbols-outlined">analytics</span>Project Analyzer</h1>
          <p class="page-subtitle">Inspect and configure Android projects</p>
        </div>
        <div class="card" style="padding:var(--sp-8);text-align:center">
          <span class="material-symbols-outlined" style="font-size:48px;opacity:.3">folder_off</span>
          <div style="margin-top:var(--sp-3);color:var(--text-muted)">
            No project currently loaded.<br>
            Upload a ZIP or Create New to run the Project Analyzer.
          </div>
          <div style="margin-top:var(--sp-4);display:flex;gap:var(--sp-3);justify-content:center">
            <button class="btn btn-primary" id="analyzer-import-btn">
              <span class="material-symbols-outlined">upload</span>Upload ZIP
            </button>
            <button class="btn btn-secondary" id="analyzer-create-btn">
              <span class="material-symbols-outlined">add</span>Create New
            </button>
          </div>
        </div>
      </div>`;

    this.#container.querySelector('#analyzer-import-btn')?.addEventListener('click', () => {
      eventBus.emit('action:importProject');
    });
    this.#container.querySelector('#analyzer-create-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'generator' });
    });
  }

  #render() {
    const p = projectService.current;
    const a = this.#analysis;
    const primary = a.primaryModule || {};
    const srcDeps = a.sourceDependencies || SourceDependencyAnalyzer.analyze(p.files || {});

    const isLib = a.projectType === 'library';
    const isApp = a.projectType === 'app';
    const typeLabel = isLib ? 'Android Library' : isApp ? 'Android App' : 'Multi-Module Android Project';
    const typeIcon = isLib ? 'package_2' : isApp ? 'smartphone' : 'layers';
    const typeBadgeColor = isLib ? 'var(--accent)' : isApp ? 'var(--success)' : 'var(--warning)';

    this.#container.innerHTML = `
      <div class="page-container">
        <!-- Header -->
        <div class="page-header">
          <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-3)">
            <div>
              <h1 class="page-title">
                <span class="material-symbols-outlined">analytics</span>Project Analyzer & Configuration
              </h1>
              <p class="page-subtitle">${this.#esc(a.summary)}</p>
            </div>
            <div style="display:flex;gap:var(--sp-2)">
              <button class="btn btn-secondary btn-sm" id="re-analyze-btn">
                <span class="material-symbols-outlined">refresh</span>Re-Analyze
              </button>
              <button class="btn btn-primary btn-sm" id="patch-gradle-btn">
                <span class="material-symbols-outlined">auto_fix_high</span>Update Gradle Configuration
              </button>
            </div>
          </div>
        </div>

        <!-- Machine-Specific Local Paths Warning Alert -->
        ${a.warnings.length > 0 ? a.warnings.map(w => `
          <div class="card" style="margin-bottom:var(--sp-4);border-left:4px solid var(--warning);background:rgba(245,158,11,0.08)">
            <div class="card-body" style="padding:var(--sp-3) var(--sp-4);display:flex;align-items:flex-start;gap:var(--sp-3)">
              <span class="material-symbols-outlined" style="color:var(--warning);font-size:24px">warning</span>
              <div style="flex:1">
                <div style="font-weight:600;color:var(--warning)">Computer-Specific Local Maven Path Detected</div>
                <div style="font-size:var(--text-sm);margin-top:2px;color:var(--text-primary)">${this.#esc(w.message)}</div>
                <div style="font-size:var(--text-xs);color:var(--text-muted);margin-top:4px">
                  Clicking "Update Gradle Configuration" will safely remove or sanitize these computer-specific paths so GitHub Actions builds succeed.
                </div>
              </div>
            </div>
          </div>
        `).join('') : ''}

        <!-- Top Overview Cards -->
        <div class="dashboard-stats" style="margin-bottom:var(--sp-4)">
          <div class="stat-card">
            <div class="stat-card-icon" style="color:${typeBadgeColor}">
              <span class="material-symbols-outlined">${typeIcon}</span>
            </div>
            <div class="stat-card-value" style="font-size:var(--text-lg)">${typeLabel}</div>
            <div class="stat-card-label">Project Type</div>
            <div class="stat-card-sub">${primary.name ? `Module :${this.#esc(primary.name)}` : 'Root project'}</div>
          </div>

          <div class="stat-card">
            <div class="stat-card-icon">
              <span class="material-symbols-outlined">memory</span>
            </div>
            <div class="stat-card-value">JDK ${a.jdkRequirement}</div>
            <div class="stat-card-label">Java Version</div>
            <div class="stat-card-sub">AGP: ${a.agpVersion || 'auto'} · Gradle: ${a.gradleVersion || '8.7'}</div>
          </div>

          <div class="stat-card">
            <div class="stat-card-icon">
              <span class="material-symbols-outlined">layers</span>
            </div>
            <div class="stat-card-value">API ${primary.compileSdk || p.compileSdk || '34'}</div>
            <div class="stat-card-label">compileSdk</div>
            <div class="stat-card-sub">minSdk: ${primary.minSdk || p.minSdk || '21'} · targetSdk: ${primary.targetSdk || p.targetSdk || '34'}</div>
          </div>

          <div class="stat-card">
            <div class="stat-card-icon">
              <span class="material-symbols-outlined">${isLib ? 'library_books' : 'android'}</span>
            </div>
            <div class="stat-card-value">${isLib ? 'AAR' : 'APK / AAB'}</div>
            <div class="stat-card-label">Target Artifact</div>
            <div class="stat-card-sub">${isLib ? 'JitPack / GitHub Packages' : 'GitHub Release Asset'}</div>
          </div>
        </div>

        <!-- ── CORE FEATURE: Source Code & External Dependency Automation ── -->
        <div class="card" style="margin-bottom:var(--sp-4);border:1px solid var(--accent)">
          <div class="card-header" style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-2)">
            <div style="display:flex;align-items:center;gap:8px">
              <span class="material-symbols-outlined" style="color:var(--accent)">account_tree</span>
              <span style="font-weight:600">Source Files & External Dependencies Automation</span>
              <span class="badge ${srcDeps.missingCount > 0 ? 'badge-warning' : 'badge-success'}">
                ${srcDeps.missingCount > 0 ? `${srcDeps.missingCount} Missing from Gradle` : 'All Dependencies Declared'}
              </span>
            </div>
            <div style="display:flex;align-items:center;gap:var(--sp-2);flex-wrap:wrap">
              <span style="font-size:var(--text-xs);color:var(--text-muted)">
                ${srcDeps.sourceFiles.length} source files scanned · ${srcDeps.totalCount} external libraries used
              </span>
              ${srcDeps.missingCount > 0 ? `
                <button class="btn btn-sm btn-primary" id="analyzer-add-all-missing-btn">
                  <span class="material-symbols-outlined">auto_fix_high</span>Auto-Add All Missing to Gradle
                </button>
              ` : ''}
              <button class="btn btn-sm btn-secondary" id="analyzer-goto-gradle-btn">
                <span class="material-symbols-outlined">build</span>Open in Gradle Page
              </button>
              <button class="btn btn-sm btn-secondary" id="analyzer-consumer-test-btn" title="Simulate Consumer Dependency Resolution & Classpath">
                <span class="material-symbols-outlined">verified</span>Resolution Test
              </button>
            </div>
          </div>

          <div class="card-body" style="padding:var(--sp-3)">
            <div style="font-size:var(--text-xs);color:var(--text-secondary);margin-bottom:var(--sp-3);line-height:1.5">
              Select an external dependency to <strong>automatically filter & select all source files</strong> that import it, or select a source file to see which external libraries it depends on. Click <strong>"Add to Gradle"</strong> to automatically inject the implementation notation into <code>build.gradle</code>.
            </div>

            <div class="gradle-deps-body" style="padding:0">
              <!-- Left Column: External Dependencies -->
              <div class="gradle-deps-list-col">
                <div style="display:flex;gap:6px;align-items:center;flex-wrap:wrap">
                  <input type="text" id="analyzer-dep-search" class="input input-sm" style="flex:1;height:30px;font-size:var(--text-xs)"
                         placeholder="Search external dependencies…" value="${this.#esc(this.#depSearchQuery)}" />
                  <div class="btn-group" style="display:flex">
                    <button class="btn btn-xs ${this.#depFilterMode === 'all' ? 'btn-primary' : 'btn-ghost'}" data-dep-filter="all">All (${srcDeps.totalCount})</button>
                    <button class="btn btn-xs ${this.#depFilterMode === 'missing' ? 'btn-primary' : 'btn-ghost'}" data-dep-filter="missing">Missing (${srcDeps.missingCount})</button>
                    <button class="btn btn-xs ${this.#depFilterMode === 'declared' ? 'btn-primary' : 'btn-ghost'}" data-dep-filter="declared">Declared (${srcDeps.declaredCount})</button>
                  </div>
                </div>

                <div id="analyzer-deps-container" style="display:flex;flex-direction:column;gap:6px;max-height:360px;overflow-y:auto;padding-right:4px">
                  ${this.#renderDependenciesList(srcDeps)}
                </div>
              </div>

              <!-- Right Column: Associated Source Files -->
              <div class="gradle-deps-list-col">
                <div style="display:flex;align-items:center;justify-content:space-between;gap:6px">
                  <span style="font-size:var(--text-xs);font-weight:600;color:var(--text-secondary)">
                    ${this.#selectedDepCoord ? `Source Files using "${this.#selectedDepCoord.split(':')[1] || this.#selectedDepCoord}"` : 'All Source Files in Project'}
                  </span>
                  ${this.#selectedDepCoord ? `
                    <button class="btn btn-ghost btn-xs" id="analyzer-clear-dep-selection">
                      <span class="material-symbols-outlined" style="font-size:14px">close</span>Show All Files
                    </button>
                  ` : ''}
                </div>

                <input type="text" id="analyzer-src-search" class="input input-sm" style="height:30px;font-size:var(--text-xs)"
                       placeholder="Filter source files by name or path…" value="${this.#esc(this.#sourceSearchQuery)}" />

                <div id="analyzer-source-files-container" style="display:flex;flex-direction:column;gap:6px;max-height:360px;overflow-y:auto;padding-right:4px">
                  ${this.#renderSourceFilesList(srcDeps)}
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="gh-two-col">
          <!-- Left Column: Detected Analysis Details -->
          <div>
            <div class="card" style="margin-bottom:var(--sp-4)">
              <div class="card-header">
                <span class="material-symbols-outlined">search</span>
                <span>Detected Project Details</span>
              </div>
              <div class="card-body">
                <div class="settings-row">
                  <div class="settings-row-label">
                    <div class="settings-row-title">Root Project Name</div>
                    <div class="settings-row-desc">${this.#esc(a.rootProjectName || p.name)}</div>
                  </div>
                </div>

                <div class="settings-row">
                  <div class="settings-row-label">
                    <div class="settings-row-title">Namespace / Package</div>
                    <div class="settings-row-desc">${this.#esc(primary.namespace || p.packageName || '—')}</div>
                  </div>
                </div>

                ${primary.applicationId ? `
                  <div class="settings-row">
                    <div class="settings-row-label">
                      <div class="settings-row-title">Application ID</div>
                      <div class="settings-row-desc">${this.#esc(primary.applicationId)}</div>
                    </div>
                  </div>` : ''}

                <div class="settings-row">
                  <div class="settings-row-label">
                    <div class="settings-row-title">Subproject Modules</div>
                    <div class="settings-row-desc">${a.modules.length > 0 ? a.modules.map(m => `:${m}`).join(', ') : 'Single module (:app)'}</div>
                  </div>
                </div>

                <div class="settings-row">
                  <div class="settings-row-label">
                    <div class="settings-row-title">Source Languages</div>
                    <div class="settings-row-desc">${a.technologies.hasKotlin ? 'Kotlin' : ''}${a.technologies.hasKotlin && a.technologies.hasJava ? ' + ' : ''}${a.technologies.hasJava ? 'Java' : ''}</div>
                  </div>
                </div>

                <div class="settings-row">
                  <div class="settings-row-label">
                    <div class="settings-row-title">Features Detected</div>
                    <div class="settings-row-desc">
                      ${[
                        a.technologies.viewBinding ? 'ViewBinding' : null,
                        a.technologies.dataBinding ? 'DataBinding' : null,
                        a.technologies.compose ? 'Compose' : null,
                        a.tests.hasUnitTests ? 'Unit Tests' : null,
                        a.tests.hasInstrumentedTests ? 'Android Tests' : null,
                        a.publishing.hasMavenPublish ? 'Maven Publish' : null,
                      ].filter(Boolean).join(' · ') || 'Standard Android'}
                    </div>
                  </div>
                </div>

                <div class="settings-row">
                  <div class="settings-row-label">
                    <div class="settings-row-title">Preserved Dependencies in Gradle</div>
                    <div class="settings-row-desc">${primary.dependencies?.length || 0} external dependencies configured</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Existing Dependencies List (Preserved!) -->
            <div class="card">
              <div class="card-header">
                <span class="material-symbols-outlined">data_object</span>
                <span>Dependencies in build.gradle</span>
              </div>
              <div class="card-body" style="padding:var(--sp-2) var(--sp-4);max-height:220px;overflow-y:auto;font-family:monospace;font-size:var(--text-xs)">
                ${primary.dependencies && primary.dependencies.length > 0
                  ? primary.dependencies.map(d => `<div style="padding:4px 0;border-bottom:1px solid rgba(255,255,255,0.05)">${this.#esc(d)}</div>`).join('')
                  : '<div style="color:var(--text-muted);padding:var(--sp-2)">No custom dependencies block found</div>'}
              </div>
            </div>
          </div>

          <!-- Right Column: Developer Configuration Form -->
          <div>
            <form id="dev-config-form">
              <div class="card" style="margin-bottom:var(--sp-4)">
                <div class="card-header">
                  <span class="material-symbols-outlined">tune</span>
                  <span>Developer Configuration</span>
                </div>
                <div class="card-body">
                  <div class="form-group">
                    <label class="form-label required">Project Type Target</label>
                    <select class="input" name="projectType" id="cfg-project-type">
                      <option value="library" ${isLib ? 'selected' : ''}>Android Library (Generates AAR, Maven Publication)</option>
                      <option value="app" ${isApp ? 'selected' : ''}>Android App (Generates APK / AAB)</option>
                    </select>
                  </div>

                  <div class="form-row">
                    <div class="form-group">
                      <label class="form-label">JDK Version</label>
                      <select class="input" name="jdkRequirement">
                        <option value="17" ${a.jdkRequirement === '17' ? 'selected' : ''}>JDK 17 (Recommended for AGP 8.x)</option>
                        <option value="21" ${a.jdkRequirement === '21' ? 'selected' : ''}>JDK 21 (Modern LTS)</option>
                      </select>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Version</label>
                      <input class="input" type="text" name="version" value="${this.#esc(p.version || '1.0.0')}" />
                    </div>
                  </div>

                  <div class="form-row">
                    <div class="form-group">
                      <label class="form-label">compileSdk</label>
                      <input class="input" type="number" name="compileSdk" value="${primary.compileSdk || p.compileSdk || '34'}" />
                    </div>
                    <div class="form-group">
                      <label class="form-label">minSdk</label>
                      <input class="input" type="number" name="minSdk" value="${primary.minSdk || p.minSdk || '21'}" />
                    </div>
                  </div>

                  <!-- GitHub Packages / Publishing -->
                  <div class="form-group">
                    <label class="form-label">Publish to GitHub Packages</label>
                    <div style="display:flex;align-items:center;gap:var(--sp-2);margin-top:4px">
                      <input type="checkbox" id="cfg-gh-packages" name="enableGhPackages" ${a.repositories.githubPackages ? 'checked' : ''} />
                      <label for="cfg-gh-packages" style="font-size:var(--text-sm);cursor:pointer">
                        Configure GitHub Packages Maven repository for Library distribution
                      </label>
                    </div>
                  </div>

                  <div class="form-group">
                    <label class="form-label">Machine-Specific Local Paths</label>
                    <div style="display:flex;align-items:center;gap:var(--sp-2);margin-top:4px">
                      <input type="checkbox" id="cfg-clean-local-paths" name="cleanLocalPaths" checked />
                      <label for="cfg-clean-local-paths" style="font-size:var(--text-sm);cursor:pointer">
                        Sanitize computer-specific paths (e.g. E:/...) for clean GitHub Actions runner execution
                      </label>
                    </div>
                  </div>

                  <div style="margin-top:var(--sp-4);display:flex;gap:var(--sp-3);flex-wrap:wrap">
                    <button type="submit" class="btn btn-primary" style="flex:1">
                      <span class="material-symbols-outlined">save</span>Apply Configuration & Patch Gradle
                    </button>
                  </div>
                </div>
              </div>
            </form>

            <!-- Actions Step Guide -->
            <div class="card">
              <div class="card-header">
                <span class="material-symbols-outlined">rocket_launch</span>
                <span>Next Steps in Pipeline</span>
              </div>
              <div class="card-body">
                <div style="display:flex;gap:var(--sp-2);flex-direction:column">
                  <button class="btn btn-secondary" id="go-to-gradle-btn" style="text-align:left;justify-content:flex-start">
                    <span class="material-symbols-outlined">build</span>Review Patched Gradle Files
                  </button>
                  <button class="btn btn-secondary" id="go-to-workflows-btn" style="text-align:left;justify-content:flex-start">
                    <span class="material-symbols-outlined">integration_instructions</span>Generate GitHub Actions Workflows
                  </button>
                  <button class="btn btn-secondary" id="go-to-publish-btn" style="text-align:left;justify-content:flex-start">
                    <span class="material-symbols-outlined">cloud_upload</span>Create GitHub Repo & Push Source
                  </button>
                  <button class="btn btn-secondary" id="go-to-testing-btn" style="text-align:left;justify-content:flex-start">
                    <span class="material-symbols-outlined">fact_check</span>Run E2E Build, Test & Verification
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>`;

    this.#bindEvents();
  }

  #renderDependenciesList(srcDeps) {
    let list = srcDeps.dependencies || [];

    if (this.#depFilterMode === 'missing') {
      list = list.filter(d => !d.isDeclared);
    } else if (this.#depFilterMode === 'declared') {
      list = list.filter(d => d.isDeclared);
    }

    if (this.#depSearchQuery) {
      const q = this.#depSearchQuery.toLowerCase();
      list = list.filter(d => d.coordinate.toLowerCase().includes(q) || d.category.toLowerCase().includes(q) || d.description.toLowerCase().includes(q));
    }

    if (!list.length) {
      return `<div style="padding:var(--sp-4);text-align:center;color:var(--text-muted);font-size:var(--text-xs)">No dependencies match criteria</div>`;
    }

    return list.map(d => {
      const isActive = this.#selectedDepCoord === d.coordinate;
      const isMissing = !d.isDeclared;
      const scopeAnalysis = TransitiveDependencyEngine.analyzeScope(d.coordinate, projectService.current?.files || {});

      return `
        <div class="gradle-dep-card ${isActive ? 'active' : ''}" data-coord="${this.#esc(d.coordinate)}">
          <div style="display:flex;align-items:center;justify-content:space-between;gap:6px">
            <span class="gradle-dep-coord">${this.#esc(d.coordinate)}</span>
            <div style="display:flex;align-items:center;gap:4px">
              <span class="badge ${scopeAnalysis.scope === 'api' ? 'badge-primary' : 'badge-info'}" style="font-size:10px;padding:2px 5px" title="${this.#esc(scopeAnalysis.reason)}">
                ${scopeAnalysis.scope}
              </span>
              <span class="badge ${isMissing ? 'badge-warning' : 'badge-success'}" style="font-size:10px;padding:2px 6px">
                ${isMissing ? 'Missing' : 'Declared'}
              </span>
            </div>
          </div>
          <div style="font-size:11px;color:var(--text-muted);margin-top:3px;display:flex;align-items:center;justify-content:space-between">
            <span>${this.#esc(d.category)}</span>
            <span style="color:var(--accent);font-weight:500">${d.sourceFiles.length} file${d.sourceFiles.length === 1 ? '' : 's'}</span>
          </div>
          <div style="font-size:10px;color:var(--text-muted);margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
            ${this.#esc(d.description)} · <span style="color:var(--text-secondary)">${this.#esc(scopeAnalysis.reason)}</span>
          </div>
          <div style="margin-top:6px;display:flex;gap:6px;justify-content:flex-end">
            ${isMissing ? `
              <button class="btn btn-xs btn-primary analyzer-inject-dep-btn" data-coord="${this.#esc(d.coordinate)}" title="Add ${scopeAnalysis.scope} '${d.coordinate}' into build.gradle">
                <span class="material-symbols-outlined" style="font-size:13px">add</span>Add as ${scopeAnalysis.scope}
              </button>
            ` : `
              <span style="font-size:10px;color:var(--success);display:flex;align-items:center;gap:2px">
                <span class="material-symbols-outlined" style="font-size:12px">check_circle</span>Present
              </span>
            `}
          </div>
        </div>
      `;
    }).join('');
  }

  #renderSourceFilesList(srcDeps) {
    let files = srcDeps.sourceFiles || [];

    // Filter by selected dependency if any
    if (this.#selectedDepCoord) {
      files = files.filter(f => f.dependencies.includes(this.#selectedDepCoord));
    }

    if (this.#sourceSearchQuery) {
      const q = this.#sourceSearchQuery.toLowerCase();
      files = files.filter(f => f.path.toLowerCase().includes(q) || f.fileName.toLowerCase().includes(q));
    }

    if (!files.length) {
      return `<div style="padding:var(--sp-4);text-align:center;color:var(--text-muted);font-size:var(--text-xs)">No source files found</div>`;
    }

    return files.map(f => {
      const isActive = this.#selectedSourcePath === f.path;
      return `
        <div class="gradle-source-file-row ${isActive ? 'active' : ''}" data-path="${this.#esc(f.path)}">
          <div style="display:flex;align-items:center;gap:6px;overflow:hidden;flex:1">
            <span class="material-symbols-outlined" style="font-size:14px;color:var(--accent);flex-shrink:0">
              ${f.fileName.endsWith('.kt') ? 'terminal' : 'code'}
            </span>
            <div style="overflow:hidden;min-width:0;flex:1">
              <div style="font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${this.#esc(f.fileName)}</div>
              <div style="font-size:10px;color:var(--text-muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${this.#esc(f.path)}</div>
            </div>
          </div>
          <div style="display:flex;align-items:center;gap:4px;flex-shrink:0">
            <span class="badge" style="font-size:10px;padding:1px 6px;background:var(--bg-elevated)">
              ${f.dependencies.length} dep${f.dependencies.length === 1 ? '' : 's'}
            </span>
            <button class="btn btn-ghost btn-xs analyzer-open-editor-btn" data-path="${this.#esc(f.path)}" title="Open file in Editor" style="padding:2px 4px">
              <span class="material-symbols-outlined" style="font-size:14px">open_in_new</span>
            </button>
          </div>
        </div>
      `;
    }).join('');
  }

  #bindEvents() {
    const c = this.#container;
    if (!c) return;

    // ── Search & Filter Controls for Source Dependencies ──
    const depSearch = c.querySelector('#analyzer-dep-search');
    depSearch?.addEventListener('input', (e) => {
      this.#depSearchQuery = e.target.value;
      this.#updateDepsUI();
    });

    const srcSearch = c.querySelector('#analyzer-src-search');
    srcSearch?.addEventListener('input', (e) => {
      this.#sourceSearchQuery = e.target.value;
      this.#updateDepsUI();
    });

    c.querySelectorAll('[data-dep-filter]').forEach(btn => {
      btn.addEventListener('click', (e) => {
        this.#depFilterMode = e.currentTarget.dataset.depFilter;
        c.querySelectorAll('[data-dep-filter]').forEach(b => {
          b.classList.toggle('btn-primary', b.dataset.depFilter === this.#depFilterMode);
          b.classList.toggle('btn-ghost', b.dataset.depFilter !== this.#depFilterMode);
        });
        this.#updateDepsUI();
      });
    });

    c.querySelector('#analyzer-clear-dep-selection')?.addEventListener('click', () => {
      this.#selectedDepCoord = null;
      this.#updateDepsUI();
    });

    // ── Automatic File Selection: Clicking a Dependency selects files ──
    c.querySelectorAll('.gradle-dep-card').forEach(card => {
      card.addEventListener('click', (e) => {
        if (e.target.closest('.analyzer-inject-dep-btn')) return; // ignore button click
        const coord = card.dataset.coord;
        this.#selectedDepCoord = (this.#selectedDepCoord === coord) ? null : coord;
        this.#updateDepsUI();
      });
    });

    // ── Clicking a Source File filters/highlights its dependencies ──
    c.querySelectorAll('.gradle-source-file-row').forEach(row => {
      row.addEventListener('click', (e) => {
        if (e.target.closest('.analyzer-open-editor-btn')) return;
        const filePath = row.dataset.path;
        this.#selectedSourcePath = (this.#selectedSourcePath === filePath) ? null : filePath;

        // Auto-select first dependency of this file if available
        const srcDeps = this.#analysis.sourceDependencies || SourceDependencyAnalyzer.analyze(projectService.current?.files || {});
        const fileEntry = srcDeps.sourceFiles.find(f => f.path === filePath);
        if (fileEntry && fileEntry.dependencies.length > 0) {
          this.#selectedDepCoord = fileEntry.dependencies[0];
        }
        this.#updateDepsUI();
      });
    });

    // ── Open Source File in Editor ──
    c.querySelectorAll('.analyzer-open-editor-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const path = btn.dataset.path;
        const content = projectService.current?.files?.[path] || '';
        eventBus.emit('editor:openFile', { path, content });
        eventBus.emit('router:navigate', { pageId: 'editor' });
      });
    });

    // ── Inject Individual Dependency to Gradle ──
    c.querySelectorAll('.analyzer-inject-dep-btn').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const coord = btn.dataset.coord;
        await this.#addDependencyToGradle(coord);
      });
    });

    // ── Auto-Add All Missing to Gradle ──
    c.querySelector('#analyzer-add-all-missing-btn')?.addEventListener('click', async () => {
      await this.#addAllMissingToGradle();
    });

    c.querySelector('#analyzer-goto-gradle-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'gradle' });
    });

    c.querySelector('#analyzer-consumer-test-btn')?.addEventListener('click', () => {
      ConsumerResolutionModal.show();
    });

    // ── Top Action Buttons ──
    c.querySelector('#re-analyze-btn')?.addEventListener('click', () => {
      const project = projectService.current;
      if (project) {
        this.#analysis = ProjectAnalyzer.analyze(project.files || {}, project);
        this.#render();
        toast.info('Analysis Refreshed');
      }
    });

    c.querySelector('#dev-config-form')?.addEventListener('submit', async (e) => {
      e.preventDefault();
      await this.#applyConfiguration(new FormData(e.target));
    });

    c.querySelector('#patch-gradle-btn')?.addEventListener('click', async () => {
      const form = c.querySelector('#dev-config-form');
      if (form) {
        await this.#applyConfiguration(new FormData(form));
      }
    });

    c.querySelector('#go-to-gradle-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'gradle' });
    });

    c.querySelector('#go-to-workflows-btn')?.addEventListener('click', async () => {
      await this.#generateWorkflows();
    });

    c.querySelector('#go-to-publish-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'gh-publish' });
    });

    c.querySelector('#go-to-testing-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'gh-testing' });
    });
  }

  #updateDepsUI() {
    const srcDeps = this.#analysis.sourceDependencies || SourceDependencyAnalyzer.analyze(projectService.current?.files || {});
    const depsContainer = this.#container.querySelector('#analyzer-deps-container');
    const srcContainer  = this.#container.querySelector('#analyzer-source-files-container');

    if (depsContainer) {
      depsContainer.innerHTML = this.#renderDependenciesList(srcDeps);
    }
    if (srcContainer) {
      srcContainer.innerHTML = this.#renderSourceFilesList(srcDeps);
    }

    // Re-bind interactive events inside container
    this.#container.querySelectorAll('.gradle-dep-card').forEach(card => {
      card.addEventListener('click', (e) => {
        if (e.target.closest('.analyzer-inject-dep-btn')) return;
        const coord = card.dataset.coord;
        this.#selectedDepCoord = (this.#selectedDepCoord === coord) ? null : coord;
        this.#updateDepsUI();
      });
    });

    this.#container.querySelectorAll('.gradle-source-file-row').forEach(row => {
      row.addEventListener('click', (e) => {
        if (e.target.closest('.analyzer-open-editor-btn')) return;
        const filePath = row.dataset.path;
        this.#selectedSourcePath = (this.#selectedSourcePath === filePath) ? null : filePath;
        const fileEntry = srcDeps.sourceFiles.find(f => f.path === filePath);
        if (fileEntry && fileEntry.dependencies.length > 0) {
          this.#selectedDepCoord = fileEntry.dependencies[0];
        }
        this.#updateDepsUI();
      });
    });

    this.#container.querySelectorAll('.analyzer-open-editor-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const path = btn.dataset.path;
        const content = projectService.current?.files?.[path] || '';
        eventBus.emit('editor:openFile', { path, content });
        eventBus.emit('router:navigate', { pageId: 'editor' });
      });
    });

    this.#container.querySelectorAll('.analyzer-inject-dep-btn').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const coord = btn.dataset.coord;
        await this.#addDependencyToGradle(coord);
      });
    });
  }

  async #addDependencyToGradle(coordinate) {
    const p = projectService.current;
    if (!p) return;

    // Find primary module build.gradle or root build.gradle
    const primary = this.#analysis.primaryModule;
    const moduleName = primary?.name || (this.#analysis.modules[0] || 'app');
    const targetGradleKey = Object.keys(p.files || {}).find(k =>
      k === `${moduleName}/build.gradle` ||
      k === `${moduleName}/build.gradle.kts` ||
      k === 'build.gradle' ||
      k === 'build.gradle.kts'
    );

    if (!targetGradleKey) {
      toast.error('No build.gradle found', 'Could not locate target build.gradle file');
      return;
    }

    const originalContent = p.files[targetGradleKey] || '';
    const scopeAnalysis = TransitiveDependencyEngine.analyzeScope(coordinate, p.files || {});
    const scope = scopeAnalysis.scope;
    const res = GradlePatcher.addDependencies(originalContent, coordinate, scope);

    if (res.added.length > 0) {
      await projectService.setFile(targetGradleKey, res.content);
      consoleService.success(`Gradle Automation: added ${scope} '${coordinate}' (${scopeAnalysis.reason}) into ${targetGradleKey}`);
      toast.success('Dependency Added to Gradle', `${scope} '${coordinate}' -> ${targetGradleKey}`);

      // Re-analyze
      this.#analysis = ProjectAnalyzer.analyze(p.files, p);
      this.#render();
    } else {
      toast.info('Already Present', `${coordinate} is already declared in ${targetGradleKey}`);
    }
  }

  async #addAllMissingToGradle() {
    const p = projectService.current;
    if (!p) return;

    const srcDeps = this.#analysis.sourceDependencies || SourceDependencyAnalyzer.analyze(p.files || {});
    const missing = srcDeps.dependencies.filter(d => !d.isDeclared).map(d => d.coordinate);

    if (missing.length === 0) {
      toast.info('All Set', 'All detected external dependencies are already declared in Gradle');
      return;
    }

    const primary = this.#analysis.primaryModule;
    const moduleName = primary?.name || (this.#analysis.modules[0] || 'app');
    const targetGradleKey = Object.keys(p.files || {}).find(k =>
      k === `${moduleName}/build.gradle` ||
      k === `${moduleName}/build.gradle.kts` ||
      k === 'build.gradle'
    );

    if (!targetGradleKey) {
      toast.error('Build file not found', 'No build.gradle found in project');
      return;
    }

    let currentContent = p.files[targetGradleKey] || '';
    let addedCount = 0;

    for (const coord of missing) {
      const scopeAnalysis = TransitiveDependencyEngine.analyzeScope(coord, p.files || {});
      const res = GradlePatcher.addDependencies(currentContent, coord, scopeAnalysis.scope);
      currentContent = res.content;
      addedCount += res.added.length;
    }

    if (addedCount > 0) {
      await projectService.setFile(targetGradleKey, currentContent);
      consoleService.success(`Gradle Automation: injected ${addedCount} dependencies with auto-scoping into ${targetGradleKey}`);
      toast.success('Dependencies Injected', `Added ${addedCount} dependencies with intelligent scopes into ${targetGradleKey}`);

      this.#analysis = ProjectAnalyzer.analyze(p.files, p);
      this.#render();
    } else {
      toast.info('No changes needed');
    }
  }

  async #applyConfiguration(formData) {
    const p = projectService.current;
    if (!p) return;

    const projectType = formData.get('projectType') || this.#analysis.projectType;
    const jdkRequirement = formData.get('jdkRequirement') || '17';
    const version = formData.get('version') || p.version || '1.0.0';
    const compileSdk = formData.get('compileSdk') || p.compileSdk || '34';
    const minSdk = formData.get('minSdk') || p.minSdk || '21';
    const cleanLocalPaths = formData.get('cleanLocalPaths') === 'on';
    const enableGhPackages = formData.get('enableGhPackages') === 'on';

    p.projectType = projectType;
    p.version = version;
    p.compileSdk = compileSdk;
    p.minSdk = minSdk;
    p.jdkRequirement = jdkRequirement;

    consoleService.info(`Patching Gradle configuration for ${p.name} (type: ${projectType}, JDK: ${jdkRequirement})…`);

    const files = p.files || {};

    // 1. Patch settings.gradle
    const settingsKey = Object.keys(files).find(k => k === 'settings.gradle' || k === 'settings.gradle.kts');
    if (settingsKey) {
      const originalSettings = files[settingsKey] || '';
      const patchedSettings = GradlePatcher.patchSettings(originalSettings, {
        projectName: p.name,
        modules: this.#analysis.modules,
        cleanLocalMachinePaths: cleanLocalPaths,
        githubPackages: enableGhPackages ? { owner: p.author || 'OWNER', repo: p.repoName || p.name } : null,
      });
      await projectService.setFile(settingsKey, patchedSettings);
    }

    // 2. Patch root build.gradle
    const rootBuildKey = Object.keys(files).find(k => k === 'build.gradle' || k === 'build.gradle.kts');
    if (rootBuildKey) {
      const originalRootBuild = files[rootBuildKey] || '';
      const patchedRootBuild = GradlePatcher.patchRootBuild(originalRootBuild, {
        projectType,
        agpVersion: this.#analysis.agpVersion,
      });
      await projectService.setFile(rootBuildKey, patchedRootBuild);
    }

    // 3. Patch module build.gradle
    const primary = this.#analysis.primaryModule;
    const moduleName = primary?.name || (this.#analysis.modules[0] || 'app');
    const moduleBuildKey = Object.keys(files).find(k => k === `${moduleName}/build.gradle` || k === `${moduleName}/build.gradle.kts`);

    if (moduleBuildKey) {
      const originalModBuild = files[moduleBuildKey] || '';
      const patchedModBuild = GradlePatcher.patchModuleBuild(originalModBuild, {
        projectType,
        configurePublishing: projectType === 'library',
        cleanLocalMachinePaths: cleanLocalPaths,
        groupId: primary?.namespace || p.packageName,
        artifactId: primary?.name || p.repoName || p.name,
        version,
        compileSdk,
        minSdk,
        jdkRequirement,
      });
      await projectService.setFile(moduleBuildKey, patchedModBuild);
    }

    // Also update GitHub workflows
    await this.#generateWorkflows();

    await projectService.save(p);
    this.#analysis = ProjectAnalyzer.analyze(p.files, p);
    this.#render();

    consoleService.success('Gradle configuration & workflows patched successfully!');
    toast.success('Configuration Applied', 'Gradle files updated. Developer dependencies preserved.');
  }

  async #generateWorkflows() {
    const p = projectService.current;
    if (!p) return;

    const owner = p.author || 'OWNER';
    const isApp = p.projectType === 'app';
    const jdk = p.jdkRequirement || this.#analysis.jdkRequirement || '17';
    const primary = this.#analysis.primaryModule;
    const compileSdk = primary?.compileSdk || p.compileSdk || '34';

    const ciContent = githubFileGenerator.generateCIWorkflow({
      projectType: p.projectType || this.#analysis.projectType,
      jdkVersion: jdk,
      compileSdk,
      branch: 'main',
    });

    const releaseContent = githubFileGenerator.generateReleaseWorkflow({
      projectType: p.projectType || this.#analysis.projectType,
      jdkVersion: jdk,
      compileSdk,
      publishToPackages: p.projectType === 'library',
    });

    await projectService.setFile('.github/workflows/ci.yml', ciContent);
    await projectService.setFile('.github/workflows/release.yml', releaseContent);

    if (p.files['.github/workflows/build.yml']) {
      delete p.files['.github/workflows/build.yml'];
      await projectService.save();
    }

    consoleService.info('GitHub Actions workflows generated: .github/workflows/ci.yml & release.yml');
    toast.success('Workflows Ready', `CI & Release workflows tailored for ${isApp ? 'Android App' : 'Android Library'}`);
  }

  #esc(s) {
    if (!s) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
}
