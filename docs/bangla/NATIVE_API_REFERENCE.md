> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# Native API Reference — Java স্তর

> এখানে শুধু সেই Java ক্লাস/মেথড আছে যেগুলো সোর্সে আছে। সিগনেচার হুবহু কপি করা।

---

## ১. `NativeApiRequest` — সব request-এর envelope

**মডিউল:** `native-runtime-contracts`
**ফাইল:** `com/roni/library/contracts/api/NativeApiRequest.java`

```java
public NativeApiRequest(@NonNull String requestId,
                        @NonNull String path,
                        @NonNull String action,
                        @Nullable Map<String, Object> parameters,
                        long timestamp)

public String getRequestId()
public String getPath()
public String getAction()
public Map<String, Object> getParameters()
public Object getParameter(String key)
public String getStringParam(String key, String defaultValue)
public boolean getBooleanParam(String key, boolean defaultValue)
public long getTimestamp()
```

**Builder:**

```java
NativeApiRequest req = new NativeApiRequest.Builder("/ui", "showToast")
        .param("message", "হ্যালো")
        .param("type", "success")
        .build();
```

---

## ২. `NativeApiResponse`

```java
public static NativeApiResponse success(String requestId, Map<String,Object> data)
public static NativeApiResponse success(String requestId)
public static NativeApiResponse error(String requestId, int statusCode, NativeApiError error)
public static NativeApiResponse error(String requestId, NativeApiError error)   // 500

public String getRequestId()
public int getStatusCode()
public boolean isSuccess()
public Map<String,Object> getData()
public NativeApiError getError()
public long getTimestamp()
```

## ৩. `NativeApiError`

```java
public NativeApiError(String code, String message, String details)
public NativeApiError(String code, String message)
public String getCode()
public String getMessage()
public String getDetails()
```

---

## ৪. `IApiHandler` — handler contract

**মডিউল:** `native-runtime-contracts`

```java
public interface IApiHandler {
    String getPath();
    String getAction();
    NativeApiResponse handleRequest(NativeApiRequest request);
}
```

`NativeRouter.registerHandler(handler)` শুধু `getPath()` দেখে route বানায়।
তাই `getPath()` = `"ui"` হলে route হয় `/ui`।

---

## ৫. `WebBridge` — transport

**মডিউল:** `native-web-bridge-sdk`
**ফাইল:** `com/roni/library/bridge/WebBridge.java`

```java
public static WebBridge getInstance()
public static synchronized WebBridge init(BridgeConfig config)
public static boolean isInitialized()

public BridgeConfig getConfig()
public IRouter getRouter()
public IEventBus getEventBus()
public IMessageSerializer getSerializer()
public NativeJavascriptInterface getJavascriptInterface()

public void setWebEvaluator(Consumer<String> evaluator)
public void clearWebEvaluator()
public void setOriginProvider(OriginProvider provider)
public void clearOriginProvider()
public void allowOrigin(String origin)
public boolean isOriginValidationEnabled()
public boolean isInstanceInitialized()
public int getPendingRequestCount()

public String getBootstrapScript()
public void reconfigure(BridgeConfig newConfig)
public void emitToWeb(String topic, Map<String,Object> data)
public void handleWebMessage(String rawJson)
public void onSuccess(NativeApiResponse response)
public void onError(NativeApiError error)
public void onError(String reqId, NativeApiError error)
public void release()
```

**`emitToWeb` — Native → Web ইভেন্ট:**

```java
WebBridge bridge = NativeWebBridgeSDK.getInstance().getBridge();
Map<String,Object> data = new HashMap<>();
data.put("orderId", 42);
bridge.emitToWeb("order.updated", data);
```

JS-এ:
```js
NativeRuntimeBridgeClient.on('order.updated', function (p) {
  console.log(p.orderId);   // 42
});
```

---

## ৬. `BridgeConfig`

```java
public static final String DEFAULT_INTERFACE_NAME = "NativeRuntimeBridge";
public static final String DEFAULT_ALLOWED_ORIGIN = "https://appassets.androidplatform.net";
public static final int DEFAULT_MAX_MESSAGE_BYTES = 1_048_576;

public static Builder builder()
public static BridgeConfig defaultConfig()
public static String sanitizeInterfaceName(String name)

public String getInterfaceName()
public Set<String> getAllowedOrigins()
public long getTimeoutMs()                 // ডিফল্ট 10000
public boolean isDebugLogging()
public boolean isOriginValidationRequired() // ডিফল্ট true
public int getMaxMessageBytes()
public void addAllowedOrigin(String origin)
public boolean isOriginAllowed(String origin)
```

**Builder:**

```java
BridgeConfig.builder()
    .interfaceName("NativeRuntimeBridge")
    .addAllowedOrigin("https://my.example")
    .timeoutMs(15000)
    .debugLogging(true)
    .requireOriginValidation(true)
    .maxMessageBytes(2 * 1024 * 1024)
    .build();
```

`sanitizeInterfaceName` regex: `[A-Za-z_$][A-Za-z0-9_$]*` — না মিললে ডিফল্ট নাম।

---

## ৭. `BridgeSecurityValidator`

```java
public BridgeSecurityValidator(BridgeConfig config)
public boolean isOriginValid(String origin)
public boolean isMessageValid(BridgeMessage message)
```

`isMessageValid` শুধু দুটি জিনিস দেখে: `id` খালি নয়, `action` খালি নয়।

---

## ৮. `NativeJavascriptInterface`

```java
public NativeJavascriptInterface(WebBridge bridge)

@JavascriptInterface public void postMessage(String jsonMessage)
@JavascriptInterface public String ping()      // "pong"
```

---

## ৯. `JavaScriptBridgeScript`

```java
public static String getInjectionScript(String interfaceName)
```

`interfaceName = "NativeRuntimeBridge"` হলে যে script তৈরি হয় তা
`window.NativeRuntimeBridgeClient` ইনস্টল করে। এই script-টি `WebViewHost.onPageFinished()`-এ
inject হয়।

---

## ১০. `BridgeMessage` / `BridgeMessageType`

```java
public static BridgeMessage request(String id, String action, Map<String,Object> payload)
public static BridgeMessage response(String id, String action, Map<String,Object> payload)
public static BridgeMessage error(String id, String action, String error)
public static BridgeMessage event(String topic, Map<String,Object> payload)
public static BridgeMessage ping(String id)

public String getId()
public BridgeMessageType getType()
public String getAction()
public Map<String,Object> getPayload()
public String getError()
public boolean isSuccess()
public long getTimestamp()
```

`BridgeMessageType`: `REQUEST, RESPONSE, ERROR, EVENT, PING`

---

## ১১. `NativeWebBridgeSDK` — facade

```java
public static NativeWebBridgeSDK getInstance()
public synchronized NativeWebBridgeSDKResult<WebBridge> init(Context context, BridgeConfig config)
public boolean isInitialized()
public RuntimeState getState()
public BridgeConfig getConfig()
public WebBridge getBridge()
public Context getContext()
public synchronized void release()
```

---

## ১২. `NativeRouter`

**মডিউল:** `native-router-sdk`

```java
public static NativeRouter getInstance()
public void registerRoute(String pathPattern, IApiHandler handler)
public void registerHandler(IApiHandler handler)
public void unregisterRoute(String pathPattern)
public void setFallbackHandler(IApiHandler fallbackHandler)
public boolean canHandle(String path)
public void dispatch(NativeApiRequest request, IApiCallback callback)
```

`NativeRouter` constructor-এ ডিফল্টে `EchoCommandHandler` ও `SystemDiagnosticHandler`
রেজিস্টার হয়।

---

## ১৩. `WebViewHost`

**মডিউল:** `native-runtime-ui`
**ফাইল:** `com/roni/library/ui/web/WebViewHost.java`

```java
public WebViewHost(Context context)
public WebViewHost(Context context, WebHostConfig config)

public void loadSource(WebSource source)
public void loadUrl(String url)
public void loadHtml(String html, String baseUrl)
public void reload()
public void stopLoading()
public boolean canGoBack()
public void goBack()
public boolean canGoForward()
public void goForward()
public void clearHistory()
public void setPageZoom(int zoom)
public int getPageZoom()
public void evaluateJavascript(String script, ValueCallback<String> callback)
public void emitEvent(String topic, Map<String,Object> data)
public void setProgressBarVisible(boolean visible)
public WebView getWebView()
public String getCurrentUrl()
public void onResume()
public void onPause()
```

**Listener:**

```java
public interface Listener {
    void onPageStarted(String url);
    void onPageFinished(String url);
    void onProgressChanged(int progress);
    void onTitleReceived(String title);
    void onReceivedError(int errorCode, String description, String failingUrl);
    void onExternalLink(String url);
}
```

---

## ১৪. `WebHostConfig`

```java
public static Builder builder()
public static WebHostConfig defaultConfig()
```

**ডিফল্ট মান:**

| Property | ডিফল্ট |
|---|---|
| `javaScriptEnabled` | `true` |
| `domStorageEnabled` | `true` |
| `databaseEnabled` | `true` |
| `allowFileAccess` | `false` |
| `allowContentAccess` | `false` |
| `allowZoom` | `false` |
| `showProgressBar` | `true` |
| `bridgeEnabled` | `true` |
| `bridgeInterfaceName` | `"NativeRuntimeBridge"` |
| `openExternalLinksInBrowser` | `true` |
| `mediaPlaybackRequiresUserGesture` | `true` |
| `cacheMode` | `CACHE_DEFAULT` |
| `mixedContentAllowed` | `false` |
| `safeBrowsingEnabled` | `true` |
| `supportMultipleWindows` | `false` |
| `fileChooserEnabled` | `true` |
| `downloadsEnabled` | `true` |
| `textZoom` | `100` |
| `useWideViewPort` | `true` |
| `loadWithOverviewMode` | `true` |
| `hardwareAcceleration` | `true` |

---

## ১৫. `WebPaths` — নিরাপত্তার মূল

```java
public static final String VIRTUAL_SUFFIX = ".runtime.local";
public static final String LEGACY_HOST = "appassets.androidplatform.net";

public static String normalize(String path)
public static boolean isSafe(String path)
public static String virtualHostFor(String projectId)
public static String originOf(String host)
public static String virtualUrl(String host, String path)
```

`isSafe()` reject করে:
- `\0`
- `%2e`, `%2f`, `%5c` (double-encoded separator)
- যেকোনো `..` segment

`virtualHostFor("proj-123")` → `proj-123-<sha256 প্রথম ৪ বাইট hex>.runtime.local`

---

## ১৬. `DownloadCenter`

```java
public static final String FOLDER = "RONiLiFEOS";
public static final String JS_INTERFACE = "RONiDownload";

public static void enqueue(Context, String url, String userAgent,
                           String contentDisposition, String mimeType, long contentLength)
public static void saveDataUrl(Context, String dataUrl, String fileName)
public static void write(Context, byte[] bytes, String fileName, String mime)
public static String blobScript(String url, String fileName, String mime)
public static String guessName(String url, String contentDisposition, String mime)
```

**JS bridge:**

```java
public static final class JsBridge {
    @JavascriptInterface public void saveBase64(String base64, String fileName, String mime)
    @JavascriptInterface public void download(String url, String fileName)
}
```

---

## ১৭. `NativeRuntimeUI`

```java
public static NativeRuntimeUI getInstance()
public synchronized NativeRuntimeUIResult<Boolean> init(Context context, NativeRuntimeUIConfig config)
public synchronized View attachActivity(Activity activity, NativeRuntimeUIConfig config)
public synchronized void detachActivity(Activity activity)
public NativeUIHost getUiHost()
public NativeUICommandHandler getCommandHandler()
public WebBridge getWebBridge()
public NativeToast getToast()
public LoadingIndicator getLoadingIndicator()
public CustomBottomSheet getBottomSheet()
public NativeActionSheet getActionSheet()
public synchronized void release()
```

---

## ১৮. `NativeRuntime`

```java
public static NativeRuntime getInstance()
public synchronized NativeRuntimeResult<Boolean> init(Context context)
public synchronized NativeRuntimeResult<Boolean> init(Context context, RuntimeConfiguration config)
public boolean isInitialized()
public RuntimeState getState()
public NativeRuntimeBootstrap.BootstrapReport getBootstrapReport()
public void addObserver(LifecycleObserver observer)
public void removeObserver(LifecycleObserver observer)
public void resumeSession()
public void pauseSession()
```

---

## ১৯. `NativeRuntimeBootstrap`

```java
public static BootstrapReport execute(Context context, RuntimeConfiguration config)
public static BootstrapReport execute(Context context, RuntimeConfiguration config, boolean failFast)
```

`BootstrapReport`:
```java
public boolean isOverallSuccess()
public long getTotalDurationMs()
public List<StepReport> getSteps()
public int getPassedCount()
public int getFailedCount()
public int getSkippedCount()
```

---

## ২০. `NativeUIHost`

```java
public DialogManager getDialogManager()
public LoadingOverlayManager getLoadingOverlayManager()
public NavigationManager getNavigationManager()
public ToolbarManager getToolbarManager()
public NativeToast getToast()
public CustomBottomSheet getBottomSheet()
public NativeActionSheet getActionSheet()
public Activity getActivity()
public View getRootView()
public NativeUIComponentManager getUIComponentManager()
public IUIComponentRegistry getRegistry()
public IUIConfigurationManager getConfigurationManager()
public IUIVisibilityManager getVisibilityManager()
public NativeRuntimeUIConfig getConfiguration()
```

---

## ২১. `NativeToast`

```java
public enum Duration { SHORT, LONG }
public enum Type { INFO, SUCCESS, WARNING, ERROR }

public void show(String message)
public void show(String message, Duration duration, Type type)
public void showNativeToast(Context context, String message, Duration duration, Type type)
public void dismiss()
public boolean isVisible()
public String getMessage()
public Duration getDuration()
public Type getType()
```

`showNativeToast` ভেতরে `new Handler(Looper.getMainLooper()).post(...)` করে — তাই
যেকোনো thread থেকে নিরাপদ।

---

## ২২. `OriginProvider`

```java
@FunctionalInterface
public interface OriginProvider {
    String getCurrentOrigin();   // null = untrusted
}
```

`WebViewHost` এভাবে দেয়:

```java
bridge.setOriginProvider(() -> {
    WebView current = webView;
    return current != null ? originOf(current.getUrl()) : null;
});
```

`originOf()` শুধু `http`/`https` scheme-এ origin দেয়; `about:blank`, `data:`, `file:` → `null`।

---

## ২৩. `BridgeCallback`

```java
public interface BridgeCallback {
    void onSuccess(BridgeMessage message);
    void onError(String error);
}
```

---

## ২৪. `IApiCallback`

```java
public interface IApiCallback {
    void onResponse(NativeApiResponse response);
    void onError(NativeApiError error);
}
```

---

## ২৫. `NativeStorageCommandHandler` (bridge-এ ব্যবহৃত)

```java
public NativeStorageCommandHandler(NativeUIHost uiHost)              // encrypted = true
public NativeStorageCommandHandler(NativeUIHost uiHost, boolean encrypted)
public boolean isEncrypted()
```

ভেতরে `StorageEngine.getEncryptedStore()` (AES-GCM) বা `StorageEngine.getKeyValueStore()`
ব্যবহার করে। প্রথম ব্যবহারে পুরনো `native_runtime_native_runtime_web_storage`
SharedPreferences একবার migrate করে, তারপর `__migrated_v1` marker রাখে।

---

## ২৬. `StorageEngine` (static facade)

```java
public static NativeStorageEngine getInstance()
public static NativeStorageEngineResult<Boolean> init(Context, NativeStorageEngineConfig)
public static boolean isInitialized()
public static EncryptedPreferences getPreferences(String name)
public static StorageDatabase getDatabase()
public static EncryptedStore getEncryptedStore()
public static KeyValueStore getKeyValueStore()
public static LruDiskCache getDiskCache()
public static DatabaseHelper getDatabaseHelper()
public static NativeStorageService getService()
public static StorageCommandHandler getCommandHandler()
public static void release()
```

---

## ২৭. `ProjectVault` / `VaultCipher` (AES-GCM)

**মডিউল:** `native-security-engine`

| ক্লাস | কাজ |
|---|---|
| `ProjectVault` | পুরো project tree seal/open |
| `VaultCipher` | একক resource encrypt/decrypt |
| `VaultFormat` | ফাইল header/magic |
| `VaultLimits` | `maxResourceBytes` (64 MB), `maxBufferedBytes` (2 MB), `chunkBytes` (64 KB) |
| `SealedInputStream` | chunk-by-chunk authenticated decrypt |
| `DecryptedResourceCache` | decrypted resource cache (maxEntryBytes 2 MB) |
| `KeyStoreManager` | Android Keystore key |

Format: `[magic "RVLT":4][version:1][chunkSize:4][chunkCount:4][plainLength:8]`
তারপর প্রতি chunk: `[iv:12][ciphertext+tag:16]`
AAD = `projectId \n path \n version \n generation \n chunkIndex`

---

## ২৮. `WebThemeBridge` (internal)

```java
public static String generateCssVariables(IThemeConfig theme)
public static String generateInjectionScript(IThemeConfig theme)
```

CSS variable নাম: `--theme-name`, `--theme-dark-mode`, `--color-primary`, `--color-surface`,
`--color-text`, `--color-secondary`, `--color-background`, `--color-text-secondary`,
`--color-accent`, `--color-status-bar`, `--color-nav-bar`, + custom tokens।

> এটি bridge-এ রেজিস্টার্ড নয়। `theme.getCssVariables` action আছে কিন্তু
> `ThemeCommandHandler` router-এ নেই — তাই JS থেকে পাওয়া যাবে না।
