> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# 06 — `storage.*` Persistent Storage

A native key/value store backed by Android `SharedPreferences`. Values survive WebView reloads,
source switches and app restarts.

> ⚠️ **Not encrypted.** Despite the ecosystem's "AES encrypted storage" marketing, the bridge
> handler writes to a plain `SharedPreferences` file named `native_runtime_web_storage`.
> Do not store tokens, passwords or personal data here without an additional encryption layer.
> See [audit #17](../AUDIT_AND_UPDATE_REPORT.md#h5-storage-is-plaintext-not-encrypted).

---

## 1. API

| Action | Payload | Returns |
| --- | --- | --- |
| `storage.get` | `{ key }` | `{ key, value, exists }` |
| `storage.set` | `{ key, value }` | `{ saved: true }` |
| `storage.remove` | `{ key }` | `{ removed: true }` |
| `storage.clear` | `{}` | `{ cleared: true }` |
| `storage.keys` | `{}` | `{ keys: [...] }` |
| `storage.has` | `{ key }` | `{ exists: boolean }` |
| `storage.getAll` | `{}` | flat map of every key/value |

---

## 2. Examples

```js
// Write
await NativeRuntime.storage.set("token", "abc123");
await NativeRuntime.storage.set("onboarded", true);
await NativeRuntime.storage.set("launchCount", 7);

// Read
var r = await NativeRuntime.storage.get("token");
if (r.exists) console.log(r.value);

// Check
var h = await NativeRuntime.storage.has("token");   // { exists: true }

// Enumerate
var k = await NativeRuntime.storage.keys();         // { keys: ["token", "onboarded", ...] }
var all = await NativeRuntime.storage.getAll();     // { token: "abc123", onboarded: true, ... }

// Delete
await NativeRuntime.storage.remove("token");
await NativeRuntime.storage.clear();
```

---

## 3. Type handling

`storage.set` preserves the JavaScript type for the four primitive types Android supports:

| JS value | Stored as | Read back as |
| --- | --- | --- |
| `"abc"` | String | `"abc"` |
| `true` / `false` | Boolean | `true` / `false` |
| `42` | Integer | `42` |
| `9007199254740991` | Long | number |
| `1.5` | Float | `1.5` |
| `null` | String `""` | `""` |
| object / array | **String** (`"[object Object]"`) | string |

> ⚠️ Objects and arrays are **not** serialized. Always `JSON.stringify` before storing:
>
> ```js
> await NativeRuntime.storage.set("cart", JSON.stringify(cart));
> var raw = await NativeRuntime.storage.get("cart");
> var cart = raw.exists ? JSON.parse(raw.value) : [];
> ```

---

## 4. Recommended wrapper

```js
var Store = {
  get: function (key, fallback) {
    return NativeRuntime.storage.get(key).then(function (r) {
      return r.exists ? r.value : fallback;
    });
  },
  set: function (key, value) {
    return NativeRuntime.storage.set(key, value);
  },
  getJSON: function (key, fallback) {
    return Store.get(key, null).then(function (raw) {
      if (raw == null) return fallback;
      try { return JSON.parse(raw); } catch (e) { return fallback; }
    });
  },
  setJSON: function (key, value) {
    return Store.set(key, JSON.stringify(value));
  }
};

// Usage
Store.setJSON("cart", [{ id: 1, qty: 2 }]);
Store.getJSON("cart", []).then(renderCart);
```

---

## 5. Limits and behaviour

| Property | Value |
| --- | --- |
| Backing store | `SharedPreferences` (`native_runtime_web_storage`) |
| Scope | Per app package, shared across all WebView sources |
| Survives app restart | Yes |
| Survives app uninstall | No |
| Survives "Clear data" | No |
| Max value size | Practical limit ~1 MB per value; keep values small |
| Thread safety | `apply()` is asynchronous; a read immediately after a write may race |

> Because `apply()` is asynchronous, do not write and immediately read in the same tick if the
> value is critical. Await the `set` Promise first — the handler calls `apply()` before returning,
> which is usually enough, but `commit()` semantics are not guaranteed.

---

## 6. When to use `localStorage` instead

`localStorage` works inside the WebView (DOM storage is enabled by default) and is synchronous.
Use it for UI state; use `storage.*` when you need the value to be readable by native code or to
survive a source switch to a different origin.

| Need | Use |
| --- | --- |
| UI preferences, draft text | `localStorage` |
| Auth token readable by native | `storage.*` |
| Data shared across asset/remote sources | `storage.*` |
| Large blobs | Neither — use IndexedDB or a native file API |
