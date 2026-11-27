# RONiLiFEOS — Native → Web Events and Callbacks

**Verified against source.** Every event below is emitted by a real `emitToWeb(topic, payload)` call.

---

## 1. How events work

Native code pushes an event by calling `WebBridge.emitToWeb(topic, payload)`:

```java
BridgeMessage eventMsg = BridgeMessage.event(topic, data);   // type = EVENT
dispatchJs(serializer.serialize(eventMsg));
```

`dispatchJs` wraps the JSON in `JSONObject.quote(...)` and evaluates:

```js
if (window['NativeRuntimeBridgeClient'] && window['NativeRuntimeBridgeClient']._handleNativeMessage) {
  window['NativeRuntimeBridgeClient']._handleNativeMessage("<escaped json>");
}
```

The client then dispatches to every listener registered for `msg.action`:

```js
var listeners = eventListeners[msg.action] || [];
listeners.forEach(function (l) { try { l(msg.payload); } catch (e) {} });
```

### 1.1 Properties you can rely on

| Property | Value |
| --- | --- |
| Delivery | Fire-and-forget, no acknowledgement |
| Ordering | Preserved per topic (single `evaluateJavascript` queue) |
| Replay | **None** — subscribe before triggering |
| Listener errors | Swallowed by the client (`try { l(...) } catch(e){}`) |
| Payload | Always an object, never `null` |
| Envelope `id` | A fresh UUID, unrelated to any request |
| Delivery when host destroyed | Dropped (`webEvaluator` is cleared in `onDestroy`) |

### 1.2 Subscribing

```js
const off = Native.on('theme.changed', function (payload) {
  console.log('theme is now', payload.themeMode);
});

// later
off();
```

`on()` returns an unsubscribe function. Native never removes listeners; a page reload clears them because the JS context is replaced.

---

## 2. Complete event catalog

### 2.1 `ui.confirm`

| | |
| --- | --- |
| **Emitted by** | `NativeUICommandHandler.showConfirm` |
| **When** | The user taps confirm **or** cancel |
| **Payload** | `{ confirmed: boolean }` |

```json
{ "confirmed": true }
```

```js
Native.on('ui.confirm', ({ confirmed }) => {
  if (confirmed) deleteNote();
});
await Native.call('ui.showConfirm', { title: 'Delete?', message: 'Cannot be undone.' });
```

> The `ui.showConfirm` promise resolves with `{ shown: true }` **before** the user answers. This event is the only way to learn the answer.

---

### 2.2 `ui.actionSelected`

| | |
| --- | --- |
| **Emitted by** | `NativeUICommandHandler.parseActionItems` (used by `showActionSheet` and `showBottomSheet`) |
| **When** | The user taps an item |
| **Payload** | `{ title: string, index: number }` |

```json
{ "title": "Delete", "index": 2 }
```

`index` is the zero-based position in the `items` array you passed.

---

### 2.3 `ui.snackbarAction`

| | |
| --- | --- |
| **Emitted by** | `NativeUICommandHandler.showSnackbar` |
| **When** | The user taps the snackbar action button |
| **Payload** | `{ actionTitle: string }` |

Only emitted when `actionTitle` was non-empty.

---

### 2.4 `ui.popupItemSelected`

| | |
| --- | --- |
| **Emitted by** | `NativeUICommandHandler.showPopupMenu` |
| **When** | The user taps a popup menu item |
| **Payload** | `{ title: string, index: number }` |

---

### 2.5 `ui.toolbarAction`

| | |
| --- | --- |
| **Emitted by** | `NativeUICommandHandler.addToolbarAction` |
| **When** | The user taps the toolbar action button |
| **Payload** | `{ id: string, label: string }` |

```js
await Native.call('ui.addToolbarAction', { id: 'save', label: 'Save' });
Native.on('ui.toolbarAction', ({ id }) => { if (id === 'save') save(); });
```

---

### 2.6 `ui.drawerItemSelected`

| | |
| --- | --- |
| **Emitted by** | `NativeUICommandHandler.addDrawerItem` |
| **When** | The user taps a drawer item |
| **Payload** | `{ id: string, title: string }` |

---

### 2.7 `theme.changed`

| | |
| --- | --- |
| **Emitted by** | `NativeApiIntegration.onThemeChanged` (registered as a `ThemeManager` listener) |
| **When** | `theme.setThemeMode`, `theme.applyTheme`, `theme.setSystemDark`, or a system dark-mode change |
| **Payload** | 10 keys |

```json
{
  "themeName": "RuntimeConfig",
  "darkMode": true,
  "primaryColor": "#2563EB",
  "surfaceColor": "#111827",
  "textColor": "#F9FAFB",
  "themeMode": "DARK",
  "secondaryColor": "#7C3AED",
  "backgroundColor": "#0B1220",
  "statusBarColor": "#0B1220",
  "navigationBarColor": "#0B1220"
}
```

`themeMode` is `LIGHT`, `DARK` or `SYSTEM`. The last five keys are present only when the active theme is a `ThemeConfig` instance (the normal case).

```js
Native.on('theme.changed', async () => {
  const { css } = await Native.call('theme.getCssVariables');
  document.getElementById('native-theme').textContent = css;
});
```

---

### 2.8 `security.biometric.result`

| | |
| --- | --- |
| **Emitted by** | `NativeApiIntegration.onBiometricResult` (wired through `SecurityCommandHandler.setBiometricResultListener`) |
| **When** | A biometric prompt started by `security.biometric.authenticate` finishes |
| **Payload** | `{ status, method, errorCode, message }` |

| `status` | Meaning | `errorCode` |
| --- | --- | --- |
| `SUCCEEDED` | Authentication passed | `0` |
| `FAILED` | Wrong finger/face, retry allowed | `0` |
| `ERROR` | Prompt failed or was cancelled | `BiometricPrompt` error code |

```json
{ "status": "SUCCEEDED", "method": "BIOMETRIC_STRONG", "errorCode": 0, "message": "Authentication succeeded" }
```

```js
const off = Native.on('security.biometric.result', ({ status, message }) => {
  off();
  if (status === 'SUCCEEDED') unlockVault();
  else showError(message);
});

await Native.call('security.biometric.authenticate', { title: 'Unlock notes' });
```

> `security.biometric.authenticate` resolves with `{ success: true, initiated: true }` as soon as the prompt is **shown**. This event carries the outcome.

---

### 2.9 Arbitrary topics via `web.emitEvent`

| | |
| --- | --- |
| **Emitted by** | `NativeWebCommandHandler.emitEvent` → `WebViewHost.emitEvent` → `WebBridge.emitToWeb` |
| **When** | You call `web.emitEvent` |
| **Payload** | Whatever you pass in `data` |

```js
Native.on('my.topic', (payload) => console.log(payload));
await Native.call('web.emitEvent', { topic: 'my.topic', data: { hello: 'world' } });
```

This is the supported way to test your own event plumbing, and the only way a page can originate an event on an arbitrary topic.

---

## 3. Event summary table

| Topic | Payload keys | Trigger |
| --- | --- | --- |
| `ui.confirm` | `confirmed` | Confirm dialog answered |
| `ui.actionSelected` | `title`, `index` | Action sheet / bottom sheet item tapped |
| `ui.snackbarAction` | `actionTitle` | Snackbar action tapped |
| `ui.popupItemSelected` | `title`, `index` | Popup menu item tapped |
| `ui.toolbarAction` | `id`, `label` | Toolbar action tapped |
| `ui.drawerItemSelected` | `id`, `title` | Drawer item tapped |
| `theme.changed` | `themeName`, `darkMode`, `primaryColor`, `surfaceColor`, `textColor`, `themeMode`, `secondaryColor`, `backgroundColor`, `statusBarColor`, `navigationBarColor` | Theme changed |
| `security.biometric.result` | `status`, `method`, `errorCode`, `message` | Biometric prompt finished |
| *(any)* | *(any)* | `web.emitEvent` |

---

## 4. Events that do **not** exist

These are frequently assumed. None of them is emitted anywhere in the source.

| Assumed event | Reality |
| --- | --- |
| `app.ready` / `native.ready` | Not emitted. Poll `NativeRuntimeBridgeClient` instead. |
| `app.pause` / `app.resume` | Not emitted. Use `document.visibilitychange`. |
| `app.backPressed` | Not emitted. The WebView handles back natively. |
| `network.online` / `network.offline` | Not emitted. Use `window.addEventListener('online'/'offline')` or poll `device.getNetworkState`. |
| `battery.changed` | Not emitted. Poll `device.getBattery`. |
| `notification.clicked` | Not emitted to the page. Deep links are routed natively by `NotificationDeepLinkRouter`. |
| `notification.received` | Not emitted. |
| `storage.changed` | Not emitted. |
| `camera.captured` | Not emitted. `camera.capture` resolves with the result. |
| `download.progress` | Not emitted. |
| `bridge.error` | Not emitted. Errors arrive as rejected promises. |
| `ui.toastShown` | Not emitted. |

---

## 5. Patterns

### 5.1 Promise wrapper around an event

```js
function once(topic, timeoutMs = 10000) {
  return new Promise((resolve, reject) => {
    let settled = false;
    const timer = setTimeout(() => {
      if (settled) return;
      settled = true;
      off();
      reject(Object.assign(new Error('EVENT_TIMEOUT'), { code: 'EVENT_TIMEOUT', topic }));
    }, timeoutMs);

    const off = Native.on(topic, (payload) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      off();
      resolve(payload);
    });
  });
}

// usage
const answer = once('ui.confirm');
await Native.call('ui.showConfirm', { title: 'Sure?' });
const { confirmed } = await answer;
```

Subscribe **before** calling, otherwise a fast native reply can arrive first.

### 5.2 Fan-out with cleanup

```js
const subscriptions = [];

function subscribe(topic, handler) {
  subscriptions.push(Native.on(topic, handler));
}

function unsubscribeAll() {
  while (subscriptions.length) subscriptions.pop()();
}

window.addEventListener('pagehide', unsubscribeAll);
```

### 5.3 Theme sync

```js
async function syncTheme() {
  const { css } = await Native.call('theme.getCssVariables');
  let style = document.getElementById('native-theme');
  if (!style) {
    style = document.createElement('style');
    style.id = 'native-theme';
    document.head.appendChild(style);
  }
  style.textContent = css;
}

await syncTheme();
Native.on('theme.changed', syncTheme);
```

### 5.4 Biometric gate

```js
async function requireBiometric(reason) {
  const capability = await Native.call('security.biometric.capability');
  if (!capability.available) return true;   // no hardware — do not lock the user out

  const result = once('security.biometric.result', 60000);
  await Native.call('security.biometric.authenticate', { title: reason });
  const outcome = await result;
  return outcome.status === 'SUCCEEDED';
}
```

---

## 6. Debugging events

```js
// Log every event the client receives
const original = window.NativeRuntimeBridgeClient._handleNativeMessage;
window.NativeRuntimeBridgeClient._handleNativeMessage = function (raw) {
  try {
    const msg = JSON.parse(raw);
    if (msg.type === 'EVENT') console.debug('[native event]', msg.action, msg.payload);
  } catch (e) { /* ignore */ }
  return original.call(this, raw);
};
```

This monkey-patch is safe: `_handleNativeMessage` is a plain property on the client object, and native calls it through `window['NativeRuntimeBridgeClient']._handleNativeMessage(...)`.

For a native-side view, use `devtools.getBridgeMetrics` (debug builds) or attach the Runtime Inspector:

```java
RuntimeInspectorIntegration.get().attach(webViewHost);
```

---

## 7. Related documents

- `JAVASCRIPT_BRIDGE.md` — envelope, gates, timeouts
- `NATIVE_API_REFERENCE.md` — the actions that trigger these events
- `NOTIFICATION_API.md` — why notification clicks are not page events
