# RONiLiFEOS — File Access and Resource Loading

**Native classes:** `WebSourceResolver`, `WebPaths`, `FileResourceProvider`, `AssetResourceProvider`, `ZipResourceProvider`, `FileCommandHandler`
**Routes:** `/web`, `/filemanager`

---

## 1. How your files are served

Every local source is served through a **virtual HTTPS origin**, never `file://`:

```
https://<slug>-<hash8>.runtime.local/index.html
```

That is what makes `fetch`, ES modules, `localStorage`, `IndexedDB`, cookies and service workers behave normally.

| Source kind | Provider | Path root |
| --- | --- | --- |
| `ASSET` | `AssetResourceProvider` | APK `assets/` |
| `ZIP` | `ZipResourceProvider` | archive root |
| `FILE` | `FileResourceProvider` | the directory you point at |
| `REMOTE_URL` | WebView network stack | the remote host |
| `INLINE_HTML` | `loadDataWithBaseURL` | `baseUrl` |

---

## 2. Loading a source

### 2.1 `web.loadSource`

| Parameter | Type | Required | Notes |
| --- | --- | --- | --- |
| `type` | string | **R** | `asset`, `file`, `url`, `html` |
| `location` | string | for asset/file/url | Path or URL |
| `html` | string | for `html` | Inline markup |
| `baseUrl` | string | O | Base for relative URLs |

```js
await Native.call('web.loadSource', { type: 'url', location: 'https://example.com/app/' });
await Native.call('web.loadSource', { type: 'asset', location: 'webapp/index.html' });
await Native.call('web.loadSource', { type: 'file', location: '/sdcard/app/index.html' });
await Native.call('web.loadSource', { type: 'html', html: '<h1>Hi</h1>', baseUrl: 'https://example.com/' });
```

### 2.2 `ui.setWebViewSource`

Same parameters, exposed on the UI route:

```js
await Native.call('ui.setWebViewSource', { type: 'asset', location: 'webapp/index.html' });
```

---

## 3. Path rules

`WebPaths.normalize()`:

| Rule | Effect |
| --- | --- |
| Backslashes → slashes | `css\style.css` → `css/style.css` |
| Repeated separators collapse | `a//b` → `a/b` |
| Leading/trailing separators trimmed | `/a/` → `a` |
| **No percent-decoding** | `%2e%2e` stays literal |

Decoding is the provider's job. Doing it twice is how `%252e%252e` becomes `..`.

### 3.1 Traversal

`FileResourceProvider` and the asset provider reject any resolved path that escapes the project root. `../../databases/app.db` returns 404 — it never reaches the filesystem.

### 3.2 Case sensitivity

Paths are **case-sensitive**. `Img/Logo.png` ≠ `img/logo.png`.

### 3.3 Absolute paths

`/img/logo.png` resolves against the virtual origin root, which is the project root. It is equivalent to `img/logo.png`.

---

## 4. ES modules

```html
<script type="module" src="js/app.js"></script>
```

```js
import { call } from './native.js';
import { render } from '../ui/render.js';
```

| Requirement | Detail |
| --- | --- |
| MIME type | The provider derives it from the extension; `.js` → `text/javascript` |
| Specifiers | Relative (`./`, `../`) or absolute (`/js/…`) |
| Bare specifiers | Need an import map |

```html
<script type="importmap">
{ "imports": { "lodash": "/vendor/lodash-es.js" } }
</script>
```

---

## 5. Offline resources

Everything under the project folder is served locally, so the app works offline by default.

`WebViewHost` deliberately **keeps** the HTTP cache on destroy — re-opening a project should not re-download remote assets.

```js
// Cache-first for local, network-first for remote
async function loadJSON(path) {
  const cached = localStorage.getItem('cache:' + path);
  if (cached) return JSON.parse(cached);
  const res = await fetch(path);
  const data = await res.json();
  localStorage.setItem('cache:' + path, JSON.stringify(data));
  return data;
}
```

---

## 6. `filemanager.*`

**Native class:** `com.roni.library.platform.filemanager.FileCommandHandler`
**Route:** `/filemanager` · **Requirement:** `PlatformEngine` initialised

### 6.1 Reading and writing

| Action | Parameters | Returns |
| --- | --- | --- |
| `filemanager.readFile` | `path` | file content |
| `filemanager.writeFile` | `path`, `base64` (O, `""`) | `{}` |
| `filemanager.createFile` | `path`, `content` (O, `""`) | file info |
| `filemanager.createFolder` | `path` | file info |
| `filemanager.getFileInfo` | `path` | file info map |
| `filemanager.listFiles` | `path` | file list |
| `filemanager.search` | `query` | file list |
| `filemanager.getRecent` | `limit` (O, `20`) | file list |

> ⚠ `writeFile` takes **base64**, not plain text. `createFile` takes plain `content`.

```js
// Write text safely (UTF-8)
function toBase64(text) {
  return btoa(unescape(encodeURIComponent(text)));
}

await Native.call('filemanager.writeFile', {
  path: 'notes/2026-11-20.txt',
  base64: toBase64('আজকের নোট')
});
```

### 6.2 Organising

| Action | Parameters | Returns |
| --- | --- | --- |
| `filemanager.copy` | `paths` (array), `targetDir` | operation |
| `filemanager.move` | `paths`, `targetDir` | operation |
| `filemanager.rename` | `path`, `newName` | file info |
| `filemanager.delete` | `paths` | operation |

### 6.3 Favourites

| Action | Parameters | Returns |
| --- | --- | --- |
| `filemanager.addFavorite` | `path` | `{}` |
| `filemanager.removeFavorite` | `path` | `{}` |
| `filemanager.getFavorites` | — | file list |

### 6.4 Archives

| Action | Parameters | Returns |
| --- | --- | --- |
| `filemanager.createArchive` | `paths`, `archivePath` | operation |
| `filemanager.extractArchive` | `path`, `targetDir`, `entries` (O) | operation |
| `filemanager.listArchive` | `path` | archive entries |

### 6.5 Storage and permissions

| Action | Parameters | Returns |
| --- | --- | --- |
| `filemanager.getStorageInfo` | — | storage info map |
| `filemanager.getStorageAnalysis` | — | analysis map |
| `filemanager.getCategories` | — | category map |
| `filemanager.getPermissions` | — | permission map |
| `filemanager.requestAccess` | — | permission map |
| `filemanager.pickDirectory` | — | directory picker result |
| `filemanager.getThumbnail` | `path` | thumbnail |

### 6.6 Import / export

| Action | Parameters | Returns |
| --- | --- | --- |
| `filemanager.import` | `treeId`, `documentId`, `targetDir` | import result |
| `filemanager.export` | `treeId`, `parentDocumentId`, `path` | export result |

### 6.7 Long operations

| Action | Parameters | Returns |
| --- | --- | --- |
| `filemanager.getOperationStatus` | `operationId` | operation status |
| `filemanager.cancelOperation` | `operationId` | cancellation result |

Copy, move, delete, archive and extract return an operation handle. Poll it:

```js
async function waitForOperation(id) {
  for (;;) {
    const status = await Native.call('filemanager.getOperationStatus', { operationId: id });
    if (status.state === 'COMPLETED' || status.state === 'FAILED' || status.state === 'CANCELLED') {
      return status;
    }
    await new Promise((r) => setTimeout(r, 300));
  }
}
```

---

## 7. Uploading a file

```html
<input type="file" id="picker">
```

```js
document.getElementById('picker').addEventListener('change', async function (event) {
  const file = event.target.files[0];
  if (!file) return;

  const bytes = new Uint8Array(await file.arrayBuffer());
  let binary = '';
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);

  await Native.call('filemanager.writeFile', {
    path: 'uploads/' + file.name,
    base64: btoa(binary)
  });
});
```

`<input type="file">` works because `WebViewHost` implements `onShowFileChooser` and forwards the result through `handleActivityResult`.

---

## 8. Saving to the public Downloads folder

```js
if (typeof window.RONiDownload !== 'undefined') {
  window.RONiDownload.saveBase64(base64, 'report.csv', 'text/csv');
  window.RONiDownload.download('https://example.com/file.zip', 'file.zip');
}
```

| Method | Signature | Behaviour |
| --- | --- | --- |
| `saveBase64` | `(base64, fileName, mime)` | Decodes and writes to Downloads |
| `download` | `(url, fileName)` | Enqueues a `DownloadManager` request |

Both are synchronous, return nothing, and have no error channel. Guard with `typeof`.

---

## 9. Limits

| Concern | Guidance |
| --- | --- |
| Bridge payload | 1 MiB per message. Chunk larger writes. |
| Base64 overhead | ~33 % larger than the raw bytes |
| `readFile` on a large file | Returns the whole content as a string — avoid for > 5 MB |
| Concurrent operations | Queue them; the engine serialises per directory |

```js
async function writeLargeFile(path, text, chunkSize = 512 * 1024) {
  const encoded = toBase64(text);
  const chunks = Math.ceil(encoded.length / chunkSize);
  for (let i = 0; i < chunks; i++) {
    await Native.call('filemanager.writeFile', {
      path: path + '.part' + i,
      base64: encoded.slice(i * chunkSize, (i + 1) * chunkSize)
    });
  }
  // then merge with filemanager.createArchive / move as appropriate
}
```

---

## 10. Related documents

- `ARCHITECTURE.md` §5 and §6 — source kinds and virtual origin
- `NATIVE_API_REFERENCE.md` §13 — full action table
- `NETWORK_AND_CONNECTIVITY.md` — saving HTTP responses
- `SECURITY_AND_PERMISSIONS.md` §3 — traversal defence
