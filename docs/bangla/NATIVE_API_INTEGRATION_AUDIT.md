# Native API Integration Audit — Phase 12.1

## ১. পদ্ধতি

প্রতিটি library-র `handler/` প্যাকেজ, manager/service ক্লাস এবং `build.gradle` পড়া হয়েছে।
"লাইব্রেরি আছে" মানে "API আছে" — এই অনুমান করা হয়নি; প্রতিটি action `switch`/`if` থেকে
হুবহু নেওয়া।

## ২. নয়টি লাইব্রেরির অবস্থা

| Library | Manager/Service | Handler | Action | অবস্থা |
|---|---|---|---|---|
| theme | `ThemeManager` | `ThemeCommandHandler` | ৬ | ✅ সম্পূর্ণ |
| security | `SecurityEngine` | `SecurityCommandHandler` | ১১ | ⚠️ biometric callback ফাঁকা ছিল |
| analytics | `NativeAnalyticsService` | `AnalyticsCommandHandler` | ১১ | ✅ সম্পূর্ণ |
| devtools | `NativeDevToolsService` | `DevToolsCommandHandler` | ৯ | ✅ সম্পূর্ণ |
| network | `NativeNetworkService` | `NetworkCommandHandler` | ১ | ⚠️ PATCH/headers/timeout ছিল না |
| platform | `PlatformEngine` | `PlatformCommandHandler` | ১৫ | ✅ সম্পূর্ণ |
| logger | `NativeLogger` | `LoggerCommandHandler` | ৪ | ✅ সম্পূর্ণ |
| service | `ServiceRegistry` | `ServiceCommandHandler` | ২ | ✅ সম্পূর্ণ |
| storage | `EncryptedStore` | `StorageCommandHandler` | ৭ | ⚠️ route সংঘর্ষ |

## ৩. যে সমস্যাগুলো পাওয়া গেছে

| # | সমস্যা | প্রমাণ | সমাধান |
|---|---|---|---|
| ১ | কোনো engine-ই bridge-এ নিবন্ধিত ছিল না | `NativeRuntimeUI.registerBridgeRoutes()` শুধু ui/web/device/storage নিবন্ধন করে | `NativeApiIntegration` তৈরি |
| ২ | `biometric.authenticate` সবসময় `{initiated:true}` ফেরায়, callback ফাঁকা | `SecurityCommandHandler` লাইন ~৫০০ | `BiometricResultListener` + `security.biometric.result` ইভেন্ট |
| ৩ | `NetworkCommandHandler` শুধু GET/POST/PUT/DELETE | `methodStr` ternary chain | `HttpMethod.values()` + PATCH/HEAD/OPTIONS |
| ৪ | network-এ header/timeout নেই | `HttpRequest.Builder` ব্যবহার হয়নি | `headers`, `timeoutMs` যোগ |
| ৫ | `storage` route দ্বিগুণ | `StorageCommandHandler.getPath()` = `storage` | storage-engine handler নিবন্ধন **করা হয়নি** |
| ৬ | `theme` দ্বিগুণ প্যাকেজ | `theme/` ও `themeengine/` | `themeengine` `@Deprecated`, ব্যবহার হয়নি |
| ৭ | `NativeRuntimeUIConfig` ও `ThemeManager` — দুই source of truth | config-এ themeMode/primaryColor | `ThemeManager` authoritative; config শুধু seed |
| ৮ | Activity ছাড়া biometric চলে না | `AndroidBiometricPromptDelegate(FragmentActivity)` | `attachBiometricHost()` + `onResume` re-attach |

## ৪. যা যোগ করা হয়নি — ইচ্ছাকৃত

| জিনিস | কারণ |
|---|---|
| `biometric.cancel` | `BiometricPrompt` instance `showPrompt()`-এর লোকাল; handle রাখা হয় না। বাতিল হয় সিস্টেম ডায়ালগের Cancel বাটনে → `onAuthenticationError(ERROR_NEGATIVE_BUTTON)` |
| `network.cancel` | `HttpClientEngine.execute()` synchronous, cancellation token নেই |
| `analytics` consent API | `NativeAnalyticsEngineConfig`-এ consent ফ্ল্যাগ নেই |
| `service.start/stop` | `ServiceCommandHandler`-এ শুধু `list` ও `status` আছে |
| `logger` retrieval | `NativeLogger`-এ log buffer পড়ার API নেই |

## ৫. আর্কিটেকচারাল সিদ্ধান্ত

`NativeApiIntegration` **নতুন manager তৈরি করে না**। প্রতিটি handler হয়:
- বিদ্যমান singleton থেকে (`SecurityEngine.getCommandHandler()`), অথবা
- বিদ্যমান service ক্লাসের চারপাশে (`new AnalyticsCommandHandler(new NativeAnalyticsService(...))`)

একমাত্র নতুন state হলো `ThemeManager` — কারণ আগে কোনো authoritative instance ছিল না।
