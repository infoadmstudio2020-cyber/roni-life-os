# Bug Fix — `StaticLayout` NullPointerException in `SwitchCompat.onMeasure`

**Date:** 2026-11-13
**Severity:** Fatal (app crash on screen open)
**Status:** Fixed in source. **Not compiled, not run** — see §6.

---

## 1. Reported crash

```text
FATAL EXCEPTION: main
java.lang.NullPointerException:
Attempt to invoke interface method
'int java.lang.CharSequence.length()'
on a null object reference

at android.text.StaticLayout.
at androidx.appcompat.widget.SwitchCompat.makeLayout
at androidx.appcompat.widget.SwitchCompat.onMeasure
```

## 2. Root cause

`SwitchCompat` keeps two internal labels, `mTextOn` and `mTextOff`, populated from
`android:textOn` / `android:textOff` during construction. When `showText` is true, the first
measure pass builds a `StaticLayout` for each label:

```java
// SwitchCompat.onMeasure()
if (mShowText) {
    if (mOnLayout == null)  mOnLayout  = makeLayout(mTextOn);
    if (mOffLayout == null) mOffLayout = makeLayout(mTextOff);
}

// SwitchCompat.makeLayout()
private Layout makeLayout(CharSequence text) {
    final CharSequence transformed = (mTransformation == null) ? text : mTransformation.apply(text);
    return new StaticLayout(transformed, mTextPaint, ...);   // -> transformed.length()
}
```

`StaticLayout`'s constructor calls `source.length()` immediately. If either label is `null`,
that call throws. There is **no null check anywhere in the chain** — the widget assumes the
style always supplies both labels.

The labels become `null` in three situations, all of which exist in this project:

| Situation | Where |
| :--- | :--- |
| Widget constructed in code with no `AttributeSet` and a theme that does not resolve `switchStyle` | `SwitchComponent.createSpecificView()` — `new SwitchCompat(context)` |
| `setTextOn(null)` / `setTextOff(null)` called after construction | any caller; the API accepts `null` without complaint |
| A style or theme sets `showText=true` without also setting both labels | any `MaterialSwitch` inflated without explicit `textOn`/`textOff` |

`MaterialSwitch` extends `SwitchCompat`, so it inherits the identical failure path — which is
why the stack shows `SwitchCompat` even though every switch in the layouts is a `MaterialSwitch`.

## 3. Fix

The guard is applied **inside the widget constructor**, i.e. before the first measure pass. This
is the only point that is guaranteed to run early enough — a fix applied after `inflate()` but
before `show()` works for dialogs, but not for a view added to an already-attached parent.

### New files

| File | Module | Purpose |
| :--- | :--- | :--- |
| `com.roni.library.ui.widget.SwitchSafety` | `native-runtime-ui` | `harden(SwitchCompat)` — replaces null labels with `""`, null main text with `""`, and disables `showText` when a label was missing. `needsShowTextDisabled(CharSequence, CharSequence)` is the pure, JVM-testable decision. |
| `com.roni.library.ui.widget.SafeSwitchCompat` | `native-runtime-ui` | `SwitchCompat` subclass; hardens in all three constructors and re-hardens if `setTextOn(null)` / `setTextOff(null)` is called later. |
| `com.roni.life.os.app.widget.SafeMaterialSwitch` | `:app` | `MaterialSwitch` subclass with the same guarantees. |

### Behaviour preserved

`harden()` only acts when a label is actually missing. A switch that deliberately sets
`android:textOn="ON"` / `android:textOff="OFF"` keeps its labels and its `showText` value, so no
existing visual design changes. The guard is a no-op on a correctly configured switch.

### Replacements

| Location | Before | After |
| :--- | :--- | :--- |
| `activity_project_ui_editor.xml` | 23 × `MaterialSwitch` | 23 × `SafeMaterialSwitch` |
| `dialog_app_settings.xml` | 4 × `MaterialSwitch` | 4 × `SafeMaterialSwitch` |
| `dialog_project_edit.xml` | 3 × `MaterialSwitch` | 3 × `SafeMaterialSwitch` |
| `item_component_config.xml` | 1 × `MaterialSwitch` | 1 × `SafeMaterialSwitch` |
| `SwitchComponent.java` | `new SwitchCompat(context)` | `new SafeSwitchCompat(context)` |

No Java call site needed changing: `SafeMaterialSwitch` **is a** `MaterialSwitch`, so every
existing `findViewById(...)` assignment and every `configureSwitch(MaterialSwitch, String)`
signature still compiles unchanged.

## 4. Related defects fixed in the same pass

### 4.1 Duplicate string resource — build-breaking

`splash_subtitle` was defined twice in the same `values/` source set:

- `values/strings.xml` → `NativeRuntime Consumer Runtime`
- `values/strings_ui_config.xml` → `Native UI Runtime`

AAPT2 rejects duplicate resources in one source set, so this alone would have failed the build.
The `strings_ui_config.xml` copy was removed; the original value is preserved.

### 4.2 Hardcoded ON/OFF labels

`dialog_app_settings.xml` used `android:textOn="ON"` / `android:textOff="OFF"` as literals —
a lint error and untranslatable. Moved to `@string/switch_on` / `@string/switch_off`.

### 4.3 Redundant defensive code

`ProjectEditDialog` already contained `configureSwitch()` and `debugSwitch()` doing the same
null-guarding by hand. They are now redundant but harmless, and were **left in place** — removing
them would be a behaviour change the brief did not ask for. `debugSwitch()` still logs at ERROR
level on every dialog open; consider deleting it once the fix is confirmed on device.

## 5. Audit results

| Check | Result |
| :--- | :--- |
| `SwitchCompat` / `MaterialSwitch` in XML | 31 found, all replaced |
| Programmatic switch construction | 1 found (`SwitchComponent`), replaced |
| `setTextOn(null)` / `setTextOff(null)` | none |
| `setShowText(true)` | none |
| `="@null"` in any layout | none |
| Duplicate resource names (`app`) | 1 (`splash_subtitle`) — fixed |
| Duplicate resource names (`native-runtime-ui`) | none |
| Unresolved `@color` refs | none |
| Unresolved `@style` refs | 4 × `Widget.MaterialComponents.*` — resolved from the Material library at merge time, not an error |
| Unresolved `@string` refs | 1 × `appbar_scrolling_view_behavior` — resolved from the Material library, not an error |
| XML well-formedness (all layouts, both modules) | all parse cleanly |
| Other `CompoundButton` subclasses | `CheckBox` / `RadioButton` do not use `StaticLayout` for labels — not affected |

## 6. Verification status

**Not built, not run.** This sandbox has no JDK and no Android SDK, and the brief forbids
installing one. Nothing below is a claim of success.

What *was* verified:

- All 31 XML switch tags were replaced (grep count: 23 + 4 + 3 + 1).
- Every layout and values file in both modules parses as well-formed XML (Python `ElementTree`).
- No duplicate resource names remain in either module.
- `native-runtime-ui` exposes `androidx.appcompat` and `com.google.android.material` as `api`
  dependencies, so `:app` can see `SwitchSafety` and `SwitchCompat` on its compile classpath.

What was **not** verified:

- Compilation.
- Unit test execution (`SwitchSafetyTest`, 6 methods).
- On-device reproduction of the original crash.

To verify locally:

```bash
cd RONiLiFEOS
./gradlew :app:assembleDebug
./gradlew test
```

Then open each screen that contains a switch: **Settings** (4 switches), **Project editor
dialog** (3 switches), **UI Configuration editor** (23 switches), and any runtime screen that
renders the `SWITCH` component.

## 7. Residual risk

- If a future screen uses a bare `SwitchCompat` or `MaterialSwitch` instead of the safe
  subclasses, the crash can return. Prefer `SafeMaterialSwitch` / `SafeSwitchCompat` in new code.
- `SwitchSafety.harden()` cannot protect a switch that is already attached and measured before
  the call — which is why the guard lives in the constructor.
- The `debugSwitch()` logging in `ProjectEditDialog` remains and will spam logcat at ERROR level.
