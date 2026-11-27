# Service API Guide — `service.*`

## ১. সৎ কথা প্রথমে

`ServiceCommandHandler`-এ **শুধু দুটি** action আছে। Android Service শুরু/বন্ধ করার কোনো
API নেই। তাই `service.start()` বা `service.stop()` **নেই** — বানানো হয়নি।

## ২. Action (২)

| Action | Payload | Response |
|---|---|---|
| `service.list` | — | `{services:{id: state, ...}}` |
| `service.status` | `{serviceId}` | `{serviceId, state, ...}` |

## ৩. ব্যবহার

```js
import { service } from './native-runtime.js';

const all = await service.list();
console.log(Object.keys(all.services));

const one = await service.status('battery');
console.log(one.state);
```

## ৪. ServiceRegistry

`ServiceRegistry.getInstance()` — অ্যাপের সব `IService` এখানে নিবন্ধিত।

Built-in: `BatteryStatusService`, `DeviceInfoService`, `NetworkStateService`

## ৫. যা নেই — এবং কেন

| সক্ষমতা | কারণ |
|---|---|
| `service.start` | `ServiceCommandHandler`-এ নেই; Android foreground service শুরু করতে `startForegroundService()` দরকার, যা Activity/Context-bound |
| `service.stop` | একই কারণ |
| Background task | `native-service-sdk` শুধু registry, executor নয় |
| Service event | `ServiceLifecycleListener` আছে, কিন্তু bridge-এ emit হয় না |

**Foreground service চাইলে:** অ্যাপ স্তরে `Service` subclass লিখে
`ServiceRegistry`-তে নিবন্ধন করুন, তারপর `service.status` দিয়ে অবস্থা পড়ুন।
