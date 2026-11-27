package com.roni.library.platform.filemanager;

import androidx.annotation.NonNull;

import com.roni.library.contracts.filemanager.FileCategory;
import com.roni.library.contracts.filemanager.FileEntry;
import com.roni.library.contracts.filemanager.FileManagerLimits;
import com.roni.library.contracts.filemanager.StorageInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only storage analysis. Never deletes or modifies user files. */
public final class StorageAnalyzer {

    private static final int MAX_HASH_CANDIDATES = 200;

    private final FileManagerRepository repository;

    public StorageAnalyzer(@NonNull FileManagerRepository repository) {
        this.repository = repository;
    }

    @NonNull
    public StorageInfo info(@NonNull String projectId) throws FileManagerException {
        File project = repository.projectDir(projectId);
        long projectBytes = repository.sizeOf(project);
        long appBytes = repository.sizeOf(repository.getRoot());
        return new StorageInfo(repository.totalSpaceBytes(), repository.freeSpaceBytes(),
                projectBytes, appBytes, repository.categoryTotals(projectId));
    }

    @NonNull
    public List<FileEntry> largeFiles(@NonNull String projectId, long minBytes, int limit)
            throws FileManagerException {
        long threshold = minBytes > 0 ? minBytes : FileManagerLimits.LARGE_FILE_BYTES;
        List<FileEntry> results = new ArrayList<>();
        collect(projectId, repository.projectDir(projectId), results, threshold, 0L, limit);
        FileManagerRepository.sort(results, "size", true);
        return results.size() > limit ? new ArrayList<>(results.subList(0, limit)) : results;
    }

    @NonNull
    public List<FileEntry> oldFiles(@NonNull String projectId, long olderThanMs, int limit)
            throws FileManagerException {
        long age = olderThanMs > 0 ? olderThanMs : FileManagerLimits.OLD_FILE_AGE_MS;
        long cutoff = System.currentTimeMillis() - age;
        List<FileEntry> results = new ArrayList<>();
        collectOld(projectId, repository.projectDir(projectId), results, cutoff, limit);
        FileManagerRepository.sort(results, "date", false);
        return results.size() > limit ? new ArrayList<>(results.subList(0, limit)) : results;
    }

    @NonNull
    public List<FileEntry> emptyFolders(@NonNull String projectId) throws FileManagerException {
        List<FileEntry> results = new ArrayList<>();
        collectEmpty(projectId, repository.projectDir(projectId), results);
        return results;
    }

    /**
     * Groups files that share a size and a SHA-256 digest.
     *
     * @return groups with at least two members; the caller decides what to do with them
     */
    @NonNull
    public List<List<FileEntry>> duplicates(@NonNull String projectId, int limit)
            throws FileManagerException {
        Map<Long, List<File>> bySize = new LinkedHashMap<>();
        collectBySize(repository.projectDir(projectId), bySize);
        List<List<FileEntry>> groups = new ArrayList<>();
        int hashed = 0;
        for (Map.Entry<Long, List<File>> entry : bySize.entrySet()) {
            if (entry.getKey() <= 0 || entry.getValue().size() < 2) {
                continue;
            }
            Map<String, List<File>> byHash = new LinkedHashMap<>();
            for (File file : entry.getValue()) {
                if (hashed++ >= MAX_HASH_CANDIDATES) {
                    break;
                }
                String hash = sha256(file);
                if (hash == null) {
                    continue;
                }
                List<File> bucket = byHash.get(hash);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    byHash.put(hash, bucket);
                }
                bucket.add(file);
            }
            for (List<File> bucket : byHash.values()) {
                if (bucket.size() < 2) {
                    continue;
                }
                List<FileEntry> group = new ArrayList<>();
                for (File file : bucket) {
                    group.add(repository.entry(projectId, repository.relativePath(projectId, file)));
                }
                groups.add(group);
                if (groups.size() >= limit) {
                    return groups;
                }
            }
        }
        return groups;
    }

    private void collect(@NonNull String projectId, @NonNull File file,
                         @NonNull List<FileEntry> results, long threshold, long unused,
                         int limit) throws FileManagerException {
        if (results.size() >= limit) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    collect(projectId, child, results, threshold, unused, limit);
                }
            }
            return;
        }
        if (file.length() >= threshold) {
            results.add(repository.entry(projectId, repository.relativePath(projectId, file)));
        }
    }

    private void collectOld(@NonNull String projectId, @NonNull File file,
                            @NonNull List<FileEntry> results, long cutoff, int limit)
            throws FileManagerException {
        if (results.size() >= limit) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectOld(projectId, child, results, cutoff, limit);
                }
            }
            return;
        }
        if (file.lastModified() > 0 && file.lastModified() < cutoff) {
            results.add(repository.entry(projectId, repository.relativePath(projectId, file)));
        }
    }

    private void collectEmpty(@NonNull String projectId, @NonNull File file,
                              @NonNull List<FileEntry> results) throws FileManagerException {
        if (!file.isDirectory()) {
            return;
        }
        File[] children = file.listFiles();
        if (children == null || children.length == 0) {
            if (!file.equals(repository.projectDir(projectId))) {
                results.add(repository.entry(projectId,
                        repository.relativePath(projectId, file)));
            }
            return;
        }
        for (File child : children) {
            collectEmpty(projectId, child, results);
        }
    }

    private void collectBySize(@NonNull File file, @NonNull Map<Long, List<File>> bySize) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectBySize(child, bySize);
                }
            }
            return;
        }
        List<File> bucket = bySize.get(file.length());
        if (bucket == null) {
            bucket = new ArrayList<>();
            bySize.put(file.length(), bucket);
        }
        bucket.add(file);
    }

    private static String sha256(@NonNull File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte value : digest.digest()) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException error) {
            return null;
        }
    }

    @NonNull
    public static String categoryLabel(@NonNull String category) {
        switch (category) {
            case FileCategory.IMAGE:
                return "Images";
            case FileCategory.VIDEO:
                return "Videos";
            case FileCategory.AUDIO:
                return "Audio";
            case FileCategory.DOCUMENT:
                return "Documents";
            case FileCategory.ARCHIVE:
                return "Archives";
            default:
                return "Other";
        }
    }
}
