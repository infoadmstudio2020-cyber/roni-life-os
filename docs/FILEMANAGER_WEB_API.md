# `/filemanager` WebBridge API

All calls go through the existing NativeRuntimeSDK bridge. The project id is resolved from
the trusted host context (`FileManagerProjectContext`) — a `projectId` field in the payload
is ignored.

## Request shape

```js
NativeBridge.request("filemanager", "listFiles", { path: "docs", limit: 50 })
  .then((res) => {
    if (!res.ok) console.warn(res.error.code, res.error.message);
    else console.log(res.data.items);
  });
```

Responses follow the standard envelope: `{ ok: true, data }` or
`{ ok: false, error: { code, message } }`.

## Actions

| # | Action | Payload | Result |
|---|--------|---------|--------|
| 1 | `getStorageInfo` | – | `{ totalBytes, freeBytes, usedBytes, projectBytes, appBytes }` |
| 2 | `listFiles` | `path`, `includeHidden`, `sortBy`, `descending`, `offset`, `limit` | `{ items, count }` |
| 3 | `getFileInfo` | `path` | `FileEntry` |
| 4 | `createFile` | `path`, `content` | `FileEntry` |
| 5 | `createFolder` | `path` | `FileEntry` |
| 6 | `readFile` | `path` | `{ path, content, encoding }` |
| 7 | `writeFile` | `path`, `content` | `FileEntry` |
| 8 | `copy` | `paths[]`, `targetDir` | `{ operationId }` |
| 9 | `move` | `paths[]`, `targetDir` | `{ operationId }` |
| 10 | `rename` | `path`, `name` | `FileEntry` |
| 11 | `delete` | `paths[]` | `{ operationId }` |
| 12 | `search` | `path`, `name`, `extension`, `mimeType`, `category`, `minBytes`, `maxBytes`, `modifiedAfter`, `modifiedBefore`, `sortBy`, `descending`, `limit` | `{ items, count }` |
| 13 | `getRecent` | `limit` | `{ items, count }` |
| 14 | `addFavorite` | `path` | `{ path }` |
| 15 | `removeFavorite` | `path` | `{ path }` |
| 16 | `getFavorites` | – | `{ items, count }` |
| 17 | `import` | `treeId`, `documentId`, `targetDir` | `FileEntry` |
| 18 | `export` | `path`, `treeId`, `documentId` | `{ documentId }` |
| 19 | `pickDirectory` | – | `{ treeId, name }` (host UI) |
| 20 | `getPermissions` | `treeId` | `{ read, write, persisted }` |
| 21 | `requestAccess` | `treeId` | `{ granted }` |
| 22 | `getThumbnail` | `path` | `{ uri }` (content URI, read-only) |
| 23 | `getCategories` | – | `{ categories: [{ id, label, bytes, count }] }` |
| 24 | `getStorageAnalysis` | – | `{ totalBytes, freeBytes, usedBytes, projectBytes, appBytes, categories, largeFiles, oldFiles, emptyFolders, duplicateGroups }` |
| 25 | `createArchive` | `paths[]`, `name` | `{ operationId }` |
| 26 | `extractArchive` | `path`, `targetDir`, `entries[]` | `{ operationId }` |
| 27 | `getOperationStatus` | `operationId` | `FileOperation` |
| 28 | `cancelOperation` | `operationId` | `{ cancelled }` |
| 29 | `listArchive` | `path` | `{ entries: [{ name, sizeBytes, compressedBytes, directory }] }` |

## Error codes

`INVALID_PATH`, `PATH_TRAVERSAL`, `NOT_FOUND`, `ALREADY_EXISTS`, `NOT_A_DIRECTORY`,
`NOT_A_FILE`, `PERMISSION_DENIED`, `NO_ACTIVE_PROJECT`, `FILE_TOO_LARGE`, `QUOTA_EXCEEDED`,
`LOW_STORAGE`, `OPERATION_CANCELLED`, `OPERATION_CONFLICT`, `ARCHIVE_INVALID`,
`ARCHIVE_LIMIT`, `ARCHIVE_SLIP`, `SAF_PERMISSION_REQUIRED`, `SAF_UNSUPPORTED_OPERATION`,
`UNSUPPORTED`, `IO_ERROR`.

## JavaScript examples

### Browse a folder

```js
const res = await NativeBridge.request("filemanager", "listFiles", {
  path: "docs",
  sortBy: "name",
  descending: false,
  limit: 100,
});
res.data.items.forEach((item) => {
  console.log(item.name, item.isDirectory, item.sizeBytes, item.mimeType);
});
```

### Create, write and read a file

```js
await NativeBridge.request("filemanager", "createFolder", { path: "notes" });
await NativeBridge.request("filemanager", "writeFile", {
  path: "notes/today.md",
  content: "# Today\n",
});
const file = await NativeBridge.request("filemanager", "readFile", {
  path: "notes/today.md",
});
console.log(file.data.content);
```

### Copy with progress and cancellation

```js
const { data } = await NativeBridge.request("filemanager", "copy", {
  paths: ["notes/today.md"],
  targetDir: "backup",
});
const timer = setInterval(async () => {
  const status = await NativeBridge.request("filemanager", "getOperationStatus", {
    operationId: data.operationId,
  });
  console.log(status.data.progressPercent, status.data.state);
  if (status.data.finished) clearInterval(timer);
}, 500);
// NativeBridge.request("filemanager", "cancelOperation", { operationId: data.operationId });
```

### Search with filters

```js
const res = await NativeBridge.request("filemanager", "search", {
  path: "",
  extension: "pdf",
  minBytes: 1024,
  sortBy: "date",
  descending: true,
  limit: 50,
});
```

### Archive

```js
const created = await NativeBridge.request("filemanager", "createArchive", {
  paths: ["docs"],
  name: "docs.zip",
});
await NativeBridge.request("filemanager", "extractArchive", {
  path: "docs.zip",
  targetDir: "restored",
});
```

## Rules for Web Apps

* Only relative paths inside the project namespace are accepted.
* Absolute paths, `..`, drive letters and reserved names are rejected with
  `PATH_TRAVERSAL` / `INVALID_PATH`.
* Web Apps cannot launch Android components or touch storage hardware directly; use
  `getThumbnail` / `export` and the host UI for pickers.
* Long-running operations must be polled with `getOperationStatus`; do not block the bridge.
