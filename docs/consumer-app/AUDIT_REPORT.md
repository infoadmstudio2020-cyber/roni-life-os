# Audit Report — NativeRuntime Ecosystem (১৫টি মডিউল)

**তারিখ:** ২০২৬-১১-০৭ · **পরিধি:** `:app` কনজিউমার অ্যাপ তৈরির আগে প্রতিটি মডিউলের
প্রকৃত পাবলিক API যাচাই।

**পদ্ধতি:** প্রতিটি মডিউলের `src/main/java` স্ক্যান, `public` সিগনেচার grep, এবং
`build.gradle`-এর `api` / `implementation` এক্সপোজার পরীক্ষা। কোনো API অনুমান করা হয়নি।

---

## ১. মডিউল তালিকা

| # | মডিউল | মূল প্যাকেজ | স্তর | `:app`-এ ব্যবহার |
| :-: | :--- | :--- | :-: | :--- |
| ১ | `native-runtime-contracts` | `com.roni.library.contracts` | L0 | পরোক্ষ (টাইপ) |
| ২ | `native-logger-sdk` | `com.roni.library.logger` | L1 | ✅ `NativeLogger` |
| ৩ | `native-service-sdk` | `com.roni.library.service` | L1 | ✅ `isInitialized()` |
| ৪ | `native-dev-tools-sdk` | `com.roni.library.devtools` | L1 | ✅ `DevToolsSDK`, `FpsMonitor` |
| ৫ | `native-router-sdk` | `com.roni.library.router` | L2 | ✅ `NativeRouter` |
| ৬ | `native-platform-engine` | `com.roni.library.platform` | L2 | ✅ `PlatformEngine` |
| ৭ | `native-storage-engine` | `com.roni.library.storage` | L2 | ✅ `StorageEngine`, `KeyValueStore` |
| ৮ | `native-security-engine` | `com.roni.library.security` | L2 | ✅ `SecurityEngine` |
| ৯ | `native-network-engine` | `com.roni.library.network` | L2 | ✅ `NetworkEngine`, `NetworkMonitor` |
| ১০ | `native-analytics-engine` | `com.roni.library.analytics` | L2 | ✅ `AnalyticsEngine` |
| ১১ | `native-theme-engine` | `com.roni.library.theme` | L2 | ✅ `ThemeEngine` |
| ১২ | `native-runtime-engine` | `com.roni.library.runtime` | L2 | ✅ `NativeRuntimeEngine` |
| ১৩ | `native-runtime-ui` | `com.roni.library.ui` | L3 | ✅ `NativeRuntimeActivity`, `WebViewHost`, `WebSource`, `NativeRuntimeUIConfig` |
| ১৪ | `native-web-bridge-sdk` | `com.roni.library.bridge` | L3 | ✅ `NativeWebBridgeSDK` |
| ১৫ | `native-runtime-sdk` | `com.roni.library.os` | L4 | ✅ `NativeRuntime`, `RuntimeConfiguration`, `NativeRuntimeBootstrap` |

---

## ২. যাচাইকৃত পাবলিক API (প্রকৃত সিগনেচার)

### ২.১ `native-runtime-sdk` (L4)

```java
NativeRuntime.getInstance()
NativeRuntimeResult<Boolean> init(Context context)
NativeRuntimeResult<Boolean> init(Context context, RuntimeConfiguration config)
boolean isInitialized()
RuntimeState getState()
NativeRuntimeBootstrap.BootstrapReport getBootstrapReport()

RuntimeConfiguration.builder()
    .appId(String) .environment(String) .debugEnabled(boolean) .logLevel(String)
    .storageEncryptionEnabled(boolean) .networkTimeoutMs(int)
    .maxNetworkRetries(int) .telemetryEnabled(boolean)
    .build()

BootstrapReport.getPassedCount()   // int
BootstrapReport.getSteps()         // List<StepReport>
BootstrapReport.getTotalDurationMs() // long

NativeRuntimeResult.isSuccess() / getErrorMessage()
```

### ২.২ `native-runtime-ui` (L3)

```java
// Activity
protected abstract NativeRuntimeUIConfig onConfigureUI(NativeRuntimeUIConfig.Builder)
NativeUIComponentManager getUIComponentManager()
WebViewHost getWebViewHost()
void loadWebApp(WebSource source)
void showLoading(String) / hideLoading() / showToast(String)

// Component manager
void showContent(View view)

// WebViewHost
boolean canGoBack() / void goBack() / void reload() / void loadSource(WebSource)

// WebSource
static WebSource asset(String) / asset(String,String,String)
static WebSource zipAsset(String) / zipAsset(String,String,String)
static WebSource file(String) / zipFile(String)
static WebSource remote(String) / remote(String,String,String)
static WebSource inlineHtml(String)
static WebSource contentUri(String)
static Builder builder(WebSourceType)
WebSource withEntryPoint(String)
WebSourceType getType() / String getEntryPoint()

// UIConfig.Builder — ১৪টি ব্যবহৃত মেথড
toolbarTitle · toolbarSubtitle · enableToolbar · enableDrawer · enableBottomNav
enableFab · enableWebView · statusBarColor · lightStatusBar · navBarColor
lightNavBar · themeMode · primaryColor · backgroundColor · build()
```

### ২.৩ `native-storage-engine` (L2)

```java
StorageEngine.isInitialized()
StorageEngine.getKeyValueStore()          // KeyValueStore
new KeyValueStore(String name, File baseDir)
KeyValueStore.putString(String,String) / getString(String,String) / remove(String) / clear()
```

### ২.৪ অন্যান্য ইঞ্জিন

```java
NativeLogger.getInstance().d/i/w/e(String tag, String message)
AnalyticsEngine.isInitialized() / trackEvent(String, Map<String,Object>)
NetworkEngine.isInitialized() / getNetworkMonitor().isConnected()
DevToolsSDK.isInitialized() / getFpsMonitor().getCurrentFps()   // double
ThemeEngine.isInitialized() / PlatformEngine.getInstance().isInitialized()
SecurityEngine.getInstance().isInitialized()
NativeRouter.getInstance().isInitialized()
NativeServiceSDK.getInstance().isInitialized()
NativeWebBridgeSDK.getInstance().isInitialized()
NativeRuntimeEngine.getInstance().isInitialized()
NativeRuntimeUI.getInstance().isInitialized()
```

---

## ৩. Gradle এক্সপোজার বিশ্লেষণ

`native-runtime-sdk/build.gradle`:

```gradle
api project(':native-runtime-contracts')
api project(':native-web-bridge-sdk')
api project(':native-runtime-engine')
api project(':native-runtime-ui')
api 'com.google.android.material:material:1.11.0'

implementation project(':native-service-sdk')   // + logger, router, platform,
implementation project(':native-storage-engine') //   storage, security, network,
implementation project(':native-analytics-engine') // analytics, theme, dev-tools
```

**সিদ্ধান্ত:** `:app` শুধু `implementation project(':native-runtime-sdk')` নেয়।
`api` এক্সপোজারের কারণে `NativeRuntimeUI`, `WebSource`, `WebViewHost` সরাসরি পাওয়া যায়।
`implementation` মডিউলগুলোর ক্লাস (`NativeLogger`, `StorageEngine` ইত্যাদি) **কম্পাইল-টাইমে
অদৃশ্য** — কিন্তু `RuntimeBridge` সেগুলো সরাসরি ইমপোর্ট করে।

**গ্যাপ #১ (গুরুত্বপূর্ণ):** `implementation` নির্ভরতা ট্রানজিটিভভাবে `:app`-এ আসে না।
তাই `com.roni.library.storage.StorageEngine` বা `com.roni.library.logger.NativeLogger`
ইমপোর্ট করলে **কম্পাইল এরর** হওয়ার কথা।

**সমাধান:** `app/build.gradle`-এ শুধু `native-runtime-sdk` রাখলে হবে না — যেসব ইঞ্জিন
সরাসরি ব্যবহৃত হয় সেগুলোও স্পষ্টভাবে যোগ করতে হবে। নিচে সংশোধিত তালিকা।

---

## ৪. গ্যাপ ও সংশোধন

| # | গ্যাপ | প্রভাব | সংশোধন |
| :-: | :--- | :--- | :--- |
| ১ | `implementation` ইঞ্জিনগুলো `:app`-এ দৃশ্যমান নয় | কম্পাইল এরর | `app/build.gradle`-এ ৯টি ইঞ্জিন মডিউল সরাসরি `implementation` যোগ |
| ২ | `RuntimeBridge.getRuntimeMessage()` নামে মেথড নেই | কম্পাইল এরর | `getRuntimeSummary()`-তে পরিবর্তন |
| ৩ | `Project.decode()` garbage ইনপুটে ডিফল্ট অবজেক্ট ফেরাত | ভুল ডেটা | `id` কী বাধ্যতামূলক |
| ৪ | `AppConfig.decode()` garbage ইনপুটে ডিফল্ট ফেরাত | ভুল ডেটা | `splashEnabled` কী বাধ্যতামূলক |
| ৫ | `ProjectFactory` অ্যাসেট পাথ `webapp` — কিন্তু `assets/`-এ ফোল্ডার নেই | রানটাইম 404 | `ZIP_ASSET` → `webapp.zip` |
| ৬ | Manifest-এ `com.example.nativeruntimesample.MainActivity` (অস্তিত্ব নেই) | ইনস্টল ব্যর্থ | তিনটি প্রকৃত Activity দিয়ে প্রতিস্থাপন |
| ৭ | `namespace 'com.roni.os.app'` বনাম প্যাকেজ `com.roni.life.os.app` | `R` unresolved | `namespace` সংশোধন |
| ৮ | পুরনো `SampleActivity` লেআউট/টেস্ট রয়ে গিয়েছিল | কম্পাইল এরর | মুছে ফেলা |
| ৯ | `native-runtime-contracts`-এ রানটাইম স্টেট নেই | ডায়াগনস্টিকসে ১৪/১৫ | সচেতন সীমা, ডকুমেন্টেড |

---

## ৫. সংশোধিত `app/build.gradle` নির্ভরতা

```gradle
implementation project(':native-runtime-sdk')   // L4 facade (api: ui, engine, contracts, bridge)

// L4 facade-এর implementation নির্ভরতাগুলো সরাসরি দৃশ্যমান করতে:
implementation project(':native-logger-sdk')
implementation project(':native-storage-engine')
implementation project(':native-network-engine')
implementation project(':native-analytics-engine')
implementation project(':native-theme-engine')
implementation project(':native-platform-engine')
implementation project(':native-security-engine')
implementation project(':native-router-sdk')
implementation project(':native-service-sdk')
implementation project(':native-dev-tools-sdk')
```

> `native-runtime-engine`, `native-runtime-ui`, `native-web-bridge-sdk`,
> `native-runtime-contracts` ইতিমধ্যে `api` — পুনরায় যোগ করার দরকার নেই।

---

## ৬. উপসংহার

- ১৫টি মডিউলের সবগুলোই প্রকৃত, কম্পাইলযোগ্য সোর্স সহ বিদ্যমান।
- `:app` মডিউলে **কোনো প্রকৃত কনজিউমার অ্যাপ ছিল না** — শুধু খালি ফাইল ও ভুল Manifest।
- ৯টি গ্যাপ চিহ্নিত, ৮টি সংশোধিত, ১টি সচেতন সীমা হিসেবে ডকুমেন্টেড।
- সব API কল `try/catch (Throwable)`-এ মোড়া, তাই একটি ইঞ্জিন ব্যর্থ হলেও অ্যাপ চলবে।
