# Storage Isolation — Project-scoped key namespace

## ১. সমস্যা

`NativeStorageCommandHandler` সরাসরি `EncryptedStore`-এ raw key লিখত:

```java
store.put(key, encode(value));   // আগে
```

`EncryptedStore` পুরো অ্যাপে **একটাই instance** (`StorageEngine.getEncryptedStore()`)।
ফলে Project A-এর `theme` আর Project B-এর `theme` একই slot-এ পড়ত।

তিনটি নির্দিষ্ট ফল:

| ক্রিয়া | আগের আচরণ | পরিণতি |
|---|---|---|
| `storage.get("theme")` | অন্য project-এর মান | তথ্য ফাঁস |
| `storage.set("theme", …)` | অন্য project-এর মান overwrite | নীরব data loss |
| `storage.clear()` | **পুরো store** মুছে | সব project-এর ডেটা ধ্বংস |
| `storage.keys` / `getAll` | সব project-এর key | নাম ফাঁস |

## ২. সমাধান — `StorageScope`

নতুন ক্লাস: `native-storage-engine/src/main/java/com/roni/library/storage/StorageScope.java`

```
__p:<escaped-project-id>:<user-key>
```

| নিয়ম | উদাহরণ |
|---|---|
| project id নেই / blank | prefix `""` → key অপরিবর্তিত (legacy) |
| `abc` | `__p:abc:theme` |
| `a:b` | `__p:a%3Ab:theme` |
| `a%b` | `__p:a%25b:theme` |

**Escaping injective:** `%` → `%25`, `:` → `%3A`। তাই দুটি ভিন্ন project id কখনো একই prefix
বানাতে পারে না — ৪,০০০ random id দিয়ে fuzz করে যাচাই করা হয়েছে।

`__p:` marker reserved। Legacy namespace-এ এমন key লিখতে গেলে `RESERVED_KEY` error ফেরে,
যাতে চুপচাপ হারিয়ে না যায়।

## ৩. Handler-এ প্রয়োগ

`NativeStorageCommandHandler`-এর সাতটি action-ই এখন scope-aware:

| Action | পরিবর্তন |
|---|---|
| `get` | `store.get(scopedKey(pid, key))` |
| `set` | `store.put(scopedKey(pid, key), …)` + reserved-key guard |
| `remove` | `store.remove(scopedKey(pid, key))` |
| `has` | `store.contains(scopedKey(pid, key))` |
| `keys` | `getAll()` ঘুরে `unscopedKey()` != null গুলো নেয়, prefix কেটে দেয় |
| `getAll` | একই ফিল্টার, unscoped key দিয়ে map |
| `clear` | **শুধু** নিজের scope-এর key remove করে, `store.clear()` আর ডাকা হয় না |

`clear` এখন কতগুলো key মুছল তা `{ "cleared": <n> }`-এ ফেরে (আগে `true`)।

## ৪. Project id কোথা থেকে আসে

```java
NativeRuntimeUI.getInstance().init(context, config);
NativeRuntimeUI.getInstance().setProjectId(project.getId());   // নতুন
```

`NativeRuntimeUI.setProjectId()` → `NativeUIHost.setProjectId()` → handler প্রতিবার
`uiHost.getProjectId()` পড়ে। অর্থাৎ WebView লোড হওয়ার **আগে** একবার সেট করাই যথেষ্ট।

`null` বা blank দিলে legacy namespace চলবে — পুরোনো ইনস্টল অপরিবর্তিত থাকবে।

## ৫. Backward compatibility

পুরোনো ডেটা `native_runtime_web_storage` SharedPreferences-এ আছে, prefix ছাড়া।
মাইগ্রেশন এখন **claim** ভিত্তিক:

1. প্রথম project scope খোলার সময় legacy prefs-এর সব key **নিজের scope-এ** কপি হয়।
2. `__p:__legacy_claimed` (global marker) লেখা হয়।
3. পরের project আর claim করে না — শুধু নিজের `__migrated_v1` marker পায়।

| পরিস্থিতি | ফল |
|---|---|
| প্রথম চালু, project id সেট | পুরোনো ডেটা সেই project-এ চলে আসে |
| দ্বিতীয় project | পুরোনো ডেটা পায় না (সঠিক — সেটি অন্য project-এর) |
| project id সেট করা হয়নি | legacy namespace, আগের মতোই সব দেখা যায় |
| মাইগ্রেশন ব্যর্থ | marker লেখা হয় না, পরেরবার আবার চেষ্টা |

**সীমাবদ্ধতা:** legacy ফাইলে project তথ্য নেই, তাই "প্রথম যে project চালু হবে" সেটিই দাবি করে।
এটি অনিবার্য। যদি অ্যাপ জানে কোন project-এর ডেটা এটি, তবে `setProjectId()` মাইগ্রেশনের
আগেই ডাকুন।

## ৬. যাচাই

`tasks/verify/storage_scope_test.py` — Java অ্যালগরিদমের faithful port, **৬৮টি assertion**:

| বিভাগ | কী প্রমাণ করে |
|---|---|
| prefix derivation | null/blank → legacy, trim, escaping |
| escape injectivity | ৪,০০০ id, শূন্য collision |
| round-trip | ৭ project × ৫ key, সব ফেরত আসে |
| isolation | A, B-এর key পড়তে/লিখতে পারে না |
| scoped clear | A-এর clear-এ B টিকে থাকে |
| legacy namespace | legacy শুধু prefix-হীন key দেখে, project key দেখে না |
| reserved marker | `__migrated_v1` ও claim marker তালিকায় আসে না |
| migration claim | প্রথম project পায়, দ্বিতীয় পায় না, পুনরাবৃত্তি নিরাপদ |

> **সীমা:** এখানে `javac` নেই (শুধু JRE 17), তাই Java ফাইলটি **কম্পাইল করা যায়নি**।
> উপরের টেস্ট অ্যালগরিদম ও handler-এর নিয়ম যাচাই করে, বাইটকোড নয়।
> `java_sanity.py` দিয়ে brace/paren ভারসাম্য ও duplicate method যাচাই করা হয়েছে (০ সমস্যা)।

## ৭. ভবিষ্যতের কাজ

- `NativeRuntimeUI`-এ project switch হলে `storageCommandHandler`-এর `migrationAttempted`
  রিসেট করা (এখন একবারই চলে, যা একটি process-এ এক project-এর জন্য ঠিক)।
- `storage.clear()`-এর নতুন return shape (`true` → `{cleared:n}`) নিয়ে changelog entry।
