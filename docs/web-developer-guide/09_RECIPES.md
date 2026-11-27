> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# 09 — Recipes

Copy-paste patterns for the screens you will actually build.

---

## 1. App bootstrap

Detect the native shell, read the config, then render.

```js
document.addEventListener("DOMContentLoaded", function () {
  var native = !!window.NativeRuntimeBridgeClient;

  if (!native) {
    document.body.classList.add("browser-preview");
    return render({ toolbarEnabled: false });
  }

  NativeRuntime.ui.getConfig().then(function (cfg) {
    document.body.classList.toggle("has-native-toolbar", cfg.toolbarEnabled);
    document.body.classList.toggle("has-native-tabs", cfg.bottomNavEnabled);
    render(cfg);
  });
});
```

```css
body.has-native-toolbar .my-header { display: none; }
body.has-native-tabs    .my-tabbar { display: none; }
```

---

## 2. Native header instead of an HTML header

```js
function setScreen(title, subtitle) {
  NativeRuntime.ui.setTitle(title);
  NativeRuntime.ui.setSubtitle(subtitle || "");
}
```

Add a contextual action:

```js
NativeRuntime.ui.clearToolbarActions();
NativeRuntime.ui.addToolbarAction("save", "Save");

NativeRuntime.on("ui.toolbarAction", function (e) {
  if (e.id === "save") save();
});
```

---

## 3. Tab bar driven by native bottom navigation

```js
var TABS = [
  { id: "tab_home",    label: "Home",    render: renderHome },
  { id: "tab_orders",  label: "Orders",  render: renderOrders },
  { id: "tab_profile", label: "Profile", render: renderProfile }
];

function bootTabs() {
  TABS.forEach(function (t) {
    NativeRuntime.ui.addTab(t.id, t.label);
  });
  NativeRuntime.ui.selectTab(TABS[0].id);
  TABS[0].render();
}

// The native tab bar does not emit a "tab changed" event.
// Mirror the selection into the URL hash so your router can react.
window.addEventListener("hashchange", function () {
  var id = location.hash.replace("#", "") || TABS[0].id;
  var tab = TABS.filter(function (t) { return t.id === id; })[0];
  if (tab) { NativeRuntime.ui.selectTab(tab.id); tab.render(); }
});
```

> There is no `ui.tabSelected` event in the current build. If you need one, ask the Android
> developer to emit it from the `BottomNavigationView` listener, or drive tabs entirely from
> the web layer.

---

## 4. Badge counter

```js
function setUnread(n) {
  NativeRuntime.ui.setBadge("tab_orders", n);
}
```

Call it after every data refresh. `0` clears the badge.

---

## 5. Loading state around a network call

```js
function withLoading(text, promise) {
  NativeRuntime.ui.loading(text, false);
  return promise.finally(function () {
    NativeRuntime.ui.hideLoading();
  });
}

withLoading("Loading orders…", fetch("/api/orders").then(function (r) { return r.json(); }))
  .then(renderOrders)
  .catch(function (e) {
    NativeRuntime.ui.toast("Could not load orders", "error");
  });
```

---

## 6. Form validation with a native alert

```js
function validate(form) {
  if (!form.email) {
    NativeRuntime.ui.alert("Missing email", "Enter your email address to continue.");
    return false;
  }
  return true;
}
```

---

## 7. Destructive action with confirm

```js
function deleteOrder(id) {
  NativeRuntime.ui.confirm("Delete order?", "This cannot be undone.", "Delete", "Cancel");

  NativeRuntime.on("ui.confirm", function onAnswer(e) {
    NativeRuntime.off("ui.confirm", onAnswer);
    if (!e.confirmed) return;
    api.delete("/orders/" + id).then(function () {
      NativeRuntime.ui.toast("Order deleted", "success");
      NativeRuntime.ui.snackbar("Order deleted", "UNDO");
    });
  });
}
```

---

## 8. Pull device info into the page

```js
NativeRuntime.device.getInfo().then(function (d) {
  document.getElementById("device-line").textContent =
    d.manufacturer + " " + d.model + " · Android " + d.osVersion;
});
```

---

## 9. Safe-area aware layout

```css
:root {
  --safe-top: env(safe-area-inset-top, 0px);
  --safe-bottom: env(safe-area-inset-bottom, 0px);
}

.app-bar { padding-top: var(--safe-top); }
.bottom-bar { padding-bottom: var(--safe-bottom); }
```

```html
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
```

Fallback for older WebViews:

```js
NativeRuntime.device.getSafeArea().then(function (a) {
  document.documentElement.style.setProperty("--safe-top", a.safeAreaTop + "px");
  document.documentElement.style.setProperty("--safe-bottom", a.safeAreaBottom + "px");
});
```

---

## 10. Persist a session

```js
var Session = {
  save: function (token, user) {
    return Promise.all([
      NativeRuntime.storage.set("token", token),
      NativeRuntime.storage.set("user", JSON.stringify(user))
    ]);
  },
  load: function () {
    return Promise.all([
      NativeRuntime.storage.get("token"),
      NativeRuntime.storage.get("user")
    ]).then(function (r) {
      if (!r[0].exists) return null;
      return { token: r[0].value, user: JSON.parse(r[1].value || "{}") };
    });
  },
  clear: function () {
    return Promise.all([
      NativeRuntime.storage.remove("token"),
      NativeRuntime.storage.remove("user")
    ]);
  }
};
```

---

## 11. Share a link

```js
function shareOrder(order) {
  NativeRuntime.device.shareText(
    "Order " + order.id + " — " + order.total + "\nhttps://example.com/o/" + order.id,
    "Order " + order.id,
    "Share order"
  );
}
```

---

## 12. Copy to clipboard with feedback

```js
function copy(text) {
  NativeRuntime.device.copyToClipboard(text, "Copied value")
    .then(function () { NativeRuntime.ui.toast("Copied", "success"); })
    .catch(function () { NativeRuntime.ui.toast("Copy failed", "error"); });
}
```

---

## 13. Haptic feedback on a primary button

```js
document.querySelectorAll("[data-haptic]").forEach(function (el) {
  el.addEventListener("click", function () {
    NativeRuntime.device.vibrate();
  });
});
```

---

## 14. Keep the screen on during media playback

```js
var video = document.querySelector("video");

video.addEventListener("play",  function () { NativeRuntime.device.setKeepScreenOn(true); });
video.addEventListener("pause", function () { NativeRuntime.device.setKeepScreenOn(false); });
video.addEventListener("ended", function () { NativeRuntime.device.setKeepScreenOn(false); });
```

---

## 15. Switch to a different web source

```js
function openHelp() {
  NativeRuntime.storage.set("returnTo", location.hash).then(function () {
    NativeRuntime.ui.setWebViewSource({ type: "asset", location: "help.html" });
  });
}

// In help.html
NativeRuntime.storage.get("returnTo").then(function (r) {
  document.getElementById("back").onclick = function () {
    NativeRuntime.ui.setWebViewSource({ type: "asset", location: "index.html" });
  };
});
```

> A source switch destroys the JS heap. Persist anything you need to restore.

---

## 16. Theme sync with the native chrome

```js
function applyDarkMode(isDark) {
  document.documentElement.classList.toggle("dark", isDark);
  NativeRuntime.ui.applyTheme({
    primaryColor: isDark ? "#22d3ee" : "#0d9488",
    backgroundColor: isDark ? "#0f172a" : "#ffffff",
    statusBarColor: isDark ? "#0f172a" : "#ffffff",
    navBarColor: isDark ? "#0f172a" : "#ffffff",
    lightStatusBar: isDark,
    lightNavBar: isDark
  });
}
```

---

## 17. Offline banner

```js
function watchNetwork() {
  function check() {
    NativeRuntime.device.getNetworkState().then(function (n) {
      document.body.classList.toggle("offline", !n.connected);
    });
  }
  check();
  setInterval(check, 15000);
  window.addEventListener("online", check);
  window.addEventListener("offline", check);
}
```

> There is no native network-change event in the current build, so poll or use the browser's
> `online`/`offline` events.

---

## 18. Graceful degradation helper

```js
function safe(fn, fallback) {
  return function () {
    try {
      var p = fn.apply(null, arguments);
      return p && p.catch ? p.catch(function () { return fallback; }) : p;
    } catch (e) {
      return Promise.resolve(fallback);
    }
  };
}

var toast = safe(function (m) { return NativeRuntime.ui.toast(m); }, null);
```

Use this around every native call while the threading defect
([audit #2](../AUDIT_AND_UPDATE_REPORT.md#h2-snackbar-and-popup-menu-are-shown-off-the-main-thread))
is unfixed.
