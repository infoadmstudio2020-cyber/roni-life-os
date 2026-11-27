# Web Project Structure — রানটাইম যা সত্যিই পড়ে

> এই ডকের প্রতিটি নিয়ম `ProjectImporter`, `ProjectManifest`, `WebSource`,
> `AssetResourceProvider`, `MimeTypes` — এই পাঁচটি ক্লাসের কোড থেকে নেওয়া।
> কোনোটা অনুমান নয়।

## ১. সবচেয়ে গুরুত্বপূর্ণ কথা

**রানটাইম আপনার ফোল্ডারের নাম নিয়ে কোনো ধারণা রাখে না।**

`styles/`, `scripts/`, `components/`, `pages/` — এগুলোর কোনো বিশেষ অর্থ নেই।
রানটাইম শুধু দুটি জিনিস জানে:

1. **একটি entry HTML ফাইল** (ডিফল্ট `index.html`)
2. **সেই ফাইল থেকে যেসব relative path আসে** — সেগুলো একই ফোল্ডার-বৃক্ষে থাকতে হবে

তাই নিচের কাঠামো **প্রস্তাবিত (recommended)**, বাধ্যতামূলক নয়। আপনি চাইলে সব ফাইল
একই ফোল্ডারে রাখতে পারেন — রানটাইম তাতে আপত্তি করবে না।

## ২. Entry point কীভাবে নির্ধারিত হয়

`ProjectImporter.detectEntryPoint(File directory)` — ঠিক এই ক্রমে:

| ধাপ | শর্ত | ফল |
|---|---|---|
| ১ | `manifest.json`-এ `entryPoint` আছে | সেটিই ব্যবহার হবে (ফাইলটি থাকতে **হবে**) |
| ২ | root-এ `index.html` আছে | `index.html` |
| ৩ | root-এ অন্য কোনো `.html` আছে | প্রথমটি (filesystem order) |
| ৪ | কোনো `.html` নেই root-এ | recursive খোঁজ, সবচেয়ে কম গভীরেরটি |
| ৫ | কিছুই নেই | `index.html` (তারপর 404) |

**উপসংহার:** `index.html` root-এ রাখাই নিরাপদ। অন্য নাম দিলে `manifest.json`-এ
`entryPoint` লিখে দিন — নাহলে ফাইল-ক্রমের উপর ভরসা করতে হবে।

## ৩. `manifest.json` — একমাত্র কনফিগ ফাইল

```json
{
  "id": "bangla-notes",
  "entryPoint": "index.html"
}
```

| নিয়ম | মান |
|---|---|
| ফাইলের নাম | `manifest.json` (হুবহু) |
| সর্বোচ্চ আকার | ৬৪ KiB (`ProjectManifest.MAX_BYTES`) |
| ফরম্যাট | JSON object — `{` দিয়ে শুরু, `}` দিয়ে শেষ |
| `entryPoint` | safe relative path, এবং ফাইলটি **থাকতে হবে** |
| `id` | safe identifier (`[A-Za-z0-9._-]` ধরনের) |
| না থাকলে | ত্রুটি নয় — entry স্বয়ংক্রিয়ভাবে খোঁজা হবে |

**`entryPoint` unsafe হলে import ব্যর্থ হয়।** যেমন `../index.html`, `/etc/passwd`,
`a/../../b.html` — সব rejected।

> ⚠️ `manifest.json`-এ `name`, `version`, `icon`, `permissions` ইত্যাদি লিখলে
> **রানটাইম সেগুলো পড়বে না**। `ProjectManifest` শুধু `entryPoint` আর `id` পড়ে।
> বাড়তি ফিল্ড রাখা যায় (অবজ্ঞাত), কিন্তু সেগুলোর কোনো প্রভাব নেই।

## ৪. ফাইলের শ্রেণীবিভাগ

### A. বাধ্যতামূলক (Required)

| ফাইল | কেন |
|---|---|
| entry HTML (`index.html`) | এটি ছাড়া WebView কিছু দেখাবে না |

ব্যস। একটিই বাধ্যতামূলক ফাইল।

### B. ঐচ্ছিক (Optional)

| ফাইল | কাজ |
|---|---|
| `manifest.json` | entry point ও id স্পষ্ট করা |
| `styles/*.css` | স্টাইল |
| `scripts/*.js` | লজিক |
| `assets/**` | ছবি, ফন্ট, অডিও |
| `favicon.ico` | আইকন (WebView-এ প্রভাব সীমিত) |

### C. প্রস্তাবিত (Recommended)

```
my-web-app/
├── index.html              ← entry (required)
├── manifest.json           ← entry + id (recommended)
├── styles/
│   ├── main.css
│   └── responsive.css
├── scripts/
│   ├── app.js              ← entry module (type="module")
│   ├── native-runtime.js   ← bridge wrapper
│   └── modules/
│       ├── storage.js
│       └── ui.js
├── assets/
│   ├── images/
│   ├── icons/
│   └── fonts/
└── README.md
```

এই কাঠামো **কোনোভাবেই বাধ্যতামূলক নয়**। `components/`, `pages/`, `config/` —
ইচ্ছা হলে যোগ করুন, রানটাইম নিরপেক্ষ।

### D. রানটাইম-উৎপন্ন (Runtime-generated)

| জিনিস | কোথায় | কে বানায় |
|---|---|---|
| `projects/<project-id>/` | app internal storage | `ProjectImporter.importZip()` |
| `resources/` | app internal storage | `ProjectImporter.resourcesRoot()` |
| WebView cache | `context.getCacheDir()` | WebView নিজে |
| `localStorage` / `IndexedDB` | WebView data dir | WebView নিজে |

এগুলো **আপনার ZIP-এ রাখবেন না**।

### E. অভ্যন্তরীণ এনক্রিপ্টেড স্টোরেজ

| জিনিস | অবস্থান | ফরম্যাট |
|---|---|---|
| `EncryptedStore` ডেটা | SharedPreferences `native_runtime_web_storage` | AES-GCM, Base64 |
| project metadata | `ProjectStore` | JSON |
| vault | `ProjectVaultRuntime` | এনক্রিপ্টেড |

এগুলো **অ্যাপের private storage**-এ থাকে, project ফোল্ডারে নয়।

### F. কখনো হাতে সম্পাদনা করবেন না

| ফাইল | কারণ |
|---|---|
| `projects/<id>/`-এর ভেতরের সব | import-এর সময় overwrite হবে |
| `native_runtime_web_storage` prefs | এনক্রিপ্টেড, হাতে লিখলে corrupt |
| `ProjectStore` JSON | schema বদলালে project হারাবে |
| `__p:*` prefix-যুক্ত storage key | `StorageScope`-এর reserved namespace |

## ৫. পাথ রেজোলিউশন — কীভাবে ফাইল খোঁজা হয়

`AssetResourceProvider.open(path)`:

```
path = "styles/main.css"
  → base + "/" + path  (base = project root)
  → tryOpen("styles/main.css")
  → না পেলে tryOpen("styles/main.css/index.html")   ← directory হলে
```

**নিয়ম:**

| নিয়ম | উদাহরণ |
|---|---|
| path সবসময় project root-এর সাপেক্ষে | `/styles/main.css` → `styles/main.css` |
| `..` দিয়ে root-এর বাইরে যাওয়া যাবে না | `WebPaths.isSafe()` ব্লক করে |
| absolute path (`/etc/...`) ব্লক | rejected |
| backslash normalize হয় | `styles\main.css` → `styles/main.css` |
| URL-encoded traversal ব্লক | `%2e%2e%2f` rejected |

## ৬. সমর্থিত ফাইল এক্সটেনশন (৩৩টি)

`MimeTypes.java` থেকে হুবহু:

| ধরন | এক্সটেনশন |
|---|---|
| ডকুমেন্ট | `html`, `htm`, `css`, `js`, `mjs`, `json`, `map`, `xml`, `txt`, `csv` |
| ছবি | `svg`, `png`, `jpg`, `jpeg`, `gif`, `webp`, `ico`, `bmp` |
| ফন্ট | `woff`, `woff2`, `ttf`, `otf`, `eot` |
| মিডিয়া | `mp3`, `wav`, `ogg`, `mp4`, `webm` |
| অন্যান্য | `wasm`, `pdf`, `zip` |

তালিকায় না থাকা এক্সটেনশন → `application/octet-stream` (ডাউনলোড হবে, রেন্ডার হবে না)।

## ৭. ES6 module ও relative import

`<script type="module" src="scripts/app.js">` কাজ করে, কারণ MIME `application/javascript`।

```js
// scripts/app.js
import { Native } from './native-runtime.js';        // ✅ একই ফোল্ডার
import { save } from './modules/storage.js';         // ✅ subfolder
import { x } from '/scripts/modules/storage.js';     // ✅ root-relative
import { y } from '../scripts/app.js';               // ⚠️ root ছাড়ালে ব্লক
```

| নিয়ম | অবস্থা |
|---|---|
| `./` ও `../` (root-এর ভেতরে) | ✅ |
| root-relative `/scripts/...` | ✅ |
| bare specifier `import x from 'lodash'` | ❌ — import map বা bundled file দরকার |
| `import()` dynamic | ✅ |
| `import ... assert { type: 'json' }` | ⚠️ WebView version-নির্ভর, যাচাই করা হয়নি |

## ৮. Virtual host — project isolation

প্রতিটি project আলাদা origin পায়:

```
https://<slug>-<hash8>.runtime.local
```

| কেন | ফল |
|---|---|
| `localStorage` আলাদা | project A, B-এর key দেখে না |
| `IndexedDB` আলাদা | আলাদা database |
| cookie আলাদা | session মিশে যায় না |
| service worker scope আলাদা | একটি project অন্যটিকে intercept করতে পারে না |

legacy shared host: `https://appassets.androidplatform.net` — শুধু পুরোনো project-এর জন্য।

## ৯. চারটি সোর্স ধরন

`WebSourceType`:

| ধরন | location | entry |
|---|---|---|
| `REMOTE_URL` | `https://example.com/app/` | URL-ই |
| `ASSET` | `webapp/index.html` (APK assets) | `index.html` |
| `FILE` | `/sdcard/.../app/` | `index.html` |
| `ZIP` | `app.zip` (assets বা file) | ভেতরের `index.html` |
| `INLINE_HTML` | — | in-memory string |
| `CONTENT_URI` | `content://...` (SAF) | `index.html` |

## ১০. ZIP import-এর নিরাপত্তা সীমা

`ArchiveExtractor`:

| সীমা | মান | অতিক্রম করলে |
|---|---|---|
| এন্ট্রি সংখ্যা | ৫,০০০ | import ব্যর্থ |
| মোট আকার | ২০০ MiB | import ব্যর্থ |
| entry নাম | `isSafeEntryName()` | ওই entry বাদ |
| `../` traversal | ব্লক | entry বাদ |
| absolute path | ব্লক | entry বাদ |

## ১১. চেকলিস্ট — ZIP বানানোর আগে

- [ ] root-এ `index.html` আছে
- [ ] `manifest.json` থাকলে বৈধ JSON, `entryPoint` ফাইলটি আছে
- [ ] সব `<link href>` ও `<script src>` relative
- [ ] সব `import` path ফাইলে পৌঁছায়
- [ ] কোনো absolute path (`/Users/...`, `C:\...`) নেই
- [ ] `node_modules/`, `.git/`, `build/` বাদ দেওয়া হয়েছে
- [ ] মোট আকার ২০০ MiB-এর কম, ফাইল সংখ্যা ৫,০০০-এর কম
- [ ] ZIP-এর ভেতরে **একটি** root ফোল্ডার (বা সরাসরি ফাইল) — দুইভাবেই চলে
