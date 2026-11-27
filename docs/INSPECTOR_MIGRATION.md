# Runtime Inspector — Migration & Architecture Record

This document records the move of the Runtime Inspector out of the host application and into
the SDK layer, and explains how the result is structured, integrated, verified and extended.

## 1. Why the move

The inspector was implemented inside `:app` (`com.roni.life.os.app.inspector`, 14 classes,
~3.3k lines, plus layouts, colours, a drawable and three test classes). That violated the
project's layering rule in two directions:

- **Downward dependency.** `:app` is L5. A reusable diagnostics console is infrastructure, not a
  host feature. Keeping it in `:app` meant no other host could use it and the app carried the
  whole implementation.
- **Duplicated log model.** The inspector declared `LogSeverity`, `LogSource` and
  `UnifiedLogEntry` next to the ecosystem's existing `LogLevel` / `LogEntry` in
  `:native-runtime-contracts`. Two log models in one ecosystem is the root cause of "which
  logger am I looking at?" confusion.

## 2. Target layer decision

| Candidate | Verdict |
| --- | --- |
| `:native-runtime-ui` (L3) | Rejected. It is a WebView UI library; the inspector also covers native, network, storage and startup events. |
| `:native-runtime-sdk` (L4) | Rejected. The master facade must not own a developer tool; that would make every consumer pull in the inspector. |
| New `:native-inspector-sdk` module | Rejected. It would duplicate the dev-tools module's purpose and add a 17th module for one feature. |
| **`:native-dev-tools-sdk` (L1)** | **Chosen.** It already owns `DevToolsSDK`, `DebugDrawer`, `NetworkInspector`, `FpsMonitor` and `CrashReporter` — the inspector is the missing log console of exactly that family. It sits at L1, so it may depend on `:native-logger-sdk` and `:native-runtime-contracts` without violating the layering rule. |

Package: `com.roni.library.devtools.inspector` (UI in `.ui`).

## 3. Before / after

| Before (`:app`) | After (`:native-dev-tools-sdk`) |
| --- | --- |
| `app/.../inspector/LogSeverity.java` | **Deleted** — replaced by `contracts.logger.LogLevel` (+ `FATAL`) |
| `app/.../inspector/LogSource.java` | Moved to `contracts.logger.LogSource` |
| `app/.../inspector/UnifiedLogEntry.java` | **Deleted** — replaced by the extended `contracts.logger.LogEntry` |
| `app/.../inspector/InspectorConfig.java` | `devtools.inspector.InspectorConfig` |
| `app/.../inspector/InspectorSettings.java` | `devtools.inspector.InspectorSettings` |
| `app/.../inspector/RuntimeLogRepository.java` | `devtools.inspector.RuntimeLogRepository` (stores `LogEntry`) |
| `app/.../inspector/RuntimeLogCollector.java` | `devtools.inspector.RuntimeLogCollector` |
| `app/.../inspector/LogFilter.java` | `devtools.inspector.LogFilter` |
| `app/.../inspector/LogExporter.java` | `devtools.inspector.LogExporter` |
| `app/.../inspector/LogRedactor.java` | `devtools.inspector.LogRedactor` |
| `app/.../inspector/RuntimeInspectorController.java` | **Merged into** `devtools.inspector.RuntimeInspector` (one public facade) |
| — | **New** `devtools.inspector.InspectorLogSink` (`ILogSink`) |
| — | **New** `devtools.inspector.LogEntryFormatter` |
| `app/.../inspector/ui/*` | `devtools.inspector.ui.*` |
| `app/src/main/res/layout/view_runtime_inspector.xml` | `native-dev-tools-sdk/src/main/res/layout/` |
| `app/src/main/res/layout/item_log_entry.xml` | `native-dev-tools-sdk/src/main/res/layout/` |
| `app/src/main/res/drawable/ic_inspector_empty.xml` | `native-dev-tools-sdk/src/main/res/drawable/` |
| 18 `inspector_*` colours in `app/.../values/colors.xml` | `native-dev-tools-sdk/src/main/res/values/inspector_colors.xml` |
| `app/src/test/.../inspector/*` | `native-dev-tools-sdk/src/test/.../inspector/*` |
| — | **New** `app/.../inspector/RuntimeInspectorIntegration.java` (host glue only) |

## 4. Dependency graph

```
:app
 └── :native-dev-tools-sdk
      ├── api :native-runtime-contracts      (LogEntry / LogLevel / LogSource)
      ├── implementation :native-logger-sdk  (NativeLogger, ILogSink)
      ├── implementation :native-service-sdk
      ├── implementation :native-storage-engine
      ├── implementation androidx.recyclerview:recyclerview:1.3.2
      └── implementation androidx.annotation:annotation:1.7.0

:native-logger-sdk
 └── :native-runtime-contracts
```

No cycles. No library module imports `com.roni.life.os.app.*` (verified by grep).

## 5. Shared log model changes (L0)

All changes are **additive**; existing producers and sinks compile unchanged.

`LogLevel`
- Added `FATAL(7, "F")`; `NONE` moved from 7 to 8.
- Added `fromAndroidPriority(int)` so the priority mapping has one definition.

`LogSource` (new in contracts)
- `NATIVE, WEB, BRIDGE, NETWORK, STORAGE, SECURITY, LIFECYCLE, PROJECT, PERFORMANCE`
- `getLabel()` for the UI, `parse(String)` for persisted values.

`LogEntry`
- New optional fields: `id`, `source`, `module`, `projectId`, `requestId`, `direction`,
  `status`, `durationMs` (default `NO_DURATION = -1`).
- New `Builder` (including a copy constructor) and `withId(long)`.
- Existing constructors `LogEntry(long, LogLevel, String, String, String, Throwable, String)` and
  `LogEntry(LogLevel, String, String)` are unchanged.
- `serialVersionUID` bumped to `2L` (new fields).

`LogcatSink`
- `FATAL` maps to `Log.e` (Android has no separate fatal priority).

## 6. Public API

```java
RuntimeInspector inspector = RuntimeInspector.get();

inspector.init(context);                       // idempotent
inspector.beginStartupTrace();                 // returns the correlation id
inspector.startup("CHECKPOINT", "detail");
inspector.onProjectOpened(projectId, name);
inspector.onProjectClosed(projectId);

RuntimeInspectorSheet sheet = inspector.install(activity);  // idempotent per Activity
sheet.onResume(); sheet.onPause(); sheet.release();

inspector.getSettings();      // InspectorSettings (nullable before init)
inspector.getCollector();     // RuntimeLogCollector
inspector.getRepository();    // RuntimeLogRepository
inspector.ensureSinkAttached();
inspector.isSinkAttached();
inspector.release();
```

`DevToolsSDK.getRuntimeInspector()` returns the same instance. It is intentionally **not**
guarded by `checkInitialized()`: a diagnostic tool must stay reachable when the rest of the
dev-tools SDK failed.

## 7. Host integration (the only app-side code)

`app/src/main/java/com/roni/life/os/app/inspector/RuntimeInspectorIntegration.java`

- Implements `WebDiagnosticsListener` and `BridgeTraceListener`.
- `attach(WebViewHost)` / `attach(WebBridge)` register the observers.
- Forwards each event to `RuntimeLogCollector`; no logic, no state, no formatting.
- Preserves the previous behaviour: `[JS-ERROR]` / `[JS-REJECTION]` console messages are forced
  to `ERROR`, `PAGE_FINISHED` records a `WEB_PAGE_READY` startup checkpoint, main-frame resource
  errors are `ERROR` and sub-resource errors are `WARN`.

`AppLog` now calls `NativeLogger` only. It no longer calls `ApplicationCore.log(...)` or the
inspector directly, which removes the previous double logcat output and guarantees exactly one
buffered entry per call. Its static initialiser raises the logger minimum level to `DEBUG` if it
is higher, so `AppLog.d(...)` is not filtered out before reaching the sinks; an explicit
`NativeLogger.init(...)` later still wins.

## 8. Backward compatibility

| Old call site | Status |
| --- | --- |
| `RuntimeInspectorController.get().init(ctx)` | → `RuntimeInspector.get().init(ctx)` |
| `...beginStartupTrace()` / `startup(...)` / `onProjectOpened(...)` / `onProjectClosed(...)` | Same names on `RuntimeInspector` |
| `...attach(WebViewHost)` | → `RuntimeInspectorIntegration.get().attach(host)` |
| `RuntimeInspectorSheet.install(activity)` | Same signature, new package |
| `sheet.onResume() / onPause() / release() / handleActivityResult(...)` | Unchanged |
| `AppLog.d/i/w/e(...)` (161 call sites) | Unchanged signature, new implementation |
| `LogEntry` producers / `ILogSink` implementations | Unchanged (additive model) |

The app was updated in place; no compatibility shim is needed because the app is the only
consumer in this repository.

## 9. Build configuration

`native-dev-tools-sdk/build.gradle`

```gradle
api project(':native-runtime-contracts')
implementation project(':native-service-sdk')
implementation project(':native-storage-engine')
implementation project(':native-logger-sdk')
implementation 'androidx.annotation:annotation:1.7.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
testImplementation 'junit:junit:4.13.2'
```

AGP `8.10.1`, Gradle `8.11.1`, `compileSdk 36`, `minSdk 21`, Java 17 — unchanged.

## 10. Verification performed

| Check | Result |
| --- | --- |
| Compile contracts + logger + inspector core + tests (ECJ 3.33, Java 17) | Pass |
| Compile the full inspector package incl. UI against `android.jar` API 36 + RecyclerView 1.3.2 + Core 1.13.1 | Pass |
| Compile `RuntimeInspectorIntegration` + `AppLog` against the real listener interfaces | Pass |
| Run `InspectorCoreTest`, `RuntimeLogCollectorTest`, `InspectorLogSinkTest`, `InspectorSheetStateTest` | **77 tests, 0 failures** |
| XML parse of all moved resources + `app/values/colors.xml` | Pass |
| Resource references in the moved layouts resolve inside the library | Pass |
| No library module imports `com.roni.life.os.app.*` | Pass |
| Single definition of `LogEntry` / `LogLevel` / `LogSource` | Pass |
| No leftover `app.inspector.*` references | Pass |

Not performed in this environment: a full `./gradlew assembleDebug`. The sandbox has no Android
SDK installation and no Gradle distribution, so the Android build itself must be run on a
machine with the SDK. The compile checks above use the real `android.jar` (API 36) and the real
AndroidX artifacts, so they catch API and signature errors, but they do not run AAPT, D8/R8 or
resource merging.

## 11. How to build and test

```bash
# unit tests for the inspector
./gradlew :native-dev-tools-sdk:testDebugUnitTest

# full app
./gradlew :app:assembleDebug
```

## 12. Known limitations

- In-memory buffer only; no persistence across process death (`CrashReporter` remains the crash
  path).
- Bridge tracing requires the host to attach a `WebBridge`; the current app does not create one.
- The sheet is an overlay in the Activity content root, not a `BottomSheetDialog`, so it cannot
  steal focus from the WebView.
- `LogEntry` is now a wider model. Producers that serialise it should be aware of
  `serialVersionUID = 2L`.
