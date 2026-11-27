package com.roni.library.platform.notification;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.roni.library.contracts.notification.NotificationActionSpec;
import com.roni.library.contracts.notification.NotificationRequest;

/**
 * Builds every {@link PendingIntent} used by a notification.
 *
 * <p>All intents are explicit (they target {@link NotificationReceiver} inside this library) and
 * immutable on API 23+, so a payload can never redirect them to another component. The deep link
 * travels as an extra and is re-validated by the host before it is used.</p>
 */
final class NotificationIntentFactory {

    private final Context context;

    NotificationIntentFactory(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    PendingIntent contentIntent(@NonNull NotificationRequest request, int notificationId) {
        Intent intent = baseIntent(NotificationReceiver.ACTION_OPEN, request, notificationId);
        return pendingIntent(intent, notificationId * 10);
    }

    @NonNull
    PendingIntent deleteIntent(@NonNull NotificationRequest request, int notificationId) {
        Intent intent = baseIntent(NotificationReceiver.ACTION_DISMISS, request, notificationId);
        return pendingIntent(intent, notificationId * 10 + 9);
    }

    @NonNull
    PendingIntent actionIntent(@NonNull NotificationRequest request,
                               @NonNull NotificationActionSpec action,
                               int notificationId,
                               int actionIndex) {
        Intent intent = baseIntent(NotificationReceiver.ACTION_ACTION, request, notificationId);
        intent.putExtra(NotificationReceiver.EXTRA_ACTION_ID, action.getActionId());
        intent.putExtra(NotificationReceiver.EXTRA_DEEP_LINK,
                action.getDeepLink().isEmpty() ? request.getDeepLink() : action.getDeepLink());
        return pendingIntent(intent, notificationId * 10 + 1 + actionIndex);
    }

    @NonNull
    PendingIntent scheduleIntent(@NonNull NotificationRequest request, int notificationId) {
        Intent intent = baseIntent(NotificationReceiver.ACTION_SCHEDULE, request, notificationId);
        return pendingIntent(intent, notificationId);
    }

    @NonNull
    private Intent baseIntent(@NonNull String action, @NonNull NotificationRequest request,
                              int notificationId) {
        Intent intent = new Intent(context, NotificationReceiver.class);
        intent.setAction(action);
        intent.putExtra(NotificationReceiver.EXTRA_ID, notificationId);
        intent.putExtra(NotificationReceiver.EXTRA_PROJECT, request.getProjectId());
        intent.putExtra(NotificationReceiver.EXTRA_KEY, request.getKey());
        intent.putExtra(NotificationReceiver.EXTRA_DEEP_LINK, request.getDeepLink());
        return intent;
    }

    @NonNull
    private PendingIntent pendingIntent(@NonNull Intent intent, int requestCode) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent, flags);
        if (pending == null) {
            // getBroadcast only returns null when the platform refuses the intent; fall back to a
            // no-op broadcast so the notification can still be built.
            Intent fallback = new Intent(context, NotificationReceiver.class);
            fallback.setAction(NotificationReceiver.ACTION_DISMISS);
            return PendingIntent.getBroadcast(context, requestCode, fallback, flags);
        }
        return pending;
    }
}
