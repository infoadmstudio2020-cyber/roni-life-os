> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# 10 — Troubleshooting

Symptom → cause → fix.

---

## 1. My call succeeds but nothing happens

**Symptom:** `NativeRuntime.ui.showTost(...)` resolves with `{ shown: true }` but no toast appears.

**Cause:** `WebBridge.handleWebMessage` falls back to echoing the payload with `success: true`
when the router cannot match the route. Typos therefore fail silently.

**Fix:** Check the action name against [03 — UI API](03_UI_API.md). To detect it yourself, compare
the response with what you sent:

```js
NativeRuntimeBridgeClient.call("ui.showTost", { message: "x" }).then(function (r) {
  if (r && r.message === "x" && !("shown" in r)) {
    console.error("Unknown action — payload was echoed back");
  }
});
```

**Permanent fix (Android developer):** in `WebBridge.handleWebMessage`, replace the fallback
`NativeApiResponse.success(...)` with an error response. See
[audit #12](../AUDIT_AND_UPDATE_REPORT.md#h1-unknown-routes-return-success).

---

## 2. `NativeRuntimeBridgeClient is undefined`

**Cause:** The client wrapper is injected in `onPageFinished`, after your script runs.

**Fix:** Poll for it, or call from `DOMContentLoaded` / a user gesture.

```js
function whenBridgeReady(cb) {
  (function poll() {
    if (window.NativeRuntimeBridgeClient) return cb();
    setTimeout(poll, 30);
  })();
}
```

If it never appears, the native side did not attach the bridge. Check that
`WebHostConfig.builder().bridgeEnabled(true)` (the default) and that the WebView host was created.

---

## 3. `Native bridge is not available`

**Cause:** You are running in a desktop browser, or the page was loaded outside the WebView.

**Fix:** Guard every call:

```js
if (!window.NativeRuntimeBridge) {
  console.log("[browser] native call skipped");
  return Promise.resolve({ simulated: true });
}
```

---

## 4. `CalledFromWrongThreadException`

**Fixed in 1.1.1.** `SnackbarManager.show()` and `PopupMenuManager.showPopupMenu()` now marshal
to the main thread via `View.post` when the caller is off-main.

If you still see this exception, you are running an older AAR. Rebuild against 1.1.1 or later.

If you are writing your own manager that touches Android views, follow the same pattern:

```java
if (Looper.myLooper() == Looper.getMainLooper()) {
    task.run();
} else {
    anchorView.post(task);
}
```

---

## 5. White screen, no error

Check, in order:

1. **Entry file path.** `WebSource.asset("index.html")` expects `assets/index.html`.
2. **Console.** `chrome://inspect` → your WebView → Console.
3. **MIME type.** A `.js` file served as `text/plain` will be blocked. Rename `.mjs` → `.js`.
4. **Absolute paths.** `/js/app.js` resolves to the virtual host root — correct. `file:///android_asset/js/app.js` — wrong.
5. **CSP.** A strict `Content-Security-Policy` can block the injected bridge script. Allow `'unsafe-inline'` for the bootstrap, or drop the CSP meta tag during development.
6. **JS error before render.** Wrap your bootstrap in `try/catch` and write the error into the DOM.

---

## 6. `fetch()` fails with a CORS or "not allowed" error

**Cause:** You are loading from `file://`, or calling a cross-origin API without CORS headers.

**Fix:**

- Local sources are served from `https://appassets.androidplatform.net` — use relative paths.
- Remote APIs must send `Access-Control-Allow-Origin`.
- Mixed content (`http://` from an `https://` page) is blocked. Use HTTPS.

---

## 7. `localStorage` is empty after a reload

**Cause:** The origin changed. `file://`, `https://appassets.androidplatform.net` and
`https://example.com` are three different origins with three different stores.

**Fix:** Keep one source type per app, or move critical data to `storage.*` (which is
origin-independent).

---

## 8. Back button exits the app instead of going back

**Cause:** The page has no history entries (single-page app with no hash changes).

**Fix:** Mirror your route into `location.hash` so `WebView.canGoBack()` returns true.

```js
function navigate(path) { location.hash = path; }
```

---

## 9. Native toolbar shows the wrong title

**Cause:** The page's `<title>` is not automatically pushed to the native toolbar.

**Fix:** Call it explicitly:

```js
NativeRuntime.ui.setTitle(document.title);
```

---

## 10. `ui.getTabs()` returns an empty array

**Cause:** The native host did not enable bottom navigation, or tabs were added after your call.

**Fix:** Check `ui.getConfig().bottomNavEnabled` first. Tabs added natively in `onUIRendered`
are available after the first frame — call `getTabs` from a `setTimeout(…, 0)` or after a user gesture.

---

## 11. Drawer / tab / FAB calls do nothing

**Cause:** The component is not enabled in the native configuration.

**Fix:**

```js
NativeRuntime.ui.getConfig().then(function (c) {
  console.log({
    toolbar: c.toolbarEnabled,
    drawer: c.drawerEnabled,
    tabs: c.bottomNavEnabled,
    fab: c.fabEnabled
  });
});
```

If a flag is `false`, the Android developer must enable it in `onConfigureUI`.

---

## 12. `web.*` returns "No active WebView is attached"

**Cause:** The call ran before the WebView host was created, or the host is in a mode without a
WebView.

**Fix:** Wait for the first paint:

```js
window.addEventListener("load", function () {
  NativeRuntime.web.getState().then(console.log);
});
```

---

## 13. `storage.*` returns "No active Context is attached"

**Cause:** The UI host has no Activity yet (called from `Application.onCreate`, for example).

**Fix:** Only call `storage.*` from an Activity-hosted page.

---

## 14. Images and fonts 404 inside a ZIP bundle

**Cause:** Case mismatch, or the file was excluded by the zip command.

**Fix:**

```bash
unzip -l webapp.zip | head -50     # verify the exact paths
```

ZIP paths are case-sensitive even though the host filesystem may not be.

---

## 15. App is slow on first load

| Cause | Fix |
| --- | --- |
| Large unminified JS | Minify and split |
| Source maps in the bundle | Exclude `*.map` |
| Many small asset requests | Bundle CSS/JS |
| Remote source with no cache | Use `web.setCacheMode("cache")` |
| Heavy web fonts | Subset, `font-display: swap` |

---

## 16. Debugging checklist

```
[ ] chrome://inspect shows the WebView
[ ] Console has no red errors
[ ] window.NativeRuntimeBridge exists
[ ] window.NativeRuntimeBridgeClient exists
[ ] NativeRuntime.ui.getConfig() resolves
[ ] NativeRuntime.web.getState() resolves
[ ] Logcat tag NativeWebBridgeSDK shows the request
[ ] Logcat tag NativeRuntimeWebHost shows page events
```

Enable verbose native logging:

```java
WebHostConfig.builder().debugLogging(true).build()
```

---

## 17. Getting help

When reporting a bug, include:

1. The exact JS call and payload.
2. The resolved/rejected value.
3. `ui.getConfig()` output.
4. `device.getInfo()` output.
5. Logcat lines for `NativeWebBridgeSDK`, `NativeRouter`, `NativeRuntimeWebHost`.
6. Whether the app was in in-shell or full-screen mode.
