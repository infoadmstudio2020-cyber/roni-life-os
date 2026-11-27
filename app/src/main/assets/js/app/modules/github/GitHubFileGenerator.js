/**
 * GitHubFileGenerator — generates standard GitHub repository files
 * from project metadata.
 * Upgraded to use Adaptive Android Project Workflow Generator.
 */
import { AndroidProjectWorkflowAnalyzer } from './AndroidProjectWorkflowAnalyzer.js';
import { WebToolEnvironmentCompatibility } from './WebToolEnvironmentCompatibility.js';
import { AdaptiveWorkflowGenerator } from './AdaptiveWorkflowGenerator.js';
import { JitPackAnalyzerEngine } from '../generator/JitPackAnalyzerEngine.js';

export class GitHubFileGenerator {

  /**
   * Generate all GitHub-related files for a project.
   * @param {object} proj
   * @param {string} owner
   * @param {object} settings
   * @returns {Record<string,string>}
   */
  generate(proj, owner, settings = {}) {
    const name    = proj.name        || 'AndroidLibrary';
    const desc    = proj.description || `${name} — Android Library`;
    const pkg     = proj.packageName || 'com.example.library';
    const ver     = proj.version     || '1.0.0';
    const license = settings.defaultLicense || proj.license || 'Apache-2.0';
    const branch  = settings.defaultBranch  || 'main';
    const year    = new Date().getFullYear();
    const repoName = proj.repoName || name.toLowerCase().replace(/\s+/g, '-');

    const files = {};

    // README.md
    files['README.md'] = this.#readme({ name, desc, owner, repoName, pkg, ver, license });

    // LICENSE
    files['LICENSE'] = this.#license(license, year, owner);

    // .gitignore
    files['.gitignore'] = this.#gitignore();

    // SECURITY.md
    files['SECURITY.md'] = this.#security(name, owner, repoName);

    // CHANGELOG.md
    files['CHANGELOG.md'] = this.#changelog(ver);

    // CONTRIBUTING.md
    files['CONTRIBUTING.md'] = this.#contributing(name, owner, repoName);

    // CODE_OF_CONDUCT.md
    files['CODE_OF_CONDUCT.md'] = this.#codeOfConduct();

    const projFiles = proj?.files || {};

    // Analyze real Android project and environment compatibility
    const profile = AndroidProjectWorkflowAnalyzer.analyze(projFiles, proj);
    const compat = WebToolEnvironmentCompatibility.assess(profile, settings);

    // .github/workflows/ci.yml — dynamically generated matching exact project configuration
    if (!projFiles['.github/workflows/ci.yml'] && !projFiles['.github/workflows/build.yml']) {
      files['.github/workflows/ci.yml'] = AdaptiveWorkflowGenerator.generateCIWorkflow(profile, compat);
    }

    // .github/workflows/release.yml — dynamically generated matching exact project configuration
    if (!projFiles['.github/workflows/release.yml']) {
      files['.github/workflows/release.yml'] = AdaptiveWorkflowGenerator.generateReleaseWorkflow(profile, compat);
    }

    // jitpack.yml — guaranteed openjdk17 and safe.directory wildcard matching project modules
    if (!projFiles['jitpack.yml']) {
      files['jitpack.yml'] = JitPackAnalyzerEngine.generateDefaultJitpackYmlForFiles(projFiles);
    }

    // .github/FUNDING.yml
    files['.github/FUNDING.yml'] = this.#funding(owner);

    // .github/PULL_REQUEST_TEMPLATE.md
    files['.github/PULL_REQUEST_TEMPLATE.md'] = this.#prTemplate();

    // .github/ISSUE_TEMPLATE/bug_report.md
    files['.github/ISSUE_TEMPLATE/bug_report.md'] = this.#issueBugReport(name);

    // .github/ISSUE_TEMPLATE/feature_request.md
    files['.github/ISSUE_TEMPLATE/feature_request.md'] = this.#issueFeatureRequest();

    return files;
  }

  // ── File content generators ───────────────────────────────────────────────

  #readme({ name, desc, owner, repoName, pkg, ver, license }) {
    return `# ${name}

[![JitPack](https://jitpack.io/v/${owner}/${repoName}.svg)](https://jitpack.io/#${owner}/${repoName})
[![License](https://img.shields.io/badge/License-${encodeURIComponent(license)}-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)

${desc}

## Installation

Add JitPack to your root \`build.gradle\`:

\`\`\`gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
\`\`\`

Add the dependency:

\`\`\`gradle
dependencies {
    implementation 'com.github.${owner}:${repoName}:${ver}'
}
\`\`\`

## Usage

\`\`\`java
// Initialize the library
${name} library = ${name}.getInstance(context);
library.init();
\`\`\`

## Requirements

- Android API 21+
- Java 8+ / Kotlin 1.8+

## License

\`\`\`
Copyright ${new Date().getFullYear()} ${owner}

Licensed under the ${license} License.
\`\`\`

See [LICENSE](LICENSE) for details.
`;
  }

  #license(type, year, author) {
    const licenses = {
      'Apache-2.0': `Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/

Copyright ${year} ${author}

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.`,
      'MIT': `MIT License

Copyright (c) ${year} ${author}

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.`,
    };
    return licenses[type] || licenses['Apache-2.0'];
  }

  #gitignore() {
    return `# Android / Gradle
*.iml
.gradle/
.idea/
local.properties
.DS_Store
build/
captures/
.externalNativeBuild/
.cxx/

# Keystore
*.jks
*.keystore

# Generated
gen/
out/

# Lint
lint/

# Distribution
*.aar
*.apk
`;
  }

  #security(name, owner, repo) {
    return `# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest  | ✅        |

## Reporting a Vulnerability

Please report security issues to the maintainer at https://github.com/${owner}/${repo}/security/advisories/new.

Do NOT open public issues for security vulnerabilities.
`;
  }

  #changelog(ver) {
    const today = new Date().toISOString().split('T')[0];
    return `# Changelog

All notable changes to this project will be documented in this file.

## [${ver}] - ${today}

### Added
- Initial release
- Core library implementation
- Sample application

### Changed
- N/A

### Fixed
- N/A
`;
  }

  #contributing(name, owner, repo) {
    return `# Contributing to ${name}

Thank you for considering contributing to ${name}!

## How to Contribute

1. Fork the repository: https://github.com/${owner}/${repo}
2. Create a feature branch: \`git checkout -b feature/my-feature\`
3. Make your changes and add tests
4. Commit: \`git commit -m 'feat: add my feature'\`
5. Push: \`git push origin feature/my-feature\`
6. Open a Pull Request

## Code Style

- Follow Google Java Style Guide
- Use meaningful variable names
- Add Javadoc for public APIs

## Reporting Bugs

Open an issue at https://github.com/${owner}/${repo}/issues
`;
  }

  #codeOfConduct() {
    return `# Contributor Covenant Code of Conduct

## Our Pledge

We pledge to make participation in our project a harassment-free experience for everyone.

## Our Standards

Examples of positive behavior:
- Using welcoming and inclusive language
- Being respectful of differing viewpoints
- Gracefully accepting constructive criticism
- Focusing on what is best for the community

## Enforcement

Instances of abusive behavior may be reported to the project maintainers.

This Code of Conduct is adapted from the [Contributor Covenant](https://www.contributor-covenant.org), version 2.1.
`;
  }

  generateCIWorkflow(opts = {}) {
    const branch = opts.branch || 'main';
    const isJavaLib = opts.projectType === 'java-library' || opts.projectType === 'java';
    const isApp = opts.projectType === 'app';
    const jdk = opts.jdkVersion || '17';
    const compileSdk = opts.compileSdk || 34;

    return `name: CI

on:
  push:
    branches: [ "${branch}", "master" ]
  pull_request:
    branches: [ "${branch}", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK ${jdk}
        uses: actions/setup-java@v4
        with:
          java-version: '${jdk}'
          distribution: 'temurin'
${!isJavaLib ? `
      - name: Set up Android SDK
        uses: android-actions/setup-android@v3
        with:
          packages: 'platform-tools platforms;android-${compileSdk} build-tools;${compileSdk}.0.0'
` : ''}
      - name: Cache Gradle packages
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: \${{ runner.os }}-gradle-\${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            \${{ runner.os }}-gradle-

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Check Environment
        run: ./gradlew --version

      - name: Run Tests & Verification
        run: ./gradlew ${isJavaLib ? 'test' : (isApp ? 'testDebugUnitTest' : 'check')} --stacktrace

      - name: Build with Gradle
        run: ./gradlew ${isJavaLib ? 'build' : (isApp ? 'assembleDebug' : 'assembleRelease')} --stacktrace

      - name: Upload Test Reports on Failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: '**/build/reports/'
`;
  }

  generateReleaseWorkflow(opts = {}) {
    const isJavaLib = opts.projectType === 'java-library' || opts.projectType === 'java';
    const isApp = opts.projectType === 'app';
    const jdk = opts.jdkVersion || '17';
    const compileSdk = opts.compileSdk || 34;
    const publishToPackages = opts.publishToPackages || false;

    return `name: Release

on:
  push:
    tags:
      - 'v*'
  workflow_dispatch:

permissions:
  contents: write
  packages: write

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK ${jdk}
        uses: actions/setup-java@v4
        with:
          java-version: '${jdk}'
          distribution: 'temurin'
${!isJavaLib ? `
      - name: Set up Android SDK
        uses: android-actions/setup-android@v3
        with:
          packages: 'platform-tools platforms;android-${compileSdk} build-tools;${compileSdk}.0.0'
` : ''}
      - name: Cache Gradle packages
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: \${{ runner.os }}-gradle-\${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
          restore-keys: |
            \${{ runner.os }}-gradle-

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Check Environment
        run: ./gradlew --version

      - name: Build Release Artifacts
        run: ./gradlew clean ${isJavaLib ? 'build' : (isApp ? 'assembleRelease bundleRelease' : 'assembleRelease')} --stacktrace
${!isApp && publishToPackages ? `
      - name: Publish to GitHub Packages
        env:
          GITHUB_TOKEN: \${{ secrets.GITHUB_TOKEN }}
          GITHUB_ACTOR: \${{ github.actor }}
        run: ./gradlew publish --stacktrace
` : ''}
      - name: Verify Generated Artifacts
        run: |
          echo "Verifying generated release artifacts..."
          find . -path "*/build/outputs/*" -o -path "*/build/libs/*" -type f
          ${isJavaLib ? 'ls -la **/build/libs/*.jar || echo "No JAR found"' : (isApp ? 'ls -la **/build/outputs/apk/release/*.apk || echo "No APK found"' : 'ls -la **/build/outputs/aar/*.aar || echo "No AAR found"')}

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: |
            ${isJavaLib ? '**/build/libs/*.jar' : (isApp ? '**/build/outputs/apk/release/*.apk\n            **/build/outputs/bundle/release/*.aab' : '**/build/outputs/aar/*.aar')}
          generate_release_notes: true
`;
  }

  #workflowCI(branch) {
    return this.generateCIWorkflow({ branch, projectType: 'library', jdkVersion: '17', compileSdk: 34 });
  }

  #workflowRelease() {
    return this.generateReleaseWorkflow({ projectType: 'library', jdkVersion: '17', compileSdk: 34 });
  }

  #funding(owner) {
    return `# These are supported funding model platforms
github: [${owner}]
# patreon: username
# open_collective: username
`;
  }

  #prTemplate() {
    return `## Description

Please describe the changes in this PR.

## Type of Change

- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing

Describe how the changes were tested.

## Checklist

- [ ] Code follows the project style
- [ ] Self-review performed
- [ ] Javadoc updated for public APIs
- [ ] Tests added / updated
`;
  }

  #issueBugReport(name) {
    return `---
name: Bug report
about: Create a report to help us improve ${name}
title: '[BUG] '
labels: bug
assignees: ''
---

**Describe the bug**
A clear description of the bug.

**To Reproduce**
Steps to reproduce the behavior.

**Expected behavior**
What you expected to happen.

**Android version / API level:**

**Library version:**

**Additional context**
Add any other context here.
`;
  }

  #issueFeatureRequest() {
    return `---
name: Feature request
about: Suggest an idea for this project
title: '[FEATURE] '
labels: enhancement
assignees: ''
---

**Is your feature request related to a problem?**
Describe the problem.

**Describe the solution you'd like**
A clear description of what you want.

**Additional context**
Add any other context here.
`;
  }
}

export const githubFileGenerator = new GitHubFileGenerator();
