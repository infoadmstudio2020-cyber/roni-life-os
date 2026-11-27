# RONiLiFEOS v1.8.0 — Security Hardening & Verification Report

**Scope:** memory safety, AES-GCM AAD hardening, interrupted-encryption recovery, concurrent
registration, cache security, WebView compatibility, and build/test execution.

**Headline:** the format layer is now **compiled and tested** — 125 checks pass on a real JDK. The
Android layer (Keystore, WebView, Gradle) is **not** compiled or run, because this environment has
no Android SDK. §7 states exactly which is which.

---

## 1. Memory safety

### 1.1 The problem

Three places buffered a whole resource before doing anything with it:

| Site | Old behaviour | Failure |
|---|---|---|
| `ProjectVault.sealTree` | `readAll(file)` → `seal(byte[])` | a 200 MB file (the archive cap) OOMs on a low-end device |
| `WebViewHost.shouldInterceptRequest` | `readAll(resource.getStream())` | every decrypted resource fully buffered |
| `VaultResourceDecoder` | returned `byte[]` | no way to stream even if the caller wanted to |

### 1.2 The fix: chunked AEAD

A single GCM tag over a large file can only be verified **after** the whole file is decrypted. That
forces the plaintext into memory before it is known to be authentic — so "stream it" and "never
expose unauthenticated plaintext" were in direct conflict.

v2 resolves it by tagging each chunk independently:

```
[magic "RVLT":4][0x02][chunkSize:4][chunkCount:4][plainLength:8]
then chunkCount × ( [iv:12][ciphertext + tag:16] )
AAD = projectId \n path \n 2 \n generation \n chunkIndex
```

`SealedInputStream` decrypts one chunk at a time and calls `doFinal` — which verifies that chunk's
tag — **before** releasing a single byte of its plaintext. Memory is bounded by one chunk (64 KB)
regardless of resource size, and no unauthenticated plaintext ever escapes.

### 1.3 Configurable limits

`VaultLimits` holds three `volatile` ceilings, settable at runtime and clamped to a sane range:

| Limit | Default | Effect |
|---|---|---|
| `maxResourceBytes` | 64 MB | larger resources are refused (404), never buffered |
| `maxBufferedBytes` | 2 MB | above this, stream instead of buffering |
| `chunkBytes` | 64 KB | plaintext per authenticated chunk |

`DecryptedResourceCache` gained a separate `maxEntryBytes` (default 2 MB) so one large resource
cannot evict everything else and still leave the cache thrashing.

### 1.4 Three size regimes

`VaultResourceDecoder.decodeStream` reads the plaintext length from the sealed header — no guessing,
no buffering to find out:

| Plaintext size | Path | Peak memory | Cached |
|---|---|---|---|
| ≤ 2 MB | decrypt fully, verify every tag, serve | one resource | yes |
| ≤ 64 MB | stream chunk by chunk, each tag verified before release | one chunk | no |
| > 64 MB | refused, request becomes 404 | none | no |

### 1.5 Cleanup

| Path | Guarantee |
|---|---|
| `sealFile` success | temp `.sealing` renamed over the target |
| `sealFile` failure | temp deleted, original untouched, streams closed in `finally` |
| `openStream` failure | source stream closed before the exception propagates |
| `decodeStream` failure | source closed; `WebViewHost` returns 404 |
| `SealedInputStream.close` | buffer dropped, source closed |
| `rotate` failure | temp `.rotating` deleted, pending marker left so the tree is detectable |

**No decrypted plaintext is ever written to disk.** The only disk writes are ciphertext (`.sealing`,
`.rotating` temporaries) and the two markers.

---

## 2. AES-GCM authentication

### 2.1 What v1 authenticated

`AAD = projectId`. That left three holes:

| Attack | v1 | v2 |
|---|---|---|
| Move a sealed file to another path in the same project | **succeeds** | fails — path is in the AAD |
| Replace resource A's ciphertext with resource B's | **succeeds** | fails — path is in the AAD |
| Substitute the project id | fails (key + AAD) | fails (key + AAD) |
| Modify ciphertext | fails (tag) | fails (per-chunk tag) |
| Reorder chunks | n/a | fails — chunk index is in the AAD |
| Rewrite the format version | **succeeds** | fails — version is in the AAD |

### 2.2 The AAD

```
projectId \n normalizePath(path) \n 2 \n generation \n chunkIndex
```

Every field is length-unambiguous: the separator cannot appear in a UUID or in a normalised path
(NUL, newline and `..` are rejected by `ProjectManifest`).

`normalizePath` is what makes the two sides agree. Sealing sees `js/app.js`; the WebView sees
`uri.getPath()` → `/js/app.js`. Both reduce to `js/app.js`, or every request would fail its tag
check. **This was verified by test** (`bindsToPath`, `VaultFormatCheck.normalizesPaths`).

### 2.3 Backward compatibility

The version byte was already in the format, so the migration is a read-path branch, not a rewrite:

- `VaultCipher.open` checks for the `RVLT` magic. Absent → `openLegacy` (v1, AAD = projectId).
- `ProjectVault.migrate(projectId, root)` re-seals every v1 file as v2 in place. Idempotent: files
  already carrying the magic are skipped, so a second run is a no-op.
- Nothing is ever written as v1 again.

**Verified by test:** `readsLegacyFormat` builds a real v1 blob and opens it through the v2 entry
point.

---

## 3. Interrupted encryption

### 3.1 The state machine

```
                 ┌──────────────┐
   new tree ────►│  no markers  │──► SEAL
                 └──────┬───────┘
                        │ write .vault.pending
                        ▼
                 ┌──────────────┐
                 │   pending    │
                 └──────┬───────┘
        crash ──────────┤
          │             │ sealTree completes
          ▼             ▼
   ┌─────────────┐  ┌──────────────┐
   │ REFUSE      │  │ pending +    │──► remove pending ──► SKIP_ALREADY_SEALED
   │ INTERRUPTED │  │ sealed       │
   │ → discard   │  └──────────────┘
   └─────────────┘
```

### 3.2 The four requirements

| Requirement | Mechanism |
|---|---|
| Never mistake a partial tree for a valid one | `.vault` is written **only** after every file succeeds; `isSealInterrupted` = pending ∧ ¬sealed |
| Never double-encrypt | Two independent guards: the `.vault` marker short-circuits the tree, and `sealDirectory` skips any file already carrying the `RVLT` magic. The second guard is what makes a **lost marker** harmless |
| Interrupted registration cannot leave a usable-looking project | `store.save()` runs after sealing; a failure returns `Result(false, …)` and the registry is never touched |
| Preserve the previous working version | `removeReplaced` archives to `app/backups/<id>-<timestamp>`, pruned to `MAX_BACKUPS = 5` |

### 3.3 Deterministic recovery

`ProjectSealer.recover(dir)` returns exactly one of four outcomes:

| State | Action | Return |
|---|---|---|
| pending, not sealed | delete the tree | `"discarded"` |
| sealed + stale pending | remove the pending marker | `"cleaned"` |
| sealed, clean | nothing | `"intact"` |
| neither | nothing | `"unsealed"` |

It also removes leftover `.sealing` / `.rotating` temporaries, which a crash during a single-file
write can leave behind. `sealFile` writes to a temp and renames, so a single file can never end up
truncated — which the tree-level marker alone could not guarantee.

---

## 4. Concurrent registration

`ProjectRegistrationService` now holds a single process-wide `REGISTRATION_LOCK` for the whole
sequence: mint id → copy → validate → seal → save.

```java
public static Result register(...) {
    synchronized (REGISTRATION_LOCK) {
        return registerLocked(...);
    }
}
```

**Why one global lock rather than per-draft:** a per-draft lock would still let two different drafts
interleave their Keystore operations, and registration is a user-initiated action that takes at most
a few seconds.

**Duplicate ids:** `store.findById(projectId)` is checked before `store.save`. A UUID collision is
not a realistic threat, but the registry is the authority on identity and must never be silently
rewritten.

**Atomicity:** a caller either sees the project fully registered or not at all — the record is
written last, after the tree is sealed.

---

## 5. Cache security

| Property | Status | Evidence |
|---|---|---|
| Project isolation | **verified** | key is `projectId + '\0' + path`; `isolatesProjects` test |
| Resource isolation | **verified** | NUL separator prevents `a`+`b/c` colliding with `a/b`+`c` |
| Invalidation on update | **implemented** | `ProjectVaultRuntime.onProjectUpdated` → `clearProject` |
| Invalidation on delete | **implemented** | `onProjectDeleted` → `clearProject` **then** `ProjectVault.forget` |
| Invalidation on close | **implemented** | `ProjectActivity.onDestroy` → `onProjectClosed` |
| Key rotation | **implemented** | `ProjectVault.rotate` + `ProjectVaultRuntime.rotateKey` |
| Max memory | **verified** | LRU by bytes; `evictsLeastRecentlyUsed`, `refusesOversizedEntry` |
| Thread safety | **verified** | every method `synchronized`; `LinkedHashMap(accessOrder=true)` |

### 5.1 Key rotation

Generation 0 keeps the original alias (`ronilifeos.project.<id>`) so projects sealed before rotation
still open. Later generations append `.gN`. `rotate()`:

1. writes `.vault.pending` (an interrupted rotation is detectable);
2. re-seals every file from generation N to N+1, streaming, temp + rename;
3. writes `.vault` with `gen=N+1`;
4. deletes the generation-N key.

The old key is deleted **only after** every file is re-sealed, so a failure leaves it available for
a retry. `forget()` now deletes every generation, not just generation 0.

### 5.2 Known limitation: plaintext is not zeroed

Eviction drops the reference but does not wipe the array. The array handed out by `get()` may still
be referenced by an in-flight `WebResourceResponse`; wiping it would corrupt that response. Java
also gives no guarantee that a `byte[]` is cleared before GC. The mitigation is **lifetime**, not
wiping: every lifecycle event clears the project's entries, and the cache is bounded.

---

## 6. Web app compatibility

### 6.1 The fixture

`my-app/` — 8 files, 48 KB:

```
index.html          module script + stylesheet + both images
manifest.json       entryPoint: index.html
css/style.css       dark card layout
js/app.js           imports greet.js, fetch()es data.json, calls the bridge
js/greet.js         export function greet(name)
js/data.json        { "message": "fetch() works …" }
assets/logo.svg     inline gradient SVG
assets/pic.png      480×240 real PNG
```

### 6.2 What each check proves

| Check | Fails when | Symptom |
|---|---|---|
| `index.html` | entry point wrong | blank page |
| `css/style.css` | MIME not `text/css` | unstyled page |
| `js/app.js` | MIME not `application/javascript` | `#status` stays "loading…" |
| `js/greet.js` | relative resolution broken | module error |
| ES6 modules | origin is `file://` | `Failed to load module script` |
| `fetch()` | origin is `file://` or CORS-blocked | `#data` shows `fetch failed: …` |
| SVG + PNG | provider path resolution broken | broken-image icons |
| `NativeRuntime.ui.toast('Loaded')` | bridge not attached | no toast |

### 6.3 Source separation

`ProjectVaultRuntime.decoderFor` is the single gate:

| Source type | Decoder | Why |
|---|---|---|
| `FILE` + `.vault` marker | **installed** | ciphertext needs decryption |
| `FILE` without marker | `null` | legacy / API 21–22 plaintext |
| `SAF_FOLDER` | `null` | live folder, never copied through the vault |
| `REMOTE_URL` | `null` | no local bytes |
| `ASSET` / `ZIP_ASSET` | `null` | APK assets are plaintext |
| `INLINE_HTML` | `null` | `loadDataWithBaseURL`, no provider |

The decision is derived from the filesystem marker, not a stored flag, so it cannot go stale after
a failed seal or a manual restore.

### 6.4 MIME and encoding

`WebViewHost` re-wraps the decoded body with `resource.getMimeType()` and `resource.getEncoding()`.
That is what keeps `application/javascript` intact for ES6 modules and `text/css` intact for
stylesheets. A decoder returning a bare stream without the original MIME would break both.

**Not runtime-verified** — see §7.

---

## 7. Build and test execution

### 7.1 Toolchain

| Tool | Status |
|---|---|
| `java` | present (JRE 17.0.20.1) |
| `javac` | **absent** — installed Temurin JDK 17.0.21 from the network instead |
| Gradle | **absent** — `./gradlew` bootstrapped Gradle 8.11.1 successfully |
| Android SDK | **absent** — `ANDROID_HOME` unset, no SDK directories |
| `adb` / `sdkmanager` / `aapt2` / `d8` | **absent** |

### 7.2 What actually ran

**Compiled — PASS.** 13 files, `javac -Xlint:all`, zero warnings, zero errors:

```
VaultFormat, VaultLimits, CryptoHash, DecryptedResourceCache,
VaultCipher, SealedInputStream,
VaultFormatCheck, VaultCipherCheck, DecryptedResourceCacheCheck,
SealDecision, ProjectManifest, SealDecisionCheck, ProjectManifestCheck
```

**Tests — PASS. 125 checks, 5 suites, 0 failures:**

| Suite | Checks | Result |
|---|---:|---|
| `VaultFormatCheck` | 36 | **PASS** |
| `VaultCipherCheck` | 33 | **PASS** |
| `DecryptedResourceCacheCheck` | 11 | **PASS** |
| `SealDecisionCheck` | 16 | **PASS** |
| `ProjectManifestCheck` | 29 | **PASS** |

### 7.3 Two real bugs the tests caught

| Bug | Symptom | Fix |
|---|---|---|
| `normalizePath("js/")` returned `"js/"` | a trailing slash would have produced a different AAD than the sealing side, 404-ing every directory-style path | strip trailing separators |
| `chunkPlainLength(0, 0, …)` returned `chunkSize` | an empty file would have reported a phantom chunk | guard `chunkCount <= 0` |

Both were found by running the tests, not by reading the code. This is the concrete value of the
JDK install.

### 7.4 What did NOT run

| Task | Status | Reason |
|---|---|---|
| `./gradlew :app:assembleDebug` | **attempted, failed** | no Android SDK — see §7.5 |
| `./gradlew test` | **NOT RUN** | Android modules need the SDK to configure |
| Android instrumentation tests | **NOT RUN** | no device, no emulator, no `adb` |
| WebView integration test | **NOT RUN** | no device |
| Keystore AES/GCM round-trip | **NOT RUN** | no Android runtime |
| `my-app` in a real WebView | **NOT RUN** | no device |

### 7.5 Gradle build attempt

`./gradlew --version` → **Gradle 8.11.1, EXIT=0**. The wrapper bootstrapped from the network.

`./gradlew :app:assembleDebug` → **BUILD FAILED in 38s**, verbatim:

```
* What went wrong:
Could not determine the dependencies of task ':app:compileDebugJavaWithJavac'.
> SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable
  or by setting the sdk.dir path in your project's local properties file at
  '.../RONiLiFEOS/local.properties'.
```

This is an environment limit, not a code defect: Gradle 8.11.1 bootstrapped and configured the
project, then stopped at the first task that needs the SDK. **No claim is made that the Android
modules compile.**

### 7.6 What the JVM tests do and do not cover

`VaultCipherCheck` exercises the **format layer** with a software AES-256 key: chunking, AAD
binding, tag verification, truncation, chunk reordering, cross-resource swap, legacy read, streaming
equivalence, and a 40-chunk resource.

It does **not** cover: Android Keystore key generation, `KeyGenParameterSpec`, the WebView
interceptor, `WebResourceResponse` MIME handling, or the `my-app` fixture in a real browser engine.
Those need a device.

---

## 8. Modified file list

### New — production (5)

| File | Purpose |
|---|---|
| `native-security-engine/.../crypto/VaultFormat.java` | byte layout, AAD construction, path normalisation |
| `native-security-engine/.../crypto/VaultLimits.java` | configurable memory ceilings |
| `native-security-engine/.../crypto/VaultCipher.java` | format layer, Android-free, JVM-testable |
| `native-security-engine/.../crypto/SealedInputStream.java` | chunk-verified decrypting stream |
| `app/.../project/ProjectVaultMigration.java` | *(not created — `ProjectVault.migrate` covers it)* |

### New — tests (2)

| File | Checks |
|---|---:|
| `native-security-engine/src/test/.../VaultFormatCheck.java` | 36 |
| `native-security-engine/src/test/.../VaultCipherCheck.java` | 33 |

### Rewritten (6)

| File | Change |
|---|---|
| `native-security-engine/.../crypto/ProjectVault.java` | slimmed to key management + tree ops; delegates crypto to `VaultCipher`; generation-aware aliases; `migrate`, `rotate`, `isSealedFile`, `currentGeneration`, `writeMarker`; `fingerprint` now hashes raw bytes |
| `native-security-engine/.../crypto/DecryptedResourceCache.java` | per-entry ceiling, hit/miss/eviction counters, `contains`, `describe` |
| `native-security-engine/.../crypto/SealedInputStream.java` | takes a `SecretKey` instead of reaching into `ProjectVault` |
| `native-runtime-ui/.../web/ResourceDecoder.java` | added `decodeStream` (default buffers), `readAll`, `closeQuietly` |
| `app/.../project/VaultResourceDecoder.java` | streaming decode, three size regimes, generation-aware |
| `app/.../project/ProjectVaultRuntime.java` | passes the generation from the marker; `rotateKey` |

### Patched (3)

| File | Change |
|---|---|
| `native-runtime-ui/.../web/WebViewHost.java` | uses `decodeStream`; removed the buffering `readAll` |
| `app/.../project/ProjectSealer.java` | `recover()` with four deterministic outcomes; removes `.sealing`/`.rotating` temporaries |
| `app/.../project/ProjectRegistrationService.java` | `REGISTRATION_LOCK`; `registerLocked` / `registerBundledZipLocked`; duplicate-id guard |

**Total this round: 16 files (7 new, 6 rewritten, 3 patched).**

---

## 9. Known limitations

| # | Severity | Limitation |
|---|---|---|
| L1 | **High** | **Nothing Android-side is compiled or run.** No SDK, no device. The Gradle build fails at configuration. |
| L2 | **High** | Keystore AES/GCM is unverified. The format is tested with a software key; the Keystore path is not. |
| L3 | Medium | `chunkSize` is validated for consistency but is **not** in the AAD. For a single-chunk file, rewriting it changes nothing and there is nothing to detect. For multi-chunk files it breaks decryption. |
| L4 | Medium | Plaintext is not zeroed on cache eviction (see §5.2). |
| L5 | Medium | An interrupted **rotation** leaves the tree split across generations. The pending marker makes it detectable, but recovery is "re-import", not "roll back". |
| L6 | Medium | `ProjectManifest` extracts `entryPoint` with a hand-rolled scan, not a JSON parser. A nested object containing `"entryPoint"` could be picked up. |
| L7 | Low | Drafts (`importZip`) are plaintext on disk until registered. |
| L8 | Low | API 21–22 stores projects unsealed (`ProjectVault.isAvailable()` is API ≥ 23). |
| L9 | Low | No UI to restore a backup from `app/backups/`. The data is preserved; the restore path is manual. |
| L10 | Low | `ProjectVault.forget` swallows Keystore failures and only logs. |

---

## 10. What still needs a device

1. `./gradlew :app:assembleDebug` on a machine with the Android SDK.
2. `./gradlew test` — runs the five check classes under the Android test task.
3. Install, import `my-app` as a ZIP, confirm `app/projects/<id>/.vault` exists and `index.html` is
   not readable as text.
4. Open the project: `#status` shows `Hello, world!`, `#data` shows the fetch message, both images
   render, a toast says `Loaded`.
5. Corrupt one byte of a sealed file → reopen → that resource 404s, the rest of the page loads.
6. Delete the project → `keytool -list` shows no `ronilifeos.project.<id>` alias.
7. Kill the app mid-import → relaunch → the project is absent, not half-registered.
8. Rotate a key → reopen → the project still loads, and the old alias is gone.
