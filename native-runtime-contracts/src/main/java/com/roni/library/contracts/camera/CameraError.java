package com.roni.library.contracts.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Structured camera failure returned by the Java API and the bridge. */
public final class CameraError {

    private final String code;
    private final String message;
    private final String details;

    public CameraError(@NonNull String code, @NonNull String message) {
        this(code, message, null);
    }

    public CameraError(@NonNull String code, @NonNull String message, @Nullable String details) {
        this.code = code;
        this.message = message;
        this.details = details;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @Nullable
    public String getDetails() {
        return details;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("message", message);
        if (details != null) {
            map.put("details", details);
        }
        return map;
    }

    @NonNull
    @Override
    public String toString() {
        return code + ": " + message + (details != null ? " (" + details + ")" : "");
    }
}
