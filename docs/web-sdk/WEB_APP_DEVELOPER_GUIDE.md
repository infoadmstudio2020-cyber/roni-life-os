# RONiLiFEOS — Web App Developer Guide

**Audience:** front-end developers who build HTML, CSS and JavaScript and want to run that app inside RONiLiFEOS with access to native Android capabilities.

**You do not need to write Java.** Everything is driven from JavaScript through one object: `window.NativeRuntimeBridgeClient`.

---

## 1. How a web app runs inside RONiLiFEOS

```
┌───────────────────────────────────────────────────────────────┐
│  RONiLiFEOS (Android)                                         │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │ NativeRuntimeActivity                                   │  │
│  │  Toolbar · Drawer · Bottom nav · FAB · System bars      │  │
│  ├─────────────────────────────────────────────────────────┤  │
│  │ WebViewHost                                             │  │
│  │   ┌───────────────────────────────────────────────────┐ │  │
│  │   │  YOUR APP  (index.html + css + js)                │ │  │
│  │   │  window.NativeRuntimeBridgeClient.call(...)       │ │  │
│  │   └───────────────────────────────────────────────────┘ │  │
│  ├─────────────────────────────────────────────────────────┤  │
│  │ Toasts · Dialogs · Sheets · Loading · Snackbars         │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

Your files are served from a **virtual HTTPS origin**, not from `file://`:

```
https://<project-slug>-<hash8>.runtime.local/index.html
```

That origin gives you `fetch()`, `XMLHttpRequest`, `localStorage`, `IndexedDB`, cookies and service workers with normal browser semantics. Each project gets its **own** origin, so project A cannot read project B's `localStorage`.

---

## 2. Minimum viable app

### 2.1 File layout

```
my-web-app/
├── manifest.json      ← required by the project importer
├── index.html
├── css/style.css
└── js/app.js
```

### 2.2 `manifest.json`

```json
{
  "id": "my-web-app",
  "name": "My Web App",
  "version": "1.0.0",
  "entryPoint": "index.html",
  "description": "A RONiLiFEOS web app",
  "theme_color": "#2563EB",
  "background_color": "#F8FAFC",
  "display": "standalone",
  "orientation": "portrait"
}
```

`entryPoint` (or `entry`) tells the runtime which file to load. `id` is used to derive the virtual origin — **never change it after shipping**, or the app loses its stored data.

### 2.3 `index.html`

```html
<!DOCTYPE html>
<html lang="bn">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <title>My Web App</title>
  <link rel="stylesheet" href="css/style.css">
</head>
<body>
  <main id="app">
    <h1>হ্যালো RONiLiFEOS</h1>
    <button id="toast">Toast দেখাও</button>
    <button id="save">সংরক্ষণ</button>
    <pre id="out"></pre>
  </main>
  <script src="js/native.js"></script>
  <script src="js/app.js"></script>
</body>
</html>
```

### 2.4 `js/native.js` — the only file that talks to native

```js
(function (global) {
  'use strict';

  var DEFAULT_TIMEOUT = 15000;

  function available() {
    return typeof global.NativeRuntimeBridgeClient !== 'undefined'
        && typeof global.NativeRuntimeBridgeClient.call === 'function';
  }

  function codeOf(text) {
    var match = String(text).match(/^([A-Z][A-Z0-9_]{2,})/);
    return match ? match[1] : 'NATIVE_ERROR';
  }

  function call(action, payload, timeoutMs) {
    if (!available()) {
      var missing = new Error('NATIVE_UNAVAILABLE');
      missing.code = 'NATIVE_UNAVAILABLE';
      missing.action = action;
      return Promise.reject(missing);
    }

    return new Promise(function (resolve, reject) {
      var settled = false;
      var timer = setTimeout(function () {
        if (settled) return;
        settled = true;
        var timeout = new Error('CLIENT_TIMEOUT');
        timeout.code = 'CLIENT_TIMEOUT';
        timeout.action = action;
        reject(timeout);
      }, timeoutMs || DEFAULT_TIMEOUT);

      global.NativeRuntimeBridgeClient.call(action, payload || {}).then(
        function (result) {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          resolve(result);
        },
        function (error) {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          var text = String(error && error.message ? error.message : error);
          var normalized = new Error(text);
          normalized.code = codeOf(text);
          normalized.action = action;
          reject(normalized);
        }
      );
    });
  }

  function on(topic, handler) {
    if (!available()) return function () {};
    global.NativeRuntimeBridgeClient.on(topic, handler);
    return function () { global.NativeRuntimeBridgeClient.off(topic, handler); };
  }

  /** Resolves once the bridge client has been injected (after onPageFinished). */
  function ready(timeoutMs) {
    var deadline = Date.now() + (timeoutMs || 5000);
    return new Promise(function (resolve, reject) {
      (function poll() {
        if (available()) return resolve(true);
        if (Date.now() > deadline) {
          var error = new Error('BRIDGE_NOT_READY');
          error.code = 'BRIDGE_NOT_READY';
          return reject(error);
        }
        setTimeout(poll, 50);
      })();
    });
  }

  global.Native = { available: available, call: call, on: on, ready: ready };
})(window);
```

### 2.5 `js/app.js`

```js
document.addEventListener('DOMContentLoaded', async function () {
  const out = document.getElementById('out');

  try {
    await Native.ready();
  } catch (error) {
    out.textContent = 'Native bridge unavailable — running in a plain browser.';
    return;
  }

  document.getElementById('toast').addEventListener('click', async function () {
    await Native.call('ui.showToast', { message: 'হ্যালো!', type: 'success' });
  });

  document.getElementById('save').addEventListener('click', async function () {
    await Native.call('storage.set', { key: 'greeting', value: 'হ্যালো' });
    const saved = await Native.call('storage.get', { key: 'greeting' });
    out.textContent = JSON.stringify(saved, null, 2);
  });

  const info = await Native.call('device.getInfo');
  out.textContent = info.model + ' · Android ' + info.osVersion;
});
```

That is the whole integration surface. Everything else is detail.

---

## 3. The `ready()` problem (read this before debugging)

`NativeRuntimeBridgeClient` is injected in `WebViewHost.onPageFinished()`. It does **not** exist while your `<head>` scripts run.

| Symptom | Cause | Fix |
| --- | --- | --- |
| `NativeRuntimeBridgeClient is undefined` at load | Called before `onPageFinished` | `await Native.ready()` |
| First call works, later calls hang | Page navigated; old promises orphaned | Re-run `ready()` after navigation |
| `NATIVE_UNAVAILABLE` in a desktop browser | No native host | Guard with `available()` |

---

## 4. Calling native APIs

### 4.1 The three forms

```js
await Native.call('ui.showToast', { message: 'Hi' });   // ✅ recommended
await Native.call('/ui/showToast', { message: 'Hi' });  // ✅ works
await Native.call('showToast', { message: 'Hi' });      // ⚠ resolves to /showToast
```

Always use `namespace.action`.

### 4.2 Reading the result

The promise resolves with the native `payload` object, **not** the envelope:

```js
const { model, osVersion } = await Native.call('device.getDeviceInfo');
const { value, exists } = await Native.call('storage.get', { key: 'token' });
const { shown } = await Native.call('ui.showToast', { message: 'ok' });
```

### 4.3 Handling errors

```js
try {
  await Native.call('storage.set', { key: '__p:bad', value: 1 });
} catch (error) {
  if (error.code === 'RESERVED_KEY') {
    console.warn('That key is reserved.');
  } else if (error.code === 'ROUTE_NOT_FOUND') {
    console.warn('This build does not expose storage.');
  } else {
    console.error(error.action, error.code, error.message);
  }
}
```

### 4.4 Feature detection

```js
async function supports(namespace, probeAction, probePayload) {
  try {
    await Native.call(probeAction, probePayload || {});
    return true;
  } catch (error) {
    return error.code !== 'ROUTE_NOT_FOUND' && error.code !== 'NATIVE_UNAVAILABLE';
  }
}

if (await supports('devtools', 'devtools.getHealth')) {
  const health = await Native.call('devtools.getHealth');
}
```

---

## 5. Receiving native events

```js
const off = Native.on('ui.confirm', function (payload) {
  console.log('confirmed:', payload.confirmed);
});

// later
off();
```

Events are fire-and-forget. There is no replay: if you subscribe after the event fired, you never see it. Subscribe **before** triggering the action.

```js
function confirmDialog(title, message) {
  return new Promise(function (resolve) {
    let settled = false;
    const off = Native.on('ui.confirm', function (payload) {
      if (settled) return;
      settled = true;
      off();
      resolve(!!payload.confirmed);
    });

    Native.call('ui.showConfirm', { title, message }).catch(function () {
      if (settled) return;
      settled = true;
      off();
      resolve(false);
    });
  });
}
```

Full event list: `EVENTS_AND_CALLBACKS.md`.

---

## 6. Persisting data

Three independent layers — pick deliberately:

| Layer | API | Scope | Survives reload | Survives reinstall |
| --- | --- | --- | --- | --- |
| `localStorage` | browser | per virtual origin | ✅ | ❌ |
| `storage.*` | native, AES-GCM | per project id | ✅ | ❌ |
| `filemanager.*` | native files | per project folder | ✅ | ❌ |

```js
// Native, encrypted, project-scoped
await Native.call('storage.set', { key: 'session', value: token });
const { value } = await Native.call('storage.get', { key: 'session' });

// Browser, per-origin
localStorage.setItem('draft', JSON.stringify(draft));
```

`storage.*` supports only JSON scalars (`string`, `number`, `boolean`, `null`). Store objects as JSON strings:

```js
await Native.call('storage.set', { key: 'profile', value: JSON.stringify(profile) });
const { value } = await Native.call('storage.get', { key: 'profile' });
const profile = value ? JSON.parse(value) : null;
```

Details: `STORAGE_API.md`.

---

## 7. Native chrome

```js
await Native.call('ui.setToolbarTitle', { title: 'নোটখাতা' });
await Native.call('ui.setToolbarSubtitle', { subtitle: '১২টি নোট' });

await Native.call('ui.addToolbarAction', { id: 'save', label: 'Save' });
Native.on('ui.toolbarAction', ({ id }) => { if (id === 'save') save(); });

await Native.call('ui.addTab', { tabId: 'home', label: 'Home' });
await Native.call('ui.addTab', { tabId: 'settings', label: 'Settings' });
await Native.call('ui.selectTab', { tabId: 'home' });
await Native.call('ui.setBadge', { tabId: 'home', count: 3 });

await Native.call('ui.addDrawerItem', { id: 'about', title: 'About' });
Native.on('ui.drawerItemSelected', ({ id }) => route(id));
```

> The toolbar, drawer and bottom navigation are **native views above the WebView**. They do not scroll with your page and they are not part of your DOM.

---

## 8. Theming

```js
const { css } = await Native.call('theme.getCssVariables');
document.getElementById('native-theme').textContent = css;

Native.on('theme.changed', async function () {
  const next = await Native.call('theme.getCssVariables');
  document.getElementById('native-theme').textContent = next.css;
});
```

```html
<style id="native-theme"></style>
```

```css
:root {
  --app-bg: var(--color-background, #ffffff);
  --app-fg: var(--color-text, #0f172a);
  --app-primary: var(--color-primary, #2563eb);
}
body { background: var(--app-bg); color: var(--app-fg); }
```

`theme.getCssVariables` returns a `:root { ... }` block. Injecting it into a `<style>` element is the supported way to follow the native theme.

---

## 9. Downloads and uploads

### 9.1 Saving a generated file

```js
const csv = 'name,qty\nনোট,৩\n';
const base64 = btoa(unescape(encodeURIComponent(csv)));

if (typeof window.RONiDownload !== 'undefined') {
  window.RONiDownload.saveBase64(base64, 'report.csv', 'text/csv');
}
```

`RONiDownload` is a **separate** JavaScript interface with no return value and no error channel. It is present only when downloads are enabled in `WebHostConfig` (default: enabled).

### 9.2 Uploading a file

```html
<input type="file" id="picker">
```

```js
document.getElementById('picker').addEventListener('change', async function (event) {
  const file = event.target.files[0];
  if (!file) return;

  const buffer = await file.arrayBuffer();
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  const base64 = btoa(binary);

  await Native.call('filemanager.writeFile', {
    path: 'uploads/' + file.name,
    base64: base64
  });
});
```

`<input type="file">` works because `WebViewHost` implements `onShowFileChooser` and forwards the result through `handleActivityResult`.

---

## 10. Networking

`fetch()` works normally against your virtual origin and against CORS-enabled remote hosts. When you need to bypass CORS or use native timeouts, use `network.request`:

```js
const res = await Native.call('network.request', {
  url: 'https://api.example.com/notes',
  method: 'GET',
  headers: { 'Authorization': 'Bearer ' + token },
  timeoutMs: 15000
});

if (res.statusCode >= 200 && res.statusCode < 300) {
  const notes = JSON.parse(res.body);
}
```

Only `http://` and `https://` are accepted. `host`, `content-length` and `connection` headers are rejected with `FORBIDDEN_HEADER`.

---

## 11. Notifications

```js
const permission = await Native.call('notification.permission');
if (!permission.canPost) {
  // The host app owns the runtime permission prompt.
  console.warn('Notifications are not permitted:', permission.state);
}

await Native.call('notification.post', {
  key: 'daily-reminder',
  title: 'আজকের নোট',
  body: '৩টি নোট লেখা বাকি',
  style: 'BASIC',
  priority: 'DEFAULT',
  autoCancel: true
});
```

Notifications require an **active project runtime**. Outside a project the call fails with `NOTIFICATION_FORBIDDEN`.

Details: `NOTIFICATION_API.md`.

---

## 12. Background work

```js
await Native.call('automation.scheduleJob', {
  id: 'sync-notes',
  name: 'Sync notes',
  workerType: 'noop',
  requiresNetwork: true,
  repeatIntervalMs: 15 * 60 * 1000,
  maxAttempts: 3,
  payload: { endpoint: '/sync' }
});
```

Details: `BACKGROUND_SERVICES_API.md`.

---

## 13. Packaging and sources

`WebViewHost.loadSource()` accepts five source kinds (`WebSource`):

| Kind | `type` value | `location` | Notes |
| --- | --- | --- | --- |
| Remote URL | `url` | `https://example.com/app/` | Must be HTTPS for service workers |
| APK asset | `asset` | `webapp/index.html` | Bundled in `assets/` |
| Device file | `file` | `/sdcard/app/index.html` | Needs file access |
| ZIP archive | `zip` | `/sdcard/app.zip` | Extracted to a virtual provider |
| Inline HTML | `html` | — (use `html` key) | `loadDataWithBaseURL` |

```js
await Native.call('web.loadSource', { type: 'url', location: 'https://example.com/app/' });
```

### 13.1 ES modules

ES modules work because the page is served over HTTPS from the virtual origin:

```html
<script type="module" src="js/app.js"></script>
```

```js
import { call } from './native.js';
```

Requirements:

1. The file must be served with a JavaScript MIME type — the asset provider derives it from the extension.
2. Paths are **case-sensitive**.
3. `import` specifiers must be relative (`./`, `../`) or absolute (`/js/...`). Bare specifiers (`import x from 'lodash'`) need an import map.

```html
<script type="importmap">
{ "imports": { "lodash": "/vendor/lodash-es.js" } }
</script>
```

### 13.2 Offline resources

Everything under your project folder is served locally, so the app works offline by default. Remote assets are cached by the WebView HTTP cache; `WebViewHost` deliberately **keeps** that cache on destroy so re-opening a project does not re-download.

---

## 14. Lifecycle

| Native moment | What your page sees |
| --- | --- |
| `onPageStarted` | Nothing — the JS context is being replaced |
| `onPageFinished` | `NativeRuntimeBridgeClient` appears |
| `onResume` | `webView.onResume()` + `resumeTimers()` — timers resume |
| `onPause` | `webView.onPause()` + `pauseTimers()` — **timers stop** |
| `onDestroy` | Evaluator cleared; native events are dropped |

Because `pauseTimers()` stops `setTimeout`/`setInterval`, do not rely on a JS timer to finish critical work while the app is backgrounded. Use `automation.*` for anything that must run in the background.

```js
document.addEventListener('visibilitychange', function () {
  if (document.hidden) flushDraft();
});
```

---

## 15. Debugging

| Tool | How |
| --- | --- |
| Chrome DevTools | `WebView.setWebContentsDebuggingEnabled(true)` when `WebHostConfig.isDebugLogging()` |
| Runtime Inspector | `RuntimeInspectorIntegration.get().attach(webViewHost)` — captures console, network, bridge traces |
| Native log | `Native.call('logger.log', { level: 'DEBUG', tag: 'JS', message: '...' })` |
| Bridge trace | `devtools.getBridgeMetrics` (debug builds) |
| Connectivity test | `Native.call('echo.echo', { ping: 1 })` |

`WebViewHost` also injects an error reporter that forwards `window.onerror` and `unhandledrejection` to `console.error`, which the host receives through `onConsoleMessage`.

---

## 16. Common mistakes

| Mistake | Result | Fix |
| --- | --- | --- |
| Calling the bridge in `<head>` | `undefined` | `await Native.ready()` |
| `ui.showToast({ text: 'x' })` | Empty toast | Use `message` |
| `analytics.trackEvent({ properties })` | Empty params | Use `parameters` |
| `analytics.startScreenView({ screen })` | `INVALID_PARAM` | Use `screenName` |
| `security.hash.sha256({ text })` | Hash of `""` | Use `input` |
| `platform.toast.show({ text })` | Default message | Use `message` |
| `storage.set` with an object | Stored as `"[object Object]"` | `JSON.stringify` first |
| `filemanager.writeFile` with plain text | Corrupt file | Send base64 |
| Expecting `ui.showConfirm` to resolve with a boolean | Always `{ shown: true }` | Listen to `ui.confirm` |
| Sending > 1 MiB in one call | Promise times out | Chunk the payload |
| Using `file://` URLs | Origin rejected | Use the virtual origin |
| Changing `manifest.json` `id` | Data loss | Keep the id stable |

---

## 17. বাংলা দ্রুত শুরু (Bengali quick start)

```html
<!DOCTYPE html>
<html lang="bn">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>আমার অ্যাপ</title>
</head>
<body>
  <h1>আমার প্রথম RONiLiFEOS অ্যাপ</h1>
  <button onclick="showToast()">টোস্ট দেখান</button>
  <script src="js/native.js"></script>
  <script>
    async function showToast() {
      await Native.ready();                       // ব্রিজ প্রস্তুত হওয়া পর্যন্ত অপেক্ষা
      await Native.call('ui.showToast', {
        message: 'হ্যালো RONiLiFEOS!',            // ⚠ অবশ্যই message, text নয়
        type: 'success'
      });
    }
  </script>
</body>
</html>
```

**মনে রাখুন:**

1. `NativeRuntimeBridgeClient` পেজ লোড শেষ হলে তৈরি হয় — তাই `Native.ready()` ব্যবহার করুন।
2. প্রতিটি কলে `namespace.action` ফরম্যাট ব্যবহার করুন, যেমন `ui.showToast`।
3. প্যারামিটারের নাম হুবহু মিলতে হবে — `message`, `parameters`, `screenName`, `input`।
4. `storage.*` শুধু string/number/boolean/null রাখে; অবজেক্টের জন্য `JSON.stringify` করুন।
5. নোটিফিকেশন ও ক্যামেরার জন্য একটি সক্রিয় প্রজেক্ট রানটাইম থাকতে হবে।

---

## 18. Next steps

| Document | Contents |
| --- | --- |
| `JAVASCRIPT_BRIDGE.md` | Protocol, gates, timeouts, error codes |
| `NATIVE_API_REFERENCE.md` | Every action, parameter and return value |
| `EVENTS_AND_CALLBACKS.md` | Every native → web event |
| `STORAGE_API.md` | Encryption, scoping, migration |
| `NOTIFICATION_API.md` | Notification styles, scheduling, channels |
| `BACKGROUND_SERVICES_API.md` | Tasks, jobs, alarms, transfers |
| `SECURITY_AND_PERMISSIONS.md` | Origin model, permissions, keystore |
| `API_COMPATIBILITY.md` | Versioning and deprecations |
| `../examples/webapp-developer-guide/` | Complete working app |
