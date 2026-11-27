# API Compatibility ও Verification

## ১. এই ডকুমেন্ট কীভাবে যাচাই করা হয়েছে

| পদ্ধতি | কী যাচাই হয়েছে | ফল |
|---|---|---|
| **সোর্স ইনস্পেকশন** | সব action নাম, parameter key, response key | ✅ ১৫৪টি action |
| **JS bootstrap extraction** | `JavaScriptBridgeScript.java` → JS | ✅ ২,২০৯ bytes, `node --check` পাস |
| **Node runtime test** | `call`/`ping`/`on`/`off`/ERROR/EVENT | ✅ ১৮/১৮ পাস |
| **Java compilation** | — | ❌ **সম্ভব হয়নি** (পরিবেশে `javac` নেই, শুধু JRE) |
| **Android build** | — | ❌ Android SDK নেই |
| **ডিভাইসে রান** | — | ❌ ডিভাইস/এমুলেটর নেই |

> **সৎ ঘোষণা:** Java কোড কম্পাইল বা ডিভাইসে চালানো যায়নি। তাই "কাজ করছে" বলার
> একমাত্র প্রমাণ হলো সোর্স কোডে `case "…"` ব্লক ও `return success(...)` লাইন
> সরাসরি দেখা। JS client-এর আচরণ সত্যিই চালিয়ে যাচাই করা হয়েছে।

---

## ২. JS client verification (প্রকৃত রান)

`JavaScriptBridgeScript.java` থেকে Python দিয়ে string concatenation এক্সট্রাক্ট করে
`bootstrap.js` তৈরি করা হয়েছে, তারপর Node 24-এ চালানো হয়েছে।

```
EXTRACTED_BYTES 2209
HAS_CLIENT True
JS_SYNTAX_OK

BRIDGE_SMOKE: 18 passed, 0 failed
```

| # | চেক | ফল |
|---|---|---|
| 1 | `NativeRuntimeBridgeClient` ইনস্টল হয় | ✅ |
| 2 | `call()` আছে | ✅ |
| 3 | `on()` আছে | ✅ |
| 4 | `off()` আছে | ✅ |
| 5 | `_handleNativeMessage()` আছে | ✅ |
| 6 | interface না থাকলে `call()` reject করে (`not bound`) | ✅ |
| 7 | envelope `type: "REQUEST"` | ✅ |
| 8 | `action` অপরিবর্তিত | ✅ |
| 9 | `payload` অপরিবর্তিত | ✅ |
| 10 | `id` তৈরি হয় (`req_…`) | ✅ |
| 11 | `timestamp` সংখ্যা | ✅ |
| 12 | RESPONSE → Promise resolve | ✅ |
| 13 | ERROR → Promise reject (native error string) | ✅ |
| 14 | EVENT → listener-এ পৌঁছায় | ✅ |
| 15 | `off()` listener সরায় | ✅ |
| 16 | `ping()` → `"pong"` | ✅ |
| 17 | malformed JSON throw করে না | ✅ |
| 18 | bootstrap idempotent | ✅ |

**স্ক্রিপ্ট:** `tasks/verify/extract_js.py`, `tasks/verify/smoke.mjs`

---

## ৩. Action সংখ্যা (সোর্স থেকে গণনা)

| Namespace | Action | সোর্স ফাইল |
|---|---|---|
| `ui` | ৬০ | `NativeUICommandHandler.java` |
| `web` | ৫৩ | `NativeWebCommandHandler.java` |
| `device` | ২৮ | `NativeDeviceCommandHandler.java` |
| `storage` | ৭ | `NativeStorageCommandHandler.java` |
| `runtime` | ৬ | `RuntimeCommandHandler.java` |
| `bridge` | ১ | `BridgeCommandHandler.java` |
| `echo` | ১ | `EchoCommandHandler.java` |
| `system` | ১ | `SystemDiagnosticHandler.java` |
| **মোট** | **১৫৭** | |

---

## ৪. `SUPPORTED_ACTIONS` বনাম `switch` — একটি সূক্ষ্ম পার্থক্য

`NativeWebCommandHandler`-এ `setTextZoom` ও `getTextZoom` **switch-এ আছে কিন্তু
`SUPPORTED_ACTIONS` অ্যারেতে নেই**।

| কল | path | ফল |
|---|---|---|
| `web.setTextZoom` | `/web` | ✅ কাজ করে (handler path-এ রেজিস্টার্ড) |
| `setTextZoom` (bare) | `/setTextZoom` | ❌ `ROUTE_NOT_FOUND` |

**শিক্ষা:** সবসময় namespace prefix (`web.`) ব্যবহার করুন।

---

## ৫. Android version সাপোর্ট

| Feature | ন্যূনতম API | নোট |
|---|---|---|
| `WebView` + `addJavascriptInterface` | 17 | |
| `evaluateJavascript` | 19 | |
| `WebViewAssetLoader`-ধাঁচের virtual origin | 21 | |
| `DownloadManager` | 9 | |
| `POST_NOTIFICATIONS` | 33 | runtime permission |
| `WindowInsetsController` (edge-to-edge) | 30 | নিচে fallback |
| `WebSettings.setAlgorithmicDarkeningAllowed` | 33 | নিচে `setForceDark` |
| `WebSettings.setSafeBrowsingEnabled` | 26 | |
| `WebSettings.setOffscreenPreRaster` | 23 | |
| `WebSettings.setMixedContentMode` | 21 | |
| `EncryptedStore` (AES-GCM) | 23 | Keystore |
| `ActivityResultLauncher` | 21 (AndroidX) | |

**`minSdk`:** `app/build.gradle`-এ দেখুন। নিচের সব API `Build.VERSION.SDK_INT`
চেক দিয়ে guard করা আছে।

---

## ৬. WebView version

| Feature | WebView version |
|---|---|
| `fetch` | 42+ |
| `Promise` | 32+ |
| `async/await` | 55+ |
| `AbortController` | 66+ |
| `Object.assign` | 45+ |
| `Element.closest` | 41+ |
| `Array.from` | 45+ |
| CSS `env(safe-area-inset-*)` | 69+ |
| CSS `:root[data-theme]` | সব |
| `100dvh` | 108+ |
| `structuredClone` | 98+ |
| `Array.prototype.at` | 92+ |
| `String.replaceAll` | 85+ |

**সুপারিশ:** Android 7+ (WebView 51+) টার্গেট করলে `async/await` ও `closest` নিরাপদ।
`100dvh` এড়িয়ে `flex` বা `calc(100vh - …)` ব্যবহার করুন।

---

## ৭. ব্রাউজার fallback ম্যাট্রিক্স

| API | ব্রাউজারে বিকল্প |
|---|---|
| `ui.showToast` | নিজের toast component |
| `ui.showAlert` | `alert()` |
| `ui.showConfirm` | `confirm()` |
| `ui.showLoading` | spinner overlay |
| `storage.*` | `localStorage` |
| `device.getInfo` | `navigator.userAgent` |
| `device.getNetworkState` | `navigator.onLine` |
| `device.getBattery` | `navigator.getBattery()` |
| `device.copyToClipboard` | `navigator.clipboard.writeText()` |
| `device.shareText` | `navigator.share()` |
| `device.openUrl` | `window.open()` |
| `device.getSafeArea` | CSS `env()` |
| `ui.getTheme` | `prefers-color-scheme` |
| `web.goBack` | `history.back()` |
| `device.finish` | `window.close()` (সীমিত) |

---

## ৮. Response key যাচাই — নমুনা

| Action | সোর্স লাইন | Response |
|---|---|---|
| `ui.showToast` | `return success(reqId, "shown", true)` | `{shown:true}` |
| `ui.showConfirm` | `return success(reqId, "shown", true)` | `{shown:true}` |
| `ui.isDialogShowing` | `return success(reqId, "showing", …)` | `{showing:bool}` |
| `ui.setToolbarTitle` | `return success(reqId, "title", title)` | `{title}` |
| `ui.getTabs` | `return success(reqId, "tabs", …)` | `{tabs:[…]}` |
| `web.getState` | `return success(reqId, "url", …)` + আরও | `{url, canGoBack, canGoForward, title, progress}` |
| `web.setMixedContentMode` | `return success(reqId, "mode", …)` | `{mode}` |
| `web.setCacheMode` | `return success(reqId, "cacheMode", …)` | `{cacheMode:int}` |
| `storage.get` | `return success(reqId, "key", …)` + আরও | `{key, value, exists}` |
| `storage.getAll` | map সরাসরি | `{key:value, …}` |

---

## ৯. যা যাচাই করা যায়নি

| দাবি | কেন যাচাই হয়নি |
|---|---|
| "Toast সত্যিই দেখা যায়" | ডিভাইস নেই |
| "AES-GCM decrypt কাজ করে" | Android Keystore নেই |
| "Virtual origin serve করে" | WebView নেই |
| "Download notification আসে" | Android runtime নেই |
| "`ui.showSnackbar` crash করে না" | ডিভাইস নেই |

**এগুলো সোর্স পড়ে যুক্তি দিয়ে বলা হয়েছে, চালিয়ে নয়।** প্রোডাকশনে যাওয়ার আগে
একটি ডিভাইসে স্মোক টেস্ট করুন।

---

## ১০. স্মোক টেস্ট চেকলিস্ট (ডিভাইসে)

- [ ] `NativeRuntimeBridgeClient` inject হয়
- [ ] `ping()` → `pong`
- [ ] `ui.showToast` দেখা যায়
- [ ] `ui.showConfirm` → `ui.confirm` ইভেন্ট
- [ ] `storage.set` → reload → `storage.get` মান ফেরে
- [ ] `device.getInfo` সঠিক মডেল দেয়
- [ ] `device.copyToClipboard` → অন্য অ্যাপে paste
- [ ] `<a download>` → `Download/RONiLiFEOS/`
- [ ] `<input type="file">` → picker খোলে
- [ ] `ui.setStatusBar` রঙ বদলায়
- [ ] back button WebView history-তে যায়
- [ ] dark mode ঠিক দেখায়
- [ ] বাংলা ফন্ট ঠিক
- [ ] safe area (notch) ঠিক
