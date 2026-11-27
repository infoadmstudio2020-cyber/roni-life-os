/**
 * GHHistoryPage — view publish history stored in IndexedDB.
 */
import { githubHistory } from '../modules/github/GitHubHistory.js';
import { GitHubUI }      from '../modules/github/GitHubUI.js';
import { modal }         from '../ui/ModalService.js';
import { toast }         from '../ui/ToastService.js';

export class GHHistoryPage {
  async init(container) {
    await githubHistory.load();
    this.#render(container);
  }

  #render(container) {
    const records = githubHistory.records;

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title">
            <span class="material-symbols-outlined">history</span>Publish History
          </h1>
          <p class="page-subtitle">All GitHub publish operations recorded on this device</p>
        </div>

        ${GitHubUI.card('timeline', 'History', `
          <div style="display:flex;justify-content:flex-end;margin-bottom:var(--sp-3)">
            <button class="btn btn-danger btn-sm" id="ghh-clear-btn" ${!records.length ? 'disabled' : ''}>
              <span class="material-symbols-outlined">delete_sweep</span>Clear All
            </button>
          </div>
          ${!records.length
            ? GitHubUI.empty('history','No publish history yet','Publish a project to see history here')
            : `<div class="gh-history-table-wrap">
                <table class="gh-history-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Repository</th>
                      <th>Version</th>
                      <th>Commit</th>
                      <th>Status</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${records.map(r => `
                      <tr>
                        <td class="gh-history-date">${r.date}</td>
                        <td>
                          ${r.repoUrl && r.repoUrl !== '—'
                            ? `<a href="${r.repoUrl}" target="_blank" class="gh-link">${r.owner}/${r.repo}</a>`
                            : `${r.owner}/${r.repo}`}
                        </td>
                        <td><code>${r.version}</code></td>
                        <td><code>${r.commitSha}</code></td>
                        <td>${GitHubUI.badge(r.status)}</td>
                        <td>
                          ${r.repoUrl && r.repoUrl !== '—'
                            ? `<a href="${r.repoUrl}" target="_blank" class="btn btn-ghost btn-sm btn-icon" title="Open repository">
                                <span class="material-symbols-outlined" style="font-size:13px">open_in_new</span></a>`
                            : ''}
                          <button class="btn btn-ghost btn-sm btn-icon btn-danger gh-delete-hist" data-id="${r.id}" title="Delete record">
                            <span class="material-symbols-outlined" style="font-size:13px">delete</span>
                          </button>
                        </td>
                      </tr>
                      ${r.error ? `<tr><td colspan="6" class="gh-history-error">${r.error}</td></tr>` : ''}
                    `).join('')}
                  </tbody>
                </table>
              </div>`
          }
        `)}
      </div>`;

    this.#bindEvents(container);
  }

  #bindEvents(container) {
    container.querySelector('#ghh-clear-btn')?.addEventListener('click', async () => {
      const ok = await modal.confirm({ icon:'delete_sweep', title:'Clear History', message:'Delete all publish history records?', confirmLabel:'Clear All', danger:true });
      if (!ok) return;
      await githubHistory.clear();
      toast.info('History cleared');
      this.#render(container);
    });

    container.querySelectorAll('.gh-delete-hist').forEach(btn => {
      btn.addEventListener('click', async () => {
        await githubHistory.delete(btn.dataset.id);
        toast.info('Record deleted');
        await githubHistory.load();
        this.#render(container);
      });
    });
  }
}
