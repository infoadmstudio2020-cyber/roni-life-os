package com.roni.library.contracts.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.api.NativeApiError;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Immutable, validated description of one notification.
 *
 * <p>This is the only shape the engine accepts. A web page never builds an Android
 * {@code Notification}: it sends a request, the native side validates it, stamps the owning
 * project id and only then hands it to the platform backend.</p>
 *
 * <p>{@link #validate()} is pure Java and returns a {@link NativeApiError} (or {@code null} when
 * the request is acceptable), which keeps the whole validation surface unit-testable without an
 * Android device.</p>
 */
public final class NotificationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern ACTION_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,32}");

    private final String projectId;
    private final String key;
    private final String title;
    private final String body;
    private final NotificationStyle style;
    private final String bigText;
    private final String imageUrl;
    private final List<String> inboxLines;
    private final int progress;
    private final boolean indeterminate;
    private final boolean ongoing;
    private final boolean autoCancel;
    private final String groupKey;
    private final boolean groupSummary;
    private final String channelId;
    private final NotificationPriority priority;
    private final NotificationVisibility visibility;
    private final boolean sound;
    private final boolean vibration;
    private final List<NotificationActionSpec> actions;
    private final String deepLink;
    private final long scheduledAt;
    private final long ttlMs;

    private NotificationRequest(Builder builder) {
        this.projectId = builder.projectId;
        this.key = builder.key;
        this.title = builder.title;
        this.body = builder.body;
        this.style = builder.style;
        this.bigText = builder.bigText;
        this.imageUrl = builder.imageUrl;
        this.inboxLines = Collections.unmodifiableList(new ArrayList<>(builder.inboxLines));
        this.progress = builder.progress;
        this.indeterminate = builder.indeterminate;
        this.ongoing = builder.ongoing;
        this.autoCancel = builder.autoCancel;
        this.groupKey = builder.groupKey;
        this.groupSummary = builder.groupSummary;
        this.channelId = builder.channelId;
        this.priority = builder.priority;
        this.visibility = builder.visibility;
        this.sound = builder.sound;
        this.vibration = builder.vibration;
        this.actions = Collections.unmodifiableList(new ArrayList<>(builder.actions));
        this.deepLink = builder.deepLink;
        this.scheduledAt = builder.scheduledAt;
        this.ttlMs = builder.ttlMs;
    }

    @NonNull
    public String getProjectId() {
        return projectId;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getBody() {
        return body;
    }

    @NonNull
    public NotificationStyle getStyle() {
        return style;
    }

    @NonNull
    public String getBigText() {
        return bigText;
    }

    @NonNull
    public String getImageUrl() {
        return imageUrl;
    }

    @NonNull
    public List<String> getInboxLines() {
        return inboxLines;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    public boolean isOngoing() {
        return ongoing;
    }

    public boolean isAutoCancel() {
        return autoCancel;
    }

    @NonNull
    public String getGroupKey() {
        return groupKey;
    }

    public boolean isGroupSummary() {
        return groupSummary;
    }

    @NonNull
    public String getChannelId() {
        return channelId;
    }

    @NonNull
    public NotificationPriority getPriority() {
        return priority;
    }

    @NonNull
    public NotificationVisibility getVisibility() {
        return visibility;
    }

    public boolean isSound() {
        return sound;
    }

    public boolean isVibration() {
        return vibration;
    }

    @NonNull
    public List<NotificationActionSpec> getActions() {
        return actions;
    }

    @NonNull
    public String getDeepLink() {
        return deepLink;
    }

    public long getScheduledAt() {
        return scheduledAt;
    }

    public long getTtlMs() {
        return ttlMs;
    }

    public boolean isScheduled() {
        return scheduledAt > 0L;
    }

    /**
     * Validates the request against {@link NotificationLimits}.
     *
     * @return {@code null} when the request is acceptable, otherwise a structured error
     */
    @Nullable
    public NativeApiError validate() {
        if (projectId.trim().isEmpty()) {
            return error(NotificationErrors.INVALID_REQUEST, "projectId is required");
        }
        if (key.isEmpty() || !KEY_PATTERN.matcher(key).matches()) {
            return error(NotificationErrors.INVALID_KEY,
                    "key must match [A-Za-z0-9._-] and be 1-" + NotificationLimits.MAX_KEY_LENGTH + " chars");
        }
        if (title.trim().isEmpty()) {
            return error(NotificationErrors.INVALID_TITLE, "title is required");
        }
        if (title.length() > NotificationLimits.MAX_TITLE_LENGTH) {
            return error(NotificationErrors.INVALID_TITLE,
                    "title exceeds " + NotificationLimits.MAX_TITLE_LENGTH + " chars");
        }
        if (body.length() > NotificationLimits.MAX_BODY_LENGTH) {
            return error(NotificationErrors.INVALID_BODY,
                    "body exceeds " + NotificationLimits.MAX_BODY_LENGTH + " chars");
        }
        if (bigText.length() > NotificationLimits.MAX_BIG_TEXT_LENGTH) {
            return error(NotificationErrors.PAYLOAD_TOO_LARGE,
                    "bigText exceeds " + NotificationLimits.MAX_BIG_TEXT_LENGTH + " chars");
        }
        if (channelId.length() > NotificationLimits.MAX_CHANNEL_ID_LENGTH) {
            return error(NotificationErrors.INVALID_REQUEST,
                    "channelId exceeds " + NotificationLimits.MAX_CHANNEL_ID_LENGTH + " chars");
        }
        if (groupKey.length() > NotificationLimits.MAX_GROUP_KEY_LENGTH) {
            return error(NotificationErrors.INVALID_REQUEST,
                    "groupKey exceeds " + NotificationLimits.MAX_GROUP_KEY_LENGTH + " chars");
        }
        if (deepLink.length() > NotificationLimits.MAX_DEEP_LINK_LENGTH) {
            return error(NotificationErrors.INVALID_DEEP_LINK,
                    "deepLink exceeds " + NotificationLimits.MAX_DEEP_LINK_LENGTH + " chars");
        }
        NativeApiError linkError = validateDeepLink(deepLink);
        if (linkError != null) {
            return linkError;
        }
        if (style == NotificationStyle.BIG_PICTURE) {
            if (imageUrl.isEmpty()) {
                return error(NotificationErrors.INVALID_IMAGE, "imageUrl is required for BIG_PICTURE");
            }
            if (imageUrl.length() > NotificationLimits.MAX_IMAGE_URL_LENGTH) {
                return error(NotificationErrors.INVALID_IMAGE,
                        "imageUrl exceeds " + NotificationLimits.MAX_IMAGE_URL_LENGTH + " chars");
            }
            String lower = imageUrl.toLowerCase(Locale.US);
            if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
                return error(NotificationErrors.INVALID_IMAGE, "imageUrl must be http(s)");
            }
        }
        if (style == NotificationStyle.INBOX) {
            if (inboxLines.isEmpty()) {
                return error(NotificationErrors.INVALID_REQUEST, "inboxLines is required for INBOX");
            }
            if (inboxLines.size() > NotificationLimits.MAX_INBOX_LINES) {
                return error(NotificationErrors.TOO_MANY_LINES,
                        "inboxLines exceeds " + NotificationLimits.MAX_INBOX_LINES);
            }
            for (String line : inboxLines) {
                if (line != null && line.length() > NotificationLimits.MAX_INBOX_LINE_LENGTH) {
                    return error(NotificationErrors.TOO_MANY_LINES,
                            "inbox line exceeds " + NotificationLimits.MAX_INBOX_LINE_LENGTH + " chars");
                }
            }
        }
        if (style == NotificationStyle.PROGRESS) {
            if (!indeterminate && (progress < 0 || progress > 100)) {
                return error(NotificationErrors.INVALID_REQUEST, "progress must be 0-100");
            }
        }
        if (actions.size() > NotificationLimits.MAX_ACTIONS) {
            return error(NotificationErrors.TOO_MANY_ACTIONS,
                    "actions exceed " + NotificationLimits.MAX_ACTIONS);
        }
        for (NotificationActionSpec action : actions) {
            if (!ACTION_ID_PATTERN.matcher(action.getActionId()).matches()) {
                return error(NotificationErrors.INVALID_ACTION,
                        "actionId must match [A-Za-z0-9._-] and be 1-32 chars");
            }
            if (action.getTitle().trim().isEmpty()) {
                return error(NotificationErrors.INVALID_ACTION, "action title is required");
            }
            if (action.getTitle().length() > NotificationLimits.MAX_ACTION_TITLE_LENGTH) {
                return error(NotificationErrors.INVALID_ACTION,
                        "action title exceeds " + NotificationLimits.MAX_ACTION_TITLE_LENGTH + " chars");
            }
            NativeApiError actionLinkError = validateDeepLink(action.getDeepLink());
            if (actionLinkError != null) {
                return actionLinkError;
            }
        }
        if (scheduledAt > 0L) {
            long now = System.currentTimeMillis();
            long delay = scheduledAt - now;
            if (delay < NotificationLimits.MIN_SCHEDULE_DELAY_MS) {
                return error(NotificationErrors.SCHEDULE_INVALID,
                        "scheduledAt must be at least " + NotificationLimits.MIN_SCHEDULE_DELAY_MS + "ms in the future");
            }
            if (delay > NotificationLimits.MAX_SCHEDULE_DELAY_MS) {
                return error(NotificationErrors.SCHEDULE_INVALID,
                        "scheduledAt exceeds the " + NotificationLimits.MAX_SCHEDULE_DELAY_MS + "ms horizon");
            }
        }
        return null;
    }

    /**
     * Rejects schemes that could launch an arbitrary component or execute script.
     *
     * <p>Only relative paths ({@code /...}) and the app's own scheme are accepted; the engine
     * additionally checks the scheme against the configured app scheme.</p>
     */
    @Nullable
    private static NativeApiError validateDeepLink(String link) {
        if (link == null || link.isEmpty()) {
            return null;
        }
        String lower = link.toLowerCase(Locale.US);
        if (lower.startsWith("javascript:") || lower.startsWith("file:")
                || lower.startsWith("content:") || lower.startsWith("intent:")
                || lower.startsWith("android-app:") || lower.startsWith("data:")) {
            return error(NotificationErrors.INVALID_DEEP_LINK, "deepLink scheme is not allowed");
        }
        if (link.startsWith("/")) {
            return null;
        }
        int colon = link.indexOf(':');
        if (colon <= 0) {
            return error(NotificationErrors.INVALID_DEEP_LINK, "deepLink must be a path or an absolute URI");
        }
        String scheme = lower.substring(0, colon);
        if (!scheme.matches("[a-z][a-z0-9+.-]*")) {
            return error(NotificationErrors.INVALID_DEEP_LINK, "deepLink scheme is malformed");
        }
        return null;
    }

    @NonNull
    private static NativeApiError error(@NonNull String code, @NonNull String message) {
        return new NativeApiError(code, message);
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectId", projectId);
        map.put("key", key);
        map.put("title", title);
        map.put("body", body);
        map.put("style", style.name());
        map.put("channelId", channelId);
        map.put("priority", priority.name());
        map.put("visibility", visibility.name());
        map.put("ongoing", ongoing);
        map.put("autoCancel", autoCancel);
        map.put("groupKey", groupKey);
        map.put("groupSummary", groupSummary);
        map.put("scheduledAt", scheduledAt);
        map.put("actionCount", actions.size());
        return map;
    }

    @NonNull
    @Override
    public String toString() {
        return "NotificationRequest{project='" + projectId + "', key='" + key + "', style=" + style + '}';
    }

    /** @return a builder pre-filled with this request, used to derive a muted copy */
    @NonNull
    public Builder toBuilder() {
        return new Builder()
                .projectId(projectId)
                .key(key)
                .title(title)
                .body(body)
                .style(style)
                .bigText(bigText)
                .imageUrl(imageUrl)
                .inboxLines(inboxLines)
                .progress(progress, indeterminate)
                .ongoing(ongoing)
                .autoCancel(autoCancel)
                .groupKey(groupKey)
                .groupSummary(groupSummary)
                .channelId(channelId)
                .priority(priority)
                .visibility(visibility)
                .sound(sound)
                .vibration(vibration)
                .actions(actions)
                .deepLink(deepLink)
                .scheduledAt(scheduledAt)
                .ttlMs(ttlMs);
    }

    /** Fluent builder; every field has a safe default so a minimal request is just title + body. */
    public static final class Builder {

        private String projectId = "";
        private String key = "";
        private String title = "";
        private String body = "";
        private NotificationStyle style = NotificationStyle.BASIC;
        private String bigText = "";
        private String imageUrl = "";
        private final List<String> inboxLines = new ArrayList<>();
        private int progress = 0;
        private boolean indeterminate = false;
        private boolean ongoing = false;
        private boolean autoCancel = true;
        private String groupKey = "";
        private boolean groupSummary = false;
        private String channelId = "";
        private NotificationPriority priority = NotificationPriority.DEFAULT;
        private NotificationVisibility visibility = NotificationVisibility.PRIVATE;
        private boolean sound = true;
        private boolean vibration = true;
        private final List<NotificationActionSpec> actions = new ArrayList<>();
        private String deepLink = "";
        private long scheduledAt = 0L;
        private long ttlMs = 0L;

        @NonNull
        public Builder projectId(@Nullable String value) {
            this.projectId = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder key(@Nullable String value) {
            this.key = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder title(@Nullable String value) {
            this.title = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder body(@Nullable String value) {
            this.body = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder style(@Nullable NotificationStyle value) {
            this.style = value != null ? value : NotificationStyle.BASIC;
            return this;
        }

        @NonNull
        public Builder bigText(@Nullable String value) {
            this.bigText = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder imageUrl(@Nullable String value) {
            this.imageUrl = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder inboxLines(@Nullable List<String> value) {
            this.inboxLines.clear();
            if (value != null) {
                this.inboxLines.addAll(value);
            }
            return this;
        }

        @NonNull
        public Builder progress(int value, boolean isIndeterminate) {
            this.progress = value;
            this.indeterminate = isIndeterminate;
            return this;
        }

        @NonNull
        public Builder ongoing(boolean value) {
            this.ongoing = value;
            return this;
        }

        @NonNull
        public Builder autoCancel(boolean value) {
            this.autoCancel = value;
            return this;
        }

        @NonNull
        public Builder groupKey(@Nullable String value) {
            this.groupKey = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder groupSummary(boolean value) {
            this.groupSummary = value;
            return this;
        }

        @NonNull
        public Builder channelId(@Nullable String value) {
            this.channelId = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder priority(@Nullable NotificationPriority value) {
            this.priority = value != null ? value : NotificationPriority.DEFAULT;
            return this;
        }

        @NonNull
        public Builder visibility(@Nullable NotificationVisibility value) {
            this.visibility = value != null ? value : NotificationVisibility.PRIVATE;
            return this;
        }

        @NonNull
        public Builder sound(boolean value) {
            this.sound = value;
            return this;
        }

        @NonNull
        public Builder vibration(boolean value) {
            this.vibration = value;
            return this;
        }

        @NonNull
        public Builder actions(@Nullable List<NotificationActionSpec> value) {
            this.actions.clear();
            if (value != null) {
                this.actions.addAll(value);
            }
            return this;
        }

        @NonNull
        public Builder deepLink(@Nullable String value) {
            this.deepLink = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder scheduledAt(long value) {
            this.scheduledAt = Math.max(0L, value);
            return this;
        }

        @NonNull
        public Builder ttlMs(long value) {
            this.ttlMs = Math.max(0L, value);
            return this;
        }

        @NonNull
        public NotificationRequest build() {
            return new NotificationRequest(this);
        }
    }
}
