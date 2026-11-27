# Changelog: RONi Android Library Generator

All notable changes to this project are documented in this file.

## [0.1.3.6] - 2026-11-01

### Added
- **JitPack Build Log Retry System**: Introduced a dedicated `#jp-retry-log-btn` and error alert banner retry button (`#jp-retry-banner-btn`) in `JitPackPage.js`. Supports manual re-fetch, retry counter tracking, and optional automated retry timers on build log fetch timeouts or network failures.
- **Screen Center-Aligned Popup Dialog System**: Redesigned modal popup styles in `css/components/modals.css` and `css/responsive/mobile.css` ensuring modal dialogs are centered across both desktop and mobile screens (`margin: auto`, `align-items: center`, `justify-content: center`). Added responsive sizing (`.modal-sm`, `.modal-lg`, `.modal-xl`, `.modal-full`) and safe viewport bounds.
- **Ecosystem Version Management Mobile Optimization**: Added dedicated responsive CSS rules in `css/pages/ecosystem-manager.css` for 768px and 480px viewports, enabling stacked HUD cards, touch-scrollable version matrices, and fluid action toolbars.

### Changed
- **Version Synchronization**: Synchronized project version to `v0.1.3.6` across `version.json`, `package.json`, `index.html`, `Sidebar.js`, `Toolbar.js`, `DownloadsPage.js`, `VersionManagerPage.js`, and comprehensive documentation.

## [0.1.3.5] - 2026-11-01

### Fixed
- **ManifestPage EventBus & Empty State Navigation**: Resolved missing `eventBus` import in `ManifestPage.js` and wired `#manifest-empty-goto-gen-btn` to smoothly navigate to the Project Generator when no manifest files are detected.
- **Downloads & Release ZIP Action Integration**: Fortified release package download buttons in `DownloadsPage.js` and `VersionManagerPage.js` with `WebViewBridge.downloadFile` to prevent silent download drops in Android WebViews and constrained browser environments.
- **Global Unhandled Rejection Guard**: Enhanced startup error handling in `index.html` with an `unhandledrejection` event listener, guaranteeing that dynamic module import failures in restricted `file://` or legacy WebView environments display clear diagnostic messages with `WebViewAssetLoader` guidance.

### Added
- **Comprehensive Project Audit & Verification Suite (`test/test-v0135-project-audit-and-compatibility.js`)**: Rigorous automated test suite verifying version metadata synchronization, module imports, error boundaries, empty state button contracts, and cross-browser/WebView capabilities.

## [0.1.3.4] - 2026-11-01

### Added
- **Android WebView & Cross-Platform Bridge (`WebViewBridge.js`)**: Comprehensive environment probing detecting Android WebView, display mode, serving protocols (`https`, `file:`, `appassets.androidplatform.net`), and native `window.AndroidBridge` interface.
- **Resilient File Download Engine**: Unified multi-tier download strategy routing through native `AndroidBridge.saveFile` / `downloadBlob`, browser Blob download with safe 60s delayed revocation (preventing thread-cancellation bugs), and Base64 Data URL fallback for restricted WebViews.
- **File Picker DOM-Attachment Protection**: Fortified `ZipService.pickAndImport` and `WebViewBridge.openFilePicker` with DOM body attachment, ensuring WebChromeClient `onShowFileChooser` detects file pick triggers across all Android versions.
- **Android WebView Integration Guide (`docs/android-webview-integration.md`)**: Full architectural guide with complete, production-tested Kotlin and Java implementations for AndroidX `WebViewAssetLoader`, `WebChromeClient`, `@JavascriptInterface`, download listeners, and permissions.
- **Browser & WebView Diagnostics Panel**: Integrated interactive diagnostics in SettingsPage displaying live status for ES Modules, IndexedDB, Fetch API, JSZip, and native bridge connectivity with 1-click self-tests.

### Fixed
- **Synchronous `revokeObjectURL` Lifecycle Bug**: Removed premature `URL.revokeObjectURL(url)` invocations across `ZipService`, `EditorModule`, `ConsoleService`, `ManifestPage`, `JitPackPage`, `GradlePage`, and `VersionManagerPage` that previously broke downloads on Android WebView and mobile browsers.
- **Native Clipboard Bridge Extension**: Extended `ClipboardUtil.js` to automatically utilize native `window.AndroidBridge.copyToClipboard` when available before falling back to `navigator.clipboard` and `document.execCommand('copy')`.

## [0.1.3.3] - 2026-11-01

### Fixed
- **Runtime Dynamic Import Resolution**: Fixed relative module import in `js/app/pages/EcosystemManagerPage.js` from `../../core/EventBus.js` to `../core/EventBus.js`, resolving the browser console error `Failed to fetch dynamically imported module: http://127.0.0.1:5502/js/app/pages/EcosystemManagerPage.js`.

### Added
- **NativeRuntime Ecosystem – GitHub & JitPack Library Version Manager**: Centralized ecosystem dashboard monitoring all registered Android library repositories across the NativeRuntime ecosystem.
- **Multi-Source Version Comparison Matrix**: Real-time cross-checking between declared project versions in build files, published GitHub Releases, Git Tags, and JitPack Maven builds.
- **Automated SemVer Comparator Engine**: Robust semantic versioning module (`VersionComparator.js`) supporting `v` prefixes, major/minor/patch precedence, pre-release identifiers (`alpha`, `beta`, `rc`), build metadata, and 8-tier status determination.
- **JitPack Live Diagnostics & Build Monitor**: Real-time polling of JitPack API build records, build status classification (`ok`, `error`, `building`, `unknown`), raw build log retrieval with error snippets, and direct links to JitPack web pages.
- **Cross-Library Dependency Topology Graph & Matrix**: Interactive SVG dependency diagram, inter-library dependency matrix, detection of outdated dependencies, missing ecosystem registrations, version conflicts, and cycle detection (DFS-based circular dependency guard).
- **Automated GitHub Repository Discovery & Android Library Detection**: Scanner module (`EcosystemScanner.js`) examining topics, descriptions, language indicators, and manifest/Gradle files with multi-repository batch registration and duplicate prevention.
- **IndexedDB Schema Migration (v2)**: Seamless database schema upgrade introducing `ecosystem_libraries`, `ecosystem_groups`, and `ecosystem_cache` object stores alongside in-memory fallback mappings.
- **Comprehensive Automated Test Suite**: Added `test/test-ecosystem-version-manager.js` covering 100% of ecosystem features, unit tests, integration tests, and verifying zero regressions across all 14 test suites.

## [0.1.3.2] - 2026-10-28

### Added
- **100% Offline Usability**: Bundled local vendor script `js/vendor/jszip.min.js` (96 KB) ensuring zero network dependencies for project archiving and export.
- **Cross-Platform Clipboard Utility**: Implemented `ClipboardUtil` in `js/app/core/ClipboardUtil.js` providing dual-mode clipboard support (`navigator.clipboard.writeText` with resilient `document.execCommand('copy')` fallback for Android WebView and `file://` contexts).
- **Dedicated Spinner UI Component**: Added `css/components/spinners.css` providing smooth CSS loading spinners (`spinner-xs`, `spinner-sm`, `spinner-md`, `spinner-lg`, `spinner-xl`) with button alignment rules to prevent UI breaks and layout shifts during async operations.
- **Project Documentation & QA Standards**: Added `docs/architecture.md`, `docs/file-structure.md`, `tests/manual-test-checklist.md`, `tests/test-report.md`, and `LICENSE`.
- **Automated Verification Suite**: Added `test/test-v0131-offline-optimization.js` validating version synchronization, offline vendor bundles, clipboard fallback, and schema output.

### Changed
- Refactored clipboard copying across all modules (`AIProjectSpecGenerator`, `EditorModule`, `ManifestPage`, `GradlePage`, `JitPackPage`, `EnvironmentPage`, `ConflictResolverPage`, `GHCommitPage`, `GHActionsWizardPage`, `LogsPage`) to use `ClipboardUtil`.
- Updated mobile viewport in `index.html` to remove restrictive `user-scalable=no, maximum-scale=1.0` constraints, ensuring full accessibility and ergonomics in Android WebView and modern browsers.
- Synchronized version `0.1.3.1` across `version.json`, `index.html`, `Sidebar.js`, `Toolbar.js`, `DownloadsPage.js`, `VersionManagerPage.js`, and `AIProjectSpecGenerator.js`.

### Fixed
- Fixed un-styled spinner elements breaking modal card alignment in GitHub Import and testing views.
- Fixed button line-wrapping during async validation operations.
- Removed obsolete backup directories and temporary callback files to ensure a lean, clean repository structure.

---

## [0.1.3.0] - 2026-10-27
- Added "Export Required Information Schema" modal in Templates page.
- Upgraded AST Gradle Helper with strict brace balance verification.
- Introduced Android Library standalone template.

## [0.1.2.9] - 2026-10-27
- Enhanced AGP 8.10.1 and Gradle 8.13 compatibility matrix.
- Added duplicate class collision guard and D8 packaging protections.

## [0.1.2.8] - 2026-10-27
- Full English and Bengali bilingual support.
- Production stability upgrade and multi-module project analysis.
