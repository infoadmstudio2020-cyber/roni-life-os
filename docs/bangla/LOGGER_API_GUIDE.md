# Logger API Guide — `logger.*`

## ১. Action (৪ + alias)

| Action | Payload | Response |
|---|---|---|
| `logger.log` | `{level, tag, message}` | `{status:"logged", tag}` |
| `logger.setLevel` | `{level}` | `{currentLevel}` |
| `logger.getLevel` | — | `{currentLevel}` |
| `logger.flush` | — | `{flushed:true}` |

`level`: `VERBOSE` `DEBUG` `INFO` `WARN` `ERROR`
Alias: `setMinimumLevel`, `getMinimumLevel`

## ২. ব্যবহার

```js
import { logger } from './native-runtime.js';

await logger.info('অ্যাপ চালু', 'BOOT');
await logger.warn('ধীর অনুরোধ', 'NET');
await logger.error('ব্যর্থ: ' + error.message, 'API');
```

## ৩. একই logger

`NativeLogger.getInstance()` — অ্যাপের বাকি অংশও এটিই ব্যবহার করে। দ্বিতীয় logging
system নেই।

Sink: `LogcatSink`, `FileLogSink`, `WebConsoleMirrorSink`, `RemoteDiagnosticSink`

## ৪. Flood নিয়ন্ত্রণ

| সুরক্ষা | আচরণ |
|---|---|
| Minimum level | `setLevel` দিয়ে বাড়ান |
| Tag | প্রতিটি লগে tag বাধ্যতামূলক |
| Message | খালি হলে কিছু হয় না |

**সুপারিশ:** লুপের ভেতরে `logger.debug` ডাকবেন না। ব্যাচ করুন।

## ৫. Sensitive data

`NativeLogger` নিজে থেকে PII ফিল্টার করে না। Caller-কে মানতে হবে:

- টোকেন, পাসওয়ার্ড, API key লগ করবেন না
- `storage`-এর মান লগ করবেন না
- ব্যবহারকারীর নাম/ফোন লগ করবেন না

## ৬. যা নেই

| সক্ষমতা | কারণ |
|---|---|
| Log retrieval | `NativeLogger`-এ buffer পড়ার API নেই |
| Log search | একই কারণ |
| Per-project log isolation | tag ভিত্তিক, project ভিত্তিক নয় |
