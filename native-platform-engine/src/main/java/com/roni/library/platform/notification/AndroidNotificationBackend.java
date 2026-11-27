package com.roni.library.platform.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.notification.NotificationActionSpec;
import com.roni.library.contracts.notification.NotificationChannelSpec;
import com.roni.library.contracts.notification.NotificationPermissionState;
import com.roni.library.contracts.notification.NotificationPriority;
import com.roni.library.contracts.notification.NotificationRequest;
import com.roni.library.contracts.notification.NotificationStyle;
import com.roni.library.contracts.notification.NotificationVisibility;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link NotificationBackend} backed by the framework {@link NotificationManager}.
 *
 * <p>No {@code NotificationCompat} is used: the module targets API 21+ and every template needed
 * here (BigText, BigPicture, Inbox, progress, groups, actions) exists in the framework. Channel
 * APIs are guarded by {@code Build.VERSION.SDK_INT >= O}.</p>
 */
public final class AndroidNotificationBackend implements NotificationBackend {

    private static final String CHANNEL_PREFIX = "p";

    private final Context context;
    private final NotificationManager manager;
    private final NotificationIntentFactory intents;
    private final NotificationImageLoader imageLoader = new NotificationImageLoader();

    private volatile int smallIconResId = android.R.drawable.ic_dialog_info;

    public AndroidNotificationBackend(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.manager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.intents = new NotificationIntentFactory(this.context);
    }

    /** Overrides the small icon; the host passes its own monochrome drawable. */
    public void setSmallIcon(int resId) {
        if (resId != 0) {
            this.smallIconResId = resId;
        }
    }

    @Override
    public boolean isSupported() {
        return context != null && manager != null;
    }

    @NonNull
    @Override
    public NotificationPermissionState getPermissionState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationPermissionState.NOT_REQUIRED;
        }
        try {
            boolean granted = context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED;
            return granted ? NotificationPermissionState.GRANTED : NotificationPermissionState.DENIED;
        } catch (Throwable error) {
            return NotificationPermissionState.UNKNOWN;
        }
    }

    @Override
    public boolean areNotificationsEnabled() {
        if (manager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                return manager.areNotificationsEnabled();
            } catch (Throwable error) {
                return true;
            }
        }
        return true;
    }

    @Override
    public void ensureChannel(@NonNull NotificationChannelSpec spec) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || manager == null) {
            return;
        }
        try {
            if (manager.getNotificationChannel(spec.getChannelId()) != null) {
                return;
            }
            NotificationChannel channel = new NotificationChannel(
                    spec.getChannelId(), spec.getName(), importanceOf(spec.getPriority()));
            channel.setDescription(spec.getDescription());
            channel.setLockscreenVisibility(visibilityOf(spec.getVisibility()));
            channel.enableVibration(spec.isVibration());
            channel.enableLights(spec.isLights());
            if (!spec.isSound()) {
                channel.setSound(null, null);
            }
            manager.createNotificationChannel(channel);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void deleteChannel(@NonNull String channelId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || manager == null) {
            return;
        }
        try {
            manager.deleteNotificationChannel(channelId);
        } catch (Throwable ignored) {
        }
    }

    @NonNull
    @Override
    public List<NotificationChannelSpec> getChannels() {
        List<NotificationChannelSpec> result = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || manager == null) {
            return result;
        }
        try {
            for (NotificationChannel channel : manager.getNotificationChannels()) {
                if (channel == null || !channel.getId().startsWith(CHANNEL_PREFIX)) {
                    continue;
                }
                result.add(new NotificationChannelSpec(
                        channel.getId(),
                        String.valueOf(channel.getName()),
                        String.valueOf(channel.getDescription()),
                        priorityOf(channel.getImportance()),
                        visibilityOf(channel.getLockscreenVisibility()),
                        channel.getSound() != null,
                        channel.shouldVibrate(),
                        channel.shouldShowLights()));
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    @Override
    public boolean post(@NonNull NotificationRequest request, int notificationId) {
        if (!isSupported()) {
            return false;
        }
        try {
            Bitmap picture = null;
            if (request.getStyle() == NotificationStyle.BIG_PICTURE) {
                picture = imageLoader.cached(request.getImageUrl());
                if (picture == null) {
                    // Post the text fallback immediately, then replace it once the image arrives.
                    imageLoader.load(request.getImageUrl(), new NotificationImageLoader.Callback() {
                        @Override
                        public void onLoaded(@NonNull String url, @Nullable Bitmap bitmap) {
                            if (bitmap != null) {
                                post(request, notificationId);
                            }
                        }
                    });
                }
            }
            Notification notification = build(request, notificationId, picture);
            manager.notify(notificationId, notification);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    @Override
    public void cancel(int notificationId) {
        if (manager == null) {
            return;
        }
        try {
            manager.cancel(notificationId);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void cancelAll() {
        if (manager == null) {
            return;
        }
        try {
            manager.cancelAll();
        } catch (Throwable ignored) {
        }
    }

    /** Releases the image loader; called when the engine is disposed. */
    public void release() {
        imageLoader.shutdown();
    }

    // ------------------------------------------------------------------
    // building
    // ------------------------------------------------------------------

    @NonNull
    private Notification build(@NonNull NotificationRequest request, int notificationId,
                               @Nullable Bitmap picture) {
        String channelId = NotificationService.channelIdFor(request.getProjectId(), request.getChannelId());
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, channelId)
                : new Notification.Builder(context);

        builder.setSmallIcon(smallIconResId);
        builder.setContentTitle(request.getTitle());
        builder.setContentText(request.getBody());
        builder.setAutoCancel(request.isAutoCancel());
        builder.setOngoing(request.isOngoing());
        builder.setVisibility(visibilityOf(request.getVisibility()));
        builder.setContentIntent(intents.contentIntent(request, notificationId));
        builder.setDeleteIntent(intents.deleteIntent(request, notificationId));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setPriority(priorityOf(request.getPriority()));
            if (request.isSound()) {
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
            }
            if (request.isVibration()) {
                builder.setVibrate(new long[]{0L, 250L, 150L, 250L});
            }
        }

        if (!request.getGroupKey().isEmpty()) {
            builder.setGroup(groupIdFor(request.getProjectId(), request.getGroupKey()));
            builder.setGroupSummary(request.isGroupSummary());
        }

        applyStyle(builder, request, picture);

        List<NotificationActionSpec> actions = request.getActions();
        for (int i = 0; i < actions.size(); i++) {
            NotificationActionSpec action = actions.get(i);
            builder.addAction(0, action.getTitle(),
                    intents.actionIntent(request, action, notificationId, i));
        }
        return builder.build();
    }

    private void applyStyle(@NonNull Notification.Builder builder,
                            @NonNull NotificationRequest request,
                            @Nullable Bitmap picture) {
        switch (request.getStyle()) {
            case BIG_TEXT:
                builder.setStyle(new Notification.BigTextStyle()
                        .bigText(request.getBigText().isEmpty() ? request.getBody() : request.getBigText()));
                break;
            case BIG_PICTURE:
                if (picture != null) {
                    builder.setStyle(new Notification.BigPictureStyle().bigPicture(picture));
                } else {
                    builder.setStyle(new Notification.BigTextStyle()
                            .bigText(request.getBigText().isEmpty() ? request.getBody() : request.getBigText()));
                }
                break;
            case INBOX:
                Notification.InboxStyle inbox = new Notification.InboxStyle();
                for (String line : request.getInboxLines()) {
                    inbox.addLine(line);
                }
                builder.setStyle(inbox);
                break;
            case PROGRESS:
                builder.setProgress(100, request.getProgress(), request.isIndeterminate());
                break;
            case GROUP_SUMMARY:
                builder.setGroupSummary(true);
                break;
            case BASIC:
            default:
                break;
        }
    }

    @NonNull
    private static String groupIdFor(@NonNull String projectId, @NonNull String groupKey) {
        return "g" + Integer.toHexString(projectId.hashCode()) + "_" + groupKey;
    }

    private static int importanceOf(@NonNull NotificationPriority priority) {
        switch (priority) {
            case MIN:
                return NotificationManager.IMPORTANCE_MIN;
            case LOW:
                return NotificationManager.IMPORTANCE_LOW;
            case HIGH:
                return NotificationManager.IMPORTANCE_HIGH;
            case MAX:
                return NotificationManager.IMPORTANCE_MAX;
            case DEFAULT:
            default:
                return NotificationManager.IMPORTANCE_DEFAULT;
        }
    }

    @NonNull
    private static NotificationPriority priorityOf(int importance) {
        if (importance <= NotificationManager.IMPORTANCE_MIN) {
            return NotificationPriority.MIN;
        }
        if (importance <= NotificationManager.IMPORTANCE_LOW) {
            return NotificationPriority.LOW;
        }
        if (importance >= NotificationManager.IMPORTANCE_MAX) {
            return NotificationPriority.MAX;
        }
        if (importance >= NotificationManager.IMPORTANCE_HIGH) {
            return NotificationPriority.HIGH;
        }
        return NotificationPriority.DEFAULT;
    }

    private static int priorityOf(@NonNull NotificationPriority priority) {
        switch (priority) {
            case MIN:
                return Notification.PRIORITY_MIN;
            case LOW:
                return Notification.PRIORITY_LOW;
            case HIGH:
                return Notification.PRIORITY_HIGH;
            case MAX:
                return Notification.PRIORITY_MAX;
            case DEFAULT:
            default:
                return Notification.PRIORITY_DEFAULT;
        }
    }

    private static int visibilityOf(@NonNull NotificationVisibility visibility) {
        switch (visibility) {
            case SECRET:
                return Notification.VISIBILITY_SECRET;
            case PUBLIC:
                return Notification.VISIBILITY_PUBLIC;
            case PRIVATE:
            default:
                return Notification.VISIBILITY_PRIVATE;
        }
    }

    @NonNull
    private static NotificationVisibility visibilityOf(int value) {
        if (value == Notification.VISIBILITY_SECRET) {
            return NotificationVisibility.SECRET;
        }
        if (value == Notification.VISIBILITY_PUBLIC) {
            return NotificationVisibility.PUBLIC;
        }
        return NotificationVisibility.PRIVATE;
    }
}
