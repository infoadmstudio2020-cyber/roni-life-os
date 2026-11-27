# Native Camera Engine

Advanced camera subsystem for the NativeRuntimeSDK, consumed by the RONiLiFEOS Consumer App and
exposed to Web Apps through the `/camera` bridge route.

## 1. Placement in the ecosystem

| Layer | Module | Responsibility |
|-------|--------|----------------|
| L0 contracts | `native-runtime-contracts` | Pure-Java camera model, validation, error codes |
| L1 infrastructure | `native-storage-engine` | `EncryptedPreferences` media index, app-private files |
| L2 platform | `native-platform-engine` | Camera2 backend, service, state machine, repository, bridge handler |
| L3 runtime | `native-runtime-sdk` | `/camera` route registration in `NativeApiIntegration` |
| Consumer | `app` | Camera workspace, gallery, review, settings |

No new Gradle module was introduced: the engine lives in `native-platform-engine`, which already
owns platform services (notifications, device, clipboard, battery). This keeps the dependency
graph unchanged and avoids a second storage/permission stack.

**Camera2 over CameraX.** CameraX would add `androidx.camera:*` artifacts and its own lifecycle
and executor model, and it does not expose manual ISO / shutter / focus distance. Camera2 is part
of the platform, supports every professional control in the requirement, and keeps the dependency
graph identical to the rest of the SDK.

## 2. Class map

```
com.roni.library.contracts.camera
  CameraLens, CameraFlashMode, CameraCaptureMode, CameraState
  CameraErrorCodes, CameraLimits
  CameraCapabilities, CameraConfiguration
  PhotoCaptureRequest, VideoRecordingRequest
  CameraMediaItem, CameraError, CameraResult

com.roni.library.platform.camera
  CameraProjectContext      host-owned project binding (never from JS)
  CameraException           checked engine failure
  CameraEventListener       state / error / recording callbacks
  CameraStateMachine        legal transition guard
  CameraCodec               line codec for the encrypted media index
  CameraMediaRepository     project-isolated files, metadata, thumbnails, recovery
  CameraBackend             hardware abstraction (fake-able in tests)
  Camera2Backend            Camera2 implementation
  CameraService             policy, validation, orchestration
  CameraCommandHandler      /camera bridge route
```

## 3. Lifecycle and state machine

```
IDLE -> OPENING -> PREVIEW -> CAPTURING -> PREVIEW
                       |          |
                       |          +-> RECORDING -> PAUSED -> RECORDING -> PREVIEW
                       +-> ERROR -> CLOSED
```

`CameraStateMachine.transition` throws `CameraException(BUSY)` on an illegal move;
`tryTransition` is the non-throwing variant used by recovery paths. `CameraService` serialises
every blocking hardware call on a dedicated `HandlerThread`, so capture, start/stop recording and
configuration changes never run on the main thread.

Interruptions: `CameraService.init()` calls `CameraMediaRepository.recoverInterrupted(project)`,
which marks unfinished recordings `INTERRUPTED`, keeps the bytes on disk and never crashes on a
truncated MP4.

## 4. Storage layout

```
<app-private>/Pictures/camera/<projectId>/
    photo_<timestamp>_<key>.jpg
    video_<timestamp>_<key>.mp4
    thumb_<id>.jpg
```

* Metadata is indexed in `EncryptedPreferences("camera_media")` through `CameraCodec`.
* Web Apps only ever receive `relativePath` (`<projectId>/file.jpg`). `CameraMediaRepository.resolve`
  rejects any path that escapes the project directory.
* `ensureCapacity` refuses writes below `CameraLimits.MIN_FREE_SPACE_BYTES` (32 MB).
* Deletion is scoped: `delete(projectId, id)` returns `false` when the item belongs to another
  project.

## 5. Security model

| Threat | Control |
|--------|---------|
| Page spoofs another project | Project comes from `CameraProjectContext`, set by the host activity; payload `projectId` is ignored |
| Page reads arbitrary files | Only relative paths are returned; `resolve` blocks traversal |
| Page deletes foreign media | Repository checks ownership before unlink |
| Background camera use | `CameraService.setEnabled(false)` on host pause; `close()` releases the device |
| Silent audio capture | `RECORD_AUDIO` requested only when `withAudio=true` |
| Unbounded requests | `CameraLimits` caps burst, timer, duration, size, page size |
| Malformed bridge input | Every request is validated before touching hardware |

## 6. Capability matrix

| Capability | Detection | Exposed when unsupported |
|------------|-----------|--------------------------|
| Front / rear lens | `CameraCharacteristics.LENS_FACING` | Lens omitted from `getCapabilities` |
| Flash / torch | `FLASH_INFO_AVAILABLE`, `CONTROL_AE_AVAILABLE_MODES` | `setFlash` returns `INVALID_FLASH` |
| Zoom | `SCALER_AVAILABLE_MAX_DIGITAL_ZOOM` | Zoom slider disabled |
| HDR | `CONTROL_AE_AVAILABLE_MODES` contains `CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE`/scene modes | HDR switch disabled |
| Manual ISO | `SENSOR_INFO_SENSITIVITY_RANGE` | ISO slider disabled |
| Manual shutter | `SENSOR_INFO_EXPOSURE_TIME_RANGE` | Shutter slider disabled |
| Manual focus | `LENS_INFO_MINIMUM_FOCUS_DISTANCE > 0` | Focus slider disabled |
| White balance | `CONTROL_AWB_AVAILABLE_MODES` | WB button disabled |
| Exposure compensation | `CONTROL_AE_COMPENSATION_RANGE` | EV slider disabled |
| Video | `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES` + `StreamConfigurationMap` | Video mode hidden |
| Audio | `RECORD_AUDIO` permission + microphone feature | Audio switch disabled |
| Histogram | Not implemented (see limitations) | — |

## 7. Consumer app screens

| Screen | Activity | Notes |
|--------|----------|-------|
| Camera home / live preview | `CameraActivity` | Dark workspace, TextureView preview, grid + horizon level |
| Photo capture | `CameraActivity` | Shutter, self-timer (off/3s/10s), burst, flash cycle |
| Video recording | `CameraActivity` | Start/stop, live duration, audio permission on demand |
| Professional controls | `CameraActivity` (PRO panel) | ISO, shutter, focus, EV, WB — disabled when unsupported |
| Capture review | `CameraReviewActivity` | Preview, metadata, delete or keep |
| Media gallery | `CameraGalleryActivity` | 3-column project grid |
| Media details | `CameraReviewActivity` | Type, size, duration, timestamp |
| Camera settings | `CameraSettingsActivity` | Grid, level, HDR, audio, JPEG quality, video resolution, free space |
| Permission / error states | `CameraActivity` | Inline status text, no `AlertDialog` dependency |

Entry point: project overflow menu → **Camera** (`R.menu.menu_project`). The activity receives the
project id from `ProjectActivity` and re-binds `CameraProjectContext` on creation so a process
restart cannot lose ownership.

## 8. Build and test report

Executed in this environment:

* `native-runtime-contracts`, `native-logger-sdk`, `native-service-sdk`, `native-storage-engine`,
  `native-platform-engine` — compiled with ECJ 3.33.0 (`-17`) against Android API 36 — **0 errors**.
* Consumer camera package (`app/.../camera/*`) — compiled against the engine output plus Android
  stubs — **0 errors**.
* `CameraEngineTest` — **20 tests, 0 failures** (state machine, capabilities, configuration and
  request validation, project isolation, codec round-trip, media ownership).
* XML resources validated with a parser (layouts, menu, manifest).

Not executed here (no Android SDK / device in this sandbox):

* `./gradlew assembleDebug` — Gradle is unavailable, so the APK was **not** built.
* Real preview, capture, recording, orientation changes, permission denial and interruption
  behaviour on hardware — **unverified**.

## 9. Known limitations

1. **Histogram** is not rendered; `CameraCapabilities.isHistogramSupported()` reports the device
   capability but the UI does not draw a live histogram yet.
2. **Burst capture** is validated and accepted by the API; the Camera2 backend performs sequential
   captures rather than a hardware burst request.
3. **HDR** maps to the closest available AE mode; true multi-frame HDR is device dependent.
4. **Video pause/resume** depends on `MediaRecorder` support; on devices that reject pause the
   engine returns `UNSUPPORTED` instead of silently continuing.
5. **Orientation** is handled through `configChanges` and the sensor orientation hint; exotic
   multi-window sizes are not specially laid out.
6. **Thumbnails** are generated with `BitmapFactory` downsampling, not a media pipeline.
