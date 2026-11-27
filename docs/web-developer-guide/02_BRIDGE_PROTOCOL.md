> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# 02 — Bridge Protocol

The bridge is a JSON request/response channel between your JavaScript and the native layer,
plus a one-way event channel from native to JavaScript.

You normally use the wrapper (`NativeRuntime.ui.toast(...)`), but understanding the wire format
is essential for debugging.

---

## 1. Objects on `window`

| Object | Injected when | Purpose |
| --- | --- | --- |
| `window.NativeRuntimeBridge` | Before page load (`addJavascriptInterface`) | Raw native interface. Has `postMessage(json)` and `ping()`. |
| `window.NativeRuntimeBridgeClient` | After `onPageFinished` | Promise wrapper. Has `call`, `on`, `off`, `ping`, `_handleNativeMessage`. |
| `window.NativeRuntime` | By your wrapper script | Friendly namespaces (`ui`, `web`, `device`, `storage`). |

The interface name is configurable. The default is `NativeRuntimeBridge`; if the host changes it,
`window.NativeRuntimeBridge` becomes `window.<YourName>` and the client becomes `window.<YourName>Client`.

---

## 2. Message envelope

Every message — in both directions — is a JSON object with the same shape:

```json
{
  "id": "req_1730000000000_1",
  "type": "REQUEST",
  "action": "ui.showToast",
  "payload": { "message": "Hi" },
  "error": null,
  "success": true,
  "timestamp": 1730000000000
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | string | Correlation id. Native echoes it back. |
| `type` | `REQUEST` \| `RESPONSE` \| `ERROR` \| `EVENT` \| `PING` | Message kind |
| `action` | string | Action name (`ui.showToast`) or event topic (`ui.confirm`) |
| `payload` | object | Parameters (request) or result data (response/event) |
| `error` | string \| null | Human-readable error message when `success` is false |
| `success` | boolean | `true` for responses and events, `false` for errors |
| `timestamp` | number | Epoch milliseconds |

---

## 3. Request lifecycle

```
JS                                          Native
 │                                             │
 │  postMessage({type:"REQUEST", ...})         │
 ├────────────────────────────────────────────►│  WebBridge.handleWebMessage()
 │                                             │    ├─ validate
 │                                             │    ├─ split "ui.showToast" → path "/ui", action "showToast"
 │                                             │    ├─ NativeRouter.dispatch()  (background thread)
 │                                             │    └─ handler.handleRequest()
 │  {type:"RESPONSE", id:"req_…", payload:{…}}  │
 │◄────────────────────────────────────────────┤  WebBridge.onSuccess()
 │                                             │
```

### Action name forms

All three forms are accepted and normalized to the same route:

| You send | Path | Action |
| --- | --- | --- |
| `ui.showToast` | `/ui` | `showToast` |
| `/ui/showToast` | `/ui` | `showToast` |
| `showToast` | `/showToast` | `showToast` |

Use the dotted form. It is the only one that is unambiguous.

---

## 4. Response payloads

A successful response carries the handler's data map directly in `payload`:

```js
NativeRuntimeBridgeClient.call("device.getBattery", {})
  .then(function (data) {
    // data = { level: 87, scale: 100, percent: 87, status: 2, plugged: 1, temperature: 310, voltage: 4200 }
  });
```

An error response has `type: "ERROR"`, `success: false`, `payload: null`, and a message in `error`:

```js
NativeRuntimeBridgeClient.call("web.setZoom", { zoom: 120 })
  .catch(function (err) {
    console.warn(err.message);   // e.g. "No active WebView is attached"
  });
```

### Error codes you may see

| `error` message | Cause |
| --- | --- |
| `Security validation failed` | Message had no `id` or no `action` |
| `No active WebView is attached` | `web.*` called before the WebView exists |
| `No active Activity is attached` | `device.*` called with no Activity |
| `No active Context is attached` | `storage.*` called with no Context |
| `Action not supported: <name>` | Handler received an action it does not implement |
| `Timed out waiting for WebView command on the main thread` | Main thread blocked > 5 s |

> ⚠️ **Important:** an **unknown route** does *not* produce an error. `WebBridge` falls back to
> echoing your payload back with `success: true`. A typo such as `ui.showTost` will resolve
> successfully and do nothing. See [Troubleshooting §1](10_TROUBLESHOOTING.md#1-my-call-succeeds-but-nothing-happens).

---

## 5. Events (native → JS)

Native code pushes events with `type: "EVENT"`. The `action` field is the topic:

```json
{
  "id": "8f3c…",
  "type": "EVENT",
  "action": "ui.confirm",
  "payload": { "confirmed": true },
  "success": true,
  "timestamp": 1730000000000
}
```

Subscribe with:

```js
NativeRuntimeBridgeClient.on("ui.confirm", function (data) {
  console.log(data.confirmed);
});
```

Full topic list: [07 — Events](07_EVENTS.md).

---

## 6. Ping

A synchronous liveness check that does not go through the router:

```js
var pong = window.NativeRuntimeBridge.ping();   // "pong"
```

Use it to detect whether you are running inside the native shell:

```js
var isNative = !!(window.NativeRuntimeBridge && window.NativeRuntimeBridge.ping);
```

---

## 7. Timeouts and threading

| Layer | Behaviour |
| --- | --- |
| `web.*` handler | Marshalled to the Android main thread with a **5 second** timeout |
| `ui.*` handler | Executed on the router's background thread pool; individual managers marshal to the UI thread themselves |
| `device.*` handler | Executed on the router's background thread pool |
| `storage.*` handler | Executed on the router's background thread pool |
| Router dispatch | Fixed thread pool sized `max(2, CPU cores)` |

Every `ui.*` action is safe to call from the bridge: each manager marshals to the UI thread
itself. `ui.showSnackbar` and `ui.showPopupMenu` were the two exceptions before 1.1.1 and are
now fixed.

---

## 8. Writing your own wrapper

If you do not want to ship `native-runtime.js`, this is the minimum viable client:

```js
(function () {
  var NAME = "NativeRuntimeBridge";
  var callbacks = {};
  var listeners = {};
  var seq = 0;

  function client() { return window[NAME + "Client"]; }

  window[NAME + "Client"] = {
    call: function (action, payload) {
      return new Promise(function (resolve, reject) {
        var id = "req_" + Date.now() + "_" + (++seq);
        callbacks[id] = { resolve: resolve, reject: reject };
        var iface = window[NAME];
        if (!iface || !iface.postMessage) {
          delete callbacks[id];
          return reject(new Error("Native bridge is not available"));
        }
        iface.postMessage(JSON.stringify({
          id: id, type: "REQUEST", action: action,
          payload: payload || {}, timestamp: Date.now()
        }));
      });
    },
    on: function (topic, fn) { (listeners[topic] = listeners[topic] || []).push(fn); },
    off: function (topic, fn) {
      listeners[topic] = (listeners[topic] || []).filter(function (l) { return l !== fn; });
    },
    _handleNativeMessage: function (raw) {
      var msg = typeof raw === "string" ? JSON.parse(raw) : raw;
      if (msg.type === "RESPONSE" || msg.type === "ERROR") {
        var cb = callbacks[msg.id];
        if (!cb) return;
        delete callbacks[msg.id];
        if (msg.success) cb.resolve(msg.payload);
        else cb.reject(new Error(msg.error || "Native error"));
      } else if (msg.type === "EVENT") {
        (listeners[msg.action] || []).forEach(function (fn) {
          try { fn(msg.payload); } catch (e) {}
        });
      }
    }
  };
})();
```

---

## 9. Debugging the wire

Enable WebView debugging on the native side:

```java
WebHostConfig.builder().debugLogging(true).build()
```

Then open `chrome://inspect` on your desktop Chrome and inspect the WebView. Every message is
visible in the console if you log it:

```js
var raw = window.NativeRuntimeBridgeClient.call;
window.NativeRuntimeBridgeClient.call = function (action, payload) {
  console.log("→", action, payload);
  return raw(action, payload).then(function (r) {
    console.log("←", action, r);
    return r;
  }, function (e) {
    console.error("✗", action, e.message);
    throw e;
  });
};
```

Native-side logs use the tag `NativeWebBridgeSDK` and `NativeRuntimeWebHost`.
