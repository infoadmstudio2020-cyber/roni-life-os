# RONiLiFEOS — Navigation and Lifecycle API

**Native classes:** `NativeRuntimeActivity`, `NativeUICommandHandler`, `NativeWebCommandHandler`
**Routes:** `/ui`, `/web`, `/device`

---

## 1. Two navigation systems

| System | Owner | API | Survives page reload |
| --- | --- | --- | --- |
| Native chrome | Android | `ui.*` | ✅ |
| In-page routing | Your JS | `history`, `location`, hash | ❌ (unless hash) |

The toolbar, drawer and bottom navigation are **native views above the WebView**. They do not scroll with the page and are not in the DOM. Your in-page router is independent — you must keep them in sync yourself.

---

## 2. Toolbar

| Action | Parameters | Returns | Event |
| --- | --- | --- | --- |
| `ui.setToolbarTitle` | `title` (O, `""`) | `{ title }` | — |
| `ui.setToolbarSubtitle` | `subtitle` (O, `""`) | `{ subtitle }` | — |
| `ui.setToolbarVisible` | `visible` (O, `true`) | `{ visible }` | — |
| `ui.setNavigationIconVisible` | `visible` (O, `true`) | `{ visible }` | — |
| `ui.addToolbarAction` | `id` (O, `action_<ts>`), `label` (O, `id`) | `{ id }` | `ui.toolbarAction` |
| `ui.removeToolbarAction` | `id` (O, `""`) | `{ removed: true }` | — |
| `ui.clearToolbarActions` | — | `{ cleared: true }` | — |
| `ui.getToolbarActions` | — | `{ actions: [{id, label}] }` | — |

```js
await Native.call('ui.setToolbarTitle', { title: 'নোটখাতা' });
await Native.call('ui.setToolbarSubtitle', { subtitle: '১২টি নোট' });

await Native.call('ui.addToolbarAction', { id: 'save', label: 'Save' });
Native.on('ui.toolbarAction', ({ id, label }) => {
  if (id === 'save') saveNote();
});
```

`ui.toolbarAction` payload: `{ id, label }`.

---

## 3. Drawer

| Action | Parameters | Returns | Event |
| --- | --- | --- | --- |
| `ui.openDrawer` | — | `{ open: true }` | — |
| `ui.closeDrawer` | — | `{ open: false }` | — |
| `ui.toggleDrawer` | — | `{ open: boolean }` | — |
| `ui.isDrawerOpen` | — | `{ open: boolean }` | — |
| `ui.selectDrawerItem` | `id` (O, `""`) | `{ selected: id }` | — |
| `ui.getSelectedDrawerItem` | — | `{ id }` | — |
| `ui.addDrawerItem` | `id` (O, `drawer_<ts>`), `title` (O, `id`) | `{ id }` | `ui.drawerItemSelected` |
| `ui.removeDrawerItem` | `id` (O, `""`) | `{ removed: true }` | — |

`ui.drawerItemSelected` payload: `{ id, title }`.

```js
await Native.call('ui.addDrawerItem', { id: 'about', title: 'সম্পর্কে' });
Native.on('ui.drawerItemSelected', ({ id }) => navigate(id));
```

---

## 4. Bottom navigation

| Action | Parameters | Returns |
| --- | --- | --- |
| `ui.addTab` | `tabId` (O, `tab_<ts>`), `label` (O, `tabId`) | `{ tabId }` |
| `ui.selectTab` | `tabId` (O, `""`) | `{ selectedTab }` |
| `ui.setBadge` | `tabId` (O, `""`), `count` (O, `0`) | `{ count }` |
| `ui.getBadge` | `tabId` (O, `""`) | `{ count }` |
| `ui.getTabs` | — | `{ tabs: [{id, label, badge}] }` |
| `ui.getSelectedTab` | — | `{ tabId }` |

> ⚠ There is **no** `ui.tabSelected` event. Tab taps are handled natively; if your page must react, call `ui.getSelectedTab` on `visibilitychange` or drive tabs from your own UI.

```js
await Native.call('ui.addTab', { tabId: 'home', label: 'হোম' });
await Native.call('ui.addTab', { tabId: 'settings', label: 'সেটিংস' });
await Native.call('ui.selectTab', { tabId: 'home' });
await Native.call('ui.setBadge', { tabId: 'home', count: 3 });
```

---

## 5. Floating action button

| Action | Parameters | Returns |
| --- | --- | --- |
| `ui.setFabVisible` | `visible` (O, `true`) | `{ visible }` |
| `ui.isFabVisible` | — | `{ visible: boolean }` |

---

## 6. WebView navigation

| Action | Parameters | Returns |
| --- | --- | --- |
| `web.loadUrl` | `url` (O, `""`) | `{ loaded: true }` |
| `web.loadHtml` | `html` (O, `""`), `baseUrl` (O, `""`) | `{ loaded: true }` |
| `web.loadSource` | `type`, `location` / `html`, `baseUrl` | `{ loaded: true }` |
| `web.reload` | — | `{ reloaded: true }` |
| `web.stop` | — | `{ stopped: true }` |
| `web.goBack` | — | `{ navigated: true }` |
| `web.goForward` | — | `{ navigated: true }` |
| `web.canGoBack` | — | `{ canGoBack: boolean }` |
| `web.canGoForward` | — | `{ canGoForward: boolean }` |
| `web.clearHistory` | — | `{ cleared: true }` |
| `web.getState` | — | `{ url, canGoBack, canGoForward, title, progress }` |

```js
const state = await Native.call('web.getState');
if (state.canGoBack) await Native.call('web.goBack');
```

> ⚠ `web.loadUrl` replaces the page. The JS context is destroyed, `NativeRuntimeBridgeClient` is re-injected on `onPageFinished`, and every pending promise is lost.

---

## 7. App-level navigation

| Action | Parameters | Returns |
| --- | --- | --- |
| `ui.navigateBack` | — | `{ navigated: boolean }` |
| `ui.openExternal` | `url` (O, `""`) | `{ opened: boolean }` |
| `device.openUrl` / `device.openExternal` | `url` (O, `""`) | `{ opened: boolean }` |
| `device.finish` | — | `{ finished: true }` |
| `device.openAppSettings` | — | `{ opened: true }` |
| `device.openApp` | `packageName` (O, `""`) | `{ opened: boolean }` |

`ui.navigateBack` pops the WebView history first; when there is no history it delegates to the Activity's back handling.

---

## 8. Lifecycle

### 8.1 Native lifecycle

```
SplashActivity (launcher)
  └─ BootstrapActivity
       └─ ProjectActivity (extends NativeRuntimeActivity)
            onCreate   → NativeRuntimeUI.init() → registerBridgeRoutes()
            onResume   → webView.onResume() + resumeTimers()
            onPause    → webView.onPause()  + pauseTimers()
            onDestroy  → bridge cleared, evaluator cleared, HTTP cache kept
```

### 8.2 What the page observes

| Native moment | Page effect |
| --- | --- |
| `onPageStarted` | JS context being replaced |
| `onPageFinished` | `NativeRuntimeBridgeClient` appears |
| `onResume` | Timers resume |
| `onPause` | **Timers stop** (`pauseTimers()`) |
| `onDestroy` | Native events dropped |

### 8.3 Detecting visibility

```js
document.addEventListener('visibilitychange', function () {
  if (document.hidden) {
    flushDraft();
  } else {
    refresh();
  }
});
```

`visibilitychange` is the only reliable signal. There is **no** `app.pause` / `app.resume` event.

### 8.4 Why timers stop

`WebViewHost.onPause()` calls `webView.pauseTimers()`. `setTimeout` and `setInterval` do not fire while the app is backgrounded. Anything that must run in the background belongs in `automation.*`.

```js
// ❌ will not fire while backgrounded
setTimeout(syncNotes, 30 * 60 * 1000);

// ✅ survives backgrounding
await Native.call('automation.scheduleJob', {
  id: 'sync-notes',
  name: 'Sync notes',
  workerType: 'noop',
  requiresNetwork: true,
  repeatIntervalMs: 30 * 60 * 1000
});
```

---

## 9. Back button

The WebView handles back natively: it pops history when possible, otherwise the Activity finishes. The page is **not** notified.

If you need to intercept back, keep your own history and expose a native action:

```js
// Track your own stack
const stack = [];
function push(route) { stack.push(route); render(route); }
function pop() { stack.pop(); render(stack[stack.length - 1] || 'home'); }

// Ask the user before leaving
window.addEventListener('beforeunload', function (event) {
  if (hasUnsavedChanges()) {
    event.preventDefault();
    event.returnValue = '';
  }
});
```

`beforeunload` is honoured by the WebView for page-initiated navigation, not for the hardware back button.

---

## 10. Deep links

Notification deep links are routed **natively** by `NotificationDeepLinkRouter`. The page is not notified. Encode the intent in the URL and read it on load:

```js
const params = new URLSearchParams(location.search);
const route = params.get('route');
if (route) navigate(route);
```

---

## 11. Complete example

```js
const Nav = {
  routes: { home: renderHome, notes: renderNotes, settings: renderSettings },
  current: 'home',

  async init() {
    await Native.call('ui.setToolbarTitle', { title: 'আমার অ্যাপ' });

    await Native.call('ui.addDrawerItem', { id: 'home', title: 'হোম' });
    await Native.call('ui.addDrawerItem', { id: 'notes', title: 'নোট' });
    await Native.call('ui.addDrawerItem', { id: 'settings', title: 'সেটিংস' });

    Native.on('ui.drawerItemSelected', ({ id }) => this.go(id));

    await Native.call('ui.addTab', { tabId: 'home', label: 'হোম' });
    await Native.call('ui.addTab', { tabId: 'notes', label: 'নোট' });

    this.go('home');
  },

  async go(route) {
    if (!this.routes[route]) return;
    this.current = route;
    this.routes[route]();
    await Native.call('ui.setToolbarTitle', { title: route });
    await Native.call('ui.selectDrawerItem', { id: route });
    await Native.call('ui.closeDrawer');
    history.replaceState(null, '', '#' + route);
  }
};
```

---

## 12. Related documents

- `NATIVE_API_REFERENCE.md` §1 and §2
- `WEB_APP_DEVELOPER_GUIDE.md` §7 and §14
- `BACKGROUND_SERVICES_API.md` — work that survives backgrounding
- `EVENTS_AND_CALLBACKS.md` — the events listed above
