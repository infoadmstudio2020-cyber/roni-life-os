# NativeWebBridgeSDK

Layer 3 transport between the WebView's JavaScript and the native `NativeRouter`.

## Overview

`NativeWebBridgeSDK` owns four things:

1. the RPC envelope (`BridgeMessage`),
2. the `@JavascriptInterface` entry point (`NativeJavascriptInterface`),
3. the event bus (`BridgeEventBus`),
4. the security gate (`BridgeSecurityValidator` + `OriginProvider`).

Every inbound message passes through, in order: **size check → parse → origin check →
envelope validation → dispatch**. Nothing reaches the router before all four pass.

## Message envelope

```json
{
  "id": "req_1700000000000_1",
  "type": "REQUEST",
  "action": "ui.showToast",
  "payload": { "message": "Hello" },
  "timestamp": 1700000000000
}
```

`type` is one of `REQUEST`, `RESPONSE`, `ERROR`, `EVENT`, `PING`.

`action` accepts three forms:

| Form | Path | Action |
| --- | --- | --- |
| `ui.showToast` | `/ui` | `showToast` |
| `/ui/showToast` | `/ui` | `showToast` |
| `showToast` | `/showToast` | `showToast` |

## Origin validation

Origin validation is **enabled by default**. The bridge cannot read the WebView itself, so the
host that owns the WebView must supply the current page origin:

```java
WebBridge bridge = NativeWebBridgeSDK.getInstance().getBridge();
bridge.setOriginProvider(() -> {
    String url = webView.getUrl();
    return url != null ? Uri.parse(url).getScheme() + "://" + Uri.parse(url).getAuthority() : null;
});
```

`WebViewHost` does this automatically; you only need to call it if you drive `WebBridge`
directly.

**Default allow-list:** `https://appassets.androidplatform.net` (the `WebViewAssetLoader`
virtual origin). Add more with `BridgeConfig.Builder.addAllowedOrigin(...)`.

**Deny rules:**

- an origin not in the allow-list is rejected with `ORIGIN_REJECTED: <origin>`;
- a `null` origin (unknown page, `about:blank`, provider not attached) is rejected;
- an empty allow-list denies everything — use `addAllowedOrigin("*")` to opt out explicitly.

Rejected messages never reach the router, including `PING` and `EVENT`.

To disable the gate entirely (not recommended):

```java
BridgeConfig.builder().requireOriginValidation(false).build();
```

## Timeouts

`BridgeConfig.timeoutMs` (default `10_000`) bounds how long a `REQUEST` may stay unanswered.
A single daemon thread sweeps pending requests every `min(timeoutMs, 5000)` ms and emits:

```json
{ "id": "req_...", "type": "ERROR", "error": "REQUEST_TIMEOUT", "success": false }
```

A response that arrives after its timeout is dropped silently.

## Payload limits

`BridgeConfig.maxMessageBytes` (default `1_048_576`) is checked **before** JSON parsing.
Oversized messages are rejected with `PAYLOAD_TOO_LARGE`.

## Lifecycle

```java
NativeWebBridgeSDK sdk = NativeWebBridgeSDK.getInstance();
sdk.init(context, BridgeConfig.builder().build());
WebBridge bridge = sdk.getBridge();
// ...
sdk.release();
```

- `WebBridge.init(config)` is idempotent. If an instance already exists with a different
  interface name, the existing name wins and a warning is logged — the name is already bound
  into the WebView via `addJavascriptInterface`.
- `WebBridge.reconfigure(config)` updates `timeoutMs`, `debugLogging`, `allowedOrigins`,
  `requireOriginValidation` and `maxMessageBytes`. It **never** changes `interfaceName`.
- `WebBridge.release()` resets state but does not null the singleton; `NativeWebBridgeSDK`
  owns that. `WebBridge.getInstance()` after a release returns a fresh instance.

## Testing

```bash
./gradlew :native-web-bridge-sdk:testDebugUnitTest
```

| Test class | Covers |
| --- | --- |
| `NativeWebBridgeSDKTest` | envelope, serializer, event bus, script generation |
| `BridgeOriginValidationTest` | B1 — allow/deny, null origin, disabled gate |
| `BridgeTimeoutTest` | B3 — `REQUEST_TIMEOUT`, late-response drop |
| `BridgeInjectionTest` | B4 — quote/`</script>`/U+2028 escaping, name sanitisation |
| `BridgeConfigTest` | B2/B7 — defaults, singleton, reconfigure, size limit |
| `BridgeLifecycleTest` | B5 — release semantics |
