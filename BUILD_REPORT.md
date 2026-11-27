# Build & Verification Report — Native Background & Automation Platform

Date: 2026-11-20

## Scope delivered

1. Download / Upload Engine
2. Background Task Engine
3. Job Scheduler / WorkManager integration
4. Alarm Engine
5. Event Engine
6. Service Engine

Speech Engine was excluded as instructed; no speech classes or dependencies were added.

## Compilation

| Target | Command | Result |
|--------|---------|--------|
| Contracts + logger + service + storage + platform engine | ECJ 3.33, Java 17, Android API stubs | **0 errors** |
| Automation unit tests | ECJ 3.33, Java 17 | **0 errors** |
| Consumer App automation screens | ECJ 3.33 with app stubs | **0 errors in `app/.../automation/*`** |

The full `app` module cannot be compiled in this sandbox: it needs the Android Gradle plugin and
the real AndroidX/Material artifacts (RecyclerView, WebView, Fragment, activity-result, FAB).
Those classes are absent from the offline stub set, so the app module reports pre-existing
unresolved imports unrelated to automation. The two new activities themselves compile cleanly.

## Tests

`org.junit.runner.JUnitCore` over the automation suite:

```
OK (40 tests)
```

| Test class | Coverage |
|------------|----------|
| `AutomationStoreTest` | per-project persistence, project registry, event append/replay, monotonic sequence |
| `EventEngineTest` | sequence assignment, replay cursor, RxJava observation, payload, clear |
| `BackgroundTaskEngineTest` | completion, retry, failure, cancellation, isolation, unknown type |
| `JobSchedulerTest` | enqueue/persist, cancel, validation, unavailable backend |
| `AlarmEngineTest` | schedule, cancel, past-trigger rejection, repeating reschedule, boot restore |
| `TransferEngineTest` | 128 KB download integrity, `.part` cleanup, cancellation, scheme rejection, isolation |
| `AutomationCommandHandlerTest` | routing, project scoping, payload `projectId` ignored, errors, alarms, events |

## XML validation

5/5 files parse: `AndroidManifest.xml`, `activity_automation.xml`,
`activity_automation_events.xml`, `menu_bootstrap.xml`, `menu_project.xml`.

## Dependencies added

`native-platform-engine/build.gradle`:

```gradle
implementation 'androidx.work:work-runtime:2.9.0'
implementation 'io.reactivex.rxjava3:rxjava:3.1.8'
implementation 'com.google.code.gson:gson:2.10.1'
```

RxJava 3 pulls `org.reactivestreams:reactive-streams:1.0.4` transitively. All three are
minSdk 21 compatible and match the existing AGP/Gradle configuration. No Kotlin, Compose,
Retrofit, OkHttp, Coroutines, Hilt or Dagger was introduced.

## Manifest

- `AutomationActivity`, `AutomationEventsActivity`
- `AutomationAlarmReceiver` (custom `ALARM` action)
- `AutomationBootReceiver` (`BOOT_COMPLETED`)
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`,
  `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`

## Hardware / OS limitations

- Exact alarms depend on the `SCHEDULE_EXACT_ALARM` grant (Android 12+); otherwise inexact.
- WorkManager periodic work is floored at 15 minutes and batched by the OS; Doze delays it.
- Boot restore depends on OEM `BOOT_COMPLETED` policy.
- Transfers need connectivity; no metered-network policy yet.
- Real device behaviour (Doze, battery savers, OEM killers) cannot be exercised in this sandbox;
  the JVM suite validates engine logic, not OS scheduling.

## Fix — duplicate menu methods

`BootstrapActivity` contained two copies of `onCreateOptionsMenu` / `onOptionsItemSelected`
(the second copy held the automation, file-manager and notification branches), which fails
compilation with "method is already defined". The two copies were merged into a single pair that
keeps every branch, and the duplicate was removed. `ProjectActivity` was already correct; its
`R.id.action_automation` reference resolves because `menu_project.xml` and `menu_bootstrap.xml`
both declare the item. Verified: no duplicate method signatures remain and both activities
compile to class files.

## Fix — build errors in NativeApiIntegration / ProjectActivity / BootstrapActivity

- `NativeApiIntegration.registerAutomation` / `registerFileManager` called `router.register(handler)`,
  which does not exist on `IRouter`; both now call `router.registerHandler(handler)`.
- The same methods built `Entry` with three arguments while `Entry` requires
  `(route, handlerClass, status, detail)`; they now pass `STATUS_REGISTERED` / `STATUS_FAILED`
  and a detail string, matching every other route registration.
- `ProjectActivity` used `Menu`, `MenuItem`, `CameraActivity` and the four `*ProjectContext`
  classes without imports; the imports were added.
- `menu_bootstrap.xml` had lost the settings, open-bootstrap, downloads, repair-storage, sources,
  diagnostics and about items while `BootstrapActivity` still referenced their ids; the full menu
  was restored.

## Verification run — updated scope

- **Compilation:** contracts, logger, service, storage and platform engine plus the automation
  test sources compiled with ECJ targeting Java 17 — **0 errors**.
- **Automated tests:** `JUnitCore` executed the seven automation suites —
  **OK (40 tests)** in 2.7 s, covering store persistence, event ordering/replay, task execution
  and cancellation, job scheduling and constraints, alarm scheduling/repeat/boot restore,
  transfer integrity/cancel and bridge routing with project isolation.
- **Dependency audit:** no Kotlin, Compose, Retrofit, OkHttp, Coroutines, Hilt or Dagger
  references in any Gradle file or Java source. RxJava, Gson and WorkManager appear only in
  `native-platform-engine/build.gradle`.
- **Speech audit:** no Speech classes, permissions or dependencies; only the `speech: false`
  capability flag.
- **Gradle limitation:** this sandbox has no Gradle distribution and no Android SDK
  (`ANDROID_HOME` unset), so `./gradlew assembleDebug` / `test` cannot run here. Compilation and
  tests were executed directly on the JVM with the same Java 17 sources and the same library
  versions declared in Gradle. Run `./gradlew :native-platform-engine:test :app:assembleDebug`
  on a machine with the Android SDK to reproduce the device build.

## Quick Ball — extensible action system

- Added `com.roni.life.os.app.quickball`: `NativeAction`, `NativeActionRegistry`,
  `QuickBallLayout`, `QuickBallPreferences`, `QuickBallSettingsActivity`.
- Registered 20 built-in actions across Runtime, System, Files, Media and Automation categories.
  Duplicate ids are rejected; registration order is preserved.
- `ProjectControlSheet` keeps all six original project actions and the live-session switcher, and
  appends the enabled registry actions plus a Customize entry.
- `ProjectOverlayButton` reads ball size and opacity from the same preference store.
- `AutomationActivity` accepts `EXTRA_SECTION` so task/transfer/job/alarm/service shortcuts open
  the matching section of the existing dashboard.
- Runtime Inspector is opened through the existing idempotent `RuntimeInspectorSheet.install()`;
  its UI, filtering, export and debugging behaviour is untouched.
- **Tests:** `QuickBallLayoutTest` (7) and `NativeActionRegistryTest` (7) — **OK (14 tests)**.
- **Compile:** the new package and the modified `ProjectControlSheet`, `ProjectOverlayButton`
  and `AutomationActivity` compile with no errors against the project's Java 17 toolchain. The
  only unresolved reference in the sandbox is `com.roni.library.devtools.*`, which is provided by
  the `:native-dev-tools-sdk` module in a real Gradle build.
- **XML:** `activity_quick_ball_settings.xml`, `activity_automation.xml` and
  `AndroidManifest.xml` parse as valid XML.
- **Gradle limitation:** no Gradle distribution and no Android SDK are available in this sandbox,
  so `./gradlew` cannot run here. Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` on a
  machine with the Android SDK to reproduce the device build.
