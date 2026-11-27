# RONiLiFEOS — Web App ডেভেলপার ডকুমেন্টেশন (বাংলা)

এই ডকুমেন্টেশন সেটটি **প্রকৃত সোর্স কোড অডিট** করে তৈরি করা হয়েছে।
এখানে শুধু সেই API-গুলো লেখা আছে যেগুলো `RONiLiFEOS` রিপোজিটরির Java সোর্সে সত্যিই আছে
এবং WebBridge দিয়ে JavaScript থেকে সত্যিই কল করা যায়।

> **অডিট বেসলাইন:** `RONiLiFEOS` ZIP (2026-11-16), 700 Java ফাইল, 21 Gradle মডিউল।
> সব তথ্য স্ট্যাটিক সোর্স ইনস্পেকশন থেকে নেওয়া। শুধু JavaScript bridge bootstrap স্ক্রিপ্টটি
> Node.js-এ চালিয়ে যাচাই করা হয়েছে (১৮টি চেক পাস) — বিস্তারিত `API_COMPATIBILITY.md`-এ।

---

## ১. এক নজরে

RONiLiFEOS হলো একটি Android অ্যাপ যা **HTML5 + CSS3 + JavaScript** দিয়ে লেখা Web App-কে
নিজের ভেতরে হোস্ট করে। Web App আর Android-এর মাঝে যোগাযোগ হয় একটি **JSON message bridge** দিয়ে।

```
┌──────────────────────────── Android (Java) ────────────────────────────┐
│  ProjectActivity → NativeRuntimeActivity → NativeRuntimeUI             │
│         │                                                              │
│         └── WebViewHost ── WebView ── addJavascriptInterface(           │
│                                        "NativeRuntimeBridge")          │
│                              │                                         │
│                              ├── WebBridge (origin gate, size gate)    │
│                              ├── NativeRouter (route dispatch)         │
│                              └── CommandHandlers (ui/web/device/...)   │
└────────────────────────────────────────────────────────────────────────┘
                                   ▲  ▼  JSON
┌──────────────────────────── Web App (JS) ──────────────────────────────┐
│  window.NativeRuntimeBridge        ← native object (postMessage/ping)  │
│  window.NativeRuntimeBridgeClient  ← JS client (call/on/off)           │
│  window.RONiDownload               ← download bridge                   │
└────────────────────────────────────────────────────────────────────────┘
```

**সবচেয়ে ছোট কাজের উদাহরণ:**

```html
<script>
  NativeRuntimeBridgeClient.call('ui.showToast', { message: 'হ্যালো', type: 'success' })
    .then(function (r) { console.log('shown:', r.shown); })
    .catch(function (e) { console.error('native error:', e.message); });
</script>
```

---

## ২. ডকুমেন্ট সূচি — ২৫টি ডক

| # | ফাইল | কী আছে |
|---|---|---|
| ১ | [README.md](README.md) | এই সূচি, শুরু করার নিয়ম |
| ২ | [PLATFORM_OVERVIEW.md](PLATFORM_OVERVIEW.md) | প্ল্যাটফর্মের সারসংক্ষেপ, মডিউল ম্যাপ |
| ৩ | [ARCHITECTURE.md](ARCHITECTURE.md) | স্তর, নির্ভরতার গ্রাফ, রানটাইম প্রবাহ |
| ৪ | [WEB_PROJECT_STRUCTURE.md](WEB_PROJECT_STRUCTURE.md) | ফোল্ডার কাঠামো, entry point, manifest |
| ৫ | [FILE_AND_FOLDER_REFERENCE.md](FILE_AND_FOLDER_REFERENCE.md) | রিপোজিটরির প্রতিটি ফোল্ডার |
| ৬ | [PROGRAMMING_LANGUAGES.md](PROGRAMMING_LANGUAGES.md) | Java 17, HTML5, CSS3, ES6+ — কী চলে, কী চলে না |
| ৭ | [NATIVE_API_REFERENCE.md](NATIVE_API_REFERENCE.md) | Java ক্লাস/মেথড সিগনেচার |
| ৮ | [WEBBRIDGE_REFERENCE.md](WEBBRIDGE_REFERENCE.md) | Bridge transport, envelope, origin gate |
| ৯ | [API_REGISTRATION_MATRIX.md](API_REGISTRATION_MATRIX.md) | ১৫২টি action বনাম Java handler |
| ১০ | [FEATURE_CATALOG.md](FEATURE_CATALOG.md) | ফিচার-ভিত্তিক ক্যাটালগ |
| ১১ | [JAVASCRIPT_INTEGRATION.md](JAVASCRIPT_INTEGRATION.md) | প্রতিটি JS কলযোগ্য action |
| ১২ | [STORAGE_AND_FILE_GUIDE.md](STORAGE_AND_FILE_GUIDE.md) | `storage.*`, ফাইল |
| ১৩ | [NAVIGATION_AND_UI_GUIDE.md](NAVIGATION_AND_UI_GUIDE.md) | `web.*`, drawer/tab, dialog, toast, toolbar |
| ১৪ | [DEVICE_API_GUIDE.md](DEVICE_API_GUIDE.md) | `device.*` ২৮টি action |
| ১৫ | [DOWNLOAD_AND_NETWORK_GUIDE.md](DOWNLOAD_AND_NETWORK_GUIDE.md) | ডাউনলোড, `fetch`, network state |
| ১৬ | [EVENT_AND_CALLBACK_GUIDE.md](EVENT_AND_CALLBACK_GUIDE.md) | Native → Web ইভেন্ট, Promise |
| ১৭ | [SECURITY_AND_PERMISSION_GUIDE.md](SECURITY_AND_PERMISSION_GUIDE.md) | Origin, path traversal, AES-GCM |
| ১৮ | [PROJECT_IMPORT_AND_EXECUTION.md](PROJECT_IMPORT_AND_EXECUTION.md) | ZIP import থেকে চালু পর্যন্ত |
| ১৯ | [WEB_APP_DEVELOPMENT_GUIDE.md](WEB_APP_DEVELOPMENT_GUIDE.md) | নতুন Web App বানানোর গাইড |
| ২০ | [AI_WEB_DEVELOPMENT_INSTRUCTIONS.md](AI_WEB_DEVELOPMENT_INSTRUCTIONS.md) | AI এজেন্টের ১৪ ধাপ |
| ২১ | [WORKING_EXAMPLES.md](WORKING_EXAMPLES.md) | ২৬টি চলমান উদাহরণ |
| ২২ | [TESTING_AND_VERIFICATION.md](TESTING_AND_VERIFICATION.md) | টেস্টের ফল ও পদ্ধতি |
| ২৩ | [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | সমস্যা ও সমাধান |
| ২৪ | [API_GAP_ANALYSIS.md](API_GAP_ANALYSIS.md) | কী নেই, কী আংশিক |
| ২৫ | [FINAL_AUDIT_REPORT.md](FINAL_AUDIT_REPORT.md) | চূড়ান্ত অডিট |

**সহায়ক ডক:** [API_COMPATIBILITY.md](API_COMPATIBILITY.md) (Android/WebView ভার্সন),
[API_USAGE_GUIDE.md](API_USAGE_GUIDE.md) (প্যাটার্ন), [STORAGE_ISOLATION.md](STORAGE_ISOLATION.md)
(project-scoped storage)।

---

## ৩. পাঁচটি নিয়ম (মুখস্থ রাখুন)

1. **`NativeRuntimeBridgeClient` সবসময় নাও থাকতে পারে।** পেজ লোড হওয়ার পর inject হয়।
   `typeof NativeRuntimeBridgeClient !== 'undefined'` দিয়ে চেক করুন, নাহলে fallback দিন।
2. **সব কল asynchronous।** `call()` একটি `Promise` ফেরত দেয়। `await` বা `.then()` ব্যবহার করুন।
3. **Action নাম ভুল হলে `ROUTE_NOT_FOUND` আসবে।** অনুমান করে নাম লিখবেন না — এই ডকুমেন্টের
   তালিকা দেখুন।
4. **`ui.showConfirm` সাথে সাথে `true/false` দেয় না।** ফলাফল আসে `ui.confirm` ইভেন্টে।
5. **`localStorage` ব্যবহার করবেন না।** `storage.*` ব্যবহার করুন — এটা AES-GCM এনক্রিপ্টেড
   এবং WebView reload/অ্যাপ রিস্টার্টেও টেকে।

---

## ৪. কোন namespace সত্যিই আছে?

| Namespace | অবস্থা | হ্যান্ডলার |
|---|---|---|
| `ui.*` | ✅ আছে (৬২টি action) | `NativeUICommandHandler` |
| `web.*` | ✅ আছে (৫৫টি action) | `NativeWebCommandHandler` |
| `device.*` | ✅ আছে (২৮টি action) | `NativeDeviceCommandHandler` |
| `storage.*` | ✅ আছে (৭টি action) | `NativeStorageCommandHandler` |
| `runtime.*` | ✅ আছে (৬টি action) | `RuntimeCommandHandler` |
| `theme.*` | ❌ Bridge-এ রেজিস্টার্ড নয় | `ThemeCommandHandler` (internal) |
| `network.*` | ❌ Bridge-এ রেজিস্টার্ড নয় | `NetworkCommandHandler` (internal) |
| `platform.*` | ❌ Bridge-এ রেজিস্টার্ড নয় | `PlatformCommandHandler` (internal) |
| `security.*` | ❌ Bridge-এ রেজিস্টার্ড নয় | `SecurityCommandHandler` (internal) |
| `analytics.*` | ❌ Bridge-এ রেজিস্টার্ড নয় | `AnalyticsCommandHandler` (internal) |
| `devtools.*` | ❌ Bridge-এ রেজিস্টার্ড নয় | `DevToolsCommandHandler` (internal) |
| `NativeRuntime.ui` | ⚠️ Native নয় — উদাহরণের wrapper | `examples/webapp-starter/native-runtime.js` |

`NativeRuntime.ui` / `NativeRuntime.storage` ইত্যাদি **native API নয়**। এগুলো
`examples/webapp-starter/native-runtime.js` ফাইলের নিজের বানানো wrapper, যা ভেতরে
`NativeRuntimeBridgeClient.call('ui.showToast', …)` কল করে। আপনি চাইলে wrapper ব্যবহার করতে পারেন,
নাহলে সরাসরি `call()` ব্যবহার করুন — দুটোই একই native action-এ যায়।

---

## ৫. দ্রুত শুরু

```html
<!DOCTYPE html>
<html lang="bn">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <title>আমার প্রথম RONiLiFEOS অ্যাপ</title>
</head>
<body>
  <button id="btn">টোস্ট দেখাও</button>

  <script>
    function nativeReady() {
      return typeof NativeRuntimeBridgeClient !== 'undefined'
          && typeof NativeRuntimeBridgeClient.call === 'function';
    }

    document.getElementById('btn').addEventListener('click', async function () {
      if (!nativeReady()) {
        alert('Native bridge নেই — ব্রাউজার মোড');
        return;
      }
      try {
        await NativeRuntimeBridgeClient.call('ui.showToast', {
          message: 'নেটিভ টোস্ট কাজ করছে',
          type: 'success',
          duration: 'short'
        });
      } catch (err) {
        console.error('native error:', err.message);
      }
    });
  </script>
</body>
</html>
```

এই ফাইলটি `index.html` নামে সেভ করে RONiLiFEOS-এ একটি Project হিসেবে খুললেই চলবে।

---

## ৬. আরও দেখুন

- সম্পূর্ণ উদাহরণ: `examples/bangla-demos/` (১৪টি আলাদা ডেমো + একটি integrated app)
- বিদ্যমান starter: `examples/webapp-starter/`
- ইংরেজি গাইড: `docs/web-developer-guide/`

---

## ৪. Phase 12 — নয়টি নেটিভ লাইব্রেরির ইন্টিগ্রেশন

| # | ফাইল | কী আছে |
|---|---|---|
| ১ | [NATIVE_API_INTEGRATION_AUDIT.md](NATIVE_API_INTEGRATION_AUDIT.md) | ৯টি লাইব্রেরির অডিট, ৮টি সমস্যা ও সমাধান |
| ২ | [NATIVE_LIBRARY_CAPABILITY_MATRIX.md](NATIVE_LIBRARY_CAPABILITY_MATRIX.md) | কোন লাইব্রেরি কী পারে |
| ৩ | [NATIVE_API_DEPENDENCY_MATRIX.md](NATIVE_API_DEPENDENCY_MATRIX.md) | মডিউল ও handler নির্ভরতা |
| ৪ | [NATIVE_API_COMPLETE_REFERENCE.md](NATIVE_API_COMPLETE_REFERENCE.md) | সব namespace ও method |
| ৫ | [NATIVE_API_INTEGRATION_MATRIX.md](NATIVE_API_INTEGRATION_MATRIX.md) | ৭৪টি dispatch label, route, সংঘর্ষ |
| ৬ | [THEME_API_GUIDE.md](THEME_API_GUIDE.md) | `theme.*` — এক authoritative state |
| ৭ | [SECURITY_API_GUIDE.md](SECURITY_API_GUIDE.md) | `security.*` — biometric দুই ধাপ |
| ৮ | [ANALYTICS_API_GUIDE.md](ANALYTICS_API_GUIDE.md) | `analytics.*` |
| ৯ | [DEVTOOLS_API_GUIDE.md](DEVTOOLS_API_GUIDE.md) | `devtools.*` — debug only |
| ১০ | [NETWORK_API_GUIDE.md](NETWORK_API_GUIDE.md) | `network.*` |
| ১১ | [PLATFORM_API_GUIDE.md](PLATFORM_API_GUIDE.md) | `platform.*` |
| ১২ | [LOGGER_API_GUIDE.md](LOGGER_API_GUIDE.md) | `logger.*` |
| ১৩ | [SERVICE_API_GUIDE.md](SERVICE_API_GUIDE.md) | `service.*` — সীমিত |
| ১৪ | [STORAGE_API_GUIDE.md](STORAGE_API_GUIDE.md) | `storage.*` — project-scoped |
| ১৫ | [NATIVE_API_SECURITY.md](NATIVE_API_SECURITY.md) | isolation ম্যাট্রিক্স ও ঝুঁকি |
| ১৬ | [NATIVE_API_TEST_REPORT.md](NATIVE_API_TEST_REPORT.md) | ১৬৫ assertion, Android Studio নির্দেশনা |
