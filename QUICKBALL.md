# Quick Ball — Extensible Native Action System

## Overview

The Quick Ball is the floating control button drawn over a running project. Tapping it opens the
project control sheet; long-pressing minimises the project. The sheet keeps every action it had
before this update (minimise, restart, project UI settings, bootstrap, open in browser, close and
the live-session switcher) and now appends a registry-driven **Native tools** section plus a
**Customize Quick Ball** entry.

## Architecture

| Component | Responsibility |
|---|---|
| `NativeAction` | Immutable descriptor: id, label, description, icon, category, optional permission, execution handler. |
| `NativeActionRegistry` | Central registry. Rejects duplicate ids, preserves registration order, filters by category, exposes `ensureDefaults()` for the built-in catalog. |
| `QuickBallLayout` | Pure ordering rules: reconcile stored order with registered ids, move up/down, filter enabled ids, CSV encode/decode. |
| `QuickBallPreferences` | Persistence through the existing `EncryptedPreferences` engine (`quickball_ui` store). |
| `QuickBallSettingsActivity` | Customization UI: enable/disable, reorder, rename, change icon, appearance, reset. |
| `ProjectControlSheet` | Renders the existing project actions, then the enabled registry actions, then the session list. |
| `ProjectOverlayButton` | Reads ball size and opacity from the same preferences. |

The registry stores descriptors only — no `Activity`, `Context` or engine instance is retained.
Handlers receive the host `Activity` and the active `Project` at execution time.

## Built-in action catalog

| Id | Label | Category |
|---|---|---|
| `qb.inspector` | Runtime Inspector | Runtime |
| `qb.quick_settings` | Quick Settings | Runtime |
| `qb.project_settings` | Project UI Settings | Runtime |
| `qb.notifications` | Notification Center | System |
| `qb.notification_settings` | Notification Settings | System |
| `qb.downloads` | Downloads | Files |
| `qb.file_manager` | File Manager | Files |
| `qb.storage_analyzer` | Storage Analyzer | Files |
| `qb.file_manager_settings` | File Manager Settings | Files |
| `qb.camera` | Camera | Media |
| `qb.camera_settings` | Camera Settings | Media |
| `qb.automation` | Automation Dashboard | Automation |
| `qb.tasks` | Background Tasks | Automation |
| `qb.transfers` | Transfers | Automation |
| `qb.jobs` | Job Scheduler | Automation |
| `qb.alarms` | Alarm Manager | Automation |
| `qb.services` | Service Manager | Automation |
| `qb.events` | Event Monitor | Automation |
| `qb.bootstrap` | Bootstrap Dashboard | System |
| `qb.browser` | Open in Browser | System |

Automation shortcuts open the existing dashboard and scroll to the requested section
(`AutomationActivity.EXTRA_SECTION`); no engine is duplicated.

## Runtime Inspector

`qb.inspector` calls `RuntimeInspectorSheet.install(activity).toggle()`. `install` is idempotent
per Activity, so the sheet installed by `ProjectActivity` is reused — its logging, filtering,
export and debugging behaviour is unchanged.

## Customization and persistence

Stored in the `quickball_ui` encrypted preference store:

- `order` — CSV of action ids in display order
- `enabled` — CSV of enabled ids
- `label.<id>` / `icon.<id>` — per-action overrides
- `ball_size` — 36–72 dp (default 48)
- `ball_opacity` — 30–100 % (default 100)
- `layout` — `list` or `grid`
- `show_labels` — boolean
- `version` — migration marker

**Migration:** a store without `version` is upgraded to version 1 by enabling every registered
action in registration order — exactly the pre-update behaviour. Unknown ids are dropped and
newly registered ids are appended, so future actions appear without wiping the user's order.

## Extending the panel

```java
NativeActionRegistry.get().register(
        new NativeAction.Builder("feature.action", "My Action", (activity, project) -> {
            activity.startActivity(new Intent(activity, MyActivity.class));
        })
                .description("What it does")
                .icon(R.drawable.ic_info)
                .category(NativeAction.Category.TOOLS)
                .build());
```

No change to `ProjectControlSheet`, `ProjectOverlayButton` or the settings screen is required.
Duplicate ids are rejected, so registration is safe to repeat.

## Verification

- `QuickBallLayoutTest` — 7 tests: unknown-id removal, new-id append, duplicate collapse,
  clamped moves, enabled filtering, CSV round-trip.
- `NativeActionRegistryTest` — 7 tests: duplicate rejection, unregister, category filter,
  registration order, immutable label/icon overrides, blank id/label validation.
- **OK (14 tests)** on the JVM.
- `activity_quick_ball_settings.xml` and the updated manifest parse as valid XML.
- Full `./gradlew` execution is not possible in this sandbox (no Gradle distribution, no Android
  SDK); the sources compile against the project's Java 17 toolchain and library versions.
