> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# Web App Development Guide — শূন্য থেকে অ্যাপ

## ১. ন্যূনতম যা লাগবে

| জিনিস | বাধ্যতামূলক? | নোট |
|---|---|---|
| `index.html` | ✅ | entry point |
| `manifest.json` | ⚠️ | Project import-এ দরকার |
| CSS/JS ফাইল | ❌ | inline-ও চলবে |
| `native-runtime.js` | ❌ | নিজের wrapper বানালেই হবে |

---

## ২. ফোল্ডার গঠন (সুপারিশ)

```
my-app/
├── index.html
├── manifest.json
├── css/
│   ├── reset.css
│   └── app.css
├── js/
│   ├── native.js        ← bridge wrapper
│   ├── store.js         ← storage helper
│   ├── router.js        ← SPA router
│   └── app.js           ← main
└── assets/
    ├── icon-192.png
    └── icon-512.png
```

---

## ৩. `manifest.json`

```json
{
  "name": "আমার অ্যাপ",
  "short_name": "আমার",
  "version": "1.0.0",
  "entry": "index.html",
  "description": "একটি RONiLiFEOS Web App",
  "icons": {
    "192": "assets/icon-192.png",
    "512": "assets/icon-512.png"
  },
  "theme_color": "#2563EB",
  "background_color": "#FFFFFF",
  "display": "standalone",
  "orientation": "portrait"
}
```

**নোট:** `entry` ফিল্ডটাই Project import-এ entry point হিসেবে ব্যবহৃত হয়।

---

## ৪. `index.html` টেমপ্লেট

```html
<!DOCTYPE html>
<html lang="bn">
<head>
  <meta charset="UTF-8">
  <meta name="viewport"
        content="width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover">
  <meta name="theme-color" content="#2563EB">
  <title>আমার অ্যাপ</title>
  <link rel="stylesheet" href="css/app.css">
</head>
<body>
  <header class="app-bar">
    <h1 id="title">আমার অ্যাপ</h1>
  </header>

  <main id="app"></main>

  <nav class="tabbar">
    <button data-route="/">হোম</button>
    <button data-route="/notes">নোট</button>
    <button data-route="/settings">সেটিংস</button>
  </nav>

  <script src="js/native.js"></script>
  <script src="js/store.js"></script>
  <script src="js/router.js"></script>
  <script src="js/app.js"></script>
</body>
</html>
```

**`viewport-fit=cover` কেন:** notch/safe area ঠিকভাবে ধরার জন্য।

---

## ৫. `js/native.js` — bridge wrapper

```js
(function (global) {
  'use strict';

  var TIMEOUT = 12000;

  function available() {
    return typeof NativeRuntimeBridgeClient !== 'undefined'
        && typeof NativeRuntimeBridgeClient.call === 'function';
  }

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

  function on(topic, fn) {
    if (available()) NativeRuntimeBridgeClient.on(topic, fn);
  }

  function off(topic, fn) {
    if (available()) NativeRuntimeBridgeClient.off(topic, fn);
  }

  function toast(message, type) {
    return call('ui.showToast', { message: message, type: type || 'info' })
      .catch(function () {});
  }

  global.Native = {
    available: available,
    call: call,
    on: on,
    off: off,
    toast: toast
  };
})(window);
```

---

## ৬. `js/store.js`

```js
(function (global) {
  'use strict';

  var PREFIX = 'myapp:';

  function key(k) { return PREFIX + k; }

  var Store = {
    get: function (k, fallback) {
      return Native.call('storage.get', { key: key(k) }).then(function (r) {
        return r.exists ? r.value : fallback;
      }).catch(function () { return fallback; });
    },
    set: function (k, v) {
      return Native.call('storage.set', { key: key(k), value: v })
        .then(function () { return true; })
        .catch(function () { return false; });
    },
    remove: function (k) {
      return Native.call('storage.remove', { key: key(k) }).catch(function () {});
    },
    getJson: function (k, fallback) {
      return Store.get(k, null).then(function (raw) {
        if (raw === null) return fallback;
        try { return JSON.parse(raw); } catch (e) { return fallback; }
      });
    },
    setJson: function (k, obj) {
      return Store.set(k, JSON.stringify(obj));
    }
  };

  global.Store = Store;
})(window);
```

---

## ৭. `js/router.js`

```js
(function (global) {
  'use strict';

  var routes = {};
  var current = null;

  function register(path, handler) { routes[path] = handler; }

  function render(path) {
    var handler = routes[path] || routes['*'];
    var root = document.getElementById('app');
    if (!handler || !root) return;
    current = path;
    root.innerHTML = '';
    handler(root);
    document.querySelectorAll('[data-route]').forEach(function (b) {
      b.classList.toggle('active', b.getAttribute('data-route') === path);
    });
  }

  function navigate(path, replace) {
    if (replace) history.replaceState({ path: path }, '', '#' + path);
    else history.pushState({ path: path }, '', '#' + path);
    render(path);
  }

  function start() {
    window.addEventListener('popstate', function () {
      render(location.hash.replace(/^#/, '') || '/');
    });
    document.addEventListener('click', function (e) {
      var el = e.target.closest('[data-route]');
      if (!el) return;
      e.preventDefault();
      navigate(el.getAttribute('data-route'));
    });
    render(location.hash.replace(/^#/, '') || '/');
  }

  global.Router = { register: register, navigate: navigate, start: start, current: function () { return current; } };
})(window);
```

**কেন hash routing:** `#/notes` WebView-এর history-তে ঢোকে, তাই Android back
কাজ করে। `pushState` path routing back button ভাঙে।

---

## ৮. `js/app.js`

```js
(function () {
  'use strict';

  function home(root) {
    root.innerHTML = '<section class="card"><h2>স্বাগতম</h2>'
      + '<p id="device-line">ডিভাইস তথ্য লোড হচ্ছে…</p></section>';

    Native.call('device.getInfo').then(function (info) {
      document.getElementById('device-line').textContent =
        info.model + ' · Android ' + info.androidVersion;
    }).catch(function () {
      document.getElementById('device-line').textContent = 'ব্রাউজার মোড';
    });
  }

  function notes(root) {
    root.innerHTML = '<section class="card"><h2>নোট</h2>'
      + '<textarea id="note" rows="6" placeholder="এখানে লিখুন…"></textarea>'
      + '<button id="save">সেভ</button></section>';

    Store.get('note', '').then(function (v) {
      document.getElementById('note').value = v;
    });

    document.getElementById('save').addEventListener('click', function () {
      var v = document.getElementById('note').value;
      Store.set('note', v).then(function (ok) {
        Native.toast(ok ? 'সেভ হয়েছে' : 'সেভ ব্যর্থ', ok ? 'success' : 'error');
      });
    });
  }

  function settings(root) {
    root.innerHTML = '<section class="card"><h2>সেটিংস</h2>'
      + '<button id="theme">থিম বদলান</button>'
      + '<button id="clear">সব ডেটা মুছুন</button></section>';

    document.getElementById('theme').addEventListener('click', function () {
      var next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      document.documentElement.dataset.theme = next;
      Store.set('theme', next);
    });

    document.getElementById('clear').addEventListener('click', function () {
      Native.call('ui.showConfirm', { title: 'মুছবেন?', message: 'সব ডেটা চলে যাবে' })
        .catch(function () {});
    });
  }

  Native.on('ui.confirm', function (p) {
    if (!p.confirmed) return;
    Native.call('storage.clear').then(function () {
      Native.toast('মুছে ফেলা হয়েছে', 'success');
      Router.navigate('/');
    });
  });

  Router.register('/', home);
  Router.register('/notes', notes);
  Router.register('/settings', settings);
  Router.register('*', function (root) {
    root.innerHTML = '<section class="card"><h2>৪০৪</h2><p>পাতা নেই</p></section>';
  });

  document.addEventListener('DOMContentLoaded', function () {
    Router.start();
    Store.get('theme', 'light').then(function (t) {
      document.documentElement.dataset.theme = t;
    });
    Native.call('ui.setToolbarTitle', { title: 'আমার অ্যাপ' }).catch(function () {});
  });
})();
```

---

## ৯. `css/app.css` — মোবাইল-ফার্স্ট

```css
:root {
  --bg: #f8fafc;
  --surface: #ffffff;
  --fg: #0f172a;
  --muted: #64748b;
  --primary: #2563eb;
  --border: #e2e8f0;
  --safe-top: env(safe-area-inset-top, 0px);
  --safe-bottom: env(safe-area-inset-bottom, 0px);
}

:root[data-theme="dark"] {
  --bg: #0f172a;
  --surface: #1e293b;
  --fg: #e2e8f0;
  --muted: #94a3b8;
  --border: #334155;
}

* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }

html, body {
  margin: 0;
  padding: 0;
  background: var(--bg);
  color: var(--fg);
  font-family: -apple-system, "Noto Sans Bengali", "Segoe UI", Roboto, sans-serif;
  font-size: 16px;
  line-height: 1.6;
  overscroll-behavior-y: contain;
}

.app-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: calc(var(--safe-top) + 12px) 16px 12px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.app-bar h1 { margin: 0; font-size: 18px; font-weight: 600; }

main { padding: 16px; padding-bottom: calc(var(--safe-bottom) + 72px); }

.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 12px;
}

button {
  min-height: 44px;
  padding: 0 18px;
  border: none;
  border-radius: 10px;
  background: var(--primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

button:active { transform: scale(0.98); }

textarea, input {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg);
  color: var(--fg);
  font: inherit;
}

.tabbar {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  display: flex;
  padding-bottom: var(--safe-bottom);
  background: var(--surface);
  border-top: 1px solid var(--border);
}

.tabbar button {
  flex: 1;
  background: transparent;
  color: var(--muted);
  border-radius: 0;
}

.tabbar button.active { color: var(--primary); }
```

**মূল বিষয়:**
- `min-height: 44px` — টাচ টার্গেট
- `font-size: 16px` — iOS zoom প্রতিরোধ
- `-webkit-tap-highlight-color: transparent` — নীল ফ্ল্যাশ বন্ধ
- `overscroll-behavior-y: contain` — pull-to-refresh বন্ধ
- `env(safe-area-inset-*)` — notch

---

## ১০. ধাপে ধাপে তৈরি

### ধাপ ১ — কাঠামো
`index.html` + `manifest.json` বানান। শুধু "হ্যালো" দেখান।

### ধাপ ২ — bridge যাচাই
```js
console.log('native:', Native.available());
Native.call('device.getInfo').then(console.log).catch(console.error);
```

### ধাপ ৩ — layout
app-bar + main + tabbar। safe area CSS।

### ধাপ ৪ — routing
hash router। প্রতিটি route-এ toolbar title sync।

### ধাপ ৫ — storage
`Store` helper। theme ও draft সেভ।

### ধাপ ৬ — native polish
- `ui.showToast` (alert-এর বদলে)
- `ui.showConfirm` (confirm-এর বদলে)
- `ui.showLoading` (নেটওয়ার্কে)
- `device.getSafeArea`

### ধাপ ৭ — error handling
প্রতিটি `call()`-এ `.catch()`। offline banner।

### ধাপ ৮ — test
- ব্রাউজারে (bridge ছাড়া) — fallback কাজ করছে?
- RONiLiFEOS-এ — সব native কল কাজ করছে?
- ডার্ক মোড
- ছোট স্ক্রিন (320px)
- back button

---

## ১১. সাধারণ ভুল

| ভুল | ফল | সমাধান |
|---|---|---|
| `NativeRuntimeBridgeClient` guard ছাড়া | `ReferenceError` | `Native.available()` |
| `localStorage` | Project clear-এ হারায় | `storage.*` |
| `alert()` | ব্লকিং, কুৎসিত | `ui.showAlert` |
| `window.open` | কিছুই হয় না | `device.openUrl` |
| `pushState` path routing | back button ভাঙে | hash routing |
| `100vh` | ঠিকানা বার/notch সমস্যা | `100dvh` বা `flex` |
| inline `<script>` + CSP | ব্লক | external `.js` |
| `fetch` timeout ছাড়া | চিরকাল ঝুলে থাকে | `AbortController` |
| `innerHTML` user input | XSS | `textContent` |
| ১ MiB+ JSON | `PAYLOAD_TOO_LARGE` | ছোট টুকরো |

---

## ১২. পারফরম্যান্স

```html
<!-- critical CSS inline -->
<style>/* app-bar, main, tabbar */</style>

<!-- বাকি CSS defer -->
<link rel="stylesheet" href="css/app.css" media="print" onload="this.media='all'">

<!-- JS defer -->
<script src="js/app.js" defer></script>
```

- ছবি: `loading="lazy"`, `width`/`height` দিন
- ফন্ট: `font-display: swap`
- DOM: একবারে বড় `innerHTML` (অনেক ছোট update-এর চেয়ে দ্রুত)
- Event: `passive: true` (scroll/touch)

---

## ১৩. চেকলিস্ট

- [ ] `manifest.json`-এ `entry`
- [ ] `viewport-fit=cover`
- [ ] safe area CSS
- [ ] `Native.available()` guard
- [ ] প্রতিটি `call()`-এ `.catch()`
- [ ] `storage.*` + prefix
- [ ] hash routing
- [ ] toolbar title sync
- [ ] dark mode
- [ ] 44px touch target
- [ ] 16px input font
- [ ] offline fallback
- [ ] ব্রাউজারে টেস্ট হয়েছে
