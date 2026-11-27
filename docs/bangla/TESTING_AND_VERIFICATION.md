# Test Report — Native API ও WebBridge যাচাই

তারিখ: ২০২৬-১১-১৭ · পরিবেশ: Linux, Node 24.21.0, Python 3.12.14, OpenJDK **JRE** 17.0.20.1

## ১. সংক্ষিপ্ত ফলাফল

| স্যুট | টুল | ফল |
|---|---|---|
| Action extraction | `extract_actions.py` | ১৫২ action, ০ dead route, ০ hidden action |
| Storage isolation | `storage_scope_test.py` | **৬৮ / ৬৮** |
| JS wrapper | `wrapper_test.mjs` (jsdom) | **২৬ / ২৬** |
| Bridge bootstrap | `smoke.mjs` | **১৮ / ১৮** |
| ২৬টি উদাহরণ (runtime) | `examples_runtime_test.mjs` (jsdom) | **১৬ ফাইল, ০ সমস্যা**, DOM assertion **১১ / ১১** |
| Java structural | `java_sanity.py` | ৬ ফাইল, ০ সমস্যা |
| Java compile | — | ⛔ **চালানো যায়নি** |
| Android build / instrumented test | — | ⛔ **চালানো যায়নি** |

## ২. যা সত্যিই চালানো হয়েছে

### ২.১ Action extraction
প্রতিটি handler-এর `switch (action)` ব্লক brace-matching দিয়ে কাটা হয়েছে, তারপর
`SUPPORTED_ACTIONS`-এর সাথে মিলিয়ে দেখা হয়েছে। ফল: `ui` ৬২, `web` ৫৫, `device` ২৮,
`storage` ৭ — মোট **১৫২**।

### ২.২ Storage isolation
Java অ্যালগরিদম Python-এ হুবহু port করে ৬৮টি assertion। বিস্তারিত `STORAGE_ISOLATION.md` §৬।

### ২.৩ JS wrapper (jsdom)
`examples/bangla-notes-app/native.js` আসল DOM-এ লোড করে:

| বিভাগ | assertion |
|---|---|
| availability | bridge ছাড়া `false`, rejection `NATIVE_UNAVAILABLE`, `toast` গিলে ফেলে |
| promise | resolve, payload forwarding, default `{}` |
| error | rejection message অপরিবর্তিত |
| timeout | নীরব bridge → `CLIENT_TIMEOUT` (constant ১২s → ৪০ms করে) |
| events | on → emit → off → আর ডাকা হয় না |
| confirm | `true`/`false`/bridge-fail তিন পথ, listener leak নেই |
| loading | show → hide → দ্বিতীয় hide no-op |
| fallback | bridge ছাড়া `on`/`off`/`loading` নিরাপদ |

**এই টেস্টে দুটি আসল বাগ ধরা পড়েছে ও ঠিক করা হয়েছে:**
1. `Native.confirm`-এর `.catch`-এ `off()` ছিল না → listener leak।
2. `loading.hide()` দ্বিতীয়বারও `ui.hideLoading` পাঠাত → `shown = false` যোগ।

### ২.৪ ২৬টি উদাহরণ — runtime integration
শুধু `node --check` নয়। প্রতিটি HTML jsdom-এ লোড করা হয়েছে, `<script src>` inline করা হয়েছে,
এবং একটি **mock `NativeRuntimeBridgeClient`** বসানো হয়েছে যা Java থেকে নেওয়া **আসল রাউটিং নিয়ম**
প্রয়োগ করে:

- prefix route না থাকলে → `ROUTE_NOT_FOUND`
- prefix আছে কিন্তু `switch`-এ case নেই → `UNKNOWN_ACTION`
- নাহলে canned response

তারপর প্রতিটি `<button>` ক্লিক করা হয়েছে, `<input type=file>`-এ synthetic `File` দেওয়া হয়েছে,
notes app-এ hash route বদলানো হয়েছে।

| ফাইল | button | call | distinct action | unknown | notfound | error |
|---|---|---|---|---|---|---|
| 01-toast | 9 | 9 | 3 | 0 | 0 | 0 |
| 02-dialog | 4 | 5 | 5 | 0 | 0 | 0 |
| 03-sheet | 5 | 5 | 5 | 0 | 0 | 0 |
| 04-loading | 5 | 5 | 3 | 0 | 0 | 0 |
| 05-toolbar | 10 | 10 | 8 | 0 | 0 | 0 |
| 06-drawer-tabs | 16 | 16 | 15 | 0 | 0 | 0 |
| 07-system-ui | 12 | 11 | 8 | 0 | 0 | 0 |
| 08-device-info | 6 | 7 | 6 | 0 | 0 | 0 |
| 09-battery-network | 2 | 4 | 2 | 0 | 0 | 0 |
| 10-clipboard-share | 5 | 6 | 6 | 0 | 0 | 0 |
| 11-storage | 9 | 12 | 8 | 0 | 0 | 0 |
| 12-download | 5 | 4 | 1 | 0 | 0 | 0 |
| 13-navigation | 11 | 20 | 9 | 0 | 0 | 0 |
| 14-upload | 0 | 0 | 0 | 0 | 0 | 0 |
| index (hub) | 2 | 1 | 1 | 0 | 0 | 0 |
| notes-app | 7 | 48 | 11 | 0 | 0 | 0 |

মোট **৭৫টি distinct action** ব্যবহৃত, সবই dispatch table-এ আছে।
DOM assertion ১১/১১ — যেমন `14-upload`-এ ফাইলের নাম রেন্ডার হয়েছে, `12-download`-এ ৩টি
anchor click হয়েছে, notes app-এ `#app` রেন্ডার হয়েছে।

`14-upload`-এ bridge call ০ — কারণ ওই ডেমো শুধু DOM-এ লেখে, bridge ডাকে না। এটি প্রত্যাশিত।

## ৩. যা চালানো যায়নি — এবং কেন

| লক্ষ্য | কারণ | কী করা হয়েছে তার বদলে |
|---|---|---|
| `javac` দিয়ে কম্পাইল | ইমেজে শুধু `openjdk-17-jre`; `apt-get install openjdk-17-jdk` ব্যর্থ (mirror DNS fail) | `java_sanity.py` — brace/paren ভারসাম্য, package/type মিল, duplicate method |
| Gradle build | JDK নেই, Android SDK নেই (`ANDROID_HOME` unset) | — |
| Unit test (`app/src/test`, `native-*/src/test`) | একই কারণ | — |
| Instrumented test (`androidTest`) | ডিভাইস/এমুলেটর নেই | — |
| আসল WebView-এ bridge | ডিভাইস নেই | jsdom + Java-থেকে-নেওয়া রাউটিং নিয়ম |

**স্পষ্ট কথা:** Java পরিবর্তনগুলো (StorageScope, handler, UIHost, RuntimeUI) **কম্পাইল-যাচাইকৃত নয়**।
কোড পর্যালোচনা ও structural check পাস করেছে, কিন্তু `javac` ছাড়া নিশ্চিত বলা যায় না।
প্রথম কাজ: `./gradlew :native-storage-engine:test :native-runtime-ui:test`.

## ৪. পুনরুৎপাদন

```bash
cd tasks/verify
python3 extract_actions.py          # action তালিকা + registration যাচাই
python3 storage_scope_test.py       # ৬৮ assertion
python3 java_sanity.py              # Java structural check
node wrapper_test.mjs               # ২৬ assertion
node examples_runtime_test.mjs      # ২৬ উদাহরণ, runtime
node smoke.mjs                      # bootstrap ১৮ assertion
python3 gen_matrix.py               # matrix markdown পুনরায় তৈরি
```

`jsdom` দরকার: `npm i jsdom` (এখানে ইনস্টল করা আছে)।
