# Network API Guide — `network.*`

## ১. একটিই HTTP client

`native-network-engine`-এর `HttpClientEngine`। Retrofit, OkHttp, Volley — কিছুই নেই।
`DownloadManager` আলাদা pipeline, duplicate করা হয়নি।

## ২. Action (১)

| Action | Payload | Response |
|---|---|---|
| `network.request` | `{url, method, headers, body, timeoutMs}` | `{statusCode, body, latencyMs, method, url}` |

`method`: `GET` `POST` `PUT` `PATCH` `DELETE` `HEAD` `OPTIONS`

## ৩. ব্যবহার

```js
import { network } from './native-runtime.js';

const res = await network.get('https://api.example.com/items');
console.log(res.statusCode, res.body);

await network.post('https://api.example.com/items',
  JSON.stringify({ name: 'নতুন' }),
  { headers: { 'Content-Type': 'application/json' }, timeoutMs: 10000 });
```

## ৪. থ্রেডিং

`NativeRouter.dispatch()` handler-কে নিজের executor-এ চালায়। তাই blocking
`execute()` UI thread-এ যায় না।

⚠️ `dispatchSync()` দিয়ে ডাকলে caller thread ব্লক হবে — main thread থেকে ডাকবেন না।

## ৫. নিরাপত্তা

| নিয়ম | আচরণ |
|---|---|
| শুধু `http://` ও `https://` | অন্য scheme → `UNSUPPORTED_SCHEME` |
| `Host`, `Content-Length`, `Connection` header | `FORBIDDEN_HEADER` |
| Timeout | ডিফল্ট ১৫s, সর্বোচ্চ ১২০s |
| Redirect | `HttpClientEngine` নিয়ন্ত্রণ করে |

## ৬. ত্রুটি

| Code | অর্থ |
|---|---|
| `INVALID_URL` | `url` খালি |
| `UNSUPPORTED_SCHEME` | http/https নয় |
| `UNSUPPORTED_METHOD` | অজানা method |
| `FORBIDDEN_HEADER` | নিষিদ্ধ header |
| `NETWORK_ERROR` | সংযোগ/সার্ভার ত্রুটি |

## ৭. যা নেই

| সক্ষমতা | কারণ |
|---|---|
| Request cancellation | `execute()` synchronous, token নেই |
| Upload progress | `DownloadManager` শুধু download |
| WebSocket | `WebSocketClient` আছে, JS action নেই |
| SSL pinning config | `SslPinningManager` আছে, JS action নেই |
