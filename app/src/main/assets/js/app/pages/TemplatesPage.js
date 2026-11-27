/**
 * TemplatesPage.js — Reusable Project Templates, Configurations & AI-Ready JSON Generator.
 *
 * Supports Android Library, Android App, Multi-Module SDK, and Java Library templates.
 * Generates structured AI-Ready JSON with one-click Copy, Download, and Generator injection.
 */

import { eventBus } from '../core/EventBus.js';
import { toast } from '../ui/ToastService.js';
import { TEMPLATES_CATALOG, AIProjectSpecGenerator } from '../modules/generator/AIProjectSpecGenerator.js';

export class TemplatesPage {
  #container = null;
  #activeCategory = 'all';
  #searchTerm = '';
  #selectedTemplateId = 'android-java-library';
  #activeDetailTab = 'config'; // 'config' | 'files' | 'json'
  #currentConfig = {};

  async init(container, params = {}) {
    this.#container = container;
    this.#container.classList.add('page-templates');

    // If specific template passed via params
    if (params?.templateId && TEMPLATES_CATALOG.some(t => t.id === params.templateId)) {
      this.#selectedTemplateId = params.templateId;
    }

    this.#initTemplateConfig();
    this.#render();
  }

  destroy() {
    const existingModal = document.getElementById('tmpl-schema-preview-modal');
    if (existingModal) existingModal.remove();

    if (this.#container) {
      this.#container.classList.remove('page-templates');
      this.#container.innerHTML = '';
      this.#container = null;
    }
  }

  #initTemplateConfig() {
    const tmpl = TEMPLATES_CATALOG.find(t => t.id === this.#selectedTemplateId) || TEMPLATES_CATALOG[0];
    this.#currentConfig = { ...tmpl.defaultConfig };
  }

  #render() {
    if (!this.#container) return;

    const filteredTemplates = TEMPLATES_CATALOG.filter(t => {
      const matchCat = this.#activeCategory === 'all' || t.category === this.#activeCategory;
      const term = this.#searchTerm.toLowerCase();
      const matchSearch = !term ||
        t.name.toLowerCase().includes(term) ||
        t.description.toLowerCase().includes(term) ||
        t.id.toLowerCase().includes(term) ||
        t.language.toLowerCase().includes(term);
      return matchCat && matchSearch;
    });

    const activeTemplate = TEMPLATES_CATALOG.find(t => t.id === this.#selectedTemplateId) || TEMPLATES_CATALOG[0];
    const generatedSpec = AIProjectSpecGenerator.generateSpec(this.#selectedTemplateId, this.#currentConfig);
    const generatedJsonString = JSON.stringify(generatedSpec, null, 2);

    this.#container.innerHTML = `
      <div class="templates-page">
        <!-- Header -->
        <div class="templates-header">
          <div class="templates-title-row">
            <div>
              <h1 class="templates-title">
                <span class="material-symbols-outlined" style="color:var(--accent);font-size:32px">auto_awesome_motion</span>
                প্রজেক্ট টেমপ্লেট ও AI-Ready JSON গ্যালারি
              </h1>
              <p class="templates-subtitle">
                Android Library, Application, Multi-Module SDK এবং JVM Library-এর পুনঃব্যবহারযোগ্য আর্কিটেকচার ও AI-Ready JSON জেনারেশন
              </p>
            </div>
            <div style="display:flex;gap:var(--sp-2)">
              <button class="btn btn-secondary btn-sm" id="tmpl-refresh-btn" title="রিলোড করুন">
                <span class="material-symbols-outlined">refresh</span> রিফ্রেশ
              </button>
            </div>
          </div>
        </div>

        <!-- Filter & Search Toolbar -->
        <div class="templates-filters-bar">
          <div class="templates-category-pills">
            <button class="category-pill ${this.#activeCategory === 'all' ? 'active' : ''}" data-cat="all">সবগুলো (${TEMPLATES_CATALOG.length})</button>
            <button class="category-pill ${this.#activeCategory === 'library' ? 'active' : ''}" data-cat="library">Android Library</button>
            <button class="category-pill ${this.#activeCategory === 'app' ? 'active' : ''}" data-cat="app">Android App</button>
            <button class="category-pill ${this.#activeCategory === 'sdk' ? 'active' : ''}" data-cat="sdk">Multi-Module SDK</button>
            <button class="category-pill ${this.#activeCategory === 'jvm' ? 'active' : ''}" data-cat="jvm">Java Library</button>
          </div>
          <div class="templates-search-box">
            <span class="material-symbols-outlined search-icon">search</span>
            <input type="text" id="tmpl-search-input" placeholder="টেমপ্লেটের নাম বা আর্কিটেকচার খুঁজুন…" value="${this.#searchTerm}" autocomplete="off" />
          </div>
        </div>

        <!-- Templates Grid -->
        <div class="templates-grid">
          ${filteredTemplates.map(t => this.#renderCard(t, t.id === this.#selectedTemplateId)).join('')}
        </div>

        <!-- Workspace & Detail Panel -->
        <div class="tmpl-detail-panel" id="tmpl-workspace-panel">
          <div class="tmpl-detail-header">
            <div class="tmpl-detail-title-group">
              <span class="material-symbols-outlined" style="color:${activeTemplate.color};font-size:28px">${activeTemplate.icon}</span>
              <div>
                <h2 style="font-size:var(--text-lg);font-weight:700;color:var(--text-primary);margin:0">
                  ${activeTemplate.name}
                </h2>
                <div style="font-size:var(--text-xs);color:var(--text-muted);margin-top:2px">
                  ${activeTemplate.description}
                </div>
              </div>
            </div>
            <div class="tmpl-detail-actions">
              <button class="btn btn-primary btn-sm" id="tmpl-export-schema-btn" title="নির্বাচিত টেমপ্লেটের রিকোয়ার্ড ইনফরমেশন স্কিমা এক্সপোর্ট ও প্রিভিউ">
                <span class="material-symbols-outlined">schema</span> Export Required Information Schema
              </button>
              <button class="btn btn-secondary btn-sm" id="tmpl-copy-json-btn">
                <span class="material-symbols-outlined">content_copy</span> AI JSON কপি
              </button>
              <button class="btn btn-secondary btn-sm" id="tmpl-download-json-btn">
                <span class="material-symbols-outlined">download</span> JSON ডাউনলোড
              </button>
              <button class="btn btn-secondary btn-sm" id="tmpl-use-generator-btn" title="জেনারেটর পেজে লোড করুন">
                <span class="material-symbols-outlined">auto_fix_high</span> জেনারেটরে লোড
              </button>
            </div>
          </div>

          <!-- Navigation Tabs -->
          <div class="tmpl-nav-tabs">
            <button class="tmpl-nav-tab ${this.#activeDetailTab === 'config' ? 'active' : ''}" data-tab="config">
              <span class="material-symbols-outlined">tune</span> রিকোয়ারমেন্টস ও কনফিগারেশন
            </button>
            <button class="tmpl-nav-tab ${this.#activeDetailTab === 'files' ? 'active' : ''}" data-tab="files">
              <span class="material-symbols-outlined">account_tree</span> ফাইল স্ট্রাকচার ও ডিপেন্ডেন্সি (${activeTemplate.fileStructure.length})
            </button>
            <button class="tmpl-nav-tab ${this.#activeDetailTab === 'json' ? 'active' : ''}" data-tab="json">
              <span class="material-symbols-outlined">data_object</span> AI-Ready JSON প্রিভিউ
            </button>
          </div>

          <!-- Tab 1: Config Form -->
          <div id="tmpl-tab-config" style="display:${this.#activeDetailTab === 'config' ? 'block' : 'none'}">
            <div class="ai-spec-banner">
              <span class="material-symbols-outlined" style="color:var(--accent);font-size:22px">info</span>
              <div>
                <strong>AI-Ready কনফিগারেশন গাইড:</strong> নিচের ফিল্ডগুলো কাস্টমাইজ করলে ডানপাশের AI JSON সরাসরি আপডেট হবে। যেকোনো ফিল্ড খালি রাখলে বা পরিবর্তন না করলে AI সেটি স্পষ্ট ডিফল্ট ও কাস্টমাইজযোগ্য হিসেবে বিবেচনা করবে।
              </div>
            </div>

            <div class="tmpl-form-grid">
              <div class="tmpl-form-field">
                <label for="cfg-projectName"><span class="material-symbols-outlined" style="font-size:16px">label</span> প্রজেক্টের নাম (Project Name)</label>
                <input class="input" id="cfg-projectName" value="${this.#currentConfig.projectName || ''}" placeholder="যেমন: AndroidAwesomeLib" />
                <span class="helper-text">Android Studio রুট প্রজেক্ট ডিরেক্টরি নাম</span>
              </div>
              <div class="tmpl-form-field">
                <label for="cfg-packageName"><span class="material-symbols-outlined" style="font-size:16px">badge</span> প্যাকেজ নেম / Application ID</label>
                <input class="input" id="cfg-packageName" value="${this.#currentConfig.packageName || ''}" placeholder="যেমন: com.example.awesomelib" />
                <span class="helper-text">Java/Kotlin রুট প্যাকেজ ও Android Namespace</span>
              </div>
              <div class="tmpl-form-field">
                <label for="cfg-groupId"><span class="material-symbols-outlined" style="font-size:16px">hub</span> Maven Group ID (JitPack)</label>
                <input class="input" id="cfg-groupId" value="${this.#currentConfig.groupId || ''}" placeholder="যেমন: com.github.yourusername" />
                <span class="helper-text">JitPack ও Maven পাবলিশিং গ্রুপ আইডেন্টিফায়ার</span>
              </div>
              <div class="tmpl-form-field">
                <label for="cfg-artifactId"><span class="material-symbols-outlined" style="font-size:16px">inventory_2</span> Artifact ID</label>
                <input class="input" id="cfg-artifactId" value="${this.#currentConfig.artifactId || ''}" placeholder="যেমন: awesome-lib" />
                <span class="helper-text">লাইব্রেরি আর্টিফ্যাক্ট নেম</span>
              </div>
              <div class="tmpl-form-field">
                <label for="cfg-version"><span class="material-symbols-outlined" style="font-size:16px">history_toggle_off</span> প্রজেক্ট ভার্সন (Version)</label>
                <input class="input" id="cfg-version" value="${this.#currentConfig.version || '1.0.0'}" placeholder="যেমন: 1.0.0" />
                <span class="helper-text">Semantic Versioning (SemVer)</span>
              </div>
              <div class="tmpl-form-field">
                <label for="cfg-compileSdk"><span class="material-symbols-outlined" style="font-size:16px">layers</span> Compile SDK</label>
                <input class="input" type="number" id="cfg-compileSdk" value="${this.#currentConfig.compileSdk || 35}" />
                <span class="helper-text">টার্গেট কম্পাইল SDK (e.g. 34, 35, 36)</span>
              </div>
              <div class="tmpl-form-field">
                <label for="cfg-minSdk"><span class="material-symbols-outlined" style="font-size:16px">vertical_align_bottom</span> Min SDK</label>
                <input class="input" type="number" id="cfg-minSdk" value="${this.#currentConfig.minSdk || 21}" />
                <span class="helper-text">সর্বনিম্ন সমর্থিত Android API লেভেল</span>
              </div>
              <div class="tmpl-form-field">
                <label for="cfg-targetSdk"><span class="material-symbols-outlined" style="font-size:16px">vertical_align_top</span> Target SDK</label>
                <input class="input" type="number" id="cfg-targetSdk" value="${this.#currentConfig.targetSdk || 35}" />
                <span class="helper-text">টার্গেট রানটাইম বিহেভিয়ার SDK</span>
              </div>
            </div>

            <div class="tmpl-form-field" style="margin-bottom:var(--sp-4)">
              <label for="cfg-description"><span class="material-symbols-outlined" style="font-size:16px">description</span> প্রজেক্টের সংক্ষিপ্ত বিবরণ (Description)</label>
              <textarea class="textarea" id="cfg-description" rows="2" placeholder="প্রজেক্ট বা লাইব্রেরির মূল উদ্দেশ্য ও ফিচার…">${this.#currentConfig.description || ''}</textarea>
            </div>
          </div>

          <!-- Tab 2: File Structure & Dependencies -->
          <div id="tmpl-tab-files" style="display:${this.#activeDetailTab === 'files' ? 'block' : 'none'}">
            <h3 style="font-size:var(--text-sm);font-weight:700;color:var(--text-primary);margin-bottom:var(--sp-3)">
              ফাইল স্ট্রাকচার ও রিপোজিটরি হায়ারার্কি
            </h3>
            <div class="tmpl-file-tree" style="margin-bottom:var(--sp-6)">
              ${activeTemplate.fileStructure.map(file => `
                <div class="tmpl-file-row">
                  <div class="tmpl-file-left">
                    <span class="material-symbols-outlined" style="font-size:16px;color:var(--accent)">
                      ${file.path.endsWith('.gradle') ? 'build' : file.path.endsWith('.xml') ? 'data_object' : file.path.endsWith('.java') ? 'code' : 'description'}
                    </span>
                    <span>${file.path}</span>
                  </div>
                  <div style="display:flex;align-items:center;gap:6px">
                    <span class="tmpl-file-badge">${file.required ? 'বাধ্যতামূলক' : 'ঐচ্ছিক'}</span>
                    <span style="color:var(--text-muted);font-size:11px">${file.purpose}</span>
                  </div>
                </div>
              `).join('')}
            </div>

            <h3 style="font-size:var(--text-sm);font-weight:700;color:var(--text-primary);margin-bottom:var(--sp-3)">
              মূল ডিপেন্ডেন্সি কনফিগারেশন (${activeTemplate.dependencies.length})
            </h3>
            <div class="table-container" style="overflow-x:auto">
              <table class="table" style="width:100%;font-size:var(--text-xs)">
                <thead>
                  <tr>
                    <th>Maven Coordinate</th>
                    <th>Gradle Scope</th>
                    <th>উদ্দেশ্য ও ভূমিকা</th>
                    <th>স্ট্যাটাস</th>
                  </tr>
                </thead>
                <tbody>
                  ${activeTemplate.dependencies.map(dep => `
                    <tr>
                      <td style="font-family:var(--font-mono);font-weight:600;color:var(--accent)">${dep.coordinate}</td>
                      <td><span class="badge badge-accent">${dep.scope}</span></td>
                      <td style="color:var(--text-secondary)">${dep.purpose}</td>
                      <td><span class="badge ${dep.required ? 'badge-success' : 'badge-muted'}">${dep.required ? 'Required' : 'Optional'}</span></td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          </div>

          <!-- Tab 3: AI-Ready JSON Preview -->
          <div id="tmpl-tab-json" style="display:${this.#activeDetailTab === 'json' ? 'block' : 'none'}">
            <div class="ai-spec-banner">
              <span class="material-symbols-outlined" style="color:var(--accent);font-size:22px">psychology</span>
              <div>
                <strong>AI-Ready গ্যারান্টি:</strong> এই JSON ফাইলটি সম্পূর্ণ সেলফ-কনটেইন্ড। এটি যেকোনো AI মডেল (ChatGPT, Claude, Gemini, DeepSeek)-এ ইনপুট দিলে AI কোনো অতিরিক্ত প্রশ্ন বা ভুল অনুমান ছাড়াই পুরো প্রজেক্ট জেনারেট বা আপডেট করতে পারবে।
              </div>
            </div>

            <div class="tmpl-json-preview-wrap">
              <div class="tmpl-json-toolbar">
                <div style="display:flex;align-items:center;gap:8px">
                  <span class="material-symbols-outlined" style="font-size:16px;color:var(--accent)">code_blocks</span>
                  <span style="font-size:12px;font-weight:700;color:var(--text-secondary)">project-ai-spec.json</span>
                  <span class="badge badge-sm" style="font-size:10px">UTF-8</span>
                </div>
                <div style="display:flex;gap:6px">
                  <button class="btn btn-ghost btn-xs btn-icon" id="tmpl-copy-json-mini-btn" title="JSON কপি করুন">
                    <span class="material-symbols-outlined" style="font-size:16px">content_copy</span>
                  </button>
                  <button class="btn btn-ghost btn-xs btn-icon" id="tmpl-download-json-mini-btn" title="JSON ফাইল ডাউনলোড করুন">
                    <span class="material-symbols-outlined" style="font-size:16px">download</span>
                  </button>
                </div>
              </div>
              <pre class="tmpl-json-pre" id="tmpl-json-code-block">${this.#escapeHtml(generatedJsonString)}</pre>
            </div>
          </div>
        </div>
      </div>
    `;

    this.#bindEvents();
  }

  #renderCard(t, isSelected) {
    return `
      <div class="tmpl-card ${isSelected ? 'selected' : ''}" data-template-id="${t.id}">
        <div class="tmpl-card-header">
          <div class="tmpl-card-icon-wrap" style="color:${t.color}">
            <span class="material-symbols-outlined" style="font-size:24px">${t.icon}</span>
          </div>
          <span class="tmpl-card-badge">${t.badge}</span>
        </div>
        <div class="tmpl-card-name">${t.name}</div>
        <div class="tmpl-card-desc">${t.description}</div>
        <div class="tmpl-card-meta">
          <span class="tmpl-meta-tag"><span class="material-symbols-outlined" style="font-size:12px;vertical-align:middle">code</span> ${t.language}</span>
          <span class="tmpl-meta-tag"><span class="material-symbols-outlined" style="font-size:12px;vertical-align:middle">layers</span> SDK ${t.recommendedBuildEnvironment.compileSdk}</span>
          <span class="tmpl-meta-tag"><span class="material-symbols-outlined" style="font-size:12px;vertical-align:middle">folder_zip</span> ${t.targetArtifact}</span>
        </div>
        <div class="tmpl-card-actions">
          <button class="btn ${isSelected ? 'btn-primary' : 'btn-secondary'} btn-sm" style="flex:1;justify-content:center" data-action="select" data-template-id="${t.id}">
            <span class="material-symbols-outlined">${isSelected ? 'check_circle' : 'tune'}</span>
            ${isSelected ? 'নির্বাচিত ও সক্রিয়' : 'নির্বাচন করুন'}
          </button>
          <button class="btn btn-secondary btn-sm" style="justify-content:center;white-space:nowrap" data-action="export-schema" data-template-id="${t.id}" title="Export Required Information Schema">
            <span class="material-symbols-outlined" style="font-size:16px">schema</span> Schema
          </button>
        </div>
      </div>
    `;
  }

  #bindEvents() {
    const c = this.#container;
    if (!c) return;

    // Category pills
    c.querySelectorAll('.category-pill').forEach(btn => {
      btn.addEventListener('click', () => {
        this.#activeCategory = btn.dataset.cat;
        this.#render();
      });
    });

    // Search input
    const searchInput = c.querySelector('#tmpl-search-input');
    searchInput?.addEventListener('input', (e) => {
      this.#searchTerm = e.target.value;
      // Re-render only cards if possible, or full render
      this.#render();
      const updatedInput = c.querySelector('#tmpl-search-input');
      if (updatedInput) {
        updatedInput.focus();
        updatedInput.setSelectionRange(updatedInput.value.length, updatedInput.value.length);
      }
    });

    // Refresh button
    c.querySelector('#tmpl-refresh-btn')?.addEventListener('click', () => {
      this.#initTemplateConfig();
      this.#render();
      toast.info('টেমপ্লেট তালিকা রিফ্রেশ করা হয়েছে');
    });

    // Template selection cards
    c.querySelectorAll('.tmpl-card[data-template-id]').forEach(card => {
      card.addEventListener('click', (e) => {
        const schemaBtn = e.target.closest('[data-action="export-schema"]');
        const id = card.dataset.templateId;
        if (schemaBtn) {
          e.stopPropagation();
          if (id) {
            this.#selectedTemplateId = id;
            this.#initTemplateConfig();
            this.#render();
            this.#openSchemaModal(id);
          }
          return;
        }

        if (id && id !== this.#selectedTemplateId) {
          this.#selectedTemplateId = id;
          this.#initTemplateConfig();
          this.#render();
          const detailPanel = c.querySelector('#tmpl-workspace-panel');
          detailPanel?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        }
      });
    });

    // Export Required Information Schema Button
    c.querySelector('#tmpl-export-schema-btn')?.addEventListener('click', () => {
      this.#openSchemaModal(this.#selectedTemplateId);
    });

    // Nav tabs
    c.querySelectorAll('.tmpl-nav-tab').forEach(tabBtn => {
      tabBtn.addEventListener('click', () => {
        this.#activeDetailTab = tabBtn.dataset.tab;
        this.#updateTabDisplay();
      });
    });

    // Live form inputs
    const fieldIds = ['projectName', 'packageName', 'groupId', 'artifactId', 'version', 'compileSdk', 'minSdk', 'targetSdk', 'description'];
    fieldIds.forEach(id => {
      const el = c.querySelector(`#cfg-${id}`);
      el?.addEventListener('input', (e) => {
        this.#currentConfig[id] = e.target.value;
        this.#updateJsonPreview();
      });
    });

    // Copy JSON action
    const handleCopy = async () => {
      const spec = AIProjectSpecGenerator.generateSpec(this.#selectedTemplateId, this.#currentConfig);
      const ok = await AIProjectSpecGenerator.copyToClipboard(spec);
      if (ok) {
        toast.success('AI-Ready JSON ক্লিপবোর্ডে কপি করা হয়েছে!', 'যেকোনো AI প্রম্পটে পেস্ট করে ব্যবহার করুন');
      } else {
        toast.error('ক্লিপবোর্ডে কপি ব্যর্থ হয়েছে', 'ম্যানুয়ালি কোড ব্লক থেকে কপি করুন');
      }
    };

    c.querySelector('#tmpl-copy-json-btn')?.addEventListener('click', handleCopy);
    c.querySelector('#tmpl-copy-json-mini-btn')?.addEventListener('click', handleCopy);

    // Download JSON action
    const handleDownload = () => {
      const spec = AIProjectSpecGenerator.generateSpec(this.#selectedTemplateId, this.#currentConfig);
      const filename = `${this.#currentConfig.projectName || 'project'}-ai-spec.json`;
      AIProjectSpecGenerator.downloadJson(spec, filename);
      toast.success('JSON ডাউনলোড সম্পন্ন হয়েছে', filename);
    };

    c.querySelector('#tmpl-download-json-btn')?.addEventListener('click', handleDownload);
    c.querySelector('#tmpl-download-json-mini-btn')?.addEventListener('click', handleDownload);

    // Use in Generator
    c.querySelector('#tmpl-use-generator-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', {
        pageId: 'generator',
        params: {
          template: this.#selectedTemplateId,
          projectName: this.#currentConfig.projectName,
          packageName: this.#currentConfig.packageName,
          groupId: this.#currentConfig.groupId,
          artifactId: this.#currentConfig.artifactId,
          version: this.#currentConfig.version,
          compileSdk: this.#currentConfig.compileSdk,
          minSdk: this.#currentConfig.minSdk,
          targetSdk: this.#currentConfig.targetSdk
        }
      });
      toast.info('টেমপ্লেট কনফিগারেশন জেনারেটরে লোড করা হয়েছে');
    });
  }

  #updateTabDisplay() {
    const c = this.#container;
    if (!c) return;
    c.querySelectorAll('.tmpl-nav-tab').forEach(b => {
      b.classList.toggle('active', b.dataset.tab === this.#activeDetailTab);
    });
    const cfgTab = c.querySelector('#tmpl-tab-config');
    const filesTab = c.querySelector('#tmpl-tab-files');
    const jsonTab = c.querySelector('#tmpl-tab-json');
    if (cfgTab) cfgTab.style.display = this.#activeDetailTab === 'config' ? 'block' : 'none';
    if (filesTab) filesTab.style.display = this.#activeDetailTab === 'files' ? 'block' : 'none';
    if (jsonTab) jsonTab.style.display = this.#activeDetailTab === 'json' ? 'block' : 'none';
  }

  #updateJsonPreview() {
    const pre = this.#container?.querySelector('#tmpl-json-code-block');
    if (!pre) return;
    const spec = AIProjectSpecGenerator.generateSpec(this.#selectedTemplateId, this.#currentConfig);
    pre.textContent = JSON.stringify(spec, null, 2);
  }

  #escapeHtml(str) {
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }

  /**
   * Open the Export Required Information Schema Preview Panel / Modal.
   * Provides:
   * 1. Copy JSON — copies full schema to clipboard
   * 2. Download JSON — downloads as .json file
   * 3. Close — closes preview and returns cleanly to Template List
   *
   * @param {string|null} templateId
   */
  #openSchemaModal(templateId = null) {
    const targetId = templateId || this.#selectedTemplateId;
    const tmpl = TEMPLATES_CATALOG.find(t => t.id === targetId) || TEMPLATES_CATALOG[0];
    const schema = AIProjectSpecGenerator.generateRequiredInformationSchema(targetId, this.#currentConfig);
    const schemaJsonString = JSON.stringify(schema, null, 2);

    // Remove any previous open modal
    const existing = document.getElementById('tmpl-schema-preview-modal');
    if (existing) existing.remove();

    const overlay = document.createElement('div');
    overlay.className = 'tmpl-schema-modal-overlay';
    overlay.id = 'tmpl-schema-preview-modal';
    overlay.setAttribute('role', 'dialog');
    overlay.setAttribute('aria-modal', 'true');

    overlay.innerHTML = `
      <div class="tmpl-schema-modal">
        <!-- Header -->
        <div class="tmpl-schema-modal-header">
          <div class="tmpl-schema-modal-title-wrap">
            <span class="material-symbols-outlined" style="color:${tmpl.color};font-size:28px">${tmpl.icon}</span>
            <div>
              <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                <h3 style="font-size:var(--text-base);font-weight:700;color:var(--text-primary);margin:0">
                  Required Information Schema: ${tmpl.name}
                </h3>
                <span class="badge badge-accent" style="font-size:10px">${schema.project_type}</span>
              </div>
              <p style="font-size:var(--text-xs);color:var(--text-muted);margin:2px 0 0">
                এই টেমপ্লেটের প্রজেক্ট তৈরি করতে যে information ও configuration প্রয়োজন তার সুসংগঠিত JSON Schema
              </p>
            </div>
          </div>
          <button class="btn btn-ghost btn-sm btn-icon" id="schema-close-x-btn" title="বন্ধ করুন (Esc)">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>

        <!-- Metadata Overview Bar -->
        <div class="tmpl-schema-modal-meta-bar">
          <div class="tmpl-schema-meta-item">
            <span class="material-symbols-outlined" style="font-size:14px;color:var(--accent)">fact_check</span>
            <span>বাধ্যতামূলক ফিল্ড: <strong>${schema.required_information.required.length}টি</strong></span>
          </div>
          <div class="tmpl-schema-meta-item">
            <span class="material-symbols-outlined" style="font-size:14px;color:var(--accent)">tune</span>
            <span>ঐচ্ছিক ফিল্ড: <strong>${schema.required_information.optional.length}টি</strong></span>
          </div>
          <div class="tmpl-schema-meta-item">
            <span class="material-symbols-outlined" style="font-size:14px;color:var(--accent)">account_tree</span>
            <span>মডিউল সংখ্যা: <strong>${schema.modules_specification.length}টি</strong> (${schema.target_artifact})</span>
          </div>
          <div class="tmpl-schema-meta-item">
            <span class="material-symbols-outlined" style="font-size:14px;color:var(--accent)">terminal</span>
            <span>Gradle: <strong>${schema.build_environment_specification.gradle_wrapper_version}</strong> · JDK: <strong>${schema.build_environment_specification.jdk_target_version}</strong></span>
          </div>
        </div>

        <!-- Action Toolbar -->
        <div class="tmpl-schema-modal-actions-bar">
          <div style="font-size:var(--text-xs);color:var(--text-secondary);display:flex;align-items:center;gap:6px">
            <span class="material-symbols-outlined" style="font-size:16px;color:var(--accent)">data_object</span>
            <span><strong>JSON Schema Draft-07</strong> (AI-Ready & Tool Validation Compatible)</span>
          </div>
          <div style="display:flex;gap:var(--sp-2)">
            <button class="btn btn-primary btn-sm" id="schema-copy-btn">
              <span class="material-symbols-outlined">content_copy</span> Copy JSON
            </button>
            <button class="btn btn-secondary btn-sm" id="schema-download-btn">
              <span class="material-symbols-outlined">download</span> Download JSON
            </button>
            <button class="btn btn-ghost btn-sm" id="schema-close-btn">
              <span class="material-symbols-outlined">close</span> Close
            </button>
          </div>
        </div>

        <!-- Scrollable JSON Body -->
        <div class="tmpl-schema-modal-body">
          <pre class="tmpl-schema-code-pre" id="tmpl-schema-code-block">${this.#escapeHtml(schemaJsonString)}</pre>
        </div>

        <!-- Footer Actions Bar -->
        <div class="tmpl-schema-modal-footer">
          <div style="font-size:var(--text-xs);color:var(--text-muted)">
            <span class="material-symbols-outlined" style="font-size:14px;vertical-align:middle;color:var(--accent)">tips_and_updates</span>
            যেকোনো AI টুলে (ChatGPT, Claude, Gemini, DeepSeek) এই JSON প্রদান করলে AI প্রয়োজনীয় সমস্ত ফিল্ড বিশ্লেষণ করে নির্বাচিত Template অনুযায়ী প্রজেক্ট তৈরি করতে পারবে।
          </div>
          <div style="display:flex;gap:var(--sp-2)">
            <button class="btn btn-primary btn-sm" id="schema-copy-btn-footer">
              <span class="material-symbols-outlined">content_copy</span> Copy JSON
            </button>
            <button class="btn btn-secondary btn-sm" id="schema-download-btn-footer">
              <span class="material-symbols-outlined">download</span> Download JSON
            </button>
            <button class="btn btn-ghost btn-sm" id="schema-close-btn-footer">
              <span class="material-symbols-outlined">close</span> Close
            </button>
          </div>
        </div>
      </div>
    `;

    document.body.appendChild(overlay);

    // Event Handlers for Copy, Download, Close
    const closeModal = () => {
      overlay.remove();
      document.removeEventListener('keydown', handleKeydown);
    };

    const handleKeydown = (e) => {
      if (e.key === 'Escape') {
        closeModal();
      }
    };
    document.addEventListener('keydown', handleKeydown);

    // Close on backdrop click
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) closeModal();
    });

    overlay.querySelector('#schema-close-x-btn')?.addEventListener('click', closeModal);
    overlay.querySelector('#schema-close-btn')?.addEventListener('click', closeModal);
    overlay.querySelector('#schema-close-btn-footer')?.addEventListener('click', closeModal);

    // Copy action
    const handleCopySchema = async () => {
      const ok = await AIProjectSpecGenerator.copyToClipboard(schema);
      if (ok) {
        toast.success('Required Information Schema ক্লিপবোর্ডে কপি করা হয়েছে!', 'AI বা জেনারেটরে পেস্ট করার জন্য প্রস্তুত');
      } else {
        toast.error('ক্লিপবোর্ডে কপি ব্যর্থ হয়েছে', 'ম্যানুয়ালি কোড ব্লক থেকে কপি করুন');
      }
    };
    overlay.querySelector('#schema-copy-btn')?.addEventListener('click', handleCopySchema);
    overlay.querySelector('#schema-copy-btn-footer')?.addEventListener('click', handleCopySchema);

    // Download action
    const handleDownloadSchema = () => {
      const filename = `${tmpl.id}-required-information-schema.json`;
      AIProjectSpecGenerator.downloadJson(schema, filename);
      toast.success('Schema JSON ডাউনলোড সম্পন্ন হয়েছে', filename);
    };
    overlay.querySelector('#schema-download-btn')?.addEventListener('click', handleDownloadSchema);
    overlay.querySelector('#schema-download-btn-footer')?.addEventListener('click', handleDownloadSchema);
  }
}
