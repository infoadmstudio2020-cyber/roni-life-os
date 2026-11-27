package com.roni.library.contracts.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.api.NativeApiError;

import java.util.LinkedHashMap;
import java.util.Map;

/** A single photo capture instruction (optionally a burst or a self-timer shot). */
public final class PhotoCaptureRequest {

    private final String key;
    private final long selfTimerMs;
    private final int burstCount;
    private final int jpegQuality;
    private final int width;
    private final int height;
    private final boolean includeMetadata;

    private PhotoCaptureRequest(Builder builder) {
        this.key = builder.key;
        this.selfTimerMs = builder.selfTimerMs;
        this.burstCount = builder.burstCount;
        this.jpegQuality = builder.jpegQuality;
        this.width = builder.width;
        this.height = builder.height;
        this.includeMetadata = builder.includeMetadata;
    }

    @NonNull
    public static PhotoCaptureRequest fromMap(@Nullable Map<String, Object> map) {
        Builder builder = new Builder();
        if (map == null) {
            return builder.build();
        }
        builder.key(string(map.get("key"), "photo"));
        builder.selfTimerMs(longValue(map.get("selfTimerMs"), 0L));
        builder.burstCount(intValue(map.get("burst"), 1));
        builder.jpegQuality(intValue(map.get("quality"), 95));
        builder.size(intValue(map.get("width"), 0), intValue(map.get("height"), 0));
        builder.includeMetadata(bool(map.get("metadata"), true));
        return builder.build();
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public long getSelfTimerMs() {
        return selfTimerMs;
    }

    public int getBurstCount() {
        return burstCount;
    }

    public int getJpegQuality() {
        return jpegQuality;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    /** @return {@code null} when the request is acceptable. */
    @Nullable
    public NativeApiError validate() {
        if (key.isEmpty() || key.length() > CameraLimits.MAX_KEY_LENGTH) {
            return new NativeApiError(CameraErrorCodes.INVALID_KEY,
                    "key must be 1-" + CameraLimits.MAX_KEY_LENGTH + " characters");
        }
        if (selfTimerMs < 0 || selfTimerMs > CameraLimits.MAX_SELF_TIMER_MS) {
            return new NativeApiError(CameraErrorCodes.INVALID_REQUEST,
                    "selfTimerMs must be between 0 and " + CameraLimits.MAX_SELF_TIMER_MS);
        }
        if (burstCount < 1 || burstCount > CameraLimits.MAX_BURST_COUNT) {
            return new NativeApiError(CameraErrorCodes.INVALID_REQUEST,
                    "burst must be between 1 and " + CameraLimits.MAX_BURST_COUNT);
        }
        if (jpegQuality < CameraLimits.MIN_JPEG_QUALITY
                || jpegQuality > CameraLimits.MAX_JPEG_QUALITY) {
            return new NativeApiError(CameraErrorCodes.INVALID_REQUEST,
                    "quality must be between " + CameraLimits.MIN_JPEG_QUALITY
                            + " and " + CameraLimits.MAX_JPEG_QUALITY);
        }
        if (width < 0 || height < 0) {
            return new NativeApiError(CameraErrorCodes.INVALID_RESOLUTION,
                    "width and height cannot be negative");
        }
        return null;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", key);
        map.put("selfTimerMs", selfTimerMs);
        map.put("burst", burstCount);
        map.put("quality", jpegQuality);
        map.put("width", width);
        map.put("height", height);
        map.put("metadata", includeMetadata);
        return map;
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

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    /** Mutable builder. */
    public static final class Builder {

        private String key = "photo";
        private long selfTimerMs;
        private int burstCount = 1;
        private int jpegQuality = 95;
        private int width;
        private int height;
        private boolean includeMetadata = true;

        @NonNull
        public Builder key(@Nullable String value) {
            this.key = value != null && !value.isEmpty() ? value : "photo";
            return this;
        }

        @NonNull
        public Builder selfTimerMs(long value) {
            this.selfTimerMs = value;
            return this;
        }

        @NonNull
        public Builder burstCount(int value) {
            this.burstCount = value;
            return this;
        }

        @NonNull
        public Builder jpegQuality(int value) {
            this.jpegQuality = value;
            return this;
        }

        @NonNull
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        @NonNull
        public Builder includeMetadata(boolean value) {
            this.includeMetadata = value;
            return this;
        }

        @NonNull
        public PhotoCaptureRequest build() {
            return new PhotoCaptureRequest(this);
        }
    }
}
