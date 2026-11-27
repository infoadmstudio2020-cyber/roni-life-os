package com.roni.library.contracts.notification;

/**
 * Delivery priority of a notification.
 *
 * <p>The rank drives both the Android priority (pre-O) and the channel importance (O+), so a
 * single value produces consistent behaviour on every supported API level.</p>
 */
public enum NotificationPriority {

    MIN(1),
    LOW(2),
    DEFAULT(3),
    HIGH(4),
    MAX(5);

    private final int rank;

    NotificationPriority(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    /**
     * @param value raw value from a bridge payload
     * @return the matching priority, or {@link #DEFAULT} when the value is unknown
     */
    public static NotificationPriority parse(String value) {
        if (value == null) {
            return DEFAULT;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.US);
        for (NotificationPriority priority : values()) {
            if (priority.name().equals(normalized)) {
                return priority;
            }
        }
        return DEFAULT;
    }
}
