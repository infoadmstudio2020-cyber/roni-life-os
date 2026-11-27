package com.roni.library.ui.dialog;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.roni.library.contracts.ui.DialogConfig;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe Dialog Manager handling alerts, confirmation modals,
 * and live Android MaterialAlertDialog presentation.
 */
public class DialogManager {

    private final AtomicBoolean dialogShowing = new AtomicBoolean(false);
    private volatile String currentTitle = "";
    private volatile String currentMessage = "";
    private Runnable confirmAction;
    private Runnable cancelAction;
    private AlertDialog activeDialog;

    public DialogManager() {}

    public void showAlert(String title, String message, Runnable onConfirm) {
        this.currentTitle = title != null ? title : "";
        this.currentMessage = message != null ? message : "";
        this.confirmAction = onConfirm;
        this.cancelAction = null;
        this.dialogShowing.set(true);
    }

    public void showConfirm(String title, String message, Runnable onConfirm, Runnable onCancel) {
        this.currentTitle = title != null ? title : "";
        this.currentMessage = message != null ? message : "";
        this.confirmAction = onConfirm;
        this.cancelAction = onCancel;
        this.dialogShowing.set(true);
    }

    public void showDialog(@NonNull DialogConfig config, @Nullable Runnable onConfirm, @Nullable Runnable onCancel) {
        this.currentTitle = config.getTitle() != null ? config.getTitle() : "";
        this.currentMessage = config.getMessage() != null ? config.getMessage() : "";
        this.confirmAction = onConfirm;
        this.cancelAction = onCancel;
        this.dialogShowing.set(true);
    }

    /**
     * Presents an active Material AlertDialog on the UI thread.
     */
    public void showNativeDialog(@NonNull Activity activity,
                                 @NonNull String title,
                                 @NonNull String message,
                                 @NonNull String positiveText,
                                 @Nullable String negativeText,
                                 boolean cancelable,
                                 @Nullable Runnable onPositive,
                                 @Nullable Runnable onNegative) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                dismissCurrent();

                currentTitle = title;
                currentMessage = message;
                confirmAction = onPositive;
                cancelAction = onNegative;

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                        .setTitle(title)
                        .setMessage(message)
                        .setCancelable(cancelable)
                        .setPositiveButton(positiveText, (d, w) -> {
                            dialogShowing.set(false);
                            if (onPositive != null) onPositive.run();
                        });

                if (negativeText != null && !negativeText.isEmpty()) {
                    builder.setNegativeButton(negativeText, (d, w) -> {
                        dialogShowing.set(false);
                        if (onNegative != null) onNegative.run();
                    });
                }

                builder.setOnCancelListener(d -> {
                    dialogShowing.set(false);
                    if (onNegative != null) onNegative.run();
                });

                activeDialog = builder.create();
                activeDialog.show();
                dialogShowing.set(true);
            }
        });
    }

    public void confirm() {
        if (dialogShowing.get()) {
            dialogShowing.set(false);
            if (activeDialog != null && activeDialog.isShowing()) {
                activeDialog.dismiss();
            }
            if (confirmAction != null) {
                try {
                    confirmAction.run();
                } catch (Throwable ignored) {}
            }
        }
    }

    public void cancel() {
        if (dialogShowing.get()) {
            dialogShowing.set(false);
            if (activeDialog != null && activeDialog.isShowing()) {
                activeDialog.dismiss();
            }
            if (cancelAction != null) {
                try {
                    cancelAction.run();
                } catch (Throwable ignored) {}
            }
        }
    }

    public void dismissCurrent() {
        dialogShowing.set(false);
        if (activeDialog != null && activeDialog.isShowing()) {
            try {
                activeDialog.dismiss();
            } catch (Throwable ignored) {}
        }
        activeDialog = null;
        confirmAction = null;
        cancelAction = null;
    }

    public boolean isDialogShowing() {
        return dialogShowing.get();
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    public String getCurrentMessage() {
        return currentMessage;
    }
}
