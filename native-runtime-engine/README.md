# native-runtime-engine

Container, loopback web server and lifecycle for NativeRuntime web applications.

## Components

| Class | Responsibility |
| --- | --- |
| `NativeRuntimeEngine` | Singleton lifecycle: `init` builds the service, starts the server, registers `/runtime` with `NativeRouter`; `release` unregisters and resets state while keeping the singleton |
| `NativeRuntimeService` | Owns `LocalWebServer`, `WebSourceResolver`, `WebViewContainer`, `CookieSyncManager`, `WebVitalsCollector` |
| `LocalWebServer` | Real `ServerSocket` bound to loopback; ephemeral port fallback; `GET`/`HEAD`; `405` for everything else |
| `WebViewContainer` | Real/headless split. Headless containers record no metrics and report `{"status":"unavailable","reason":"headless"}` for `evaluateJavascript` |
| `WebVitalsCollector` | FP / FCP / LCP / DOM-ready / load. Unrecorded metrics are `-1`, never `0` |
| `CookieSyncManager` | Cookie store plus `flush()` to the platform `CookieManager` |

## Real vs headless

```java
// Real: bound to a WebView, collects paint timings from performance.getEntriesByType('paint')
WebViewContainer container = new WebViewContainer("main", webView);

// Headless: navigation state only
WebViewContainer headless = new WebViewContainer("worker");
headless.loadUrl("https://example.test/");
headless.getVitalsCollector().getPageLoadMs(); // -1 (UNRECORDED)
```

## Local server

```java
LocalWebServer server = new LocalWebServer(0, resolver); // 0 = ephemeral port
server.start();
server.getBaseUrl(); // http://127.0.0.1:<actual port>
server.stop();
```

`start()` binds the configured port; if that fails it binds an ephemeral port and reports the
real one through `getPort()`. `start()` is idempotent and `stop()` closes the listening socket
and shuts down both executors.

## Lifecycle

```java
NativeRuntimeEngine.getInstance().init(context, NativeRuntimeEngineConfig.defaultConfiguration());
NativeRuntimeEngine.getInstance().getState(); // RuntimeState.READY
NativeRuntimeEngine.getInstance().release();  // state -> UNINITIALIZED, singleton retained
```

Accessors other than `getService()`/`getCommandHandler()` throw `IllegalStateException` before
`init`, so a missing bootstrap fails loudly instead of returning a half-built object.

## Tests

`LocalWebServerTest` opens real sockets against the loopback server. `RuntimeEngineLifecycleTest`
asserts init idempotency, singleton retention across `release()`, and `/runtime` route
registration. `WebViewContainerHeadlessTest` and `WebVitalsCollectorTest` pin the honest-default
contract.
