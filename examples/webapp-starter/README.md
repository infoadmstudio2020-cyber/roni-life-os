# RONiLiFEOS Web Starter

ES6 module ভিত্তিক ন্যূনতম starter — শুধু যাচাইকৃত Native API ব্যবহার করে।

## কাঠামো

```
webapp-starter/
├── index.html
├── manifest.json
├── styles/
│   ├── main.css
│   └── responsive.css
└── scripts/
    ├── app.js
    ├── native-runtime.js
    └── modules/
        ├── device.js
        ├── storage.js
        └── ui.js
```

## চালানো

1. ফোল্ডারটি ZIP করুন (root-এ `index.html` থাকবে)
2. RONiLiFEOS-এ import করুন
3. চালু করুন

ব্রাউজারে সরাসরি খুললেও চলবে — `Native.available()` false হলে localStorage fallback।

## ব্যবহৃত API

| Action | কোথায় |
|---|---|
| `ui.showToast` | `native-runtime.js` → `toast()` |
| `ui.showConfirm` | `native-runtime.js` → `confirm()` |
| `ui.showLoading` / `ui.hideLoading` | `native-runtime.js` → `loading()` |
| `storage.get` / `set` / `remove` | `modules/storage.js` |
| `device.getInfo` | `modules/device.js` |

## নিজের অ্যাপ বানাতে

1. `scripts/modules/`-এ নতুন মডিউল যোগ করুন
2. `native-runtime.js`-এর `call()` দিয়ে action ডাকুন
3. `available()` চেক করে fallback রাখুন
4. `manifest.json`-এ `entryPoint` ঠিক রাখুন

## যা করবেন না

- `import x from 'lodash'` — bare specifier কাজ করবে না
- TypeScript / JSX — compile step নেই
- absolute path (`/Users/...`) — ব্লক হবে
