/**
 * EnvironmentPage.js
 *
 * Dedicated Project Environment Management Page:
 * - Dynamic configuration for Java Library, Android Library, and Android Application
 * - Conflict & compatibility detection (JDK, Gradle Wrapper, AGP, compileSdk, dependencies)
 * - 1-Click Auto-Fix & file synchronization
 * - Live preview and editor for settings.gradle, build.gradle, wrapper, manifests, and README
 * - Mandatory 7-step pre-publish validation launcher
 */

import { EnvironmentManager } from '../modules/environment/EnvironmentManager.js';
import {
  PROJECT_TYPES,
  ENVIRONMENT_PRESETS,
} from '../modules/environment/EnvironmentCompatibilityMatrix.js';
import { DependencyCompatibilityAdvisor } from '../modules/environment/DependencyCompatibilityAdvisor.js';
import { PublishValidationWizard } from '../modules/environment/PublishValidationWizard.js';
import { projectService } from '../modules/storage/ProjectService.js';
import { toast } from '../ui/ToastService.js';
import { ClipboardUtil } from '../core/ClipboardUtil.js';
import { consoleService } from '../modules/storage/ConsoleService.js';
import { eventBus } from '../core/EventBus.js';

export class EnvironmentPage {
  #container = null;
  #activeTab = 'config'; // 'config' | 'diagnostics' | 'files' | 'readme'
  #selectedFileKey = 'build.gradle';
  #analysis = null;
  #currentConfig = null;
  #depAnalysis = null;
  #unsubs = [];

  async init(container) {
    this.#container = container;
    this.#refreshAnalysis();
    this.#render();
    this.#bindEvents();
  }

  destroy() {
    this.#unsubs.forEach(u => u());
    this.#unsubs = [];
    if (this.#container) {
      this.#container.innerHTML = '';
      this.#container = null;
    }
  }

  #refreshAnalysis() {
    this.#analysis = EnvironmentManager.analyze();
    this.#currentConfig = EnvironmentManager.resolveTargetConfig(this.#analysis);
    const files = projectService.current?.files || {};
    this.#depAnalysis = DependencyCompatibilityAdvisor.analyze(files, this.#currentConfig);
  }

  #bindEvents() {
    this.#unsubs.forEach(u => u());
    this.#unsubs = [
      eventBus.on('project:loaded', () => {
        this.#refreshAnalysis();
        this.#render();
      }),
      eventBus.on('project:fileChanged', () => {
        this.#refreshAnalysis();
        this.#render();
      })
    ];
  }

  #render() {
    if (!this.#container) return;

    const a = this.#analysis;
    const c = this.#currentConfig;
    const typeInfo = a.projectTypeInfo;
    const errorCount = a.issues.filter(i => i.severity === 'error').length;
    const warnCount = a.issues.filter(i => i.severity === 'warning').length;

    let scoreClass = 'healthy';
    if (a.healthScore < 50) scoreClass = 'danger';
    else if (a.healthScore < 80) scoreClass = 'warning';

    this.#container.innerHTML = `
      <div class="env-container">
        <!-- Header -->
        <div class="env-header">
          <div class="env-title-group">
            <h1>
              <span class="material-symbols-outlined" style="color:var(--accent)">tune</span>
              Project Environment Manager
            </h1>
            <p>Configure JDK, Gradle, AGP, and SDK compatibility for Java & Android projects</p>
          </div>
          <div style="display:flex;gap:8px;flex-wrap:wrap">
            <button class="btn btn-secondary btn-sm" id="env-auto-fix-all-btn" ${a.issues.length === 0 ? 'disabled' : ''}>
              <span class="material-symbols-outlined" style="font-size:16px">auto_fix_high</span> Auto-Fix All Issues
            </button>
            <button class="btn btn-primary btn-sm" id="env-launch-wizard-btn">
              <span class="material-symbols-outlined" style="font-size:16px">verified_user</span> Pre-Publish Wizard
            </button>
          </div>
        </div>

        <!-- Health Banner -->
        <div class="env-health-banner">
          <div class="env-health-left">
            <div class="env-score-circle ${scoreClass}">
              ${a.healthScore}%
            </div>
            <div>
              <div style="font-size:15px;font-weight:700;color:var(--text-primary);display:flex;align-items:center;gap:8px">
                ${typeInfo.name}
                <span class="badge ${a.isClean ? 'badge-success' : (errorCount ? 'badge-error' : 'badge-warning')}">
                  ${a.isClean ? 'COMPATIBLE' : `${errorCount} Errors, ${warnCount} Warnings`}
                </span>
              </div>
              <div style="font-size:12px;color:var(--text-secondary);margin-top:2px">
                ${typeInfo.description}
              </div>
            </div>
          </div>
          <div style="display:flex;gap:8px;font-size:12px;flex-wrap:wrap;align-items:center">
            <div style="padding:6px 12px;background:var(--bg-panel);border-radius:6px;border:1px solid var(--border)">
              <div style="color:var(--text-secondary);font-size:10px">JDK</div>
              <strong style="color:var(--text-primary)">JDK ${c.jdkVersion}</strong>
            </div>
            <div style="padding:6px 12px;background:var(--bg-panel);border-radius:6px;border:1px solid var(--border)">
              <div style="color:var(--text-secondary);font-size:10px">GRADLE</div>
              <strong style="color:var(--text-primary)">v${c.gradleVersion}</strong>
            </div>
            ${c.agpVersion ? `
            <div style="padding:6px 12px;background:var(--bg-panel);border-radius:6px;border:1px solid var(--border)">
              <div style="color:var(--text-secondary);font-size:10px">AGP</div>
              <strong style="color:var(--text-primary)">v${c.agpVersion}</strong>
            </div>` : ''}
            ${c.compileSdk ? `
            <div style="padding:6px 12px;background:var(--bg-panel);border-radius:6px;border:1px solid var(--border)">
              <div style="color:var(--text-secondary);font-size:10px">SDK</div>
              <strong style="color:var(--text-primary)">SDK ${c.compileSdk}</strong>
            </div>` : ''}
          </div>
        </div>

        <!-- Navigation Tabs -->
        <div class="env-nav-tabs">
          <button class="env-tab-btn ${this.#activeTab === 'config' ? 'active' : ''}" data-tab="config">
            <span class="material-symbols-outlined" style="font-size:16px">settings</span> Configuration & Toolchain
          </button>
          <button class="env-tab-btn ${this.#activeTab === 'diagnostics' ? 'active' : ''}" data-tab="diagnostics">
            <span class="material-symbols-outlined" style="font-size:16px">health_and_safety</span> Conflicts & Diagnostics (${a.issues.length})
          </button>
          <button class="env-tab-btn ${this.#activeTab === 'dependencies' ? 'active' : ''}" data-tab="dependencies">
            <span class="material-symbols-outlined" style="font-size:16px">inventory_2</span> Dependencies & Suggestions (${(this.#depAnalysis?.suggestions?.length || 0) + (this.#depAnalysis?.conflicts?.length || 0)})
          </button>
          <button class="env-tab-btn ${this.#activeTab === 'files' ? 'active' : ''}" data-tab="files">
            <span class="material-symbols-outlined" style="font-size:16px">code</span> Gradle & Manifest Files
          </button>
          <button class="env-tab-btn ${this.#activeTab === 'readme' ? 'active' : ''}" data-tab="readme">
            <span class="material-symbols-outlined" style="font-size:16px">description</span> README & JitPack Preview
          </button>
        </div>

        <!-- Tab Contents -->
        <div class="env-tab-content">
          ${this.#renderActiveTab()}
        </div>
      </div>
    `;

    this.#attachTabListeners();
  }

  #renderActiveTab() {
    switch (this.#activeTab) {
      case 'config':
        return this.#renderConfigTab();
      case 'diagnostics':
        return this.#renderDiagnosticsTab();
      case 'dependencies':
        return this.#renderDependenciesTab();
      case 'files':
        return this.#renderFilesTab();
      case 'readme':
        return this.#renderReadmeTab();
      default:
        return this.#renderConfigTab();
    }
  }

  #renderConfigTab() {
    const c = this.#currentConfig;
    const isAndroid = c.projectType === 'ANDROID_LIBRARY' || c.projectType === 'ANDROID_APP';
    const isApp = c.projectType === 'ANDROID_APP';

    return `
      <div class="env-grid-2">
        <!-- Preset and Type Selector -->
        <div class="env-card">
          <div class="env-card-header">
            <div class="env-card-title">
              <span class="material-symbols-outlined" style="color:var(--accent)">layers</span>
              Project Type & Presets
            </div>
          </div>

          <div class="env-form-group">
            <label for="env-project-type">Project Type</label>
            <select id="env-project-type">
              <option value="ANDROID_LIBRARY" ${c.projectType === 'ANDROID_LIBRARY' ? 'selected' : ''}>Android Library (AAR) — for JitPack</option>
              <option value="ANDROID_APP" ${c.projectType === 'ANDROID_APP' ? 'selected' : ''}>Android Application (APK/AAB)</option>
              <option value="JAVA_LIBRARY" ${c.projectType === 'JAVA_LIBRARY' ? 'selected' : ''}>Java Library (JAR) — for JitPack</option>
            </select>
          </div>

          <div class="env-form-group">
            <label for="env-preset-selector">Apply Standard Preset</label>
            <select id="env-preset-selector">
              <option value="">-- Choose Preset --</option>
              ${Object.values(ENVIRONMENT_PRESETS).map(p => `
                <option value="${p.id}">${p.name}</option>
              `).join('')}
            </select>
          </div>

          <div class="env-form-group">
            <label for="env-proj-name">Project Root Name</label>
            <input type="text" id="env-proj-name" value="${c.name || ''}" placeholder="MyAwesomeLib" />
          </div>

          <div class="env-form-group">
            <label for="env-group-id">Group ID / Namespace</label>
            <input type="text" id="env-group-id" value="${c.groupId || ''}" placeholder="com.example.lib" />
          </div>

          ${isApp ? `
          <div class="env-form-group">
            <label for="env-app-id">Application ID</label>
            <input type="text" id="env-app-id" value="${c.applicationId || ''}" placeholder="com.example.myapp" />
          </div>` : ''}
        </div>

        <!-- Toolchain & SDK Versions -->
        <div class="env-card">
          <div class="env-card-header">
            <div class="env-card-title">
              <span class="material-symbols-outlined" style="color:var(--accent)">build</span>
              Toolchain & SDK Versions
            </div>
          </div>

          <div class="env-form-group">
            <label for="env-jdk-version">Java / JDK Version</label>
            <select id="env-jdk-version">
              <option value="8" ${c.jdkVersion === '8' ? 'selected' : ''}>JDK 8 (Java 1.8)</option>
              <option value="11" ${c.jdkVersion === '11' ? 'selected' : ''}>JDK 11 LTS</option>
              <option value="17" ${c.jdkVersion === '17' ? 'selected' : ''}>JDK 17 LTS (Recommended for AGP 8.x)</option>
              <option value="21" ${c.jdkVersion === '21' ? 'selected' : ''}>JDK 21 LTS (Modern)</option>
            </select>
          </div>

          <div class="env-form-group">
            <label for="env-gradle-version">Gradle Wrapper Version</label>
            <select id="env-gradle-version">
              <option value="7.4.2" ${c.gradleVersion === '7.4.2' ? 'selected' : ''}>Gradle 7.4.2</option>
              <option value="7.6.4" ${c.gradleVersion === '7.6.4' ? 'selected' : ''}>Gradle 7.6.4 (LTS for AGP 7.4)</option>
              <option value="8.0.2" ${c.gradleVersion === '8.0.2' ? 'selected' : ''}>Gradle 8.0.2</option>
              <option value="8.4" ${c.gradleVersion === '8.4' ? 'selected' : ''}>Gradle 8.4</option>
              <option value="8.7" ${c.gradleVersion === '8.7' ? 'selected' : ''}>Gradle 8.7 (Recommended)</option>
              <option value="8.9" ${c.gradleVersion === '8.9' ? 'selected' : ''}>Gradle 8.9 (Latest Android 15)</option>
            </select>
          </div>

          ${isAndroid ? `
          <div class="env-form-group">
            <label for="env-agp-version">Android Gradle Plugin (AGP)</label>
            <select id="env-agp-version">
              <option value="7.4.2" ${c.agpVersion === '7.4.2' ? 'selected' : ''}>AGP 7.4.2</option>
              <option value="8.1.4" ${c.agpVersion === '8.1.4' ? 'selected' : ''}>AGP 8.1.4</option>
              <option value="8.2.2" ${c.agpVersion === '8.2.2' ? 'selected' : ''}>AGP 8.2.2</option>
              <option value="8.4.2" ${c.agpVersion === '8.4.2' ? 'selected' : ''}>AGP 8.4.2 (Recommended)</option>
              <option value="8.7.0" ${c.agpVersion === '8.7.0' ? 'selected' : ''}>AGP 8.7.0 (Android 15)</option>
            </select>
          </div>

          <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:12px">
            <div class="env-form-group">
              <label for="env-compile-sdk">compileSdk</label>
              <select id="env-compile-sdk">
                <option value="31" ${c.compileSdk === 31 ? 'selected' : ''}>31 (Android 12)</option>
                <option value="33" ${c.compileSdk === 33 ? 'selected' : ''}>33 (Android 13)</option>
                <option value="34" ${c.compileSdk === 34 ? 'selected' : ''}>34 (Android 14)</option>
                <option value="35" ${c.compileSdk === 35 ? 'selected' : ''}>35 (Android 15)</option>
                <option value="36" ${c.compileSdk === 36 ? 'selected' : ''}>36 (Android 16 Dev)</option>
              </select>
            </div>
            <div class="env-form-group">
              <label for="env-min-sdk">minSdk</label>
              <select id="env-min-sdk">
                <option value="21" ${c.minSdk === 21 ? 'selected' : ''}>21 (Android 5.0)</option>
                <option value="23" ${c.minSdk === 23 ? 'selected' : ''}>23 (Android 6.0)</option>
                <option value="24" ${c.minSdk === 24 ? 'selected' : ''}>24 (Android 7.0)</option>
                <option value="26" ${c.minSdk === 26 ? 'selected' : ''}>26 (Android 8.0)</option>
                <option value="28" ${c.minSdk === 28 ? 'selected' : ''}>28 (Android 9.0)</option>
              </select>
            </div>
            <div class="env-form-group">
              <label for="env-target-sdk">targetSdk</label>
              <select id="env-target-sdk">
                <option value="31" ${c.targetSdk === 31 ? 'selected' : ''}>31 (Android 12)</option>
                <option value="33" ${c.targetSdk === 33 ? 'selected' : ''}>33 (Android 13)</option>
                <option value="34" ${c.targetSdk === 34 ? 'selected' : ''}>34 (Android 14)</option>
                <option value="35" ${c.targetSdk === 35 ? 'selected' : ''}>35 (Android 15)</option>
                <option value="36" ${c.targetSdk === 36 ? 'selected' : ''}>36 (Android 16 Dev)</option>
              </select>
            </div>
          </div>
          ` : ''}

          <div style="margin-top:10px;display:flex;justify-content:flex-end">
            <button class="btn btn-primary btn-sm" id="env-save-config-btn">
              <span class="material-symbols-outlined" style="font-size:16px">save</span> Apply Configuration
            </button>
          </div>
        </div>
      </div>
    `;
  }

  #renderDiagnosticsTab() {
    const issues = this.#analysis.issues;

    if (!issues.length) {
      return `
        <div class="env-card" style="text-align:center;padding:40px 20px">
          <span class="material-symbols-outlined" style="font-size:56px;color:var(--success)">check_circle</span>
          <h3 style="font-size:18px;margin:12px 0 6px 0;color:var(--text-primary)">All Configurations Compatible!</h3>
          <p style="font-size:13px;color:var(--text-secondary);max-width:500px;margin:0 auto">
            No missing files, incompatible plugin versions, or conflicting SDK toolchain configurations detected. Your environment is production-ready.
          </p>
        </div>
      `;
    }

    return `
      <div class="env-card">
        <div class="env-card-header">
          <div class="env-card-title">
            <span class="material-symbols-outlined" style="color:var(--accent)">warning</span>
            Detected Conflicts & Environment Deficiencies (${issues.length})
          </div>
          <button class="btn btn-primary btn-sm" id="env-diag-fix-all-btn">
            <span class="material-symbols-outlined" style="font-size:16px">auto_fix_high</span> Fix All
          </button>
        </div>

        <div style="display:flex;flex-direction:column;gap:10px">
          ${issues.map(iss => `
            <div class="env-issue-item ${iss.severity}">
              <span class="material-symbols-outlined" style="color:${iss.severity === 'error' ? 'var(--error)' : 'var(--warning)'};font-size:20px">
                ${iss.severity === 'error' ? 'error' : 'warning'}
              </span>
              <div class="env-issue-content">
                <div class="env-issue-title">${iss.title}</div>
                <div class="env-issue-desc">${iss.message}</div>
                ${iss.file ? `<div class="env-issue-file">${iss.file}</div>` : ''}
              </div>
              <div>
                <button class="btn btn-secondary btn-sm" data-fix-issue="${iss.id}" style="padding:4px 10px;font-size:11px">
                  Fix Issue
                </button>
              </div>
            </div>
          `).join('')}
        </div>
      </div>
    `;
  }

  #renderDependenciesTab() {
    const da = this.#depAnalysis || { declaredDependencies: [], conflicts: [], suggestions: [], recommendedCatalog: [] };
    const declared = da.declaredDependencies || [];
    const conflicts = da.conflicts || [];
    const suggestions = da.suggestions || [];
    const catalog = da.recommendedCatalog || [];

    return `
      <div style="display:flex;flex-direction:column;gap:16px">
        <!-- Summary & Batch Action Bar -->
        <div class="env-card">
          <div class="env-card-header">
            <div class="env-card-title">
              <span class="material-symbols-outlined" style="color:var(--accent)">verified</span>
              SDK & Build Environment Dependency Analysis
            </div>
            <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
              <span class="env-version-pill">Target compileSdk: ${da.compileSdk}</span>
              <button class="btn btn-primary btn-sm" id="env-dep-upgrade-all-btn" ${suggestions.length === 0 ? 'disabled' : ''}>
                <span class="material-symbols-outlined" style="font-size:16px">upgrade</span>
                Upgrade All to Compatible Versions (${suggestions.length})
              </button>
            </div>
          </div>
          <p style="font-size:13px;color:var(--text-secondary);margin:0 0 12px 0">
            Dependencies are analyzed against Android compileSdk ${da.compileSdk}, minSdk ${da.minSdk}, AGP ${da.agpVersion}, and JDK ${da.jdkVersion}.
            Mismatches and deprecations are flagged automatically.
          </p>

          <!-- Conflicts Alert (if any) -->
          ${conflicts.length > 0 ? `
            <div style="display:flex;flex-direction:column;gap:8px;margin-bottom:16px">
              ${conflicts.map(c => `
                <div class="env-issue-item ${c.severity}">
                  <span class="material-symbols-outlined" style="color:${c.severity === 'error' ? 'var(--error)' : 'var(--warning)'};font-size:20px">
                    ${c.severity === 'error' ? 'dangerous' : 'warning'}
                  </span>
                  <div class="env-issue-content">
                    <div class="env-issue-title">${c.title}</div>
                    <div class="env-issue-desc">${c.message}</div>
                    <div style="font-size:12px;color:var(--accent);margin-top:4px">💡 ${c.suggestion}</div>
                  </div>
                  ${c.canAutoFix ? `
                    <button class="btn btn-secondary btn-sm" data-dep-fix="${c.coordinate}" data-target-coord="${c.fixAction?.targetCoordinate}" data-file-path="${c.fixAction?.filePath}" data-scope="${c.fixAction?.scope}">
                      Auto-Fix
                    </button>
                  ` : ''}
                </div>
              `).join('')}
            </div>
          ` : ''}

          <!-- Declared Dependencies Table -->
          <div style="overflow-x:auto">
            <table class="env-dep-table">
              <thead>
                <tr>
                  <th>Dependency Coordinate</th>
                  <th>Scope</th>
                  <th>Current Version</th>
                  <th>Recommended Version</th>
                  <th>Status</th>
                  <th style="text-align:right">Action</th>
                </tr>
              </thead>
              <tbody>
                ${declared.length === 0 ? `
                  <tr>
                    <td colspan="6" style="text-align:center;color:var(--text-muted);padding:24px">
                      No external dependencies declared in build.gradle yet. Use the catalog below to add recommended libraries.
                    </td>
                  </tr>
                ` : declared.map(dep => {
                  const suggestion = suggestions.find(s => s.groupArtifact === dep.groupArtifact);
                  const conflict = conflicts.find(c => c.coordinate.includes(dep.groupArtifact));
                  let statusBadge = '<span class="env-dep-badge compatible">✓ Compatible</span>';
                  if (conflict) {
                    statusBadge = '<span class="env-dep-badge conflict">⚠ Incompatible</span>';
                  } else if (suggestion) {
                    statusBadge = '<span class="env-dep-badge upgrade">↑ Upgrade Available</span>';
                  }

                  const targetVer = suggestion ? suggestion.recommendedVersion : (dep.version || '—');

                  return `
                    <tr>
                      <td>
                        <strong style="color:var(--text-primary)">${dep.groupArtifact}</strong>
                        <div style="font-size:11px;color:var(--text-muted)">${dep.filePath}</div>
                      </td>
                      <td>
                        <span class="badge badge-secondary" style="font-family:var(--font-mono);font-size:11px">${dep.scope}</span>
                      </td>
                      <td>
                        <span class="env-version-pill">${dep.version || 'unspecified'}</span>
                      </td>
                      <td>
                        <span class="env-version-pill ${suggestion ? 'target' : ''}">${targetVer}</span>
                      </td>
                      <td>${statusBadge}</td>
                      <td style="text-align:right">
                        ${suggestion ? `
                          <button class="btn btn-secondary btn-sm" data-dep-update="${dep.groupArtifact}" data-target-coord="${dep.groupArtifact}:${suggestion.recommendedVersion}" data-file-path="${dep.filePath}" data-scope="${dep.scope}">
                            <span class="material-symbols-outlined" style="font-size:14px">upgrade</span> Update
                          </button>
                        ` : `
                          <span style="font-size:12px;color:var(--text-muted)">Aligned</span>
                        `}
                      </td>
                    </tr>
                  `;
                }).join('')}
              </tbody>
            </table>
          </div>
        </div>

        <!-- Add Recommended Modern Library Catalog -->
        <div class="env-card">
          <div class="env-card-header">
            <div class="env-card-title">
              <span class="material-symbols-outlined" style="color:var(--accent)">add_circle</span>
              Add Verified Android & JVM Libraries (compileSdk ${da.compileSdk} Compatible)
            </div>
          </div>
          <p style="font-size:13px;color:var(--text-secondary);margin:0 0 12px 0">
            One-click insertion of official AndroidX, Jetpack, Kotlin Coroutines, Material 3, and HTTP networking libraries into your project with zero brace errors.
          </p>

          <div class="env-catalog-grid">
            ${catalog.map(cat => `
              <div class="env-catalog-card">
                <div>
                  <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:6px">
                    <strong style="color:var(--text-primary);font-size:14px">${cat.name}</strong>
                    <span class="badge badge-primary" style="font-size:10px">${cat.category}</span>
                  </div>
                  <div style="font-size:11px;color:var(--text-muted);font-family:var(--font-mono);margin-bottom:6px">
                    ${cat.groupArtifact}
                  </div>
                  <p style="font-size:12px;color:var(--text-secondary);margin:0 0 8px 0;line-height:1.4">
                    ${cat.description}
                  </p>
                </div>
                <div style="display:flex;justify-content:space-between;align-items:center;border-top:1px solid var(--border-subtle);padding-top:8px">
                  <span class="env-version-pill target">v${cat.recommendedVersion}</span>
                  <button class="btn btn-secondary btn-sm" data-add-lib="${cat.groupArtifact}:${cat.recommendedVersion}" data-scope="${cat.defaultScope}">
                    <span class="material-symbols-outlined" style="font-size:14px">add</span> Add Library
                  </button>
                </div>
              </div>
            `).join('')}
          </div>
        </div>
      </div>
    `;
  }

  #renderFilesTab() {
    const proj = projectService.current;
    const files = proj?.files || {};
    const generated = EnvironmentManager.generateConfigurationFiles(this.#currentConfig, files);

    const availableFiles = [
      'build.gradle',
      'settings.gradle',
      'gradle/wrapper/gradle-wrapper.properties',
    ];

    if (this.#currentConfig.projectType !== 'JAVA_LIBRARY') {
      availableFiles.push('src/main/AndroidManifest.xml');
    }
    if (this.#currentConfig.projectType !== 'ANDROID_APP') {
      availableFiles.push('jitpack.yml');
    }

    const currentContent = files[this.#selectedFileKey] || generated[this.#selectedFileKey] || '';

    return `
      <div class="env-card">
        <div class="env-card-header">
          <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
            <div class="env-card-title">
              <span class="material-symbols-outlined" style="color:var(--accent)">terminal</span>
              File Content Manager
            </div>
            <select id="env-file-select" style="padding:4px 10px;background:var(--bg-panel);border:1px solid var(--border);border-radius:6px;color:var(--text-primary);font-size:12px">
              ${availableFiles.map(f => `
                <option value="${f}" ${f === this.#selectedFileKey ? 'selected' : ''}>${f}</option>
              `).join('')}
            </select>
          </div>
          <div style="display:flex;gap:8px">
            <button class="btn btn-secondary btn-sm" id="env-copy-file-btn">
              <span class="material-symbols-outlined" style="font-size:15px">content_copy</span> Copy
            </button>
            <button class="btn btn-primary btn-sm" id="env-save-file-btn">
              <span class="material-symbols-outlined" style="font-size:15px">save</span> Save to Project
            </button>
          </div>
        </div>

        <div class="env-code-preview" id="env-code-preview-box">${this.#escapeHtml(currentContent)}</div>
      </div>
    `;
  }

  #renderReadmeTab() {
    const proj = projectService.current;
    const files = proj?.files || {};
    const generated = EnvironmentManager.generateConfigurationFiles(this.#currentConfig, files);
    const readmeContent = files['README.md'] || generated['README.md'] || '';

    return `
      <div class="env-card">
        <div class="env-card-header">
          <div class="env-card-title">
            <span class="material-symbols-outlined" style="color:var(--accent)">article</span>
            README.md & JitPack Installation Instructions
          </div>
          <div style="display:flex;gap:8px">
            <button class="btn btn-secondary btn-sm" id="env-copy-readme-btn">
              <span class="material-symbols-outlined" style="font-size:15px">content_copy</span> Copy
            </button>
            <button class="btn btn-primary btn-sm" id="env-save-readme-btn">
              <span class="material-symbols-outlined" style="font-size:15px">save</span> Save README
            </button>
          </div>
        </div>

        <div class="env-code-preview">${this.#escapeHtml(readmeContent)}</div>
      </div>
    `;
  }

  #syncInputsToConfig() {
    if (!this.#container) return;
    const projName = this.#container.querySelector('#env-proj-name')?.value;
    const groupId = this.#container.querySelector('#env-group-id')?.value;
    const appId = this.#container.querySelector('#env-app-id')?.value;
    const jdk = this.#container.querySelector('#env-jdk-version')?.value;
    const gradle = this.#container.querySelector('#env-gradle-version')?.value;
    const agp = this.#container.querySelector('#env-agp-version')?.value;
    const compileSdk = this.#container.querySelector('#env-compile-sdk')?.value;
    const minSdk = this.#container.querySelector('#env-min-sdk')?.value;
    const targetSdk = this.#container.querySelector('#env-target-sdk')?.value;

    if (projName !== undefined && projName !== null) this.#currentConfig.name = projName;
    if (groupId !== undefined && groupId !== null) {
      this.#currentConfig.groupId = groupId;
      this.#currentConfig.namespace = groupId;
    }
    if (appId !== undefined && appId !== null) this.#currentConfig.applicationId = appId;
    if (jdk) this.#currentConfig.jdkVersion = jdk;
    if (gradle) this.#currentConfig.gradleVersion = gradle;
    if (agp !== undefined && agp !== null) this.#currentConfig.agpVersion = agp;
    if (compileSdk) this.#currentConfig.compileSdk = parseInt(compileSdk, 10);
    if (minSdk) this.#currentConfig.minSdk = parseInt(minSdk, 10);
    if (targetSdk) this.#currentConfig.targetSdk = parseInt(targetSdk, 10);
  }

  #attachTabListeners() {
    // Tab switching
    this.#container.querySelectorAll('.env-tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        this.#syncInputsToConfig();
        this.#activeTab = btn.dataset.tab;
        this.#render();
      });
    });

    // Auto fix all
    this.#container.querySelector('#env-auto-fix-all-btn')?.addEventListener('click', async () => {
      this.#syncInputsToConfig();
      await EnvironmentManager.applyAllFixes(this.#currentConfig);
      toast.success('Environment Synchronized', 'All configuration files generated and updated.');
      this.#refreshAnalysis();
      this.#render();
    });

    this.#container.querySelector('#env-diag-fix-all-btn')?.addEventListener('click', async () => {
      this.#syncInputsToConfig();
      await EnvironmentManager.applyAllFixes(this.#currentConfig);
      toast.success('Environment Synchronized', 'All configuration files generated and updated.');
      this.#refreshAnalysis();
      this.#render();
    });

    // Launch Pre-Publish Wizard
    this.#container.querySelector('#env-launch-wizard-btn')?.addEventListener('click', () => {
      PublishValidationWizard.open();
    });

    // Preset selector
    this.#container.querySelector('#env-preset-selector')?.addEventListener('change', e => {
      this.#syncInputsToConfig();
      const presetId = e.target.value;
      if (!presetId || !ENVIRONMENT_PRESETS[presetId]) return;
      const preset = ENVIRONMENT_PRESETS[presetId];

      this.#currentConfig.projectType = preset.projectType;
      this.#currentConfig.jdkVersion = preset.jdkVersion;
      this.#currentConfig.gradleVersion = preset.gradleVersion;
      this.#currentConfig.agpVersion = preset.agpVersion;
      this.#currentConfig.compileSdk = preset.compileSdk;
      this.#currentConfig.minSdk = preset.minSdk;
      this.#currentConfig.targetSdk = preset.targetSdk;

      this.#render();
      toast.info('Preset Loaded', preset.name);
    });

    // Project type change
    this.#container.querySelector('#env-project-type')?.addEventListener('change', e => {
      this.#syncInputsToConfig();
      this.#currentConfig.projectType = e.target.value;
      if (this.#currentConfig.projectType === 'JAVA_LIBRARY') {
        this.#currentConfig.agpVersion = null;
        this.#currentConfig.compileSdk = null;
        this.#currentConfig.minSdk = null;
        this.#currentConfig.targetSdk = null;
      } else {
        if (!this.#currentConfig.agpVersion) this.#currentConfig.agpVersion = '8.4.2';
        if (!this.#currentConfig.compileSdk) this.#currentConfig.compileSdk = 34;
        if (!this.#currentConfig.minSdk) this.#currentConfig.minSdk = 21;
      }
      this.#render();
    });

    // Save config
    this.#container.querySelector('#env-save-config-btn')?.addEventListener('click', async () => {
      const projName = this.#container.querySelector('#env-proj-name')?.value;
      const groupId = this.#container.querySelector('#env-group-id')?.value;
      const appId = this.#container.querySelector('#env-app-id')?.value;
      const jdk = this.#container.querySelector('#env-jdk-version')?.value;
      const gradle = this.#container.querySelector('#env-gradle-version')?.value;
      const agp = this.#container.querySelector('#env-agp-version')?.value;
      const compileSdk = this.#container.querySelector('#env-compile-sdk')?.value;
      const minSdk = this.#container.querySelector('#env-min-sdk')?.value;
      const targetSdk = this.#container.querySelector('#env-target-sdk')?.value;

      if (projName) this.#currentConfig.name = projName;
      if (groupId) {
        this.#currentConfig.groupId = groupId;
        this.#currentConfig.namespace = groupId;
      }
      if (appId) this.#currentConfig.applicationId = appId;
      if (jdk) this.#currentConfig.jdkVersion = jdk;
      if (gradle) this.#currentConfig.gradleVersion = gradle;
      if (agp) this.#currentConfig.agpVersion = agp;
      if (compileSdk) this.#currentConfig.compileSdk = parseInt(compileSdk, 10);
      if (minSdk) this.#currentConfig.minSdk = parseInt(minSdk, 10);
      if (targetSdk) this.#currentConfig.targetSdk = parseInt(targetSdk, 10);

      await EnvironmentManager.applyAllFixes(this.#currentConfig);
      toast.success('Configuration Saved', 'Environment settings and Gradle files updated.');
      this.#refreshAnalysis();
      this.#render();
    });

    // Individual issue fix button
    this.#container.querySelectorAll('[data-fix-issue]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const issueId = btn.dataset.fixIssue;
        btn.disabled = true;
        btn.textContent = 'Fixing…';
        await EnvironmentManager.applyFix(issueId, this.#currentConfig);
        toast.success('Issue Fixed', `Resolved ${issueId}`);
        this.#refreshAnalysis();
        this.#render();
      });
    });

    // File selector in files tab
    this.#container.querySelector('#env-file-select')?.addEventListener('change', e => {
      this.#selectedFileKey = e.target.value;
      this.#render();
    });

    // Copy & Save file

    // Dependency update single
    this.#container.querySelectorAll('[data-dep-update], [data-dep-fix]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const filePath = btn.dataset.filePath || 'build.gradle';
        const targetCoord = btn.dataset.targetCoord;
        const scope = btn.dataset.scope || 'implementation';
        if (!targetCoord) return;

        btn.disabled = true;
        const ok = DependencyCompatibilityAdvisor.applyUpdate(filePath, targetCoord, scope);
        if (ok) {
          toast.success('Dependency Updated', `Applied ${targetCoord}`);
          this.#refreshAnalysis();
          this.#render();
        } else {
          toast.error('Update Failed', `Could not update ${targetCoord}`);
          btn.disabled = false;
        }
      });
    });

    // Batch upgrade all dependencies
    this.#container.querySelector('#env-dep-upgrade-all-btn')?.addEventListener('click', async () => {
      const suggestions = this.#depAnalysis?.suggestions || [];
      if (!suggestions.length) return;

      const count = DependencyCompatibilityAdvisor.batchApplyUpdates(suggestions);
      toast.success('Batch Upgrade Completed', `Updated ${count} dependencies to compatible versions.`);
      this.#refreshAnalysis();
      this.#render();
    });

    // Add Library from Catalog
    this.#container.querySelectorAll('[data-add-lib]').forEach(btn => {
      btn.addEventListener('click', async () => {
        const targetCoord = btn.dataset.addLib;
        const scope = btn.dataset.scope || 'implementation';
        const targetFile = this.#currentConfig.projectType === 'ANDROID_APP' ? 'app/build.gradle' : 'build.gradle';

        btn.disabled = true;
        const ok = DependencyCompatibilityAdvisor.applyUpdate(targetFile, targetCoord, scope);
        if (ok) {
          toast.success('Library Added', `Inserted ${targetCoord} into ${targetFile}`);
          this.#refreshAnalysis();
          this.#render();
        } else {
          toast.error('Add Failed', `Could not insert ${targetCoord}`);
          btn.disabled = false;
        }
      });
    });

    this.#container.querySelector('#env-copy-file-btn')?.addEventListener('click', async () => {
      const text = this.#container.querySelector('#env-code-preview-box')?.textContent || '';
      const ok = await ClipboardUtil.copyText(text);
      if (ok) {
        toast.info('Copied', `${this.#selectedFileKey} copied to clipboard`);
      } else {
        toast.error('Copy Failed', 'Please copy manually');
      }
    });

    this.#container.querySelector('#env-save-file-btn')?.addEventListener('click', async () => {
      const proj = projectService.current;
      const files = proj?.files || {};
      const generated = EnvironmentManager.generateConfigurationFiles(this.#currentConfig, files);
      const content = generated[this.#selectedFileKey] || '';
      if (content) {
        await projectService.setFile(this.#selectedFileKey, content);
        toast.success('Saved to Project', this.#selectedFileKey);
        this.#refreshAnalysis();
      }
    });

    // Copy & Save README
    this.#container.querySelector('#env-copy-readme-btn')?.addEventListener('click', async () => {
      const proj = projectService.current;
      const files = proj?.files || {};
      const generated = EnvironmentManager.generateConfigurationFiles(this.#currentConfig, files);
      const ok = await ClipboardUtil.copyText(generated['README.md'] || '');
      if (ok) {
        toast.info('Copied', 'README.md content copied to clipboard');
      } else {
        toast.error('Copy Failed', 'Please copy manually');
      }
    });

    this.#container.querySelector('#env-save-readme-btn')?.addEventListener('click', async () => {
      const proj = projectService.current;
      const files = proj?.files || {};
      const generated = EnvironmentManager.generateConfigurationFiles(this.#currentConfig, files);
      await projectService.setFile('README.md', generated['README.md'] || '');
      toast.success('Saved', 'README.md updated in project files');
      this.#refreshAnalysis();
    });
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
}
