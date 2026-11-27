# Storage & Default/Runtime Sources

## Root

`Android/data/<package>/files/RONiLiFEOS/` — resolved through `Context.getExternalFilesDir(null)`,
with the internal files directory as a fallback when external storage is unavailable.

## Tree

```
system/splash/{default,runtime,config}
system/bootstrap/{default,runtime,config}
system/{config,database}
app/{projects,config,backups,imports}
runtime/{workspace,cache,sessions,logs,config}
shared/{assets,themes,resources}
```

`system/splash/default` and `system/bootstrap/default` are seeded from
`app/src/main/assets/ronilifeos/` on first launch. `runtime/` folders are created empty: they are
the user's space and are never populated automatically.

## Configuration

`system/splash/config/splash-config.json`

```json
{ "schemaVersion": 2, "activeSource": "default", "fallbackEnabled": true,
  "runtimeEntry": "index.html", "updatedAt": 0 }
```

`system/bootstrap/config/bootstrap-config.json` adds `activeMode` (`native` | `web`).

Source selection and execution mode are independent: `activeSource` decides *which* copy loads,
`activeMode` decides *how* Bootstrap renders.

## Resolution

1. Read the surface's configuration.
2. Validate the active source: folder exists, entry document is non-empty, `manifest.json` is
   present.
3. Load it.
4. If the runtime source is invalid and `fallbackEnabled` is true, load Default instead and log the
   reason.
5. If neither validates, report a diagnostic; Splash then shows its inline fallback page.

## Operations

| Operation | Effect on files | Effect on config |
|---|---|---|
| Create | copies Default into `runtime/` | unchanged |
| Import / Update / Replace | staged, validated, then swapped in | unchanged |
| Validate | none | none |
| Activate | none | `activeSource = runtime` |
| Deactivate | none | `activeSource = default` |
| Remove | kept | `activeSource = default` |
| Delete | `runtime/` cleared | `activeSource = default` |
| Reset | none | `activeSource = default`, fallback on |

## Code map

| Class | Responsibility |
|---|---|
| `core/StorageLayout` | canonical paths |
| `core/StorageSpec` | required directories and files |
| `core/RONiLiFEOSStorageInitializer` | first-run seeding, repair, validation |
| `source/SourceConfig` | configuration model and codec (pure Java) |
| `source/SourceConfigStore` | atomic read/write |
| `source/SourceValidator` | structural validation (pure Java) |
| `source/SourceResolver` | selection, validation, fallback |
| `source/SourceManager` | runtime lifecycle |
| `source/SourceManagerDialog` | management UI |
| `runtime/SplashWebSource` | splash page resolution |
| `runtime/BootstrapSource` | bootstrap source + mode resolution |
