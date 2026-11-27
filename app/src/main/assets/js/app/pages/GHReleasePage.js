/**
 * GHReleasePage — create and list GitHub releases.
 */
import { githubAuth }       from '../modules/github/GitHubAuth.js';
import { githubRepository } from '../modules/github/GitHubRepository.js';
import { githubRelease }    from '../modules/github/GitHubRelease.js';
import { githubTag }        from '../modules/github/GitHubTag.js';
import { githubSettings }   from '../modules/github/GitHubSettings.js';
import { projectService }   from '../modules/storage/ProjectService.js';
import { GitHubUI }         from '../modules/github/GitHubUI.js';
import { toast }            from '../ui/ToastService.js';
import { consoleService }   from '../modules/storage/ConsoleService.js';

export class GHReleasePage {
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

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title">
            <span class="material-symbols-outlined">rocket_launch</span>Releases
          </h1>
          <p class="page-subtitle">Create GitHub releases from version tags</p>
        </div>

        ${!repo ? GitHubUI.alert('warning','No repository selected. Go to GitHub › Repository first.') : ''}

        <div class="gh-two-col">
          ${GitHubUI.card('new_releases', 'Create Release', `
            ${GitHubUI.select('ghr-tag-select','Tag Name',['loading…'],'loading…')}
            ${GitHubUI.field('ghr-name','Release Title','text',`${proj?.name||''} v${version}`,`v${version}`,'')}
            ${GitHubUI.textarea('ghr-notes','Release Notes',
              githubRelease.generateChangelog([], `v${version}`),'Describe this release…',6)}
            <div class="gh-two-col" style="gap:var(--sp-2)">
              ${GitHubUI.toggle('ghr-prerelease','Pre-release', false,'Mark as pre-release')}
              ${GitHubUI.toggle('ghr-draft','Draft', false,'Save as draft')}
            </div>
            <div class="gh-action-row" style="margin-top:var(--sp-3)">
              <button class="btn btn-primary" id="ghr-create-btn" ${!repo ? 'disabled' : ''}>
                <span class="material-symbols-outlined">rocket_launch</span>Create Release
              </button>
              <button class="btn btn-ghost" id="ghr-load-tags-btn" ${!repo ? 'disabled' : ''}>
                <span class="material-symbols-outlined">refresh</span>Load Tags
              </button>
            </div>
          `)}

          ${GitHubUI.card('history', 'Release History', `
            <button class="btn btn-secondary btn-sm" id="ghr-list-btn" ${!repo?'disabled':''}>
              <span class="material-symbols-outlined">refresh</span>Load Releases
            </button>
            <div id="ghr-releases-list" style="margin-top:var(--sp-3)"></div>
          `)}
        </div>
      </div>`;

    if (repo) this.#loadTags(container);
    this.#bindEvents(container);
  }

  async #loadTags(container) {
    const repo = githubRepository.activeRepo;
    const sel  = container.querySelector('#ghr-tag-select');
    if (!repo || !sel) return;
    try {
      const tags = await githubTag.list(repo.owner.login, repo.name);
      sel.innerHTML = tags.length
        ? tags.map(t => `<option value="${t.name}">${t.name}</option>`).join('')
        : '<option value="">— no tags found —</option>';
    } catch { sel.innerHTML = '<option value="">— load failed —</option>'; }
  }

  #bindEvents(container) {
    container.querySelector('#ghr-load-tags-btn')?.addEventListener('click', () => this.#loadTags(container));

    container.querySelector('#ghr-create-btn')?.addEventListener('click', async () => {
      const repo  = githubRepository.activeRepo;
      const tag   = container.querySelector('#ghr-tag-select')?.value;
      const name  = container.querySelector('#ghr-name')?.value?.trim() || tag;
      const notes = container.querySelector('#ghr-notes')?.value?.trim();
      const pre   = container.querySelector('#ghr-prerelease')?.checked || false;
      const draft = container.querySelector('#ghr-draft')?.checked || false;
      if (!repo || !tag) { toast.error('Required', 'Select a tag first'); return; }

      const btn = container.querySelector('#ghr-create-btn');
      btn.disabled = true;
      btn.innerHTML = '<span class="material-symbols-outlined spin">sync</span>Creating…';
      try {
        const s = githubSettings.get();
        const rel = await githubRelease.create(repo.owner.login, repo.name, {
          tag_name: tag, name, body: notes,
          draft, prerelease: pre,
          target_commitish: s.defaultBranch || 'main',
        });
        toast.success('Release created', rel.html_url);
        consoleService.success(`GitHub: release ${rel.tag_name} → ${rel.html_url}`);
        container.querySelector('#ghr-list-btn')?.click();
      } catch (e) {
        toast.error('Release failed', e.message);
      } finally {
        btn.disabled = false;
        btn.innerHTML = '<span class="material-symbols-outlined">rocket_launch</span>Create Release';
      }
    });

    container.querySelector('#ghr-list-btn')?.addEventListener('click', async () => {
      const repo   = githubRepository.activeRepo;
      const listEl = container.querySelector('#ghr-releases-list');
      if (!repo || !listEl) return;
      listEl.innerHTML = '<div style="color:var(--text-muted)"><span class="material-symbols-outlined spin">sync</span> Loading…</div>';
      try {
        const releases = await githubRelease.list(repo.owner.login, repo.name);
        if (!releases.length) { listEl.innerHTML = GitHubUI.empty('rocket_launch','No releases yet','Create one above'); return; }
        listEl.innerHTML = releases.map(r => `
          <div class="gh-release-row">
            <div class="gh-release-tag">
              <span class="material-symbols-outlined" style="color:var(--accent)">sell</span>${r.tag_name}
              ${r.prerelease ? '<span class="gh-badge badge-warn" style="font-size:10px">pre-release</span>' : ''}
              ${r.draft      ? '<span class="gh-badge badge-info" style="font-size:10px">draft</span>' : ''}
            </div>
            <div class="gh-release-name">${r.name || r.tag_name}</div>
            <div class="gh-release-date">${new Date(r.created_at).toLocaleDateString()}</div>
            <a href="${r.html_url}" target="_blank" class="btn btn-ghost btn-sm">
              <span class="material-symbols-outlined" style="font-size:13px">open_in_new</span>
            </a>
          </div>`).join('');
      } catch (e) { listEl.innerHTML = GitHubUI.alert('error', e.message); }
    });
  }
}
