package com.roni.library.platform.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.camera.CameraError;

/** Typed engine failure carrying a stable {@link CameraError} code. */
public class CameraException extends Exception {

    private final String code;

    public CameraException(@NonNull String code, @NonNull String message) {
        this(code, message, null);
    }

    public CameraException(@NonNull String code, @NonNull String message,
                           @Nullable Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    public CameraError toError() {
        return new CameraError(code, getMessage() != null ? getMessage() : code);
    }
}
