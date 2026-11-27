package com.roni.library.contracts.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One action button attached to a notification.
 *
 * <p>The action never carries an Android {@code Intent} or a component name. It carries an
 * opaque {@code actionId} plus an optional deep link, both of which are re-validated natively
 * before a {@code PendingIntent} is built.</p>
 */
public final class NotificationActionSpec implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String actionId;
    private final String title;
    private final String deepLink;
    private final boolean foreground;

    public NotificationActionSpec(@NonNull String actionId,
                                  @NonNull String title,
                                  @Nullable String deepLink,
                                  boolean foreground) {
        this.actionId = actionId != null ? actionId : "";
        this.title = title != null ? title : "";
        this.deepLink = deepLink != null ? deepLink : "";
        this.foreground = foreground;
    }

    @NonNull
    public String getActionId() {
        return actionId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getDeepLink() {
        return deepLink;
    }

    public boolean isForeground() {
        return foreground;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("actionId", actionId);
        map.put("title", title);
        map.put("deepLink", deepLink);
        map.put("foreground", foreground);
        return map;
    }

    @NonNull
    @Override
    public String toString() {
        return "NotificationActionSpec{id='" + actionId + "', title='" + title + "'}";
    }
}
