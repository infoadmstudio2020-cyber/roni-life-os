/**
 * GitHubPage — generate and review GitHub-related project files.
 */
import { projectService } from '../modules/storage/ProjectService.js';
import { eventBus } from '../core/EventBus.js';
import { toast } from '../ui/ToastService.js';
import { consoleService } from '../modules/storage/ConsoleService.js';

export class GitHubPage {
  async init(container) {
    const p = projectService.current;

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header" style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:var(--sp-3)">
          <div>
            <h1 class="page-title"><span class="material-symbols-outlined">hub</span>GitHub Integration</h1>
            <p class="page-subtitle">Configure your repository and generate GitHub-related files</p>
          </div>
          <div style="display:flex;align-items:center;gap:var(--sp-2);flex-wrap:wrap">
            <button class="btn btn-secondary" id="gh-go-wizard-btn" style="display:inline-flex;align-items:center;gap:0.4rem">
              <span class="material-symbols-outlined" style="color:#38BDF8">smart_toy</span>Actions Wizard
            </button>
            <button class="btn btn-primary" id="gh-go-test-btn" style="display:inline-flex;align-items:center;gap:0.4rem">
              <span class="material-symbols-outlined">fact_check</span>Run Full E2E Test
            </button>
          </div>
        </div>

        <div style="display:grid;grid-template-columns:1fr 1fr;gap:var(--sp-4)" class="gh-two-col">

          <!-- Config -->
          <div class="card">
            <div class="card-header"><span class="material-symbols-outlined">settings</span>Repository Configuration</div>
            <div class="card-body">
              <div class="form-group">
                <label class="form-label">Repository Name</label>
                <input class="input" id="gh-repo-name" value="${p?.repoName || ''}" placeholder="my-android-library" />
              </div>
              <div class="form-group">
                <label class="form-label">GitHub Username / Org</label>
                <input class="input" id="gh-username" value="${p?.author || ''}" placeholder="YourGitHubUsername" />
              </div>
              <div class="form-group">
                <label class="form-label">Description</label>
                <textarea class="input textarea" id="gh-description" rows="2">${p?.description || ''}</textarea>
              </div>
              <div class="form-group">
                <label class="form-label">License</label>
                <div class="select-wrapper">
                  <select class="input select" id="gh-license">
                    ${['Apache-2.0','MIT','GPL-3.0','LGPL-2.1','BSD-3-Clause','ISC','Unlicense'].map(l =>
                      `<option${p?.license===l?' selected':''}>${l}</option>`).join('')}
                  </select>
                </div>
              </div>
              <button class="btn btn-primary" id="gh-generate-btn" ${!p?'disabled':''}>
                <span class="material-symbols-outlined">auto_fix_high</span>Generate GitHub Files
              </button>
            </div>
          </div>

          <!-- Generated files list -->
          <div class="card">
            <div class="card-header"><span class="material-symbols-outlined">folder</span>Generated Files</div>
            <div class="card-body" style="padding:0">
              <div id="gh-files-list">
                ${this.#fileList(p)}
              </div>
            </div>
          </div>
        </div>

        <!-- Setup Instructions -->
        <div class="card" style="margin-top:var(--sp-4)">
          <div class="card-header"><span class="material-symbols-outlined">help</span>GitHub Setup Instructions</div>
          <div class="card-body">
            <ol style="padding-left:var(--sp-5);display:flex;flex-direction:column;gap:var(--sp-2);font-size:var(--text-sm);color:var(--text-secondary)">
              <li>Export your project as ZIP and extract it locally</li>
              <li>Initialize a Git repository: <code class="code-inline">git init</code></li>
              <li>Create a new repository on <a href="https://github.com" target="_blank" style="color:var(--accent)">github.com</a> (public, no README)</li>
              <li>Add the remote: <code class="code-inline">git remote add origin https://github.com/USERNAME/REPO.git</code></li>
              <li>Commit and push: <code class="code-inline">git add . && git commit -m "Initial release" && git push -u origin main</code></li>
              <li>Create a release tag: <code class="code-inline">git tag v1.0.0 && git push --tags</code></li>
              <li>Visit <a href="https://jitpack.io" target="_blank" style="color:var(--accent)">jitpack.io</a> and look up your repository</li>
            </ol>
          </div>
        </div>
      </div>`;

    container.querySelector('#gh-go-wizard-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'gh-wizard' });
    });
    container.querySelector('#gh-go-test-btn')?.addEventListener('click', () => {
      eventBus.emit('router:navigate', { pageId: 'gh-testing' });
    });

    container.querySelector('#gh-generate-btn')?.addEventListener('click', async () => {
      if (!p) return;
      const username = container.querySelector('#gh-username')?.value?.trim() || p.author;
      const repoName = container.querySelector('#gh-repo-name')?.value?.trim() || p.repoName;
      const license  = container.querySelector('#gh-license')?.value || p.license;
      const desc     = container.querySelector('#gh-description')?.value?.trim() || p.description;

      // Update project meta
      p.author  = username;
      p.repoName = repoName;
      p.license  = license;
      p.description = desc;

      // Generate badge for README
      const readmePath = 'README.md';
      let readme = projectService.getFile(readmePath) || '';
      if (readme && !readme.includes('jitpack.io/v/')) {
        readme = `[![JitPack](https://jitpack.io/v/${username}/${repoName}.svg)](https://jitpack.io/#${username}/${repoName})\n\n` + readme;
        await projectService.setFile(readmePath, readme);
      }

      consoleService.success('GitHub files are already present in your project');
      toast.success('GitHub Files Ready', 'Check Explorer for .github/ directory');

      // Refresh file list
      const listEl = container.querySelector('#gh-files-list');
      if (listEl) listEl.innerHTML = this.#fileList(projectService.current);
    });
  }

  #fileList(p) {
    if (!p) return '<div style="padding:var(--sp-6);text-align:center;color:var(--text-muted)">No project open</div>';
    const ghFiles = Object.keys(p.files || {}).filter(f => f.startsWith('.github/') || f === '.gitignore' || f === 'LICENSE');
    if (!ghFiles.length) return '<div style="padding:var(--sp-4);text-align:center;color:var(--text-muted);font-size:var(--text-sm)">No GitHub files yet. Generate them above.</div>';
    return ghFiles.map(f => `
      <div class="settings-row" style="cursor:pointer" data-open="${f}">
        <div class="settings-row-label">
          <div class="settings-row-title" style="font-family:var(--font-mono);font-size:var(--text-xs)">${f}</div>
        </div>
        <span class="material-symbols-outlined" style="font-size:14px;color:var(--text-muted)">open_in_new</span>
      </div>`).join('');
  }
}
