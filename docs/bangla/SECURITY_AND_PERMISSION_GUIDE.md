# Security ও Permission Guide

## ১. নিরাপত্তার সাতটি স্তর

| # | স্তর | ক্লাস | কী আটকায় |
|---|---|---|---|
| 1 | Origin gate | `BridgeSecurityValidator.isOriginValid` | অন্য origin থেকে bridge কল |
| 2 | Message validation | `BridgeSecurityValidator.isMessageValid` | খালি `id`/`action` |
| 3 | Size gate | `WebBridge.handleWebMessage` | > 1 MiB payload |
| 4 | Path traversal | `WebPaths.isSafe` | `..`, `%2e`, `%2f`, `\0` |
| 5 | Project isolation | `WebPaths.virtualHostFor` | এক Project অন্য Project পড়া |
| 6 | Encryption at rest | `ProjectVault` / `VaultCipher` | ফাইল চুরি |
| 7 | WebView hardening | `WebHostConfig` | file://, mixed content, SSL bypass |

---

## ২. Origin gate — বিস্তারিত

### ২.১ allow-list কোথা থেকে আসে

```java
// BridgeConfig ডিফল্ট
DEFAULT_ALLOWED_ORIGIN = "https://appassets.androidplatform.net"

// WebViewHost প্রতিটি লোডে
bridge.allowOrigin("https://" + WebPaths.virtualHostFor(projectId));
```

### ২.২ origin কীভাবে বের হয়

```java
String originOf(String url) {
    Uri uri = Uri.parse(url);
    String scheme = uri.getScheme();
    if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
    return scheme + "://" + uri.getHost();   // port বাদ
}
```

| URL | origin | ফল |
|---|---|---|
| `https://abc-1a2b3c4d.runtime.local/index.html` | `https://abc-1a2b3c4d.runtime.local` | ✅ |
| `https://appassets.androidplatform.net/x.html` | `https://appassets.androidplatform.net` | ✅ |
| `about:blank` | `null` | ❌ `ORIGIN_REJECTED` |
| `data:text/html,…` | `null` | ❌ |
| `file:///android_asset/x.html` | `null` | ❌ |
| `https://evil.example` | `https://evil.example` | ❌ |

### ২.৩ যা করবেন না

```java
// ❌ কখনো না
config.addAllowedOrigin("*");
```

`"*"` দিলে যেকোনো পেজ (এমনকি iframe-এ লোড করা তৃতীয় পক্ষের সাইট) bridge কল করতে পারবে।

### ২.৪ iframe

`WebViewHost` iframe-এর origin আলাদা করে দেখে না — `webView.getUrl()` শুধু **top-level**
URL দেয়। তাই আপনার পেজে তৃতীয় পক্ষের iframe থাকলে সেটি **আপনার origin-এর হয়ে** bridge
কল করতে পারবে (যদি সে `NativeRuntimeBridge` object পায়)।

**প্রতিরোধ:**
```html
<iframe src="https://third-party.example" sandbox="allow-scripts"></iframe>
```
`sandbox`-এ `allow-same-origin` দেবেন না।

---

## ৩. Path traversal — `WebPaths.isSafe`

```java
public static boolean isSafe(String path) {
    if (path == null) return false;
    if (path.indexOf('\0') >= 0) return false;
    String lower = path.toLowerCase(Locale.ROOT);
    if (lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")) return false;
    for (String seg : path.split("[/\\\\]")) {
        if ("..".equals(seg)) return false;
    }
    return true;
}
```

**কেন `%2e` আলাদা করে ব্লক:** `..%2f..%2fetc%2fpasswd` ডিকোডিংয়ের আগেই আটকাতে হবে।

**আপনার দায়িত্ব:** `web.loadSource`-এ `location` দিলে সেটি `WebPaths.isSafe` পাস করবে
কিনা ভাবুন। `../` থাকলে 404 আসবে।

---

## ৪. Project isolation

প্রতিটি Project আলাদা virtual host পায়:

```
https://notes-7f3a91c2.runtime.local/
https://shop-2b8e44d1.runtime.local/
```

**ফলাফল:**

| জিনিস | আলাদা? |
|---|---|
| `localStorage` | ✅ origin-ভিত্তিক |
| `sessionStorage` | ✅ |
| `IndexedDB` | ✅ |
| Cookie | ✅ |
| `storage.*` (native) | ❌ **শেয়ার্ড** |
| Download folder | ❌ শেয়ার্ড |

**তাই `storage.*`-এ key prefix বাধ্যতামূলক** (দেখুন `STORAGE_AND_FILE_GUIDE.md` §৪)।

---

## ৫. Encryption at rest

### ৫.১ Vault format

```
[magic "RVLT":4][version:1][chunkSize:4][chunkCount:4][plainLength:8]
chunkCount × ( [iv:12][ciphertext + tag:16] )
```

- **Algorithm:** AES-GCM (256-bit)
- **Key:** Android Keystore (`KeyStoreManager`)
- **AAD:** `projectId \n path \n version \n generation \n chunkIndex`
- **Chunk:** ৬৪ KB

### ৫.২ কেন chunked

একটি বড় ফাইলে একটাই GCM tag হলে পুরো ফাইল decrypt না করে verify করা যায় না —
তাই পুরো plaintext মেমরিতে আসে। Chunked AEAD-তে প্রতিটি chunk-এর tag **আলাদা**
verify হয়, তাই এক chunk-এর বেশি মেমরি লাগে না এবং unauthenticated plaintext কখনো
বেরোয় না।

### ৫.৩ সীমা

| Limit | ডিফল্ট | অর্থ |
|---|---|---|
| `maxResourceBytes` | 64 MB | এর বড় resource 404 |
| `maxBufferedBytes` | 2 MB | এর নিচে buffer, উপরে stream |
| `chunkBytes` | 64 KB | প্রতি chunk plaintext |

### ৫.৪ AAD কেন গুরুত্বপূর্ণ

AAD-তে `projectId` ও `path` আছে। ফলে এক Project-এর এনক্রিপ্টেড ফাইল অন্য Project-এর
path-এ কপি করলে decrypt **ব্যর্থ** হবে — swap attack আটকায়।

---

## ৬. WebView hardening

`WebHostConfig` ডিফল্ট:

| সেটিং | মান | কেন |
|---|---|---|
| `allowFileAccess` | `false` | `file://` দিয়ে সিস্টেম ফাইল পড়া বন্ধ |
| `allowContentAccess` | `false` | `content://` দিয়ে অন্য অ্যাপের ডেটা বন্ধ |
| `allowFileAccessFromFileURLs` | `false` | file:// → অন্য file:// XHR বন্ধ |
| `allowUniversalAccessFromFileURLs` | `false` | file:// → http:// বন্ধ |
| `mixedContentAllowed` | `false` | HTTPS পেজে HTTP বন্ধ |
| `safeBrowsingEnabled` | `true` | Google Safe Browsing |
| `supportMultipleWindows` | `false` | `window.open` বন্ধ |
| `mediaPlaybackRequiresUserGesture` | `true` | অটোপ্লে বন্ধ |
| `openExternalLinksInBrowser` | `true` | বাইরের লিংক ব্রাউজারে |

### ৬.১ SSL error

```java
@Override
public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
    handler.cancel();     // কখনো handler.proceed() নয়
}
```

**কেন:** `proceed()` করলে MITM attack সম্ভব। কোনো "ডেভেলপমেন্টে allow" অপশন নেই।

### ৬.২ `window.open`

`supportMultipleWindows = false` → `window.open()` কিছুই করে না।
`target="_blank"` লিংকও নতুন window খোলে না।

**সমাধান:** `device.openUrl` ব্যবহার করুন।

---

## ৭. Permission model

### ৭.১ Web permission (camera, mic, geolocation)

```java
WebHostConfig.builder()
    .permissionHandler(new WebPermissionHandler() {
        @Override
        public void onPermissionRequest(PermissionRequest request, PermissionCallback cb) {
            // নিজের UI দিয়ে জিজ্ঞাসা করুন
            cb.deny();     // বা cb.grant(request.getResources())
        }
        @Override
        public void onGeolocationRequest(String origin, GeolocationCallback cb) {
            cb.deny();
        }
    })
    .build();
```

**`WebPermissionHandler` না থাকলে সব deny।** তাই ডিফল্ট বিল্ডে:

| API | ফল |
|---|---|
| `getUserMedia({video:true})` | ❌ `NotAllowedError` |
| `getUserMedia({audio:true})` | ❌ |
| `navigator.geolocation` | ❌ |
| `<input type="file">` | ✅ (file chooser আলাদা) |
| `Notification.requestPermission()` | ❌ (WebView-এ সাপোর্ট নেই) |

### ৭.২ Android runtime permission

`device.*` API-গুলোর **কোনোটিরই** runtime permission লাগে না:

| API | ব্যবহৃত mechanism | Permission |
|---|---|---|
| `device.copyToClipboard` | `ClipboardManager` | ❌ |
| `device.readClipboard` | `ClipboardManager` | ❌ (Android 10+ foreground-এ) |
| `device.vibrate` | `performHapticFeedback` | ❌ |
| `device.setBrightness` | `window.attributes` | ❌ |
| `device.openUrl` | `Intent` | ❌ |
| `device.shareText` | `Intent.ACTION_SEND` | ❌ |
| `device.getBattery` | `BatteryManager` | ❌ |
| `device.getNetworkState` | `ConnectivityManager` | `ACCESS_NETWORK_STATE` (install-time) |
| Download | `DownloadManager` | `INTERNET` + notification |

---

## ৮. Data handling নিয়ম

### ৮.১ যা কখনো করবেন না

```js
// ❌ পাসওয়ার্ড plain
await Native.call('storage.set', { key: 'pwd', value: password });

// ❌ API key client-এ
const API_KEY = 'sk-live-...';

// ❌ token URL-এ
location.href = '/callback?token=' + token;
```

### ৮.২ যা করবেন

```js
// ✅ শুধু token সেভ, পাসওয়ার্ড নয়
await Native.call('storage.set', { key: 'app:token', value: accessToken });

// ✅ token সবসময় header-এ
fetch('/api/me', { headers: { Authorization: 'Bearer ' + accessToken } });

// ✅ লগআউটে মুছে ফেলুন
await Native.call('storage.remove', { key: 'app:token' });
```

### ৮.৩ Log

```js
// ❌
console.log('login', { email: e, password: p });

// ✅
console.log('login attempt', { email: e });
```

`device.log` native logcat-এ লেখে — প্রোডাকশনে সংবেদনশীল ডেটা দেবেন না।

---

## ৯. XSS প্রতিরোধ

```js
// ❌
el.innerHTML = '<div>' + userInput + '</div>';

// ✅
el.textContent = userInput;

// ✅ (HTML দরকার হলে)
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, function (c) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
  });
}
el.innerHTML = '<div>' + escapeHtml(userInput) + '</div>';
```

**কেন গুরুত্বপূর্ণ:** XSS হলে attacker আপনার origin-এ কোড চালাতে পারবে, এবং
**bridge-এ পূর্ণ অ্যাক্সেস** পাবে (`storage.clear()`, `device.finish()`, …)।

---

## ১০. CSP (Content Security Policy)

```html
<meta http-equiv="Content-Security-Policy"
      content="default-src 'self';
               script-src 'self';
               style-src 'self' 'unsafe-inline';
               img-src 'self' data: https:;
               connect-src 'self' https://api.example.com;
               object-src 'none';
               base-uri 'none';
               frame-ancestors 'none'">
```

**সতর্কতা:** `script-src 'self'` দিলে inline `<script>` ব্লক হবে। উদাহরণগুলোতে inline
script আছে — প্রোডাকশনে external `.js` ফাইলে সরান।

---

## ১১. নিরাপত্তা চেকলিস্ট

- [ ] `addAllowedOrigin("*")` কোথাও নেই
- [ ] `handler.proceed()` (SSL) কোথাও নেই
- [ ] `allowFileAccess` / `allowContentAccess` `false`
- [ ] `WebPermissionHandler` স্পষ্টভাবে সেট (deny ডিফল্ট)
- [ ] `storage.*` key-তে project prefix
- [ ] পাসওয়ার্ড/API key client-এ নেই
- [ ] `innerHTML`-এ user input escape করা
- [ ] CSP meta tag
- [ ] iframe-এ `sandbox` (allow-same-origin ছাড়া)
- [ ] `device.openUrl`-এ URL scheme whitelist
- [ ] লগে সংবেদনশীল ডেটা নেই
