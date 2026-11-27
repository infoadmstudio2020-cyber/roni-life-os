package com.roni.library.contracts.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Success/failure envelope used across the file manager engine. */
public final class FileManagerResult<T> {

    @Nullable
    private final T value;
    @Nullable
    private final FileManagerError error;

    private FileManagerResult(@Nullable T value, @Nullable FileManagerError error) {
        this.value = value;
        this.error = error;
    }

    @NonNull
    public static <T> FileManagerResult<T> ok(@Nullable T value) {
        return new FileManagerResult<>(value, null);
    }

    @NonNull
    public static <T> FileManagerResult<T> fail(@NonNull FileManagerError error) {
        return new FileManagerResult<>(null, error);
    }

    @NonNull
    public static <T> FileManagerResult<T> fail(@NonNull String code, @Nullable String message) {
        return new FileManagerResult<>(null, new FileManagerError(code, message));
    }

    public boolean isSuccess() {
        return error == null;
    }

    @Nullable
    public T getValue() {
        return value;
    }

    @Nullable
    public FileManagerError getError() {
        return error;
    }
}
