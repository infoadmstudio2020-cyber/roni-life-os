# NativeRuntime SDK — Audit & Update Report

**Date:** 2026-11-07
**Scope:** `NativeRuntimeSDK.zip` — 1,109 files, 15 library modules + 1 sample app, 48,415 Java LOC
**Method:** static source inspection of every module, manifest, Gradle script, asset and doc.
**Not performed:** Gradle build, APK assembly, on-device execution (no Android SDK in this environment).

---

## Status — 1.1.1

Four findings were fixed in this release. The rest remain open and are listed in
[`CHANGELOG.md`](../CHANGELOG.md#still-open).

| ID | Finding | Status |
| --- | --- | --- |
| C1 | `package` attribute in every manifest | ✅ Fixed — removed from all 16 manifests |
| C2 | `docs/API.md` documents a non-existent class | ✅ Fixed — rewritten against `RuntimeConfiguration` |
| H1 | Unknown routes return success | ✅ Fixed — now returns `ROUTE_NOT_FOUND` |
| H2 | Snackbar / popup menu shown off the main thread | ✅ Fixed — marshalled to the UI thread |
| C3, C4, H3–H8, M1–M13, L1–L7 | — | ⬜ Open |

The findings below are preserved as written at audit time so the evidence stays traceable.

---

## 0. Executive summary

The ecosystem is **architecturally sound and unusually well-structured** for a 15-module Android
library: the layering is real, the contracts module is genuinely dependency-free, and the
`WebSourceResolver` virtual-origin design is better than most commercial WebView wrappers.

It is **not shippable as-is**. Four defects block a release:

1. Every `AndroidManifest.xml` still carries a `package` attribute, which AGP 8.x rejects — **the project will not build**.
2. `docs/API.md` documents a class (`NativeRuntimeSDKConfig`) that does not exist.
3. `NativeRuntime.executeApiRequest()` is a stub that returns fake success.
4. `NativeRuntimeBootstrap` reports 10/10 steps passed without initialising anything.

Beyond those, the bridge has **no origin validation**, two UI actions crash off the main thread,
and `storage.*` is plaintext despite the ecosystem advertising AES encryption.

**Verdict:** ~85% of the code is production-quality. The remaining 15% is concentrated in the
integration seams — exactly where a demo passes and a real app fails.

| Severity | Count |
| --- | --- |
| Critical (build-blocking or false API) | 4 |
| High (broken feature or security hole) | 8 |
| Medium (partial feature or drift) | 13 |
| Low (hygiene) | 7 |

---

## 1. What was verified, and how

| Check | Method | Result |
| --- | --- | --- |
| Module inventory | `settings.gradle` + directory scan | 15 libraries + `:app` ✅ |
| Internal import resolution | Script: every `import com.roni.library.*` matched against a real `.java` file | 0 missing ✅ |
| Manifest well-formedness | XML parse of all 16 manifests | All well-formed ✅ |
| Brace balance | Per-file `{`/`}` count across 229 files | 0 mismatches ✅ |
| Gradle dependency declarations | Manual read of all 16 `build.gradle` | Material/AppCompat declared where used ✅ |
| Bridge contract | Cross-read `app.js` ↔ `BRIDGE_API.md` ↔ 4 handlers | 4 namespaces, ~120 actions ✅ |
| Threading | Trace each handler → manager → view mutation | 2 unsafe paths found ❌ |
| Build | **Not run** — no Android SDK | ⚠️ unverified |

> The repository's own `scripts/verify_project.py` checks XML well-formedness, brace balance and
> one manifest entry. It cannot detect any finding below. Treat a green run as "files parse",
> not "project builds".

---

## 2. Critical findings

### C1. `package` attribute in every manifest — build fails on AGP 8.x

**Evidence:** all 16 `AndroidManifest.xml` files declare `package="…"` while the matching
`build.gradle` also declares `namespace '…'`.

```
native-runtime-ui/src/main/AndroidManifest.xml:3   package="com.roni.library.ui"
native-runtime-ui/build.gradle:8                   namespace 'com.roni.library.ui'
```

AGP 8.0 removed support for the manifest `package` attribute. With AGP 8.10.1 the build stops at
manifest merger with:

```
The package attribute is no longer supported. Use the namespace property in the build.gradle file instead.
```

**Impact:** `./gradlew assembleDebug` fails before compiling a single class. Every module is affected.

**Fix:** delete the `package="…"` attribute from all 16 manifests. `namespace` in `build.gradle`
already supplies the value. One-line change per file, ~10 minutes total.

```bash
find . -name AndroidManifest.xml -exec sed -i '/package="com\.roni\.library\./d' {} \;
```

(Verify each file afterwards — the attribute may share a line with `<manifest`.)

---

### C2. `docs/API.md` documents a class that does not exist

**Evidence:** `docs/API.md` documents `NativeRuntimeSDKConfig` with `debugEnabled()`, `tag()`,
`timeout()` and `build()`. No such file exists:

```
native-runtime-sdk/src/main/java/com/roni/library/sdk/
├── activity/
├── bootstrap/
├── LifecycleObserver.java
├── NativeRuntime.java
├── NativeRuntimeResult.java
├── RuntimeBuilder.java
├── RuntimeConfiguration.java
└── RuntimeSession.java
```

The real API is `RuntimeConfiguration.builder()` with `appId`, `environment`, `debugEnabled`,
`logLevel`, `storageEncryptionEnabled`, `networkTimeoutMs`, `maxNetworkRetries`,
`telemetryEnabled`, `customProperties`.

**Impact:** any developer following the docs writes code that does not compile.

**Fix:** rewrite `docs/API.md` against `RuntimeConfiguration` / `RuntimeBuilder` / `NativeRuntime`.

---

### C3. `NativeRuntime.executeApiRequest()` is a stub

**Evidence:** `NativeRuntime.java:227`

```java
public NativeApiResponse executeApiRequest(@Nullable NativeApiRequest request) {
    // returns a map containing handledBy / path / action / timestamp
    // never calls NativeRouter
}
```

**Impact:** the documented "master facade" entry point returns `success` for every request and
performs no work. A host app that routes through it silently does nothing.

**Fix:** delegate to the router:

```java
return NativeRouter.getInstance().dispatchSync(request);
```

or remove the method and document `NativeRouter` as the entry point.

---

### C4. `NativeRuntimeBootstrap` reports success without initialising anything

**Evidence:** the 10-step sequence records `StepResult.success(...)` for each step
unconditionally. No engine `init()` is invoked, no service is registered, no failure path exists.

**Impact:** `NativeRuntime.isHealthy()` always returns `true`, and
`getBootstrapReport()` always shows 10/10. The health signal is meaningless — a broken
installation looks identical to a working one.

**Fix:** either call the real engine initialisers and record their results, or rename the report
to `BootstrapPlan` and stop exposing `isHealthy()`.

---

## 3. High findings

### H1. Unknown routes return success

**Evidence:** `WebBridge.handleWebMessage` falls back to
`NativeApiResponse.success(message.getId(), payload)` when no route matches.

**Impact:** `ui.showTost(...)` resolves with `{shown:true}` and does nothing. Typos, renamed
actions and version skew all fail silently. This is the single most likely cause of
"the bridge doesn't work" reports.

**Fix:** return `NativeApiError("ROUTE_NOT_FOUND: " + action)`.

---

### H2. Snackbar and popup menu are shown off the main thread

**Evidence:**

```java
// SnackbarManager.java:16
public Snackbar show(View view, String message, int duration, String actionTitle, Runnable onAction) {
    Snackbar snackbar = Snackbar.make(view, message, duration);
    ...
    snackbar.show();          // ← no runOnUiThread
}

// PopupMenuManager.java:38
public void showPopupMenu(Context context, View anchor, List<PopupMenuItem> items) {
    PopupMenu popupMenu = new PopupMenu(context, anchor);
    ...
    popupMenu.show();         // ← no runOnUiThread
}
```

`NativeUICommandHandler` runs on `NativeRouter`'s background pool. Every other manager
(`DialogManager`, `ToolbarManager`, `NavigationManager`, `LoadingOverlayManager`,
`CustomBottomSheet`, `NativeUIComponentManager`) wraps its view work in `runOnUiThread` — these
two do not.

**Impact:** `ui.showSnackbar` and `ui.showPopupMenu` throw `CalledFromWrongThreadException` and
crash the app.

**Fix:** wrap both bodies in `activity.runOnUiThread(...)`, or marshal the whole handler the way
`NativeWebCommandHandler` already does with `MAIN_HANDLER.post(new FutureTask<>(...))`.

---

### H3. `ui.showActionSheet` is an alias of the bottom sheet

**Evidence:** the `showActionSheet` case calls the same `showNativeBottomSheet(...)` helper as
`showBottomSheet`. A dedicated `NativeActionSheet` class exists in `native-runtime-ui` but is not
referenced by the handler.

**Impact:** the documented "action sheet" is a bottom sheet. Visual and behavioural expectations
differ (action sheets are typically anchored and shorter).

**Fix:** call `uiHost.getActionSheet()` in the `showActionSheet` case, or delete the action and
document the alias.

---

### H4. `web.evaluate` discards the script result

**Evidence:** the handler calls `webView.evaluateJavascript(script, null)` and returns
`{evaluated: true}`. The `ValueCallback` is `null`.

**Impact:** you cannot read a value from the page. Every "get something from JS" use case needs a
second bridge round-trip.

**Fix:** pass a `ValueCallback<String>` and return `{evaluated:true, result: value}`.

---

### H5. `storage.*` is plaintext, not encrypted

**Evidence:** `NativeStorageCommandHandler` writes to
`context.getSharedPreferences("native_runtime_web_storage", MODE_PRIVATE)`.

**Impact:** contradicts `RuntimeConfiguration.storageEncryptionEnabled` and the ecosystem's
"AES-GCM encrypted storage" claim. Tokens are readable on a rooted or debuggable device.

**Fix:** route through `NativeStorageEngine` (which already implements AES-GCM), or switch to
`EncryptedSharedPreferences`.

---

### H6. No origin validation on bridge messages

**Evidence:** `BridgeSecurityValidator.isOriginValid(...)` is defined but never called.
`isMessageValid(...)` only checks that `id` and `action` are non-empty.

**Impact:** any page loaded in the bridged WebView — including a remote page reached by
navigation or redirect — can call every native action, including `device.readClipboard`,
`storage.getAll` and `device.finish`.

**Fix:** in `WebBridge.handleWebMessage`, compare `webView.getUrl()`'s origin against an
allow-list before dispatch. Reject with `ORIGIN_REJECTED`.

---

### H7. WebView permission requests are always denied

**Evidence:**

```java
@Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
@Override public void onGeolocationPermissionsShowPrompt(...) { callback.invoke(origin, false, false); }
```

**Impact:** camera, microphone and geolocation never work. `web.setGeolocationEnabled(true)`
flips a settings flag that the host then ignores.

**Fix:** implement a permission bridge (`device.requestPermission`) or document the limitation
and remove the settings toggle.

---

### H8. `native-runtime-engine` is dead code

**Evidence:** `grep -rn "NativeRuntimeEngine" app/ native-runtime-sdk/` returns nothing.
`native-runtime-sdk/build.gradle` still declares `api project(':native-runtime-engine')`.
Its `WebViewContainer` has no `addJavascriptInterface`, no `WebViewClient` hardening and no
bridge — it is a second, incompatible WebView host.

**Impact:** two competing WebView implementations. A consumer who picks the engine gets a
WebView with no bridge and no security controls.

**Fix:** delete the module and its dependency, or wire it to `WebBridge` and make it the single
host.

---

## 4. Medium findings

| # | Finding | Evidence | Impact |
| --- | --- | --- | --- |
| M1 | `ui.applyTheme` applies only `primaryColor` and `surfaceColor` | `NativeUIComponentManager.applyTheme:319` reads two keys | 5 of 7 documented theme keys silently ignored |
| M2 | `web.setZoom` is text zoom | maps to `WebSettings.setTextZoom()` | "120" scales fonts, not layout |
| M3 | `device.getBrightness` returns the window override | `getWindow().getAttributes().screenBrightness` | returns `-1` on a fresh app |
| M4 | `ui.setComponentVisible` live-toggles only 5 types | `NativeUIComponentManager.setComponentVisibility:386` switch has 5 cases + default | other types update state only |
| M5 | Two `NativeUIHost` classes | `ui/NativeUIHost.java` and `ui/host/NativeUIHost.java` | wrong-import hazard |
| M6 | `NativeRuntimeUI.init` runs twice per Activity | `NativeRuntime.init` → `NativeRuntimeUI.init(default)`; `NativeRuntimeActivity.onCreate` → `NativeRuntimeUI.init(config)` | wasted work, first config discarded |
| M7 | `WebBridge.init` is first-call-wins | `if (sInstance != null) return sInstance;` | interface name fixed by whoever initialises first; `getInstance()` defaults to `NativeBridge`, UI uses `NativeRuntimeBridge` |
| M8 | `NativeRuntimeUI.release()` nulls the singleton | `instance = null` while `NativeRuntime` holds a reference | stale reference after release |
| M9 | Configured cache mode is not applied at init | `WebViewHost.initView` has `setCacheMode` commented out | `WebHostConfig.cacheMode` has no effect |
| M10 | `docs/` are in Bangla | `docs/NativeRuntimeFullSyestemDocumentation/*` | inconsistent with an English codebase |
| M11 | Version drift | README `1.0.1-provisional`; manifests `1.0.0`/`1.0.1`/`1.0.2`/`1.0.6`; `app` `1.1.0` (code 2); analytics manifest `1.0.6` vs gradle `1.0.0` | no single source of truth |
| M12 | Build docs are stale | `docs/05` says compileSdk 34 / AGP 8.3.2 (actual 36 / 8.10.1); `docs/SETUP.md` says Gradle 8.7 (actual 8.11.1) | misleading setup instructions |
| M13 | `verify_project.py` is not a build | checks XML, braces, one manifest entry | cannot catch C1–C4 |

---

## 5. Low findings

| # | Finding |
| --- | --- |
| L1 | `native-runtime-sdk` declares `api project(':native-runtime-engine')` but never uses it |
| L2 | `native-runtime-sdk` declares `implementation project(':native-router-sdk')` while `native-web-bridge-sdk` already exposes it via `api` |
| L3 | `app` release build signs with `signingConfigs.debug` |
| L4 | `lint { abortOnError false }` in every module |
| L5 | `minifyEnabled false` in release for all modules — no R8, no shrinking |
| L6 | `WebViewContainer` (engine) has no `WebViewClient` hardening at all |
| L7 | `NativeRuntimeActivity` calls `runtime.init(this)` before `super.onCreate()` |

---

## 6. Documentation drift

| Document | Claims | Reality |
| --- | --- | --- |
| `docs/API.md` | `NativeRuntimeSDKConfig` with `debugEnabled/tag/timeout` | class does not exist; use `RuntimeConfiguration` |
| `docs/05_BUILD_TEST_AND_MIGRATION_GUIDE.md` | compileSdk 34, AGP 8.3.2 | compileSdk 36, AGP 8.10.1 |
| `docs/05` | "229 Java source files … 0 syntax errors … PASSED" | brace balance only; no compilation |
| `docs/SETUP.md` | Gradle 8.7 | wrapper is 8.11.1 |
| `README.md` | version `1.0.1-provisional` | modules are 1.0.0–1.0.6, app is 1.1.0 |
| `BRIDGE_API.md` | `ui.showActionSheet` is an action sheet | alias of bottom sheet |
| `BRIDGE_API.md` | `web.setZoom` zooms the page | text zoom only |
| `BRIDGE_API.md` | `storage.*` is encrypted | plaintext SharedPreferences |
| `BRIDGE_API.md` | unknown actions error | unknown actions return success |

---

## 7. What is genuinely good

Worth preserving during any refactor:

1. **Layer discipline is real.** `native-runtime-contracts` has zero Android UI dependencies and
   is imported by all 14 other modules. That is rare and valuable.
2. **`NativeWebCommandHandler` is the correct pattern.** `MAIN_HANDLER.post(new FutureTask<>(...))`
   with a 5-second timeout is exactly how a bridge handler should marshal to the UI thread.
   Copy it to `NativeUICommandHandler`.
3. **`WebSourceResolver` virtual origin.** Serving assets from
   `https://appassets.androidplatform.net` via `shouldInterceptRequest` makes `fetch`,
   `localStorage`, service workers and ES modules work. Most wrappers get this wrong.
4. **Resource provider abstraction.** `AssetResourceProvider` / `ZipResourceProvider` /
   `FileResourceProvider` behind one `WebResourceProvider` interface is clean and testable.
5. **Per-module `consumer-rules.pro`.** ProGuard rules ship with each library — correct for a
   multi-module AAR.
6. **`WebHostConfig` builder.** Sensible defaults, no required fields, readable call sites.
7. **Zero missing internal imports** across 229 files. The code is internally consistent.

---

## 8. Remediation plan

### Phase 1 — make it build (½ day)

| # | Task | Files |
| --- | --- | --- |
| 1 | Remove `package` attribute from all manifests | 16 × `AndroidManifest.xml` |
| 2 | Run `./gradlew assembleDebug` and fix whatever surfaces next | — |
| 3 | Rewrite `docs/API.md` against `RuntimeConfiguration` | 1 doc |

### Phase 2 — make it honest (2 days)

| # | Task | Files |
| --- | --- | --- |
| 4 | Return an error for unknown routes | `WebBridge.java` |
| 5 | Implement `executeApiRequest` via `NativeRouter` | `NativeRuntime.java` |
| 6 | Make `NativeRuntimeBootstrap` call real initialisers, or rename the report | `NativeRuntimeBootstrap.java` |
| 7 | Return the value from `web.evaluate` | `NativeWebCommandHandler.java` |
| 8 | Marshal snackbar + popup to the UI thread | `SnackbarManager.java`, `PopupMenuManager.java` |

### Phase 3 — make it safe (3 days)

| # | Task | Files |
| --- | --- | --- |
| 9 | Origin validation in `handleWebMessage` | `WebBridge.java`, `BridgeSecurityValidator.java` |
| 10 | Navigation allow-list in `shouldOverrideUrlLoading` | `WebViewHost.java` |
| 11 | Encrypt `storage.*` | `NativeStorageCommandHandler.java` |
| 12 | Implement or remove permission requests | `WebViewHost.java` |

### Phase 4 — make it consistent (2 days)

| # | Task | Files |
| --- | --- | --- |
| 13 | Delete `native-runtime-engine` or wire it to the bridge | module + `settings.gradle` |
| 14 | Merge the two `NativeUIHost` classes | `native-runtime-ui` |
| 15 | Single version source (`version.properties` read by all modules) | all `build.gradle` |
| 16 | Enable R8 + `abortOnError true` for release | all `build.gradle` |
| 17 | Translate `docs/` to English | `docs/` |

**Total: ~7.5 engineering days** to reach a defensible 1.1.0.

---

## 9. Appendix — inventory

### Modules

| Layer | Module | Namespace | Manifest version |
| --- | --- | --- | --- |
| L0 | `native-runtime-contracts` | `com.roni.library.contracts` | 1.0.2 |
| L1 | `native-service-sdk` | `com.roni.library.service` | 1.0.1 |
| L1 | `native-logger-sdk` | `com.roni.library.logger` | — |
| L2 | `native-router-sdk` | `com.roni.library.router` | 1.0.0 |
| L2 | `native-platform-engine` | `com.roni.library.platform` | — |
| L2 | `native-storage-engine` | `com.roni.library.storage` | — |
| L2 | `native-security-engine` | `com.roni.library.security` | — |
| L2 | `native-network-engine` | `com.roni.library.network` | — |
| L2 | `native-analytics-engine` | `com.roni.library.analytics` | 1.0.6 |
| L2 | `native-theme-engine` | `com.roni.library.theme` | — |
| L2 | `native-dev-tools-sdk` | `com.roni.library.devtools` | 1.0.0 |
| L2 | `native-runtime-engine` | `com.roni.library.runtime` | 1.0.0 |
| L2 | `native-runtime-ui` | `com.roni.library.ui` | 1.0.0 |
| L3 | `native-web-bridge-sdk` | `com.roni.library.bridge` | 1.0.1 |
| L4 | `native-runtime-sdk` | `com.roni.library.sdk` | 1.0.1 |
| L5 | `:app` | `com.roni.library.sdk.app` | 1.1.0 (code 2) |

### Build

| Item | Value |
| --- | --- |
| AGP | 8.10.1 |
| Gradle wrapper | 8.11.1 |
| compileSdk / targetSdk | 36 / 36 |
| minSdk | 21 |
| Java | 17 |
| Java files | 229 |
| Java LOC | 48,415 |

### Bridge surface

| Namespace | Actions | Handler |
| --- | --- | --- |
| `ui.*` | ~70 | `NativeUICommandHandler` |
| `web.*` | ~50 | `NativeWebCommandHandler` |
| `device.*` | ~22 | `NativeDeviceCommandHandler` |
| `storage.*` | 7 | `NativeStorageCommandHandler` |
| Events | 6 topics | `WebBridge.emitToWeb` |

---

## 10. Bottom line

The hard part — the layering, the contracts, the virtual-origin asset server, the RPC envelope —
is done and done well. What remains is a short list of integration defects, most of them
one-to-ten-line fixes, plus a documentation pass so the docs stop describing a system that does
not exist.

Fix C1 first. Nothing else can be validated until the project compiles.
