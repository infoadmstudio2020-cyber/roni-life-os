> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# 01 — Getting Started

This document takes you from an empty folder to a web app running inside the native shell.

---

## 1. How the pieces fit together

There are two sides:

| Side | Who owns it | What it does |
| --- | --- | --- |
| **Native host** | Android developer (or the SDK sample) | Creates the Activity, builds the toolbar/drawer/tabs, creates the WebView, attaches the bridge |
| **Web app** | **You** | Ships `index.html` + assets, calls `NativeRuntime.*` |

You only need to agree on **one thing** with the native side: *where your entry file lives*.

---

## 2. Project layout

Put your web app in the Android module's assets folder:

```
app/src/main/assets/
├── index.html              ← entry point
├── about.html
├── css/
│   └── style.css
├── js/
│   ├── native-runtime.js   ← bridge wrapper (copy from examples/)
│   └── app.js              ← your code
└── img/
    └── logo.png
```

Everything under `assets/` is packaged into the APK and served from a virtual HTTPS origin
(`https://appassets.androidplatform.net/`), so **relative paths work exactly like a normal web server**:

```html
<link rel="stylesheet" href="css/style.css">
<script src="js/app.js"></script>
<img src="img/logo.png">
```

> **Why a virtual origin?** `file://` URLs break `fetch()`, `XMLHttpRequest`, `localStorage`,
> service workers and ES modules. The SDK serves assets over a virtual HTTPS host so all of
> those APIs behave normally.

---

## 3. The HTML skeleton

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <title>My App</title>
  <link rel="stylesheet" href="css/style.css">
</head>
<body>
  <main id="app">
    <h1>Hello native</h1>
    <button id="toast">Show native toast</button>
  </main>

  <script src="js/native-runtime.js"></script>
  <script src="js/app.js"></script>
</body>
</html>
```

Two meta tags matter:

- `width=device-width, initial-scale=1` — without it the WebView renders a 980px desktop viewport.
- `viewport-fit=cover` — lets you use `env(safe-area-inset-*)` for notches. Pair it with `device.getSafeArea()`.

---

## 4. The bridge wrapper

Copy `examples/webapp-starter/native-runtime.js` into `assets/js/`. It creates `window.NativeRuntime`
with four namespaces and handles the Promise plumbing for you.

```js
NativeRuntime.ui.toast("Saved", "success");
NativeRuntime.ui.setTitle("Orders");
NativeRuntime.ui.setBadge("tab_orders", 3);
```

If you prefer to write your own wrapper, read [02 — Bridge Protocol](02_BRIDGE_PROTOCOL.md). The raw API is:

```js
window.NativeRuntimeBridgeClient.call("ui.showToast", { message: "Hi" })
  .then(function (res) { /* res = { shown: true } */ })
  .catch(function (err) { /* err.message */ });
```

---

## 5. Waiting for the bridge

The native interface object (`window.NativeRuntimeBridge`) is injected **before** the page loads.
The Promise wrapper (`window.NativeRuntimeBridgeClient`) is injected **after** the page finishes loading.

If your code runs at parse time and calls the bridge immediately, guard it:

```js
function whenBridgeReady(callback, timeoutMs) {
  var deadline = Date.now() + (timeoutMs || 5000);
  (function poll() {
    if (window.NativeRuntimeBridgeClient) return callback();
    if (Date.now() > deadline) return console.warn("Native bridge not available");
    setTimeout(poll, 30);
  })();
}

whenBridgeReady(function () {
  NativeRuntime.ui.setTitle("Ready");
});
```

In practice, calling from a `DOMContentLoaded` handler or a user gesture is always safe.

---

## 6. Your first screen

```js
document.getElementById("toast").addEventListener("click", function () {
  NativeRuntime.ui.toast("Hello from the web", "success");
});
```

Run the app. You should see a native Android toast — not an HTML one.

---

## 7. Choosing a shell mode

The native host decides this, but you should know which one you are targeting because it changes
what `ui.*` can do.

### Mode A — In-shell (recommended)

The SDK builds a native toolbar, optional drawer, optional bottom navigation and optional FAB,
and puts your web app in the content area.

```java
// Native side
builder
    .enable(AppBar.TOOLBAR)
    .enable(Navigation.BOTTOM_NAVIGATION)
    .enable(Overlay.FLOATING_ACTION_BUTTON)
    .enable(Content.WEB_VIEW)
    .webAppInShell(WebSource.asset("index.html"))
    .build();
```

Your web app can then drive all of it:

```js
NativeRuntime.ui.setTitle("Orders");
NativeRuntime.ui.setSubtitle("12 open");
NativeRuntime.ui.selectTab("tab_orders");
NativeRuntime.ui.setBadge("tab_orders", 12);
NativeRuntime.ui.openDrawer();
```

### Mode B — Full-screen web host

No native chrome. Your web app owns the whole screen.

```java
builder.webApp(WebSource.asset("index.html")).build();
```

In this mode `ui.setTitle`, `ui.openDrawer`, `ui.selectTab` and `ui.setFabVisible` are no-ops
(there is nothing to control). Overlays (`toast`, `alert`, `loading`) still work.

**Rule of thumb:** if you already have your own HTML header and tab bar, use Mode B.
If you want the native look, use Mode A.

---

## 8. Packaging options

| Option | Native call | Best for |
| --- | --- | --- |
| Assets folder | `WebSource.asset("index.html")` | Most apps |
| ZIP bundle | `WebSource.zipAsset("webapp.zip")` | Build-pipeline output, versioned bundles |
| Remote URL | `WebSource.remote("https://app.example.com")` | Server-rendered apps, instant updates |
| Inline HTML | `WebSource.inlineHtml(html)` | Generated/offline screens |
| Device file | `WebSource.file("/sdcard/app/index.html")` | Kiosk / sideloaded content |
| content:// URI | `WebSource.contentUri(uri)` | Files picked via SAF |

Details: [08 — Packaging & Sources](08_PACKAGING_AND_SOURCES.md).

---

## 9. Switching sources at runtime

Your web app can replace itself without recreating the native shell:

```js
NativeRuntime.ui.setWebViewSource({ type: "asset", location: "about.html" });
NativeRuntime.ui.setWebViewSource({ type: "remote", location: "https://example.com" });
NativeRuntime.ui.setWebViewSource({ type: "zip", location: "webapp.zip", entryPoint: "index.html" });
```

The toolbar, drawer and tabs stay mounted. The page reloads, so **in-memory JS state is lost** —
persist anything important with `storage.*` first.

---

## 10. Checklist before you hand off to the Android developer

- [ ] `index.html` is at the root of `assets/` (or inside the ZIP root).
- [ ] All paths are **relative** (`css/style.css`, not `/css/style.css`).
- [ ] `viewport` meta tag is present.
- [ ] `native-runtime.js` is loaded before your app code.
- [ ] No `file://` URLs anywhere.
- [ ] No absolute `http://` calls to `localhost` — use your real API host.
- [ ] You handle the "bridge not available" case (browser preview, desktop debugging).
- [ ] You tested with the WebView's Chrome DevTools (`chrome://inspect`).
- [ ] You read the [audit report](../AUDIT_AND_UPDATE_REPORT.md) and know which features are safe.

---

## 11. Debugging in a desktop browser

Your app should still run in Chrome/Firefox without the native layer. Make the wrapper degrade gracefully:

```js
var hasNative = !!window.NativeRuntimeBridgeClient;

NativeRuntime.ui.toast = function (message) {
  if (hasNative) return NativeRuntimeBridgeClient.call("ui.showToast", { message: message });
  console.log("[toast]", message);          // browser fallback
  return Promise.resolve({ shown: true });
};
```

The starter wrapper in `examples/webapp-starter/native-runtime.js` already does this: every call
resolves with a `{ simulated: true }` payload when the bridge is missing, so your UI never breaks
during development.

---

## 12. Next steps

1. [02 — Bridge Protocol](02_BRIDGE_PROTOCOL.md) — understand what happens on the wire.
2. [03 — UI API](03_UI_API.md) — build the native chrome.
3. [09 — Recipes](09_RECIPES.md) — copy a working screen.
