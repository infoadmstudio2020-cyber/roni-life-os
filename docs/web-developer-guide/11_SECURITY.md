> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# 11 — Security

Read this before shipping. The current build has **no origin validation** on the bridge, which
means any page loaded in the WebView can call native APIs.

---

## 1. The core risk

`WebBridge` attaches a JavaScript interface to the WebView:

```java
webView.addJavascriptInterface(bridge, "NativeRuntimeBridge");
```

`addJavascriptInterface` exposes the object to **every frame of every page** loaded in that
WebView — including a remote page you navigate to, an ad iframe, or a page reached through a
redirect.

`BridgeSecurityValidator` exists but only checks that `id` and `action` are non-empty:

```java
public boolean isMessageValid(String json) {
    // parses JSON, checks id != null && action != null
}
```

`isOriginValid(...)` is defined but **never called** from the message path.

**Consequence:** if your app can be navigated to an attacker-controlled URL, that page can call
`device.readClipboard`, `storage.getAll`, `device.openUrl`, `device.finish`, and every other
action. See [audit #11](../AUDIT_AND_UPDATE_REPORT.md#h6-no-origin-validation-on-bridge-messages).

---

## 2. Mitigations you control (web side)

### 2.1 Never navigate to untrusted URLs in the same WebView

```js
// Bad — loads a third-party page into the bridged WebView
location.href = userSuppliedUrl;

// Good — hand it to the system browser
NativeRuntime.ui.openExternal(userSuppliedUrl);
```

### 2.2 Validate before you render links

```js
function safeOpen(url) {
  var u;
  try { u = new URL(url); } catch (e) { return; }
  if (u.protocol !== "https:") return;
  if (!/\.(example\.com|example\.org)$/.test(u.hostname)) {
    return NativeRuntime.ui.openExternal(url);
  }
  location.href = url;
}
```

### 2.3 Do not put secrets in the page

Anything in `assets/` is extractable from the APK with `unzip`. Anything in `localStorage` or
`storage.*` is readable on a rooted device. API keys belong on your server.

### 2.4 Treat `storage.*` as public

It is a plain `SharedPreferences` file. On a rooted or debuggable device it is readable with
`adb shell run-as <pkg> cat shared_prefs/native_runtime_web_storage.xml`.

If you must store a token:

- keep it short-lived,
- bind it to the device on the server,
- revoke on logout,
- never store a refresh token.

---

## 3. Mitigations the Android developer must apply

| # | Fix | Why |
| --- | --- | --- |
| 1 | Validate the origin in `WebBridge.handleWebMessage` using `webView.getUrl()` | Blocks calls from foreign pages |
| 2 | Restrict navigation: `shouldOverrideUrlLoading` → open non-allow-listed hosts externally | Prevents the WebView from ever hosting a foreign origin |
| 3 | Set `setAllowFileAccess(false)`, `setAllowContentAccess(false)` unless required | Prevents local file exfiltration |
| 4 | Keep `setMixedContentMode(MIXED_CONTENT_NEVER_ALLOW)` | Blocks HTTP injection |
| 5 | Keep `setSafeBrowsingEnabled(true)` | Malware/phishing blocking |
| 6 | Disable `setJavaScriptCanOpenWindowsAutomatically(false)` | Prevents popup abuse |
| 7 | Disable WebView debugging in release (`WebView.setWebContentsDebuggingEnabled(false)`) | Prevents remote inspection |
| 8 | Do not enable `setAllowUniversalAccessFromFileURLs` | Classic file:// escape |
| 9 | Encrypt `storage.*` with `EncryptedSharedPreferences` or the existing `NativeStorageEngine` | Protects tokens at rest |
| 10 | Gate destructive actions (`device.finish`, `device.openApp`, `device.openFile`) behind an allow-list | Limits blast radius |

---

## 4. Permission model

| Capability | Permission | Declared by | Works today |
| --- | --- | --- | --- |
| Internet | `INTERNET` | `native-network-engine` | ✅ |
| Network state | `ACCESS_NETWORK_STATE` | `native-network-engine`, `native-platform-engine` | ✅ |
| Vibrate | `VIBRATE` | `native-platform-engine` | ✅ |
| Biometric | `USE_BIOMETRIC` | `native-security-engine` | ✅ (engine only) |
| Camera | `CAMERA` | not declared | ❌ denied by host |
| Microphone | `RECORD_AUDIO` | not declared | ❌ denied by host |
| Location | `ACCESS_FINE_LOCATION` | not declared | ❌ denied by host |
| Storage | `READ/WRITE_EXTERNAL_STORAGE` | not declared | ❌ |

`WebViewHost.onPermissionRequest` calls `deny()` unconditionally, and
`onGeolocationPermissionsShowPrompt` calls `callback.invoke(origin, false, false)`.
Any web feature that needs a runtime permission will fail silently.

---

## 5. Data flow map

```
assets/  ──► AssetResourceProvider ──► https://appassets.androidplatform.net
                                              │
                                              ▼
                                        WebView (JS)
                                              │  JSON over addJavascriptInterface
                                              ▼
                                        WebBridge
                                              │
                        ┌─────────────────────┼─────────────────────┐
                        ▼                     ▼                     ▼
                   ui.* handler         device.* handler      storage.* handler
                        │                     │                     │
                   Android views        Clipboard/Intent      SharedPreferences
                                                              (plaintext)
```

Every arrow crossing into native is a trust boundary. Today only the first one (message shape)
is checked.

---

## 6. Pre-release checklist

```
[ ] No secrets in assets/ or JS bundles
[ ] All API calls use HTTPS
[ ] No user-supplied URL is loaded into the bridged WebView
[ ] External links go through ui.openExternal
[ ] storage.* holds no long-lived credentials
[ ] WebView debugging disabled in release
[ ] ProGuard/R8 enabled for release builds
[ ] Release build signed with a release keystore (not debug)
[ ] Origin validation added to WebBridge (Android developer)
[ ] Navigation allow-list added (Android developer)
[ ] Permission requests handled or features removed from the UI
```

---

## 7. Reporting

If you find a vulnerability, do not open a public issue. Contact the maintainer listed in the
repository `README.md` with reproduction steps and the affected module.
