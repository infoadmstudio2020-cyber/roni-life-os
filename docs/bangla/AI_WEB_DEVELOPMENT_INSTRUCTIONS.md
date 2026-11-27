# AI Web Development Instructions — বাধ্যতামূলক নির্দেশনা

> এই ফাইলটি ভবিষ্যৎ AI এজেন্টের জন্য। RONiLiFEOS-এর জন্য Web App লিখতে গেলে
> এই নিয়মগুলো **অবশ্যই** মানতে হবে। এগুলো সোর্স কোড অডিট থেকে নেওয়া, অনুমান নয়।

---

## ১. পরিবেশ সম্পর্কে যা জানতে হবে

| তথ্য | মান |
|---|---|
| Host | Android WebView |
| Bridge object | `window.NativeRuntimeBridge` (native) |
| JS client | `window.NativeRuntimeBridgeClient` (injected) |
| Inject timing | `onPageFinished` |
| Origin | `https://<slug>-<hash8>.runtime.local` |
| Message limit | 1,048,576 bytes |
| Timeout | 10,000 ms |
| Storage | AES-GCM `EncryptedStore` |
| Download folder | `Download/RONiLiFEOS` |

---

## ২. নিষিদ্ধ (কখনো লিখবেন না)

| ❌ নিষিদ্ধ | ✅ এর বদলে |
|---|---|
| `localStorage.setItem` | `Native.call('storage.set', …)` |
| `sessionStorage` | in-memory variable |
| `alert()` | `ui.showAlert` |
| `confirm()` | `ui.showConfirm` + `on('ui.confirm')` |
| `prompt()` | নিজের modal |
| `window.open()` | `device.openUrl` |
| `NativeRuntimeBridgeClient.call()` guard ছাড়া | `if (Native.available())` |
| `NativeRuntime.ui.showToast()` | `Native.call('ui.showToast', …)` |
| `NativeRuntime.storage.get()` | `Native.call('storage.get', …)` |
| `theme.getTheme` | `ui.getTheme` |
| `network.getState` | `device.getNetworkState` |
| `platform.getInfo` | `device.getInfo` |
| `security.*` | (কোনো JS API নেই) |
| `analytics.*` | (কোনো JS API নেই) |
| `devtools.*` | (কোনো JS API নেই) |
| `app.pause` ইভেন্ট | `document.visibilitychange` |
| `page.loaded` ইভেন্ট | `window.onload` |
| `network.changed` ইভেন্ট | polling `device.getNetworkState` |
| `battery.changed` ইভেন্ট | polling `device.getBattery` |
| `ui.showConfirm`-এর return-এ `confirmed` | `on('ui.confirm')` |
| `history.pushState` path routing | hash routing (`#/path`) |
| `100vh` | `100dvh` বা flex |
| `innerHTML = userInput` | `textContent` বা escape |
| ১ MiB+ JSON | ছোট টুকরো / IndexedDB |
| `Content-Type: multipart/form-data` হাতে | `FormData` |
| `URL.revokeObjectURL` সাথে সাথে | ৫ সেকেন্ড পরে |

---

## ৩. বাধ্যতামূলক (সবসময়)

1. **Bridge guard**
   ```js
   function hasNative() {
     return typeof NativeRuntimeBridgeClient !== 'undefined'
         && typeof NativeRuntimeBridgeClient.call === 'function';
   }
   ```

2. **প্রতিটি `call()`-এ `.catch()`** — কোনো ব্যতিক্রম নেই।

3. **`storage.*`-এ project prefix**
   ```js
   const PREFIX = 'myapp:';
   ```

4. **`viewport-fit=cover`** + safe area CSS
   ```html
   <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
   ```

5. **Touch target ≥ 44px**, input `font-size: 16px`

6. **Hash routing** (`#/path`) — back button কাজ করার জন্য

7. **Toolbar title sync** প্রতিটি route-এ
   ```js
   Native.call('ui.setToolbarTitle', { title: 'নোট' }).catch(() => {});
   ```

8. **`try/finally`** — `showLoading` → `hideLoading`

9. **`on()` করার পর `off()`** — listener leak প্রতিরোধ

10. **বাংলা ফন্ট stack**
    ```css
    font-family: "Noto Sans Bengali", "Hind Siliguri", -apple-system, sans-serif;
    ```

11. **CSV-তে BOM** (`\uFEFF`)

12. **`fetch`-এ timeout** (`AbortController`)

13. **ব্রাউজার fallback** — প্রতিটি native কলের বিকল্প

14. **`lang="bn"`** `<html>`-এ

15. **`<meta charset="UTF-8">`** — বাংলার জন্য অপরিহার্য

---

## ৪. API নামের সঠিক রূপ

শুধু এই namespace ব্যবহার করুন:

```
ui.*       (৬০)
web.*      (৫৩)
device.*   (২৮)
storage.*  (৭)
runtime.*  (৬)   ← সাধারণত দরকার নেই
```

**সবসময় ডট ফর্ম:** `ui.showToast` — কখনো `showToast` বা `/ui/showToast` নয়।

---

## ৫. Response key — অনুমান করবেন না

| Action | Response |
|---|---|
| `ui.showToast` | `{shown:true}` |
| `ui.showConfirm` | `{shown:true}` (+ ইভেন্ট) |
| `ui.showBottomSheet` | `{shown:true}` (+ ইভেন্ট) |
| `ui.isDialogShowing` | `{showing:bool}` |
| `ui.getTheme` | `{themeMode, primaryColor, …}` |
| `ui.getConfig` | ২৩টি key |
| `web.getState` | `{url, canGoBack, canGoForward, title, progress}` |
| `device.getInfo` | app + device + screen + locale + safeArea |
| `device.getBattery` | `{level, percent, charging, …}` |
| `storage.get` | `{key, value, exists}` |
| `storage.getAll` | `{key:value, …}` |

**নিয়ম:** যে key ডকুমেন্টে নেই, সেটি ধরে নেবেন না। `console.log` করে দেখুন।

---

## ৬. কোড লেখার ক্রম

```
1. index.html (viewport, charset, lang)
2. css/app.css (variables, safe area, dark mode)
3. js/native.js (wrapper)
4. js/store.js (storage helper)
5. js/router.js (hash router)
6. js/app.js (routes + init)
7. manifest.json
```

---

## ৭. প্রতিটি ফিচারের জন্য প্যাটার্ন

### Toast
```js
Native.call('ui.showToast', { message: 'সেভ হয়েছে', type: 'success' }).catch(() => {});
```

### Confirm
```js
Native.on('ui.confirm', p => { if (p.confirmed) doIt(); });
Native.call('ui.showConfirm', { title: 'নিশ্চিত?', message: '…' }).catch(() => {});
```

### Loading
```js
await Native.call('ui.showLoading', { text: '…' }).catch(() => {});
try { await work(); } finally { await Native.call('ui.hideLoading').catch(() => {}); }
```

### Storage
```js
await Native.call('storage.set', { key: PREFIX + 'k', value: v });
const r = await Native.call('storage.get', { key: PREFIX + 'k' });
const v = r.exists ? r.value : fallback;
```

### Download
```js
const a = document.createElement('a');
a.href = url; a.download = name;
document.body.appendChild(a); a.click(); document.body.removeChild(a);
```

### Network
```js
const n = await Native.call('device.getNetworkState').catch(() => ({ connected: navigator.onLine }));
```

---

## ৮. CSS বাধ্যতামূলক নিয়ম

```css
:root {
  --safe-top: env(safe-area-inset-top, 0px);
  --safe-bottom: env(safe-area-inset-bottom, 0px);
}
* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
html, body { margin: 0; overscroll-behavior-y: contain; }
button { min-height: 44px; }
input, textarea, select { font-size: 16px; }
```

**নিষিদ্ধ:** `position: fixed` + `100vh` একসাথে (keyboard খুললে ভাঙে)।

---

## ৯. Error handling বাধ্যতামূলক

```js
const ERRORS = {
  NATIVE_UNAVAILABLE: 'নেটিভ সাপোর্ট নেই',
  ROUTE_NOT_FOUND: 'এই ফিচার এই ভার্সনে নেই',
  ORIGIN_REJECTED: 'নিরাপত্তা ব্লক',
  REQUEST_TIMEOUT: 'সাড়া পাওয়া যায়নি',
  PAYLOAD_TOO_LARGE: 'ডেটা বেশি বড়',
  STORAGE_UNAVAILABLE: 'স্টোরেজ সমস্যা',
  CLIENT_TIMEOUT: 'সময় শেষ'
};

function humanError(e) {
  return ERRORS[e.message] || 'অজানা সমস্যা';
}
```

---

## ১০. যা লিখবেন না (হ্যালুসিনেশন)

| ভুল দাবি | সত্য |
|---|---|
| `NativeRuntime.ui.showToast()` | এমন API নেই |
| `NativeRuntime.session.getToken()` | নেই |
| `NativeRuntime.app.exit()` | `device.finish` |
| `NativeRuntime.events.on()` | `NativeRuntimeBridgeClient.on` |
| `NativeRuntime.network.isOnline()` | `device.getNetworkState` |
| `NativeRuntime.download.file()` | `RONiDownload.download` |
| `NativeRuntime.navigation.push()` | `web.loadUrl` / hash router |
| `NativeRuntime.device.getIMEI()` | নেই (এবং কখনো হবে না) |
| `NativeRuntime.storage.secure()` | `storage.*` ইতিমধ্যে এনক্রিপ্টেড |
| `theme.apply()` | `ui.applyTheme` (CSS বদলায় না) |
| `ui.showConfirm` → `{confirmed}` | `{shown}` + ইভেন্ট |
| `ui.setFabIcon()` | নেই |
| `ui.onTabSelected()` | নেই (polling) |
| `device.vibrate(500)` | parameter নেই |
| `device.shareFile()` | নেই (শুধু text) |
| `web.upload()` | নেই (`<input type="file">`) |
| `storage.setObject()` | নেই (`JSON.stringify`) |

---

## ১১. Self-check (কোড জমা দেওয়ার আগে)

- [ ] সব `call()` guard + `.catch()`
- [ ] `localStorage`/`alert`/`confirm`/`window.open` নেই
- [ ] `storage.*` key-তে prefix
- [ ] hash routing
- [ ] toolbar title sync
- [ ] safe area CSS
- [ ] 44px touch target
- [ ] 16px input font
- [ ] dark mode
- [ ] বাংলা ফন্ট
- [ ] `lang="bn"`, `charset=UTF-8`
- [ ] ব্রাউজার fallback
- [ ] `showLoading` → `hideLoading` (finally)
- [ ] `on()` → `off()`
- [ ] CSV-তে BOM
- [ ] `fetch` timeout
- [ ] কোনো হ্যালুসিনেটেড API নেই

---

## ১২. টেস্ট মোড

```js
const DEV = location.search.indexOf('dev=1') !== -1;

if (DEV) {
  ['ui.confirm','ui.actionSelected','ui.popupItemSelected','ui.snackbarAction',
   'ui.toolbarAction','ui.drawerItemSelected'].forEach(t => {
    Native.on(t, p => console.log('[event]', t, p));
  });
}
```

---

## ১৩. সংক্ষেপে

> **guard → call → catch → fallback**
>
> **storage নয় localStorage · hash নয় pushState · device.openUrl নয় window.open**
>
> **ইভেন্টে উত্তর, Promise-এ নয় (confirm/sheet/menu)**
>
> **অনুমান নয় — ডকুমেন্ট**
