> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# NativeRuntime — Web Developer Guide

**Audience:** front-end / web app developers who build HTML, CSS and JavaScript and want to ship it as a native Android app using the NativeRuntime SDK.

**You do not need to write Java.** Everything in this guide is driven from JavaScript through a single bridge object.

---

## 1. What NativeRuntime gives you

NativeRuntime wraps your web app in a native Android shell and exposes the shell to JavaScript:

```
┌──────────────────────────────────────────────────────────────┐
│  Native Android shell (built by the SDK, not by you)         │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ Toolbar · Drawer · Bottom navigation · FAB             │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │                                                        │  │
│  │   YOUR WEB APP  (HTML / CSS / JS)                      │  │
│  │   window.NativeRuntime.ui / web / device / storage     │  │
│  │                                                        │  │
│  ├────────────────────────────────────────────────────────┤  │
│  │ Toasts · Dialogs · Bottom sheets · Loading overlay     │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

Your web app can:

| Capability | Namespace | Examples |
| --- | --- | --- |
| Drive the native chrome | `ui.*` | set toolbar title, open drawer, select tab, show badge |
| Show native overlays | `ui.*` | alert, confirm, toast, snackbar, bottom sheet, loading |
| Control the WebView | `web.*` | navigate, zoom, cookies, cache, evaluate JS, change source |
| Read device & app data | `device.*` | model, OS, screen, battery, network, locale, safe area |
| Use device hardware | `device.*` | clipboard, share sheet, vibrate, orientation, brightness |
| Persist data natively | `storage.*` | key/value store that survives reloads and restarts |

---

## 2. The 60-second version

```html
<script src="native-runtime.js"></script>
<script>
  NativeRuntime.ui.toast("Hello from the web", "success");
  NativeRuntime.ui.setTitle("My App");

  NativeRuntime.device.getInfo().then(function (info) {
    console.log(info.model, info.osVersion);
  });

  NativeRuntime.storage.set("token", "abc123");
</script>
```

That is the whole integration surface. Everything else in this guide is detail.

---

## 3. Document map

| # | Document | Read it when |
| --- | --- | --- |
| 01 | [Getting Started](01_GETTING_STARTED.md) | First integration, project layout, packaging |
| 02 | [Bridge Protocol](02_BRIDGE_PROTOCOL.md) | You need the raw message format, timeouts, error handling |
| 03 | [UI API](03_UI_API.md) | Every `ui.*` action, parameter and response |
| 04 | [Web API](04_WEB_API.md) | Every `web.*` action and WebSettings toggle |
| 05 | [Device API](05_DEVICE_API.md) | Every `device.*` action and its response shape |
| 06 | [Storage API](06_STORAGE_API.md) | Every `storage.*` action |
| 07 | [Events](07_EVENTS.md) | Reacting to native callbacks (dialog results, menu picks) |
| 08 | [Packaging & Sources](08_PACKAGING_AND_SOURCES.md) | Assets, ZIP bundles, remote URLs, SPA routing |
| 09 | [Recipes](09_RECIPES.md) | Copy-paste patterns for common screens |
| 10 | [Troubleshooting](10_TROUBLESHOOTING.md) | Something does not work |
| 11 | [Security](11_SECURITY.md) | Before you ship to production |

**Also read:** [`../AUDIT_AND_UPDATE_REPORT.md`](../AUDIT_AND_UPDATE_REPORT.md) — a verified list of which native features actually work today, which are broken, and how to fix them. Read this **before** you plan a production release.

---

## 4. Feature status at a glance

This table is the honest summary of the audit. Details and file/line evidence are in the audit report.

| Area | Status | Notes |
| --- | --- | --- |
| Bridge transport (`call`, `on`, `ping`) | ✅ Works | Promise-based, request/response + events |
| `ui.showToast` | ✅ Works | |
| `ui.showAlert` / `showConfirm` | ✅ Works | Material dialog on the UI thread |
| `ui.showBottomSheet` | ✅ Works | |
| `ui.showActionSheet` | ⚠️ Alias | Same implementation as bottom sheet |
| `ui.showLoading` / `hideLoading` | ✅ Works | |
| `ui.showSnackbar` | ✅ Works | Fixed in 1.1.1 |
| `ui.showPopupMenu` | ✅ Works | Fixed in 1.1.1 |
| Toolbar / drawer / tab / FAB control | ✅ Works | |
| System bars / fullscreen / immersive | ✅ Works | |
| `ui.setComponentVisible` | ⚠️ Partial | Live toggle for 5 component types only |
| `ui.applyTheme` | ⚠️ Partial | Only `primaryColor` + `surfaceColor` applied |
| `web.*` (all actions) | ✅ Works | Explicitly marshalled to the main thread |
| `device.getInfo` family | ✅ Works | Requires an attached Activity for screen/safe-area fields |
| `device` clipboard / share / vibrate / orientation | ✅ Works | |
| `device.getBrightness` | ⚠️ Partial | Returns the window override, not system brightness |
| `storage.*` | ✅ Works | SharedPreferences, **not** encrypted |
| Events (`ui.confirm`, `ui.actionSelected`, …) | ✅ Works | |
| Unknown action names | ❌ Silent success | Returns the echoed payload instead of an error |

---

## 5. Minimum requirements

| Item | Value |
| --- | --- |
| Android API | 21 (Android 5.0) and above |
| Target / compile SDK | 36 |
| Java | 17 |
| WebView | System WebView, JavaScript enabled |
| Bridge interface name | `NativeRuntimeBridge` (default) |

---

## 6. Where the sample lives

The repository ships a working reference web app:

```
app/src/main/assets/
├── index.html          # demo page with a button for every action
├── about.html
├── contact.html
├── css/style.css
├── js/app.js           # the reference JS wrapper (window.NativeRuntime)
└── webapp.zip          # the same app packaged as a ZIP bundle
```

`app/src/main/assets/js/app.js` is the canonical wrapper. The `examples/webapp-starter/` folder in this repository contains a cleaned-up, documented copy you can drop into your own project.
