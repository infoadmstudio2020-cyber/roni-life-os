# Plan — Project Minimize & Multi-Project Runtime

## 1. What already exists

| Component | State | Notes |
| :--- | :--- | :--- |
| `ProjectSessionManager` | **Complete** | 5-session cap, `ACTIVE`/`MINIMIZED`, LRU eviction of minimised sessions, `register`/`minimize`/`resume`/`close`/`getSessions` |
| `ProjectActivity.onPause()` | **Complete** | Calls `ProjectSessionManager.minimize(id)` |
| `ProjectActivity.onResume()` | **Complete** | Calls `ProjectSessionManager.resume(id)` |
| Dashboard `stat_sessions` | **Complete** | Renders `n/5` |
| `ProjectAdapter` | **Complete** | RecyclerView list of all projects |

The session *model* is done. Everything below is the missing *surface*.

## 2. The gaps

### G1 — No way to minimise from inside a project
`onBackPressed()` → `goBootstrap()` → `finish()`. The activity is **destroyed**, the WebView is gone, and the session is never closed. "Minimise" does not exist as an action.

### G2 — Session lifecycle is correct, but only because nothing survives
`onDestroy()` does call `ProjectSessionManager.close()`, so there is **no leak**. The problem is the opposite: because `goBootstrap()` always calls `finish()`, every exit destroys the runtime and frees the slot. A session can never actually be *minimised* — the `MINIMIZED` state is only ever reached for the brief window between `onPause()` and `onDestroy()`.

### G3 — No overlay control
Nothing floats over the project runtime. The only exit is the system Back gesture.

### G4 — No management dialog
No bottom sheet, no restart, no close, no switch.

### G5 — No resume path
Nothing ever calls `startActivity` for an already-open project. `resume()` exists but is only reachable by the system restoring the activity.

### G6 — Dashboard cannot see sessions
`stat_sessions` is a number. It is not tappable and lists nothing.

## 3. Design decisions

### D1 — "Minimise" = reorder, not destroy
`minimizeToDashboard()` starts `BootstrapActivity` with `FLAG_ACTIVITY_REORDER_TO_FRONT` and **does not call `finish()`**. The `ProjectActivity` instance stays in the back stack in `onStop()`, its WebView intact. Resuming reuses the same instance — no reload, no lost scroll position.

Rejected: `moveTaskToBack()` (backgrounds the whole app, not the project), Picture-in-Picture (video-only, needs API 26+ and a `supportsPictureInPicture` manifest flag), freeform multi-window (cannot be forced programmatically).

### D2 — Overlay button lives inside the activity, not the system
A `FrameLayout` added via `addContentView()`, containing one 48dp circular button. **No `SYSTEM_ALERT_WINDOW` permission** — that is a scary, frequently-denied permission and would draw over other apps. The button is draggable and snaps to the nearest horizontal edge so it never covers content permanently.

The overlay `FrameLayout` is not clickable, so touches outside the button fall through to the WebView.

### D3 — Back = minimise, Close = destroy
Back is non-destructive: it minimises and keeps the session. Explicit destruction is only via the sheet's **Close project** row, which calls `finish()` + `close()`. This matches how mobile users expect Back to behave in a tabbed runtime.

### D4 — Session cap stays at 5
`MAX_SESSIONS = 5` is already enforced with LRU eviction. Each live session holds a WebView; five is a defensible memory ceiling on a low-end device. The sheet surfaces the count so the limit is never a surprise.

## 4. Implementation

### New files

| File | Purpose |
| :--- | :--- |
| `ui/ProjectOverlayButton.java` | Draggable circular FAB overlaid on the project runtime |
| `ui/ProjectControlSheet.java` | `BottomSheetDialog` — minimise / restart / settings / close / switch |
| `res/layout/sheet_project_control.xml` | Sheet shell; action rows built in code |
| `res/drawable/bg_sheet_top.xml` | Top-rounded sheet background |

### Modified files

| File | Change |
| :--- | :--- |
| `ProjectActivity.java` | Attach overlay; `minimizeToDashboard()`; Back → minimise; `onDestroy` closes the session when finishing |
| `BootstrapActivity.java` | Running-sessions section; tap to resume |
| `activity_bootstrap.xml` | `running_container` + `running_empty` |
| `ProjectAdapter.java` | `RUNNING` chip on live projects |
| `item_project.xml` | `project_running_chip` |
| `strings.xml` | `pm_*` strings |

### Resume mechanics

`BootstrapActivity.launchProject()` starts `ProjectActivity` with `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`, so the existing instance is reused rather than stacked.

`ProjectActivity.onNewIntent()` then decides: same project id → no-op (WebView state preserved); different id → `recreate()` to load the new source.

`FLAG_ACTIVITY_REORDER_TO_FRONT` was **rejected** here. It matches by component, not by intent extras, so resuming project B could surface project A's instance. `CLEAR_TOP` plus an explicit id comparison is deterministic.

**Phase 1 limitation:** only one `ProjectActivity` instance exists at a time. Opening a different project recreates the runtime and frees the previous session. True concurrent runtimes need the per-task document model in Phase 2.

## 5. Out of scope (Phase 2)

- **True multi-task document model** — `FLAG_ACTIVITY_NEW_DOCUMENT` + `documentLaunchMode="always"` so each project appears as its own entry in the system Recents screen. Requires `ActivityManager.moveTaskToFront` and per-task bookkeeping. Deferred: it changes how the app appears in Recents, which is a product decision, not a bug fix.
- **Picture-in-Picture** — only meaningful for video content.
- **Persisting sessions across process death** — `ProjectSessionManager` is in-memory by design; a cold start begins with zero sessions.

## 6. Verification plan

1. Open project A → overlay button visible, draggable, snaps to edge.
2. Tap overlay → sheet shows A, four actions, no other sessions.
3. **Minimise** → dashboard appears, `stat_sessions` = `1/5`, A shows a `RUNNING` chip.
4. Tap A on the dashboard → returns to the *same* WebView instance (scroll position preserved).
5. Open B, minimise, open C → three sessions, sheet lists B and C under "Other running projects".
6. **Close** from the sheet → session count drops, dashboard chip clears.
7. Back out of a project → session **stays** (minimise), count unchanged.
8. Force-stop and reopen → zero sessions, no phantom lockout.
