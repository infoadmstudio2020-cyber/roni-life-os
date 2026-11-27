package com.roni.library.contracts.camera;

/** Stable error codes shared by the Java API and the {@code /camera} bridge route. */
public final class CameraErrorCodes {

    public static final String UNSUPPORTED = "CAMERA_UNSUPPORTED";
    public static final String PERMISSION_DENIED = "CAMERA_PERMISSION_DENIED";
    public static final String MIC_PERMISSION_DENIED = "CAMERA_MIC_PERMISSION_DENIED";
    public static final String BUSY = "CAMERA_BUSY";
    public static final String NOT_OPEN = "CAMERA_NOT_OPEN";
    public static final String ALREADY_RECORDING = "CAMERA_ALREADY_RECORDING";
    public static final String NOT_RECORDING = "CAMERA_NOT_RECORDING";
    public static final String INVALID_REQUEST = "CAMERA_INVALID_REQUEST";
    public static final String INVALID_KEY = "CAMERA_INVALID_KEY";
    public static final String INVALID_LENS = "CAMERA_INVALID_LENS";
    public static final String INVALID_FLASH = "CAMERA_INVALID_FLASH";
    public static final String INVALID_ZOOM = "CAMERA_INVALID_ZOOM";
    public static final String INVALID_FOCUS = "CAMERA_INVALID_FOCUS";
    public static final String INVALID_EXPOSURE = "CAMERA_INVALID_EXPOSURE";
    public static final String INVALID_RESOLUTION = "CAMERA_INVALID_RESOLUTION";
    public static final String UNSUPPORTED_FEATURE = "CAMERA_UNSUPPORTED_FEATURE";
    public static final String CAPTURE_FAILED = "CAMERA_CAPTURE_FAILED";
    public static final String RECORDING_FAILED = "CAMERA_RECORDING_FAILED";
    public static final String STORAGE_FULL = "CAMERA_STORAGE_FULL";
    public static final String MEDIA_NOT_FOUND = "CAMERA_MEDIA_NOT_FOUND";
    public static final String FORBIDDEN = "CAMERA_FORBIDDEN";
    public static final String NO_ACTIVE_PROJECT = "CAMERA_NO_ACTIVE_PROJECT";
    public static final String ENGINE_UNAVAILABLE = "CAMERA_ENGINE_UNAVAILABLE";
    public static final String DISABLED = "CAMERA_DISABLED";
    public static final String INTERRUPTED = "CAMERA_INTERRUPTED";

    private CameraErrorCodes() {
    }
}
