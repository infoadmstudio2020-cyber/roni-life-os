# Theme API Guide — `theme.*`

## ১. এক authoritative state

আগে দুটি জায়গায় theme ছিল: `NativeRuntimeUIConfig` (themeMode, primaryColor) এবং
`ThemeManager`। এখন **`ThemeManager`-ই একমাত্র সত্য**।

```
NativeRuntimeUIConfig  ──seed──▶  ThemeManager  ──▶  theme.* API
   (init-এ একবার)                  (runtime state)      (JS)
```

`NativeApiIntegration.seedTheme()` init-এ config থেকে একবার মান বসায়। এরপর সব পরিবর্তন
`ThemeManager`-এ হয়। `NativeRuntimeUIConfig` আর runtime state নয়।

## ২. Action

| Action | Payload | Response |
|---|---|---|
| `theme.getTheme` | — | `{themeName, darkMode, primaryColor, surfaceColor, textColor, themeMode, secondaryColor, backgroundColor, statusBarColor, navigationBarColor}` |
| `theme.setThemeMode` | `{mode: "LIGHT"\|"DARK"\|"SYSTEM"}` | `{mode}` |
| `theme.applyTheme` | `{themeName, darkMode, primaryColor, secondaryColor, backgroundColor, surfaceColor, textColor, statusBarColor, navigationBarColor}` | `{applied:true}` |
| `theme.isSystemDark` | — | `{isSystemDark}` |
| `theme.setSystemDark` | `{dark:boolean}` | `{systemDark}` |
| `theme.getCssVariables` | — | `{css:":root{--color-primary:#...}"}` |

## ৩. ব্যবহার

```js
import { theme } from './native-runtime.js';

const current = await theme.get();
console.log(current.themeName, current.darkMode);

await theme.setMode('DARK');

const { css } = await theme.cssVariables();
document.getElementById('theme').textContent = css;
```

## ৪. ইভেন্ট

`theme.changed` — প্রতিবার theme বদলালে।

```js
const off = theme.onChange((event) => {
  document.body.dataset.theme = event.darkMode ? 'dark' : 'light';
});
// পরে: off();
```

Payload: `{themeName, darkMode, primaryColor, surfaceColor, textColor, themeMode, secondaryColor, backgroundColor, statusBarColor, navigationBarColor}`

## ৫. CSS variable নাম

`WebThemeBridge.generateCssVariables()`:

| Variable | উৎস |
|---|---|
| `--theme-name` | themeName |
| `--theme-dark-mode` | darkMode |
| `--color-primary` | primaryColor |
| `--color-surface` | surfaceColor |
| `--color-text` | textColor |
| `--color-secondary` | secondaryColor |
| `--color-background` | backgroundColor |
| `--color-text-secondary` | textSecondary |
| `--color-accent` | accent |
| `--color-status-bar` | statusBarColor |
| `--color-nav-bar` | navigationBarColor |

## ৬. সীমাবদ্ধতা

| বিষয় | অবস্থা |
|---|---|
| Project-specific theme | ❌ একটিই `ThemeManager` instance |
| Custom token | ✅ `ThemePalette.getCustomTokens()` |
| System theme অনুসরণ | ✅ `SYSTEM` mode |
| Native status bar প্রয়োগ | ⚠️ শুধু CSS variable; Activity-তে আলাদা করে apply করতে হবে |
