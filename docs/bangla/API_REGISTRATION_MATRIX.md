# API Registration Matrix — সব action বনাম Java handler

> এই ফাইলটি `tasks/verify/extract_actions.py` দিয়ে **স্বয়ংক্রিয়ভাবে** তৈরি — হাতে লেখা নয়।
> সোর্স: প্রতিটি handler-এর `switch (action)` ব্লক, `SUPPORTED_ACTIONS` অ্যারে এবং
> `NativeRuntimeUI.registerBridgeRoutes()`।

## ১. রাউটিং কীভাবে কাজ করে

`registerBridgeRoutes()` দুইভাবে route যোগ করে:

```java
bridge.getRouter().registerHandler(commandHandler);          // path = "ui"  → prefix route
for (String action : NativeUICommandHandler.SUPPORTED_ACTIONS) {
    bridge.getRouter().registerRoute(action, commandHandler); // "showToast" → bare route
}
```

JS সবসময় **prefix** দিয়ে ডাকে (`ui.showToast`)। তাই:

| স্তর | কী মেলে | ফল |
|---|---|---|
| prefix route (`ui`) | `ui.*` যেকোনো কিছু | handler-এ পৌঁছায়, তারপর switch-এ মিলতে হবে |
| bare route (`showToast`) | শুধু `showToast` | JS থেকে **কখনো** ব্যবহৃত হয় না |

অর্থাৎ **প্রকৃত dispatch table হলো `switch (action)`-এর case তালিকা**, `SUPPORTED_ACTIONS` নয়।
`SUPPORTED_ACTIONS` শুধু native caller-দের জন্য bare route বানায়।

## ২. সারসংক্ষেপ

| Handler | Path | SUPPORTED_ACTIONS | switch case | অবস্থা |
|---|---|---|---|---|
| `ui` | `ui` | 62 | 62 | ✅ রেজিস্টার্ড |
| `web` | `web` | 55 | 55 | ✅ রেজিস্টার্ড |
| `device` | `device` | 28 | 33 | ✅ রেজিস্টার্ড |
| `storage` | `storage` | 7 | 7 | ✅ রেজিস্টার্ড |
| **মোট** | | **152** | | |

`device`-এ ৩৩টি case দেখা যায়, কিন্তু ৫টি (`portrait`, `landscape`, `reverse_portrait`, `reverse_landscape`, `sensor`) `setOrientation`-এর **ভেতরের** switch — action নয়। তাই প্রকৃত action সংখ্যা **২৮**।

## ৩. রেজিস্টার্ড action — পূর্ণ তালিকা

### ৩.1 `ui.*` — 62টি action

| # | Action | Parameters | Error codes |
|---|---|---|---|
| 1 | `ui.addDrawerItem` | `id`, `title` | — |
| 2 | `ui.addTab` | `label`, `tabId` | — |
| 3 | `ui.addToolbarAction` | `id`, `label` | — |
| 4 | `ui.applyTheme` | — | — |
| 5 | `ui.clearToolbarActions` | — | — |
| 6 | `ui.closeDrawer` | — | — |
| 7 | `ui.dismissActionSheet` | — | — |
| 8 | `ui.dismissBottomSheet` | — | — |
| 9 | `ui.dismissDialog` | — | — |
| 10 | `ui.dismissToast` | — | — |
| 11 | `ui.getActionSheetState` | — | — |
| 12 | `ui.getBadge` | `tabId` | — |
| 13 | `ui.getComponentCount` | — | — |
| 14 | `ui.getComponentState` | `category`, `key` | — |
| 15 | `ui.getConfig` | — | — |
| 16 | `ui.getSelectedDrawerItem` | — | — |
| 17 | `ui.getSelectedTab` | — | — |
| 18 | `ui.getSystemUIState` | — | — |
| 19 | `ui.getTabs` | — | — |
| 20 | `ui.getTheme` | — | — |
| 21 | `ui.getToolbarActions` | — | — |
| 22 | `ui.getWebViewState` | — | — |
| 23 | `ui.hideLoading` | — | — |
| 24 | `ui.isBottomSheetVisible` | — | — |
| 25 | `ui.isComponentEnabled` | `category`, `key` | — |
| 26 | `ui.isDialogShowing` | — | — |
| 27 | `ui.isDrawerOpen` | — | — |
| 28 | `ui.isFabVisible` | — | — |
| 29 | `ui.isLoading` | — | — |
| 30 | `ui.isToastVisible` | — | — |
| 31 | `ui.listComponents` | — | — |
| 32 | `ui.navigateBack` | — | — |
| 33 | `ui.openDrawer` | — | — |
| 34 | `ui.openExternal` | `url` | `UNKNOWN_ACTION` |
| 35 | `ui.reload` | — | — |
| 36 | `ui.removeDrawerItem` | `id` | — |
| 37 | `ui.removeToolbarAction` | `id` | — |
| 38 | `ui.selectDrawerItem` | `id` | — |
| 39 | `ui.selectTab` | `tabId` | — |
| 40 | `ui.setBadge` | `tabId` | — |
| 41 | `ui.setComponentEnabled` | `category`, `key` | — |
| 42 | `ui.setComponentVisible` | `category`, `key` | — |
| 43 | `ui.setEdgeToEdge` | — | — |
| 44 | `ui.setFabVisible` | — | — |
| 45 | `ui.setFullscreen` | — | — |
| 46 | `ui.setImmersive` | — | — |
| 47 | `ui.setNavigationBar` | `color` | — |
| 48 | `ui.setNavigationIconVisible` | — | — |
| 49 | `ui.setStatusBar` | `color` | — |
| 50 | `ui.setToolbarSubtitle` | `subtitle` | — |
| 51 | `ui.setToolbarTitle` | `title` | — |
| 52 | `ui.setToolbarVisible` | — | — |
| 53 | `ui.setWebViewSource` | — | — |
| 54 | `ui.showActionSheet` | `title` | — |
| 55 | `ui.showAlert` | `message`, `title` | — |
| 56 | `ui.showBottomSheet` | `title` | — |
| 57 | `ui.showConfirm` | `cancelText`, `confirmText`, `message`, `title` | — |
| 58 | `ui.showLoading` | `text` | — |
| 59 | `ui.showPopupMenu` | — | — |
| 60 | `ui.showSnackbar` | `actionTitle`, `duration`, `message` | — |
| 61 | `ui.showToast` | `duration`, `message`, `type` | — |
| 62 | `ui.toggleDrawer` | — | — |

### ৩.2 `web.*` — 55টি action

| # | Action | Parameters | Error codes |
|---|---|---|---|
| 1 | `web.canGoBack` | — | — |
| 2 | `web.canGoForward` | — | — |
| 3 | `web.clearCache` | — | — |
| 4 | `web.clearCookies` | — | — |
| 5 | `web.clearHistory` | — | — |
| 6 | `web.emitEvent` | `topic` | — |
| 7 | `web.evaluate` | `script` | `MAIN_THREAD_BLOCKED` |
| 8 | `web.getCookies` | `url` | — |
| 9 | `web.getSettings` | — | — |
| 10 | `web.getState` | — | — |
| 11 | `web.getTextZoom` | — | — |
| 12 | `web.getUserAgent` | — | — |
| 13 | `web.getZoom` | — | — |
| 14 | `web.goBack` | — | — |
| 15 | `web.goForward` | — | — |
| 16 | `web.loadHtml` | `baseUrl`, `html` | — |
| 17 | `web.loadSource` | — | — |
| 18 | `web.loadUrl` | `url` | — |
| 19 | `web.reload` | — | — |
| 20 | `web.setAlgorithmicDarkeningAllowed` | — | — |
| 21 | `web.setAllowContentAccess` | — | — |
| 22 | `web.setAllowFileAccess` | — | — |
| 23 | `web.setBackgroundColor` | `color` | — |
| 24 | `web.setBlockNetworkImage` | — | — |
| 25 | `web.setBlockNetworkLoads` | — | — |
| 26 | `web.setBuiltInZoomControls` | — | — |
| 27 | `web.setCacheMode` | `mode` | — |
| 28 | `web.setCookie` | `url`, `value` | — |
| 29 | `web.setDatabaseEnabled` | — | — |
| 30 | `web.setDefaultFontSize` | — | — |
| 31 | `web.setDefaultTextEncodingName` | `encoding` | — |
| 32 | `web.setDisplayZoomControls` | — | — |
| 33 | `web.setDomStorage` | — | — |
| 34 | `web.setForceDark` | `mode` | — |
| 35 | `web.setGeolocationEnabled` | — | — |
| 36 | `web.setJavaScript` | — | — |
| 37 | `web.setJavaScriptCanOpenWindowsAutomatically` | — | — |
| 38 | `web.setLoadWithOverviewMode` | — | — |
| 39 | `web.setLoadsImagesAutomatically` | — | `UNKNOWN_ACTION` |
| 40 | `web.setMediaPlaybackRequiresUserGesture` | — | — |
| 41 | `web.setMinimumFontSize` | — | — |
| 42 | `web.setMixedContentMode` | `mode` | — |
| 43 | `web.setOffscreenPreRaster` | — | — |
| 44 | `web.setProgressBarVisible` | — | — |
| 45 | `web.setSafeBrowsing` | — | — |
| 46 | `web.setSaveFormData` | — | — |
| 47 | `web.setSettings` | — | — |
| 48 | `web.setStandardFontFamily` | `family` | — |
| 49 | `web.setSupportMultipleWindows` | — | — |
| 50 | `web.setSupportZoom` | — | — |
| 51 | `web.setTextZoom` | — | — |
| 52 | `web.setUseWideViewPort` | — | — |
| 53 | `web.setUserAgent` | `userAgent` | — |
| 54 | `web.setZoom` | — | — |
| 55 | `web.stop` | — | — |

### ৩.3 `device.*` — 33টি action

| # | Action | Parameters | Error codes |
|---|---|---|---|
| 1 | `device.copyToClipboard` | — | — |
| 2 | `device.finish` | — | — |
| 3 | `device.getAppInfo` | — | — |
| 4 | `device.getBattery` | — | — |
| 5 | `device.getBrightness` | — | — |
| 6 | `device.getClipboard` | — | — |
| 7 | `device.getDeviceInfo` | — | — |
| 8 | `device.getInfo` | — | — |
| 9 | `device.getLocale` | — | — |
| 10 | `device.getNetworkState` | — | — |
| 11 | `device.getSafeArea` | — | — |
| 12 | `device.getScreenInfo` | — | — |
| 13 | `device.haptic` | — | — |
| 14 | `device.isAppInstalled` | `packageName` | — |
| 15 | `device.landscape` | — | — |
| 16 | `device.log` | `message`, `tag` | `UNKNOWN_ACTION` |
| 17 | `device.openApp` | `packageName` | — |
| 18 | `device.openAppSettings` | — | — |
| 19 | `device.openExternal` | `url` | — |
| 20 | `device.openFile` | `mimeType`, `uri` | — |
| 21 | `device.openUrl` | — | — |
| 22 | `device.portrait` | — | — |
| 23 | `device.readClipboard` | — | — |
| 24 | `device.reverse_landscape` | — | — |
| 25 | `device.reverse_portrait` | — | — |
| 26 | `device.sensor` | — | — |
| 27 | `device.setBrightness` | — | — |
| 28 | `device.setClipboard` | `label`, `text` | — |
| 29 | `device.setKeepScreenOn` | — | — |
| 30 | `device.setOrientation` | `orientation` | — |
| 31 | `device.share` | `subject`, `text`, `title` | — |
| 32 | `device.shareText` | — | — |
| 33 | `device.vibrate` | — | — |

### ৩.4 `storage.*` — 7টি action

| # | Action | Parameters | Error codes |
|---|---|---|---|
| 1 | `storage.clear` | — | — |
| 2 | `storage.get` | `key` | — |
| 3 | `storage.getAll` | — | `UNKNOWN_ACTION` |
| 4 | `storage.has` | `key` | — |
| 5 | `storage.keys` | — | — |
| 6 | `storage.remove` | `key` | — |
| 7 | `storage.set` | `key` | — |

## ৪. রেজিস্টার্ড নয় — কারণসহ সিদ্ধান্ত

| Module | Path | case | সিদ্ধান্ত | কারণ |
|---|---|---|---|---|
| `theme` | `theme` | 6 | ❌ রাখা হয়নি | `ui.getTheme`/`ui.applyTheme` ইতিমধ্যে থিম দেয়, কিন্তু সেগুলো `NativeRuntimeUIConfig` পড়ে — আর `theme.*` পড়ে `ThemeManager.getActiveTheme()`। দুটো আলাদা source of truth, তাই একসাথে expose করলে দুটি ভিন্ন থিম দেখাবে। |
| `theme_legacy` | `—` | 0 | ❌ রাখা হয়নি | `theme.handler.ThemeCommandHandler`-এর ১৩ লাইনের alias। রেজিস্টার করলে একই `theme` route-এ দুটি handler দাঁড়াবে। `@Deprecated` করা হয়েছে। |
| `network` | `network` | 0 | ❌ রাখা হয়নি | এটি bridge handler নয় — কোনো action dispatch নেই। এটি raw HTTP ক্লায়েন্ট (`execute(url, body, callback)`)। |
| `platform` | `—` | 0 | ❌ রাখা হয়নি | `getPath()` নেই, action switch নেই — অসম্পূর্ণ। |
| `security` | `—` | 11 | ❌ রাখা হয়নি | BiometricPrompt-এর জন্য Activity দরকার; `NativeRuntimeUI`-এর কাছে সেই Activity নেই। ভুল Activity দিলে biometric prompt crash করবে। |
| `analytics` | `—` | 11 | ❌ রাখা হয়নি | constructor-এ একাধিক dependency (service + config) দরকার, `NativeRuntimeUI` সেগুলো তৈরি করে না। |
| `devtools` | `devtools` | 9 | ❌ রাখা হয়নি | শুধু debug build-এর জন্য; release-এ expose করলে internal state প্রকাশ পাবে। |
| `logger` | `—` | 0 | ❌ রাখা হয়নি | action dispatch নেই। |
| `service` | `—` | 0 | ❌ রাখা হয়নি | action dispatch নেই। |
| `storage_engine` | `storage` | 7 | ❌ রাখা হয়নি | `getPath()` = `storage` — `NativeStorageCommandHandler`-এর সাথে সংঘর্ষ। দুটোই রেজিস্টার করলে একটি অন্যটিকে ঢেকে দেবে। |

### ৪.১ `theme.*` — কেন এক্সপোজ করা হয়নি

```java
// NativeUICommandHandler.java:645  (ui.getTheme)
NativeRuntimeUIConfig config = uiHost.getConfiguration();
data.put("themeMode", config.getThemeMode());

// ThemeCommandHandler.java:60  (theme.getTheme)
IThemeConfig active = themeManager.getActiveTheme();
data.put("themeName", active.getThemeName());
```

দুটি ভিন্ন অবজেক্ট, দুটি ভিন্ন মান। `theme.setThemeMode` কল করলে `ui.getTheme` সেটি দেখবে না।
একই অ্যাপে দুটি থিম API থাকলে ডেভেলপার ভুল তথ্য পাবেন।

**সুপারিশ:** `ui.getTheme`-কে `ThemeManager`-এ delegate করান, তারপর `theme.*` রেজিস্টার করুন।
এটি আলাদা টাস্ক — এখানে করা হয়নি, কারণ এতে `NativeRuntimeUIConfig`-এর contract বদলাবে।

## ৫. এই টাস্কে করা পরিবর্তন

| ফাইল | পরিবর্তন |
|---|---|
| `NativeWebCommandHandler.java` | `SUPPORTED_ACTIONS`-এ `setTextZoom`, `getTextZoom` যোগ (৫৩ → ৫৫) |
| `StorageScope.java` | নতুন — project-scoped key namespace |
| `NativeStorageCommandHandler.java` | ৭টি action-ই scope-aware; `clear` শুধু নিজের key মুছে |
| `NativeUIHost.java` | `projectId` field + getter/setter |
| `NativeRuntimeUI.java` | `setProjectId()` / `getProjectId()` |
| `themeengine/handler/ThemeCommandHandler.java` | `@Deprecated` + সতর্কবার্তা |

`web.setTextZoom`/`getTextZoom` আগে switch-এ ছিল কিন্তু `SUPPORTED_ACTIONS`-এ ছিল না — তাই শুধু prefix route দিয়ে কাজ করত। এখন bare route-ও আছে, অর্থাৎ native caller-রাও ডাকতে পারবে।

## ৬. যাচাই

| চেক | ফল |
|---|---|
| `extract_actions.py` — dead route (SUPPORTED_ACTIONS-এ আছে, case নেই) | ০ |
| `extract_actions.py` — hidden action (case আছে, SUPPORTED_ACTIONS-এ নেই) | ০ |
| `examples_runtime_test.mjs` — উদাহরণে ব্যবহৃত ৭৫টি action সবই dispatch table-এ | ✅ |
| `examples_runtime_test.mjs` — prefix-only action | ০ |
