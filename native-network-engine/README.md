# native-network-engine

HTTP, WebSocket, download and connectivity primitives for NativeRuntime. Plain Java standard
library plus the Android SDK — no third-party networking dependency.

## What is real

| Area | Implementation |
| --- | --- |
| HTTP | `HttpURLConnection` with configurable connect/read timeouts, manual redirect following (max 5 hops, `Authorization`/`Cookie` stripped on cross-host redirects), retry for idempotent methods only |
| WebSocket | RFC 6455 client over `Socket`/`SSLSocket`: handshake, client frame masking, text/binary/ping/pong/close, 7/16/64-bit payload lengths, fragmentation reassembly, dedicated reader thread |
| Downloads | Streaming to a `.part` file, `Range` resume on `206 Partial Content`, cancel deletes the partial file, throttled progress callbacks |
| Connectivity | `ConnectivityManager.NetworkCallback` bound to a real `Context`; reports `NONE`/`UNKNOWN` until bound |
| TLS pinning | `SslPinningManager.verify(host, presentedPin)` — fail-closed when pins exist for the host |

## Quick start

```java
NativeNetworkEngine engine = NativeNetworkEngine.getInstance();
engine.init(context, NativeNetworkEngineConfig.builder()
        .connectTimeout(10_000)
        .readTimeout(20_000)
        .maxRetries(2)
        .debugEnabled(BuildConfig.DEBUG)
        .build());

engine.getHttpClient().get("https://api.example.com/health", response -> {
    // response.getStatusCode(), response.getBody()
});

WebSocketClient ws = engine.createWebSocket("wss://api.example.com/stream", listener);
ws.connect();
ws.sendText("{\"subscribe\":\"trades\"}");
```

## WebSocket contract

- `connect()` performs the HTTP Upgrade handshake and blocks until the `101` response is read.
- `sendText` / `sendBinary` are thread-safe and mask every client frame.
- Incoming `ping` frames are answered with a `pong` automatically; `onPing`/`onPong` are
  optional listener callbacks.
- Fragmented messages (`FIN=0`) are reassembled before `onMessage` fires.
- `close()` sends a close frame, then closes the socket; `onClose` fires exactly once.

## Honest defaults

- `NetworkMonitor.getConnectionType()` returns `NONE` and `getBandwidthQuality()` returns
  `UNKNOWN` until `bind(context)` succeeds. Nothing is guessed.
- `SslPinningManager.verify` returns `false` when the host has pins and the presented pin does
  not match — including when the presented pin is `null`.
- `RetryInterceptor` never retries `POST` or `PATCH`.

## Tests

`src/test/java/com/roni/library/network/ws/` contains an in-process RFC 6455 server
(`TestWebSocketServer`) used by the handshake, frame, send and lifecycle suites. No network
access is required.
