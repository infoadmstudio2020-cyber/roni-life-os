# API Gap Analysis — কী নেই, কী আংশিক, কী ঝুঁকি

## ১. সারসংক্ষেপ

| শ্রেণি | সংখ্যা |
|---|---|
| JS থেকে সম্পূর্ণ কাজ করে | ১৫২ action (ui ৬২ / web ৫৫ / device ২৮ / storage ৭) |
| আংশিক (শুধু কিছু দিক) | ৯ |
| Internal-only (JS থেকে অপ্রাপ্য) | ৯ handler |
| সম্পূর্ণ অনুপস্থিত | ১৪ ফিচার |

---

## ২. Internal-only handler — বড় ফাঁক

এই handler-গুলোতে পূর্ণ API আছে, কিন্তু `NativeRuntimeUI.registerBridgeRoutes()` বা
`NativeRuntimeEngine` এগুলো router-এ **রেজিস্টার করে না**।

| Handler | Path | কী হারাচ্ছে |
|---|---|---|
| `ThemeCommandHandler` | `theme` | `theme.getTheme`, `theme.setTheme`, `theme.getCssVariables`, `theme.applyTheme` |
| `NetworkCommandHandler` | `network` | `network.getState`, `network.isConnected`, `network.getType` |
| `PlatformCommandHandler` | `platform` | `platform.getInfo`, `platform.getCapabilities` |
| `SecurityCommandHandler` | `security` | `security.isBiometricAvailable`, `security.encrypt`, `security.decrypt` |
| `AnalyticsCommandHandler` | `analytics` | `analytics.track`, `analytics.getMetrics` |
| `DevToolsCommandHandler` | `devtools` | `devtools.getLogs`, `devtools.getPerformance` |
| `LoggerCommandHandler` | `logger` | `logger.log`, `logger.setLevel` |
| `ServiceCommandHandler` | `service` | `service.start`, `service.stop` |
| `StorageCommandHandler` (engine) | `storage` | path clash — UI handler জেতে |

**প্রভাব:**

- `theme.getCssVariables` থাকলে Web App নিজে থেকে theme CSS পেত — এখন `ui.getTheme`
  দিয়ে ম্যানুয়ালি বানাতে হয়।
- `network.getState` থাকলে সমৃদ্ধ নেটওয়ার্ক তথ্য পেত — এখন `device.getNetworkState`
  (সীমিত)।
- `security.isBiometricAvailable` থাকলে biometric login সম্ভব হতো — এখন নেই।

**সমাধান (অ্যাপ ডেভেলপার):** `NativeRuntimeUI.registerBridgeRoutes()`-এ যোগ করুন:

```java
bridge.getRouter().registerHandler(ThemeEngine.getInstance().getCommandHandler());
```

**সমাধান (Web App ডেভেলপার):** এই API-গুলোর উপর নির্ভর করে কোড লিখবেন না।

---

## ৩. আংশিক ফিচার

| ফিচার | কী আছে | কী নেই |
|---|---|---|
| **Theme** | `ui.getTheme`, `ui.applyTheme` | CSS variable injection, live theme change ইভেন্ট |
| **Navigation** | drawer, tab, toolbar | tab click ইভেন্ট, FAB click ইভেন্ট, drawer icon |
| **Storage** | ৭টি action | object API, TTL, quota info, project isolation |
| **Download** | URL/blob/data | progress ইভেন্ট, cancel, pause, upload |
| **Network** | `device.getNetworkState` | change listener, speed, signal strength |
| **Battery** | `device.getBattery` | change listener, charging time |
| **Clipboard** | copy/read | image clipboard, multi-item |
| **Share** | text | file share, image share, multi-target |
| **Vibrate** | একক haptic | pattern, duration |
| **Orientation** | set | lock/unlock, current orientation read |
| **Brightness** | set/get (window) | system brightness |
| **File** | picker, download, open | read, write, delete, list |
| **Camera** | WebRTC (permission handler দরকার) | native camera intent |
| **Geolocation** | Web API (permission handler দরকার) | native location API |
| **Notification** | download notification | custom notification |
| **Lifecycle** | `visibilitychange` | pause/resume ইভেন্ট |
| **Events** | ৬টি built-in | page load, error, progress |

---

## ৪. সম্পূর্ণ অনুপস্থিত

| # | ফিচার | কেন গুরুত্বপূর্ণ | বিকল্প |
|---|---|---|---|
| 1 | **Upload API** | বড় ফাইল native ভাবে পাঠানো | `<input type="file">` + `fetch` |
| 2 | **File read/write** | local file management | IndexedDB |
| 3 | **Notification API** | push/local notification | ❌ নেই |
| 4 | **Camera API** | native camera | WebRTC (permission handler) |
| 5 | **Contacts** | contact picker | ❌ নেই |
| 6 | **SMS/Call** | `tel:`/`sms:` intent | `device.openUrl('tel:…')` |
| 7 | **Biometric** | fingerprint login | ❌ নেই |
| 8 | **In-app purchase** | monetization | ❌ নেই |
| 9 | **SQLite bridge** | structured query | IndexedDB |
| 10 | **Session API** | login state | `storage.*` |
| 11 | **Analytics** | event tracking | নিজের `fetch` |
| 12 | **Crash reporting** | error tracking | `window.onerror` + `fetch` |
| 13 | **Deep link** | URL → app route | hash routing |
| 14 | **Background task** | sync | ❌ নেই |

---

## ৫. নিরাপত্তা ঝুঁকি

### ৫.১ উচ্চ

| # | ঝুঁকি | বিবরণ | প্রশমন |
|---|---|---|---|
| H1 | **`storage.*` project isolation নেই** | সব Project একই `EncryptedStore` দেখে। এক Project অন্য Project-এর key পড়তে পারে যদি নাম জানে। | key prefix বাধ্যতামূলক |
| H2 | **iframe origin confusion** | `webView.getUrl()` top-level URL দেয়। iframe-এর script-ও একই origin হিসেবে bridge কল করতে পারে। | iframe-এ `sandbox` (allow-same-origin ছাড়া) |
| H3 | **`device.openUrl` scheme unrestricted** | `intent://`, `file://`, custom scheme সবই চলে। | URL whitelist |
| H4 | **`web.evaluate` যেকোনো script** | পেজ নিজে নিজেকে script inject করতে পারে (তবে সীমিত লাভ)। | ব্যবহার করবেন না |

### ৫.২ মধ্যম

| # | ঝুঁকি | বিবরণ | প্রশমন |
|---|---|---|---|
| M1 | **`web.setCookie` unrestricted** | যেকোনো domain-এর cookie সেট করা যায়। | ব্যবহার করবেন না |
| M2 | **`web.setUserAgent`** | UA বদলে fingerprint spoofing | দরকার ছাড়া নয় |
| M3 | **`web.setAllowFileAccess(true)`** | `file://` অ্যাক্সেস খুলে দেয় | কখনো করবেন না |
| M4 | **`web.setMixedContentMode('always')`** | HTTP অনুমোদন | কখনো করবেন না |
| M5 | **`device.readClipboard`** | Android 10+ এ foreground-এ সম্ভব | সংবেদনশীল ডেটা লগ করবেন না |
| M6 | **`device.log`** | logcat-এ যায়, অন্য অ্যাপ পড়তে পারে (debug build) | প্রোডাকশনে সংবেদনশীল ডেটা নয় |

### ৫.৩ নিম্ন

| # | ঝুঁকি | প্রশমন |
|---|---|---|
| L1 | `device.getDeviceInfo` fingerprinting | স্বাভাবিক |
| L2 | `device.isAppInstalled` app enumeration | whitelist |
| L3 | `web.getCookies` | নিজের origin-এ সীমিত |

---

## ৬. স্থাপত্য সীমা

| # | সীমা | প্রভাব |
|---|---|---|
| A1 | `WebBridge` process-wide singleton | একসাথে একাধিক WebView-এ bridge অস্থির |
| A2 | `setOriginProvider` replace হয় | শেষ attach করা WebView জেতে |
| A3 | `NativeWebCommandHandler` main thread-এ ১০s block | একসাথে অনেক `web.*` কল timeout |
| A4 | `ui.showConfirm` দুই-ধাপ | `await`-এ উত্তর পাওয়া যায় না |
| A5 | tab/FAB click ইভেন্ট নেই | polling বা drawer-এ সরে যেতে হয় |
| A6 | `storage.*` ১ MiB limit | বড় ডেটা IndexedDB-তে |
| A7 | `saveBase64` main thread-এ decode | বড় ফাইলে UI ঝুলে যায় |
| A8 | `web.loadUrl` নতুন origin-এ bridge ভাঙে | `device.openUrl` ব্যবহার |

---

## ৭. অগ্রাধিকারভিত্তিক সুপারিশ

### P0 — এখনই ঠিক করা উচিত

1. **`storage.*`-এ project namespace** — `NativeStorageCommandHandler`-এ
   `projectId` prefix স্বয়ংক্রিয়ভাবে যোগ করুন। (H1)
2. **`theme.*` রেজিস্টার করুন** — `ThemeCommandHandler` router-এ যোগ করুন। (বড় ফাঁক)
3. **`network.*` রেজিস্টার করুন** — `device.getNetworkState`-এর চেয়ে সমৃদ্ধ।

### P1 — পরবর্তী রিলিজে

4. **Tab/FAB click ইভেন্ট** — `ui.tabSelected`, `ui.fabClicked` যোগ করুন।
5. **Upload API** — `device.pickFile()` → base64/URI।
6. **Notification API** — `device.notify({title, body})`।
7. **Download progress ইভেন্ট** — `download.progress`।

### P2 — ভবিষ্যতে

8. **Biometric API** — `security.authenticate()`।
9. **File API** — sandboxed read/write।
10. **Session API** — token lifecycle।
11. **Analytics bridge** — `analytics.track()`।
12. **Deep link** — `app.onDeepLink`।

---

## ৮. Web App ডেভেলপারের জন্য করণীয়

| পরিস্থিতি | করণীয় |
|---|---|
| `theme.*` দরকার | `ui.getTheme` + নিজের CSS |
| `network.*` দরকার | `device.getNetworkState` + polling |
| `platform.*` দরকার | `device.getInfo` |
| `security.*` দরকার | ❌ অসম্ভব — অ্যাপ ডেভেলপারকে বলুন |
| `analytics.*` দরকার | নিজের `fetch` |
| Upload দরকার | `<input type="file">` |
| Notification দরকার | ❌ অসম্ভব |
| Camera দরকার | WebRTC + permission handler |
| File read/write দরকার | IndexedDB |
| Session দরকার | `storage.*` + নিজের logic |

---

## ৯. উপসংহার

RONiLiFEOS-এর bridge **UI, WebView, device, storage** — এই চারটি ক্ষেত্রে পরিপূর্ণ।
কিন্তু **theme, network, platform, security, analytics, devtools** — ছয়টি ইঞ্জিনের
API লেখা হয়েছে অথচ bridge-এ যুক্ত হয়নি। এটি সম্ভবত অসম্পূর্ণ ইন্টিগ্রেশন, ইচ্ছাকৃত
সীমাবদ্ধতা নয়।

Web App ডেভেলপার হিসেবে **শুধু ui/web/device/storage/runtime**-এর উপর নির্ভর করুন।
বাকিগুলো এই ডকুমেন্টে "নেই" হিসেবে ধরে নিন।
