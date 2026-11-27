/**
 * GHTagsPage — version tag management.
 */
import { githubAuth }       from '../modules/github/GitHubAuth.js';
import { githubRepository } from '../modules/github/GitHubRepository.js';
import { githubTag }        from '../modules/github/GitHubTag.js';
import { githubSettings }   from '../modules/github/GitHubSettings.js';
import { projectService }   from '../modules/storage/ProjectService.js';
import { GitHubUI }         from '../modules/github/GitHubUI.js';
import { modal }            from '../ui/ModalService.js';
import { toast }            from '../ui/ToastService.js';
import { consoleService }   from '../modules/storage/ConsoleService.js';
import { eventBus }         from '../core/EventBus.js';

export class GHTagsPage {
  async init(container) {
    await githubSettings.load();
    this.#render(container);
  }

  #render(container) {
    if (!githubAuth.isAuthenticated) {
      container.innerHTML = `<div class="page-container">${GitHubUI.alert('warning',
        'Please authenticate first. Go to <strong>GitHub › Authentication</strong>.')}</div>`;
      return;
    }
    const repo    = githubRepository.activeRepo;
    const proj    = projectService.current;
    const version = proj?.version || '1.0.0';
    const incPatch = githubTag.increment(version, 'patch');
    const incMinor = githubTag.increment(version, 'minor');
    const incMajor = githubTag.increment(version, 'major');

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title">
            <span class="material-symbols-outlined">sell</span>Version Tags
          </h1>
          <p class="page-subtitle">Create and manage semantic version tags on your repository</p>
        </div>

        ${!repo ? GitHubUI.alert('warning','No repository selected. Go to GitHub › Repository first.') : ''}

        <div class="gh-two-col">
          ${GitHubUI.card('new_label', 'Create Tag', `
            <div id="ght-status-area"></div>
            ${GitHubUI.field('ght-version','Version','text', version,'1.0.0','Semantic version — e.g. 1.2.3 (v prefix added automatically)',true)}
            <div class="gh-version-shortcuts" id="ght-shortcuts">
              <span style="font-size:var(--text-xs);color:var(--text-muted)">Quick increment:</span>
              <button type="button" class="btn btn-ghost btn-sm gh-ver-quick" data-v="${incPatch}">patch → v${incPatch}</button>
              <button type="button" class="btn btn-ghost btn-sm gh-ver-quick" data-v="${incMinor}">minor → v${incMinor}</button>
              <button type="button" class="btn btn-ghost btn-sm gh-ver-quick" data-v="${incMajor}">major → v${incMajor}</button>
            </div>
            ${GitHubUI.textarea('ght-message','Tag Message',`Release v${version}`,'Annotation message',2)}
            <div class="gh-action-row" style="margin-top:var(--sp-3)">
              <button type="button" class="btn btn-primary" id="ght-create-btn" ${!repo ? 'disabled' : ''}>
                <span class="material-symbols-outlined">new_label</span>Create Tag
              </button>
              <button type="button" class="btn btn-secondary" id="ght-list-btn" ${!repo ? 'disabled' : ''}>
                <span class="material-symbols-outlined">list</span>Load Tags
              </button>
            </div>
          `)}

          ${GitHubUI.card('sell', 'Existing Tags', `
            <div id="ght-tags-list">${GitHubUI.empty('sell','No tags loaded','Click "Load Tags" to fetch',)}</div>
          `)}
        </div>
      </div>`;

    this.#bindEvents(container);
  }

  #bindEvents(container) {
    const statusArea = container.querySelector('#ght-status-area');
    const versionInput = container.querySelector('#ght-version');
    const msgInput = container.querySelector('#ght-message');
    const createBtn = container.querySelector('#ght-create-btn');
    const listBtn = container.querySelector('#ght-list-btn');
    const quickBtns = container.querySelectorAll('.gh-ver-quick');

    const setFormDisabled = (disabled) => {
      if (createBtn) createBtn.disabled = disabled;
      if (listBtn) listBtn.disabled = disabled;
      if (versionInput) versionInput.disabled = disabled;
      if (msgInput) msgInput.disabled = disabled;
      quickBtns.forEach(b => { b.disabled = disabled; });
    };

    quickBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        if (versionInput) {
          versionInput.value = btn.dataset.v;
          versionInput.dispatchEvent(new Event('input'));
        }
      });
    });

    createBtn?.addEventListener('click', async () => {
      const repo    = githubRepository.activeRepo;
      const version = versionInput?.value?.trim();
      const msg     = msgInput?.value?.trim() || `Release v${version}`;
      if (!repo || !version) {
        toast.error('Required', 'Enter a version number');
        if (statusArea) {
          statusArea.innerHTML = `
            <div class="ght-error-box" role="alert">
              <span class="material-symbols-outlined">error</span>
              <span>Please enter a valid version number (e.g. 1.0.0).</span>
            </div>`;
        }
        return;
      }

      // Immediately disable action and set live loading state
      setFormDisabled(true);
      createBtn.innerHTML = '<span class="material-symbols-outlined spin">sync</span>Creating…';
      if (statusArea) {
        statusArea.innerHTML = `
          <div class="ght-loading-box" role="status" aria-live="polite">
            <span class="material-symbols-outlined spin">sync</span>
            <div class="ght-loading-text">
              <strong>Creating GitHub tag, please wait...</strong>
              <div style="font-size:var(--text-xs);color:var(--text-muted)">Creating tag ref for v${version} on ${repo.name}</div>
            </div>
          </div>`;
      }

      try {
        const ref = await import('../modules/github/GitHubApi.js');
        const { githubApi } = ref;
        const s = githubSettings.get();
        const branch = s.defaultBranch || 'main';
        let sha;
        try {
          const refData = await githubApi.getRef(repo.owner.login, repo.name, branch);
          sha = refData.object.sha;
        } catch (e) {
          throw new Error('No commits yet. Push project files to the repository before creating a tag.');
        }

        // Real tag creation
        const tag = await githubTag.create(repo.owner.login, repo.name, version, sha, msg);
        toast.success('Tag created', tag);
        consoleService.success(`GitHub: created tag ${tag} pointing to commit ${sha.slice(0, 7)}`);

        // Success state
        if (statusArea) {
          statusArea.innerHTML = `
            <div class="ght-success-box" role="alert" aria-live="polite">
              <div class="ght-status-header">
                <span class="material-symbols-outlined" style="color:var(--success);font-size:22px">check_circle</span>
                <strong style="color:var(--success);font-size:var(--text-sm)">✓ Tag created successfully</strong>
              </div>
              <div class="ght-status-meta" style="margin:var(--sp-2) 0;font-size:var(--text-xs);color:var(--text-secondary)">
                Tag: <code class="tag-badge">${tag}</code> &bull; Target commit: <code>${sha.slice(0, 7)}</code>
              </div>
              <div class="ght-status-actions" style="display:flex;gap:var(--sp-2);margin-top:var(--sp-2);flex-wrap:wrap">
                <button type="button" class="btn btn-primary btn-sm" id="ght-goto-publish-btn">
                  <span class="material-symbols-outlined">rocket_launch</span>Next: Publish to GitHub ›
                </button>
              </div>
            </div>`;

          // Connect next step CTA
          statusArea.querySelector('#ght-goto-publish-btn')?.addEventListener('click', async () => {
            const { eventBus } = await import('../core/EventBus.js');
            eventBus.emit('router:navigate', { pageId: 'gh-publish', params: { tag } });
          });
        }

        // Refresh tags list
        listBtn?.click();
      } catch (err) {
        toast.error('Tag failed', err.message);
        if (statusArea) {
          statusArea.innerHTML = `
            <div class="ght-error-box" role="alert" aria-live="assertive">
              <div class="ght-status-header">
                <span class="material-symbols-outlined" style="color:var(--error);font-size:20px">error</span>
                <strong style="color:var(--error);font-size:var(--text-sm)">Tag Creation Failed</strong>
              </div>
              <div class="ght-error-message" style="margin:var(--sp-2) 0;font-size:var(--text-xs);color:var(--text-secondary)">
                ${err.message || 'An error occurred while creating the GitHub tag.'}
              </div>
              <button type="button" class="btn btn-secondary btn-sm" id="ght-retry-btn">
                <span class="material-symbols-outlined">replay</span>Retry
              </button>
            </div>`;

          statusArea.querySelector('#ght-retry-btn')?.addEventListener('click', () => {
            createBtn?.click();
          });
        }
      } finally {
        setFormDisabled(false);
        if (createBtn) {
          createBtn.innerHTML = '<span class="material-symbols-outlined">new_label</span>Create Tag';
        }
      }
    });

    container.querySelector('#ght-list-btn')?.addEventListener('click', async () => {
      const repo   = githubRepository.activeRepo;
      const listEl = container.querySelector('#ght-tags-list');
      if (!repo || !listEl) return;
      listEl.innerHTML = '<div style="padding:var(--sp-3);color:var(--text-muted)"><span class="material-symbols-outlined spin">sync</span> Loading…</div>';
      try {
        const tags = await githubTag.list(repo.owner.login, repo.name);
        if (!tags.length) { listEl.innerHTML = GitHubUI.empty('sell','No tags found','Create a tag above'); return; }
        listEl.innerHTML = tags.map(t => `
          <div class="gh-tag-row">
            <span class="material-symbols-outlined" style="color:var(--accent)">sell</span>
            <span class="gh-tag-name">${t.name}</span>
            <code class="gh-commit-sha">${t.commit.sha.slice(0,7)}</code>
            <button class="btn btn-ghost btn-sm btn-danger gh-delete-tag" data-tag="${t.name}" title="Delete tag">
              <span class="material-symbols-outlined" style="font-size:13px">delete</span>
            </button>
          </div>`).join('');

        listEl.querySelectorAll('.gh-delete-tag').forEach(btn => {
          btn.addEventListener('click', async (e) => {
            const tag = e.currentTarget.dataset.tag;
            const ok  = await modal.confirm({ icon:'delete', title:'Delete Tag', message:`Delete tag "${tag}"?`, confirmLabel:'Delete', danger:true });
            if (!ok) return;
            try {
              await githubTag.delete(repo.owner.login, repo.name, tag);
              toast.warn('Tag deleted', tag);
              container.querySelector('#ght-list-btn')?.click();
            } catch (err) { toast.error('Delete failed', err.message); }
          });
        });
      } catch (e) { listEl.innerHTML = GitHubUI.alert('error', e.message); }
    });
  }
}
