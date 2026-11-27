package com.roni.library.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller and state model for transient Toast alerts and snackbars,
 * supporting both state machine testing and live Android Toast display on the main UI thread.
 */
public class NativeToast {

    public enum Duration {
        SHORT, LONG
    }

    public enum Type {
        INFO, SUCCESS, WARNING, ERROR
    }

    private final AtomicBoolean visible = new AtomicBoolean(false);
    private volatile String message = "";
    private volatile Duration duration = Duration.SHORT;
    private volatile Type type = Type.INFO;

    public NativeToast() {}

    public void show(String message) {
        show(message, Duration.SHORT, Type.INFO);
    }

    public void show(String message, Duration duration, Type type) {
        this.message = message != null ? message : "";
        this.duration = duration != null ? duration : Duration.SHORT;
        this.type = type != null ? type : Type.INFO;
        this.visible.set(true);
    }

    /**
     * Displays a native Android Toast on the main UI thread.
     */
    public void showNativeToast(Context context, String message, Duration duration, Type type) {
        show(message, duration, type);
        if (context == null) return;

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    int toastDuration = (duration == Duration.LONG) ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT;
                    String prefix = "";
                    if (type == Type.SUCCESS) prefix = "✓ ";
                    else if (type == Type.WARNING) prefix = "⚠ ";
                    else if (type == Type.ERROR) prefix = "✗ ";

                    Toast.makeText(context.getApplicationContext(), prefix + message, toastDuration).show();
                } catch (Throwable ignored) {}
            }
        });
    }

    public void dismiss() {
        this.visible.set(false);
    }

    public boolean isVisible() {
        return visible.get();
    }

    public String getMessage() {
        return message;
    }

    public Duration getDuration() {
        return duration;
    }

    public Type getType() {
        return type;
    }
}
