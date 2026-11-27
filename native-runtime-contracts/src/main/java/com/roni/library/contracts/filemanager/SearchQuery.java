package com.roni.library.contracts.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded search request; the engine never performs an unrestricted recursive scan. */
public final class SearchQuery {

    public static final String SORT_NAME = "name";
    public static final String SORT_SIZE = "size";
    public static final String SORT_DATE = "date";
    public static final String SORT_TYPE = "type";

    private final String name;
    private final String extension;
    private final String mimeType;
    private final String category;
    private final long minSizeBytes;
    private final long maxSizeBytes;
    private final long modifiedAfter;
    private final long modifiedBefore;
    private final boolean includeHidden;
    private final int limit;
    private final String sortBy;
    private final boolean descending;

    private SearchQuery(Builder builder) {
        this.name = builder.name;
        this.extension = builder.extension;
        this.mimeType = builder.mimeType;
        this.category = builder.category;
        this.minSizeBytes = builder.minSizeBytes;
        this.maxSizeBytes = builder.maxSizeBytes;
        this.modifiedAfter = builder.modifiedAfter;
        this.modifiedBefore = builder.modifiedBefore;
        this.includeHidden = builder.includeHidden;
        this.limit = builder.limit;
        this.sortBy = builder.sortBy;
        this.descending = builder.descending;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getExtension() {
        return extension;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }

    @NonNull
    public String getCategory() {
        return category;
    }

    public long getMinSizeBytes() {
        return minSizeBytes;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public long getModifiedAfter() {
        return modifiedAfter;
    }

    public long getModifiedBefore() {
        return modifiedBefore;
    }

    public boolean isIncludeHidden() {
        return includeHidden;
    }

    public int getLimit() {
        return limit;
    }

    @NonNull
    public String getSortBy() {
        return sortBy;
    }

    public boolean isDescending() {
        return descending;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("extension", extension);
        map.put("mimeType", mimeType);
        map.put("category", category);
        map.put("minSizeBytes", minSizeBytes);
        map.put("maxSizeBytes", maxSizeBytes);
        map.put("modifiedAfter", modifiedAfter);
        map.put("modifiedBefore", modifiedBefore);
        map.put("includeHidden", includeHidden);
        map.put("limit", limit);
        map.put("sortBy", sortBy);
        map.put("descending", descending);
        return map;
    }

    @NonNull
    public static SearchQuery fromMap(@Nullable Map<String, Object> map) {
        if (map == null) {
            return new Builder().build();
        }
        return new Builder()
                .name(string(map.get("name")))
                .extension(string(map.get("extension")))
                .mimeType(string(map.get("mimeType")))
                .category(string(map.get("category")))
                .minSizeBytes(number(map.get("minSizeBytes")))
                .maxSizeBytes(number(map.get("maxSizeBytes")))
                .modifiedAfter(number(map.get("modifiedAfter")))
                .modifiedBefore(number(map.get("modifiedBefore")))
                .includeHidden(Boolean.TRUE.equals(map.get("includeHidden")))
                .limit((int) number(map.get("limit")))
                .sortBy(string(map.get("sortBy")))
                .descending(Boolean.TRUE.equals(map.get("descending")))
                .build();
    }

    private static String string(@Nullable Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static long number(@Nullable Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    public static final class Builder {
        private String name = "";
        private String extension = "";
        private String mimeType = "";
        private String category = "";
        private long minSizeBytes;
        private long maxSizeBytes;
        private long modifiedAfter;
        private long modifiedBefore;
        private boolean includeHidden;
        private int limit = 100;
        private String sortBy = SORT_NAME;
        private boolean descending;

        @NonNull
        public Builder name(@Nullable String value) {
            this.name = value != null ? value.trim() : "";
            return this;
        }

        @NonNull
        public Builder extension(@Nullable String value) {
            this.extension = value != null ? value.trim() : "";
            return this;
        }

        @NonNull
        public Builder mimeType(@Nullable String value) {
            this.mimeType = value != null ? value.trim() : "";
            return this;
        }

        @NonNull
        public Builder category(@Nullable String value) {
            this.category = value != null ? value.trim() : "";
            return this;
        }

        @NonNull
        public Builder minSizeBytes(long value) {
            this.minSizeBytes = value;
            return this;
        }

        @NonNull
        public Builder maxSizeBytes(long value) {
            this.maxSizeBytes = value;
            return this;
        }

        @NonNull
        public Builder modifiedAfter(long value) {
            this.modifiedAfter = value;
            return this;
        }

        @NonNull
        public Builder modifiedBefore(long value) {
            this.modifiedBefore = value;
            return this;
        }

        @NonNull
        public Builder includeHidden(boolean value) {
            this.includeHidden = value;
            return this;
        }

        @NonNull
        public Builder limit(int value) {
            this.limit = value;
            return this;
        }

        @NonNull
        public Builder sortBy(@Nullable String value) {
            this.sortBy = value != null && !value.isEmpty() ? value : SORT_NAME;
            return this;
        }

        @NonNull
        public Builder descending(boolean value) {
            this.descending = value;
            return this;
        }

        @NonNull
        public SearchQuery build() {
            return new SearchQuery(this);
        }
    }
}
