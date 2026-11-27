/**
 * EcosystemManagerPage — NativeRuntime Ecosystem: GitHub & JitPack Library Version Manager.
 *
 * Implements:
 * 1. All Libraries Dashboard (HUD counters, searchable table, filters, sorting, direct actions)
 * 2. Release Monitor (source project version vs GitHub release vs Git tag vs JitPack build)
 * 3. JitPack Build Monitor (build status, log inspection, error excerpt, direct web links)
 * 4. Ecosystem Dependency Monitor (matrix, SVG dependency graph, outdated/conflict/cycle detection)
 * 5. GitHub Repository Discovery & manual registration
 * 6. Library Details Panel & custom ecosystem groups
 */

import { ecosystemRepositoryService } from '../modules/ecosystem/EcosystemRepositoryService.js';
import { EcosystemScanner } from '../modules/ecosystem/EcosystemScanner.js';
import { EcosystemDependencyGraph } from '../modules/ecosystem/EcosystemDependencyGraph.js';
import { JitPackClient } from '../modules/ecosystem/JitPackClient.js';
import { VersionComparator } from '../modules/ecosystem/VersionComparator.js';
import { githubAuth } from '../modules/github/GitHubAuth.js';
import { toast } from '../ui/ToastService.js';
import { modal } from '../ui/ModalService.js';
import { eventBus } from '../core/EventBus.js';

export class EcosystemManagerPage {
  #container = null;
  #activeTab = 'dashboard'; // 'dashboard' | 'release-monitor' | 'jitpack-monitor' | 'dependency-monitor'
  #libraries = [];
  #metrics = {
    totalLibraries: 0,
    latestCount: 0,
    updateAvailableCount: 0,
    jitpackFailureCount: 0,
    unverifiedCount: 0,
    lastSyncTimestamp: null,
  };
  #groups = [];
  #filters = {
    search: '',
    owner: 'all',
    status: 'all',
    group: 'all',
    sortBy: 'name',
    sortDir: 'asc',
  };
  #isLoading = false;
  #isRefreshing = false;
  #unsubs = [];

  async init(container, params = {}) {
    this.#container = container;
    if (params.tab) this.#activeTab = params.tab;

    // Listen to ecosystem events
    this.#unsubs.push(
      eventBus.on('ecosystem:library:registered', () => this.#reloadAndRender()),
      eventBus.on('ecosystem:library:updated', () => this.#reloadAndRender()),
      eventBus.on('ecosystem:libraries:updated', () => this.#reloadAndRender()),
      eventBus.on('ecosystem:library:removed', () => this.#reloadAndRender()),
      eventBus.on('ecosystem:refreshed:all', () => this.#reloadAndRender())
    );

    await this.#loadData();
    this.#render();
  }

  destroy() {
    this.#unsubs.forEach(u => u());
    this.#unsubs = [];
    if (this.#container) {
      this.#container.innerHTML = '';
      this.#container = null;
    }
  }

  async #loadData() {
    this.#isLoading = true;
    try {
      await githubAuth.loadSaved();
      await ecosystemRepositoryService.init();
      this.#libraries = await ecosystemRepositoryService.list(this.#filters);
      this.#metrics = await ecosystemRepositoryService.getDashboardMetrics();
      this.#groups = await ecosystemRepositoryService.getGroups();
    } catch (err) {
      toast.error(`Failed to load ecosystem data: ${err.message}`);
    } finally {
      this.#isLoading = false;
    }
  }

  async #reloadAndRender() {
    await this.#loadData();
    if (this.#container && document.body.contains(this.#container)) {
      this.#render();
    }
  }

  #render() {
    if (!this.#container) return;

    // Collect distinct owners for filter dropdown
    const allOwners = Array.from(new Set(this.#libraries.map(l => l.owner))).filter(Boolean);
    const isAuth = githubAuth.isAuthenticated;
    const authUser = githubAuth.user;
    const hasDummy = this.#libraries.some(l => l.owner === 'infoadmstudio2020-cyber' || l.isDefault);

    this.#container.innerHTML = `
      <div class="page-container ecosystem-page">

        <!-- Page Header -->
        <div class="page-header" style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:var(--sp-3)">
          <div>
            <h1 class="page-title" style="display:flex;align-items:center;gap:var(--sp-2)">
              <span class="material-symbols-outlined" style="color:var(--accent);font-size:28px">hub</span>
              NativeRuntime Ecosystem Version Manager
            </h1>
            <p class="page-subtitle">
              Centralized monitoring for GitHub releases, Git tags, JitPack builds, and cross-library dependency compatibility.
            </p>
          </div>
          <div style="display:flex;align-items:center;gap:var(--sp-2);flex-wrap:wrap">
            ${isAuth ? `
              <button class="btn btn-primary" id="btn-import-my-repos" style="display:inline-flex;align-items:center;gap:6px">
                <span class="material-symbols-outlined">cloud_download</span>
                Import My Repos
              </button>
            ` : ''}
            <button class="btn btn-secondary" id="btn-scan-github" style="display:inline-flex;align-items:center;gap:6px">
              <span class="material-symbols-outlined">search</span>
              Scan GitHub
            </button>
            <button class="btn btn-secondary" id="btn-add-manual" style="display:inline-flex;align-items:center;gap:6px">
              <span class="material-symbols-outlined">add</span>
              Add Repository
            </button>
            <button class="btn btn-secondary" id="btn-load-real-samples" style="display:inline-flex;align-items:center;gap:6px" title="Load real Android libraries (Retrofit, Gson, OkHttp) with live releases">
              <span class="material-symbols-outlined">auto_stories</span>
              Load Samples
            </button>
            ${hasDummy ? `
              <button class="btn btn-secondary" id="btn-remove-dummy" style="display:inline-flex;align-items:center;gap:6px;color:var(--error)" title="Remove unverified dummy placeholder libraries">
                <span class="material-symbols-outlined">delete_sweep</span>
                Remove Dummy
              </button>
            ` : ''}
            <button class="btn btn-primary" id="btn-refresh-all" style="display:inline-flex;align-items:center;gap:6px" ${this.#isRefreshing ? 'disabled' : ''}>
              <span class="material-symbols-outlined ${this.#isRefreshing ? 'rotating' : ''}">sync</span>
              ${this.#isRefreshing ? 'Syncing…' : 'Refresh All'}
            </button>
          </div>
        </div>

        <!-- GitHub Authentication Notice / Status Banner -->
        ${!isAuth ? `
          <div class="ecosystem-auth-banner" style="background:linear-gradient(135deg, rgba(56,189,248,0.12) 0%, rgba(14,165,233,0.05) 100%);border:1px solid rgba(56,189,248,0.3);border-radius:var(--radius-lg);padding:var(--sp-4) var(--sp-5);margin-bottom:var(--sp-4);display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-3)">
            <div style="display:flex;align-items:center;gap:var(--sp-3);max-width:740px">
              <div style="width:42px;height:42px;border-radius:50%;background:rgba(56,189,248,0.2);display:flex;align-items:center;justify-content:center;color:#38BDF8;flex-shrink:0">
                <span class="material-symbols-outlined" style="font-size:24px">key</span>
              </div>
              <div>
                <div style="display:flex;align-items:center;gap:8px">
                  <h3 style="font-size:15px;font-weight:600;margin:0;color:var(--text-primary)">
                    Connect your GitHub account using a Personal Access Token
                  </h3>
                  <span class="badge" style="background:rgba(245,158,11,0.18);color:#F59E0B;font-size:10px;padding:2px 6px">
                    Unauthenticated: 60 req/hr
                  </span>
                </div>
                <p style="font-size:12px;color:var(--text-secondary);margin:4px 0 0 0;line-height:1.4">
                  First connect your GitHub Personal Access Token on the <strong>Authentication Page</strong> to unlock <strong>5,000 req/hr</strong>, discover private & organization repositories, and activate full real-mode Ecosystem Version Manager.
                </p>
              </div>
            </div>
            <div style="display:flex;align-items:center;gap:var(--sp-2)">
              <button class="btn btn-primary btn-goto-auth-page" style="display:inline-flex;align-items:center;gap:6px">
                <span class="material-symbols-outlined">verified_user</span>
                Go to Authentication Page
              </button>
              <button class="btn btn-secondary" id="btn-quick-connect-token" style="display:inline-flex;align-items:center;gap:6px">
                <span class="material-symbols-outlined">vpn_key</span>
                Connect Token
              </button>
            </div>
          </div>
        ` : `
          <div class="ecosystem-auth-badge-bar" style="background:rgba(16,185,129,0.07);border:1px solid rgba(16,185,129,0.22);border-radius:var(--radius-md);padding:var(--sp-2) var(--sp-4);margin-bottom:var(--sp-3);display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-2)">
            <div style="display:flex;align-items:center;gap:var(--sp-3)">
              <img src="${this.#escape(authUser?.avatar_url || '')}" alt="${this.#escape(authUser?.login || '')}" style="width:26px;height:26px;border-radius:50%;border:1px solid var(--border)" />
              <div style="display:flex;align-items:center;gap:6px">
                <span style="font-size:13px;font-weight:600;color:var(--text-primary)">@${this.#escape(authUser?.login || '')}</span>
                <span class="badge" style="background:rgba(16,185,129,0.15);color:#10B981;font-size:10px;padding:2px 6px">
                  <span class="material-symbols-outlined" style="font-size:11px;vertical-align:middle">check_circle</span> Authenticated (5,000 req/hr)
                </span>
              </div>
              <span style="font-size:11px;color:var(--text-muted)">• Real-Mode Live GitHub & JitPack Active</span>
            </div>
            <div style="display:flex;align-items:center;gap:var(--sp-2)">
              <button class="btn btn-sm btn-secondary btn-goto-auth-page" style="font-size:11px;padding:3px 8px">
                Manage Authentication
              </button>
              <button class="btn btn-sm btn-secondary" id="btn-disconnect-token" style="font-size:11px;padding:3px 8px;color:var(--error)">
                Disconnect
              </button>
            </div>
          </div>
        `}

        <!-- HUD Metrics Summary Cards -->
        <div class="ecosystem-hud-grid">
          <div class="ecosystem-hud-card">
            <span class="ecosystem-hud-lbl">Registered Libraries</span>
            <span class="ecosystem-hud-val">${this.#metrics.totalLibraries}</span>
          </div>
          <div class="ecosystem-hud-card">
            <span class="ecosystem-hud-lbl">Latest & Synchronized</span>
            <span class="ecosystem-hud-val" style="color:#10B981">${this.#metrics.latestCount}</span>
          </div>
          <div class="ecosystem-hud-card">
            <span class="ecosystem-hud-lbl">Updates Available</span>
            <span class="ecosystem-hud-val" style="color:#38BDF8">${this.#metrics.updateAvailableCount}</span>
          </div>
          <div class="ecosystem-hud-card">
            <span class="ecosystem-hud-lbl">JitPack Failures</span>
            <span class="ecosystem-hud-val" style="color:#EF4444">${this.#metrics.jitpackFailureCount}</span>
          </div>
          <div class="ecosystem-hud-card">
            <span class="ecosystem-hud-lbl">Unverified Versions</span>
            <span class="ecosystem-hud-val" style="color:#F59E0B">${this.#metrics.unverifiedCount}</span>
          </div>
          <div class="ecosystem-hud-card">
            <span class="ecosystem-hud-lbl">Last Synchronized</span>
            <span class="ecosystem-hud-val" style="font-size:14px;color:var(--text-secondary)">
              ${this.#metrics.lastSyncTimestamp ? new Date(this.#metrics.lastSyncTimestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : 'Never'}
            </span>
          </div>
        </div>

        <!-- Main Tab Navigation -->
        <div class="ecosystem-tabs">
          <button class="ecosystem-tab-btn ${this.#activeTab === 'dashboard' ? 'active' : ''}" data-tab="dashboard">
            <span class="material-symbols-outlined">dashboard</span>
            All Libraries Dashboard
          </button>
          <button class="ecosystem-tab-btn ${this.#activeTab === 'release-monitor' ? 'active' : ''}" data-tab="release-monitor">
            <span class="material-symbols-outlined">new_releases</span>
            Release Monitor
          </button>
          <button class="ecosystem-tab-btn ${this.#activeTab === 'jitpack-monitor' ? 'active' : ''}" data-tab="jitpack-monitor">
            <span class="material-symbols-outlined">terminal</span>
            JitPack Build Monitor
          </button>
          <button class="ecosystem-tab-btn ${this.#activeTab === 'dependency-monitor' ? 'active' : ''}" data-tab="dependency-monitor">
            <span class="material-symbols-outlined">schema</span>
            Dependency Monitor
          </button>
        </div>

        <!-- Tab Content Area -->
        <div id="ecosystem-tab-content">
          ${this.#renderActiveTab(allOwners)}
        </div>

      </div>
    `;

    this.#bindEvents();
  }

  #renderActiveTab(allOwners) {
    if (this.#activeTab === 'dashboard') {
      return this.#renderDashboardTab(allOwners);
    }
    if (this.#activeTab === 'release-monitor') {
      return this.#renderReleaseMonitorTab();
    }
    if (this.#activeTab === 'jitpack-monitor') {
      return this.#renderJitPackMonitorTab();
    }
    if (this.#activeTab === 'dependency-monitor') {
      return this.#renderDependencyMonitorTab();
    }
    return '';
  }

  // ── Tab 1: All Libraries Dashboard ────────────────────────────────────────

  #renderDashboardTab(allOwners) {
    const isAuth = githubAuth.isAuthenticated;
    return `
      <div style="display:flex;flex-direction:column;gap:var(--sp-4)">
        <!-- Filter Toolbar -->
        <div class="ecosystem-toolbar">
          <div class="ecosystem-filters-group">
            <div class="input-with-icon ecosystem-search-input">
              <span class="material-symbols-outlined">search</span>
              <input type="text" id="filter-search" class="input" placeholder="Search by library, artifact, owner…" value="${this.#escape(this.#filters.search)}" />
            </div>

            <select id="filter-owner" class="input" style="width:auto">
              <option value="all">All Owners</option>
              ${allOwners.map(o => `<option value="${this.#escape(o)}" ${this.#filters.owner === o ? 'selected' : ''}>${this.#escape(o)}</option>`).join('')}
            </select>

            <select id="filter-status" class="input" style="width:auto">
              <option value="all">All Statuses</option>
              <option value="Latest" ${this.#filters.status === 'Latest' ? 'selected' : ''}>Latest</option>
              <option value="Update Available" ${this.#filters.status === 'Update Available' ? 'selected' : ''}>Update Available</option>
              <option value="Project Version Ahead" ${this.#filters.status === 'Project Version Ahead' ? 'selected' : ''}>Project Ahead</option>
              <option value="Release Missing" ${this.#filters.status === 'Release Missing' ? 'selected' : ''}>Release Missing</option>
              <option value="JitPack Build Failed" ${this.#filters.status === 'JitPack Build Failed' ? 'selected' : ''}>JitPack Failed</option>
              <option value="JitPack Not Verified" ${this.#filters.status === 'JitPack Not Verified' ? 'selected' : ''}>Not Verified</option>
              <option value="Version Mismatch" ${this.#filters.status === 'Version Mismatch' ? 'selected' : ''}>Version Mismatch</option>
            </select>

            <select id="filter-group" class="input" style="width:auto">
              <option value="all">All Groups</option>
              ${this.#groups.map(g => `<option value="${this.#escape(g.name)}" ${this.#filters.group === g.name ? 'selected' : ''}>${this.#escape(g.name)}</option>`).join('')}
            </select>
          </div>

          <div class="ecosystem-actions-group">
            <select id="sort-by" class="input" style="width:auto">
              <option value="name" ${this.#filters.sortBy === 'name' ? 'selected' : ''}>Sort by Name</option>
              <option value="version" ${this.#filters.sortBy === 'version' ? 'selected' : ''}>Sort by Version</option>
              <option value="releaseDate" ${this.#filters.sortBy === 'releaseDate' ? 'selected' : ''}>Sort by Release Date</option>
              <option value="updateDate" ${this.#filters.sortBy === 'updateDate' ? 'selected' : ''}>Sort by Last Updated</option>
            </select>
            <button class="btn btn-icon btn-secondary" id="btn-toggle-sort" title="Toggle Asc/Desc">
              <span class="material-symbols-outlined">${this.#filters.sortDir === 'asc' ? 'arrow_upward' : 'arrow_downward'}</span>
            </button>
          </div>
        </div>

        <!-- Libraries Table -->
        <div class="ecosystem-table-wrap">
          ${this.#libraries.length === 0 ? `
            <div style="padding:var(--sp-8);text-align:center;background:var(--bg-surface);border:1px dashed var(--border);border-radius:var(--radius-lg);margin:var(--sp-4) 0">
              <span class="material-symbols-outlined" style="font-size:48px;color:var(--accent);margin-bottom:var(--sp-2)">inventory_2</span>
              <h3 style="margin:0 0 var(--sp-2) 0;font-size:16px;color:var(--text-primary)">No Android Libraries Registered Yet</h3>
              <p style="color:var(--text-secondary);font-size:13px;max-width:540px;margin:0 auto var(--sp-4) auto;line-height:1.5">
                Connect your GitHub account using a Personal Access Token to auto-discover your repositories, or load verified real Android open-source libraries (Retrofit, Gson, OkHttp) with live release, tag, and JitPack tracking.
              </p>
              <div style="display:flex;justify-content:center;gap:var(--sp-2);flex-wrap:wrap">
                ${isAuth ? `
                  <button class="btn btn-primary" id="btn-empty-import-my" style="display:inline-flex;align-items:center;gap:6px">
                    <span class="material-symbols-outlined">cloud_download</span> Import My GitHub Repos
                  </button>
                ` : `
                  <button class="btn btn-primary btn-goto-auth-page" style="display:inline-flex;align-items:center;gap:6px">
                    <span class="material-symbols-outlined">verified_user</span> Connect GitHub Account
                  </button>
                `}
                <button class="btn btn-secondary" id="btn-empty-scan" style="display:inline-flex;align-items:center;gap:6px">
                  <span class="material-symbols-outlined">search</span> Scan Repositories
                </button>
                <button class="btn btn-secondary" id="btn-empty-real-samples" style="display:inline-flex;align-items:center;gap:6px">
                  <span class="material-symbols-outlined">auto_stories</span> Load Real Samples
                </button>
              </div>
            </div>
          ` : `
            <table class="ecosystem-table">
              <thead>
                <tr>
                  <th>Library / Repository</th>
                  <th>Maven Coordinates</th>
                  <th>Project Ver</th>
                  <th>GitHub Release</th>
                  <th>Git Tag</th>
                  <th>JitPack Status</th>
                  <th>Ecosystem Status</th>
                  <th>Last Updated</th>
                  <th style="text-align:right">Actions</th>
                </tr>
              </thead>
              <tbody>
                ${this.#libraries.map(lib => this.#renderLibraryRow(lib)).join('')}
              </tbody>
            </table>
          `}
        </div>
      </div>
    `;
  }

  #renderLibraryRow(lib) {
    const jpStatusClass = lib.jitpackStatus === 'ok' ? 'success' : (lib.jitpackStatus === 'error' ? 'error' : 'muted');
    const jpStatusText = lib.jitpackStatus === 'ok' ? 'Verified OK' : (lib.jitpackStatus === 'error' ? 'Failed' : (lib.jitpackStatus === 'building' ? 'Building…' : 'Unverified'));

    return `
      <tr data-id="${this.#escape(lib.id)}">
        <td>
          <div style="display:flex;flex-direction:column;gap:2px">
            <span style="font-weight:600;color:var(--text-primary)">${this.#escape(lib.name)}</span>
            <div style="display:flex;align-items:center;gap:6px;font-size:11px;color:var(--text-muted)">
              <span>${this.#escape(lib.owner)}/${this.#escape(lib.repo)}</span>
              ${lib.isEcosystem ? `<span class="badge" style="font-size:9px;padding:1px 4px;background:rgba(2,132,199,0.1);color:#38BDF8">Ecosystem</span>` : ''}
              <span class="badge" style="font-size:9px;padding:1px 4px;background:var(--bg-muted);color:var(--text-secondary)">${this.#escape(lib.ecosystemGroup || 'Core')}</span>
            </div>
          </div>
        </td>
        <td>
          <div style="display:flex;flex-direction:column;gap:1px;font-family:monospace;font-size:11px">
            <span style="color:var(--text-secondary)">${this.#escape(lib.groupId)}</span>
            <span style="color:var(--text-muted)">:${this.#escape(lib.artifactId)}</span>
          </div>
        </td>
        <td>
          <span class="version-pill btn-click-deps" data-id="${this.#escape(lib.id)}" style="cursor:pointer" title="Click to view parsed source dependencies">
            ${this.#escape(lib.projectVersion || 'Unknown')}
          </span>
        </td>
        <td>
          <div style="display:flex;flex-direction:column;gap:1px">
            <span class="version-pill btn-click-releases" data-owner="${this.#escape(lib.owner)}" data-repo="${this.#escape(lib.repo)}" style="color:#38BDF8;cursor:pointer" title="Click to view GitHub Releases">
              ${this.#escape(lib.githubRelease || '—')}
            </span>
            ${lib.lastReleaseDate ? `<span style="font-size:10px;color:var(--text-muted)">${new Date(lib.lastReleaseDate).toLocaleDateString()}</span>` : ''}
          </div>
        </td>
        <td>
          <span class="version-pill btn-click-tags" data-owner="${this.#escape(lib.owner)}" data-repo="${this.#escape(lib.repo)}" style="cursor:pointer" title="Click to view Git Tags">
            ${this.#escape(lib.gitTag || '—')}
          </span>
        </td>
        <td>
          <div style="display:flex;align-items:center;gap:4px">
            <span class="badge-status ${jpStatusClass} btn-click-jitpack" data-owner="${this.#escape(lib.owner)}" data-repo="${this.#escape(lib.repo)}" data-ver="${this.#escape(lib.jitpackVersion || lib.gitTag || lib.projectVersion || '')}" style="cursor:pointer" title="Click to check JitPack build status & logs">
              <span class="material-symbols-outlined" style="font-size:12px">
                ${lib.jitpackStatus === 'ok' ? 'check_circle' : (lib.jitpackStatus === 'error' ? 'cancel' : 'help')}
              </span>
              ${jpStatusText}
            </span>
          </div>
        </td>
        <td>
          <span class="badge-status ${lib.severity || 'muted'}" title="${this.#escape(lib.statusDescription || '')}">
            ${this.#escape(lib.versionStatus)}
          </span>
        </td>
        <td>
          <span style="font-size:11px;color:var(--text-muted)">
            ${lib.lastRepoUpdate ? new Date(lib.lastRepoUpdate).toLocaleDateString() : '—'}
          </span>
        </td>
        <td style="text-align:right">
          <div style="display:inline-flex;align-items:center;gap:4px">
            <button class="btn btn-icon btn-secondary btn-row-releases" data-owner="${this.#escape(lib.owner)}" data-repo="${this.#escape(lib.repo)}" title="View GitHub Releases">
              <span class="material-symbols-outlined">new_releases</span>
            </button>
            <button class="btn btn-icon btn-secondary btn-row-tags" data-owner="${this.#escape(lib.owner)}" data-repo="${this.#escape(lib.repo)}" title="View Git Tags">
              <span class="material-symbols-outlined">sell</span>
            </button>
            <button class="btn btn-icon btn-secondary btn-row-jitpack" data-owner="${this.#escape(lib.owner)}" data-repo="${this.#escape(lib.repo)}" data-ver="${this.#escape(lib.jitpackVersion || lib.gitTag || lib.projectVersion || '')}" title="Check JitPack Build Status & Logs">
              <span class="material-symbols-outlined">terminal</span>
            </button>
            <button class="btn btn-icon btn-secondary btn-row-deps" data-id="${this.#escape(lib.id)}" title="View Dependencies (${lib.dependencyCount || 0})">
              <span class="material-symbols-outlined">account_tree</span>
            </button>
            <button class="btn btn-icon btn-secondary btn-row-refresh" data-id="${this.#escape(lib.id)}" title="Refresh Status from GitHub & JitPack">
              <span class="material-symbols-outlined">refresh</span>
            </button>
            <button class="btn btn-icon btn-secondary btn-row-details" data-id="${this.#escape(lib.id)}" title="View Library Details">
              <span class="material-symbols-outlined">info</span>
            </button>
            <a href="${this.#escape(lib.url)}" target="_blank" rel="noopener noreferrer" class="btn btn-icon btn-secondary" title="Open GitHub Repository">
              <span class="material-symbols-outlined">open_in_new</span>
            </a>
            <a href="${JitPackClient.getRepoWebUrl(lib.owner, lib.repo)}" target="_blank" rel="noopener noreferrer" class="btn btn-icon btn-secondary" title="Open JitPack Page">
              <span class="material-symbols-outlined">open_in_browser</span>
            </a>
            <button class="btn btn-icon btn-secondary btn-row-remove" data-id="${this.#escape(lib.id)}" title="Remove from Monitor (Does not delete on GitHub)">
              <span class="material-symbols-outlined" style="color:var(--error)">delete</span>
            </button>
          </div>
        </td>
      </tr>
    `;
  }

  // ── Tab 2: Release Monitor ────────────────────────────────────────────────

  #renderReleaseMonitorTab() {
    return `
      <div style="display:flex;flex-direction:column;gap:var(--sp-4)">
        <div class="card" style="padding:var(--sp-4);background:var(--bg-surface);border:1px solid var(--border);border-radius:var(--radius-lg)">
          <h3 style="font-size:var(--text-base);font-weight:600;display:flex;align-items:center;gap:8px">
            <span class="material-symbols-outlined" style="color:var(--accent)">compare_arrows</span>
            Multi-Source Version Comparison Matrix
          </h3>
          <p style="font-size:var(--text-xs);color:var(--text-muted);margin-top:4px">
            Directly cross-checks the project version configured in build files against published GitHub releases, Git tags, and JitPack build statuses.
          </p>
        </div>

        <div class="release-monitor-grid">
          ${this.#libraries.map(lib => `
            <div class="release-card">
              <div class="release-card-header">
                <div>
                  <div class="release-card-title">${this.#escape(lib.name)}</div>
                  <div style="font-size:11px;color:var(--text-muted)">${this.#escape(lib.owner)}/${this.#escape(lib.repo)}</div>
                </div>
                <span class="badge-status ${lib.severity || 'muted'}">
                  ${this.#escape(lib.versionStatus)}
                </span>
              </div>

              <div class="version-compare-row">
                <div class="version-compare-item">
                  <label>Project Config</label>
                  <span>${this.#escape(lib.projectVersion || 'None')}</span>
                </div>
                <div class="version-compare-item">
                  <label>Latest Release</label>
                  <span style="color:#38BDF8">${this.#escape(lib.githubRelease || 'None')}</span>
                </div>
                <div class="version-compare-item">
                  <label>Latest Tag</label>
                  <span>${this.#escape(lib.gitTag || 'None')}</span>
                </div>
                <div class="version-compare-item">
                  <label>JitPack Status</label>
                  <span style="color:${lib.jitpackStatus === 'ok' ? '#10B981' : (lib.jitpackStatus === 'error' ? '#EF4444' : '#F59E0B')}">
                    ${this.#escape(lib.jitpackStatus || 'Unverified')}
                  </span>
                </div>
              </div>

              ${lib.mismatchDetails && lib.mismatchDetails.length > 0 ? `
                <div style="font-size:11px;color:var(--warning);background:rgba(245,158,11,0.1);padding:6px 10px;border-radius:4px;border:1px solid rgba(245,158,11,0.2)">
                  <span class="material-symbols-outlined" style="font-size:13px;vertical-align:text-bottom">warning</span>
                  ${lib.mismatchDetails.map(d => this.#escape(d)).join(' • ')}
                </div>
              ` : `
                <div style="font-size:11px;color:#10B981;display:flex;align-items:center;gap:4px">
                  <span class="material-symbols-outlined" style="font-size:14px">check_circle</span>
                  All version sources are verified and synchronized.
                </div>
              `}

              <div style="display:flex;justify-content:space-between;align-items:center;margin-top:auto;padding-top:var(--sp-2);border-top:1px solid var(--border)">
                <span style="font-size:10px;color:var(--text-muted)">Checked: ${new Date(lib.lastChecked || Date.now()).toLocaleTimeString()}</span>
                <button class="btn btn-sm btn-secondary btn-row-refresh" data-id="${this.#escape(lib.id)}">
                  <span class="material-symbols-outlined" style="font-size:14px">refresh</span> Refresh
                </button>
              </div>
            </div>
          `).join('')}
        </div>
      </div>
    `;
  }

  // ── Tab 3: JitPack Build Monitor ──────────────────────────────────────────

  #renderJitPackMonitorTab() {
    return `
      <div style="display:flex;flex-direction:column;gap:var(--sp-4)">
        <div class="card" style="padding:var(--sp-4);background:var(--bg-surface);border:1px solid var(--border);border-radius:var(--radius-lg)">
          <h3 style="font-size:var(--text-base);font-weight:600;display:flex;align-items:center;gap:8px">
            <span class="material-symbols-outlined" style="color:var(--accent)">terminal</span>
            JitPack Live Build Diagnostics & Artifact Verification
          </h3>
          <p style="font-size:var(--text-xs);color:var(--text-muted);margin-top:4px">
            Monitors real JitPack maven publication endpoints, build logs, and detects compilation failures before consumer libraries break.
          </p>
        </div>

        <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(360px, 1fr));gap:var(--sp-4)">
          ${this.#libraries.map(lib => {
            const isError = lib.jitpackStatus === 'error' || lib.jitpackStatus === 'failed';
            const isOk = lib.jitpackStatus === 'ok' || lib.jitpackStatus === 'success';

            return `
              <div class="jitpack-card">
                <div style="display:flex;justify-content:space-between;align-items:flex-start">
                  <div>
                    <h4 style="font-size:15px;font-weight:600;color:var(--text-primary)">${this.#escape(lib.name)}</h4>
                    <span style="font-size:11px;font-family:monospace;color:var(--text-muted)">${this.#escape(lib.groupId)}:${this.#escape(lib.artifactId)}</span>
                  </div>
                  <span class="badge-status ${isOk ? 'success' : (isError ? 'error' : 'muted')}">
                    ${isOk ? 'BUILD SUCCESSFUL' : (isError ? 'BUILD FAILED' : 'NOT VERIFIED')}
                  </span>
                </div>

                <div style="display:flex;flex-direction:column;gap:6px;background:var(--bg-muted);padding:var(--sp-3);border-radius:var(--radius-md)">
                  <div style="display:flex;justify-content:space-between;font-size:12px">
                    <span style="color:var(--text-muted)">Target Version:</span>
                    <span style="font-family:monospace;font-weight:600;color:var(--text-primary)">${this.#escape(lib.jitpackVersion || lib.projectVersion || '—')}</span>
                  </div>
                  <div style="display:flex;justify-content:space-between;font-size:12px">
                    <span style="color:var(--text-muted)">Build Status:</span>
                    <span style="font-weight:600;color:${isOk ? '#10B981' : (isError ? '#EF4444' : 'var(--text-secondary)')}">
                      ${this.#escape(lib.jitpackStatus || 'unknown')}
                    </span>
                  </div>
                  <div style="display:flex;justify-content:space-between;font-size:12px">
                    <span style="color:var(--text-muted)">JitPack Page:</span>
                    <a href="${JitPackClient.getRepoWebUrl(lib.owner, lib.repo)}" target="_blank" rel="noopener noreferrer" style="color:var(--accent)">
                      Open Page <span class="material-symbols-outlined" style="font-size:12px">open_in_new</span>
                    </a>
                  </div>
                </div>

                <div style="display:flex;gap:var(--sp-2)">
                  <button class="btn btn-sm btn-secondary btn-view-build-log" data-owner="${this.#escape(lib.owner)}" data-repo="${this.#escape(lib.repo)}" data-ver="${this.#escape(lib.jitpackVersion || lib.projectVersion)}" style="flex:1">
                    <span class="material-symbols-outlined" style="font-size:14px">receipt_long</span> View Build Log
                  </button>
                  <button class="btn btn-sm btn-secondary btn-row-refresh" data-id="${this.#escape(lib.id)}" title="Recheck JitPack">
                    <span class="material-symbols-outlined" style="font-size:14px">sync</span>
                  </button>
                </div>
              </div>
            `;
          }).join('')}
        </div>
      </div>
    `;
  }

  // ── Tab 4: Dependency Monitor ─────────────────────────────────────────────

  #renderDependencyMonitorTab() {
    const analysis = EcosystemDependencyGraph.analyze(this.#libraries);
    const svgMarkup = EcosystemDependencyGraph.renderSvgGraph(analysis, 800, 380);

    return `
      <div style="display:flex;flex-direction:column;gap:var(--sp-4)">
        <!-- Dependency HUD Summary -->
        <div class="ecosystem-hud-grid">
          <div class="ecosystem-hud-card">
            <span class="ecosystem-hud-lbl">Total Inter-Deps</span>
            <span class="ecosystem-hud-val">${analysis.edges.length}</span>
          </div>
          <div class="ecosystem-hud-card">
            <span class="ecosystem-hud-lbl">Outdated Dependencies</span>
            <span class="ecosystem-hud-val" style="color:${analysis.outdatedCount > 0 ? '#F59E0B' : '#10B981'}">
              ${analysis.outdatedCount}
            </span>
          </div>
          <div class="ecosystem-hud-card">
            <span class="ecosystem-hud-lbl">Version Conflicts</span>
            <span class="ecosystem-hud-val" style="color:${analysis.conflictCount > 0 ? '#EF4444' : '#10B981'}">
              ${analysis.conflictCount}
            </span>
          </div>
          <div class="ecosystem-hud-card">
            <span class="ecosystem-hud-lbl">Dependency Cycles</span>
            <span class="ecosystem-hud-val" style="color:${analysis.cycleCount > 0 ? '#EF4444' : '#10B981'}">
              ${analysis.cycleCount}
            </span>
          </div>
        </div>

        <!-- Visual Dependency Graph -->
        <div class="card" style="padding:var(--sp-4);background:var(--bg-surface);border:1px solid var(--border);border-radius:var(--radius-lg)">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-3)">
            <h3 style="font-size:var(--text-base);font-weight:600;display:flex;align-items:center;gap:8px">
              <span class="material-symbols-outlined" style="color:var(--accent)">hub</span>
              Ecosystem Dependency Topology Graph
            </h3>
            <span style="font-size:11px;color:var(--text-muted)">
              Interactive SVG (Blue: Direct, Yellow: Outdated, Red: Cycle)
            </span>
          </div>
          ${svgMarkup}
        </div>

        <!-- Outdated Dependencies Alert Table -->
        ${analysis.outdatedCount > 0 ? `
          <div class="card" style="padding:var(--sp-4);background:var(--bg-surface);border:1px solid rgba(245,158,11,0.3);border-radius:var(--radius-lg)">
            <h4 style="font-size:14px;font-weight:600;color:#F59E0B;display:flex;align-items:center;gap:6px;margin-bottom:var(--sp-2)">
              <span class="material-symbols-outlined">warning</span>
              Outdated Ecosystem Dependencies Detected (${analysis.outdatedCount})
            </h4>
            <table class="ecosystem-table" style="font-size:12px">
              <thead>
                <tr>
                  <th>Consumer Library</th>
                  <th>Dependency</th>
                  <th>Declared Version</th>
                  <th>Available Version</th>
                  <th>Scope</th>
                </tr>
              </thead>
              <tbody>
                ${analysis.outdatedList.map(item => `
                  <tr>
                    <td style="font-weight:600">${this.#escape(item.sourceLib)}</td>
                    <td>${this.#escape(item.targetLib)}</td>
                    <td><span class="version-pill" style="color:#EF4444">${this.#escape(item.declaredVersion)}</span></td>
                    <td><span class="version-pill" style="color:#10B981">${this.#escape(item.latestVersion)}</span></td>
                    <td><span class="badge" style="font-size:10px">${this.#escape(item.scope)}</span></td>
                  </tr>
                `).join('')}
              </tbody>
            </table>
          </div>
        ` : ''}

        <!-- Conflicts and Cycles Alerts -->
        ${analysis.conflictCount > 0 ? `
          <div class="card" style="padding:var(--sp-4);background:var(--bg-surface);border:1px solid rgba(239,68,68,0.3);border-radius:var(--radius-lg)">
            <h4 style="font-size:14px;font-weight:600;color:#EF4444;display:flex;align-items:center;gap:6px;margin-bottom:var(--sp-2)">
              <span class="material-symbols-outlined">error</span>
              Version Conflict Hazards (${analysis.conflictCount})
            </h4>
            <p style="font-size:12px;color:var(--text-muted);margin-bottom:var(--sp-2)">
              Different ecosystem modules are consuming disparate versions of the same library:
            </p>
            <div style="display:flex;flex-direction:column;gap:var(--sp-2)">
              ${analysis.conflictList.map(c => `
                <div style="padding:var(--sp-2) var(--sp-3);background:var(--bg-muted);border-radius:var(--radius-md)">
                  <span style="font-weight:600">${this.#escape(c.targetLib)}:</span>
                  ${c.usages.map(u => `<span style="font-size:11px;margin-left:8px">${this.#escape(u.consumer)} (${this.#escape(u.version)})</span>`).join('')}
                </div>
              `).join('')}
            </div>
          </div>
        ` : ''}
      </div>
    `;
  }

  // ── Modals: Details, Discovery & Manual Add ───────────────────────────────

  async #showLibraryDetails(id) {
    const lib = await ecosystemRepositoryService.get(id);
    if (!lib) return;

    modal.show({
      title: `${lib.name} — Library Details`,
      content: `
        <div class="ecosystem-modal-body">
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:var(--sp-3);background:var(--bg-muted);padding:var(--sp-3);border-radius:var(--radius-md)">
            <div>
              <span style="font-size:11px;color:var(--text-muted)">Repository</span>
              <div style="font-weight:600">${this.#escape(lib.owner)}/${this.#escape(lib.repo)}</div>
            </div>
            <div>
              <span style="font-size:11px;color:var(--text-muted)">Coordinates</span>
              <div style="font-family:monospace;font-size:11px">${this.#escape(lib.groupId)}:${this.#escape(lib.artifactId)}</div>
            </div>
            <div>
              <span style="font-size:11px;color:var(--text-muted)">Ecosystem Status</span>
              <div><span class="badge-status ${lib.severity || 'muted'}">${this.#escape(lib.versionStatus)}</span></div>
            </div>
            <div>
              <span style="font-size:11px;color:var(--text-muted)">JitPack Status</span>
              <div><span class="badge-status ${lib.jitpackStatus === 'ok' ? 'success' : 'error'}">${this.#escape(lib.jitpackStatus || 'unknown')}</span></div>
            </div>
          </div>

          <div>
            <h5 style="font-size:13px;font-weight:600;margin-bottom:var(--sp-1)">Description</h5>
            <p style="font-size:12px;color:var(--text-secondary)">${this.#escape(lib.description || 'No description provided.')}</p>
          </div>

          <div>
            <h5 style="font-size:13px;font-weight:600;margin-bottom:var(--sp-1)">Dependencies (${lib.dependencies?.length || 0})</h5>
            ${lib.dependencies && lib.dependencies.length > 0 ? `
              <div style="display:flex;flex-wrap:wrap;gap:6px">
                ${lib.dependencies.map(d => `
                  <span class="badge" style="font-family:monospace;font-size:10px;padding:3px 8px;background:var(--bg-muted)">
                    ${this.#escape(d.groupId || '')}:${this.#escape(d.artifactId || d.name)}:${this.#escape(d.version)} (${this.#escape(d.scope || 'impl')})
                  </span>
                `).join('')}
              </div>
            ` : '<span style="font-size:11px;color:var(--text-muted)">No external dependencies declared.</span>'}
          </div>

          <div style="display:flex;gap:var(--sp-2);margin-top:var(--sp-2)">
            <a href="${this.#escape(lib.url)}" target="_blank" rel="noopener noreferrer" class="btn btn-secondary btn-sm" style="flex:1;text-align:center">
              <span class="material-symbols-outlined" style="font-size:14px">open_in_new</span> GitHub Repository
            </a>
            <a href="${this.#escape(lib.url)}/releases" target="_blank" rel="noopener noreferrer" class="btn btn-secondary btn-sm" style="flex:1;text-align:center">
              <span class="material-symbols-outlined" style="font-size:14px">new_releases</span> GitHub Releases
            </a>
            <a href="${JitPackClient.getRepoWebUrl(lib.owner, lib.repo)}" target="_blank" rel="noopener noreferrer" class="btn btn-secondary btn-sm" style="flex:1;text-align:center">
              <span class="material-symbols-outlined" style="font-size:14px">terminal</span> JitPack Page
            </a>
          </div>
        </div>
      `,
      buttons: [
        {
          label: 'Refresh Status',
          variant: 'primary',
          action: async () => {
            modal.close();
            toast.info(`Refreshing ${lib.name}…`);
            await ecosystemRepositoryService.refreshLibrary(lib.id, true);
            await this.#reloadAndRender();
            toast.success(`${lib.name} refreshed.`);
          }
        },
        { label: 'Close', variant: 'secondary', action: () => modal.close() }
      ]
    });
  }

  async #showScanGitHubModal() {
    const isAuth = githubAuth.isAuthenticated;
    const defaultUser = isAuth ? githubAuth.user?.login : '';

    modal.show({
      title: 'Scan GitHub for Android Libraries',
      content: `
        <div class="ecosystem-modal-body">
          ${!isAuth ? `
            <div style="padding:var(--sp-2) var(--sp-3);background:rgba(56,189,248,0.08);border:1px solid rgba(56,189,248,0.25);border-radius:var(--radius-md);margin-bottom:var(--sp-3);display:flex;align-items:center;justify-content:space-between;gap:var(--sp-2);font-size:12px">
              <div style="display:flex;align-items:center;gap:6px">
                <span class="material-symbols-outlined" style="color:#38BDF8;font-size:18px">info</span>
                <span>Unauthenticated (60 req/hr). Connect your GitHub Token for private repos & 5,000 req/hr.</span>
              </div>
              <button class="btn btn-sm btn-secondary" id="btn-scan-to-auth" style="font-size:11px;padding:3px 8px">Connect Token</button>
            </div>
          ` : `
            <div style="padding:var(--sp-2) var(--sp-3);background:rgba(16,185,129,0.08);border:1px solid rgba(16,185,129,0.25);border-radius:var(--radius-md);margin-bottom:var(--sp-3);display:flex;align-items:center;gap:6px;font-size:12px;color:var(--text-secondary)">
              <span class="material-symbols-outlined" style="color:#10B981;font-size:18px">verified</span>
              <span>Scanning as authenticated user <strong>@${this.#escape(defaultUser)}</strong> (5,000 req/hr).</span>
            </div>
          `}
          <div style="display:flex;gap:var(--sp-2);align-items:center">
            <input type="text" id="scan-target-user" class="input" placeholder="GitHub username or organization" value="${this.#escape(defaultUser || '')}" style="flex:1" />
            <button class="btn btn-primary" id="btn-run-scan" style="display:inline-flex;align-items:center;gap:4px">
              <span class="material-symbols-outlined">search</span> Scan
            </button>
          </div>
          <div id="scan-results-container" style="min-height:160px;display:flex;flex-direction:column;gap:var(--sp-2)">
            <div style="padding:var(--sp-6);text-align:center;color:var(--text-muted)">
              Enter a GitHub username or organization and click Scan.
            </div>
          </div>
        </div>
      `,
      buttons: [
        {
          label: 'Register Selected',
          variant: 'primary',
          id: 'btn-register-selected',
          disabled: true,
          action: () => {} // Bound dynamically below
        },
        { label: 'Cancel', variant: 'secondary', action: () => modal.close() }
      ]
    });

    const scanToAuthBtn = document.getElementById('btn-scan-to-auth');
    if (scanToAuthBtn) {
      scanToAuthBtn.onclick = () => {
        modal.close();
        this.#showQuickConnectModal();
      };
    }

    const runScanBtn = document.getElementById('btn-run-scan');
    const userInput = document.getElementById('scan-target-user');
    const resultsContainer = document.getElementById('scan-results-container');
    const registerBtn = document.getElementById('btn-register-selected');

    let scannedCandidates = [];

    const doScan = async () => {
      const u = userInput ? userInput.value.trim() : '';
      if (!u) {
        toast.warning('Please enter a username or organization');
        return;
      }
      if (runScanBtn) {
        runScanBtn.disabled = true;
        runScanBtn.innerHTML = '<span class="material-symbols-outlined rotating">sync</span> Scanning…';
      }
      if (resultsContainer) {
        resultsContainer.innerHTML = '<div style="padding:var(--sp-4);text-align:center"><span class="material-symbols-outlined rotating" style="font-size:24px">sync</span><p>Scanning GitHub repositories…</p></div>';
      }

      try {
        const result = await EcosystemScanner.scanRepositories({ username: u, perPage: 50 });
        scannedCandidates = result.candidates;

        if (scannedCandidates.length === 0) {
          if (resultsContainer) resultsContainer.innerHTML = '<div style="padding:var(--sp-4);text-align:center;color:var(--text-muted)">No repositories found.</div>';
          if (registerBtn) registerBtn.disabled = true;
        } else {
          if (resultsContainer) {
            resultsContainer.innerHTML = `
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-2)">
              <span style="font-size:12px;color:var(--text-muted)">Found ${scannedCandidates.length} repositories:</span>
              <label style="font-size:12px;display:inline-flex;align-items:center;gap:4px;cursor:pointer">
                <input type="checkbox" id="check-all-candidates" /> Select All Unregistered
              </label>
            </div>
            <div style="display:flex;flex-direction:column;gap:6px;max-height:280px;overflow-y:auto">
              ${scannedCandidates.map((c, idx) => `
                <div class="candidate-card" style="opacity:${c.isRegistered ? '0.5' : '1'}">
                  <div style="display:flex;align-items:center;gap:var(--sp-2)">
                    <input type="checkbox" class="candidate-check" data-idx="${idx}" ${c.isRegistered ? 'disabled' : (c.isAndroidLibrary ? 'checked' : '')} />
                    <div>
                      <div style="font-weight:600;font-size:13px">${this.#escape(c.repo)}</div>
                      <div style="font-size:10px;color:var(--text-muted)">
                        ${this.#escape(c.language)} • ${c.isRegistered ? 'Already Registered' : (c.isAndroidLibrary ? 'Android Library' : 'General Repo')}
                      </div>
                    </div>
                  </div>
                  <span class="badge" style="font-size:10px">${this.#escape(c.confidence)} confidence</span>
                </div>
              `).join('')}
            </div>
          `;
          }

          if (registerBtn) {
            registerBtn.disabled = false;
            registerBtn.onclick = async () => {
              const checks = document.querySelectorAll('.candidate-check:checked');
              const toAdd = Array.from(checks).map(cb => scannedCandidates[parseInt(cb.dataset.idx, 10)]).filter(Boolean);
              if (toAdd.length === 0) {
                toast.warning('No repositories selected');
                return;
              }
              modal.close();
              toast.info(`Registering ${toAdd.length} repositories…`);
              const res = await EcosystemScanner.batchRegister(toAdd);
              await this.#reloadAndRender();
              toast.success(`Registered ${res.registered} repositories (${res.skipped} skipped).`);
            };
          }

          const checkAll = document.getElementById('check-all-candidates');
          if (checkAll) {
            checkAll.onchange = (e) => {
              document.querySelectorAll('.candidate-check:not(:disabled)').forEach(cb => {
                cb.checked = e.target.checked;
              });
            };
          }
        }
      } catch (err) {
        resultsContainer.innerHTML = `<div style="padding:var(--sp-4);color:var(--error);text-align:center">${this.#escape(err.message)}</div>`;
      } finally {
        runScanBtn.disabled = false;
        runScanBtn.innerHTML = '<span class="material-symbols-outlined">search</span> Scan';
      }
    };

    runScanBtn.onclick = doScan;
    if (defaultUser) doScan();
  }

  #showManualAddModal() {
    modal.show({
      title: 'Register Android Library Manually',
      content: `
        <div class="ecosystem-modal-body">
          <div class="form-group">
            <label class="form-label">GitHub Owner / Organization *</label>
            <input type="text" id="manual-owner" class="input" placeholder="e.g. infoadmstudio2020-cyber" />
          </div>
          <div class="form-group">
            <label class="form-label">Repository Name *</label>
            <input type="text" id="manual-repo" class="input" placeholder="e.g. native-logger-sdk" />
          </div>
          <div class="form-group">
            <label class="form-label">Library Display Name</label>
            <input type="text" id="manual-name" class="input" placeholder="e.g. NativeLogger SDK" />
          </div>
          <div class="form-group">
            <label class="form-label">Maven Group ID</label>
            <input type="text" id="manual-group" class="input" placeholder="e.g. com.github.infoadmstudio2020-cyber" />
          </div>
          <div class="form-group">
            <label class="form-label">Maven Artifact ID</label>
            <input type="text" id="manual-artifact" class="input" placeholder="e.g. native-logger-sdk" />
          </div>
          <div class="form-group">
            <label class="form-label">Ecosystem Category</label>
            <select id="manual-ecosystem-group" class="input">
              ${this.#groups.map(g => `<option value="${this.#escape(g.name)}">${this.#escape(g.name)}</option>`).join('')}
            </select>
          </div>
        </div>
      `,
      buttons: [
        {
          label: 'Register Library',
          variant: 'primary',
          action: async () => {
            const owner = document.getElementById('manual-owner')?.value.trim();
            const repo = document.getElementById('manual-repo')?.value.trim();
            const name = document.getElementById('manual-name')?.value.trim() || repo;
            const groupId = document.getElementById('manual-group')?.value.trim() || `com.github.${owner}`;
            const artifactId = document.getElementById('manual-artifact')?.value.trim() || repo;
            const ecosystemGroup = document.getElementById('manual-ecosystem-group')?.value || 'NativeRuntime Core';

            if (!owner || !repo) {
              toast.error('Owner and Repository name are required.');
              return;
            }

            try {
              await ecosystemRepositoryService.register({
                owner,
                repo,
                name,
                groupId,
                artifactId,
                ecosystemGroup,
                isEcosystem: true,
              });
              modal.close();
              toast.success(`Registered ${name} into ecosystem.`);
              await this.#reloadAndRender();
            } catch (err) {
              toast.error(err.message);
            }
          }
        },
        { label: 'Cancel', variant: 'secondary', action: () => modal.close() }
      ]
    });
  }

  async #showBuildLogModal(owner, repo, version) {
    modal.show({
      title: `JitPack Build Log — ${owner}/${repo} (${version})`,
      content: `
        <div class="ecosystem-modal-body">
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span style="font-size:11px;color:var(--text-muted)">Log Source: JitPack Live Worker</span>
            <a href="${JitPackClient.getBuildLogUrl(owner, repo, version)}" target="_blank" rel="noopener noreferrer" style="font-size:11px;color:var(--accent)">
              Open Raw Log <span class="material-symbols-outlined" style="font-size:11px">open_in_new</span>
            </a>
          </div>
          <div id="jitpack-log-content" class="jitpack-log-box">
            Loading build log from JitPack…
          </div>
        </div>
      `,
      buttons: [{ label: 'Close', variant: 'secondary', action: () => modal.close() }]
    });

    const box = document.getElementById('jitpack-log-content');
    const logRes = await JitPackClient.fetchBuildLog(owner, repo, version);
    if (!box) return;

    if (logRes.success) {
      box.textContent = logRes.log || 'Log is empty.';
    } else {
      box.innerHTML = `<span style="color:#F59E0B">${this.#escape(logRes.errorSnippet || logRes.error || 'Build log not found.')}</span>\n\nDirect URL: ${this.#escape(logRes.logUrl)}`;
    }
  }

  async #showReleasesModal(owner, repo) {
    modal.show({
      title: `GitHub Releases — ${owner}/${repo}`,
      content: `
        <div class="ecosystem-modal-body">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-2)">
            <span style="font-size:12px;color:var(--text-muted)">Live releases queried via GitHub REST API</span>
            <a href="https://github.com/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/releases" target="_blank" rel="noopener noreferrer" style="font-size:12px;color:var(--accent)">
              Open on GitHub <span class="material-symbols-outlined" style="font-size:12px">open_in_new</span>
            </a>
          </div>
          <div id="modal-releases-container" style="min-height:120px;display:flex;flex-direction:column;gap:8px">
            <div style="text-align:center;padding:var(--sp-4);color:var(--text-muted)">
              <span class="material-symbols-outlined rotating">sync</span> Fetching live releases…
            </div>
          </div>
        </div>
      `,
      buttons: [{ label: 'Close', variant: 'secondary', action: () => modal.close() }]
    });

    const container = document.getElementById('modal-releases-container');
    if (!container) return;

    try {
      const releases = await ecosystemRepositoryService.getReleases(owner, repo);
      if (!releases || releases.length === 0) {
        container.innerHTML = '<div style="padding:var(--sp-4);text-align:center;color:var(--text-muted)">No releases found on GitHub.</div>';
        return;
      }

      container.innerHTML = `
        <div style="display:flex;flex-direction:column;gap:8px;max-height:360px;overflow-y:auto">
          ${releases.map(rel => `
            <div style="padding:var(--sp-3);background:var(--bg-muted);border-radius:var(--radius-md);border:1px solid var(--border)">
              <div style="display:flex;justify-content:space-between;align-items:flex-start">
                <div>
                  <span style="font-weight:600;font-size:14px">${this.#escape(rel.name || rel.tag_name)}</span>
                  <span class="version-pill" style="margin-left:6px;color:#38BDF8">${this.#escape(rel.tag_name)}</span>
                  ${rel.prerelease ? '<span class="badge" style="background:#F59E0B;color:#000;font-size:9px;margin-left:4px">Pre-release</span>' : ''}
                  ${rel.draft ? '<span class="badge" style="background:#64748B;color:#FFF;font-size:9px;margin-left:4px">Draft</span>' : ''}
                </div>
                <span style="font-size:11px;color:var(--text-muted)">${new Date(rel.published_at || rel.created_at).toLocaleDateString()}</span>
              </div>
              ${rel.body ? `<div style="font-size:11px;color:var(--text-secondary);margin-top:4px;max-height:60px;overflow-y:auto;white-space:pre-wrap">${this.#escape(rel.body.slice(0, 300))}</div>` : ''}
              ${rel.assets && rel.assets.length > 0 ? `
                <div style="display:flex;flex-wrap:wrap;gap:4px;margin-top:6px">
                  ${rel.assets.map(a => `
                    <a href="${this.#escape(a.browser_download_url)}" target="_blank" rel="noopener noreferrer" class="badge" style="font-size:10px;text-decoration:none">
                      📦 ${this.#escape(a.name)} (${Math.round(a.size / 1024)} KB)
                    </a>
                  `).join('')}
                </div>
              ` : ''}
            </div>
          `).join('')}
        </div>
      `;
    } catch (err) {
      container.innerHTML = `<div style="padding:var(--sp-4);color:var(--error);text-align:center">${this.#escape(err.message)}</div>`;
    }
  }

  async #showTagsModal(owner, repo) {
    modal.show({
      title: `Git Tags — ${owner}/${repo}`,
      content: `
        <div class="ecosystem-modal-body">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-2)">
            <span style="font-size:12px;color:var(--text-muted)">Live Git commit tags from GitHub repository</span>
            <a href="https://github.com/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/tags" target="_blank" rel="noopener noreferrer" style="font-size:12px;color:var(--accent)">
              Open on GitHub <span class="material-symbols-outlined" style="font-size:12px">open_in_new</span>
            </a>
          </div>
          <div id="modal-tags-container" style="min-height:120px;display:flex;flex-direction:column;gap:8px">
            <div style="text-align:center;padding:var(--sp-4);color:var(--text-muted)">
              <span class="material-symbols-outlined rotating">sync</span> Fetching live Git tags…
            </div>
          </div>
        </div>
      `,
      buttons: [{ label: 'Close', variant: 'secondary', action: () => modal.close() }]
    });

    const container = document.getElementById('modal-tags-container');
    if (!container) return;

    try {
      const tags = await ecosystemRepositoryService.getTags(owner, repo);
      if (!tags || tags.length === 0) {
        container.innerHTML = '<div style="padding:var(--sp-4);text-align:center;color:var(--text-muted)">No Git tags found on GitHub.</div>';
        return;
      }

      container.innerHTML = `
        <div style="display:flex;flex-direction:column;gap:6px;max-height:360px;overflow-y:auto">
          ${tags.map(t => `
            <div style="display:flex;justify-content:space-between;align-items:center;padding:var(--sp-2) var(--sp-3);background:var(--bg-muted);border-radius:var(--radius-md);border:1px solid var(--border)">
              <div>
                <span class="version-pill" style="font-weight:600">${this.#escape(t.name)}</span>
                <span style="font-family:monospace;font-size:11px;color:var(--text-muted);margin-left:8px">SHA: ${this.#escape((t.commit?.sha || '').slice(0, 7))}</span>
              </div>
              <div style="display:flex;gap:6px">
                <a href="${this.#escape(t.zipball_url)}" target="_blank" rel="noopener noreferrer" class="btn btn-sm btn-secondary" title="Download ZIP">
                  ZIP
                </a>
                <a href="${this.#escape(t.tarball_url)}" target="_blank" rel="noopener noreferrer" class="btn btn-sm btn-secondary" title="Download TAR">
                  TAR
                </a>
              </div>
            </div>
          `).join('')}
        </div>
      `;
    } catch (err) {
      container.innerHTML = `<div style="padding:var(--sp-4);color:var(--error);text-align:center">${this.#escape(err.message)}</div>`;
    }
  }

  async #showJitPackModal(owner, repo, targetVer) {
    modal.show({
      title: `JitPack Build Status — ${owner}/${repo}`,
      content: `
        <div class="ecosystem-modal-body">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-2)">
            <span style="font-size:12px;color:var(--text-muted)">Coordinates: <code>com.github.${this.#escape(owner)}:${this.#escape(repo)}</code></span>
            <a href="${JitPackClient.getRepoWebUrl(owner, repo)}" target="_blank" rel="noopener noreferrer" style="font-size:12px;color:var(--accent)">
              Open JitPack <span class="material-symbols-outlined" style="font-size:12px">open_in_new</span>
            </a>
          </div>
          <div id="modal-jitpack-container" style="min-height:120px;display:flex;flex-direction:column;gap:8px">
            <div style="text-align:center;padding:var(--sp-4);color:var(--text-muted)">
              <span class="material-symbols-outlined rotating">sync</span> Querying JitPack build API…
            </div>
          </div>
        </div>
      `,
      buttons: [{ label: 'Close', variant: 'secondary', action: () => modal.close() }]
    });

    const container = document.getElementById('modal-jitpack-container');
    if (!container) return;

    try {
      const jp = await JitPackClient.getBuilds(owner, repo);
      const versions = jp.allVersions || Object.keys(jp.statusList || {});

      if (versions.length === 0) {
        container.innerHTML = `
          <div style="padding:var(--sp-4);text-align:center;color:var(--text-muted)">
            <p>No builds recorded on JitPack for this repository yet.</p>
            <p style="font-size:11px;margin-top:6px">JitPack builds on demand when a release tag is requested by Gradle.</p>
          </div>
        `;
        return;
      }

      container.innerHTML = `
        <table class="ecosystem-table" style="font-size:12px">
          <thead>
            <tr>
              <th>Version / Tag</th>
              <th>Status</th>
              <th style="text-align:right">Build Log</th>
            </tr>
          </thead>
          <tbody>
            ${versions.map(v => {
              const status = jp.statusList[v] || 'unknown';
              const isOk = status === 'ok' || status === 'success';
              const isErr = status === 'error' || status === 'failed';
              return `
                <tr>
                  <td style="font-weight:600;font-family:monospace">${this.#escape(v)}</td>
                  <td>
                    <span class="badge-status ${isOk ? 'success' : (isErr ? 'error' : 'muted')}">
                      <span class="material-symbols-outlined" style="font-size:12px">
                        ${isOk ? 'check_circle' : (isErr ? 'cancel' : 'help')}
                      </span>
                      ${this.#escape(status)}
                    </span>
                  </td>
                  <td style="text-align:right">
                    <button class="btn btn-sm btn-secondary btn-view-build-log" data-owner="${this.#escape(owner)}" data-repo="${this.#escape(repo)}" data-ver="${this.#escape(v)}">
                      View Log
                    </button>
                  </td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      `;

      container.querySelectorAll('.btn-view-build-log').forEach(btn => {
        btn.onclick = () => {
          this.#showBuildLogModal(btn.dataset.owner, btn.dataset.repo, btn.dataset.ver);
        };
      });
    } catch (err) {
      container.innerHTML = `<div style="padding:var(--sp-4);color:var(--error);text-align:center">${this.#escape(err.message)}</div>`;
    }
  }

  async #showDependenciesModal(id) {
    const lib = await ecosystemRepositoryService.get(id);
    if (!lib) return;

    modal.show({
      title: `${lib.name} — Declared Dependencies`,
      content: `
        <div class="ecosystem-modal-body">
          <div style="font-size:12px;color:var(--text-muted);margin-bottom:var(--sp-2)">
            Parsed from remote Gradle build scripts (build.gradle, build.gradle.kts, gradle.properties, libs.versions.toml)
          </div>
          ${lib.dependencies && lib.dependencies.length > 0 ? `
            <table class="ecosystem-table" style="font-size:12px">
              <thead>
                <tr>
                  <th>Dependency Coordinates</th>
                  <th>Scope</th>
                  <th>Declared Version</th>
                </tr>
              </thead>
              <tbody>
                ${lib.dependencies.map(d => `
                  <tr>
                    <td style="font-family:monospace">
                      <span style="font-weight:600">${this.#escape(d.groupId || '')}</span>:${this.#escape(d.artifactId || d.name)}
                    </td>
                    <td><span class="badge" style="font-size:10px">${this.#escape(d.scope || 'implementation')}</span></td>
                    <td><span class="version-pill">${this.#escape(d.version || 'unknown')}</span></td>
                  </tr>
                `).join('')}
              </tbody>
            </table>
          ` : `
            <div style="padding:var(--sp-6);text-align:center;color:var(--text-muted)">
              No external dependencies detected in remote build configuration.
            </div>
          `}
        </div>
      `,
      buttons: [
        {
          label: 'Re-inspect Build Files',
          variant: 'primary',
          action: async () => {
            modal.close();
            toast.info(`Inspecting build files for ${lib.name}…`);
            await ecosystemRepositoryService.refreshLibrary(lib.id, true);
            await this.#reloadAndRender();
            toast.success(`Inspection completed for ${lib.name}.`);
          }
        },
        { label: 'Close', variant: 'secondary', action: () => modal.close() }
      ]
    });
  }

  #showQuickConnectModal() {
    modal.show({
      title: 'Connect GitHub Personal Access Token',
      content: `
        <div class="ecosystem-modal-body">
          <p style="font-size:13px;color:var(--text-secondary);margin:0 0 var(--sp-3) 0">
            Enter your GitHub Personal Access Token to connect your GitHub account, unlock 5,000 API requests/hour, and enable real repository scanning.
          </p>
          <div class="form-group" style="margin-bottom:var(--sp-3)">
            <label class="form-label" style="font-size:12px;font-weight:600">Personal Access Token (PAT)</label>
            <input type="password" id="modal-quick-pat" class="input" placeholder="ghp_xxxxxxxxxxxxxxxxxxxx or github_pat_..." style="font-family:monospace;font-size:12px" />
            <span style="font-size:11px;color:var(--text-muted);margin-top:4px;display:block">
              Required scopes: <code>repo</code>, <code>read:packages</code>. Stored securely only in your browser's IndexedDB.
            </span>
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center;padding:var(--sp-2) var(--sp-3);background:var(--bg-muted);border-radius:var(--radius-md);font-size:11px">
            <span>Don't have a token yet?</span>
            <a href="https://github.com/settings/tokens/new?scopes=repo,read:packages&description=RONi-LibGen" target="_blank" rel="noopener noreferrer" style="color:var(--accent);font-weight:600">
              Generate on GitHub <span class="material-symbols-outlined" style="font-size:11px">open_in_new</span>
            </a>
          </div>
        </div>
      `,
      buttons: [
        {
          label: 'Connect & Authenticate',
          variant: 'primary',
          id: 'btn-modal-do-connect',
          action: async () => {
            const tokenInput = document.getElementById('modal-quick-pat');
            const token = tokenInput ? tokenInput.value.trim() : '';
            if (!token) {
              toast.warning('Please enter a Personal Access Token');
              return;
            }
            try {
              toast.info('Verifying token with GitHub…');
              const user = await githubAuth.loginWithToken(token);
              modal.close();
              toast.success(`Authenticated as @${user.login}! Live real-mode activated.`);
              await this.#reloadAndRender();
            } catch (err) {
              toast.error(`Authentication failed: ${err.message}`);
            }
          }
        },
        { label: 'Cancel', variant: 'secondary', action: () => modal.close() }
      ]
    });
  }

  async #importUserRepositories() {
    if (!githubAuth.user) {
      toast.warning('Please connect your GitHub account first.');
      this.#showQuickConnectModal();
      return;
    }
    toast.info(`Scanning GitHub repositories for @${githubAuth.user.login}…`);
    try {
      const res = await EcosystemScanner.scanRepositories({ username: githubAuth.user.login, perPage: 100 });
      if (!res.candidates || res.candidates.length === 0) {
        toast.info(`No repositories found under @${githubAuth.user.login}. You can add one manually.`);
        this.#showManualAddModal();
        return;
      }
      // Purge dummy placeholder libraries and register all discovered repos
      await ecosystemRepositoryService.removeDummyLibraries();
      const reg = await EcosystemScanner.batchRegister(res.candidates);
      toast.success(`Imported ${reg.registered} repositories from @${githubAuth.user.login}! (${reg.skipped} already registered).`);
      await this.#reloadAndRender();
      ecosystemRepositoryService.refreshAll().then(() => this.#reloadAndRender());
    } catch (e) {
      toast.error(`Import failed: ${e.message}`);
    }
  }

  // ── Event Binding ─────────────────────────────────────────────────────────

  #bindEvents() {
    if (!this.#container) return;

    // Authentication actions
    this.#container.querySelectorAll('.btn-goto-auth-page').forEach(btn => {
      btn.onclick = () => {
        eventBus.emit('router:navigate', { pageId: 'gh-auth' });
        window.location.hash = '#gh-auth';
      };
    });

    const quickConnectBtn = this.#container.querySelector('#btn-quick-connect-token');
    if (quickConnectBtn) {
      quickConnectBtn.onclick = () => this.#showQuickConnectModal();
    }

    const disconnectBtn = this.#container.querySelector('#btn-disconnect-token');
    if (disconnectBtn) {
      disconnectBtn.onclick = async () => {
        await githubAuth.logout();
        toast.info('Disconnected GitHub account.');
        await this.#reloadAndRender();
      };
    }

    // Import My Repos actions
    const importMyBtns = this.#container.querySelectorAll('#btn-import-my-repos, #btn-empty-import-my');
    importMyBtns.forEach(btn => {
      btn.onclick = () => this.#importUserRepositories();
    });

    // Remove dummy libraries
    const removeDummyBtn = this.#container.querySelector('#btn-remove-dummy');
    if (removeDummyBtn) {
      removeDummyBtn.onclick = async () => {
        const removed = await ecosystemRepositoryService.removeDummyLibraries();
        toast.success(`Removed ${removed} dummy placeholder libraries.`);
        await this.#reloadAndRender();
      };
    }

    // Load real Android samples (Retrofit, Gson, OkHttp)
    const loadSamplesBtns = this.#container.querySelectorAll('#btn-load-real-samples, #btn-empty-real-samples');
    loadSamplesBtns.forEach(btn => {
      btn.onclick = async () => {
        toast.info('Loading verified Android libraries (Retrofit, Gson, OkHttp)…');
        await ecosystemRepositoryService.loadRealSampleLibraries();
        toast.success('Loaded verified Android libraries. Syncing live releases…');
        await this.#reloadAndRender();
        ecosystemRepositoryService.refreshAll().then(() => this.#reloadAndRender());
      };
    });

    const emptyScanBtn = this.#container.querySelector('#btn-empty-scan');
    if (emptyScanBtn) {
      emptyScanBtn.onclick = () => this.#showScanGitHubModal();
    }

    // Tab buttons
    this.#container.querySelectorAll('.ecosystem-tab-btn').forEach(btn => {
      btn.onclick = () => {
        this.#activeTab = btn.dataset.tab;
        this.#render();
      };
    });

    // Action buttons in header
    const btnScan = this.#container.querySelector('#btn-scan-github');
    if (btnScan) btnScan.onclick = () => this.#showScanGitHubModal();

    const btnAdd = this.#container.querySelector('#btn-add-manual');
    if (btnAdd) btnAdd.onclick = () => this.#showManualAddModal();

    const btnRefreshAll = this.#container.querySelector('#btn-refresh-all');
    if (btnRefreshAll) {
      btnRefreshAll.onclick = async () => {
        this.#isRefreshing = true;
        this.#render();
        toast.info('Refreshing all ecosystem libraries…');
        try {
          await ecosystemRepositoryService.refreshAll();
          toast.success('All libraries refreshed successfully.');
        } catch (e) {
          toast.error(`Refresh error: ${e.message}`);
        } finally {
          this.#isRefreshing = false;
          await this.#reloadAndRender();
        }
      };
    }

    // Filter controls in dashboard tab
    const searchInput = this.#container.querySelector('#filter-search');
    if (searchInput) {
      searchInput.oninput = (e) => {
        this.#filters.search = e.target.value;
        this.#debounceFilter();
      };
    }

    const ownerSelect = this.#container.querySelector('#filter-owner');
    if (ownerSelect) {
      ownerSelect.onchange = (e) => {
        this.#filters.owner = e.target.value;
        this.#reloadAndRender();
      };
    }

    const statusSelect = this.#container.querySelector('#filter-status');
    if (statusSelect) {
      statusSelect.onchange = (e) => {
        this.#filters.status = e.target.value;
        this.#reloadAndRender();
      };
    }

    const groupSelect = this.#container.querySelector('#filter-group');
    if (groupSelect) {
      groupSelect.onchange = (e) => {
        this.#filters.group = e.target.value;
        this.#reloadAndRender();
      };
    }

    const sortSelect = this.#container.querySelector('#sort-by');
    if (sortSelect) {
      sortSelect.onchange = (e) => {
        this.#filters.sortBy = e.target.value;
        this.#reloadAndRender();
      };
    }

    const sortDirBtn = this.#container.querySelector('#btn-toggle-sort');
    if (sortDirBtn) {
      sortDirBtn.onclick = () => {
        this.#filters.sortDir = this.#filters.sortDir === 'asc' ? 'desc' : 'asc';
        this.#reloadAndRender();
      };
    }

    // Row Actions
    this.#container.querySelectorAll('.btn-row-details').forEach(btn => {
      btn.onclick = () => this.#showLibraryDetails(btn.dataset.id);
    });

    this.#container.querySelectorAll('.btn-row-releases, .btn-click-releases').forEach(btn => {
      btn.onclick = (e) => {
        e.stopPropagation();
        this.#showReleasesModal(btn.dataset.owner, btn.dataset.repo);
      };
    });

    this.#container.querySelectorAll('.btn-row-tags, .btn-click-tags').forEach(btn => {
      btn.onclick = (e) => {
        e.stopPropagation();
        this.#showTagsModal(btn.dataset.owner, btn.dataset.repo);
      };
    });

    this.#container.querySelectorAll('.btn-row-jitpack, .btn-click-jitpack').forEach(btn => {
      btn.onclick = (e) => {
        e.stopPropagation();
        this.#showJitPackModal(btn.dataset.owner, btn.dataset.repo, btn.dataset.ver);
      };
    });

    this.#container.querySelectorAll('.btn-row-deps, .btn-click-deps').forEach(btn => {
      btn.onclick = (e) => {
        e.stopPropagation();
        this.#showDependenciesModal(btn.dataset.id);
      };
    });

    this.#container.querySelectorAll('.btn-row-refresh').forEach(btn => {
      btn.onclick = async () => {
        const id = btn.dataset.id;
        toast.info('Refreshing library status…');
        await ecosystemRepositoryService.refreshLibrary(id, true);
        await this.#reloadAndRender();
        toast.success('Library refreshed.');
      };
    });

    this.#container.querySelectorAll('.btn-row-remove').forEach(btn => {
      btn.onclick = async () => {
        const id = btn.dataset.id;
        modal.show({
          title: 'Remove Library from Monitor?',
          content: '<p>Are you sure you want to remove this repository from your ecosystem dashboard? This will not delete the repository on GitHub.</p>',
          buttons: [
            {
              label: 'Remove',
              variant: 'danger',
              action: async () => {
                await ecosystemRepositoryService.remove(id);
                modal.close();
                toast.success('Library removed from monitor.');
                await this.#reloadAndRender();
              }
            },
            { label: 'Cancel', variant: 'secondary', action: () => modal.close() }
          ]
        });
      };
    });

    this.#container.querySelectorAll('.btn-view-build-log').forEach(btn => {
      btn.onclick = () => {
        this.#showBuildLogModal(btn.dataset.owner, btn.dataset.repo, btn.dataset.ver);
      };
    });
  }

  #filterTimeout = null;
  #debounceFilter() {
    clearTimeout(this.#filterTimeout);
    this.#filterTimeout = setTimeout(() => this.#reloadAndRender(), 200);
  }

  #escape(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }
}
