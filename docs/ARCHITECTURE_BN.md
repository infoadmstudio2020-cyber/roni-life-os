# RONiLiFEOS — সম্পূর্ণ প্রজেক্ট ও লাইব্রেরি ইকোসিস্টেম বিশ্লেষণ

> এই ডকুমেন্টটি সোর্স কোড সরাসরি পড়ে তৈরি। প্রতিটি মডিউলের ভূমিকা, অ্যাপের Activity প্রবাহ,
> স্টোরেজ বিন্যাস এবং ডেটা ফ্লো এখানে বর্ণনা করা হয়েছে।

---

## ১. এক নজরে

**RONiLiFEOS** একটি Android অ্যাপ্লিকেশন, যা নিজে কোনো নির্দিষ্ট কাজ করে না — এটি একটি
**মিনি-অপারেটিং সিস্টেম শেল**। ব্যবহারকারী নিজের ওয়েব প্রজেক্ট (HTML / CSS / JavaScript)
ইমপোর্ট করেন, অ্যাপ সেটি নিজের স্টোরেজে সংরক্ষণ করে এবং একটি WebView রানটাইমে চালায়।

| বিষয় | মান |
|---|---|
| ভাষা | Java 17 (Kotlin নয়, Compose নয়) |
| minSdk / targetSdk | 21 / 36 |
| লাইব্রেরি মডিউল | ১৫টি |
| অ্যাপ মডিউল | ১টি |
| মোট Java ফাইল | ~৬৭৭ |
| বর্তমান সংস্করণ | 1.6.1 (versionCode 15) |

---

## ২. স্তরভিত্তিক আর্কিটেকচার

মডিউলগুলো নিচ থেকে উপরে পাঁচ স্তরে সাজানো। উপরের স্তর নিচের স্তরের উপর নির্ভর করে,
কিন্তু নিচের স্তর উপরের স্তরকে চেনে না।

```
┌─────────────────────────────────────────────┐
│  app  (কনজিউমার অ্যাপ — Activity ও ড্যাশবোর্ড) │
├─────────────────────────────────────────────┤
│  native-runtime-sdk  (পাবলিক এন্ট্রি পয়েন্ট)   │
├─────────────────────────────────────────────┤
│  native-runtime-ui   (WebView হোস্ট ও UI কিট)  │
├─────────────────────────────────────────────┤
│  native-runtime-engine (রানটাইম সার্ভার)       │
├─────────────────────────────────────────────┤
│  native-runtime-contracts (ইন্টারফেস ও মডেল)   │
└─────────────────────────────────────────────┘
        ↑ এর পাশে স্বাধীন ইঞ্জিনগুলো ↑
  storage · network · security · platform · router
  theme · analytics · logger · service · devtools · bridge
```

---

## ৩. প্রতিটি লাইব্রেরি মডিউল

### ৩.১ `native-runtime-contracts` — ৬৬ ফাইল
পুরো ইকোসিস্টেমের **চুক্তি (contract)** স্তর। এখানে কোনো বাস্তবায়ন নেই, শুধু ইন্টারফেস ও মডেল।
প্যাকেজ: `service`, `common`, `theme`, `runtime`, `platform`, `api`, `logger`, `models`,
`interfaces`, `ui`, `analytics`, `utils`।

**কেন গুরুত্বপূর্ণ:** অন্য সব মডিউল এই ইন্টারফেসগুলোর বিরুদ্ধে কোড করে, তাই একটি ইঞ্জিন
বদলালেও বাকি কোড অপরিবর্তিত থাকে।

### ৩.২ `native-runtime-engine` — ২৬ ফাইল
রানটাইমের **সার্ভার অংশ**। প্যাকেজ: `server`, `service`, `vitals`, `container`, `source`, `handler`।
এটি প্রজেক্টের সোর্স রিজলভ করে, রিসোর্স সার্ভ করে এবং রানটাইম কন্টেইনার চালায়।

### ৩.৩ `native-runtime-ui` — ২০২ ফাইল (সবচেয়ে বড়)
সম্পূর্ণ **UI কিট ও WebView হোস্ট**। প্রধান প্যাকেজ:

| প্যাকেজ | কাজ |
|---|---|
| `ui/web` | `WebViewHost` — WebView তৈরি, কনফিগ, নেভিগেশন, ডাউনলোড লিসেনার |
| `ui/components/*` | রিইউজেবল কম্পোনেন্ট (input, dialog, appbar, feedback, animation…) |
| `ui/navigation` | স্ক্রিন নেভিগেশন |
| `ui/theme`, `ui/customization` | থিম ও কাস্টমাইজেশন |
| `ui/download` | `DownloadCenter` — ডাউনলোড পাইপলাইন |
| `ui/renderer`, `ui/builder` | ডাইনামিক ভিউ তৈরি |
| `ui/overlay`, `ui/dialog` | ওভারলে ও ডায়ালগ |
| `ui/config` | প্রজেক্ট UI কনফিগ ম্যাপিং |

### ৩.৪ `native-runtime-sdk` — ১০ ফাইল
বাইরের জগতের জন্য **পাবলিক API**। `os/bootstrap` ও `os/activity` — এখানেই
`NativeRuntimeActivity`-র মতো এন্ট্রি ক্লাস থাকে, যা অ্যাপের `ProjectActivity` এক্সটেন্ড করে।

### ৩.৫ `native-web-bridge-sdk` — ২৫ ফাইল
**JavaScript ↔ Java সেতু**। প্যাকেজ: `bridge/js`, `bridge/message`, `bridge/serializer`,
`bridge/event`, `bridge/handler`। ওয়েব পেজ `window.*` দিয়ে নেটিভ ফিচার কল করতে পারে।

### ৩.৬ `native-storage-engine` — ২৪ ফাইল
স্টোরেজ স্তর: `storage/database`, `storage/preferences`, `storage/crypto`, `storage/service`।
ফাইল, কী-ভ্যালু ও এনক্রিপ্টেড ডেটা — তিন ধরনের সংরক্ষণ এখানে।

### ৩.৭ `native-network-engine` — ২৯ ফাইল
নেটওয়ার্ক স্তর: `network/http`, `network/ws` (WebSocket), `network/monitor` (কানেক্টিভিটি),
`network/service`।

### ৩.৮ `native-security-engine` — ২৫ ফাইল
নিরাপত্তা: `security/permission`, `security/biometric`, `security/keystore`, `security/crypto`।
SAF পারমিশন ও বায়োমেট্রিক আনলক এখানেই।

### ৩.৯ `native-platform-engine` — ৩০ ফাইল
Android প্ল্যাটফর্মের মোড়ক: `platform/service`, `platform/model`, `platform/listener`,
`platform/exception`। ডিভাইস তথ্য, ইন্টেন্ট, সিস্টেম সার্ভিস।

### ৩.১০ `native-router-sdk` — ২৪ ফাইল
রাউটিং: `router/matcher`, `router/interceptor`, `router/service`। URL প্যাটার্ন ম্যাচিং ও
ইন্টারসেপ্টর চেইন।

### ৩.১১ `native-theme-engine` — ২৮ ফাইল
থিম ইঞ্জিন: `theme/parser`, `theme/manager`, `theme/observer`। CSS-সদৃশ থিম পার্স করে
পুরো UI-তে প্রয়োগ করে।

### ৩.১২ `native-analytics-engine` — ২১ ফাইল
অ্যানালিটিক্স: `analytics/tracker`, `analytics/queue`, `analytics/dispatcher`, `analytics/provider`।
ইভেন্ট কিউ করে ব্যাচে পাঠায়।

### ৩.১৩ `native-logger-sdk` — ১৫ ফাইল
লগিং: `logger/sink`, `logger/formatter`। একাধিক আউটপুট (ফাইল, কনসোল, রিমোট)।

### ৩.১৪ `native-service-sdk` — ২২ ফাইল
সার্ভিস রেজিস্ট্রি ও লাইফসাইকল: `service/provider`, `service/lifecycle`, `service/builtins`।
সব ইঞ্জিন এখানে রেজিস্টার হয় এবং নির্দিষ্ট ক্রমে শুরু/বন্ধ হয়।

### ৩.১৫ `native-dev-tools-sdk` — ৩১ ফাইল
ডেভেলপার টুলস: `devtools/server`, `devtools/profiler`, `devtools/network`, `devtools/diagnostics`,
`devtools/protocol`। ডিভাইসে ডিবাগিং ব্রিজ।

---

## ৪. কনজিউমার অ্যাপ (`app`)

### ৪.১ তিনটি প্রধান Activity

| Activity | দায়িত্ব |
|---|---|
| `SplashActivity` | ঐচ্ছিক স্টার্টআপ স্ক্রিন; কনফিগ পড়ে গন্তব্য নির্ধারণ করে |
| `BootstrapActivity` | মূল ড্যাশবোর্ড — প্রজেক্ট তৈরি, ইমপোর্ট, রেজিস্টার, ডিলিট, লঞ্চ |
| `ProjectActivity` | একটি প্রজেক্টের রানটাইম হোস্ট (WebView) |

সহায়ক Activity: `BootstrapWebActivity` (ওয়েব ড্যাশবোর্ড), `ProjectSlotActivity` (স্লট ম্যানেজমেন্ট),
`ProjectUIEditorActivity` (প্রজেক্ট UI এডিটর), `DownloadsActivity` (ডাউনলোড ম্যানেজার)।

### ৪.২ প্যাকেজ বিভাজন

| প্যাকেজ | ভূমিকা | প্রধান ক্লাস |
|---|---|---|
| `core` | স্টোরেজ বিন্যাস, কনফিগ, বুটস্ট্র্যাপ | `StorageLayout`, `StorageSpec`, `AppConfig`, `RONiLiFEOSStorageInitializer`, `ApplicationCore` |
| `project` | প্রজেক্ট মডেল ও রেজিস্ট্রি | `Project`, `ProjectStore`, `ProjectImporter`, `ProjectValidator`, `StartupDecision` |
| `runtime` | স্টার্টআপ ও সেশন | `StartupManager`, `ProjectLaunchRouter`, `ProjectSessionManager`, `RuntimeBridge` |
| `source` | Default/Runtime সোর্স ম্যানেজমেন্ট | `SourceManager`, `SourceResolver`, `SourceConfig`, `SourceValidator` |
| `resource` | রিসোর্স ডাউনলোড ও যাচাই | `ProjectResourceManager`, `ResourceFetcher`, `ArchiveExtractor`, `ResourceVerifier` |
| `ui` | ড্যাশবোর্ড ভিউ | `ProjectAdapter`, `ProjectControlSheet`, `ProjectEditDialog` |
| `uiconfig` | প্রজেক্ট UI কনফিগ সংরক্ষণ | `ProjectUIConfigStore` |
| `util` | সহায়ক | `AppLog`, `Codec`, `UiUtils` |

### ৪.৩ স্টোরেজ বিন্যাস

সব ডেটা থাকে `Android/data/<package>/files/RONiLiFEOS/` এর নিচে:

```
RONiLiFEOS/
├── system/
│   ├── splash/{default,runtime,config}/
│   ├── bootstrap/{default,runtime,config}/
│   ├── config/          (app-config, storage-config, runtime-config)
│   └── database/
├── app/
│   ├── projects/default/ (manifest, ui, sources, config, resources)
│   ├── config/           (project-registry.json, active-project.json)
│   ├── backups/  imports/
├── runtime/  (workspace, cache, sessions, logs, config)
└── shared/   (assets, themes, resources)
```

**Default বনাম Runtime:** `default/` ফোল্ডার APK-এর `assets/ronilifeos/` থেকে সিড হয় এবং
কখনো ওভাররাইট হয় না। `runtime/` ব্যবহারকারীর সম্পাদনাযোগ্য — Create, Import, Replace,
Validate, Activate, Delete, Reset।

### ৪.৪ স্টার্টআপ সিদ্ধান্ত

`StartupManager.resolve(store)` প্রথমে অকার্যকর রেফারেন্স পরিষ্কার করে, তারপর কনফিগ পড়ে
গন্তব্য ঠিক করে:

| Splash | Dashboard | ফলাফল |
|---|---|---|
| চালু | চালু | Splash → Bootstrap |
| বন্ধ | চালু | Bootstrap |
| চালু | বন্ধ | Splash → Default Project |
| বন্ধ | বন্ধ | Default Project |

**ব্যতিক্রম:** কোনো বৈধ প্রজেক্ট না থাকলে ড্যাশবোর্ড বাধ্যতামূলকভাবে খোলে — কনফিগ বন্ধ থাকলেও।
`StartupDecision.resolveMain(...)` এই ম্যাট্রিক্সটি ধারণ করে।

### ৪.৫ প্রজেক্ট লঞ্চ প্রবাহ

```
BootstrapActivity
   └─ ProjectLaunchRouter.launch(projectId)
        ├─ ProjectStore থেকে মেটাডেটা
        ├─ ProjectValidator (সোর্স আছে?)
        ├─ ProjectSessionManager (সেশন তৈরি)
        └─ Intent → ProjectActivity (EXTRA_PROJECT_ID)
              └─ NativeRuntimeActivity → WebViewHost → index.html
```

### ৪.৬ ব্যাক নেভিগেশন

`ProjectActivity`-তে `OnBackPressedCallback` রেজিস্টার করা:
১. `canGoBack()` → `goBack()` (আগের পেজ)
২. না থাকলে `minimizeToDashboard()` (রানটাইম ধ্বংস হয় না)
৩. ৪০০ ms ডিবাউন্স ডাবল-ট্যাপ আটকায়

### ৪.৭ ডাউনলোড সিস্টেম

`DownloadCenter` (লাইব্রেরি) তিন ধরনের URL সামলায়:

| স্কিম | পথ |
|---|---|
| `http` / `https` | সিস্টেম `DownloadManager` (নোটিফিকেশন, রিজিউম) |
| `data:` | সরাসরি ডিকোড করে লেখা |
| `blob:` | পেজ base64-এ রূপান্তর করে `window.RONiDownload`-এ পাঠায় |

ফাইল জমা হয় `Download/RONiLiFEOS`-এ। `DownloadsActivity` সেই তালিকা দেখায় —
Pause, Resume, Cancel, Delete এবং Clear all।

---

## ৫. ডেটা ফ্লো

```
অ্যাপ চালু
   ↓
ApplicationCore → RONiLiFEOSStorageInitializer (ফোল্ডার সিড + যাচাই)
   ↓
SplashActivity → StartupManager.resolve()
   ↓
BootstrapActivity (ড্যাশবোর্ড)  ──→  ProjectActivity (রানটাইম)
   ↑                                      │
   └────────── Back / minimize ───────────┘
```

---

## ৬. বিল্ড ও সংস্করণ

- Gradle মডিউল: ১৬টি (`settings.gradle`)
- `app/build.gradle`: `versionCode 15`, `versionName "1.6.1"`
- নেটওয়ার্ক, SAF, MediaStore — সবই Android স্ট্যান্ডার্ড API দিয়ে, কোনো থার্ড-পার্টি
  HTTP লাইব্রেরি (OkHttp/Retrofit) নেই।

---

## ৭. যাচাইয়ের সীমা

এই বিশ্লেষণ সোর্স কোড পড়ে তৈরি। স্যান্ডবক্সে Android SDK, Gradle বা `javac` নেই,
তাই **কোনো মডিউল কম্পাইল বা রান করা হয়নি**। ক্লাসের দায়িত্ব ও প্যাকেজ গঠন কোড থেকে
নেওয়া, কিন্তু আচরণ (runtime behavior) যাচাই করা হয়নি।
