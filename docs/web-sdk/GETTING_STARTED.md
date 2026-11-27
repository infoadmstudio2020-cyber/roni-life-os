# RONiLiFEOS — Getting Started for Web Developers

**Time to first working call: about 5 minutes.**

---

## 1. What you need

| Requirement | Detail |
| --- | --- |
| A text editor | Any |
| A browser | For layout work — the app degrades gracefully outside RONiLiFEOS |
| RONiLiFEOS on a device | Android 7.0+ (API 24) |
| No Android SDK | You never compile Java |

---

## 2. Create the project folder

```
my-app/
├── manifest.json
├── index.html
├── css/
│   └── style.css
└── js/
    ├── native.js
    └── app.js
```

---

## 3. `manifest.json`

```json
{
  "schemaVersion": 1,
  "id": "my-app",
  "name": "My App",
  "description": "My first RONiLiFEOS web app",
  "version": 1,
  "type": "WEB",
  "entryPoint": "index.html",
  "source": { "type": "FILE", "path": ".", "entryPoint": "index.html" },
  "splash": { "enabled": true, "durationMs": 1200 },
  "bootstrap": { "enabled": true, "renderingMode": "NATIVE", "atStartup": false }
}
```

> ⚠ `id` derives your virtual origin **and** your storage namespace. Changing it after release wipes the app's data.

---

## 4. `index.html`

```html
<!DOCTYPE html>
<html lang="bn">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <title>My App</title>
  <link rel="stylesheet" href="css/style.css">
</head>
<body>
  <h1>আমার প্রথম অ্যাপ</h1>
  <button id="hello">টোস্ট দেখাও</button>
  <pre id="out"></pre>

  <script src="js/native.js"></script>
  <script src="js/app.js"></script>
</body>
</html>
```

---

## 5. `js/native.js` — copy this verbatim

```js
(function (global) {
  'use strict';

  function available() {
    return typeof global.NativeRuntimeBridgeClient !== 'undefined'
        && typeof global.NativeRuntimeBridgeClient.call === 'function';
  }

  function call(action, payload, timeoutMs) {
    if (!available()) {
      var e = new Error('NATIVE_UNAVAILABLE');
      e.code = 'NATIVE_UNAVAILABLE';
      return Promise.reject(e);
    }
    return new Promise(function (resolve, reject) {
      var settled = false;
      var timer = setTimeout(function () {
        if (settled) return;
        settled = true;
        var t = new Error('CLIENT_TIMEOUT');
        t.code = 'CLIENT_TIMEOUT';
        reject(t);
      }, timeoutMs || 15000);

      global.NativeRuntimeBridgeClient.call(action, payload || {}).then(
        function (r) { if (settled) return; settled = true; clearTimeout(timer); resolve(r); },
        function (err) {
          if (settled) return;
          settled = true;
          clearTimeout(timer);
          var text = String(err && err.message ? err.message : err);
          var m = text.match(/^([A-Z][A-Z0-9_]{2,})/);
          var n = new Error(text);
          n.code = m ? m[1] : 'NATIVE_ERROR';
          n.action = action;
          reject(n);
        }
      );
    });
  }

  function on(topic, handler) {
    if (!available()) return function () {};
    global.NativeRuntimeBridgeClient.on(topic, handler);
    return function () { global.NativeRuntimeBridgeClient.off(topic, handler); };
  }

  function ready(timeoutMs) {
    var deadline = Date.now() + (timeoutMs || 5000);
    return new Promise(function (resolve, reject) {
      (function poll() {
        if (available()) return resolve(true);
        if (Date.now() > deadline) {
          var e = new Error('BRIDGE_NOT_READY');
          e.code = 'BRIDGE_NOT_READY';
          return reject(e);
        }
        setTimeout(poll, 50);
      })();
    });
  }

  global.Native = { available: available, call: call, on: on, ready: ready };
})(window);
```

---

## 6. `js/app.js`

```js
document.addEventListener('DOMContentLoaded', async function () {
  var out = document.getElementById('out');

  try {
    await Native.ready();
  } catch (e) {
    out.textContent = 'Native bridge নেই — সাধারণ ব্রাউজারে চলছে।';
    return;
  }

  document.getElementById('hello').addEventListener('click', async function () {
    try {
      await Native.call('ui.showToast', { message: 'হ্যালো!', type: 'success' });
      var info = await Native.call('device.getAppInfo');
      out.textContent = JSON.stringify(info, null, 2);
    } catch (error) {
      out.textContent = error.code + ': ' + error.message;
    }
  });
});
```

---

## 7. Run it

1. Copy the folder into RONiLiFEOS (import as a project or ZIP).
2. Open the project.
3. Tap the button — a native toast appears and the app info is printed.

---

## 8. The four rules

| # | Rule | Why |
| --- | --- | --- |
| 1 | `await Native.ready()` before the first call | The client is injected in `onPageFinished` |
| 2 | Use `namespace.action` | `ui.showToast`, not `showToast` |
| 3 | Use the exact parameter name | `message`, not `text` |
| 4 | Listen for events, not just promises | `ui.showConfirm` resolves before the answer |

---

## 9. Your first five calls

```js
// 1. Toast
await Native.call('ui.showToast', { message: 'হ্যালো', type: 'success' });

// 2. Toolbar
await Native.call('ui.setToolbarTitle', { title: 'আমার অ্যাপ' });

// 3. Device
const info = await Native.call('device.getInfo');

// 4. Storage
await Native.call('storage.set', { key: 'visits', value: 1 });
const { value } = await Native.call('storage.get', { key: 'visits' });

// 5. Confirm (event-based)
Native.on('ui.confirm', ({ confirmed }) => console.log(confirmed));
await Native.call('ui.showConfirm', { title: 'নিশ্চিত?', message: 'এগিয়ে যাবেন?' });
```

---

## 10. Common first-run problems

| Symptom | Cause | Fix |
| --- | --- | --- |
| `NativeRuntimeBridgeClient is undefined` | Called before `onPageFinished` | `await Native.ready()` |
| `NATIVE_UNAVAILABLE` | Opened in a desktop browser | Expected — guard with `available()` |
| Toast is empty | Sent `text` instead of `message` | Use `message` |
| `ROUTE_NOT_FOUND` | Typo in the namespace | Check `NATIVE_API_REFERENCE.md` |
| `ORIGIN_REJECTED` | Loaded from `file://` | Use the project's virtual origin |
| Storage lost after edit | Changed `manifest.json` `id` | Keep the id stable |

---

## 11. Next steps

| Goal | Document |
| --- | --- |
| Understand the shell | `ARCHITECTURE.md` |
| Learn the protocol | `JAVASCRIPT_BRIDGE.md` |
| Find an action | `NATIVE_API_REFERENCE.md` |
| Handle events | `EVENTS_AND_CALLBACKS.md` |
| Persist data | `STORAGE_API.md` |
| Ship it | `WEB_APP_DEVELOPER_GUIDE.md` §13 |
| Debug it | `TROUBLESHOOTING_AND_FAQ.md` |
