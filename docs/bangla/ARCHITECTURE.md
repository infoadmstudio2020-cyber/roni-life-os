# Architecture — RONiLiFEOS-এর স্তর ও নির্ভরতা

> এই ডকের প্রতিটি নির্ভরতা `*/build.gradle`-এর `implementation project(...)` লাইন থেকে নেওয়া।
> কোনো অনুমান নেই।

## ১. স্তর (Layers)

```
┌──────────────────────────────────────────────────────────────┐
│  Web Application (HTML5 + CSS3 + ES6)                        │
│  index.html · styles/ · scripts/ · assets/                   │
└───────────────────────────┬──────────────────────────────────┘
                            │  window.NativeRuntimeBridgeClient
                            │  { id, type:"REQUEST", action, payload }
┌───────────────────────────▼──────────────────────────────────┐
│  native-web-bridge-sdk     transport · envelope · origin gate │
├──────────────────────────────────────────────────────────────┤
│  native-router-sdk         route matching · dispatch          │
├──────────────────────────────────────────────────────────────┤
│  native-runtime-ui         WebViewHost · UI/Web/Device/       │
│                            Storage command handlers           │
├──────────────────────────────────────────────────────────────┤
│  native-runtime-engine     WebView lifecycle · WebSource      │
│  native-runtime-sdk        একত্রে বাঁধার façade               │
├──────────────────────────────────────────────────────────────┤
│  native-storage-engine  native-network-engine                 │
│  native-theme-engine    native-platform-engine                │
│  native-security-engine native-analytics-engine               │
│  native-logger-sdk      native-service-sdk                    │
│  native-dev-tools-sdk   native-runtime-contracts              │
└──────────────────────────────────────────────────────────────┘
```

## ২. নির্ভরতার গ্রাফ (যাচাইকৃত)

| মডিউল | নির্ভর করে |
|---|---|
| `native-runtime-contracts` | — (কোনো project dependency নেই) |
| `native-service-sdk` | contracts, logger |
| `native-logger-sdk` | contracts |
| `native-router-sdk` | service-sdk, contracts, logger |
| `native-web-bridge-sdk` | contracts, router-sdk, service-sdk, logger |
| `native-security-engine` | contracts, service-sdk, logger |
| `native-storage-engine` | contracts, service-sdk, **security-engine**, logger |
| `native-theme-engine` | contracts, service-sdk, logger |
| `native-platform-engine` | contracts, service-sdk, logger |
| `native-network-engine` | contracts, service-sdk, logger |
| `native-analytics-engine` | contracts, service-sdk, **storage-engine** |
| `native-dev-tools-sdk` | contracts, service-sdk, **storage-engine** |
| `native-runtime-engine` | contracts, service-sdk, **router-sdk**, logger |
| `native-runtime-ui` | **web-bridge-sdk**, contracts, theme-engine, storage-engine, platform-engine, security-engine |
| `native-runtime-sdk` | **সব** (১৪টি) — একমাত্র aggregation point |
| `app` | runtime-sdk, logger, storage, network, analytics, theme, platform, security, router, service, dev-tools |

**নিয়ম:** `native-runtime-sdk` ছাড়া কোনো মডিউল অন্যদের একত্র করে না। অ্যাপ শুধু
`native-runtime-sdk`-এর façade ব্যবহার করে। এই নিয়ম ভাঙলে circular dependency তৈরি হবে।

## ৩. রানটাইম প্রবাহ

### ৩.১ অ্যাপ চালু → WebView

```
MainActivity
  └─ NativeRuntimeUI.getInstance().init(context, config)
       ├─ NativeUIHost তৈরি (Activity, WebView container, config)
       ├─ NativeStorageCommandHandler(uiHost)
       ├─ NativeWebCommandHandler(uiHost)
       ├─ NativeDeviceCommandHandler(uiHost)
       └─ registerBridgeRoutes()
            ├─ router.registerHandler(handler)        // prefix route: "ui"
            └─ for (a : SUPPORTED_ACTIONS) registerRoute(a, handler)  // bare route
```

### ৩.২ WebView লোড

```
WebViewHost.load(WebSource)
  ├─ WebSource.getEntryPoint()      → "index.html" (ডিফল্ট)
  ├─ resource provider নির্বাচন:
  │    ASSET       → AssetResourceProvider
  │    FILE        → FileResourceProvider
  │    ZIP         → ZipResourceProvider
  │    CONTENT_URI → SafResourceProvider
  │    REMOTE_URL  → সরাসরি http(s)
  └─ WebViewClient.shouldInterceptRequest()
       └─ provider.open(path) → WebResource(MIME, stream)
```

### ৩.৩ JS → Native → JS

```
JS: NativeRuntimeBridgeClient.call("ui.showToast", {...})
  → WebViewCompat.addWebMessageListener / JavascriptInterface
  → NativeWebBridge.onMessage(json)
       ├─ origin যাচাই (requireOriginValidation)
       ├─ size যাচাই (maxMessageBytes = 1 MiB)
       ├─ envelope parse { id, type:"REQUEST", action, payload }
       ├─ router.dispatch(action, payload)
       │     ├─ prefix route "ui" → NativeUICommandHandler
       │     └─ switch(action) → case "showToast"
       └─ response { id, type:"RESPONSE", success:true, payload }
  → JS Promise resolve
```

### ৩.৪ Native → JS ইভেন্ট

```
Native: bridge.emit("ui.confirm", {confirmed:true})
  → evaluateJavascript("window.__nativeRuntimeEmit('ui.confirm', {...})")
  → bootstrap listener → JS callback
```

## ৪. মূল ক্লাস ও দায়িত্ব

| ক্লাস | মডিউল | দায়িত্ব |
|---|---|---|
| `NativeRuntimeUI` | runtime-ui | façade; handler তৈরি ও route registration |
| `NativeUIHost` | runtime-ui | Activity, WebView container, config, `projectId` |
| `NativeUICommandHandler` | runtime-ui | ৬২টি `ui.*` action |
| `NativeWebCommandHandler` | runtime-ui | ৫৫টি `web.*` action |
| `NativeDeviceCommandHandler` | runtime-ui | ২৮টি `device.*` action |
| `NativeStorageCommandHandler` | runtime-ui | ৭টি `storage.*` action (scope-aware) |
| `StorageScope` | storage-engine | project key namespace |
| `WebViewHost` | runtime-ui | WebView তৈরি, client, resource interception |
| `WebSource` | runtime-ui | source descriptor (৬ ধরনের) |
| `MimeTypes` | runtime-ui | ৩৩টি এক্সটেনশন → MIME |
| `NativeWebBridge` | web-bridge-sdk | transport, envelope, origin gate |
| `BridgeRouter` | router-sdk | route matching ও dispatch |
| `ProjectImporter` | app | ZIP import, entry detection |
| `ProjectManifest` | app | `manifest.json` যাচাই |
| `ArchiveExtractor` | app | ZIP নিরাপদে খোলা (path traversal রোধ) |

## ৫. থ্রেডিং

| কাজ | থ্রেড |
|---|---|
| Bridge message গ্রহণ | WebView-এর JS thread → main thread-এ post |
| Handler execution | main thread (UI action) |
| Storage read/write | `EncryptedStore` — synchronous, caller thread |
| Network | `native-network-engine` — background executor |
| Response ফেরানো | main thread → `evaluateJavascript` |

**নিয়ম:** handler-এর ভেতরে blocking I/O করবেন না। বড় ফাইল অপারেশন হলে
`NativeRuntimeBridge`-এর async path ব্যবহার করুন।

## ৬. Lifecycle

| ইভেন্ট | কী হয় |
|---|---|
| `init()` | handler তৈরি, route registration, bridge attach |
| `load(source)` | WebView লোড, bootstrap inject |
| `onPause()` | WebView pause (JS timer থামে) |
| `onResume()` | WebView resume |
| `onDestroy()` | bridge detach, WebView destroy, listener clear |
| project switch | `setProjectId()` → storage scope বদলায় |

## ৭. যা এই আর্কিটেকচারে নেই

| জিনিস | অবস্থা |
|---|---|
| DI container (Hilt/Dagger) | নেই — manual wiring |
| Kotlin | নেই — পুরোটাই Java 17 |
| Jetpack Compose | নেই — View system + XML |
| React Native / Flutter | নেই |
| Service worker | নেই (WebView-এ সীমিত) |
| Server-side rendering | নেই — static asset serving |
