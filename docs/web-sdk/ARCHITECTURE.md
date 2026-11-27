# RONiLiFEOS — Project Architecture Overview

**Verified against:** `settings.gradle`, 16 Gradle modules, `NativeRuntimeSDK`, `NativeApiIntegration`

---

## 1. What RONiLiFEOS is

RONiLiFEOS is an Android host application that runs **web apps** (HTML/CSS/JS) inside a native shell. The shell provides the chrome (toolbar, drawer, bottom navigation, FAB), the overlays (dialogs, toasts, sheets), and 19 native API routes that the page can call.

A web app is a folder with a `manifest.json` and an `index.html`. It is served from a per-project virtual HTTPS origin, so it behaves like a normal website — `fetch`, `localStorage`, `IndexedDB`, cookies, service workers and ES modules all work.

---

## 2. Module graph

`settings.gradle` declares 16 modules:

```
RONiLiFEOS
├── app                          host application (com.roni.life.os.app)
├── native-runtime-sdk           Layer 4 master orchestration facade
├── native-runtime-engine        runtime state machine, source resolution
├── native-runtime-ui            WebViewHost, NativeRuntimeActivity, ui/web/device/storage handlers
├── native-web-bridge-sdk        bridge protocol, JS client injection, security gates
├── native-router-sdk            route matching, NativeApiRequest/Response, echo, system diagnostics
├── native-runtime-contracts     shared contracts (automation, platform, theme, …)
├── native-platform-engine       device, battery, network, notifications, camera, files, automation
├── native-storage-engine        EncryptedStore, KeyValueStore, StorageScope
├── native-security-engine       Keystore, biometrics, permissions, hashing
├── native-theme-engine          theme manager, CSS variable generation
├── native-analytics-engine      events, metrics, sessions, traces
├── native-network-engine        HTTP client, connection pool, WebSocket
├── native-logger-sdk            leveled logger
├── native-service-sdk           service registry
└── native-dev-tools-sdk         Runtime Inspector, network traffic, CDP, HAR export
```

### 2.1 Layering

```
┌──────────────────────────────────────────────────────────────┐
│ app            ProjectActivity · BootstrapActivity · QuickBall│
├──────────────────────────────────────────────────────────────┤
│ native-runtime-sdk        NativeRuntime (master facade)       │
├──────────────────────────────────────────────────────────────┤
│ native-runtime-engine     NativeRuntimeEngine                 │
│ native-runtime-ui         NativeRuntimeActivity · WebViewHost │
│ native-web-bridge-sdk     WebBridge · NativeJavascriptInterface│
├──────────────────────────────────────────────────────────────┤
│ native-router-sdk         NativeRouter · IApiHandler          │
├──────────────────────────────────────────────────────────────┤
│ platform · storage · security · theme · analytics · network   │
│ logger · service · dev-tools                                  │
├──────────────────────────────────────────────────────────────┤
│ native-runtime-contracts  shared types                        │
└──────────────────────────────────────────────────────────────┘
```

**Rule:** a lower layer never imports an upper layer. `native-web-bridge-sdk` knows nothing about `native-platform-engine`; it only knows `IApiHandler`.

---

## 3. Request path (Web → Native)

```
page JS
  │  window.NativeRuntimeBridgeClient.call('ui.showToast', { message: 'Hi' })
  ▼
NativeJavascriptInterface.postMessage(json)          ← @JavascriptInterface
  │
  ▼
WebBridge.handleWebMessage(rawJson)
  ├─ Gate 1  size ≤ 1 MiB
  ├─ Gate 2  origin in allow-list
  ├─ Gate 3  BridgeSecurityValidator.isMessageValid()
  └─ Gate 4  route exists
  │
  ▼
NativeRouter.dispatch(NativeApiRequest)
  │  PatternRouteMatcher → /ui
  ▼
NativeUICommandHandler.handleRequest(request)
  │  switch (action) { case "showToast": … }
  ▼
NativeApiResponse.success(requestId, { shown: true })
  │
  ▼
WebBridge → BridgeMessage.response(...) → dispatchJs(...)
  │  webView.evaluateJavascript("…_handleNativeMessage('…')")
  ▼
NativeRuntimeBridgeClient resolves the pending promise
```

### 3.1 Response path (Native → Web)

```
handler → WebBridge.emitToWeb(topic, payload)
        → BridgeMessage.event(topic, payload)
        → dispatchJs(...)
        → NativeRuntimeBridgeClient._handleNativeMessage(raw)
        → listeners[topic].forEach(l => l(payload))
```

---

## 4. Project model

### 4.1 On-disk layout

```
app/src/main/assets/ronilifeos/
├── app/
│   ├── config/
│   │   ├── project-registry.json     index of all projects
│   │   └── active-project.json       last opened project
│   └── projects/
│       └── default/
│           ├── manifest.json
│           ├── config/
│           ├── resources/
│           ├── ui/                   ← web app files live here
│           └── sources/
├── runtime/config/
├── shared/{assets,resources,themes}
└── system/{bootstrap,config,splash,database}
```

### 4.2 `manifest.json`

The default project ships with:

```json
{
  "schemaVersion": 1,
  "id": "default",
  "name": "Default project",
  "description": "Generated on first launch. Edit or replace it freely.",
  "version": 1,
  "type": "WEB",
  "entryPoint": "ui/index.html",
  "source": { "type": "FILE", "path": ".", "entryPoint": "ui/index.html" },
  "splash": { "enabled": true, "durationMs": 1600 },
  "bootstrap": { "enabled": true, "renderingMode": "NATIVE", "atStartup": false },
  "generated": true
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `schemaVersion` | int | Manifest schema; currently `1` |
| `id` | string | **Stable identity.** Derives the virtual origin and the storage namespace. Never change after release. |
| `name` | string | Display name |
| `description` | string | Shown in the project list |
| `version` | int | Project revision counter |
| `type` | string | `WEB` |
| `entryPoint` | string | File loaded first, relative to the project root |
| `source.type` | string | `FILE`, `ASSET`, `ZIP`, `URL`, `HTML` |
| `source.path` | string | Root for `FILE`/`ASSET`/`ZIP` |
| `source.entryPoint` | string | Overrides `entryPoint` for this source |
| `splash.enabled` | boolean | Show the splash screen |
| `splash.durationMs` | int | Splash duration |
| `bootstrap.enabled` | boolean | Run the bootstrap renderer |
| `bootstrap.renderingMode` | string | `NATIVE` or `WEB` |
| `bootstrap.atStartup` | boolean | Run bootstrap before the first page load |
| `generated` | boolean | `true` when created by the app |

### 4.3 `project-registry.json`

```json
{
  "schemaVersion": 1,
  "layoutVersion": 3,
  "count": 1,
  "projects": [
    {
      "id": "default",
      "name": "Default project",
      "type": "WEB",
      "sourceType": "FILE",
      "entryPoint": "ui/index.html",
      "directory": "app/projects/default"
    }
  ]
}
```

`layoutVersion` is `3`. The registry is the authoritative list; the app reconciles it against the filesystem on boot.

---

## 5. Source kinds

`WebSourceResolver.resolve()` maps a `WebSource` to a `ResolvedSource`:

| `type` | Resolved to | File access | Notes |
| --- | --- | --- | --- |
| `REMOTE_URL` | the URL itself | off | HTTPS recommended |
| `CONTENT_URI` | the content URI | off | `content://` provider |
| `INLINE_HTML` | `loadDataWithBaseURL` | depends on `baseUrl` | `file://` base enables file access |
| `ASSET` | `https://<host>/…` via `AssetResourceProvider` | off | Bundled in APK |
| `ZIP` | `https://<host>/…` via `ZipResourceProvider` | off | Extracted to a virtual provider |
| `FILE` | `https://<host>/…` via `FileResourceProvider` | on | Directory or single file |

Every local kind is served through the **virtual origin**, never `file://`. That is what makes `fetch`, modules and service workers work.

---

## 6. Virtual origin

```
https://<slug>-<hash8>.runtime.local/
```

| Part | Derivation |
| --- | --- |
| `slug` | First 40 chars of the project id, lowercased, non-alphanumerics replaced |
| `hash8` | First 4 bytes of `SHA-256(projectId)`, 8 lowercase hex chars |
| suffix | `WebPaths.VIRTUAL_SUFFIX` = `.runtime.local` |
| legacy | `appassets.androidplatform.net` |

Derived from the **project id only** — a rename must not move the origin, or the project loses `localStorage`, `IndexedDB`, cookies and service-worker registrations.

---

## 7. Native chrome

`NativeRuntimeActivity` composes six groups:

| Group | Provides |
| --- | --- |
| `SystemUIGroup` | status bar, navigation bar, fullscreen, immersive, edge-to-edge |
| `AppNavigationGroup` | toolbar, drawer, bottom navigation, FAB |
| `ContentGroup` | the WebView host |
| `OverlayGroup` | dialogs, toasts, sheets, loading, snackbars |
| `CustomizationGroup` | theme, colours, component registry |
| `InteractionGroup` | back handling, navigation callbacks |

The chrome is **native views above the WebView**. It does not scroll with the page and is not part of the DOM.

---

## 8. Developer-facing features

| Feature | Module | Entry point |
| --- | --- | --- |
| Runtime Inspector | `native-dev-tools-sdk` + `app` | `RuntimeInspector.get().install(activity)` |
| Quick Ball | `app` | `NativeActionRegistry.get().ensureDefaults(context)` |
| DevTools route | `native-dev-tools-sdk` | `/devtools` (debug builds only) |
| Native Tools | `app` | Quick Ball actions |
| Splash | `app` | `manifest.splash` |
| Bootstrap renderer | `app` | `manifest.bootstrap` |

Details: `DEVELOPER_TOOLS.md`.

---

## 9. Threading model

| Work | Thread |
| --- | --- |
| Bridge message parsing | WebView JS thread → `WebBridge` |
| Route dispatch | caller thread |
| `ui.*` | main thread (posted if needed) |
| `web.*` | main thread, 10 s wait |
| `storage.*` | caller thread, synchronous disk I/O |
| `network.request` | background executor |
| `automation.*` | background executors |
| `notification.*` | main thread for posting |

`web.evaluate` **must not** be called from the main thread — it returns `MAIN_THREAD_BLOCKED`.

---

## 10. Lifecycle

```
SplashActivity (launcher)
  └─ BootstrapActivity
       └─ ProjectActivity  (extends NativeRuntimeActivity)
            ├─ onCreate   → NativeRuntimeUI.init() → registerBridgeRoutes()
            ├─ onResume   → webView.onResume() + resumeTimers()
            ├─ onPause    → webView.onPause()  + pauseTimers()
            └─ onDestroy  → bridge cleared, evaluator cleared, cache kept
```

`pauseTimers()` stops `setTimeout`/`setInterval`. Anything that must survive backgrounding belongs in `automation.*`.

---

## 11. Related documents

- `GETTING_STARTED.md` — first app in 10 minutes
- `JAVASCRIPT_BRIDGE.md` — protocol details
- `NATIVE_API_REFERENCE.md` — all actions
- `DEVELOPER_TOOLS.md` — Runtime Inspector, Quick Ball
- `FILE_ACCESS_AND_RESOURCES.md` — source kinds and resource loading
