package com.roni.library.contracts.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable description of what the current device can actually do.
 *
 * <p>The engine never exposes a control that is not advertised here, so a web page can build its
 * UI from this object instead of guessing.</p>
 */
public final class CameraCapabilities {

    private final boolean supported;
    private final List<CameraLens> lenses;
    private final List<String> aspectRatios;
    private final int maxPhotoWidth;
    private final int maxPhotoHeight;
    private final boolean zoomSupported;
    private final float maxZoom;
    private final List<CameraFlashMode> flashModes;
    private final boolean hdrSupported;
    private final boolean manualIsoSupported;
    private final int minIso;
    private final int maxIso;
    private final boolean manualShutterSupported;
    private final long minShutterNs;
    private final long maxShutterNs;
    private final boolean manualFocusSupported;
    private final float minFocusDistance;
    private final boolean whiteBalanceSupported;
    private final List<String> whiteBalanceModes;
    private final boolean exposureCompensationSupported;
    private final float minExposureCompensation;
    private final float maxExposureCompensation;
    private final float exposureCompensationStep;
    private final boolean histogramSupported;
    private final boolean videoSupported;
    private final List<String> videoResolutions;
    private final List<Integer> videoFrameRates;
    private final boolean stabilizationSupported;
    private final boolean audioSupported;

    private CameraCapabilities(Builder builder) {
        this.supported = builder.supported;
        this.lenses = Collections.unmodifiableList(new ArrayList<>(builder.lenses));
        this.aspectRatios = Collections.unmodifiableList(new ArrayList<>(builder.aspectRatios));
        this.maxPhotoWidth = builder.maxPhotoWidth;
        this.maxPhotoHeight = builder.maxPhotoHeight;
        this.zoomSupported = builder.zoomSupported;
        this.maxZoom = builder.maxZoom;
        this.flashModes = Collections.unmodifiableList(new ArrayList<>(builder.flashModes));
        this.hdrSupported = builder.hdrSupported;
        this.manualIsoSupported = builder.manualIsoSupported;
        this.minIso = builder.minIso;
        this.maxIso = builder.maxIso;
        this.manualShutterSupported = builder.manualShutterSupported;
        this.minShutterNs = builder.minShutterNs;
        this.maxShutterNs = builder.maxShutterNs;
        this.manualFocusSupported = builder.manualFocusSupported;
        this.minFocusDistance = builder.minFocusDistance;
        this.whiteBalanceSupported = builder.whiteBalanceSupported;
        this.whiteBalanceModes = Collections.unmodifiableList(new ArrayList<>(builder.whiteBalanceModes));
        this.exposureCompensationSupported = builder.exposureCompensationSupported;
        this.minExposureCompensation = builder.minExposureCompensation;
        this.maxExposureCompensation = builder.maxExposureCompensation;
        this.exposureCompensationStep = builder.exposureCompensationStep;
        this.histogramSupported = builder.histogramSupported;
        this.videoSupported = builder.videoSupported;
        this.videoResolutions = Collections.unmodifiableList(new ArrayList<>(builder.videoResolutions));
        this.videoFrameRates = Collections.unmodifiableList(new ArrayList<>(builder.videoFrameRates));
        this.stabilizationSupported = builder.stabilizationSupported;
        this.audioSupported = builder.audioSupported;
    }

    /** Capabilities of a device without a usable camera. */
    @NonNull
    public static CameraCapabilities unsupported() {
        return new Builder().supported(false).build();
    }

    public boolean isSupported() {
        return supported;
    }

    @NonNull
    public List<CameraLens> getLenses() {
        return lenses;
    }

    @NonNull
    public List<String> getAspectRatios() {
        return aspectRatios;
    }

    public int getMaxPhotoWidth() {
        return maxPhotoWidth;
    }

    public int getMaxPhotoHeight() {
        return maxPhotoHeight;
    }

    public boolean isZoomSupported() {
        return zoomSupported;
    }

    public float getMaxZoom() {
        return maxZoom;
    }

    @NonNull
    public List<CameraFlashMode> getFlashModes() {
        return flashModes;
    }

    public boolean isHdrSupported() {
        return hdrSupported;
    }

    public boolean isManualIsoSupported() {
        return manualIsoSupported;
    }

    public int getMinIso() {
        return minIso;
    }

    public int getMaxIso() {
        return maxIso;
    }

    public boolean isManualShutterSupported() {
        return manualShutterSupported;
    }

    public long getMinShutterNs() {
        return minShutterNs;
    }

    public long getMaxShutterNs() {
        return maxShutterNs;
    }

    public boolean isManualFocusSupported() {
        return manualFocusSupported;
    }

    public float getMinFocusDistance() {
        return minFocusDistance;
    }

    public boolean isWhiteBalanceSupported() {
        return whiteBalanceSupported;
    }

    @NonNull
    public List<String> getWhiteBalanceModes() {
        return whiteBalanceModes;
    }

    public boolean isExposureCompensationSupported() {
        return exposureCompensationSupported;
    }

    public float getMinExposureCompensation() {
        return minExposureCompensation;
    }

    public float getMaxExposureCompensation() {
        return maxExposureCompensation;
    }

    public float getExposureCompensationStep() {
        return exposureCompensationStep;
    }

    public boolean isHistogramSupported() {
        return histogramSupported;
    }

    public boolean isVideoSupported() {
        return videoSupported;
    }

    @NonNull
    public List<String> getVideoResolutions() {
        return videoResolutions;
    }

    @NonNull
    public List<Integer> getVideoFrameRates() {
        return videoFrameRates;
    }

    public boolean isStabilizationSupported() {
        return stabilizationSupported;
    }

    public boolean isAudioSupported() {
        return audioSupported;
    }

    public boolean supportsLens(@NonNull CameraLens lens) {
        return lenses.contains(lens);
    }

    public boolean supportsFlash(@NonNull CameraFlashMode mode) {
        return flashModes.contains(mode);
    }

    /** Bridge-friendly representation. */
    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("supported", supported);
        List<String> lensNames = new ArrayList<>();
        for (CameraLens lens : lenses) {
            lensNames.add(lens.name());
        }
        map.put("lenses", lensNames);
        map.put("aspectRatios", new ArrayList<>(aspectRatios));
        map.put("maxPhotoWidth", maxPhotoWidth);
        map.put("maxPhotoHeight", maxPhotoHeight);
        map.put("zoomSupported", zoomSupported);
        map.put("maxZoom", maxZoom);
        List<String> flashes = new ArrayList<>();
        for (CameraFlashMode mode : flashModes) {
            flashes.add(mode.name());
        }
        map.put("flashModes", flashes);
        map.put("hdrSupported", hdrSupported);
        map.put("manualIsoSupported", manualIsoSupported);
        map.put("isoRange", new int[]{minIso, maxIso});
        map.put("manualShutterSupported", manualShutterSupported);
        map.put("shutterRangeNs", new long[]{minShutterNs, maxShutterNs});
        map.put("manualFocusSupported", manualFocusSupported);
        map.put("minFocusDistance", minFocusDistance);
        map.put("whiteBalanceSupported", whiteBalanceSupported);
        map.put("whiteBalanceModes", new ArrayList<>(whiteBalanceModes));
        map.put("exposureCompensationSupported", exposureCompensationSupported);
        map.put("exposureCompensationRange", new float[]{
                minExposureCompensation, maxExposureCompensation});
        map.put("exposureCompensationStep", exposureCompensationStep);
        map.put("histogramSupported", histogramSupported);
        map.put("videoSupported", videoSupported);
        map.put("videoResolutions", new ArrayList<>(videoResolutions));
        map.put("videoFrameRates", new ArrayList<>(videoFrameRates));
        map.put("stabilizationSupported", stabilizationSupported);
        map.put("audioSupported", audioSupported);
        return map;
    }

    /** Mutable builder used by the platform capability detector. */
    public static final class Builder {

        private boolean supported = true;
        private final List<CameraLens> lenses = new ArrayList<>();
        private final List<String> aspectRatios = new ArrayList<>();
        private int maxPhotoWidth;
        private int maxPhotoHeight;
        private boolean zoomSupported;
        private float maxZoom = 1f;
        private final List<CameraFlashMode> flashModes = new ArrayList<>();
        private boolean hdrSupported;
        private boolean manualIsoSupported;
        private int minIso;
        private int maxIso;
        private boolean manualShutterSupported;
        private long minShutterNs;
        private long maxShutterNs;
        private boolean manualFocusSupported;
        private float minFocusDistance;
        private boolean whiteBalanceSupported;
        private final List<String> whiteBalanceModes = new ArrayList<>();
        private boolean exposureCompensationSupported;
        private float minExposureCompensation;
        private float maxExposureCompensation;
        private float exposureCompensationStep;
        private boolean histogramSupported;
        private boolean videoSupported;
        private final List<String> videoResolutions = new ArrayList<>();
        private final List<Integer> videoFrameRates = new ArrayList<>();
        private boolean stabilizationSupported;
        private boolean audioSupported;

        @NonNull
        public Builder supported(boolean value) {
            this.supported = value;
            return this;
        }

        @NonNull
        public Builder addLens(@Nullable CameraLens lens) {
            if (lens != null && !lenses.contains(lens)) {
                lenses.add(lens);
            }
            return this;
        }

        @NonNull
        public Builder addAspectRatio(@Nullable String ratio) {
            if (ratio != null && !ratio.isEmpty() && !aspectRatios.contains(ratio)) {
                aspectRatios.add(ratio);
            }
            return this;
        }

        @NonNull
        public Builder maxPhotoSize(int width, int height) {
            this.maxPhotoWidth = width;
            this.maxPhotoHeight = height;
            return this;
        }

        @NonNull
        public Builder zoom(boolean supported, float max) {
            this.zoomSupported = supported;
            this.maxZoom = max;
            return this;
        }

        @NonNull
        public Builder addFlashMode(@Nullable CameraFlashMode mode) {
            if (mode != null && !flashModes.contains(mode)) {
                flashModes.add(mode);
            }
            return this;
        }

        @NonNull
        public Builder hdr(boolean value) {
            this.hdrSupported = value;
            return this;
        }

        @NonNull
        public Builder iso(boolean supported, int min, int max) {
            this.manualIsoSupported = supported;
            this.minIso = min;
            this.maxIso = max;
            return this;
        }

        @NonNull
        public Builder shutter(boolean supported, long minNs, long maxNs) {
            this.manualShutterSupported = supported;
            this.minShutterNs = minNs;
            this.maxShutterNs = maxNs;
            return this;
        }

        @NonNull
        public Builder manualFocus(boolean supported, float minDistance) {
            this.manualFocusSupported = supported;
            this.minFocusDistance = minDistance;
            return this;
        }

        @NonNull
        public Builder whiteBalance(boolean supported, @Nullable List<String> modes) {
            this.whiteBalanceSupported = supported;
            if (modes != null) {
                for (String mode : modes) {
                    if (mode != null && !mode.isEmpty() && !whiteBalanceModes.contains(mode)) {
                        whiteBalanceModes.add(mode);
                    }
                }
            }
            return this;
        }

        @NonNull
        public Builder exposureCompensation(boolean supported, float min, float max, float step) {
            this.exposureCompensationSupported = supported;
            this.minExposureCompensation = min;
            this.maxExposureCompensation = max;
            this.exposureCompensationStep = step;
            return this;
        }

        @NonNull
        public Builder histogram(boolean value) {
            this.histogramSupported = value;
            return this;
        }

        @NonNull
        public Builder video(boolean supported, @Nullable List<String> resolutions,
                             @Nullable List<Integer> frameRates) {
            this.videoSupported = supported;
            if (resolutions != null) {
                for (String resolution : resolutions) {
                    if (resolution != null && !resolution.isEmpty()
                            && !videoResolutions.contains(resolution)) {
                        videoResolutions.add(resolution);
                    }
                }
            }
            if (frameRates != null) {
                for (Integer rate : frameRates) {
                    if (rate != null && rate > 0 && !videoFrameRates.contains(rate)) {
                        videoFrameRates.add(rate);
                    }
                }
            }
            return this;
        }

        @NonNull
        public Builder stabilization(boolean value) {
            this.stabilizationSupported = value;
            return this;
        }

        @NonNull
        public Builder audio(boolean value) {
            this.audioSupported = value;
            return this;
        }

        @NonNull
        public CameraCapabilities build() {
            return new CameraCapabilities(this);
        }
    }
}
