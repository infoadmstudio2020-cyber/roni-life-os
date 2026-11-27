# Build and Test Report — Native Camera Engine

Date: 2026-11-19
Environment: Linux sandbox, no Android SDK, no Gradle, no device/emulator.

## Toolchain used

| Tool | Version / path |
|------|----------------|
| ECJ (Eclipse compiler) | 3.33.0 — `/tmp/jvm/lib/ecj-3.33.0.jar` |
| Java target | 17 |
| Android platform jar | API 36, `java/*` and `javax/*` stripped (`android-clean.jar`) |
| AndroidX annotations | `annotation-jvm-1.7.0.jar` |
| AndroidX core | `core-1.13.1.jar` |
| JUnit | 4.13.2 + hamcrest-core 1.3 |

## Compilation results

| Module | Sources | Result |
|--------|---------|--------|
| `native-runtime-contracts` | camera contracts + existing | 0 errors |
| `native-logger-sdk` | existing | 0 errors |
| `native-service-sdk` | existing | 0 errors |
| `native-storage-engine` | existing | 0 errors |
| `native-platform-engine` | camera engine + existing | 0 errors |
| `app` camera package | 6 classes | 0 errors |

Command shape:

```
java -jar ecj-3.33.0.jar -17 -encoding UTF-8 -nowarn -d out \
  -cp "android-clean.jar:annotation-jvm.jar:core-1.13.1.jar" \
  $(find <module>/src/main/java -name "*.java")
```

## Test results

`com.roni.library.platform.camera.CameraEngineTest` — **20 tests, 0 failures, 0 errors**.

| Area | Tests |
|------|-------|
| State machine | open flow, illegal transition, recording/pause, non-throwing variant |
| Capabilities | unsupported device, declared-feature exposure |
| Configuration validation | unsupported device, JPEG quality range, oversized photo, valid defaults |
| Photo request validation | burst limit, self-timer limit, valid input |
| Video request validation | duration limit, valid input |
| Project isolation | project switching, blank identifier rejection |
| Media metadata | codec round-trip, corrupt line rejection, map round-trip ownership |

A defect was found and fixed by the suite: `CameraProjectContext.setActiveProject("   ")`
previously stored a blank id, which would have collapsed projects into one media bucket. Blank
values are now ignored.

## Resource validation

85 XML files (layouts, menu, values, manifest) parsed successfully — 0 errors.

## Not executed

* `./gradlew assembleDebug` / `assembleRelease` — Gradle and the Android SDK are not installed in
  this sandbox. **No APK was produced.**
* Instrumented tests, real preview, capture, recording, orientation changes, permission denial and
  interruption recovery on hardware — **unverified**.

## Device-dependent behaviour

| Feature | Dependency |
|---------|------------|
| 4K recording | `StreamConfigurationMap` + encoder support |
| Manual ISO / shutter | `SENSOR_INFO_*` ranges |
| Manual focus | `LENS_INFO_MINIMUM_FOCUS_DISTANCE > 0` |
| Torch | `FLASH_INFO_AVAILABLE` |
| HDR | AE mode availability |
| Pause/resume recording | `MediaRecorder.pause()` (API 24+) |
| Horizon level | Accelerometer presence |
