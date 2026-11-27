package com.roni.library.platform.automation;

import androidx.annotation.NonNull;

import com.roni.library.contracts.automation.AlarmSpec;
import com.roni.library.contracts.automation.AutomationLimits;

/** Pure scheduling math shared by the alarm engine and its tests. */
public final class AlarmSchedule {

    private AlarmSchedule() {
    }

    /** @return the next trigger time, or {@code 0} when the alarm is in the past and not repeating */
    public static long nextTrigger(@NonNull AlarmSpec spec, long now) {
        long triggerAt = spec.getTriggerAt();
        if (triggerAt <= 0L) {
            return 0L;
        }
        if (triggerAt > now) {
            return triggerAt;
        }
        if (!spec.isRepeating() || spec.getIntervalMs() <= 0L) {
            return 0L;
        }
        long interval = Math.max(spec.getIntervalMs(), 1000L);
        long missed = (now - triggerAt) / interval + 1L;
        long next = triggerAt + missed * interval;
        if (next - now > AutomationLimits.MAX_ALARM_HORIZON_MS) {
            return 0L;
        }
        return next;
    }

    public static boolean isValid(@NonNull AlarmSpec spec, long now) {
        if (spec.getId().trim().isEmpty() || spec.getLabel().trim().isEmpty()) {
            return false;
        }
        if (spec.getTriggerAt() <= 0L) {
            return false;
        }
        if (spec.isRepeating() && spec.getIntervalMs() < 1000L) {
            return false;
        }
        return spec.getTriggerAt() > now - AutomationLimits.MAX_ALARM_HORIZON_MS;
    }
}
