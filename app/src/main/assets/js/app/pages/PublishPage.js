/**
 * PublishPage — pre-publish checklist and export.
 *
 * Styling lives in css/pages/publish.css — no inline styles in this file.
 */
import { projectService } from '../modules/storage/ProjectService.js';
import { eventBus }       from '../core/EventBus.js';

export class PublishPage {
  async init(container) {
    const p = projectService.current;
    const files = p?.files || {};

    const checks = [
      { label: 'Project generated',         done: !!p,                                        tip: 'Create a project using the Generator' },
      { label: 'README.md present',          done: 'README.md' in files,                      tip: 'README.md should be in project root' },
      { label: 'LICENSE file present',       done: 'LICENSE' in files,                        tip: 'LICENSE file should be in project root' },
      { label: '.gitignore present',         done: '.gitignore' in files,                     tip: '.gitignore should be in project root' },
      { label: 'jitpack.yml present',        done: 'jitpack.yml' in files,                   tip: 'Required for JitPack to build your library' },
      { label: 'Library build.gradle present', done: Object.keys(files).some(k => (k.endsWith('/build.gradle') || k.endsWith('/build.gradle.kts')) && k !== 'build.gradle'), tip: 'Library module must have a build.gradle' },
      { label: 'Maven publishing configured', done: Object.values(files).some(c => typeof c === 'string' && (c.includes('maven-publish') || c.includes('publishing {'))), tip: 'Required for JitPack and GitHub Packages distribution' },
      { label: 'AndroidManifest.xml present',done: Object.keys(files).some(k => k.includes('AndroidManifest.xml')), tip: 'Required in library src/main/ directory' },
      { label: 'GitHub Actions CI configured',done: Object.keys(files).some(k => k.includes('.github/workflows')), tip: 'Add .github/workflows/ci.yml' },
      { label: 'App module present',         done: Object.keys(files).some(k => k.startsWith('app/') || k.startsWith('sample/')), tip: 'Recommended: add a companion app module' },
      { label: 'Author name set',            done: !!(p?.author),                             tip: 'Set in Project Settings' },
      { label: 'Repository name set',        done: !!(p?.repoName),                          tip: 'Set in Project Settings or Generator' },
    ];

    const passCount = checks.filter(c => c.done).length;
    const pct       = Math.round((passCount / checks.length) * 100);
    const ready     = passCount === checks.length;

    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title"><span class="material-symbols-outlined">rocket_launch</span>Publish</h1>
          <p class="page-subtitle">Review the publishing checklist and export your project</p>
        </div>

        <!-- Score -->
        <div class="card">
          <div class="card-body">
            <div class="publish-score-row${ready ? ' is-ready' : ''}">
              <span class="material-symbols-outlined publish-score-icon">
                ${ready ? 'rocket_launch' : 'pending'}
              </span>
              <div class="publish-score-text">
                <div class="publish-score-title">
                  ${ready ? 'Ready to Publish!' : `${passCount}/${checks.length} checks passed`}
                </div>
                <div class="publish-score-sub">
                  ${ready ? 'Your project is ready for GitHub and JitPack' : 'Complete remaining items before publishing'}
                </div>
              </div>
              <div class="publish-score-pct">${pct}%</div>
            </div>
            <div class="progress-bar publish-score-bar">
              <div class="progress-bar-fill publish-score-fill" style="width:${pct}%"></div>
            </div>
          </div>
        </div>

        <!-- Checklist -->
        <div class="card">
          <div class="card-header"><span class="material-symbols-outlined">checklist</span>Publishing Checklist</div>
          <div class="card-body publish-checklist-body">
            ${checks.map(c => `
              <div class="settings-row">
                <div class="settings-row-label">
                  <div class="settings-row-title">${c.label}</div>
                  ${!c.done ? `<div class="settings-row-desc">${c.tip}</div>` : ''}
                </div>
                <span class="material-symbols-outlined publish-check-icon${c.done ? ' is-done' : ''}">
                  ${c.done ? 'check_circle' : 'radio_button_unchecked'}
                </span>
              </div>`).join('')}
          </div>
        </div>

        <!-- Export Button -->
        <div class="card">
          <div class="card-body">
            <div class="publish-actions-row">
              <button type="button" class="btn btn-primary btn-lg" id="pub-export-btn" ${!p?'disabled':''}>
                <span class="material-symbols-outlined">download</span>Export as ZIP
              </button>
              <button type="button" class="btn btn-secondary" id="pub-validate-btn" ${!p?'disabled':''}>
                <span class="material-symbols-outlined">verified</span>Run Validation
              </button>
            </div>
            <div class="publish-note">
              After export: extract the ZIP, initialize Git, push to GitHub, then tag a release for JitPack.
            </div>
          </div>
        </div>
      </div>`;

    container.querySelector('#pub-export-btn')?.addEventListener('click', async () => {
      const { zipService } = await import('../modules/zip/ZipService.js');
      await zipService.exportProject();
    });

    container.querySelector('#pub-validate-btn')?.addEventListener('click', () => {
      import('../core/EventBus.js').then(({ eventBus }) =>
        eventBus.emit('router:navigate', { pageId: 'build' }));
    });
  }
}
