package com.roni.library.platform.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.automation.AutomationErrorCodes;

/** Checked failure carrying a stable automation error code. */
public final class AutomationException extends Exception {

    private final String code;

    public AutomationException(@NonNull String code, @NonNull String message) {
        super(message);
        this.code = code;
    }

    public AutomationException(@NonNull String code, @NonNull String message,
                               @Nullable Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    public static AutomationException invalid(@NonNull String message) {
        return new AutomationException(AutomationErrorCodes.INVALID_REQUEST, message);
    }

    @NonNull
    public static AutomationException notFound(@NonNull String message) {
        return new AutomationException(AutomationErrorCodes.TASK_NOT_FOUND, message);
    }

    @NonNull
    public static AutomationException io(@NonNull String message, @Nullable Throwable cause) {
        return new AutomationException(AutomationErrorCodes.IO_ERROR, message, cause);
    }
}
