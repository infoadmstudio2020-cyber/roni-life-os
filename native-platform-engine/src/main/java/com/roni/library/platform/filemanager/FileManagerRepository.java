package com.roni.library.platform.filemanager;

import android.content.Context;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.filemanager.FileCategory;
import com.roni.library.contracts.filemanager.FileEntry;
import com.roni.library.contracts.filemanager.FileManagerErrorCodes;
import com.roni.library.contracts.filemanager.FileManagerLimits;
import com.roni.library.contracts.filemanager.SearchQuery;
import com.roni.library.storage.preferences.EncryptedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Project-isolated file storage for the file manager.
 *
 * <p>Files live under {@code <app-private>/files/filemanager/<projectId>/}. Every path is
 * normalised and canonicalised before use, so a request can never escape its project directory.
 * Metadata (favorites, recent items) reuses {@link EncryptedPreferences} from the storage engine
 * instead of introducing a second store.</p>
 */
public final class FileManagerRepository {

    private static final String ROOT_DIR = "filemanager";
    private static final String PREFS = "filemanager_meta";
    private static final String FAVORITES_PREFIX = "favorites:";
    private static final String RECENT_PREFIX = "recent:";

    /** Progress sink for long running copies. */
    public interface ProgressListener {
        void onProgress(long processedBytes, long totalBytes, int processedItems, int totalItems);

        boolean isCancelled();
    }

    private final File root;
    @Nullable
    private final EncryptedPreferences preferences;
    private final Map<String, List<String>> memoryMetadata = new LinkedHashMap<>();

    public FileManagerRepository(@NonNull Context context,
                                 @Nullable EncryptedPreferences preferences) {
        this(new File(context.getFilesDir(), ROOT_DIR), preferences);
    }

    /** Test seam: builds a repository over an explicit root directory. */
    public FileManagerRepository(@NonNull File root,
                                 @Nullable EncryptedPreferences preferences) {
        this.root = root;
        this.preferences = preferences;
        if (!root.exists()) {
            //noinspection ResultOfMethodCallIgnored
            root.mkdirs();
        }
    }

    @NonNull
    public File getRoot() {
        return root;
    }

    @NonNull
    public File projectDir(@NonNull String projectId) throws FileManagerException {
        String safe = FileManagerPaths.requireProjectId(projectId);
        File dir = new File(root, safe);
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            throw FileManagerException.io("Cannot create the project directory", null);
        }
        return dir;
    }

    /** Resolves a project-relative path and proves it stays inside the project directory. */
    @NonNull
    public File resolve(@NonNull String projectId, @Nullable String relativePath)
            throws FileManagerException {
        File dir = projectDir(projectId);
        String normalized = FileManagerPaths.normalize(relativePath);
        File target = normalized.isEmpty() ? dir : new File(dir, normalized);
        try {
            String base = dir.getCanonicalPath();
            String path = target.getCanonicalPath();
            if (!path.equals(base) && !path.startsWith(base + File.separator)) {
                throw new FileManagerException(FileManagerErrorCodes.PATH_TRAVERSAL,
                        "Path escapes the project directory");
            }
        } catch (IOException error) {
            throw FileManagerException.io("Cannot resolve the path", error);
        }
        return target;
    }

    // ------------------------------------------------------------------
    // listing
    // ------------------------------------------------------------------

    @NonNull
    public List<FileEntry> list(@NonNull String projectId, @Nullable String relativePath,
                                boolean includeHidden, @NonNull String sortBy, boolean descending,
                                int offset, int limit) throws FileManagerException {
        File dir = resolve(projectId, relativePath);
        if (!dir.exists()) {
            throw FileManagerException.notFound("Folder does not exist");
        }
        if (!dir.isDirectory()) {
            throw new FileManagerException(FileManagerErrorCodes.NOT_A_DIRECTORY,
                    "Not a folder");
        }
        File[] children = dir.listFiles();
        List<FileEntry> entries = new ArrayList<>();
        if (children != null) {
            for (File child : children) {
                if (!includeHidden && child.getName().startsWith(".")) {
                    continue;
                }
                entries.add(toEntry(projectId, child));
            }
        }
        sort(entries, sortBy, descending);
        int from = Math.max(0, offset);
        if (from >= entries.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(entries.size(), from + Math.max(1, limit));
        return new ArrayList<>(entries.subList(from, to));
    }

    @NonNull
    public FileEntry entry(@NonNull String projectId, @Nullable String relativePath)
            throws FileManagerException {
        File file = resolve(projectId, relativePath);
        if (!file.exists()) {
            throw FileManagerException.notFound("File does not exist");
        }
        return toEntry(projectId, file);
    }

    @NonNull
    private FileEntry toEntry(@NonNull String projectId, @NonNull File file)
            throws FileManagerException {
        String relative = relativePath(projectId, file);
        String name = file.getName();
        boolean directory = file.isDirectory();
        String mime = directory ? "inode/directory" : mimeOf(name);
        int childCount = 0;
        if (directory) {
            String[] children = file.list();
            childCount = children != null ? children.length : 0;
        }
        return new FileEntry.Builder()
                .id(relative)
                .name(name)
                .relativePath(relative)
                .directory(directory)
                .sizeBytes(directory ? 0L : file.length())
                .modifiedAt(file.lastModified())
                .mimeType(mime)
                .category(directory ? FileCategory.OTHER : FileCategory.fromMime(mime))
                .hidden(name.startsWith("."))
                .childCount(childCount)
                .provider("internal")
                .build();
    }

    @NonNull
    public String relativePath(@NonNull String projectId, @NonNull File file)
            throws FileManagerException {
        File dir = projectDir(projectId);
        try {
            String base = dir.getCanonicalPath();
            String path = file.getCanonicalPath();
            if (path.equals(base)) {
                return "";
            }
            if (!path.startsWith(base + File.separator)) {
                throw new FileManagerException(FileManagerErrorCodes.CROSS_PROJECT_ACCESS,
                        "File belongs to another project");
            }
            return path.substring(base.length() + 1).replace(File.separatorChar, '/');
        } catch (IOException error) {
            throw FileManagerException.io("Cannot resolve the relative path", error);
        }
    }

    @NonNull
    public static String mimeOf(@NonNull String name) {
        String extension = FileCategory.extensionOf(name);
        if (!extension.isEmpty()) {
            try {
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (mime != null && !mime.isEmpty()) {
                    return mime;
                }
            } catch (Throwable ignored) {
                // Fall through to the built-in table when the platform map is unavailable.
            }
            String fallback = FALLBACK_MIME.get(extension);
            if (fallback != null) {
                return fallback;
            }
        }
        return "application/octet-stream";
    }

    private static final Map<String, String> FALLBACK_MIME = new LinkedHashMap<>();

    static {
        FALLBACK_MIME.put("txt", "text/plain");
        FALLBACK_MIME.put("md", "text/markdown");
        FALLBACK_MIME.put("json", "application/json");
        FALLBACK_MIME.put("xml", "application/xml");
        FALLBACK_MIME.put("html", "text/html");
        FALLBACK_MIME.put("csv", "text/csv");
        FALLBACK_MIME.put("pdf", "application/pdf");
        FALLBACK_MIME.put("png", "image/png");
        FALLBACK_MIME.put("jpg", "image/jpeg");
        FALLBACK_MIME.put("jpeg", "image/jpeg");
        FALLBACK_MIME.put("webp", "image/webp");
        FALLBACK_MIME.put("gif", "image/gif");
        FALLBACK_MIME.put("mp4", "video/mp4");
        FALLBACK_MIME.put("mp3", "audio/mpeg");
        FALLBACK_MIME.put("wav", "audio/wav");
        FALLBACK_MIME.put("zip", "application/zip");
    }

    public static void sort(@NonNull List<FileEntry> entries, @NonNull String sortBy,
                            boolean descending) {
        Comparator<FileEntry> base;
        switch (sortBy) {
            case SearchQuery.SORT_SIZE:
                base = Comparator.comparingLong(FileEntry::getSizeBytes);
                break;
            case SearchQuery.SORT_DATE:
                base = Comparator.comparingLong(FileEntry::getModifiedAt);
                break;
            case SearchQuery.SORT_TYPE:
                base = Comparator.comparing(FileEntry::getCategory)
                        .thenComparing(FileEntry::getName, String.CASE_INSENSITIVE_ORDER);
                break;
            case SearchQuery.SORT_NAME:
            default:
                base = Comparator.comparing(FileEntry::getName, String.CASE_INSENSITIVE_ORDER);
                break;
        }
        final Comparator<FileEntry> comparator = descending ? base.reversed() : base;
        Comparator<FileEntry> withFoldersFirst = (left, right) -> {
            if (left.isDirectory() != right.isDirectory()) {
                return left.isDirectory() ? -1 : 1;
            }
            return comparator.compare(left, right);
        };
        Collections.sort(entries, withFoldersFirst);
    }

    // ------------------------------------------------------------------
    // mutations
    // ------------------------------------------------------------------

    @NonNull
    public FileEntry createFile(@NonNull String projectId, @NonNull String relativePath,
                                @Nullable String content) throws FileManagerException {
        return createFile(projectId, relativePath,
                content == null ? null : content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public FileEntry createFile(@NonNull String projectId, @NonNull String relativePath,
                                @Nullable byte[] content) throws FileManagerException {
        File file = resolve(projectId, relativePath);
        if (file.exists()) {
            throw new FileManagerException(FileManagerErrorCodes.ALREADY_EXISTS,
                    "A file with that name already exists");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw FileManagerException.io("Cannot create the parent folder", null);
        }
        try {
            if (!file.createNewFile()) {
                throw FileManagerException.io("Cannot create the file", null);
            }
            if (content != null && content.length > 0) {
                writeBytes(file, content);
            }
        } catch (IOException error) {
            throw FileManagerException.io("Cannot create the file", error);
        }
        return toEntry(projectId, file);
    }

    @NonNull
    public FileEntry createFolder(@NonNull String projectId, @NonNull String relativePath)
            throws FileManagerException {
        File dir = resolve(projectId, relativePath);
        if (dir.exists()) {
            throw new FileManagerException(FileManagerErrorCodes.ALREADY_EXISTS,
                    "A file or folder with that name already exists");
        }
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw FileManagerException.io("Cannot create the folder", null);
        }
        return toEntry(projectId, dir);
    }

    @NonNull
    public FileEntry rename(@NonNull String projectId, @NonNull String relativePath,
                            @NonNull String newName) throws FileManagerException {
        FileManagerPaths.validateName(newName);
        File source = resolve(projectId, relativePath);
        if (!source.exists()) {
            throw FileManagerException.notFound("File does not exist");
        }
        String parent = FileManagerPaths.parentOf(FileManagerPaths.normalize(relativePath));
        File target = resolve(projectId, FileManagerPaths.childOf(parent, newName.trim()));
        if (target.exists()) {
            throw new FileManagerException(FileManagerErrorCodes.ALREADY_EXISTS,
                    "A file with that name already exists");
        }
        if (!source.renameTo(target)) {
            throw FileManagerException.io("Rename failed", null);
        }
        return toEntry(projectId, target);
    }

    public void delete(@NonNull String projectId, @NonNull String relativePath)
            throws FileManagerException {
        File target = resolve(projectId, relativePath);
        if (!target.exists()) {
            throw FileManagerException.notFound("File does not exist");
        }
        if (target.equals(projectDir(projectId))) {
            throw new FileManagerException(FileManagerErrorCodes.PERMISSION_DENIED,
                    "The project root cannot be deleted");
        }
        if (!deleteRecursive(target)) {
            throw FileManagerException.io("Delete failed", null);
        }
    }

    private boolean deleteRecursive(@NonNull File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    @NonNull
    public FileEntry copy(@NonNull String projectId, @NonNull String sourcePath,
                          @NonNull String targetPath, @Nullable ProgressListener listener)
            throws FileManagerException {
        File source = resolve(projectId, sourcePath);
        File target = resolve(projectId, targetPath);
        if (!source.exists()) {
            throw FileManagerException.notFound("Source does not exist");
        }
        if (isInside(source, target)) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_PATH,
                    "Cannot copy a folder into itself");
        }
        if (target.exists()) {
            throw new FileManagerException(FileManagerErrorCodes.ALREADY_EXISTS,
                    "Target already exists");
        }
        long total = sizeOf(source);
        long[] processed = {0L};
        int[] items = {0};
        copyRecursive(source, target, total, processed, items, listener);
        return toEntry(projectId, target);
    }

    @NonNull
    public FileEntry move(@NonNull String projectId, @NonNull String sourcePath,
                          @NonNull String targetPath, @Nullable ProgressListener listener)
            throws FileManagerException {
        File source = resolve(projectId, sourcePath);
        File target = resolve(projectId, targetPath);
        if (!source.exists()) {
            throw FileManagerException.notFound("Source does not exist");
        }
        if (target.exists()) {
            throw new FileManagerException(FileManagerErrorCodes.ALREADY_EXISTS,
                    "Target already exists");
        }
        if (isInside(source, target)) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_PATH,
                    "Cannot move a folder into itself");
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw FileManagerException.io("Cannot create the target folder", null);
        }
        if (source.renameTo(target)) {
            if (listener != null) {
                long size = sizeOf(target);
                listener.onProgress(size, size, 1, 1);
            }
            return toEntry(projectId, target);
        }
        // Cross-volume or provider refusal: fall back to copy + delete.
        long total = sizeOf(source);
        long[] processed = {0L};
        int[] items = {0};
        copyRecursive(source, target, total, processed, items, listener);
        if (!deleteRecursive(source)) {
            throw FileManagerException.io("Move copied the data but could not remove the source",
                    null);
        }
        return toEntry(projectId, target);
    }

    private void copyRecursive(@NonNull File source, @NonNull File target, long total,
                               @NonNull long[] processed, @NonNull int[] items,
                               @Nullable ProgressListener listener) throws FileManagerException {
        if (listener != null && listener.isCancelled()) {
            throw new FileManagerException(FileManagerErrorCodes.OPERATION_CANCELLED,
                    "Operation cancelled");
        }
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs() && !target.isDirectory()) {
                throw FileManagerException.io("Cannot create the target folder", null);
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursive(child, new File(target, child.getName()), total,
                            processed, items, listener);
                }
            }
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw FileManagerException.io("Cannot create the target folder", null);
        }
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                if (listener != null && listener.isCancelled()) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    throw new FileManagerException(FileManagerErrorCodes.OPERATION_CANCELLED,
                            "Operation cancelled");
                }
                output.write(buffer, 0, read);
                processed[0] += read;
                if (listener != null) {
                    listener.onProgress(processed[0], total, items[0], 0);
                }
            }
        } catch (IOException error) {
            throw FileManagerException.io("Copy failed: " + error.getMessage(), error);
        }
        items[0]++;
        if (listener != null) {
            listener.onProgress(processed[0], total, items[0], 0);
        }
    }

    private static boolean isInside(@NonNull File parent, @NonNull File child) {
        try {
            String parentPath = parent.getCanonicalPath();
            String childPath = child.getCanonicalPath();
            return childPath.equals(parentPath) || childPath.startsWith(parentPath + File.separator);
        } catch (IOException error) {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // content
    // ------------------------------------------------------------------

    @NonNull
    public byte[] read(@NonNull String projectId, @NonNull String relativePath, long maxBytes)
            throws FileManagerException {
        File file = resolve(projectId, relativePath);
        if (!file.exists() || file.isDirectory()) {
            throw FileManagerException.notFound("File does not exist");
        }
        if (file.length() > maxBytes) {
            throw new FileManagerException(FileManagerErrorCodes.FILE_TOO_LARGE,
                    "File exceeds the read limit of " + maxBytes + " bytes");
        }
        try (InputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            int read;
            while (offset < data.length && (read = input.read(data, offset,
                    data.length - offset)) > 0) {
                offset += read;
            }
            return data;
        } catch (IOException error) {
            throw FileManagerException.io("Read failed", error);
        }
    }

    public void write(@NonNull String projectId, @NonNull String relativePath,
                      @NonNull byte[] content) throws FileManagerException {
        write(projectId, relativePath, content, FileManagerLimits.MAX_WRITE_BYTES);
    }

    public void write(@NonNull String projectId, @NonNull String relativePath,
                      @NonNull byte[] content, long maxBytes) throws FileManagerException {
        if (content.length > maxBytes) {
            throw new FileManagerException(FileManagerErrorCodes.FILE_TOO_LARGE,
                    "Content exceeds the write limit");
        }
        File file = resolve(projectId, relativePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw FileManagerException.io("Cannot create the parent folder", null);
        }
        try {
            writeBytes(file, content);
        } catch (IOException error) {
            throw FileManagerException.io("Write failed", error);
        }
    }

    private static void writeBytes(@NonNull File file, @NonNull byte[] content)
            throws IOException {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(content);
            output.flush();
        }
    }

    // ------------------------------------------------------------------
    // search
    // ------------------------------------------------------------------

    @NonNull
    public List<FileEntry> search(@NonNull String projectId, @Nullable String startPath,
                                  @NonNull SearchQuery query) throws FileManagerException {
        File start = resolve(projectId, startPath);
        if (!start.exists() || !start.isDirectory()) {
            throw FileManagerException.notFound("Search folder does not exist");
        }
        int limit = Math.min(Math.max(1, query.getLimit()), FileManagerLimits.MAX_SEARCH_RESULTS);
        List<FileEntry> results = new ArrayList<>();
        Deque<File> queue = new ArrayDeque<>();
        Deque<Integer> depths = new ArrayDeque<>();
        queue.add(start);
        depths.add(0);
        while (!queue.isEmpty() && results.size() < limit) {
            File current = queue.removeFirst();
            int depth = depths.removeFirst();
            File[] children = current.listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                if (results.size() >= limit) {
                    break;
                }
                if (!query.isIncludeHidden() && child.getName().startsWith(".")) {
                    continue;
                }
                if (matches(child, query)) {
                    results.add(toEntry(projectId, child));
                }
                if (child.isDirectory() && depth + 1 < FileManagerLimits.MAX_SEARCH_DEPTH) {
                    queue.addLast(child);
                    depths.addLast(depth + 1);
                }
            }
        }
        sort(results, query.getSortBy(), query.isDescending());
        return results;
    }

    private static boolean matches(@NonNull File file, @NonNull SearchQuery query) {
        String name = file.getName().toLowerCase(Locale.US);
        if (!query.getName().isEmpty() && !name.contains(query.getName().toLowerCase(Locale.US))) {
            return false;
        }
        if (!query.getExtension().isEmpty()) {
            String extension = FileCategory.extensionOf(name);
            if (!extension.equals(query.getExtension().toLowerCase(Locale.US))) {
                return false;
            }
        }
        if (file.isDirectory()) {
            return query.getMinSizeBytes() <= 0 && query.getMaxSizeBytes() <= 0
                    && query.getMimeType().isEmpty() && query.getCategory().isEmpty();
        }
        if (!query.getMimeType().isEmpty()) {
            String mime = mimeOf(name);
            if (!mime.toLowerCase(Locale.US).startsWith(query.getMimeType().toLowerCase(Locale.US))) {
                return false;
            }
        }
        if (!query.getCategory().isEmpty()) {
            String category = FileCategory.fromMime(mimeOf(name));
            if (!category.equalsIgnoreCase(query.getCategory())) {
                return false;
            }
        }
        long size = file.length();
        if (query.getMinSizeBytes() > 0 && size < query.getMinSizeBytes()) {
            return false;
        }
        if (query.getMaxSizeBytes() > 0 && size > query.getMaxSizeBytes()) {
            return false;
        }
        long modified = file.lastModified();
        if (query.getModifiedAfter() > 0 && modified < query.getModifiedAfter()) {
            return false;
        }
        return query.getModifiedBefore() <= 0 || modified <= query.getModifiedBefore();
    }

    // ------------------------------------------------------------------
    // favorites and recent
    // ------------------------------------------------------------------

    @NonNull
    public List<String> favorites(@NonNull String projectId) {
        return readList(FAVORITES_PREFIX + projectId);
    }

    public void addFavorite(@NonNull String projectId, @NonNull String relativePath)
            throws FileManagerException {
        String normalized = FileManagerPaths.normalize(relativePath);
        if (normalized.isEmpty()) {
            throw FileManagerException.invalidPath("The project root cannot be a favorite");
        }
        List<String> values = readList(FAVORITES_PREFIX + projectId);
        if (!values.contains(normalized)) {
            values.add(normalized);
            while (values.size() > FileManagerLimits.MAX_FAVORITES) {
                values.remove(0);
            }
            writeList(FAVORITES_PREFIX + projectId, values);
        }
    }

    public void removeFavorite(@NonNull String projectId, @NonNull String relativePath)
            throws FileManagerException {
        String normalized = FileManagerPaths.normalize(relativePath);
        List<String> values = readList(FAVORITES_PREFIX + projectId);
        if (values.remove(normalized)) {
            writeList(FAVORITES_PREFIX + projectId, values);
        }
    }

    public void recordRecent(@NonNull String projectId, @NonNull String relativePath) {
        try {
            String normalized = FileManagerPaths.normalize(relativePath);
            if (normalized.isEmpty()) {
                return;
            }
            List<String> values = readList(RECENT_PREFIX + projectId);
            values.remove(normalized);
            values.add(0, normalized);
            while (values.size() > FileManagerLimits.MAX_RECENT_ITEMS) {
                values.remove(values.size() - 1);
            }
            writeList(RECENT_PREFIX + projectId, values);
        } catch (FileManagerException ignored) {
            // Recent history is best effort and must never fail a user operation.
        }
    }

    @NonNull
    public List<FileEntry> recent(@NonNull String projectId, int limit) {
        List<FileEntry> entries = new ArrayList<>();
        for (String path : readList(RECENT_PREFIX + projectId)) {
            try {
                File file = resolve(projectId, path);
                if (file.exists()) {
                    entries.add(toEntry(projectId, file));
                }
            } catch (FileManagerException ignored) {
                // Skip entries that no longer resolve.
            }
            if (entries.size() >= limit) {
                break;
            }
        }
        return entries;
    }

    @NonNull
    private List<String> readList(@NonNull String key) {
        List<String> values = new ArrayList<>();
        if (preferences == null) {
            List<String> cached = memoryMetadata.get(key);
            if (cached != null) {
                values.addAll(cached);
            }
            return values;
        }
        String raw = preferences.getString(key, "");
        if (raw.isEmpty()) {
            return values;
        }
        for (String line : raw.split("\n")) {
            if (!line.trim().isEmpty()) {
                values.add(line.trim());
            }
        }
        return values;
    }

    private void writeList(@NonNull String key, @NonNull List<String> values) {
        if (preferences == null) {
            memoryMetadata.put(key, new ArrayList<>(values));
            return;
        }
        preferences.putString(key, String.join("\n", values));
    }

    // ------------------------------------------------------------------
    // size helpers
    // ------------------------------------------------------------------

    public long sizeOf(@NonNull File file) {
        if (!file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return file.length();
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                total += sizeOf(child);
            }
        }
        return total;
    }

    public long projectSize(@NonNull String projectId) throws FileManagerException {
        return sizeOf(projectDir(projectId));
    }

    @NonNull
    public Map<String, Long> categoryTotals(@NonNull String projectId) throws FileManagerException {
        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put(FileCategory.IMAGE, 0L);
        totals.put(FileCategory.VIDEO, 0L);
        totals.put(FileCategory.AUDIO, 0L);
        totals.put(FileCategory.DOCUMENT, 0L);
        totals.put(FileCategory.ARCHIVE, 0L);
        totals.put(FileCategory.OTHER, 0L);
        accumulateCategories(projectDir(projectId), totals);
        return totals;
    }

    private void accumulateCategories(@NonNull File file, @NonNull Map<String, Long> totals) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    accumulateCategories(child, totals);
                }
            }
            return;
        }
        String category = FileCategory.fromMime(mimeOf(file.getName()));
        Long current = totals.get(category);
        totals.put(category, (current != null ? current : 0L) + file.length());
    }

    public long freeSpaceBytes() {
        return root.getFreeSpace();
    }

    public long totalSpaceBytes() {
        return root.getTotalSpace();
    }

    /** @return the preferences store used for favorites and recent items */
    @Nullable
    public EncryptedPreferences getPreferences() {
        return preferences;
    }

    @NonNull
    public static String preferencesName() {
        return PREFS;
    }
}
