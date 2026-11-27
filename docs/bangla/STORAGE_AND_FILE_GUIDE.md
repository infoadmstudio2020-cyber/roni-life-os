# Storage ও File API

## ১. তিনটি ভিন্ন storage — গুলিয়ে ফেলবেন না

| Storage | কোথায় থাকে | কে পড়তে পারে | টিকে থাকে |
|---|---|---|---|
| `storage.*` (native) | Android `EncryptedStore` (AES-GCM) | শুধু এই অ্যাপ | ✅ reload + restart |
| `localStorage` | WebView DOM storage | এই origin-এর পেজ | ✅ reload, ⚠️ clear data-তে মুছে যায় |
| `sessionStorage` | WebView memory | এই tab | ❌ reload-এ মুছে যায় |
| `IndexedDB` | WebView | এই origin | ✅ reload |

**সুপারিশ:** টোকেন, সেটিংস, ইউজার ডেটা → `storage.*`।
বড় ডেটা (ছবি, ক্যাশ) → `IndexedDB`।

---

## ২. `storage.*` API

### ২.১ `storage.set`

```js
await Native.call('storage.set', { key: 'theme', value: 'dark' });
// → { saved: true }
```

**সমর্থিত টাইপ:** `string`, `boolean`, `number` (int/long/float/double)।
`null`/`undefined` → খালি string হিসেবে সেভ হয়।

### ২.২ `storage.get`

```js
const r = await Native.call('storage.get', { key: 'theme' });
// → { key: 'theme', value: 'dark', exists: true }
```

**গুরুত্বপূর্ণ:** `exists:false` হলে `value` ফিল্ড নাও থাকতে পারে। সবসময়
`r.exists` চেক করুন, `r.value` সরাসরি ব্যবহার করবেন না।

### ২.৩ বাকি পাঁচটি

```js
await Native.call('storage.has',    { key: 'theme' });   // { exists: true }
await Native.call('storage.remove', { key: 'theme' });   // { removed: true }
await Native.call('storage.keys');                       // { keys: ['theme', ...] }
await Native.call('storage.getAll');                     // { theme: 'dark', ... }
await Native.call('storage.clear');                      // { cleared: true }
```

> `storage.getAll` সরাসরি key→value map দেয়, `{values:{…}}` নয়।

---

## ৩. Object/Array সেভ করা

`storage.set` শুধু primitive নেয়। Object-এর জন্য JSON:

```js
const Store = {
  async setJson(key, obj) {
    return Native.call('storage.set', { key: key, value: JSON.stringify(obj) });
  },
  async getJson(key, fallback) {
    try {
      const r = await Native.call('storage.get', { key: key });
      if (!r.exists) return fallback;
      return JSON.parse(r.value);
    } catch (e) {
      return fallback;
    }
  }
};

await Store.setJson('profile', { id: 7, name: 'রনি', tags: ['a', 'b'] });
const profile = await Store.getJson('profile', null);
```

**সতর্কতা:** ১ MiB-এর বড় JSON পাঠাবেন না (bridge limit)। বড় ডেটা `IndexedDB`-তে রাখুন।

---

## ৪. Key naming — Project prefix বাধ্যতামূলক

`storage.*` **অ্যাপ-ওয়াইড শেয়ার্ড**। দুইটি Project একই key ব্যবহার করলে একে অন্যের
ডেটা overwrite করবে।

```js
const PROJECT = 'notes';           // আপনার project slug
function k(name) { return PROJECT + ':' + name; }

await Native.call('storage.set', { key: k('draft'), value: '...' });
```

---

## ৫. Migration: পুরনো `localStorage` থেকে

```js
async function migrateFromLocalStorage() {
  const flag = await Native.call('storage.get', { key: 'migrated_v1' });
  if (flag.exists) return;

  const keys = ['theme', 'token', 'profile'];
  for (const key of keys) {
    const v = localStorage.getItem(key);
    if (v !== null) {
      await Native.call('storage.set', { key: key, value: v });
      localStorage.removeItem(key);
    }
  }
  await Native.call('storage.set', { key: 'migrated_v1', value: '1' });
}
```

**নোট:** native নিজেও একবার পুরনো `native_runtime_web_storage` SharedPreferences
migrate করে এবং `__migrated_v1` marker রাখে — সেটি আপনার JS ডেটার জন্য নয়।

---

## ৬. Storage নিরাপত্তা

| দিক | অবস্থা |
|---|---|
| এনক্রিপশন | AES-GCM, Android Keystore key |
| Project isolation | ❌ নেই — সব Project একই store দেখে |
| Size limit | Android SharedPreferences/File limit (কয়েক MB নিরাপদ) |
| Backup | `android:allowBackup` সেটিংসের উপর নির্ভর |
| Clear data | অ্যাপ ডেটা মুছলে সব চলে যায় |

**কখনো সেভ করবেন না:** পাসওয়ার্ড (plain), পূর্ণ credit card নম্বর, private key।

---

## ৭. File API — কী আছে, কী নেই

### ৭.১ যা আছে

| কাজ | কীভাবে |
|---|---|
| ফাইল **সেভ/ডাউনলোড** | `<a download>`, `RONiDownload.download()`, `RONiDownload.saveBase64()` |
| ফাইল **বেছে নেওয়া (upload)** | `<input type="file">` → Android picker |
| ফাইল **খোলা (অন্য অ্যাপে)** | `device.openFile({uri, mimeType})` |
| ফাইল **শেয়ার (text)** | `device.shareText` |

### ৭.২ যা নেই

| কাজ | অবস্থা |
|---|---|
| JS থেকে ফাইল পড়া (path দিয়ে) | ❌ নেই |
| JS থেকে ফাইল লেখা (path-এ) | ❌ নেই |
| Directory listing | ❌ নেই |
| File delete | ❌ নেই |
| File rename | ❌ নেই |
| Binary upload API | ❌ নেই (`fetch` + FormData ব্যবহার করুন) |

**কারণ:** `WebViewHost` ইচ্ছাকৃতভাবে `allowFileAccess=false` রাখে। Web App কখনো
সরাসরি ফাইলসিস্টেম দেখে না — শুধু user-chosen file (picker) ও download।

---

## ৮. Download — তিনটি উপায়

### ৮.১ সরল লিংক (সবচেয়ে নির্ভরযোগ্য)

```html
<a href="https://example.com/report.pdf" download="report.pdf">ডাউনলোড</a>
```

`WebViewHost.setDownloadListener` → `DownloadCenter.enqueue(...)` →
`DownloadManager` → `Download/RONiLiFEOS/report.pdf` + notification।

### ৮.২ JS দিয়ে শুরু

```js
function triggerDownload(url, filename) {
  const a = document.createElement('a');
  a.href = url;
  a.download = filename || '';
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}
```

### ৮.৩ Page-generated blob

```js
function downloadBlob(filename, blob) {
  const url = URL.createObjectURL(blob);
  triggerDownload(url, filename);
  setTimeout(function () { URL.revokeObjectURL(url); }, 5000);
}

downloadBlob('data.csv', new Blob([csvText], { type: 'text/csv;charset=utf-8' }));
```

`WebViewHost` `blob:` URL দেখলে `DownloadCenter.blobScript()` inject করে base64
পড়ে নেয় — তাই blob download কাজ করে।

### ৮.৪ `RONiDownload` (সরাসরি)

```js
// URL থেকে
window.RONiDownload.download('https://example.com/a.pdf', 'a.pdf');

// base64 থেকে (data URL নয়, শুধু base64 অংশ)
window.RONiDownload.saveBase64(base64String, 'photo.jpg', 'image/jpeg');
```

**সতর্কতা:** `saveBase64` main thread-এ ডিকোড করে। ৫ MB-এর বেশি base64 দিলে UI
ঝুলতে পারে। বড় ফাইল URL দিয়ে ডাউনলোড করুন।

---

## ৯. Upload — `<input type="file">`

```html
<input type="file" id="picker" accept="image/*" multiple>
<script>
  document.getElementById('picker').addEventListener('change', async function (e) {
    const files = Array.from(e.target.files);
    for (const file of files) {
      const form = new FormData();
      form.append('file', file);
      const res = await fetch('https://api.example.com/upload', {
        method: 'POST',
        body: form
      });
      console.log(file.name, res.status);
    }
  });
</script>
```

**শর্ত:** `WebHostConfig.fileChooserEnabled = true` (ডিফল্ট)।
`WebViewHost.onShowFileChooser` → `ActivityResultLauncher` → `onActivityResult` →
`ValueCallback<Uri[]>.onReceiveValue(...)`।

**সীমা:** একবারে একটি picker request; multiple file সাপোর্টেড।

---

## ১০. `device.openFile`

```js
await Native.call('device.openFile', {
  uri: 'content://com.example.provider/file/123',
  mimeType: 'application/pdf'
});
```

`Intent.ACTION_VIEW` + `FLAG_GRANT_READ_URI_PERMISSION`। `content://` ছাড়া
`file://` URI দিলে Android 7+ এ `FileUriExposedException` হতে পারে — তাই
`content://` ব্যবহার করুন।

---

## ১১. Storage debug

```js
async function dumpStorage() {
  const all = await Native.call('storage.getAll');
  console.table(all);
  const { keys } = await Native.call('storage.keys');
  console.log('মোট key:', keys.length);
}
```

---

## ১২. চেকলিস্ট

- [ ] `localStorage`-এর বদলে `storage.*`
- [ ] key-তে project prefix
- [ ] `r.exists` চেক করেছি
- [ ] Object → `JSON.stringify`
- [ ] ১ MiB-এর নিচে payload
- [ ] download-এ `revokeObjectURL` দেরিতে
- [ ] upload-এ `<input type="file">`
- [ ] পাসওয়ার্ড plain সেভ করিনি
