package com.roni.library.platform.automation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms persisted alarms after a device reboot. */
public final class AutomationBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        AutomationService service = AutomationService.peek();
        if (service == null) {
            service = AutomationService.create(context.getApplicationContext());
        }
        service.restoreAlarms();
    }
}
