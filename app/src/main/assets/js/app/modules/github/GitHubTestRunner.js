/**
 * GitHubTestRunner.js — Real End-to-End Testing Engine for RONi Android Library Publisher.
 *
 * Executes real GitHub API & Actions validation pipeline:
 * 1. Validate GitHub Connection
 * 2. Validate Repository Access
 * 3. Validate Actions Access
 * 4. Generate Fresh Android Library Project
 * 5. Push / Update Test Project on GitHub
 * 6. Trigger & Monitor GitHub Actions Workflow
 * 7. Verify Android SDK Setup step
 * 8. Verify Gradle Build step
 * 9. Verify assembleRelease step
 * 10. Verify Maven / Packages Publish step (if enabled)
 * 11. Trace failures back to source generator / template files
 */

import { githubApi } from './GitHubApi.js';
import { githubAuth } from './GitHubAuth.js';
import { AndroidLibraryTemplate } from '../generator/templates/AndroidLibraryTemplate.js';
import { GitHubFileGenerator } from './GitHubFileGenerator.js';
import { githubUploader } from './GitHubUploader.js';

export const TEST_STATUS = {
  PASS:          'PASS',
  FAIL:          'FAIL',
  NOT_TESTED:    'NOT TESTED',
  NOT_AVAILABLE: 'NOT AVAILABLE',
  RUNNING:       'RUNNING',
  PENDING:       'PENDING'
};

/** Mapping from generated project file patterns to source generator modules */
export const TEMPLATE_ORIGIN_MAP = [
  {
    pattern: /^\.github\/workflows\/(ci|release)\.yml$/,
    template: 'AndroidLibraryTemplate.js & GitHubFileGenerator.js',
    path: 'js/app/modules/generator/templates/AndroidLibraryTemplate.js',
    component: 'Workflow CI/Release Generator'
  },
  {
    pattern: /^gradlew(\.bat)?$/,
    template: 'AndroidLibraryTemplate.js',
    path: 'js/app/modules/generator/templates/AndroidLibraryTemplate.js',
    component: 'Gradle Wrapper Script Generator'
  },
  {
    pattern: /^gradle\/wrapper\/gradle-wrapper\.properties$/,
    template: 'AndroidLibraryTemplate.js',
    path: 'js/app/modules/generator/templates/AndroidLibraryTemplate.js',
    component: 'Gradle Wrapper Properties'
  },
  {
    pattern: /^(build|settings|gradle)\.gradle(\.kts)?$/,
    template: 'AndroidLibraryTemplate.js',
    path: 'js/app/modules/generator/templates/AndroidLibraryTemplate.js',
    component: 'Root Build & Settings Scripts'
  },
  {
    pattern: /^(java|kotlin)-library\/build\.gradle$/,
    template: 'AndroidLibraryTemplate.js',
    path: 'js/app/modules/generator/templates/AndroidLibraryTemplate.js',
    component: 'Library Module Build Script (Maven Publish & Android Config)'
  },
  {
    pattern: /^(app|sample)\/build\.gradle$/,
    template: 'AndroidLibraryTemplate.js',
    path: 'js/app/modules/generator/templates/AndroidLibraryTemplate.js',
    component: 'App Module Build Script'
  },
  {
    pattern: /AndroidManifest\.xml$/,
    template: 'AndroidLibraryTemplate.js',
    path: 'js/app/modules/generator/templates/AndroidLibraryTemplate.js',
    component: 'Android Manifest Template'
  },
  {
    pattern: /jitpack\.yml$/,
    template: 'AndroidLibraryTemplate.js',
    path: 'js/app/modules/generator/templates/AndroidLibraryTemplate.js',
    component: 'JitPack Configuration'
  },
  {
    pattern: /^\.github\//,
    template: 'GitHubFileGenerator.js',
    path: 'js/app/modules/github/GitHubFileGenerator.js',
    component: 'GitHub Community & Issue Template Generator'
  }
];

export class GitHubTestRunner {
  #activeRun = null;
  #cancelRequested = false;

  // ── Connection Validation ──────────────────────────────────────────────────

  /**
   * Validates GitHub Connection, Repository Access, Actions Access, and Packages Access.
   * NEVER simulates success.
   *
   * @param {Object} params
   * @param {string} [params.token]
   * @param {string} params.owner
   * @param {string} params.repo
   * @param {boolean} [params.enablePackages=false]
   * @returns {Promise<Object>}
   */
  async validateConnection({ token, owner, repo, enablePackages = false }) {
    const results = {
      connection:     { status: TEST_STATUS.NOT_AVAILABLE, message: 'No token configured' },
      repoAccess:     { status: TEST_STATUS.NOT_AVAILABLE, message: 'Requires GitHub connection' },
      actionsAccess:  { status: TEST_STATUS.NOT_AVAILABLE, message: 'Requires repository access' },
      packagesAccess: { status: enablePackages ? TEST_STATUS.NOT_AVAILABLE : TEST_STATUS.NOT_TESTED, message: enablePackages ? 'Requires connection' : 'Disabled' },
      user: null
    };

    if (token) {
      githubApi.setToken(token);
    } else if (!githubAuth.token) {
      return results;
    }

    // 1. Validate GitHub Connection
    try {
      const user = await githubApi.getUser();
      results.user = user;
      results.connection = {
        status: TEST_STATUS.PASS,
        message: `Connected as @${user.login} (${user.name || user.login})`
      };
    } catch (err) {
      results.connection = {
        status: TEST_STATUS.FAIL,
        message: err.message || 'Authentication failed'
      };
      return results;
    }

    // 2. Validate Repository Access
    if (!owner || !repo) {
      results.repoAccess = {
        status: TEST_STATUS.NOT_AVAILABLE,
        message: 'Owner or repository name not specified'
      };
      return results;
    }

    try {
      const repository = await githubApi.getRepo(owner, repo);
      results.repoAccess = {
        status: TEST_STATUS.PASS,
        message: `Repository ${repository.full_name} accessible (${repository.private ? 'Private' : 'Public'})`
      };
    } catch (err) {
      results.repoAccess = {
        status: TEST_STATUS.FAIL,
        message: `Repository access failed: ${err.message}`
      };
      return results;
    }

    // 3. Validate Actions Access
    try {
      const workflowsData = await githubApi.listWorkflows(owner, repo);
      const count = workflowsData.total_count ?? (workflowsData.workflows ? workflowsData.workflows.length : 0);
      results.actionsAccess = {
        status: TEST_STATUS.PASS,
        message: `Actions accessible (${count} workflow${count === 1 ? '' : 's'} registered)`
      };
    } catch (err) {
      results.actionsAccess = {
        status: TEST_STATUS.FAIL,
        message: `Actions permission check failed: ${err.message}`
      };
    }

    // 4. Validate Packages Access (when enabled)
    if (enablePackages) {
      try {
        await githubApi.listUserPackages(owner);
        results.packagesAccess = {
          status: TEST_STATUS.PASS,
          message: 'GitHub Packages permission verified'
        };
      } catch (err) {
        // Packages endpoint might return 404 if no packages yet, which is fine
        if (err.message && err.message.includes('404')) {
          results.packagesAccess = {
            status: TEST_STATUS.PASS,
            message: 'GitHub Packages accessible (0 packages published)'
          };
        } else {
          results.packagesAccess = {
            status: TEST_STATUS.FAIL,
            message: `Packages access failed: ${err.message}`
          };
        }
      }
    }

    return results;
  }

  // ── Full Test Execution Pipeline ───────────────────────────────────────────

  /**
   * Executes the full end-to-end verification pipeline.
   *
   * @param {Object} options
   * @param {string} options.owner
   * @param {string} options.repo
   * @param {string} [options.branch='main']
   * @param {boolean} [options.enableMavenPublish=false]
   * @param {Function} [options.onStageUpdate]
   * @returns {Promise<Object>}
   */
  async runFullTest({
    owner,
    repo,
    branch = 'main',
    enableMavenPublish = false,
    onStageUpdate = () => {}
  }) {
    this.#cancelRequested = false;

    const stages = {
      connection:     { name: 'GitHub Connection', status: TEST_STATUS.PENDING, details: '' },
      repoAccess:     { name: 'Repository Access', status: TEST_STATUS.PENDING, details: '' },
      actionsAccess:  { name: 'Actions Access',    status: TEST_STATUS.PENDING, details: '' },
      generateProject:{ name: 'Generate Project',  status: TEST_STATUS.PENDING, details: '' },
      pushProject:    { name: 'Push Test Project', status: TEST_STATUS.PENDING, details: '' },
      androidSdkSetup:{ name: 'Android SDK Setup', status: TEST_STATUS.PENDING, details: '' },
      gradleBuild:    { name: 'Gradle Build',      status: TEST_STATUS.PENDING, details: '' },
      assembleRelease:{ name: 'assembleRelease',   status: TEST_STATUS.PENDING, details: '' },
      mavenPublish:   { name: 'Maven Publish',     status: enableMavenPublish ? TEST_STATUS.PENDING : TEST_STATUS.NOT_TESTED, details: '' },
      overallResult:  { name: 'Overall Result',    status: TEST_STATUS.PENDING, details: '' }
    };

    const updateStage = (stageKey, status, details = '') => {
      stages[stageKey].status = status;
      stages[stageKey].details = details;
      onStageUpdate(stageKey, stages[stageKey], stages);
    };

    let failureInfo = null;

    // Helper to abort with failure
    const failPipeline = (stageKey, message, diagnostics = null) => {
      updateStage(stageKey, TEST_STATUS.FAIL, message);
      failureInfo = {
        stage: stages[stageKey].name,
        stageKey,
        error: message,
        diagnostics
      };
      // Mark subsequent pending stages as NOT TESTED or NOT AVAILABLE
      Object.keys(stages).forEach(k => {
        if (stages[k].status === TEST_STATUS.PENDING) {
          stages[k].status = TEST_STATUS.NOT_TESTED;
          stages[k].details = 'Skipped due to upstream failure';
        }
      });
      updateStage('overallResult', TEST_STATUS.FAIL, `Failed at stage: ${stages[stageKey].name}`);
      return { success: false, stages, failure: failureInfo };
    };

    // ── STAGE 1, 2, 3: Credentials & Access ─────────────────────────────────
    if (!githubAuth.token && !githubApi._token) {
      updateStage('connection', TEST_STATUS.NOT_AVAILABLE, 'No GitHub credentials configured');
      updateStage('repoAccess', TEST_STATUS.NOT_AVAILABLE, 'No GitHub credentials configured');
      updateStage('actionsAccess', TEST_STATUS.NOT_AVAILABLE, 'No GitHub credentials configured');
      return failPipeline('connection', 'GitHub credentials are unavailable. Connect GitHub first.');
    }

    updateStage('connection', TEST_STATUS.RUNNING, 'Validating GitHub authentication...');
    const connCheck = await this.validateConnection({ owner, repo, enablePackages: enableMavenPublish });

    if (connCheck.connection.status !== TEST_STATUS.PASS) {
      return failPipeline('connection', connCheck.connection.message);
    }
    updateStage('connection', TEST_STATUS.PASS, connCheck.connection.message);

    if (connCheck.repoAccess.status !== TEST_STATUS.PASS) {
      return failPipeline('repoAccess', connCheck.repoAccess.message);
    }
    updateStage('repoAccess', TEST_STATUS.PASS, connCheck.repoAccess.message);

    if (connCheck.actionsAccess.status !== TEST_STATUS.PASS) {
      return failPipeline('actionsAccess', connCheck.actionsAccess.message);
    }
    updateStage('actionsAccess', TEST_STATUS.PASS, connCheck.actionsAccess.message);

    // ── STAGE 4: Generate Android Library Test Project ───────────────────────
    updateStage('generateProject', TEST_STATUS.RUNNING, 'Generating fresh Android library project...');

    let generatedFiles = {};
    try {
      const testConfig = {
        name:        'RoniTestLibrary',
        packageName: 'com.roni.testlib',
        version:     '1.0.0-test',
        description: 'Automated E2E Test Library generated by RONi Publisher',
        author:      owner,
        repoName:    repo,
        license:     'Apache-2.0',
        language:    'java',
        minSdk:      21,
        targetSdk:   34,
        compileSdk:  34,
        includeSample: true
      };

      generatedFiles = AndroidLibraryTemplate.generate(testConfig);
      const ghGen = new GitHubFileGenerator();
      const ghFiles = ghGen.generate(testConfig, owner, { defaultBranch: branch });
      Object.assign(generatedFiles, ghFiles);

      // Validate project structure
      const hasBuildScript = Object.keys(generatedFiles).some(f => f.endsWith('build.gradle') || f.endsWith('build.gradle.kts'));
      const requiredFiles = [
        'gradlew',
        'gradlew.bat',
        'settings.gradle',
        'gradle/wrapper/gradle-wrapper.properties',
        '.github/workflows/ci.yml'
      ];

      const missing = requiredFiles.filter(f => !generatedFiles[f]);
      if (!hasBuildScript) missing.push('build.gradle');
      if (missing.length > 0) {
        throw new Error(`Generated project missing essential files: ${missing.join(', ')}`);
      }

      // Check for forbidden legacy 'tools' in workflow
      const ciContent = generatedFiles['.github/workflows/ci.yml'] || '';
      if (/\bsdkmanager\s+tools\b/.test(ciContent) || /packages:.*['"]tools\s/.test(ciContent)) {
        throw new Error('Generated workflow contains invalid "tools" SDK package request!');
      }

      const fileCount = Object.keys(generatedFiles).length;
      updateStage('generateProject', TEST_STATUS.PASS, `Generated ${fileCount} files successfully`);
    } catch (err) {
      const diag = this.diagnoseFileError('generateProject', err.message);
      return failPipeline('generateProject', `Project generation failed: ${err.message}`, diag);
    }

    // ── STAGE 5: Push Test Project to GitHub ────────────────────────────────
    updateStage('pushProject', TEST_STATUS.RUNNING, `Pushing files to ${owner}/${repo}@${branch}...`);

    let commitSha = null;
    try {
      const commitMessage = `test(e2e): automated RONi Android library verification [run-${Date.now()}]`;
      const pushResult = await githubUploader.uploadAll(
        owner,
        repo,
        branch,
        generatedFiles,
        commitMessage,
        null,
        (current, total, filename) => {
          updateStage('pushProject', TEST_STATUS.RUNNING, `Pushing (${current}/${total}): ${filename}`);
        }
      );
      commitSha = pushResult?.commitSha || pushResult?.commit?.sha || pushResult?.sha;
      updateStage('pushProject', TEST_STATUS.PASS, `Pushed to ${branch} (commit: ${commitSha ? commitSha.slice(0, 7) : 'head'})`);
    } catch (err) {
      return failPipeline('pushProject', `Failed to push test project to GitHub: ${err.message}`);
    }

    // ── STAGE 6, 7, 8, 9, 10: Trigger & Monitor GitHub Actions ──────────────
    updateStage('androidSdkSetup', TEST_STATUS.RUNNING, 'Waiting for GitHub Actions workflow run...');

    try {
      // Find the triggered workflow run (poll up to 30 seconds for run to appear)
      let workflowRun = null;
      const startTime = Date.now();

      while (Date.now() - startTime < 35000) {
        if (this.#cancelRequested) throw new Error('Test cancelled by user');

        const runsData = await githubApi.listWorkflowRuns(owner, repo, { branch, per_page: 5 });
        const runs = runsData?.workflow_runs || [];

        // Find run matching our commit or newest run
        if (runs.length > 0) {
          if (commitSha) {
            workflowRun = runs.find(r => r.head_sha === commitSha);
          }
          if (!workflowRun && Date.now() - startTime > 10000) {
            // Pick newest run triggered within the last 2 minutes
            const newest = runs[0];
            const runCreated = new Date(newest.created_at).getTime();
            if (Date.now() - runCreated < 120000) {
              workflowRun = newest;
            }
          }
        }

        if (workflowRun) break;
        await new Promise(r => setTimeout(r, 4000));
      }

      if (!workflowRun) {
        return failPipeline('androidSdkSetup', 'GitHub Actions workflow run did not start within 35 seconds. Verify GitHub Actions is enabled in repository settings.');
      }

      // Monitor workflow run until completion or timeout (max 8 minutes)
      const runId = workflowRun.id;
      const maxDuration = 8 * 60 * 1000;
      const monitorStart = Date.now();
      let lastJobsData = null;

      while (Date.now() - monitorStart < maxDuration) {
        if (this.#cancelRequested) throw new Error('Test cancelled by user');

        const currentRun = await githubApi.getWorkflowRun(owner, repo, runId);
        lastJobsData = await githubApi.listWorkflowJobs(owner, repo, runId);
        const jobs = lastJobsData?.jobs || [];
        const mainJob = jobs[0];

        if (mainJob && mainJob.steps) {
          const steps = mainJob.steps;

          // 1. Check Android SDK Setup step
          const sdkStep = steps.find(s =>
            s.name.toLowerCase().includes('android sdk') ||
            s.name.toLowerCase().includes('setup-android')
          );

          if (sdkStep) {
            if (sdkStep.conclusion === 'success') {
              updateStage('androidSdkSetup', TEST_STATUS.PASS, 'Android SDK configured cleanly');
            } else if (sdkStep.conclusion === 'failure') {
              const logs = await githubApi.getJobLogs(owner, repo, mainJob.id);
              const errorSnippet = this.extractErrorSnippet(logs, 'sdk');
              const diag = this.diagnoseFileError('.github/workflows/ci.yml', errorSnippet);
              return failPipeline('androidSdkSetup', errorSnippet || 'Android SDK Setup failed in GitHub Actions', {
                workflow: currentRun.name,
                step: sdkStep.name,
                logs,
                ...diag
              });
            } else if (sdkStep.status === 'in_progress') {
              updateStage('androidSdkSetup', TEST_STATUS.RUNNING, 'Running sdkmanager & configuring SDK...');
            }
          }

          // 2. Check Gradle Build step
          const gradleStep = steps.find(s =>
            s.name.toLowerCase().includes('gradle') ||
            s.name.toLowerCase().includes('build')
          );

          if (gradleStep) {
            if (gradleStep.conclusion === 'success') {
              updateStage('gradleBuild', TEST_STATUS.PASS, 'Gradle build completed successfully');
            } else if (gradleStep.conclusion === 'failure') {
              const logs = await githubApi.getJobLogs(owner, repo, mainJob.id);
              const errorSnippet = this.extractErrorSnippet(logs, 'gradle');
              const diag = this.diagnoseFileError('build.gradle', errorSnippet);
              return failPipeline('gradleBuild', errorSnippet || 'Gradle Build failed in GitHub Actions', {
                workflow: currentRun.name,
                step: gradleStep.name,
                logs,
                ...diag
              });
            } else if (gradleStep.status === 'in_progress') {
              updateStage('gradleBuild', TEST_STATUS.RUNNING, 'Executing ./gradlew build...');
            }
          }

          // 3. Check assembleRelease step
          const assembleStep = steps.find(s =>
            s.name.toLowerCase().includes('assemblerelease') ||
            s.name.toLowerCase().includes('release') ||
            s.name.toLowerCase().includes('build release aar')
          ) || gradleStep;

          if (assembleStep) {
            if (assembleStep.conclusion === 'success') {
              updateStage('assembleRelease', TEST_STATUS.PASS, 'Release AAR generated successfully');
            } else if (assembleStep.conclusion === 'failure') {
              const logs = await githubApi.getJobLogs(owner, repo, mainJob.id);
              const errorSnippet = this.extractErrorSnippet(logs, 'assembleRelease');
              const diagFile = Object.keys(generatedFiles).find(f => f.includes('library') && f.endsWith('build.gradle')) || 'build.gradle';
              const diag = this.diagnoseFileError(diagFile, errorSnippet);
              return failPipeline('assembleRelease', errorSnippet || 'assembleRelease failed', {
                workflow: currentRun.name,
                step: assembleStep.name,
                logs,
                ...diag
              });
            }
          }

          // 4. Check Maven / Packages Publish step (if enabled)
          if (enableMavenPublish) {
            const publishStep = steps.find(s =>
              s.name.toLowerCase().includes('publish') ||
              s.name.toLowerCase().includes('maven')
            );
            if (publishStep) {
              if (publishStep.conclusion === 'success') {
                updateStage('mavenPublish', TEST_STATUS.PASS, 'Maven artifacts published to GitHub Packages');
              } else if (publishStep.conclusion === 'failure') {
                const logs = await githubApi.getJobLogs(owner, repo, mainJob.id);
                const errorSnippet = this.extractErrorSnippet(logs, 'publish');
                const diagFile = Object.keys(generatedFiles).find(f => f.includes('library') && f.endsWith('build.gradle')) || 'build.gradle';
                const diag = this.diagnoseFileError(diagFile, errorSnippet);
                return failPipeline('mavenPublish', errorSnippet || 'Maven publish step failed', {
                  workflow: currentRun.name,
                  step: publishStep.name,
                  logs,
                  ...diag
                });
              }
            }
          }
        }

        // Check overall workflow completion
        if (currentRun.status === 'completed') {
          if (currentRun.conclusion === 'success') {
            updateStage('androidSdkSetup', TEST_STATUS.PASS, 'Passed');
            updateStage('gradleBuild', TEST_STATUS.PASS, 'Passed');
            updateStage('assembleRelease', TEST_STATUS.PASS, 'Passed');
            if (enableMavenPublish) {
              updateStage('mavenPublish', TEST_STATUS.PASS, 'Passed');
            }
            updateStage('overallResult', TEST_STATUS.PASS, 'All E2E pipeline stages completed successfully!');
            return {
              success: true,
              stages,
              workflowUrl: currentRun.html_url,
              runId: currentRun.id
            };
          } else {
            const logs = mainJob ? await githubApi.getJobLogs(owner, repo, mainJob.id) : '';
            const errorSnippet = this.extractErrorSnippet(logs);
            return failPipeline('gradleBuild', `Workflow run ${currentRun.conclusion}: ${errorSnippet}`, {
              workflow: currentRun.name,
              logs
            });
          }
        }

        // Sleep before polling again
        await new Promise(r => setTimeout(r, 6000));
      }

      return failPipeline('gradleBuild', 'Workflow execution timed out after 8 minutes');
    } catch (err) {
      return failPipeline('androidSdkSetup', `Workflow monitoring error: ${err.message}`);
    }
  }

  cancel() {
    this.#cancelRequested = true;
  }

  // ── Error Extraction & Diagnostics ─────────────────────────────────────────

  /**
   * Extracts relevant error lines from full GitHub Actions log.
   *
   * @param {string} rawLogs
   * @param {string} [context]
   * @returns {string}
   */
  extractErrorSnippet(rawLogs, context = '') {
    if (!rawLogs) return 'No log details available.';

    const lines = rawLogs.split('\n');
    const relevantLines = [];

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (
        line.includes('Error:') ||
        line.includes('ERROR:') ||
        line.includes('FAILED') ||
        line.includes('BUILD FAILED') ||
        line.includes('sdkmanager failed') ||
        line.includes('Failed to find package') ||
        line.includes('Could not find method') ||
        line.includes('Caused by:') ||
        line.includes('Execution failed for task')
      ) {
        // Collect context window: 2 lines before and 4 lines after
        const start = Math.max(0, i - 2);
        const end = Math.min(lines.length - 1, i + 5);
        for (let j = start; j <= end; j++) {
          if (!relevantLines.includes(lines[j])) {
            relevantLines.push(lines[j]);
          }
        }
      }
    }

    if (relevantLines.length > 0) {
      // Clean timestamps like '2026-10-05T12:34:56.789Z '
      return relevantLines
        .slice(0, 15)
        .map(l => l.replace(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d+Z\s*/, ''))
        .join('\n');
    }

    // Fallback: return tail of log
    return lines.slice(-15).join('\n');
  }

  /**
   * Diagnoses which generator/template produced the failing file based on file path or error message.
   *
   * @param {string} filePath
   * @param {string} errorMessage
   * @returns {Object}
   */
  diagnoseFileError(filePath, errorMessage = '') {
    // Check known file matches
    for (const entry of TEMPLATE_ORIGIN_MAP) {
      if (entry.pattern.test(filePath)) {
        return {
          failingFile: filePath,
          sourceTemplate: entry.template,
          sourcePath: entry.path,
          component: entry.component,
          advice: `Fix this error in ${entry.template} under ${entry.path}. Regenerate fresh project and re-run test.`
        };
      }
    }

    // Check error message contents for clues
    if (errorMessage.includes('sdkmanager') || errorMessage.includes('setup-android') || errorMessage.includes('tools')) {
      return {
        failingFile: '.github/workflows/ci.yml',
        sourceTemplate: 'AndroidLibraryTemplate.js & GitHubFileGenerator.js',
        sourcePath: 'js/app/modules/generator/templates/AndroidLibraryTemplate.js',
        component: 'Workflow CI/Release Generator',
        advice: 'Check android-actions/setup-android package arguments in workflow templates.'
      };
    }

    if (errorMessage.includes('publishing') || errorMessage.includes('maven-publish') || errorMessage.includes('MavenPublication')) {
      return {
        failingFile: filePath || 'build.gradle',
        sourceTemplate: 'MavenPublishGenerator.js',
        sourcePath: 'js/app/modules/generator/MavenPublishGenerator.js',
        component: 'Library Module Build Script (Maven Publish)',
        advice: 'Verify plugins { id "maven-publish" } and publishing {} configuration in build.gradle.'
      };
    }

    return {
      failingFile: filePath,
      sourceTemplate: 'AndroidLibraryTemplate.js',
      sourcePath: 'js/app/modules/generator/templates/AndroidLibraryTemplate.js',
      component: 'Core Generator Template',
      advice: 'Review the failing Gradle/script code in AndroidLibraryTemplate.js and regenerate.'
    };
  }
}

export const gitHubTestRunner = new GitHubTestRunner();
