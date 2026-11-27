# Notification System — Architecture

## 1. Ownership

| Concern | Owner |
| --- | --- |
| Notification engine (channels, styles, scheduling, delivery policy) | `:native-platform-engine` (L2) |
| Notification contracts (request, record, preferences, limits, errors) | `:native-runtime-contracts` (L0) |
| Persistence of history / scheduled / preferences | `:native-storage-engine` via `EncryptedPreferences` |
| Bridge route `/notification` | `:native-platform-engine` handler, registered by `:native-runtime-sdk` |
| Notification Center, settings, permission flow, deep-link routing | RONiLiFEOS consumer app |

No new Gradle module was introduced. The engine lives next to `ToastNotificationService`
because that module already owns platform-level user-facing services.

Dependency direction is unchanged: `app → native-runtime-sdk → native-platform-engine →
native-runtime-contracts`. `native-platform-engine` gained one dependency on
`:native-storage-engine` (same layer, no cycle) so notification metadata reuses the existing
encrypted preference store instead of a second storage engine.

## 2. Components

```
native-runtime-contracts/.../contracts/notification/
  NotificationStyle, NotificationPriority, NotificationVisibility,
  NotificationPermissionState, NotificationLimits, NotificationErrors,
  NotificationActionSpec, NotificationChannelSpec,
  NotificationRequest (+ Builder, validate()), NotificationRecord,
  NotificationPreferences (+ Builder)

native-platform-engine/.../platform/notification/
  NotificationBackend        delivery abstraction (Android impl + test fake)
  NotificationScheduler      alarm abstraction
  NotificationStore          persistence abstraction
  NotificationListener       host callbacks
  NotificationRateLimiter    fixed-window per-project limiter
  NotificationException      typed engine failure
  NotificationCodec          line-based encoding for the preference store
  NotificationService        the engine: policy, history, isolation, lifecycle
  NotificationProjectContext active project scope (set by the host)
  NotificationNavigationHandler  host routing contract
  InMemoryNotificationStore  fallback store
  PreferencesNotificationStore   EncryptedPreferences-backed store
  AndroidNotificationBackend framework NotificationManager implementation
  NotificationIntentFactory  immutable, explicit PendingIntents
  NotificationImageLoader    bounded BigPicture loader
  AlarmNotificationScheduler AlarmManager implementation
  NotificationReceiver       interaction + alarm entry point
  NotificationCommandHandler bridge route /notification
```

## 3. Delivery pipeline

```
Web app  --(NativeApiRequest /notification)-->  NotificationCommandHandler
                                                     |
                                        project from NotificationProjectContext
                                                     |
                                            NotificationRequest.validate()
                                                     |
                                        NotificationService.post()
                                    ┌────────────────┼────────────────┐
                            preferences        rate limiter      history store
                          (block / mute)      (20 per 60 s)     (max 200)
                                    └────────────────┼────────────────┘
                                              NotificationBackend
                                                     |
                                          NotificationManager.notify()
```

`NotificationService` never trusts a project id from the payload. The handler resolves the
project from `NotificationProjectContext`, which `ProjectActivity` sets in `onResume()` and
clears in `onDestroy()`. A request with no active project is rejected with
`NOTIFICATION_FORBIDDEN`.

## 4. Isolation

* Notification ids are derived from `projectId + key` (`NotificationService.notificationId`).
* Channel ids are derived from `projectId + channelId` (`NotificationService.channelIdFor`),
  so two projects can both use a channel called `orders` without colliding.
* `cancel(projectId, key)` only matches records owned by that project; a foreign key raises
  `NOTIFICATION_NOT_FOUND` and leaves the existing notification untouched.
* History queries are always filtered by project.

## 5. Security

* Every `PendingIntent` is explicit (targets `NotificationReceiver`) and immutable on API 23+.
* Deep links are validated by `NotificationRequest.validate()`: only `http`, `https` and the
  app scheme are accepted; `javascript:`, `file:`, `content:` and `intent:` are rejected.
* `NotificationReceiver` is `android:exported="false"`.
* Payload limits: title 120, body 1000, big text 2000, image URL 2048, 7 inbox lines,
  3 actions, deep link 512, key 64, group key 64, channel id 64.
* Rate limit: 20 notifications per project per 60 s window.
* Schedule window: 1 s to 30 days.
* BigPicture downloads are capped at 2 MB and decoded with a sample size; any failure falls
  back to BigText instead of crashing the host.

## 6. Lifecycle

* `PlatformEngine.init()` builds the engine, calls `NotificationService.init()` and
  `restoreScheduled()`, which re-arms every persisted alarm.
* `PlatformEngine.dispose()` calls `NotificationService.release()` and drops the reference.
* History, scheduled requests and preferences are persisted through
  `PreferencesNotificationStore`; when the encrypted store cannot be opened the engine falls
  back to `InMemoryNotificationStore` so notifications keep working for the process lifetime.
* `NotificationReceiver` re-resolves `PlatformEngine.getInstance()` on every broadcast, so a
  process restart never leaves a stale service reference.

## 7. Consumer app

* `NotificationCenterActivity` — all notifications across projects, unread filter, mark all
  read, clear history, dismiss, open project.
* `NotificationSettingsActivity` — global switch, runtime permission request, sound and
  vibration, per-project block/mute, per-channel block.
* `NotificationPermissionHelper` — `POST_NOTIFICATIONS` on Android 13+, no-op below.
* `NotificationDeepLinkRouter` — implements `NotificationNavigationHandler`; builds an
  explicit `ProjectActivity` intent and carries the deep link as an extra.
* Entry points: overflow menu on the dashboard (`menu_bootstrap.xml`).

## 8. Testing

```bash
./gradlew :native-platform-engine:test
```

25 JVM tests cover contract validation, the rate limiter, engine policy (post, cancel,
cross-project isolation, block, mute, rate limiting, history scoping, read state) and the
bridge handler (project scoping, invalid payload, cross-project cancel).

Device behaviour (channel creation, heads-up display, alarm firing) requires an emulator or
device and is **not** claimed as verified by the JVM suite.

## 9. Build

```bash
./gradlew :native-platform-engine:assembleDebug
./gradlew :app:assembleDebug
```

Java 17 only. No Kotlin, no Compose, no Retrofit/OkHttp/Coroutines/Hilt/Dagger.
