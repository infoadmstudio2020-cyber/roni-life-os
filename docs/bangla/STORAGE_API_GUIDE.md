# Storage API Guide — `storage.*`

## ১. Project isolation

প্রতিটি key project-scoped:

```
__p:<escaped-project-id>:<user-key>
```

| ক্রিয়া | ফল |
|---|---|
| A: `storage.set('theme','dark')` | `__p:A:theme` |
| B: `storage.get('theme')` | `null` |
| A: `storage.clear()` | শুধু `__p:A:*` |
| A: `storage.keys()` | prefix ছাড়া key |

## ২. Action (৭)

| Action | Payload | Response |
|---|---|---|
| `storage.get` | `{key}` | `{value}` |
| `storage.set` | `{key, value}` | `{saved:true}` |
| `storage.remove` | `{key}` | `{removed:true}` |
| `storage.clear` | — | `{cleared:n}` |
| `storage.keys` | — | `{keys:[...]}` |
| `storage.has` | `{key}` | `{exists:boolean}` |
| `storage.getAll` | — | `{values:{...}}` |

## ৩. ব্যবহার

```js
import { storage } from './native-runtime.js';

await storage.set('profile', { name: 'রনি', lang: 'bn' });
const { value } = await storage.get('profile');
const { keys } = await storage.keys();
await storage.remove('profile');
```

## ৪. Route সংঘর্ষের সমাধান

দুটি handler ছিল:

| Handler | getPath() | নিবন্ধিত? |
|---|---|---|
| `NativeStorageCommandHandler` (runtime-ui) | `storage` | ✅ |
| `StorageCommandHandler` (storage-engine) | `storage` | ❌ |

দ্বিতীয়টি নিবন্ধন করলে প্রথমটি overwrite হয়ে project isolation ভাঙত। তাই
`NativeApiIntegration` এটিকে `SKIPPED` হিসেবে রেকর্ড করে, নিবন্ধন করে না।

## ৫. Legacy migration

| ধাপ | আচরণ |
|---|---|
| প্রথম project | `__p:__legacy_claimed` দাবি করে |
| পুরোনো unscoped key | সেই project-এর scope-এ migrate |
| দ্বিতীয় project | unscoped key দেখে না |
| Marker | `__migrated_v1` |

## ৬. সীমাবদ্ধতা

| বিষয় | অবস্থা |
|---|---|
| `clear()` return | `{cleared:n}` — আগে `true` ছিল |
| Storage event | ❌ নেই |
| Quota | `EncryptedStore` সীমা নেই; বড় blob সাবধান |
| Encryption | AES-GCM, Android Keystore key |
