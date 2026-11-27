/**
 * ManifestPage.js — Dedicated Android Manifest Management Page (Generate -> Manifest)
 *
 * Dedicated to managing AndroidManifest.xml files across project modules.
 * Fully separated from Gradle management to maintain clear functional boundaries.
 */

import { projectService } from '../modules/storage/ProjectService.js';
import { toast } from '../ui/ToastService.js';
import { consoleService } from '../modules/storage/ConsoleService.js';
import { modal } from '../ui/ModalService.js';
import { ManifestManager, COMMON_PERMISSIONS } from '../modules/generator/ManifestManager.js';
import { PreValidatorService } from '../modules/build/PreValidatorService.js';
import { ClipboardUtil } from '../core/ClipboardUtil.js';
import { WebViewBridge } from '../core/WebViewBridge.js';
import { eventBus } from '../core/EventBus.js';

export class ManifestPage {
  #container = null;
  #activeFile = null;
  #activeTab = 'permissions'; // 'permissions' | 'application' | 'components' | 'editor'
  #parsedManifest = null;
  #searchPermQuery = '';

  async init(container) {
    this.#container = container;
    const p = projectService.current;
    if (!p) {
      container.innerHTML = this.#noProject();
      container.querySelector('#manifest-goto-gen-btn')?.addEventListener('click', () => {
        eventBus.emit('router:navigate', { pageId: 'generator' });
      });
      return;
    }

    const manifestFiles = this.#getManifestFiles(p);
    this.#activeFile = manifestFiles[0] || null;

    if (this.#activeFile && p.files?.[this.#activeFile]) {
      this.#parsedManifest = ManifestManager.parse(p.files[this.#activeFile]);
    }

    this.#render(manifestFiles);
  }

  #getManifestFiles(p) {
    const files = p?.files || {};
    return Object.keys(files).filter(k => k.endsWith('AndroidManifest.xml'));
  }

  #render(manifestFiles = []) {
    if (!this.#container) return;
    const p = projectService.current;

    if (manifestFiles.length === 0) {
      this.#container.innerHTML = this.#renderEmptyState();
      this.#bindEmptyStateEvents();
      return;
    }

    const activeFile = this.#activeFile || manifestFiles[0];
    const xmlContent = p?.files?.[activeFile] || '';
    const parsed = this.#parsedManifest || ManifestManager.parse(xmlContent);

    this.#container.innerHTML = `
      <div class="page-container">
        
        <!-- Page Header -->
        <div class="page-header">
          <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-2)">
            <div>
              <h1 class="page-title">
                <span class="material-symbols-outlined" style="color:#38BDF8">description</span>
                Android Manifest Management
              </h1>
              <p class="page-subtitle">Configure Android package, permissions, application attributes, and components</p>
            </div>
            <div style="display:flex;gap:var(--sp-2);flex-wrap:wrap">
              <button type="button" class="btn btn-secondary btn-sm" id="manifest-validate-btn" title="Validate AndroidManifest XML syntax">
                <span class="material-symbols-outlined" style="font-size:16px">check_circle</span> Validate XML
              </button>
              <button type="button" class="btn btn-secondary btn-sm" id="manifest-scaffold-btn" title="Generate or reset standard manifest template">
                <span class="material-symbols-outlined" style="font-size:16px">auto_fix_normal</span> Scaffold
              </button>
              <button type="button" class="btn btn-secondary btn-sm" id="manifest-copy-btn" title="Copy full XML content to clipboard">
                <span class="material-symbols-outlined" style="font-size:16px">content_copy</span> Copy Full Content
              </button>
              <button type="button" class="btn btn-secondary btn-sm" id="manifest-new-module-btn">
                <span class="material-symbols-outlined" style="font-size:16px">add_circle</span> Add Manifest
              </button>
              <button type="button" class="btn btn-secondary btn-sm" id="manifest-download-btn">
                <span class="material-symbols-outlined" style="font-size:16px">download</span> Download XML
              </button>
              <button type="button" class="btn btn-primary btn-sm" id="manifest-save-btn">
                <span class="material-symbols-outlined" style="font-size:16px">save</span> Save Manifest
              </button>
            </div>
          </div>
        </div>

        <!-- Manifest File Selector & Main Workspace Split -->
        <div class="manifest-layout">
          
          <!-- Left: Module Manifest Files List -->
          <div class="manifest-sidebar-card">
            <div style="display:flex;align-items:center;justify-content:space-between;padding:4px 6px">
              <span style="font-size:11px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px">
                Manifest Files (${manifestFiles.length})
              </span>
            </div>

            <div style="display:flex;flex-direction:column;gap:4px">
              ${manifestFiles.map(f => {
                const isActive = f === activeFile;
                const isApp = f.includes('app/') || f.includes('sample/');
                const label = f.replace('/src/main/AndroidManifest.xml', '').replace('/AndroidManifest.xml', '') || 'root';
                return `
                  <div class="manifest-file-item ${isActive ? 'active' : ''}" data-file="${this.#esc(f)}">
                    <span class="material-symbols-outlined" style="font-size:16px;color:${isActive ? '#38BDF8' : 'var(--text-muted)'}">
                      ${isApp ? 'smartphone' : 'package_2'}
                    </span>
                    <div style="flex:1;min-width:0;overflow:hidden">
                      <div style="font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${this.#esc(label)}</div>
                      <div style="font-size:10px;opacity:0.7;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${this.#esc(f)}</div>
                    </div>
                  </div>
                `;
              }).join('')}
            </div>

            <!-- Diagnostics Pill -->
            ${parsed.diagnostics.length > 0 ? `
              <div style="margin-top:auto;padding:8px;background:rgba(239, 68, 68, 0.1);border:1px solid rgba(239, 68, 68, 0.3);border-radius:6px;font-size:11px;color:#FCA5A5">
                <strong style="display:flex;align-items:center;gap:4px">
                  <span class="material-symbols-outlined" style="font-size:14px;color:#EF4444">warning</span>
                  ${parsed.diagnostics.length} Diagnostic Warning${parsed.diagnostics.length > 1 ? 's' : ''}
                </strong>
                <p style="margin:4px 0 0;line-height:1.3">${this.#esc(parsed.diagnostics[0].message)}</p>
              </div>
            ` : `
              <div style="margin-top:auto;padding:8px;background:rgba(16, 185, 129, 0.1);border:1px solid rgba(16, 185, 129, 0.3);border-radius:6px;font-size:11px;color:#34D399;display:flex;align-items:center;gap:4px">
                <span class="material-symbols-outlined" style="font-size:14px">check_circle</span>
                <span>Manifest syntax valid</span>
              </div>
            `}
          </div>

          <!-- Right: Visual Manager & XML Editor -->
          <div class="manifest-main-card">
            
            <!-- File Info Bar -->
            <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px;padding-bottom:12px;border-bottom:1px solid var(--border-color)">
              <div style="display:flex;align-items:center;gap:8px">
                <span class="material-symbols-outlined" style="color:#38BDF8">code</span>
                <div>
                  <strong style="font-size:13px">${this.#esc(activeFile)}</strong>
                  ${parsed.packageName ? `<span style="font-size:11px;color:var(--text-muted);margin-left:6px">package: <code>${this.#esc(parsed.packageName)}</code></span>` : ''}
                </div>
              </div>
              <div style="display:flex;align-items:center;gap:6px">
                <span class="badge badge-info" style="font-size:11px">${parsed.permissions.length} Permissions</span>
                <span class="badge badge-neutral" style="font-size:11px">${parsed.activities.length} Activities</span>
              </div>
            </div>

            <!-- Tab Navigation -->
            <div class="tabs-nav" style="margin-bottom:0">
              <button class="tab-btn ${this.#activeTab === 'permissions' ? 'active' : ''}" id="manifest-tab-perm">
                <span class="material-symbols-outlined">security</span> Permissions (${parsed.permissions.length})
              </button>
              <button class="tab-btn ${this.#activeTab === 'application' ? 'active' : ''}" id="manifest-tab-app">
                <span class="material-symbols-outlined">settings_applications</span> Application Tags
              </button>
              <button class="tab-btn ${this.#activeTab === 'components' ? 'active' : ''}" id="manifest-tab-comp">
                <span class="material-symbols-outlined">widgets</span> Components (${parsed.activities.length + parsed.services.length + parsed.receivers.length})
              </button>
              <button class="tab-btn ${this.#activeTab === 'editor' ? 'active' : ''}" id="manifest-tab-editor">
                <span class="material-symbols-outlined">edit_note</span> XML Source
              </button>
            </div>

            <!-- Tab Content -->
            ${this.#renderActiveTabContent(activeFile, xmlContent, parsed)}

          </div>

        </div>

      </div>
    `;

    this.#bindEvents(manifestFiles);
  }

  #renderActiveTabContent(activeFile, xmlContent, parsed) {
    if (this.#activeTab === 'permissions') {
      return this.#renderPermissionsTab(parsed);
    }
    if (this.#activeTab === 'application') {
      return this.#renderApplicationTab(parsed);
    }
    if (this.#activeTab === 'components') {
      return this.#renderComponentsTab(parsed);
    }
    return this.#renderEditorTab(xmlContent);
  }

  #renderPermissionsTab(parsed) {
    const activePerms = parsed.permissions || [];

    return `
      <div style="display:flex;flex-direction:column;gap:14px">
        
        <!-- Active Declared Permissions -->
        <div>
          <label style="font-size:12px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px">
            Active Declared Permissions (${activePerms.length})
          </label>

          ${activePerms.length > 0 ? `
            <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:8px">
              ${activePerms.map(p => `
                <div class="ghw-chip" style="background:rgba(56, 189, 248, 0.1);border-color:rgba(56, 189, 248, 0.3)">
                  <span class="material-symbols-outlined" style="font-size:14px;color:#38BDF8">security</span>
                  <span style="font-family:var(--font-mono);font-size:11px;color:#F8FAFC">${this.#esc(p)}</span>
                  <button type="button" class="btn btn-ghost btn-xs manifest-remove-perm-btn" data-perm="${this.#esc(p)}" style="padding:0;color:#F87171" title="Remove permission">
                    <span class="material-symbols-outlined" style="font-size:14px">close</span>
                  </button>
                </div>
              `).join('')}
            </div>
          ` : `
            <p style="font-size:12px;color:var(--text-muted);margin:6px 0">No permissions currently declared in this manifest.</p>
          `}
        </div>

        <!-- Add Custom Permission Input -->
        <div style="display:flex;gap:8px;align-items:center;background:rgba(15, 23, 42, 0.5);padding:10px;border-radius:8px;border:1px solid var(--border-color)">
          <span class="material-symbols-outlined" style="color:var(--text-muted)">add_moderator</span>
          <input type="text" id="manifest-custom-perm-input" class="form-input" style="flex:1" placeholder="e.g. android.permission.ACCESS_FINE_LOCATION or custom permission" />
          <button type="button" class="btn btn-primary btn-sm" id="manifest-add-custom-perm-btn">
            <span class="material-symbols-outlined" style="font-size:15px">add</span> Add Permission
          </button>
        </div>

        <!-- Common Permissions Grid -->
        <div>
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
            <label style="font-size:12px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px">
              Quick Add Common Android Permissions
            </label>
          </div>

          <div class="manifest-perm-grid">
            ${COMMON_PERMISSIONS.map(cp => {
              const isAdded = activePerms.includes(cp.name);
              return `
                <div class="manifest-perm-card ${isAdded ? 'active' : ''}">
                  <div class="manifest-perm-top">
                    <span class="manifest-perm-title">${this.#esc(cp.label)}</span>
                    <button type="button" class="btn btn-xs ${isAdded ? 'btn-ghost' : 'btn-primary'} manifest-toggle-perm-btn" data-perm="${this.#esc(cp.name)}" data-added="${isAdded}">
                      <span class="material-symbols-outlined" style="font-size:13px">${isAdded ? 'check' : 'add'}</span>
                      ${isAdded ? 'Added' : 'Add'}
                    </button>
                  </div>
                  <div class="manifest-perm-name">${this.#esc(cp.name)}</div>
                  <div class="manifest-perm-desc">${this.#esc(cp.desc)}</div>
                </div>
              `;
            }).join('')}
          </div>
        </div>

      </div>
    `;
  }

  #renderApplicationTab(parsed) {
    const app = parsed.application || {};

    return `
      <div style="display:flex;flex-direction:column;gap:14px">
        <p style="font-size:12px;color:var(--text-secondary);margin:0">
          Configure top-level <code>&lt;application&gt;</code> attributes. Changes apply directly to the active manifest.
        </p>

        <div class="manifest-form-grid">
          <div class="manifest-field-card">
            <label>android:allowBackup</label>
            <select id="manifest-app-allowbackup" class="form-input">
              <option value="" ${app.allowBackup === null ? 'selected' : ''}>Not Specified</option>
              <option value="true" ${app.allowBackup === 'true' ? 'selected' : ''}>true (Enabled)</option>
              <option value="false" ${app.allowBackup === 'false' ? 'selected' : ''}>false (Disabled)</option>
            </select>
            <span style="font-size:10px;color:var(--text-muted)">Whether app allows ADB & cloud backups</span>
          </div>

          <div class="manifest-field-card">
            <label>android:supportsRtl</label>
            <select id="manifest-app-supportsrtl" class="form-input">
              <option value="" ${app.supportsRtl === null ? 'selected' : ''}>Not Specified</option>
              <option value="true" ${app.supportsRtl === 'true' ? 'selected' : ''}>true (Recommended)</option>
              <option value="false" ${app.supportsRtl === 'false' ? 'selected' : ''}>false</option>
            </select>
            <span style="font-size:10px;color:var(--text-muted)">Right-To-Left layout support</span>
          </div>

          <div class="manifest-field-card">
            <label>android:usesCleartextTraffic</label>
            <select id="manifest-app-cleartext" class="form-input">
              <option value="" ${app.usesCleartextTraffic === null ? 'selected' : ''}>Not Specified</option>
              <option value="true" ${app.usesCleartextTraffic === 'true' ? 'selected' : ''}>true (Allow HTTP)</option>
              <option value="false" ${app.usesCleartextTraffic === 'false' ? 'selected' : ''}>false (HTTPS Only)</option>
            </select>
            <span style="font-size:10px;color:var(--text-muted)">Allow unencrypted HTTP traffic</span>
          </div>

          <div class="manifest-field-card">
            <label>android:theme</label>
            <input type="text" id="manifest-app-theme" class="form-input" value="${this.#esc(app.theme || '')}" placeholder="@style/Theme.AppCompat" />
            <span style="font-size:10px;color:var(--text-muted)">Default application UI theme</span>
          </div>

          <div class="manifest-field-card">
            <label>android:label</label>
            <input type="text" id="manifest-app-label" class="form-input" value="${this.#esc(app.label || '')}" placeholder="@string/app_name" />
            <span style="font-size:10px;color:var(--text-muted)">Application title or string resource</span>
          </div>

          <div class="manifest-field-card">
            <label>android:icon</label>
            <input type="text" id="manifest-app-icon" class="form-input" value="${this.#esc(app.icon || '')}" placeholder="@mipmap/ic_launcher" />
            <span style="font-size:10px;color:var(--text-muted)">Main application icon resource</span>
          </div>
        </div>

        <div style="display:flex;justify-content:flex-end">
          <button type="button" class="btn btn-primary btn-sm" id="manifest-save-app-attrs-btn">
            <span class="material-symbols-outlined" style="font-size:15px">check</span> Apply Application Changes
          </button>
        </div>
      </div>
    `;
  }

  #renderComponentsTab(parsed) {
    const activities = parsed.activities || [];
    const services = parsed.services || [];
    const receivers = parsed.receivers || [];

    return `
      <div style="display:flex;flex-direction:column;gap:14px">
        
        <!-- Header & Add Button -->
        <div style="display:flex;align-items:center;justify-content:space-between">
          <label style="font-size:12px;font-weight:700;color:var(--text-muted);text-transform:uppercase;letter-spacing:0.5px">
            Declared Activities (${activities.length})
          </label>
          <button type="button" class="btn btn-secondary btn-xs" id="manifest-add-activity-btn">
            <span class="material-symbols-outlined" style="font-size:14px">add</span> Add Activity
          </button>
        </div>

        <!-- Activities List -->
        ${activities.length > 0 ? `
          <div style="display:flex;flex-direction:column;gap:6px">
            ${activities.map(act => `
              <div class="manifest-component-row">
                <div style="display:flex;align-items:center;gap:8px">
                  <span class="material-symbols-outlined" style="color:#38BDF8;font-size:18px">window</span>
                  <div>
                    <strong style="font-size:12px">${this.#esc(act.name)}</strong>
                    <div style="display:flex;gap:4px;margin-top:2px">
                      ${act.isLauncher ? `<span class="badge badge-success" style="font-size:9px;padding:0 4px">LAUNCHER</span>` : ''}
                      ${act.exported ? `<span class="badge ${act.exported === 'true' ? 'badge-info' : 'badge-neutral'}" style="font-size:9px;padding:0 4px">exported="${act.exported}"</span>` : '<span class="badge badge-warning" style="font-size:9px;padding:0 4px">no exported flag</span>'}
                    </div>
                  </div>
                </div>
              </div>
            `).join('')}
          </div>
        ` : `
          <p style="font-size:12px;color:var(--text-muted)">No activities declared in this module.</p>
        `}

        <!-- Services & Receivers Summary -->
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:6px">
          <div class="manifest-field-card">
            <label>Services (${services.length})</label>
            ${services.length > 0 ? `
              <div style="font-size:11px;color:var(--text-secondary)">${services.map(s => this.#esc(s.name)).join(', ')}</div>
            ` : '<span style="font-size:11px;color:var(--text-muted)">None declared</span>'}
          </div>

          <div class="manifest-field-card">
            <label>Broadcast Receivers (${receivers.length})</label>
            ${receivers.length > 0 ? `
              <div style="font-size:11px;color:var(--text-secondary)">${receivers.map(r => this.#esc(r.name)).join(', ')}</div>
            ` : '<span style="font-size:11px;color:var(--text-muted)">None declared</span>'}
          </div>
        </div>

      </div>
    `;
  }

  #renderEditorTab(xmlContent) {
    return `
      <div style="display:flex;flex-direction:column;gap:10px">
        <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:6px">
          <span style="font-size:12px;color:var(--text-muted)">Direct XML Source Editor with syntax preservation</span>
          <div style="display:flex;gap:6px;flex-wrap:wrap">
            <button type="button" class="btn btn-secondary btn-xs" id="manifest-validate-xml-btn">
              <span class="material-symbols-outlined" style="font-size:14px">check_circle</span> Validate XML
            </button>
            <button type="button" class="btn btn-secondary btn-xs" id="manifest-copy-xml-btn">
              <span class="material-symbols-outlined" style="font-size:14px">content_copy</span> Copy Full Content
            </button>
            <button type="button" class="btn btn-secondary btn-xs" id="manifest-format-xml-btn">
              <span class="material-symbols-outlined" style="font-size:14px">format_align_left</span> Format XML
            </button>
            <button type="button" class="btn btn-secondary btn-xs" id="manifest-reset-default-btn">
              <span class="material-symbols-outlined" style="font-size:14px">restart_alt</span> Reset to Default
            </button>
          </div>
        </div>

        <textarea id="manifest-raw-xml-editor" class="manifest-editor-textarea" spellcheck="false">${this.#esc(xmlContent)}</textarea>
      </div>
    `;
  }

  #renderEmptyState() {
    return `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title"><span class="material-symbols-outlined" style="color:#38BDF8">description</span>Android Manifest Management</h1>
          <p class="page-subtitle">No AndroidManifest.xml found in current project</p>
        </div>

        <div class="card" style="padding:48px 24px;text-align:center;display:flex;flex-direction:column;align-items:center;gap:12px">
          <span class="material-symbols-outlined" style="font-size:48px;color:var(--text-muted)">description</span>
          <h3 style="margin:0;font-size:16px">No Manifest File Detected</h3>
          <p style="margin:0;max-width:460px;font-size:13px;color:var(--text-muted)">
            Android modules require an AndroidManifest.xml file specifying package, permissions, and components.
          </p>
          <div style="display:flex;gap:10px;margin-top:12px;flex-wrap:wrap;justify-content:center">
            <button type="button" class="btn btn-primary" id="manifest-create-lib-btn">
              <span class="material-symbols-outlined">package_2</span> Create Library Manifest
            </button>
            <button type="button" class="btn btn-secondary" id="manifest-create-app-btn">
              <span class="material-symbols-outlined">smartphone</span> Create App Manifest
            </button>
            <button type="button" class="btn btn-ghost" id="manifest-empty-goto-gen-btn">
              <span class="material-symbols-outlined">auto_fix_high</span> Go to Generator
            </button>
          </div>
        </div>
      </div>
    `;
  }

  #bindEvents(manifestFiles) {
    const c = this.#container;
    if (!c) return;

    // Tab buttons
    c.querySelector('#manifest-tab-perm')?.addEventListener('click', () => {
      this.#syncEditorToActive();
      this.#activeTab = 'permissions';
      this.#render(manifestFiles);
    });
    c.querySelector('#manifest-tab-app')?.addEventListener('click', () => {
      this.#syncEditorToActive();
      this.#activeTab = 'application';
      this.#render(manifestFiles);
    });
    c.querySelector('#manifest-tab-comp')?.addEventListener('click', () => {
      this.#syncEditorToActive();
      this.#activeTab = 'components';
      this.#render(manifestFiles);
    });
    c.querySelector('#manifest-tab-editor')?.addEventListener('click', () => {
      this.#activeTab = 'editor';
      this.#render(manifestFiles);
    });

    // File selection
    c.querySelectorAll('.manifest-file-item').forEach(item => {
      item.addEventListener('click', () => {
        const file = item.getAttribute('data-file');
        if (file) {
          this.#syncEditorToActive();
          this.#activeFile = file;
          const p = projectService.current;
          if (p?.files?.[file]) {
            this.#parsedManifest = ManifestManager.parse(p.files[file]);
          }
          this.#render(manifestFiles);
        }
      });
    });

    // Toggle Permission from Common Grid
    c.querySelectorAll('.manifest-toggle-perm-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        const perm = btn.getAttribute('data-perm');
        const isAdded = btn.getAttribute('data-added') === 'true';
        const p = projectService.current;
        if (!p || !this.#activeFile) return;

        let curXml = p.files[this.#activeFile] || '';
        if (isAdded) {
          curXml = ManifestManager.removePermission(curXml, perm);
          toast.info('Permission Removed', `Removed ${perm}`);
        } else {
          curXml = ManifestManager.addPermission(curXml, perm);
          toast.success('Permission Added', `Added ${perm}`);
        }

        p.files[this.#activeFile] = curXml;
        await projectService.save();
        this.#parsedManifest = ManifestManager.parse(curXml);
        this.#render(manifestFiles);
      });
    });

    // Remove Permission chip
    c.querySelectorAll('.manifest-remove-perm-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        const perm = btn.getAttribute('data-perm');
        const p = projectService.current;
        if (!p || !this.#activeFile || !perm) return;

        let curXml = p.files[this.#activeFile] || '';
        curXml = ManifestManager.removePermission(curXml, perm);
        p.files[this.#activeFile] = curXml;
        await projectService.save();
        this.#parsedManifest = ManifestManager.parse(curXml);
        toast.info('Permission Removed', `Removed ${perm}`);
        this.#render(manifestFiles);
      });
    });

    // Add Custom Permission
    c.querySelector('#manifest-add-custom-perm-btn')?.addEventListener('click', async () => {
      const input = c.querySelector('#manifest-custom-perm-input');
      const val = input?.value.trim();
      if (!val) return;

      const p = projectService.current;
      if (!p || !this.#activeFile) return;

      let curXml = p.files[this.#activeFile] || '';
      curXml = ManifestManager.addPermission(curXml, val);
      p.files[this.#activeFile] = curXml;
      await projectService.save();
      this.#parsedManifest = ManifestManager.parse(curXml);
      toast.success('Permission Added', `Added ${val}`);
      this.#render(manifestFiles);
    });

    // Save Application Attributes
    c.querySelector('#manifest-save-app-attrs-btn')?.addEventListener('click', async () => {
      const p = projectService.current;
      if (!p || !this.#activeFile) return;

      let curXml = p.files[this.#activeFile] || '';

      const allowBackup = c.querySelector('#manifest-app-allowbackup')?.value;
      const supportsRtl = c.querySelector('#manifest-app-supportsrtl')?.value;
      const cleartext = c.querySelector('#manifest-app-cleartext')?.value;
      const theme = c.querySelector('#manifest-app-theme')?.value.trim();
      const label = c.querySelector('#manifest-app-label')?.value.trim();
      const icon = c.querySelector('#manifest-app-icon')?.value.trim();

      curXml = ManifestManager.setApplicationAttribute(curXml, 'allowBackup', allowBackup || null);
      curXml = ManifestManager.setApplicationAttribute(curXml, 'supportsRtl', supportsRtl || null);
      curXml = ManifestManager.setApplicationAttribute(curXml, 'usesCleartextTraffic', cleartext || null);
      curXml = ManifestManager.setApplicationAttribute(curXml, 'theme', theme || null);
      curXml = ManifestManager.setApplicationAttribute(curXml, 'label', label || null);
      curXml = ManifestManager.setApplicationAttribute(curXml, 'icon', icon || null);

      p.files[this.#activeFile] = curXml;
      await projectService.save();
      this.#parsedManifest = ManifestManager.parse(curXml);
      toast.success('Attributes Saved', 'Updated <application> tag attributes');
      this.#render(manifestFiles);
    });

    // Add Activity Modal
    c.querySelector('#manifest-add-activity-btn')?.addEventListener('click', () => {
      modal.show({
        title: 'Add Activity to Manifest',
        content: `
          <div style="display:flex;flex-direction:column;gap:12px">
            <div class="form-group">
              <label class="form-label">Activity Class Name:</label>
              <input type="text" id="manifest-new-act-name" class="form-input" placeholder=".DetailActivity" value=".DetailActivity" />
            </div>
            <div class="form-group">
              <label class="form-label">android:exported (Required for Android 12+):</label>
              <select id="manifest-new-act-exported" class="form-input">
                <option value="false">false (Internal to app only)</option>
                <option value="true">true (Exported / Accessible to system)</option>
              </select>
            </div>
            <div style="display:flex;align-items:center;gap:8px">
              <input type="checkbox" id="manifest-new-act-launcher" />
              <label for="manifest-new-act-launcher" style="font-size:13px;cursor:pointer">Set as Main Launcher Activity</label>
            </div>
          </div>
        `,
        buttons: [
          { label: 'Cancel', variant: 'secondary', onClick: () => modal.close() },
          {
            label: 'Add Activity',
            variant: 'primary',
            onClick: async () => {
              const actName = document.getElementById('manifest-new-act-name')?.value.trim();
              const expVal = document.getElementById('manifest-new-act-exported')?.value;
              const isLauncher = !!document.getElementById('manifest-new-act-launcher')?.checked;
              modal.close();

              if (!actName) return;
              const p = projectService.current;
              if (!p || !this.#activeFile) return;

              let curXml = p.files[this.#activeFile] || '';
              curXml = ManifestManager.addActivity(curXml, { name: actName, exported: expVal, isLauncher });
              p.files[this.#activeFile] = curXml;
              await projectService.save();
              this.#parsedManifest = ManifestManager.parse(curXml);
              toast.success('Activity Added', `Added ${actName} to manifest`);
              this.#render(manifestFiles);
            }
          }
        ]
      });
    });

    // Format XML Button
    c.querySelector('#manifest-format-xml-btn')?.addEventListener('click', async () => {
      const ta = c.querySelector('#manifest-raw-xml-editor');
      if (ta) {
        const formatted = ManifestManager.formatXml(ta.value);
        ta.value = formatted;
        const p = projectService.current;
        if (p && this.#activeFile) {
          p.files[this.#activeFile] = formatted;
          await projectService.save();
          this.#parsedManifest = ManifestManager.parse(formatted);
          toast.success('XML Formatted', 'Cleaned up indentation and structure.');
        }
      }
    });

    // Reset to Default Template
    c.querySelector('#manifest-reset-default-btn')?.addEventListener('click', () => {
      modal.confirm({
        title: 'Reset Manifest to Default?',
        message: 'This will replace the current manifest content with a standard template.',
        confirmLabel: 'Reset',
        confirmVariant: 'danger',
        onConfirm: async () => {
          const p = projectService.current;
          if (!p || !this.#activeFile) return;

          const isApp = this.#activeFile.includes('app/') || this.#activeFile.includes('sample/');
          const defaultXml = ManifestManager.createDefaultManifest({
            isApp,
            packageName: p.packageName || 'com.example.android',
            label: p.name || 'AndroidModule'
          });

          p.files[this.#activeFile] = defaultXml;
          await projectService.save();
          this.#parsedManifest = ManifestManager.parse(defaultXml);
          toast.success('Manifest Reset', 'Applied standard default template.');
          this.#render(manifestFiles);
        }
      });
    });

    // Validate Manifest XML Button
    const validateManifest = () => {
      this.#syncEditorToActive();
      const p = projectService.current;
      if (!p || !this.#activeFile) return;

      const content = p.files[this.#activeFile] || '';
      const isApp = this.#activeFile.includes('app/') || this.#activeFile.includes('sample/');
      const check = PreValidatorService.validateManifestXml(content, this.#activeFile, isApp);

      if (check.valid && check.warnings.length === 0) {
        toast.success('XML Valid', `${this.#activeFile} passed XML validation with no errors.`);
        modal.show({
          title: 'Manifest Validation Passed',
          content: `
            <div style="display:flex;align-items:center;gap:12px;color:#10B981">
              <span class="material-symbols-outlined" style="font-size:32px">check_circle</span>
              <div>
                <strong style="display:block;font-size:14px">AndroidManifest.xml is Valid</strong>
                <span style="font-size:12px;color:var(--text-secondary)">No parsing errors, valid root &lt;manifest&gt; element, and valid XML syntax.</span>
              </div>
            </div>
          `,
          buttons: [{ label: 'Close', variant: 'primary', onClick: () => modal.close() }]
        });
      } else {
        const title = check.valid ? 'Manifest Warnings' : 'Manifest Validation Errors';
        const icon = check.valid ? 'warning' : 'error';
        const color = check.valid ? '#F59E0B' : '#EF4444';
        modal.show({
          title,
          content: `
            <div style="display:flex;flex-direction:column;gap:10px">
              <div style="display:flex;align-items:center;gap:8px;color:${color}">
                <span class="material-symbols-outlined">${icon}</span>
                <strong>${check.errors.length} error(s), ${check.warnings.length} warning(s) in ${this.#esc(this.#activeFile)}</strong>
              </div>
              <ul style="margin:0;padding-left:20px;font-size:12px;line-height:1.5">
                ${check.errors.map(e => `<li style="color:#EF4444;font-weight:600">${this.#esc(e)}</li>`).join('')}
                ${check.warnings.map(w => `<li style="color:#F59E0B">${this.#esc(w)}</li>`).join('')}
              </ul>
            </div>
          `,
          buttons: [{ label: 'Close', variant: 'secondary', onClick: () => modal.close() }]
        });
        if (!check.valid) {
          toast.error('Validation Errors', check.errors[0]);
        }
      }
    };

    c.querySelector('#manifest-validate-btn')?.addEventListener('click', validateManifest);
    c.querySelector('#manifest-validate-xml-btn')?.addEventListener('click', validateManifest);

    // Copy Full Content Buttons
    const copyContent = async () => {
      this.#syncEditorToActive();
      const p = projectService.current;
      const content = p?.files?.[this.#activeFile] || '';
      if (!content) {
        toast.info('Manifest is empty', 'Nothing to copy');
        return;
      }
      const ok = await ClipboardUtil.copyText(content);
      if (ok) {
        toast.success('Copied Full Content', 'AndroidManifest.xml copied to clipboard');
      } else {
        toast.error('Copy failed', 'Please select text and copy manually');
      }
    };

    c.querySelector('#manifest-copy-btn')?.addEventListener('click', copyContent);
    c.querySelector('#manifest-copy-xml-btn')?.addEventListener('click', copyContent);

    // Scaffold / Generate Manifest Button
    c.querySelector('#manifest-scaffold-btn')?.addEventListener('click', () => {
      const p = projectService.current;
      modal.show({
        title: 'Scaffold Android Manifest',
        content: `
          <div style="display:flex;flex-direction:column;gap:12px">
            <p style="font-size:13px;color:var(--text-secondary);margin:0">
              Generate a clean, standard AndroidManifest.xml template:
            </p>
            <div class="form-group">
              <label class="form-label">Module Type:</label>
              <select id="manifest-scaffold-type" class="form-input">
                <option value="library">Android Library (AAR) — &lt;manifest&gt; without launcher activity</option>
                <option value="app">Android Application (APK/AAB) — includes MainActivity & launcher intent-filter</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">Package Name:</label>
              <input type="text" id="manifest-scaffold-pkg" class="form-input" value="${this.#esc(p?.packageName || 'com.example.android')}" />
            </div>
            <div class="form-group">
              <label class="form-label">App / Library Label:</label>
              <input type="text" id="manifest-scaffold-label" class="form-input" value="${this.#esc(p?.name || 'MyModule')}" />
            </div>
          </div>
        `,
        buttons: [
          { label: 'Cancel', variant: 'secondary', onClick: () => modal.close() },
          {
            label: 'Generate Manifest',
            variant: 'primary',
            onClick: async () => {
              const isApp = document.getElementById('manifest-scaffold-type')?.value === 'app';
              const pkgName = document.getElementById('manifest-scaffold-pkg')?.value.trim() || 'com.example.android';
              const label = document.getElementById('manifest-scaffold-label')?.value.trim() || 'MyModule';
              modal.close();

              const generated = ManifestManager.createDefaultManifest({
                isApp,
                packageName: pkgName,
                label
              });

              if (p && this.#activeFile) {
                p.files[this.#activeFile] = generated;
                await projectService.save();
                this.#parsedManifest = ManifestManager.parse(generated);
                toast.success('Manifest Generated', `Scaffolded ${isApp ? 'App' : 'Library'} manifest`);
                this.#render(manifestFiles);
              }
            }
          }
        ]
      });
    });

    // Save Manifest with XML Pre-Validation
    c.querySelector('#manifest-save-btn')?.addEventListener('click', async () => {
      this.#syncEditorToActive();
      const p = projectService.current;
      if (!p || !this.#activeFile) return;

      const content = p.files[this.#activeFile] || '';
      const isApp = this.#activeFile.includes('app/') || this.#activeFile.includes('sample/');
      const check = PreValidatorService.validateManifestXml(content, this.#activeFile, isApp);

      if (!check.valid) {
        toast.error('Cannot Save: Invalid XML', check.errors[0] || 'Fix XML syntax errors before saving');
        consoleService.error(`Manifest XML validation failed for ${this.#activeFile}: ${check.errors.join('; ')}`);
        return;
      }

      await projectService.save();
      toast.success('Manifest Saved', `Successfully saved ${this.#activeFile}`);
      consoleService.success(`Saved manifest ${this.#activeFile}`);
      this.#parsedManifest = ManifestManager.parse(content);
      this.#render(manifestFiles);
    });

    // Download XML
    c.querySelector('#manifest-download-btn')?.addEventListener('click', () => {
      const p = projectService.current;
      const content = p?.files?.[this.#activeFile] || '';
      WebViewBridge.downloadFile('AndroidManifest.xml', content, 'application/xml;charset=utf-8');
      toast.info('Downloaded', 'AndroidManifest.xml downloaded.');
    });

    // Add Manifest File for another module
    c.querySelector('#manifest-new-module-btn')?.addEventListener('click', () => {
      modal.show({
        title: 'Add Android Manifest',
        content: `
          <div style="display:flex;flex-direction:column;gap:12px">
            <div class="form-group">
              <label class="form-label">Module Directory Name:</label>
              <input type="text" id="manifest-module-name-input" class="form-input" placeholder="e.g. library or app or core" value="library" />
            </div>
            <div class="form-group">
              <label class="form-label">Type:</label>
              <select id="manifest-module-type-input" class="form-input">
                <option value="library">Android Library (AAR)</option>
                <option value="app">Android Application</option>
              </select>
            </div>
          </div>
        `,
        buttons: [
          { label: 'Cancel', variant: 'secondary', onClick: () => modal.close() },
          {
            label: 'Create File',
            variant: 'primary',
            onClick: async () => {
              const mod = document.getElementById('manifest-module-name-input')?.value.trim() || 'library';
              const type = document.getElementById('manifest-module-type-input')?.value || 'library';
              modal.close();

              const filePath = `${mod}/src/main/AndroidManifest.xml`;
              const p = projectService.current;
              if (!p) return;

              if (p.files[filePath]) {
                toast.error('File Exists', `${filePath} already exists`);
                return;
              }

              const newXml = ManifestManager.createDefaultManifest({
                isApp: type === 'app',
                packageName: `${p.packageName || 'com.example'}.${mod}`,
                label: p.name || mod
              });

              p.files[filePath] = newXml;
              await projectService.save();
              this.#activeFile = filePath;
              this.#parsedManifest = ManifestManager.parse(newXml);
              toast.success('Manifest Created', `Created ${filePath}`);
              this.#render(this.#getManifestFiles(p));
            }
          }
        ]
      });
    });
  }

  #syncEditorToActive() {
    const ta = this.#container?.querySelector('#manifest-raw-xml-editor');
    if (ta && this.#activeFile) {
      const p = projectService.current;
      if (p) {
        p.files[this.#activeFile] = ta.value;
        this.#parsedManifest = ManifestManager.parse(ta.value);
      }
    }
  }

  #bindEmptyStateEvents() {
    const c = this.#container;
    if (!c) return;

    c.querySelector('#manifest-create-lib-btn')?.addEventListener('click', async () => {
      await this.#createManifestFile(false);
    });
    c.querySelector('#manifest-create-app-btn')?.addEventListener('click', async () => {
      await this.#createManifestFile(true);
    });
    c.querySelector('#manifest-empty-goto-gen-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'generator' });
    });
  }

  async #createManifestFile(isApp) {
    const p = projectService.current;
    if (!p) return;

    const targetPath = isApp ? 'app/src/main/AndroidManifest.xml' : 'library/src/main/AndroidManifest.xml';
    const newXml = ManifestManager.createDefaultManifest({
      isApp,
      packageName: p.packageName || 'com.example.android',
      label: p.name || 'AndroidModule'
    });

    p.files[targetPath] = newXml;
    await projectService.save();
    this.#activeFile = targetPath;
    this.#parsedManifest = ManifestManager.parse(newXml);
    toast.success('Manifest Created', `Created ${targetPath}`);
    this.#render([targetPath]);
  }

  #noProject() {
    return `
      <div class="page-container">
        <div class="card empty-state-card" style="padding:48px 24px;text-align:center;display:flex;flex-direction:column;align-items:center;gap:12px">
          <span class="material-symbols-outlined empty-state-icon" style="font-size:48px;color:var(--text-muted)">description</span>
          <div class="empty-state-text" style="font-size:16px;font-weight:600">No project open. Generate or import a project first.</div>
          <button type="button" class="btn btn-primary" id="manifest-goto-gen-btn" style="margin-top:12px">
            <span class="material-symbols-outlined">auto_fix_high</span> Go to Project Generator
          </button>
        </div>
      </div>
    `;
  }

  #esc(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
}

export default ManifestPage;
