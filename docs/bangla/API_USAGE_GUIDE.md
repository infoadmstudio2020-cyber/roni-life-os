# API Usage Guide — ধাপে ধাপে

## ১. প্রথম ধাপ: bridge আছে কি না জানুন

```js
function hasNative() {
  return typeof NativeRuntimeBridgeClient !== 'undefined'
      && typeof NativeRuntimeBridgeClient.call === 'function';
}
```

**কেন:** `NativeRuntimeBridgeClient` `onPageFinished`-এ inject হয়। পেজের প্রথম script
চলার সময় এটি নাও থাকতে পারে। ব্রাউজারে টেস্ট করলে কখনোই থাকবে না।

---

## ২. একটি নিরাপদ wrapper বানান

```js
const Native = (function () {
  const TIMEOUT_MS = 12000;

  function available() {
    return typeof NativeRuntimeBridgeClient !== 'undefined'
        && typeof NativeRuntimeBridgeClient.call === 'function';
  }

  function call(action, payload) {
    if (!available()) {
      return Promise.reject(new Error('NATIVE_UNAVAILABLE'));
    }
    return Promise.race([
      NativeRuntimeBridgeClient.call(action, payload || {}),
      new Promise(function (_, reject) {
        setTimeout(function () { reject(new Error('CLIENT_TIMEOUT')); }, TIMEOUT_MS);
      })
    ]);
  }

  function on(topic, fn) {
    if (available()) NativeRuntimeBridgeClient.on(topic, fn);
  }

  return { available: available, call: call, on: on };
})();
```

**কেন `Promise.race`:** bridge-এর নিজের timeout ১০ সেকেন্ড, কিন্তু native main thread
আটকে গেলে উত্তর কখনো নাও আসতে পারে। নিজের timeout রাখলে UI কখনো ঝুলে থাকে না।

---

## ৩. প্যাটার্ন: Fire-and-forget

```js
Native.call('ui.showToast', { message: 'সেভ হয়েছে', type: 'success' })
      .catch(function () { /* চুপচাপ উপেক্ষা */ });
```

ব্যবহার: টোস্ট, হ্যাপটিক, লগ — যেখানে ব্যর্থ হলেও চলবে।

---

## ৪. প্যাটার্ন: ফলাফল দরকার

```js
async function getDeviceInfo() {
  try {
    return await Native.call('device.getInfo');
  } catch (err) {
    console.warn('device info unavailable:', err.message);
    return { model: 'unknown', width: window.innerWidth, height: window.innerHeight };
  }
}
```

**নিয়ম:** প্রতিটি `await`-এ `try/catch` এবং একটি **fallback**।

---

## ৫. প্যাটার্ন: Confirm → কাজ

```js
function confirmThen(title, message) {
  return new Promise(function (resolve) {
    let settled = false;
    function handler(p) {
      if (settled) return;
      settled = true;
      NativeRuntimeBridgeClient.off('ui.confirm', handler);
      resolve(!!p.confirmed);
    }
    Native.on('ui.confirm', handler);
    Native.call('ui.showConfirm', { title: title, message: message })
      .catch(function () { if (!settled) { settled = true; resolve(false); } });
  });
}

// ব্যবহার
if (await confirmThen('মুছবেন?', 'ফেরানো যাবে না')) {
  await deleteItem();
}
```

**কেন `off`:** listener না সরালে প্রতিবার নতুন listener জমে মেমরি leak হবে এবং
পুরনো handler-ও fire করবে।

---

## ৬. প্যাটার্ন: Loading + কাজ + cleanup

```js
async function withLoading(text, task) {
  let shown = false;
  try {
    await Native.call('ui.showLoading', { text: text, cancelable: false });
    shown = true;
  } catch (e) { /* loading না দেখালেও কাজ চলবে */ }

  try {
    return await task();
  } finally {
    if (shown) {
      try { await Native.call('ui.hideLoading'); } catch (e) {}
    }
  }
}

// ব্যবহার
const data = await withLoading('লোড হচ্ছে…', function () {
  return fetch('/api/items').then(function (r) { return r.json(); });
});
```

---

## ৭. প্যাটার্ন: Storage helper

```js
const Store = {
  async get(key, fallback) {
    try {
      const r = await Native.call('storage.get', { key: key });
      return r.exists ? r.value : fallback;
    } catch (e) { return fallback; }
  },
  async set(key, value) {
    try { await Native.call('storage.set', { key: key, value: value }); return true; }
    catch (e) { return false; }
  },
  async getJson(key, fallback) {
    const raw = await Store.get(key, null);
    if (raw === null) return fallback;
    try { return JSON.parse(raw); } catch (e) { return fallback; }
  },
  async setJson(key, obj) {
    return Store.set(key, JSON.stringify(obj));
  }
};
```

---

## ৮. প্যাটার্ন: Safe area

```js
async function applySafeArea() {
  let top = 0, bottom = 0;
  try {
    const sa = await Native.call('device.getSafeArea');
    top = sa.top || 0;
    bottom = sa.bottom || 0;
  } catch (e) {
    // CSS env() fallback
  }
  document.documentElement.style.setProperty('--safe-top', top + 'px');
  document.documentElement.style.setProperty('--safe-bottom', bottom + 'px');
}
```

CSS:
```css
body { padding-top: var(--safe-top, env(safe-area-inset-top, 0px)); }
```

---

## ৯. প্যাটার্ন: Back button

WebView-এর back চাপলে `web.goBack` নিজে থেকে হয় না — `ProjectActivity` হ্যান্ডেল করে।
আপনার SPA-তে নিজের history থাকলে:

```js
window.addEventListener('popstate', function () {
  renderRoute(location.pathname);
});
```

**সতর্কতা:** `history.pushState` ব্যবহার করলে WebView-এর `canGoBack` বদলায় না।
নিজের state ম্যানেজ করুন।

---

## ১০. প্যাটার্ন: Theme

```js
async function applyTheme() {
  let mode = 'light';
  try {
    const t = await Native.call('ui.getTheme');
    mode = t.themeMode === 'dark' ? 'dark' : 'light';
  } catch (e) {
    mode = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }
  document.documentElement.dataset.theme = mode;
}
```

CSS:
```css
:root[data-theme="dark"] { --bg: #0f172a; --fg: #e2e8f0; }
:root[data-theme="light"] { --bg: #ffffff; --fg: #0f172a; }
```

---

## ১১. প্যাটার্ন: Download

```js
function downloadText(filename, text) {
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
}
```

**কেন `revokeObjectURL` দেরিতে:** সাথে সাথে revoke করলে download শুরু হওয়ার আগেই
URL মরে যেতে পারে।

---

## ১২. প্যাটার্ন: Network-aware UI

```js
let online = true;

async function checkNetwork() {
  try {
    const n = await Native.call('device.getNetworkState');
    online = !!n.connected;
  } catch (e) {
    online = navigator.onLine;
  }
  document.body.classList.toggle('offline', !online);
}

checkNetwork();
setInterval(checkNetwork, 8000);
window.addEventListener('online', checkNetwork);
window.addEventListener('offline', checkNetwork);
```

---

## ১৩. প্যাটার্ন: একাধিক কল একসাথে

```js
const [info, battery, net] = await Promise.all([
  Native.call('device.getDeviceInfo').catch(function () { return {}; }),
  Native.call('device.getBattery').catch(function () { return {}; }),
  Native.call('device.getNetworkState').catch(function () { return {}; })
]);
```

**সতর্কতা:** `NativeWebCommandHandler` main thread-এ কাজ করে এবং ১০ সেকেন্ড পর্যন্ত
অপেক্ষা করে। একসাথে ১০টির বেশি `web.*` কল দিলে timeout হতে পারে। `ui.*`/`device.*`
হালকা, নিরাপদ।

---

## ১৪. প্যাটার্ন: Version-safe feature detection

```js
const FEATURES = {
  snackbar: true,
  popupMenu: true,
  edgeToEdge: true
};

async function probe() {
  try { await Native.call('ui.getActionSheetState'); }
  catch (e) { FEATURES.actionSheet = false; }
}
```

**কেন:** পুরনো বিল্ডে কোনো action না থাকলে `ROUTE_NOT_FOUND` আসে। একবার probe করে
feature flag সেট করে রাখলে বাকি কোড পরিষ্কার থাকে।

---

## ১৫. Anti-pattern (করবেন না)

| ❌ ভুল | ✅ সঠিক |
|---|---|
| `NativeRuntimeBridgeClient.call(...)` guard ছাড়া | `if (hasNative())` |
| `await` ছাড়া `call()` | `await` বা `.catch()` |
| `localStorage.setItem` | `storage.set` |
| `alert()` | `ui.showAlert` |
| `ui.showConfirm`-এর return-এ `confirmed` খোঁজা | `on('ui.confirm')` |
| ১ MiB-র বেশি JSON পাঠানো | ছোট টুকরো বা `fetch` |
| `web.evaluate` নিজের পেজে | সাধারণ JS |
| `device.finish` ভুলবশত | শুধু "বন্ধ করুন" বাটনে |
| `on()` করে `off()` না করা | handler-এ `off` |
| `setInterval` ছাড়া battery/network পড়া | ৫–১০s polling |
