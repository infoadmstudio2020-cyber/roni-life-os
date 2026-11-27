# Working Examples — ১৪টি ডেমো + একটি পূর্ণ অ্যাপ

সব উদাহরণ `examples/` ফোল্ডারে আছে এবং **সত্যিই চলার মতো** লেখা — কোনো pseudo-code নয়।

---

## ১. ফোল্ডার গঠন

```
examples/
├── bangla-demos/            ← ১৪টি আলাদা ডেমো + hub
│   ├── index.html           ← তালিকা (এখান থেকে শুরু)
│   ├── native.js            ← shared bridge wrapper
│   ├── demo.css             ← shared style
│   ├── 01-toast.html
│   ├── 02-dialog.html
│   ├── 03-sheet.html
│   ├── 04-loading.html
│   ├── 05-toolbar.html
│   ├── 06-drawer-tabs.html
│   ├── 07-system-ui.html
│   ├── 08-device-info.html
│   ├── 09-battery-network.html
│   ├── 10-clipboard-share.html
│   ├── 11-storage.html
│   ├── 12-download.html
│   ├── 13-navigation.html
│   └── 14-upload.html
└── bangla-notes-app/        ← পূর্ণ অ্যাপ
    ├── index.html
    ├── manifest.json
    ├── app.css
    ├── native.js
    ├── store.js
    ├── router.js
    └── app.js
```

---

## ২. ডেমো তালিকা

| # | ফাইল | API | কী শেখায় |
|---|---|---|---|
| ০১ | `01-toast.html` | `ui.showToast`, `ui.dismissToast`, `ui.isToastVisible` | চার ধরনের বার্তা, duration |
| ০২ | `02-dialog.html` | `ui.showAlert`, `ui.showConfirm`, `ui.isDialogShowing`, `ui.dismissDialog` | দুই-ধাপ confirm প্যাটার্ন |
| ০৩ | `03-sheet.html` | `ui.showBottomSheet`, `ui.showActionSheet`, `ui.showPopupMenu` | তিন ধরনের মেনু, ইভেন্ট |
| ০৪ | `04-loading.html` | `ui.showLoading`, `ui.hideLoading`, `ui.isLoading` | `try/finally` প্যাটার্ন |
| ০৫ | `05-toolbar.html` | `ui.setToolbarTitle`, `addToolbarAction`, … | action বাটন ইভেন্ট |
| ০৬ | `06-drawer-tabs.html` | `ui.openDrawer`, `addDrawerItem`, `addTab`, `setBadge`, `setFabVisible` | নেভিগেশন shell |
| ০৭ | `07-system-ui.html` | `ui.setStatusBar`, `setFullscreen`, `getTheme`, `getConfig` | system UI ও theme |
| ০৮ | `08-device-info.html` | `device.getInfo`, `getAppInfo`, `getScreenInfo`, `getSafeArea` | safe area প্রয়োগ |
| ০৯ | `09-battery-network.html` | `device.getBattery`, `device.getNetworkState` | polling (listener নেই) |
| ১০ | `10-clipboard-share.html` | `device.copyToClipboard`, `readClipboard`, `shareText`, `vibrate` | clipboard + share |
| ১১ | `11-storage.html` | `storage.set/get/has/remove/keys/getAll/clear` | JSON, persistence |
| ১২ | `12-download.html` | `<a download>`, blob, `RONiDownload` | CSV + BOM |
| ১৩ | `13-navigation.html` | `web.getState`, `goBack`, `goForward`, `reload`, `device.openUrl` | hash routing |
| ১৪ | `14-upload.html` | `<input type="file">`, `FileReader` | picker, preview |

---

## ৩. প্রতিটি ডেমোর গঠন

সব ডেমো একই কাঠামো মানে:

```html
<header>
  <h1>শিরোনাম</h1>
  <p>ব্যাখ্যা <span id="bridge-state" class="badge">…</span></p>
</header>
<main>
  <a class="back" href="index.html">← তালিকায় ফিরুন</a>
  <div class="card">…নিয়ন্ত্রণ…</div>
  <div class="card"><div class="log" id="log"></div></div>
</main>
<script src="native.js"></script>
<script>/* ডেমো লজিক */</script>
```

**`bridge-state` badge** স্বয়ংক্রিয়ভাবে দেখায় bridge আছে কি নেই।
**`log`** প্রতিটি কলের response/error দেখায় — ডিবাগিং সহজ।

---

## ৪. `native.js` — shared wrapper

```js
Native.available()          // bridge আছে?
Native.call(action, payload) // Promise, 12s timeout
Native.on(topic, fn)        // event
Native.off(topic, fn)
Native.toast(msg, type)     // fire-and-forget
Native.log(elId, label, v)  // লগ প্যানেলে লেখে
Native.guard(fn)            // guard + try/catch
```

**মূল কোড:**

```js
function call(action, payload) {
  if (!available()) return Promise.reject(new Error('NATIVE_UNAVAILABLE'));
  return new Promise(function (resolve, reject) {
    var done = false;
    var timer = setTimeout(function () {
      if (!done) { done = true; reject(new Error('CLIENT_TIMEOUT')); }
    }, TIMEOUT);

    NativeRuntimeBridgeClient.call(action, payload || {}).then(function (r) {
      if (!done) { done = true; clearTimeout(timer); resolve(r); }
    }).catch(function (e) {
      if (!done) { done = true; clearTimeout(timer); reject(e); }
    });
  });
}
```

**কেন `done` flag:** timeout-এর পরে response এলে সেটি উপেক্ষা করতে।

---

## ৫. পূর্ণ অ্যাপ — নোটখাতা

### ৫.১ কী আছে

| ফিচার | API |
|---|---|
| নোট তৈরি/মুছে ফেলা | `storage.*` |
| থিম (light/dark) | `storage.*` + CSS variable |
| CSV এক্সপোর্ট (বাংলা BOM) | blob download |
| ডিভাইস তথ্য | `device.getInfo` |
| Safe area | `device.getSafeArea` |
| Offline banner | `device.getNetworkState` polling |
| Drawer নেভিগেশন | `ui.addDrawerItem` + `ui.drawerItemSelected` |
| Toolbar sync | `ui.setToolbarTitle` |
| Confirm dialog | `ui.showConfirm` + `ui.confirm` |
| Loading | `ui.showLoading` / `hideLoading` |
| Toast | `ui.showToast` |
| Hash routing | নিজের `router.js` |

### ৫.২ ফাইল দায়িত্ব

| ফাইল | দায়িত্ব |
|---|---|
| `native.js` | bridge wrapper + `confirm()` + `loading()` helper |
| `store.js` | `storage.*` + `localStorage` fallback + prefix `notes:` |
| `router.js` | hash router, `data-route` click delegation |
| `app.js` | তিনটি view + export + network + drawer |
| `app.css` | theme variable, safe area, tabbar, offline banner |

### ৫.৩ `Native.confirm` — Promise wrapper

```js
function confirm(title, message) {
  return new Promise(function (resolve) {
    if (!available()) { resolve(window.confirm(message)); return; }
    var settled = false;
    function handler(p) {
      if (settled) return;
      settled = true;
      off('ui.confirm', handler);
      resolve(!!p.confirmed);
    }
    on('ui.confirm', handler);
    call('ui.showConfirm', { title: title, message: message })
      .catch(function () { if (!settled) { settled = true; resolve(false); } });
  });
}
```

**তিনটি সুরক্ষা:** `settled` flag (double-resolve নয়), `off()` (leak নয়),
`catch` (bridge ব্যর্থ হলেও resolve)।

### ৫.৪ `Native.loading` — try/finally ছাড়াই

```js
var loader = Native.loading('সেভ হচ্ছে…');
loader.show().then(function () {
  return saveNotes(list);
}).then(function (ok) {
  return loader.hide().then(function () { Native.toast('সেভ হয়েছে', 'success'); });
}).catch(function (e) {
  loader.hide();
  Native.toast('ব্যর্থ: ' + e.message, 'error');
});
```

`hide()` নিজেই জানে `show()` সফল হয়েছিল কি না — তাই নিরাপদে সবসময় ডাকা যায়।

### ৫.৫ CSV export

```js
var csv = '\uFEFF' + lines.join('\r\n');
var blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
var url = URL.createObjectURL(blob);
var a = document.createElement('a');
a.href = url;
a.download = 'notes-' + Date.now() + '.csv';
document.body.appendChild(a);
a.click();
document.body.removeChild(a);
setTimeout(function () { URL.revokeObjectURL(url); }, 5000);
```

**`\uFEFF`** — Excel-এ বাংলা ঠিক দেখানোর জন্য BOM।
**`setTimeout` revoke** — download শুরু হওয়ার আগে URL মরে যাওয়া রোধ।

---

## ৬. কীভাবে চালাবেন

### ৬.১ RONiLiFEOS-এ

1. `examples/bangla-demos/` ফোল্ডারটি একটি Project হিসেবে import করুন
   (`manifest.json` নেই — তাই সরাসরি ফোল্ডার/zip দিলে `index.html` entry ধরুন)
2. অথবা `examples/bangla-notes-app/` import করুন (`manifest.json` আছে)
3. অ্যাপে Project খুলুন → `index.html` লোড হবে

### ৬.২ ব্রাউজারে (fallback পরীক্ষা)

```bash
cd examples/bangla-demos
python3 -m http.server 8080
# http://localhost:8080
```

সব native কল `NATIVE_UNAVAILABLE` দেবে — এটাই প্রত্যাশিত। লক্ষ্য: **UI ভাঙে না**।

---

## ৭. যাচাই ফলাফল

| চেক | ফল |
|---|---|
| `node --check` — ৫টি standalone JS | ✅ পাস |
| `node --check` — সব inline `<script>` | ✅ পাস |
| `native.js` bridge smoke test (১৮ assertion) | ✅ পাস |
| HTML well-formed | ✅ |
| বাংলা UTF-8 | ✅ |

---

## ৮. ডেমো থেকে কোড কপি করার নিয়ম

1. `native.js` কপি করুন (অথবা নিজের wrapper বানান)
2. প্রাসঙ্গিক `<script>` ব্লক কপি করুন
3. `Native.log(...)` লাইনগুলো সরান (ডেমোর জন্য, প্রোডাকশনে দরকার নেই)
4. `demo.css`-এর বদলে নিজের CSS ব্যবহার করুন
5. `bridge-state` badge সরান

---

## ৯. আরও দেখুন

- `docs/bangla/WEB_APP_DEVELOPMENT_GUIDE.md` — শূন্য থেকে অ্যাপ
- `docs/bangla/JAVASCRIPT_INTEGRATION.md` — সব action
- `examples/webapp-starter/` — মূল রিপোর starter (ইংরেজি)

---

## ১৫. `examples/webapp-starter/` — ES6 module starter

নতুন অ্যাপ শুরু করার জন্য প্রস্তাবিত টেমপ্লেট। ১০টি ফাইল, ES module, কোনো build step নেই।

```
webapp-starter/
├── index.html
├── manifest.json
├── styles/{main,responsive}.css
└── scripts/
    ├── app.js
    ├── native-runtime.js
    └── modules/{device,storage,ui}.js
```

| ফিচার | কীভাবে |
|---|---|
| availability detection | `available()` — bridge না থাকলে badge বদলায় |
| promise + timeout | `call()` — ১২s timeout, `CLIENT_TIMEOUT` |
| event on/off | `on('ui.confirm', fn)` / `off(...)` |
| confirm | `ui.showConfirm` + `ui.confirm` ইভেন্ট, listener cleanup সহ |
| loading | `loading()` — show/hide idempotent |
| storage fallback | bridge না থাকলে `localStorage` |
| error handling | প্রতিটি মডিউলে try/catch, UI-তে log |

**যাচাই:** `tools/verify/starter_test.mjs` — ১৯টি assertion, native ও browser দুই মোডে।
