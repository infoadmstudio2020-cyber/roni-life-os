package com.roni.library.platform.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.filemanager.FileManagerError;
import com.roni.library.contracts.filemanager.FileManagerErrorCodes;

/** Checked failure raised by the file manager engine. */
public final class FileManagerException extends Exception {

    private final String code;

    public FileManagerException(@NonNull String code, @Nullable String message) {
        super(message);
        this.code = code;
    }

    public FileManagerException(@NonNull String code, @Nullable String message,
                                @Nullable Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    public FileManagerError toError() {
        return new FileManagerError(code, getMessage());
    }

    @NonNull
    public static FileManagerException invalidPath(@Nullable String detail) {
        return new FileManagerException(FileManagerErrorCodes.INVALID_PATH, detail);
    }

    @NonNull
    public static FileManagerException notFound(@Nullable String detail) {
        return new FileManagerException(FileManagerErrorCodes.NOT_FOUND, detail);
    }

    @NonNull
    public static FileManagerException io(@Nullable String detail, @Nullable Throwable cause) {
        return new FileManagerException(FileManagerErrorCodes.IO_ERROR, detail, cause);
    }
}
