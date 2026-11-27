# RONiLiFEOS — Troubleshooting and FAQ

Symptom-driven. Find your symptom, apply the fix.

---

## 1. The bridge

### `NativeRuntimeBridgeClient is undefined`

**Cause:** the client is injected in `WebViewHost.onPageFinished()`. Your script ran before that.

**Fix:**

```js
await Native.ready();
```

Never call the bridge from a synchronous `<head>` script.

---

### `NATIVE_UNAVAILABLE`

**Cause:** no native host — a desktop browser, a plain WebView, or a page loaded outside a project.

**Fix:** guard every call.

```js
if (!Native.available()) {
  // fall back to browser APIs
}
```

---

### `BRIDGE_NOT_READY`

**Cause:** `ready()` waited 5 s and the client never appeared.

**Check:**

1. Is the page actually loaded inside RONiLiFEOS?
2. Did `onPageFinished` fire? (A page that never finishes loading never injects.)
3. Is `WebHostConfig.isBridgeEnabled()` `true`?

---

### `ORIGIN_REJECTED: <origin>`

**Cause:** the page origin is not in the bridge allow-list.

| Situation | Fix |
| --- | --- |
| Loaded from `file://` | Load through the project's virtual origin |
| Loaded from a remote URL | The host must call `bridge.addAllowedOrigin(...)` |
| Custom `baseUrl` in `loadHtml` | Use the project origin as the base |

---

### `ROUTE_NOT_FOUND`

**Cause:** no handler for the namespace.

| Namespace | Why it might be absent |
| --- | --- |
| `devtools` | Release build |
| `storage` | `StorageEngine` not initialised |
| `security` | `SecurityEngine` not initialised |
| `platform`, `camera`, `filemanager`, `automation`, `notification` | `PlatformEngine` not initialised |
| `runtime` | `NativeRuntimeEngine` not initialised |

**Fix:** probe first.

```js
if (await Native.isSupported('camera')) { /* … */ }
```

---

### `REQUEST_TIMEOUT`

**Cause:** the handler did not answer within 10 s (`BridgeConfig.timeoutMs`).

**Check:**

1. Is the handler blocking on the main thread?
2. Is `web.evaluate` waiting on a script that never returns?
3. Is the payload enormous?

---

### `PAYLOAD_TOO_LARGE`

**Cause:** the message exceeded 1 MiB.

**Fix:** chunk it. Send file content through `filemanager.writeFile` in pieces, or store large data with `storage.set` under several keys.

> The rejection uses `id: "unknown"`, so your promise **times out** rather than rejecting with this code. If a call hangs and the inspector shows `PAYLOAD_TOO_LARGE`, this is why.

---

## 2. UI

### The toast is empty

**Cause:** you sent `text`.

```js
await Native.call('ui.showToast', { message: 'হ্যালো' });   // ✅
await Native.call('ui.showToast', { text: 'হ্যালো' });      // ❌
```

---

### `ui.showConfirm` always returns `{ shown: true }`

**Cause:** that is the contract. The answer arrives as an event.

```js
const answer = new Promise((resolve) => {
  const off = Native.on('ui.confirm', (p) => { off(); resolve(p.confirmed); });
});
await Native.call('ui.showConfirm', { title: 'Sure?', message: 'Really?' });
if (await answer) proceed();
```

---

### The toolbar / drawer / tabs do nothing

**Cause:** the chrome is disabled in the project's UI config.

**Check:**

```js
const config = await Native.call('ui.getConfig');
console.log(config.toolbarEnabled, config.drawerEnabled, config.bottomNavEnabled);
```

If `false`, the host app must enable it — a web app cannot.

---

### `{ applied: false }` from `ui.setStatusBar`

**Cause:** no Activity attached to `NativeUIHost`. The call is a no-op, not an error.

---

### Tab taps do nothing in my page

**Cause:** there is no `ui.tabSelected` event. Tab taps are handled natively.

**Fix:** drive tabs from your own UI, or poll `ui.getSelectedTab` on `visibilitychange`.

---

## 3. Storage

### Values disappear after I edit the project

**Cause:** you changed `manifest.json` `id`. The id derives the storage namespace.

**Fix:** keep the id stable. Migrate explicitly if you must change it.

---

### `storage.set` with an object stores `"[object Object]"`

**Cause:** the store only encodes scalars.

```js
await Native.call('storage.set', { key: 'profile', value: JSON.stringify(profile) });
const { value } = await Native.call('storage.get', { key: 'profile' });
const profile = value ? JSON.parse(value) : null;
```

---

### `RESERVED_KEY`

**Cause:** the key starts with `__p:` and you are in the legacy (no-project) namespace.

**Fix:** rename the key, or run inside a project.

---

### `STORAGE_UNAVAILABLE`

**Cause:** `StorageEngine` is not initialised.

**Fix:** the host app must call `StorageEngine.initialize()`. A web app cannot.

---

### Another project can read my keys

**It cannot.** `StorageScope` prefixes every key with `__p:<escaped-project-id>:`, and `unscopedKey()` returns `null` for a foreign key. If you see cross-project data, you are running both apps under the same `manifest.json` `id`.

---

## 4. Files

### `filemanager.writeFile` creates an empty file

**Cause:** you sent `content` instead of `base64`.

```js
await Native.call('filemanager.writeFile', {
  path: 'notes/a.txt',
  base64: btoa(unescape(encodeURIComponent('আজকের নোট')))
});
```

---

### A file 404s

| Cause | Fix |
| --- | --- |
| Wrong case | Paths are case-sensitive |
| `../` in the path | Traversal is rejected |
| Leading `/` | Resolves against the project root — usually fine |
| File not in the project folder | Move it under the project root |

---

### ES modules fail to load

| Cause | Fix |
| --- | --- |
| Bare specifier (`import x from 'lodash'`) | Add an import map |
| Wrong MIME type | Use a `.js` extension |
| `file://` origin | Load through the virtual origin |
| Case mismatch | Match the filename exactly |

---

## 5. Notifications

### `NOTIFICATION_FORBIDDEN`

**Cause:** no active project runtime.

**Fix:** notifications only work inside a project. Check `notification.status` → `activeProject`.

---

### `notification.permission` says `canPost: false`

**Cause:** `POST_NOTIFICATIONS` not granted, or notifications disabled in system settings.

**Fix:** there is no `notification.requestPermission`. Guide the user:

```js
await Native.call('ui.showAlert', { title: 'নোটিফিকেশন বন্ধ', message: 'সেটিংস থেকে চালু করুন।' });
await Native.call('device.openAppSettings');
```

---

### Re-posting creates duplicates

**Cause:** you used a different `key` each time.

**Fix:** `key` is the identity. Same key → replace.

---

### A scheduled notification never arrives

| Cause | Fix |
| --- | --- |
| `scheduledAt` in the past | Use a future timestamp |
| Exact alarm not permitted | `automation.getCapabilities` → `exactAlarms` |
| Battery optimisation | Ask the user to exempt the app |

---

## 6. Background work

### My `setTimeout` stops when the app is backgrounded

**Cause:** `WebViewHost.onPause()` calls `webView.pauseTimers()`.

**Fix:** use `automation.*`.

```js
await Native.call('automation.scheduleJob', {
  id: 'sync', name: 'Sync', workerType: 'noop',
  requiresNetwork: true, repeatIntervalMs: 15 * 60 * 1000
});
```

---

### A task finishes but my page never learns

**Cause:** background work does not resume the page.

**Fix:** read the event log.

```js
const { events } = await Native.call('automation.getEvents', { sinceSequence: cursor, limit: 100 });
```

---

### `AUTOMATION_UNKNOWN_WORKER`

**Cause:** the `type` / `workerType` is not registered.

**Fix:** use `"noop"` (always present) or check `automation.getCapabilities`.

---

## 7. Network

### `FORBIDDEN_HEADER`

**Cause:** you sent `host`, `content-length` or `connection`.

**Fix:** remove them — the engine sets them.

---

### `UNSUPPORTED_SCHEME`

**Cause:** not `http://` or `https://`.

---

### `network.request` resolves with a 500

**That is not an error.** The promise resolves for every HTTP status. Check `statusCode` yourself.

---

### CORS errors with `fetch`

**Cause:** the remote host does not send CORS headers.

**Fix:** use `network.request`, which bypasses CORS.

---

## 8. Security

### `security.hash.sha256` returns the hash of an empty string

**Cause:** you sent `text` instead of `input`.

```js
await Native.call('security.hash.sha256', { input: 'hello' });   // ✅
```

The SHA-256 of `""` is `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.

---

### Keystore decryption fails

**Cause:** missing or wrong `iv`.

```js
const { ciphertext, iv } = await Native.call('security.keystore.encrypt', { alias, plaintext });
await Native.call('security.keystore.decrypt', { alias, ciphertext, iv });   // iv required
```

Store `iv` next to `ciphertext`. It is not secret, but it is not recoverable.

---

### Biometrics never resolve

**Cause:** `security.biometric.authenticate` resolves when the prompt is **shown**. The outcome is an event.

```js
const result = new Promise((resolve) => {
  const off = Native.on('security.biometric.result', (p) => { off(); resolve(p); });
});
await Native.call('security.biometric.authenticate', { title: 'Unlock' });
const outcome = await result;
```

---

### `BIOMETRIC_ERROR_NONE_ENROLLED`

**Cause:** no fingerprint or face enrolled.

**Fix:** fail open — do not lock the user out.

```js
const { available } = await Native.call('security.biometric.capability');
if (!available) return true;
```

---

## 9. Lifecycle

### Pending promises vanish after navigation

**Cause:** `web.loadUrl` replaces the page and destroys the JS context.

**Fix:** do not navigate while a call is in flight. Re-run `Native.ready()` after navigation.

---

### Native events stop arriving

**Cause:** the host was destroyed (`onDestroy` clears the evaluator), or you subscribed after the event fired.

**Fix:** subscribe before triggering. Events are not replayed.

---

## 10. FAQ

**Do I need Android Studio?**
No. You write HTML, CSS and JavaScript.

**Can I use React / Vue / Svelte?**
Yes, if the build output is static files. The page is served over HTTPS from the virtual origin, so ES modules and bundlers work.

**Can I use `npm` packages?**
Yes, bundled. Bare specifiers need an import map.

**Does `fetch` work?**
Yes, subject to CORS. Use `network.request` to bypass CORS.

**Does `localStorage` work?**
Yes, per project origin.

**Is `storage.*` encrypted?**
Yes — AES-GCM with the key in the Android Keystore, by default.

**Can I use a service worker?**
Yes, because the origin is HTTPS. Scope it to the project root.

**Can I open a camera stream with `getUserMedia`?**
Not through the bridge. Use `camera.*`, which drives the native camera.

**Can I get the user's location?**
No. Not implemented.

**Can I send push notifications?**
No. Local notifications only (`notification.*`).

**Can I use WebSockets?**
Yes, from the page. `native-network-engine` also has a WebSocket client, but it is not exposed on the bridge.

**Can I read SMS / contacts / call logs?**
No. Not implemented, by design.

**Can I run code when the app is backgrounded?**
Not JavaScript. Use `automation.*` — the page is paused.

**Why is my toast empty?**
`message`, not `text`.

**Why does `analytics.trackEvent` record no parameters?**
`parameters`, not `properties`.

**Why does `analytics.startScreenView` return `INVALID_PARAM`?**
`screenName`, not `screen`.

**Why did my storage disappear?**
You changed `manifest.json` `id`.

**How do I see bridge traffic?**
Runtime Inspector (`DEVELOPER_TOOLS.md`).

**How do I test without a device?**
Open `index.html` in a browser. `Native.available()` returns `false` and your fallbacks run.

---

## 11. Diagnostic script

Paste this into the console when something is wrong:

```js
(async function diagnose() {
  const report = {};

  report.bridge = Native.available();
  if (!report.bridge) return console.table(report);

  try {
    report.echo = await Native.call('echo.echo', { ping: 1 });
  } catch (e) { report.echo = e.code; }

  const routes = ['ui','web','device','storage','theme','security','analytics',
                  'devtools','network','platform','notification','camera',
                  'filemanager','automation','logger','service','runtime'];
  report.routes = {};
  for (const r of routes) report.routes[r] = (await Native.isSupported(r)) ? 'OK' : 'ABSENT';

  try { report.app = await Native.call('device.getAppInfo'); } catch (e) { report.app = e.code; }
  try { report.storage = await Native.call('storage.keys'); } catch (e) { report.storage = e.code; }
  try { report.theme = await Native.call('theme.getTheme'); } catch (e) { report.theme = e.code; }

  console.table(report.routes);
  console.log(report);
})();
```

---

## 12. Related documents

- `ERROR_CODES.md` — every code and its meaning
- `DEVELOPER_TOOLS.md` — Runtime Inspector
- `API_COMPATIBILITY.md` §4 — parameter traps
- `JAVASCRIPT_BRIDGE.md` §4 — the four gates
