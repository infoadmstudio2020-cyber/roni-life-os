# NATIVE_API_INTEGRATION_MATRIX — Phase 12

> স্বয়ংক্রিয়ভাবে Java সোর্স থেকে উৎপন্ন। প্রতিটি action `case` বা `equalsIgnoreCase` থেকে নেওয়া।

## ১. সারসংক্ষেপ

| Namespace | Handler | Dispatch labels | Route | Registration |
|---|---|---|---|---|
| `theme` | `ThemeCommandHandler` | 6 | `theme` | NativeApiIntegration |
| `security` | `SecurityCommandHandler` | 11 | `security` | NativeApiIntegration |
| `analytics` | `AnalyticsCommandHandler` | 11 | `analytics` | NativeApiIntegration |
| `devtools` | `DevToolsCommandHandler` | 9 | `devtools` | NativeApiIntegration |
| `network` | `NetworkCommandHandler` | 1 | `network` | NativeApiIntegration |
| `platform` | `PlatformCommandHandler` | 15 | `platform` | NativeApiIntegration |
| `logger` | `LoggerCommandHandler` | 6 | `logger` | NativeApiIntegration |
| `service` | `ServiceCommandHandler` | 2 | `service` | NativeApiIntegration |
| `storage` | `StorageCommandHandler` | 13 | `storage` | NativeRuntimeUI (আগে থেকেই) |
| **মোট** | | **74** | | |

আগের ১৫২টি action অপরিবর্তিত। নতুন **৬১**টি (storage-এর ১৩টি আগে থেকেই ছিল)।

> কিছু label alias: `platform`-এ `device`/`battery`/`network`/`storage`/`vibrate`,
> `logger`-এ `setMinimumLevel`/`getMinimumLevel`, `storage`-এ `getAll`/`has`।
> alias বাদ দিলে canonical action সংখ্যা কম।

## ২. পূর্ণ action তালিকা

### `theme.*` — 6

| # | Action |
|---|---|
| 1 | `theme.applyTheme` |
| 2 | `theme.getCssVariables` |
| 3 | `theme.getTheme` |
| 4 | `theme.isSystemDark` |
| 5 | `theme.setSystemDark` |
| 6 | `theme.setThemeMode` |

### `security.*` — 11

| # | Action |
|---|---|
| 1 | `security.biometric.authenticate` |
| 2 | `security.biometric.capability` |
| 3 | `security.diagnostic` |
| 4 | `security.hash.sha256` |
| 5 | `security.keystore.decrypt` |
| 6 | `security.keystore.delete` |
| 7 | `security.keystore.encrypt` |
| 8 | `security.keystore.generate` |
| 9 | `security.keystore.hasKey` |
| 10 | `security.keystore.list` |
| 11 | `security.permission.check` |

### `analytics.*` — 11

| # | Action |
|---|---|
| 1 | `analytics.clear` |
| 2 | `analytics.endScreenView` |
| 3 | `analytics.endSession` |
| 4 | `analytics.flush` |
| 5 | `analytics.getStats` |
| 6 | `analytics.recordMetric` |
| 7 | `analytics.startScreenView` |
| 8 | `analytics.startSession` |
| 9 | `analytics.startTrace` |
| 10 | `analytics.stopTrace` |
| 11 | `analytics.trackEvent` |

### `devtools.*` — 9

| # | Action |
|---|---|
| 1 | `devtools.clearTraffic` |
| 2 | `devtools.dispatchCdp` |
| 3 | `devtools.exportHar` |
| 4 | `devtools.getBridgeMetrics` |
| 5 | `devtools.getHealth` |
| 6 | `devtools.getManifest` |
| 7 | `devtools.getMetrics` |
| 8 | `devtools.startProfiling` |
| 9 | `devtools.stopProfiling` |

### `network.*` — 1

| # | Action |
|---|---|
| 1 | `network.request` |

### `platform.*` — 15

| # | Action |
|---|---|
| 1 | `platform.battery` |
| 2 | `platform.battery.status` |
| 3 | `platform.clipboard.get` |
| 4 | `platform.clipboard.set` |
| 5 | `platform.device` |
| 6 | `platform.device.info` |
| 7 | `platform.diagnostic` |
| 8 | `platform.health` |
| 9 | `platform.network` |
| 10 | `platform.network.status` |
| 11 | `platform.storage` |
| 12 | `platform.storage.stats` |
| 13 | `platform.toast.show` |
| 14 | `platform.toast.vibrate` |
| 15 | `platform.vibrate` |

### `logger.*` — 6

| # | Action |
|---|---|
| 1 | `logger.flush` |
| 2 | `logger.getLevel` |
| 3 | `logger.getMinimumLevel` |
| 4 | `logger.log` |
| 5 | `logger.setLevel` |
| 6 | `logger.setMinimumLevel` |

### `service.*` — 2

| # | Action |
|---|---|
| 1 | `service.list` |
| 2 | `service.status` |

### `storage.*` — 13

| # | Action |
|---|---|
| 1 | `storage.clear` |
| 2 | `storage.db.count` |
| 3 | `storage.db.findById` |
| 4 | `storage.db.insert` |
| 5 | `storage.get` |
| 6 | `storage.getAll` |
| 7 | `storage.has` |
| 8 | `storage.keys` |
| 9 | `storage.pref.get` |
| 10 | `storage.pref.remove` |
| 11 | `storage.pref.set` |
| 12 | `storage.remove` |
| 13 | `storage.set` |

## ৩. রুট নিবন্ধন

`NativeApiIntegration.install()` প্রতিটি handler-এ `router.registerHandler(handler)` ডাকে,
যা `handler.getPath()` ব্যবহার করে। `PatternRouteMatcher.normalizeUri()` একটি leading slash
সরায়, তাই `/security` ও `security` একই route।

| Handler | getPath() | কার্যকর route |
|---|---|---|
| `ThemeCommandHandler` | `"theme"` | `theme` |
| `SecurityCommandHandler` | `ROUTE_PREFIX` | `security` |
| `AnalyticsCommandHandler` | `ROUTE_PREFIX` | `analytics` |
| `DevToolsCommandHandler` | `"devtools"` | `devtools` |
| `NetworkCommandHandler` | `"network"` | `network` |
| `PlatformCommandHandler` | `ROUTE_PREFIX` | `platform` |
| `LoggerCommandHandler` | `PATH` | `logger` |
| `ServiceCommandHandler` | `ROUTE_PREFIX` | `service` |
| `StorageCommandHandler` | `"storage"` | `storage` |

## ৪. সংঘর্ষ যাচাই

| সম্ভাব্য সংঘর্ষ | ফল |
|---|---|
| `storage` (runtime-ui) বনাম `storage` (storage-engine) | storage-engine নিবন্ধিত **নয়** — runtime-ui-এর scope-aware handler জেতে |
| `theme` (theme) বনাম `theme` (themeengine) | `themeengine` সংস্করণ `@Deprecated`, নিবন্ধিত নয় |
| `theme` বনাম `ui.getTheme` | দুটোই আছে; `theme.*` এখন authoritative |
| `platform.toast.show` বনাম `ui.showToast` | আলাদা route, সংঘর্ষ নেই |