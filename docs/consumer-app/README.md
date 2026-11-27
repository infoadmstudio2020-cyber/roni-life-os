# RONi LiFE OS — Consumer Application

**মডিউল:** `:app` · **প্যাকেজ:** `com.roni.life.os.app` · **ভাষা:** Java 17 + XML Layout

`NativeRuntime Ecosystem`-এর ১৫টি লাইব্রেরি মডিউলের প্রকৃত পাবলিক API ব্যবহার করে তৈরি একটি সম্পূর্ণ
Android কনজিউমার অ্যাপ। এটি একাধিক ওয়েব-ভিত্তিক প্রজেক্ট তৈরি, কনফিগার, সংরক্ষণ ও রানটাইম হোস্ট করে।

---

## ১. তিনটি Activity

| Activity | ভূমিকা | Launcher |
| :--- | :--- | :--- |
| `SplashActivity` | Main Launcher ও Startup Router | ✅ |
| `BootstrapActivity` | Main Application, Project Manager ও Configuration Controller | — |
| `ProjectActivity` | নির্বাচিত Project-এর Runtime Host | — |

তিনটি ছাড়া অন্য কোনো Activity নেই।

---

## ২. Main Application Startup Matrix

`SplashActivity` চালু হওয়ার সাথে সাথেই সিদ্ধান্ত নেয় (`StartupDecision.resolveMain`)।

| Splash | Bootstrap | গন্তব্য |
| :---: | :---: | :--- |
| ON | ON | Splash → `BootstrapActivity` |
| OFF | ON | সরাসরি `BootstrapActivity` |
| ON | OFF | Splash → `ProjectActivity` |
| OFF | OFF | সরাসরি `ProjectActivity` |

**Fallback:** Bootstrap বন্ধ কিন্তু কোনো বৈধ প্রজেক্ট resolve হয়নি → নিরাপদে `BootstrapActivity`।
Splash বন্ধ থাকলে `SplashActivity` কোনো layout inflate না করেই রুট করে (শূন্য ফ্লিকার)।

প্রজেক্ট resolve-এর অগ্রাধিকার: `lastOpenedProjectId` → `defaultProjectId` → `isDefault` ফ্ল্যাগ → কিছুই নয়।

---

## ৩. Project-Level Startup Matrix

প্রতিটি প্রজেক্টের **নিজস্ব** Splash ও Bootstrap টগল আছে — Main App-এর থেকে সম্পূর্ণ আলাদা।

| Project Splash | Project Bootstrap | প্রবাহ |
| :---: | :---: | :--- |
| ON | ON | Project Splash → Bootstrap Process → Web Runtime |
| OFF | ON | Bootstrap Process → Web Runtime |
| ON | OFF | Project Splash → Web Runtime |
| OFF | OFF | সরাসরি Web Runtime |

Bootstrap Process-এর ধাপ: ভ্যালিডেশন → সোর্স পরীক্ষা → রানটাইম প্রস্তুতি → `WebSource` রেজলভ।
সব ধাপ ব্যাকগ্রাউন্ড থ্রেডে, ফলাফল মেইন থ্রেডে।

---

## ৪. Project Management (`BootstrapActivity`)

- **তৈরি / সম্পাদনা / কপি / মুছুন** — সম্পূর্ণ CRUD
- **তালিকা** — ডিফল্ট প্রজেক্ট প্রথমে, তারপর সর্বশেষ খোলা, তারপর নাম অনুসারে
- **সোর্স টাইপ** — `ASSET`, `ZIP_ASSET`, `FILE`, `ZIP_FILE`, `REMOTE_URL`, `INLINE_HTML`
- **সোর্স কনফিগ** — পাথ/URL, এন্ট্রি পয়েন্ট, বেস URL, ইনলাইন HTML
- **আইকন রঙ** — ৬টি প্রিসেট থেকে নির্বাচন
- **প্রতি-প্রজেক্ট টগল** — Splash ON/OFF, Bootstrap ON/OFF, Splash সময়সীমা (২০০–১০০০০ ms)
- **ডিফল্ট প্রজেক্ট** — একটি প্রজেক্টকে ডিফল্ট করা হলে বাকিগুলোর ফ্ল্যাগ স্বয়ংক্রিয়ভাবে সরে যায়
- **ভ্যালিডেশন** — খালি/ডুপ্লিকেট নাম, অনুপস্থিত পাথ, ভুল URL, খালি ইনলাইন HTML
- **অ্যাপ স্টার্টআপ সেটিংস** — Main App Splash/Bootstrap/Telemetry/Debug
- **রানটাইম ডায়াগনস্টিকস** — ১৪টি ইঞ্জিনের প্রকৃত ইনিশিয়ালাইজেশন অবস্থা

ব্যবহারকারী কোনো প্রজেক্ট নির্বাচন না করা পর্যন্ত `BootstrapActivity` খোলা থাকে।

---

## ৫. ডেটা মডেল (`Project`)

| ফিল্ড | ধরন | বিবরণ |
| :--- | :--- | :--- |
| `id` | String | ইউনিক আইডেন্টিফায়ার (UUID) |
| `name` | String | প্রজেক্টের নাম (≤ ৬০ অক্ষর, ইউনিক) |
| `description` | String | বিবরণ (≤ ২৪০ অক্ষর) |
| `iconColor` | int | ARGB রঙ |
| `sourceType` | enum | ৬টি সোর্স টাইপ |
| `sourcePath` | String | অ্যাসেট পাথ / ফাইল পাথ / URL |
| `entryPoint` | String | ডিফল্ট `index.html` |
| `baseUrl` | String | ইনলাইন HTML-এর বেস URL |
| `inlineHtml` | String | সরাসরি HTML কনটেন্ট |
| `splashEnabled` | boolean | প্রজেক্ট স্প্ল্যাশ |
| `bootstrapEnabled` | boolean | প্রজেক্ট বুটস্ট্র্যাপ |
| `splashDurationMs` | int | ২০০–১০০০০ ms |
| `isDefault` | boolean | ডিফল্ট প্রজেক্ট |
| `lastOpenedAt` | long | সর্বশেষ খোলার সময় |
| `createdAt` / `updatedAt` | long | টাইমস্ট্যাম্প |

সিরিয়ালাইজেশন **URL-encoded Key-Value** (`Codec`) — Android নির্ভরতা ছাড়াই কাজ করে, তাই
`Project` ও `AppConfig` সরাসরি JUnit-এ টেস্ট করা যায়।

---

## ৬. স্টোরেজ

সব ডেটা `NativeStorageEngine`-এর `KeyValueStore`-এ সংরক্ষিত হয়।
`KeyValueBackend` ইন্টারফেসের মাধ্যমে এটি বিমূর্ত — প্রোডাকশনে `KeyValueStoreBackend`,
টেস্টে ইন-মেমরি ইমপ্লিমেন্টেশন।

স্টোরেজ ইঞ্জিন অনুপলব্ধ হলে `AppInitializer` একই লাইব্রেরির `KeyValueStore` সরাসরি
`filesDir/runtime/`-এ তৈরি করে — অ্যাপ কখনো স্টোরেজের অভাবে ক্র্যাশ করে না।

---

## ৭. প্রথম রান

প্রথম ইনস্টলেশনে `AppInitializer` স্বয়ংক্রিয়ভাবে একটি **ডেমো প্রজেক্ট** তৈরি করে
(`ZIP_ASSET` → `webapp.zip` → `index.html`), সেটিকে ডিফল্ট করে এবং `seeded` ফ্ল্যাগ সেট করে।
ফলে ব্যবহারকারী প্রথমবারই একটি কার্যকর রানটাইম দেখতে পান।

---

## ৮. ডকুমেন্টেশন সূচি

| ফাইল | বিষয় |
| :--- | :--- |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | স্তরবিন্যাস, প্যাকেজ, ক্লাস দায়িত্ব, ডেটা প্রবাহ |
| [`BUILD_INSTRUCTIONS.md`](BUILD_INSTRUCTIONS.md) | বিল্ড, রান ও ভ্যালিডেশন |
| [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md) | সীমাবদ্ধতা ও অসম্পূর্ণ ইন্টিগ্রেশন |
| [`AUDIT_REPORT.md`](AUDIT_REPORT.md) | ১৫টি মডিউলের API অডিট ও গ্যাপ |

---

## ৯. স্ট্যাটিক ভ্যালিডেশন

```bash
python3 tools/validate_project.py
```

যাচাই করে: Java গঠন, `R.*` রেফারেন্স, XML রিসোর্স রেফারেন্স, Manifest ক্লাস, Gradle মডিউল।
বর্তমান অবস্থা: **২৫টি Java ফাইল, ৫২টি রিসোর্স আইডি, ৭টি লেআউট, ০ ত্রুটি, ০ সতর্কতা**।
