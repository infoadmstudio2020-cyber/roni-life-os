package com.roni.life.os.app.notification;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.roni.library.contracts.notification.NotificationPermissionState;

/** Runtime notification permission helper for the consumer app. */
public final class NotificationPermissionHelper {

    public static final int REQUEST_CODE = 4101;

    private NotificationPermissionHelper() {
    }

    @NonNull
    public static NotificationPermissionState getState(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationPermissionState.NOT_REQUIRED;
        }
        boolean granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        return granted ? NotificationPermissionState.GRANTED : NotificationPermissionState.DENIED;
    }

    public static boolean isGranted(@NonNull Context context) {
        return getState(context).canPost();
    }

    /** Requests the runtime permission on Android 13+; a no-op on older releases. */
    public static void request(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (isGranted(activity)) {
            return;
        }
        activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE);
    }

    @NonNull
    public static String describe(@NonNull NotificationPermissionState state) {
        switch (state) {
            case GRANTED:
                return "Notifications are allowed";
            case DENIED:
                return "Notifications are blocked — tap to allow";
            case NOT_REQUIRED:
                return "Notifications are allowed on this Android version";
            case UNKNOWN:
            default:
                return "Notification permission state is unknown";
        }
    }
}
