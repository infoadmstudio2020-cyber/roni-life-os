/**
 * ConsumerResolutionModal.js — Interactive Consumer Dependency Resolution
 * Test & Verification Report Modal.
 *
 * Provides developers with an instant audit of:
 * 1. Consumer Compile Classpath (Direct + API transitive only)
 * 2. Consumer Runtime Classpath (All transitive dependencies packaged into APK/AAB)
 * 3. Multi-Module Project publication and inter-module dependencies
 * 4. Repository declarations (Google, MavenCentral, JitPack)
 * 5. AGP & Gradle compatibility matrix
 * 6. Generated POM (pom.xml) and Gradle Module Metadata (.module) previews
 */

import { modal } from '../../ui/ModalService.js';
import { toast } from '../../ui/ToastService.js';
import { projectService } from '../storage/ProjectService.js';
import { consoleService } from '../storage/ConsoleService.js';
import { TransitiveDependencyEngine } from './TransitiveDependencyEngine.js';
import { MavenPublishGenerator } from '../generator/MavenPublishGenerator.js';
import { zipService } from '../zip/ZipService.js';

export class ConsumerResolutionModal {
  /**
   * Open the Consumer Resolution Test & Verification Report modal.
   * @param {object} [opts]
   */
  static show(opts = {}) {
    const project = projectService.current;
    if (!project) {
      toast.error('No Project', 'Please open or create a project first');
      return;
    }

    const files = project.files || {};
    const detected = MavenPublishGenerator.detectMetadata(files, project);
    const report = TransitiveDependencyEngine.runConsumerVerification(files, {
      ...detected,
      ...opts
    });

    const isPassed = report.status === 'passed';
    const isWarn = report.status === 'warning';
    const badgeClass = isPassed ? 'badge-success' : isWarn ? 'badge-warning' : 'badge-error';
    const statusText = isPassed ? 'ALL CHECKS PASSED' : isWarn ? 'PASSED WITH WARNINGS' : 'CONFIGURATION ISSUES';

    const modalId = 'consumer-resolution-dialog';

    modal.show({
      title: 'Consumer Dependency Resolution Test & Verification Report',
      content: `
        <div id="${modalId}" style="display:flex;flex-direction:column;gap:16px;max-height:75vh;overflow-y:auto;padding-right:4px">
          <!-- Status Banner -->
          <div style="display:flex;align-items:center;justify-content:space-between;padding:12px 16px;background:var(--bg-surface);border:1px solid var(--border);border-radius:6px;flex-wrap:wrap;gap:8px">
            <div>
              <div style="display:flex;align-items:center;gap:8px">
                <span class="badge ${badgeClass}" style="font-size:12px;font-weight:700;padding:2px 8px">${statusText}</span>
                <strong style="font-size:14px">Public JitPack & Maven Transitive Resolution</strong>
              </div>
              <p style="font-size:12px;color:var(--text-secondary);margin:4px 0 0 0">${report.summary}</p>
            </div>
            <div style="font-size:11px;color:var(--text-muted);text-align:right">
              <div>Target: <code>${detected.groupId}:${detected.artifactId}:${detected.version}</code></div>
              <div>AGP: <code>${report.compatibility.agpVersion}</code> | Gradle: <code>${report.compatibility.gradleVersion}</code></div>
            </div>
          </div>

          <!-- Audit Check Items -->
          <div>
            <h4 style="font-size:12px;font-weight:700;text-transform:uppercase;color:var(--text-secondary);margin:0 0 8px 0;letter-spacing:0.5px">
              Compatibility & Publication Checks
            </h4>
            <div style="display:flex;flex-direction:column;gap:6px">
              ${report.checks.map(c => {
                const icon = c.status === 'pass' ? 'check_circle' : c.status === 'warn' ? 'warning' : 'cancel';
                const color = c.status === 'pass' ? 'var(--success)' : c.status === 'warn' ? 'var(--warning)' : 'var(--error)';
                return `
                  <div style="display:flex;align-items:flex-start;gap:8px;padding:8px 12px;background:var(--bg-muted);border-radius:4px;font-size:12px">
                    <span class="material-symbols-outlined" style="color:${color};font-size:16px;margin-top:1px;flex-shrink:0">${icon}</span>
                    <div style="flex:1;min-width:0">
                      <strong>${c.title}</strong>
                      <div style="color:var(--text-muted);font-size:11px;margin-top:2px">${c.message}</div>
                    </div>
                  </div>
                `;
              }).join('')}
            </div>
          </div>

          <!-- Consumer Resolution Simulation (Classpath View) -->
          <div>
            <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
              <h4 style="font-size:12px;font-weight:700;text-transform:uppercase;color:var(--text-secondary);margin:0;letter-spacing:0.5px">
                Simulated Consumer Classpath Resolution
              </h4>
              <span style="font-size:11px;color:var(--text-muted)">
                Consumer: <code>implementation '${report.consumerSnippets.coordinate}'</code>
              </span>
            </div>

            <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(280px, 1fr));gap:12px">
              <!-- Compile Classpath Card -->
              <div style="border:1px solid var(--border);border-radius:6px;padding:10px;background:var(--bg-surface)">
                <div style="display:flex;align-items:center;gap:6px;margin-bottom:8px">
                  <span class="material-symbols-outlined" style="font-size:16px;color:var(--accent)">code</span>
                  <strong style="font-size:12px">Consumer Compile Classpath</strong>
                  <span class="badge" style="font-size:10px;margin-left:auto">${report.consumerClasspath.compileClasspath.length} items</span>
                </div>
                <div style="font-size:11px;color:var(--text-muted);margin-bottom:6px">
                  Visible to consumer Java/Kotlin code. Includes direct AAR and <code>api</code> dependencies only.
                </div>
                <div style="display:flex;flex-direction:column;gap:4px;max-height:140px;overflow-y:auto">
                  ${report.consumerClasspath.compileClasspath.map(item => `
                    <div style="padding:4px 6px;background:var(--bg-muted);border-radius:3px;font-size:11px;display:flex;align-items:center;justify-content:space-between">
                      <code style="color:var(--accent)">${item.coordinate}</code>
                      <span class="badge badge-info" style="font-size:9px">${item.type}</span>
                    </div>
                  `).join('')}
                </div>
              </div>

              <!-- Runtime Classpath Card -->
              <div style="border:1px solid var(--border);border-radius:6px;padding:10px;background:var(--bg-surface)">
                <div style="display:flex;align-items:center;gap:6px;margin-bottom:8px">
                  <span class="material-symbols-outlined" style="font-size:16px;color:var(--success)">devices</span>
                  <strong style="font-size:12px">Consumer Runtime Classpath (APK/AAB)</strong>
                  <span class="badge" style="font-size:10px;margin-left:auto">${report.consumerClasspath.runtimeClasspath.length} items</span>
                </div>
                <div style="font-size:11px;color:var(--text-muted);margin-bottom:6px">
                  Bundled into consumer final APK. Includes all <code>api</code> and encapsulated <code>implementation</code> dependencies.
                </div>
                <div style="display:flex;flex-direction:column;gap:4px;max-height:140px;overflow-y:auto">
                  ${report.consumerClasspath.runtimeClasspath.map(item => `
                    <div style="padding:4px 6px;background:var(--bg-muted);border-radius:3px;font-size:11px;display:flex;align-items:center;justify-content:space-between">
                      <code style="color:var(--success)">${item.coordinate}</code>
                      <span class="badge" style="font-size:9px">${item.scope}</span>
                    </div>
                  `).join('')}
                </div>
              </div>
            </div>
          </div>

          <!-- Multi-Module Status Section -->
          ${report.multiModuleStatus.isMultiModule ? `
            <div style="border:1px solid var(--border);border-radius:6px;padding:10px;background:var(--bg-surface)">
              <div style="display:flex;align-items:center;gap:6px;margin-bottom:6px">
                <span class="material-symbols-outlined" style="font-size:16px;color:var(--warning)">layers</span>
                <strong style="font-size:12px">Multi-Module Project Architecture</strong>
                <span class="badge badge-warning" style="font-size:10px;margin-left:auto">${report.multiModuleStatus.modules.length} Modules</span>
              </div>
              <div style="font-size:11px;color:var(--text-muted);margin-bottom:6px">
                Detected inter-module subprojects. All published library modules are automatically configured for JitPack compilation.
              </div>
              <div style="display:flex;gap:6px;flex-wrap:wrap">
                ${report.multiModuleStatus.modules.map(m => `
                  <span class="badge" style="font-size:11px;padding:3px 8px">
                    :${m.name} ${m.isLibrary ? '(AAR Library)' : m.isApp ? '(App)' : ''}
                  </span>
                `).join('')}
              </div>
            </div>
          ` : ''}

          <!-- Metadata & POM Preview Toggle -->
          <details style="border:1px solid var(--border);border-radius:6px;padding:8px 12px;background:var(--bg-surface)">
            <summary style="font-size:12px;font-weight:600;cursor:pointer;color:var(--accent)">
              View Generated Maven POM (pom.xml with compile/runtime scopes)
            </summary>
            <pre style="margin-top:8px;padding:8px;background:var(--bg-muted);border-radius:4px;font-size:11px;overflow-x:auto;max-height:160px">${this.#esc(report.pomXmlPreview)}</pre>
          </details>

          <details style="border:1px solid var(--border);border-radius:6px;padding:8px 12px;background:var(--bg-surface)">
            <summary style="font-size:12px;font-weight:600;cursor:pointer;color:var(--accent)">
              View Generated Gradle Module Metadata (.module v1.1)
            </summary>
            <pre style="margin-top:8px;padding:8px;background:var(--bg-muted);border-radius:4px;font-size:11px;overflow-x:auto;max-height:160px">${this.#esc(report.moduleMetadataPreview)}</pre>
          </details>
        </div>
      `,
      buttons: [
        {
          label: 'Close',
          variant: 'secondary',
          onClick: () => modal.close()
        },
        {
          label: 'Auto-Sync & Apply Files',
          variant: 'primary',
          onClick: async () => {
            try {
              const res = await MavenPublishGenerator.applyToProject(projectService, detected);
              toast.success('Project Synced', `Updated ${res.updatedFiles.length} file(s) for Maven & JitPack publication.`);
              consoleService.success(`Applied Maven & Transitive Dependency files: ${res.updatedFiles.join(', ')}`);
              modal.close();
            } catch (err) {
              toast.error('Sync Failed', err.message);
            }
          }
        },
        {
          label: 'Download Project ZIP',
          variant: 'secondary',
          onClick: async () => {
            try {
              await zipService.exportProject(projectService.current);
              toast.success('ZIP Downloaded', 'Updated project archive exported.');
            } catch (err) {
              toast.error('Download Failed', err.message);
            }
          }
        }
      ]
    });
  }

  static #esc(str = '') {
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
}
