package com.roni.library.contracts.camera;

/** Flash / torch behaviour. {@link #TORCH} keeps the light on while previewing. */
public enum CameraFlashMode {
    OFF,
    ON,
    AUTO,
    TORCH;

    public static CameraFlashMode fromName(String value) {
        if (value == null) {
            return OFF;
        }
        for (CameraFlashMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return OFF;
    }
}
