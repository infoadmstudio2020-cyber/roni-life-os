package com.roni.library.contracts.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** One file or folder as seen by the explorer, the analyzer and the bridge. */
public final class FileEntry {

    private final String id;
    private final String name;
    private final String relativePath;
    private final boolean directory;
    private final long sizeBytes;
    private final long modifiedAt;
    private final String mimeType;
    private final String category;
    private final boolean hidden;
    private final int childCount;
    private final String provider;

    private FileEntry(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.relativePath = builder.relativePath;
        this.directory = builder.directory;
        this.sizeBytes = builder.sizeBytes;
        this.modifiedAt = builder.modifiedAt;
        this.mimeType = builder.mimeType;
        this.category = builder.category;
        this.hidden = builder.hidden;
        this.childCount = builder.childCount;
        this.provider = builder.provider;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getRelativePath() {
        return relativePath;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }

    @NonNull
    public String getCategory() {
        return category;
    }

    public boolean isHidden() {
        return hidden;
    }

    public int getChildCount() {
        return childCount;
    }

    @NonNull
    public String getProvider() {
        return provider;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("relativePath", relativePath);
        map.put("directory", directory);
        map.put("sizeBytes", sizeBytes);
        map.put("modifiedAt", modifiedAt);
        map.put("mimeType", mimeType);
        map.put("category", category);
        map.put("hidden", hidden);
        map.put("childCount", childCount);
        map.put("provider", provider);
        return map;
    }

    @NonNull
    public static FileEntry fromMap(@Nullable Map<String, Object> map) {
        if (map == null) {
            return new Builder().build();
        }
        return new Builder()
                .id(string(map.get("id")))
                .name(string(map.get("name")))
                .relativePath(string(map.get("relativePath")))
                .directory(Boolean.TRUE.equals(map.get("directory")))
                .sizeBytes(number(map.get("sizeBytes")))
                .modifiedAt(number(map.get("modifiedAt")))
                .mimeType(string(map.get("mimeType")))
                .category(string(map.get("category")))
                .hidden(Boolean.TRUE.equals(map.get("hidden")))
                .childCount((int) number(map.get("childCount")))
                .provider(string(map.get("provider")))
                .build();
    }

    private static String string(@Nullable Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static long number(@Nullable Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    public static final class Builder {
        private String id = "";
        private String name = "";
        private String relativePath = "";
        private boolean directory;
        private long sizeBytes;
        private long modifiedAt;
        private String mimeType = "application/octet-stream";
        private String category = FileCategory.OTHER;
        private boolean hidden;
        private int childCount;
        private String provider = "internal";

        @NonNull
        public Builder id(@Nullable String value) {
            this.id = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder name(@Nullable String value) {
            this.name = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder relativePath(@Nullable String value) {
            this.relativePath = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder directory(boolean value) {
            this.directory = value;
            return this;
        }

        @NonNull
        public Builder sizeBytes(long value) {
            this.sizeBytes = value;
            return this;
        }

        @NonNull
        public Builder modifiedAt(long value) {
            this.modifiedAt = value;
            return this;
        }

        @NonNull
        public Builder mimeType(@Nullable String value) {
            this.mimeType = value != null && !value.isEmpty()
                    ? value : "application/octet-stream";
            return this;
        }

        @NonNull
        public Builder category(@Nullable String value) {
            this.category = value != null && !value.isEmpty() ? value : FileCategory.OTHER;
            return this;
        }

        @NonNull
        public Builder hidden(boolean value) {
            this.hidden = value;
            return this;
        }

        @NonNull
        public Builder childCount(int value) {
            this.childCount = value;
            return this;
        }

        @NonNull
        public Builder provider(@Nullable String value) {
            this.provider = value != null && !value.isEmpty() ? value : "internal";
            return this;
        }

        @NonNull
        public FileEntry build() {
            return new FileEntry(this);
        }
    }
}
