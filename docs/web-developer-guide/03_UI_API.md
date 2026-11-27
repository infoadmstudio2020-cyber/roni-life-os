> ⚠️ **SUPERSEDED — DO NOT USE.**
>
> This document describes a `NativeRuntime.ui.*` facade that **is not injected by the SDK**.
> The real API is `window.NativeRuntimeBridgeClient.call("namespace.action", payload)`.
> See [`docs/web-sdk/README.md`](../web-sdk/README.md) for the verified documentation.

---

# 03 — `ui.*` Native UI API

Controls the native Android shell: overlays, toolbar, drawer, bottom navigation, FAB,
system bars, theme and component visibility.

All calls return a Promise. The resolved object is shown in the **Returns** column.

> **Threading.** The `ui.*` handler runs on the router's background thread. Every manager
> marshals to the Android UI thread internally, so all actions in this document are safe to call
> from JavaScript. `ui.showSnackbar` and `ui.showPopupMenu` were the two exceptions before 1.1.1
> and are now fixed.

---

## 1. Overlays

### `ui.showAlert`

Native alert dialog with a single OK button.

```js
NativeRuntime.ui.alert("Delete order?", "This cannot be undone.");
```

| Param | Type | Default | Notes |
| --- | --- | --- | --- |
| `title` | string | `"Alert"` | |
| `message` | string | `""` | |

**Returns:** `{ shown: true }`

---

### `ui.showConfirm`

Two-button confirmation dialog. Emits `ui.confirm` when the user answers.

```js
NativeRuntime.ui.confirm("Publish?", "Go live now?", "Publish", "Later")
  .then(function () { /* dialog shown */ });

NativeRuntime.on("ui.confirm", function (e) {
  if (e.confirmed) publish();
});
```

| Param | Type | Default |
| --- | --- | --- |
| `title` | string | `"Confirm"` |
| `message` | string | `""` |
| `confirmText` | string | `"OK"` |
| `cancelText` | string | `"Cancel"` |

**Returns:** `{ shown: true }`
**Event:** `ui.confirm` → `{ confirmed: boolean }`

> The Promise resolves when the dialog is **shown**, not when it is answered. Always use the event.

---

### `ui.showToast`

Native Android toast. Safe to call from any thread.

```js
NativeRuntime.ui.toast("Saved", "success");
NativeRuntime.ui.toast("Upload failed", "error", "long");
```

| Param | Type | Default | Values |
| --- | --- | --- | --- |
| `message` | string | `""` | |
| `type` | string | `"info"` | `info` · `success` · `warning` · `error` |
| `duration` | string | `"short"` | `short` · `long` |

**Returns:** `{ shown: true }`

`success` prefixes `✓`, `warning` prefixes `⚠`, `error` prefixes `✗`.

---

### `ui.showBottomSheet`

Native bottom sheet with a list of options. Emits `ui.actionSelected` on tap.

```js
NativeRuntime.ui.bottomSheet("Order actions", [
  { title: "Edit" },
  { title: "Duplicate" },
  { title: "Delete", destructive: true }
]);

NativeRuntime.on("ui.actionSelected", function (e) {
  console.log(e.title, e.index);
});
```

| Param | Type | Notes |
| --- | --- | --- |
| `title` | string | Sheet header |
| `items` | array | `[{ title, destructive }]` or `["Edit", "Delete"]` |

**Returns:** `{ shown: true }`
**Event:** `ui.actionSelected` → `{ title, index }`

---

### `ui.showActionSheet`

> ⚠️ **Alias.** In the current build this executes the *same* code path as `ui.showBottomSheet`.
> It does not render a distinct action sheet. Use `ui.showBottomSheet` and treat this as deprecated.

| Param | Type | Notes |
| --- | --- | --- |
| `title` | string | |
| `items` | array | `[{ title, destructive }]` |

**Returns:** `{ shown: true }`
**Event:** `ui.actionSelected` → `{ title, index }`

---

### `ui.showLoading` / `ui.hideLoading`

Blocking loading overlay.

```js
NativeRuntime.ui.loading("Uploading…", false);   // not cancelable
fetch("/api/upload").finally(function () {
  NativeRuntime.ui.hideLoading();
});
```

| Param | Type | Default |
| --- | --- | --- |
| `text` | string | `"Loading..."` |
| `cancelable` | boolean | `true` |

**Returns:** `{ shown: true }` / `{ hidden: true }`

---

### `ui.showSnackbar`

Material snackbar anchored to the root view, with an optional action button.

```js
NativeRuntime.ui.snackbar("Message archived", "UNDO");

NativeRuntime.on("ui.snackbarAction", function (e) {
  console.log(e.actionTitle);   // "UNDO"
});
```

| Param | Type | Default |
| --- | --- | --- |
| `message` | string | `""` |
| `actionTitle` | string | `""` (no button) |
| `duration` | string | `"short"` |

**Returns:** `{ shown: true }`
**Event:** `ui.snackbarAction` → `{ actionTitle }`

> If no root view is available the call silently degrades to a toast.

---

### `ui.showPopupMenu`

Anchored popup menu.

```js
NativeRuntime.ui.popupMenu([
  { id: 1, title: "Refresh" },
  { id: 2, title: "Settings" }
]);

NativeRuntime.on("ui.popupItemSelected", function (e) {
  console.log(e.title, e.index);
});
```

| Param | Type | Notes |
| --- | --- | --- |
| `items` | array | `[{ id, title }]` |

**Returns:** `{ shown: true }` — or `{ shown: false }` if no anchor view exists.
**Event:** `ui.popupItemSelected` → `{ title, index }`

---

### Dismissal and state

| Action | Payload | Returns |
| --- | --- | --- |
| `ui.dismissDialog` | `{}` | `{ dismissed: true }` |
| `ui.dismissBottomSheet` | `{}` | `{ dismissed: true }` |
| `ui.dismissToast` | `{}` | `{ dismissed: true }` |
| `ui.dismissActionSheet` | `{}` | `{ dismissed: true }` |
| `ui.isDialogShowing` | `{}` | `{ showing: boolean }` |
| `ui.isBottomSheetVisible` | `{}` | `{ visible: boolean }` |
| `ui.isToastVisible` | `{}` | `{ visible: boolean }` |
| `ui.isLoading` | `{}` | `{ showing: boolean }` |
| `ui.getActionSheetState` | `{}` | `{ visible: boolean }` |

---

## 2. Toolbar

| Action | Payload | Returns |
| --- | --- | --- |
| `ui.setToolbarTitle` | `{ title }` | `{ title }` |
| `ui.setToolbarSubtitle` | `{ subtitle }` | `{ subtitle }` |
| `ui.setToolbarVisible` | `{ visible }` | `{ visible }` |
| `ui.setNavigationIconVisible` | `{ visible }` | `{ visible }` |
| `ui.addToolbarAction` | `{ id, label }` | `{ id }` |
| `ui.removeToolbarAction` | `{ id }` | `{ removed: true }` |
| `ui.clearToolbarActions` | `{}` | `{ cleared: true }` |
| `ui.getToolbarActions` | `{}` | `{ actions: [...] }` |

```js
NativeRuntime.ui.setTitle("Orders");
NativeRuntime.ui.setSubtitle("12 open");
NativeRuntime.ui.addToolbarAction("refresh", "Refresh");

NativeRuntime.on("ui.toolbarAction", function (e) {
  if (e.id === "refresh") reload();
});
```

**Event:** `ui.toolbarAction` → `{ id, label }`

> Toolbar actions added from the web are text actions. Icon resources must be supplied natively.

---

## 3. Navigation drawer

| Action | Payload | Returns |
| --- | --- | --- |
| `ui.openDrawer` | `{}` | `{ open: true }` |
| `ui.closeDrawer` | `{}` | `{ open: false }` |
| `ui.toggleDrawer` | `{}` | `{ open: boolean }` |
| `ui.isDrawerOpen` | `{}` | `{ open: boolean }` |
| `ui.selectDrawerItem` | `{ id }` | `{ selected: id }` |
| `ui.getSelectedDrawerItem` | `{}` | `{ id }` |
| `ui.addDrawerItem` | `{ id, title }` | `{ id }` |
| `ui.removeDrawerItem` | `{ id }` | `{ removed: true }` |

```js
NativeRuntime.ui.addDrawerItem("drawer_settings", "Settings");
NativeRuntime.ui.openDrawer();

NativeRuntime.on("ui.drawerItemSelected", function (e) {
  console.log(e.id, e.title);
});
```

**Event:** `ui.drawerItemSelected` → `{ id, title }`

> The drawer must be enabled by the native host (`Navigation.NAVIGATION_DRAWER`).
> If it is not, these calls return success but do nothing.

---

## 4. Bottom navigation

| Action | Payload | Returns |
| --- | --- | --- |
| `ui.selectTab` | `{ tabId }` | `{ selectedTab }` |
| `ui.addTab` | `{ tabId, label }` | `{ tabId }` |
| `ui.setBadge` | `{ tabId, count }` | `{ count }` |
| `ui.getBadge` | `{ tabId }` | `{ count }` |
| `ui.getTabs` | `{}` | `{ tabs: [...] }` |
| `ui.getSelectedTab` | `{}` | `{ tabId }` |

```js
NativeRuntime.ui.selectTab("tab_orders");
NativeRuntime.ui.setBadge("tab_orders", 12);
NativeRuntime.ui.setBadge("tab_orders", 0);   // clears the badge
```

> Tab ids are assigned by the native host (`addBottomNavTab("tab_home", "Home", 0)`).
> Ask the Android developer for the list, or call `ui.getTabs()` at runtime.

---

## 5. Floating action button

| Action | Payload | Returns |
| --- | --- | --- |
| `ui.setFabVisible` | `{ visible }` | `{ visible }` |
| `ui.isFabVisible` | `{}` | `{ visible }` |

```js
NativeRuntime.ui.setFabVisible(false);   // hide while a modal is open
```

> The FAB's click handler is owned by the native host. The web layer can only show/hide it.

---

## 6. Component visibility

| Action | Payload | Returns |
| --- | --- | --- |
| `ui.setComponentVisible` | `{ category, key, visible }` | `{ visible }` |
| `ui.setComponentEnabled` | `{ category, key, enabled }` | `{ enabled }` |
| `ui.isComponentEnabled` | `{ category, key }` | `{ enabled }` |
| `ui.getComponentState` | `{ category, key }` | `{ key, category, enabled, visible }` |
| `ui.getComponentCount` | `{}` | `{ count }` |
| `ui.listComponents` | `{}` | `{ components: [...] }` |

`category` is one of the 12 UI categories: `SYSTEM_UI`, `APP_BAR`, `NAVIGATION`, `CONTENT`,
`DIALOG`, `FEEDBACK`, `OVERLAY`, `INPUT`, `WEB_HOST`, `THEME`, `LIFECYCLE`, `ANIMATION`.

`key` is the component constant name, e.g. `TOOLBAR`, `BOTTOM_NAVIGATION`, `FLOATING_ACTION_BUTTON`.

```js
NativeRuntime.ui.setComponentVisible({ category: "APP_BAR", key: "TOOLBAR", visible: false });
```

> **What actually happens:** `setComponentVisible` updates the visibility manager *and* toggles the
> live view for these component types: `TOOLBAR`, `BOTTOM_NAV`, `FAB`, `STATUS_BAR`, `WEB_VIEW`.
> For every other type it only updates registry state — nothing on screen changes.
>
> `setComponentEnabled` only updates the configuration override; it does not add or remove views.
> Use it to record intent before the shell is built, not to mutate a running screen.

---

## 7. System UI

| Action | Payload | Returns |
| --- | --- | --- |
| `ui.setStatusBar` | `{ color, lightIcons }` | `{ color }` |
| `ui.setNavigationBar` | `{ color, lightButtons }` | `{ color }` |
| `ui.setFullscreen` | `{ enabled }` | `{ enabled }` |
| `ui.setImmersive` | `{ enabled }` | `{ enabled }` |
| `ui.setEdgeToEdge` | `{ enabled }` | `{ enabled }` |
| `ui.getSystemUIState` | `{}` | see below |

```js
NativeRuntime.ui.setStatusBar("#0f172a", true);      // dark bar, light icons
NativeRuntime.ui.setNavigationBar("#0f172a", true);
NativeRuntime.ui.setFullscreen(true);
```

`getSystemUIState` returns:

```json
{
  "fullscreen": false,
  "immersive": false,
  "edgeToEdge": true,
  "statusBarColor": "#0f172a",
  "navBarColor": "#0f172a",
  "lightStatusBar": true,
  "lightNavBar": true
}
```

> `lightIcons: true` means **light-coloured icons**, i.e. for a dark background.
> Colours accept `#RRGGBB` or `#AARRGGBB`.

---

## 8. Theme

### `ui.applyTheme`

```js
NativeRuntime.ui.applyTheme({
  primaryColor: "#0d9488",
  surfaceColor: "#f8fafc",
  backgroundColor: "#ffffff",
  statusBarColor: "#0f172a",
  navBarColor: "#0f172a",
  lightStatusBar: true,
  lightNavBar: true
});
```

**Returns:** `{ applied: true }`

> ⚠️ **Only two keys are applied.** `NativeUIComponentManager.applyTheme` reads `primaryColor`
> (toolbar + FAB background) and `surfaceColor` (coordinator background). `backgroundColor`,
> `statusBarColor`, `navBarColor`, `lightStatusBar` and `lightNavBar` are accepted but ignored.
> To change the system bars use `ui.setStatusBar` / `ui.setNavigationBar` explicitly.
> See [audit #6](../AUDIT_AND_UPDATE_REPORT.md#m1-uiapplytheme-applies-only-primarycolor-and-surfacecolor).

### `ui.getTheme`

```json
{
  "themeMode": "SYSTEM",
  "primaryColor": "#0d9488",
  "backgroundColor": "#ffffff",
  "statusBarColor": "#0f172a",
  "navBarColor": "#0f172a",
  "lightStatusBar": true,
  "lightNavBar": true
}
```

### `ui.getConfig`

Returns the full UI configuration:

```json
{
  "toolbarEnabled": true,
  "drawerEnabled": true,
  "bottomNavEnabled": true,
  "webViewEnabled": true,
  "fabEnabled": true,
  "systemUIEnabled": true,
  "fullScreenWebHost": false,
  "toolbarTitle": "NativeRuntime Demo",
  "toolbarSubtitle": "index.html",
  "drawerHeaderTitle": "Menu",
  "drawerHeaderSubtitle": "Shell",
  "webViewInitialUrl": "",
  "themeMode": "SYSTEM",
  "primaryColor": "#0d9488",
  "backgroundColor": "#ffffff",
  "statusBarColor": "#0f172a",
  "navBarColor": "#0f172a",
  "lightStatusBar": true,
  "lightNavBar": true,
  "fullscreen": false,
  "immersive": false,
  "edgeToEdge": false
}
```

Use this at startup to discover which native features are available:

```js
NativeRuntime.ui.getConfig().then(function (cfg) {
  if (!cfg.toolbarEnabled) document.body.classList.add("no-native-toolbar");
  if (!cfg.bottomNavEnabled) showHtmlTabBar();
});
```

---

## 9. WebView convenience (on the `ui` namespace)

| Action | Payload | Returns |
| --- | --- | --- |
| `ui.reload` | `{}` | `{ reloaded: boolean }` |
| `ui.getWebViewState` | `{}` | `{ url, canGoBack, canGoForward }` |
| `ui.setWebViewSource` | `{ type, location, entryPoint }` | `{ loaded: boolean }` |
| `ui.navigateBack` | `{}` | `{ navigated: boolean }` |
| `ui.openExternal` | `{ url }` | `{ opened: boolean }` |

```js
NativeRuntime.ui.setWebViewSource({ type: "asset", location: "about.html" });
NativeRuntime.ui.openExternal("https://example.com");
```

`type` accepts: `asset` · `file` · `zip` · `zipfile` · `inline` · `content` · `remote`.

---

## 10. Complete action list

```
Overlays   showAlert showConfirm showToast showBottomSheet showActionSheet
           showLoading hideLoading showSnackbar showPopupMenu
           dismissDialog dismissBottomSheet dismissToast dismissActionSheet
           isDialogShowing isBottomSheetVisible isToastVisible isLoading getActionSheetState
Toolbar    setToolbarTitle setToolbarSubtitle setToolbarVisible setNavigationIconVisible
           addToolbarAction removeToolbarAction clearToolbarActions getToolbarActions
Drawer     openDrawer closeDrawer toggleDrawer isDrawerOpen
           selectDrawerItem getSelectedDrawerItem addDrawerItem removeDrawerItem
Tabs       selectTab addTab setBadge getBadge getTabs getSelectedTab
FAB        setFabVisible isFabVisible
Components isComponentEnabled setComponentEnabled setComponentVisible
           getComponentState getComponentCount listComponents
System UI  setStatusBar setNavigationBar setFullscreen setImmersive setEdgeToEdge
           getSystemUIState applyTheme getTheme getConfig
WebView    reload getWebViewState setWebViewSource navigateBack openExternal
```
