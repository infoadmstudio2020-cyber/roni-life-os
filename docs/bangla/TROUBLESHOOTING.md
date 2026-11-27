# Troubleshooting — সমস্যা ও সমাধান

## ১. `NativeRuntimeBridgeClient is not defined`

**লক্ষণ:** `ReferenceError: NativeRuntimeBridgeClient is not defined`

**কারণ:**

| # | কারণ | যাচাই |
|---|---|---|
| 1 | পেজ লোড হওয়ার আগেই script চলছে | `onPageFinished`-এর আগে inject হয় না |
| 2 | ব্রাউজারে খোলা হয়েছে | bridge শুধু WebView-এ |
| 3 | origin allow-list-এ নেই | `ORIGIN_REJECTED` লগ |
| 4 | `bridgeEnabled = false` | `WebHostConfig` চেক |
| 5 | পেজ `about:blank`/`data:` | origin `null` |

**সমাধান:**

```js
function waitForBridge(timeoutMs) {
  return new Promise(function (resolve, reject) {
    var start = Date.now();
    (function poll() {
      if (typeof NativeRuntimeBridgeClient !== 'undefined') return resolve(true);
      if (Date.now() - start > (timeoutMs || 5000)) return reject(new Error('BRIDGE_TIMEOUT'));
      setTimeout(poll, 50);
    })();
  });
}

waitForBridge(5000).then(function () { init(); }).catch(function () { initBrowserMode(); });
```

---

## ২. `ROUTE_NOT_FOUND`

**লক্ষণ:** `Error: ROUTE_NOT_FOUND`

**কারণ:** action নাম ভুল, অথবা namespace রেজিস্টার্ড নয়।

**যাচাই:**

```js
// ✅ ঠিক
await Native.call('ui.showToast', { message: 'x' });

// ❌ ভুল — theme রেজিস্টার্ড নয়
await Native.call('theme.getTheme');
```

**সমাধান:** `JAVASCRIPT_INTEGRATION.md`-এর তালিকা দেখুন। `theme.*`, `network.*`,
`platform.*`, `security.*`, `analytics.*`, `devtools.*` — এগুলো **নেই**।

---

## ৩. `ORIGIN_REJECTED`

**লক্ষণ:** `Error: ORIGIN_REJECTED: https://something`

**কারণ:**

| # | কারণ | সমাধান |
|---|---|---|
| 1 | `web.loadUrl` দিয়ে বাইরের সাইটে গেছেন | `device.openUrl` ব্যবহার করুন |
| 2 | `web.loadHtml` দিয়ে origin বদলেছে | `loadHtml` এড়িয়ে চলুন |
| 3 | iframe-এর ভেতর থেকে কল | top-level origin ব্যবহার হয় |
| 4 | `about:blank` | প্রকৃত URL লোড করুন |

**ডায়াগনস্টিক:**

```js
console.log('current origin:', location.origin);
console.log('href:', location.href);
```

---

## ৪. `REQUEST_TIMEOUT`

**লক্ষণ:** ১০ সেকেন্ড পর `Error: REQUEST_TIMEOUT`

**কারণ:**

| # | কারণ | সমাধান |
|---|---|---|
| 1 | main thread আটকে আছে | ভারী কাজ background-এ সরান |
| 2 | `web.evaluate`-এ ভারী script | ছোট script |
| 3 | handler exception | logcat দেখুন |
| 4 | WebView destroy হয়ে গেছে | Activity state চেক |

**সমাধান:** নিজের timeout যোগ করুন (bridge-এর চেয়ে কম):

```js
function callFast(action, payload) {
  return Promise.race([
    Native.call(action, payload),
    new Promise(function (_, rej) { setTimeout(function () { rej(new Error('FAST_TIMEOUT')); }, 6000); })
  ]);
}
```

---

## ৫. `PAYLOAD_TOO_LARGE`

**লক্ষণ:** `Error: PAYLOAD_TOO_LARGE`

**কারণ:** JSON ১ MiB (1,048,576 bytes) ছাড়িয়েছে।

**সমাধান:**

```js
// ❌ বড় base64
await Native.call('storage.set', { key: 'img', value: hugeBase64 });

// ✅ ছোট টুকরো
const CHUNK = 200 * 1024;
for (let i = 0; i < data.length; i += CHUNK) {
  await Native.call('storage.set', {
    key: 'img:' + i,
    value: data.slice(i, i + CHUNK)
  });
}
```

**আরও ভালো:** বড় ডেটা `IndexedDB`-তে রাখুন, `storage.*`-এ শুধু pointer।

---

## ৬. `UNKNOWN_ACTION`

**লক্ষণ:** `Error: UNKNOWN_ACTION`

**কারণ:** path ঠিক আছে কিন্তু action নাম ভুল।

```js
// ❌
await Native.call('ui.showtoast', { message: 'x' });   // ছোট হাতের o
// ✅
await Native.call('ui.showToast', { message: 'x' });
```

**নোট:** action নাম **case-sensitive**।

---

## ৭. `STORAGE_UNAVAILABLE`

**লক্ষণ:** `Error: STORAGE_UNAVAILABLE`

**কারণ:** `StorageEngine.init()` হয়নি (bootstrap ব্যর্থ)।

**সমাধান:** `NativeRuntimeBootstrap`-এর report দেখুন। সাধারণত Keystore সমস্যা
(emulator-এ কখনো কখনো) বা disk full।

**Fallback:**

```js
async function safeSet(key, value) {
  try {
    await Native.call('storage.set', { key: key, value: value });
    return true;
  } catch (e) {
    if (e.message === 'STORAGE_UNAVAILABLE') {
      localStorage.setItem(key, value);   // অস্থায়ী fallback
      return false;
    }
    throw e;
  }
}
```

---

## ৮. `WEBVIEW_UNAVAILABLE`

**লক্ষণ:** `Error: WEBVIEW_UNAVAILABLE`

**কারণ:** `WebViewHost` attach হয়নি বা destroy হয়েছে।

**সমাধান:** `ui.getWebViewState` দিয়ে চেক করুন। Activity recreate হলে সাময়িকভাবে
আসতে পারে — retry করুন।

---

## ৯. `MAIN_THREAD_TIMEOUT`

**লক্ষণ:** `Error: MAIN_THREAD_TIMEOUT`

**কারণ:** `NativeWebCommandHandler` main thread-এ ১০ সেকেন্ড অপেক্ষা করেছে কিন্তু
কাজ শেষ হয়নি।

**সমাধান:** `web.*` কল কমিয়ে দিন, একসাথে অনেক `web.*` কল দেবেন না।

---

## ১০. Toast/Dialog দেখা যাচ্ছে না

| কারণ | যাচাই | সমাধান |
|---|---|---|
| Activity নেই | `ui.getConfig` কাজ করে? | Activity attach হওয়ার পর কল করুন |
| Activity finishing | — | `onResume`-এর পর কল করুন |
| অন্য dialog খোলা | `ui.isDialogShowing` | আগেরটি dismiss করুন |
| Toast দ্রুত চলে গেছে | — | `duration:'long'` |

---

## ১১. Confirm-এর উত্তর আসছে না

**লক্ষণ:** `ui.showConfirm` resolve হয় কিন্তু `ui.confirm` ইভেন্ট আসে না।

**কারণ:** listener রেজিস্ট্রেশন দেরিতে হয়েছে।

```js
// ❌
await Native.call('ui.showConfirm', {...});
Native.on('ui.confirm', handler);      // অনেক দেরি

// ✅
Native.on('ui.confirm', handler);
await Native.call('ui.showConfirm', {...});
```

---

## ১২. `ui.showConfirm`-এ `confirmed` নেই

**লক্ষণ:** `const r = await call('ui.showConfirm', …); r.confirmed` → `undefined`

**কারণ:** response শুধু `{shown:true}`। উত্তর ইভেন্টে।

```js
// ❌
const r = await Native.call('ui.showConfirm', { title: 'x' });
if (r.confirmed) { ... }        // কখনো সত্য হবে না

// ✅
Native.on('ui.confirm', p => { if (p.confirmed) { ... } });
await Native.call('ui.showConfirm', { title: 'x' });
```

---

## ১৩. Storage-এ ডেটা পাওয়া যাচ্ছে না

| কারণ | যাচাই |
|---|---|
| `r.exists` চেক করিনি | `console.log(r)` |
| key-তে prefix ভুল | `storage.keys` দেখুন |
| `null` সেভ হয়েছে | `null` → `""` হয়ে যায় |
| Object stringify করিনি | `JSON.stringify` |
| অন্য Project-এর key | prefix আলাদা করুন |

```js
const all = await Native.call('storage.getAll');
console.log(all);
```

---

## ১৪. Download হচ্ছে না

| কারণ | সমাধান |
|---|---|
| `blob:` URL সাথে সাথে revoke | ৫ সেকেন্ড পরে revoke |
| notification permission নেই (API 33+) | `POST_NOTIFICATIONS` চান |
| URL HTTP (cleartext) | HTTPS |
| `downloadsEnabled = false` | `WebHostConfig` চেক |
| ফাইল নামে অবৈধ অক্ষর | sanitize |

**যাচাই:** `Download/RONiLiFEOS/` ফোল্ডার দেখুন।

---

## ১৫. Camera/Mic কাজ করছে না

**কারণ:** `WebPermissionHandler` সেট নেই → সব deny।

**সমাধান (অ্যাপ ডেভেলপারকে):**

```java
WebHostConfig.builder()
    .permissionHandler(new WebPermissionHandler() {
        @Override
        public void onPermissionRequest(PermissionRequest req, PermissionCallback cb) {
            cb.grant(req.getResources());
        }
        @Override
        public void onGeolocationRequest(String origin, GeolocationCallback cb) {
            cb.grant();
        }
    })
    .build();
```

**Web App ডেভেলপার হিসেবে:** permission deny হলে fallback UI দেখান।

```js
try {
  const stream = await navigator.mediaDevices.getUserMedia({ video: true });
} catch (e) {
  showMessage('ক্যামেরা অনুমতি নেই — ফাইল আপলোড করুন');
}
```

---

## ১৬. `fetch` ব্যর্থ

| Error | কারণ | সমাধান |
|---|---|---|
| `Mixed Content` | HTTPS পেজ → HTTP API | HTTPS |
| `CORS` | সার্ভার header নেই | `Access-Control-Allow-Origin` |
| `ERR_CLEARTEXT_NOT_PERMITTED` | Android 9+ | HTTPS |
| `Failed to fetch` | DNS/নেটওয়ার্ক | `device.getNetworkState` |
| `AbortError` | timeout | timeout বাড়ান |

---

## ১৭. Back button অ্যাপ বন্ধ করছে

**কারণ:** `history.pushState` WebView history-তে ঢোকে না।

**সমাধান:** hash routing ব্যবহার করুন (`#/notes`)।

```js
// ❌
history.pushState({}, '', '/notes');

// ✅
location.hash = '#/notes';
```

---

## ১৮. পেজ সাদা দেখাচ্ছে

| কারণ | যাচাই |
|---|---|
| JS error | `console` দেখুন |
| `manifest.json`-এ `entry` ভুল | ফাইল path |
| CSS path ভুল | Network tab |
| CSP ব্লক | console-এ CSP error |
| `index.html` নেই | ফোল্ডার গঠন |

---

## ১৯. বাংলা ফন্ট ভাঙছে

```css
body {
  font-family: "Noto Sans Bengali", "Hind Siliguri", -apple-system, sans-serif;
}
```

**CSV-তে বাংলা:** `\uFEFF` (BOM) যোগ করুন।

---

## ২০. ডায়াগনস্টিক স্ক্রিপ্ট

```js
async function diagnose() {
  const report = {
    bridge: typeof NativeRuntimeBridgeClient !== 'undefined',
    origin: location.origin,
    href: location.href,
    ua: navigator.userAgent
  };

  if (report.bridge) {
    report.ping = NativeRuntimeBridgeClient.ping();
    for (const action of ['ui.getConfig', 'device.getInfo', 'storage.keys', 'web.getState']) {
      try {
        report[action] = await Native.call(action);
      } catch (e) {
        report[action] = 'ERROR: ' + e.message;
      }
    }
  }

  console.log(JSON.stringify(report, null, 2));
  return report;
}
```

---

## ২১. দ্রুত রেফারেন্স

| Error | প্রথমে যা দেখবেন |
|---|---|
| `NativeRuntimeBridgeClient is not defined` | inject timing / browser mode |
| `ROUTE_NOT_FOUND` | action নামের বানান |
| `ORIGIN_REJECTED` | `location.origin` |
| `REQUEST_TIMEOUT` | main thread block |
| `PAYLOAD_TOO_LARGE` | JSON size |
| `UNKNOWN_ACTION` | case sensitivity |
| `STORAGE_UNAVAILABLE` | bootstrap report |
| `WEBVIEW_UNAVAILABLE` | Activity state |
| `MAIN_THREAD_TIMEOUT` | একসাথে অনেক `web.*` কল |
