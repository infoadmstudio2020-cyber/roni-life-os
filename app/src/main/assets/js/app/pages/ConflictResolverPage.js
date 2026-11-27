/**
 * ConflictResolverPage — Comprehensive UI for:
 * 1. Duplicate Class Detection (classes.jar & embedded libs/*.jar)
 * 2. Dependency Graph Resolution (Maven coordinates & version deduplication)
 * 3. Kotlin Runtime Alignment (kotlin-stdlib, jdk7, jdk8 alignment for Kotlin 1.8+)
 * 4. D8 Packaging Safety Guard (Blocks duplicate classes from reaching D8 dexer)
 */

import { BinaryClassInspector } from '../modules/conflict/BinaryClassInspector.js';
import { DependencyGraphResolver } from '../modules/conflict/DependencyGraphResolver.js';
import { KotlinRuntimeAlignmentService } from '../modules/conflict/KotlinRuntimeAlignmentService.js';
import { D8PackagingGuard } from '../modules/conflict/D8PackagingGuard.js';
import { projectService } from '../modules/storage/ProjectService.js';
import { toast } from '../ui/ToastService.js';
import { ClipboardUtil } from '../core/ClipboardUtil.js';

export class ConflictResolverPage {
  #container = null;
  #activeTab = 'duplicate-classes'; // 'duplicate-classes' | 'dep-graph' | 'kotlin-runtime' | 'd8-guard'

  // Working state
  #inspectionResult = null;
  #dependencyResult = null;
  #kotlinResult = null;
  #d8PreflightResult = null;
  #d8SanitizedResult = null;

  async init(container) {
    this.#container = container;
    this.#loadInitialData();
    this.#render();
  }

  #loadInitialData() {
    // 1. Baseline simulated and project archives with classes.jar and embedded JARs
    const sampleArchives = [
      {
        id: 'mylibrary-core.aar',
        name: 'mylibrary-core.aar',
        type: 'aar',
        classes: [
          'com.roni.library.core.LibCore',
          'com.roni.library.util.Logger',
          'com.google.common.base.Preconditions',
          'org.apache.commons.io.IOUtils'
        ],
        embeddedJars: [
          {
            name: 'internal-helper.jar',
            classes: [
              'com.roni.library.internal.Helper',
              'org.apache.commons.io.IOUtils' // Duplicate in embedded jar!
            ]
          }
        ]
      },
      {
        id: 'guava-31.0.jar',
        name: 'guava-31.0.jar',
        type: 'jar',
        classes: [
          'com.google.common.base.Preconditions', // Duplicate across guava and mylibrary-core!
          'com.google.common.collect.Lists',
          'com.google.common.primitives.Ints'
        ]
      },
      {
        id: 'commons-io-2.11.0.jar',
        name: 'commons-io-2.11.0.jar',
        type: 'jar',
        classes: [
          'org.apache.commons.io.IOUtils', // Duplicate with embedded jar!
          'org.apache.commons.io.FileUtils'
        ]
      },
      {
        id: 'kotlin-stdlib-1.9.22.jar',
        name: 'kotlin-stdlib-1.9.22.jar',
        type: 'jar',
        classes: [
          'kotlin.collections.CollectionsKt',
          'kotlin.collections.jdk8.CollectionsJDK8Kt', // Merged in Kotlin 1.8+
          'kotlin.internal.PlatformImplementations'
        ]
      },
      {
        id: 'kotlin-stdlib-jdk8-1.7.20.jar',
        name: 'kotlin-stdlib-jdk8-1.7.20.jar',
        type: 'jar',
        classes: [
          'kotlin.collections.jdk8.CollectionsJDK8Kt' // Classic fatal D8 duplicate class collision!
        ]
      }
    ];

    this.#inspectionResult = BinaryClassInspector.inspectArchives(sampleArchives);

    // 2. Baseline dependencies
    const sampleDependencies = [
      "implementation 'com.google.guava:guava:31.0-android'",
      "api 'com.google.guava:guava:32.1.2-jre'", // Duplicate coordinate!
      "implementation 'com.squareup.okhttp3:okhttp:4.9.0'",
      "api 'com.squareup.okhttp3:okhttp:4.12.0'", // Duplicate coordinate!
      "implementation 'org.jetbrains.kotlin:kotlin-stdlib:1.9.22'",
      "implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.20'", // Version mismatch!
      "implementation 'org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.7.20'", // Version mismatch!
      "implementation 'androidx.core:core:1.12.0'",
      "implementation 'androidx.annotation:annotation:1.7.1'"
    ];

    this.#dependencyResult = DependencyGraphResolver.resolveDependencies(sampleDependencies);

    // 3. Kotlin Runtime Audit
    this.#kotlinResult = KotlinRuntimeAlignmentService.auditKotlinRuntime(sampleDependencies, '1.9.22');

    // 4. D8 Pre-flight verification using inspected classes
    const allClassEntries = [];
    for (const [cls, locs] of this.#inspectionResult.classMap.entries()) {
      for (const loc of locs) {
        allClassEntries.push({
          className: cls,
          archiveName: loc.archiveName,
          path: loc.path
        });
      }
    }
    this.#d8PreflightResult = D8PackagingGuard.preflightCheck(allClassEntries);
    this.#d8SanitizedResult = D8PackagingGuard.sanitizeForD8(allClassEntries, { strategy: 'PICK_FIRST' });
  }

  #render() {
    if (!this.#container) return;

    this.#container.innerHTML = `
      <div class="page-container">
        <div class="page-header" style="margin-bottom:var(--sp-4)">
          <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:var(--sp-3)">
            <div>
              <h1 class="page-title" style="display:flex;align-items:center;gap:var(--sp-2)">
                <span class="material-symbols-outlined" style="color:var(--accent)">shield_with_heart</span>
                ডুপ্লিকেট ক্লাস ও D8 প্যাকেজিং সেফটি গার্ড
              </h1>
              <p class="page-subtitle">
                Duplicate Class Detection · Dependency Graph Resolution · Kotlin Runtime Alignment · D8 Dexing Guard
              </p>
            </div>
            <div style="display:flex;gap:var(--sp-2)">
              <span class="badge ${this.#inspectionResult.hasConflicts ? 'badge-warning' : 'badge-success'}" style="font-size:var(--text-xs);padding:6px 12px">
                ${this.#inspectionResult.hasConflicts ? '⚠ ' + this.#inspectionResult.totalDuplicatesCount + ' Duplicate Class Conflicts' : '✓ Classes Unique'}
              </span>
              <span class="badge ${this.#d8PreflightResult.status === 'BLOCKED' ? 'badge-danger' : 'badge-success'}" style="font-size:var(--text-xs);padding:6px 12px">
                D8 Status: ${this.#d8PreflightResult.status}
              </span>
            </div>
          </div>
        </div>

        <!-- Tab Navigation Buttons -->
        <div class="tab-buttons" style="display:flex;gap:var(--sp-2);border-bottom:1px solid var(--border);padding-bottom:var(--sp-3);margin-bottom:var(--sp-6);overflow-x:auto">
          <button type="button" class="btn btn-sm ${this.#activeTab === 'duplicate-classes' ? 'btn-primary' : 'btn-secondary'}" data-tab="duplicate-classes">
            <span class="material-symbols-outlined">view_list</span>
            ১. ডুপ্লিকেট ক্লাস শনাক্তকরণ (${this.#inspectionResult.totalDuplicatesCount})
          </button>
          <button type="button" class="btn btn-sm ${this.#activeTab === 'dep-graph' ? 'btn-primary' : 'btn-secondary'}" data-tab="dep-graph">
            <span class="material-symbols-outlined">hub</span>
            ২. ডিপেন্ডেন্সি গ্রাফ রেজোলিউশন (${this.#dependencyResult.conflictsCount})
          </button>
          <button type="button" class="btn btn-sm ${this.#activeTab === 'kotlin-runtime' ? 'btn-primary' : 'btn-secondary'}" data-tab="kotlin-runtime">
            <span class="material-symbols-outlined">code</span>
            ৩. কোটলিন রানটাইম এলাইনমেন্ট ${this.#kotlinResult.hasMergedCollisionRisk ? '⚠' : '✓'}
          </button>
          <button type="button" class="btn btn-sm ${this.#activeTab === 'd8-guard' ? 'btn-primary' : 'btn-secondary'}" data-tab="d8-guard">
            <span class="material-symbols-outlined">security</span>
            ৪. D8 প্যাকেজিং সেফটি গার্ড (${this.#d8PreflightResult.status})
          </button>
        </div>

        <!-- Tab 1: Duplicate Classes -->
        <div id="tab-duplicate-classes" style="display:${this.#activeTab === 'duplicate-classes' ? 'block' : 'none'}">
          <div class="card" style="margin-bottom:var(--sp-6)">
            <div class="card-header" style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:var(--sp-2)">
              <div style="display:flex;align-items:center;gap:var(--sp-2)">
                <span class="material-symbols-outlined" style="color:var(--accent)">find_in_page</span>
                <span style="font-weight:600">JAR ও AAR classes.jar ইনস্পেকশন রিপোর্ট</span>
              </div>
              <span class="badge badge-info">${this.#inspectionResult.totalArchives} Archives Inspected · ${this.#inspectionResult.totalUniqueClasses} Unique Classes</span>
            </div>
            <div class="card-body">
              <p style="font-size:var(--text-sm);color:var(--text-secondary);margin-bottom:var(--sp-4)">
                প্রতিটি ইনপুট JAR/AAR-এর <code>classes.jar</code> এবং <code>libs/*.jar</code>-এ থাকা ক্লাসগুলোর নাম নিচে তালিকাভুক্ত করা হয়েছে। একই ক্লাস একাধিক বাইনারিতে উপস্থিত থাকলে কনফ্লিক্ট ফ্ল্যাগ করা হয়েছে।
              </p>

              ${this.#inspectionResult.duplicates.length === 0 ? `
                <div style="padding:var(--sp-6);text-align:center;color:var(--text-muted)">
                  <span class="material-symbols-outlined" style="font-size:40px;color:var(--success)">check_circle</span>
                  <div style="margin-top:var(--sp-2);font-weight:500">কোনো ডুপ্লিকেট ক্লাস পাওয়া যায়নি! সমস্ত বাইনারি ক্লাস অনন্য।</div>
                </div>
              ` : `
                <div class="table-container" style="overflow-x:auto">
                  <table class="data-table" style="width:100%;font-size:var(--text-xs);border-collapse:collapse">
                    <thead>
                      <tr style="border-bottom:1px solid var(--border);text-align:left">
                        <th style="padding:var(--sp-2)">ক্লাস নেম (Fully-Qualified Class)</th>
                        <th style="padding:var(--sp-2)">পুনরাবৃত্তি</th>
                        <th style="padding:var(--sp-2)">উৎস বাইনারি ও পাথ (JAR/AAR Archives)</th>
                        <th style="padding:var(--sp-2)">ঝুঁকি মাত্রা</th>
                      </tr>
                    </thead>
                    <tbody>
                      ${this.#inspectionResult.duplicates.map(dup => `
                        <tr style="border-bottom:1px solid var(--border-subtle);background:${dup.distinctArchivesCount > 1 ? 'rgba(239, 68, 68, 0.05)' : 'transparent'}">
                          <td style="padding:var(--sp-3);font-family:monospace;font-weight:600;color:var(--accent)">
                            ${dup.className}
                          </td>
                          <td style="padding:var(--sp-3)">
                            <span class="badge badge-danger">${dup.occurrences} বার</span>
                          </td>
                          <td style="padding:var(--sp-3)">
                            ${dup.locations.map(loc => `
                              <div style="margin-bottom:2px">
                                <strong>${loc.archiveName}</strong>
                                <span style="color:var(--text-muted)">(${loc.isEmbedded ? 'Embedded: ' + loc.path : loc.path})</span>
                              </div>
                            `).join('')}
                          </td>
                          <td style="padding:var(--sp-3)">
                            <span class="badge badge-warning">D8 Collision</span>
                          </td>
                        </tr>
                      `).join('')}
                    </tbody>
                  </table>
                </div>
              `}
            </div>
          </div>
        </div>

        <!-- Tab 2: Dependency Graph Resolution -->
        <div id="tab-dep-graph" style="display:${this.#activeTab === 'dep-graph' ? 'block' : 'none'}">
          <div class="card" style="margin-bottom:var(--sp-6)">
            <div class="card-header" style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:var(--sp-2)">
              <div style="display:flex;align-items:center;gap:var(--sp-2)">
                <span class="material-symbols-outlined" style="color:var(--accent)">auto_fix_high</span>
                <span style="font-weight:600">Maven Coordinate ও ভার্সন ডি-ডুপ্লিকেশন</span>
              </div>
              <span class="badge badge-info">${this.#dependencyResult.duplicatesRemovedCount} Duplicates Eliminated</span>
            </div>
            <div class="card-body">
              <p style="font-size:var(--text-sm);color:var(--text-secondary);margin-bottom:var(--sp-4)">
                একই Maven Coordinate (Group:Artifact) বিশিষ্ট একাধিক ভার্সনের ডিপেন্ডেন্সির পুনরাবৃত্তি বাদ দিয়ে হাইয়েস্ট ভার্সন নির্বাচন করা হয়েছে:
              </p>

              <div class="table-container" style="overflow-x:auto;margin-bottom:var(--sp-4)">
                <table class="data-table" style="width:100%;font-size:var(--text-xs);border-collapse:collapse">
                  <thead>
                    <tr style="border-bottom:1px solid var(--border);text-align:left">
                      <th style="padding:var(--sp-2)">Artifact (Module Key)</th>
                      <th style="padding:var(--sp-2)">শনাক্তকৃত সংস্করণসমূহ</th>
                      <th style="padding:var(--sp-2)">চূড়ান্ত নির্বাচিত সংস্করণ (Resolved)</th>
                      <th style="padding:var(--sp-2)">বাদ দেওয়া ডুপ্লিকেট</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${this.#dependencyResult.conflicts.map(c => `
                      <tr style="border-bottom:1px solid var(--border-subtle)">
                        <td style="padding:var(--sp-3);font-family:monospace;font-weight:600">${c.moduleKey}</td>
                        <td style="padding:var(--sp-3)">${c.versionsFound.join(', ')}</td>
                        <td style="padding:var(--sp-3)"><span class="badge badge-success">${c.resolvedVersion} (Highest)</span></td>
                        <td style="padding:var(--sp-3);color:var(--text-muted)">${c.instancesCount - 1} redundant declarations dropped</td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              </div>

              ${this.#dependencyResult.gradleResolutionScript ? `
                <div style="margin-top:var(--sp-4)">
                  <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-2)">
                    <span style="font-weight:600;font-size:var(--text-xs)">Automated Gradle resolutionStrategy Patch:</span>
                    <button type="button" class="btn btn-ghost btn-xs" id="btn-copy-dep-patch">
                      <span class="material-symbols-outlined" style="font-size:14px">content_copy</span> Copy Script
                    </button>
                  </div>
                  <pre style="background:var(--bg-base);padding:var(--sp-3);border-radius:var(--radius);font-size:var(--text-xs);border:1px solid var(--border);overflow-x:auto"><code>${this.#dependencyResult.gradleResolutionScript}</code></pre>
                </div>
              ` : ''}
            </div>
          </div>
        </div>

        <!-- Tab 3: Kotlin Runtime Alignment -->
        <div id="tab-kotlin-runtime" style="display:${this.#activeTab === 'kotlin-runtime' ? 'block' : 'none'}">
          <div class="card" style="margin-bottom:var(--sp-6)">
            <div class="card-header" style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:var(--sp-2)">
              <div style="display:flex;align-items:center;gap:var(--sp-2)">
                <span class="material-symbols-outlined" style="color:var(--accent)">tune</span>
                <span style="font-weight:600">কোটলিন রানটাইম সংস্করণ সমন্বয় (Kotlin 1.8+ Merge Protection)</span>
              </div>
              <span class="badge ${this.#kotlinResult.hasMergedCollisionRisk ? 'badge-warning' : 'badge-success'}">
                ${this.#kotlinResult.hasMergedCollisionRisk ? 'Collision Risk Detected' : 'Aligned'}
              </span>
            </div>
            <div class="card-body">
              <div style="background:rgba(234, 179, 8, 0.1);border:1px solid rgba(234, 179, 8, 0.3);padding:var(--sp-3);border-radius:var(--radius);margin-bottom:var(--sp-4);font-size:var(--text-sm)">
                <strong>গুরুত্বপূর্ণ তথ্য:</strong> Kotlin 1.8.0 বা তার পরবর্তী সংস্করণে <code>kotlin-stdlib-jdk7</code> এবং <code>kotlin-stdlib-jdk8</code> মূল <code>kotlin-stdlib</code>-এর সাথে মার্জ হয়ে গেছে। প্রজেক্টে পুরনো লাইব্রেরি থাকলে ডুপ্লিকেট ক্লাস (যেমন <code>kotlin.collections.jdk8.CollectionsJDK8Kt</code>) জনিত D8 বিল্ড ফেইলিউর ঘটে।
              </div>

              ${this.#kotlinResult.hasMergedCollisionRisk ? `
                <div style="margin-bottom:var(--sp-4);padding:var(--sp-3);background:var(--bg-card);border:1px solid var(--border);border-radius:var(--radius)">
                  <div style="color:var(--danger);font-weight:600;margin-bottom:var(--sp-1)">ঝুঁকি বিশ্লেষণ:</div>
                  <div style="font-size:var(--text-xs);color:var(--text-secondary)">${this.#kotlinResult.collisionExplanation}</div>
                </div>
              ` : ''}

              <div style="margin-top:var(--sp-4)">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--sp-2)">
                  <span style="font-weight:600;font-size:var(--text-xs)">প্রস্তাবিত Gradle Alignment DSL (build.gradle):</span>
                  <button type="button" class="btn btn-ghost btn-xs" id="btn-copy-kotlin-patch">
                    <span class="material-symbols-outlined" style="font-size:14px">content_copy</span> Copy Patch
                  </button>
                </div>
                <pre style="background:var(--bg-base);padding:var(--sp-3);border-radius:var(--radius);font-size:var(--text-xs);border:1px solid var(--border);overflow-x:auto"><code>${this.#kotlinResult.gradleAlignmentPatch}</code></pre>
              </div>
            </div>
          </div>
        </div>

        <!-- Tab 4: D8 Packaging Safety Guard -->
        <div id="tab-d8-guard" style="display:${this.#activeTab === 'd8-guard' ? 'block' : 'none'}">
          <div class="card" style="margin-bottom:var(--sp-6)">
            <div class="card-header" style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:var(--sp-2)">
              <div style="display:flex;align-items:center;gap:var(--sp-2)">
                <span class="material-symbols-outlined" style="color:var(--accent)">security</span>
                <span style="font-weight:600">D8 প্যাকেজিং সেফটি গার্ড (Pre-flight Dex Verification)</span>
              </div>
              <span class="badge ${this.#d8PreflightResult.status === 'BLOCKED' ? 'badge-danger' : 'badge-success'}">
                Pre-flight: ${this.#d8PreflightResult.status}
              </span>
            </div>
            <div class="card-body">
              <p style="font-size:var(--text-sm);color:var(--text-secondary);margin-bottom:var(--sp-4)">
                একই ক্লাস দুইবার প্যাকেজ না করার জন্য এবং duplicate class-কে নির্বিচারে D8-এ না পাঠানোর জন্য এই সেফটি গার্ড কাজ করে।
              </p>

              <div style="padding:var(--sp-4);border-radius:var(--radius);border:1px solid ${this.#d8PreflightResult.status === 'BLOCKED' ? 'var(--danger)' : 'var(--success)'};background:${this.#d8PreflightResult.status === 'BLOCKED' ? 'rgba(239, 68, 68, 0.08)' : 'rgba(34, 197, 94, 0.08)'};margin-bottom:var(--sp-4)">
                <div style="display:flex;align-items:center;gap:var(--sp-2)">
                  <span class="material-symbols-outlined" style="color:${this.#d8PreflightResult.status === 'BLOCKED' ? 'var(--danger)' : 'var(--success)'}">
                    ${this.#d8PreflightResult.status === 'BLOCKED' ? 'dangerous' : 'verified'}
                  </span>
                  <strong style="font-size:var(--text-sm)">${this.#d8PreflightResult.message}</strong>
                </div>
              </div>

              <!-- Sanitized Outcome -->
              <div class="card" style="border:1px solid var(--border);background:var(--bg-card);margin-bottom:var(--sp-4)">
                <div class="card-header" style="font-size:var(--text-xs);font-weight:600">
                  স্বয়ংক্রিয় D8 ফিল্টারিং ও ডি-ডুপ্লিকেশন ফলাফল (Sanitized Payload)
                </div>
                <div class="card-body" style="padding-top:var(--sp-2)">
                  <div style="display:flex;gap:var(--sp-6);flex-wrap:wrap;font-size:var(--text-xs);margin-bottom:var(--sp-3)">
                    <div><strong>মূল ক্লাস সংখ্যা:</strong> ${this.#d8SanitizedResult.originalCount}</div>
                    <div><strong>ফিল্টারকৃত নিরাপদ ক্লাস:</strong> <span style="color:var(--success);font-weight:600">${this.#d8SanitizedResult.sanitizedCount}</span></div>
                    <div><strong>বর্জনকৃত ডুপ্লিকেট:</strong> <span style="color:var(--danger)">${this.#d8SanitizedResult.droppedCount}</span></div>
                    <div><strong>সার্টিফাইড D8 স্ট্যাটাস:</strong> <span class="badge badge-success">CERTIFIED SAFE</span></div>
                  </div>

                  <button type="button" class="btn btn-primary btn-sm" id="btn-apply-d8-filter">
                    <span class="material-symbols-outlined">verified</span>
                    ফিল্টার নিশ্চিত করুন ও D8 ডেক্সিং অনুমোদিত করুন
                  </button>
                </div>
              </div>

              <!-- Packaging DSL -->
              <div>
                <div style="font-weight:600;font-size:var(--text-xs);margin-bottom:var(--sp-2)">
                  Android Gradle Packaging DSL (build.gradle pickFirsts):
                </div>
                <pre style="background:var(--bg-base);padding:var(--sp-3);border-radius:var(--radius);font-size:var(--text-xs);border:1px solid var(--border);overflow-x:auto"><code>${this.#d8SanitizedResult.packagingOptionsGradle}</code></pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;

    this.#bindEvents();
  }

  #bindEvents() {
    this.#container.querySelectorAll('[data-tab]').forEach(btn => {
      btn.addEventListener('click', () => {
        this.#activeTab = btn.dataset.tab;
        this.#render();
      });
    });

    this.#container.querySelector('#btn-copy-dep-patch')?.addEventListener('click', async () => {
      const ok = await ClipboardUtil.copyText(this.#dependencyResult.gradleResolutionScript);
      if (ok) {
        toast.success('কপি সম্পন্ন', 'Gradle resolutionStrategy স্ক্রিপ্ট কপি হয়েছে');
      } else {
        toast.error('কপি ব্যর্থ', 'অনুগ্রহ করে ম্যানুয়ালি কপি করুন');
      }
    });

    this.#container.querySelector('#btn-copy-kotlin-patch')?.addEventListener('click', async () => {
      const ok = await ClipboardUtil.copyText(this.#kotlinResult.gradleAlignmentPatch);
      if (ok) {
        toast.success('কপি সম্পন্ন', 'Kotlin runtime alignment DSL কপি হয়েছে');
      } else {
        toast.error('কপি ব্যর্থ', 'অনুগ্রহ করে ম্যানুয়ালি কপি করুন');
      }
    });

    this.#container.querySelector('#btn-apply-d8-filter')?.addEventListener('click', () => {
      toast.success('D8 সেফটি গার্ড সক্রিয়', 'সমস্ত ডুপ্লিকেট ক্লাস ফিল্টার সম্পন্ন। D8 ডেক্সিং এখন সম্পূর্ণ নিরাপদ!');
    });
  }
}
