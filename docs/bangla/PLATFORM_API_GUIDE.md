# Platform API Guide — `platform.*`

## ১. Action (১৫, ৫টি alias)

| Action | Payload | Response |
|---|---|---|
| `platform.device.info` | — | `{deviceInfo:{model, manufacturer, brand, androidVersion, sdkInt, ...}}` |
| `platform.battery.status` | — | `{level, charging, ...}` |
| `platform.network.status` | — | `{connected, type}` |
| `platform.storage.stats` | — | `{totalBytes, freeBytes, usedBytes}` |
| `platform.clipboard.get` | — | `{text}` |
| `platform.clipboard.set` | `{text}` | `{copied:true}` |
| `platform.toast.show` | `{text}` | `{shown:true}` |
| `platform.toast.vibrate` | `{duration}` | `{vibrated:true}` |
| `platform.diagnostic` | — | `{...}` |
| `platform.health` | — | `{healthy:true}` |

Alias: `device`, `battery`, `network`, `storage`, `vibrate` — একই কাজ করে।

## ২. ব্যবহার

```js
import { platform } from './native-runtime.js';

const { deviceInfo } = await platform.device();
document.getElementById('model').textContent = deviceInfo.model;

const battery = await platform.battery();
if (battery.level < 20) warnLowBattery();

await platform.clipboard.set('কপি হয়েছে');
```

## ৩. Privacy

| ডেটা | প্রকাশ |
|---|---|
| model, brand, manufacturer | ✅ |
| Android version, SDK | ✅ |
| screen size, density | ✅ |
| IMEI, Android ID, serial | ❌ কখনো নয় |
| MAC address | ❌ |
| Account/email | ❌ |

## ৪. Permission

| Action | Permission |
|---|---|
| `clipboard.get` | Android 10+ এ foreground |
| `toast.vibrate` | `VIBRATE` (manifest-এ থাকতে হবে) |
| `network.status` | `ACCESS_NETWORK_STATE` |
| `storage.stats` | কোনোটি নয় |

## ৫. সীমাবদ্ধতা

- `PlatformEngine` singleton; `NativeApiIntegration` নতুন instance বানায় না
- Sensor (`SensorHub`) আছে, JS action নেই
