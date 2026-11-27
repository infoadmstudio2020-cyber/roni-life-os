package com.roni.library.contracts.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.api.NativeApiError;

import java.util.LinkedHashMap;
import java.util.Map;

/** A video recording instruction. Audio is only requested when {@code withAudio} is true. */
public final class VideoRecordingRequest {

    private final String key;
    private final int width;
    private final int height;
    private final int frameRate;
    private final boolean withAudio;
    private final long maxDurationMs;
    private final long maxFileSizeBytes;

    private VideoRecordingRequest(Builder builder) {
        this.key = builder.key;
        this.width = builder.width;
        this.height = builder.height;
        this.frameRate = builder.frameRate;
        this.withAudio = builder.withAudio;
        this.maxDurationMs = builder.maxDurationMs;
        this.maxFileSizeBytes = builder.maxFileSizeBytes;
    }

    @NonNull
    public static VideoRecordingRequest fromMap(@Nullable Map<String, Object> map) {
        Builder builder = new Builder();
        if (map == null) {
            return builder.build();
        }
        builder.key(string(map.get("key"), "video"));
        builder.size(intValue(map.get("width"), 0), intValue(map.get("height"), 0));
        builder.frameRate(intValue(map.get("frameRate"), 30));
        builder.withAudio(bool(map.get("audio"), true));
        builder.maxDurationMs(longValue(map.get("maxDurationMs"), 0L));
        builder.maxFileSizeBytes(longValue(map.get("maxFileSizeBytes"), 0L));
        return builder.build();
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public boolean isWithAudio() {
        return withAudio;
    }

    public long getMaxDurationMs() {
        return maxDurationMs;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    /** @return {@code null} when the request is acceptable. */
    @Nullable
    public NativeApiError validate() {
        if (key.isEmpty() || key.length() > CameraLimits.MAX_KEY_LENGTH) {
            return new NativeApiError(CameraErrorCodes.INVALID_KEY,
                    "key must be 1-" + CameraLimits.MAX_KEY_LENGTH + " characters");
        }
        if (width < 0 || height < 0) {
            return new NativeApiError(CameraErrorCodes.INVALID_RESOLUTION,
                    "width and height cannot be negative");
        }
        if (frameRate <= 0 || frameRate > 120) {
            return new NativeApiError(CameraErrorCodes.INVALID_RESOLUTION,
                    "frameRate must be between 1 and 120");
        }
        if (maxDurationMs < 0 || maxDurationMs > CameraLimits.MAX_VIDEO_DURATION_MS) {
            return new NativeApiError(CameraErrorCodes.INVALID_REQUEST,
                    "maxDurationMs must be between 0 and " + CameraLimits.MAX_VIDEO_DURATION_MS);
        }
        if (maxFileSizeBytes < 0 || maxFileSizeBytes > CameraLimits.MAX_VIDEO_SIZE_BYTES) {
            return new NativeApiError(CameraErrorCodes.INVALID_REQUEST,
                    "maxFileSizeBytes exceeds the supported maximum");
        }
        return null;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", key);
        map.put("width", width);
        map.put("height", height);
        map.put("frameRate", frameRate);
        map.put("audio", withAudio);
        map.put("maxDurationMs", maxDurationMs);
        map.put("maxFileSizeBytes", maxFileSizeBytes);
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

        private String key = "video";
        private int width;
        private int height;
        private int frameRate = 30;
        private boolean withAudio = true;
        private long maxDurationMs;
        private long maxFileSizeBytes;

        @NonNull
        public Builder key(@Nullable String value) {
            this.key = value != null && !value.isEmpty() ? value : "video";
            return this;
        }

        @NonNull
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        @NonNull
        public Builder frameRate(int value) {
            this.frameRate = value;
            return this;
        }

        @NonNull
        public Builder withAudio(boolean value) {
            this.withAudio = value;
            return this;
        }

        @NonNull
        public Builder maxDurationMs(long value) {
            this.maxDurationMs = value;
            return this;
        }

        @NonNull
        public Builder maxFileSizeBytes(long value) {
            this.maxFileSizeBytes = value;
            return this;
        }

        @NonNull
        public VideoRecordingRequest build() {
            return new VideoRecordingRequest(this);
        }
    }
}
