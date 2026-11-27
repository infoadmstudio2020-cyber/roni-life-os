package com.roni.library.contracts.notification;

/**
 * Rendering style of a notification.
 *
 * <p>Every style maps to a standard Android notification template; the engine never invents a
 * custom RemoteViews layout, so the system keeps full control over appearance and theming.</p>
 */
public enum NotificationStyle {

    /** Title + short body. */
    BASIC,

    /** Collapsed single line that expands into a long text block. */
    BIG_TEXT,

    /** Collapsed single line that expands into a large image. */
    BIG_PICTURE,

    /** Collapsed single line that expands into a list of lines. */
    INBOX,

    /** Determinate or indeterminate progress bar. */
    PROGRESS,

    /** Group summary that aggregates the members of one group. */
    GROUP_SUMMARY;

    /**
     * @param value raw value from a bridge payload
     * @return the matching style, or {@link #BASIC} when the value is unknown
     */
    public static NotificationStyle parse(String value) {
        if (value == null) {
            return BASIC;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.US);
        for (NotificationStyle style : values()) {
            if (style.name().equals(normalized)) {
                return style;
            }
        }
        return BASIC;
    }
}
