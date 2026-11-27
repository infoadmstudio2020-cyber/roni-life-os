package com.roni.library.contracts.notification;

/**
 * Hard limits applied to every notification request.
 *
 * <p>They exist to bound memory, prevent notification spam and keep a hostile web page from
 * turning the notification shade into a denial-of-service surface.</p>
 */
public final class NotificationLimits {

    /** Maximum length of a notification key. */
    public static final int MAX_KEY_LENGTH = 64;

    /** Maximum title length. */
    public static final int MAX_TITLE_LENGTH = 120;

    /** Maximum body length. */
    public static final int MAX_BODY_LENGTH = 1000;

    /** Maximum expanded text length. */
    public static final int MAX_BIG_TEXT_LENGTH = 2000;

    /** Maximum image URL length. */
    public static final int MAX_IMAGE_URL_LENGTH = 2048;

    /** Maximum number of inbox lines. */
    public static final int MAX_INBOX_LINES = 7;

    /** Maximum length of one inbox line. */
    public static final int MAX_INBOX_LINE_LENGTH = 200;

    /** Maximum number of action buttons. */
    public static final int MAX_ACTIONS = 3;

    /** Maximum action title length. */
    public static final int MAX_ACTION_TITLE_LENGTH = 40;

    /** Maximum deep-link length. */
    public static final int MAX_DEEP_LINK_LENGTH = 512;

    /** Maximum group key length. */
    public static final int MAX_GROUP_KEY_LENGTH = 64;

    /** Maximum channel id length. */
    public static final int MAX_CHANNEL_ID_LENGTH = 64;

    /** Maximum number of retained history records. */
    public static final int MAX_HISTORY = 200;

    /** Maximum number of pending scheduled notifications. */
    public static final int MAX_SCHEDULED = 50;

    /** Sliding window used by the per-project rate limiter. */
    public static final long RATE_WINDOW_MS = 60_000L;

    /** Maximum notifications a single project may post inside {@link #RATE_WINDOW_MS}. */
    public static final int RATE_MAX_PER_WINDOW = 20;

    /** Minimum delay accepted for a scheduled notification. */
    public static final long MIN_SCHEDULE_DELAY_MS = 1_000L;

    /** Maximum delay accepted for a scheduled notification (30 days). */
    public static final long MAX_SCHEDULE_DELAY_MS = 30L * 24L * 60L * 60L * 1000L;

    private NotificationLimits() {
    }
}
