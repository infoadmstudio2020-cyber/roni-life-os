/**
 * ProjectPage — view and edit current project metadata.
 */
import { projectService } from '../modules/storage/ProjectService.js';
import { toast } from '../ui/ToastService.js';
import { consoleService } from '../modules/storage/ConsoleService.js';
import { eventBus } from '../core/EventBus.js';

export class ProjectPage {
  async init(container) {
    const p = projectService.current;
    if (!p) {
      container.innerHTML = `
        <div class="page-container">
          <div class="page-header">
            <h1 class="page-title"><span class="material-symbols-outlined">folder_special</span>Project</h1>
          </div>
          <div class="card" style="padding:var(--sp-8);text-align:center">
            <span class="material-symbols-outlined" style="font-size:48px;opacity:.3;font-variation-settings:'FILL' 0,'wght' 200">folder_off</span>
            <div style="margin-top:var(--sp-3);color:var(--text-muted)">No project is currently open.<br>Create or open a project from the Dashboard.</div>
          </div>

          <!-- Project Actions -->
          <div class="card" style="margin-bottom:var(--sp-4)" id="sect-project-actions">
            <div class="card-header"><span class="material-symbols-outlined">swap_vert</span>Project Actions</div>
            <div class="card-body" style="padding:0">
              <div class="settings-row">
                <div class="settings-row-label">
                  <div class="settings-row-title">GitHub Repository Import</div>
                  <div class="settings-row-desc">Import Android project directly from a GitHub repository URL</div>
                </div>
                <div class="settings-row-control">
                  <button type="button" class="btn btn-secondary btn-sm" id="page-gh-import-btn">
                    <span class="material-symbols-outlined">hub</span>Import from GitHub
                  </button>
                </div>
              </div>
              <div class="settings-row">
                <div class="settings-row-label">
                  <div class="settings-row-title">Import Project</div>
                  <div class="settings-row-desc">Import an existing project from a ZIP archive</div>
                </div>
                <div class="settings-row-control">
                  <button type="button" class="btn btn-primary btn-sm" id="page-import-btn">
                    <span class="material-symbols-outlined">upload</span>Import
                  </button>
                </div>
              </div>
              <div class="settings-row">
                <div class="settings-row-label">
                  <div class="settings-row-title">Export Project</div>
                  <div class="settings-row-desc">Download the active project as a ZIP archive</div>
                </div>
                <div class="settings-row-control">
                  <button type="button" class="btn btn-secondary btn-sm" id="page-export-btn">
                    <span class="material-symbols-outlined">download</span>Export
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>`;

      // Reuse the existing toolbar import/export flow (ZipService)
      container.querySelector('#page-gh-import-btn')?.addEventListener('click', () =>
        eventBus.emit('action:importGitHub'));
      container.querySelector('#page-import-btn')?.addEventListener('click', () =>
        eventBus.emit('action:importProject'));
      container.querySelector('#page-export-btn')?.addEventListener('click', () =>
        eventBus.emit('action:exportZip'));
      return;
    }

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title"><span class="material-symbols-outlined">folder_special</span>Project Settings</h1>
          <p class="page-subtitle">Edit metadata for <strong>${p.name}</strong></p>
        </div>

        <form id="proj-form" novalidate>
          <div class="card" style="margin-bottom:var(--sp-4)">
            <div class="card-header"><span class="material-symbols-outlined">info</span>General</div>
            <div class="card-body">
              <div class="form-row">
                <div class="form-group">
                  <label class="form-label required">Project Name</label>
                  <input class="input" name="name" value="${p.name}" required />
                </div>
                <div class="form-group">
                  <label class="form-label required">Package Name</label>
                  <input class="input" name="packageName" value="${p.packageName}" required />
                </div>
              </div>
              <div class="form-row">
                <div class="form-group">
                  <label class="form-label">Version</label>
                  <input class="input" name="version" value="${p.version}" />
                </div>
                <div class="form-group">
                  <label class="form-label">Repository Name</label>
                  <input class="input" name="repoName" value="${p.repoName || ''}" />
                </div>
              </div>
              <div class="form-row">
                <div class="form-group">
                  <label class="form-label">Author</label>
                  <input class="input" name="author" value="${p.author || ''}" />
                </div>
                <div class="form-group">
                  <label class="form-label">Company</label>
                  <input class="input" name="company" value="${p.company || ''}" />
                </div>
              </div>
              <div class="form-group">
                <label class="form-label">Description</label>
                <textarea class="input textarea" name="description" rows="2">${p.description || ''}</textarea>
              </div>
            </div>
          </div>

          <div class="card" style="margin-bottom:var(--sp-4)">
            <div class="card-header"><span class="material-symbols-outlined">build</span>SDK Configuration</div>
            <div class="card-body">
              <div class="form-row form-row-3">
                <div class="form-group">
                  <label class="form-label">Min SDK</label>
                  <input class="input" type="number" name="minSdk" value="${p.minSdk || 21}" />
                </div>
                <div class="form-group">
                  <label class="form-label">Target SDK</label>
                  <input class="input" type="number" name="targetSdk" value="${p.targetSdk || 34}" />
                </div>
                <div class="form-group">
                  <label class="form-label">Compile SDK</label>
                  <input class="input" type="number" name="compileSdk" value="${p.compileSdk || 34}" />
                </div>
              </div>
            </div>
          </div>

          <div style="display:flex;gap:var(--sp-3);flex-wrap:wrap">
            <button type="submit" class="btn btn-primary">
              <span class="material-symbols-outlined">save</span>Save Changes
            </button>
            <button type="button" class="btn btn-accent" id="page-fix-missing-btn" title="Detect and non-destructively generate missing standard files (.gitignore, LICENSE, gradlew, wrapper, icons, etc.)">
              <span class="material-symbols-outlined">auto_fix_high</span>Generate Missing Files
            </button>
            <button type="button" class="btn btn-secondary" id="page-autodetect-btn" title="Auto-detect package name and metadata from project source code">
              <span class="material-symbols-outlined">search_check</span>Auto-Detect Metadata
            </button>
            <button type="button" class="btn btn-secondary" id="page-export-btn" title="Export the active project as a ZIP archive">
              <span class="material-symbols-outlined">download</span>Export Project
            </button>
            <button type="button" class="btn btn-secondary" id="page-gh-import-btn" title="Import repository from GitHub">
              <span class="material-symbols-outlined">hub</span>Import from GitHub
            </button>
            <button type="button" class="btn btn-secondary" id="page-import-btn" title="Import a project from a ZIP archive">
              <span class="material-symbols-outlined">upload</span>Import Project
            </button>
          </div>
        </form>
      </div>`;

    // Reuse the existing toolbar import/export flow (ZipService)
    container.querySelector('#page-gh-import-btn')?.addEventListener('click', () =>
      eventBus.emit('action:importGitHub'));
    container.querySelector('#page-import-btn')?.addEventListener('click', () =>
      eventBus.emit('action:importProject'));
    container.querySelector('#page-export-btn')?.addEventListener('click', () =>
      eventBus.emit('action:exportZip'));

    container.querySelector('#page-fix-missing-btn')?.addEventListener('click', async () => {
      const btn = container.querySelector('#page-fix-missing-btn');
      btn.disabled = true;
      btn.innerHTML = `<span class="material-symbols-outlined rotating">sync</span>Generating…`;

      const { MissingFilesService } = await import('../modules/build/MissingFilesService.js');
      await MissingFilesService.generateMissingFiles(p);

      btn.disabled = false;
      btn.innerHTML = `<span class="material-symbols-outlined">auto_fix_high</span>Generate Missing Files`;
    });

    container.querySelector('#page-autodetect-btn')?.addEventListener('click', async () => {
      const { ProjectAnalyzer } = await import('../modules/analyzer/ProjectAnalyzer.js');
      const analysis = ProjectAnalyzer.analyze(p.files || {});
      const form = container.querySelector('#proj-form');
      if (form && analysis) {
        if (analysis.detectedPackageName) {
          const input = form.querySelector('[name="packageName"]');
          if (input) input.value = analysis.detectedPackageName;
        }
        if (analysis.rootProjectName) {
          const input = form.querySelector('[name="name"]');
          if (input) input.value = analysis.rootProjectName;
        }
        if (analysis.primaryModule?.compileSdk) {
          const input = form.querySelector('[name="compileSdk"]');
          if (input) input.value = analysis.primaryModule.compileSdk;
        }
        if (analysis.primaryModule?.minSdk) {
          const input = form.querySelector('[name="minSdk"]');
          if (input) input.value = analysis.primaryModule.minSdk;
        }
        if (analysis.primaryModule?.targetSdk) {
          const input = form.querySelector('[name="targetSdk"]');
          if (input) input.value = analysis.primaryModule.targetSdk;
        }
        toast.success('Metadata Auto-Detected', `Found package: ${analysis.detectedPackageName || 'N/A'}`);
      }
    });

    container.querySelector('#proj-form')?.addEventListener('submit', async e => {
      e.preventDefault();
      const fd = new FormData(e.target);
      for (const [k, v] of fd.entries()) {
        if (k in p) p[k] = v;
      }
      await projectService.save(p);
      consoleService.success('Project metadata saved');
      toast.success('Saved', 'Project metadata updated');
    });
  }
}
