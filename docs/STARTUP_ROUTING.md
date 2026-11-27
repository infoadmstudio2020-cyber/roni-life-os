# Startup Routing & Default Project

This document is the contract for how the app decides what to open. It exists because the
default project used to be resolved from three competing sources, which made "Set as default"
unreliable.

## 1. Project types

| Type | Hosted by | Can be the default application project |
|------|-----------|----------------------------------------|
| `SPLASH` | `SplashActivity` | No |
| `BOOTSTRAP` | `BootstrapActivity` (NATIVE) / `WebBootstrapActivity` (WEB) | No |
| `WEB` | `ProjectActivity` | **Yes** |
| `WORKSPACE` | reference only | No |

`ProjectType.canBeDefaultApplication()` is the single source of truth for the last column.
A Splash project is a presentation, never an application project.

## 2. The default project rule

`DefaultProjectResolver` is the only place that answers "which project is the default?".

1. `AppConfig.defaultWebProjectId` is **authoritative**.
2. The per-project `isDefault` flag and `AppConfig.defaultProjectId` are **legacy inputs**.
   They are read only when the authoritative value is empty, and are migrated into it once.
3. `AppConfig.lastOpenedProjectId` is **never consulted**. Opening a project must never change
   which project starts.
4. No project is ever picked at random. When the configured project is missing or is not a
   `WEB` project, the resolution **fails** and startup shows `StartupErrorActivity`.

Resolution statuses: `RESOLVED`, `NOT_CONFIGURED`, `MISSING`, `NOT_LAUNCHABLE`.

### Writing the default

`ProjectStore.setDefaultProject(id)` is the only writer. It:

- rejects a project that does not exist or is not a `WEB` project, returning a message;
- clears every other `isDefault` flag;
- writes `defaultWebProjectId` **and** `defaultProjectId` together;
- re-reads the config and verifies the value round-tripped.

`ProjectStore.save(project)` routes through the same writer, so the slot editor and the
project dialog cannot produce a half-written default. `clearDefault()` removes the selection
from the flag and both pointers.

## 3. Startup matrix

Driven only by `splashEnabled` and `dashboardEnabled` (`StartupDecision`):

| Splash | Dashboard | Startup | After splash |
|--------|-----------|---------|--------------|
| ON | ON | `SplashActivity` | Dashboard |
| OFF | ON | Dashboard | — |
| ON | OFF | `SplashActivity` | Default project |
| OFF | OFF | Default project | — |

`dashboardEnabled = false` **never** resolves to the dashboard. If no launchable default
project exists, the destination is `CONFIG_ERROR` → `StartupErrorActivity`, which always offers
an explicit "Open Bootstrap" action.

`StartupManager.resolveAfterSplash()` and `resolveForHost()` never return `SPLASH`, which is
what prevents Splash → Bootstrap → Splash loops.

## 4. Bootstrap vs Dashboard

`BootstrapActivity` serves two different purposes and they are now separated:

- **Dashboard** — the automatic destination when `dashboardEnabled = true`. An automatic
  dashboard intent that arrives while the preference is off is refused at the Activity
  boundary and re-routed through the startup matrix.
- **Management** — opened explicitly from Settings (`EXTRA_MANUAL_LAUNCH`). It never
  auto-routes to a project, never changes the dashboard preference, shows a "Management"
  subtitle, and Back returns to the project that was running.

## 5. First-run seeding

`RONiLiFEOSStorageInitializer.ensureDefaultProject()` seeds a project only when the registry is
completely empty, and adopts the pointer only when nothing is configured. It never overwrites
an existing selection — the previous behaviour of rewriting `defaultProjectId` on every launch
is what made an explicitly selected default revert after a restart.

## 6. Diagnostics

Every startup writes these lines to the in-app log:

```
STARTUP_CONFIG: splashEnabled=… dashboardEnabled=…
DEFAULT_PROJECT: selectedId=… resolvedId=… source=… status=… projectExists=… projectLaunchable=…
STARTUP_DESTINATION: SPLASH_ACTIVITY|DASHBOARD|PROJECT_ACTIVITY|STARTUP_ERROR projectId=…
PROJECT_LAUNCH: projectId=… entryPoint=… result=SUCCESS|BLOCKED
SET_DEFAULT: selectedId=… previousId=… persistedId=… resolvedId=…
```

## 7. Tests

`DefaultProjectResolverTest` and `StartupMatrixTest` cover the rule and the four-way matrix:
selection replacement, restart persistence, deletion, invalid type, legacy migration, and
"dashboard off never opens the dashboard".
