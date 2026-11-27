/**
 * GeneratorModule — drives the project generation wizard.
 * Loads templates, validates form input, calls template.generate(), saves to project.
 */
import { eventBus } from '../../core/EventBus.js';
import { projectService } from '../storage/ProjectService.js';
import { consoleService } from '../storage/ConsoleService.js';
import { toast } from '../../ui/ToastService.js';
import { PureJavaLibraryTemplate } from './templates/PureJavaLibraryTemplate.js';
import { AndroidLibraryStandaloneTemplate } from './templates/AndroidLibraryStandaloneTemplate.js';
import { AndroidLibraryTemplate, resolveLibraryModuleName, sanitizeModuleName } from './templates/AndroidLibraryTemplate.js';
import { AndroidAppTemplate } from './templates/AndroidAppTemplate.js';
import { MultiModuleTemplate } from './templates/MultiModuleTemplate.js';
import { PreValidatorService } from '../build/PreValidatorService.js';

const TEMPLATES = [
  PureJavaLibraryTemplate,
  AndroidLibraryStandaloneTemplate,
  AndroidAppTemplate,
  AndroidLibraryTemplate,
  MultiModuleTemplate,
  {
    id: 'android-kotlin-library', name: 'Android Kotlin Library',
    description: 'Kotlin-first Android library with coroutines support',
    icon: 'android', color: 'accent2', language: 'kotlin', tags: ['android','kotlin','library'],
    generate: (meta) => AndroidLibraryTemplate.generate({ ...meta, language: 'kotlin' }),
  },
  {
    id: 'crash-monitor', name: 'Crash Monitor Library',
    description: 'Uncaught exception handler with logging and reporting hooks',
    icon: 'bug_report', color: 'error', language: 'java', tags: ['crash','monitor','android'],
    generate: (meta) => AndroidLibraryTemplate.generate({ ...meta, name: meta.name || 'CrashMonitor', description: 'Android crash monitoring library' }),
  },
  {
    id: 'floating-overlay', name: 'Floating Overlay Library',
    description: 'System-window floating view manager for Android',
    icon: 'picture_in_picture', color: 'warning', language: 'java', tags: ['overlay','floating','android'],
    generate: (meta) => AndroidLibraryTemplate.generate({ ...meta, name: meta.name || 'FloatingOverlay' }),
  },
  {
    id: 'webview-library', name: 'WebView Library',
    description: 'Enhanced WebView wrapper with JS bridge and lifecycle management',
    icon: 'language', color: 'success', language: 'java', tags: ['webview','android'],
    generate: (meta) => AndroidLibraryTemplate.generate({ ...meta, name: meta.name || 'WebViewLib' }),
  },
  {
    id: 'custom', name: 'Custom Template',
    description: 'Start with a minimal scaffold and build your own structure',
    icon: 'tune', color: 'muted', language: 'java', tags: ['custom'],
    generate: (meta) => AndroidLibraryTemplate.generate(meta),
  },
];

const LICENSES = ['Apache-2.0','MIT','GPL-3.0','LGPL-2.1','BSD-2-Clause','BSD-3-Clause','ISC','MPL-2.0','Unlicense'];

export class GeneratorModule {
  #container  = null;
  #formValues = {
    name: '', packageName: 'com.example.library', libraryModuleName: '', version: '1.0.0',
    minSdk: '21', targetSdk: '34', compileSdk: '34',
    language: 'java', license: 'Apache-2.0', repoName: '',
    company: '', author: '', description: '', template: 'android-java-library',
  };

  #resolveModuleNamePreview() {
    return resolveLibraryModuleName(this.#formValues);
  }

  init(container, options = {}) {
    this.#container = container;
    if (options && options.template) {
      this.#formValues.template = options.template;
      const tmpl = TEMPLATES.find(t => t.id === options.template);
      if (tmpl) {
        if (tmpl.language) this.#formValues.language = tmpl.language;
        if (!this.#formValues.name) {
          if (tmpl.id === 'pure-java-library') {
            this.#formValues.name = 'JavaLib';
            this.#formValues.repoName = 'java-lib';
          } else if (tmpl.id === 'android-library-standalone') {
            this.#formValues.name = 'AndroidLib';
            this.#formValues.repoName = 'android-lib';
          } else if (tmpl.id === 'android-app') {
            this.#formValues.name = 'AndroidApp';
            this.#formValues.repoName = 'android-app';
          } else if (tmpl.id === 'android-multi-module') {
            this.#formValues.name = 'MultiProject';
            this.#formValues.repoName = 'multi-project';
          }
        }
      }
    }
    if (options.projectName) this.#formValues.name = options.projectName;
    if (options.packageName) this.#formValues.packageName = options.packageName;
    if (options.compileSdk) this.#formValues.compileSdk = String(options.compileSdk);
    if (options.minSdk) this.#formValues.minSdk = String(options.minSdk);
    if (options.targetSdk) this.#formValues.targetSdk = String(options.targetSdk);
    this.#render();
  }

  #render() {
    this.#container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title">
            <span class="material-symbols-outlined">auto_fix_high</span>
            Project Generator
          </h1>
          <p class="page-subtitle">Generate a fully structured JitPack-compatible Android library project</p>
        </div>

        <div class="generator-layout">
          <form class="generator-form" id="gen-form" novalidate>

            <!-- Template Selection -->
            <div class="card">
              <div class="card-header">
                <span class="material-symbols-outlined">grid_view</span>
                <span>Select Template</span>
              </div>
              <div class="card-body">
                <div class="template-gallery" id="gen-template-gallery">
                  ${TEMPLATES.map(t => `
                    <div class="template-card${this.#formValues.template === t.id ? ' selected' : ''}"
                         data-template="${t.id}" role="button" tabindex="0">
                      <span class="material-symbols-outlined template-card-icon" style="color:var(--${t.color === 'accent' ? 'accent' : t.color === 'accent2' ? 'accent2' : t.color === 'error' ? 'error' : t.color === 'warning' ? 'warning' : t.color === 'success' ? 'success' : 'text-muted'});">${t.icon}</span>
                      <div class="template-card-name">${t.name}</div>
                      <div class="template-card-desc">${t.description}</div>
                      <div style="display:flex;gap:4px;flex-wrap:wrap;margin-top:4px">
                        ${t.tags.map(tag => `<span class="badge">${tag}</span>`).join('')}
                      </div>
                    </div>`).join('')}
                </div>
              </div>
            </div>

            <!-- Project Metadata -->
            <div class="card">
              <div class="card-header">
                <span class="material-symbols-outlined">info</span>
                <span>Project Details</span>
              </div>
              <div class="card-body">
                <div class="form-row">
                  <div class="form-group">
                    <label class="form-label required">Project Name</label>
                    <input class="input" type="text" name="name" placeholder="MyAwesomeLibrary"
                           value="${this.#formValues.name}" required />
                    <div class="form-hint">Used as class name — PascalCase recommended</div>
                  </div>
                  <div class="form-group">
                    <label class="form-label required">Package Name</label>
                    <input class="input" type="text" name="packageName" placeholder="com.example.library"
                           value="${this.#formValues.packageName}" required />
                  </div>
                </div>
                <div class="form-row">
                  <div class="form-group">
                    <label class="form-label">Library Module Name</label>
                    <input class="input" type="text" name="libraryModuleName"
                           placeholder="Auto (e.g. ${this.#resolveModuleNamePreview()})"
                           value="${this.#formValues.libraryModuleName || ''}" />
                    <div class="form-hint" id="gen-module-name-hint">Dynamic module (blank to auto-derive: <code>${this.#resolveModuleNamePreview()}</code>)</div>
                  </div>
                  <div class="form-group">
                    <label class="form-label">Version</label>
                    <input class="input" type="text" name="version" placeholder="1.0.0"
                           value="${this.#formValues.version}" />
                  </div>
                </div>
                <div class="form-row">
                  <div class="form-group">
                    <label class="form-label">Repository Name</label>
                    <input class="input" type="text" name="repoName" placeholder="my-android-library"
                           value="${this.#formValues.repoName}" />
                    <div class="form-hint">GitHub repository slug</div>
                  </div>
                  <div class="form-group">
                    <label class="form-label">Author</label>
                    <input class="input" type="text" name="author" placeholder="YourGitHubUsername"
                           value="${this.#formValues.author}" />
                  </div>
                </div>
                <div class="form-row">
                  <div class="form-group">
                    <label class="form-label">Company / Organization</label>
                    <input class="input" type="text" name="company" placeholder="Acme Corp"
                           value="${this.#formValues.company}" />
                  </div>
                </div>
                <div class="form-group">
                  <label class="form-label">Description</label>
                  <textarea class="input textarea" name="description" rows="2"
                            placeholder="A brief description of what your library does">${this.#formValues.description}</textarea>
                </div>
              </div>
            </div>

            <!-- SDK & Build -->
            <div class="card">
              <div class="card-header">
                <span class="material-symbols-outlined">build</span>
                <span>SDK & Build Configuration</span>
              </div>
              <div class="card-body">
                <div class="form-row form-row-3">
                  <div class="form-group">
                    <label class="form-label">Min SDK</label>
                    <input class="input" type="number" name="minSdk" placeholder="21"
                           value="${this.#formValues.minSdk}" min="16" max="34" />
                  </div>
                  <div class="form-group">
                    <label class="form-label">Target SDK</label>
                    <input class="input" type="number" name="targetSdk" placeholder="34"
                           value="${this.#formValues.targetSdk}" min="21" max="34" />
                  </div>
                  <div class="form-group">
                    <label class="form-label">Compile SDK</label>
                    <input class="input" type="number" name="compileSdk" placeholder="34"
                           value="${this.#formValues.compileSdk}" min="21" max="34" />
                  </div>
                </div>
                <div class="form-row">
                  <div class="form-group">
                    <label class="form-label">Language</label>
                    <div class="select-wrapper">
                      <select class="input select" name="language">
                        <option value="java"   ${this.#formValues.language==='java'   ?'selected':''}>Java</option>
                        <option value="kotlin" ${this.#formValues.language==='kotlin' ?'selected':''}>Kotlin</option>
                      </select>
                    </div>
                  </div>
                  <div class="form-group">
                    <label class="form-label">License</label>
                    <div class="select-wrapper">
                      <select class="input select" name="license">
                        ${LICENSES.map(l => `<option value="${l}" ${this.#formValues.license===l?'selected':''}>${l}</option>`).join('')}
                      </select>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Actions -->
            <div class="generator-actions">
              <button type="submit" class="btn btn-primary btn-lg" id="gen-btn">
                <span class="material-symbols-outlined">auto_fix_high</span>
                Generate Project
              </button>
              <button type="button" class="btn btn-secondary" id="gen-reset-btn">
                <span class="material-symbols-outlined">refresh</span>
                Reset
              </button>
            </div>

            <!-- Progress -->
            <div id="gen-progress" style="display:none">
              <div class="progress-bar"><div class="progress-bar-fill" id="gen-progress-fill" style="width:0%"></div></div>
              <div style="font-size:var(--text-xs);color:var(--text-muted);margin-top:4px" id="gen-progress-label">Starting…</div>
            </div>
          </form>

          <!-- Preview Sidebar -->
          <div class="generator-preview">
            <div class="generator-preview-title">Generated Files Preview</div>
            <div class="generator-file-list" id="gen-preview-list">
              <div class="generator-file-entry" style="color:var(--text-disabled);padding:var(--sp-4);text-align:center">
                Fill in form details to preview
              </div>
            </div>
            <div id="gen-selected-template" style="margin-top:var(--sp-3)"></div>
          </div>
        </div>
      </div>`;

    this.#bindFormEvents();
  }

  #bindFormEvents() {
    const form = this.#container.querySelector('#gen-form');

    // Template selection
    this.#container.querySelector('#gen-template-gallery')?.addEventListener('click', e => {
      const card = e.target.closest('.template-card');
      if (!card) return;
      this.#container.querySelectorAll('.template-card').forEach(c => c.classList.remove('selected'));
      card.classList.add('selected');
      this.#formValues.template = card.dataset.template;
      this.#updatePreview();
    });

    // Live input sync + preview update
    form?.querySelectorAll('input, select, textarea').forEach(el => {
      el.addEventListener('input', () => {
        this.#formValues[el.name] = el.value;
        this.#updatePreview();
      });
    });

    form?.addEventListener('submit', async e => {
      e.preventDefault();
      await this.#generate();
    });

    this.#container.querySelector('#gen-btn')?.addEventListener('click', async e => {
      e.preventDefault();
      await this.#generate();
    });

    this.#container.querySelector('#gen-reset-btn')?.addEventListener('click', () => {
      this.#resetForm();
    });

    this.#updatePreview();
  }

  #updatePreview() {
    const meta     = this.#getFormData();
    const template = TEMPLATES.find(t => t.id === meta.template) || TEMPLATES[0];
    const list     = this.#container.querySelector('#gen-preview-list');
    if (!list) return;

    if (!meta.name || !meta.packageName) {
      list.innerHTML = `<div class="generator-file-entry" style="color:var(--text-disabled);padding:var(--sp-4);text-align:center">Enter project name and package to preview</div>`;
      return;
    }

    const hintEl = this.#container.querySelector('#gen-module-name-hint');
    if (hintEl) {
      hintEl.innerHTML = `Dynamic module (blank to auto-derive: <code>${this.#resolveModuleNamePreview()}</code>)`;
    }
    const inputEl = this.#container.querySelector('input[name="libraryModuleName"]');
    if (inputEl) {
      inputEl.placeholder = `Auto (e.g. ${this.#resolveModuleNamePreview()})`;
    }

    const activeLibModule = resolveLibraryModuleName(meta);

    // Preview structure (flat list of key files)
    const structure = [
      { path: 'Root Files', type: 'folder' },
      { path: 'settings.gradle' }, { path: 'build.gradle' },
      { path: 'gradle.properties' }, { path: 'jitpack.yml' },
      { path: '.gitignore' }, { path: 'gradlew' }, { path: 'README.md' },
      { path: 'LICENSE' }, { path: 'CHANGELOG.md' },
      { path: `${activeLibModule}/`, type: 'folder' },
      { path: 'build.gradle' }, { path: 'AndroidManifest.xml' },
      { path: `${meta.name?.replace(/[^a-zA-Z0-9]/g,'') || 'Library'}.java` },
      { path: `${meta.name?.replace(/[^a-zA-Z0-9]/g,'') || 'Library'}Config.java` },
      { path: `${meta.name?.replace(/[^a-zA-Z0-9]/g,'') || 'Library'}Logger.java` },
      { path: `${meta.name?.replace(/[^a-zA-Z0-9]/g,'') || 'Library'}Constants.java` },
      { path: `${meta.name?.replace(/[^a-zA-Z0-9]/g,'') || 'Library'}Exception.java` },
      { path: 'interfaces/ILibraryListener.java' },
      { path: 'models/LibraryResult.java' },
      { path: 'utils/LibraryUtils.java' },
      { path: 'app/', type: 'folder' },
      { path: 'MainActivity.java' }, { path: 'AndroidManifest.xml' },
      { path: '.github/workflows/', type: 'folder' },
      { path: 'ci.yml' }, { path: 'release.yml' },
      { path: 'docs/', type: 'folder' },
      { path: 'API.md' }, { path: 'SETUP.md' },
      { path: 'scripts/', type: 'folder' },
      { path: 'build.sh' }, { path: 'publish.sh' },
    ];

    list.innerHTML = structure.map(f => {
      const icon = f.type === 'folder' ? 'folder' : 'insert_drive_file';
      return `<div class="generator-file-entry ${f.type === 'folder' ? 'folder' : 'file'}">
        <span class="material-symbols-outlined" style="font-size:12px">${icon}</span>
        ${f.path}
      </div>`;
    }).join('');

    // Template info
    const tmplEl = this.#container.querySelector('#gen-selected-template');
    if (tmplEl) {
      tmplEl.innerHTML = `
        <div class="card" style="padding:var(--sp-3)">
          <div style="display:flex;align-items:center;gap:var(--sp-2);margin-bottom:var(--sp-2)">
            <span class="material-symbols-outlined" style="color:var(--accent);font-size:18px">${template.icon}</span>
            <span style="font-size:var(--text-sm);font-weight:600;color:var(--text-primary)">${template.name}</span>
          </div>
          <div style="font-size:var(--text-xs);color:var(--text-muted)">${template.description}</div>
        </div>`;
    }
  }

  #getFormData() {
    const data = { ...this.#formValues };
    if (data.name && typeof data.name === 'string') {
      data.name = data.name.trim();
    }
    if (data.packageName && typeof data.packageName === 'string') {
      data.packageName = data.packageName.trim();
    }
    if (data.libraryModuleName && typeof data.libraryModuleName === 'string') {
      data.libraryModuleName = data.libraryModuleName.trim();
    }
    return data;
  }

  async #generate() {
    const meta     = this.#getFormData();
    const errors   = this.#validate(meta);
    if (errors.length) {
      toast.error('Validation failed', errors[0]);
      return;
    }

    const btn      = this.#container.querySelector('#gen-btn');
    const progress = this.#container.querySelector('#gen-progress');
    const fill     = this.#container.querySelector('#gen-progress-fill');
    const label    = this.#container.querySelector('#gen-progress-label');

    if (btn)      btn.disabled = true;
    if (progress) progress.style.display = '';

    const setProgress = (pct, msg) => {
      if (fill)  fill.style.width = `${pct}%`;
      if (label) label.textContent = msg;
    };

    try {
      eventBus.emit('action:generating');
      setProgress(10, 'Loading template…');
      await this.#delay(100);

      const template = TEMPLATES.find(t => t.id === meta.template) || TEMPLATES[0];
      consoleService.info(`Generator: using template "${template.name}"`);

      setProgress(30, 'Generating project structure…');
      await this.#delay(150);
      const files = template.generate(meta);

      setProgress(60, 'Creating project…');
      await this.#delay(100);

      // Auto-fill repoName from name
      if (!meta.repoName && meta.name) {
        meta.repoName = meta.name.replace(/([A-Z])/g, '-$1').toLowerCase().replace(/^-/, '');
      }

      // Set explicit projectType from template
      meta.projectType = template.projectType || (meta.template === 'pure-java-library' ? 'java-library' : (meta.template === 'android-app' ? 'app' : 'library'));

      const project = await projectService.create(meta);

      setProgress(80, `Writing ${Object.keys(files).length} files…`);
      await this.#delay(100);

      // ── Gradle Wrapper validation (fail fast before saving) ───────────────
      const REQUIRED_WRAPPER = [
        'gradlew',
        'gradlew.bat',
        'gradle/wrapper/gradle-wrapper.jar',
        'gradle/wrapper/gradle-wrapper.properties',
      ];
      const missingWrapper = REQUIRED_WRAPPER.filter(f => !(f in files));
      if (missingWrapper.length > 0) {
        throw new Error(`Gradle Wrapper generation failed — missing: ${missingWrapper.join(', ')}`);
      }

      // ── Repository structure validation ───────────────────────────────────
      const isJavaLib = meta.template === 'pure-java-library' || meta.projectType === 'java-library';
      const isStandaloneLib = meta.template === 'android-library-standalone';
      const isApp = meta.template === 'android-app' || meta.projectType === 'app';

      let REQUIRED_STRUCTURE;
      if (isJavaLib) {
        REQUIRED_STRUCTURE = [
          'settings.gradle', 'build.gradle', 'gradle.properties', 'jitpack.yml',
          '.github/workflows/ci.yml', '.github/workflows/release.yml',
          'README.md', 'LICENSE', '.gitignore',
        ];
      } else if (isStandaloneLib) {
        REQUIRED_STRUCTURE = [
          'settings.gradle', 'build.gradle', 'gradle.properties', 'jitpack.yml',
          'library/build.gradle', 'library/src/main/AndroidManifest.xml',
          '.github/workflows/ci.yml', '.github/workflows/release.yml',
          'README.md', 'LICENSE', '.gitignore',
        ];
      } else if (isApp) {
        REQUIRED_STRUCTURE = [
          'settings.gradle', 'build.gradle', 'gradle.properties',
          'app/build.gradle', 'app/src/main/AndroidManifest.xml',
          '.github/workflows/ci.yml', '.github/workflows/release.yml',
          'README.md', '.gitignore',
        ];
      } else {
        const libMod = resolveLibraryModuleName(meta);
        REQUIRED_STRUCTURE = [
          'settings.gradle', 'build.gradle', 'gradle.properties', 'jitpack.yml',
          `${libMod}/build.gradle`,
          `${libMod}/src/main/AndroidManifest.xml`,
          'app/build.gradle', 'app/src/main/AndroidManifest.xml',
          '.github/workflows/ci.yml', '.github/workflows/release.yml',
          'README.md', 'LICENSE', '.gitignore',
        ];
      }

      const missingStructure = REQUIRED_STRUCTURE.filter(f => !(f in files));
      if (missingStructure.length > 0) {
        consoleService.error(`Repository structure incomplete — missing: ${missingStructure.join(', ')}`);
        toast.error('Structure Warning', `${missingStructure.length} expected file(s) missing — see console`);
      } else {
        consoleService.info('Generator: repository structure validation passed ✓');
      }

      consoleService.info('Generator: Gradle Wrapper validation passed ✓ (gradlew, gradlew.bat, gradle-wrapper.jar, gradle-wrapper.properties)');

      // Batch save all generated files in a single transaction
      await projectService.setFiles(files);
      for (const path of Object.keys(files)) {
        consoleService.info(`  created: ${path}`);
      }

      setProgress(100, 'Done!');
      await this.#delay(300);

      consoleService.success(`Project "${meta.name}" generated — ${Object.keys(files).length} files`);
      toast.success('Project Generated!', `${Object.keys(files).length} files created`);
      eventBus.emit('action:generated');
      eventBus.emit('router:navigate', { pageId: 'analyzer' });

    } catch (err) {
      consoleService.error(`Generator error: ${err.message}`);
      toast.error('Generation failed', err.message);
    } finally {
      if (btn) btn.disabled = false;
      setTimeout(() => {
        if (progress) progress.style.display = 'none';
        if (fill) fill.style.width = '0%';
      }, 1500);
    }
  }

  #validate(meta) {
    const result = PreValidatorService.validateProjectMetadata(meta);
    return result.errors || [];
  }

  #resetForm() {
    this.#formValues = {
      name: '', packageName: 'com.example.library', libraryModuleName: '', version: '1.0.0',
      minSdk: '21', targetSdk: '34', compileSdk: '34',
      language: 'java', license: 'Apache-2.0', repoName: '',
      company: '', author: '', description: '', template: 'android-java-library',
    };
    this.#render();
  }

  #delay(ms) { return new Promise(r => setTimeout(r, ms)); }
}
