# File Manager Security Audit

## Threat model

A Web App is untrusted input. It can send arbitrary paths, names, archive contents and
operation requests through `/filemanager`. The host (Consumer App) is trusted and owns the
project identity.

## Controls

| Threat | Control | Location |
|--------|---------|----------|
| Path traversal (`..`, absolute, drive letters) | `normalize` rejects before I/O; canonical check after resolution | `FileManagerPaths`, `FileManagerRepository.resolve` |
| Cross-project access | Root is `<files>/filemanager/<projectId>`; project id comes from `FileManagerProjectContext`, never from the payload | `FileManagerRepository.projectDir`, `FileManagerService.project` |
| Malicious file names | Control characters, separators, reserved DOS names, length limits | `FileManagerPaths.validateName` |
| ZIP Slip | Entry names normalised; canonical target must stay under the extraction root; violations raise `ARCHIVE_SLIP` | `ArchiveManager.extract` |
| Archive bombs | Entry count, per-entry size and total uncompressed limits | `FileManagerLimits` |
| Oversized files | Read/write byte limits; streaming copy with a fixed buffer | `FileManagerRepository.read/write`, `copyRecursive` |
| Untrusted MIME | MIME derived from the extension with a safe fallback (`application/octet-stream`) | `FileManagerRepository.mimeOf` |
| Unauthorized deletion | Deletion requires an explicit path list; UI confirms; no automatic cleanup | `FileManagerService.delete`, `FileManagerActivity` |
| Arbitrary filesystem access | No absolute paths cross the bridge; SAF addressed by `treeId`/`documentId` | `FileCommandHandler`, `SafFileStore` |
| Race conditions | Single-thread executor; duplicate-target guard in `FileOperationRegistry`; unique target names | `FileManagerService`, `FileOperationRegistry` |
| Symlink escape | Canonical path comparison against the project root | `FileManagerRepository.resolve` |
| URI exposure | Sharing uses `FileProvider` with a scoped `file_paths.xml` | `AndroidManifest.xml` |

## Permissions

* No `MANAGE_EXTERNAL_STORAGE`.
* No broad `READ/WRITE_EXTERNAL_STORAGE`.
* SAF access only through user-granted, persisted tree URIs.
* `FileProvider` is `exported=false` and grants per-URI read access only.

## Residual risks

* SAF providers may implement rename/delete inconsistently; the engine reports
  `SAF_UNSUPPORTED_OPERATION` rather than guessing.
* Duplicate detection hashes only files below `MAX_HASH_BYTES`; larger files are compared by
  size, so a false positive is possible (never auto-deleted).
* Deletion is permanent — there is no trash in the storage architecture.
