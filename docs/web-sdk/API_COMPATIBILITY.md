# RONiLiFEOS — API Compatibility

**Bridge protocol version:** 1.0
**Documented against:** `native-web-bridge-sdk`, `native-runtime-ui`, `native-platform-engine`, `native-security-engine`, `native-storage-engine`

---

## 1. Compatibility contract

| Surface | Stability | Notes |
| --- | --- | --- |
| `NativeRuntimeBridgeClient.call/on/off/ping` | **Frozen** | Changing these breaks every app |
| Message envelope fields | **Frozen** | `id`, `type`, `action`, `payload`, `error`, `success`, `timestamp` |
| Action names (`namespace.action`) | **Additive** | New actions may appear; existing ones are not renamed |
| Action parameters | **Additive** | New optional parameters may appear; existing names are not renamed |
| Response payload keys | **Additive** | New keys may appear; existing keys keep their meaning |
| Event topics | **Additive** | New topics may appear |
| Event payload keys | **Additive** | |
| Error codes | **Additive** | New codes may appear; existing codes keep their meaning |
| `RONiDownload` interface | **Frozen** | `saveBase64`, `download` |
| Virtual origin format | **Frozen per project** | Derived from project id; changing it loses data |

**Rule for consumers:** ignore unknown keys, never assume a key is absent, and treat an unknown error code as a generic failure.

---

## 2. Version history of the bridge

| Version | Change |
| --- | --- |
| 1.0 | Initial protocol: `REQUEST` / `RESPONSE` / `ERROR` / `EVENT` / `PING`, origin validation, 1 MiB limit, 10 s sweep |

The protocol has not changed since 1.0. All evolution has been **additive at the route layer**.

---

## 3. Route availability matrix

A route can be absent for three reasons: the engine is not initialised, the build is release (devtools), or the host app did not install the integration.

| Route | Always | Needs engine | Debug only | Needs project |
| --- | --- | --- | --- | --- |
| `ui` | ✅ | — | — | — |
| `web` | ✅ | — | — | — |
| `device` | ✅ | — | — | — |
| `storage` | — | `StorageEngine` | — | — |
| `theme` | ✅ | — | — | — |
| `security` | — | `SecurityEngine` | — | — |
| `analytics` | ✅ | — | — | — |
| `devtools` | — | — | ✅ | — |
| `network` | ✅ | — | — | — |
| `platform` | — | `PlatformEngine` | — | — |
| `notification` | — | `PlatformEngine` | — | ✅ |
| `camera` | — | `PlatformEngine` | — | `open` only |
| `filemanager` | — | `PlatformEngine` | — | — |
| `automation` | — | `PlatformEngine` | — | — |
| `logger` | ✅ | — | — | — |
| `service` | ✅ | — | — | — |
| `runtime` | — | `NativeRuntimeEngine` | — | — |
| `bridge` | ✅ | — | — | — |
| `echo` | ✅ | — | — | — |

**Always probe before use.** See `JAVASCRIPT_BRIDGE.md` §7.4.

---

## 4. Known parameter-name traps

These are the mismatches that cause silent failures — the call succeeds but does nothing.

| Action | Wrong | Right | Symptom of the wrong name |
| --- | --- | --- | --- |
| `ui.showToast` | `text` | `message` | Empty toast |
| `platform.toast.show` | `text` | `message` | Shows `"Notification"` |
| `analytics.trackEvent` | `properties` | `parameters` | Event with no parameters |
| `analytics.startScreenView` | `screen` | `screenName` | `INVALID_PARAM` |
| `analytics.endScreenView` | `screen` | `screenName` | `INVALID_PARAM` |
| `security.hash.sha256` | `text` | `input` | Hash of the empty string |
| `security.keystore.decrypt` | *(missing `iv`)* | `iv` required | Decryption failure |
| `devtools.dispatchCdp` | `domain` | `method` | Runs `Runtime.enable` |
| `notification.post` | `message` | `body` (alias accepted) | Works — `message` is a documented alias |
| `filemanager.writeFile` | `content` | `base64` | Empty file |
| `network.request` | object `body` | string `body` | Empty request body |

---

## 5. Deprecated and removed

| Item | Status | Replacement |
| --- | --- | --- |
| `appassets.androidplatform.net` as the only host | **Legacy, still supported** | Per-project `<slug>-<hash>.runtime.local` |
| Legacy unscoped storage namespace | **Legacy, still readable** | `__p:<project-id>:` namespace |
| `StorageCommandHandler` (engine-level) | **Not registered** | `NativeStorageCommandHandler` on `/storage` |
| `notification.requestPermission` | **Never existed** | `notification.permission` (read-only) |
| `ui.showDialog` | **Never existed** | `ui.showAlert` / `ui.showConfirm` |
| `device.getLocation` | **Never existed** | — |

---

## 6. Behavioural quirks that are part of the contract

These are surprising but intentional. They will not change without a major version.

| Quirk | Why |
| --- | --- |
| `ui.showConfirm` resolves before the user answers | The native dialog is asynchronous; the answer arrives as `ui.confirm` |
| `security.biometric.authenticate` resolves when the prompt is shown | Same reason; the outcome arrives as `security.biometric.result` |
| `PAYLOAD_TOO_LARGE` uses `id: "unknown"` | The message could not be parsed, so no request id is available — the caller's promise times out instead of rejecting |
| `platform.*` never returns `UNKNOWN_ACTION` | Unknown actions fall through to the diagnostic branch |
| `notification.*` never returns `UNKNOWN_ACTION` | Unknown actions fall through to `status` |
| `web.evaluate` returns a JSON-encoded string | It forwards the raw `evaluateJavascript` callback value |
| `device.vibrate` is a haptic tick, not a timed vibration | It uses `HapticFeedbackConstants.VIRTUAL_KEY` |
| `storage.set` with an object stores `"[object Object]"` | The store only encodes scalars |
| `web.setZoom` uses CSS `zoom` | `WebSettings` has no zoom-level API |
| `ui.getConfig` returns 22 keys | It is a snapshot of `NativeRuntimeUIConfig` |

---

## 7. Migrating from the legacy facade

`examples/webapp-starter/scripts/native-runtime.js` is a **JavaScript wrapper**, not part of the SDK. It predates several parameter renames and contains six mismatches:

| Facade method | Sends | Native expects | Fix |
| --- | --- | --- | --- |
| `toast(text)` | `ui.showToast { text }` | `message` | `{ message: text }` |
| `analytics.track(name, props)` | `analytics.trackEvent { properties }` | `parameters` | `{ parameters: props }` |
| `analytics.screenView(screen)` | `analytics.startScreenView { screen }` | `screenName` | `{ screenName: screen }` |
| `security.sha256(text)` | `security.hash.sha256 { text }` | `input` | `{ input: text }` |
| `security.keystore.decrypt(alias, ct)` | `{ alias, ciphertext }` | `alias`, `ciphertext`, `iv` | Add `iv` |
| `platform.toast(text)` | `platform.toast.show { text }` | `message` | `{ message: text }` |
| `devtools.dispatchCdp(domain, method, params)` | `{ domain, method, params }` | `method` | Drop `domain` |

The corrected wrapper ships as `examples/webapp-developer-guide/js/native.js` and is the reference implementation.

---

## 8. Testing compatibility

### 8.1 Connectivity

```js
const pong = await Native.call('echo.echo', { ping: 1 });
// { echo: { ping: 1 }, echoPath: '/echo', echoAction: 'echo', requestId: '...', timestamp: 0 }
```

### 8.2 Route inventory

```js
const ROUTES = ['ui','web','device','storage','theme','security','analytics',
                'devtools','network','platform','notification','camera',
                'filemanager','automation','logger','service','runtime'];

const PROBES = {
  ui: ['ui.getConfig', {}],
  web: ['web.getState', {}],
  device: ['device.getAppInfo', {}],
  storage: ['storage.keys', {}],
  theme: ['theme.getTheme', {}],
  security: ['security.diagnostic', {}],
  analytics: ['analytics.getStats', {}],
  devtools: ['devtools.getHealth', {}],
  network: ['network.request', { url: 'https://example.com' }],
  platform: ['platform.diagnostic', {}],
  notification: ['notification.status', {}],
  camera: ['camera.getCapabilities', {}],
  filemanager: ['filemanager.getStorageInfo', {}],
  automation: ['automation.getStatus', {}],
  logger: ['logger.getLevel', {}],
  service: ['service.list', {}],
  runtime: ['runtime.getVitals', {}]
};

async function inventory() {
  const report = {};
  for (const route of ROUTES) {
    const [action, payload] = PROBES[route];
    try {
      await Native.call(action, payload);
      report[route] = 'OK';
    } catch (error) {
      report[route] = error.code === 'ROUTE_NOT_FOUND' ? 'ABSENT' : 'PRESENT(' + error.code + ')';
    }
  }
  return report;
}
```

`PRESENT(...)` means the route exists but the probe failed for a business reason (no project, no permission) — that is still a working route.

### 8.3 Parameter smoke test

```js
async function smokeTest() {
  const results = {};

  results.toast = await Native.call('ui.showToast', { message: 'smoke', type: 'info' });
  results.storage = await Native.call('storage.set', { key: '__smoke', value: 1 });
  results.read = await Native.call('storage.get', { key: '__smoke' });
  results.hash = await Native.call('security.hash.sha256', { input: 'abc' });
  results.device = await Native.call('device.getAppInfo');

  await Native.call('storage.remove', { key: '__smoke' });
  return results;
}
```

Expected: `results.read.value === 1`, `results.hash.sha256 === 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'`.

---

## 9. Related documents

- `JAVASCRIPT_BRIDGE.md` — protocol details
- `NATIVE_API_REFERENCE.md` — full action catalog
- `DOCUMENTATION_AUDIT_REPORT.md` — what was wrong in the previous docs
