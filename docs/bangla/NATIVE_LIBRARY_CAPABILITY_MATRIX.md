# Native Library Capability Matrix

| সক্ষমতা | theme | security | analytics | devtools | network | platform | logger | service | storage |
|---|---|---|---|---|---|---|---|---|---|
| JS থেকে কলযোগ্য | ✅ | ✅ | ✅ | ✅ (debug) | ✅ | ✅ | ✅ | ✅ | ✅ |
| State পড়া | ✅ | ✅ | ✅ | ✅ | — | ✅ | ✅ | ✅ | ✅ |
| State লেখা | ✅ | ✅ | ✅ | ✅ | — | ✅ | ✅ | — | ✅ |
| ইভেন্ট পাঠায় | ✅ | ✅ | — | — | — | — | — | — | — |
| Project-scoped | ⚠️ | — | ⚠️ | — | — | — | — | — | ✅ |
| Permission দরকার | — | ✅ | — | — | — | ✅ | — | — | — |
| Activity দরকার | — | ✅ | — | — | — | — | — | — | — |
| Async | — | ✅ | — | — | ✅ | — | — | — | — |
| Debug-only | — | — | — | ✅ | — | — | — | — | — |

## বিস্তারিত

### theme
- `ThemeManager` — `applyTheme`, `setThemeMode`, `getActiveTheme`, listener
- `WebThemeBridge` — CSS variable generation
- Project-specific: `ThemeManager` instance প্রতি project আলাদা রাখা **যায়**, কিন্তু
  `NativeApiIntegration` একটিই instance রাখে। Project-ভিত্তিক theme চাইলে
  `setProjectId()`-এর মতো `themeManager` swap করতে হবে — এখনো করা হয়নি।

### security
- `SecurityEngine` singleton, `KeyStoreManager`, `BiometricAuthService`, `PermissionService`
- Biometric-এর জন্য `FragmentActivity` বাধ্যতামূলক
- Credential কখনো JS-এ যায় না — শুধু status

### analytics
- `NativeAnalyticsService` — EventQueue, SessionTracker, PerformanceTracker
- Project attribution: `NativeAnalyticsEngineConfig`-এ projectId ফিল্ড নেই → **অসম্পূর্ণ**

### devtools
- `NativeDevToolsService` — profiler, HAR export, CDP dispatcher
- `NativeRuntimeUIConfig.isDebugEnabled()` false হলে নিবন্ধিত হয় না

### network
- `HttpClientEngine` — একমাত্র HTTP client (Retrofit/OkHttp নেই)
- `DownloadManager` আলাদা pipeline — duplicate করা হয়নি

### platform
- `PlatformEngine` singleton — device/battery/network/storage/clipboard/toast
- Sensitive identifier (IMEI, Android ID) **প্রকাশ করে না**

### logger
- `NativeLogger` singleton — logcat + file + web console sink
- Log retrieval API নেই

### service
- `ServiceRegistry` — `list`, `status` মাত্র
- Android Service শুরু/বন্ধ করার API নেই

### storage
- `EncryptedStore` + `StorageScope` — project isolation
- `StorageCommandHandler` (engine) নিবন্ধিত নয়
