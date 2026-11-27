/**
 * GHPublishPage — one-click full publish workflow with live progress.
 */
import { githubAuth }        from '../modules/github/GitHubAuth.js';
import { githubRepository }  from '../modules/github/GitHubRepository.js';
import { githubSettings }    from '../modules/github/GitHubSettings.js';
import { githubPublisher }   from '../modules/github/GitHubPublisher.js';
import { githubValidator }   from '../modules/github/GitHubValidator.js';
import { projectService }    from '../modules/storage/ProjectService.js';
import { eventBus }          from '../core/EventBus.js';
import { GitHubUI }          from '../modules/github/GitHubUI.js';
import { toast }             from '../ui/ToastService.js';
import { consoleService }    from '../modules/storage/ConsoleService.js';
import { PublishValidationWizard } from '../modules/environment/PublishValidationWizard.js';

export class GHPublishPage {
  #container = null;
  #steps = [];
  #unsubscribers = [];

  async init(container) {
    this.#container = container;
    await githubSettings.load();
    this.#render();
  }

  #render() {
    const container = this.#container;
    const proj   = projectService.current;
    const authed = githubAuth.isAuthenticated;
    const repo   = githubRepository.activeRepo;
    const s      = githubSettings.get();
    const owner  = s.defaultUsername || githubAuth.user?.login || '';
    const version = proj?.version || '1.0.0';
    const branch  = s.defaultBranch || 'main';
    const repoName = repo?.name || proj?.repoName || '';

    const report = githubValidator.validate();

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title">
            <span class="material-symbols-outlined">rocket_launch</span>Publish to GitHub
          </h1>
          <p class="page-subtitle">One-click: validate → generate files → create repo → upload → commit → tag → release</p>
        </div>

        <!-- Validation summary -->
        ${report.errors.length ? report.errors.map(e => GitHubUI.alert('error', e)).join('') : ''}
        ${report.warnings.length ? `
          <div class="gh-warnings">
            ${report.warnings.map(w => GitHubUI.alert('warning', w)).join('')}
          </div>` : ''}
        ${report.passed ? GitHubUI.alert('success', 'Project is ready to publish!') : ''}

        <div class="gh-two-col gh-publish-config-row">

          <!-- Publish config -->
          ${GitHubUI.card('tune', 'Publish Configuration', `
            ${GitHubUI.field('ghp-owner','GitHub Owner','text',owner,'username','Your GitHub username or organization',true)}
            ${GitHubUI.field('ghp-repo','Repository Name','text',repoName,'my-library','Leave blank to use project setting',true)}
            ${GitHubUI.field('ghp-version','Version','text',version,'1.0.0','Semantic version for tag and release',true)}
            ${GitHubUI.field('ghp-branch','Default Branch','text',branch,'main','')}
            ${GitHubUI.field('ghp-commit-msg','Commit Message','text',`feat: initial release v${version}`,'','')}
            ${GitHubUI.textarea('ghp-release-notes','Release Notes','','Auto-generated from commits…',3)}
            <div class="gh-toggle-group">
              ${GitHubUI.toggle('ghp-generate-files','Generate GitHub files (README, LICENSE, workflows)', s.autoReadme !== false)}
              ${GitHubUI.toggle('ghp-create-release','Create GitHub Release after push', s.autoRelease !== false)}
              ${GitHubUI.toggle('ghp-use-existing','Use existing repository (skip creation)', !!repo)}
            </div>
          `)}

          <!-- Progress panel -->
          <div>
            ${GitHubUI.card('terminal', 'Publish Progress', `
              <div class="progress-bar" id="ghp-progress-bar">
                <div class="progress-bar-fill" id="ghp-progress-fill" style="width:0%"></div>
              </div>
              <div id="ghp-status-text" class="ghp-status-text">
                Ready to publish
              </div>
              <div class="gh-progress-steps" id="ghp-steps">
                ${this.#renderSteps([])}
              </div>
            `)}

            ${GitHubUI.card('check_circle', 'Result', `
              <div id="ghp-result">${GitHubUI.empty('rocket_launch','Publish result will appear here','')}</div>
            `)}
          </div>
        </div>

        <!-- Publish / Abort buttons -->
        <div class="gh-publish-actions">
          <button class="btn btn-primary btn-lg" id="ghp-publish-btn" ${!report.passed ? 'disabled' : ''}>
            <span class="material-symbols-outlined">rocket_launch</span>Publish to GitHub
          </button>
          <button type="button" class="btn btn-danger" id="ghp-abort-btn" hidden>
            <span class="material-symbols-outlined">stop_circle</span>Abort
          </button>
          <button class="btn btn-ghost" id="ghp-validate-btn">
            <span class="material-symbols-outlined">fact_check</span>Re-validate
          </button>
        </div>
      </div>`;

    this.#bindEvents();
  }

  #renderSteps(steps) {
    const stepDefs = [
      { id: 'validate',   icon: 'fact_check',    label: 'Validate project' },
      { id: 'gen-files',  icon: 'auto_fix_high', label: 'Generate GitHub files' },
      { id: 'repo',       icon: 'folder_special',label: 'Create / connect repository' },
      { id: 'upload',     icon: 'cloud_upload',  label: 'Upload files' },
      { id: 'tag',        icon: 'sell',          label: 'Create version tag' },
      { id: 'release',    icon: 'new_releases',  label: 'Create release' },
      { id: 'history',    icon: 'history',       label: 'Record history' },
    ];
    const stateMap = {};
    steps.forEach(s => { stateMap[s.id] = s.state; });
    return stepDefs.map(s => GitHubUI.progressStep(s.icon, s.label, stateMap[s.id] || 'pending')).join('');
  }

  #bindEvents() {
    const c = this.#container;

    c.querySelector('#ghp-validate-btn')?.addEventListener('click', () => this.#render());

    c.querySelector('#ghp-abort-btn')?.addEventListener('click', () => {
      githubPublisher.abort();
      toast.warn('Aborting…', 'Publish will stop at next checkpoint');
    });

    c.querySelector('#ghp-publish-btn')?.addEventListener('click', async () => {
      const owner  = c.querySelector('#ghp-owner')?.value?.trim();
      const repo   = c.querySelector('#ghp-repo')?.value?.trim();
      const ver    = c.querySelector('#ghp-version')?.value?.trim();
      const branch = c.querySelector('#ghp-branch')?.value?.trim() || 'main';
      const msg    = c.querySelector('#ghp-commit-msg')?.value?.trim();
      const notes  = c.querySelector('#ghp-release-notes')?.value?.trim();
      const genFiles    = c.querySelector('#ghp-generate-files')?.checked !== false;
      const createRel   = c.querySelector('#ghp-create-release')?.checked !== false;
      const useExisting = c.querySelector('#ghp-use-existing')?.checked || false;

      if (!owner || !repo || !ver) { toast.error('Required', 'Owner, repository, and version are required'); return; }

      // Mandatory Pre-Publish 7-step Validation Wizard Check
      await PublishValidationWizard.open({
        onPublish: async () => {
          await this.#executePublish({
            owner, repo, ver, branch, msg, notes, genFiles, createRel, useExisting
          });
        }
      });
    });
  }

  async #executePublish({ owner, repo, ver, branch, msg, notes, genFiles, createRel, useExisting }) {
    const c = this.#container;
    const publishBtn = c.querySelector('#ghp-publish-btn');
    const abortBtn   = c.querySelector('#ghp-abort-btn');
    if (publishBtn) publishBtn.hidden = true;
    if (abortBtn) abortBtn.hidden   = false;
    this.#setStatus('Starting publish…', 0);

      // Subscribe to events
      this.#clearListeners();
      this.#unsubscribers.push(
        eventBus.on('github:publishStep', ({ step, detail }) => {
          this.#setStatus(`${step}${detail ? ': ' + detail : ''}`, null);
          this.#updateStepInProgress(step);
        }),
        eventBus.on('github:publishProgress', ({ pct }) => {
          this.#setStatus(null, pct);
        }),
        eventBus.on('github:publishDone', ({ repoUrl, version: v, tag, skippedWorkflows }) => {
          this.#clearListeners();
          publishBtn.hidden = false;
          abortBtn.hidden   = true;
          this.#showResult(true, repoUrl, v, tag, skippedWorkflows);
          if (skippedWorkflows) {
            toast.warning('Published (Workflows Skipped)', 'Published successfully! Note: GitHub Actions workflows were skipped because your token lacks the "workflow" scope.');
          } else {
            toast.success('Published!', repoUrl);
          }
        }),
        eventBus.on('github:publishError', ({ error }) => {
          this.#clearListeners();
          publishBtn.hidden = false;
          abortBtn.hidden   = true;
          this.#showResult(false, '', '', '', error);
          toast.error('Publish failed', error);
        }),
      );

      try {
        await githubPublisher.publish({
          owner, repoName: repo, version: ver, branch,
          commitMessage: msg, releaseNotes: notes,
          createRelease: createRel, generateFiles: genFiles,
          useExistingRepo: useExisting,
          description: projectService.current?.description || '',
          isPrivate: false,
        });
      } catch (e) {
        // Error already handled via event
        consoleService.error(`Publish error: ${e.message}`);
      }
  }

  #setStatus(text, pct) {
    const statusEl = this.#container?.querySelector('#ghp-status-text');
    const fillEl   = this.#container?.querySelector('#ghp-progress-fill');
    if (text && statusEl) statusEl.textContent = text;
    if (pct !== null && fillEl) fillEl.style.width = `${pct}%`;
  }

  #updateStepInProgress(stepText) {
    const stepsEl = this.#container?.querySelector('#ghp-steps');
    if (!stepsEl) return;
    const lower = stepText.toLowerCase();
    const stepMap = {
      'validat': 'validate',
      'generat': 'gen-files',
      'creating repository': 'repo',
      'uploading': 'upload',
      'creating tag': 'tag',
      'creating release': 'release',
      'finaliz': 'history',
    };
    const matchedId = Object.entries(stepMap).find(([k]) => lower.includes(k))?.[1];
    if (matchedId) {
      const row = stepsEl.querySelector(`.gh-progress-step`);
    }
    // Re-render steps with current active
    stepsEl.innerHTML = this.#renderSteps([]);
  }

  #showResult(success, url, version, tag, skippedWorkflows = false, err = '') {
    const el = this.#container?.querySelector('#ghp-result');
    if (!el) return;
    if (success) {
      const s = githubSettings.get();
      const owner = s.defaultUsername || githubAuth.user?.login || '';
      const repo = githubRepository.activeRepo?.name || projectService.current?.repoName || '';
      const jitpackUrl = `https://jitpack.io/#${encodeURIComponent(owner || 'user')}/${encodeURIComponent(repo || 'repo')}/${encodeURIComponent(tag)}`;

      el.innerHTML = `
        <div class="gh-publish-success">
          <span class="material-symbols-outlined ghp-success-icon">check_circle</span>
          <div class="gh-publish-success-text">Published successfully!</div>
          <div class="gh-publish-success-version">${version} — ${tag}</div>
          <div style="display:flex;gap:8px;justify-content:center;margin-top:12px;flex-wrap:wrap">
            <a href="${url}" target="_blank" rel="noopener" class="btn btn-secondary gh-publish-open-btn">
              <span class="material-symbols-outlined">open_in_new</span>Open Repository
            </a>
            <a href="${jitpackUrl}" target="_blank" rel="noopener" class="btn btn-secondary" title="View on JitPack">
              <span class="material-symbols-outlined">public</span>View on JitPack
            </a>
            <button type="button" class="btn btn-primary" id="ghp-inspect-jitpack-btn">
              <span class="material-symbols-outlined">monitoring</span>Inspect JitPack Build &amp; Logs
            </button>
          </div>
          <div class="gh-publish-url">${url}</div>
          ${skippedWorkflows ? `
            <div style="margin-top:10px;font-size:12px;color:var(--text-warning);background:rgba(234,179,8,0.1);padding:8px 12px;border-radius:6px;border:1px solid rgba(234,179,8,0.3);text-align:left">
              <strong>Note:</strong> GitHub Actions workflows (<code>.github/workflows/</code>) were omitted because your token lacks the <code>workflow</code> OAuth scope. All library source code, gradle files, and configurations were published successfully.
            </div>` : ''}
        </div>`;

      // Wire up direct navigation to JitPack Monitor
      el.querySelector('#ghp-inspect-jitpack-btn')?.addEventListener('click', () => {
        eventBus.emit('router:navigate', {
          pageId: 'jitpack',
          params: { activeTab: 'monitor', owner, repo, tag }
        });
      });
    } else {
      el.innerHTML = GitHubUI.alert('error', `Publish failed: ${err}`);
    }
  }

  #clearListeners() {
    this.#unsubscribers.forEach(fn => typeof fn === 'function' && fn());
    this.#unsubscribers = [];
  }
}
