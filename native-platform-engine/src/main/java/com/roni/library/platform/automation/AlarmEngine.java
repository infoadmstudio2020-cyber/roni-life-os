package com.roni.library.platform.automation;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.automation.AlarmSpec;
import com.roni.library.contracts.automation.AutomationErrorCodes;
import com.roni.library.contracts.automation.AutomationLimits;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Time based triggers backed by {@link AlarmManager}.
 *
 * <p>Exact alarms are used only when the platform allows them; otherwise the engine falls back
 * to an inexact idle-safe alarm and reports the mode in the event payload.</p>
 */
public final class AlarmEngine {

    private final AutomationStore store;
    private final EventEngine events;
    @Nullable
    private final Context context;
    private final Map<String, AlarmSpec> alarms = new LinkedHashMap<>();

    public AlarmEngine(@NonNull AutomationStore store, @NonNull EventEngine events,
                       @Nullable Context context) {
        this.store = store;
        this.events = events;
        this.context = context == null ? null : context.getApplicationContext();
    }

    @NonNull
    public AlarmSpec schedule(@NonNull String projectId, @NonNull AlarmSpec spec)
            throws AutomationException {
        if (projectId.trim().isEmpty()) {
            throw new AutomationException(AutomationErrorCodes.NO_ACTIVE_PROJECT,
                    "No active project");
        }
        long now = System.currentTimeMillis();
        if (!AlarmSchedule.isValid(spec, now)) {
            throw AutomationException.invalid("Invalid alarm specification");
        }
        if (list(projectId).size() >= AutomationLimits.MAX_ALARMS) {
            throw new AutomationException(AutomationErrorCodes.ALARM_LIMIT,
                    "Alarm limit reached");
        }
        long triggerAt = AlarmSchedule.nextTrigger(spec, now);
        if (triggerAt <= 0L) {
            throw AutomationException.invalid("Alarm trigger time is in the past");
        }
        AlarmSpec stored = new AlarmSpec.Builder()
                .id(spec.getId())
                .label(spec.getLabel())
                .triggerAt(triggerAt)
                .intervalMs(spec.getIntervalMs())
                .exact(spec.isExact())
                .payload(spec.getPayload())
                .build();
        alarms.put(key(projectId, spec.getId()), stored);
        persist(projectId);
        boolean exact = arm(projectId, stored);
        Map<String, Object> data = stored.toMap();
        data.put("exactScheduled", exact);
        events.emit(projectId, EventEngine.TOPIC_ALARM, "scheduled", data);
        return stored;
    }

    public boolean cancel(@NonNull String projectId, @NonNull String alarmId) {
        AlarmSpec spec = alarms.remove(key(projectId, alarmId));
        if (spec == null) {
            return false;
        }
        if (context != null) {
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (manager != null) {
                manager.cancel(pendingIntent(projectId, alarmId));
            }
        }
        persist(projectId);
        events.emit(projectId, EventEngine.TOPIC_ALARM, "cancelled", spec.toMap());
        return true;
    }

    @NonNull
    public List<AlarmSpec> list(@NonNull String projectId) {
        List<AlarmSpec> result = new ArrayList<>();
        for (Map.Entry<String, AlarmSpec> entry : alarms.entrySet()) {
            if (entry.getKey().startsWith(projectId + "|")) {
                result.add(entry.getValue());
            }
        }
        if (result.isEmpty()) {
            result.addAll(store.loadAlarms(projectId));
        }
        return result;
    }

    /** Re-arms persisted alarms after boot or process restart. */
    public int restore(@NonNull String projectId) {
        int armed = 0;
        for (AlarmSpec spec : store.loadAlarms(projectId)) {
            alarms.put(key(projectId, spec.getId()), spec);
            long next = AlarmSchedule.nextTrigger(spec, System.currentTimeMillis());
            if (next > 0L && arm(projectId, spec)) {
                armed++;
            }
        }
        return armed;
    }

    /** Called by the receiver when an alarm fires. */
    public void onFired(@NonNull String projectId, @NonNull String alarmId) {
        AlarmSpec spec = alarms.get(key(projectId, alarmId));
        if (spec == null) {
            return;
        }
        events.emit(projectId, EventEngine.TOPIC_ALARM, "fired", spec.toMap());
        if (spec.isRepeating()) {
            long interval = Math.max(spec.getIntervalMs(), 1000L);
            long now = System.currentTimeMillis();
            long next = spec.getTriggerAt() + interval;
            while (next <= now) {
                next += interval;
            }
            if (next - now > AutomationLimits.MAX_ALARM_HORIZON_MS) {
                next = 0L;
            }
            if (next > 0L) {
                AlarmSpec rescheduled = new AlarmSpec.Builder()
                        .id(spec.getId())
                        .label(spec.getLabel())
                        .triggerAt(next)
                        .intervalMs(spec.getIntervalMs())
                        .exact(spec.isExact())
                        .payload(spec.getPayload())
                        .build();
                alarms.put(key(projectId, alarmId), rescheduled);
                persist(projectId);
                arm(projectId, rescheduled);
            }
        } else {
            alarms.remove(key(projectId, alarmId));
            persist(projectId);
        }
    }

    private boolean arm(@NonNull String projectId, @NonNull AlarmSpec spec) {
        if (context == null) {
            return false;
        }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return false;
        }
        PendingIntent intent = pendingIntent(projectId, spec.getId());
        boolean exact = spec.isExact() && canScheduleExact(manager);
        try {
            if (exact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                            spec.getTriggerAt(), intent);
                } else {
                    manager.setExact(AlarmManager.RTC_WAKEUP, spec.getTriggerAt(), intent);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, spec.getTriggerAt(), intent);
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, spec.getTriggerAt(), intent);
            }
            return exact;
        } catch (SecurityException error) {
            return false;
        }
    }

    private static boolean canScheduleExact(@NonNull AlarmManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return manager.canScheduleExactAlarms();
        }
        return true;
    }

    @NonNull
    private PendingIntent pendingIntent(@NonNull String projectId, @NonNull String alarmId) {
        Intent intent = new Intent(context, AutomationAlarmReceiver.class);
        intent.setAction(AutomationAlarmReceiver.ACTION_ALARM);
        intent.putExtra(AutomationAlarmReceiver.EXTRA_PROJECT_ID, projectId);
        intent.putExtra(AutomationAlarmReceiver.EXTRA_ALARM_ID, alarmId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, (projectId + "|" + alarmId).hashCode(),
                intent, flags);
    }

    private void persist(@NonNull String projectId) {
        store.saveAlarms(projectId, snapshot(projectId));
    }

    @NonNull
    private List<AlarmSpec> snapshot(@NonNull String projectId) {
        List<AlarmSpec> result = new ArrayList<>();
        for (Map.Entry<String, AlarmSpec> entry : alarms.entrySet()) {
            if (entry.getKey().startsWith(projectId + "|")) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    @NonNull
    private static String key(@NonNull String projectId, @NonNull String alarmId) {
        return projectId + "|" + alarmId;
    }
}
