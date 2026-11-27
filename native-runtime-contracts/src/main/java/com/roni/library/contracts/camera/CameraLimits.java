package com.roni.library.contracts.camera;

/** Hard limits enforced before any request reaches the hardware. */
public final class CameraLimits {

    public static final int MAX_KEY_LENGTH = 64;
    public static final int MAX_BURST_COUNT = 10;
    public static final long MAX_SELF_TIMER_MS = 30_000L;
    public static final long MAX_VIDEO_DURATION_MS = 30L * 60L * 1000L;
    public static final long MAX_VIDEO_SIZE_BYTES = 2L * 1024L * 1024L * 1024L;
    public static final int MIN_JPEG_QUALITY = 40;
    public static final int MAX_JPEG_QUALITY = 100;
    public static final float MAX_ZOOM = 10f;
    public static final int MAX_MEDIA_ITEMS = 500;
    public static final int MAX_PAGE_SIZE = 100;
    public static final long MIN_FREE_SPACE_BYTES = 32L * 1024L * 1024L;
    public static final int MAX_THUMBNAIL_SIZE = 512;

    private CameraLimits() {
    }
}
