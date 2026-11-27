/**
 * GHCommitPage — view commit history and build commit messages.
 */
import { githubAuth }       from '../modules/github/GitHubAuth.js';
import { githubRepository } from '../modules/github/GitHubRepository.js';
import { githubCommit }     from '../modules/github/GitHubCommit.js';
import { githubSettings }   from '../modules/github/GitHubSettings.js';
import { GitHubUI }         from '../modules/github/GitHubUI.js';
import { toast }            from '../ui/ToastService.js';
import { ClipboardUtil }    from '../core/ClipboardUtil.js';

export class GHCommitPage {
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
    const repo   = githubRepository.activeRepo;
    const s      = githubSettings.get();
    const branch = s.defaultBranch || 'main';

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title">
            <span class="material-symbols-outlined">commit</span>Commit
          </h1>
          <p class="page-subtitle">Build commit messages and view commit history</p>
        </div>

        <div class="gh-two-col">
          ${GitHubUI.card('edit_note', 'Commit Message Builder', `
            ${GitHubUI.select('ghc-type','Type',[
              {value:'feat',label:'feat — new feature'},
              {value:'fix',label:'fix — bug fix'},
              {value:'chore',label:'chore — maintenance'},
              {value:'docs',label:'docs — documentation'},
              {value:'refactor',label:'refactor — code refactor'},
              {value:'style',label:'style — formatting'},
              {value:'test',label:'test — tests'},
            ], 'feat')}
            ${GitHubUI.field('ghc-scope','Scope (optional)','text','','e.g. library, sample','')}
            ${GitHubUI.field('ghc-subject','Subject','text','','Short description of the change','',true)}
            ${GitHubUI.textarea('ghc-body','Body (optional)','','Detailed description…',3)}
            <div class="gh-action-row" style="margin-top:var(--sp-3)">
              <button class="btn btn-secondary" id="ghc-build-btn">
                <span class="material-symbols-outlined">build</span>Build Message
              </button>
              <button class="btn btn-ghost" id="ghc-copy-btn" disabled>
                <span class="material-symbols-outlined">content_copy</span>Copy
              </button>
            </div>
            <div id="ghc-preview" style="display:none;margin-top:var(--sp-3)">
              <label class="form-label">Preview</label>
              <div class="gh-commit-preview" id="ghc-preview-text"></div>
            </div>
          `)}

          <div>
            ${GitHubUI.card('history', 'Recent Commits', `
              <div id="ghc-history-list">
                ${repo
                  ? `<button class="btn btn-secondary btn-sm" id="ghc-load-commits-btn">
                      <span class="material-symbols-outlined">refresh</span>Load from ${repo.name}
                    </button>`
                  : GitHubUI.empty('hub','No repository selected','Select a repository in GitHub › Repository')
                }
              </div>
            `)}
          </div>
        </div>
      </div>`;

    this.#bindEvents(container);
  }

  #bindEvents(container) {
    let builtMessage = '';

    container.querySelector('#ghc-build-btn')?.addEventListener('click', () => {
      const type    = container.querySelector('#ghc-type')?.value || 'feat';
      const scope   = container.querySelector('#ghc-scope')?.value?.trim();
      const subject = container.querySelector('#ghc-subject')?.value?.trim();
      const body    = container.querySelector('#ghc-body')?.value?.trim();
      if (!subject) { toast.error('Required', 'Subject is required'); return; }

      builtMessage = githubCommit.buildMessage(type, scope, subject, body);
      const preview = container.querySelector('#ghc-preview');
      const text    = container.querySelector('#ghc-preview-text');
      const copy    = container.querySelector('#ghc-copy-btn');
      if (preview) preview.style.display = '';
      if (text)    text.textContent = builtMessage;
      if (copy)    copy.disabled = false;
    });

    container.querySelector('#ghc-copy-btn')?.addEventListener('click', async () => {
      if (!builtMessage) return;
      const ok = await ClipboardUtil.copyText(builtMessage);
      if (ok) {
        toast.info('Copied', 'Commit message copied');
      } else {
        toast.error('Copy Failed', 'Please copy manually');
      }
    });

    container.querySelector('#ghc-load-commits-btn')?.addEventListener('click', async () => {
      const btn    = container.querySelector('#ghc-load-commits-btn');
      const listEl = container.querySelector('#ghc-history-list');
      const repo   = githubRepository.activeRepo;
      if (!repo) return;
      btn.disabled = true;
      btn.innerHTML = '<span class="material-symbols-outlined spin">sync</span>Loading…';
      try {
        const s = githubSettings.get();
        const raw = await githubCommit.listRecent(repo.owner.login, repo.name, s.defaultBranch || 'main');
        const commits = raw.map(c => githubCommit.formatCommit(c));
        listEl.innerHTML = commits.map(c => `
          <div class="gh-commit-row">
            <code class="gh-commit-sha">${c.sha}</code>
            <div class="gh-commit-msg">${c.message}</div>
            <div class="gh-commit-meta">${c.author} · ${c.date}</div>
            <a href="${c.url}" target="_blank" class="btn btn-ghost btn-sm btn-icon">
              <span class="material-symbols-outlined" style="font-size:13px">open_in_new</span>
            </a>
          </div>`).join('');
      } catch (e) {
        listEl.innerHTML = GitHubUI.alert('error', e.message);
      }
    });
  }
}
