/**
 * ImportChooserModal.js — Presents users with clear options for importing projects:
 * 1. Import from GitHub Repository (Remote URL, branch selection, git blobs/tree)
 * 2. Import Local Project (ZIP Archive from file system)
 *
 * Preserves 100% of existing local ZIP import while introducing GitHub import.
 */

import { modal } from '../../ui/ModalService.js';
import { zipService } from '../zip/ZipService.js';
import { gitHubImportModal } from './GitHubImportModal.js';

export class ImportChooserModal {
  #backdrop = null;

  /**
   * Open the import destination chooser.
   */
  open() {
    this.#backdrop = modal.open({
      icon: 'upload',
      title: 'Import Project',
      size: 'lg',
      body: `
        <div class="import-chooser-container">
          <p style="color:var(--text-secondary); font-size:var(--text-sm); margin-bottom:var(--sp-4);">
            Select where you would like to import your Android project from:
          </p>

          <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(260px, 1fr)); gap:var(--sp-3);">
            <!-- Option 1: GitHub Repository -->
            <div class="card import-chooser-card" id="import-opt-github"
                 style="padding:var(--sp-4); background:var(--bg-surface); border:1px solid var(--border); border-radius:var(--radius-lg); cursor:pointer; transition:all 0.15s ease;">
              <div style="display:flex; align-items:center; gap:var(--sp-3); margin-bottom:var(--sp-2);">
                <div style="width:40px; height:40px; border-radius:var(--radius-md); background:rgba(59,130,246,0.12); display:flex; align-items:center; justify-content:center; color:var(--accent);">
                  <span class="material-symbols-outlined" style="font-size:22px;">hub</span>
                </div>
                <div>
                  <h4 style="font-size:var(--text-base); font-weight:600; color:var(--text-primary); margin:0;">
                    GitHub Repository
                  </h4>
                  <span class="badge badge-primary" style="font-size:10px; margin-top:2px;">Featured</span>
                </div>
              </div>
              <p style="font-size:var(--text-xs); color:var(--text-secondary); line-height:1.5; margin-bottom:var(--sp-4);">
                Import directly from any public or private GitHub repository via URL. Select branches and load all Gradle files, modules, sources, and resources.
              </p>
              <button type="button" class="btn btn-primary btn-sm btn-block" style="justify-content:center;">
                <span class="material-symbols-outlined">cloud_download</span>
                Import from GitHub
              </button>
            </div>

            <!-- Option 2: Local ZIP Archive -->
            <div class="card import-chooser-card" id="import-opt-zip"
                 style="padding:var(--sp-4); background:var(--bg-surface); border:1px solid var(--border); border-radius:var(--radius-lg); cursor:pointer; transition:all 0.15s ease;">
              <div style="display:flex; align-items:center; gap:var(--sp-3); margin-bottom:var(--sp-2);">
                <div style="width:40px; height:40px; border-radius:var(--radius-md); background:rgba(168,85,247,0.12); display:flex; align-items:center; justify-content:center; color:var(--purple, #a855f7);">
                  <span class="material-symbols-outlined" style="font-size:22px;">folder_zip</span>
                </div>
                <div>
                  <h4 style="font-size:var(--text-base); font-weight:600; color:var(--text-primary); margin:0;">
                    Local ZIP Archive
                  </h4>
                  <span class="badge badge-secondary" style="font-size:10px; margin-top:2px;">File System</span>
                </div>
              </div>
              <p style="font-size:var(--text-xs); color:var(--text-secondary); line-height:1.5; margin-bottom:var(--sp-4);">
                Import an existing Android project archive from your computer. Extracts and analyzes modules, source code, and configurations into the workspace.
              </p>
              <button type="button" class="btn btn-secondary btn-sm btn-block" style="justify-content:center;">
                <span class="material-symbols-outlined">file_open</span>
                Choose Local ZIP
              </button>
            </div>
          </div>
        </div>
      `,
      footer: [
        { label: 'Cancel', type: 'secondary', close: true }
      ]
    });

    // Handle GitHub option click
    this.#backdrop.querySelector('#import-opt-github')?.addEventListener('click', () => {
      this.close();
      gitHubImportModal.open();
    });

    // Handle ZIP option click
    this.#backdrop.querySelector('#import-opt-zip')?.addEventListener('click', () => {
      this.close();
      zipService.pickAndImport();
    });
  }

  close() {
    if (this.#backdrop) {
      modal.close(this.#backdrop);
      this.#backdrop = null;
    }
  }
}

export const importChooserModal = new ImportChooserModal();
