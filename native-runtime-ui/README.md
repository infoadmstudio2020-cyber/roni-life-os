# native-runtime-ui

Native UI shell, WebView host and JavaScript bridge handlers for NativeRuntime applications.

## Bridge namespaces

| Namespace | Handler | Highlights |
| --- | --- | --- |
| `ui.*` | `NativeUICommandHandler` | Toast, alert, confirm, loading, snackbar, action sheet, bottom sheet, toolbar, drawer, tabs, theme, system UI |
| `web.*` | `NativeWebCommandHandler` | Load, reload, back/forward, evaluate, zoom, cookies, cache, source switching |
| `device.*` | `NativeDeviceCommandHandler` | Orientation, keep-screen-on, brightness, app settings, file open, logging |
| `storage.*` | `NativeStorageCommandHandler` | `get`/`set`/`remove`/`clear`/`keys`/`has`/`getAll` over the storage engine |

## Storage

`storage.*` is an adapter over `native-storage-engine`:

- default tier: `EncryptedStore` (AES-GCM, key from `AndroidKeyStore`)
- opt-in plaintext tier: `new NativeStorageCommandHandler(host, false)` → `KeyValueStore`
- values keep their type across the bridge (`b:`/`i:`/`l:`/`f:`/`d:`/`s:` prefixes)
- the legacy `native_runtime_web_storage` preferences are copied once and cleared, guarded by a
  `__migrated_v1` marker

## Web permissions

```java
WebHostConfig config = WebHostConfig.builder()
        .permissionHandler(new SecurityWebPermissionHandler())
        .cacheMode(WebHostConfig.CACHE_ELSE_NETWORK)
        .build();
```

`SecurityWebPermissionHandler` grants camera/microphone/geolocation only when the matching
Android permission is already `GRANTED` in `native-security-engine`. With no handler configured,
`WebViewHost` denies every request — the historical behaviour.

## Bridge origin

`WebViewHost` implements `OriginProvider` for the bridge: it reports the
`scheme://host[:port]` origin of the current page. `about:blank`, `data:` and `file:` URLs
report `null`, which the bridge treats as untrusted.

## Zoom

- `web.setZoom` / `web.getZoom` — page zoom (25–400 %), applied through CSS `zoom`
- `web.setTextZoom` / `web.getTextZoom` — text-only zoom via `WebSettings`

## Lifecycle

`NativeRuntimeUI.init` is idempotent and registers bridge routes exactly once.
`release()` clears handlers and state but keeps the singleton, so a later `init` rebuilds the
same instance.
