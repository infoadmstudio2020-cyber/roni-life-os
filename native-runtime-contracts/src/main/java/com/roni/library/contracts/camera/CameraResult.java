package com.roni.library.contracts.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Small success/failure wrapper used by the Java camera API. */
public final class CameraResult<T> {

    private final T value;
    private final CameraError error;

    private CameraResult(@Nullable T value, @Nullable CameraError error) {
        this.value = value;
        this.error = error;
    }

    @NonNull
    public static <T> CameraResult<T> ok(@Nullable T value) {
        return new CameraResult<>(value, null);
    }

    @NonNull
    public static <T> CameraResult<T> fail(@NonNull CameraError error) {
        return new CameraResult<>(null, error);
    }

    @NonNull
    public static <T> CameraResult<T> fail(@NonNull String code, @NonNull String message) {
        return new CameraResult<>(null, new CameraError(code, message));
    }

    public boolean isSuccess() {
        return error == null;
    }

    @Nullable
    public T getValue() {
        return value;
    }

    @Nullable
    public CameraError getError() {
        return error;
    }
}
