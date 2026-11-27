# Analytics API Guide — `analytics.*`

## ১. Action (১১)

| Action | Payload | Response |
|---|---|---|
| `analytics.trackEvent` | `{name, properties}` | `{tracked:true}` |
| `analytics.recordMetric` | `{name, value}` | `{recorded:true}` |
| `analytics.startSession` | — | `{sessionId}` |
| `analytics.endSession` | — | `{durationMs}` |
| `analytics.startTrace` | `{name}` | `{started:true}` |
| `analytics.stopTrace` | `{name}` | `{durationMs}` |
| `analytics.startScreenView` | `{screen}` | `{screen}` |
| `analytics.endScreenView` | `{screen}` | `{durationMs}` |
| `analytics.getStats` | — | `{...}` |
| `analytics.flush` | — | `{flushed:true}` |
| `analytics.clear` | — | `{cleared:true}` |

## ২. ব্যবহার

```js
import { analytics } from './native-runtime.js';

await analytics.startSession();
await analytics.track('checkout_started', { items: 3, currency: 'BDT' });
await analytics.screenView('checkout');
await analytics.metric('cart_value', 1250);
await analytics.flush();
```

## ৩. আর্কিটেকচার

```
AnalyticsCommandHandler
  └─ NativeAnalyticsService
       ├─ EventQueue
       ├─ SessionTracker
       ├─ PerformanceTracker
       └─ CompositeAnalyticsProvider
            ├─ MemoryAnalyticsProvider
            └─ DiskBufferAnalyticsProvider  (storage-engine)
```

`NativeAnalyticsService` একবার তৈরি হয় `NativeApiIntegration`-এ। দ্বিতীয় instance নেই।

## ৪. Privacy

| বিষয় | অবস্থা |
|---|---|
| Consent API | ❌ `NativeAnalyticsEngineConfig`-এ consent ফ্ল্যাগ নেই |
| PII ফিল্টার | ❌ নেই — caller-কে সাবধান হতে হবে |
| Project attribution | ⚠️ config-এ projectId ফিল্ড নেই |
| Local-only | ✅ ডিফল্টে কোনো remote upload নেই (`BatchUploader` আলাদা করে চালাতে হয়) |

**সুপারিশ:** `trackEvent`-এ কখনো নাম, ফোন, ইমেইল, টোকেন পাঠাবেন না।

## ৫. সীমাবদ্ধতা

- `analytics.clear` শুধু in-memory queue মুছে; disk buffer আলাদা
- Event retrieval API নেই (`getStats` শুধু সংখ্যা)
