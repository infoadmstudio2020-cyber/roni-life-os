# Native API Dependency Matrix

## ১. মডিউল নির্ভরতা (build.gradle থেকে)

| মডিউল | নির্ভর করে |
|---|---|
| `native-runtime-contracts` | — |
| `native-logger-sdk` | contracts |
| `native-service-sdk` | contracts, logger |
| `native-router-sdk` | service-sdk, contracts, logger |
| `native-web-bridge-sdk` | contracts, router-sdk, service-sdk, logger |
| `native-security-engine` | contracts, service-sdk, logger, **androidx.biometric**, **androidx.fragment** |
| `native-storage-engine` | contracts, service-sdk, **security-engine**, logger |
| `native-theme-engine` | contracts, service-sdk, logger |
| `native-platform-engine` | contracts, service-sdk, logger |
| `native-network-engine` | contracts, service-sdk, logger |
| `native-analytics-engine` | contracts, service-sdk, **storage-engine** |
| `native-dev-tools-sdk` | contracts, service-sdk, **storage-engine** |
| `native-runtime-engine` | contracts, service-sdk, router-sdk, logger |
| `native-runtime-ui` | web-bridge-sdk, contracts, theme-engine, storage-engine, platform-engine, security-engine |
| `native-runtime-sdk` | **সব ১৪টি** + appcompat + material + **fragment** (Phase 12-এ যোগ) |

## ২. Handler → Service নির্ভরতা

| Handler | Constructor | Singleton ব্যবহার |
|---|---|---|
| `ThemeCommandHandler` | `ThemeManager` | — |
| `SecurityCommandHandler` | (no-arg) | `SecurityEngine.getInstance()` |
| `AnalyticsCommandHandler` | `NativeAnalyticsService` | — |
| `DevToolsCommandHandler` | `NativeDevToolsService` | — |
| `NetworkCommandHandler` | `NativeNetworkService` | — |
| `PlatformCommandHandler` | (no-arg) | `PlatformEngine.getInstance()` |
| `LoggerCommandHandler` | (no-arg) | `NativeLogger.getInstance()` |
| `ServiceCommandHandler` | (no-arg) | `ServiceRegistry.getInstance()` |
| `StorageCommandHandler` | (no-arg) | `EncryptedStore` |

## ৩. কেন `NativeApiIntegration` `native-runtime-sdk`-এ

`native-runtime-sdk` একমাত্র মডিউল যা সব engine-এ পৌঁছায়। `native-runtime-ui`-তে রাখলে
analytics/devtools/network/logger/service-এর জন্য পাঁচটি নতুন dependency যোগ করতে হতো,
যা UI স্তরকে ভারী করত এবং স্তর-বিচ্ছেদ ভাঙত।

## ৪. নতুন dependency

| মডিউল | যোগ | কেন |
|---|---|---|
| `native-runtime-sdk` | `androidx.fragment:fragment:1.6.2` | `FragmentActivity` টাইপ `NativeApiIntegration`-এ দরকার |

আর কিছু যোগ করা হয়নি। Retrofit, OkHttp, Kotlin, Compose, Hilt — কিছুই নেই।
