# `/camera` Web Bridge API

Web Apps request camera operations through the NativeRuntimeSDK bridge. They never touch Android
camera hardware, never receive absolute filesystem paths and never choose the project — the host
binds `CameraProjectContext` before the page is allowed to call this route.

## Envelope

Request:

```json
{ "route": "camera", "action": "capture", "payload": { "key": "photo" } }
```

Response:

```json
{ "success": true, "data": { "id": "media-1", "type": "PHOTO", "relativePath": "project-a/photo_1.jpg" } }
```

Error:

```json
{ "success": false, "error": { "code": "CAMERA_BUSY", "message": "Cannot move from PREVIEW to RECORDING" } }
```

Every call is asynchronous; the bridge resolves the promise when the engine finishes the blocking
hardware operation.

## Actions

| Action | Payload | Result |
|--------|---------|--------|
| `getCapabilities` | — | capability object |
| `requestPermission` | `{ "audio": false }` | `{ "camera": true, "audio": false }` |
| `open` | `{ "lens": "BACK", "flashMode": "AUTO" }` | capability object |
| `close` | — | `{}` |
| `switchCamera` | — | `{ "lens": "FRONT" }` |
| `capture` | `{ "key": "photo", "selfTimerMs": 0, "burstCount": 1, "jpegQuality": 95 }` | media item |
| `startRecording` | `{ "key": "video", "width": 1920, "height": 1080, "frameRate": 30, "withAudio": true }` | media item |
| `stopRecording` | — | media item |
| `pauseRecording` | — | `{}` |
| `resumeRecording` | — | `{}` |
| `setZoom` | `{ "zoom": 2.5 }` | `{ "zoom": 2.5 }` |
| `setFlash` | `{ "mode": "TORCH" }` | `{ "mode": "TORCH" }` |
| `setFocus` | `{ "x": 0.5, "y": 0.5 }` | `{}` |
| `setExposure` | `{ "ev": 0.5 }` | `{ "ev": 0.5 }` |
| `setResolution` | `{ "width": 3840, "height": 2160 }` | `{}` |
| `getStatus` | — | state, recording duration, size, zoom, flash |
| `getMedia` | `{ "type": "PHOTO", "limit": 50 }` | media item array |
| `deleteMedia` | `{ "id": "media-1" }` | `{ "deleted": true }` |

## JavaScript example

```js
async function takePhoto() {
  const capabilities = await NativeRuntime.call('camera', 'getCapabilities');
  if (!capabilities.supported) {
    throw new Error('No camera available');
  }
  await NativeRuntime.call('camera', 'open', { lens: 'BACK', flashMode: 'AUTO' });
  const photo = await NativeRuntime.call('camera', 'capture', {
    key: 'web-photo',
    selfTimerMs: 0,
    jpegQuality: 92
  });
  await NativeRuntime.call('camera', 'close');
  return photo.relativePath; // safe, project-relative
}
```

```js
async function recordClip() {
  await NativeRuntime.call('camera', 'open', { lens: 'BACK' });
  await NativeRuntime.call('camera', 'startRecording', {
    key: 'web-clip', width: 1920, height: 1080, frameRate: 30, withAudio: true
  });
  await new Promise(resolve => setTimeout(resolve, 5000));
  const clip = await NativeRuntime.call('camera', 'stopRecording');
  await NativeRuntime.call('camera', 'close');
  return clip;
}
```

## Error codes

| Code | Meaning |
|------|---------|
| `CAMERA_UNSUPPORTED` | Device exposes no usable camera |
| `CAMERA_PERMISSION_DENIED` | Runtime camera permission missing |
| `AUDIO_PERMISSION_DENIED` | Microphone permission missing for audio recording |
| `CAMERA_BUSY` | Illegal state transition (e.g. capture while recording) |
| `CAMERA_NOT_OPEN` | Operation requires an open session |
| `INVALID_REQUEST` | Payload failed validation |
| `INVALID_LENS` / `INVALID_FLASH` / `INVALID_RESOLUTION` | Unsupported hardware option |
| `NO_ACTIVE_PROJECT` | Host has not bound a project |
| `MEDIA_NOT_FOUND` | Unknown id, or the item belongs to another project |
| `STORAGE_FULL` | Free space below the 32 MB floor |
| `RECORDING_FAILED` | Recorder could not start or finalise |

## Rules for Web Apps

1. Call `getCapabilities` first and hide controls the device does not support.
2. Always `close` the session when the page loses focus.
3. Treat `relativePath` as opaque; never build filesystem paths.
4. `deleteMedia` only affects the current project; a foreign id returns `MEDIA_NOT_FOUND`.
5. Audio is recorded only when `withAudio` is `true` **and** the user granted the microphone.
