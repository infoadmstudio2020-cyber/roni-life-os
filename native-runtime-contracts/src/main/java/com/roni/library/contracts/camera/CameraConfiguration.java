package com.roni.library.contracts.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.api.NativeApiError;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Desired camera configuration. Every field is optional; {@link #validate(CameraCapabilities)}
 * rejects values the device cannot honour instead of silently degrading.
 */
public final class CameraConfiguration {

    private final CameraLens lens;
    private final CameraFlashMode flashMode;
    private final String aspectRatio;
    private final int jpegQuality;
    private final int photoWidth;
    private final int photoHeight;
    private final int videoWidth;
    private final int videoHeight;
    private final int videoFrameRate;
    private final boolean hdr;
    private final boolean gridEnabled;
    private final boolean levelEnabled;
    private final boolean proMode;
    private final int iso;
    private final long shutterNs;
    private final float focusDistance;
    private final String whiteBalanceMode;
    private final float exposureCompensation;

    private CameraConfiguration(Builder builder) {
        this.lens = builder.lens;
        this.flashMode = builder.flashMode;
        this.aspectRatio = builder.aspectRatio;
        this.jpegQuality = builder.jpegQuality;
        this.photoWidth = builder.photoWidth;
        this.photoHeight = builder.photoHeight;
        this.videoWidth = builder.videoWidth;
        this.videoHeight = builder.videoHeight;
        this.videoFrameRate = builder.videoFrameRate;
        this.hdr = builder.hdr;
        this.gridEnabled = builder.gridEnabled;
        this.levelEnabled = builder.levelEnabled;
        this.proMode = builder.proMode;
        this.iso = builder.iso;
        this.shutterNs = builder.shutterNs;
        this.focusDistance = builder.focusDistance;
        this.whiteBalanceMode = builder.whiteBalanceMode;
        this.exposureCompensation = builder.exposureCompensation;
    }

    @NonNull
    public static CameraConfiguration defaults() {
        return new Builder().build();
    }

    /** Parses a bridge payload; unknown keys are ignored, malformed values fall back to defaults. */
    @NonNull
    public static CameraConfiguration fromMap(@Nullable Map<String, Object> map) {
        Builder builder = new Builder();
        if (map == null) {
            return builder.build();
        }
        builder.lens(CameraLens.fromName(string(map.get("lens"), "BACK")));
        builder.flashMode(CameraFlashMode.fromName(string(map.get("flash"), "OFF")));
        builder.aspectRatio(string(map.get("aspectRatio"), "4:3"));
        builder.jpegQuality(intValue(map.get("quality"), 95));
        builder.photoSize(intValue(map.get("width"), 0), intValue(map.get("height"), 0));
        builder.videoSize(intValue(map.get("videoWidth"), 0), intValue(map.get("videoHeight"), 0));
        builder.videoFrameRate(intValue(map.get("frameRate"), 30));
        builder.hdr(bool(map.get("hdr"), false));
        builder.grid(bool(map.get("grid"), false));
        builder.level(bool(map.get("level"), false));
        builder.proMode(bool(map.get("proMode"), false));
        builder.iso(intValue(map.get("iso"), 0));
        builder.shutterNs(longValue(map.get("shutterNs"), 0L));
        builder.focusDistance(floatValue(map.get("focusDistance"), 0f));
        builder.whiteBalanceMode(string(map.get("whiteBalance"), "AUTO"));
        builder.exposureCompensation(floatValue(map.get("exposure"), 0f));
        return builder.build();
    }

    @NonNull
    public CameraLens getLens() {
        return lens;
    }

    @NonNull
    public CameraFlashMode getFlashMode() {
        return flashMode;
    }

    @NonNull
    public String getAspectRatio() {
        return aspectRatio;
    }

    public int getJpegQuality() {
        return jpegQuality;
    }

    public int getPhotoWidth() {
        return photoWidth;
    }

    public int getPhotoHeight() {
        return photoHeight;
    }

    public int getVideoWidth() {
        return videoWidth;
    }

    public int getVideoHeight() {
        return videoHeight;
    }

    public int getVideoFrameRate() {
        return videoFrameRate;
    }

    public boolean isHdr() {
        return hdr;
    }

    public boolean isGridEnabled() {
        return gridEnabled;
    }

    public boolean isLevelEnabled() {
        return levelEnabled;
    }

    public boolean isProMode() {
        return proMode;
    }

    public int getIso() {
        return iso;
    }

    public long getShutterNs() {
        return shutterNs;
    }

    public float getFocusDistance() {
        return focusDistance;
    }

    @NonNull
    public String getWhiteBalanceMode() {
        return whiteBalanceMode;
    }

    public float getExposureCompensation() {
        return exposureCompensation;
    }

    /** @return {@code null} when the configuration is valid for this device. */
    @Nullable
    public NativeApiError validate(@NonNull CameraCapabilities capabilities) {
        if (!capabilities.isSupported()) {
            return new NativeApiError(CameraErrorCodes.UNSUPPORTED,
                    "This device does not expose a usable camera");
        }
        if (!capabilities.supportsLens(lens)) {
            return new NativeApiError(CameraErrorCodes.INVALID_LENS,
                    "Lens " + lens.name() + " is not available on this device");
        }
        if (!capabilities.supportsFlash(flashMode)) {
            return new NativeApiError(CameraErrorCodes.INVALID_FLASH,
                    "Flash mode " + flashMode.name() + " is not supported");
        }
        if (jpegQuality < CameraLimits.MIN_JPEG_QUALITY
                || jpegQuality > CameraLimits.MAX_JPEG_QUALITY) {
            return new NativeApiError(CameraErrorCodes.INVALID_REQUEST,
                    "JPEG quality must be between " + CameraLimits.MIN_JPEG_QUALITY
                            + " and " + CameraLimits.MAX_JPEG_QUALITY);
        }
        if (photoWidth < 0 || photoHeight < 0 || videoWidth < 0 || videoHeight < 0) {
            return new NativeApiError(CameraErrorCodes.INVALID_RESOLUTION,
                    "Resolution values cannot be negative");
        }
        if (photoWidth > capabilities.getMaxPhotoWidth()
                || photoHeight > capabilities.getMaxPhotoHeight()) {
            return new NativeApiError(CameraErrorCodes.INVALID_RESOLUTION,
                    "Requested photo size exceeds the sensor maximum");
        }
        if (videoWidth > 0 && videoHeight > 0
                && !capabilities.getVideoResolutions().contains(videoWidth + "x" + videoHeight)) {
            return new NativeApiError(CameraErrorCodes.INVALID_RESOLUTION,
                    "Video size " + videoWidth + "x" + videoHeight + " is not supported");
        }
        if (videoFrameRate <= 0 || videoFrameRate > 120) {
            return new NativeApiError(CameraErrorCodes.INVALID_RESOLUTION,
                    "Frame rate must be between 1 and 120");
        }
        if (iso != 0) {
            if (!capabilities.isManualIsoSupported()) {
                return new NativeApiError(CameraErrorCodes.UNSUPPORTED_FEATURE,
                        "Manual ISO is not supported by this camera");
            }
            if (iso < capabilities.getMinIso() || iso > capabilities.getMaxIso()) {
                return new NativeApiError(CameraErrorCodes.INVALID_EXPOSURE,
                        "ISO must be between " + capabilities.getMinIso()
                                + " and " + capabilities.getMaxIso());
            }
        }
        if (shutterNs != 0) {
            if (!capabilities.isManualShutterSupported()) {
                return new NativeApiError(CameraErrorCodes.UNSUPPORTED_FEATURE,
                        "Manual shutter speed is not supported by this camera");
            }
            if (shutterNs < capabilities.getMinShutterNs()
                    || shutterNs > capabilities.getMaxShutterNs()) {
                return new NativeApiError(CameraErrorCodes.INVALID_EXPOSURE,
                        "Shutter speed is outside the supported range");
            }
        }
        if (focusDistance != 0f) {
            if (!capabilities.isManualFocusSupported()) {
                return new NativeApiError(CameraErrorCodes.UNSUPPORTED_FEATURE,
                        "Manual focus is not supported by this camera");
            }
            if (focusDistance < 0f || focusDistance > 1f) {
                return new NativeApiError(CameraErrorCodes.INVALID_FOCUS,
                        "Focus distance must be a dioptre value between 0 and 1");
            }
        }
        if (!"AUTO".equalsIgnoreCase(whiteBalanceMode)
                && !capabilities.getWhiteBalanceModes().contains(whiteBalanceMode)) {
            return new NativeApiError(CameraErrorCodes.UNSUPPORTED_FEATURE,
                    "White balance mode " + whiteBalanceMode + " is not supported");
        }
        if (exposureCompensation != 0f) {
            if (!capabilities.isExposureCompensationSupported()) {
                return new NativeApiError(CameraErrorCodes.UNSUPPORTED_FEATURE,
                        "Exposure compensation is not supported by this camera");
            }
            if (exposureCompensation < capabilities.getMinExposureCompensation()
                    || exposureCompensation > capabilities.getMaxExposureCompensation()) {
                return new NativeApiError(CameraErrorCodes.INVALID_EXPOSURE,
                        "Exposure compensation is outside the supported range");
            }
        }
        return null;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lens", lens.name());
        map.put("flash", flashMode.name());
        map.put("aspectRatio", aspectRatio);
        map.put("quality", jpegQuality);
        map.put("width", photoWidth);
        map.put("height", photoHeight);
        map.put("videoWidth", videoWidth);
        map.put("videoHeight", videoHeight);
        map.put("frameRate", videoFrameRate);
        map.put("hdr", hdr);
        map.put("grid", gridEnabled);
        map.put("level", levelEnabled);
        map.put("proMode", proMode);
        map.put("iso", iso);
        map.put("shutterNs", shutterNs);
        map.put("focusDistance", focusDistance);
        map.put("whiteBalance", whiteBalanceMode);
        map.put("exposure", exposureCompensation);
        return map;
    }

    @NonNull
    public Builder toBuilder() {
        return new Builder()
                .lens(lens)
                .flashMode(flashMode)
                .aspectRatio(aspectRatio)
                .jpegQuality(jpegQuality)
                .photoSize(photoWidth, photoHeight)
                .videoSize(videoWidth, videoHeight)
                .videoFrameRate(videoFrameRate)
                .hdr(hdr)
                .grid(gridEnabled)
                .level(levelEnabled)
                .proMode(proMode)
                .iso(iso)
                .shutterNs(shutterNs)
                .focusDistance(focusDistance)
                .whiteBalanceMode(whiteBalanceMode)
                .exposureCompensation(exposureCompensation);
    }

    private static String string(Object value, String fallback) {
        return value instanceof String && !((String) value).isEmpty() ? (String) value : fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static float floatValue(Object value, float fallback) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        if (value instanceof String) {
            try {
                return Float.parseFloat(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return fallback;
    }

    /** Mutable builder. */
    public static final class Builder {

        private CameraLens lens = CameraLens.BACK;
        private CameraFlashMode flashMode = CameraFlashMode.OFF;
        private String aspectRatio = "4:3";
        private int jpegQuality = 95;
        private int photoWidth;
        private int photoHeight;
        private int videoWidth;
        private int videoHeight;
        private int videoFrameRate = 30;
        private boolean hdr;
        private boolean gridEnabled;
        private boolean levelEnabled;
        private boolean proMode;
        private int iso;
        private long shutterNs;
        private float focusDistance;
        private String whiteBalanceMode = "AUTO";
        private float exposureCompensation;

        @NonNull
        public Builder lens(@Nullable CameraLens value) {
            this.lens = value != null ? value : CameraLens.BACK;
            return this;
        }

        @NonNull
        public Builder flashMode(@Nullable CameraFlashMode value) {
            this.flashMode = value != null ? value : CameraFlashMode.OFF;
            return this;
        }

        @NonNull
        public Builder aspectRatio(@Nullable String value) {
            this.aspectRatio = value != null && !value.isEmpty() ? value : "4:3";
            return this;
        }

        @NonNull
        public Builder jpegQuality(int value) {
            this.jpegQuality = value;
            return this;
        }

        @NonNull
        public Builder photoSize(int width, int height) {
            this.photoWidth = width;
            this.photoHeight = height;
            return this;
        }

        @NonNull
        public Builder videoSize(int width, int height) {
            this.videoWidth = width;
            this.videoHeight = height;
            return this;
        }

        @NonNull
        public Builder videoFrameRate(int value) {
            this.videoFrameRate = value;
            return this;
        }

        @NonNull
        public Builder hdr(boolean value) {
            this.hdr = value;
            return this;
        }

        @NonNull
        public Builder grid(boolean value) {
            this.gridEnabled = value;
            return this;
        }

        @NonNull
        public Builder level(boolean value) {
            this.levelEnabled = value;
            return this;
        }

        @NonNull
        public Builder proMode(boolean value) {
            this.proMode = value;
            return this;
        }

        @NonNull
        public Builder iso(int value) {
            this.iso = value;
            return this;
        }

        @NonNull
        public Builder shutterNs(long value) {
            this.shutterNs = value;
            return this;
        }

        @NonNull
        public Builder focusDistance(float value) {
            this.focusDistance = value;
            return this;
        }

        @NonNull
        public Builder whiteBalanceMode(@Nullable String value) {
            this.whiteBalanceMode = value != null && !value.isEmpty() ? value : "AUTO";
            return this;
        }

        @NonNull
        public Builder exposureCompensation(float value) {
            this.exposureCompensation = value;
            return this;
        }

        @NonNull
        public CameraConfiguration build() {
            return new CameraConfiguration(this);
        }
    }
}
