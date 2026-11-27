> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# 07 — Native Events

Native code pushes events into your page when the user interacts with native UI.
Events are one-way (native → JS) and are delivered through the same bridge.

---

## 1. Subscribing

```js
NativeRuntime.on("ui.confirm", function (data) {
  console.log(data.confirmed);
});

// Unsubscribe
function handler(data) { /* … */ }
NativeRuntime.on("ui.actionSelected", handler);
NativeRuntime.off("ui.actionSelected", handler);
```

Raw form:

```js
window.NativeRuntimeBridgeClient.on("ui.confirm", function (data) { /* … */ });
```

---

## 2. Event catalogue

| Topic | Payload | Emitted by |
| --- | --- | --- |
| `ui.confirm` | `{ confirmed: boolean }` | `ui.showConfirm` |
| `ui.actionSelected` | `{ title, index }` | `ui.showBottomSheet`, `ui.showActionSheet` |
| `ui.toolbarAction` | `{ id, label }` | Toolbar action tap |
| `ui.drawerItemSelected` | `{ id, title }` | Drawer item tap |
| `ui.popupItemSelected` | `{ title, index }` | `ui.showPopupMenu` |
| `ui.snackbarAction` | `{ actionTitle }` | Snackbar action button |

> Events are only emitted for UI that the **native host** created or that the **web layer**
> created through the bridge. A toolbar action added natively with a Java `Runnable` will run
> that `Runnable` and may not emit `ui.toolbarAction`.

---

## 3. Patterns

### Confirm before a destructive action

```js
function confirmDelete() {
  return new Promise(function (resolve) {
    var done = false;
    function onAnswer(e) {
      if (done) return;
      done = true;
      NativeRuntime.off("ui.confirm", onAnswer);
      resolve(e.confirmed);
    }
    NativeRuntime.on("ui.confirm", onAnswer);
    NativeRuntime.ui.confirm("Delete item?", "This cannot be undone.", "Delete", "Cancel");
  });
}

confirmDelete().then(function (ok) {
  if (ok) removeItem();
});
```

> Always remove the listener. `ui.confirm` is a global topic — two dialogs open at once will
> both fire the same listeners.

### Bottom sheet as a picker

```js
function pick(options) {
  return new Promise(function (resolve) {
    function onPick(e) {
      NativeRuntime.off("ui.actionSelected", onPick);
      resolve(options[e.index]);
    }
    NativeRuntime.on("ui.actionSelected", onPick);
    NativeRuntime.ui.bottomSheet("Choose", options.map(function (o) {
      return { title: o.label, destructive: !!o.danger };
    }));
  });
}

pick([{ label: "Camera" }, { label: "Gallery" }]).then(function (choice) {
  console.log(choice.label);
});
```

### Snackbar undo

```js
function archive(id) {
  api.archive(id);
  NativeRuntime.ui.snackbar("Archived", "UNDO");
  NativeRuntime.on("ui.snackbarAction", function undo(e) {
    if (e.actionTitle !== "UNDO") return;
    NativeRuntime.off("ui.snackbarAction", undo);
    api.restore(id);
  });
}
```

### Native → web signalling

The host app can push arbitrary topics:

```js
NativeRuntime.on("app.refresh", function (data) {
  reload();
});
```

```java
// Native side
webViewHost.emitEvent("app.refresh", new HashMap<>());
```

---

## 4. Lifecycle caveats

| Caveat | Consequence |
| --- | --- |
| Listeners live in the JS heap | A page reload or `setWebViewSource` drops all listeners |
| Events are fire-and-forget | If no listener is registered, the event is lost — there is no queue |
| Events are delivered on the WebView's JS thread | Handlers run as normal JS tasks |
| Topic names are case-sensitive | `ui.confirm` ≠ `UI.Confirm` |

Because events are not queued, register listeners **before** triggering the action that emits them.

---

## 5. Debugging

Log every event during development:

```js
["ui.confirm", "ui.actionSelected", "ui.toolbarAction", "ui.drawerItemSelected",
 "ui.popupItemSelected", "ui.snackbarAction"].forEach(function (topic) {
  NativeRuntime.on(topic, function (data) {
    console.log("[native event]", topic, data);
  });
});
```

If nothing arrives, check that the native side actually calls `emitToWeb`. The `ui.*` handler
emits through `NativeWebBridgeSDK.getInstance().getBridge().emitToWeb(topic, data)`, which
requires the bridge to be initialised and a `webEvaluator` to be attached (it is, once the
WebView host is created).
