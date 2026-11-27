# DevTools API Guide — `devtools.*`

## ১. শুধু debug build

`NativeApiIntegration` এই route নিবন্ধন করে **কেবল** যখন:

```java
NativeRuntimeUIConfig.isDebugEnabled() == true
```

Release build-এ route নেই → JS পায় `ROUTE_NOT_FOUND`।

```js
if (!await Native.isSupported('devtools')) {
  console.log('devtools শুধু debug build-এ');
  return;
}
```

## ২. Action (৯)

| Action | Payload | Response |
|---|---|---|
| `devtools.getMetrics` | — | profiler metrics |
| `devtools.getHealth` | — | health snapshot |
| `devtools.getBridgeMetrics` | — | bridge counters |
| `devtools.getManifest` | — | build manifest |
| `devtools.exportHar` | — | HAR JSON |
| `devtools.clearTraffic` | — | `{cleared:true}` |
| `devtools.startProfiling` | — | `{started:true}` |
| `devtools.stopProfiling` | — | `{stopped:true}` |
| `devtools.dispatchCdp` | `{domain, method, params}` | CDP response |

## ৩. ব্যবহার

```js
import { devtools } from './native-runtime.js';

const health = await devtools.health();
console.table(health);

await devtools.startProfiling();
// ... কাজ ...
const metrics = await devtools.stopProfiling();
```

## ৪. নিরাপত্তা

| নিয়ম | বাস্তবায়ন |
|---|---|
| Release-এ অনুপস্থিত | route নিবন্ধনই হয় না |
| Storage ডেটা প্রকাশ নয় | `getManifest` শুধু build তথ্য |
| Token/secret নয় | কোনো action credential ফেরত দেয় না |
| Explicit authorization | `isDebugEnabled()` ফ্ল্যাগ |

## ৫. সীমাবদ্ধতা

- `dispatchCdp` শুধু নিবন্ধিত domain হ্যান্ডেল করে (`CdpDomainDispatcher`)
- HAR export মেমোরিতে তৈরি হয়; বড় traffic-এ সাবধান
