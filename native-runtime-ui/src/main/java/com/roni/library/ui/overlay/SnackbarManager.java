package com.roni.library.ui.overlay;

import android.os.Looper;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;

/**
 * Manages Material Design Snackbars with action triggers, custom styling, and anchor coordination.
 *
 * <p>Safe to call from any thread. The bridge dispatches handlers on a background pool, so the
 * Snackbar is always created and shown on the main thread.</p>
 */
public class SnackbarManager {

    public SnackbarManager() {}

    public Snackbar show(@NonNull View view, @NonNull String message, int duration,
                         @Nullable String actionTitle, @Nullable Runnable onAction) {
        final Snackbar[] holder = new Snackbar[1];
        Runnable task = () -> {
            Snackbar snackbar = Snackbar.make(view, message, duration);
            if (actionTitle != null && onAction != null) {
                snackbar.setAction(actionTitle, v -> onAction.run());
            }
            snackbar.show();
            holder[0] = snackbar;
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            view.post(task);
        }
        return holder[0];
    }

    public Snackbar showShort(@NonNull View view, @NonNull String message) {
        return show(view, message, Snackbar.LENGTH_SHORT, null, null);
    }

    public Snackbar showLong(@NonNull View view, @NonNull String message) {
        return show(view, message, Snackbar.LENGTH_LONG, null, null);
    }
}
