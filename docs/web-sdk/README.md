# RONiLiFEOS — Web SDK Documentation

**The authoritative documentation for building web apps that run inside RONiLiFEOS.**

Every action, parameter, event and error code in this folder was read from the Java source. Nothing is inferred from a class name.

---

## Start here

| If you want to… | Read |
| --- | --- |
| Build your first app in 10 minutes | [`GETTING_STARTED.md`](GETTING_STARTED.md) |
| Understand the shell and modules | [`ARCHITECTURE.md`](ARCHITECTURE.md) |
| Build a full app | [`WEB_APP_DEVELOPER_GUIDE.md`](WEB_APP_DEVELOPER_GUIDE.md) |
| Understand how the bridge works | [`JAVASCRIPT_BRIDGE.md`](JAVASCRIPT_BRIDGE.md) |
| Look up an action | [`NATIVE_API_REFERENCE.md`](NATIVE_API_REFERENCE.md) |
| React to a native event | [`EVENTS_AND_CALLBACKS.md`](EVENTS_AND_CALLBACKS.md) |
| Save data | [`STORAGE_API.md`](STORAGE_API.md) |
| Show a notification | [`NOTIFICATION_API.md`](NOTIFICATION_API.md) |
| Run work in the background | [`BACKGROUND_SERVICES_API.md`](BACKGROUND_SERVICES_API.md) |
| Call an HTTP API | [`NETWORK_AND_CONNECTIVITY.md`](NETWORK_AND_CONNECTIVITY.md) |
| Read or write files | [`FILE_ACCESS_AND_RESOURCES.md`](FILE_ACCESS_AND_RESOURCES.md) |
| Navigate and handle lifecycle | [`NAVIGATION_AND_LIFECYCLE.md`](NAVIGATION_AND_LIFECYCLE.md) |
| Log or measure behaviour | [`LOGGING_AND_ANALYTICS.md`](LOGGING_AND_ANALYTICS.md) |
| Secure your app | [`SECURITY_AND_PERMISSIONS.md`](SECURITY_AND_PERMISSIONS.md) |
| Decode an error | [`ERROR_CODES.md`](ERROR_CODES.md) |
| Debug a problem | [`TROUBLESHOOTING_AND_FAQ.md`](TROUBLESHOOTING_AND_FAQ.md) |
| Use the Runtime Inspector | [`DEVELOPER_TOOLS.md`](DEVELOPER_TOOLS.md) |
| Check version compatibility | [`API_COMPATIBILITY.md`](API_COMPATIBILITY.md) |
| See what was wrong before | [`DOCUMENTATION_AUDIT_REPORT.md`](DOCUMENTATION_AUDIT_REPORT.md) |

---

## The one thing to remember

```js
await window.NativeRuntimeBridgeClient.call('ui.showToast', { message: 'হ্যালো' });
```

That is the entire integration surface. Everything else is a wrapper around it.

- The object is `NativeRuntimeBridgeClient` — **not** `NativeRuntime`.
- The action is `namespace.action` — **not** a method name.
- The parameter is `message` — **not** `text`.

---

## Document map

```
docs/web-sdk/
├── README.md                        ← you are here
├── GETTING_STARTED.md               first app in 10 minutes
├── ARCHITECTURE.md                  16 modules, request path, project model
├── WEB_APP_DEVELOPER_GUIDE.md       end-to-end guide, packaging, debugging
├── JAVASCRIPT_BRIDGE.md             protocol, gates, timeouts, error codes
├── NATIVE_API_REFERENCE.md          290 action identifiers across 19 routes
├── EVENTS_AND_CALLBACKS.md          9 event topics + patterns
├── STORAGE_API.md                   AES-GCM store, project scoping, migration
├── NOTIFICATION_API.md              5 styles, scheduling, channels, deep links
├── BACKGROUND_SERVICES_API.md       tasks, jobs, alarms, transfers, event log
├── NETWORK_AND_CONNECTIVITY.md      native HTTP, CORS bypass, connectivity
├── FILE_ACCESS_AND_RESOURCES.md     source kinds, ES modules, filemanager
├── NAVIGATION_AND_LIFECYCLE.md      toolbar, drawer, tabs, back, pause/resume
├── LOGGING_AND_ANALYTICS.md         logger levels, events, metrics, traces
├── SECURITY_AND_PERMISSIONS.md      origin model, permissions, Keystore, biometrics
├── ERROR_CODES.md                   every code, retry policy, silent failures
├── TROUBLESHOOTING_AND_FAQ.md       symptom-driven diagnosis
├── DEVELOPER_TOOLS.md               Runtime Inspector, Quick Ball, /devtools
├── API_COMPATIBILITY.md             versioning, parameter traps, migration
└── DOCUMENTATION_AUDIT_REPORT.md    what the old docs got wrong
```

Working code: [`../../examples/webapp-developer-guide/`](../../examples/webapp-developer-guide/)

---

## Route cheat sheet

| Namespace | Actions | What it does |
| --- | --- | --- |
| `ui` | 65 | Toolbar, drawer, tabs, FAB, dialogs, toasts, sheets, system bars |
| `web` | 55 | WebView navigation, zoom, cookies, cache, settings, evaluate |
| `device` | 30 | Device info, battery, network, clipboard, share, orientation |
| `storage` | 7 | Encrypted, project-scoped key/value store |
| `theme` | 6 | Theme mode, colours, CSS variables |
| `security` | 11 | Keystore, biometrics, permissions, SHA-256 |
| `analytics` | 11 | Events, metrics, sessions, traces, screen views |
| `devtools` | 9 | Metrics, health, HAR export, CDP (debug builds) |
| `network` | 1 | Native HTTP client (CORS-free) |
| `platform` | 15 | Device/battery/network/storage snapshot, clipboard, toast |
| `notification` | 10 | Post, update, schedule, cancel, list, channels |
| `camera` | 18 | Capabilities, open, capture, record, zoom, flash |
| `filemanager` | 28 | Read, write, copy, move, delete, search, archive |
| `automation` | 26 | Tasks, jobs, alarms, transfers, event log, services |
| `logger` | 4 | Native log with levels |
| `service` | 2 | Service registry |
| `runtime` | 6 | Runtime engine, vitals, source resolution |
| `bridge` | 1 | Bridge dispatch |
| `echo` | 1 | Connectivity test |

---

## Bengali quick reference

```js
// টোস্ট
await Native.call('ui.showToast', { message: 'সংরক্ষিত', type: 'success' });

// ডেটা রাখা
await Native.call('storage.set', { key: 'নাম', value: 'রহিম' });

// ডেটা পড়া
const { value } = await Native.call('storage.get', { key: 'নাম' });

// ডায়ালগ
Native.on('ui.confirm', ({ confirmed }) => console.log(confirmed));
await Native.call('ui.showConfirm', { title: 'মুছবেন?', message: 'ফিরিয়ে আনা যাবে না' });

// টুলবার
await Native.call('ui.setToolbarTitle', { title: 'আমার অ্যাপ' });

// ডিভাইস তথ্য
const info = await Native.call('device.getInfo');
```

**সতর্কতা:** `text` নয়, `message`। `properties` নয়, `parameters`। `screen` নয়, `screenName`। `text` নয়, `input`।

---

## Support matrix

| Requirement | Minimum |
| --- | --- |
| Android | API 24 (Android 7.0) |
| WebView | Chromium 60+ (ES2017) |
| Bridge protocol | 1.0 |
| `NativeRuntimeBridgeClient` | Injected on `onPageFinished` |

---

## Conventions used in these documents

| Notation | Meaning |
| --- | --- |
| **R** | Required parameter |
| **O** | Optional parameter (default shown) |
| **→** | Resolved value of the promise |
| ⚠ | Behaviour that differs from what the name suggests |
| `code` | Error code — the prefix of the rejection message |
