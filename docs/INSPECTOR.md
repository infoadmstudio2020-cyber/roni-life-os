# RONiLiFEOS — Runtime Inspector

Unified Native + Web logging console for the RONiLiFEOS ecosystem. It collects native, WebView,
bridge, network, lifecycle and startup events into one bounded buffer and renders them in an
expandable bottom sheet that can be opened over a running project.

**Owner module:** `:native-dev-tools-sdk` (L1 — infrastructure / developer tools)
**Package:** `com.roni.library.devtools.inspector`
**Public entry point:** `RuntimeInspector.get()`

The inspector is a **library component**, not an app feature. The host application only wires
the two observer interfaces the runtime libraries already expose; it contains no inspector
logic, no buffer and no UI.

## Architecture

```
                       ┌──────────────────────────────┐
  AppLog ─────────────►│  NativeLogger (native-logger)│
  RuntimeBridge.log ──►│  fan-out to every ILogSink   │
                       └──────────────┬───────────────┘
                                      │ InspectorLogSink (ILogSink)
                                      ▼
 WebViewHost ──► WebDiagnosticsListener ─┐
 WebBridge  ──► BridgeTraceListener ─────┤   RuntimeLogCollector
 StartupManager ──► startup(checkpoint) ─┤   (single ingest gate:
 NetworkInspector ──► network(...) ──────┘    level, source, redaction, id)
                                                      │
                                                      ▼
                                          RuntimeLogRepository
                                          (bounded ring buffer)
                                                      │
                                                      ▼
                                          RuntimeInspectorSheet
                                          (3 states, 7 tabs, filter,
                                           copy, export, settings)
```

### One logging pipeline

Application logs reach the inspector **only** through `InspectorLogSink`, a regular `ILogSink`
registered on the existing `NativeLogger`. The inspector never re-logs what it receives, so one
`AppLog.i(...)` call produces exactly one buffered entry — no duplicate pipeline, no double
entries.

Runtime events (WebView console, bridge traffic, network, startup checkpoints) are **event
sources**, not loggers: they build a `LogEntry` and hand it to the same ingest gate. They do not
go through `NativeLogger` because the logger's minimum level and runtime-state gate would drop
diagnostic events exactly when they matter most (a broken runtime, a DEBUG-level bridge trace).

### Layering

| Layer | Module | Inspector involvement |
| --- | --- | --- |
| L0 Contracts | `:native-runtime-contracts` | Owns `LogEntry`, `LogLevel`, `LogSource` — the shared log model |
| L1 Infrastructure | `:native-logger-sdk` | Owns `NativeLogger` / `ILogSink`; the inspector registers a sink |
| L1 Infrastructure | `:native-dev-tools-sdk` | **Owns the inspector**: buffer, gate, filter, export, UI |
| L3 Bridge/UI | `:native-runtime-ui`, `:native-web-bridge-sdk` | Expose `WebDiagnosticsListener` / `BridgeTraceListener`; know nothing about the inspector |
| L5 Host app | `:app` | Composition root: `RuntimeInspectorIntegration` wires the observers |

No library module imports `com.roni.life.os.app.*`. The dependency direction is strictly
downward: `app → dev-tools → logger → contracts`.

## Components

| Class | Responsibility |
| --- | --- |
| `RuntimeInspector` | Public facade: init, session context, startup trace, sink attachment, `install(Activity)`. |
| `InspectorConfig` | The settings surface the collector needs (keeps the collector JVM-testable). |
| `InspectorSettings` | `SharedPreferences`-backed implementation of `InspectorConfig`. |
| `RuntimeLogCollector` | Single ingest gate; applies level, per-source switches, redaction and ids. |
| `InspectorLogSink` | `ILogSink` bridge — the only path for application logs. |
| `RuntimeLogRepository` | Thread-safe bounded ring buffer with listeners and drop counters. |
| `LogFilter` | AND-combined level / source / project / module / text / tag / request id / errors-only / failed-only / time range. |
| `LogExporter` | TXT, JSON and CSV with a metadata header; redacts again on the way out. |
| `LogRedactor` | Masks credentials, bearer tokens and sensitive query parameters. |
| `LogEntryFormatter` | Renders a shared `LogEntry` as text / JSON / CSV row for the UI and the exporter. |
| `ui.RuntimeInspectorSheet` | The bottom sheet: 3 states, 7 tabs, search, copy, export, settings. |
| `ui.LogEntryAdapter` | RecyclerView adapter with id-based multi-selection. |
| `ui.InspectorSheetState` | Pure geometry/state math for the three sheet states. |

The shared log model lives in `:native-runtime-contracts`:

| Type | Notes |
| --- | --- |
| `LogEntry` | Immutable, `Serializable`, with a `Builder`. Carries the diagnostic dimensions (`source`, `module`, `projectId`, `requestId`, `direction`, `status`, `durationMs`) as optional fields with neutral defaults. |
| `LogLevel` | `VERBOSE … FATAL, NONE`; `fromAndroidPriority(int)` maps `android.util.Log` priorities. |
| `LogSource` | `NATIVE, WEB, BRIDGE, NETWORK, STORAGE, SECURITY, LIFECYCLE, PROJECT, PERFORMANCE`. |

## Collection points

- **Native** — `AppLog` calls `NativeLogger`; the logger fans out to `InspectorLogSink`. The
  whole application is covered without touching individual call sites.
- **Web** — `WebViewHost.HostWebChromeClient.onConsoleMessage` reports console output;
  `HostWebViewClient` reports page start/finish, resource errors, HTTP errors and renderer
  crashes. Uncaught errors and unhandled rejections are captured by a small script injected on
  page finish that re-reports them through `console.error` with a `[JS-ERROR]` /
  `[JS-REJECTION]` prefix. **No `addJavascriptInterface` is added**, so an untrusted page gains
  no new native capability and the origin allowlist is unchanged.
- **Bridge** — `WebBridge` reports request, response, rejection and native→web events. The
  callbacks are payload-free: a token sent through the bridge cannot reach the buffer.
- **Network** — `NetworkInspector` reports method, redacted URL, status, duration and failure.
- **Startup** — `RuntimeInspector.startup(checkpoint, detail)` records the launch sequence under
  one correlation id, so the whole startup can be reconstructed in the inspector.

## Host integration

```java
// Application / bootstrap
RuntimeInspector.get().init(context);
RuntimeInspector.get().beginStartupTrace();
RuntimeInspector.get().startup("APPLICATION_CREATE", "pid=" + Process.myPid());

// Project runtime
RuntimeInspector.get().onProjectOpened(project.getId(), project.getName());
RuntimeInspectorIntegration.get().attach(webViewHost);   // WebView console + page events
RuntimeInspectorIntegration.get().attach(webBridge);     // bridge trace (when available)

// Activity
RuntimeInspectorSheet sheet = RuntimeInspector.get().install(this);
sheet.onResume();   // onResume
sheet.onPause();    // onPause
sheet.release();    // onDestroy
```

`install()` is idempotent per Activity and calls `init()` itself, so the sheet works even if the
host forgot to initialise the inspector.

## Performance and safety

- The buffer is a bounded ring (`DEFAULT_CAPACITY = 1000`, hard ceiling `20000`). Evictions are
  counted, not silently dropped.
- Listeners are notified **outside** the lock; a slow UI callback cannot block a WebView console
  message or a bridge call.
- Every observer callback only appends to the buffer — no I/O, no formatting, no UI work on the
  WebView thread.
- Every public entry point swallows its own failures: a diagnostics bug can never break the
  runtime, the logger or the host app.
- Redaction runs on ingest **and** again on export. Export is the one path where data leaves the
  device, so it does not rely on a single upstream guarantee.

## Export

TXT, JSON and CSV, each with a metadata header (app version, SDK version, Android version,
device model, project id, export time, log count). Files are written through the Storage Access
Framework picker; no storage permission is required.

## Tests

`native-dev-tools-sdk/src/test/java/com/roni/library/devtools/inspector/`

| Test | Covers |
| --- | --- |
| `InspectorCoreTest` | Buffer, eviction, listeners, concurrency, filter, redaction, export, formatter, level mapping. |
| `RuntimeLogCollectorTest` | Settings gate, per-source switches, redaction on ingest, session context, bridge/network/web producers. |
| `InspectorLogSinkTest` | Exactly-once delivery from the logger, level/tag/source preservation, id assignment, disable/dispose. |
| `ui.InspectorSheetStateTest` | Collapsed/half/expanded geometry and state transitions. |

Run them with:

```bash
./gradlew :native-dev-tools-sdk:testDebugUnitTest
```

## Known limitations

- The inspector is a **debug/diagnostic** component. It is not a crash reporter and does not
  persist logs across process death; `CrashReporter` remains the crash path.
- The buffer is in memory only. Export is the only way to keep a log set.
- Bridge tracing is only active when the host attaches a `WebBridge`; the current app does not
  create one, so bridge entries appear only in hosts that do.
- The sheet is a plain overlay, not a `BottomSheetDialog`: it deliberately avoids a second
  window so it cannot steal focus from the WebView.
