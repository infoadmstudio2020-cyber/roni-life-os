# File & Folder Reference — রিপোজিটরির প্রতিটি অংশ

## ১. শীর্ষ স্তর

| পাথ | ধরন | কাজ |
|---|---|---|
| `app/` | Android application | ডেমো অ্যাপ, project import/management UI |
| `native-runtime-sdk/` | library | সব মডিউল একত্র করা façade |
| `native-runtime-ui/` | library | WebView host + ৪টি command handler |
| `native-runtime-engine/` | library | WebView lifecycle, `WebSource` |
| `native-web-bridge-sdk/` | library | JS ↔ Native transport |
| `native-router-sdk/` | library | route matching |
| `native-storage-engine/` | library | `EncryptedStore`, `StorageScope` |
| `native-network-engine/` | library | HTTP client, network state |
| `native-theme-engine/` | library | theme manager |
| `native-platform-engine/` | library | platform info |
| `native-security-engine/` | library | crypto, permission |
| `native-analytics-engine/` | library | event batching |
| `native-logger-sdk/` | library | logging |
| `native-service-sdk/` | library | service registry |
| `native-dev-tools-sdk/` | library | debug tools |
| `native-runtime-contracts/` | library | interface/contract মাত্র |
| `docs/bangla/` | documentation | ২৫টি বাংলা ডক |
| `examples/` | web projects | ১৪ ডেমো + notes app + starter |
| `tools/verify/` | scripts | compile-free যাচাই |
| `gradle/`, `gradlew`, `settings.gradle`, `build.gradle` | build | Gradle 8.11.1, AGP 8.10.1 |

## ২. প্রতিটি মডিউলের ভেতরের গঠন

```
native-<name>/
├── build.gradle
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/roni/library/<name>/
    │   └── res/
    └── test/java/...          ← JVM unit test (Robolectric নয়)
```

## ৩. `native-runtime-ui` — সবচেয়ে গুরুত্বপূর্ণ মডিউল

```
native-runtime-ui/src/main/java/com/roni/library/ui/
├── NativeRuntimeUI.java              façade, route registration
├── host/NativeUIHost.java            Activity + config + projectId
├── handler/
│   ├── NativeUICommandHandler.java       ৬২ action
│   ├── NativeWebCommandHandler.java      ৫৫ action
│   ├── NativeDeviceCommandHandler.java   ২৮ action
│   └── NativeStorageCommandHandler.java   ৭ action
├── web/
│   ├── WebViewHost.java              WebView তৈরি ও client
│   ├── WebSource.java                source descriptor
│   ├── WebSourceType.java            ৬ ধরন
│   ├── AssetResourceProvider.java    APK assets
│   ├── FileResourceProvider.java     device file
│   ├── ZipResourceProvider.java      ZIP archive
│   ├── SafResourceProvider.java      content:// URI
│   ├── MimeTypes.java                ৩৩ এক্সটেনশন
│   └── WebPaths.java                 path safety
├── config/
│   ├── ProjectUIConfig.java
│   ├── ProjectUIConfigMapper.java
│   ├── ProjectUIConfigPresets.java
│   └── ProjectUIConfigValidator.java
├── content/WebViewContainer.java
└── components/webhost/WebViewContainerComponent.java
```

## ৪. `app` — project management

```
app/src/main/java/com/roni/life/os/app/
├── project/
│   ├── Project.java                  model
│   ├── ProjectType.java
│   ├── ProjectSourceType.java
│   ├── ProjectManifest.java          manifest.json যাচাই
│   ├── ProjectImporter.java          ZIP import + entry detection
│   ├── ProjectValidator.java         নাম/id সংঘর্ষ যাচাই
│   ├── ProjectStore.java             persistence
│   ├── ProjectRegistrationService.java
│   ├── ProjectDeletionService.java
│   ├── ProjectRestoreService.java
│   ├── ProjectVaultRuntime.java      এনক্রিপ্টেড vault
│   ├── ProjectRoleResolver.java
│   ├── ProjectTestRunner.java
│   ├── SafPermissionManager.java     persistable URI permission
│   ├── KeyValueBackend.java / KeyValueStoreBackend.java
│   ├── SealDecision.java / StartupDecision.java
│   └── VaultResourceDecoder.java
└── resource/
    ├── ArchiveExtractor.java         নিরাপদ ZIP extraction
    ├── ProjectResourceManager.java
    ├── ProjectResourceStore.java
    ├── ProjectResourceFactory.java
    └── ProjectResourceState.java
```

## ৫. `examples/` — তিনটি Web Project

| ফোল্ডার | কী | ফাইল |
|---|---|---|
| `bangla-demos/` | ১৪টি একক-পৃষ্ঠা ডেমো + hub | ১৫ HTML + `native.js` |
| `bangla-notes-app/` | পূর্ণ অ্যাপ (router + store) | ৭ ফাইল |
| `webapp-starter/` | ES6 module starter | ৬ ফাইল |

## ৬. `docs/bangla/` — ২৫টি ডক

| # | ফাইল | বিষয় |
|---|---|---|
| ১ | `README.md` | সূচি ও শুরু |
| ২ | `PLATFORM_OVERVIEW.md` | প্ল্যাটফর্মের সারসংক্ষেপ |
| ৩ | `ARCHITECTURE.md` | স্তর ও নির্ভরতা |
| ৪ | `WEB_PROJECT_STRUCTURE.md` | ফোল্ডার কাঠামো |
| ৫ | `FILE_AND_FOLDER_REFERENCE.md` | এই ফাইল |
| ৬ | `PROGRAMMING_LANGUAGES.md` | ভাষা ও ভার্সন |
| ৭ | `NATIVE_API_REFERENCE.md` | Java API |
| ৮ | `WEBBRIDGE_REFERENCE.md` | bridge প্রোটোকল |
| ৯ | `API_REGISTRATION_MATRIX.md` | ১৫২ action ম্যাট্রিক্স |
| ১০ | `FEATURE_CATALOG.md` | ফিচার ক্যাটালগ |
| ১১ | `JAVASCRIPT_INTEGRATION.md` | JS API |
| ১২ | `STORAGE_AND_FILE_GUIDE.md` | storage |
| ১৩ | `NAVIGATION_AND_UI_GUIDE.md` | navigation + UI |
| ১৪ | `DEVICE_API_GUIDE.md` | device API |
| ১৫ | `DOWNLOAD_AND_NETWORK_GUIDE.md` | download/network |
| ১৬ | `EVENT_AND_CALLBACK_GUIDE.md` | event |
| ১৭ | `SECURITY_AND_PERMISSION_GUIDE.md` | নিরাপত্তা |
| ১৮ | `PROJECT_IMPORT_AND_EXECUTION.md` | import ও চালু |
| ১৯ | `WEB_APP_DEVELOPMENT_GUIDE.md` | অ্যাপ বানানোর গাইড |
| ২০ | `AI_WEB_DEVELOPMENT_INSTRUCTIONS.md` | AI এজেন্টের নির্দেশনা |
| ২১ | `WORKING_EXAMPLES.md` | উদাহরণের ব্যাখ্যা |
| ২২ | `TESTING_AND_VERIFICATION.md` | টেস্ট |
| ২৩ | `TROUBLESHOOTING.md` | সমস্যা সমাধান |
| ২৪ | `API_GAP_ANALYSIS.md` | কী নেই |
| ২৫ | `FINAL_AUDIT_REPORT.md` | চূড়ান্ত অডিট |

সহায়ক: `API_COMPATIBILITY.md`, `API_USAGE_GUIDE.md`, `STORAGE_ISOLATION.md`।

## ৭. `tools/verify/` — যাচাই স্ক্রিপ্ট

| ফাইল | কাজ |
|---|---|
| `extract_actions.py` | Java থেকে action তালিকা → `actions.json` |
| `storage_scope_test.py` | ৬৮ assertion |
| `java_sanity.py` | structural check |
| `wrapper_test.mjs` | ২৬ assertion (jsdom) |
| `examples_runtime_test.mjs` | ২৬ উদাহরণ runtime |
| `smoke.mjs` | bootstrap ১৮ assertion |
| `gen_matrix.py` | matrix markdown পুনরুৎপাদন |
| `responses.mjs` | mock bridge response |

## ৮. যে ফাইলগুলো হাতে সম্পাদনা করবেন না

| ফাইল | কারণ |
|---|---|
| `gradle/wrapper/gradle-wrapper.jar` | binary, checksum-নির্ভর |
| `app/src/main/assets/`-এর ভেতরের project | import-এ overwrite |
| `actions.json` | স্ক্রিপ্ট উৎপন্ন করে |
| `docs/bangla/API_REGISTRATION_MATRIX.md` | `gen_matrix.py` উৎপন্ন করে |
