# Build Instructions — RONi LiFE OS Consumer App

## ১. প্রয়োজনীয় পরিবেশ

| উপাদান | সংস্করণ |
| :--- | :--- |
| Android Studio | Meerkat Feature Drop 2024.3.2 Patch 1 (বা নতুন) |
| Android Gradle Plugin | 8.10.1 |
| Gradle | 8.11.1 |
| JDK | 17 |
| compileSdk | 36 |
| minSdk | 21 |
| targetSdk | 36 |
| Build Tools | 36.0.0 |

> Kotlin, Jetpack Compose, Retrofit, OkHttp, Coroutines, Hilt/Dagger — **কোনোটিই ব্যবহৃত হয় না**।

---

## ২. প্রজেক্ট খোলা

1. Android Studio → **Open** → রুট ফোল্ডার `RONiLiFEOS` নির্বাচন করুন ( `:app` নয়)।
2. Gradle sync শেষ হওয়া পর্যন্ত অপেক্ষা করুন। ১৫টি লাইব্রেরি মডিউল লোকাল প্রজেক্ট হিসেবে যুক্ত।
3. Run configuration-এ `app` নির্বাচন করুন।

`settings.gradle`-এ ১৬টি মডিউল আছে:

```
:app
:native-runtime-sdk          :native-runtime-ui
:native-runtime-engine       :native-runtime-contracts
:native-web-bridge-sdk       :native-storage-engine
:native-network-engine       :native-security-engine
:native-analytics-engine     :native-theme-engine
:native-platform-engine      :native-router-sdk
:native-logger-sdk           :native-service-sdk
:native-dev-tools-sdk
```

---

## ৩. কমান্ড লাইন বিল্ড

```bash
# ডিবাগ APK
./gradlew :app:assembleDebug

# রিলিজ APK
./gradlew :app:assembleRelease

# ইনস্টল
./gradlew :app:installDebug
```

আউটপুট: `app/build/outputs/apk/debug/app-debug.apk`

---

## ৪. ইউনিট টেস্ট

```bash
./gradlew :app:testDebugUnitTest
```

রিপোর্ট: `app/build/reports/tests/testDebugUnitTest/index.html`

পাঁচটি টেস্ট ক্লাস (সম্পূর্ণ pure JUnit, কোনো Robolectric নয়):

| টেস্ট | যাচাই করে |
| :--- | :--- |
| `StartupDecisionTest` | Main ও Project ম্যাট্রিক্সের ১০টি কেস |
| `ProjectValidatorTest` | নাম, ডুপ্লিকেট, URL, পাথ, ইনলাইন HTML |
| `ProjectSerializationTest` | encode/decode রাউন্ডট্রিপ, ক্ল্যাম্পিং, কপি |
| `ProjectStoreTest` | CRUD, ডিফল্ট, lastOpened, স্টার্টআপ রেজোলিউশন |
| `AppConfigTest` | ডিফল্ট, ক্ল্যাম্পিং, রাউন্ডট্রিপ |

---

## ৫. স্ট্যাটিক ভ্যালিডেশন (Android SDK ছাড়াই)

```bash
python3 tools/validate_project.py
```

স্ক্রিপ্টটি যাচাই করে:

1. প্রতিটি Java ফাইলের ব্রেস/বন্ধনী ভারসাম্য
2. `package` ঘোষণা ↔ ডিরেক্টরি পথ
3. ক্লাস নাম ↔ ফাইল নাম
4. Java-তে ব্যবহৃত প্রতিটি `R.<type>.<name>` রিসোর্স বিদ্যমান কি না
5. XML-এ ব্যবহৃত প্রতিটি `@type/name` রেফারেন্স বিদ্যমান কি না
6. `AndroidManifest.xml`-এর প্রতিটি Activity/Application ক্লাস বিদ্যমান কি না
7. ঠিক একটি launcher activity আছে কি না
8. `settings.gradle` ও `app/build.gradle`-এর প্রতিটি মডিউল ডিরেক্টরি বিদ্যমান কি না

প্রত্যাশিত আউটপুট:

```
Java files      : 25
Resource ids    : 52
Layouts         : 7
Strings         : 27
Errors          : 0
Warnings        : 0
```

---

## ৬. প্রথম রান

অ্যাপ চালু হলে `AppInitializer` স্বয়ংক্রিয়ভাবে ডেমো প্রজেক্ট তৈরি করে
(`ZIP_ASSET` → `webapp.zip` → `index.html`)। `BootstrapActivity`-তে সেটি দেখা যাবে।

- প্রজেক্টে ট্যাপ → রানটাইম খুলবে
- `⋮` → খুলুন / সম্পাদনা / কপি / ডিফল্ট / মুছুন
- টুলবারের ⚙ → Main App-এর Splash ও Bootstrap টগল
- টুলবারের ⋮ → রানটাইম ডায়াগনস্টিকস

---

## ৭. সমস্যা সমাধান

| সমস্যা | কারণ ও সমাধান |
| :--- | :--- |
| `Failed to resolve plugin for module` | `app.json` নয় — `AndroidManifest`-এ ভুল ক্লাস। `tools/validate_project.py` চালান। |
| `Unresolved reference: R` | `namespace` ভুল। `app/build.gradle`-এ `com.roni.life.os.app` হতে হবে। |
| `Duplicate class` | একই লাইব্রেরি দুইবার। শুধু `implementation project(':native-runtime-sdk')` রাখুন। |
| সাদা স্ক্রিন | `webapp.zip`-এ `index.html` আছে কি না দেখুন (`unzip -l app/src/main/assets/webapp.zip`)। |
| রানটাইম "সীমিত মোড" | `NativeRuntime.init()` ব্যর্থ। ডায়াগনস্টিকস ডায়ালগে কোন ইঞ্জিন ব্যর্থ দেখুন। |
| Gradle sync ব্যর্থ | JDK 17 নির্বাচন করুন (Settings → Build → Gradle → Gradle JDK)। |

---

## ৮. রিলিজ বিল্ড

`app/build.gradle`-এ `signingConfig signingConfigs.debug` আছে যাতে CI-তে রিলিজ বিল্ড হয়।
প্রোডাকশনের জন্য নিজের keystore যোগ করুন:

```gradle
signingConfigs {
    release {
        storeFile file("release.keystore")
        storePassword System.getenv("KEYSTORE_PASSWORD")
        keyAlias System.getenv("KEY_ALIAS")
        keyPassword System.getenv("KEY_PASSWORD")
    }
}
```
