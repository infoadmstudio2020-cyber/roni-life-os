# Programming Languages & Technology

## ১. সারসংক্ষেপ

| পরিবেশ | ভাষা | ভার্সন | বাধ্যতামূলক? |
|---|---|---|---|
| Android Native | **Java** | ১৭ | ✅ |
| Android build | Groovy DSL (Gradle) | Gradle 8.11.1, AGP 8.10.1 | ✅ |
| Android UI | XML | — | ✅ |
| Web App | **HTML5** | — | ✅ |
| Web App | **CSS3** | — | ✅ |
| Web App | **JavaScript ES6+** | — | ✅ |
| Web App | JSON | — | ঐচ্ছিক (`manifest.json`) |

## ২. Android Native Environment

| বিষয় | মান | সোর্স |
|---|---|---|
| Java | **17** (`VERSION_17`) | `app/build.gradle:45` |
| compileSdk | ৩৬ | `app/build.gradle:7` |
| minSdk | ২১ (Android 5.0) | `app/build.gradle:11` |
| targetSdk | ৩৬ | `app/build.gradle:12` |
| AGP | ৮.১০.১ | `build.gradle:4` |
| Gradle | ৮.১১.১ | `gradle-wrapper.properties` |
| applicationId | `com.roni.life.os.app` | `app/build.gradle:10` |
| versionName | ১.৮.০ | `app/build.gradle:14` |

### ২.১ Java 17-এর যে ফিচার ব্যবহার করা যায়

| ফিচার | অবস্থা |
|---|---|
| `var` (local type inference) | ✅ |
| switch expression (`->`) | ✅ |
| text block (`"""`) | ✅ |
| record | ✅ |
| sealed interface | ✅ |
| pattern matching `instanceof` | ✅ |
| `List.of()`, `Map.of()` | ✅ |

### ২.২ যা ব্যবহার করা হয় না

| প্রযুক্তি | কারণ |
|---|---|
| Kotlin | পুরো কোডবেস Java |
| Jetpack Compose | View + XML ব্যবহৃত |
| Hilt / Dagger | manual wiring |
| RxJava | plain callback/executor |
| Retrofit | নিজস্ব `native-network-engine` |

## ৩. Web Application Environment

### ৩.১ বাধ্যতামূলক

| প্রযুক্তি | কেন |
|---|---|
| HTML5 | entry document |
| CSS3 | layout, responsive |
| JavaScript ES6+ | bridge কল, DOM |

### ৩.২ সমর্থিত (যাচাইকৃত)

| ফিচার | অবস্থা | মন্তব্য |
|---|---|---|
| ES Modules (`import`/`export`) | ✅ | MIME `application/javascript` |
| `import()` dynamic | ✅ | |
| `fetch()` | ✅ | same-origin asset; CORS প্রযোজ্য |
| `Promise`, `async/await` | ✅ | |
| `localStorage` | ✅ | project-scoped virtual host |
| `IndexedDB` | ✅ | project-scoped |
| `URL`, `Blob`, `FileReader` | ✅ | |
| `Web Worker` | ⚠️ | WebView-এ সমর্থিত, কিন্তু এই প্রজেক্টে যাচাই করা হয়নি |
| `Service Worker` | ❌ | WebView-এ অনির্ভরযোগ্য |
| CSS Grid / Flexbox | ✅ | |
| `@media` query | ✅ | |
| CSS custom properties | ✅ | |
| `Intl` API | ✅ | WebView version-নির্ভর |

### ৩.৩ নিষিদ্ধ / অসমর্থিত

| প্রযুক্তি | কারণ |
|---|---|
| React / Vue / Angular | রানটাইম কোনো bundler দেয় না |
| TypeScript | compile ধাপ নেই |
| JSX | একই কারণ |
| npm package (bare import) | import map নেই |
| Webpack / Vite | build step নেই |
| Node.js API (`fs`, `path`) | browser environment |
| `eval`-ভিত্তিক template | CSP সীমিত |

> **গুরুত্বপূর্ণ:** framework ব্যবহার করতে চাইলে **আগে build করে** সাধারণ
> HTML/CSS/JS আউটপুট বানাতে হবে, তারপর ZIP-এ দিতে হবে। রানটাইম TSX বা `.vue`
> পড়তে পারে না।

## ৪. WebView compatibility

| বিষয় | মান |
|---|---|
| সর্বনিম্ন WebView | Android 5.0-এর system WebView (Chromium 37+) |
| বাস্তবসম্মত ন্যূনতম | Chromium 80+ (ES module, optional chaining) |
| `addWebMessageListener` | Chromium 88+ (AndroidX `WebViewCompat` fallback সহ) |
| `JavascriptInterface` | সব ভার্সনে |

**নিরাপদ নিয়ম:** ES2019+ ব্যবহার করুন, `?.` ও `??` ঠিক আছে, কিন্তু
`Array.prototype.at()`, `structuredClone()`, top-level await — পুরোনো WebView-এ
ব্যর্থ হতে পারে। `Native.available()` দিয়ে feature detect করুন।

## ৫. ভাষা-নির্বাচন সিদ্ধান্ত

| প্রশ্ন | উত্তর |
|---|---|
| TypeScript ব্যবহার করব? | না — compile step নেই |
| React ব্যবহার করব? | না — build করে static output দিলে চলবে |
| CSS framework (Tailwind)? | না — build দরকার; হাতে CSS লিখুন |
| jQuery? | না — দরকার নেই |
| ES6 class ব্যবহার করব? | হ্যাঁ, সমর্থিত |
| `var` না `let`/`const`? | `const` > `let` > `var` (var এড়িয়ে চলুন) |

## ৬. ফাইল এক্সটেনশন

| এক্সটেনশন | MIME | রেন্ডার |
|---|---|---|
| `.html` `.htm` | `text/html` | ✅ |
| `.css` | `text/css` | ✅ |
| `.js` `.mjs` | `application/javascript` | ✅ |
| `.json` | `application/json` | ✅ (fetch) |
| `.svg` `.png` `.jpg` `.webp` | `image/*` | ✅ |
| `.woff2` `.ttf` `.otf` | `font/*` | ✅ |
| `.wasm` | `application/wasm` | ⚠️ যাচাই হয়নি |
| `.ts` `.tsx` `.vue` `.jsx` | `application/octet-stream` | ❌ |
