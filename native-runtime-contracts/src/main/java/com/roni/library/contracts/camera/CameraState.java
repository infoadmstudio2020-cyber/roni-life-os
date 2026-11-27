package com.roni.library.contracts.camera;

/** Lifecycle of the camera engine. */
public enum CameraState {
    IDLE,
    OPENING,
    PREVIEW,
    CAPTURING,
    RECORDING,
    PAUSED,
    CLOSED,
    ERROR;

    public boolean isOpen() {
        return this == PREVIEW || this == CAPTURING || this == RECORDING || this == PAUSED;
    }

    public boolean isRecording() {
        return this == RECORDING || this == PAUSED;
    }
}
