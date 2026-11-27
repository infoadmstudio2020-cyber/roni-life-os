package com.roni.library.contracts.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Structured failure returned by the file manager engine. */
public final class FileManagerError {

    private final String code;
    private final String message;

    public FileManagerError(@NonNull String code, @Nullable String message) {
        this.code = code;
        this.message = message != null ? message : "";
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("message", message);
        return map;
    }
}
