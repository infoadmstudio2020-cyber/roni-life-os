# Storage Initialization

RONiLiFEOS builds its working tree on the first launch and verifies it on every launch after
that. This document describes the contract, the files involved and the guarantees the
implementation makes.

## 1. Where the tree lives

```
Android/data/<package>/files/RONiLiFEOS/
```

The root comes from `Context.getExternalFilesDir(null)`. App-specific external storage needs no
runtime permission on any supported API level, and the tree stays inspectable over USB. When
external storage is unavailable the initializer falls back to `Context.getFilesDir()` so the app
still starts; the fallback is transparent to every caller because they all go through
`StorageLayout.root(Context)`.

## 2. The tree

```
RONiLiFEOS/
├── system/
│   ├── splash/      config/  assets/  themes/
│   ├── bootstrap/   native/  web/  config/
│   ├── config/      app-config.json  runtime-config.json  storage-config.json  initialization.json
│   └── database/    schema.json
├── app/
│   ├── projects/
│   │   └── default/ manifest.json  ui/  sources/  config/  resources/
│   ├── config/      project-registry.json  active-project.json
│   ├── backups/
│   └── imports/
├── runtime/         workspace/  cache/  sessions/  logs/  config/
└── shared/          assets/  themes/  resources/
```

`StorageSpec` is the single source of truth for this list. It is pure Java, so the initializer and
the unit tests assert against exactly the same paths.

## 3. First launch

`RONiLiFEOSStorageInitializer.initialize(context, store)` runs before the runtime bootstrap and
performs five steps:

1. **Provision** — `StorageLayout.provision()` creates every directory. It also migrates a
   pre-1.4 install from internal storage to external storage, once, without overwriting anything.
2. **Copy templates** — the bundle at `assets/ronilifeos/` mirrors the tree one-to-one and is
   copied into place. A destination that already exists is skipped, never replaced.
3. **Default project** — `app/projects/default` gets `manifest.json`, `ui/`, `sources/`,
   `config/` and `resources/`, and is registered in `ProjectStore` as the active project.
4. **Validate** — `StorageSpec.missingDirectories()` and `missingFiles()` must both come back
   empty. A zero-length file counts as missing.
5. **Record** — `system/config/initialization.json` is written last, through a temp file and a
   rename.

If any step fails, the marker is not written and `AppInitializer` returns
`runtimeReady = false` with the reason. Bootstrap never starts on a half-built tree.

## 4. Later launches

The marker exists, so the pass is a verification rather than a build:

- Directories are recreated only when they are missing.
- Template files are copied only when they are missing or empty.
- The default project record is created only when it is absent; the active pointer is corrected
  only when it points somewhere else.
- The marker is rewritten with the current timestamp.

A user who edits `system/config/app-config.json` or replaces `app/projects/default/ui/index.html`
keeps their changes: the copy step never opens a file that already has content.

## 5. Interrupted runs

The marker is the commit point. A crash before it is written leaves the tree partially built and
the next launch repeats the same idempotent steps — existing files are skipped, missing ones are
created. A crash during a file write cannot leave a truncated file, because every write goes to
`<name>.tmp` and is renamed into place.

## 6. Registry consistency

`ProjectStore` is the single source of truth for projects; the JSON files under `app/config/` are
a mirror written by `StorageMirror`. The runtime bootstrap swaps the key-value backend, so the
initializer registers the default project twice: once before bootstrap (so the tree is complete
before the runtime starts) and once after, through
`RONiLiFEOSStorageInitializer.ensureRegistered(context, store)`. The second call only touches the
registry — no file is copied again.

## 7. Tests

| Test | Covers |
| --- | --- |
| `StorageSpecTest` | required paths are unique and relative; an empty root reports everything; a complete tree passes; a zero-length file counts as missing; a deleted directory is reported for recovery |
| `StorageInitStateTest` | marker round trip; corrupt or truncated markers read as "not initialised"; older versions are detected as upgrades; escaping survives |

Run them with:

```bash
./gradlew :app:testDebugUnitTest --tests "com.roni.life.os.app.core.*"
```

## 8. Manual verification

1. Clear app data, launch → the full tree exists and `initialization.json` is present.
2. Edit `system/config/app-config.json`, relaunch → the edit survives.
3. Delete `runtime/logs`, relaunch → the folder is recreated, nothing else changes.
4. Delete `initialization.json`, relaunch → the pass re-runs and rewrites the marker.
5. Kill the app during the first launch, relaunch → the tree completes without duplicates.
