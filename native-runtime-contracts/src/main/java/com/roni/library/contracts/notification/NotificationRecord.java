package com.roni.library.contracts.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persisted metadata of one notification, used by the Notification Center and by the engine's
 * ownership checks.
 *
 * <p>The record is deliberately small: it never stores the full payload, only what the history
 * list and the isolation rules need. The Android notification itself is the source of truth for
 * what is currently visible in the shade.</p>
 */
public final class NotificationRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String projectId;
    private final String key;
    private final String title;
    private final String body;
    private final NotificationStyle style;
    private final String channelId;
    private final NotificationPriority priority;
    private final long postedAt;
    private final boolean read;
    private final boolean cancelled;
    private final String groupKey;
    private final String deepLink;
    private final int actionCount;
    private final boolean ongoing;
    private final long scheduledAt;

    public NotificationRecord(long id,
                              @NonNull String projectId,
                              @NonNull String key,
                              @NonNull String title,
                              @NonNull String body,
                              @NonNull NotificationStyle style,
                              @NonNull String channelId,
                              @NonNull NotificationPriority priority,
                              long postedAt,
                              boolean read,
                              boolean cancelled,
                              @NonNull String groupKey,
                              @NonNull String deepLink,
                              int actionCount,
                              boolean ongoing,
                              long scheduledAt) {
        this.id = id;
        this.projectId = projectId != null ? projectId : "";
        this.key = key != null ? key : "";
        this.title = title != null ? title : "";
        this.body = body != null ? body : "";
        this.style = style != null ? style : NotificationStyle.BASIC;
        this.channelId = channelId != null ? channelId : "";
        this.priority = priority != null ? priority : NotificationPriority.DEFAULT;
        this.postedAt = postedAt;
        this.read = read;
        this.cancelled = cancelled;
        this.groupKey = groupKey != null ? groupKey : "";
        this.deepLink = deepLink != null ? deepLink : "";
        this.actionCount = actionCount;
        this.ongoing = ongoing;
        this.scheduledAt = scheduledAt;
    }

    public long getId() {
        return id;
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
    public String getChannelId() {
        return channelId;
    }

    @NonNull
    public NotificationPriority getPriority() {
        return priority;
    }

    public long getPostedAt() {
        return postedAt;
    }

    public boolean isRead() {
        return read;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    @NonNull
    public String getGroupKey() {
        return groupKey;
    }

    @NonNull
    public String getDeepLink() {
        return deepLink;
    }

    public int getActionCount() {
        return actionCount;
    }

    public boolean isOngoing() {
        return ongoing;
    }

    public long getScheduledAt() {
        return scheduledAt;
    }

    public boolean isScheduled() {
        return scheduledAt > 0L;
    }

    @NonNull
    public NotificationRecord withRead(boolean value) {
        return new NotificationRecord(id, projectId, key, title, body, style, channelId, priority,
                postedAt, value, cancelled, groupKey, deepLink, actionCount, ongoing, scheduledAt);
    }

    @NonNull
    public NotificationRecord withCancelled(boolean value) {
        return new NotificationRecord(id, projectId, key, title, body, style, channelId, priority,
                postedAt, read, value, groupKey, deepLink, actionCount, ongoing, scheduledAt);
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("projectId", projectId);
        map.put("key", key);
        map.put("title", title);
        map.put("body", body);
        map.put("style", style.name());
        map.put("channelId", channelId);
        map.put("priority", priority.name());
        map.put("postedAt", postedAt);
        map.put("read", read);
        map.put("cancelled", cancelled);
        map.put("groupKey", groupKey);
        map.put("deepLink", deepLink);
        map.put("actionCount", actionCount);
        map.put("ongoing", ongoing);
        map.put("scheduledAt", scheduledAt);
        return map;
    }

    @NonNull
    @Override
    public String toString() {
        return "NotificationRecord{id=" + id + ", project='" + projectId + "', key='" + key + "'}";
    }
}
