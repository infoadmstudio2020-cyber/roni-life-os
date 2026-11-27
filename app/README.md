# NativeRuntime — Sample Consumer Application

A complete Android Java sample that consumes the **NativeRuntime** library. It proves that a
developer only needs to extend `NativeRuntimeActivity` and supply a web content source to get a
full native Android application shell.

```
Consumer Sample Activity
        ↓
extends NativeRuntimeActivity
        ↓
provides NativeRuntimeUIConfig
        ↓
NativeRuntime builds the Native UI Shell
        ↓
WebView loads Consumer Content
        ↓
Native Android UI stays persistent
```

The WebView is **only** a content renderer. Every shell element — toolbar, drawer, bottom
navigation, FAB, status bar, toast, dialogs, bottom sheet, snackbar, popup menu, loading overlay —
is a real Android view.

---

## 1. Add the library dependency

`settings.gradle`

```gradle
include ':app'
include ':native-runtime-sdk'
include ':native-runtime-ui'
include ':native-runtime-contracts'
```

`app/build.gradle`

```gradle
dependencies {
    implementation project(':native-runtime-sdk')
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
}
```

`AndroidManifest.xml` — network access is required for remote sources:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 2. Extend `NativeRuntimeActivity`

```java
public class MainActivity extends NativeRuntimeActivity {

    @Override
    protected NativeRuntimeUIConfig onConfigureUI(@NonNull NativeRuntimeUIConfig.Builder builder) {
        return builder
                .enable(SystemUI.STATUS_BAR)
                .enable(AppBar.TOOLBAR)
                .toolbarTitle("NativeRuntime Demo")
                .enable(Navigation.NAVIGATION_DRAWER)
                .enable(Navigation.BOTTOM_NAVIGATION)
                .enable(Overlay.FLOATING_ACTION_BUTTON)
                .enable(Content.CONTENT_CONTAINER)
                .enable(Content.WEB_VIEW)
                .webViewInitialUrl("file:///android_asset/index.html")
                .build();
    }

    @Override
    protected void onUIRendered(@NonNull NativeUIComponentManager manager) {
        // The shell already exists here — wire actions, never rebuild it.
    }
}
```

`onConfigureUI` runs **once**. `onUIRendered` receives the fully built `NativeUIComponentManager`.

---

## 3. Configure the native UI

| Builder call | Result |
| --- | --- |
| `enable(AppBar.TOOLBAR)` / `toolbarTitle(..)` | Native `MaterialToolbar` |
| `enable(Navigation.NAVIGATION_DRAWER)` | Native `DrawerLayout` + `NavigationView` |
| `enable(Navigation.BOTTOM_NAVIGATION)` | Native `BottomNavigationView` |
| `enable(Overlay.FLOATING_ACTION_BUTTON)` | Native `FloatingActionButton` |
| `enable(Content.WEB_VIEW)` / `webViewInitialUrl(..)` | Isolated `WebView` content host |
| `enable(Feedback.TOAST)` / `enable(Feedback.SNACKBAR)` | Native toast + snackbar |
| `enable(Dialog.ALERT_DIALOG)` / `CONFIRMATION_DIALOG` / `CUSTOM_DIALOG` | Native dialogs |
| `enable(Overlay.BOTTOM_SHEET)` / `LOADING_OVERLAY` / `POPUP_WINDOW` | Native overlays |

---

## 4. Load a remote URL

```java
WebViewContainer web = getUIComponentManager().getContentGroup().getWebViewContainer();
web.loadUrl("https://developer.android.com");
```

Loading progress, error handling and history are owned by `WebViewContainer`.

## 5. Load bundled assets HTML

Files live in `app/src/main/assets/`:

```
assets/
├── index.html
├── about.html
├── contact.html
├── css/style.css
└── js/app.js
```

```java
web.loadUrl("file:///android_asset/index.html");
```

## 6. Load local HTML content

```java
web.loadHtml(htmlString, "file:///android_asset/");
```

This is the `loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)` path. The base URL
keeps relative `css/` and `js/` references working.

## 7. Switch the web source at runtime

```java
WebSourceManager manager = new WebSourceManager(this, getUIComponentManager());
manager.loadSource(WebSource.remote("remote", "Android", "https://developer.android.com"));
manager.loadSource(WebSource.asset("home", "Assets", "index.html"));
manager.loadSource(WebSource.localHtml("inline", "Inline", "<h1>Hello</h1>"));
```

`WebSourceManager` only touches the content host. The toolbar, drawer, bottom navigation and FAB
are never recreated, so scroll position, drawer state and theme survive every switch.

## 8. Toolbar, drawer and bottom navigation

```java
addToolbarAction("action_reload", "Reload", () -> manager.reload());
addDrawerItem("drawer_home", "Assets Home", 0, () -> manager.loadSource(home));
addBottomNavTab("tab_home", "Home", 0);
linkDrawerAndBottomNav("drawer_home", "tab_home");
```

## 9. Dialogs, toast and bottom sheet

```java
showToast("Saved");
showAlert("Title", "Message");
showConfirm("Confirm", "Proceed?", onConfirm, onCancel);
showLoading("Syncing…");
hideLoading();

List<NativeActionSheet.ActionItem> items = new ArrayList<>();
items.add(new NativeActionSheet.ActionItem("Reload", false, () -> manager.reload()));
showBottomSheet("Actions", items);
```

## 10. Back navigation and WebView lifecycle

```java
@Override
public void onBackPressed() {
    if (webSourceManager.canGoBack()) {
        webSourceManager.goBack();
        return;
    }
    super.onBackPressed();   // closes drawer, then exits
}
```

`NativeRuntimeActivity` already forwards `onPause` / `onResume` / `onDestroy` to the WebView and
calls `WebViewContainer.destroy()` on teardown, so no leaked web resources remain.

---

## Web → native bridge

`assets/js/app.js` posts JSON to the host:

```js
window.NativeRuntimeBridge.postMessage(JSON.stringify({ type: "toast", message: "Hi" }));
```

Handle it in the activity and answer with a **native** component:

```java
case "toast":   showToast(message); break;
case "alert":   showAlert("Web Event", message); break;
case "sheet":   showBottomSheet("Actions", items); break;
```

---

## Project layout

```
app/src/main/
├── java/com/example/nativeruntimesample/
│   ├── MainActivity.java
│   ├── WebSourceManager.java
│   ├── WebSource.java
│   ├── WebSourceType.java
│   └── demo/NativeComponentDemo.java
├── assets/            # bundled demo website
└── AndroidManifest.xml
```

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

## Notes

- `MainActivity` is the launcher. `SampleActivity` remains available as the component/Bangla chart
  verification harness.
- No HTML is used for shell chrome. The only HTML in the app is the demo website rendered inside
  the content host.
- `file://` access is limited to `android_asset`; arbitrary local file access is opt-in per source.
