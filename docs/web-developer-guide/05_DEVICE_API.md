> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# 05 — `device.*` Device & App API

Reads device, app, screen, battery and network information, and drives device hardware:
clipboard, share sheet, vibration, orientation, brightness, keep-screen-on and app launching.

> Most actions require an attached `Activity`. If none is attached the handler returns
> `{ error: "No active Activity is attached" }` for activity-bound actions, and partial data
> for the info actions.

---

## 1. Information

### `device.getInfo`

One call that returns app + device + screen + locale + safe area.

```js
NativeRuntime.device.getInfo().then(function (info) {
  console.log(info.model, info.osVersion, info.screenWidth, info.locale);
});
```

```json
{
  "packageName": "com.example.app",
  "versionName": "1.1.0",
  "versionCode": 2,
  "appLabel": "My App",
  "manufacturer": "Google",
  "brand": "google",
  "model": "Pixel 8",
  "device": "shiba",
  "product": "shiba",
  "osVersion": "14",
  "sdkInt": 34,
  "screenWidth": 1080,
  "screenHeight": 2400,
  "density": 2.75,
  "densityDpi": 440,
  "orientation": "portrait",
  "statusBarHeight": 72,
  "navigationBarHeight": 132,
  "locale": "en_US",
  "language": "en",
  "country": "US",
  "safeAreaTop": 72,
  "safeAreaBottom": 132,
  "safeAreaLeft": 0,
  "safeAreaRight": 0
}
```

> Screen and safe-area fields are omitted when no Activity is attached.

---

### `device.getAppInfo`

```json
{ "packageName": "com.example.app", "versionName": "1.1.0", "versionCode": 2, "appLabel": "My App" }
```

---

### `device.getDeviceInfo`

```json
{
  "manufacturer": "Google", "brand": "google", "model": "Pixel 8",
  "device": "shiba", "product": "shiba", "osVersion": "14", "sdkInt": 34
}
```

---

### `device.getScreenInfo`

```json
{
  "screenWidth": 1080, "screenHeight": 2400, "density": 2.75, "densityDpi": 440,
  "orientation": "portrait", "statusBarHeight": 72, "navigationBarHeight": 132
}
```

All pixel values are **physical pixels**. Convert to CSS pixels with `density`:

```js
NativeRuntime.device.getScreenInfo().then(function (s) {
  var cssWidth = s.screenWidth / s.density;
  console.log(cssWidth + " CSS px wide");
});
```

---

### `device.getLocale`

```json
{ "locale": "en_US", "language": "en", "country": "US" }
```

---

### `device.getSafeArea`

```json
{ "safeAreaTop": 72, "safeAreaBottom": 132, "safeAreaLeft": 0, "safeAreaRight": 0 }
```

Use it when you need exact insets in JS:

```js
NativeRuntime.device.getSafeArea().then(function (a) {
  document.documentElement.style.setProperty("--safe-top", a.safeAreaTop + "px");
});
```

> Prefer CSS `env(safe-area-inset-top)` with `viewport-fit=cover` — it updates automatically on
> rotation and is cheaper than a bridge round-trip.

---

### `device.getNetworkState`

```json
{ "connected": true, "type": "WIFI", "metered": false, "roaming": false }
```

`type` is the Android network type name (`WIFI`, `MOBILE`, `ETHERNET`, …) or `"none"`.

---

### `device.getBattery`

```json
{
  "level": 87, "scale": 100, "percent": 87,
  "status": 2, "plugged": 1, "temperature": 310, "voltage": 4200
}
```

| Field | Meaning |
| --- | --- |
| `percent` | 0–100, or `-1` if unknown |
| `status` | Android `BatteryManager.EXTRA_STATUS` (2 = charging, 3 = discharging, 5 = full) |
| `plugged` | 0 = unplugged, 1 = AC, 2 = USB, 4 = wireless |
| `temperature` | Tenths of a degree Celsius (310 = 31.0 °C) |
| `voltage` | Millivolts |

---

## 2. Clipboard

### `device.copyToClipboard`

```js
NativeRuntime.device.copyToClipboard("INV-2026-0042", "Invoice number");
```

| Param | Type | Default |
| --- | --- | --- |
| `text` | string | — |
| `label` | string | `"NativeRuntime"` |

**Returns:** `{ copied: true }`

### `device.readClipboard`

```js
NativeRuntime.device.readClipboard().then(function (r) {
  console.log(r.text);
});
```

**Returns:** `{ text }`

> On Android 10+ the clipboard is only readable while your app has focus. Reading from a
> background page returns an empty string.

---

## 3. Sharing

### `device.shareText`

Opens the system share sheet.

```js
NativeRuntime.device.shareText(
  "Check out this order: https://example.com/o/42",
  "Order 42",          // subject (email)
  "Share order"        // chooser title
);
```

| Param | Type | Default |
| --- | --- | --- |
| `text` | string | — |
| `subject` | string | `""` |
| `title` | string | `"Share"` |

**Returns:** `{ shared: true }`

---

## 4. Haptics

### `device.vibrate`

```js
NativeRuntime.device.vibrate();
```

**Returns:** `{ vibrated: true }`

> Requires the `VIBRATE` permission, which the platform engine manifest already declares.
> Duration and pattern are fixed by the SDK.

---

## 5. Opening things

| Action | Payload | Returns |
| --- | --- | --- |
| `device.openUrl` | `{ url }` | `{ opened: boolean }` |
| `device.openExternal` | `{ url }` | `{ opened: boolean }` |
| `device.openApp` | `{ packageName }` | `{ opened: boolean }` |
| `device.isAppInstalled` | `{ packageName }` | `{ installed: boolean }` |
| `device.openAppSettings` | `{}` | `{ opened: true }` |
| `device.openFile` | `{ uri, mimeType }` | `{ opened: true }` |
| `device.finish` | `{}` | `{ finished: true }` |

```js
NativeRuntime.device.isAppInstalled("com.whatsapp").then(function (r) {
  if (r.installed) NativeRuntime.device.openApp("com.whatsapp");
});

NativeRuntime.device.openFile("content://media/external/images/1", "image/jpeg");
```

> `device.finish` closes the host Activity — the app exits. Use it only for an explicit "Exit" action.

---

## 6. Screen & orientation

### `device.setOrientation`

```js
NativeRuntime.device.setOrientation("landscape");
```

| Value | Effect |
| --- | --- |
| `portrait` | Lock portrait |
| `landscape` | Lock landscape |
| `reverse_portrait` | Lock reverse portrait |
| `reverse_landscape` | Lock reverse landscape |
| `sensor` | Follow the sensor |
| anything else | `SCREEN_ORIENTATION_UNSPECIFIED` (system default) |

**Returns:** `{ orientation }`

> The host Activity must declare `android:configChanges="orientation|screenSize"` or it will be
> recreated on rotation and your JS state will be lost.

---

### `device.setKeepScreenOn`

```js
NativeRuntime.device.setKeepScreenOn(true);   // while a video plays
```

| Param | Type |
| --- | --- |
| `enabled` | boolean |

**Returns:** `{ enabled }`

---

### `device.setBrightness` / `device.getBrightness`

```js
NativeRuntime.device.setBrightness(0.8);   // 0.0 – 1.0, window only
NativeRuntime.device.getBrightness().then(function (r) {
  console.log(r.brightness);   // -1 means "follow system"
});
```

| Param | Type | Range |
| --- | --- | --- |
| `value` | number | `0.0` – `1.0` |

**Returns:** `{ brightness }`

> ⚠️ `getBrightness` returns the **window override**, not the system brightness. A fresh app
> returns `-1` (`BRIGHTNESS_OVERRIDE_NONE`) even though the screen is at 60%.
> See [audit #7](../AUDIT_AND_UPDATE_REPORT.md#m3-devicegetbrightness-returns-the-window-override).

---

## 7. Logging

### `device.log`

Writes to Logcat under the tag you supply.

```js
NativeRuntime.device.log("checkout started", "Checkout");
```

| Param | Type | Default |
| --- | --- | --- |
| `message` | string | — |
| `tag` | string | `"NativeRuntimeWeb"` |

**Returns:** `{ logged: true }`

---

## 8. Complete action list

```
getInfo getAppInfo getDeviceInfo getScreenInfo getLocale getNetworkState getBattery getSafeArea
copyToClipboard readClipboard shareText vibrate
openUrl openExternal openApp isAppInstalled openAppSettings openFile finish
setOrientation setKeepScreenOn setBrightness getBrightness log
```
