# File Manager — Build & Test Report

Date: 2026-11-20
Scope: NativeRuntimeSDK file manager engine + RONiLiFEOS Consumer App integration.

## 1. Modules touched

| Module | Change |
|--------|--------|
| `native-runtime-contracts` | +10 file manager contract classes (`com.roni.library.contracts.filemanager`) |
| `native-platform-engine` | +10 engine classes (`com.roni.library.platform.filemanager`), `PlatformEngine` wiring |
| `native-runtime-sdk` | `/filemanager` route registration in `NativeApiIntegration` |
| `app` | 4 activities, 1 adapter, 5 layouts, FileProvider, menu entries |

No new Gradle module was introduced. `native-storage-engine` was reused, not duplicated.

## 2. Compilation

The sandbox has no Android SDK/Gradle toolchain, so compilation was performed with the
Eclipse compiler (ECJ 3.33.0, `-17`) against a cleaned Android API 36 classpath
(`android-clean.jar`, `annotation-jvm.jar`, `core-1.13.1.jar`).

| Target | Result |
|--------|--------|
| `native-runtime-contracts` + `native-logger-sdk` + `native-service-sdk` + `native-storage-engine` + `native-platform-engine` | **PASS** (0 errors) |
| `app` file manager package (with AndroidX stubs) | **PASS** (0 errors) |
| XML resources (manifest, layouts, menus, `file_paths.xml`) | **PASS** (9/9 well-formed) |

Errors found and fixed during the build:

1. `SafFileStore.createDocument` — unhandled `FileNotFoundException` → mapped to `NOT_FOUND`.
2. `SafFileStore.displayName` — unhandled `FileManagerException` → falls back to the document id.
3. `FileManagerRepository.sort` — non-final comparator captured in a lambda → final local.
4. `FileCommandHandler` — list results passed to a map-only helper → added `list(...)` helper.
5. `FileManagerService` — `total` mutated then captured → final `totalBytes` snapshot.
6. `FileManagerRepository.mimeOf` — `MimeTypeMap` unavailable off-device → guarded with a
   built-in extension table.

## 3. Unit tests

`native-platform-engine/src/test/java/com/roni/library/platform/filemanager/FileManagerEngineTest.java`

```
JUnit version 4.13.2
...................
Time: 0.166
OK (19 tests)
```

| # | Test | Covers |
|---|------|--------|
| 1 | `normalizeRejectsTraversalAndAbsolutePaths` | `..`, absolute, drive letter, reserved name, control chars |
| 2 | `normalizeAcceptsNestedRelativePaths` | normalisation of `./a//b` |
| 3 | `projectIdIsRequired` | blank project id |
| 4 | `createListAndSortEntries` | create, list, sort asc/desc, MIME, category |
| 5 | `hiddenFilesAreFilteredByDefault` | hidden visibility |
| 6 | `paginationLimitsResults` | offset/limit |
| 7 | `renameCopyMoveDelete` | rename, copy, move, delete |
| 8 | `copyIntoItselfIsRejected` | self-copy guard |
| 9 | `duplicateNamesAreResolvedWithoutOverwrite` | no silent overwrite |
| 10 | `writeRejectsOversizedPayload` | write limit |
| 11 | `projectsCannotSeeEachOther` | project isolation |
| 12 | `traversalCannotEscapeProjectRoot` | cross-project traversal |
| 13 | `searchFiltersByNameExtensionAndCategory` | search filters |
| 14 | `archiveCreateListAndExtract` | ZIP create/list/extract |
| 15 | `zipSlipEntriesAreRejected` | ZIP Slip |
| 16 | `analyzerFindsLargeOldEmptyAndDuplicates` | analyzer + hash duplicates |
| 17 | `favoritesAndRecentAreProjectScoped` | metadata isolation |
| 18 | `operationRegistryTracksProgressAndCancellation` | progress + cancel |
| 19 | `batchDeleteRemovesAllTargets` | batch delete |

Not executed (no device/emulator in the sandbox): instrumented SAF tests, real
`ContentResolver` access, and on-device UI tests. These are **not** claimed as passing.

## 4. Behavioural notes

* Copy/move never overwrite: the service appends ` (1)`, ` (2)` before the extension.
* All mutations run on a single-thread executor; progress is polled through
  `getOperationStatus`.
* SAF documents are addressed by `treeId` + `documentId`; no filesystem path is fabricated.
* Sharing/opening uses `FileProvider` (`<applicationId>.fileprovider`).

## 5. Known limitations

* No trash/recovery (storage architecture has no recycle bin).
* SAF rename/delete depends on the provider; unsupported cases return
  `SAF_UNSUPPORTED_OPERATION`.
* Thumbnails are category icons; media preview delegates to `ACTION_VIEW`.
* Duplicate hashing is bounded by `MAX_HASH_BYTES`.
* Gradle/AGP builds and device tests could not be executed in this environment.
