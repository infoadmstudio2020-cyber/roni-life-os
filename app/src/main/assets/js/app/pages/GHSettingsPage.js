/**
 * GHSettingsPage — GitHub Publisher default settings.
 */
import { githubSettings } from '../modules/github/GitHubSettings.js';
import { GitHubUI }       from '../modules/github/GitHubUI.js';
import { toast }          from '../ui/ToastService.js';

const LICENSES = ['Apache-2.0','MIT','GPL-3.0','LGPL-2.1','BSD-2-Clause','BSD-3-Clause','ISC','MPL-2.0','Unlicense'];

export class GHSettingsPage {
  async init(container) {
    await githubSettings.load();
    this.#render(container);
  }

  #render(container) {
    const s = githubSettings.get();

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title">
            <span class="material-symbols-outlined">settings</span>GitHub Settings
          </h1>
          <p class="page-subtitle">Default values used by the GitHub Publisher</p>
        </div>

        <div class="gh-settings-grid">

          ${GitHubUI.card('person', 'Account Defaults', `
            ${GitHubUI.field('ghs-username','Default Username / Owner','text',s.defaultUsername,'YourGitHubUsername','Used as repo owner when not specified',false)}
            ${GitHubUI.field('ghs-org','Default Organization','text',s.defaultOrg,'my-org','Optional: override owner with an org',false)}
          `)}

          ${GitHubUI.card('build', 'Repository Defaults', `
            ${GitHubUI.select('ghs-license','Default License',LICENSES, s.defaultLicense||'Apache-2.0')}
            ${GitHubUI.field('ghs-branch','Default Branch','text',s.defaultBranch||'main','main','')}
            ${GitHubUI.select('ghs-visibility','Default Visibility',[{value:'public',label:'Public'},{value:'private',label:'Private'}], s.defaultVisibility||'public')}
            ${GitHubUI.field('ghs-version','Default Version','text',s.defaultVersion||'1.0.0','1.0.0','')}
          `)}

          ${GitHubUI.card('auto_fix_high', 'Automation', `
            <div class="gh-toggle-group">
              ${GitHubUI.toggle('ghs-auto-readme',    'Auto-generate README.md',         s.autoReadme    !== false)}
              ${GitHubUI.toggle('ghs-auto-license',   'Auto-generate LICENSE',            s.autoLicense   !== false)}
              ${GitHubUI.toggle('ghs-auto-gitignore', 'Auto-generate .gitignore',         s.autoGitignore !== false)}
              ${GitHubUI.toggle('ghs-auto-workflows', 'Auto-generate CI/CD Workflows',    s.autoWorkflows !== false)}
              ${GitHubUI.toggle('ghs-auto-commit',    'Auto-commit on publish',           s.autoCommit    !== false)}
              ${GitHubUI.toggle('ghs-auto-release',   'Auto-create Release after push',   s.autoRelease   !== false)}
            </div>
          `)}

        </div>

        <div class="gh-action-row" style="margin-top:var(--sp-6)">
          <button class="btn btn-primary" id="ghs-save-btn">
            <span class="material-symbols-outlined">save</span>Save Settings
          </button>
          <button class="btn btn-secondary" id="ghs-reset-btn">
            <span class="material-symbols-outlined">restore</span>Reset to Defaults
          </button>
        </div>
      </div>`;

    this.#bindEvents(container);
  }

  #bindEvents(container) {
    const v = (id) => container.querySelector(id)?.value?.trim();
    const c = (id) => container.querySelector(id)?.checked !== false;

    container.querySelector('#ghs-save-btn')?.addEventListener('click', async () => {
      await githubSettings.save({
        defaultUsername:   v('#ghs-username'),
        defaultOrg:        v('#ghs-org'),
        defaultLicense:    v('#ghs-license'),
        defaultBranch:     v('#ghs-branch') || 'main',
        defaultVisibility: v('#ghs-visibility') || 'public',
        defaultVersion:    v('#ghs-version')   || '1.0.0',
        autoReadme:        c('#ghs-auto-readme'),
        autoLicense:       c('#ghs-auto-license'),
        autoGitignore:     c('#ghs-auto-gitignore'),
        autoWorkflows:     c('#ghs-auto-workflows'),
        autoCommit:        c('#ghs-auto-commit'),
        autoRelease:       c('#ghs-auto-release'),
      });
      toast.success('Settings saved');
    });

    container.querySelector('#ghs-reset-btn')?.addEventListener('click', async () => {
      await githubSettings.reset();
      toast.info('Settings reset to defaults');
      this.#render(container);
    });
  }
}
