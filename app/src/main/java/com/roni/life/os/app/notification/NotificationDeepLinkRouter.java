package com.roni.life.os.app.notification;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.platform.notification.NotificationNavigationHandler;
import com.roni.life.os.app.activity.ProjectActivity;
import com.roni.life.os.app.util.AppLog;

/**
 * Default host routing for notification interactions.
 *
 * <p>Only an explicit intent to {@link ProjectActivity} is ever built — the project id comes from
 * the engine (never from the web page) and the deep link is carried as an extra for the runtime to
 * consume. No component name is ever resolved from a payload.</p>
 */
public final class NotificationDeepLinkRouter implements NotificationNavigationHandler {

    public static final String EXTRA_DEEP_LINK = "extra_notification_deep_link";

    private final Context context;

    public NotificationDeepLinkRouter(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void onNotificationOpened(@NonNull String projectId, @Nullable String deepLink) {
        open(projectId, deepLink);
    }

    @Override
    public void onNotificationAction(@NonNull String projectId, @NonNull String actionId,
                                     @Nullable String deepLink) {
        open(projectId, deepLink);
    }

    private void open(@NonNull String projectId, @Nullable String deepLink) {
        if (projectId.isEmpty()) {
            return;
        }
        try {
            Intent intent = new Intent(context, ProjectActivity.class);
            intent.putExtra(ProjectActivity.EXTRA_PROJECT_ID, projectId);
            if (deepLink != null && !deepLink.isEmpty()) {
                intent.putExtra(EXTRA_DEEP_LINK, deepLink);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable error) {
            AppLog.w("NotificationDeepLinkRouter", "Unable to open project " + projectId, error);
        }
    }
}
