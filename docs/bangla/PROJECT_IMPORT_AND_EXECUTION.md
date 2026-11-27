# Project Import & Execution — ZIP থেকে চালু পর্যন্ত

> সোর্স: `app/.../project/ProjectImporter.java`, `ProjectManifest.java`,
> `ProjectValidator.java`, `ProjectStore.java`, `ProjectRegistrationService.java`,
> `resource/ArchiveExtractor.java`, `SafPermissionManager.java`

## ১. পূর্ণ প্রবাহ

```
ব্যবহারকারী ZIP বেছে নেয় (SAF)
  → ProjectImporter.importZip(context, zipUri, projectName)
       ├─ ArchiveExtractor.extract(stream, target)      ← নিরাপত্তা যাচাই
       ├─ ProjectManifest.validate(target)              ← manifest.json
       ├─ detectEntryPoint(target)                      ← entry খোঁজা
       └─ Result(success, message, directory, entryPoint)
  → ProjectValidator.validate(project, existing)        ← নাম/id সংঘর্ষ
  → ProjectStore.save(project)                          ← persistence
  → ProjectRegistrationService.register(project)        ← runtime-এ নিবন্ধন
  → WebViewHost.load(WebSource.file(dir, entryPoint))   ← চালু
```

## ২. চারটি সোর্স থেকে import

| সোর্স | পদ্ধতি | নোট |
|---|---|---|
| **ZIP** | `importZip(context, zipUri, name)` | সবচেয়ে সাধারণ |
| **ফোল্ডার (SAF)** | `SafPermissionManager` + `SafResourceProvider` | persistable URI permission নিতে হয় |
| **Remote HTTPS** | `WebSource.remote(url)` | import লাগে না, সরাসরি লোড |
| **APK asset** | `WebSource.asset(path)` | ডেভেলপার-বান্ডল করা |

## ৩. ZIP extraction-এর নিরাপত্তা

`ArchiveExtractor.extract()` প্রতিটি entry-তে:

| চেক | ব্যর্থ হলে |
|---|---|
| নাম `isSafeEntryName()` | entry বাদ |
| `../` traversal | entry বাদ |
| absolute path (`/`, `C:\`) | entry বাদ |
| symlink | entry বাদ |
| এন্ট্রি সংখ্যা > ৫,০০০ | **পুরো import ব্যর্থ** |
| মোট আকার > ২০০ MiB | **পুরো import ব্যর্থ** |
| decompressed size mismatch (zip bomb) | entry বাদ |

## ৪. `manifest.json` যাচাই

`ProjectManifest.validate(directory)`:

| অবস্থা | ফল |
|---|---|
| ফাইল নেই | ✅ বৈধ — entry স্বয়ংক্রিয়ভাবে খোঁজা হবে |
| ৬৪ KiB-এর বেশি | ❌ ব্যর্থ |
| JSON object নয় | ❌ ব্যর্থ |
| `entryPoint` unsafe path | ❌ ব্যর্থ |
| `entryPoint` ফাইল নেই | ❌ ব্যর্থ |
| `id` unsafe | ❌ ব্যর্থ |

## ৫. Entry point detection

`detectEntryPoint()` — ক্রম:

1. `manifest.json`-এর `entryPoint`
2. root-এর `index.html`
3. root-এর যেকোনো `.html`
4. recursive সবচেয়ে কম গভীরের `.html`
5. fallback `index.html`

## ৬. Project model

`Project.java`-এর মূল ফিল্ড (আনুমানিক, কোড অনুযায়ী):

| ফিল্ড | কাজ |
|---|---|
| `id` | অনন্য পরিচয় — storage scope-এর ভিত্তি |
| `name` | প্রদর্শিত নাম |
| `type` | `ProjectType` |
| `sourceType` | `ProjectSourceType` |
| `entryPoint` | শুরুর HTML |
| `directory` | extraction path |
| `createdAt` / `updatedAt` | সময় |

## ৭. Storage isolation — project প্রতি আলাদা

প্রতিটি project-এর `storage.*` কল আলাদা namespace-এ যায়:

```
__p:<escaped-project-id>:<user-key>
```

| ক্রিয়া | ফল |
|---|---|
| A-এর `storage.set('theme','dark')` | `__p:A:theme` |
| B-এর `storage.get('theme')` | `null` (A-এর মান দেখে না) |
| A-এর `storage.clear()` | শুধু `__p:A:*` মুছে |
| `storage.keys()` | শুধু নিজের key, prefix ছাড়া |

বিস্তারিত: `STORAGE_ISOLATION.md`।

## ৮. Project switch

```java
NativeRuntimeUI.getInstance().setProjectId(newProject.getId());
webViewHost.load(WebSource.file(newProject.getDirectory(), newProject.getEntryPoint()));
```

`setProjectId()` বদলালে পরের `storage.*` কল নতুন scope-এ যাবে।
**WebView reload করা জরুরি** — নাহলে পুরোনো পেজের JS পুরোনো scope-এ লিখতে থাকবে।

## ৯. Project deletion

`ProjectDeletionService`:

1. `ProjectStore` থেকে entry মুছে
2. `projects/<id>/` recursive delete (`ArchiveExtractor.deleteRecursively`)
3. storage scope-এর key মুছে
4. SAF permission release

## ১০. Restore

`ProjectRestoreService` — অ্যাপ চালু হলে:

1. `ProjectStore` পড়ে
2. প্রতিটি project-এর directory আছে কি না দেখে
3. না থাকলে `ProjectResourceState` আপডেট
4. শেষ project চালু করে (বা home screen)

## ১১. Vault

`ProjectVaultRuntime` + `VaultResourceDecoder` — project-এর সংবেদনশীল resource
এনক্রিপ্ট করে রাখে। `SealDecision` ঠিক করে vault সিল হবে কি না।

## ১২. চেকলিস্ট — import করার আগে

- [ ] ZIP-এ root-এ `index.html` (বা `manifest.json`-এ `entryPoint`)
- [ ] সব path relative, `..` নেই
- [ ] মোট আকার < ২০০ MiB, ফাইল < ৫,০০০
- [ ] `manifest.json` বৈধ JSON, ৬৪ KiB-এর কম
- [ ] `node_modules/`, `.git/` বাদ
