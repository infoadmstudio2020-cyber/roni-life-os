package com.roni.library.platform.notification;

import androidx.annotation.NonNull;

import com.roni.library.contracts.notification.NotificationRecord;

/**
 * Observer for notification lifecycle events.
 *
 * <p>Callbacks are invoked on the thread that produced the event. Implementations must return
 * quickly and must never throw: the service isolates every listener so a broken observer cannot
 * break delivery.</p>
 */
public interface NotificationListener {

    /** A notification was posted or replaced. */
    void onNotificationPosted(@NonNull NotificationRecord record);

    /** A notification was cancelled by the app or by the user. */
    void onNotificationCancelled(@NonNull NotificationRecord record);

    /** The user tapped the notification body. */
    void onNotificationClicked(@NonNull NotificationRecord record);

    /** The user tapped one of the action buttons. */
    void onNotificationAction(@NonNull NotificationRecord record, @NonNull String actionId);
}
