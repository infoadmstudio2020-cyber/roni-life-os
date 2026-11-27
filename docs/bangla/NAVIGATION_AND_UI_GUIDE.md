# Navigation ও UI Guide

> এই ডকটি দুই ভাগে বিভক্ত — **পর্ব ১: Navigation** (`web.*`, drawer, tab) এবং
> **পর্ব ২: UI ও System UI** (dialog, toast, toolbar, status bar)।


## ১. নেভিগেশনের চারটি স্তর

| স্তর | কী বদলায় | API |
|---|---|---|
| **Page** | WebView-এর URL | `web.loadUrl`, `web.loadHtml`, `web.loadSource` |
| **History** | WebView back/forward stack | `web.goBack`, `web.goForward`, `web.clearHistory` |
| **SPA route** | পেজের ভেতরের view | নিজের JS router (`history.pushState`) |
| **Native shell** | Toolbar, drawer, tab | `ui.setToolbarTitle`, `ui.selectTab`, … |

**মূল কথা:** `ui.selectTab` শুধু নিচের ট্যাবের highlight বদলায় — পেজ বদলায় না।
পেজ বদলাতে `web.loadUrl` বা নিজের SPA router লাগবে।

---

## ২. `web.loadUrl`

```js
await Native.call('web.loadUrl', { url: 'https://example.com/page' });
// → { loaded: true }
```

**`loaded:true` মানে লোড শুরু হয়েছে**, শেষ হয়নি। লোড শেষ জানতে `window.onload`
ব্যবহার করুন (নতুন পেজে)।

**⚠️ Origin gate:** নতুন origin-এ গেলে bridge আর কাজ করবে না, কারণ
`WebViewHost` শুধু **প্রথম** লোড করা host-কে `allowOrigin` করে। বাইরের সাইটে গেলে
`ORIGIN_REJECTED` আসবে।

**সমাধান:** বাইরের লিংক `device.openUrl` দিয়ে ব্রাউজারে খুলুন।

---

## ৩. `web.loadHtml`

```js
await Native.call('web.loadHtml', {
  html: '<h1>হ্যালো</h1>',
  baseUrl: 'https://appassets.androidplatform.net/'
});
```

`baseUrl` না দিলে relative resource ভাঙবে। `loadDataWithBaseURL` ব্যবহার হয়।

**সতর্কতা:** `loadHtml` দিলে বর্তমান origin বদলে যেতে পারে (`about:blank`) → bridge
বন্ধ হয়ে যাবে। শুধু ডায়াগনস্টিকে ব্যবহার করুন।

---

## ৪. `web.loadSource` / `ui.setWebViewSource`

```js
await Native.call('web.loadSource', {
  type: 'asset',              // remote | asset | file | zip | zipfile | inline | content
  location: 'web/index.html',
  entryPoint: 'index.html'
});
```

| `type` | `location` মানে |
|---|---|
| `remote` | পূর্ণ URL |
| `asset` | `assets/` এর ভেতরের path |
| `file` | ফাইলসিস্টেম path |
| `zip` | zip ফাইলের path |
| `zipfile` | zip + entry |
| `inline` | `html` ফিল্ডে সরাসরি HTML |
| `content` | `content://` URI |

**সুপারিশ:** Project নিজেই source ঠিক করে, তাই সাধারণ Web App-এ এটি ডাকার দরকার নেই।

---

## ৫. Back / Forward

```js
const state = await Native.call('web.getState');
// { url, canGoBack, canGoForward, title, progress }

if (state.canGoBack) {
  await Native.call('web.goBack');
}
```

**`ui.navigateBack`** — Activity-র নিজের back (WebView history নয়):

```js
await Native.call('ui.navigateBack');   // { navigated: true }
```

**পার্থক্য:**

| API | কী করে |
|---|---|
| `web.goBack` | WebView-এর আগের পেজে যায় |
| `ui.navigateBack` | Activity finish করে (ড্যাশবোর্ডে ফেরে) |
| `device.finish` | Activity finish করে (একই কাজ) |

---

## ৬. SPA routing

```js
const routes = {
  '/': renderHome,
  '/notes': renderNotes,
  '/settings': renderSettings
};

function navigate(path) {
  history.pushState({}, '', path);
  renderRoute(path);
}

function renderRoute(path) {
  const view = routes[path] || renderNotFound;
  document.getElementById('app').innerHTML = '';
  view(document.getElementById('app'));
  syncToolbar(path);
}

window.addEventListener('popstate', function () {
  renderRoute(location.pathname);
});

document.addEventListener('click', function (e) {
  const a = e.target.closest('a[data-link]');
  if (!a) return;
  e.preventDefault();
  navigate(a.getAttribute('href'));
});
```

**গুরুত্বপূর্ণ:** `history.pushState` WebView-এর `canGoBack` বদলায় না। তাই Android
back চাপলে Activity বন্ধ হয়ে যেতে পারে। নিজের back handling দরকার হলে
`popstate` + `history.state` ব্যবহার করুন, অথবা hash routing (`#/notes`) ব্যবহার করুন —
hash routing WebView history-তে ঢোকে, তাই `web.goBack` কাজ করে।

---

## ৭. Toolbar sync

```js
const TITLES = { '/': 'হোম', '/notes': 'নোট', '/settings': 'সেটিংস' };

async function syncToolbar(path) {
  try {
    await Native.call('ui.setToolbarTitle', { title: TITLES[path] || 'অ্যাপ' });
  } catch (e) { /* ব্রাউজার মোড */ }
  document.title = TITLES[path] || 'অ্যাপ';
}
```

---

## ৮. Drawer navigation

```js
async function initDrawer() {
  const items = [
    { id: 'home', title: 'হোম' },
    { id: 'notes', title: 'নোট' },
    { id: 'settings', title: 'সেটিংস' }
  ];
  for (const it of items) {
    await Native.call('ui.addDrawerItem', it);
  }

  NativeRuntimeBridgeClient.on('ui.drawerItemSelected', function (p) {
    navigate('/' + (p.id === 'home' ? '' : p.id));
    Native.call('ui.closeDrawer').catch(function () {});
  });
}
```

**সীমা:** `addDrawerItem`-এ icon নেই। `removeDrawerItem` দিয়ে সরানো যায়।

---

## ৯. Bottom navigation

```js
async function initTabs() {
  await Native.call('ui.addTab', { tabId: 'home', label: 'হোম' });
  await Native.call('ui.addTab', { tabId: 'inbox', label: 'ইনবক্স' });
  await Native.call('ui.selectTab', { tabId: 'home' });
  await Native.call('ui.setBadge', { tabId: 'inbox', count: 3 });
}
```

**সীমা:** tab click-এর কোনো ইভেন্ট নেই। `ui.getSelectedTab` দিয়ে polling করতে হবে:

```js
let lastTab = null;
setInterval(async function () {
  try {
    const { tabId } = await Native.call('ui.getSelectedTab');
    if (tabId !== lastTab) { lastTab = tabId; navigate('/' + tabId); }
  } catch (e) {}
}, 400);
```

> ৪০০ms polling হালকা কিন্তু আদর্শ নয়। ভালো UX-এর জন্য drawer ব্যবহার করুন
> (`ui.drawerItemSelected` ইভেন্ট আছে)।

---

## ১০. External link

```js
document.addEventListener('click', function (e) {
  const a = e.target.closest('a');
  if (!a) return;
  const href = a.getAttribute('href') || '';
  if (/^https?:\/\//.test(href) && a.host !== location.host) {
    e.preventDefault();
    Native.call('device.openUrl', { url: href }).catch(function () {
      window.open(href, '_blank');
    });
  }
});
```

**কেন:** বাইরের origin-এ গেলে bridge ভেঙে যাবে। ব্রাউজারে খুললে অ্যাপ অক্ষত থাকে।

---

## ১১. Activity lifecycle

| ইভেন্ট | JS-এ কী পাওয়া যায় |
|---|---|
| Activity pause | `document.visibilitychange` → `hidden` |
| Activity resume | `document.visibilitychange` → `visible` |
| Activity destroy | `pagehide` |
| WebView reload | নতুন JS context (সব listener মুছে যায়) |

```js
document.addEventListener('visibilitychange', function () {
  if (document.hidden) {
    saveDraft();
  } else {
    refreshData();
  }
});
```

**নোট:** `onPause`/`onResume`-এর জন্য আলাদা bridge ইভেন্ট নেই।

---

## ১২. Form state restore

`ProjectActivity` স্বয়ংক্রিয়ভাবে form state সেভ/রিস্টোর করে (`FormStateScript`)।

**শর্ত:**
- প্রতিটি input-এ `id` বা `name` থাকতে হবে
- restore শুধু **খালি** field পূরণ করে (user-typed মান overwrite করে না)
- `password` field বাদ যায়

```html
<form>
  <input id="title" name="title" type="text">
  <textarea id="body" name="body"></textarea>
</form>
```

---

## ১৩. চেকলিস্ট

- [ ] বাইরের লিংক `device.openUrl` দিয়ে
- [ ] `web.loadUrl`-এ নতুন origin-এ যাচ্ছি না
- [ ] SPA route-এ toolbar title sync
- [ ] `history.pushState` ব্যবহার করলে back handling ভেবেছি
- [ ] drawer ব্যবহার করলে `ui.drawerItemSelected` শুনছি
- [ ] tab click দরকার হলে polling বা drawer-এ সরে গেছি
- [ ] `visibilitychange`-এ draft সেভ

---


## ১. কোন UI component কে নিয়ন্ত্রণ করে

```
NativeUIHost
 ├── ToolbarManager          → ui.setToolbarTitle / addToolbarAction
 ├── NavigationManager       → ui.openDrawer / selectTab / setBadge
 ├── DialogManager           → ui.showAlert / showConfirm
 ├── LoadingOverlayManager   → ui.showLoading / hideLoading
 ├── SnackbarManager         → ui.showSnackbar
 ├── PopupMenuManager        → ui.showPopupMenu
 ├── NativeToast             → ui.showToast
 ├── CustomBottomSheet       → ui.showBottomSheet
 ├── NativeActionSheet       → ui.showActionSheet
 └── SystemUIManager         → ui.setStatusBar / setFullscreen
```

---

## ২. Dialog

### ২.১ `ui.showAlert`

```js
await Native.call('ui.showAlert', { title: 'ত্রুটি', message: 'সার্ভারে সমস্যা' });
```

- Activity না থাকলে `DialogManager.showAlert()` (headless) — কিছুই দেখাবে না
- `activity.isFinishing()` বা `isDestroyed()` হলে নিঃশব্দে বাদ যায়
- একই সময়ে একটির বেশি dialog থাকলে আগেরটি dismiss হয়

### ২.২ `ui.showConfirm`

```js
NativeRuntimeBridgeClient.on('ui.confirm', function (p) {
  console.log(p.confirmed);
});
await Native.call('ui.showConfirm', {
  title: 'লগআউট?',
  message: 'আপনি লগআউট হয়ে যাবেন',
  confirmText: 'হ্যাঁ',
  cancelText: 'না'
});
```

**তিনটি পথেই `ui.confirm` আসে:**
- confirm বাটন → `{confirmed:true}`
- cancel বাটন → `{confirmed:false}`
- back / বাইরে ট্যাপ → `{confirmed:false}`

---

## ৩. Toast

```js
await Native.call('ui.showToast', {
  message: 'সেভ হয়েছে',
  type: 'success',      // info | success | warning | error
  duration: 'short'     // short | long
});
```

| `type` | রঙ/আইকন |
|---|---|
| `info` | নীল ℹ |
| `success` | সবুজ ✓ |
| `warning` | কমলা ⚠ |
| `error` | লাল ✕ |

**সীমা:** একটির বেশি Toast একসাথে থাকলে নতুনটি আগেরটি প্রতিস্থাপন করে।
`ui.dismissToast` দিয়ে সরানো যায়, `ui.isToastVisible` দিয়ে অবস্থা জানা যায়।

---

## ৪. Snackbar

```js
NativeRuntimeBridgeClient.on('ui.snackbarAction', function (p) {
  undoDelete();
});
await Native.call('ui.showSnackbar', {
  message: 'নোট মুছে ফেলা হয়েছে',
  actionTitle: 'ফিরিয়ে আনুন',
  duration: 'long'
});
```

**পার্থক্য Toast vs Snackbar:**

| | Toast | Snackbar |
|---|---|---|
| Action বাটন | ❌ | ✅ |
| Anchor | স্ক্রিন | নিচের view |
| Activity দরকার | ✅ | ✅ (anchor) |
| Fallback | — | Toast |

---

## ৫. Bottom Sheet / Action Sheet

```js
await Native.call('ui.showBottomSheet', {
  title: 'ফাইল',
  items: [
    { title: 'খুলুন' },
    { title: 'শেয়ার' },
    { title: 'মুছুন', destructive: true }
  ]
});
```

`destructive:true` → লাল রঙ।

**Action Sheet বনাম Bottom Sheet:** দুটোই একই ইভেন্ট (`ui.actionSelected`) দেয়।
`showActionSheet` প্রতিবার `clear()` করে নতুন আইটেম যোগ করে; `showBottomSheet`
`showNativeBottomSheet` ব্যবহার করে (আরও বেশি Material)।

---

## ৬. Popup Menu

```js
NativeRuntimeBridgeClient.on('ui.popupItemSelected', function (p) {
  console.log(p.index, p.title);
});
await Native.call('ui.showPopupMenu', {
  items: [{ id: 'edit', title: 'সম্পাদনা' }, { id: 'del', title: 'মুছুন' }]
});
```

**সীমা:** `id` ফেরত আসে না — শুধু `title` ও `index`। তাই `index` দিয়ে ম্যাপ করুন।

---

## ৭. Loading

```js
await Native.call('ui.showLoading', { text: 'অপেক্ষা করুন…', cancelable: false });
// … কাজ …
await Native.call('ui.hideLoading');
```

**নিয়ম:**
- `cancelable:false` → back চাপলেও বন্ধ হবে না
- `hideLoading` না ডাকলে overlay চিরকাল থাকবে
- `ui.isLoading` দিয়ে চেক করা যায়

**সুপারিশ:** `try/finally` ব্যবহার করুন (দেখুন `API_USAGE_GUIDE.md` §৬)।

---

## ৮. Toolbar

```js
await Native.call('ui.setToolbarTitle', { title: 'নোট' });
await Native.call('ui.setToolbarSubtitle', { subtitle: '১২টি আইটেম' });
await Native.call('ui.setToolbarVisible', { visible: true });
await Native.call('ui.setNavigationIconVisible', { visible: true });
```

**Action বাটন:**

```js
await Native.call('ui.clearToolbarActions');
await Native.call('ui.addToolbarAction', { id: 'search', label: 'খুঁজুন' });
await Native.call('ui.addToolbarAction', { id: 'more', label: 'আরও' });

NativeRuntimeBridgeClient.on('ui.toolbarAction', function (p) {
  if (p.id === 'search') openSearch();
});
```

**সীমা:** icon নেই, শুধু text label। `getToolbarActions` দিয়ে তালিকা পাওয়া যায়।

---

## ৯. Drawer

```js
await Native.call('ui.openDrawer');
await Native.call('ui.toggleDrawer');
const { open } = await Native.call('ui.isDrawerOpen');
await Native.call('ui.closeDrawer');
```

**শর্ত:** Project UI config-এ `drawerEnabled = true` না হলে drawer নেই।

---

## ১০. Bottom Navigation

```js
await Native.call('ui.addTab', { tabId: 'home', label: 'হোম' });
await Native.call('ui.addTab', { tabId: 'inbox', label: 'ইনবক্স' });
await Native.call('ui.selectTab', { tabId: 'inbox' });
await Native.call('ui.setBadge', { tabId: 'inbox', count: 5 });
```

**সীমা:** tab click ইভেন্ট নেই। `ui.getSelectedTab` polling করুন (দেখুন
`NAVIGATION_AND_UI_GUIDE.md` §৯)।

---

## ১১. FAB

```js
await Native.call('ui.setFabVisible', { visible: true });
const { visible } = await Native.call('ui.isFabVisible');
```

**সীমা:** click ইভেন্ট নেই। FAB-এর কাজ করতে হলে পেজের ভেতরে নিজের বাটন বানান।

---

## ১২. System UI

### ১২.১ Status bar

```js
await Native.call('ui.setStatusBar', { color: '#0F172A', lightIcons: true });
```

`lightIcons:true` → সাদা আইকন (গাঢ় background-এর জন্য)।

### ১২.২ Navigation bar

```js
await Native.call('ui.setNavigationBar', { color: '#0F172A', lightButtons: true });
```

### ১২.৩ Fullscreen / Immersive / Edge-to-edge

```js
await Native.call('ui.setFullscreen', { enabled: true });
await Native.call('ui.setImmersive', { enabled: true });
await Native.call('ui.setEdgeToEdge', { enabled: true });
```

| মোড | কী লুকায় |
|---|---|
| `fullscreen` | status bar |
| `immersive` | status + nav bar (swipe-এ ফিরে আসে) |
| `edgeToEdge` | কিছুই লুকায় না, content bar-এর নিচে যায় |

### ১২.৪ অবস্থা পড়া

```js
const s = await Native.call('ui.getSystemUIState');
// { fullscreen, immersive, edgeToEdge, statusBarColor, navBarColor,
//   lightStatusBar, lightNavBar }
```

**গুরুত্বপূর্ণ:** `ProjectActivity` ডিফল্টে immersive fullscreen চালায়। তাই
`ui.getSystemUIState().fullscreen` সাধারণত `true` আসবে।

---

## ১৩. Theme

```js
const t = await Native.call('ui.getTheme');
// { themeMode, primaryColor, backgroundColor, statusBarColor, navBarColor,
//   lightStatusBar, lightNavBar }
```

`themeMode` মান: `light` | `dark` | `system` | `auto`

**`ui.applyTheme`** — শুধু native component-এ প্রযোজ্য, WebView CSS বদলায় না:

```js
await Native.call('ui.applyTheme', { primaryColor: '#2563EB' });
```

CSS নিজে বদলাতে:

```js
document.documentElement.style.setProperty('--primary', t.primaryColor);
```

---

## ১৪. Component state

```js
const { components } = await Native.call('ui.listComponents');
// [{ key:'TOOLBAR', name:'Toolbar', category:'APP_BAR', enabled:true, visible:true }, …]

await Native.call('ui.setComponentVisible', { category: 'APP_BAR', key: 'TOOLBAR', visible: false });
// → { visible:false, applied:true }
```

`applied:false` মানে component এই Activity-তে attach করা নেই (তাই কিছু হয়নি)।

---

## ১৫. Config পড়া

```js
const cfg = await Native.call('ui.getConfig');
console.log(cfg.toolbarEnabled, cfg.drawerEnabled, cfg.bottomNavEnabled, cfg.themeMode);
```

Project-এর UI config জানতে এটাই সবচেয়ে নির্ভরযোগ্য উপায় — এর ভিত্তিতে নিজের layout
সাজান (যেমন drawer না থাকলে hamburger বাটন দেখাবেন না)।

---

## ১৬. Safe area ও edge-to-edge CSS

```css
:root {
  --safe-top: env(safe-area-inset-top, 0px);
  --safe-bottom: env(safe-area-inset-bottom, 0px);
}
.app-bar { padding-top: var(--safe-top); }
.bottom-nav { padding-bottom: var(--safe-bottom); }
```

```js
Native.call('device.getSafeArea').then(function (sa) {
  document.documentElement.style.setProperty('--safe-top', (sa.top || 0) + 'px');
  document.documentElement.style.setProperty('--safe-bottom', (sa.bottom || 0) + 'px');
}).catch(function () {});
```

---

## ১৭. চেকলিস্ট

- [ ] `showLoading` সবসময় `hideLoading`-এ শেষ
- [ ] `showConfirm`-এ `on('ui.confirm')` আগে রেজিস্টার
- [ ] `on()` করার পর `off()`
- [ ] toolbar action-এ `id` ইউনিক
- [ ] drawer/tab ব্যবহারের আগে `ui.getConfig` দিয়ে চেক
- [ ] safe area CSS-এ
- [ ] FAB/tab click-এর জন্য ইভেন্ট নেই জেনে বিকল্প ভেবেছি
