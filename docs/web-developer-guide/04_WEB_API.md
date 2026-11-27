> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# 04 — `web.*` WebView API

Controls the WebView that hosts your app: navigation, source loading, zoom, cookies, cache,
JavaScript evaluation and every `WebSettings` flag.

All `web.*` calls are marshalled to the Android main thread with a **5 second** timeout, so they
are safe to call from any JS context.

---

## 1. Loading content

### `web.loadSource`

Loads any supported source type.

```js
NativeRuntime.web.loadSource({ type: "asset", location: "index.html" });
NativeRuntime.web.loadSource({ type: "remote", location: "https://example.com" });
NativeRuntime.web.loadSource({ type: "zip", location: "webapp.zip", entryPoint: "index.html" });
```

| Param | Type | Notes |
| --- | --- | --- |
| `type` | string | `asset` · `file` · `zip` · `zipfile` · `inline` · `content` · `remote` |
| `location` | string | Path, URL or URI |
| `entryPoint` | string | Entry file inside a ZIP or directory |
| `html` | string | Required for `inline` |
| `baseUrl` | string | Optional base for `inline` |

**Returns:** `{ loaded: true }`

---

### `web.loadUrl`

```js
NativeRuntime.web.loadUrl("https://example.com");
```

**Returns:** `{ loaded: true }`

---

### `web.loadHtml`

```js
NativeRuntime.web.loadHtml("<h1>Hello</h1>", "https://appassets.androidplatform.net/");
```

| Param | Type | Default |
| --- | --- | --- |
| `html` | string | — |
| `baseUrl` | string | virtual origin |

**Returns:** `{ loaded: true }`

---

## 2. Navigation

| Action | Payload | Returns |
| --- | --- | --- |
| `web.reload` | `{}` | `{ reloaded: true }` |
| `web.stop` | `{}` | `{ stopped: true }` |
| `web.goBack` | `{}` | `{ navigated: true }` |
| `web.goForward` | `{}` | `{ navigated: true }` |
| `web.canGoBack` | `{}` | `{ canGoBack: boolean }` |
| `web.canGoForward` | `{}` | `{ canGoForward: boolean }` |
| `web.clearHistory` | `{}` | `{ cleared: true }` |
| `web.getState` | `{}` | see below |

`web.getState` returns:

```json
{
  "url": "https://appassets.androidplatform.net/index.html",
  "canGoBack": false,
  "canGoForward": false,
  "title": "My App",
  "progress": 100
}
```

> `web.reload` reloads the **current source descriptor**, not just `location.reload()`.
> For a plain page refresh prefer `location.reload()` — it preserves the JS heap.

---

## 3. JavaScript evaluation

### `web.evaluate`

Runs a script in the page context and returns the raw result string.

```js
NativeRuntime.web.evaluate("document.title").then(function (r) {
  console.log(r.evaluated);   // true — the result itself is not returned
});
```

| Param | Type |
| --- | --- |
| `script` | string |

**Returns:** `{ evaluated: true }`

> ⚠️ The handler does **not** return the script's value. If you need a value back, have the
> script call the bridge itself:
>
> ```js
> NativeRuntime.web.evaluate(
>   "NativeRuntimeBridgeClient.call('storage.set', {key:'title', value: document.title})"
> );
> ```

---

## 4. Zoom

| Action | Payload | Returns |
| --- | --- | --- |
| `web.setZoom` | `{ zoom }` | `{ zoom }` |
| `web.getZoom` | `{}` | `{ zoom }` |

```js
NativeRuntime.web.setZoom(120);   // 120% text scale
```

> ⚠️ **This is text zoom, not page zoom.** It maps to `WebSettings.setTextZoom()`, which scales
> font sizes only. Layout width is unchanged. For true page zoom use CSS
> (`document.documentElement.style.zoom`) or the viewport meta tag.

---

## 5. User agent

| Action | Payload | Returns |
| --- | --- | --- |
| `web.setUserAgent` | `{ userAgent }` | `{ userAgent }` |
| `web.getUserAgent` | `{}` | `{ userAgent }` |

```js
NativeRuntime.web.getUserAgent().then(function (r) {
  console.log(r.userAgent);   // "Mozilla/5.0 (Linux; Android 14; …) …"
});
```

> Changing the UA does not reload the page. Call `web.reload` afterwards.

---

## 6. Settings

### `web.getSettings`

Returns the live `WebSettings` snapshot:

```json
{
  "javaScriptEnabled": true,
  "domStorageEnabled": true,
  "databaseEnabled": false,
  "geolocationEnabled": false,
  "saveFormData": true,
  "supportZoom": true,
  "builtInZoomControls": true,
  "displayZoomControls": false,
  "loadWithOverviewMode": true,
  "useWideViewPort": true,
  "defaultTextEncodingName": "UTF-8",
  "standardFontFamily": "sans-serif",
  "defaultFontSize": 16,
  "minimumFontSize": 8,
  "textZoom": 100,
  "userAgent": "Mozilla/5.0 …",
  "allowFileAccess": false,
  "allowContentAccess": true,
  "javaScriptCanOpenWindowsAutomatically": false,
  "supportMultipleWindows": false,
  "blockNetworkImage": false,
  "blockNetworkLoads": false,
  "loadsImagesAutomatically": true,
  "cacheMode": -1,
  "mediaPlaybackRequiresUserGesture": true,
  "mixedContentMode": 0
}
```

### `web.setSettings`

Applies several flags at once, then returns the new snapshot.

```js
NativeRuntime.web.setSettings({
  javaScriptEnabled: true,
  domStorageEnabled: true,
  supportZoom: false,
  defaultFontSize: 18
});
```

### Individual setters

| Action | Payload | Returns |
| --- | --- | --- |
| `web.setJavaScript` | `{ enabled }` | `{ enabled }` |
| `web.setDomStorage` | `{ enabled }` | `{ enabled }` |
| `web.setAllowFileAccess` | `{ enabled }` | `{ enabled }` |
| `web.setAllowContentAccess` | `{ enabled }` | `{ enabled }` |
| `web.setMixedContentMode` | `{ mode }` | `{ mode }` |
| `web.setSafeBrowsing` | `{ enabled }` | `{ enabled }` |
| `web.setSupportZoom` | `{ enabled }` | `{ enabled }` |
| `web.setBuiltInZoomControls` | `{ enabled }` | `{ enabled }` |
| `web.setDisplayZoomControls` | `{ enabled }` | `{ enabled }` |
| `web.setLoadWithOverviewMode` | `{ enabled }` | `{ enabled }` |
| `web.setUseWideViewPort` | `{ enabled }` | `{ enabled }` |
| `web.setDefaultTextEncodingName` | `{ encoding }` | `{ encoding }` |
| `web.setStandardFontFamily` | `{ family }` | `{ family }` |
| `web.setDefaultFontSize` | `{ size }` | `{ size }` |
| `web.setMinimumFontSize` | `{ size }` | `{ size }` |
| `web.setDatabaseEnabled` | `{ enabled }` | `{ enabled }` |
| `web.setGeolocationEnabled` | `{ enabled }` | `{ enabled }` |
| `web.setSaveFormData` | `{ enabled }` | `{ enabled }` |
| `web.setJavaScriptCanOpenWindowsAutomatically` | `{ enabled }` | `{ enabled }` |
| `web.setSupportMultipleWindows` | `{ enabled }` | `{ enabled }` |
| `web.setMediaPlaybackRequiresUserGesture` | `{ enabled }` | `{ enabled }` |
| `web.setOffscreenPreRaster` | `{ enabled }` | `{ enabled }` |
| `web.setForceDark` | `{ mode }` | `{ mode }` |
| `web.setAlgorithmicDarkeningAllowed` | `{ enabled }` | `{ enabled }` |
| `web.setBlockNetworkImage` | `{ enabled }` | `{ enabled }` |
| `web.setBlockNetworkLoads` | `{ enabled }` | `{ enabled }` |
| `web.setLoadsImagesAutomatically` | `{ enabled }` | `{ enabled }` |

`mixedContentMode` values: `always` · `never` · `compatibility` (default).
`forceDark` values: `auto` · `on` · `off`.

> ⚠️ `web.setGeolocationEnabled(true)` flips the settings flag, but the host's
> `onGeolocationPermissionsShowPrompt` always denies the request. Geolocation does not work
> in the current build. See [audit #14](../AUDIT_AND_UPDATE_REPORT.md#h7-webview-permission-requests-are-always-denied).

---

## 7. Cache

| Action | Payload | Returns |
| --- | --- | --- |
| `web.clearCache` | `{}` | `{ cleared: true }` |
| `web.setCacheMode` | `{ mode }` | `{ cacheMode }` |

`mode` values:

| Value | Maps to |
| --- | --- |
| `default` | `LOAD_DEFAULT` |
| `cache` | `LOAD_CACHE_ELSE_NETWORK` |
| `no_cache` | `LOAD_NO_CACHE` |
| `cache_only` | `LOAD_CACHE_ONLY` |

```js
NativeRuntime.web.setCacheMode("no_cache");   // force fresh content
```

---

## 8. Cookies

| Action | Payload | Returns |
| --- | --- | --- |
| `web.setCookie` | `{ url, value }` | `{ set: true }` |
| `web.getCookies` | `{ url }` | `{ cookies }` |
| `web.clearCookies` | `{}` | `{ cleared: true }` |

```js
NativeRuntime.web.setCookie("https://api.example.com", "session=abc123; Path=/; Secure");
NativeRuntime.web.getCookies("https://api.example.com").then(function (r) {
  console.log(r.cookies);
});
```

> `setCookie` writes the raw `Set-Cookie` string. It does not parse a name/value pair for you.

---

## 9. Appearance

| Action | Payload | Returns |
| --- | --- | --- |
| `web.setBackgroundColor` | `{ color }` | `{ color }` |
| `web.setProgressBarVisible` | `{ visible }` | `{ visible }` |

```js
NativeRuntime.web.setBackgroundColor("#0f172a");   // avoids a white flash on dark themes
NativeRuntime.web.setProgressBarVisible(false);
```

---

## 10. Emitting events to the page

`web.emitEvent` pushes a native event into your JS listeners. Useful for native → web signalling
from a host app.

```js
NativeRuntime.on("app.refresh", function (data) {
  console.log("native asked us to refresh", data);
});

// Native side:
// WebViewHost.emitEvent("app.refresh", data)
```

| Param | Type |
| --- | --- |
| `topic` | string |
| `data` | object |

**Returns:** `{ emitted: true }`

---

## 11. Complete action list

```
loadSource loadUrl loadHtml reload stop goBack goForward canGoBack canGoForward
clearHistory getState evaluate setZoom getZoom setUserAgent getUserAgent
getSettings setSettings clearCache setCacheMode setCookie getCookies clearCookies
setBackgroundColor setProgressBarVisible emitEvent
setJavaScript setDomStorage setAllowFileAccess setAllowContentAccess setMixedContentMode
setSafeBrowsing setSupportZoom setBuiltInZoomControls setDisplayZoomControls
setLoadWithOverviewMode setUseWideViewPort setDefaultTextEncodingName
setStandardFontFamily setDefaultFontSize setMinimumFontSize setDatabaseEnabled
setGeolocationEnabled setSaveFormData setJavaScriptCanOpenWindowsAutomatically
setSupportMultipleWindows setMediaPlaybackRequiresUserGesture setOffscreenPreRaster
setForceDark setAlgorithmicDarkeningAllowed setBlockNetworkImage setBlockNetworkLoads
setLoadsImagesAutomatically
```
