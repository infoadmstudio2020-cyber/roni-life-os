# Download ও Network API

## ১. Download pipeline

```
<a download> / RONiDownload.download() / blob:
        │
        ▼
WebViewHost.setDownloadListener
        │
        ├─ blob: → DownloadCenter.blobScript() inject → base64 → saveBase64
        ├─ data: → DownloadCenter.saveDataUrl()
        └─ http(s): → DownloadCenter.enqueue()
                          │
                          ▼
                  DownloadManager.enqueue(Request)
                          │
                          ▼
              Download/RONiLiFEOS/<filename>
                  + notification (POST_NOTIFICATIONS)
```

**ফোল্ডার:** `Download/RONiLiFEOS` (public Downloads-এর ভেতরে)।

---

## ২. তিনটি উপায়

### ২.১ `<a download>` — সবচেয়ে নির্ভরযোগ্য

```html
<a href="https://example.com/report.pdf" download="report.pdf">রিপোর্ট</a>
```

`download` attribute না দিলেও চলে — `Content-Disposition` বা URL থেকে নাম অনুমান হয়।

### ২.২ JS দিয়ে

```js
function download(url, filename) {
  const a = document.createElement('a');
  a.href = url;
  a.download = filename || '';
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}
```

### ২.৩ `window.RONiDownload`

```js
// URL
window.RONiDownload.download('https://example.com/a.pdf', 'a.pdf');

// base64 (data URL prefix ছাড়া)
window.RONiDownload.saveBase64(b64, 'photo.jpg', 'image/jpeg');
```

**কখন `RONiDownload` দরকার:** যখন `<a>` click কাজ করে না (যেমন JS-এ তৈরি লিংক
যা DOM-এ নেই, বা custom scheme)।

---

## ৩. Page-generated ফাইল (blob)

```js
function downloadText(filename, text, mime) {
  const blob = new Blob([text], { type: mime || 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(function () { URL.revokeObjectURL(url); }, 5000);
}

downloadText('notes.csv', 'id,title\n1,প্রথম\n2,দ্বিতীয়', 'text/csv;charset=utf-8');
```

**কেন কাজ করে:** `WebViewHost` `blob:` URL দেখলে `DownloadCenter.blobScript(url, name, mime)`
inject করে, যা `FileReader` দিয়ে base64 বানিয়ে `RONiDownload.saveBase64()`-এ পাঠায়।

**সীমা:** বড় blob (১০ MB+) base64 করতে গিয়ে মেমরি চাপে পড়তে পারে।

---

## ৪. CSV/JSON export প্যাটার্ন

```js
function toCsv(rows, headers) {
  const esc = function (v) {
    const s = String(v == null ? '' : v);
    return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  };
  const lines = [headers.map(esc).join(',')];
  rows.forEach(function (r) {
    lines.push(headers.map(function (h) { return esc(r[h]); }).join(','));
  });
  return '\uFEFF' + lines.join('\r\n');   // BOM → Excel-এ বাংলা ঠিক দেখাবে
}

downloadText('report.csv', toCsv(rows, ['id', 'title', 'amount']), 'text/csv;charset=utf-8');
```

**`\uFEFF` (BOM) কেন:** Excel UTF-8 না ধরে ANSI ধরে নেয়, ফলে বাংলা ভাঙে। BOM দিলে ঠিক হয়।

---

## ৫. Network — `fetch`

Web App-এ নেটওয়ার্কের জন্য **কোনো native API নেই** — সাধারণ `fetch` ব্যবহার করুন।

```js
async function api(path, options) {
  const res = await fetch('https://api.example.com' + path, Object.assign({
    headers: { 'Content-Type': 'application/json' }
  }, options || {}));

  if (!res.ok) {
    throw new Error('HTTP_' + res.status);
  }
  return res.json();
}
```

### ৫.১ Timeout

`fetch`-এ built-in timeout নেই:

```js
function fetchWithTimeout(url, options, ms) {
  const controller = new AbortController();
  const timer = setTimeout(function () { controller.abort(); }, ms || 15000);
  return fetch(url, Object.assign({}, options, { signal: controller.signal }))
    .finally(function () { clearTimeout(timer); });
}
```

### ৫.২ Retry

```js
async function fetchRetry(url, options, attempts) {
  let lastErr;
  for (let i = 0; i < (attempts || 3); i++) {
    try {
      return await fetchWithTimeout(url, options, 15000);
    } catch (e) {
      lastErr = e;
      await new Promise(function (r) { setTimeout(r, 400 * Math.pow(2, i)); });
    }
  }
  throw lastErr;
}
```

---

## ৬. Network state

```js
async function networkState() {
  try {
    const n = await Native.call('device.getNetworkState');
    return { connected: !!n.connected, type: n.type, metered: !!n.metered };
  } catch (e) {
    return { connected: navigator.onLine, type: 'unknown', metered: false };
  }
}
```

**`type` মান:** `wifi`, `mobile`, `ethernet`, `none`, `unknown`

**কোনো live listener নেই** — polling করুন:

```js
let lastConnected = null;
setInterval(async function () {
  const s = await networkState();
  if (s.connected !== lastConnected) {
    lastConnected = s.connected;
    document.body.classList.toggle('offline', !s.connected);
    if (s.connected) flushQueue();
  }
}, 8000);
```

---

## ৭. Offline queue প্যাটার্ন

```js
const QUEUE_KEY = 'app:pending';

async function enqueue(req) {
  const q = await Store.getJson(QUEUE_KEY, []);
  q.push(req);
  await Store.setJson(QUEUE_KEY, q);
}

async function flushQueue() {
  const q = await Store.getJson(QUEUE_KEY, []);
  if (!q.length) return;
  const remaining = [];
  for (const req of q) {
    try {
      await api(req.path, req.options);
    } catch (e) {
      remaining.push(req);
    }
  }
  await Store.setJson(QUEUE_KEY, remaining);
}
```

---

## ৮. Mixed content ও CORS

| সমস্যা | কারণ | সমাধান |
|---|---|---|
| `Mixed Content blocked` | HTTPS পেজ থেকে HTTP API | API-কে HTTPS করুন |
| `CORS error` | সার্ভারে header নেই | সার্ভারে `Access-Control-Allow-Origin` |
| `net::ERR_CLEARTEXT_NOT_PERMITTED` | Android 9+ cleartext ব্লক | HTTPS, বা `network_security_config` |
| Cookie পাঠায় না | `credentials` নেই | `fetch(url, { credentials: 'include' })` |

`WebHostConfig.mixedContentAllowed = false` (ডিফল্ট) — তাই HTTP একদম ব্লক।

---

## ৯. Cookie

```js
await Native.call('web.setCookie', {
  url: 'https://example.com',
  value: 'session=abc123; Path=/; Secure'
});

const { cookies } = await Native.call('web.getCookies', { url: 'https://example.com' });
await Native.call('web.clearCookies');
```

**সতর্কতা:** `web.setCookie` `CookieManager.setCookie()` ব্যবহার করে — এটি
asynchronous। সাথে সাথে `getCookies` ডাকলে পুরনো মান আসতে পারে।

---

## ১০. Cache

```js
await Native.call('web.setCacheMode', { mode: 'no_cache' });   // সবসময় নেটওয়ার্ক
await Native.call('web.clearCache');
```

| `mode` | অর্থ |
|---|---|
| `default` | ব্রাউজার সিদ্ধান্ত নেয় |
| `cache` | cache থাকলে ব্যবহার, নাহলে নেটওয়ার্ক |
| `no_cache` | সবসময় নেটওয়ার্ক |
| `cache_only` | শুধু cache (অফলাইন মোড) |

---

## ১১. Upload

```js
const form = new FormData();
form.append('file', fileInput.files[0]);
form.append('meta', JSON.stringify({ title: 'রিপোর্ট' }));

const res = await fetch('https://api.example.com/upload', {
  method: 'POST',
  body: form          // Content-Type নিজে সেট করবেন না — boundary নষ্ট হবে
});
```

**সাধারণ ভুল:** `headers: { 'Content-Type': 'multipart/form-data' }` দিলে boundary
যায় না → সার্ভার parse করতে পারে না। `FormData` দিলে header নিজে সেট হতে দিন।

---

## ১২. Progress

`fetch`-এ upload/download progress নেই। `XMLHttpRequest` ব্যবহার করুন:

```js
function uploadWithProgress(url, formData, onProgress) {
  return new Promise(function (resolve, reject) {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', url);
    xhr.upload.onprogress = function (e) {
      if (e.lengthComputable) onProgress(e.loaded / e.total);
    };
    xhr.onload = function () {
      if (xhr.status >= 200 && xhr.status < 300) resolve(xhr.responseText);
      else reject(new Error('HTTP_' + xhr.status));
    };
    xhr.onerror = function () { reject(new Error('NETWORK_ERROR')); };
    xhr.send(formData);
  });
}
```

---

## ১৩. চেকলিস্ট

- [ ] download-এ `revokeObjectURL` দেরিতে
- [ ] CSV-তে BOM (`\uFEFF`)
- [ ] `fetch`-এ timeout (`AbortController`)
- [ ] network state polling (listener নেই)
- [ ] offline queue
- [ ] HTTPS only (mixed content ব্লক)
- [ ] `FormData`-এ Content-Type নিজে সেট করিনি
- [ ] বড় blob-এ base64 এড়িয়েছি
