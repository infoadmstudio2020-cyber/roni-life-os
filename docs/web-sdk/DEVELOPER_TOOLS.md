# RONiLiFEOS — Developer Tools

**Native classes:** `RuntimeInspector`, `RuntimeInspectorIntegration`, `RuntimeInspectorSheet`, `NativeActionRegistry`, `QuickBallLayout`, `DevToolsCommandHandler`

---

## 1. What ships for developers

| Tool | Module | Purpose |
| --- | --- | --- |
| Runtime Inspector | `native-dev-tools-sdk` + `app` | Live log, bridge trace, network, page events |
| Quick Ball | `app` | Floating shortcut to 20 native tools |
| `/devtools` route | `native-dev-tools-sdk` | Programmatic access (debug builds only) |
| Chrome DevTools | WebView | DOM, console, network |
| `echo.echo` | `native-router-sdk` | Connectivity test |

---

## 2. Runtime Inspector

### 2.1 Native API

```java
RuntimeInspector inspector = RuntimeInspector.get();
inspector.init(context);

// Attach to a WebView host — captures console, page events, resource errors
RuntimeInspectorIntegration.get().attach(webViewHost);

// Or attach to the bridge directly — captures requests, responses, rejections
RuntimeInspectorIntegration.get().attach(webBridge);

// Show the sheet
RuntimeInspectorSheet sheet = inspector.install(activity);
```

### 2.2 Public methods

| Method | Purpose |
| --- | --- |
| `get()` | Singleton |
| `init(Context)` | Initialise |
| `isInitialized()` | State |
| `getSettings()` | `InspectorSettings` |
| `getCollector()` | `RuntimeLogCollector` |
| `getRepository()` | `RuntimeLogRepository` |
| `ensureSinkAttached()` | Attach the log sink |
| `isSinkAttached()` | State |
| `onProjectOpened(id, name)` | Tag subsequent logs |
| `onProjectClosed(id)` | Clear the tag |
| `startup(checkpoint, detail)` | Record a startup checkpoint |
| `beginStartupTrace()` | Start a startup trace, returns its id |
| `install(Activity)` | Create and show the sheet |
| `getBufferedCount()` | Buffered log entries |
| `record(LogEntry)` | Push an entry manually |
| `release()` | Tear down |

### 2.3 What it captures

`RuntimeInspectorIntegration` hooks:

| Hook | Records |
| --- | --- |
| `onConsoleMessage(priority, message, sourceId, line)` | `console.*` from the page |
| `onPageEvent(event, url)` | Page lifecycle |
| `onResourceError(url, description, mainFrame)` | Failed sub-resources |
| `onRequest(requestId, action, route)` | Bridge request |
| `onResponse(requestId, action, durationMs, success, …)` | Bridge response + timing |
| `onRejected(requestId, action, reason)` | Gate rejection |
| `onEvent(topic)` | Native → web event |

### 2.4 Reading it from JavaScript

The inspector is a **native UI**, not a bridge route. There is no `inspector.*` namespace. To read the same data programmatically, use `/devtools` in a debug build:

```js
const metrics = await Native.call('devtools.getMetrics');
const bridge = await Native.call('devtools.getBridgeMetrics');
const health = await Native.call('devtools.getHealth');
const har = await Native.call('devtools.exportHar');
```

---

## 3. `/devtools` route

**Native class:** `com.roni.library.devtools.handler.DevToolsCommandHandler`
**Availability:** registered only when `NativeRuntimeUIConfig.isDebugEnabled()` is `true`. In release builds every call fails with `ROUTE_NOT_FOUND`.

| Action | Parameters | Returns |
| --- | --- | --- |
| `devtools.getMetrics` | — | performance profiler snapshot |
| `devtools.getHealth` | — | service health map |
| `devtools.getBridgeMetrics` | — | bridge counters |
| `devtools.getManifest` | — | platform manifest |
| `devtools.exportHar` | — | HAR object |
| `devtools.clearTraffic` | — | `{ cleared: true }` |
| `devtools.startProfiling` | — | `{ recording: true }` |
| `devtools.stopProfiling` | — | `{ recording: false }` |
| `devtools.dispatchCdp` | `method` (O, `"Runtime.enable"`) + CDP params | CDP response map |

> ⚠ `devtools.dispatchCdp` reads `method` from the payload. A `domain` key is ignored.

```js
if (await Native.isSupported('devtools')) {
  const health = await Native.call('devtools.getHealth');
  console.table(health);
}
```

---

## 4. Quick Ball

A floating button that opens a panel of native tools. It is an **app-level** feature, not a bridge route — a web app cannot open it programmatically.

### 4.1 Registry API

```java
NativeActionRegistry registry = NativeActionRegistry.get();
registry.ensureDefaults(context);

registry.register(new NativeAction.Builder("my.action", "My Tool", iconRes)
        .category(NativeAction.Category.TOOLS)
        .build());

registry.unregister("my.action");
NativeAction action = registry.find("qb.inspector");
List<NativeAction> all = registry.getAll();
List<NativeAction> tools = registry.getByCategory(NativeAction.Category.TOOLS);
List<String> ids = registry.getIds();
int count = registry.size();
registry.reset();
```

### 4.2 Built-in action ids

| Id | Tool |
| --- | --- |
| `qb.inspector` | Runtime Inspector |
| `qb.quick_settings` | Quick Settings |
| `qb.project_settings` | Project UI Settings |
| `qb.notifications` | Notification Center |
| `qb.notification_settings` | Notification Settings |
| `qb.downloads` | Downloads |
| `qb.file_manager` | File Manager |
| `qb.storage_analyzer` | Storage Analyzer |
| `qb.file_manager_settings` | File Manager Settings |
| `qb.camera` | Camera |
| `qb.automation` | Automation Dashboard |
| `qb.tasks` | Background Tasks |
| `qb.transfers` | Transfers |
| `qb.jobs` | Jobs |
| `qb.alarms` | Alarms |
| `qb.services` | Services |
| `qb.events` | Event Monitor |
| `qb.bootstrap` | Bootstrap |
| `qb.browser` | Browser |

### 4.3 Preferences

`QuickBallPreferences` persists per-action enable/disable, order, rename and icon, plus ball size, opacity, panel layout and label visibility — in the encrypted `quickball_ui` store.

First run after an upgrade enables every action in registration order, matching the previous panel exactly. Unknown ids are dropped; new ids are appended without losing the user's order.

---

## 5. Chrome DevTools

`WebViewHost` enables `WebView.setWebContentsDebuggingEnabled(true)` when `WebHostConfig.isDebugLogging()` is set.

```
chrome://inspect  →  your device  →  the WebView
```

You get DOM inspection, console, network and sources for the page — but **not** the bridge. Bridge traffic appears in the Runtime Inspector.

---

## 6. Connectivity test

```js
const pong = await Native.call('echo.echo', { ping: Date.now() });
// { echo: { ping: 1732091234567 }, echoPath: '/echo', echoAction: 'echo',
//   requestId: 'req_...', timestamp: 0 }
```

If `echo.echo` fails, the problem is the bridge, not your action.

---

## 7. Route inventory

```js
const ROUTES = ['ui','web','device','storage','theme','security','analytics',
                'devtools','network','platform','notification','camera',
                'filemanager','automation','logger','service','runtime'];

async function inventory() {
  const report = {};
  for (const route of ROUTES) {
    report[route] = (await Native.isSupported(route)) ? 'OK' : 'ABSENT';
  }
  return report;
}
```

---

## 8. Debugging checklist

| Step | Check |
| --- | --- |
| 1 | `Native.available()` → `true`? |
| 2 | `echo.echo` resolves? |
| 3 | `inventory()` shows the route you need? |
| 4 | Runtime Inspector shows the request? |
| 5 | The response payload has the keys you expect? |
| 6 | The parameter names match `NATIVE_API_REFERENCE.md`? |

---

## 9. Related documents

- `ERROR_CODES.md` — what the inspector's rejections mean
- `TROUBLESHOOTING_AND_FAQ.md` — symptom-driven diagnosis
- `LOGGING_AND_ANALYTICS.md` — `logger.*` and `analytics.*`
- `ARCHITECTURE.md` §8 — where these tools live
