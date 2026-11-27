package com.roni.library.contracts.notification;

/** Stable error codes returned by the notification engine and the bridge route. */
public final class NotificationErrors {

    public static final String INVALID_REQUEST = "NOTIFICATION_INVALID_REQUEST";
    public static final String INVALID_KEY = "NOTIFICATION_INVALID_KEY";
    public static final String INVALID_TITLE = "NOTIFICATION_INVALID_TITLE";
    public static final String INVALID_BODY = "NOTIFICATION_INVALID_BODY";
    public static final String INVALID_STYLE = "NOTIFICATION_INVALID_STYLE";
    public static final String INVALID_IMAGE = "NOTIFICATION_INVALID_IMAGE";
    public static final String INVALID_DEEP_LINK = "NOTIFICATION_INVALID_DEEP_LINK";
    public static final String INVALID_ACTION = "NOTIFICATION_INVALID_ACTION";
    public static final String TOO_MANY_ACTIONS = "NOTIFICATION_TOO_MANY_ACTIONS";
    public static final String TOO_MANY_LINES = "NOTIFICATION_TOO_MANY_LINES";
    public static final String PAYLOAD_TOO_LARGE = "NOTIFICATION_PAYLOAD_TOO_LARGE";
    public static final String PERMISSION_DENIED = "NOTIFICATION_PERMISSION_DENIED";
    public static final String PROJECT_BLOCKED = "NOTIFICATION_PROJECT_BLOCKED";
    public static final String PROJECT_MUTED = "NOTIFICATION_PROJECT_MUTED";
    public static final String CHANNEL_BLOCKED = "NOTIFICATION_CHANNEL_BLOCKED";
    public static final String RATE_LIMITED = "NOTIFICATION_RATE_LIMITED";
    public static final String NOT_FOUND = "NOTIFICATION_NOT_FOUND";
    public static final String FORBIDDEN = "NOTIFICATION_FORBIDDEN";
    public static final String SCHEDULE_LIMIT = "NOTIFICATION_SCHEDULE_LIMIT";
    public static final String SCHEDULE_INVALID = "NOTIFICATION_SCHEDULE_INVALID";
    public static final String ENGINE_UNAVAILABLE = "NOTIFICATION_ENGINE_UNAVAILABLE";
    public static final String DISABLED = "NOTIFICATION_DISABLED";

    private NotificationErrors() {
    }
}
