# WebBridge Overview — যোগাযোগের প্রোটোকল

## ১. দুইটি JavaScript object

পেজ লোড হওয়ার পর `WebViewHost.onPageFinished()` দুটি জিনিস inject করে:

### ১.১ `window.NativeRuntimeBridge` (native, `@JavascriptInterface`)

| মেথড | সিগনেচার | রিটার্ন |
|---|---|---|
| `postMessage` | `postMessage(jsonMessage: string): void` | কিছু না (fire-and-forget) |
| `ping` | `ping(): string` | সবসময় `"pong"` |

এটি `NativeJavascriptInterface` ক্লাস থেকে আসে। **এটাই একমাত্র native entry point।**

### ১.২ `window.NativeRuntimeBridgeClient` (JavaScript, inject করা)

| মেথড | সিগনেচার | রিটার্ন |
|---|---|---|
| `call` | `call(action: string, payload?: object): Promise<any>` | native response payload |
| `ping` | `ping(): string` | `"pong"` বা `"pong_fallback"` |
| `on` | `on(topic: string, listener: (payload) => void): void` | — |
| `off` | `off(topic: string, listener: Function): void` | — |
| `_handleNativeMessage` | `_handleNativeMessage(rawJson: string): void` | internal |

`call()` ভেতরে ভেতরে `postMessage()` ডাকে এবং একটি Promise ফেরত দেয়।

> **নোট:** `NativeRuntimeBridgeClient` শুধু `onPageFinished`-এ inject হয়। পেজের প্রথম
> `<script>` ট্যাগ চলার সময় এটি **নাও থাকতে পারে**। তাই সবসময় guard দিন।

---

## ২. Message envelope

### ২.১ Web → Native (REQUEST)

```json
{
  "id": "req_1737000000000_1",
  "type": "REQUEST",
  "action": "ui.showToast",
  "payload": { "message": "হ্যালো" },
  "timestamp": 1737000000000
}
```

| ফিল্ড | টাইপ | বাধ্যতামূলক | নিয়ম |
|---|---|---|---|
| `id` | string | ✅ | খালি হলে `Security validation failed` |
| `type` | string | ❌ | ডিফল্ট `REQUEST` |
| `action` | string | ✅ | খালি হলে reject |
| `payload` | object | ❌ | ডিফল্ট `{}` |
| `timestamp` | number | ❌ | ডিফল্ট এখন |

### ২.২ Native → Web (RESPONSE)

```json
{
  "id": "req_1737000000000_1",
  "type": "RESPONSE",
  "action": "ui.showToast",
  "success": true,
  "timestamp": 1737000000123,
  "payload": { "shown": true }
}
```

### ২.৩ Native → Web (ERROR)

```json
{
  "id": "req_1737000000000_1",
  "type": "ERROR",
  "action": "ui.showToast",
  "success": false,
  "timestamp": 1737000000123,
  "error": "UNKNOWN_ACTION",
  "payload": {}
}
```

`client.call()` এই ক্ষেত্রে `Promise.reject(new Error("UNKNOWN_ACTION"))` করে।

### ২.৪ Native → Web (EVENT)

```json
{
  "id": "uuid",
  "type": "EVENT",
  "action": "ui.confirm",
  "success": true,
  "timestamp": 1737000000123,
  "payload": { "confirmed": true }
}
```

`client.on('ui.confirm', fn)` দিয়ে ধরতে হয়।

### ২.৫ PING / PONG

```json
{ "id": "p1", "type": "PING", "action": "ping", "payload": {} }
```
উত্তর:
```json
{ "id": "p1", "type": "PING", "action": "ping", "success": true,
  "payload": { "pong": true, "serverTime": 1737000000123 } }
```

---

## ৩. Action নামের তিনটি রূপ

`WebBridge.handleWebMessage()` এই তিনটিই গ্রহণ করে:

| লেখা | path | action |
|---|---|---|
| `ui.showToast` | `/ui` | `showToast` |
| `/ui/showToast` | `/ui` | `showToast` |
| `showToast` | `/showToast` | `showToast` |

**সুপারিশ:** সবসময় ডট ফর্ম (`ui.showToast`) ব্যবহার করুন। এটা সবচেয়ে স্পষ্ট এবং
`SUPPORTED_ACTIONS` তালিকার উপর নির্ভর করে না।

---

## ৪. Origin gate (নিরাপত্তার প্রথম স্তর)

`BridgeConfig` ডিফল্টে `requireOriginValidation = true`।

```
allow-list (ডিফল্ট):
  https://appassets.androidplatform.net          ← bundled asset origin
  + https://<slug>-<hash8>.runtime.local         ← প্রতি Project-এর নিজের origin
```

প্রতিটি inbound message-এ:

```java
String origin = originProvider.getCurrentOrigin();   // webView.getUrl() থেকে
if (!securityValidator.isOriginValid(origin)) {
    → ERROR "ORIGIN_REJECTED: <origin>"
}
```

- `origin == null` (যেমন `about:blank`, `data:`) → **reject**
- allow-list খালি → **সব reject**
- `"*"` যোগ করলে সব allow (কিন্তু `WebViewHost` কখনো `*` যোগ করে না)

`WebViewHost.trustBridgeOrigin(host)` শুধু **যে host লোড হচ্ছে সেটাই** যোগ করে।

---

## ৫. Size gate

```java
int size = rawJson.getBytes(UTF_8).length;
if (size > config.getMaxMessageBytes()) {   // ডিফল্ট 1,048,576 (1 MiB)
    → ERROR "PAYLOAD_TOO_LARGE"
}
```

**এর মানে:** ১ MiB-এর বড় JSON পাঠানো যাবে না। বড় ডেটা (ছবি, ফাইল) base64 করে পাঠাবেন না —
`RONiDownload` বা `fetch` ব্যবহার করুন।

---

## ৬. Timeout

`BridgeConfig.timeoutMs` ডিফল্ট **10000 ms**।

`WebBridge` একটি daemon thread (`native-bridge-timeout`) চালায় যা প্রতি
`min(timeout, 5000) ms`-এ pending request স্ক্যান করে। সময় পার হলে:

```json
{ "type": "ERROR", "error": "REQUEST_TIMEOUT" }
```

JS-এ:
```js
try {
  await NativeRuntimeBridgeClient.call('web.evaluate', { script: '1+1' });
} catch (e) {
  if (e.message === 'REQUEST_TIMEOUT') { /* native সাড়া দেয়নি */ }
}
```

> **সতর্কতা:** `web.evaluate` নিজেই ৩ সেকেন্ড wait করে, এবং `NativeWebCommandHandler`
> main thread-এ ১০ সেকেন্ড পর্যন্ত অপেক্ষা করে। তাই ভারী script দিলে bridge timeout
> (১০s) আগে fire করতে পারে।

---

## ৭. Response গুলো কোথায় যায়

`WebBridge.dispatchJs()` এই script inject করে:

```js
if (window['NativeRuntimeBridgeClient'] &&
    window['NativeRuntimeBridgeClient']._handleNativeMessage) {
  window['NativeRuntimeBridgeClient']._handleNativeMessage("<JSON-escaped string>");
}
```

JSON টি `JSONObject.quote()` দিয়ে escape করা হয় — তাই payload-এ `"`, `</script>`,
U+2028, U+2029 থাকলেও script ভাঙে না।

---

## ৮. একাধিক WebView

`WebBridge` **process-wide singleton**। `WebViewHost` প্রতিবার attach করার সময়
`setOriginProvider` ও `setWebEvaluator` **replace** করে।

**ফলাফল:** একই সময়ে দুইটি Project runtime সক্রিয় থাকলে, শেষ attach করা WebView-ই
response পায়। বাস্তবে `ProjectActivity` প্রতি Project-এ আলাদা task খোলে এবং একটিই
foreground-এ থাকে, তাই ব্যবহারিক সমস্যা হয় না — কিন্তু ব্যাকগ্রাউন্ড Project-এর
pending call হারিয়ে যেতে পারে।

---

## ৯. Bridge config বদলানো

```java
BridgeConfig cfg = BridgeConfig.builder()
    .interfaceName("NativeRuntimeBridge")   // ⚠️ runtime-এ বদলানো যায় না
    .timeoutMs(15000)
    .maxMessageBytes(2 * 1024 * 1024)
    .debugLogging(true)
    .requireOriginValidation(true)
    .addAllowedOrigin("https://my.example")
    .build();

NativeWebBridgeSDK.getInstance().init(context, cfg);
```

`interfaceName` একবার `addJavascriptInterface`-এ বাঁধা হয়ে গেলে বদলানো যায় না —
`WebBridge.init()` ভিন্ন নাম চাইলে warning লগ করে পুরনো নামই রাখে।

---

## ১০. চেকলিস্ট

- [ ] `typeof NativeRuntimeBridgeClient !== 'undefined'` চেক করেছি
- [ ] প্রতিটি `call()`-এ `.catch()` দিয়েছি
- [ ] payload ১ MiB-এর নিচে
- [ ] action নাম এই ডকুমেন্টের তালিকা থেকে নিয়েছি
- [ ] event listener `on()` দিয়ে আগে থেকে রেজিস্টার করেছি
- [ ] `localStorage`-এর বদলে `storage.*` ব্যবহার করেছি
