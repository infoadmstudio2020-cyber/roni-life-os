# Feature Catalog — Web App কী কী করতে পারে

প্রতিটি ফিচারে: **কী করে → কেন দরকার → কোন মডিউল → কোন JS API → উদাহরণ → error → নিরাপত্তা**

---

## F-01 নেটিভ Toast

| | |
|---|---|
| **কী করে** | Android-এর নিজের Toast দেখায় (info/success/warning/error prefix সহ) |
| **কেন দরকার** | `alert()`-এর বদলে অ্যাপের ভেতরের অনুভূতি; ব্লক করে না |
| **মডিউল** | `native-runtime-ui` → `NativeUICommandHandler` → `NativeToast` |
| **JS API** | `ui.showToast` |

```js
await native('ui.showToast', { message: 'সেভ হয়েছে', type: 'success', duration: 'short' });
```

**Output:** `{shown:true}` · **Error:** `UNKNOWN_ACTION`, `INTERNAL_ERROR`
**নিরাপত্তা:** কোনো permission নেই। message ১ MiB-এর কম হতে হবে।

---

## F-02 নেটিভ Alert

| | |
|---|---|
| **কী করে** | Material AlertDialog, একটি OK বাটন |
| **কেন দরকার** | গুরুত্বপূর্ণ বার্তা যা ব্যবহারকারীকে থামায় |
| **মডিউল** | `NativeUICommandHandler` → `DialogManager.showNativeDialog` |
| **JS API** | `ui.showAlert` |

```js
await native('ui.showAlert', { title: 'ত্রুটি', message: 'ইন্টারনেট নেই' });
```

**Output:** `{shown:true}` (dialog দেখানোর সাথে সাথেই resolve — বন্ধ হওয়ার অপেক্ষা করে না)

---

## F-03 নেটিভ Confirm

| | |
|---|---|
| **কী করে** | দুই-বাটন ডায়ালগ, উত্তর ইভেন্টে আসে |
| **কেন দরকার** | ধ্বংসাত্মক কাজের আগে নিশ্চিতকরণ |
| **মডিউল** | `NativeUICommandHandler` → `DialogManager` |
| **JS API** | `ui.showConfirm` + `on('ui.confirm')` |

```js
NativeRuntimeBridgeClient.on('ui.confirm', function (p) {
  if (p.confirmed) doDelete();
});
await native('ui.showConfirm', { title: 'মুছবেন?', message: 'ফেরানো যাবে না' });
```

**Output:** `{shown:true}`; পরে ইভেন্ট `{confirmed:true|false}`
**নোট:** back চাপলে বা বাইরে ট্যাপ করলে `confirmed:false` আসে (`setOnCancelListener`)।

---

## F-04 Bottom Sheet / Action Sheet

| | |
|---|---|
| **কী করে** | নিচ থেকে ওঠা অপশন তালিকা |
| **কেন দরকার** | মোবাইলে স্বাভাবিক মেনু |
| **মডিউল** | `NativeUICommandHandler` → `CustomBottomSheet` / `NativeActionSheet` |
| **JS API** | `ui.showBottomSheet`, `ui.showActionSheet` |

```js
NativeRuntimeBridgeClient.on('ui.actionSelected', p => console.log(p.index, p.title));
await native('ui.showBottomSheet', {
  title: 'অপশন',
  items: [{ title: 'শেয়ার' }, { title: 'মুছুন', destructive: true }]
});
```

**Output:** `{shown:true}`; ইভেন্ট `{title, index}`

---

## F-05 Loading overlay

| | |
|---|---|
| **কী করে** | ব্লকিং প্রোগ্রেস ওভারলে |
| **কেন দরকার** | নেটওয়ার্ক কাজের সময় UI জমে যাওয়া রোধ |
| **মডিউল** | `NativeUICommandHandler` → `LoadingOverlayManager` |
| **JS API** | `ui.showLoading`, `ui.hideLoading`, `ui.isLoading` |

```js
await native('ui.showLoading', { text: 'লোড হচ্ছে…', cancelable: false });
try { await fetchData(); }
finally { await native('ui.hideLoading'); }
```

**Output:** `{shown:true}` / `{hidden:true}` / `{showing:bool}`

---

## F-06 Snackbar

| | |
|---|---|
| **কী করে** | নিচে ছোট বার, একটি action বাটন সহ |
| **কেন দরকার** | "Undo" ধরনের কাজ |
| **মডিউল** | `NativeUICommandHandler` → `SnackbarManager` |
| **JS API** | `ui.showSnackbar` + `on('ui.snackbarAction')` |

```js
NativeRuntimeBridgeClient.on('ui.snackbarAction', p => undo(p.actionTitle));
await native('ui.showSnackbar', { message: 'মুছে ফেলা হয়েছে', actionTitle: 'ফিরিয়ে আনুন' });
```

**Output:** `{shown:true}`; ইভেন্ট `{actionTitle}`
**সীমা:** anchor view না থাকলে Toast-এ fallback হয়।

---

## F-07 Toolbar নিয়ন্ত্রণ

| | |
|---|---|
| **কী করে** | শিরোনাম, সাবটাইটেল, দৃশ্যমানতা, action বাটন |
| **কেন দরকার** | পেজ বদলালে শিরোনাম বদলানো |
| **মডিউল** | `NativeUICommandHandler` → `ToolbarManager` |
| **JS API** | `ui.setToolbarTitle`, `ui.setToolbarSubtitle`, `ui.setToolbarVisible`, `ui.addToolbarAction`, `ui.removeToolbarAction`, `ui.clearToolbarActions`, `ui.getToolbarActions` |

```js
await native('ui.setToolbarTitle', { title: 'ড্যাশবোর্ড' });
await native('ui.addToolbarAction', { id: 'save', label: 'সেভ' });
NativeRuntimeBridgeClient.on('ui.toolbarAction', p => { if (p.id === 'save') save(); });
```

**সীমা:** `addToolbarAction` শুধু `id` ও `label` নেয় — icon সেট করার API নেই।

---

## F-08 Drawer

| | |
|---|---|
| **কী করে** | সাইড নেভিগেশন ড্রয়ার |
| **মডিউল** | `NativeUICommandHandler` → `NavigationManager` |
| **JS API** | `ui.openDrawer`, `ui.closeDrawer`, `ui.toggleDrawer`, `ui.isDrawerOpen`, `ui.addDrawerItem`, `ui.removeDrawerItem`, `ui.selectDrawerItem`, `ui.getSelectedDrawerItem` |

```js
await native('ui.addDrawerItem', { id: 'home', title: 'হোম' });
NativeRuntimeBridgeClient.on('ui.drawerItemSelected', p => route(p.id));
```

**সীমা:** `addDrawerItem`-এ icon parameter নেই (সোর্সে `0` পাস হয়)।

---

## F-09 Bottom navigation ও badge

| | |
|---|---|
| **কী করে** | নিচের ট্যাব, badge সংখ্যা |
| **মডিউল** | `NativeUICommandHandler` → `NavigationManager` |
| **JS API** | `ui.selectTab`, `ui.addTab`, `ui.setBadge`, `ui.getBadge`, `ui.getTabs`, `ui.getSelectedTab` |

```js
await native('ui.setBadge', { tabId: 'inbox', count: 3 });
const { tabs } = await native('ui.getTabs');
```

**নোট:** `selectTab` শুধু UI state বদলায় — পেজ বদলায় না। পেজ বদলাতে `web.loadUrl` বা
নিজের SPA router ব্যবহার করুন।

---

## F-10 FAB

| | |
|---|---|
| **কী করে** | Floating Action Button দেখানো/লুকানো |
| **JS API** | `ui.setFabVisible`, `ui.isFabVisible` |

```js
await native('ui.setFabVisible', { visible: true });
```

**সীমা:** FAB-এর click ইভেন্ট bridge-এ নেই — শুধু দৃশ্যমানতা।

---

## F-11 System UI (status/nav bar, fullscreen)

| | |
|---|---|
| **কী করে** | Status bar রঙ, nav bar রঙ, fullscreen, immersive, edge-to-edge |
| **মডিউল** | `NativeUICommandHandler` → `SystemUIManager` |
| **JS API** | `ui.setStatusBar`, `ui.setNavigationBar`, `ui.setFullscreen`, `ui.setImmersive`, `ui.setEdgeToEdge`, `ui.getSystemUIState` |

```js
await native('ui.setStatusBar', { color: '#0F172A', lightIcons: true });
await native('ui.setEdgeToEdge', { enabled: true });
```

**নোট:** `ProjectActivity` ডিফল্টে immersive fullscreen চালায়। Project UI config-এ
fullscreen বন্ধ না করলে status bar লুকানো থাকবে।

---

## F-12 Theme পড়া

| | |
|---|---|
| **কী করে** | Project-এর UI config থেকে theme মান পড়া |
| **JS API** | `ui.getTheme`, `ui.getConfig` |

```js
const t = await native('ui.getTheme');
document.documentElement.dataset.theme = t.themeMode;
```

**সীমা:** `ui.applyTheme` আছে কিন্তু সেটি শুধু `NativeUIComponentManager.applyTheme(map)`
ডাকে — WebView-এর CSS বদলায় না। CSS নিজে বদলাতে হবে।

---

## F-13 WebView নেভিগেশন

| | |
|---|---|
| **কী করে** | URL লোড, reload, back/forward, history |
| **মডিউল** | `NativeWebCommandHandler` → `WebViewHost` |
| **JS API** | `web.loadUrl`, `web.loadHtml`, `web.loadSource`, `web.reload`, `web.stop`, `web.goBack`, `web.goForward`, `web.canGoBack`, `web.canGoForward`, `web.clearHistory`, `web.getState` |

```js
const s = await native('web.getState');
if (s.canGoBack) await native('web.goBack');
```

**সতর্কতা:** `web.loadUrl` দিয়ে অন্য origin-এ গেলে bridge origin gate-এ আটকে যেতে পারে
(নতুন origin allow-list-এ যোগ হয় না)।

---

## F-14 External link / Intent

| | |
|---|---|
| **কী করে** | ব্রাউজার/অন্য অ্যাপে URL খোলে |
| **JS API** | `ui.openExternal`, `device.openUrl`, `device.openExternal` |

```js
await native('device.openUrl', { url: 'https://example.com' });
```

**নিরাপত্তা:** যেকোনো scheme (`tel:`, `mailto:`, `intent:`) চলে — URL নিজে যাচাই করুন।

---

## F-15 Device ও App তথ্য

| | |
|---|---|
| **কী করে** | মডেল, Android version, screen, locale, safe area |
| **মডিউল** | `NativeDeviceCommandHandler` (+ `PlatformEngine` থাকলে সেখান থেকে) |
| **JS API** | `device.getInfo`, `device.getAppInfo`, `device.getDeviceInfo`, `device.getScreenInfo`, `device.getLocale`, `device.getSafeArea` |

```js
const info = await native('device.getInfo');
document.documentElement.style.setProperty('--safe-top', info.safeAreaTop + 'px');
```

**নোট:** `getInfo` সব key একসাথে দেয়; আলাদা কল কমাতে এটাই ব্যবহার করুন।

---

## F-16 Battery ও Network

| | |
|---|---|
| **কী করে** | ব্যাটারি শতাংশ, চার্জিং, নেটওয়ার্ক টাইপ |
| **JS API** | `device.getBattery`, `device.getNetworkState` |

```js
const b = await native('device.getBattery');
const n = await native('device.getNetworkState');
if (!n.connected) showOfflineBanner();
```

**সীমা:** কোনো live listener নেই — polling করতে হবে (৫–১০ সেকেন্ড)।

---

## F-17 Clipboard

| | |
|---|---|
| **কী করে** | টেক্সট কপি/পেস্ট |
| **JS API** | `device.copyToClipboard`, `device.readClipboard` |

```js
await native('device.copyToClipboard', { text: 'কোড: 1234', label: 'কোড' });
const { text } = await native('device.readClipboard');
```

**নিরাপত্তা:** Android 10+ এ clipboard পড়া সীমিত হতে পারে (foreground না হলে খালি)।

---

## F-18 Share

| | |
|---|---|
| **কী করে** | Android share sheet |
| **JS API** | `device.shareText` |

```js
await native('device.shareText', { text: 'দেখুন!', subject: 'লিংক', title: 'শেয়ার' });
```

**সীমা:** শুধু text। ফাইল শেয়ার নেই।

---

## F-19 Orientation, brightness, keep-screen-on

| | |
|---|---|
| **কী করে** | স্ক্রিন ঘোরানো, উজ্জ্বলতা, স্ক্রিন জাগিয়ে রাখা |
| **JS API** | `device.setOrientation`, `device.setBrightness`, `device.getBrightness`, `device.setKeepScreenOn` |

```js
await native('device.setOrientation', { orientation: 'landscape' });
await native('device.setKeepScreenOn', { enabled: true });
```

**সীমা:** `setBrightness` শুধু এই Activity-র window-এ কাজ করে, সিস্টেম সেটিং বদলায় না।

---

## F-20 Persistent storage

| | |
|---|---|
| **কী করে** | Key-value স্টোর, AES-GCM এনক্রিপ্টেড |
| **মডিউল** | `native-runtime-ui` → `NativeStorageCommandHandler` → `native-storage-engine` |
| **JS API** | `storage.get`, `storage.set`, `storage.remove`, `storage.clear`, `storage.keys`, `storage.has`, `storage.getAll` |

```js
await native('storage.set', { key: 'token', value: 'abc123' });
const r = await native('storage.get', { key: 'token' });
```

**নিরাপত্তা:** `EncryptedStore` (AES-GCM, Android Keystore key)। Project আলাদা origin
পেলেও storage **অ্যাপ-ওয়াইড শেয়ার্ড** — key-তে project prefix দিন।

---

## F-21 Download

| | |
|---|---|
| **কী করে** | HTTP/data:/blob: ফাইল `Download/RONiLiFEOS`-এ সেভ |
| **মডিউল** | `native-runtime-ui` → `DownloadCenter` |
| **JS API** | `<a download>` লিংক, অথবা `window.RONiDownload.download(url, name)`, `window.RONiDownload.saveBase64(b64, name, mime)` |

```js
// সরল লিংক — WebView download listener ধরে নেয়
const a = document.createElement('a');
a.href = 'https://example.com/file.pdf';
a.download = 'file.pdf';
a.click();

// blob (page-generated)
const blob = new Blob(['hello'], { type: 'text/plain' });
const url = URL.createObjectURL(blob);
const a2 = document.createElement('a');
a2.href = url; a2.download = 'hello.txt'; a2.click();
```

**Output:** Android notification + `Download/RONiLiFEOS/<name>`
**সীমা:** upload API নেই।

---

## F-22 Network request

| | |
|---|---|
| **কী করে** | সাধারণ `fetch` / `XMLHttpRequest` |
| **মডিউল** | WebView নিজেই (কোনো native API লাগে না) |
| **JS API** | `fetch()` |

```js
const res = await fetch('https://api.example.com/data');
const json = await res.json();
```

**নিরাপত্তা:** `mixedContentAllowed=false` — HTTP endpoint HTTPS পেজ থেকে ব্লক হবে।
CORS স্বাভাবিকভাবে প্রযোজ্য।

---

## F-23 File chooser (upload)

| | |
|---|---|
| **কী করে** | `<input type="file">` → Android file picker |
| **মডিউল** | `WebViewHost.onShowFileChooser` |
| **JS API** | `<input type="file">` |

```html
<input type="file" accept="image/*" id="picker">
<script>
  document.getElementById('picker').addEventListener('change', function (e) {
    const file = e.target.files[0];
    if (file) console.log(file.name, file.size);
  });
</script>
```

**শর্ত:** `WebHostConfig.fileChooserEnabled = true` (ডিফল্ট true)।

---

## F-24 Camera / Mic (WebRTC)

| | |
|---|---|
| **কী করে** | `getUserMedia()` |
| **মডিউল** | `WebViewHost.onPermissionRequest` → `WebPermissionHandler` |
| **JS API** | `navigator.mediaDevices.getUserMedia()` |

```js
const stream = await navigator.mediaDevices.getUserMedia({ video: true });
```

**গুরুত্বপূর্ণ:** `WebPermissionHandler` সেট না থাকলে **সব permission deny** হয়।
অ্যাপ ডেভেলপারকে `WebHostConfig.builder().permissionHandler(...)` দিতে হবে।
তাই ডিফল্ট বিল্ডে camera/mic কাজ করবে না।

---

## F-25 Geolocation

| | |
|---|---|
| **কী করে** | `navigator.geolocation` |
| **মডিউল** | `WebViewHost.onGeolocationPermissionsShowPrompt` |
| **শর্ত** | `WebPermissionHandler.onGeolocationRequest` implement করা থাকতে হবে |

**ডিফল্ট:** deny।

---

## F-26 Custom event (Native → Web)

| | |
|---|---|
| **কী করে** | Native কোড থেকে পেজে ইভেন্ট পাঠানো |
| **মডিউল** | `WebBridge.emitToWeb(topic, data)` |
| **JS API** | `NativeRuntimeBridgeClient.on(topic, fn)` |

```js
NativeRuntimeBridgeClient.on('order.updated', p => refresh(p.orderId));
```

**নোট:** `web.emitEvent` action দিয়েও পেজ নিজে ইভেন্ট পাঠাতে পারে (নিজেই শোনে)।

---

## F-27 App lifecycle

| | |
|---|---|
| **কী করে** | Activity বন্ধ করা, app settings খোলা |
| **JS API** | `device.finish`, `device.openAppSettings` |

```js
await native('device.openAppSettings');
```

**সীমা:** `onPause`/`onResume` ইভেন্ট bridge-এ নেই। `document.visibilitychange` ব্যবহার করুন।

---

## F-28 Form state restore

| | |
|---|---|
| **কী করে** | Project minimize হলে form input সেভ, ফিরলে restore |
| **মডিউল** | `app` → `FormStateScript`, `FormStateStore` |
| **JS API** | কোনোটি নেই — স্বয়ংক্রিয় |

**শর্ত:** input-এ `id` বা `name` থাকতে হবে। restore শুধু খালি field পূরণ করে।

---

## F-29 যা নেই (স্পষ্টভাবে)

| ফিচার | অবস্থা |
|---|---|
| Upload API (native) | ❌ নেই — `<input type="file">` ব্যবহার করুন |
| File read/write API | ❌ নেই |
| Notification API | ❌ নেই |
| Camera API (native) | ❌ নেই — WebRTC + permission handler |
| Contacts / SMS / Call | ❌ নেই |
| Biometric prompt (JS) | ❌ নেই (`SecurityCommandHandler` internal) |
| In-app purchase | ❌ নেই |
| SQLite / IndexedDB bridge | ❌ নেই — WebView-এর নিজের IndexedDB চলে |
| Session API | ❌ নেই |
| Analytics API | ❌ নেই (`AnalyticsCommandHandler` internal) |
| Theme apply (CSS) | ❌ নেই — শুধু `ui.getTheme` |
| FAB click event | ❌ নেই |
| Network change listener | ❌ নেই — polling |
| Battery change listener | ❌ নেই — polling |
