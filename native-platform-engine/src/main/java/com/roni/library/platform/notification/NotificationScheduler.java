package com.roni.library.platform.notification;

import androidx.annotation.NonNull;

import com.roni.library.contracts.notification.NotificationRequest;

/**
 * Abstraction over the alarm mechanism used for scheduled local notifications.
 *
 * <p>The service persists the request before handing it to the scheduler, so a process death
 * between the two steps is recoverable: {@link NotificationService#restoreScheduled()} re-arms
 * everything that is still in the future.</p>
 */
public interface NotificationScheduler {

    /**
     * Arms an alarm for {@code request.getScheduledAt()}.
     *
     * @return {@code true} when the alarm was accepted
     */
    boolean schedule(@NonNull NotificationRequest request, int notificationId);

    /** Disarms the alarm for a notification id. */
    void cancel(int notificationId);

    /** Disarms every alarm owned by this app. */
    void cancelAll();
}
