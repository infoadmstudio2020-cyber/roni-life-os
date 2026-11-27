# Build & Verification Report — Runtime Inspector migration

Date: 2026-11-19
Project: RONiLiFEOS (AGP 8.10.1, Gradle 8.11.1, compileSdk 36, minSdk 21, Java 17)

## What changed

The Runtime Inspector moved from `:app` (`com.roni.life.os.app.inspector`) into
`:native-dev-tools-sdk` (`com.roni.library.devtools.inspector`), and now consumes the
ecosystem's existing logging pipeline through `InspectorLogSink` (`ILogSink`) instead of
maintaining a parallel collector.

- 14 app classes → 13 library classes (Controller merged into the `RuntimeInspector` facade;
  `LogSeverity` and `UnifiedLogEntry` replaced by the shared contracts model).
- Layouts, drawable and 18 colours moved into the library's `res/`.
- 3 test classes moved; 1 new test class added (`InspectorLogSinkTest`).
- App keeps a single glue class: `RuntimeInspectorIntegration` (observer wiring only).
- `AppLog` now calls `NativeLogger` only — one pipeline, no duplicate entries.

## Verification matrix

| # | Check | Command / method | Result |
|---|---|---|---|
| 1 | Contracts + logger + inspector core + tests compile | ECJ 3.33, `-17`, JUnit 4.13.2 on classpath | PASS |
| 2 | Full inspector package incl. UI compiles | ECJ against `android.jar` API 36, RecyclerView 1.3.2, Core 1.13.1 | PASS |
| 3 | Host glue compiles | ECJ against real `WebDiagnosticsListener` / `BridgeTraceListener` | PASS |
| 4 | Unit tests | `JUnitCore` on 4 test classes | **77 tests, 0 failures** |
| 5 | Resource XML well-formed | Python `xml.etree` parse of 5 files | PASS |
| 6 | Layout resource references resolve in the library | reference scan vs `inspector_colors.xml` / drawable / ids | PASS |
| 7 | No library → app dependency | `grep -rn "com.roni.life.os.app" native-*/src/main` | 0 hits |
| 8 | Single log model | `find -name LogEntry.java -o -name LogLevel.java -o -name LogSource.java` | 1 each, all in contracts |
| 9 | No stale app inspector references | `grep -rn "app.inspector.<removed classes>"` | 0 hits |
| 10 | App inspector package contents | `find app/src -path "*inspector*"` | only `RuntimeInspectorIntegration.java` |

## Test breakdown

| Test class | Tests |
|---|---|
| `InspectorCoreTest` | buffer, eviction, listeners, concurrency, filter, redaction, export, formatter, level mapping |
| `RuntimeLogCollectorTest` | settings gate, per-source switches, redaction on ingest, session context, producers |
| `InspectorLogSinkTest` | exactly-once delivery, level/tag/source preservation, id assignment, disable/dispose |
| `ui.InspectorSheetStateTest` | collapsed/half/expanded geometry and transitions |
| **Total** | **77 passing** |

## Not verified here

A full `./gradlew assembleDebug` was **not** run: this sandbox has no Android SDK installation
and no Gradle distribution. The compile checks use the real `android.jar` (API 36) and real
AndroidX artifacts, so they catch API/signature errors, but they do not exercise AAPT resource
merging, D8/R8 or packaging.

Run on a machine with the Android SDK:

```bash
./gradlew :native-dev-tools-sdk:testDebugUnitTest
./gradlew :app:assembleDebug
```

## Known limitations

- In-memory buffer only; no persistence across process death.
- Bridge tracing requires the host to attach a `WebBridge`; the current app does not create one.
- `LogEntry.serialVersionUID` is now `2L` because of the added diagnostic fields.
