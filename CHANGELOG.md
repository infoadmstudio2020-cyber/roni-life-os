# Changelog

All notable changes to the NativeRuntime SDK.

---

## [1.11.0] — Web SDK Documentation Rewrite

Documentation-only release. **No source code, API or behaviour changed.**

### Added
- `docs/web-sdk/` — a verified documentation set generated from the Java source (20 documents):
  - `README.md` — index, route cheat sheet, Bengali quick reference
  - `GETTING_STARTED.md` — first app in 10 minutes, four rules, first five calls
  - `ARCHITECTURE.md` — 16 Gradle modules, layering, request path, project model, virtual origin
  - `WEB_APP_DEVELOPER_GUIDE.md` — end-to-end guide (packaging, ES modules, lifecycle, debugging)
  - `JAVASCRIPT_BRIDGE.md` — protocol envelope, four security gates, timeouts, error codes
  - `NATIVE_API_REFERENCE.md` — all **290 action identifiers** across **19 routes**, with parameters and return values
  - `EVENTS_AND_CALLBACKS.md` — all 9 event topics plus patterns and a list of non-existent events
  - `STORAGE_API.md` — AES-GCM store, `__p:<project-id>:` scoping, legacy migration
  - `NOTIFICATION_API.md` — 5 styles, scheduling, channels, deep links, error codes
  - `BACKGROUND_SERVICES_API.md` — tasks, jobs, alarms, transfers, durable event log
  - `NETWORK_AND_CONNECTIVITY.md` — native HTTP, CORS bypass, connectivity polling
  - `FILE_ACCESS_AND_RESOURCES.md` — source kinds, path rules, ES modules, `filemanager.*`
  - `NAVIGATION_AND_LIFECYCLE.md` — toolbar, drawer, tabs, back handling, pause/resume
  - `LOGGING_AND_ANALYTICS.md` — `logger.*` levels and `analytics.*` events/metrics/traces
  - `SECURITY_AND_PERMISSIONS.md` — per-project origin model, permissions, Keystore, biometrics
  - `ERROR_CODES.md` — every code, retry policy, silent-failure table
  - `TROUBLESHOOTING_AND_FAQ.md` — symptom-driven diagnosis and a diagnostic script
  - `DEVELOPER_TOOLS.md` — Runtime Inspector, Quick Ball, `/devtools`, Chrome DevTools
  - `API_COMPATIBILITY.md` — stability contract, parameter traps, migration table
  - `DOCUMENTATION_AUDIT_REPORT.md` — full mismatch report
- `examples/webapp-developer-guide/` — runnable reference app with a corrected bridge wrapper
  (`js/native.js`) covering UI, device, storage, theme, security, notification and network.

### Fixed (documentation)
- Removed the fictional `NativeRuntime.ui.*` / `.device.*` / `.storage.*` facade. The SDK injects
  only `window.NativeRuntimeBridgeClient` with `call`, `ping`, `on`, `off`.
- Corrected 19 invented method names (`ui.toast` → `ui.showToast`, `storage.setItem` → `storage.set`, …).
- Corrected 11 parameter names (`text` → `message`, `properties` → `parameters`,
  `screen` → `screenName`, `text` → `input`, missing `iv` on keystore decrypt, …).
- Removed 9 invented events (`app.ready`, `app.pause`, `network.online`, `notification.clicked`, …).
- Documented that `ui.showConfirm` and `security.biometric.authenticate` resolve **before** the user
  answers; the outcome arrives as an event.
- Documented the per-project virtual origin `<slug>-<hash8>.runtime.local` and the `__p:` storage prefix.

### Deprecated
- `docs/web-developer-guide/*`, `docs/API.md`, `BRIDGE_API.md`, `docs/bangla/*` — each now carries a
  superseded banner pointing at `docs/web-sdk/`.

### Known issues (not fixed — source untouched)
- `examples/webapp-starter/scripts/native-runtime.js` sends six wrong parameter names. See
  `docs/web-sdk/API_COMPATIBILITY.md` §7 for the one-line fixes.

---

## [1.10.0] — Quick Ball: Extensible Native Action System

### Added
- `NativeAction`, `NativeActionRegistry`, `QuickBallLayout`, `QuickBallPreferences` and
  `QuickBallSettingsActivity` in `com.roni.life.os.app.quickball`.
- 20 built-in actions covering Runtime Inspector, Quick Settings, notifications, downloads,
  file manager, storage analyzer, camera, automation dashboard, background tasks, transfers,
  jobs, alarms, services, event monitor, bootstrap and browser.
- Per-action enable/disable, reorder, rename and icon selection, persisted in the encrypted
  `quickball_ui` store.
- Ball size and opacity, panel layout and label visibility settings.
- `AutomationActivity.EXTRA_SECTION` so automation shortcuts open the requested section.

### Changed
- `ProjectControlSheet` appends the registry actions and a Customize entry after the existing
  project actions; the session switcher and all six original actions are unchanged.
- `ProjectOverlayButton` reads size and opacity from the Quick Ball preferences.

### Compatibility
- First run after the upgrade enables every action in registration order, matching the previous
  panel exactly. Unknown ids are dropped and new ids appended without losing the user's order.

## [1.9.0] — 2026-11-20

Native Background & Automation Platform. `versionCode 19`, `versionName "1.9.0"`.

### Added
- `native-runtime-contracts/automation` — `BackgroundTask`, `JobSpec`, `AlarmSpec`,
  `TransferRequest`, `TransferProgress`, `AutomationEvent`, `ServiceStatus`, `AutomationLimits`,
  `AutomationErrorCodes`.
- `native-platform-engine/automation` — `EventEngine` (RxJava 3 streams + bounded replay),
  `BackgroundTaskEngine` (executor, retry, progress, cancellation), `JobScheduler` (WorkManager
  constraints and periodic work), `AlarmEngine` (AlarmManager, exact/inexact, boot restore),
  `TransferEngine` (streaming download/upload, resume, atomic rename), `ServiceEngine`
  (facade over `NativeServiceSDK`).
- `AutomationService` facade, `AutomationCommandHandler` (`/automation` route),
  `AutomationWorker`, `AutomationAlarmReceiver`, `AutomationBootReceiver`.
- Consumer App `AutomationActivity` and `AutomationEventsActivity`, reachable from the overflow
  menu of the bootstrap and project screens.
- `AUTOMATION.md`, `BUILD_REPORT.md`; `/automation` section in `BRIDGE_API.md`; automation
  security notes in `SECURITY.md`.

### Dependencies
- `androidx.work:work-runtime:2.9.0`, `io.reactivex.rxjava3:rxjava:3.1.8`,
  `com.google.code.gson:gson:2.10.1` in `native-platform-engine`.

### Fixed
- `BootstrapActivity` declared `onCreateOptionsMenu` and `onOptionsItemSelected` twice; the
  duplicate pair was merged into one method containing every menu branch.

### Notes
- Speech Engine is out of scope for this release.
- 39 JVM tests cover store, events, tasks, jobs, alarms, transfers and the bridge handler.

---

## [1.8.0] — 2026-11-16

Vault integration. `versionCode 18`, `versionName "1.8.0"`.

### Added
- `DecryptedResourceCache` (`native-security-engine`) — bounded LRU cache for decrypted bytes.
  Keyed by `projectId + '\0' + path`, so projects are isolated; synchronized for the WebView's
  concurrent interceptor threads; oversized entries are refused rather than evicting everything.
- `ResourceDecoder` (`native-runtime-ui/ui/web`) — optional hook on `WebHostConfig`. Only internal
  encrypted projects install one; SAF and remote sources leave it null and keep their existing
  path byte-for-byte.
- `WebViewHost.shouldInterceptRequest` now decodes the body when a decoder is present, preserving
  MIME type, encoding and status so ES6 modules, CSS and SVG still resolve.
- `ProjectVault.sealTree(...)` / `isSealed(...)` — seals a project tree in place, idempotent via a
  `.vault` marker.
- `ProjectImporter.sealProject(dir, projectId)` — the write-pipeline entry point.
- `VaultResourceDecoder` (app) — binds a project id to the cache and the vault.
- `DecryptedResourceCacheCheck` — standalone checks for isolation, LRU, budget and clearing.

### Not done
- `sealProject` is not yet called from `importZip`: the Keystore alias is the project id, which
  registration assigns after extraction, so the call belongs in the registration step.
- No build or test run — no Android SDK, Gradle or JDK in this environment.

---

## [1.7.0] — 2026-11-16

Encryption at rest for project files. `versionCode 17`, `versionName "1.7.0"`.

### Added
- `ProjectVault` (`native-security-engine/security/crypto`) — AES-256/GCM sealing backed by the
  Android Keystore, one key per project (`ronilifeos.project.<id>`). The GCM tag authenticates
  every byte, and the project id is bound as AAD so a sealed file cannot be moved between
  projects. Sealed layout is `[version][iv:12][ciphertext+tag]`.
- `forget(projectId)` drops a project's key on permanent delete.
- `docs/SECURE_PROJECT_STORAGE_BN.md` — Bengali report: what already existed (virtual origin,
  `shouldInterceptRequest`, bridge origin validation, SAF/ZIP/remote sources), what was missing,
  and the two integration points still open.

### Not done
- The vault is not yet wired into the resource-serving path; that needs a decision on caching
  decrypted bytes in memory.
- No build or test run — this environment has no Android SDK or Gradle.

---

## [1.6.2] — 2026-11-16

Documentation only. `versionCode 16`, `versionName "1.6.2"`.

### Added
- `docs/ARCHITECTURE_BN.md` — a full Bengali analysis of the project: the five-layer module
  stack, all fifteen library modules with their packages, the consumer app's activities and
  packages, the storage layout, the startup matrix, the project launch flow, back navigation and
  the download system.

---

## [1.6.1] — 2026-11-14

Download card polish. `versionCode 15`, `versionName "1.6.1"`.

### Changed
- Each download is now a card (`bg_card`) with 16/14 dp padding and a 10 dp gap, instead of a
  bare row separated by whitespace.
- Live transfers show a 4 dp progress bar tinted with `brand_accent` over `surface_elevated`.
- Progress updates in place on a 600 ms ticker using `setProgress(value, true)` (API 24+), so the
  bar animates and the list never rebuilds or jumps mid-scroll. The row rebuilds once, when the
  transfer reaches a terminal state.
- The meta line now reads `status · downloaded / total` while a transfer is moving.
- Action buttons are right-aligned, 36 dp tall, with 6 dp gaps and no minimum width.

---

## [1.6.0] — 2026-11-14

Back navigation hardening. `versionCode 14`, `versionName "1.6.0"`.

### Changed
- `ProjectActivity` back handling moved from the deprecated `onBackPressed()` override to an
  `OnBackPressedCallback` on the dispatcher, so predictive back on API 33+ reaches the same path.
- Order is fixed and explicit: WebView history first (`canGoBack()` → `goBack()`), dashboard
  second (`minimizeToDashboard()`). The runtime is never destroyed by back.
- A 400 ms debounce drops duplicate back events, so a double tap cannot walk two history entries
  or minimise twice.

### Verified against the existing architecture
- Startup routing already lives in `StartupManager.resolve(...)` / `StartupDecision.resolveMain(...)`
  and already falls back to the dashboard when no usable project exists.
- Splash, Bootstrap and Project remain the only three application-owned activities.

---

## [1.5.5] — 2026-11-14

Downloads manager. `versionCode 13`, `versionName "1.5.5"`.

### Added
- `DownloadsActivity` — lists every file a project saved into `Download/RONiLiFEOS`, merging
  system `DownloadManager` transfers with files written directly (`data:` / `blob:`).
- Per-row actions: **Pause**, **Resume**, **Cancel** for live transfers; **Delete** for any file.
- **Clear all saved files** removes the whole set in one pass.
- Reachable from the dashboard menu → *Downloads*.

---

## [1.5.4] — 2026-11-14

Download system in the runtime library. `versionCode 12`, `versionName "1.5.4"`.

### Added
- `com.roni.library.ui.download.DownloadCenter` — one pipeline for every download a project can
  trigger:
  - `http`/`https` → system `DownloadManager` (notification, retry, resume).
  - `data:` → decoded in-process and written directly.
  - `blob:` → the page converts the blob to base64 and posts it back through the injected
    `window.RONiDownload` bridge.
- Files land in `Download/RONiLiFEOS` (MediaStore on API 29+, public Downloads below), so they are
  visible in the Files app and survive uninstall. Any MIME type is accepted; the extension comes
  from the URL, `Content-Disposition`, or the MIME type.
- `WebViewHost` now delegates its download listener to `DownloadCenter` and injects the bridge
  only when downloads are enabled in `WebHostConfig`.

---

## [1.5.3] — 2026-11-14

Open / share a running project. `versionCode 11`, `versionName "1.5.3"`.

### Added
- Overlay control sheet gains **Open in another app**. A remote project opens in the user's
  browser through a chooser; a local project is zipped and offered through the system share
  sheet, so it can be sent to another device and re-imported.
- `ProjectBrowserLauncher` — URL resolution, ZIP export and FileProvider sharing.
- `FileProvider` (`${applicationId}.fileprovider`) with `res/xml/file_paths.xml`, scoped to the
  share cache and the projects folder.

---

## [1.5.2] — 2026-11-14

Project registration recovery. `versionCode 10`, `versionName "1.5.2"`.

### Fixed
- Default-project registration is now verified after `store.save(...)` and retried once, so a
  key-value backend that is not ready yet can no longer leave the registry empty.
- `ProjectActivity` repairs storage and retries the lookup when the registry is empty, instead of
  bouncing straight back to the project manager with "No project was found".
- New `RONiLiFEOSStorageInitializer.repair(context, store)` — idempotent re-initialisation plus
  re-registration, safe to call at any time.

### UI
- Dashboard status line now reports the active Splash and Bootstrap sources
  (`Splash Default · Bootstrap Runtime (web)`).
- New "Repair storage & projects" menu entry re-runs initialisation and refreshes the list.

---

## [1.5.1] — 2026-11-14

Build fix. `versionCode 9`, `versionName "1.5.1"`.

### Fixed
- `RONiLiFEOSStorageInitializer` was missing its private helper block, so the module failed to
  compile with "cannot find symbol" for `elapsed`, `readState`, `writeState`, `collectMissing`,
  `join`, `writeAtomically` and `closeQuietly`. The helpers are restored, including the atomic
  temp-file promotion used by every write.
- The marker was read through its private fields (`state.initialized`, `state.version`); it now
  uses `isInitialized()` / `getVersion()`.

---

## [1.5.0] — 2026-11-14

Default/Runtime source management for Splash and Bootstrap. `versionCode 8`, `versionName "1.5.0"`.

### Storage layout (breaking, migrated automatically)
- `system/splash/` now holds `default/`, `runtime/` and `config/`; the old `assets/` and `themes/`
  folders are gone.
- `system/bootstrap/` now holds `default/{native,web}`, `runtime/{native,web}` and `config/`.
- `StorageSpec.INIT_VERSION` is `2`; the initializer repairs a 1.x tree in place without touching
  user files.

### Source management
- `SourceKind`, `SourceSlot`, `SourceConfig`, `SourceConfigStore`, `SourceValidator`,
  `SourceResolver`, `SourceManager` and `SourceManagerDialog` under `com.roni.life.os.app.source`.
- Splash and Bootstrap each own a configuration file, a default folder and a runtime folder; the
  two surfaces never share state.
- Lifecycle: create, import (ZIP or folder), update, replace, validate, activate, deactivate,
  remove (keeps files), delete (removes files), reset, fallback toggle and mode toggle.
- Imports are staged in `runtime-staging` and promoted only after validation, so a broken archive
  never destroys a working runtime source.

### Resolution and fallback
- `SourceResolver` reads the configuration, validates the active source, falls back to Default
  when the runtime copy is invalid and fallback is enabled, and reports a diagnostic when neither
  validates.
- `SplashWebSource` now delegates to the resolver instead of the old `assets/splash` + ZIP chain.
- `BootstrapSource` exposes the resolved bootstrap source together with the independent
  `native`/`web` mode.

### UI
- New "Splash & Bootstrap sources" menu entry in the dashboard opens the source manager.

### Tests
- `SourceConfigTest` and `SourceValidatorTest` cover the codec, the default/unknown-value
  behaviour, source/mode independence and every validation branch.

---

## [1.4.0] — 2026-11-14

Automatic first-launch storage initialization. `versionCode 7`, `versionName "1.4.0"`.

### Storage

- **The tree is now built on first launch, not on demand.** `RONiLiFEOSStorageInitializer` creates
  the complete `system/`, `app/`, `runtime/` and `shared/` hierarchy, copies the template bundle
  from `assets/ronilifeos/`, generates the default project and records
  `system/config/initialization.json` — all before the runtime bootstrap runs.
- **The root moved to app-specific external storage.** `StorageLayout.root()` now resolves
  `Context.getExternalFilesDir(null)/RONiLiFEOS`, which needs no runtime permission and stays
  inspectable over USB. An existing internal-storage install is migrated once, in place, without
  overwriting anything; if external storage is unavailable the internal directory is used.
- **`StorageSpec`** is the canonical, Android-free description of the required directories and
  files. The initializer validates against it and the unit tests assert against it, so the
  contract cannot drift.
- **The default project is generated and activated.** `app/projects/default` ships with
  `manifest.json`, `ui/`, `sources/`, `config/` and `resources/`, is registered in
  `project-registry.json` and becomes the active project.
- **Initialization is idempotent and non-destructive.** Existing files are never overwritten,
  missing ones are recreated, and every write goes through a temp file plus rename. A crash
  before the marker is written simply repeats the same safe steps on the next launch.
- **Bootstrap is gated.** `AppInitializer` returns `runtimeReady = false` with the reason when
  initialization fails, so the WebView host never opens on a half-built tree.
- `ApplicationCore` exposes `getStorageResult()` / `isStorageReady()` for diagnostics.

### Tests

- `StorageSpecTest` — required paths, completeness, zero-length files, deleted-directory recovery.
- `StorageInitStateTest` — marker round trip, corrupt/truncated markers, version upgrades, escaping.

### Docs

- `docs/STORAGE_INITIALIZATION.md` — the tree, the five steps, the guarantees and the manual
  verification checklist.

---

## [1.3.1] — 2026-11-14

Bundled web splash by default, internal project copies. `versionCode 6`, `versionName "1.3.1"`.

### Splash

- **The web splash is now the default presentation.** `AppConfig.splashMode` starts at
  `SPLASH_WEB`, and a record written before this release (no `splashModeVersion` key) migrates to
  it once. After that the stored mode is the user's own choice and is never overridden again.
- **`SplashActivity` loads the bundled page instead of building HTML in Java.** The WebView opens
  `assets/splash/index.html` with JavaScript and file access enabled, so the splash is authored
  once as HTML/CSS/JS. Network loads stay blocked — the page is entirely local.
- **`SplashWebSource`** resolves the page through a three-step chain: the loose
  `assets/splash/index.html`, then `assets/splash.zip` extracted into
  `files/RONiLiFEOS/system/splash/assets`, then the inline HTML page as a last resort. ZIP
  extraction is staged in a sibling folder and promoted only after it succeeds, and a stamp keyed
  on the APK build skips re-extraction on normal starts.
- **Configured values still apply.** Background colour, accent colour, logo text and the
  `ANIM_NONE` setting are injected into the bundled page after load; the shipped layout and copy
  are left untouched when a setting is at its default.
- The splash WebView is destroyed in `onDestroy` instead of leaking with the activity.

### Project registration

- **`ProjectRegistrationService` now registers `ASSET` and `ZIP_ASSET` sources.** A bundled ZIP is
  extracted and a bundled asset folder is copied into `files/RONiLiFEOS/app/projects/<uuid>`, so
  every registered project owns an internal copy and never points back at an APK asset. Depth and
  file-count caps match the archive extractor, and a failed copy removes the partial directory.
- **`registerBundledZip`** is the first-run seeding path: `assets/webapp.zip` is extracted into the
  internal projects folder and registered as a `FILE` project. If extraction fails, seeding falls
  back to the previous asset reference so the app still starts with a working project.

---

## [1.3.0] — 2026-11-13

Structured storage layout. `versionCode 5`, `versionName "1.3.0"`.

### On-device tree

`StorageLayout` now owns the canonical tree under `files/RONiLiFEOS/` and provisions it on
every start:

```
RONiLiFEOS/
├── system/   splash/{config,assets,themes}, bootstrap/{native,web,config},
│             config/{app-config,storage-config,runtime-config}.json, database/
├── app/      projects/<id>/{manifest.json,ui,sources,config,resources},
│             config/{project-registry,active-project}.json, backups/, imports/
├── runtime/  workspace/, cache/, sessions/, logs/, config/
└── shared/   assets/, themes/, resources/
```

- **Projects moved** from `RONiLiFEOS/projects` to `RONiLiFEOS/app/projects`; downloaded
  resources moved from `RONiLiFEOS/resources` to `RONiLiFEOS/shared/resources`.
  `ProjectImporter.projectsRoot` / `resourcesRoot` now delegate to `StorageLayout`, so every
  existing caller follows the new tree without changes.
- **In-place migration.** `StorageLayout.provision` moves a layout-1 install into the new
  folders on first start, never overwriting a file that already exists at the destination and
  falling back to copy-then-delete when `renameTo` fails.
- **`StorageMirror`** writes the readable JSON view — `app-config.json`,
  `storage-config.json`, `runtime-config.json`, `project-registry.json`,
  `active-project.json` and a per-project `manifest.json`. `ProjectStore` stays the single
  source of truth; the mirror is a projection, and a failed write is logged rather than
  propagated.
- **`ProjectStore.setChangeListener`** fires after every registry/config mutation so the
  mirror refreshes without any screen having to remember to call it.

---

## [1.2.0] — 2026-11-13

Dashboard Project Section release. `versionCode 4`, `versionName "1.2.0"`.

### Dashboard project section

- **Four quick actions.** The dashboard now exposes Splash, Dashboard, App and Workspace as
  first-class entry points, each opening its own management screen. The section is additive —
  the existing project list, FAB and statistics are untouched.
- **`ProjectSlot`** models the four slots as rules rather than a second project model: target
  `ProjectType`, single-vs-multiple, accepted source types, and whether the slot registers a
  project at all. Splash and Dashboard are single-project; App is multi-project; Workspace is a
  single reference-only folder.
- **`ProjectType.WORKSPACE`** added, with `activeWorkspaceProjectId` persisted in `AppConfig` and
  a `WORKSPACE` branch in `ProjectStore.setActiveRole` / reference cleanup.
- **`ProjectRegistrationService`** is the only path that makes a project permanent: validate →
  copy the source into `files/RONiLiFEOS/projects/<uuid>` → write metadata → register → point the
  slot's role. Remote URL registers metadata only, Inline HTML writes `index.html`, ZIP extracts,
  SAF folders copy the document tree. A failed copy removes the partial directory and leaves the
  registry untouched; the user's original source is only ever read.
- **`ProjectTestRunner`** performs a read-only compatibility check (config, source, permission,
  entry document, local `src`/`href` targets) and works on unregistered drafts. Preview, Test and
  Register remain three distinct operations.
- **`ProjectSlotActivity`** renders one screen per slot. Workspace replaces Register with
  "Use this folder", so a workspace is never silently registered as an app project. Dashboard's
  Open action routes through `BootstrapActivity`'s native bootstrap path instead of a bare
  WebView host.

### Visual polish

- Quick action cards now carry a subtle 2dp resting elevation (`cardMaxElevation` 4dp) and a
  Material ripple (`ripple_light`, white at 12%) instead of a foreground selectable drawable,
  which previously painted over the card content. Row containers set `clipChildren="false"` so
  the shadow is not clipped.

---

## [1.0.3] — 2026-11-13

Runtime completion release. Closes audit findings N1–N5, E1–E7 and U1–U16.

### Network engine (`native-network-engine` 1.0.6)

- **N1 — real WebSocket client.** `WebSocketClient` now speaks RFC 6455 over `Socket`/`SSLSocket`:
  HTTP Upgrade handshake, client frame masking, text/binary/ping/pong/close opcodes, 7/16/64-bit
  payload lengths, fragmentation reassembly, a dedicated reader thread and thread-safe writes.
  The deprecated `simulate*` hooks are retained for backward compatibility.
- **N2 — connectivity is bound to a real Context.** `NetworkMonitor` registers a
  `ConnectivityManager.NetworkCallback`; before binding it reports `NONE`/`UNKNOWN` instead of
  pretending to be online.
- **N3 — TLS pinning fails closed.** `SslPinningManager.verify(host, presentedPin)` returns
  `false` when pins exist and the presented pin is missing or mismatched.
- **N4 — downloads resume and cancel.** `DownloadManager` streams to a `.part` file, sends a
  `Range` header and resumes on `206`, and deletes the partial file on cancel.
- **N5 — retries are idempotent-only.** `RetryInterceptor` never retries `POST`/`PATCH`;
  `HttpClientEngine` follows redirects manually and strips `Authorization`/`Cookie` on
  cross-host hops.

### Runtime engine (`native-runtime-engine` 1.0.1)

- **E1 — real/headless container split.** `WebViewContainer(String)` is headless and records no
  metrics; `WebViewContainer(String, WebView)` drives a real view and collects paint timings.
- **E2 — real loopback server.** `LocalWebServer` binds a `ServerSocket` on the loopback
  interface, falls back to an ephemeral port, serves `GET`/`HEAD` and answers `405` otherwise.
- **E3 — complete init/release.** `NativeRuntimeEngine.init` builds the service, starts the
  server and registers `/runtime` with `NativeRouter`; `release` unregisters the route and resets
  state while retaining the singleton.
- **E4–E7 — honest metrics.** `WebVitalsCollector` reports `-1` (`UNRECORDED`) for anything that
  was never measured, and `CookieSyncManager.flush()` persists the platform cookie store.

### Runtime UI (`native-runtime-ui` 1.0.1)

- **U1 — `showActionSheet` is its own action.** It no longer aliases `showBottomSheet`; it
  populates `NativeActionSheet` and reports its own visibility.
- **U2 — `web.evaluate` returns the script result.** The handler waits on a `ValueCallback` with
  a 3 s timeout and returns `{evaluated, result}`; calling it on the main thread is rejected with
  `MAIN_THREAD_BLOCKED` instead of deadlocking.
- **U3 — `storage.*` is backed by the storage engine.** The handler is now an adapter over
  `EncryptedStore` (AES-GCM, default) or `KeyValueStore`, with a one-time migration from the
  legacy `native_runtime_web_storage` preferences.
- **U6 — web permissions are grantable.** New `WebPermissionHandler` +
  `SecurityWebPermissionHandler`; `WebViewHost` forwards `onPermissionRequest` and geolocation
  prompts instead of denying unconditionally. No handler still means deny.
- **U7 — cache mode is applied.** `WebHostConfig.cacheMode` is mapped to `WebSettings` and
  unknown values fall back to `LOAD_DEFAULT` with a warning.
- **U8 — bridge origin provider is wired.** `WebViewHost` supplies the current page origin to
  `WebBridge`, so origin validation sees a real value instead of `null`.
- **U9 — `applyTheme` honours more keys.** `backgroundColor`, `textColor`, `accentColor` and
  `onPrimaryColor` are applied in addition to `primaryColor`/`surfaceColor`.
- **U10 — `setComponentVisible` reports `applied`.** The response now distinguishes "state
  recorded" from "view actually updated".
- **U11–U14 — lifecycle.** `NativeRuntimeUI.init` is idempotent, bridge routes are registered
  once, and `release()` no longer nulls the singleton.
- **U16 — `setZoom`/`getZoom` control page zoom.** `setTextZoom`/`getTextZoom` remain for text
  scaling.

---

## [1.0.2] — 2026-11-13

Bridge hardening release. Closes audit findings B1–B7 in `native-web-bridge-sdk`.

### Storage engine (`native-storage-engine` 1.0.2)

Closes audit findings S1–S6.

- **S1 — silent auto-initialisation removed.** `checkInitialized()` used to call
  `init(null, null)`, which fell back to `java.io.tmpdir` — not app-writable on Android — so
  every write was silently lost. All accessors now throw `IllegalStateException`.
- **S2 — shared hard-coded passphrase removed.** `EncryptedPreferences` no longer substitutes
  `"NativeStorageDefaultPassphrase2026!"`, and `NativeStorageEngineConfig` no longer defaults
  to `"NativeStorageEngineKey2026!"`. The master key now comes from `AndroidKeyStore` via the
  new `crypto/KeystoreKeyProvider` (AES-256, GCM, `setRandomizedEncryptionRequired(true)`).
  A passphrase is only a fallback for JVM tests and must be supplied explicitly.
- **S3 — `KeyValueStore` can encrypt.** New `KeyValueStore(String, File, SecretKey)`
  constructor; values are AES-GCM encrypted before being written. Legacy plaintext files are
  migrated once and rewritten with a `__format__=2` marker. `isEncrypted()` reports the mode.
- **S4 — `release()` no longer destroys the singleton.** State is reset and every sub-object
  is nulled, but `sInstance` is retained, so a later `init` rebuilds the same engine instead
  of creating a second one with a different root directory. `release()` is idempotent.
- **S5 — one canonical `storage.*` handler.** `StorageCommandHandler` now implements the seven
  actions the UI handler exposed (`get`, `set`, `remove`, `clear`, `keys`, `has`, `getAll`)
  with identical response shapes, accepts all three action forms, exposes
  `SUPPORTED_ACTIONS`, and gains a no-arg constructor. Legacy `pref.*` and `db.*` actions are
  retained.
- **S6 — `persist()` no longer throws.** Failures are logged and reported through the new
  `flush()`; the in-memory cache stays consistent.

### Security

- **B1 — origin validation is now enforced.** `BridgeSecurityValidator.isOriginValid` was
  never called, so any page loaded in the bridged WebView could invoke every native action.
  `WebBridge.handleWebMessage` now checks the page origin before `PING`, `EVENT` or `REQUEST`
  dispatch and rejects with `ORIGIN_REJECTED`. New `OriginProvider` interface; validation is
  on by default and fails closed when no provider is attached.
- **B4 — JS string injection in `dispatchJs`.** Outbound payloads are wrapped with
  `JSONObject.quote`, so `"`, `</script>`, U+2028 and U+2029 can no longer break out of the
  evaluation string. Interface names are validated against `[A-Za-z_$][A-Za-z0-9_$]*` and
  fall back to `NativeRuntimeBridge`.

### Fixed

- **B2 — interface-name mismatch.** `BridgeConfig` defaulted to `NativeBridge` while the UI
  layer used `NativeRuntimeBridge`; whichever initialised first won. Default is now
  `NativeRuntimeBridge`, `init` logs a warning on mismatch, and `reconfigure` never changes
  the name.
- **B3 — `pendingRequests` leak.** Entries never expired. They now carry a timestamp and a
  daemon sweeper emits `REQUEST_TIMEOUT` after `timeoutMs`. Late responses are dropped.
- **B5 — double singleton teardown.** `WebBridge.release()` no longer nulls its own
  singleton; `NativeWebBridgeSDK` owns that lifecycle.
- **B6 — payload echo.** `BridgeCommandHandler` no longer returns `receivedParameters`.
- **B7 — no payload limit.** `maxMessageBytes` (default 1 MiB) is enforced before parsing.

### Documentation

- New `native-web-bridge-sdk/README.md`.
- `docs/API.md` — `WebBridge` section.
- `BRIDGE_API.md` — origin validation, timeouts, payload limits, error codes.

---

## [1.1.1] — 2026-11-07

Maintenance release driven by the audit in
[`docs/AUDIT_AND_UPDATE_REPORT.md`](docs/AUDIT_AND_UPDATE_REPORT.md). Four defects fixed, one
documentation set rewritten, one new guide added.

### Fixed

- **Build blocker — `package` attribute removed from all 16 manifests.**
  AGP 8.x rejects a manifest that declares `package` when `build.gradle` also declares
  `namespace`. Every module was affected, so `./gradlew assembleDebug` failed at manifest
  merger before compiling a single class. The `namespace` property in each `build.gradle`
  already supplies the value. *(audit C1)*

- **Unknown bridge routes now return an error instead of a fake success.**
  `WebBridge.handleWebMessage` used to echo the request payload back as
  `NativeApiResponse.success(...)` when no handler matched. A typo such as `ui.showTost`
  therefore resolved with `{shown: true}` and did nothing. It now returns
  `NativeApiError("ROUTE_NOT_FOUND", …)`, so the JavaScript promise rejects and the mistake
  is visible. *(audit H1)*

- **`ui.showSnackbar` no longer crashes.**
  `SnackbarManager.show` called `Snackbar.show()` directly. The `ui.*` handler runs on the
  router's background pool, so Android threw `CalledFromWrongThreadException`. The call is now
  marshalled to the main thread via `View.post` when the caller is off-main. *(audit H2)*

- **`ui.showPopupMenu` no longer crashes.**
  Same root cause in `PopupMenuManager.showPopupMenu`; same fix, anchored on the anchor view.
  *(audit H2)*

### Changed

- **`docs/API.md` rewritten.** The previous version documented `NativeRuntimeSDKConfig` with
  `debugEnabled()`, `tag()` and `timeout()` — a class that does not exist. The new document
  covers the real surface: `NativeRuntime`, `RuntimeConfiguration`, `RuntimeBuilder`,
  `NativeRuntimeResult`, `RuntimeState`, `NativeRuntimeUI`, `NativeRuntimeUIConfig` and
  `WebSource`. *(audit C2)*

### Added

- **`docs/web-developer-guide/`** — a 12-part guide for front-end developers: getting started,
  bridge protocol, `ui.*` / `web.*` / `device.*` / `storage.*` references, events, packaging,
  recipes, troubleshooting and security.
- **`examples/webapp-starter/`** — a runnable demo app with a `window.NativeRuntime` wrapper
  that degrades gracefully in a desktop browser.
- **`docs/AUDIT_AND_UPDATE_REPORT.md`** — full audit with evidence, severity and a phased
  remediation plan.

### Still open

These were identified but **not** changed in this release, because each needs a design decision
or a build to validate:

| ID | Issue | Why deferred |
| --- | --- | --- |
| C3 | `NativeRuntime.executeApiRequest()` is a stub | Needs the router's sync-dispatch contract confirmed |
| C4 | `NativeRuntimeBootstrap` reports 10/10 without initialising | Needs the real engine init order |
| H3 | `ui.showActionSheet` is an alias of the bottom sheet | Behavioural decision |
| H4 | `web.evaluate` discards the script result | Needs an async result channel (blocking would deadlock the UI thread) |
| H5 | `storage.*` is plaintext `SharedPreferences` | Needs a migration path for existing installs |
| H6 | No origin validation on bridge messages | Needs an allow-list policy from the host app |
| H7 | WebView permission requests always denied | Needs a permission bridge design |
| H8 | `native-runtime-engine` is dead code | Module removal affects `settings.gradle` and consumers |

---

## [1.1.0] — prior

Sample application release. `versionCode 2`, `versionName "1.1.0"`.
