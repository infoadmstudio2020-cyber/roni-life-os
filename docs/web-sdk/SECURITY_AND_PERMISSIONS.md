# RONiLiFEOS — Security and Permissions

**Native classes:** `BridgeSecurityValidator`, `WebBridge`, `WebViewHost`, `WebPaths`, `SecurityCommandHandler`, `StorageScope`
**Route:** `/security`

---

## 1. Threat model

A web app inside RONiLiFEOS is **untrusted code**. It may be written by a third party, loaded from a remote URL, or updated without an app-store review. The SDK therefore assumes:

1. The page will try to call actions it should not.
2. The page will try to read another project's data.
3. The page will try to escape its origin with `../` or `%2e%2e`.
4. The page will try to forge bridge messages.

Every defence below exists to answer one of those four.

---

## 2. Origin model

### 2.1 Per-project virtual host

```
https://<slug>-<hash8>.runtime.local/
```

| Part | Source |
| --- | --- |
| `slug` | First 40 characters of the project id, lowercased, non-alphanumerics replaced |
| `hash8` | First 4 bytes of `SHA-256(projectId)`, 8 lowercase hex chars |
| suffix | `WebPaths.VIRTUAL_SUFFIX` = `.runtime.local` |

The host is derived from the **project id only**, never the project name. Renaming a project must not move its origin, or the project would silently lose `localStorage`, `IndexedDB`, cookies and service-worker registrations.

The legacy host `appassets.androidplatform.net` still exists for sources that do not specify a project.

### 2.2 Why a per-project origin matters

A single shared host means every project shares one browser origin — and therefore one `localStorage`, one `IndexedDB`, one cookie jar and one service-worker scope. Project A's JavaScript could read what project B persisted. The per-project host makes the **browser** enforce the isolation that the resource provider already enforces in native code.

### 2.3 Origin validation on the bridge

`BridgeConfig.requireOriginValidation` defaults to `true`. The allow-list starts with `https://appassets.androidplatform.net`; `WebViewHost.trustBridgeOrigin(host)` adds the origin of the source it is about to load.

```java
// WebViewHost.loadSource()
bridge.addAllowedOrigin(WebPaths.originOf(host));
```

A message from any other origin is rejected:

```json
{ "type": "ERROR", "error": "ORIGIN_REJECTED: https://evil.example" }
```

| Rule | Behaviour |
| --- | --- |
| `null` origin | Rejected |
| Empty allow-list | Everything rejected |
| `addAllowedOrigin("*")` | Check disabled — **never do this in production** |
| Sub-path of an allowed origin | Allowed (origin comparison, not URL comparison) |

---

## 3. Path traversal

`WebPaths.normalize()` collapses separators and converts backslashes, but deliberately does **not** percent-decode:

> Decoding is the provider's job, and doing it twice is how `%252e%252e` becomes `..`.

`FileResourceProvider` and the asset provider reject any resolved path that escapes the project root. A request for `../../databases/app.db` fails with a 404 from the provider — it never reaches the filesystem.

**Consequence for you:** paths are case-sensitive and must be relative to the project root. `./img/logo.png` and `img/logo.png` both work; `/img/logo.png` resolves against the virtual origin root, which is the project root.

---

## 4. Bridge message validation

`BridgeSecurityValidator.isMessageValid()` runs after the origin check:

| Check | Failure |
| --- | --- |
| `action` present and non-blank | `Security validation failed` |
| Payload is an object or absent | `Security validation failed` |
| Message size ≤ `maxMessageBytes` (1 MiB) | `PAYLOAD_TOO_LARGE` |

A message that fails validation never reaches a handler, so a handler can assume a well-formed request.

---

## 5. Permissions

### 5.1 Checking a permission

```js
const result = await Native.call('security.permission.check', {
  permission: 'android.permission.CAMERA'
});
// { permission: 'android.permission.CAMERA', status: 'GRANTED', granted: true }
```

| `status` | Meaning |
| --- | --- |
| `GRANTED` | Held |
| `DENIED` | Declared but not granted |
| `NOT_DECLARED` | Not in the manifest — cannot be requested |

`permission` is the **fully-qualified Android permission string**. Short names (`CAMERA`) are not resolved.

### 5.2 What the web app can and cannot do

| Capability | Web app | Host app |
| --- | --- | --- |
| Read permission state | ✅ `security.permission.check` | ✅ |
| Request a runtime permission | ❌ | ✅ |
| Show the system dialog | ❌ | ✅ |
| Open app settings | ✅ `device.openAppSettings` | ✅ |

`camera.requestPermission` reports state and sets `needsHostPrompt: true`; it does **not** show a dialog. `notification.permission` is read-only.

**Pattern:** check, then guide the user.

```js
async function ensurePermission(permission, rationale) {
  const { granted, status } = await Native.call('security.permission.check', { permission });
  if (granted) return true;

  if (status === 'NOT_DECLARED') {
    await Native.call('ui.showAlert', {
      title: 'সুবিধা নেই',
      message: 'এই অ্যাপে এই অনুমতিটি ঘোষণা করা হয়নি।'
    });
    return false;
  }

  await Native.call('ui.showAlert', { title: 'অনুমতি প্রয়োজন', message: rationale });
  await Native.call('device.openAppSettings');
  return false;
}
```

---

## 6. Keystore

`security.keystore.*` wraps the Android Keystore. Keys never leave the secure element / TEE.

### 6.1 Generate a key

```js
const { success, alias, algorithm } = await Native.call('security.keystore.generate', {
  alias: 'notes_key',
  algorithm: 'AES_GCM_256',
  requiresUserAuth: false
});
```

| Parameter | Default | Notes |
| --- | --- | --- |
| `alias` | `"default_key"` | |
| `algorithm` | `"AES_GCM_256"` | |
| `requiresUserAuth` | `false` | `true` → key usable only after device unlock |

### 6.2 Encrypt / decrypt

```js
const { ciphertext, iv } = await Native.call('security.keystore.encrypt', {
  alias: 'notes_key',
  plaintext: 'গোপন নোট'
});

const { plaintext } = await Native.call('security.keystore.decrypt', {
  alias: 'notes_key',
  ciphertext,
  iv
});
```

> ⚠ `iv` is **required** for decryption. Store it next to the ciphertext — it is not secret, but it is not recoverable from the ciphertext either.

```js
await Native.call('storage.set', {
  key: 'vault',
  value: JSON.stringify({ ciphertext, iv })
});
```

### 6.3 Key management

| Action | Parameters | Returns |
| --- | --- | --- |
| `security.keystore.hasKey` | `alias` (**R**) | `{ hasKey, alias }` |
| `security.keystore.delete` | `alias` (**R**) | `{ deleted, alias }` |
| `security.keystore.list` | — | `{ aliases: string[] }` |

Deleting a key makes every ciphertext encrypted with it **permanently unreadable**. There is no recovery path.

### 6.4 Diagnostic

```js
const diag = await Native.call('security.diagnostic');
// { state, hsmAvailable, biometricAvailable, registeredKeysCount, routePrefix, success }
```

---

## 7. Biometrics

### 7.1 Capability

```js
const capability = await Native.call('security.biometric.capability');
// { code, description, available }
```

| `code` | Meaning |
| --- | --- |
| `BIOMETRIC_SUCCESS` | Hardware present and enrolled |
| `BIOMETRIC_ERROR_NO_HARDWARE` | No sensor |
| `BIOMETRIC_ERROR_HW_UNAVAILABLE` | Sensor busy or unavailable |
| `BIOMETRIC_ERROR_NONE_ENROLLED` | No fingerprint/face enrolled |
| `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED` | Sensor compromised |
| `BIOMETRIC_STATUS_UNKNOWN` | Undetermined |

### 7.2 Authenticate

```js
const off = Native.on('security.biometric.result', ({ status, message }) => {
  off();
  if (status === 'SUCCEEDED') unlock();
  else showError(message);
});

await Native.call('security.biometric.authenticate', { title: 'নোট খুলুন' });
```

The call resolves with `{ success: true, initiated: true, message }` as soon as the prompt is **shown**. The outcome arrives as the event.

### 7.3 Fail-open pattern

Never lock a user out because a sensor is missing:

```js
async function requireBiometric(reason) {
  const { available } = await Native.call('security.biometric.capability');
  if (!available) return true;

  const result = new Promise((resolve) => {
    const off = Native.on('security.biometric.result', (payload) => {
      off();
      resolve(payload);
    });
  });

  await Native.call('security.biometric.authenticate', { title: reason });
  const outcome = await result;
  return outcome.status === 'SUCCEEDED';
}
```

---

## 8. Hashing

```js
const { sha256 } = await Native.call('security.hash.sha256', { input: 'hello' });
// 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
```

> ⚠ The parameter is `input`, **not** `text`. Passing `text` hashes the empty string and returns the SHA-256 of `""` — a silent, dangerous failure.

SHA-256 is the only hash exposed. There is no HMAC, PBKDF2, bcrypt or Argon2 in the bridge.

---

## 9. Storage isolation

| Layer | Isolation mechanism |
| --- | --- |
| `localStorage` / `IndexedDB` / cookies | Per-project virtual origin (browser-enforced) |
| `storage.*` | `StorageScope` prefix `__p:<escaped-id>:` (native-enforced) |
| `filemanager.*` | Per-project directory (native-enforced) |
| Keystore aliases | Global to the app — **not** project-scoped |

> ⚠ Keystore aliases are shared across projects. Two projects using `alias: "default_key"` share one key. Namespace your aliases: `notes_key`, `todo_key`.

---

## 10. Network security

`network.request` enforces:

| Rule | Failure |
| --- | --- |
| Scheme must be `http` or `https` | `UNSUPPORTED_SCHEME` |
| URL must parse | `INVALID_URL` |
| Method must be in the allow-list | `UNSUPPORTED_METHOD` |
| `host`, `content-length`, `connection` headers forbidden | `FORBIDDEN_HEADER` |

`http://` is permitted but Android's cleartext policy may still block it. Prefer HTTPS.

---

## 11. WebView hardening

`WebHostConfig` defaults that matter:

| Setting | Default | Why |
| --- | --- | --- |
| `javaScriptEnabled` | `true` | Required |
| `domStorageEnabled` | `true` | Required for `localStorage` |
| `allowFileAccess` | `false` | Prevents `file://` reads |
| `allowContentAccess` | `false` | Prevents `content://` reads |
| `mixedContentMode` | `NEVER_ALLOW` | Blocks HTTP sub-resources on HTTPS pages |
| `safeBrowsingEnabled` | `true` | Google Safe Browsing |
| `mediaPlaybackRequiresUserGesture` | `true` | No autoplay |
| `setSaveFormData` | `false` | No form autofill leakage |
| `setAllowFileAccessFromFileURLs` | `false` | |
| `setAllowUniversalAccessFromFileURLs` | `false` | |

`WebViewHost` also:

- Removes the `NativeRuntimeBridge` interface in `onDestroy`.
- Clears the web evaluator and origin provider in `onDestroy`.
- Keeps the HTTP cache (deliberate — re-opening a project should not re-download).
- Rejects `shouldOverrideUrlLoading` for non-http(s) schemes unless explicitly handled.

---

## 12. Security checklist for web app authors

- [ ] Never store secrets in `localStorage` — use `storage.*` (encrypted) or `security.keystore.*`.
- [ ] Namespace keystore aliases per project.
- [ ] Always send `iv` with `ciphertext`.
- [ ] Check `security.permission.check` before using a capability; degrade gracefully.
- [ ] Do not assume biometrics exist — fail open.
- [ ] Validate every value that came from `network.request` before rendering it (`textContent`, not `innerHTML`).
- [ ] Do not `eval()` data from the network.
- [ ] Keep payloads under 1 MiB.
- [ ] Do not change `manifest.json` `id` after release.
- [ ] Do not call `addAllowedOrigin("*")` in a host app you ship.

---

## 13. Related documents

- `JAVASCRIPT_BRIDGE.md` §4 — the four bridge gates
- `STORAGE_API.md` — scoping and encryption at rest
- `NATIVE_API_REFERENCE.md` §6 — `security.*` signatures
- `API_COMPATIBILITY.md` — what may change between versions
