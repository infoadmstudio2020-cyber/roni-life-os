# Architecture — RONi LiFE OS Consumer App

## ১. স্তরবিন্যাস

অ্যাপটি `NativeRuntime` ইকোসিস্টেমের **Layer 5 (Host Application)**। নিম্ন স্তর কখনোই
উপরের স্তরের উপর নির্ভর করে না।

```
┌──────────────────────────────────────────────────────────────┐
│ L5  Host App        :app                                     │
│     activity/  ui/  core/  project/  runtime/  util/         │
├──────────────────────────────────────────────────────────────┤
│ L4  Master          native-runtime-sdk                       │
│     NativeRuntime · RuntimeConfiguration · BootstrapReport   │
├──────────────────────────────────────────────────────────────┤
│ L3  Bridge / UI     native-runtime-ui · native-web-bridge-sdk│
│     NativeRuntimeActivity · WebViewHost · WebSource          │
├──────────────────────────────────────────────────────────────┤
│ L2  Domain Engines  runtime · storage · network · security   │
│                     analytics · theme · platform · router    │
├──────────────────────────────────────────────────────────────┤
│ L1  Infrastructure  logger · service · dev-tools             │
├──────────────────────────────────────────────────────────────┤
│ L0  Contracts       native-runtime-contracts                 │
└──────────────────────────────────────────────────────────────┘
```

অ্যাপ শুধু **L4-এর পাবলিক API** এবং L3-এর UI ক্লাস ব্যবহার করে। কোনো ইঞ্জিনের ইন্টারনাল
প্যাকেজে সরাসরি প্রবেশ করে না।

---

## ২. প্যাকেজ কাঠামো

```
com.roni.life.os.app
├── App.java                     Application — একবারই বুটস্ট্র্যাপ
├── activity/
│   ├── BaseActivity.java        কোর/স্টোর/কনফিগ/টোস্ট সহায়ক
│   ├── SplashActivity.java      Launcher + Main Startup Router
│   ├── BootstrapActivity.java   Project Manager + Config Controller
│   └── ProjectActivity.java     Runtime Host (NativeRuntimeActivity)
├── core/
│   ├── AppConfig.java           Main App স্টার্টআপ কনফিগ (সিরিয়ালাইজেবল)
│   ├── AppInitializer.java      ইনিশিয়ালাইজেশন পাইপলাইন + প্রথম-রান সিডিং
│   └── ApplicationCore.java     কেন্দ্রীয় সিঙ্গেলটন
├── project/
│   ├── Project.java             ডোমেইন এন্টিটি
│   ├── ProjectSourceType.java   ৬টি সোর্স টাইপ
│   ├── StartupDecision.java     দুই স্তরের স্টার্টআপ ম্যাট্রিক্স (pure Java)
│   ├── ProjectValidator.java    ভ্যালিডেশন নিয়ম
│   ├── ProjectFactory.java      ডিফল্ট/ডেমো/কপি ফ্যাক্টরি
│   ├── KeyValueBackend.java     স্টোরেজ অ্যাবস্ট্রাকশন
│   ├── KeyValueStoreBackend.java KeyValueStore অ্যাডাপ্টার
│   └── ProjectStore.java        CRUD + ডিফল্ট + স্টার্টআপ রেজোলিউশন
├── runtime/
│   ├── RuntimeBridge.java       ইকোসিস্টেমের একক ইন্টিগ্রেশন পয়েন্ট
│   ├── WebSourceFactory.java    Project → WebSource
│   ├── ProjectSourceInspector.java  সোর্স উপস্থিতি যাচাই
│   └── ProjectBootstrapRunner.java  ব্যাকগ্রাউন্ড বুট ধাপ
├── ui/
│   ├── ProjectAdapter.java      RecyclerView অ্যাডাপ্টার
│   └── ProjectEditDialog.java   Create/Edit ডায়ালগ
└── util/
    ├── Codec.java               URL-encoded KV কোডেক (pure Java)
    ├── AppLog.java              লগিং র্যাপার
    └── UiUtils.java             dp, drawable, visibility সহায়ক
```

---

## ৩. ক্লাসের দায়িত্ব

| ক্লাস | দায়িত্ব | নির্ভরতা |
| :--- | :--- | :--- |
| `App` | `ApplicationCore.init()` একবার ডাকা | `ApplicationCore` |
| `ApplicationCore` | স্টোর, কনফিগ, রানটাইম স্টেট ধরে রাখা | `AppInitializer`, `RuntimeBridge` |
| `AppInitializer` | রানটাইম বুট → স্টোরেজ → স্টোর → সিডিং | `RuntimeBridge`, `ProjectStore` |
| `RuntimeBridge` | ১৪টি ইঞ্জিনের নিরাপদ অ্যাক্সেস, ব্যর্থতা 흡ণ | L4/L2 পাবলিক API |
| `StartupDecision` | দুই স্তরের ম্যাট্রিক্স, **শূন্য Android নির্ভরতা** | — |
| `ProjectStore` | একমাত্র সত্যের উৎস | `KeyValueBackend` |
| `ProjectValidator` | ডেটা-স্তরের নিয়ম | `Project` |
| `ProjectSourceInspector` | ফাইল/অ্যাসেটের প্রকৃত উপস্থিতি | `AssetManager`, `File` |
| `ProjectBootstrapRunner` | ৪-ধাপ বুট, মেইন থ্রেডে কলব্যাক | `ProjectValidator`, `WebSourceFactory` |
| `WebSourceFactory` | ৬টি সোর্স টাইপ → `WebSource` | L3 `WebSource` |

---

## ৪. ডেটা প্রবাহ

### ৪.১ কোল্ড স্টার্ট

```
App.onCreate()
  └─ ApplicationCore.init(context)
       └─ AppInitializer.initialize(context)
            ├─ 1. ProjectStore(KeyValueStore) → AppConfig লোড
            ├─ 2. RuntimeBridge.initialize()  → NativeRuntime.init() → ১০-ধাপ বুট
            ├─ 3. প্রকৃত KeyValueStore নির্বাচন (ইঞ্জিন → ফলব্যাক)
            ├─ 4. প্রথম রান হলে ডেমো প্রজেক্ট সিড
            └─ 5. Result(store, config, runtimeReady, summary, duration)
  └─ SplashActivity.onCreate()
       ├─ StartupDecision.resolveMain(splash, bootstrap, hasProject)
       ├─ BOOTSTRAP → BootstrapActivity
       ├─ PROJECT   → ProjectActivity(projectId)
       └─ SPLASH    → layout + অ্যানিমেশন → resolveAfterSplash()
```

### ৪.২ প্রজেক্ট খোলা

```
BootstrapActivity.onProjectClicked()
  ├─ ProjectValidator.validate()  → ব্যর্থ হলে টোস্ট, ফেরত
  ├─ ProjectStore.markOpened(id)  → lastOpenedProjectId আপডেট
  └─ startActivity(ProjectActivity, EXTRA_PROJECT_ID)

ProjectActivity.onCreate()
  ├─ resolveProject()  → intent id → lastOpened → default
  ├─ StartupDecision.resolveProject(splash, bootstrap)
  ├─ onConfigureUI(builder)  → টুলবার, স্ট্যাটাস বার, থিম
  ├─ [Splash ON]  showProjectSplash() → duration পরে splashDone = true
  ├─ [Boot ON]    ProjectBootstrapRunner.run() → bootstrapDone = true
  └─ maybeLoadWebApp()  → দুই ফ্ল্যাগ সত্য হলে loadWebApp(WebSource)
```

`maybeLoadWebApp()` **দুই ফ্ল্যাগের AND** — তাই চারটি সমন্বয়ই সঠিক ক্রমে সম্পন্ন হয়।

---

## ৫. রানটাইম ইন্টিগ্রেশন পয়েন্ট

| ইঞ্জিন | ব্যবহৃত প্রকৃত API | ব্যবহার |
| :--- | :--- | :--- |
| `NativeRuntime` | `getInstance()`, `init(Context, RuntimeConfiguration)`, `isInitialized()`, `getState()`, `getBootstrapReport()` | কোর বুটস্ট্র্যাপ |
| `RuntimeConfiguration` | `builder()`, `appId`, `environment`, `debugEnabled`, `logLevel`, `storageEncryptionEnabled`, `networkTimeoutMs`, `maxNetworkRetries`, `telemetryEnabled` | কনফিগারেশন |
| `NativeRuntimeBootstrap` | `BootstrapReport.getPassedCount()`, `getSteps()`, `getTotalDurationMs()` | ডায়াগনস্টিকস |
| `NativeRuntimeUI` | `getInstance().isInitialized()` | স্ট্যাটাস |
| `NativeRuntimeActivity` (ui) | `onConfigureUI()`, `getUIComponentManager()`, `loadWebApp()`, `showLoading()`, `hideLoading()`, `showToast()`, `getWebViewHost()` | রানটাইম হোস্ট |
| `NativeRuntimeUIConfig.Builder` | `toolbarTitle`, `toolbarSubtitle`, `enableToolbar`, `enableDrawer`, `enableBottomNav`, `enableFab`, `enableWebView`, `statusBarColor`, `lightStatusBar`, `navBarColor`, `lightNavBar`, `themeMode`, `primaryColor`, `backgroundColor` | UI কনফিগারেশন |
| `WebSource` | `asset()`, `zipAsset()`, `file()`, `zipFile()`, `remote()`, `builder(WebSourceType)`, `withEntryPoint()` | সোর্স ম্যাপিং |
| `WebViewHost` | `canGoBack()`, `goBack()` | ব্যাক নেভিগেশন |
| `NativeUIComponentManager` | `showContent(View)` | কাস্টম কনটেন্ট |
| `StorageEngine` | `isInitialized()`, `getKeyValueStore()` | স্টোরেজ |
| `KeyValueStore` | `putString()`, `getString()`, `remove()` | পার্সিস্টেন্স |
| `NativeLogger` | `getInstance()`, `d/i/w/e(tag, msg)` | লগিং |
| `AnalyticsEngine` | `isInitialized()`, `trackEvent(String, Map)` | টেলিমেট্রি |
| `NetworkEngine` | `isInitialized()`, `getNetworkMonitor().isConnected()` | নেটওয়ার্ক স্ট্যাটাস |
| `DevToolsSDK` | `isInitialized()`, `getFpsMonitor().getCurrentFps()` | FPS |
| `ThemeEngine` / `PlatformEngine` / `SecurityEngine` / `NativeRouter` / `NativeServiceSDK` / `NativeWebBridgeSDK` / `NativeRuntimeEngine` | `isInitialized()` | ডায়াগনস্টিকস |

প্রতিটি কল `try/catch (Throwable)`-এ মোড়া — একটি ইঞ্জিন ব্যর্থ হলেও অ্যাপ চলবে।

---

## ৬. নকশা সিদ্ধান্ত

1. **`StartupDecision` pure Java** — Android SDK ছাড়াই দুই স্তরের ম্যাট্রিক্স যাচাইযোগ্য।
2. **`KeyValueBackend` অ্যাবস্ট্রাকশন** — প্রোডাকশন স্টোরেজ ও ইন-মেমরি টেস্ট মক উভয়ই সম্ভব।
3. **`RuntimeBridge` একক পয়েন্ট** — ১৪টি ইঞ্জিনের সাথে যোগাযোগ এক জায়গায়, প্রতিটি কল guarded।
4. **`ProjectBootstrapRunner` আলাদা থ্রেড** — মেইন থ্রেড কখনো ব্লক হয় না।
5. **Splash বন্ধ থাকলে layout inflate হয় না** — শূন্য ফ্লিকার, দ্রুত রুট।
6. **প্রথম-রান সিডিং** — খালি অ্যাপে ব্যবহারকারী আটকে থাকেন না।
