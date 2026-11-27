package com.roni.library.platform.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives fired alarms and forwards them to the alarm engine. */
public final class AutomationAlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_ALARM = "com.roni.library.platform.automation.ALARM";
    public static final String EXTRA_PROJECT_ID = "projectId";
    public static final String EXTRA_ALARM_ID = "alarmId";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_ALARM.equals(intent.getAction())) {
            return;
        }
        String projectId = intent.getStringExtra(EXTRA_PROJECT_ID);
        String alarmId = intent.getStringExtra(EXTRA_ALARM_ID);
        if (projectId == null || alarmId == null) {
            return;
        }
        AutomationService service = AutomationService.peek();
        if (service != null) {
            service.onAlarmFired(projectId, alarmId);
        }
    }
}
