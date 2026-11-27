# RONiLiFEOS — Error Codes and Exception Handling

**Verified against:** every `NativeApiResponse.error(...)` and `NativeApiError(...)` call in the source.

---

## 1. How errors reach JavaScript

Native sends an `ERROR` envelope:

```json
{ "id": "req_...", "type": "ERROR", "action": "ui.showToast",
  "error": "UNKNOWN_ACTION: Action not supported: nope", "success": false }
```

The JS client rejects with `new Error(msg.error)`. **It does not attach a `.code` property.** Parse the prefix yourself:

```js
function codeOf(error) {
  const text = String(error && error.message ? error.message : error);
  const match = text.match(/^([A-Z][A-Z0-9_]{2,})/);
  return match ? match[1] : 'NATIVE_ERROR';
}
```

The convention is `CODE: human readable detail`. Not every handler follows it — `Security validation failed` has no code prefix, and `ORIGIN_REJECTED: <origin>` puts the origin after the colon.

---

## 2. Bridge-level errors

| Code | Emitted by | Cause | Recovery |
| --- | --- | --- | --- |
| `ROUTE_NOT_FOUND` | `WebBridge` | No handler for the path | Check the namespace; probe with `isSupported()` |
| `REQUEST_TIMEOUT` | `WebBridge` sweeper | Handler did not answer within `timeoutMs` (10 s) | Retry; check for a blocking handler |
| `PAYLOAD_TOO_LARGE` | `WebBridge` | Message > 1 MiB | Chunk the payload |
| `ORIGIN_REJECTED` | `WebBridge` | Page origin not in the allow-list | Load through the project's virtual origin |
| `Security validation failed` | `WebBridge` | Missing/blank action, malformed envelope | Fix the call |
| `NATIVE_UNAVAILABLE` | JS client | `NativeRuntimeBridgeClient` absent | `await Native.ready()` |
| `CLIENT_TIMEOUT` | JS wrapper | Your own timeout fired | Increase `timeoutMs` |
| `BRIDGE_NOT_READY` | JS wrapper | `ready()` deadline passed | Check that the page finished loading |

> ⚠ `PAYLOAD_TOO_LARGE` is emitted with `id: "unknown"` because the message could not be parsed. The originating promise is **not** rejected — it times out instead.

---

## 3. Handler-level errors

### 3.1 Common to most handlers

| Code | Cause |
| --- | --- |
| `UNKNOWN_ACTION` | Action not in the handler's `switch` |
| `MISSING_ACTION` | Empty action (`ui`, `web`, `device`, `theme`, `runtime`, `devtools`) |
| `INVALID_REQUEST` | Null request object |
| `INTERNAL_ERROR` | Uncaught exception inside the handler |
| `INVALID_PARAM` | Missing or malformed required parameter (`analytics`) |

### 3.2 `ui`

| Code | Cause |
| --- | --- |
| `UNKNOWN_ACTION` | Unknown action |
| `MISSING_ACTION` | Empty action |
| `INVALID_REQUEST` | Null request |
| `INTERNAL_ERROR` | Unexpected failure |

Most `ui.*` actions never fail — they return `{ applied: false }` when no Activity is attached.

### 3.3 `web`

| Code | Cause |
| --- | --- |
| `MAIN_THREAD_TIMEOUT` | Command did not run on the UI thread within 10 s |
| `MAIN_THREAD_UNAVAILABLE` | UI thread handler unavailable |
| `MAIN_THREAD_BLOCKED` | `web.evaluate` called from the main thread |
| `UNKNOWN_ACTION` | Unknown action |

### 3.4 `device`

| Code | Cause |
| --- | --- |
| `ACTIVITY_UNAVAILABLE` | No Activity attached to `NativeUIHost` |
| `UNKNOWN_ACTION` | Unknown action |

### 3.5 `storage`

| Code | Cause | Recovery |
| --- | --- | --- |
| `STORAGE_UNAVAILABLE` | `StorageEngine` not initialised | Host app must call `StorageEngine.initialize()` |
| `RESERVED_KEY` | Key starts with `__p:` in the legacy namespace | Rename the key |
| `INVALID_REQUEST` | Null request | Pass a payload |
| `INTERNAL_ERROR` | Keystore or disk failure | Check `logger` output |

### 3.6 `security`

| Code | Cause |
| --- | --- |
| `INVALID_PARAM` | Missing `alias`, `plaintext`, `ciphertext`, `iv` or `permission` |
| `KEY_NOT_FOUND` | Alias does not exist |
| `KEY_GENERATION_FAILED` | Keystore refused to create the key |
| `ENCRYPTION_FAILED` / `DECRYPTION_FAILED` | Cipher error — usually a wrong `iv` |
| `BIOMETRIC_UNAVAILABLE` | No sensor or none enrolled |
| `BIOMETRIC_FAILED` | Prompt failed to start |
| `INTERNAL_ERROR` | Unexpected failure |

### 3.7 `network`

| Code | Cause |
| --- | --- |
| `INVALID_URL` | Missing or unparseable URL |
| `UNSUPPORTED_SCHEME` | Not `http`/`https` |
| `UNSUPPORTED_METHOD` | Method not in the allow-list |
| `FORBIDDEN_HEADER` | `host`, `content-length` or `connection` supplied |
| `NETWORK_ERROR` | DNS, TLS, timeout, reset |

### 3.8 `notification`

| Code | Cause |
| --- | --- |
| `NOTIFICATION_FORBIDDEN` | No active project |
| `NOTIFICATION_INVALID_KEY` | Empty `key` on cancel |
| `NOTIFICATION_INVALID_SCHEDULE` | `scheduledAt` in the past |
| `NOTIFICATION_PERMISSION_DENIED` | `POST_NOTIFICATIONS` not granted |
| `NOTIFICATION_CHANNEL_ERROR` | Channel creation failed |
| `NOTIFICATION_INVALID_REQUEST` | Malformed payload |
| `NOTIFICATION_INTERNAL_ERROR` | Unexpected failure |

### 3.9 `automation`

| Code | Cause |
| --- | --- |
| `AUTOMATION_UNAVAILABLE` | `PlatformEngine` not initialised |
| `AUTOMATION_UNKNOWN_WORKER` | Unknown `type` / `workerType` |
| `AUTOMATION_INVALID_TASK` | Malformed task payload |
| `AUTOMATION_INVALID_JOB` | Missing `id` or invalid interval |
| `AUTOMATION_INVALID_ALARM` | `triggerAt` in the past |
| `AUTOMATION_TRANSFER_FAILED` | Network or disk failure |
| `AUTOMATION_NOT_FOUND` | Unknown id |
| `AUTOMATION_INTERNAL_ERROR` | Unexpected failure |

### 3.10 `camera` and `filemanager`

| Code | Cause |
| --- | --- |
| `CAMERA_UNAVAILABLE` | No camera or engine not ready |
| `CAMERA_PERMISSION_DENIED` | Camera permission missing |
| `CAMERA_IN_USE` | Another app holds the camera |
| `CAMERA_CAPTURE_FAILED` | Capture or encode failure |
| `FILE_NOT_FOUND` | Path does not exist |
| `FILE_ACCESS_DENIED` | Path outside the project root |
| `FILE_ALREADY_EXISTS` | Target exists |
| `FILE_OPERATION_FAILED` | Copy/move/delete failure |
| `FILE_INVALID_PATH` | Empty or malformed path |

### 3.10 `analytics`

| Code | Cause |
| --- | --- |
| `INVALID_PARAM` | Missing `name`, non-finite `value`, missing `screenName` |
| `UNKNOWN_ACTION` | Unknown action |

### 3.11 `service`

| Code | Cause |
| --- | --- |
| `SERVICE_NOT_FOUND` | Unknown `serviceId` (HTTP 404) |

---

## 4. Handling pattern

```js
async function safeCall(action, payload) {
  try {
    return { ok: true, data: await Native.call(action, payload) };
  } catch (error) {
    const code = error.code || codeOf(error);

    switch (code) {
      case 'NATIVE_UNAVAILABLE':
      case 'BRIDGE_NOT_READY':
        return { ok: false, code, fatal: true, message: 'Native host নেই' };

      case 'ROUTE_NOT_FOUND':
        return { ok: false, code, unsupported: true, message: 'এই বিল্ডে সুবিধাটি নেই' };

      case 'REQUEST_TIMEOUT':
      case 'CLIENT_TIMEOUT':
        return { ok: false, code, retryable: true, message: 'সময় শেষ' };

      case 'PAYLOAD_TOO_LARGE':
        return { ok: false, code, message: 'ডেটা খুব বড়' };

      case 'STORAGE_UNAVAILABLE':
      case 'ACTIVITY_UNAVAILABLE':
        return { ok: false, code, retryable: true, message: 'সার্ভিস প্রস্তুত নয়' };

      default:
        return { ok: false, code, message: error.message };
    }
  }
}
```

---

## 5. Retry policy

| Code | Retry? | Backoff |
| --- | --- | --- |
| `REQUEST_TIMEOUT` | ✅ | 1 s, 2 s, 4 s |
| `CLIENT_TIMEOUT` | ✅ | same |
| `NETWORK_ERROR` | ✅ | exponential, cap 30 s |
| `STORAGE_UNAVAILABLE` | ✅ | 500 ms |
| `ACTIVITY_UNAVAILABLE` | ✅ | 500 ms |
| `ROUTE_NOT_FOUND` | ❌ | permanent |
| `NATIVE_UNAVAILABLE` | ❌ | permanent |
| `PAYLOAD_TOO_LARGE` | ❌ | fix the payload |
| `ORIGIN_REJECTED` | ❌ | fix the source |
| `RESERVED_KEY` | ❌ | rename the key |
| `INVALID_PARAM` | ❌ | fix the call |

```js
async function withRetry(fn, attempts = 3) {
  let delay = 1000;
  for (let i = 0; i < attempts; i++) {
    try {
      return await fn();
    } catch (error) {
      const retryable = ['REQUEST_TIMEOUT', 'CLIENT_TIMEOUT', 'NETWORK_ERROR',
                         'STORAGE_UNAVAILABLE', 'ACTIVITY_UNAVAILABLE'].includes(error.code);
      if (!retryable || i === attempts - 1) throw error;
      await new Promise((r) => setTimeout(r, delay));
      delay *= 2;
    }
  }
}
```

---

## 6. Silent failures to watch for

These calls **succeed** but do nothing. They are the most expensive bugs.

| Call | Problem | Fix |
| --- | --- | --- |
| `ui.showToast { text }` | Empty toast | `message` |
| `platform.toast.show { text }` | Shows `"Notification"` | `message` |
| `analytics.trackEvent { properties }` | No parameters | `parameters` |
| `security.hash.sha256 { text }` | Hash of `""` | `input` |
| `filemanager.writeFile { content }` | Empty file | `base64` |
| `network.request { body: {} }` | Empty body | Stringify it |
| `storage.set { value: {} }` | Stores `"[object Object]"` | `JSON.stringify` |
| `ui.showConfirm` awaited for a boolean | Always `{ shown: true }` | Listen to `ui.confirm` |
| `security.biometric.authenticate` awaited for a result | Always `{ initiated: true }` | Listen to `security.biometric.result` |

---

## 7. Logging errors

```js
function reportError(action, error) {
  Native.call('logger.log', {
    level: 'ERROR',
    tag: 'WebApp',
    message: action + ' → ' + (error.code || 'ERROR') + ': ' + error.message
  }).catch(() => {});
}
```

For a native-side view, attach the Runtime Inspector — it records bridge requests, responses and rejections with timings. See `DEVELOPER_TOOLS.md`.

---

## 8. Related documents

- `JAVASCRIPT_BRIDGE.md` §6 — bridge error table
- `API_COMPATIBILITY.md` §4 — parameter traps
- `TROUBLESHOOTING_AND_FAQ.md` — symptom-driven diagnosis
- `DEVELOPER_TOOLS.md` — Runtime Inspector
