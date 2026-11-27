package com.roni.library.platform.camera;

import androidx.annotation.NonNull;

import com.roni.library.contracts.camera.CameraError;
import com.roni.library.contracts.camera.CameraMediaItem;
import com.roni.library.contracts.camera.CameraState;

/** Host callbacks. All methods are optional. */
public interface CameraEventListener {

    default void onStateChanged(@NonNull CameraState state) {
    }

    default void onPhotoSaved(@NonNull CameraMediaItem item) {
    }

    default void onRecordingStarted() {
    }

    default void onRecordingProgress(long durationMs, long sizeBytes) {
    }

    default void onRecordingStopped(@NonNull CameraMediaItem item) {
    }

    default void onError(@NonNull CameraError error) {
    }
}
