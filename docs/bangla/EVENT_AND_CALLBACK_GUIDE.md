# Event ও Callback Guide

## ১. দুই ধরনের callback

| ধরন | কখন | কীভাবে |
|---|---|---|
| **Promise** | এক-বারের ফলাফল (request → response) | `await call()` |
| **Event** | পরে যেকোনো সময় ঘটা (user action) | `on(topic, fn)` |

`ui.showConfirm` একটি **দুই-ধাপ** কল: প্রথম ধাপে Promise resolve হয় (dialog দেখানো হয়েছে),
দ্বিতীয় ধাপে ইভেন্ট আসে (ব্যবহারকারী কী চাপল)।

---

## ২. সব native ইভেন্টের তালিকা

| Topic | Payload | কে পাঠায় |
|---|---|---|
| `ui.confirm` | `{confirmed: boolean}` | `ui.showConfirm` |
| `ui.actionSelected` | `{title: string, index: number}` | `ui.showBottomSheet`, `ui.showActionSheet` |
| `ui.popupItemSelected` | `{title: string, index: number}` | `ui.showPopupMenu` |
| `ui.snackbarAction` | `{actionTitle: string}` | `ui.showSnackbar` |
| `ui.toolbarAction` | `{id: string, label: string}` | `ui.addToolbarAction` |
| `ui.drawerItemSelected` | `{id: string, title: string}` | `ui.addDrawerItem` |
| যেকোনো custom | যা পাঠানো হয়েছে | `web.emitEvent` বা `WebBridge.emitToWeb` |

> এই সাতটি ছাড়া অন্য কোনো built-in ইভেন্ট নেই। `page.loaded`, `app.pause`,
> `network.changed`, `battery.changed` — এগুলো **নেই**।

---

## ৩. `on()` / `off()` এর সঠিক ব্যবহার

```js
function onConfirm(handler) {
  const wrapped = function (payload) { handler(!!payload.confirmed); };
  NativeRuntimeBridgeClient.on('ui.confirm', wrapped);
  return function () {
    NativeRuntimeBridgeClient.off('ui.confirm', wrapped);
  };
}

const unsubscribe = onConfirm(function (ok) {
  console.log('উত্তর:', ok);
  unsubscribe();          // একবারই দরকার
});
```

**নিয়ম:**
1. `on()`-এ যে **একই function reference** দিয়েছেন, `off()`-এ সেটাই দিতে হবে।
   `off('ui.confirm', function(){})` কাজ করবে না — নতুন function।
2. একই topic-এ একাধিক listener রাখা যায়; সবাই ডাকা হবে।
3. Listener-এ exception হলে bridge সেটি গিলে ফেলে — বাকি listener চলবে।
4. পেজ reload হলে সব listener মুছে যায় (নতুন JS context)।

---

## ৪. একাধিক listener-এর ক্রম

`BridgeEventBus` `CopyOnWriteArrayList` ব্যবহার করে — **রেজিস্ট্রেশনের ক্রমে** ডাকা হয়।
তবে `NativeRuntimeBridgeClient`-এর ভেতরের map ব্যবহারের ক্রম guarantee করে না।
তাই listener-দের একে অন্যের উপর নির্ভরশীল করবেন না।

---

## ৫. Event-এ reply করা

Event-এ সরাসরি reply করার API নেই। দরকার হলে নতুন `call()` করুন:

```js
NativeRuntimeBridgeClient.on('ui.actionSelected', async function (p) {
  if (p.title === 'শেয়ার') {
    await Native.call('device.shareText', { text: location.href });
  }
});
```

---

## ৬. Custom event (Native → Web)

Java:
```java
Map<String, Object> data = new HashMap<>();
data.put("orderId", 42);
data.put("status", "paid");
WebBridge bridge = NativeWebBridgeSDK.getInstance().getBridge();
bridge.emitToWeb("order.updated", data);
```

JS:
```js
NativeRuntimeBridgeClient.on('order.updated', function (p) {
  console.log(p.orderId, p.status);
});
```

**নিয়ম:** topic নামে ডট ব্যবহার করুন (`order.updated`), সব lowercase রাখুন।

---

## ৭. Custom event (Web → Web, native হয়ে)

```js
// পাঠানো
await Native.call('web.emitEvent', { topic: 'app.refresh', data: { reason: 'pull' } });

// শোনা
NativeRuntimeBridgeClient.on('app.refresh', function (p) {
  console.log(p.reason);
});
```

**কেন এটা ব্যবহার করবেন:** সাধারণত দরকার নেই। একই পেজে ইভেন্ট পাঠাতে
`window.dispatchEvent(new CustomEvent('app:refresh', { detail: {...} }))` অনেক সহজ
এবং native round-trip লাগে না।

---

## ৮. Timeout ও race condition

```js
function callWithTimeout(action, payload, ms) {
  return new Promise(function (resolve, reject) {
    let done = false;
    const timer = setTimeout(function () {
      if (!done) { done = true; reject(new Error('CLIENT_TIMEOUT')); }
    }, ms || 12000);

    NativeRuntimeBridgeClient.call(action, payload || {}).then(function (r) {
      if (!done) { done = true; clearTimeout(timer); resolve(r); }
    }).catch(function (e) {
      if (!done) { done = true; clearTimeout(timer); reject(e); }
    });
  });
}
```

**কেন `done` flag:** timeout-এর পরে response এলে সেটি উপেক্ষা করতে হবে, নাহলে
double-resolve হয় (Promise-এ ক্ষতিকর নয়, কিন্তু logic ভাঙে)।

---

## ৯. Event listener leak প্রতিরোধ

```js
const listeners = [];

function listen(topic, fn) {
  NativeRuntimeBridgeClient.on(topic, fn);
  listeners.push({ topic: topic, fn: fn });
}

function unlistenAll() {
  listeners.forEach(function (l) {
    try { NativeRuntimeBridgeClient.off(l.topic, l.fn); } catch (e) {}
  });
  listeners.length = 0;
}

window.addEventListener('pagehide', unlistenAll);
```

---

## ১০. Debug: সব ইভেন্ট দেখা

```js
// শুধু ডেভেলপমেন্টে
['ui.confirm','ui.actionSelected','ui.popupItemSelected','ui.snackbarAction',
 'ui.toolbarAction','ui.drawerItemSelected'].forEach(function (t) {
  NativeRuntimeBridgeClient.on(t, function (p) {
    console.log('[native event]', t, p);
  });
});
```

---

## ১১. Callback-এর ভুল ধারণা

| ভুল ধারণা | বাস্তব |
|---|---|
| `ui.showConfirm` → `{confirmed:true}` | `{shown:true}`, উত্তর ইভেন্টে |
| `ui.showBottomSheet` → `{index:1}` | `{shown:true}`, index ইভেন্টে |
| `ui.showToast` শেষ হলে callback | কোনো callback নেই |
| `ui.showLoading` auto-hide | নিজে `hideLoading` ডাকতে হবে |
| `device.vibrate` শেষ হলে ইভেন্ট | নেই |
| `web.loadUrl` শেষ হলে ইভেন্ট | `{loaded:true}` মানে শুরু হয়েছে, শেষ নয় |
| `on('page.loaded')` | এমন ইভেন্ট নেই — `window.onload` ব্যবহার করুন |
