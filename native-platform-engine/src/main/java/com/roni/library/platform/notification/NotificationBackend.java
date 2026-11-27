package com.roni.library.platform.notification;

import androidx.annotation.NonNull;

import com.roni.library.contracts.notification.NotificationChannelSpec;
import com.roni.library.contracts.notification.NotificationPermissionState;
import com.roni.library.contracts.notification.NotificationRequest;

import java.util.List;

/**
 * Platform abstraction over {@code android.app.NotificationManager}.
 *
 * <p>Keeping the Android calls behind this interface is what makes {@link NotificationService}
 * runnable on a plain JVM: the service owns every policy decision (validation, isolation, rate
 * limiting, history) and the backend owns only the platform mechanics.</p>
 */
public interface NotificationBackend {

    /** @return {@code true} when the platform can post notifications at all */
    boolean isSupported();

    /** @return the current runtime permission state */
    @NonNull
    NotificationPermissionState getPermissionState();

    /** @return {@code true} when the user has not disabled notifications for the app */
    boolean areNotificationsEnabled();

    /** Creates the channel when missing; never modifies an existing one. */
    void ensureChannel(@NonNull NotificationChannelSpec spec);

    /** Deletes a channel owned by the app. */
    void deleteChannel(@NonNull String channelId);

    /** @return every channel currently registered by the app */
    @NonNull
    List<NotificationChannelSpec> getChannels();

    /**
     * Posts or replaces a notification.
     *
     * @param request        validated request
     * @param notificationId stable id derived from project + key
     * @return {@code true} when the platform accepted the notification
     */
    boolean post(@NonNull NotificationRequest request, int notificationId);

    /** Cancels one notification. */
    void cancel(int notificationId);

    /** Cancels every notification posted by this app. */
    void cancelAll();
}
