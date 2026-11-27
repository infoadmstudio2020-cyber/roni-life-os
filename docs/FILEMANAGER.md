# Native File Manager Engine

Project-isolated file management for the NativeRuntimeSDK ecosystem, consumed by the
RONiLiFEOS Consumer App and exposed to Web Apps through the `/filemanager` bridge route.

## 1. Architecture

```
Web App (JS)
   │  NativeBridge.request("filemanager", action, payload)
   ▼
NativeApiIntegration  ── registers ──►  FileCommandHandler   (native-platform-engine)
                                             │
                                             ▼
                                       FileManagerService
                        ┌──────────────┬─────────────┬──────────────┐
                        ▼              ▼             ▼              ▼
              FileManagerRepository  SafFileStore  ArchiveManager  StorageAnalyzer
                        │              │             │              │
                        ▼              ▼             ▼              ▼
              app-private files   DocumentsContract  java.util.zip  hashing / walk
                        │
                        ▼
              native-storage-engine (EncryptedPreferences, StorageEngine)
```

The engine lives in `native-platform-engine` (L2). It **does not** create a second storage
engine: metadata reuses `EncryptedPreferences` from `native-storage-engine`, and the root
directory is the app-private `files/filemanager` folder.

### Layers

| Layer | Module | Responsibility |
|-------|--------|----------------|
| Contracts | `native-runtime-contracts` | Pure Java models, no Android imports |
| Engine | `native-platform-engine` | Repository, SAF store, operations, archives, analyzer |
| Bridge | `native-runtime-sdk` | `/filemanager` route registration |
| UI | `app` | Explorer, details, analyzer, settings |

### Contracts (`com.roni.library.contracts.filemanager`)

`FileManagerLimits`, `FileManagerErrorCodes`, `FileCategory`, `FileEntry`,
`FileManagerError`, `FileManagerResult`, `StorageInfo`, `FileOperation`, `ArchiveEntry`,
`SearchQuery`.

### Engine (`com.roni.library.platform.filemanager`)

| Class | Responsibility |
|-------|----------------|
| `FileManagerProjectContext` | Trusted active project id (set by the host, never by JS) |
| `FileManagerPaths` | Normalisation, traversal guards, reserved names, project id validation |
| `FileManagerRepository` | Project roots, listing, sorting, metadata, copy/move/delete |
| `SafFileStore` | SAF document trees via `DocumentsContract` (`treeId` + `documentId`) |
| `FileOperationRegistry` | Operation ids, progress, cancellation, duplicate-target guard |
| `ArchiveManager` | ZIP create/list/extract with ZIP Slip protection and limits |
| `StorageAnalyzer` | Usage, categories, large/old files, empty folders, duplicates |
| `FileManagerService` | Orchestration, background executor, project resolution |
| `FileCommandHandler` | `/filemanager` bridge actions and payload validation |

## 2. Storage layout

```
<app-private>/files/filemanager/<projectId>/…
```

* Every project gets its own directory; the id is validated (`[A-Za-z0-9._-]`, max 64 chars).
* Paths crossing the bridge are **relative** (`docs/report.pdf`). Absolute filesystem paths
  are never returned to Web Apps.
* `FileManagerPaths.normalize` rejects `..`, absolute paths, drive letters, reserved DOS
  names (`CON`, `NUL`, `COM1`…), control characters and over-long segments before any I/O.
* Canonical-path checks run again after resolution, so symlinks cannot escape the root.

## 3. Storage providers

| Provider | Support |
|----------|---------|
| App-private internal storage | Full (create, read, write, copy, move, rename, delete, archive) |
| Project storage | Full, isolated per project |
| SAF document trees | List, read, write, create, delete, rename where the provider allows |
| Content URIs | Addressed as `treeId` + `documentId`; never converted to filesystem paths |

SAF operations that a provider does not support surface as
`SAF_UNSUPPORTED_OPERATION` instead of silently failing. Persisted grants are stored in
`EncryptedPreferences` (`filemanager_meta`).

## 4. Operations

* All blocking mutations run on a single-thread `ExecutorService`; the main thread only
  receives progress callbacks.
* Each operation gets an id (`FileOperationRegistry`) with byte/item progress and a
  cancellation flag polled inside copy loops.
* Existing targets are never overwritten: the service appends ` (1)`, ` (2)` … before the
  extension.
* Copying a folder into itself is rejected before any I/O.
* Low-storage conditions are checked before a copy starts (`ensureCapacity`).

## 5. Consumer App

| Screen | Class |
|--------|-------|
| File manager home / explorer | `FileManagerActivity` |
| File details | `FileDetailsActivity` |
| Storage analyzer | `StorageAnalyzerActivity` |
| Settings + SAF picker | `FileManagerSettingsActivity` |

Entry points: RONiLiFEOS dashboard menu → **File manager**, and project menu → **File manager**
(project-scoped). The explorer provides breadcrumbs, search, category chips (images, videos,
audio, documents), recent, favorites, multi-selection, create folder/file, copy/move/paste,
delete, share through the Android Sharesheet, and a progress bar driven by the operation
registry.

Sharing and `ACTION_VIEW` use a `FileProvider` (`<applicationId>.fileprovider`) so no
`file://` URI is ever exposed.

## 6. Known limitations

* Trash/recovery is not implemented: the storage architecture has no recycle bin, and
  deleting is permanent (confirmed by a dialog).
* SAF providers may not support rename/delete; those calls return
  `SAF_UNSUPPORTED_OPERATION`.
* Thumbnails are not generated by the engine; the UI uses category icons. Media preview
  reuses the platform viewer through `ACTION_VIEW`.
* Duplicate detection hashes files up to `MAX_HASH_BYTES`; larger files are compared by
  size only.
* `MANAGE_EXTERNAL_STORAGE` is never requested.
