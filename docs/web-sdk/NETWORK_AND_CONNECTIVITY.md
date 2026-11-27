# RONiLiFEOS — Network and Connectivity API

**Native classes:** `NetworkCommandHandler`, `NativeNetworkService`, `NativeDeviceCommandHandler`
**Routes:** `/network`, `/device`, `/platform`

---

## 1. Three ways to reach the network

| Way | API | CORS | Timeout control | Use when |
| --- | --- | --- | --- | --- |
| Browser `fetch` | `fetch()` | Subject to CORS | `AbortController` | Same-origin or CORS-enabled hosts |
| Native HTTP | `network.request` | **Bypassed** | `timeoutMs` | Third-party APIs without CORS headers |
| Background transfer | `automation.download` / `upload` | Bypassed | Engine default | Large files, must survive backgrounding |

`fetch()` works normally because the page is served from `https://<project>.runtime.local`, a real HTTPS origin.

---

## 2. `network.request`

**Native class:** `com.roni.library.network.handler.NetworkCommandHandler`
**Route:** `/network` · **Action pattern:** `*` · **Method:** `handleRequest(NativeApiRequest)`

### 2.1 Parameters

| Parameter | Type | Required | Default | Notes |
| --- | --- | --- | --- | --- |
| `url` | string | **R** | — | Must be `http://` or `https://` |
| `method` | string | O | `GET` | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS` |
| `headers` | object | O | `{}` | String → string map |
| `body` | string | O | — | **Must be a string.** Objects are ignored. |
| `timeoutMs` | number | O | engine default | Milliseconds |

### 2.2 Return value

```json
{
  "statusCode": 200,
  "body": "{\"ok\":true}",
  "latencyMs": 143,
  "method": "GET",
  "url": "https://api.example.com/health"
}
```

`body` is always a **string**. Parse it yourself.

### 2.3 Promise behaviour

Resolves for **any** HTTP status, including 4xx and 5xx. It rejects only on transport failure. Check `statusCode` yourself.

### 2.4 Errors

| Code | Cause |
| --- | --- |
| `INVALID_URL` | URL missing or unparseable |
| `UNSUPPORTED_SCHEME` | Not `http`/`https` |
| `UNSUPPORTED_METHOD` | Method not in the allow-list |
| `FORBIDDEN_HEADER` | `host`, `content-length` or `connection` supplied |
| `NETWORK_ERROR` | DNS, TLS, timeout, connection reset |

### 2.5 Example

```js
async function api(path, options = {}) {
  const res = await Native.call('network.request', {
    url: 'https://api.example.com' + path,
    method: options.method || 'GET',
    headers: Object.assign({ 'Content-Type': 'application/json' }, options.headers),
    body: options.body ? JSON.stringify(options.body) : undefined,
    timeoutMs: 15000
  });

  if (res.statusCode < 200 || res.statusCode >= 300) {
    const error = new Error('HTTP_' + res.statusCode);
    error.code = 'HTTP_' + res.statusCode;
    error.body = res.body;
    throw error;
  }
  return res.body ? JSON.parse(res.body) : null;
}

const notes = await api('/notes');
await api('/notes', { method: 'POST', body: { title: 'নোট' } });
```

### 2.6 Expected response

```json
{ "statusCode": 201, "body": "{\"id\":42}", "latencyMs": 88, "method": "POST", "url": "https://api.example.com/notes" }
```

---

## 3. Connectivity state

### 3.1 `device.getNetworkState`

```json
{ "connected": true, "type": "WIFI", "metered": false, "roaming": false, "source": "platform" }
```

| `type` | Meaning |
| --- | --- |
| `WIFI` | Wi-Fi |
| `CELLULAR` | Mobile data |
| `ETHERNET` | Wired |
| `NONE` | Offline |
| `UNKNOWN` | Undetermined |

`source` is `"platform"` when `PlatformEngine` answered, `"system"` on the `ConnectivityManager` fallback.

### 3.2 `platform.network.status`

```js
const { networkStatus } = await Native.call('platform.network.status');
```

Returns the platform engine's richer snapshot (transport, metered, validated).

### 3.3 Browser events

```js
window.addEventListener('online', refresh);
window.addEventListener('offline', showOfflineBanner);
```

These fire from the WebView's own connectivity tracking and are independent of `device.getNetworkState`.

### 3.4 Polling pattern

There is **no** `network.changed` event. Poll, or use the browser events:

```js
async function isOnline() {
  try {
    const state = await Native.call('device.getNetworkState');
    return !!state.connected;
  } catch (e) {
    return navigator.onLine;
  }
}
```

---

## 4. Background transfers

For large files or work that must survive backgrounding, use `automation.*`:

```js
const transfer = await Native.call('automation.download', {
  id: 'dl-' + Date.now(),
  url: 'https://example.com/archive.zip',
  path: 'downloads/archive.zip'
});
```

Full details: `BACKGROUND_SERVICES_API.md` §6.

---

## 5. Saving a response to disk

```js
const res = await Native.call('network.request', { url: 'https://example.com/data.json' });
const base64 = btoa(unescape(encodeURIComponent(res.body)));
await Native.call('filemanager.writeFile', { path: 'cache/data.json', base64 });
```

`filemanager.writeFile` takes **base64**, not plain text.

---

## 6. Downloads to the public Downloads folder

```js
if (typeof window.RONiDownload !== 'undefined') {
  window.RONiDownload.download('https://example.com/report.pdf', 'report.pdf');
}
```

`RONiDownload` is a separate JavaScript interface (`DownloadCenter.JS_INTERFACE`), synchronous and fire-and-forget. It has no return value and no error channel.

---

## 7. Security notes

| Concern | Guidance |
| --- | --- |
| Cleartext | `http://` is accepted by the bridge but Android's network security config may block it. Use HTTPS. |
| Certificate pinning | Not exposed to the bridge. Configure it in the host app. |
| Auth headers | Allowed. Never log them. |
| Response size | No bridge limit on the response, but a multi-MB `body` string will stall the JS thread. Stream large payloads via `automation.download`. |
| Redirects | Followed by the engine. The final URL is not reported. |

---

## 8. Related documents

- `NATIVE_API_REFERENCE.md` §9 — action table
- `BACKGROUND_SERVICES_API.md` — transfers and retries
- `FILE_ACCESS_AND_RESOURCES.md` — writing responses to disk
- `ERROR_CODES.md` — full error catalog
