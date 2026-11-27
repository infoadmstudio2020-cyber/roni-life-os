> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](docs/web-sdk/README.md) for the verified documentation.

---

# NativeRuntime Bridge API

All calls go through `window.NativeRuntimeBridgeClient.call(action, payload)`.
The sample `js/app.js` wraps this in `window.NativeRuntime` with four namespaces.

```js
NativeRuntime.ui.toast("Hello from web");
NativeRuntime.device.getInfo().then(console.log);
NativeRuntime.storage.set("token", "abc123");
NativeRuntime.web.setZoom(120);
```

## `ui.*` — Native UI

| Action | Payload | Description |
| --- | --- | --- |
| `ui.showAlert` | `{title, message}` | Native alert dialog |
| `ui.showConfirm` | `{title, message, confirmText, cancelText}` | Confirm dialog; emits `ui.confirm` |
| `ui.showToast` | `{message, type, duration}` | Toast (`info/success/warning/error`) |
| `ui.showBottomSheet` | `{title, items:[{title, destructive}]}` | Native bottom sheet |
| `ui.showActionSheet` | `{title, items:[...]}` | Action sheet; emits `ui.actionSelected` |
| `ui.showLoading` | `{text, cancelable}` | Blocking loading overlay |
| `ui.hideLoading` | `{}` | Hide loading overlay |
| `ui.showSnackbar` | `{message, actionTitle, duration}` | Snackbar; emits `ui.snackbarAction` |
| `ui.showPopupMenu` | `{items:[{id, title}]}` | Anchored popup menu; emits `ui.popupItemSelected` |
| `ui.dismissDialog` | `{}` | Dismiss current dialog |
| `ui.dismissBottomSheet` | `{}` | Dismiss bottom sheet |
| `ui.dismissToast` | `{}` | Dismiss toast |
| `ui.setToolbarTitle` | `{title}` | Set toolbar title |
| `ui.setToolbarSubtitle` | `{subtitle}` | Set toolbar subtitle |
| `ui.setToolbarVisible` | `{visible}` | Show/hide toolbar |
| `ui.setNavigationIconVisible` | `{visible}` | Show/hide navigation icon |
| `ui.addToolbarAction` | `{id, label}` | Add toolbar action; emits `ui.toolbarAction` |
| `ui.removeToolbarAction` | `{id}` | Remove toolbar action |
| `ui.clearToolbarActions` | `{}` | Remove all toolbar actions |
| `ui.getToolbarActions` | `{}` | List toolbar actions |
| `ui.openDrawer` / `ui.closeDrawer` / `ui.toggleDrawer` | `{}` | Drawer control |
| `ui.isDrawerOpen` | `{}` | Drawer state |
| `ui.selectDrawerItem` | `{id}` | Select drawer item |
| `ui.getSelectedDrawerItem` | `{}` | Selected drawer item id |
| `ui.addDrawerItem` | `{id, title}` | Add drawer item; emits `ui.drawerItemSelected` |
| `ui.removeDrawerItem` | `{id}` | Remove drawer item |
| `ui.selectTab` | `{tabId}` | Select bottom navigation tab |
| `ui.addTab` | `{tabId, label}` | Add bottom navigation tab |
| `ui.setBadge` | `{tabId, count}` | Set tab badge |
| `ui.getBadge` | `{tabId}` | Get tab badge |
| `ui.getTabs` | `{}` | List tabs |
| `ui.getSelectedTab` | `{}` | Selected tab id |
| `ui.setFabVisible` | `{visible}` | Show/hide FAB |
| `ui.isFabVisible` | `{}` | FAB visibility |
| `ui.setComponentVisible` | `{category, key, visible}` | Show/hide a component |
| `ui.setComponentEnabled` | `{category, key, enabled}` | Enable/disable a component |
| `ui.getComponentState` | `{category, key}` | Component state |
| `ui.listComponents` | `{}` | List registered components |
| `ui.setStatusBar` | `{color, lightIcons}` | Status bar color/icons |
| `ui.setNavigationBar` | `{color, lightButtons}` | Navigation bar color/icons |
| `ui.setFullscreen` | `{enabled}` | Fullscreen mode |
| `ui.setImmersive` | `{enabled}` | Immersive mode |
| `ui.setEdgeToEdge` | `{enabled}` | Edge-to-edge layout |
| `ui.getSystemUIState` | `{}` | System UI state |
| `ui.applyTheme` | `{primaryColor, surfaceColor, ...}` | Apply runtime theme |
| `ui.getTheme` | `{}` | Current theme |
| `ui.getConfig` | `{}` | Full UI configuration |
| `ui.reload` | `{}` | Reload WebView |
| `ui.getWebViewState` | `{}` | URL and history state |
| `ui.setWebViewSource` | `{type, location, entryPoint}` | Switch WebView source |
| `ui.navigateBack` | `{}` | Activity back |
| `ui.openExternal` | `{url}` | Open URL externally |

## `web.*` — WebView Control

`web.loadSource`, `web.loadUrl`, `web.loadHtml`, `web.reload`, `web.stop`,
`web.goBack`, `web.goForward`, `web.canGoBack`, `web.canGoForward`,
`web.clearHistory`, `web.getState`, `web.evaluate`, `web.setZoom`, `web.getZoom`,
`web.setUserAgent`, `web.getUserAgent`, `web.getSettings`, `web.setSettings`,
`web.clearCache`, `web.setCacheMode`, `web.setCookie`, `web.getCookies`,
`web.clearCookies`, `web.setBackgroundColor`, `web.setProgressBarVisible`,
`web.emitEvent`.

WebSettings modifiers: `web.setJavaScript`, `web.setDomStorage`,
`web.setAllowFileAccess`, `web.setAllowContentAccess`, `web.setMixedContentMode`,
`web.setSafeBrowsing`, `web.setSupportZoom`, `web.setBuiltInZoomControls`,
`web.setDisplayZoomControls`, `web.setLoadWithOverviewMode`,
`web.setUseWideViewPort`, `web.setDefaultTextEncodingName`,
`web.setStandardFontFamily`, `web.setDefaultFontSize`, `web.setMinimumFontSize`,
`web.setDatabaseEnabled`, `web.setGeolocationEnabled`, `web.setSaveFormData`,
`web.setJavaScriptCanOpenWindowsAutomatically`, `web.setSupportMultipleWindows`,
`web.setMediaPlaybackRequiresUserGesture`, `web.setOffscreenPreRaster`,
`web.setForceDark`, `web.setAlgorithmicDarkeningAllowed`,
`web.setBlockNetworkImage`, `web.setBlockNetworkLoads`,
`web.setLoadsImagesAutomatically`.

## `device.*` — Device & App

`device.getInfo`, `device.getAppInfo`, `device.getDeviceInfo`,
`device.getScreenInfo`, `device.getLocale`, `device.getNetworkState`,
`device.getBattery`, `device.getSafeArea`, `device.copyToClipboard`,
`device.readClipboard`, `device.shareText`, `device.vibrate`, `device.openUrl`,
`device.setOrientation`, `device.setKeepScreenOn`, `device.setBrightness`,
`device.getBrightness`, `device.finish`, `device.openAppSettings`,
`device.isAppInstalled`, `device.openApp`, `device.openFile`, `device.log`.

## `storage.*` — Persistent Storage

`storage.get`, `storage.set`, `storage.remove`, `storage.clear`,
`storage.keys`, `storage.has`, `storage.getAll`.


## `automation.*` — Background & Automation

Route: `automation`. The active project is resolved natively; a `projectId` parameter is ignored.

| Action | Parameters | Result |
|--------|------------|--------|
| `getStatus` | — | `projectId`, counts, `workManagerAvailable` |
| `getCapabilities` | — | feature flags (`speech` is always `false`) |
| `submitTask` | `name`, `type`, `payload?`, `maxAttempts?` | task object |
| `cancelTask` | `taskId` | `cancelled` |
| `getTask` | `taskId` | task object |
| `listTasks` | — | `items`, `count` |
| `clearTasks` | — | `removed` |
| `scheduleJob` | `id?`, `name`, `workerType`, `requiresNetwork?`, `requiresCharging?`, `requiresIdle?`, `initialDelayMs?`, `repeatIntervalMs?`, `maxAttempts?`, `payload?` | job object |
| `cancelJob` | `jobId` | `cancelled` |
| `listJobs` | — | `items`, `count` |
| `setAlarm` | `id?`, `label`, `triggerAt` (epoch ms), `intervalMs?`, `exact?`, `payload?` | alarm object |
| `cancelAlarm` | `alarmId` | `cancelled` |
| `listAlarms` | — | `items`, `count` |
| `download` | `url`, `path`, `headers?`, `expectedBytes?` | transfer object |
| `upload` | `url`, `path`, `headers?` | transfer object |
| `cancelTransfer` | `transferId` | `cancelled` |
| `getTransfer` | `transferId` | transfer object |
| `listTransfers` | — | `items`, `count` |
| `publishEvent` | `topic`, `payload?` | event object |
| `getEvents` | `sinceSequence?`, `limit?` | `items`, `count`, `lastSequence` |
| `clearEvents` | — | `cleared` |
| `listServices` | — | `items`, `count` |
| `getServiceStatus` | `name` | service object |
| `startService` | `name` | `started` |
| `stopService` | `name` | `stopped` |

Transfer objects expose `state` (`QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`),
`bytesTransferred`, `totalBytes`, `percent` and `error`.

Automation error codes: `NO_ACTIVE_PROJECT`, `INVALID_ARGUMENT`, `TASK_NOT_FOUND`,
`TASK_LIMIT`, `JOB_NOT_FOUND`, `JOB_LIMIT`, `WORK_UNAVAILABLE`, `ALARM_NOT_FOUND`,
`ALARM_LIMIT`, `TRANSFER_NOT_FOUND`, `TRANSFER_LIMIT`, `LOW_STORAGE`, `SERVICE_NOT_FOUND`,
`UNSUPPORTED`, `IO_ERROR`.

## Events

Native callbacks are delivered through `NativeRuntimeBridgeClient.on(topic, listener)`:

- `ui.actionSelected` — `{title, index}`
- `ui.toolbarAction` — `{id, label}`
- `ui.drawerItemSelected` — `{id, title}`
- `ui.popupItemSelected` — `{title, index}`
- `ui.snackbarAction` — `{actionTitle}`
- `ui.confirm` — `{confirmed}`

## Origin validation

Every inbound message is origin-checked before dispatch. The bridge reads the current page
origin from an `OriginProvider` supplied by the host:

```java
bridge.setOriginProvider(() -> currentPageOrigin);
```

| Condition | Result |
| --- | --- |
| Origin in the allow-list | dispatched |
| Origin not in the allow-list | `ERROR` with `ORIGIN_REJECTED: <origin>` |
| Origin is `null` / provider missing | `ERROR` with `ORIGIN_REJECTED: unknown` |
| `requireOriginValidation(false)` | gate skipped |

Default allow-list: `https://appassets.androidplatform.net`. An empty allow-list denies
everything; `addAllowedOrigin("*")` opts out explicitly.

## Timeouts and payload limits

- `timeoutMs` (default `10_000`) — an unanswered `REQUEST` emits `REQUEST_TIMEOUT` and is
  removed from the pending table. A late response for the same id is dropped.
- `maxMessageBytes` (default `1_048_576`) — oversized messages are rejected with
  `PAYLOAD_TOO_LARGE` before parsing.

## Error codes

| Code | Meaning |
| --- | --- |
| `ORIGIN_REJECTED` | Page origin is not allowed |
| `PAYLOAD_TOO_LARGE` | Message exceeds `maxMessageBytes` |
| `REQUEST_TIMEOUT` | No response within `timeoutMs` |
| `ROUTE_NOT_FOUND` | No handler registered for the resolved path |
| `Security validation failed` | Missing `id` or `action` |
