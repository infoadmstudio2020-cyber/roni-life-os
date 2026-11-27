# Device API Guide — `device.*` (২৮টি action)

> সোর্স: `native-runtime-ui/.../handler/NativeDeviceCommandHandler.java`
> প্রতিটি সারি `switch (action)`-এর case থেকে স্বয়ংক্রিয়ভাবে নেওয়া।

## ১. সাধারণ নিয়ম

```js
const info = await NativeRuntimeBridgeClient.call('device.getInfo', {});
```

| নিয়ম | মান |
|---|---|
| prefix | `device.` |
| payload | সবসময় object, না দিলে `{}` |
| response | `{ success: true, payload: {...} }` |
| error | `{ success: false, error: { code, message } }` |

## ২. পূর্ণ তালিকা

| # | Action | Parameters | Error codes |
|---|---|---|---|
| 1 | `device.copyToClipboard` | — | — |
| 2 | `device.finish` | — | — |
| 3 | `device.getAppInfo` | — | — |
| 4 | `device.getBattery` | — | — |
| 5 | `device.getBrightness` | — | — |
| 6 | `device.getClipboard` | — | — |
| 7 | `device.getDeviceInfo` | — | — |
| 8 | `device.getInfo` | — | — |
| 9 | `device.getLocale` | — | — |
| 10 | `device.getNetworkState` | — | — |
| 11 | `device.getSafeArea` | — | — |
| 12 | `device.getScreenInfo` | — | — |
| 13 | `device.haptic` | — | — |
| 14 | `device.isAppInstalled` | `packageName` | — |
| 15 | `device.log` | `message`, `tag` | `UNKNOWN_ACTION` |
| 16 | `device.openApp` | `packageName` | — |
| 17 | `device.openAppSettings` | — | — |
| 18 | `device.openExternal` | `url` | — |
| 19 | `device.openFile` | `mimeType`, `uri` | — |
| 20 | `device.openUrl` | — | — |
| 21 | `device.readClipboard` | — | — |
| 22 | `device.setBrightness` | — | — |
| 23 | `device.setClipboard` | `label`, `text` | — |
| 24 | `device.setKeepScreenOn` | — | — |
| 25 | `device.setOrientation` | `orientation` | — |
| 26 | `device.share` | `subject`, `text`, `title` | — |
| 27 | `device.shareText` | — | — |
| 28 | `device.vibrate` | — | — |

## ৩. ব্যবহারের উদাহরণ

### ৩.১ ডিভাইস তথ্য

```js
const d = await NativeRuntimeBridgeClient.call('device.getInfo', {});
console.log(d.model, d.manufacturer, d.androidVersion, d.sdkInt);
```

### ৩.২ ব্যাটারি

```js
const b = await NativeRuntimeBridgeClient.call('device.getBattery', {});
if (b.level <= 20 && !b.charging) showLowBatteryWarning();
```

### ৩.৩ ক্লিপবোর্ড

```js
await NativeRuntimeBridgeClient.call('device.copyToClipboard', { text: 'কপি হয়েছে' });
const { text } = await NativeRuntimeBridgeClient.call('device.readClipboard', {});
```

### ৩.৪ শেয়ার

```js
await NativeRuntimeBridgeClient.call('device.shareText', {
  text: 'RONiLiFEOS দেখুন',
  title: 'শেয়ার'
});
```

### ৩.৫ হ্যাপটিক

```js
await NativeRuntimeBridgeClient.call('device.vibrate', { duration: 50 });
await NativeRuntimeBridgeClient.call('device.haptic', { type: 'light' });
```

## ৪. সীমাবদ্ধতা

| বিষয় | সীমা |
|---|---|
| `device.vibrate` | ডিভাইসে vibrator না থাকলে নীরবে ব্যর্থ |
| `device.readClipboard` | Android 10+ এ শুধু foreground-এ |
| `device.getBattery` | Android 5-এ কিছু ফিল্ড `null` |
| `device.openUrl` | `http(s)`, `tel:`, `mailto:` scheme |
| `device.setOrientation` | Activity config change দরকার |

## ৫. যাচাই

`tools/verify/examples_runtime_test.mjs` — ১৪টি `device.*` action আসল উদাহরণে চালানো হয়েছে,
সবই dispatch table-এ মিলেছে (০ unknown)।
