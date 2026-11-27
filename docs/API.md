> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](web-sdk/README.md) for the verified documentation.

---

# NativeRuntime SDK — API Reference

Public surface of the `native-runtime-sdk` module (`com.roni.library.sdk`).

> This document replaces an earlier version that described `NativeRuntimeSDKConfig` — a class
> that does not exist. The real configuration type is `RuntimeConfiguration`, built through
> `RuntimeConfiguration.builder()` or the `RuntimeBuilder` convenience wrapper.

---

## Contents

1. [`NativeRuntime`](#nativeruntime)
2. [`RuntimeConfiguration`](#runtimeconfiguration)
3. [`RuntimeBuilder`](#runtimebuilder)
4. [`NativeRuntimeResult`](#nativeruntimeresult)
5. [`RuntimeState`](#runtimestate)
6. [`NativeRuntimeUI`](#nativeruntimeui)
7. [`NativeRuntimeUIConfig`](#nativeruntimeuiconfig)
8. [`WebSource`](#websource)
9. [Typical setup](#typical-setup)

---

## `NativeRuntime`

`public final class NativeRuntime` — the master facade. One instance per process.

### `static NativeRuntime getInstance()`

Returns the process-wide singleton. Safe to call before `init`.

### `synchronized NativeRuntimeResult<Boolean> init(Context context)`

Initialises with default configuration. Equivalent to `init(context, RuntimeConfiguration.builder().build())`.

### `synchronized NativeRuntimeResult<Boolean> init(Context context, RuntimeConfiguration config)`

Initialises the runtime. Must be called before any other method.

```java
NativeRuntimeResult<Boolean> result =
        NativeRuntime.getInstance().init(context, config);
if (!result.isSuccess()) {
    Log.e(TAG, "init failed: " + result.getErrorMessage());
}
```

### Session lifecycle

| Method | Effect |
| --- | --- |
| `startSession()` | Opens a session; state → `READY` |
| `pauseSession()` | State → `SUSPENDED` |
| `resumeSession()` | State → `READY` |
| `stopSession()` | Closes the session |

### Accessors

| Method | Returns |
| --- | --- |
| `getUI()` | `NativeRuntimeUI` |
| `getUIHost()` | `NativeUIHost` |
| `getConfiguration()` | `RuntimeConfiguration` |
| `getSession()` | `RuntimeSession` |
| `getState()` | `RuntimeState` |
| `getBootstrapReport()` | `NativeRuntimeBootstrap.BootstrapReport` |
| `isInitialized()` | `boolean` |
| `isHealthy()` | `boolean` |

> ⚠️ `isHealthy()` currently always returns `true` — the bootstrap records success for every
> step without running it. See [audit C4](AUDIT_AND_UPDATE_REPORT.md#c4-nativeruntimebootstrap-reports-success-without-initialising-anything).

### `synchronized void release()`

Releases all resources. After this, `getInstance()` returns a fresh instance.

### Observers

```java
public void addObserver(LifecycleObserver observer)
public void removeObserver(LifecycleObserver observer)
```

### `NativeApiResponse executeApiRequest(NativeApiRequest request)`

> ⚠️ **Stub.** Returns a synthetic success map and never dispatches to the router.
> See [audit C3](AUDIT_AND_UPDATE_REPORT.md#c3-nativeruntimeexecuteapirequest-is-a-stub).
> Use `NativeRouter.getInstance().dispatchSync(request)` instead.

### Telemetry

```java
public void trackEvent(String eventName, Map<String, Object> params)
public Map<String, Object> getTelemetryEvents()
```

---

## `RuntimeConfiguration`

`public final class RuntimeConfiguration implements Serializable` — immutable.

### `static Builder builder()`

### Builder methods

| Method | Default | Description |
| --- | --- | --- |
| `appId(String)` | — | Application identifier |
| `environment(String)` | — | `"dev"`, `"staging"`, `"prod"` |
| `debugEnabled(boolean)` | `false` | Verbose logging |
| `logLevel(String)` | — | `"VERBOSE"`, `"DEBUG"`, `"INFO"`, `"WARN"`, `"ERROR"` |
| `storageEncryptionEnabled(boolean)` | — | Request encrypted storage |
| `networkTimeoutMs(int)` | — | HTTP timeout |
| `maxNetworkRetries(int)` | — | Retry count |
| `telemetryEnabled(boolean)` | — | Collect events |
| `addCustomProperty(String, String)` | — | Arbitrary key/value |
| `build()` | — | Returns `RuntimeConfiguration` |

### Getters

`getAppId()`, `getEnvironment()`, `isDebugEnabled()`, `getLogLevel()`,
`isStorageEncryptionEnabled()`, `getNetworkTimeoutMs()`, `getMaxNetworkRetries()`,
`isTelemetryEnabled()`, `getCustomProperties()`.

> ⚠️ `storageEncryptionEnabled` is recorded but not honoured by the web `storage.*` bridge,
> which writes plaintext `SharedPreferences`. See
> [audit H5](AUDIT_AND_UPDATE_REPORT.md#h5-storage-is-plaintext-not-encrypted).

---

## `RuntimeBuilder`

`public final class RuntimeBuilder` — fluent wrapper that builds a `RuntimeConfiguration` and
initialises in one chain.

```java
NativeRuntime runtime = RuntimeBuilder.with(context)
        .appId("com.example.app")
        .environment("prod")
        .debugEnabled(BuildConfig.DEBUG)
        .logLevel("INFO")
        .networkTimeoutMs(15000)
        .maxNetworkRetries(2)
        .telemetryEnabled(true)
        .initialize();
```

| Method | Returns |
| --- | --- |
| `static RuntimeBuilder with(Context)` | `RuntimeBuilder` |
| `appId(String)` | `RuntimeBuilder` |
| `environment(String)` | `RuntimeBuilder` |
| `debugEnabled(boolean)` | `RuntimeBuilder` |
| `logLevel(String)` | `RuntimeBuilder` |
| `storageEncryptionEnabled(boolean)` | `RuntimeBuilder` |
| `networkTimeoutMs(int)` | `RuntimeBuilder` |
| `maxNetworkRetries(int)` | `RuntimeBuilder` |
| `telemetryEnabled(boolean)` | `RuntimeBuilder` |
| `addCustomProperty(String, String)` | `RuntimeBuilder` |
| `initialize()` | `NativeRuntime` |

---

## `NativeRuntimeResult`

`public final class NativeRuntimeResult<T> implements Serializable`

| Member | Description |
| --- | --- |
| `static <T> success(T data)` | Success factory |
| `static <T> failure(int errorCode, String errorMessage)` | Failure factory |
| `static <T> failure(Throwable t)` | Failure from an exception |
| `isSuccess()` | `boolean` |
| `getData()` | `T` or `null` |
| `getErrorCode()` | `int` |
| `getErrorMessage()` | `String` or `null` |
| `getTimestamp()` | `long` |

---

## `RuntimeState`

`public enum RuntimeState`

```
UNINITIALIZED → INITIALIZING → READY → SUSPENDED → TERMINATED
                                  ↘ ERROR
```

Helpers: `isTerminated()`, `isReady()`.

---

## Storage

`NativeStorageEngine` — Layer 2 storage. Initialise before use; every accessor throws
`IllegalStateException` otherwise.

| Method | Description |
| --- | --- |
| `StorageEngine.init(Context, NativeStorageEngineConfig)` | Initialise |
| `StorageEngine.isInitialized()` | `boolean` |
| `getPreferences(String)` | `EncryptedPreferences` (AES-GCM) |
| `getKeyValueStore()` | `KeyValueStore` (AES-GCM when a key is available) |
| `getDatabase()` / `getDatabaseHelper()` | Document database / SQLite helper |
| `getDiskCache()` | `LruDiskCache` |
| `getEncryptedStore()` | `EncryptedStore` |
| `getService()` / `getCommandHandler()` | Service / `storage.*` handler |
| `release()` | Idempotent; the singleton is retained and re-initialisable |

`NativeStorageEngineConfig.Builder`: `storageDirectory(File)`, `encryptionKeyAlias(String)`
(default `native_storage_master_key`), `encryptionPassword(String)` (fallback only — there is
no default passphrase), `debugEnabled(boolean)`, `tag(String)`, `timeout(int)`.

`KeyValueStore(String, File)` is plaintext; `KeyValueStore(String, File, SecretKey)` is
AES-GCM. `isEncrypted()` and `flush()` report mode and force a write.

`EncryptedPreferences(String, File, String)` and `EncryptedPreferences(String, File, SecretKey)`
both throw `StorageException("MISSING_KEY", …)` when constructed without key material.

`StorageCommandHandler.SUPPORTED_ACTIONS` — `get`, `set`, `remove`, `clear`, `keys`, `has`,
`getAll`, accepted as `storage.get`, `/storage/get` or `get`.

---

## `WebBridge`

`public final class WebBridge` — Layer 3 transport between JavaScript and `NativeRouter`.

| Method | Description |
| --- | --- |
| `static getInstance()` | Singleton; returns a fresh instance after `release()` |
| `static init(BridgeConfig)` | Idempotent; the first interface name wins |
| `static isInitialized()` | `boolean` |
| `getConfig()` | `BridgeConfig` |
| `getRouter()` / `getEventBus()` / `getSerializer()` | Collaborators |
| `getJavascriptInterface()` | `NativeJavascriptInterface` |
| `setWebEvaluator(Consumer<String>)` / `clearWebEvaluator()` | Bind the WebView evaluator |
| `setOriginProvider(OriginProvider)` / `clearOriginProvider()` | Supply the current page origin |
| `isOriginValidationEnabled()` | `true` when required **and** a provider is attached |
| `getBootstrapScript()` | JS client injection script |
| `emitToWeb(String, Map)` | Native → web event |
| `handleWebMessage(String)` | Web → native entry point |
| `reconfigure(BridgeConfig)` | Update runtime settings; never changes `interfaceName` |
| `isInitialized()` | `boolean` |
| `getPendingRequestCount()` | In-flight requests |
| `release()` | Reset state (singleton owned by `NativeWebBridgeSDK`) |

`BridgeConfig.Builder` additions: `requireOriginValidation(boolean)` (default `true`),
`maxMessageBytes(int)` (default `1_048_576`). The default interface name is
`NativeRuntimeBridge` and the default allow-list is `https://appassets.androidplatform.net`.

---

## `NativeRuntimeUI`

`public class NativeRuntimeUI` — the declarative shell.

| Method | Description |
| --- | --- |
| `static getInstance()` | Singleton |
| `synchronized init(Context, NativeRuntimeUIConfig)` | Initialise the shell |
| `synchronized attachActivity(Activity, NativeRuntimeUIConfig)` | Attach an Activity, returns the root `View` |
| `synchronized detachActivity(Activity)` | Detach |
| `isInitialized()` | `boolean` |
| `getConfig()` | `NativeRuntimeUIConfig` |
| `getUiHost()` | `NativeUIHost` |
| `getCommandHandler()` | `NativeUICommandHandler` |
| `getWebBridge()` | `WebBridge` |
| `getToast()` | `NativeToast` |
| `getLoadingIndicator()` | `LoadingIndicator` |
| `getBottomSheet()` | `CustomBottomSheet` |
| `getActionSheet()` | `NativeActionSheet` |
| `synchronized release()` | Tear down |

Contract accessors: `getRegistry()`, `getConfigurationManager()`, `getCompositionManager()`,
`getVisibilityManager()`, `getStateManager()`, `getEventDispatcher()`, `getRenderer()`.

---

## `NativeRuntimeUIConfig`

`public class NativeRuntimeUIConfig implements Serializable`

### `static Builder builder()` / `static defaultConfiguration()`

### Builder methods

**General**

| Method | Description |
| --- | --- |
| `debugEnabled(boolean)` | Verbose UI logging |
| `tag(String)` | Log tag |
| `timeout(int)` | Operation timeout |
| `animationDurationMs(int)` | Animation length |
| `bottomSheetDismissible(boolean)` | Allow swipe-to-dismiss |
| `autoDismissToasts(boolean)` | Auto-hide toasts |

**Components**

| Method | Description |
| --- | --- |
| `enable(ComponentType)` | Enable one component |
| `enableComponent(ComponentType)` | Same, explicit name |
| `disableComponent(ComponentType)` | Disable one component |
| `enableGroup(ComponentType.Group)` | Enable a group |
| `disableGroup(ComponentType.Group)` | Disable a group |

**Toolbar**

`enableToolbar(boolean)`, `toolbarTitle(String)`, `toolbarSubtitle(String)`

**Drawer**

`enableDrawer(boolean)`, `drawerHeaderTitle(String)`, `drawerHeaderSubtitle(String)`

**Bottom navigation**

`enableBottomNav(boolean)`

**Web content**

| Method | Description |
| --- | --- |
| `enableWebView(boolean)` | Enable the WebView component |
| `webViewInitialUrl(String)` | Load a URL |
| `webApp(WebSource)` | Full-screen web app |
| `webAppInShell(WebSource)` | Web app inside the native shell |
| `fullScreenWeb(boolean)` | Full-screen mode |
| `webHostConfig(WebHostConfig)` | WebView tuning |

**FAB / system UI**

`enableFab(boolean)`, `enableSystemUI(boolean)`, `statusBarColor(int)`, `lightStatusBar(boolean)`,
`navBarColor(int)`, `lightNavBar(boolean)`, `fullscreen(boolean)`, `immersive(boolean)`,
`edgeToEdge(boolean)`

**Theme**

`themeMode(String)` (`"SYSTEM"`, `"LIGHT"`, `"DARK"`), `primaryColor(int)`, `backgroundColor(int)`

**Custom content**

`contentLayoutResId(int)`, `customContentView(View)`

**Advanced**

`statusBar(SystemUIBuilders.StatusBarConfig)`, `navigationBar(SystemUIBuilders.NavigationBarConfig)`,
`immersiveMode(SystemUIBuilders.ImmersiveModeConfig)`

### Example

```java
@Override
protected NativeRuntimeUIConfig onConfigureUI(@NonNull NativeRuntimeUIConfig.Builder builder) {
    return builder
            .enable(SystemUI.STATUS_BAR)
            .enable(SystemUI.EDGE_TO_EDGE)
            .enable(AppBar.TOOLBAR)
            .toolbarTitle("My App")
            .enable(Navigation.BOTTOM_NAVIGATION)
            .enable(Overlay.FLOATING_ACTION_BUTTON)
            .enable(Content.WEB_VIEW)
            .enable(Feedback.TOAST)
            .enable(Feedback.SNACKBAR)
            .webAppInShell(WebSource.asset("index.html"))
            .build();
}
```

---

## `WebSource`

`com.roni.library.ui.web.WebSource` — describes where the web app comes from.

| Factory | Loads from |
| --- | --- |
| `WebSource.asset(String path)` | `assets/` |
| `WebSource.zipAsset(String path)` | ZIP inside `assets/` |
| `WebSource.zipFile(String path)` | ZIP on the device |
| `WebSource.remote(String url)` | Absolute URL |
| `WebSource.file(String path)` | Absolute file path |
| `WebSource.inlineHtml(String html)` | HTML string |
| `WebSource.contentUri(String uri)` | SAF / provider URI |

`zipAsset(...)` returns a builder with `withEntryPoint(String)`.

Local sources are served from `https://appassets.androidplatform.net/`, not `file://`.
See [`web-developer-guide/08_PACKAGING_AND_SOURCES.md`](web-developer-guide/08_PACKAGING_AND_SOURCES.md).

---

## Typical setup

```java
public class MainActivity extends NativeRuntimeActivity {

    @Override
    protected NativeRuntimeUIConfig onConfigureUI(@NonNull NativeRuntimeUIConfig.Builder builder) {
        return builder
                .enable(SystemUI.STATUS_BAR)
                .enable(AppBar.TOOLBAR)
                .enable(Content.WEB_VIEW)
                .webAppInShell(WebSource.asset("index.html"))
                .build();
    }
}
```

`NativeRuntimeActivity` initialises the runtime, attaches the shell and wires the bridge for you.
For manual control use `RuntimeBuilder.with(context).initialize()` followed by
`NativeRuntimeUI.getInstance().attachActivity(activity, config)`.

---

## `NativeRuntimeEngine`

```java
NativeRuntimeEngine engine = NativeRuntimeEngine.getInstance();
NativeRuntimeEngineResult<Boolean> result =
        engine.init(context, NativeRuntimeEngineConfig.defaultConfiguration());
engine.getState();      // RuntimeState.READY
engine.getService();    // NativeRuntimeService
engine.release();       // state -> UNINITIALIZED, singleton retained
```

`init` builds the service, starts the loopback server and registers `/runtime` with
`NativeRouter`. `release` unregisters the route and resets state. Accessors other than
`getService()`/`getCommandHandler()` throw `IllegalStateException` before `init`.

### `LocalWebServer`

```java
LocalWebServer server = new LocalWebServer(0, resolver); // 0 = ephemeral port
server.start();
server.getBaseUrl();   // http://127.0.0.1:<actual port>
server.getPort();      // real bound port
server.stop();
```

Binds the loopback interface only. `GET` and `HEAD` are served; every other method returns
`405`. If the configured port is taken, an ephemeral port is bound and reported by `getPort()`.

### `WebViewContainer`

```java
WebViewContainer real = new WebViewContainer("main", webView); // collects vitals
WebViewContainer headless = new WebViewContainer("worker");    // no metrics
headless.evaluateJavascript("1+1", cb); // {"status":"unavailable","reason":"headless"}
```

### `WebVitalsCollector`

`getMetrics()` returns `fpMs`, `fcpMs`, `lcpMs`, `domReadyMs`, `pageLoadMs` and
`navigationDurationMs`. A metric that was never measured is `-1`
(`WebVitalsCollector.UNRECORDED`), never `0`.

## `NativeNetworkEngine`

```java
NativeNetworkEngine engine = NativeNetworkEngine.getInstance();
engine.init(context, NativeNetworkEngineConfig.builder()
        .connectTimeout(10_000)
        .readTimeout(20_000)
        .maxRetries(2)
        .build());

WebSocketClient ws = engine.createWebSocket("wss://example.com/stream", listener);
ws.connect();
ws.sendText("hello");
ws.close();
```

`WebSocketClient` implements RFC 6455 over `Socket`/`SSLSocket`. `NetworkMonitor` reports
`NONE`/`UNKNOWN` until bound to a `Context`. `SslPinningManager.verify(host, pin)` fails closed.

## Web permissions

```java
WebHostConfig config = WebHostConfig.builder()
        .permissionHandler(new SecurityWebPermissionHandler())
        .cacheMode(WebHostConfig.CACHE_ELSE_NETWORK)
        .build();
```

`WebPermissionHandler` receives `onPermissionRequest` and `onGeolocationRequest`. With no
handler configured, every request is denied.

## `storage.*` bridge

Backed by `native-storage-engine` (`EncryptedStore` by default, `KeyValueStore` when
constructed with `encrypted = false`). Actions: `get`, `set`, `remove`, `clear`, `keys`, `has`,
`getAll`. Legacy `native_runtime_web_storage` preferences are migrated once.

## Related

- [`web-developer-guide/`](web-developer-guide/README.md) — the JavaScript side
- [`AUDIT_AND_UPDATE_REPORT.md`](AUDIT_AND_UPDATE_REPORT.md) — known defects
- [`../CHANGELOG.md`](../CHANGELOG.md) — what changed and when
