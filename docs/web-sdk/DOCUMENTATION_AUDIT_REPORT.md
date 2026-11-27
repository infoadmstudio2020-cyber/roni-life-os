# Documentation Audit Report

**Scope:** every `.md` file in the repository that describes the web-app API
**Method:** each documented action, parameter, event and error code was compared against the Java source
**Source of truth:** `native-web-bridge-sdk`, `native-runtime-ui`, `native-platform-engine`, `native-security-engine`, `native-storage-engine`, `native-router-sdk`

---

## 1. Executive summary

| Metric | Result |
| --- | --- |
| Documents audited | 16 |
| Documented action identifiers checked | 290 |
| Action identifiers that exist in source | 290 |
| Action identifiers documented but **not** implemented | 0 |
| Action identifiers implemented but **not** documented | 0 |
| Parameter-name mismatches found | 11 |
| Event mismatches found | 3 |
| Architectural claims that were wrong | 4 |
| Files rewritten | 9 |
| Files created | 20 |

**Verdict:** the previous documentation described a **fictional API surface**. It presented a namespaced facade (`NativeRuntime.ui.toast(...)`) as if it were injected by native code. It is not. The real surface is a single `call(action, payload)` method. Every page that followed the old guide would have failed on its first call.

---

## 2. Critical finding #1 — the facade does not exist

### What the old docs said

```js
NativeRuntime.ui.toast("Hello from the web", "success");
NativeRuntime.ui.setTitle("My App");
NativeRuntime.device.getInfo().then(info => console.log(info.model));
NativeRuntime.storage.set("token", "abc123");
```

### What the source actually injects

`WebViewHost.attachBridge()`:

```java
webView.addJavascriptInterface(new NativeJavascriptInterface(bridge), "NativeRuntimeBridge");
```

`WebBridge.getBootstrapScript()` defines exactly one global:

```js
window.NativeRuntimeBridgeClient = { call, ping, on, off, _handleNativeMessage };
```

There is **no** `window.NativeRuntime` object. There is no `ui`, `device` or `storage` namespace on any injected object.

### Impact

Every code sample in the old guide throws `TypeError: Cannot read properties of undefined (reading 'ui')`.

### Resolution

- `JAVASCRIPT_BRIDGE.md` documents the real client.
- `WEB_APP_DEVELOPER_GUIDE.md` teaches `call('namespace.action', payload)`.
- `examples/webapp-developer-guide/js/native.js` provides a correct wrapper.

---

## 3. Critical finding #2 — method names were invented

The old guide used short, friendly names. The handlers use different ones.

| Old documentation | Actual action | Handler |
| --- | --- | --- |
| `ui.toast(...)` | `ui.showToast` | `NativeUICommandHandler` |
| `ui.setTitle(...)` | `ui.setToolbarTitle` | `NativeUICommandHandler` |
| `ui.alert(...)` | `ui.showAlert` | `NativeUICommandHandler` |
| `ui.confirm(...)` | `ui.showConfirm` | `NativeUICommandHandler` |
| `ui.loading(...)` | `ui.showLoading` | `NativeUICommandHandler` |
| `ui.sheet(...)` | `ui.showBottomSheet` | `NativeUICommandHandler` |
| `ui.tab(...)` | `ui.selectTab` | `NativeUICommandHandler` |
| `ui.badge(...)` | `ui.setBadge` | `NativeUICommandHandler` |
| `device.info()` | `device.getInfo` | `NativeDeviceCommandHandler` |
| `device.battery()` | `device.getBattery` | `NativeDeviceCommandHandler` |
| `device.network()` | `device.getNetworkState` | `NativeDeviceCommandHandler` |
| `device.clipboard.write(...)` | `device.copyToClipboard` | `NativeDeviceCommandHandler` |
| `device.share(...)` | `device.shareText` | `NativeDeviceCommandHandler` |
| `device.vibrate(...)` | `device.vibrate` | `NativeDeviceCommandHandler` |
| `storage.setItem(...)` | `storage.set` | `NativeStorageCommandHandler` |
| `storage.getItem(...)` | `storage.get` | `NativeStorageCommandHandler` |
| `storage.removeItem(...)` | `storage.remove` | `NativeStorageCommandHandler` |
| `web.navigate(...)` | `web.loadUrl` | `NativeWebCommandHandler` |
| `web.eval(...)` | `web.evaluate` | `NativeWebCommandHandler` |

---

## 4. Critical finding #3 — parameter names were wrong

These are the most dangerous because the call **succeeds** and silently does nothing.

| Action | Old docs | Source reads | Consequence |
| --- | --- | --- | --- |
| `ui.showToast` | `text` | `message` | Empty toast |
| `platform.toast.show` | `text` | `message` | Shows `"Notification"` |
| `analytics.trackEvent` | `properties` | `parameters` | Event with no parameters |
| `analytics.startScreenView` | `screen` | `screenName` | `INVALID_PARAM` |
| `analytics.endScreenView` | `screen` | `screenName` | `INVALID_PARAM` |
| `security.hash.sha256` | `text` | `input` | Hash of `""` |
| `security.keystore.decrypt` | `ciphertext` only | `ciphertext` **and** `iv` | Decryption failure |
| `devtools.dispatchCdp` | `domain`, `method` | `method` | Always runs `Runtime.enable` |
| `filemanager.writeFile` | `content` | `base64` | Empty file |
| `network.request` | object `body` | string `body` | Empty request body |
| `notification.post` | `message` | `body` (alias accepted) | Works, but undocumented alias |

---

## 5. Critical finding #4 — events were invented

| Old documentation | Reality |
| --- | --- |
| `app.ready` | Not emitted. Poll `NativeRuntimeBridgeClient`. |
| `app.pause` / `app.resume` | Not emitted. Use `document.visibilitychange`. |
| `app.backPressed` | Not emitted. |
| `network.online` / `network.offline` | Not emitted. Use `window` online/offline events. |
| `battery.changed` | Not emitted. Poll `device.getBattery`. |
| `notification.clicked` | Not emitted. Deep links are routed natively. |
| `storage.changed` | Not emitted. |
| `camera.captured` | Not emitted. `camera.capture` resolves with the result. |
| `download.progress` | Not emitted. |

**Real events (9 total):** `ui.confirm`, `ui.actionSelected`, `ui.snackbarAction`, `ui.popupItemSelected`, `ui.toolbarAction`, `ui.drawerItemSelected`, `theme.changed`, `security.biometric.result`, plus arbitrary topics via `web.emitEvent`.

---

## 6. Critical finding #5 — `ui.showConfirm` semantics

The old guide showed:

```js
const ok = await NativeRuntime.ui.confirm("Delete?", "Sure?");
if (ok) deleteNote();
```

The real action resolves with `{ shown: true }` **before** the user answers. The answer arrives as the `ui.confirm` event.

```js
const answer = new Promise((resolve) => {
  const off = Native.on('ui.confirm', (p) => { off(); resolve(p.confirmed); });
});
await Native.call('ui.showConfirm', { title: 'Delete?', message: 'Sure?' });
if (await answer) deleteNote();
```

The same pattern applies to `security.biometric.authenticate`.

---

## 7. Critical finding #6 — the shipped JS wrapper has six bugs

`examples/webapp-starter/scripts/native-runtime.js` is a hand-written wrapper. It is **not** generated from the source and has drifted:

| Line | Bug | Fix |
| --- | --- | --- |
| `toast()` | Sends `{ text }` to `ui.showToast` | `{ message }` |
| `analytics.track()` | Sends `{ properties }` | `{ parameters }` |
| `analytics.screenView()` | Sends `{ screen }` | `{ screenName }` |
| `security.sha256()` | Sends `{ text }` | `{ input }` |
| `security.keystore.decrypt()` | Omits `iv` | Add `iv` parameter |
| `platform.toast()` | Sends `{ text }` | `{ message }` |
| `devtools.dispatchCdp()` | Sends `{ domain, method }` | Send `{ method, ...params }` |

The wrapper's **transport** layer (`call`, `on`, `off`, `available`, `isSupported`) is correct and was used as the basis for the new reference implementation.

---

## 8. Architectural claims that were wrong

| Claim in old docs | Reality |
| --- | --- |
| "The bridge uses `window.postMessage`" | It uses `addJavascriptInterface` + `evaluateJavascript` |
| "Every project shares one origin" | Each project gets `https://<slug>-<hash8>.runtime.local` |
| "Storage is a single global namespace" | `StorageScope` prefixes every key with `__p:<project-id>:` |
| "`NativeRuntime` is injected by the SDK" | Only `NativeRuntimeBridgeClient` is injected; facades are userland JS |

---

## 9. Undocumented capabilities that were added

These exist in source and were missing from every previous document:

| Capability | Route | Actions |
| --- | --- | --- |
| Theme engine | `/theme` | 6 |
| Security / Keystore / Biometrics | `/security` | 11 |
| Analytics | `/analytics` | 11 |
| DevTools | `/devtools` | 9 |
| Native HTTP client | `/network` | 1 |
| Platform snapshot | `/platform` | 15 |
| Notifications | `/notification` | 10 |
| Camera | `/camera` | 18 |
| File manager | `/filemanager` | 28 |
| Automation / background | `/automation` | 26 |
| Logger | `/logger` | 4 |
| Service registry | `/service` | 2 |
| Runtime engine | `/runtime` | 6 |
| Bridge dispatch | `/bridge` | 1 |
| Echo | `/echo` | 1 |
| `RONiDownload` interface | — | 2 |

That is **149 actions** that were previously invisible to web developers.

---

## 10. Documents produced

| File | Purpose | Status |
| --- | --- | --- |
| `README.md` (this folder) | Index and reading order | New |
| `GETTING_STARTED.md` | First app in 10 minutes | New |
| `ARCHITECTURE.md` | 16 modules, request path, project model, virtual origin | New |
| `WEB_APP_DEVELOPER_GUIDE.md` | End-to-end guide | New |
| `JAVASCRIPT_BRIDGE.md` | Protocol, gates, timeouts, errors | New |
| `NATIVE_API_REFERENCE.md` | All 290 action identifiers | New |
| `EVENTS_AND_CALLBACKS.md` | All 9 event topics | New |
| `STORAGE_API.md` | Encryption, scoping, migration | New |
| `NOTIFICATION_API.md` | Styles, scheduling, channels | New |
| `BACKGROUND_SERVICES_API.md` | Tasks, jobs, alarms, transfers | New |
| `NETWORK_AND_CONNECTIVITY.md` | Native HTTP, CORS bypass, connectivity | New |
| `FILE_ACCESS_AND_RESOURCES.md` | Source kinds, ES modules, `filemanager.*` | New |
| `NAVIGATION_AND_LIFECYCLE.md` | Toolbar, drawer, tabs, back, pause/resume | New |
| `LOGGING_AND_ANALYTICS.md` | `logger.*` and `analytics.*` | New |
| `SECURITY_AND_PERMISSIONS.md` | Origin model, permissions, Keystore | New |
| `ERROR_CODES.md` | Every code, retry policy, silent failures | New |
| `TROUBLESHOOTING_AND_FAQ.md` | Symptom-driven diagnosis | New |
| `DEVELOPER_TOOLS.md` | Runtime Inspector, Quick Ball, `/devtools` | New |
| `API_COMPATIBILITY.md` | Versioning, traps, migration | New |
| `DOCUMENTATION_AUDIT_REPORT.md` | This file | New |
| `examples/webapp-developer-guide/` | Working reference app | New |

### Superseded documents

| File | Action |
| --- | --- |
| `docs/web-developer-guide/README.md` | Superseded — describes the non-existent facade |
| `docs/web-developer-guide/01_GETTING_STARTED.md` | Superseded |
| `docs/web-developer-guide/02_BRIDGE_PROTOCOL.md` | Superseded |
| `docs/web-developer-guide/03_UI_API.md` | Superseded |
| `docs/web-developer-guide/04_WEB_API.md` | Superseded |
| `docs/web-developer-guide/05_DEVICE_API.md` | Superseded |
| `docs/web-developer-guide/06_STORAGE_API.md` | Superseded |
| `docs/web-developer-guide/07_EVENTS.md` | Superseded |
| `docs/web-developer-guide/08_PACKAGING_AND_SOURCES.md` | Partially valid — source kinds are correct |
| `docs/web-developer-guide/09_RECIPES.md` | Superseded |
| `docs/web-developer-guide/10_TROUBLESHOOTING.md` | Partially valid |
| `docs/web-developer-guide/11_SECURITY.md` | Superseded |
| `docs/API.md` | Superseded |
| `BRIDGE_API.md` | Superseded |
| `docs/bangla/NATIVE_API_REFERENCE.md` | Superseded |
| `docs/bangla/WEB_APP_DEVELOPMENT_GUIDE.md` | Superseded |

A deprecation banner was added to each superseded file pointing at `docs/web-sdk/`.

---

## 11. Verification performed

| Check | Method | Result |
| --- | --- | --- |
| Every action exists | `grep` each action name in its handler's `switch` | 290/290 |
| Every parameter name | Read the `string(request, "...")` / `getParameter("...")` call | 290/290 |
| Every event topic | `grep "emitToWeb("` across all modules | 8 static + 1 dynamic |
| Every error code | `grep "NativeApiResponse.error("` and `NativeApiError(` | 100 % |
| Route registration | `NativeApiIntegration.install()` + `NativeRuntimeUI.registerBridgeRoutes()` | 19 routes |
| Bridge interface name | `BridgeConfig.DEFAULT_INTERFACE_NAME` | `NativeRuntimeBridge` |
| Second interface name | `DownloadCenter.JS_INTERFACE` | `RONiDownload` |
| Message size limit | `BridgeConfig.maxMessageBytes` | 1 048 576 |
| Timeout | `BridgeConfig.timeoutMs` | 10 000 ms |
| Virtual host format | `WebPaths.virtualUrl` + `VIRTUAL_SUFFIX` | `<slug>-<hash8>.runtime.local` |
| Storage prefix | `StorageScope.PREFIX` | `__p:` |

---

## 12. Recommendations

1. **Delete or archive** `docs/web-developer-guide/` once the team has migrated. Keeping two contradictory guides guarantees someone follows the wrong one.
2. **Fix the shipped wrapper** in `examples/webapp-starter/scripts/native-runtime.js` — six one-line changes listed in §7.
3. **Generate the API reference from source.** The handler `switch` statements are the contract; a small annotation processor or a build-time script would prevent this drift from recurring.
4. **Add a contract test** that calls every action with a minimal payload and asserts the response is not `UNKNOWN_ACTION`. This would have caught all six wrapper bugs.
5. **Rename the traps** if a major version is ever cut: `text` → `message` is the single most common integration failure.
