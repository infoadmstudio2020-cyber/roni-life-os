package com.roni.library.platform.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.roni.library.contracts.notification.NotificationRequest;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link NotificationScheduler} backed by {@link AlarmManager}.
 *
 * <p>Exact alarms are used when the platform allows them; the engine degrades to an inexact alarm
 * instead of throwing when the app lacks the exact-alarm capability. The set of armed ids is kept
 * so {@link #cancelAll()} can disarm everything without enumerating the system alarm table.</p>
 */
public final class AlarmNotificationScheduler implements NotificationScheduler {

    private final Context context;
    private final NotificationIntentFactory intents;
    private final Set<Integer> armed = ConcurrentHashMap.newKeySet();

    public AlarmNotificationScheduler(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.intents = new NotificationIntentFactory(this.context);
    }

    @Override
    public boolean schedule(@NonNull NotificationRequest request, int notificationId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return false;
        }
        try {
            PendingIntent pending = intents.scheduleIntent(request, notificationId);
            long triggerAt = request.getScheduledAt();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
            armed.add(notificationId);
            return true;
        } catch (Throwable error) {
            try {
                PendingIntent pending = intents.scheduleIntent(request, notificationId);
                AlarmManager manager2 = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (manager2 != null) {
                    manager2.set(AlarmManager.RTC_WAKEUP, request.getScheduledAt(), pending);
                    armed.add(notificationId);
                    return true;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    @Override
    public void cancel(int notificationId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            NotificationRequest placeholder = new NotificationRequest.Builder()
                    .projectId("")
                    .key("")
                    .build();
            manager.cancel(intents.scheduleIntent(placeholder, notificationId));
        } catch (Throwable ignored) {
        }
        armed.remove(notificationId);
    }

    @Override
    public void cancelAll() {
        for (Integer id : armed) {
            cancel(id);
        }
        armed.clear();
    }
}
