package com.roni.library.contracts.filemanager;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/** One entry inside a ZIP archive. */
public final class ArchiveEntry {

    private final String name;
    private final long sizeBytes;
    private final long compressedBytes;
    private final boolean directory;
    private final long modifiedAt;

    public ArchiveEntry(@NonNull String name, long sizeBytes, long compressedBytes,
                        boolean directory, long modifiedAt) {
        this.name = name;
        this.sizeBytes = sizeBytes;
        this.compressedBytes = compressedBytes;
        this.directory = directory;
        this.modifiedAt = modifiedAt;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getCompressedBytes() {
        return compressedBytes;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("sizeBytes", sizeBytes);
        map.put("compressedBytes", compressedBytes);
        map.put("directory", directory);
        map.put("modifiedAt", modifiedAt);
        return map;
    }
}
