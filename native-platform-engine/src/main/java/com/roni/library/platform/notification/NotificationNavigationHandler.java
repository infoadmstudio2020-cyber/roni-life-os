package com.roni.library.platform.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Host hook invoked when the user interacts with a notification.
 *
 * <p>The library never launches an Activity by itself: doing so would mean resolving an arbitrary
 * component from a payload. The host receives the already-validated project id and deep link and
 * decides how to route it.</p>
 */
public interface NotificationNavigationHandler {

    /**
     * @param projectId owning project
     * @param deepLink  validated deep link, may be empty
     */
    void onNotificationOpened(@NonNull String projectId, @Nullable String deepLink);

    /**
     * @param projectId owning project
     * @param actionId  id of the tapped action
     * @param deepLink  validated deep link of the action, may be empty
     */
    void onNotificationAction(@NonNull String projectId, @NonNull String actionId,
                              @Nullable String deepLink);
}
