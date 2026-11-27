# Secure Project Storage ও WebView Serving — বিশ্লেষণ ও বাস্তবায়ন রিপোর্ট

## ১. প্রথমে যা পরীক্ষা করা হয়েছে

কোড পড়ে দেখা গেছে, আপনার চাওয়া সাতটি বিষয়ের **ছয়টি আগে থেকেই বাস্তবায়িত**।
নতুন করে কিছু বানানোর আগে সেগুলো এখানে তালিকাভুক্ত করা হলো।

| প্রয়োজন | অবস্থা | প্রমাণ (ফাইল) |
|---|---|---|
| HTTPS Virtual Origin | ✅ আছে | `WebSourceResolver.VIRTUAL_ORIGIN = "https://" + VIRTUAL_HOST` |
| Storage থেকে Resource Serve | ✅ আছে | `WebViewHost.shouldInterceptRequest(...)` |
| Bridge Origin Validation | ✅ আছে | `BridgeOriginValidationTest.virtualOriginAllowedByDefault` |
| SAF ফোল্ডার সরাসরি Serve | ✅ আছে | `SafWebSourceProvider`, `SafPermissionManager` |
| ZIP ইমপোর্ট | ✅ আছে | `ArchiveExtractor`, `ProjectImporter` |
| Remote HTTPS প্রজেক্ট | ✅ আছে | `WebSourceFactory`, `ProjectBrowserLauncher` |
| **Encryption at Rest** | ❌ **ছিল না** | `security/crypto/`-এ শুধু `CryptoHash.java` |

অর্থাৎ `file://` ব্যবহার হচ্ছে না, Virtual Origin দিয়ে Serving হচ্ছে, এবং SAF/ZIP/Remote —
তিন পথই বিদ্যমান। **একমাত্র প্রকৃত ঘাটতি ছিল ডিস্কে ফাইল এনক্রিপশন।**

---

## ২. যা নতুন যোগ করা হলো

### `ProjectVault` — `native-security-engine/security/crypto/ProjectVault.java`

| দিক | বাস্তবায়ন |
|---|---|
| অ্যালগরিদম | AES-256/GCM/NoPadding |
| কী ব্যবস্থাপনা | Android Keystore, প্রতি প্রজেক্টে আলাদা alias `ronilifeos.project.<id>` |
| কী ডিভাইস ছাড়ে? | না — Keystore-এ থাকে, অ্যাপ শুধু রেফারেন্স পায় |
| Integrity | GCM auth tag (128-bit) — এক বাইট বদলালেই ডিক্রিপশন ব্যর্থ |
| AAD | `projectId` — এক প্রজেক্টের সিল করা ফাইল অন্য প্রজেক্টে বসানো যায় না |
| ফরম্যাট | `[version:1][iv:12][ciphertext+tag]` |
| IV | প্রতি সিলে নতুন, `SecureRandom` |
| কী মুছে ফেলা | `forget(projectId)` — প্রজেক্ট স্থায়ীভাবে ডিলিটের সময় |

**API:**

```java
boolean ok = ProjectVault.isAvailable();          // API 23+
byte[] sealed = ProjectVault.seal(projectId, plain);
byte[] plain  = ProjectVault.open(projectId, sealed);
ProjectVault.sealFile(projectId, source, target);
byte[] data   = ProjectVault.openFile(projectId, file);
String hash   = ProjectVault.fingerprint(bytes);  // SHA-256
ProjectVault.forget(projectId);
```

**নিরাপত্তা সিদ্ধান্ত:** API 23-এর নিচে `isAvailable()` false ফেরায় এবং `seal()` ব্যতিক্রম
ছোড়ে। ইচ্ছাকৃতভাবে — নীরবে এনক্রিপশন ছাড়া ফাইল লেখা তার চেয়ে খারাপ, কারণ ব্যবহারকারী
ভুল নিরাপত্তার বিশ্বাসে থাকবেন।

---

## ৩. ইন্টিগ্রেশন পয়েন্ট (পরবর্তী ধাপ)

`ProjectVault` স্বয়ংসম্পূর্ণ, কিন্তু **রিসোর্স সার্ভিং পথে এখনো যুক্ত করা হয়নি**।
সংযোগ করতে হলে দুটি জায়গায় হাত দিতে হবে:

1. **লেখার সময়** — `ProjectImporter` / `ProjectResourceManager` যখন প্রজেক্ট ফাইল
   `app/projects/<id>/`-এ লেখে, তখন `ProjectVault.sealFile(...)` দিয়ে লিখবে।
2. **পড়ার সময়** — `WebViewHost.shouldInterceptRequest(...)` যে `InputStream` ফেরায়,
   সেটি `ProjectVault.openFile(...)` থেকে আসবে।

দ্বিতীয়টি করার আগে একটি সিদ্ধান্ত দরকার: **প্রতিটি রিকোয়েস্টে ডিক্রিপ্ট করলে বড় ফাইলে
(ছবি, ফন্ট) পারফরম্যান্স পড়তে পারে।** সাধারণ সমাধান — ডিক্রিপ্ট করা বাইট মেমরিতে
সীমিত আকারের LRU ক্যাশে রাখা, ডিস্কে নয়। এটি ডিজাইন সিদ্ধান্ত, তাই আপনার অনুমোদন ছাড়া
করিনি।

---

## ৪. ES6 Module সম্পর্কে

ES6 `import`/`export`, relative import, `import()`, `fetch()` — এগুলো WebView নিজেই সামলায়,
শর্ত একটাই: **প্রতিটি ফাইল সঠিক `Content-Type` নিয়ে সার্ভ হতে হবে** (`text/javascript`,
`text/css`, `image/svg+xml`) এবং সব রিসোর্স **একই Virtual Origin**-এ থাকতে হবে।

`WebSourceResolver` ইতিমধ্যে সব পাথ একই `https://<host>/`-এ ম্যাপ করে, তাই Same-Origin
রক্ষা পায়। MIME ম্যাপিং `WebViewHost`-এর ইন্টারসেপ্টরে আছে — এটি কোডে যাচাই করা হয়েছে,
কিন্তু **ডিভাইসে চালিয়ে যাচাই করা হয়নি**।

---

## ৫. যা করা হয়নি — স্পষ্টভাবে

| কাজ | অবস্থা |
|---|---|
| `ProjectVault` লেখা | ✅ সম্পন্ন |
| রিসোর্স পথে যুক্ত করা | ❌ বাকি (উপরের §৩) |
| Gradle Build | ❌ স্যান্ডবক্সে Android SDK নেই |
| Unit / Integration Test চালানো | ❌ বাকি |
| ডিভাইসে ES6 মডিউল যাচাই | ❌ বাকি |

আপনি "Build করবে এবং Test করবে" চেয়েছিলেন। এই পরিবেশে `javac`, Gradle ও Android SDK
কোনোটিই নেই, তাই কম্পাইল বা রান করা সম্ভব নয়। যে দাবি যাচাই করিনি, সেটি "হয়েছে" বলে
দেখানো ঠিক হবে না — তাই এখানে আলাদা করে লিখে রাখলাম।
