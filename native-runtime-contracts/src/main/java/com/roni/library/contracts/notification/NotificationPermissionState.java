package com.roni.library.contracts.notification;

/** Runtime notification permission state as seen by the engine. */
public enum NotificationPermissionState {

    /** The user granted the runtime permission (or the OS does not require one). */
    GRANTED,

    /** The user denied the runtime permission. */
    DENIED,

    /** The OS version does not require a runtime permission (API &lt; 33). */
    NOT_REQUIRED,

    /** The state could not be determined. */
    UNKNOWN;

    public boolean canPost() {
        return this == GRANTED || this == NOT_REQUIRED;
    }
}
