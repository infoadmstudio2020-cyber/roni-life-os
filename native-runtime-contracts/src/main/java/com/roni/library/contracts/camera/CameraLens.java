package com.roni.library.contracts.camera;

/** Physical camera facing used by a capture session. */
public enum CameraLens {
    BACK,
    FRONT,
    EXTERNAL;

    public static CameraLens fromName(String value) {
        if (value == null) {
            return BACK;
        }
        for (CameraLens lens : values()) {
            if (lens.name().equalsIgnoreCase(value.trim())) {
                return lens;
            }
        }
        return BACK;
    }
}
