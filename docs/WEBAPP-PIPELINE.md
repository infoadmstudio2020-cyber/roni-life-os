# Secure Web App Pipeline — Technical Report & API Reference

**Scope:** store → register → read → validate → decrypt → serve → run, using the existing
RONiLiFEOS libraries. No new storage engine, no new resource provider, no new bridge.

---

## 1. The pipeline

```
my-app/  ──import──►  app/projects/<id>/          (ciphertext, .vault marker)
                          │
                          │  ProjectStore.save()  ← registry entry
                          ▼
                    ProjectActivity
                          │  WebSourceFactory.from(project)
                          ▼
              WebSource{ type=FILE, virtualHost=<id>.runtime.local }
                          │  WebSourceResolver.resolve()
                          ▼
        https://<id>.runtime.local/index.html  +  FileResourceProvider
                          │
                          │  WebView request
                          ▼
        WebViewHost.shouldInterceptRequest()
             ├─ host must equal activeHost        (origin gate)
             ├─ WebPaths.isSafe(path)             (path gate)
             ├─ provider.open(path)               (ciphertext)
             └─ VaultResourceDecoder.decodeStream (AES-GCM, per-chunk tag)
                          ▼
                 WebResourceResponse(mime, encoding, body)
                          ▼
              ES6 modules · fetch() · SVG · NativeRuntime.ui.toast()
```

---

## 2. Per-project virtual origin — the change that matters

### 2.1 What was wrong

Every local source was served from one host:

```java
public static final String VIRTUAL_HOST = "appassets.androidplatform.net";
```

The resource provider is per-`WebViewHost`, so project A could not *fetch* project B's files. But
the **browser** saw one origin, and an origin owns `localStorage`, `sessionStorage`, `IndexedDB`,
the cookie jar and the service-worker scope. Project A's JavaScript could read everything project B
persisted. Native isolation was real; browser isolation was not.

### 2.2 What it is now

```
https://my-app-1a2b3c4d.runtime.local/index.html
```

Derived by `WebPaths.virtualHostFor(projectId)`:

| Step | Rule |
|---|---|
| slug | lowercase, `[a-z0-9]` kept, everything else → `-`, collapsed, trimmed, ≤ 40 chars |
| hash | first 4 bytes of SHA-256(id), 8 hex chars |
| host | `<slug>-<hash>.runtime.local` |

**Derived from the id, never the name.** A rename must not move the origin, or the project would
silently lose its stored data. The hash makes the host collision-free even when two ids slugify to
the same text (`"my app"` and `"my-app"` both slug to `my-app` but get different hashes).

### 2.3 Where the host is enforced

| Layer | Check |
|---|---|
| `WebSourceFactory` | sets the host on every source it builds |
| `WebSourceResolver` | builds the URL from that host; reports it on `ResolvedSource` |
| `WebViewHost.loadSource` | stores it in `activeHost`, trusts that origin with the bridge |
| `WebViewHost.shouldInterceptRequest` | `host.equals(uri.getHost())` — anything else returns `null` (not intercepted) |
| `WebViewHost.handleUrl` | the active host is internal, never opened externally |

A request to another project's host is not intercepted at all, so the WebView tries to resolve it
over the network and fails — it never reaches a provider.

---

## 3. Path validation

`WebPaths.isSafe(path)` runs **once**, in the interceptor, before any provider sees the path:

| Rejected | Why |
|---|---|
| any `..` segment | traversal out of the project root |
| `\0` | truncation tricks in native file APIs |
| `%2e`, `%2f`, `%5c` | the WebView hands over a decoded path, so a surviving escape means double-encoding |

Centralising it means `FileResourceProvider`, `AssetResourceProvider`, `ZipResourceProvider` and
`SafResourceProvider` all inherit the same rule instead of each re-implementing it. Their own
checks remain as defence in depth.

---

## 4. Source matrix

| Source | `WebSourceType` | Provider | Decoder | Origin |
|---|---|---|---|---|
| Internal encrypted project | `FILE` | `FileResourceProvider` | **`VaultResourceDecoder`** | per-project |
| Internal legacy (unsealed) | `FILE` | `FileResourceProvider` | none | per-project |
| ZIP imported | `ZIP` | `ZipResourceProvider` | none | per-project |
| APK asset | `ASSET` | `AssetResourceProvider` | none | per-project |
| SAF folder | `CONTENT_URI` (tree) | `SafResourceProvider` | none | per-project |
| Remote HTTPS | `REMOTE_URL` | none | none | the remote origin |
| Inline HTML | `INLINE_HTML` | `AssetResourceProvider` | none | per-project |

**SAF is served in place.** `SafResourceProvider` reads through the persisted `content://` tree URI;
nothing is copied into app storage. The decoder is `null` because the bytes are already plaintext —
running them through AES/GCM would fail the tag check and 404 every request.

---

## 5. ES6 module support

| Requirement | Mechanism |
|---|---|
| `import` / `export` | `application/javascript` from `MimeTypes` for `.js` and `.mjs` |
| Relative imports | provider resolves `./greet.js` against the request path |
| Nested modules | same resolution, any depth |
| Dynamic `import()` | works because the origin is `https://`, not `file://` |
| `fetch()` | same-origin request, intercepted like any other resource |
| CSS relative paths | `text/css` + same resolution |
| SVG / PNG | `image/svg+xml`, `image/png` |
| Same-origin policy | one host per project, so modules and `fetch()` are same-origin by construction |

The MIME type is preserved through decryption: `WebViewHost` re-wraps the decoded body with
`resource.getMimeType()` and `resource.getEncoding()`. A decoder that returned a bare stream would
break both ES6 modules and stylesheets.

---

## 6. Bridge security

```javascript
if (window.NativeRuntime?.ui) {
    window.NativeRuntime.ui.toast('Loaded');
}
```

| Gate | Implementation |
|---|---|
| Origin | `WebBridge` checks `originProvider.getCurrentOrigin()` against `BridgeConfig.allowedOrigins` before PING, EVENT or REQUEST dispatch |
| Per-project trust | `WebViewHost.trustBridgeOrigin(host)` calls `bridge.allowOrigin("https://" + host)` when a source loads — only the origin actually loaded, never `*` |
| Message shape | `BridgeSecurityValidator` requires a non-empty id and action |
| Size | `BridgeConfig.maxMessageBytes`, default 1 MiB |
| Interface name | `BridgeConfig.sanitizeInterfaceName` rejects anything that is not a JS identifier |

`BridgeConfig.addAllowedOrigin` is copy-on-write on a `volatile` set, so the bridge thread can read
while the UI thread adds an origin.

---

## 7. Restore

`ProjectRestoreService` turns an archived update back into a live project.

| Method | Behaviour |
|---|---|
| `listBackups(context, projectId)` | archived versions, newest first |
| `readBackup(File)` | parses `project.json` → `Project`, or `null` |
| `restore(context, store, File)` | moves the tree back, re-registers, clears cached plaintext |

`ProjectRegistrationService.removeReplaced` now writes `project.json` (the `Project.encode()`
record) beside the archived files, so a backup is self-contained.

**Restore refuses to overwrite a live project.** A restore must never silently replace the version
the user is running.

**Restore does not work for a deleted project.** `ProjectDeletionService` destroys the Keystore key
on purpose — a surviving alias would let a later project that reuses the id decrypt the old
ciphertext. Deletion is permanent; the way back is to re-import the source.

---

## 8. API reference (new and changed)

### `WebPaths` — new, pure Java

```java
static String  normalize(String path)          // separators, no percent-decoding
static boolean isSafe(String path)             // traversal / NUL / encoded-escape gate
static String  virtualHostFor(String projectId) // <slug>-<hash8>.runtime.local
static String  originOf(String host)           // https://<host>
static String  virtualUrl(String host, String path)
static final String VIRTUAL_SUFFIX = ".runtime.local";
static final String LEGACY_HOST    = "appassets.androidplatform.net";
```

### `WebSource` — additive

```java
String getVirtualHost()                        // "" = use the legacy shared host
WebSource withVirtualHost(String host)
Builder  virtualHost(String host)
```

### `WebSourceResolver` — changed

```java
static String effectiveHost(WebSource source)
static String virtualUrl(String host, String path)
static String virtualUrl(String path)          // legacy host
ResolvedSource.getVirtualHost()                // "" for remote/content sources
```

### `WebViewHost` — internal

```java
private volatile String activeHost             // the host of the loaded source
private void trustBridgeOrigin(String host)
```

### `BridgeConfig` / `WebBridge` — additive

```java
void BridgeConfig.addAllowedOrigin(String origin)   // copy-on-write
void WebBridge.allowOrigin(String origin)
```

### `ProjectRestoreService` — new

```java
static List<File> listBackups(Context, String projectId)
static Project    readBackup(File backup)
static boolean    restore(Context, ProjectStore, File backup)
static final String BACKUP_MANIFEST = "project.json";
```

---

## 9. Test results

Seven suites, **333 checks, 0 failures**, compiled with `javac -Xlint:all` (no warnings):

| Suite | Checks | Covers |
|---|---:|---|
| `VaultFormatCheck` | 36 | byte layout, AAD, path normalisation |
| `VaultCipherCheck` | 33 | chunking, tag verification, tampering, legacy read, streaming |
| `DecryptedResourceCacheCheck` | 11 | LRU, isolation, entry ceiling |
| `SealDecisionCheck` | 16 | seal / skip / refuse |
| `ProjectManifestCheck` | 29 | manifest parsing, path rejection |
| **`WebPathsCheck`** | **193** | traversal, encoded traversal, host derivation, DNS safety |
| **`WebSourceCheck`** | **15** | host survives every builder round-trip |

`WebPathsCheck` and `WebSourceCheck` are new this round. `WebPaths` and `WebSource` were made
Android-free specifically so these rules could be tested on a JVM.

---

## 10. Limitations

| # | Severity | Limitation |
|---|---|---|
| L1 | **High** | Nothing Android-side is compiled or run — no SDK, no device. `./gradlew :app:assembleDebug` fails with `SDK location not found`. |
| L2 | **High** | The WebView interceptor, bridge injection and `my-app` in a real browser engine are **not** runtime-verified. |
| L3 | Medium | Changing a project's origin (first run after this change) moves its `localStorage`/`IndexedDB` to a new origin. Existing projects lose browser-side data once. |
| L4 | Medium | `ProjectRestoreService` has no UI. The API exists; the screen does not. |
| L5 | Medium | Restore is impossible after a delete, by design (key destroyed). |
| L6 | Low | `WebPaths.isSafe` rejects any path containing `%2e`, so a legitimate file literally named `a%2eb.js` cannot be served. |
| L7 | Low | The bridge has no per-action permission model — origin is the only gate. |
| L8 | Low | `ProjectManifest` extracts `entryPoint` with a hand-rolled scan, not a JSON parser. |

---

## 11. What still needs a device

1. `./gradlew :app:assembleDebug` on a machine with the Android SDK.
2. Import `my-app` as a ZIP → confirm `app/projects/<id>/.vault` exists and `index.html` is not
   readable as text.
3. Open it → `#status` shows `Hello, world!`, `#data` shows the fetch message, both images render,
   a toast says `Loaded`.
4. Check the address bar origin is `https://<id>.runtime.local`, not the shared host.
5. Open two projects → confirm `localStorage` in one does not see the other's keys.
6. Request `/../etc/passwd` → 404.
7. Update a project → confirm `app/backups/<id>-<ts>/project.json` exists → restore it.
8. Delete a project → `keytool -list` shows no `ronilifeos.project.<id>` alias.
