# Native Background & Automation Platform

Six cooperating engines inside `native-platform-engine`, exposed to Web Apps through the
`/automation` Native API route and to users through the Consumer App dashboard.

Speech Engine is intentionally **not** part of this cycle.

## Dependency policy

- Java 17 only. No Kotlin, no Jetpack Compose, no Retrofit, no OkHttp, no Coroutines,
  no Hilt and no Dagger anywhere in the automation stack.
- Allowed and used: RxJava 3.1.8 (event streams, task coordination), Gson 2.10.1
  (validated JSON payloads and persisted metadata), AndroidX WorkManager 2.9.0
  (constraint-based deferred work) and AndroidX Lifecycle 2.6.2 (transitive WorkManager
  dependency).
- These libraries are declared **only** in `native-platform-engine/build.gradle`; no other
  module gains them automatically. `app` consumes the engine through its public API.
- Versions are compatible with the project's AGP / Gradle / minSdk configuration; no new
  dependency was added where an existing one already covered the need.
- Speech Engine is out of scope: there are no Speech classes, no microphone permissions and no
  Speech dependencies. The capability map reports `speech: false` so Web Apps can detect the
  absence explicitly.

## Module ownership

| Layer | Module | Responsibility |
|-------|--------|----------------|
| L1 contracts | `native-runtime-contracts` (`contracts/automation`) | Pure-Java models, limits, error codes |
| L2 platform | `native-platform-engine` (`platform/automation`) | All six engines, receivers, worker, bridge handler |
| L3 SDK | `native-runtime-sdk` (`NativeApiIntegration`) | Registers the `automation` route |
| App | `app` (`automation/`) | Dashboard, event log, menu entry |

Dependencies point downward only. No library module references `com.roni.life.os.app.*`.

## Engines

### Event Engine (`EventEngine`)
- RxJava 3 `PublishSubject` per project, exposed as `Observable<AutomationEvent>`.
- Every event is persisted through `AutomationStore` and replayed from a bounded per-project
  buffer (`AutomationLimits.MAX_EVENTS`).
- Sequence numbers are monotonic per project and never reused, so a client cursor stays valid
  after `clearEvents`.
- Topics: `task`, `transfer`, `alarm`, `job`, `service`, `custom`.

### Background Task Engine (`BackgroundTaskEngine`)
- Fixed thread pool (2 workers) plus a scheduled retry executor.
- Built-in handlers: `noop`, `echo`, `delay`, `event`; custom handlers register by type.
- Progress reporting, cooperative cancellation, retry with backoff up to `maxAttempts`.
- States: `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`.
- Unknown task types are rejected at submit time.

### Job Scheduler (`JobScheduler`)
- AndroidX WorkManager backend with network / charging / idle constraints, initial delay and
  periodic intervals (minimum 15 minutes, enforced by `AutomationLimits`).
- `AutomationWorker` runs the job and forwards the payload to the task engine.
- When WorkManager is unavailable the job is still persisted and emitted as `deferred`, so it can
  be restored later; `getCapabilities` reports `workManager: false`.

### Alarm Engine (`AlarmEngine`)
- `AlarmManager` with exact alarms when permitted, inexact otherwise.
- One-shot and repeating alarms; repeating alarms advance from the scheduled trigger, skipping
  missed intervals.
- `AutomationAlarmReceiver` handles delivery, `AutomationBootReceiver` re-arms persisted alarms
  after reboot for every project recorded in the store (the receiver creates the service when the
  process is cold).

### Transfer Engine (`TransferEngine`)
- Streaming downloads and uploads over `HttpURLConnection`; nothing is buffered in memory.
- Downloads write to `<file>.part` and rename atomically on completion.
- Resume via HTTP `Range` when a partial file exists.
- Progress events throttled to ~4 per second; cooperative cancellation.
- Paths resolve through `FileManagerRepository`, so app-private storage rules still apply.

### Service Engine (`ServiceEngine`)
- Thin lifecycle facade over the existing `NativeServiceSDK` `ServiceRegistry`.
- No second service container: `list`, `status`, `start`, `stop` map to `IService`
  (`getServiceName`, `isRunning`, `onStart`, `onStop`).

## Project isolation

`AutomationProjectContext` holds the active project, set by the host when a project opens.
Every engine keys its state by `projectId`; the `/automation` bridge ignores any `projectId`
supplied by JavaScript. With no active project, mutating actions fail with
`NO_ACTIVE_PROJECT`.

## Persistence

`AutomationStore` serialises tasks, jobs, alarms and events with Gson into
`EncryptedPreferences` (`automation_meta`) from `native-storage-engine`. No second storage
engine and no plain-text files. A memory-backed `KeyValueStore` is used when encryption is
unavailable (tests, headless runs).

## Threading

- Task execution, retries and transfers run on `ExecutorService` threads.
- `AlarmManager` / `WorkManager` callbacks hop to the engines, never to the main thread for I/O.
- UI refresh is a 2-second main-thread tick that only reads in-memory snapshots.

## Consumer App

`AutomationActivity` shows tasks, transfers, alarms, jobs and services with cancel/start/stop
actions and creates tasks, alarms and downloads. `AutomationEventsActivity` shows the event log
with a topic filter. Both are reachable from the overflow menu of the bootstrap and project
screens.

## Platform limitations

- Exact alarms require `SCHEDULE_EXACT_ALARM` (Android 12+) and may be denied by the user; the
  engine falls back to inexact delivery and reports `exactScheduled: false`.
- WorkManager periodic work has a 15-minute floor and is batched by the OS; Doze can delay it.
- Transfers require network connectivity; there is no cellular/Wi-Fi policy switch yet.
- Boot restore depends on the device allowing `BOOT_COMPLETED` (some OEM builds restrict it).
- Background execution limits (Android 8+) mean long transfers should be treated as
  best-effort while the app is in the background.
