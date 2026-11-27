> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# 08 — Packaging & Web Sources

NativeRuntime can load your web app from six different places. This document explains each one,
how paths resolve, and how to structure a single-page app.

---

## 1. Source types

| Type | Factory | Location meaning |
| --- | --- | --- |
| `ASSET` | `WebSource.asset("index.html")` | Path inside `assets/` |
| `ZIP` | `WebSource.zipAsset("webapp.zip")` | ZIP file inside `assets/` |
| `ZIP` (file) | `WebSource.zipFile("/sdcard/app.zip")` | ZIP on the device |
| `REMOTE_URL` | `WebSource.remote("https://…")` | Absolute URL |
| `FILE` | `WebSource.file("/sdcard/app/index.html")` | Absolute file path |
| `INLINE_HTML` | `WebSource.inlineHtml(html)` | HTML string |
| `CONTENT_URI` | `WebSource.contentUri("content://…")` | SAF / provider URI |

From JavaScript, use `ui.setWebViewSource` or `web.loadSource`:

```js
NativeRuntime.ui.setWebViewSource({ type: "asset", location: "index.html" });
NativeRuntime.ui.setWebViewSource({ type: "zip", location: "webapp.zip", entryPoint: "index.html" });
NativeRuntime.ui.setWebViewSource({ type: "remote", location: "https://app.example.com" });
NativeRuntime.ui.setWebViewSource({ type: "inline", html: "<h1>Hi</h1>" });
```

---

## 2. The virtual origin

Local sources are **not** loaded with `file://`. The SDK serves them from:

```
https://appassets.androidplatform.net/
```

`WebViewClient.shouldInterceptRequest` maps every request under that host to the asset/ZIP/file
provider. This gives you:

- a real `https://` origin, so `fetch`, `XHR`, `localStorage`, `IndexedDB`, service workers and
  ES modules all work;
- correct MIME types (`.js` → `text/javascript`, `.css` → `text/css`, `.wasm` → `application/wasm`);
- a stable base URL for relative paths.

**Consequences:**

| You want | Do this |
| --- | --- |
| Relative asset | `href="css/style.css"` |
| Absolute asset | `href="/css/style.css"` (resolves to the virtual host root) |
| API call | `fetch("https://api.example.com/…")` — absolute, cross-origin |
| Local JSON | `fetch("data/items.json")` — same origin, works |

> Do **not** hard-code `file:///android_asset/…`. It bypasses the provider and breaks `fetch`.

---

## 3. Assets folder layout

```
app/src/main/assets/
├── index.html
├── about.html
├── css/style.css
├── js/app.js
├── js/native-runtime.js
├── data/items.json
└── img/logo.png
```

Native side:

```java
builder.webAppInShell(WebSource.asset("index.html"))
```

`WebSource.asset("index.html")` resolves to `https://appassets.androidplatform.net/index.html`.
`WebSource.asset("dist/index.html")` resolves to `…/dist/index.html`, and relative paths inside
that page resolve under `/dist/`.

---

## 4. ZIP bundles

A ZIP bundle is the cleanest way to ship a build-pipeline output (Vite, Webpack, Angular, …).

```
webapp.zip
├── index.html
├── assets/index-3f2a1c.js
├── assets/index-9b7d2e.css
└── favicon.ico
```

```java
WebSource.zipAsset("webapp.zip")                       // entry = index.html
WebSource.zipAsset("webapp.zip").withEntryPoint("app/index.html")
```

```js
NativeRuntime.ui.setWebViewSource({
  type: "zip",
  location: "webapp.zip",
  entryPoint: "index.html"
});
```

Rules:

- The ZIP must be **stored or deflated** — no encryption, no split archives.
- `entryPoint` is relative to the ZIP root.
- Paths inside the ZIP are case-sensitive.
- The provider is opened lazily and closed when the source changes.

### Building the ZIP

```bash
cd dist && zip -r ../app/src/main/assets/webapp.zip . -x "*.map"
```

> Exclude source maps from production bundles — they roughly double the APK size.

---

## 5. Remote URLs

```java
WebSource.remote("https://app.example.com")
```

```js
NativeRuntime.ui.setWebViewSource({ type: "remote", location: "https://app.example.com" });
```

Considerations:

| Topic | Detail |
| --- | --- |
| Offline | Nothing is cached beyond the WebView's HTTP cache. Ship an asset fallback. |
| Mixed content | Blocked by default (`MIXED_CONTENT_NEVER_ALLOW`). Use HTTPS everywhere. |
| External links | Hosts listed in `WebHostConfig` open in the system browser; others load in place. |
| Bridge | Still attached — a remote page can call native APIs. See [Security](11_SECURITY.md). |
| Updates | Instant, no Play Store release. |

---

## 6. Inline HTML

```java
WebSource.inlineHtml("<h1>Generated at runtime</h1>")
```

```js
NativeRuntime.ui.setWebViewSource({
  type: "inline",
  html: "<h1>Hello</h1>",
  baseUrl: "https://appassets.androidplatform.net/"
});
```

Loaded with `loadDataWithBaseURL`. The default base URL is the virtual origin, so relative
assets still resolve. Use this for error pages, offline screens and server-generated fragments.

---

## 7. Single-page apps (SPA)

The WebView does not know about your router. Two rules:

1. **Use hash routing** (`#/orders/42`) — it never triggers a network request.
2. If you must use history routing, intercept links and call `history.pushState` yourself.

```js
// Hash router — always safe
window.addEventListener("hashchange", render);
function navigate(path) { location.hash = path; }
```

History routing works for the initial load but a hard refresh on `/orders/42` will 404 because
the asset provider has no such file. Either:

- add a fallback in the native provider (Android developer task), or
- keep the SPA on hash routes.

### Back button

The native Activity handles back by calling `WebView.canGoBack()`. With hash routing, hash
changes create history entries, so back works naturally. If you use a custom in-page router,
listen for the native back intent — there is no dedicated event, so mirror your route into
`location.hash` to keep the WebView history in sync.

---

## 8. Versioning and cache busting

The WebView caches aggressively. When you ship a new bundle:

```js
NativeRuntime.web.clearCache();
NativeRuntime.web.setCacheMode("no_cache");
NativeRuntime.ui.reload();
```

Or version the file name (`app.2f9c1.js`) so the URL changes.

---

## 9. Size budget

| Item | Guidance |
| --- | --- |
| HTML + CSS + JS | Keep under 2 MB uncompressed for a fast cold start |
| Images | Use WebP; the WebView supports it natively |
| Fonts | Subset; each weight is ~30–80 KB |
| ZIP bundle | Compression helps JS/CSS ~3×; images are already compressed |
| Source maps | Exclude from release |

---

## 10. Checklist

- [ ] Entry file is at the root of `assets/` or the ZIP root.
- [ ] All URLs are relative or absolute `https://`.
- [ ] No `file://` references.
- [ ] MIME types are correct (`.mjs` may need renaming to `.js`).
- [ ] SPA uses hash routing.
- [ ] `viewport` meta tag present.
- [ ] Cache-busting strategy decided.
- [ ] Offline fallback exists if loading from a remote URL.
