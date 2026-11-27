# NativeRuntimeSDK (Layer 4 Master Orchestration SDK)

[![Release](https://img.shields.io/badge/Release-v1.0.1-blue.svg)](https://github.com/infoadmstudio2020-cyber/native-runtime-sdk/releases)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![License](https://img.shields.io/badge/License-Apache-2.0-blue.svg)](LICENSE)
[![Architecture](https://img.shields.io/badge/Architecture-NativeRuntime%20L4%20Master-orange.svg)](#architecture)

Master Turnkey Facade, Coordinated 10-Step Bootstrapper, and Host Activity Orchestrator for the entire **NativeRuntime Android SDK Ecosystem**.

---

## Architecture Overview

`NativeRuntimeSDK` sits at **Layer 4: Master Orchestration** within the 6-layer NativeRuntime architecture. It encapsulates and orchestrates all lower subsystems into a single turnkey dependency.

```
+---------------------------------------------------------------------------------------------------+
| Layer 5: Host Application Layer (Your Android Application)                                         |
+---------------------------------------------------------------------------------------------------+
                                                  | NativeRuntime.init(context, config)
+---------------------------------------------------------------------------------------------------+
| Layer 4: Master Orchestration (NativeRuntimeSDK)                                                  |
|  - Master Facade: NativeRuntime                                                                   |
|  - Turnkey 10-Step Bootstrapper: NativeRuntimeBootstrap                                           |
|  - Lifecycle Observer & Session Tracker: RuntimeSession & NativeRuntimeActivity                   |
+---------------------------------------------------------------------------------------------------+
                                                  | Coordinates
+---------------------------------------------------------------------------------------------------+
| Layer 3: Transport Bridge & UI (native-web-bridge-sdk, native-runtime-engine, native-runtime-ui)  |
+---------------------------------------------------------------------------------------------------+
                                                  | Routes
+---------------------------------------------------------------------------------------------------+
| Layer 2: Domain Engines (Storage, Security, Network, Analytics, Theme, Platform, DevTools)        |
+---------------------------------------------------------------------------------------------------+
                                                  | Manages
+---------------------------------------------------------------------------------------------------+
| Layer 1: Core Infrastructure (NativeServiceSDK, NativeLoggerSDK, NativeRouterSDK)                 |
+---------------------------------------------------------------------------------------------------+
                                                  | Inherits
+---------------------------------------------------------------------------------------------------+
| Layer 0: Universal Foundation Contracts (NativeRuntimeContracts:v1.0.2)                           |
+---------------------------------------------------------------------------------------------------+
```

---

## 10-Step Coordinated Startup Sequence

When `NativeRuntime.init(context, config)` is invoked, `NativeRuntimeBootstrap` executes the following synchronized sequence:

1. **L0 Contracts & Foundation**: Validates ABI contracts, runtime bounds, and application identity.
2. **L1 Logging Subsystem (`NativeLoggerSDK`)**: Initializes structured logging and log levels.
3. **L1 Service Directory (`NativeServiceSDK`)**: Mounts the inter-service registry.
4. **L2 Security Subsystem (`native-security-engine`)**: Initializes hardware KeyStore and tamper detection.
5. **L2 Storage Subsystem (`native-storage-engine`)**: Mounts AES encrypted preferences and document stores.
6. **L2 Platform Subsystem (`native-platform-engine`)**: Profiles device capabilities, memory, and sensors.
7. **L2 Network Subsystem (`native-network-engine`)**: Initializes HTTP connection pool, WebSocket, and monitor.
8. **L2 Analytics Subsystem (`native-analytics-engine`)**: Activates telemetry dispatcher and event queue.
9. **L2 Theme & DevTools Subsystem (`native-theme-engine`, `native-devtools-sdk`)**: Attaches theme registry and debug bus.
10. **L3/L4 Bridge & Runtime (`native-web-bridge-sdk`, `native-runtime-engine`)**: Mounts RPC dispatchers and engine state machine.

---

## Consumer Integration Guide

### 1. Configure Maven Repository in `settings.gradle`
```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 2. Add Dependency in `app/build.gradle`
```groovy
dependencies {
    // 1. PUBLIC ABI APIs (Exported transitively to host apps)
    api 'com.github.infoadmstudio2020-cyber:NativeRuntimeContracts:v1.0.2'
    api 'com.github.infoadmstudio2020-cyber:native-web-bridge-sdk:v1.0.1'
    api 'com.github.infoadmstudio2020-cyber:native-runtime-engine:v1.0.0'
    api 'com.github.infoadmstudio2020-cyber:native-runtime-ui:v1.0.0'

    // 2. ENCAPSULATED IMPLEMENTATIONS (Engineered inside NativeRuntimeSDK)
    implementation 'com.github.infoadmstudio2020-cyber:native-runtime-sdk:1.0.1'
}
```

### 3. ProGuard / R8 Rules (`consumer-rules.pro`)
Automatically included in the library AAR:
```proguard
-keep public class com.roni.library.sdk.** { public *; }
-keep interface com.roni.library.sdk.** { *; }
-keep enum com.roni.library.sdk.** { *; }
-keep class com.roni.library.sdk.bootstrap.** { *; }
-keep class com.roni.library.sdk.activity.** { *; }
-keep class com.roni.library.sdk.NativeRuntimeResult { *; }
-keep class com.roni.library.sdk.RuntimeConfiguration { *; }
-keep class com.roni.library.sdk.RuntimeSession { *; }
```

---

## Code Examples

### One-Line Bootstrap
```java
RuntimeConfiguration config = RuntimeConfiguration.builder()
        .appId("com.mycompany.app")
        .environment("PRODUCTION")
        .debugEnabled(BuildConfig.DEBUG)
        .storageEncryptionEnabled(true)
        .networkTimeoutMs(15000)
        .telemetryEnabled(true)
        .build();

NativeRuntime.getInstance().init(context, config);
```

### Fluent Builder
```java
NativeRuntime runtime = RuntimeBuilder.with(context)
        .appId("com.mycompany.app")
        .debugEnabled(true)
        .initialize();
```

### Subsystem Dispatch & Telemetry
```java
// Dispatch RPC Request through Master Engine
NativeApiRequest request = new NativeApiRequest("req-01", "network", "ping", null, System.currentTimeMillis());
NativeApiResponse response = NativeRuntime.getInstance().executeApiRequest(request);

// Ingest Telemetry
Map<String, Object> params = new HashMap<>();
params.put("button_id", "submit_order");
NativeRuntime.getInstance().trackEvent("user_click", params);
```

---

## Interactive Companion Demo App

The companion demonstration app in `:app` provides:
- **Master Orchestration Dashboard:** Live engine state, active session ID, duration, and overall bootstrap health.
- **10-Step Boot Visualizer:** Live interactive visual list of all 10 boot sequence stages with millisecond duration and details.
- **Command & Telemetry Center:** Live buttons to dispatch RPC requests, record telemetry, and pause/resume sessions.
- **Integration Test Center:** Automated 10-step test runner providing instant PASS/FAIL verification.
- **Runtime Console:** Real-time monospace event log with clear log controls.

---

## License

```
Copyright 2026 infoadmstudio2020-cyber

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

## Consumer Application (`:app`)

The repository ships a complete Android consumer application that exercises the whole
ecosystem through its real public APIs.

| Item | Value |
| :--- | :--- |
| Module | `:app` |
| Package | `com.roni.life.os.app` |
| Language | Java 17 + XML layouts (no Kotlin, no Compose) |
| Activities | `SplashActivity` (launcher), `BootstrapActivity`, `ProjectActivity` |

**Documentation:** [`docs/consumer-app/README.md`](docs/consumer-app/README.md)

- [Architecture](docs/consumer-app/ARCHITECTURE.md)
- [Build instructions](docs/consumer-app/BUILD_INSTRUCTIONS.md)
- [Known limitations](docs/consumer-app/KNOWN_LIMITATIONS.md)
- [Module audit report](docs/consumer-app/AUDIT_REPORT.md)

---

## Web App Development

Web apps run inside `WebViewHost` and talk to native code through a single injected object:

```js
await window.NativeRuntimeBridgeClient.call('ui.showToast', { message: 'হ্যালো' });
```

**290 action identifiers across 19 routes** — UI, WebView, device, storage, theme, security, analytics,
network, platform, notifications, camera, file manager, automation, logger, services.

**Documentation:** [`docs/web-sdk/README.md`](docs/web-sdk/README.md)

| Document | Contents |
| :--- | :--- |
| [Web App Developer Guide](docs/web-sdk/WEB_APP_DEVELOPER_GUIDE.md) | Build, package, debug, ship |
| [JavaScript Bridge](docs/web-sdk/JAVASCRIPT_BRIDGE.md) | Protocol, gates, timeouts, error codes |
| [Native API Reference](docs/web-sdk/NATIVE_API_REFERENCE.md) | Every action, parameter and return value |
| [Events and Callbacks](docs/web-sdk/EVENTS_AND_CALLBACKS.md) | All native → web events |
| [Storage API](docs/web-sdk/STORAGE_API.md) | AES-GCM store, project scoping |
| [Notification API](docs/web-sdk/NOTIFICATION_API.md) | Styles, scheduling, channels |
| [Background Services](docs/web-sdk/BACKGROUND_SERVICES_API.md) | Tasks, jobs, alarms, transfers |
| [Security and Permissions](docs/web-sdk/SECURITY_AND_PERMISSIONS.md) | Origin model, Keystore, biometrics |
| [API Compatibility](docs/web-sdk/API_COMPATIBILITY.md) | Versioning and parameter traps |
| [Documentation Audit](docs/web-sdk/DOCUMENTATION_AUDIT_REPORT.md) | What the previous docs got wrong |

**Working example:** [`examples/webapp-developer-guide/`](examples/webapp-developer-guide/) — a
runnable app that exercises every documented subsystem.

> ⚠️ `docs/web-developer-guide/`, `docs/API.md`, `BRIDGE_API.md` and `docs/bangla/` are
> **superseded**. They describe a `NativeRuntime.ui.*` facade that the SDK does not inject.

**Static validation** (no Android SDK required):

```bash
python3 tools/validate_project.py
```
