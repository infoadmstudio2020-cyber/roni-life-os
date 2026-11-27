/**
 * GHFilesPage — generate and preview GitHub repository files.
 */
import { githubAuth }          from '../modules/github/GitHubAuth.js';
import { githubSettings }      from '../modules/github/GitHubSettings.js';
import { githubFileGenerator } from '../modules/github/GitHubFileGenerator.js';
import { projectService }      from '../modules/storage/ProjectService.js';
import { GitHubUI }            from '../modules/github/GitHubUI.js';
import { eventBus }            from '../core/EventBus.js';
import { toast }               from '../ui/ToastService.js';
import { consoleService }      from '../modules/storage/ConsoleService.js';

const GH_FILES = [
  'README.md', 'LICENSE', '.gitignore', 'SECURITY.md', 'CHANGELOG.md',
  'CONTRIBUTING.md', 'CODE_OF_CONDUCT.md',
  '.github/workflows/ci.yml', '.github/workflows/release.yml',
  '.github/FUNDING.yml', '.github/PULL_REQUEST_TEMPLATE.md',
  '.github/ISSUE_TEMPLATE/bug_report.md', '.github/ISSUE_TEMPLATE/feature_request.md',
];

export class GHFilesPage {
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
    const proj   = projectService.current;
    const s      = githubSettings.get();
    const owner  = s.defaultUsername || githubAuth.user?.login || '';
    const files  = proj?.files || {};

    const existingGhFiles = GH_FILES.filter(f => files[f] !== undefined);
    const missingGhFiles  = GH_FILES.filter(f => files[f] === undefined);

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title">
            <span class="material-symbols-outlined">folder_zip</span>GitHub Files
          </h1>
          <p class="page-subtitle">Auto-generate standard GitHub repository files for your project</p>
        </div>

        ${!proj ? GitHubUI.alert('warning', 'No project loaded. Generate a project first.') : ''}

        <div class="gh-two-col">
          ${GitHubUI.card('settings', 'Generation Settings', `
            ${GitHubUI.field('ghf-owner','GitHub Username / Owner','text',owner,'YourUsername','Used in README badges and URLs',true)}
            ${GitHubUI.field('ghf-repo','Repository Name','text',proj?.repoName||'','my-android-library','',true)}
            <div class="gh-toggle-group">
              ${GitHubUI.toggle('ghf-readme',  'README.md with JitPack badge', s.autoReadme !== false)}
              ${GitHubUI.toggle('ghf-license', 'LICENSE file',                  s.autoLicense !== false)}
              ${GitHubUI.toggle('ghf-gitignore','.gitignore (Android)',          s.autoGitignore !== false)}
              ${GitHubUI.toggle('ghf-workflows','CI/CD GitHub Workflows',        s.autoWorkflows !== false)}
              ${GitHubUI.toggle('ghf-extras',  'SECURITY, CONTRIBUTING, CoC',   true)}
              ${GitHubUI.toggle('ghf-templates','Issue & PR Templates',          true)}
            </div>
            <div class="gh-action-row" style="margin-top:var(--sp-3)">
              <button class="btn btn-primary" id="ghf-generate-btn" ${!proj ? 'disabled' : ''}>
                <span class="material-symbols-outlined">auto_fix_high</span>Generate Files
              </button>
            </div>
          `)}

          ${GitHubUI.card('checklist', 'File Status', `
            <div class="gh-file-status-list">
              ${GH_FILES.map(f => {
                const exists = files[f] !== undefined;
                return `
                  <div class="gh-file-row${exists ? ' exists' : ''}" data-file="${f}">
                    <span class="material-symbols-outlined" style="font-size:14px;color:${exists ? 'var(--success)' : 'var(--text-disabled)'}">
                      ${exists ? 'check_circle' : 'radio_button_unchecked'}
                    </span>
                    <span class="gh-file-path">${f}</span>
                    ${exists ? `<button class="btn btn-ghost btn-sm gh-open-file" data-file="${f}" title="Open in Editor">
                      <span class="material-symbols-outlined" style="font-size:13px">open_in_new</span>
                    </button>` : ''}
                  </div>`;
              }).join('')}
            </div>
          `)}
        </div>
      </div>`;

    this.#bindEvents(container);
  }

  #bindEvents(container) {
    container.querySelector('#ghf-generate-btn')?.addEventListener('click', async () => {
      const btn    = container.querySelector('#ghf-generate-btn');
      const owner  = container.querySelector('#ghf-owner')?.value?.trim();
      const repo   = container.querySelector('#ghf-repo')?.value?.trim();
      if (!owner || !repo) { toast.error('Required', 'Enter username and repository name'); return; }

      const s = githubSettings.get();
      btn.disabled = true;
      btn.innerHTML = '<span class="material-symbols-outlined spin">sync</span>Generating…';

      try {
        const proj  = projectService.current;
        const useReadme    = container.querySelector('#ghf-readme')?.checked !== false;
        const useWorkflows = container.querySelector('#ghf-workflows')?.checked !== false;
        const useExtras    = container.querySelector('#ghf-extras')?.checked !== false;
        const useTemplates = container.querySelector('#ghf-templates')?.checked !== false;

        const allFiles = githubFileGenerator.generate({ ...proj, repoName: repo }, owner, { ...s, defaultBranch: s.defaultBranch || 'main' });

        const toWrite = {};
        if (useReadme)    { toWrite['README.md'] = allFiles['README.md']; toWrite['LICENSE'] = allFiles['LICENSE']; toWrite['.gitignore'] = allFiles['.gitignore']; }
        if (useWorkflows) { toWrite['.github/workflows/ci.yml'] = allFiles['.github/workflows/ci.yml'] || allFiles['.github/workflows/build.yml']; toWrite['.github/workflows/release.yml'] = allFiles['.github/workflows/release.yml']; toWrite['.github/FUNDING.yml'] = allFiles['.github/FUNDING.yml']; }
        if (useExtras)    { toWrite['SECURITY.md'] = allFiles['SECURITY.md']; toWrite['CHANGELOG.md'] = allFiles['CHANGELOG.md']; toWrite['CONTRIBUTING.md'] = allFiles['CONTRIBUTING.md']; toWrite['CODE_OF_CONDUCT.md'] = allFiles['CODE_OF_CONDUCT.md']; }
        if (useTemplates) { toWrite['.github/PULL_REQUEST_TEMPLATE.md'] = allFiles['.github/PULL_REQUEST_TEMPLATE.md']; toWrite['.github/ISSUE_TEMPLATE/bug_report.md'] = allFiles['.github/ISSUE_TEMPLATE/bug_report.md']; toWrite['.github/ISSUE_TEMPLATE/feature_request.md'] = allFiles['.github/ISSUE_TEMPLATE/feature_request.md']; }

        for (const [path, content] of Object.entries(toWrite)) {
          await projectService.setFile(path, content);
        }
        await githubSettings.save({ defaultUsername: owner });

        const count = Object.keys(toWrite).length;
        toast.success('Files generated', `${count} GitHub files added to project`);
        consoleService.success(`GitHub: generated ${count} files`);
        this.#render(container);
      } catch (e) {
        toast.error('Generation failed', e.message);
      } finally {
        btn.disabled = false;
        btn.innerHTML = '<span class="material-symbols-outlined">auto_fix_high</span>Generate Files';
      }
    });

    container.querySelectorAll('.gh-open-file').forEach(btn => {
      btn.addEventListener('click', () => {
        const path    = btn.dataset.file;
        const content = projectService.getFile(path) || '';
        eventBus.emit('editor:openFile', { path, content });
        eventBus.emit('router:navigate', { pageId: 'editor' });
      });
    });
  }
}
