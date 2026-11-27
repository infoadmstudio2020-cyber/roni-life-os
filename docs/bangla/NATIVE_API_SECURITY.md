# Native API Security — Phase 12

## ১. Project identity

**JavaScript কখনো `projectId` পাঠায় না।** `NativeStorageCommandHandler` এটি নেয়
`NativeUIHost.getProjectId()` থেকে — অর্থাৎ trusted native host context থেকে।

```
JS: storage.set('k','v')        ← কোনো projectId নেই
  → NativeStorageCommandHandler
      → uiHost.getProjectId()   ← native সত্য
          → StorageScope.scopedKey(projectId, 'k')
```

JS থেকে `projectId` পাঠালেও তা **উপেক্ষিত** হয়।

## ২. Project isolation ম্যাট্রিক্স

| API | Isolated? | কীভাবে |
|---|---|---|
| `storage.*` | ✅ | `StorageScope` prefix |
| `theme.*` | ❌ | একটিই `ThemeManager` |
| `analytics.*` | ⚠️ | config-এ projectId নেই |
| `logger.*` | ❌ | tag ভিত্তিক |
| `devtools.*` | ✅ | debug-only, release-এ route নেই |
| `network.*` | — | stateless |
| `platform.*` | — | read-only |
| `service.*` | — | registry global |

## ৩. Origin gate

| নিয়ম | মান |
|---|---|
| `requireOriginValidation` | `true` (ডিফল্ট) |
| অনুমোদিত origin | `https://appassets.androidplatform.net` |
| Per-project host | `https://<slug>-<hash8>.runtime.local` |
| Max message | ১ MiB |
| Timeout | ১০s |

## ৪. Biometric

| নিয়ম | বাস্তবায়ন |
|---|---|
| Credential JS-এ যায় না | শুধু `status`/`method` |
| Fake success নেই | callback ছাড়া `SUCCEEDED` পাঠানো হয় না |
| Activity ছাড়া | `ERROR_NO_HOST` (-1000) |
| Headless simulation | ডিফল্টে বন্ধ |
| Bypass | কোনো API নেই |

## ৫. DevTools

| নিয়ম | বাস্তবায়ন |
|---|---|
| Release-এ অনুপস্থিত | `isDebugEnabled()` false → route নেই |
| Storage ডেটা নয় | `getManifest` শুধু build তথ্য |
| Secret নয় | কোনো action credential ফেরত দেয় না |

## ৬. Network

| নিয়ম | আচরণ |
|---|---|
| Scheme | শুধু http/https |
| Forbidden header | `Host`, `Content-Length`, `Connection` |
| Timeout | সর্বোচ্চ ১২০s |
| Redirect | engine নিয়ন্ত্রিত |

## ৭. Storage

| নিয়ম | আচরণ |
|---|---|
| Cross-project read | অসম্ভব — prefix ভিন্ন |
| `clear()` | শুধু নিজের scope |
| Reserved key | `__p:` prefix ব্যবহারকারী লিখতে পারে না |
| Encryption | AES-GCM + Android Keystore |

## ৮. অবশিষ্ট ঝুঁকি

| ঝুঁকি | প্রভাব | সুপারিশ |
|---|---|---|
| `analytics` project attribution নেই | event মিশে যেতে পারে | config-এ projectId যোগ করুন |
| `theme` global | এক project অন্যটির theme বদলায় | per-project `ThemeManager` |
| `logger` global | log মিশে যায় | tag-এ project prefix দিন |
| `keystore` alias global | alias সংঘর্ষ | alias-এ project prefix দিন |
