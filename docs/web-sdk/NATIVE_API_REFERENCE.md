# RONiLiFEOS — Complete Native API Catalog

**Verified against source.** Every action below was read from the handler's `switch` statement. Nothing is inferred from a class name.

**Transport:** `window.NativeRuntimeBridgeClient.call('<namespace>.<action>', payload)` → `Promise<payload>`
See `JAVASCRIPT_BRIDGE.md` for the envelope, gates and error handling.

**Legend**

| Symbol | Meaning |
| --- | --- |
| **R** | Required parameter |
| **O** | Optional parameter (default shown) |
| **→** | Resolved value of the promise (the native `payload`) |
| **⚠** | Behaviour that differs from what the name suggests |

---

## 0. Route index

| Namespace | Handler class | Actions | Availability |
| --- | --- | --- | --- |
| `ui` | `NativeUICommandHandler` | 65 | Always (when `NativeRuntimeUI` is initialised) |
| `web` | `NativeWebCommandHandler` | 55 | Always |
| `device` | `NativeDeviceCommandHandler` | 30 | Always |
| `storage` | `NativeStorageCommandHandler` | 7 | Requires `StorageEngine` |
| `theme` | `ThemeCommandHandler` | 6 | Always |
| `security` | `SecurityCommandHandler` | 11 | Requires `SecurityEngine` |
| `analytics` | `AnalyticsCommandHandler` | 11 | Always |
| `devtools` | `DevToolsCommandHandler` | 9 | **Debug builds only** |
| `network` | `NetworkCommandHandler` | 1 | Always |
| `platform` | `PlatformCommandHandler` | 15 | Requires `PlatformEngine` |
| `notification` | `NotificationCommandHandler` | 10 | Requires an active project |
| `camera` | `CameraCommandHandler` | 18 | Requires `PlatformEngine` + active project |
| `filemanager` | `FileCommandHandler` | 28 | Requires `PlatformEngine` |
| `automation` | `AutomationCommandHandler` | 26 | Requires `PlatformEngine` |
| `logger` | `LoggerCommandHandler` | 4 | Always |
| `service` | `ServiceCommandHandler` | 2 | Always |
| `runtime` | `RuntimeCommandHandler` | 6 | Requires `NativeRuntimeEngine` |
| `bridge` | `BridgeCommandHandler` | 1 | Always |
| `echo` | `EchoCommandHandler` | 1 | Always |

---

## 1. `ui.*` — Native UI control

**Native class:** `com.roni.library.ui.handler.NativeUICommandHandler`
**Route:** `/ui` · **Action pattern:** `*`
**Signature:** `handleRequest(NativeApiRequest) → NativeApiResponse`

All actions return a **flat object** with one or two keys. `ui.showToast` → `{ shown: true }`.

### 1.1 Overlays

#### `ui.showAlert`

| | |
| --- | --- |
| **Parameters** | `title` (O, `"Alert"`), `message` (O, `""`) |
| **Returns** | `{ shown: true }` |
| **Events** | none |
| **Permissions** | none |

```js
await call('ui.showAlert', { title: 'Notice', message: 'Saved successfully.' });
```

#### `ui.showConfirm`

| | |
| --- | --- |
| **Parameters** | `title` (O, `"Confirm"`), `message` (O, `""`), `confirmText` (O, `"OK"`), `cancelText` (O, `"Cancel"`) |
| **Returns** | `{ shown: true }` — **resolves immediately**, before the user answers |
| **Events** | `ui.confirm` → `{ confirmed: boolean }` |

```js
const off = on('ui.confirm', ({ confirmed }) => {
  off();
  console.log(confirmed ? 'accepted' : 'cancelled');
});
await call('ui.showConfirm', { title: 'Delete?', message: 'This cannot be undone.' });
```

> ⚠ The promise does **not** carry the user's answer. You must listen for `ui.confirm`.

#### `ui.showToast`

| | |
| --- | --- |
| **Parameters** | `message` (O, `""`) — **not** `text`; `duration` (O, `"short"` \| `"long"`), `type` (O, `"info"` \| `"success"` \| `"warning"` \| `"error"`) |
| **Returns** | `{ shown: true }` |

```js
await call('ui.showToast', { message: 'নোট সংরক্ষিত হয়েছে', type: 'success', duration: 'short' });
```

#### `ui.showBottomSheet`

| | |
| --- | --- |
| **Parameters** | `title` (O, `""`), `items` (O, `Array<string \| {title, destructive}>`) |
| **Returns** | `{ shown: true }` |
| **Events** | `ui.actionSelected` → `{ title, index }` |

#### `ui.showActionSheet`

Same parameters and events as `ui.showBottomSheet`. Clears any previous items first.

#### `ui.showLoading`

| | |
| --- | --- |
| **Parameters** | `text` (O, `"Loading..."`), `cancelable` (O, `true`) |
| **Returns** | `{ shown: true }` |

#### `ui.hideLoading` → `{ hidden: true }`

#### `ui.showSnackbar`

| | |
| --- | --- |
| **Parameters** | `message` (O, `""`), `actionTitle` (O, `""`), `duration` (O, `"short"` \| `"long"`) |
| **Returns** | `{ shown: true }` |
| **Events** | `ui.snackbarAction` → `{ actionTitle }` (only when `actionTitle` is non-empty) |

#### `ui.showPopupMenu`

| | |
| --- | --- |
| **Parameters** | `items` (O, `Array<{id, title}>`) |
| **Returns** | `{ shown: boolean }` — `false` when no anchor view is attached |
| **Events** | `ui.popupItemSelected` → `{ title, index }` |

#### Dismiss / state actions

| Action | Returns |
| --- | --- |
| `ui.dismissDialog` | `{ dismissed: true }` |
| `ui.isDialogShowing` | `{ showing: boolean }` |
| `ui.dismissBottomSheet` | `{ dismissed: true }` |
| `ui.isBottomSheetVisible` | `{ visible: boolean }` |
| `ui.dismissToast` | `{ dismissed: true }` |
| `ui.isToastVisible` | `{ visible: boolean }` |
| `ui.isLoading` | `{ showing: boolean }` |
| `ui.dismissActionSheet` | `{ dismissed: true }` |
| `ui.getActionSheetState` | `{ visible: boolean }` |

### 1.2 Toolbar

| Action | Parameters | Returns | Event |
| --- | --- | --- | --- |
| `ui.setToolbarTitle` | `title` (O, `""`) | `{ title }` | — |
| `ui.setToolbarSubtitle` | `subtitle` (O, `""`) | `{ subtitle }` | — |
| `ui.setToolbarVisible` | `visible` (O, `true`) | `{ visible }` | — |
| `ui.setNavigationIconVisible` | `visible` (O, `true`) | `{ visible }` | — |
| `ui.addToolbarAction` | `id` (O, `action_<ts>`), `label` (O, `id`) | `{ id }` | `ui.toolbarAction` → `{ id, label }` |
| `ui.removeToolbarAction` | `id` (O, `""`) | `{ removed: true }` | — |
| `ui.clearToolbarActions` | — | `{ cleared: true }` | — |
| `ui.getToolbarActions` | — | `{ actions: [{id, label}] }` | — |

```js
await call('ui.setToolbarTitle', { title: 'নোটখাতা' });
await call('ui.addToolbarAction', { id: 'save', label: 'Save' });
on('ui.toolbarAction', ({ id }) => { if (id === 'save') saveNote(); });
```

### 1.3 Drawer

| Action | Parameters | Returns | Event |
| --- | --- | --- | --- |
| `ui.openDrawer` | — | `{ open: true }` | — |
| `ui.closeDrawer` | — | `{ open: false }` | — |
| `ui.toggleDrawer` | — | `{ open: boolean }` | — |
| `ui.isDrawerOpen` | — | `{ open: boolean }` | — |
| `ui.selectDrawerItem` | `id` (O, `""`) | `{ selected: id }` | — |
| `ui.getSelectedDrawerItem` | — | `{ id }` | — |
| `ui.addDrawerItem` | `id` (O, `drawer_<ts>`), `title` (O, `id`) | `{ id }` | `ui.drawerItemSelected` → `{ id, title }` |
| `ui.removeDrawerItem` | `id` (O, `""`) | `{ removed: true }` | — |

### 1.4 Bottom navigation

| Action | Parameters | Returns |
| --- | --- | --- |
| `ui.selectTab` | `tabId` (O, `""`) | `{ selectedTab }` |
| `ui.addTab` | `tabId` (O, `tab_<ts>`), `label` (O, `tabId`) | `{ tabId }` |
| `ui.setBadge` | `tabId` (O, `""`), `count` (O, `0`) | `{ count }` |
| `ui.getBadge` | `tabId` (O, `""`) | `{ count }` |
| `ui.getTabs` | — | `{ tabs: [{id, label, badge}] }` |
| `ui.getSelectedTab` | — | `{ tabId }` |

### 1.5 Floating action button

| Action | Parameters | Returns |
| --- | --- | --- |
| `ui.setFabVisible` | `visible` (O, `true`) | `{ visible }` |
| `ui.isFabVisible` | — | `{ visible: boolean }` |

### 1.6 Component registry

| Action | Parameters | Returns |
| --- | --- | --- |
| `ui.isComponentEnabled` | `category` (O, `""`), `key` (O, `""`) | `{ enabled: boolean }` |
| `ui.setComponentEnabled` | `category`, `key`, `enabled` (O, `true`) | `{ enabled }` |
| `ui.setComponentVisible` | `category`, `key`, `visible` (O, `true`) | `{ visible, applied, reason? }` |
| `ui.getComponentState` | `category`, `key` | `{ key, category, enabled, visible }` or `{}` |
| `ui.getComponentCount` | — | `{ count: number }` |
| `ui.listComponents` | — | `{ components: [{key, name, category, enabled, visible}] }` |

`category` is a `UICategory` enum name; `key` is the component type key (e.g. `FAB`, `TOOLBAR`).

### 1.7 System UI

| Action | Parameters | Returns |
| --- | --- | --- |
| `ui.setStatusBar` | `color` (O, `"#000000"`), `lightIcons` (O, `false`) | `{ color: int }` or `{ applied: false }` |
| `ui.setNavigationBar` | `color` (O, `"#000000"`), `lightButtons` (O, `false`) | `{ color: int }` or `{ applied: false }` |
| `ui.setFullscreen` | `enabled` (O, `true`) | `{ enabled }` or `{ applied: false }` |
| `ui.setImmersive` | `enabled` (O, `true`) | `{ enabled }` or `{ applied: false }` |
| `ui.setEdgeToEdge` | `enabled` (O, `true`) | `{ enabled }` or `{ applied: false }` |
| `ui.getSystemUIState` | — | `{ fullscreen, immersive, edgeToEdge, statusBarColor, navBarColor, lightStatusBar, lightNavBar }` |

`{ applied: false }` means no Activity is attached — the call is a no-op, not an error.

### 1.8 Theme (UI layer)

| Action | Parameters | Returns |
| --- | --- | --- |
| `ui.applyTheme` | any key/value pairs, forwarded verbatim to `UIComponentManager.applyTheme` | `{ applied: true }` |
| `ui.getTheme` | — | `{ themeMode, primaryColor, backgroundColor, statusBarColor, navBarColor, lightStatusBar, lightNavBar }` |
| `ui.getConfig` | — | 22 keys — see below |

`ui.getConfig` returns:
`toolbarEnabled`, `drawerEnabled`, `bottomNavEnabled`, `webViewEnabled`, `fabEnabled`, `systemUIEnabled`, `fullScreenWebHost`, `toolbarTitle`, `toolbarSubtitle`, `drawerHeaderTitle`, `drawerHeaderSubtitle`, `webViewInitialUrl`, `themeMode`, `primaryColor`, `backgroundColor`, `statusBarColor`, `navBarColor`, `lightStatusBar`, `lightNavBar`, `fullscreen`, `immersive`, `edgeToEdge`.

### 1.9 WebView convenience

| Action | Parameters | Returns |
| --- | --- | --- |
| `ui.reload` | — | `{ reloaded: boolean }` |
| `ui.getWebViewState` | — | `{ url, canGoBack, canGoForward }` |
| `ui.setWebViewSource` | `type` (`asset`\|`file`\|`url`\|`html`), `location` / `html` | `{ loaded: boolean }` |
| `ui.navigateBack` | — | `{ navigated: boolean }` |
| `ui.openExternal` | `url` (O, `""`) | `{ opened: boolean }` |

---

## 2. `web.*` — WebView control

**Native class:** `com.roni.library.ui.handler.NativeWebCommandHandler`
**Route:** `/web` · **Action pattern:** `*`

> ⚠ Every action runs on the **Android main thread**. If the caller is already on the main thread it executes inline; otherwise it is posted and awaited for **10 s** (`MAIN_THREAD_TIMEOUT`).

### 2.1 Navigation

| Action | Parameters | Returns |
| --- | --- | --- |
| `web.loadSource` | `type` (`asset`\|`file`\|`url`\|`html`), `location` or `html`, `baseUrl` (O) | `{ loaded: true }` |
| `web.loadUrl` | `url` (O, `""`) | `{ loaded: true }` |
| `web.loadHtml` | `html` (O, `""`), `baseUrl` (O, `""`) | `{ loaded: true }` |
| `web.reload` | — | `{ reloaded: true }` |
| `web.stop` | — | `{ stopped: true }` |
| `web.goBack` | — | `{ navigated: true }` |
| `web.goForward` | — | `{ navigated: true }` |
| `web.canGoBack` | — | `{ canGoBack: boolean }` |
| `web.canGoForward` | — | `{ canGoForward: boolean }` |
| `web.clearHistory` | — | `{ cleared: true }` |
| `web.getState` | — | `{ url, canGoBack, canGoForward, title, progress }` |

### 2.2 Script evaluation

#### `web.evaluate`

| | |
| --- | --- |
| **Parameters** | `script` (O, `""`) |
| **Returns** | `{ evaluated: true, result: string }` or `{ evaluated: false, error: "TIMEOUT" }` |
| **Errors** | `MAIN_THREAD_BLOCKED` when called from the main thread |

```js
const { result } = await call('web.evaluate', { script: 'document.title' });
```

`result` is the raw `evaluateJavascript` callback value — a **JSON-encoded string**. `"\"Home\""` means the page title is `Home`. Parse it if you need the value.

### 2.3 Zoom and text

| Action | Parameters | Returns |
| --- | --- | --- |
| `web.setZoom` | `zoom` (O, `100`, clamped 25–400) | `{ zoom }` |
| `web.getZoom` | — | `{ zoom }` |
| `web.setTextZoom` | `zoom` (O, `100`) | `{ zoom }` |
| `web.getTextZoom` | — | `{ zoom }` |

`setZoom` applies CSS `zoom` on `document.documentElement`; `setTextZoom` changes `WebSettings` text scaling.

### 2.4 User agent and settings

| Action | Parameters | Returns |
| --- | --- | --- |
| `web.setUserAgent` | `userAgent` (O, `""`) | `{ userAgent }` |
| `web.getUserAgent` | — | `{ userAgent }` |
| `web.getSettings` | — | full settings snapshot (see below) |
| `web.setSettings` | any subset of the snapshot | updated snapshot |

`getSettings` returns: `javaScriptEnabled`, `domStorageEnabled`, `databaseEnabled`, `allowFileAccess`, `allowContentAccess`, `useWideViewPort`, `loadWithOverviewMode`, `supportZoom`, `builtInZoomControls`, `displayZoomControls`, `textZoom`, `userAgent`, `cacheMode`, `mixedContentMode`, `safeBrowsingEnabled`, `mediaPlaybackRequiresUserGesture`, `supportMultipleWindows`, `javaScriptCanOpenWindowsAutomatically`, `defaultTextEncodingName`, `standardFontFamily`, `defaultFontSize`, `minimumFontSize`, `saveFormData`, `geolocationEnabled`, `blockNetworkImage`, `blockNetworkLoads`, `loadsImagesAutomatically`, `offscreenPreRaster`, `forceDark`, `algorithmicDarkeningAllowed`.

### 2.5 Cache and cookies

| Action | Parameters | Returns |
| --- | --- | --- |
| `web.clearCache` | — | `{ cleared: true }` |
| `web.setCacheMode` | `mode` (O, `"default"`) | `{ cacheMode: int }` |
| `web.setCookie` | `url` (O, `""`), `value` (O, `""`) | `{ set: true }` |
| `web.getCookies` | `url` (O, `""`) | `{ cookies: string }` |
| `web.clearCookies` | — | `{ cleared: true }` |

`mode` accepts `default`, `no_cache`, `cache_else_network`, `cache_only`.

### 2.6 Appearance and events

| Action | Parameters | Returns |
| --- | --- | --- |
| `web.setBackgroundColor` | `color` (O, `"#FFFFFF"`) | `{ color }` |
| `web.setProgressBarVisible` | `visible` (O, `true`) | `{ visible }` |
| `web.emitEvent` | `topic` (O, `"web.event"`), `data` (O, `{}`) | `{ emitted: true }` |

`web.emitEvent` is the **only** way a page can trigger a native → web event on an arbitrary topic. It is useful for testing your own listeners.

### 2.7 WebSettings toggles

Each takes `enabled` (O, `true`) and returns `{ enabled }`:

`web.setJavaScript`, `web.setDomStorage`, `web.setAllowFileAccess`, `web.setAllowContentAccess`, `web.setSupportZoom`, `web.setBuiltInZoomControls`, `web.setDisplayZoomControls`, `web.setLoadWithOverviewMode`, `web.setUseWideViewPort`, `web.setDatabaseEnabled`, `web.setGeolocationEnabled`, `web.setSaveFormData`, `web.setJavaScriptCanOpenWindowsAutomatically`, `web.setSupportMultipleWindows`, `web.setMediaPlaybackRequiresUserGesture`, `web.setOffscreenPreRaster`, `web.setForceDark`, `web.setAlgorithmicDarkeningAllowed`, `web.setBlockNetworkImage`, `web.setBlockNetworkLoads`, `web.setLoadsImagesAutomatically`.

Value-typed variants:

| Action | Parameters | Returns |
| --- | --- | --- |
| `web.setMixedContentMode` | `mode` (O, `"never"`) | `{ mode }` |
| `web.setSafeBrowsing` | `enabled` (O, `true`) | `{ enabled }` |
| `web.setDefaultTextEncodingName` | `encoding` (O, `"UTF-8"`) | `{ encoding }` |
| `web.setStandardFontFamily` | `family` (O, `"sans-serif"`) | `{ family }` |
| `web.setDefaultFontSize` | `size` (O, `16`) | `{ size }` |
| `web.setMinimumFontSize` | `size` (O, `8`) | `{ size }` |

---

## 3. `device.*` — Device and app information

**Native class:** `com.roni.library.ui.handler.NativeDeviceCommandHandler`
**Route:** `/device` · **Action pattern:** `*`

> ⚠ Actions that need an Activity return `ACTIVITY_UNAVAILABLE` when `NativeUIHost` has none attached.

### 3.1 Information

#### `device.getInfo`

Returns the union of app, device, screen, locale and safe-area data:

```json
{
  "packageName": "com.roni.life.os",
  "versionName": "1.4.2",
  "versionCode": 142,
  "appLabel": "RONiLiFEOS",
  "manufacturer": "Google", "brand": "google", "model": "Pixel 7",
  "device": "panther", "osVersion": "14", "sdkInt": 34,
  "architecture": "arm64-v8a",
  "totalMemoryBytes": 8234567890, "freeMemoryBytes": 3123456789,
  "product": "panther",
  "screenWidth": 1080, "screenHeight": 2400,
  "density": 2.75, "densityDpi": 440,
  "orientation": "portrait",
  "statusBarHeight": 72, "navigationBarHeight": 132,
  "locale": "bn_BD", "language": "bn", "country": "BD",
  "safeAreaTop": 72, "safeAreaBottom": 132, "safeAreaLeft": 0, "safeAreaRight": 0
}
```

| Action | Returns |
| --- | --- |
| `device.getAppInfo` | `packageName`, `versionName`, `versionCode`, `appLabel` |
| `device.getDeviceInfo` | `manufacturer`, `brand`, `model`, `device`, `osVersion`, `sdkInt`, `architecture`, `totalMemoryBytes`, `freeMemoryBytes`, `product` |
| `device.getScreenInfo` | `screenWidth`, `screenHeight`, `density`, `densityDpi`, `orientation`, `statusBarHeight`, `navigationBarHeight` |
| `device.getLocale` | `locale`, `language`, `country` |
| `device.getSafeArea` | `safeAreaTop`, `safeAreaBottom`, `safeAreaLeft`, `safeAreaRight` |

### 3.2 Power and network

#### `device.getBattery`

```json
{ "level": 87, "scale": 100, "percent": 87, "status": 2, "plugged": 2,
  "plugType": "USB", "temperature": 310, "voltage": -1,
  "charging": true, "health": "GOOD", "source": "platform" }
```

`source` is `"platform"` when `PlatformEngine` answered, `"system"` when it fell back to the `ACTION_BATTERY_CHANGED` broadcast. `status` follows `BatteryManager.BATTERY_STATUS_*` (2 = charging, 3 = discharging).

#### `device.getNetworkState`

```json
{ "connected": true, "type": "WIFI", "metered": false, "roaming": false, "source": "platform" }
```

### 3.3 Clipboard and sharing

| Action | Parameters | Returns |
| --- | --- | --- |
| `device.copyToClipboard` / `device.setClipboard` | `text` (O, `""`), `label` (O, `"NativeRuntime"`) | `{ copied: boolean }` |
| `device.readClipboard` / `device.getClipboard` | — | `{ text: string }` |
| `device.shareText` / `device.share` | `text` (O, `""`), `subject` (O, `""`), `title` (O, `"Share"`) | `{ shared: true }` |

### 3.4 Haptics and links

| Action | Parameters | Returns |
| --- | --- | --- |
| `device.vibrate` / `device.haptic` | — | `{ vibrated: true }` |
| `device.openUrl` / `device.openExternal` | `url` (O, `""`) | `{ opened: boolean }` |
| `device.openFile` | `uri` (O, `""`), `mimeType` (O, `"*/*"`) | `{ opened: true }` |

`device.vibrate` performs `HapticFeedbackConstants.VIRTUAL_KEY` on the decor view — it is a **haptic tick**, not a timed vibration.

### 3.5 Window control

| Action | Parameters | Returns |
| --- | --- | --- |
| `device.setOrientation` | `orientation` (O, `"auto"`) — `portrait`, `landscape`, `reverse_portrait`, `reverse_landscape`, `sensor`, `auto` | `{ orientation }` |
| `device.setKeepScreenOn` | `enabled` (O, `true`) | `{ enabled }` |
| `device.setBrightness` | `value` (O, `-1`, 0.0–1.0; `<0` resets) | `{ brightness: float }` |
| `device.getBrightness` | — | `{ brightness: float }` |
| `device.finish` | — | `{ finished: true }` |

### 3.6 App management

| Action | Parameters | Returns |
| --- | --- | --- |
| `device.openAppSettings` | — | `{ opened: true }` |
| `device.isAppInstalled` | `packageName` (O, `""`) | `{ installed: boolean }` |
| `device.openApp` | `packageName` (O, `""`) | `{ opened: boolean }` |
| `device.log` | `tag` (O, `"NativeRuntimeWeb"`), `message` (O, `""`) | `{ logged: true }` |

---

## 4. `storage.*` — Project-scoped key/value storage

**Native class:** `com.roni.library.ui.handler.NativeStorageCommandHandler`
**Route:** `/storage` · **Action pattern:** `*`
**Backend:** `EncryptedStore` (AES-GCM, default) or `KeyValueStore` (plaintext, opt-in)

Full details in `STORAGE_API.md`.

| Action | Parameters | Returns |
| --- | --- | --- |
| `storage.get` | `key` (O, `""`) | `{ key, value, exists }` |
| `storage.set` | `key` (O, `""`), `value` (any JSON scalar) | `{ saved: true }` |
| `storage.remove` | `key` (O, `""`) | `{ removed: true }` |
| `storage.clear` | — | `{ cleared: <count> }` |
| `storage.keys` | — | `{ keys: string[] }` |
| `storage.has` | `key` (O, `""`) | `{ exists: boolean }` |
| `storage.getAll` | — | flat `{ key: value }` map |

---

## 5. `theme.*` — Theme engine

**Native class:** `com.roni.library.theme.handler.ThemeCommandHandler`
**Route:** `/theme` · **Action pattern:** `*`

| Action | Parameters | Returns | Event |
| --- | --- | --- | --- |
| `theme.getTheme` | — | `{ themeName, darkMode, primaryColor, surfaceColor, textColor, themeMode, secondaryColor, backgroundColor, statusBarColor, navigationBarColor }` | — |
| `theme.setThemeMode` | `mode` (O, `"SYSTEM"`) — `LIGHT`, `DARK`, `SYSTEM` | `{ mode }` | `theme.changed` |
| `theme.applyTheme` | `themeName`, `darkMode`, `primaryColor`, `secondaryColor`, `backgroundColor`, `surfaceColor`, `textColor`, `statusBarColor`, `navigationBarColor` | `{ applied: true }` | `theme.changed` |
| `theme.isSystemDark` | — | `{ isSystemDark: boolean }` | — |
| `theme.setSystemDark` | `dark` (O, `false`) | `{ systemDark }` | `theme.changed` |
| `theme.getCssVariables` | — | `{ css: string }` | — |

`theme.getCssVariables` returns a ready-to-inject `:root { --color-primary: ...; }` block generated by `WebThemeBridge.generateCssVariables()`.

```js
const { css } = await call('theme.getCssVariables');
document.getElementById('native-theme').textContent = css;
on('theme.changed', () => refreshTheme());
```

---

## 6. `security.*` — Keystore, biometrics, permissions

**Native class:** `com.roni.library.security.handler.SecurityCommandHandler`
**Route:** `/security` · **Action pattern:** `*`

Full details in `SECURITY_AND_PERMISSIONS.md`.

| Action | Parameters | Returns |
| --- | --- | --- |
| `security.keystore.generate` | `alias` (O, `"default_key"`), `algorithm` (O, `"AES_GCM_256"`), `requiresUserAuth` (O, `false`) | `{ success, alias, algorithm }` |
| `security.keystore.hasKey` | `alias` (**R**) | `{ hasKey, alias }` |
| `security.keystore.delete` | `alias` (**R**) | `{ deleted, alias }` |
| `security.keystore.list` | — | `{ aliases: string[] }` |
| `security.keystore.encrypt` | `alias` (O, `"default_key"`), `plaintext` (**R**) | `{ ciphertext, iv, success }` |
| `security.keystore.decrypt` | `alias` (**R**), `ciphertext` (**R**), `iv` (**R**) | `{ plaintext, success }` |
| `security.biometric.capability` | `authenticators` (O, `BIOMETRIC_STRONG`) | `{ code, description, available }` |
| `security.biometric.authenticate` | `title` (O, `"Biometric Authentication"`) | `{ success: true, initiated: true, message }` |
| `security.permission.check` | `permission` (**R**) | `{ permission, status, granted }` |
| `security.hash.sha256` | `input` (O, `""`) — **not** `text` | `{ sha256: string }` |
| `security.diagnostic` | — | `{ state, hsmAvailable, biometricAvailable, registeredKeysCount, routePrefix, success }` |

> ⚠ `security.biometric.authenticate` resolves as soon as the prompt is **shown**. The result arrives as the `security.biometric.result` event.

---

## 7. `analytics.*` — Telemetry

**Native class:** `com.roni.library.analytics.handler.AnalyticsCommandHandler`
**Route:** `/analytics` · **Action pattern:** `*`

| Action | Parameters | Returns |
| --- | --- | --- |
| `analytics.trackEvent` | `name` (**R**), `parameters` (O, object) — **not** `properties` | `{ status: "queued", event }` |
| `analytics.recordMetric` | `name` (**R**), `value` (**R**, finite number), `unit` (O, `"count"`) | `{ status: "recorded", metric }` |
| `analytics.startSession` | — | `{ sessionId, startTime }` |
| `analytics.endSession` | — | `{ status: "ended", activeDurationMs }` |
| `analytics.startTrace` | `name` (**R**) | `{ trace, status: "started" }` |
| `analytics.stopTrace` | `name` (**R**) | `{ trace, durationMs }` |
| `analytics.startScreenView` | `screenName` (**R**) — **not** `screen` | `{ screenName }` |
| `analytics.endScreenView` | `screenName` (**R**) | `{ screenName, durationMs }` |
| `analytics.getStats` | — | `{ sessionId, sessionActive, totalActiveDurationMs, memoryEventCount, memoryMetricCount, offlineQueueCount }` |
| `analytics.flush` | — | `{ status: "flushed" }` |
| `analytics.clear` | — | `{ status: "cleared" }` |

Missing required parameters return `INVALID_PARAM` with a descriptive message.

---

## 8. `devtools.*` — Developer tooling (debug builds only)

**Native class:** `com.roni.library.devtools.handler.DevToolsCommandHandler`
**Route:** `/devtools` · **Action pattern:** `*`
**Availability:** registered only when `NativeRuntimeUIConfig.isDebugEnabled()` is `true`. In release builds the route is absent and every call fails with `ROUTE_NOT_FOUND`.

| Action | Parameters | Returns |
| --- | --- | --- |
| `devtools.getMetrics` | — | performance profiler snapshot |
| `devtools.getHealth` | — | service health map |
| `devtools.getBridgeMetrics` | — | bridge counters |
| `devtools.getManifest` | — | platform manifest |
| `devtools.exportHar` | — | HAR object |
| `devtools.clearTraffic` | — | `{ cleared: true }` |
| `devtools.startProfiling` | — | `{ recording: true }` |
| `devtools.stopProfiling` | — | `{ recording: false }` |
| `devtools.dispatchCdp` | `method` (O, `"Runtime.enable"`), plus any CDP params | CDP response map |

> ⚠ `devtools.dispatchCdp` reads `method` from the payload. A `domain` key is ignored.

---

## 9. `network.*` — Native HTTP client

**Native class:** `com.roni.library.network.handler.NetworkCommandHandler`
**Route:** `/network` · **Action pattern:** `*`

### `network.request`

| | |
| --- | --- |
| **Parameters** | `url` (**R**, `http://` or `https://` only), `method` (O, `GET`), `headers` (O, object), `body` (O, string), `timeoutMs` (O, engine default) |
| **Methods** | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, `OPTIONS` |
| **Returns** | `{ statusCode, body, latencyMs, method, url }` |
| **Errors** | `INVALID_URL`, `UNSUPPORTED_SCHEME`, `UNSUPPORTED_METHOD`, `FORBIDDEN_HEADER`, `NETWORK_ERROR` |

Forbidden request headers: `host`, `content-length`, `connection`.

```js
const res = await call('network.request', {
  url: 'https://api.example.com/notes',
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ title: 'নোট' }),
  timeoutMs: 15000
});
const data = JSON.parse(res.body);
```

> ⚠ `body` must be a **string**. Objects are ignored silently.

---

## 10. `platform.*` — Platform engine snapshot

**Native class:** `com.roni.library.platform.handler.PlatformCommandHandler`
**Route:** `/platform` · **Action pattern:** `*`

| Action | Parameters | Returns |
| --- | --- | --- |
| `platform.device.info` / `platform.device` | — | `{ deviceInfo }` |
| `platform.battery.status` / `platform.battery` | — | `{ batteryStatus }` |
| `platform.network.status` / `platform.network` | — | `{ networkStatus }` |
| `platform.storage.stats` / `platform.storage` | — | `{ storageStats }` |
| `platform.clipboard.get` | — | `{ clipboardText, hasText }` |
| `platform.clipboard.set` | `text` (O, `""`) | `{ success, text }` |
| `platform.toast.show` | `message` (O, `"Notification"`) — **not** `text`; `long` (O, `false`) | `{ shown, message }` |
| `platform.toast.vibrate` / `platform.vibrate` | `duration` (O, `100`) | `{ vibrated, durationMs }` |
| `platform.diagnostic` / `platform.health` | — | `{ device, battery, network, storage, state, initialized }` |

Any unrecognised action falls through to the diagnostic branch — it never returns `UNKNOWN_ACTION`.

---

## 11. `notification.*` — Notifications

**Native class:** `com.roni.library.platform.notification.NotificationCommandHandler`
**Route:** `/notification` · **Action pattern:** `*`
**Requirement:** an active project runtime (`NotificationProjectContext.getActiveProject()` must be non-empty)

Full details in `NOTIFICATION_API.md`.

| Action | Aliases | Returns |
| --- | --- | --- |
| `notification.post` | `show`, `notify` | `{ notification, notificationId }` |
| `notification.update` | — | `{ notification, notificationId }` |
| `notification.schedule` | — | `{ scheduled: true, notificationId, scheduledAt }` |
| `notification.cancel` | — | `{ cancelled: true, notification }` |
| `notification.cancelScheduled` | `cancel_scheduled` | `{ cancelled: boolean }` |
| `notification.cancelAll` | `cancel_all` | `{ cancelledCount }` |
| `notification.list` | `history` | `{ notifications: [], count }` |
| `notification.channels` | — | `{ channels: [], count }` |
| `notification.permission` | — | `{ state, canPost, notificationsEnabled, globalEnabled }` |
| `notification.status` | *(default)* | `{ initialized, enabled, permission, notificationsEnabled, activeProject, unread }` |

---

## 12. `camera.*` — Camera

**Native class:** `com.roni.library.platform.camera.CameraCommandHandler`
**Route:** `/camera` · **Action pattern:** `*`
**Requirement:** `PlatformEngine` initialised; `camera.open` additionally requires an active project.

| Action | Aliases | Parameters | Returns |
| --- | --- | --- | --- |
| `camera.getCapabilities` | `capabilities` | — | `{ capabilities, supported, cameraPermission, audioPermission }` |
| `camera.requestPermission` | `permission` | — | `{ cameraPermission, audioPermission, granted, needsHostPrompt }` |
| `camera.open` | — | configuration map | `{ capabilities, configuration, state }` |
| `camera.close` | — | — | `{ state }` |
| `camera.switchCamera` | `switch` | — | `{ state }` |
| `camera.capture` | `takePhoto` | — | capture result |
| `camera.startRecording` | `record` | — | recording result |
| `camera.stopRecording` | — | — | recording result |
| `camera.pauseRecording` | — | — | state |
| `camera.resumeRecording` | — | — | state |
| `camera.setZoom` | — | `level` | state |
| `camera.setFlash` | — | `mode` | state |
| `camera.setFocus` | — | `mode` | state |
| `camera.setExposure` | — | `value` | state |
| `camera.setResolution` | — | `width`, `height` | state |
| `camera.getStatus` | `status` | — | state |
| `camera.getMedia` | `listMedia` | — | media list |
| `camera.deleteMedia` | — | `id` | deletion result |

`camera.requestPermission` **cannot** show a system dialog. It reports the current state and sets `needsHostPrompt: true` when the host app must prompt.

---

## 13. `filemanager.*` — File manager

**Native class:** `com.roni.library.platform.filemanager.FileCommandHandler`
**Route:** `/filemanager` · **Action pattern:** `*`

| Action | Parameters | Returns |
| --- | --- | --- |
| `filemanager.getStorageInfo` | — | storage info map |
| `filemanager.listFiles` | `path` | file list |
| `filemanager.getFileInfo` | `path` | file info map |
| `filemanager.createFile` | `path`, `content` (O, `""`) | file info |
| `filemanager.createFolder` | `path` | file info |
| `filemanager.readFile` | `path` | file content |
| `filemanager.writeFile` | `path`, `base64` (O, `""`) | `{}` |
| `filemanager.copy` | `paths` (array), `targetDir` | operation |
| `filemanager.move` | `paths`, `targetDir` | operation |
| `filemanager.rename` | `path`, `newName` | file info |
| `filemanager.delete` | `paths` | operation |
| `filemanager.search` | `query` | file list |
| `filemanager.getRecent` | `limit` (O, `20`) | file list |
| `filemanager.addFavorite` | `path` | `{}` |
| `filemanager.removeFavorite` | `path` | `{}` |
| `filemanager.getFavorites` | — | file list |
| `filemanager.import` | `treeId`, `documentId`, `targetDir` | import result |
| `filemanager.export` | `treeId`, `parentDocumentId`, `path` | export result |
| `filemanager.pickDirectory` | — | directory picker result |
| `filemanager.getPermissions` | — | permission map |
| `filemanager.requestAccess` | — | permission map |
| `filemanager.getThumbnail` | `path` | thumbnail |
| `filemanager.getCategories` | — | category map |
| `filemanager.getStorageAnalysis` | — | analysis map |
| `filemanager.createArchive` | `paths`, `archivePath` | operation |
| `filemanager.extractArchive` | `path`, `targetDir`, `entries` (O) | operation |
| `filemanager.listArchive` | `path` | archive entries |
| `filemanager.getOperationStatus` | `operationId` | operation status |
| `filemanager.cancelOperation` | `operationId` | cancellation result |

`writeFile` takes **base64**, not plain text. Encode with `btoa(unescape(encodeURIComponent(text)))` for UTF-8 safety.

---

## 14. `automation.*` — Background services

**Native class:** `com.roni.library.platform.automation.AutomationCommandHandler`
**Route:** `/automation` · **Action pattern:** `*`

Full details in `BACKGROUND_SERVICES_API.md`.

| Action | Parameters | Returns |
| --- | --- | --- |
| `automation.getStatus` | — | service status |
| `automation.getCapabilities` | — | capability map |
| `automation.submitTask` | `name`, `type` (O, `"noop"`), `payload`, `maxAttempts` (O, `1`) | task map |
| `automation.cancelTask` | `taskId` | `{ cancelled }` |
| `automation.getTask` | `taskId` | task map |
| `automation.listTasks` | — | `{ tasks }` |
| `automation.clearTasks` | — | `{ removed }` |
| `automation.scheduleJob` | `id`, `name`, `workerType`, `requiresNetwork`, `requiresCharging`, `requiresIdle`, `initialDelayMs`, `repeatIntervalMs`, `maxAttempts`, `payload` | job map |
| `automation.cancelJob` | `jobId` | `{ cancelled }` |
| `automation.listJobs` | — | `{ jobs }` |
| `automation.setAlarm` | `id`, `label`, `triggerAt`, `intervalMs`, `exact`, `payload` | alarm map |
| `automation.cancelAlarm` | `alarmId` | `{ cancelled }` |
| `automation.listAlarms` | — | `{ alarms }` |
| `automation.download` | `id`, `url`, `path`, `headers`, `expectedBytes` | transfer progress |
| `automation.upload` | same as download | transfer progress |
| `automation.cancelTransfer` | `transferId` | `{ cancelled }` |
| `automation.getTransfer` | `transferId` | transfer status |
| `automation.listTransfers` | — | `{ transfers }` |
| `automation.publishEvent` | `topic`, `payload` | event map |
| `automation.getEvents` | `sinceSequence` (O, `0`), `limit` (O, `100`) | `{ events }` |
| `automation.clearEvents` | — | `{ cleared: true }` |
| `automation.listServices` | — | `{ services }` |
| `automation.getServiceStatus` | `name` | service status map |
| `automation.startService` | `name` | `{ started }` |
| `automation.stopService` | `name` | `{ stopped }` |

---

## 15. `logger.*` — Native logger

**Native class:** `com.roni.library.logger.handler.LoggerCommandHandler`
**Route:** `/logger` · **Action pattern:** `*`

| Action | Aliases | Parameters | Returns |
| --- | --- | --- | --- |
| `logger.log` | — | `level` (O, `"INFO"`), `tag` (O, `"RPC"`), `message` (O, `""`) | `{ status: "logged", tag }` |
| `logger.setLevel` | `setMinimumLevel` | `level` (O, `"INFO"`) | `{ currentLevel }` |
| `logger.getLevel` | `getMinimumLevel` | — | `{ currentLevel }` |
| `logger.flush` | — | — | `{ flushed: true }` |

Levels: `VERBOSE`, `DEBUG`, `INFO`, `WARN`, `ERROR`.

---

## 16. `service.*` — Service registry

**Native class:** `com.roni.library.service.handler.ServiceCommandHandler`
**Route:** `/service` · **Action pattern:** `*`

| Action | Parameters | Returns |
| --- | --- | --- |
| `service.list` | — | `{ <serviceId>: "<STATE>" }` |
| `service.status` | `serviceId` | `{ serviceId, state, available, priority }` |

`service.status` with an unknown id returns a 404 `NativeApiError`.

---

## 17. `runtime.*` — Runtime engine

**Native class:** `com.roni.library.runtime.handler.RuntimeCommandHandler`
**Route:** `/runtime` · **Action pattern:** `*`

| Action | Parameters | Returns |
| --- | --- | --- |
| `runtime.loadUrl` | `url` (O, `"about:blank"`) | `{ currentUrl }` |
| `runtime.getVitals` | — | vitals map |
| `runtime.resolveSource` | `url` (O, `"/"`) | `{ url, statusCode, mimeType, isFound }` |
| `runtime.setCookie` | `domain` (O, `"127.0.0.1"`), `cookie` (O, `""`) | `{ stored: true }` |
| `runtime.getCookies` | `domain` (O, `"127.0.0.1"`) | `{ cookies: string[] }` |
| `runtime.clearCookies` | — | `{ cleared: true }` |

---

## 18. `bridge.*` and `echo.*`

| Action | Returns |
| --- | --- |
| `bridge.dispatch` | `{ status: "handled", action, requestId }` |
| `echo.echo` | `{ echo, echoPath, echoAction, requestId, timestamp }` |

`echo.echo` is the canonical connectivity test — it proves the router, the bridge and the JS client are all alive.

```js
const pong = await call('echo.echo', { hello: 'world' });
```

---

## 19. Actions that do **not** exist

The following are commonly assumed but are **not implemented** anywhere in the source. Do not document or use them:

| Assumed | Reality |
| --- | --- |
| `ui.showDialog` | Use `ui.showAlert` / `ui.showConfirm` |
| `ui.setTitle` | Use `ui.setToolbarTitle` |
| `ui.toast` | Use `ui.showToast` |
| `device.getLocation` / `geolocation.*` | Not implemented |
| `device.getContacts` | Not implemented |
| `device.scanBarcode` / `qr.*` | Not implemented |
| `device.getSensors` / `sensor.*` | Not implemented |
| `device.getCamera` | Use `camera.*` |
| `storage.setItem` / `getItem` | Use `storage.set` / `storage.get` |
| `notification.requestPermission` | Use `notification.permission` (read-only) |
| `network.fetch` | Use `network.request` |
| `fs.*` | Use `filemanager.*` |
| `clipboard.*` | Use `device.*` or `platform.clipboard.*` |
| `share.*` | Use `device.shareText` |
| `biometric.*` | Use `security.biometric.*` |
| `payment.*` / `iap.*` | Not implemented |
| `push.*` / `fcm.*` | Not implemented |
| `sqlite.*` / `database.*` | Not implemented (use `storage.*`) |
| `crypto.*` | Only `security.hash.sha256` and `security.keystore.*` |
| `device.getImei` / `getAndroidId` | Not implemented (privacy) |
| `web.postMessage` | Not implemented — use `call()` |
| `ui.setBadgeCount` | Use `ui.setBadge` |
