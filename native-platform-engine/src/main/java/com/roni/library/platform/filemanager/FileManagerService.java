package com.roni.library.platform.filemanager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.filemanager.ArchiveEntry;
import com.roni.library.contracts.filemanager.FileCategory;
import com.roni.library.contracts.filemanager.FileEntry;
import com.roni.library.contracts.filemanager.FileManagerErrorCodes;
import com.roni.library.contracts.filemanager.FileManagerLimits;
import com.roni.library.contracts.filemanager.FileOperation;
import com.roni.library.contracts.filemanager.SearchQuery;
import com.roni.library.contracts.filemanager.StorageInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * File manager policy layer.
 *
 * <p>Every call is scoped to {@link FileManagerProjectContext}. Batch mutations run on a single
 * background executor so two operations can never write the same target concurrently; callers
 * receive an operation id and poll {@link #getOperationStatus(String)}.</p>
 */
public final class FileManagerService {

    private final FileManagerRepository repository;
    private final SafFileStore safStore;
    private final ArchiveManager archiveManager;
    private final StorageAnalyzer analyzer;
    private final FileOperationRegistry operations = new FileOperationRegistry();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public FileManagerService(@NonNull FileManagerRepository repository,
                              @NonNull SafFileStore safStore) {
        this.repository = repository;
        this.safStore = safStore;
        this.archiveManager = new ArchiveManager(repository);
        this.analyzer = new StorageAnalyzer(repository);
    }

    public void release() {
        executor.shutdownNow();
    }

    @NonNull
    private static String project() throws FileManagerException {
        return FileManagerPaths.requireProjectId(FileManagerProjectContext.getActiveProject());
    }

    // ------------------------------------------------------------------
    // read
    // ------------------------------------------------------------------

    @NonNull
    public StorageInfo getStorageInfo() throws FileManagerException {
        return analyzer.info(project());
    }

    @NonNull
    public List<FileEntry> listFiles(@Nullable String path, boolean includeHidden,
                                     @NonNull String sortBy, boolean descending,
                                     int offset, int limit) throws FileManagerException {
        int page = Math.min(Math.max(1, limit), FileManagerLimits.MAX_PAGE_SIZE);
        return repository.list(project(), path, includeHidden, sortBy, descending,
                Math.max(0, offset), page);
    }

    @NonNull
    public FileEntry getFileInfo(@Nullable String path) throws FileManagerException {
        return repository.entry(project(), path);
    }

    @NonNull
    public Map<String, Object> readFile(@Nullable String path) throws FileManagerException {
        String project = project();
        FileEntry entry = repository.entry(project, path);
        if (entry.isDirectory()) {
            throw new FileManagerException(FileManagerErrorCodes.NOT_A_FILE,
                    "Folders cannot be read as files");
        }
        byte[] data = repository.read(project, path, FileManagerLimits.MAX_READ_BYTES);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sizeBytes", data.length);
        result.put("mimeType", entry.getMimeType());
        result.put("base64", Base64.encodeToString(data, Base64.NO_WRAP));
        if (entry.getMimeType().startsWith("text/") || entry.getMimeType().contains("json")
                || entry.getMimeType().contains("xml")) {
            result.put("text", new String(data, java.nio.charset.StandardCharsets.UTF_8));
        }
        repository.recordRecent(project, path);
        return result;
    }

    @NonNull
    public List<FileEntry> search(@Nullable String startPath, @NonNull SearchQuery query)
            throws FileManagerException {
        return repository.search(project(), startPath, query);
    }

    @NonNull
    public List<FileEntry> getRecent(int limit) throws FileManagerException {
        return repository.recent(project(),
                Math.min(Math.max(1, limit), FileManagerLimits.MAX_RECENT_ITEMS));
    }

    @NonNull
    public List<FileEntry> getFavorites() throws FileManagerException {
        String project = project();
        List<FileEntry> entries = new ArrayList<>();
        for (String path : repository.favorites(project)) {
            try {
                entries.add(repository.entry(project, path));
            } catch (FileManagerException ignored) {
                // A favorite may point at a deleted file; skip it.
            }
        }
        return entries;
    }

    public void addFavorite(@Nullable String path) throws FileManagerException {
        repository.addFavorite(project(), path != null ? path : "");
    }

    public void removeFavorite(@Nullable String path) throws FileManagerException {
        repository.removeFavorite(project(), path != null ? path : "");
    }

    @NonNull
    public Map<String, Object> getCategories() throws FileManagerException {
        String project = project();
        Map<String, Long> totals = repository.categoryTotals(project);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            Map<String, Object> category = new LinkedHashMap<>();
            category.put("bytes", entry.getValue());
            category.put("label", StorageAnalyzer.categoryLabel(entry.getKey()));
            result.put(entry.getKey(), category);
        }
        return result;
    }

    @NonNull
    public Map<String, Object> getStorageAnalysis() throws FileManagerException {
        String project = project();
        StorageInfo info = analyzer.info(project);
        Map<String, Object> result = new LinkedHashMap<>(info.toMap());
        result.put("largeFiles", toMaps(analyzer.largeFiles(project, 0L, 50)));
        result.put("oldFiles", toMaps(analyzer.oldFiles(project, 0L, 50)));
        result.put("emptyFolders", toMaps(analyzer.emptyFolders(project)));
        List<List<FileEntry>> duplicates = analyzer.duplicates(project, 20);
        List<Object> groups = new ArrayList<>();
        for (List<FileEntry> group : duplicates) {
            groups.add(toMaps(group));
        }
        result.put("duplicateGroups", groups);
        return result;
    }

    @NonNull
    public Map<String, Object> getPermissions() throws FileManagerException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", project());
        result.put("canRead", true);
        result.put("canWrite", true);
        result.put("safRoots", safStore.listRoots());
        result.put("requiresHostPicker", true);
        return result;
    }

    @NonNull
    public Map<String, Object> getThumbnail(@Nullable String path) throws FileManagerException {
        String project = project();
        FileEntry entry = repository.entry(project, path);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entry.getId());
        if (entry.isDirectory() || !FileCategory.IMAGE.equals(entry.getCategory())) {
            result.put("thumbnail", "");
            return result;
        }
        File file = repository.resolve(project, path);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        int sample = 1;
        while (options.outWidth / sample > FileManagerLimits.MAX_THUMBNAIL_SIZE
                || options.outHeight / sample > FileManagerLimits.MAX_THUMBNAIL_SIZE) {
            sample *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) {
            result.put("thumbnail", "");
            return result;
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output);
        bitmap.recycle();
        result.put("thumbnail", "data:image/jpeg;base64,"
                + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP));
        return result;
    }

    // ------------------------------------------------------------------
    // synchronous mutations
    // ------------------------------------------------------------------

    @NonNull
    public FileEntry createFile(@Nullable String path, @Nullable String content)
            throws FileManagerException {
        byte[] bytes = content != null
                ? content.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
        if (bytes.length > FileManagerLimits.MAX_WRITE_BYTES) {
            throw new FileManagerException(FileManagerErrorCodes.FILE_TOO_LARGE,
                    "Content exceeds the write limit");
        }
        ensureCapacity(bytes.length);
        return repository.createFile(project(), path, bytes);
    }

    @NonNull
    public FileEntry createFolder(@Nullable String path) throws FileManagerException {
        return repository.createFolder(project(), path);
    }

    @NonNull
    public FileEntry rename(@Nullable String path, @Nullable String newName)
            throws FileManagerException {
        if (newName == null || newName.trim().isEmpty()) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_REQUEST,
                    "A new name is required");
        }
        return repository.rename(project(), path, newName);
    }

    public void writeFile(@Nullable String path, @Nullable String base64)
            throws FileManagerException {
        if (base64 == null) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_REQUEST,
                    "Content is required");
        }
        byte[] data;
        try {
            data = Base64.decode(base64, Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_REQUEST,
                    "Content is not valid base64", error);
        }
        if (data.length > FileManagerLimits.MAX_WRITE_BYTES) {
            throw new FileManagerException(FileManagerErrorCodes.FILE_TOO_LARGE,
                    "Content exceeds the write limit");
        }
        ensureCapacity(data.length);
        repository.write(project(), path, data);
    }

    /** Resolves a project file for the host to share through a content URI. */
    @Nullable
    public File resolveForSharing(@Nullable String path) throws FileManagerException {
        return repository.resolve(project(), path);
    }

    public void deleteOne(@Nullable String path) throws FileManagerException {
        repository.delete(project(), path);
    }

    private void ensureCapacity(long requiredBytes) throws FileManagerException {
        if (repository.freeSpaceBytes() - requiredBytes < FileManagerLimits.MIN_FREE_SPACE_BYTES) {
            throw new FileManagerException(FileManagerErrorCodes.STORAGE_FULL,
                    "Not enough free space to complete the operation");
        }
    }

    // ------------------------------------------------------------------
    // asynchronous batch mutations
    // ------------------------------------------------------------------

    @NonNull
    public String copy(@NonNull List<String> sources, @Nullable String targetDir)
            throws FileManagerException {
        String project = project();
        List<File> resolved = new ArrayList<>();
        long total = 0L;
        for (String source : sources) {
            File file = repository.resolve(project, source);
            if (!file.exists()) {
                throw FileManagerException.notFound("Source does not exist: " + source);
            }
            resolved.add(file);
            total += repository.sizeOf(file);
        }
        ensureCapacity(total);
        final long totalBytes = total;
        String operationId = operations.start("COPY", total, resolved.size());
        executor.execute(() -> {
            try {
                int done = 0;
                for (File source : resolved) {
                    if (operations.isCancelled(operationId)) {
                        throw new FileManagerException(FileManagerErrorCodes.OPERATION_CANCELLED,
                                "Operation cancelled");
                    }
                    String target = uniqueTarget(project, targetDir, source.getName());
                    repository.copy(project, repository.relativePath(project, source), target,
                            listener(operationId));
                    done++;
                    operations.progress(operationId, totalBytes, done);
                }
                operations.complete(operationId, "Copied " + done + " item(s)");
            } catch (FileManagerException error) {
                operations.fail(operationId, error.getMessage());
            } catch (Throwable error) {
                operations.fail(operationId, "Copy failed: " + error.getMessage());
            }
        });
        return operationId;
    }

    @NonNull
    public String move(@NonNull List<String> sources, @Nullable String targetDir)
            throws FileManagerException {
        String project = project();
        List<File> resolved = new ArrayList<>();
        long total = 0L;
        for (String source : sources) {
            File file = repository.resolve(project, source);
            if (!file.exists()) {
                throw FileManagerException.notFound("Source does not exist: " + source);
            }
            resolved.add(file);
            total += repository.sizeOf(file);
        }
        final long totalBytes = total;
        String operationId = operations.start("MOVE", total, resolved.size());
        executor.execute(() -> {
            try {
                int done = 0;
                for (File source : resolved) {
                    if (operations.isCancelled(operationId)) {
                        throw new FileManagerException(FileManagerErrorCodes.OPERATION_CANCELLED,
                                "Operation cancelled");
                    }
                    String target = uniqueTarget(project, targetDir, source.getName());
                    repository.move(project, repository.relativePath(project, source), target,
                            listener(operationId));
                    done++;
                    operations.progress(operationId, totalBytes, done);
                }
                operations.complete(operationId, "Moved " + done + " item(s)");
            } catch (FileManagerException error) {
                operations.fail(operationId, error.getMessage());
            } catch (Throwable error) {
                operations.fail(operationId, "Move failed: " + error.getMessage());
            }
        });
        return operationId;
    }

    /**
     * Resolves a non-colliding destination path. Existing files are never overwritten; a
     * numbered suffix is appended instead.
     */
    @NonNull
    private String uniqueTarget(@NonNull String project, @Nullable String targetDir,
                                @NonNull String name) throws FileManagerException {
        String dir = FileManagerPaths.normalize(targetDir);
        String candidate = FileManagerPaths.childOf(dir, name);
        if (!repository.resolve(project, candidate).exists()) {
            return candidate;
        }
        String base = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            extension = name.substring(dot);
        }
        for (int index = 1; index < 1000; index++) {
            candidate = FileManagerPaths.childOf(dir, base + " (" + index + ")" + extension);
            if (!repository.resolve(project, candidate).exists()) {
                return candidate;
            }
        }
        throw new FileManagerException(FileManagerErrorCodes.ALREADY_EXISTS,
                "Too many files with the same name");
    }

    @NonNull
    public String delete(@NonNull List<String> paths) throws FileManagerException {
        String project = project();
        List<File> resolved = new ArrayList<>();
        long total = 0L;
        for (String path : paths) {
            File file = repository.resolve(project, path);
            if (!file.exists()) {
                throw FileManagerException.notFound("File does not exist: " + path);
            }
            resolved.add(file);
            total += repository.sizeOf(file);
        }
        final long totalBytes = total;
        String operationId = operations.start("DELETE", total, resolved.size());
        executor.execute(() -> {
            try {
                int done = 0;
                for (File file : resolved) {
                    if (operations.isCancelled(operationId)) {
                        throw new FileManagerException(FileManagerErrorCodes.OPERATION_CANCELLED,
                                "Operation cancelled");
                    }
                    repository.delete(project, repository.relativePath(project, file));
                    done++;
                    operations.progress(operationId, totalBytes, done);
                }
                operations.complete(operationId, "Deleted " + done + " item(s)");
            } catch (FileManagerException error) {
                operations.fail(operationId, error.getMessage());
            } catch (Throwable error) {
                operations.fail(operationId, "Delete failed: " + error.getMessage());
            }
        });
        return operationId;
    }

    @NonNull
    public String createArchive(@NonNull List<String> sources, @Nullable String archivePath)
            throws FileManagerException {
        String project = project();
        long total = 0L;
        for (String source : sources) {
            total += repository.sizeOf(repository.resolve(project, source));
        }
        ensureCapacity(total);
        String operationId = operations.start("ARCHIVE_CREATE", total, sources.size());
        executor.execute(() -> {
            try {
                archiveManager.create(project, sources, archivePath != null ? archivePath : "",
                        listener(operationId));
                operations.complete(operationId, "Archive created");
            } catch (FileManagerException error) {
                operations.fail(operationId, error.getMessage());
            } catch (Throwable error) {
                operations.fail(operationId, "Archive creation failed: " + error.getMessage());
            }
        });
        return operationId;
    }

    @NonNull
    public String extractArchive(@Nullable String archivePath, @Nullable String targetDir,
                                 @Nullable List<String> selected) throws FileManagerException {
        String project = project();
        String operationId = operations.start("ARCHIVE_EXTRACT", 0L, 0);
        executor.execute(() -> {
            try {
                int count = archiveManager.extract(project, archivePath, targetDir, selected,
                        listener(operationId));
                operations.complete(operationId, "Extracted " + count + " file(s)");
            } catch (FileManagerException error) {
                operations.fail(operationId, error.getMessage());
            } catch (Throwable error) {
                operations.fail(operationId, "Extraction failed: " + error.getMessage());
            }
        });
        return operationId;
    }

    @NonNull
    public List<ArchiveEntry> listArchive(@Nullable String archivePath) throws FileManagerException {
        return archiveManager.list(project(), archivePath);
    }

    @NonNull
    private FileManagerRepository.ProgressListener listener(@NonNull String operationId) {
        return new FileManagerRepository.ProgressListener() {
            @Override
            public void onProgress(long processedBytes, long totalBytes, int processedItems,
                                   int totalItems) {
                operations.progress(operationId, processedBytes, processedItems);
            }

            @Override
            public boolean isCancelled() {
                return operations.isCancelled(operationId);
            }
        };
    }

    // ------------------------------------------------------------------
    // SAF import / export
    // ------------------------------------------------------------------

    @NonNull
    public FileEntry importFromSaf(@Nullable String treeId, @Nullable String documentId,
                                   @Nullable String targetDir) throws FileManagerException {
        if (treeId == null || documentId == null) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_REQUEST,
                    "treeId and documentId are required");
        }
        String project = project();
        String name = safStore.displayName(treeId, documentId);
        FileManagerPaths.validateName(name);
        String target = FileManagerPaths.childOf(FileManagerPaths.normalize(targetDir), name);
        File destination = repository.resolve(project, target);
        if (destination.exists()) {
            throw new FileManagerException(FileManagerErrorCodes.ALREADY_EXISTS,
                    "A file with that name already exists");
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw FileManagerException.io("Cannot create the target folder", null);
        }
        try (InputStream input = safStore.openInput(treeId, documentId);
             OutputStream output = new java.io.FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            destination.delete();
            throw FileManagerException.io("Import failed: " + error.getMessage(), error);
        }
        return repository.entry(project, target);
    }

    @NonNull
    public FileEntry exportToSaf(@Nullable String treeId, @Nullable String parentDocumentId,
                                 @Nullable String path) throws FileManagerException {
        if (treeId == null || parentDocumentId == null) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_REQUEST,
                    "treeId and parentDocumentId are required");
        }
        String project = project();
        FileEntry entry = repository.entry(project, path);
        if (entry.isDirectory()) {
            throw new FileManagerException(FileManagerErrorCodes.NOT_A_FILE,
                    "Only files can be exported");
        }
        File source = repository.resolve(project, path);
        FileEntry created = safStore.createDocument(treeId, parentDocumentId, entry.getName(),
                entry.getMimeType());
        String documentId = created.getRelativePath();
        try (InputStream input = new java.io.FileInputStream(source);
             OutputStream output = safStore.openOutput(treeId, documentId)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
        } catch (IOException error) {
            throw FileManagerException.io("Export failed: " + error.getMessage(), error);
        }
        return created;
    }

    @NonNull
    public List<FileEntry> listSaf(@Nullable String treeId, @Nullable String documentId)
            throws FileManagerException {
        if (treeId == null) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_REQUEST,
                    "treeId is required");
        }
        return safStore.list(treeId, documentId);
    }

    @NonNull
    public String registerSafTree(@NonNull android.net.Uri treeUri) {
        return safStore.registerTree(treeUri);
    }

    @NonNull
    public List<Map<String, Object>> getSafRoots() {
        return safStore.listRoots();
    }

    // ------------------------------------------------------------------
    // operations
    // ------------------------------------------------------------------

    @Nullable
    public FileOperation getOperationStatus(@Nullable String id) {
        return id != null ? operations.get(id) : null;
    }

    @NonNull
    public List<FileOperation> listOperations() {
        return operations.list();
    }

    public boolean cancelOperation(@Nullable String id) {
        if (id == null) {
            return false;
        }
        operations.cancel(id);
        return true;
    }

    @NonNull
    private static List<Map<String, Object>> toMaps(@NonNull List<FileEntry> entries) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (FileEntry entry : entries) {
            maps.add(entry.toMap());
        }
        return maps;
    }
}
