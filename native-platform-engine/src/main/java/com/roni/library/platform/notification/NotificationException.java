package com.roni.library.platform.notification;

import androidx.annotation.NonNull;

import com.roni.library.contracts.api.NativeApiError;

/** Checked failure raised by {@link NotificationService}; carries a stable error code. */
public final class NotificationException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String code;

    public NotificationException(@NonNull String code, @NonNull String message) {
        super(message);
        this.code = code;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    public NativeApiError toApiError() {
        return new NativeApiError(code, getMessage() != null ? getMessage() : code);
    }
}
