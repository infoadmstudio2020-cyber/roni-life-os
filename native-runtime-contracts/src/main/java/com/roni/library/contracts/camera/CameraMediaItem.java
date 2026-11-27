package com.roni.library.contracts.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata for one captured photo or video.
 *
 * <p>{@code relativePath} is always relative to the project media root. Absolute filesystem paths
 * are never exposed to web pages.</p>
 */
public final class CameraMediaItem {

    public static final String TYPE_PHOTO = "PHOTO";
    public static final String TYPE_VIDEO = "VIDEO";

    public static final String STATE_READY = "READY";
    public static final String STATE_INTERRUPTED = "INTERRUPTED";
    public static final String STATE_DELETED = "DELETED";

    private final String id;
    private final String projectId;
    private final String type;
    private final String fileName;
    private final String relativePath;
    private final String thumbnailPath;
    private final String mimeType;
    private final long sizeBytes;
    private final int width;
    private final int height;
    private final long durationMs;
    private final long createdAt;
    private final String state;

    private CameraMediaItem(Builder builder) {
        this.id = builder.id;
        this.projectId = builder.projectId;
        this.type = builder.type;
        this.fileName = builder.fileName;
        this.relativePath = builder.relativePath;
        this.thumbnailPath = builder.thumbnailPath;
        this.mimeType = builder.mimeType;
        this.sizeBytes = builder.sizeBytes;
        this.width = builder.width;
        this.height = builder.height;
        this.durationMs = builder.durationMs;
        this.createdAt = builder.createdAt;
        this.state = builder.state;
    }

    @NonNull
    public static CameraMediaItem fromMap(@Nullable Map<String, Object> map) {
        Builder builder = new Builder();
        if (map == null) {
            return builder.build();
        }
        builder.id(string(map.get("id"), ""));
        builder.projectId(string(map.get("projectId"), ""));
        builder.type(string(map.get("type"), TYPE_PHOTO));
        builder.fileName(string(map.get("fileName"), ""));
        builder.relativePath(string(map.get("relativePath"), ""));
        builder.thumbnailPath(string(map.get("thumbnailPath"), ""));
        builder.mimeType(string(map.get("mimeType"), "image/jpeg"));
        builder.sizeBytes(longValue(map.get("sizeBytes"), 0L));
        builder.size(intValue(map.get("width"), 0), intValue(map.get("height"), 0));
        builder.durationMs(longValue(map.get("durationMs"), 0L));
        builder.createdAt(longValue(map.get("createdAt"), 0L));
        builder.state(string(map.get("state"), STATE_READY));
        return builder.build();
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getProjectId() {
        return projectId;
    }

    @NonNull
    public String getType() {
        return type;
    }

    @NonNull
    public String getFileName() {
        return fileName;
    }

    @NonNull
    public String getRelativePath() {
        return relativePath;
    }

    @NonNull
    public String getThumbnailPath() {
        return thumbnailPath;
    }

    @NonNull
    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @NonNull
    public String getState() {
        return state;
    }

    public boolean isVideo() {
        return TYPE_VIDEO.equals(type);
    }

    public boolean isReady() {
        return STATE_READY.equals(state);
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("projectId", projectId);
        map.put("type", type);
        map.put("fileName", fileName);
        map.put("relativePath", relativePath);
        map.put("thumbnailPath", thumbnailPath);
        map.put("mimeType", mimeType);
        map.put("sizeBytes", sizeBytes);
        map.put("width", width);
        map.put("height", height);
        map.put("durationMs", durationMs);
        map.put("createdAt", createdAt);
        map.put("state", state);
        return map;
    }

    @NonNull
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .projectId(projectId)
                .type(type)
                .fileName(fileName)
                .relativePath(relativePath)
                .thumbnailPath(thumbnailPath)
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .size(width, height)
                .durationMs(durationMs)
                .createdAt(createdAt)
                .state(state);
    }

    private static String string(Object value, String fallback) {
        return value instanceof String && !((String) value).isEmpty() ? (String) value : fallback;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static long longValue(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    /** Mutable builder. */
    public static final class Builder {

        private String id = "";
        private String projectId = "";
        private String type = TYPE_PHOTO;
        private String fileName = "";
        private String relativePath = "";
        private String thumbnailPath = "";
        private String mimeType = "image/jpeg";
        private long sizeBytes;
        private int width;
        private int height;
        private long durationMs;
        private long createdAt;
        private String state = STATE_READY;

        @NonNull
        public Builder id(@Nullable String value) {
            this.id = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder projectId(@Nullable String value) {
            this.projectId = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder type(@Nullable String value) {
            this.type = value != null && !value.isEmpty() ? value : TYPE_PHOTO;
            return this;
        }

        @NonNull
        public Builder fileName(@Nullable String value) {
            this.fileName = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder relativePath(@Nullable String value) {
            this.relativePath = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder thumbnailPath(@Nullable String value) {
            this.thumbnailPath = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder mimeType(@Nullable String value) {
            this.mimeType = value != null && !value.isEmpty() ? value : "image/jpeg";
            return this;
        }

        @NonNull
        public Builder sizeBytes(long value) {
            this.sizeBytes = value;
            return this;
        }

        @NonNull
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        @NonNull
        public Builder durationMs(long value) {
            this.durationMs = value;
            return this;
        }

        @NonNull
        public Builder createdAt(long value) {
            this.createdAt = value;
            return this;
        }

        @NonNull
        public Builder state(@Nullable String value) {
            this.state = value != null && !value.isEmpty() ? value : STATE_READY;
            return this;
        }

        @NonNull
        public CameraMediaItem build() {
            return new CameraMediaItem(this);
        }
    }
}
