/**
 * BuildPage — run validation checks and display results.
 */
import { validatorService } from '../modules/build/ValidatorService.js';
import { projectService } from '../modules/storage/ProjectService.js';
import { consoleService } from '../modules/storage/ConsoleService.js';

export class BuildPage {
  async init(container) {
    container.innerHTML = `
      <div class="page-container">
        <div class="page-header">
          <h1 class="page-title"><span class="material-symbols-outlined">verified</span>Build Validator</h1>
          <p class="page-subtitle">Validate your project structure, Android Studio compatibility, and publishing configuration</p>
        </div>

        <div style="display:flex;gap:var(--sp-3);margin-bottom:var(--sp-5);flex-wrap:wrap">
          <button class="btn btn-primary" id="build-validate-btn" ${!projectService.current?'disabled':''}>
            <span class="material-symbols-outlined">play_arrow</span>Run Validation
          </button>
          <button class="btn btn-accent" id="build-fix-missing-btn" ${!projectService.current?'disabled':''}>
            <span class="material-symbols-outlined">auto_fix_high</span>Generate Missing Files
          </button>
          <button class="btn btn-secondary" id="build-export-btn" ${!projectService.current?'disabled':''}>
            <span class="material-symbols-outlined">download</span>Export ZIP
          </button>
        </div>

        <div id="build-results">
          <div class="card" style="padding:var(--sp-8);text-align:center">
            <span class="material-symbols-outlined" style="font-size:48px;opacity:.3;font-variation-settings:'FILL' 0,'wght' 200">verified</span>
            <div style="margin-top:var(--sp-3);color:var(--text-muted)">
              ${projectService.current ? 'Click "Run Validation" to check your project' : 'No project open. Generate a project first.'}
            </div>
          </div>
        </div>
      </div>`;

    container.querySelector('#build-validate-btn')?.addEventListener('click', async () => {
      const btn = container.querySelector('#build-validate-btn');
      btn.disabled = true;
      btn.innerHTML = `<span class="material-symbols-outlined rotating">sync</span>Validating…`;

      try {
        consoleService.info('Starting project validation…');
        const results = await validatorService.validate();
        this.#renderResults(container, results);
      } catch (err) {
        consoleService.error(`Validation error: ${err.message}`);
        container.querySelector('#build-results').innerHTML = `
          <div class="card" style="padding:var(--sp-6);border-left:4px solid var(--error)">
            <div style="font-weight:700;color:var(--error);margin-bottom:var(--sp-2)">Validation Failed with Error</div>
            <div style="color:var(--text-muted);font-family:monospace;font-size:var(--text-sm)">${err.message}</div>
          </div>`;
      } finally {
        btn.disabled = false;
        btn.innerHTML = `<span class="material-symbols-outlined">play_arrow</span>Run Validation`;
      }
    });

    container.querySelector('#build-fix-missing-btn')?.addEventListener('click', async () => {
      const btn = container.querySelector('#build-fix-missing-btn');
      btn.disabled = true;
      btn.innerHTML = `<span class="material-symbols-outlined rotating">sync</span>Generating…`;

      try {
        const { MissingFilesService } = await import('../modules/build/MissingFilesService.js');
        await MissingFilesService.generateMissingFiles(projectService.current);

        // Auto re-validate after generating
        const results = await validatorService.validate();
        this.#renderResults(container, results);
      } catch (err) {
        consoleService.error(`Missing files generation error: ${err.message}`);
      } finally {
        btn.disabled = false;
        btn.innerHTML = `<span class="material-symbols-outlined">auto_fix_high</span>Generate Missing Files`;
      }
    });

    container.querySelector('#build-export-btn')?.addEventListener('click', async () => {
      const { zipService } = await import('../modules/zip/ZipService.js');
      await zipService.exportProject();
    });
  }

  #renderResults(container, results) {
    const pass  = results.filter(r => r.status === 'pass').length;
    const fail  = results.filter(r => r.status === 'fail').length;
    const warn  = results.filter(r => r.status === 'warn').length;
    const total = results.length;

    const overall = fail > 0 ? 'fail' : warn > 0 ? 'warn' : 'pass';
    const pct     = Math.round((pass / total) * 100);

    const statusColors = { pass: 'var(--success)', fail: 'var(--error)', warn: 'var(--warning)' };
    const statusIcons  = { pass: 'check_circle',   fail: 'error',        warn: 'warning' };

    container.querySelector('#build-results').innerHTML = `
      <!-- Summary -->
      <div class="card" style="margin-bottom:var(--sp-4)">
        <div class="card-body">
          <div style="display:flex;align-items:center;gap:var(--sp-4)">
            <span class="material-symbols-outlined" style="font-size:40px;color:${statusColors[overall]};font-variation-settings:'FILL' 1,'wght' 400">
              ${statusIcons[overall]}
            </span>
            <div style="flex:1">
              <div style="font-size:var(--text-lg);font-weight:700;color:${statusColors[overall]}">
                ${overall === 'pass' ? 'All Checks Passed' : overall === 'warn' ? 'Warnings Found' : 'Validation Failed'}
              </div>
              <div style="font-size:var(--text-sm);color:var(--text-muted);margin-top:2px">
                ${pass} passed · ${warn} warnings · ${fail} failed · ${total} total
              </div>
            </div>
            <div style="font-size:var(--text-xl);font-weight:700;color:${statusColors[overall]}">${pct}%</div>
          </div>
          <div class="progress-bar" style="margin-top:var(--sp-3)">
            <div class="progress-bar-fill" style="width:${pct}%;background:${statusColors[overall]}"></div>
          </div>
        </div>
      </div>

      <!-- Results list -->
      <div class="card">
        <div class="card-header"><span class="material-symbols-outlined">list</span>Validation Results</div>
        <div class="card-body" style="padding:0">
          ${results.map(r => `
            <div class="settings-row">
              <div class="settings-row-label">
                <div class="settings-row-title">${r.label}</div>
                <div class="settings-row-desc">${r.message}</div>
              </div>
              <span class="material-symbols-outlined"
                    style="color:${statusColors[r.status]};font-variation-settings:'FILL' 1,'wght' 400;flex-shrink:0">
                ${statusIcons[r.status]}
              </span>
            </div>`).join('')}
        </div>
      </div>`;
  }
}
