package com.roni.library.contracts.notification;

/** Lock-screen visibility of a notification's content. */
public enum NotificationVisibility {

    SECRET(0),
    PRIVATE(1),
    PUBLIC(2);

    private final int rank;

    NotificationVisibility(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    public static NotificationVisibility parse(String value) {
        if (value == null) {
            return PRIVATE;
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.US);
        for (NotificationVisibility visibility : values()) {
            if (visibility.name().equals(normalized)) {
                return visibility;
            }
        }
        return PRIVATE;
    }
}
