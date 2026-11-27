# Native API Overview — সিস্টেমের গঠন

## ১. স্তর (Layers)

RONiLiFEOS একটি স্তরভিত্তিক (layered) সিস্টেম। প্রতিটি স্তর আলাদা Gradle মডিউল।

| স্তর | মডিউল | কাজ |
|---|---|---|
| L0 | `native-runtime-contracts` | সব interface, DTO, `NativeApiRequest` / `NativeApiResponse` |
| L1 | `native-logger-sdk`, `native-storage-engine`, `native-security-engine`, `native-theme-engine`, `native-platform-engine`, `native-network-engine`, `native-analytics-engine`, `native-service-sdk`, `native-dev-tools-sdk` | ইঞ্জিন (engine) |
| L2 | `native-router-sdk` | `NativeRouter` — path → handler dispatch |
| L3 | `native-web-bridge-sdk` | `WebBridge` — WebView ↔ Native transport |
| L4 | `native-runtime-sdk`, `native-runtime-engine`, `native-runtime-ui` | Orchestration, UI, WebView host |
| App | `app` | `RONiLiFEOS` অ্যাপ — Project management, Activity |

---

## ২. একটি কলের পূর্ণ যাত্রা

```
JS: NativeRuntimeBridgeClient.call('ui.showToast', {message:'hi'})
  │
  ├─ JSON.stringify({id:'req_…', type:'REQUEST', action:'ui.showToast', payload:{…}, timestamp:…})
  │
  ├─ window.NativeRuntimeBridge.postMessage(json)          ← @JavascriptInterface
  │
  ├─ NativeJavascriptInterface.postMessage()               (native-web-bridge-sdk)
  │
  ├─ WebBridge.handleWebMessage(rawJson)
  │     ├─ size check      → > 1,048,576 bytes? → PAYLOAD_TOO_LARGE
  │     ├─ deserialize     → JsonMessageSerializer
  │     ├─ origin gate     → BridgeSecurityValidator.isOriginValid()
  │     ├─ message valid   → id ও action খালি নয়?
  │     ├─ action parse    → "ui.showToast" → path "/ui", action "showToast"
  │     └─ router.dispatch(NativeApiRequest)
  │
  ├─ NativeRouter → NativeUICommandHandler.handleRequest()
  │     └─ switch(action) → uiHost.getToast().showNativeToast(…)
  │
  ├─ NativeApiResponse.success(reqId, {shown:true})
  │
  ├─ WebBridge.onSuccess() → BridgeMessage.response()
  │
  ├─ dispatchJs() → webView.evaluateJavascript(
  │       "window['NativeRuntimeBridgeClient']._handleNativeMessage('<json>')")
  │
  └─ JS: Promise resolve({shown:true})
```

---

## ৩. মডিউল ম্যাপ — কোন API কোথায়

### ৩.১ Bridge (L3)

| ক্লাস | ফাইল | দায়িত্ব |
|---|---|---|
| `WebBridge` | `native-web-bridge-sdk/.../bridge/WebBridge.java` | transport, origin gate, timeout sweeper, JS dispatch |
| `BridgeConfig` | `.../bridge/BridgeConfig.java` | interface name, allow-list, timeout, max bytes |
| `BridgeSecurityValidator` | `.../bridge/BridgeSecurityValidator.java` | origin + message validation |
| `NativeJavascriptInterface` | `.../bridge/js/NativeJavascriptInterface.java` | `postMessage`, `ping` |
| `JavaScriptBridgeScript` | `.../bridge/js/JavaScriptBridgeScript.java` | `NativeRuntimeBridgeClient` inject করে |
| `JsonMessageSerializer` | `.../bridge/serializer/JsonMessageSerializer.java` | envelope serialize/deserialize |
| `BridgeEventBus` | `.../bridge/event/BridgeEventBus.java` | native-side event bus |

### ৩.২ Command Handlers (JS থেকে যেগুলোতে পৌঁছানো যায়)

| Handler | Path | Action সংখ্যা | মডিউল |
|---|---|---|---|
| `NativeUICommandHandler` | `/ui` | ৬০ | `native-runtime-ui` |
| `NativeWebCommandHandler` | `/web` | ৫৩ | `native-runtime-ui` |
| `NativeDeviceCommandHandler` | `/device` | ২৮ | `native-runtime-ui` |
| `NativeStorageCommandHandler` | `/storage` | ৭ | `native-runtime-ui` |
| `RuntimeCommandHandler` | `/runtime` | ৬ | `native-runtime-engine` |
| `BridgeCommandHandler` | `/bridge` | ১ (`dispatch`) | `native-web-bridge-sdk` |
| `EchoCommandHandler` | `/echo` | ১ | `native-router-sdk` |
| `SystemDiagnosticHandler` | `/system` | ১ | `native-router-sdk` |

### ৩.৩ Internal-only handlers (Bridge-এ রেজিস্টার্ড **নয়**)

| Handler | Path | কেন internal |
|---|---|---|
| `ThemeCommandHandler` | `theme` | `NativeThemeEngine` নিজে রেজিস্টার করে না |
| `NetworkCommandHandler` | `network` | একই |
| `PlatformCommandHandler` | `platform` | একই |
| `SecurityCommandHandler` | `security` | একই |
| `AnalyticsCommandHandler` | `analytics` | একই |
| `DevToolsCommandHandler` | `devtools` | একই |
| `LoggerCommandHandler` | `logger` | একই |
| `ServiceCommandHandler` | `service` | একই |
| `StorageCommandHandler` (engine) | `storage` | path clash — UI handler জেতে |

> **গুরুত্বপূর্ণ:** এই handler-গুলোতে Java কোড থেকে `handleRequest()` কল করা যায়, কিন্তু
> `NativeRuntimeBridgeClient.call('theme.getTheme')` দিলে **`ROUTE_NOT_FOUND`** আসবে।
> কারণ `NativeRuntimeUI.registerBridgeRoutes()` শুধু ui/web/device/storage রেজিস্টার করে,
> আর `NativeRuntimeEngine` শুধু `/runtime`।

---

## ৪. রেজিস্ট্রেশন কোথায় হয়

```java
// native-runtime-ui/.../NativeRuntimeUI.java  (init() থেকে ডাকা হয়)
private void registerBridgeRoutes(@Nullable Context context) {
    WebBridge bridge = NativeWebBridgeSDK.getInstance().getBridge();
    bridge.getRouter().registerHandler(commandHandler);          // path "ui"
    for (String action : NativeUICommandHandler.SUPPORTED_ACTIONS)
        bridge.getRouter().registerRoute(action, commandHandler); // bare name
    // … একইভাবে web, device, storage
}
```

```java
// native-runtime-engine/.../NativeRuntimeEngine.java  (init())
NativeRouter router = NativeRouter.getInstance();
router.registerHandler(this.commandHandler);   // path "runtime"
router.registerRoute("/runtime", this.commandHandler);
```

**এর মানে:**
- `ui.showToast` → path `/ui` → ✅
- `/ui/showToast` → path `/ui` → ✅
- `showToast` (bare) → path `/showToast` → ✅ (কারণ `SUPPORTED_ACTIONS`-এ আছে)
- `web.setTextZoom` → path `/web` → ✅ (bare `setTextZoom` ❌, `SUPPORTED_ACTIONS`-এ নেই)

---

## ৫. WebView Host

`WebViewHost` (`native-runtime-ui/.../ui/web/WebViewHost.java`) যা করে:

| কাজ | কীভাবে |
|---|---|
| Bridge attach | `webView.addJavascriptInterface(new NativeJavascriptInterface(bridge), "NativeRuntimeBridge")` |
| Origin provider | `bridge.setOriginProvider(() -> originOf(webView.getUrl()))` |
| JS evaluator | `bridge.setWebEvaluator(script -> webView.post(() -> evaluateJavascript(script)))` |
| Bootstrap inject | `onPageFinished` → `evaluateJavascript(bridge.getBootstrapScript())` |
| Local source | virtual HTTPS origin (`https://<host>/…`) দিয়ে serve |
| Path guard | `WebPaths.isSafe(path)` — `..`, `%2e`, `%2f`, `%5c`, `\0` reject |
| Download | `DownloadCenter` + `window.RONiDownload` |
| File chooser | `onShowFileChooser` → `startActivityForResult` |
| Permission | `WebPermissionHandler` না থাকলে **deny** |
| SSL error | `handler.cancel()` — কখনো bypass নয় |

---

## ৬. Project → WebView

`ProjectActivity` প্রতিটি Project-এর জন্য আলাদা document task খোলে।

```
Project (ProjectStore)
   │
   ├─ ProjectSourceType: ASSET | SAF_FOLDER | REMOTE_URL | ZIP | INLINE | FILE
   │
   ├─ WebSourceFactory.from(project) → WebSource
   │
   ├─ ProjectUIConfigMapper.apply(config, builder, decoder)
   │      └─ decoder = ProjectVaultRuntime.decoderFor(project)   // AES-GCM হলে
   │
   └─ WebViewHost.loadSource(source)
          ├─ WebSourceResolver.resolve() → virtual host + provider
          ├─ trustBridgeOrigin(host)  → bridge.allowOrigin("https://<host>")
          └─ webView.loadUrl("https://<host>/index.html")
```

**প্রতিটি Project আলাদা origin পায়:** `https://<slug>-<hash8>.runtime.local`
(`WebPaths.virtualHostFor(projectId)`)। ফলে এক Project-এর `localStorage`/cookie অন্য Project
পড়তে পারে না।

---

## ৭. Android Manifest — যা দরকার

`app/src/main/AndroidManifest.xml`-এ যে permission গুলো আছে:

| Permission | কেন |
|---|---|
| `INTERNET` | remote source, download |
| `ACCESS_NETWORK_STATE` | `device.getNetworkState` |
| `VIBRATE` | (haptic আসলে `performHapticFeedback`, permission লাগে না) |
| `POST_NOTIFICATIONS` | download notification (API 33+) |
| `READ_MEDIA_*` / `WRITE_EXTERNAL_STORAGE` | download folder (version-ভেদে) |

`device.*` API-গুলোর **কোনোটিরই runtime permission prompt লাগে না** — কারণ সেগুলো
Activity-র নিজের window/clipboard/intent ব্যবহার করে।

---

## ৮. সংক্ষেপে

- **Native API** = Java ক্লাস + `IApiHandler.handleRequest()`
- **WebBridge API** = `NativeRuntimeBridgeClient.call(action, payload)`
- **Native → Web** = `WebBridge.emitToWeb(topic, data)` → `client.on(topic, fn)`
- **Web → Native** = `client.call()` → `WebBridge.handleWebMessage()`
- **Internal-only** = handler আছে কিন্তু router-এ রেজিস্টার্ড নয়
