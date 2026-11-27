package com.roni.library.platform.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.platform.PlatformEngine;

/**
 * Entry point for every notification interaction and for scheduled alarms.
 *
 * <p>The receiver is not exported, so only the app itself (through the {@code PendingIntent}s
 * built by {@link NotificationIntentFactory}) can reach it. It performs no routing of its own: it
 * forwards the already-validated project id and deep link to the host's
 * {@link NotificationNavigationHandler}.</p>
 */
public final class NotificationReceiver extends BroadcastReceiver {

    public static final String ACTION_SCHEDULE =
            "com.roni.library.platform.notification.action.SCHEDULE";
    public static final String ACTION_OPEN =
            "com.roni.library.platform.notification.action.OPEN";
    public static final String ACTION_ACTION =
            "com.roni.library.platform.notification.action.ACTION";
    public static final String ACTION_DISMISS =
            "com.roni.library.platform.notification.action.DISMISS";

    public static final String EXTRA_ID = "notification_id";
    public static final String EXTRA_PROJECT = "project_id";
    public static final String EXTRA_KEY = "notification_key";
    public static final String EXTRA_ACTION_ID = "action_id";
    public static final String EXTRA_DEEP_LINK = "deep_link";

    private static volatile NotificationNavigationHandler navigationHandler;

    /** Installed once by the host application. */
    public static void setNavigationHandler(@Nullable NotificationNavigationHandler handler) {
        navigationHandler = handler;
    }

    @Nullable
    public static NotificationNavigationHandler getNavigationHandler() {
        return navigationHandler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        NotificationService service;
        try {
            service = PlatformEngine.getInstance().getNotificationService();
        } catch (Throwable error) {
            return;
        }
        if (service == null) {
            return;
        }

        int notificationId = intent.getIntExtra(EXTRA_ID, 0);
        if (ACTION_SCHEDULE.equals(action)) {
            service.onAlarmFired(notificationId);
            return;
        }

        String projectId = intent.getStringExtra(EXTRA_PROJECT);
        String key = intent.getStringExtra(EXTRA_KEY);
        if (projectId == null || key == null) {
            return;
        }
        String deepLink = intent.getStringExtra(EXTRA_DEEP_LINK);

        if (ACTION_OPEN.equals(action)) {
            service.dispatchClick(projectId, key);
            NotificationNavigationHandler handler = navigationHandler;
            if (handler != null) {
                try {
                    handler.onNotificationOpened(projectId, deepLink);
                } catch (Throwable ignored) {
                }
            }
            return;
        }

        if (ACTION_ACTION.equals(action)) {
            String actionId = intent.getStringExtra(EXTRA_ACTION_ID);
            if (actionId == null) {
                return;
            }
            service.dispatchAction(projectId, key, actionId);
            NotificationNavigationHandler handler = navigationHandler;
            if (handler != null) {
                try {
                    handler.onNotificationAction(projectId, actionId, deepLink);
                } catch (Throwable ignored) {
                }
            }
            return;
        }

        if (ACTION_DISMISS.equals(action)) {
            try {
                service.cancel(projectId, key);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Convenience for hosts that only need the project id. */
    @NonNull
    public static String projectOf(@NonNull Intent intent) {
        String value = intent.getStringExtra(EXTRA_PROJECT);
        return value != null ? value : "";
    }
}
