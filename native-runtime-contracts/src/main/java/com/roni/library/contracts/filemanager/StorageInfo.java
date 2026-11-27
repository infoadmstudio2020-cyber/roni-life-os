package com.roni.library.contracts.filemanager;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/** Device and project storage snapshot. */
public final class StorageInfo {

    private final long totalBytes;
    private final long freeBytes;
    private final long projectBytes;
    private final long appBytes;
    private final Map<String, Long> categories;

    public StorageInfo(long totalBytes, long freeBytes, long projectBytes, long appBytes,
                       @NonNull Map<String, Long> categories) {
        this.totalBytes = totalBytes;
        this.freeBytes = freeBytes;
        this.projectBytes = projectBytes;
        this.appBytes = appBytes;
        this.categories = categories;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getFreeBytes() {
        return freeBytes;
    }

    public long getUsedBytes() {
        return Math.max(0L, totalBytes - freeBytes);
    }

    public long getProjectBytes() {
        return projectBytes;
    }

    public long getAppBytes() {
        return appBytes;
    }

    @NonNull
    public Map<String, Long> getCategories() {
        return categories;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalBytes", totalBytes);
        map.put("freeBytes", freeBytes);
        map.put("usedBytes", getUsedBytes());
        map.put("projectBytes", projectBytes);
        map.put("appBytes", appBytes);
        map.put("categories", new LinkedHashMap<>(categories));
        return map;
    }
}
