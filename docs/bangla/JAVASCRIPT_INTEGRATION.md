# JavaScript API Reference — সম্পূর্ণ তালিকা

> এই ফাইলের প্রতিটি action সোর্স কোডের `switch (action)` কেস থেকে নেওয়া।
> যেগুলো এখানে নেই, সেগুলো **নেই** — অনুমান করে লিখবেন না।

---

## ০. Transport

```js
// সব কল এই একটি ফাংশন দিয়েই হয়
NativeRuntimeBridgeClient.call(action, payload)   // → Promise<payload>
NativeRuntimeBridgeClient.on(topic, listener)     // → void
NativeRuntimeBridgeClient.off(topic, listener)    // → void
NativeRuntimeBridgeClient.ping()                  // → "pong"
```

**Availability check:**

```js
function hasNative() {
  return typeof NativeRuntimeBridgeClient !== 'undefined'
      && typeof NativeRuntimeBridgeClient.call === 'function';
}
```

**নিরাপদ wrapper (সব উদাহরণে এটাই ব্যবহার করা হয়েছে):**

```js
async function native(action, payload) {
  if (!hasNative()) throw new Error('NATIVE_UNAVAILABLE');
  return NativeRuntimeBridgeClient.call(action, payload || {});
}
```

---

## ১. `ui.*` — ৬২টি action

### ১.১ Overlay / Dialog

| Action | Payload | Response | ইভেন্ট |
|---|---|---|---|
| `ui.showAlert` | `{title?, message?}` | `{shown:true}` | — |
| `ui.showConfirm` | `{title?, message?, confirmText?, cancelText?}` | `{shown:true}` | `ui.confirm` → `{confirmed:bool}` |
| `ui.showToast` | `{message?, duration?:"short"\|"long", type?:"info"\|"success"\|"warning"\|"error"}` | `{shown:true}` | — |
| `ui.showBottomSheet` | `{title?, items?:[{title, destructive?}]}` | `{shown:true}` | `ui.actionSelected` → `{title, index}` |
| `ui.showActionSheet` | `{title?, items?:[…]}` | `{shown:true}` | `ui.actionSelected` |
| `ui.showLoading` | `{text?, cancelable?}` | `{shown:true}` | — |
| `ui.hideLoading` | `{}` | `{hidden:true}` | — |
| `ui.showSnackbar` | `{message?, actionTitle?, duration?}` | `{shown:true}` | `ui.snackbarAction` → `{actionTitle}` |
| `ui.showPopupMenu` | `{items?:[{id?, title}]}` | `{shown:true}` | `ui.popupItemSelected` → `{title, index}` |
| `ui.dismissDialog` | `{}` | `{dismissed:true}` | — |
| `ui.isDialogShowing` | `{}` | `{showing:bool}` | — |
| `ui.dismissBottomSheet` | `{}` | `{dismissed:true}` | — |
| `ui.isBottomSheetVisible` | `{}` | `{visible:bool}` | — |
| `ui.dismissToast` | `{}` | `{dismissed:true}` | — |
| `ui.isToastVisible` | `{}` | `{visible:bool}` | — |
| `ui.isLoading` | `{}` | `{showing:bool}` | — |
| `ui.dismissActionSheet` | `{}` | `{dismissed:true}` | — |
| `ui.getActionSheetState` | `{}` | `{visible:bool}` | — |

**উদাহরণ — Confirm:**

```js
NativeRuntimeBridgeClient.on('ui.confirm', function (p) {
  console.log(p.confirmed ? 'ঠিক আছে চাপা হয়েছে' : 'বাতিল');
});

await native('ui.showConfirm', {
  title: 'মুছে ফেলবেন?',
  message: 'এই কাজটি ফেরানো যাবে না।',
  confirmText: 'মুছুন',
  cancelText: 'না'
});
// এখানে await শেষ মানে dialog দেখানো হয়েছে — উত্তর এখনো আসেনি
```

**উদাহরণ — Bottom sheet:**

```js
NativeRuntimeBridgeClient.on('ui.actionSelected', function (p) {
  console.log('index', p.index, 'title', p.title);
});

await native('ui.showBottomSheet', {
  title: 'কী করতে চান?',
  items: [
    { title: 'শেয়ার' },
    { title: 'ডাউনলোড' },
    { title: 'মুছে ফেলুন', destructive: true }
  ]
});
```

### ১.২ Toolbar

| Action | Payload | Response | ইভেন্ট |
|---|---|---|---|
| `ui.setToolbarTitle` | `{title}` | `{title}` | — |
| `ui.setToolbarSubtitle` | `{subtitle}` | `{subtitle}` | — |
| `ui.setToolbarVisible` | `{visible:bool}` | `{visible}` | — |
| `ui.setNavigationIconVisible` | `{visible:bool}` | `{visible}` | — |
| `ui.addToolbarAction` | `{id?, label?}` | `{id}` | `ui.toolbarAction` → `{id, label}` |
| `ui.removeToolbarAction` | `{id}` | `{removed:true}` | — |
| `ui.clearToolbarActions` | `{}` | `{cleared:true}` | — |
| `ui.getToolbarActions` | `{}` | `{actions:[{id,label}]}` | — |

```js
NativeRuntimeBridgeClient.on('ui.toolbarAction', function (p) {
  if (p.id === 'refresh') location.reload();
});

await native('ui.addToolbarAction', { id: 'refresh', label: 'রিফ্রেশ' });
```

### ১.৩ Drawer

| Action | Payload | Response | ইভেন্ট |
|---|---|---|---|
| `ui.openDrawer` | `{}` | `{open:true}` | — |
| `ui.closeDrawer` | `{}` | `{open:false}` | — |
| `ui.toggleDrawer` | `{}` | `{open:bool}` | — |
| `ui.isDrawerOpen` | `{}` | `{open:bool}` | — |
| `ui.selectDrawerItem` | `{id}` | `{selected:id}` | — |
| `ui.getSelectedDrawerItem` | `{}` | `{id}` | — |
| `ui.addDrawerItem` | `{id?, title?}` | `{id}` | `ui.drawerItemSelected` → `{id, title}` |
| `ui.removeDrawerItem` | `{id}` | `{removed:true}` | — |

### ১.৪ Bottom navigation

| Action | Payload | Response |
|---|---|---|
| `ui.selectTab` | `{tabId}` | `{selectedTab}` |
| `ui.addTab` | `{tabId?, label?}` | `{tabId}` |
| `ui.setBadge` | `{tabId, count:number}` | `{count}` |
| `ui.getBadge` | `{tabId}` | `{count}` |
| `ui.getTabs` | `{}` | `{tabs:[{id,label,badge}]}` |
| `ui.getSelectedTab` | `{}` | `{tabId}` |

```js
await native('ui.setBadge', { tabId: 'inbox', count: 7 });
const { tabs } = await native('ui.getTabs');
```

### ১.৫ FAB

| Action | Payload | Response |
|---|---|---|
| `ui.setFabVisible` | `{visible:bool}` | `{visible}` |
| `ui.isFabVisible` | `{}` | `{visible:bool}` |

### ১.৬ Component state

| Action | Payload | Response |
|---|---|---|
| `ui.isComponentEnabled` | `{category, key}` | `{enabled:bool}` |
| `ui.setComponentEnabled` | `{category, key, enabled}` | `{enabled}` |
| `ui.setComponentVisible` | `{category, key, visible}` | `{visible, applied, reason?}` |
| `ui.getComponentState` | `{category, key}` | `{key, category, enabled, visible}` |
| `ui.getComponentCount` | `{}` | `{count:number}` |
| `ui.listComponents` | `{}` | `{components:[{key,name,category,enabled,visible}]}` |

`category` মান: `UICategory` enum-এর নাম (যেমন `APP_BAR`, `NAVIGATION`, `CONTENT`, `SYSTEM_UI`)।
`key` মান: `UIComponentType.getKey()` (যেমন `TOOLBAR`, `WEB_VIEW`, `BOTTOM_NAVIGATION`)।

> `setComponentVisible`-এ `applied:false` এলে বোঝায় component এই Activity-তে attach করা নেই।

### ১.৭ System UI ও Theme

| Action | Payload | Response |
|---|---|---|
| `ui.setStatusBar` | `{color:"#RRGGBB", lightIcons?:bool}` | `{color:int}` |
| `ui.setNavigationBar` | `{color:"#RRGGBB", lightButtons?:bool}` | `{color:int}` |
| `ui.setFullscreen` | `{enabled:bool}` | `{enabled}` |
| `ui.setImmersive` | `{enabled:bool}` | `{enabled}` |
| `ui.setEdgeToEdge` | `{enabled:bool}` | `{enabled}` |
| `ui.getSystemUIState` | `{}` | `{fullscreen, immersive, edgeToEdge, statusBarColor, navBarColor, lightStatusBar, lightNavBar}` |
| `ui.applyTheme` | `{…key:value}` | `{applied:true}` |
| `ui.getTheme` | `{}` | `{themeMode, primaryColor, backgroundColor, statusBarColor, navBarColor, lightStatusBar, lightNavBar}` |
| `ui.getConfig` | `{}` | নিচে দেখুন |

`ui.getConfig` response keys:
`toolbarEnabled, drawerEnabled, bottomNavEnabled, webViewEnabled, fabEnabled, systemUIEnabled,
fullScreenWebHost, toolbarTitle, toolbarSubtitle, drawerHeaderTitle, drawerHeaderSubtitle,
webViewInitialUrl, themeMode, primaryColor, backgroundColor, statusBarColor, navBarColor,
lightStatusBar, lightNavBar, fullscreen, immersive, edgeToEdge`

### ১.৮ WebView convenience

| Action | Payload | Response |
|---|---|---|
| `ui.reload` | `{}` | `{reloaded:bool}` |
| `ui.getWebViewState` | `{}` | `{url, canGoBack, canGoForward}` |
| `ui.setWebViewSource` | `{type, location, entryPoint?, html?}` | `{loaded:bool}` |
| `ui.navigateBack` | `{}` | `{navigated:bool}` |
| `ui.openExternal` | `{url}` | `{opened:bool}` |

`type` মান: `remote` (ডিফল্ট), `asset`, `file`, `zip`, `zipfile`, `inline`, `content`।

---

## ২. `web.*` — ৫৫টি action

### ২.১ লোডিং ও নেভিগেশন

| Action | Payload | Response |
|---|---|---|
| `web.loadSource` | `{type, location, entryPoint?, html?}` | `{loaded:true}` |
| `web.loadUrl` | `{url}` | `{loaded:true}` |
| `web.loadHtml` | `{html, baseUrl?}` | `{loaded:true}` |
| `web.reload` | `{}` | `{reloaded:true}` |
| `web.stop` | `{}` | `{stopped:true}` |
| `web.goBack` | `{}` | `{navigated:true}` |
| `web.goForward` | `{}` | `{navigated:true}` |
| `web.canGoBack` | `{}` | `{canGoBack:bool}` |
| `web.canGoForward` | `{}` | `{canGoForward:bool}` |
| `web.clearHistory` | `{}` | `{cleared:true}` |
| `web.getState` | `{}` | `{url, canGoBack, canGoForward, title, progress}` |

### ২.২ Script ও ইভেন্ট

| Action | Payload | Response |
|---|---|---|
| `web.evaluate` | `{script}` | `{evaluated:true, result}` বা `{evaluated:false, error:"TIMEOUT"}` |
| `web.emitEvent` | `{topic, data?:object}` | `{emitted:true}` |

> `web.evaluate` নিজের পেজে নিজে কল করবেন না — recursion হবে। এটি মূলত native/debug
> থেকে পেজে script চালানোর জন্য। পেজের ভেতরে সাধারণ JavaScript লিখুন।
>
> `web.emitEvent` দিয়ে নিজের ইভেন্ট বানিয়ে নিজেই শুনতে পারেন — তবে সাধারণভাবে
> `window.dispatchEvent(new CustomEvent(...))` সহজ।

### ২.৩ Zoom ও font

| Action | Payload | Response |
|---|---|---|
| `web.setZoom` | `{zoom:number}` | `{zoom}` |
| `web.getZoom` | `{}` | `{zoom}` |
| `web.setTextZoom` | `{zoom:number}` | `{zoom}` |
| `web.getTextZoom` | `{}` | `{zoom}` |
| `web.setDefaultFontSize` | `{size:number}` | `{size}` |
| `web.setMinimumFontSize` | `{size:number}` | `{size}` |
| `web.setStandardFontFamily` | `{family}` | `{family}` |
| `web.setDefaultTextEncodingName` | `{encoding}` | `{encoding}` |

### ২.৪ User agent, cookie, cache

| Action | Payload | Response |
|---|---|---|
| `web.setUserAgent` | `{userAgent}` | `{userAgent}` |
| `web.getUserAgent` | `{}` | `{userAgent}` |
| `web.setCookie` | `{url, value}` | `{set:true}` |
| `web.getCookies` | `{url}` | `{cookies:string}` |
| `web.clearCookies` | `{}` | `{cleared:true}` |
| `web.clearCache` | `{}` | `{cleared:true}` |
| `web.setCacheMode` | `{mode:"default"\|"cache"\|"no_cache"\|"cache_only"}` | `{cacheMode:int}` |

### ২.৫ WebSettings

| Action | Payload | Response |
|---|---|---|
| `web.getSettings` | `{}` | নিচের সব key |
| `web.setSettings` | যেকোনো subset | নতুন settings map |
| `web.setJavaScript` | `{enabled}` | `{enabled}` |
| `web.setDomStorage` | `{enabled}` | `{enabled}` |
| `web.setDatabaseEnabled` | `{enabled}` | `{enabled}` |
| `web.setGeolocationEnabled` | `{enabled}` | `{enabled}` |
| `web.setSaveFormData` | `{enabled}` | `{enabled}` |
| `web.setAllowFileAccess` | `{enabled}` | `{enabled}` |
| `web.setAllowContentAccess` | `{enabled}` | `{enabled}` |
| `web.setSupportZoom` | `{enabled}` | `{enabled}` |
| `web.setBuiltInZoomControls` | `{enabled}` | `{enabled}` |
| `web.setDisplayZoomControls` | `{enabled}` | `{enabled}` |
| `web.setLoadWithOverviewMode` | `{enabled}` | `{enabled}` |
| `web.setUseWideViewPort` | `{enabled}` | `{enabled}` |
| `web.setJavaScriptCanOpenWindowsAutomatically` | `{enabled}` | `{enabled}` |
| `web.setSupportMultipleWindows` | `{enabled}` | `{enabled}` |
| `web.setMediaPlaybackRequiresUserGesture` | `{enabled}` | `{enabled}` |
| `web.setOffscreenPreRaster` | `{enabled}` | `{enabled}` |
| `web.setBlockNetworkImage` | `{enabled}` | `{enabled}` |
| `web.setBlockNetworkLoads` | `{enabled}` | `{enabled}` |
| `web.setLoadsImagesAutomatically` | `{enabled}` | `{enabled}` |
| `web.setSafeBrowsing` | `{enabled}` | `{enabled}` |
| `web.setAlgorithmicDarkeningAllowed` | `{enabled}` | `{enabled}` |
| `web.setMixedContentMode` | `{mode:"always"\|"never"\|"compatibility"}` | `{mode}` |
| `web.setForceDark` | `{mode:"auto"\|"on"\|"off"}` | `{mode}` |

`web.getSettings` response keys:
`javaScriptEnabled, domStorageEnabled, databaseEnabled, geolocationEnabled, saveFormData,
supportZoom, builtInZoomControls, displayZoomControls, loadWithOverviewMode, useWideViewPort,
defaultTextEncodingName, standardFontFamily, defaultFontSize, minimumFontSize, textZoom,
userAgent, allowFileAccess, allowContentAccess, javaScriptCanOpenWindowsAutomatically,
supportMultipleWindows, blockNetworkImage, blockNetworkLoads, loadsImagesAutomatically,
cacheMode, mediaPlaybackRequiresUserGesture, mixedContentMode, offscreenPreRaster,
safeBrowsingEnabled, forceDark, algorithmicDarkeningAllowed`

### ২.৬ Appearance

| Action | Payload | Response |
|---|---|---|
| `web.setBackgroundColor` | `{color:"#RRGGBB"}` | `{color}` |
| `web.setProgressBarVisible` | `{visible:bool}` | `{visible}` |

---

## ৩. `device.*` — ২৮টি action

### ৩.১ তথ্য

| Action | Payload | Response |
|---|---|---|
| `device.getInfo` | `{}` | app + device + screen + locale + safeArea একসাথে |
| `device.getAppInfo` | `{}` | `{packageName, appName, versionName, versionCode, …}` |
| `device.getDeviceInfo` | `{}` | `{model, brand, manufacturer, device, product, androidVersion, sdkInt, …}` |
| `device.getScreenInfo` | `{}` | `{width, height, density, densityDpi, …}` |
| `device.getLocale` | `{}` | `{language, country, …}` |
| `device.getSafeArea` | `{}` | `{top, bottom, left, right}` (px) |
| `device.getNetworkState` | `{}` | `{connected, type, metered, roaming, source}` |
| `device.getBattery` | `{}` | `{level, scale, percent, status, plugged, temperature, voltage, charging?, health?, source}` |

```js
const info = await native('device.getInfo');
console.log(info.model, info.androidVersion, info.width + 'x' + info.height);
```

### ৩.২ Clipboard ও Share

| Action | Payload | Response |
|---|---|---|
| `device.copyToClipboard` | `{text, label?}` | `{copied:bool}` |
| `device.setClipboard` | `{text, label?}` | `{copied:bool}` (alias) |
| `device.readClipboard` | `{}` | `{text:string}` |
| `device.getClipboard` | `{}` | `{text:string}` (alias) |
| `device.shareText` | `{text, subject?, title?}` | `{shared:true}` |
| `device.share` | `{text, subject?, title?}` | `{shared:true}` (alias) |

### ৩.৩ Hardware ও window

| Action | Payload | Response |
|---|---|---|
| `device.vibrate` | `{}` | `{vibrated:true}` |
| `device.haptic` | `{}` | `{vibrated:true}` (alias) |
| `device.setOrientation` | `{orientation:"portrait"\|"landscape"\|"reverse_portrait"\|"reverse_landscape"\|"sensor"\|"auto"}` | `{orientation}` |
| `device.setKeepScreenOn` | `{enabled:bool}` | `{enabled}` |
| `device.setBrightness` | `{value:0.0–1.0}` (`-1` = সিস্টেম ডিফল্ট) | `{brightness:float}` |
| `device.getBrightness` | `{}` | `{brightness:float}` |

> `device.vibrate` আসলে `performHapticFeedback(VIRTUAL_KEY)` — ছোট একটি স্পন্দন।
> দীর্ঘ vibration pattern নেই।

### ৩.৪ Intent ও অ্যাপ

| Action | Payload | Response |
|---|---|---|
| `device.openUrl` | `{url}` | `{opened:bool}` |
| `device.openExternal` | `{url}` | `{opened:bool}` (alias) |
| `device.openApp` | `{packageName}` | `{opened:bool}` |
| `device.isAppInstalled` | `{packageName}` | `{installed:bool}` |
| `device.openAppSettings` | `{}` | `{opened:true}` |
| `device.openFile` | `{uri, mimeType?}` | `{opened:true}` |
| `device.finish` | `{}` | `{finished:true}` |
| `device.log` | `{message, tag?}` | `{logged:true}` |

> `device.finish` Activity বন্ধ করে — ব্যবহারকারী ড্যাশবোর্ডে ফিরে যাবেন।
> `device.openFile`-এ `content://` URI দিলে `FLAG_GRANT_READ_URI_PERMISSION` যোগ হয়।

---

## ৪. `storage.*` — ৭টি action

| Action | Payload | Response |
|---|---|---|
| `storage.get` | `{key}` | `{key, value, exists:bool}` |
| `storage.set` | `{key, value}` | `{saved:true}` |
| `storage.remove` | `{key}` | `{removed:true}` |
| `storage.clear` | `{}` | `{cleared:true}` |
| `storage.keys` | `{}` | `{keys:[string]}` |
| `storage.has` | `{key}` | `{exists:bool}` |
| `storage.getAll` | `{}` | `{key:value, …}` (সরাসরি map) |

**টাইপ সমর্থন:** `string`, `boolean`, `int`, `long`, `float`, `double`।
`null` → `"s:"` (খালি string) হিসেবে সেভ হয়।

```js
await native('storage.set', { key: 'theme', value: 'dark' });
const r = await native('storage.get', { key: 'theme' });
console.log(r.exists, r.value);   // true "dark"
```

**Object/Array সেভ করতে JSON.stringify করুন:**

```js
await native('storage.set', { key: 'user', value: JSON.stringify({ id: 7, name: 'রনি' }) });
const raw = await native('storage.get', { key: 'user' });
const user = raw.exists ? JSON.parse(raw.value) : null;
```

> `storage.getAll` response-এ `{key: value}` map সরাসরি আসে — `{values:{…}}` নয়।

---

## ৫. `runtime.*` — ৬টি action

| Action | Payload | Response |
|---|---|---|
| `runtime.loadUrl` | `{url}` | `{currentUrl}` |
| `runtime.getVitals` | `{}` | vitals metrics map |
| `runtime.resolveSource` | `{url}` | `{url, statusCode, mimeType, isFound}` |
| `runtime.setCookie` | `{domain, cookie}` | `{stored:true}` |
| `runtime.getCookies` | `{domain}` | `{cookies:[string]}` |
| `runtime.clearCookies` | `{}` | `{cleared:true}` |

> `runtime.loadUrl` `NativeRuntimeService`-এর **default container**-এ লোড করে, যে container
> আপনার Project-এর WebView নয়। বাস্তবে এটি ব্যবহার করবেন না — `web.loadUrl` বা
> `ui.setWebViewSource` ব্যবহার করুন।

---

## ৬. অন্যান্য route

| Action | Payload | Response |
|---|---|---|
| `bridge.dispatch` | যেকোনো | `{status:"handled", action, requestId}` |
| `echo` | যেকোনো | `{echoPath, …}` |
| `system` | `{}` | diagnostic তথ্য |

এগুলো ডায়াগনস্টিক — সাধারণ অ্যাপে দরকার নেই।

---

## ৭. Error code তালিকা

| Error string | কোথা থেকে | কারণ |
|---|---|---|
| `ROUTE_NOT_FOUND` | `WebBridge` | এই path-এ কোনো handler নেই |
| `ORIGIN_REJECTED: <origin>` | `WebBridge` | origin allow-list-এ নেই |
| `PAYLOAD_TOO_LARGE` | `WebBridge` | > 1 MiB |
| `REQUEST_TIMEOUT` | `WebBridge` sweeper | ১০ সেকেন্ডে উত্তর নেই |
| `Security validation failed` | `BridgeSecurityValidator` | `id` বা `action` খালি |
| `UNKNOWN_ACTION` | প্রতিটি handler | action নাম ভুল |
| `MISSING_ACTION` | ui/web/device | action খালি |
| `INVALID_REQUEST` | সব handler | request null |
| `INTERNAL_ERROR` | সব handler | Java exception |
| `STORAGE_UNAVAILABLE` | `NativeStorageCommandHandler` | `StorageEngine` init হয়নি |
| `WEBVIEW_UNAVAILABLE` | `NativeWebCommandHandler` | WebView host নেই |
| `MAIN_THREAD_TIMEOUT` | `NativeWebCommandHandler` | ১০s-এ main thread সাড়া দেয়নি |
| `MAIN_THREAD_BLOCKED` | `web.evaluate` | main thread থেকে কল |
| `MAIN_THREAD_UNAVAILABLE` | `NativeWebCommandHandler` | handler post ব্যর্থ |

**JS-এ error handle করার নিয়ম:**

```js
try {
  await native('ui.showToast', { message: 'x' });
} catch (err) {
  // err.message হলো উপরের string
  if (err.message === 'ROUTE_NOT_FOUND') {
    console.warn('এই API এই বিল্ডে নেই');
  }
}
```

---

## ৮. যা এই রেফারেন্সে নেই (এবং কেন)

| চাওয়া হয়েছে | অবস্থা |
|---|---|
| `NativeRuntime.ui` | ❌ native নয় — উদাহরণের wrapper |
| `NativeRuntime.session` | ❌ নেই |
| `NativeRuntime.app` | ❌ নেই (`device.getAppInfo` ব্যবহার করুন) |
| `NativeRuntime.events` | ⚠️ `NativeRuntimeBridgeClient.on/off` আছে |
| `NativeRuntime.network` | ❌ bridge-এ নেই (`device.getNetworkState` আছে) |
| `NativeRuntime.download` | ⚠️ `window.RONiDownload` আছে |
| `NativeRuntime.navigation` | ⚠️ `web.*` + `ui.navigateBack` |
| `NativeRuntime.device` | ✅ `device.*` |
| `NativeRuntime.storage` | ✅ `storage.*` |
