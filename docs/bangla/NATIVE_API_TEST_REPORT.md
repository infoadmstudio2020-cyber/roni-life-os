# Native API Test Report — Phase 12

তারিখ: ২০২৬-১১-১৭

## ১. চালানো হয়েছে

| স্যুট | ফল | স্তর |
|---|---|---|
| `phase12_test.mjs` | **৩৪ / ৩৪** | JS RUNTIME (jsdom + Node ESM) |
| `starter_test.mjs` | **১৯ / ১৯** | JS RUNTIME (jsdom) |
| `wrapper_test.mjs` | **২৬ / ২৬** | JS RUNTIME (jsdom) |
| `examples_runtime_test.mjs` | **১৬ ফাইল, ০ সমস্যা** | JS RUNTIME (jsdom) |
| `smoke.mjs` | **১৮ / ১৮** | JS RUNTIME |
| `storage_scope_test.py` | **৬৮ / ৬৮** | STATIC (algorithm port) |
| `java_sanity.py` | **০ সমস্যা** | STATIC |

মোট: **১৬৫টি assertion**, সব পাস।

## ২. `phase12_test.mjs` কী যাচাই করে

| গ্রুপ | Assertion |
|---|---|
| Java action inventory | ৯টি handler ফাইল স্ক্যান, প্রতিটিতে action আছে |
| Wrapper → Java contract | wrapper-এর প্রতিটি `call('x.y')` literal Java-তে আছে |
| Namespace export | ৯টি namespace |
| Runtime dispatch | theme/analytics/platform/logger/storage/network/security সঠিক action পাঠায় |
| Availability probe | `isSupported('theme')` true, `isSupported('devtools')` false |
| Error handling | অজানা action reject করে, `code` বহন করে |
| Event lifecycle | `onChange` unsubscribe কাজ করে |

## ৩. যা চালানো যায়নি

| লক্ষ্য | কারণ |
|---|---|
| `./gradlew :native-storage-engine:test` | JDK নেই (শুধু JRE 17), Android SDK নেই |
| `./gradlew test` | একই |
| `./gradlew assemble` | একই |
| `./gradlew build` | একই |
| Instrumented test | ডিভাইস/এমুলেটর নেই |
| আসল WebView bridge | ডিভাইস নেই |
| BiometricPrompt | ডিভাইস নেই |

**স্পষ্ট কথা:** Java পরিবর্তনগুলো **কম্পাইল-যাচাইকৃত নয়**। `NativeApiIntegration`,
`SecurityCommandHandler`-এর পরিবর্তন, `NetworkCommandHandler` — এগুলো structural check
পাস করেছে, কিন্তু বাইটকোড তৈরি হয়নি।

## ৪. Android Studio-তে যাচাইয়ের নির্দেশনা

```bash
# ১. JDK 17 নিশ্চিত করুন
java -version          # 17.x হওয়া চাই

# ২. SDK path
export ANDROID_HOME=$HOME/Android/Sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

# ৩. নির্দিষ্ট মডিউল
./gradlew :native-storage-engine:test :native-runtime-ui:test --stacktrace

# ৪. সব unit test
./gradlew test

# ৫. assemble
./gradlew assembleDebug

# ৬. পূর্ণ build
./gradlew build
```

### প্রত্যাশিত ফল

| ধাপ | প্রত্যাশা |
|---|---|
| `:native-runtime-sdk:compileDebugJavaWithJavac` | `NativeApiIntegration` কম্পাইল হয় |
| `:native-security-engine:compileDebugJavaWithJavac` | `BiometricResultListener` কম্পাইল হয় |
| `:native-network-engine:compileDebugJavaWithJavac` | নতুন handler কম্পাইল হয় |
| `test` | বিদ্যমান unit test পাস |

### ডিভাইসে smoke test

1. `assembleDebug` → ইনস্টল
2. `examples/webapp-starter` ZIP করে import
3. চালু করে দেখুন:
   - badge "Native সংযুক্ত"
   - ডিভাইস তথ্য দেখায়
   - Light/Dark বাটন theme বদলায়
   - Analytics/Logger বাটন লগে কিছু দেখায়
   - Biometric বাটন প্রম্পট দেখায় (ডিভাইসে biometric থাকলে)
   - DevTools বাটন release-এ "শুধু debug build-এ" বলে

## ৫. সীমাবদ্ধতা

| # | সীমাবদ্ধতা |
|---|---|
| ১ | Java কম্পাইল যাচাই হয়নি |
| ২ | Gradle build চালানো হয়নি |
| ৩ | আসল WebView-এ route নিবন্ধন দেখা যায়নি |
| ৪ | Biometric flow ডিভাইসে যাচাই হয়নি |
| ৫ | Network request আসল সার্ভারে যায়নি |
