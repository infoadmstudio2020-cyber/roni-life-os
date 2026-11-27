# RONiLiFEOS — Web SDK Reference App

A complete, runnable web app that exercises the **real** native API. Every button calls an action that exists in the Java source.

---

## Run it

1. Copy this folder into a RONiLiFEOS project (or import it as a ZIP).
2. Open the project — `manifest.json` points at `index.html`.
3. The status pill at the top turns green when `NativeRuntimeBridgeClient` is injected.

Opening `index.html` in a desktop browser also works: the app detects the missing bridge and shows a notice instead of throwing.

---

## Files

| File | Purpose |
| --- | --- |
| `manifest.json` | Project descriptor — `id` derives the virtual origin |
| `index.html` | UI, Bengali labels, 8 demo sections |
| `css/style.css` | Dark/light theme driven by `theme.changed` |
| `js/native.js` | **The reference bridge wrapper** — the only file that touches native |
| `js/app.js` | Demo handlers, one per action |

---

## What each section proves

| Section | Actions exercised | What to look for |
| --- | --- | --- |
| 1. UI overlays | `ui.showToast`, `ui.showAlert`, `ui.showConfirm`, `ui.showActionSheet`, `ui.showSnackbar`, `ui.showLoading`/`ui.hideLoading` | Confirm resolves via the `ui.confirm` **event**, not the promise |
| 2. Toolbar | `ui.setToolbarTitle`, `ui.setToolbarSubtitle`, `ui.addToolbarAction`, `ui.addTab`, `ui.selectTab`, `ui.setBadge` | `ui.toolbarAction` event fires on tap |
| 3. Device | `device.getInfo`, `device.getBattery`, `device.getNetworkState`, `device.copyToClipboard`, `device.readClipboard`, `device.shareText`, `device.vibrate` | Real hardware values |
| 4. Storage | `storage.set`, `storage.get`, `storage.keys`, `storage.getAll`, `storage.clear` | Values survive a reload; keys are project-scoped |
| 5. Theme | `theme.setThemeMode`, `theme.getCssVariables` | Page colours follow the native theme |
| 6. Security | `security.hash.sha256`, `security.biometric.capability`, `security.biometric.authenticate`, `security.keystore.*`, `security.permission.check` | Biometric result arrives as an event |
| 7. Notification | `notification.permission`, `notification.post`, `notification.cancel` | Requires an active project |
| 8. Network | `network.request`, `echo.echo`, route inventory | `echo.echo` proves the whole chain |

---

## The three rules this app demonstrates

```js
// 1. Wait for the bridge
await Native.ready();

// 2. Use namespace.action
await Native.call('ui.showToast', { message: 'হ্যালো' });

// 3. Use the exact parameter name
//    message — not text
//    parameters — not properties
//    screenName — not screen
//    input — not text
```

---

## Extending it

`js/native.js` exposes typed helpers. Add one:

```js
var camera = {
  capabilities: function () { return call('camera.getCapabilities'); },
  open: function (config) { return call('camera.open', config || {}); },
  capture: function () { return call('camera.capture'); }
};
```

Then wire a button in `index.html`:

```html
<button data-action="camera">ক্যামেরা</button>
```

and a handler in `js/app.js`:

```js
camera: async function () {
  var caps = await Native.call('camera.getCapabilities');
  print('camera.getCapabilities', caps);
}
```

---

## Documentation

- [`../../docs/web-sdk/README.md`](../../docs/web-sdk/README.md) — index
- [`../../docs/web-sdk/WEB_APP_DEVELOPER_GUIDE.md`](../../docs/web-sdk/WEB_APP_DEVELOPER_GUIDE.md) — full guide
- [`../../docs/web-sdk/NATIVE_API_REFERENCE.md`](../../docs/web-sdk/NATIVE_API_REFERENCE.md) — all 290 action identifiers
