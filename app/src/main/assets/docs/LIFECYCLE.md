# Consumer Activity Lifecycle & Navigation

Configuration-driven startup, project launching and back navigation for the RONiLiFEOS consumer
app (`com.roni.life.os.app`).

## 1. The four concepts

| Concept | Implementation | Optional |
|---|---|---|
| Splash | `SplashActivity` (also the launcher) | yes — `splashEnabled` |
| Dashboard | `BootstrapActivity` in dashboard mode | yes — `dashboardEnabled` |
| Bootstrap / base | `BootstrapActivity` + `NativeRuntimeActivity` | always reachable |
| Project runtime | `ProjectActivity` (one document task per project) | always runnable |

A project never depends on the dashboard. `ProjectActivity` is started directly by
`StartupManager` when the dashboard is off.

## 2. Startup routing

`StartupDecision.resolveConsumer(splashEnabled, dashboardEnabled, hasDefaultProject)` is the
single source of truth. `StartupManager.resolve()` and `StartupManager.resolveAfterSplash()`
both delegate to it.

```
                    ┌──────────────────────────┐
                    │  SplashActivity (launcher)│
                    └────────────┬─────────────┘
                                 │ splashEnabled?
              ┌──────────────────┴───────────────────┐
             ON                                     OFF
              │                                      │
     ┌────────▼────────┐                    ┌────────▼─────────┐
     │  Splash screen  │                    │  hand-off only   │
     └────────┬────────┘                    └────────┬─────────┘
              │  resolveConsumerAfterSplash()        │
              └───────────────┬──────────────────────┘
                              │
              ┌───────────────┼────────────────┐
     dashboardEnabled?   dashboardEnabled?   no default project
            YES               NO                   │
              │                │                   │
     ┌────────▼──────┐  ┌──────▼─────────┐  ┌──────▼────────────┐
     │ BootstrapActivity │ │ ProjectActivity │  │ StartupErrorActivity │
     │  (dashboard)  │  │ (default project)│  │  (safe fallback)  │
     └───────────────┘  └────────────────┘  └───────────────────┘
```

| Splash | Dashboard | Startup |
|---|---|---|
| ON | ON | Splash → Dashboard |
| OFF | ON | Dashboard |
| ON | OFF | Splash → Default Project |
| OFF | OFF | Default Project |

Rules enforced in code:

- Dashboard ON never auto-opens a project at startup.
- Dashboard OFF opens **only** the configured default project (`ProjectStore.defaultWebProject()`).
- No default project → `CONFIG_ERROR` → `StartupErrorActivity`. A random project is never chosen.
- `StartupManager.dashboardIntent(context)` refuses to build a dashboard intent while the
  dashboard is disabled; only `dashboardIntent(context, true)` (explicit user action) is allowed.
- `StartupManager` keeps a process-wide one-shot launch guard, so Splash → Dashboard → Splash
  cycles are impossible.

## 3. Back navigation

```
Back pressed
   │
   ├─ within BACK_DEBOUNCE_MS (400 ms) ──────────────► ignored
   │
   ├─ WebView.canGoBack() ───────────────────────────► webView.goBack()
   │                                                   (SPA / JS history preserved)
   │
   └─ history empty
        ├─ dashboardEnabled = true ──────────────────► minimizeToDashboard()
        └─ dashboardEnabled = false ─────────────────► root exit dialog
                                                         ├─ Minimize → moveTaskToBack(true)
                                                         ├─ Exit     → closing = true; finishAndRemoveTask()
                                                         └─ Cancel   → no change
```

The exit dialog is guarded by `exitDialogShowing`, so rapid back presses cannot stack dialogs.
Minimising never finishes the activity: the WebView, scroll position and session stay alive.
Exiting sets `closing = true`, which makes `onDestroy()` take the explicit-close path
(`ProjectSessionManager.close`) instead of the recoverable path.

## 4. Open Bootstrap from a project

`ProjectControlSheet` → **Open Bootstrap** → `ProjectActivity.openBootstrapFromProject()`:

1. `captureFormState()` snapshots uncommitted form input.
2. The session is marked minimised and persisted.
3. `BootstrapActivity` is started with `EXTRA_MANUAL_LAUNCH = true` and
   `NEW_TASK | REORDER_TO_FRONT | SINGLE_TOP`.
4. The project activity is **not** finished, so returning restores the same runtime.

`BootstrapActivity` skips its "open the startup project" shortcut when `manualLaunch` is true,
so the management interface is always what the user sees. `dashboardEnabled` is never modified
by this path.

## 5. Lifecycle safety

| Risk | Mitigation |
|---|---|
| Duplicate dashboard | `dashboardIntent` refuses when disabled; `goBootstrap()` falls back to the error screen |
| Splash loop | `resolveConsumerAfterSplash` never returns `SPLASH`; launch guard in `StartupManager` |
| Auto-launch loop | Default project is launched once per process by the launch guard |
| Project switch | `ProjectLaunchRouter` restores a live task instead of recreating it |
| Config change | `configChanges` on every activity; WebView state survives rotation |
| Process recreation | `SessionSnapshotStore` + `ProjectSessionManager.markRecoverable` |
| Finish vs. minimise | `closing` flag distinguishes explicit close from system destruction |

## 6. Test coverage

`app/src/test/java/com/roni/life/os/app/project/ConsumerStartupRoutingTest.java` covers the four
matrix rows, the splash hand-off, the configuration-error path, the "never fall back to the
dashboard" rule and the back policy. These are pure JVM tests (JUnit 4).

Scenarios that require a device (WebView history, SPA routing, task reordering, dialog
behaviour) are documented above and must be verified on an emulator — see section 7.

## 7. Manual verification checklist

1. Splash ON + Dashboard ON → splash, then dashboard, no project auto-opened.
2. Splash OFF + Dashboard ON → dashboard directly.
3. Splash ON + Dashboard OFF → splash, then default project.
4. Splash OFF + Dashboard OFF → default project directly.
5. Dashboard OFF + no default project → configuration screen, no dashboard.
6. Dashboard OFF + corrupted default project → configuration screen, no crash.
7. Dashboard OFF + in-page link → back returns to the previous page.
8. Dashboard OFF + SPA route change → back pops the SPA history.
9. Dashboard OFF + root page → back shows the exit dialog.
10. Exit dialog → Minimize → app backgrounded, project still listed as a session.
11. Exit dialog → Exit → app closed, session slot freed.
12. Project control sheet → Open Bootstrap → management UI, dashboard flag unchanged.
13. Back from Bootstrap → the same project page and scroll position.
14. Dashboard ON → open several projects → each keeps its own task.
15. Home button → resume → same page.
16. Rapid back presses → one history step / one dialog only.
