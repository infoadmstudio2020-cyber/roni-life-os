# Implementation Status — Native UI Configuration System

**Date:** 2026-11-13
**Baseline:** RONiLiFEOS 1.0.3 (15 library modules + `:app`, 516 main Java files / 52,994 LOC)

---

## 1. Status summary

| # | Area | Status |
| :- | :--- | :--- |
| 1 | Full project audit | ✅ Complete |
| 2 | `ProjectUIConfig` configuration model | ✅ Implemented |
| 3 | `ProjectUIConfigMapper` | ✅ Implemented |
| 4 | Integration with `ProjectActivity` / NativeRuntimeUI | ✅ Implemented |
| 5 | Configuration persistence (save / load / reset / export / import) | ✅ Implemented |
| 6 | `ProjectUIConfigValidator` | ✅ Implemented |
| 7 | `ProjectUIConfigPresets` (6 presets) | ✅ Implemented |
| 8 | Splash enable / disable + native / web + logo + animation + duration + fullscreen | ✅ Implemented |
| 9 | Dashboard enable / disable + toolbar / drawer / bottom nav / FAB / stats / recent / fullscreen | ✅ Implemented |
| 10 | Independent system UI component controls (31 components) | ✅ Implemented |
| 11 | Project UI editor + XML layouts | ✅ Implemented |
| 12 | Unit tests | ✅ Written (4 classes, 40 test methods) |
| 13 | Documentation | ✅ This file + `UI_CONFIGURATION_GUIDE.md` |
| 14 | **Gradle build verification** | ❌ **Not run** — no JDK / Android SDK in this sandbox |
| 15 | **Unit test execution** | ❌ **Not run** — same reason |
| 16 | **Debug APK** | ❌ Not produced |

**Nothing in this delivery has been compiled or executed.** The sandbox has no JDK and no Android
SDK, and the brief explicitly forbids installing one. Every claim below is a *source-level* claim,
verified by reading the code, not by a compiler.

---

## 2. What was implemented

### 2.1 `native-runtime-ui` — package `com.roni.library.ui.config`

| File | Lines | Responsibility |
| :--- | ----: | :--- |
| `ProjectUIConfig.java` | 1,361 | Per-project configuration aggregate. Nested `Splash`, `Dashboard`, `Theme`, `Component`, `WebView`, `Navigation`. All 31 `ComponentType` values in an `EnumMap` with an `enabled` flag and free-form style properties. Flat URL-encoded `encode()` / `decode()`. `resetToDefaults()`, `copy()`. **No Android imports** — JVM-testable. |
| `ProjectUIConfigMapper.java` | ~215 | `ProjectUIConfig` → `NativeRuntimeUIConfig.Builder`. Disables the builder's legacy "empty means defaults" fallback so a project that enables nothing is not silently given a toolbar. Only enabled components reach the builder. |
| `ProjectUIConfigPresets.java` | ~250 | Six presets: `minimal`, `status_bar_toolbar`, `fullscreen_webview`, `dashboard_full`, `web_app`, `everything`. Each returns a fresh instance. |
| `ProjectUIConfigValidator.java` | ~230 | `ERROR` / `WARNING` findings with field + message. Covers splash duration, web dashboard without source, dashboard chrome without its component, zero enabled components, bottom-nav + rail conflict, fullscreen + visible status bar, font scale and animation bounds. |

### 2.2 `:app` — package `com.roni.life.os.app.uiconfig`

| File | Responsibility |
| :--- | :--- |
| `ProjectUIConfigStore.java` | Key `uiconfig.<projectId>` on the existing `KeyValueBackend`. `load`, `save`, `reset`, `exists`, `applyPreset`, `export`, `importFrom`. Corrupt records fall back to defaults instead of throwing. Import rejects payloads without the `v=` schema marker so a bad paste cannot wipe a project. |

### 2.3 Modified files

| File | Change |
| :--- | :--- |
| `NativeRuntimeUIConfig.java` | Added `Builder.defaultFallbackEnabled(boolean)` (default `true`, so existing callers are unaffected) and guarded the empty-set fallback in `build()`. |
| `ProjectStore.java` | Added `getBackend()` so sibling stores reuse the same storage wiring. |
| `AppConfig.java` | Added 16 optional fields: splash mode / logo text / logo drawable / background / accent / animation / fullscreen / skip-on-tap, and dashboard enabled / toolbar / FAB / stats / recent / fullscreen / background / accent. Every key decodes with a fallback, so older records still load. |
| `ProjectActivity.java` | `onConfigureUI` now loads the project's own config and runs the mapper. `applyImmersiveFullScreen` respects the per-project navigation settings instead of always hiding both bars. |
| `SplashActivity.java` | Rewritten. Honours enable/disable, `NATIVE` / `WEB` / `NONE` mode, logo text, background and accent colours, five animations, duration, fullscreen and tap-to-skip. A disabled splash inflates no view and routes immediately. |
| `BootstrapActivity.java` | Dashboard enable/disable (routes to the startup project when off), toolbar / FAB / stats / recent visibility, background colour, fullscreen. Project menu gained a "UI Configuration" entry. |
| `AndroidManifest.xml` | Registered `ProjectUIEditorActivity`. |

### 2.4 New resources

| File | Purpose |
| :--- | :--- |
| `layout/activity_project_ui_editor.xml` | Editor screen: presets, splash, dashboard, theme, component list, WebView, system bars, validation summary, save button. |
| `layout/item_component_config.xml` | One component row (name, `#number · TYPE`, switch). |
| `layout/view_splash_native.xml` | Native splash surface. |
| `values/strings_ui_config.xml` | All editor and splash strings (English). |
| `values/styles_ui_editor.xml` | `UiEditorSection`, `UiEditorLabel`, `UiEditorSwitch`, `UiEditorSpinner`, `UiEditorInput`. |
| `drawable/ic_back.xml` | Toolbar back arrow. |

### 2.5 New activity

`ProjectUIEditorActivity` — edits a working copy, commits only on Save. Preset chips, five enum
spinners, 20 switches, numeric and colour inputs, a grouped list of all 29 canonical components,
live validation, and a menu with Save / Reset / Export / Import / Validate. Export copies the
encoded configuration to the clipboard; import decodes a pasted string.

### 2.6 Tests

| File | Methods | Covers |
| :--- | ------: | :--- |
| `ProjectUIConfigTest` | 12 | defaults, full round-trip, null/empty decode, unknown keys, clamping, `copy()` independence, toggle, reset |
| `ProjectUIConfigPresetsTest` | 10 | each preset's component set, unknown-preset fallback, fresh instances, every preset validates |
| `ProjectUIConfigValidatorTest` | 10 | each rule and its severity, warning-vs-error behaviour |
| `ProjectUIConfigStoreTest` | 13 | round-trip, **project isolation**, reset, presets, export/import, rejection paths, null backend |

---

## 3. Architecture

```
Project (registry record)
   └── ProjectUIConfigStore.load(projectId)          key: uiconfig.<projectId>
         └── ProjectUIConfig                          immutable-by-convention aggregate
               ├── Splash / Dashboard / Theme
               ├── WebView / Navigation
               └── EnumMap<ComponentType, Component>  31 entries, each optional
                     └── ProjectUIConfigMapper.apply(config, builder)
                           └── NativeRuntimeUIConfig.Builder
                                 └── NativeUIComponentManager
                                       └── native Android rendering
```

A component that is `false` in the `EnumMap` is passed to `builder.disableComponent(...)` and is
never instantiated by the manager.

---

## 4. Build verification

**Not performed.** No JDK, no Android SDK, no Gradle distribution in this environment.

To verify locally:

```bash
cd RONiLiFEOS
./gradlew :app:assembleDebug
./gradlew test
```

### Source-level risks to watch on the first build

These are the places most likely to need a one-line fix. They were checked by reading the code,
not by compiling it.

1. **`ProjectUIConfigMapper` uses `android.graphics.Color`** — fine in a library module, but it
   means the mapper cannot be unit-tested on a plain JVM. Only the pure-Java classes are tested.
2. **`ProjectUIConfigStore` calls `AppLog`** on the validation-issue path. A default
   configuration produces no issues, so the happy-path tests never touch `android.util.Log`.
   If you add a test that triggers a warning, add
   `testOptions { unitTests.returnDefaultValues = true }` to `app/build.gradle`.
3. **`ComponentType` aliases** — `CUSTOM_CONTENT` and `SYSTEM_UI` share a `componentNumber` with
   their canonical entries. The mapper and the editor both skip them explicitly. `EnumMap` treats
   them as distinct keys, so nothing breaks, but do not remove the skip.
4. **`MaterialToolbar` / `MaterialButton`** in the editor layout require the Material Components
   dependency, which `:app` already uses (`MaterialSwitch`, `ExtendedFloatingActionButton`).
5. **`ContextCompat.getColor`** is used throughout the editor because `minSdk` is 21 and
   `Context.getColor(int)` needs API 23.
6. **JitPack** — `:app` depends on `com.github.infoadmstudio2020-cyber:crashguard:1.0.1`. A cold
   build needs network access to JitPack.

---

## 5. Known limitations

- **No build, no test run, no APK.** Treat every file as unverified until you build it.
- **Web dashboard mode is configured but not hosted.** `DashboardMode.WEB` and
  `Dashboard.webSource` are stored, validated and exported, but no `WebDashboardActivity` exists
  yet. Selecting `WEB` currently falls back to the native dashboard. This is the largest
  remaining gap against the brief.
- **Component style properties are stored, not applied.** `Component.setProperty("height", "56")`
  round-trips through persistence, but the mapper does not yet translate those keys into builder
  calls. Only the typed fields (colours, sizes on the dedicated blocks) reach the runtime.
- **No UI preview.** The editor validates but does not render a preview of the configured layout.
- **`schemaVersion` is written but not used for migration.** A future format change should branch
  on it inside `decode`.
- **Per-project splash still uses `Project.splashEnabled` / `splashDurationMs`** in
  `ProjectActivity`; `ProjectUIConfig.Splash` is the richer model and is not yet wired into the
  project-level splash path. The app-level splash (`SplashActivity`) is fully wired.

---

## 6. Remaining work, in priority order

1. `WebDashboardActivity` — host `DashboardMode.WEB` with the existing `WebViewHost`, fall back to
   native on load failure.
2. Wire `ProjectUIConfig.Splash` into `ProjectActivity.showProjectSplash()` so per-project splash
   mode, logo and animation take effect (currently only duration and enable/disable).
3. Translate `Component` style properties into builder calls in the mapper.
4. Add `testOptions { unitTests.returnDefaultValues = true }` and a mapper test.
5. Build, run the tests, fix whatever the compiler reports, then produce the APK.
