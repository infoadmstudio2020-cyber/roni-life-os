package com.roni.library.ui;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller and state representation for draggable, smooth bottom sheet dialogs,
 * supporting both state machine testing and live Material BottomSheetDialog rendering.
 */
public class CustomBottomSheet {

    private final AtomicBoolean visible = new AtomicBoolean(false);
    private final AtomicBoolean expanded = new AtomicBoolean(false);
    private volatile String title = "";
    private volatile boolean dismissible = true;
    private BottomSheetDialog activeDialog;

    public CustomBottomSheet() {}

    public void show() {
        visible.set(true);
    }

    public void dismiss() {
        visible.set(false);
        expanded.set(false);
        if (activeDialog != null && activeDialog.isShowing()) {
            try {
                activeDialog.dismiss();
            } catch (Throwable ignored) {}
        }
        activeDialog = null;
    }

    /**
     * Presents an active Material BottomSheetDialog on the UI thread.
     */
    public void showNativeBottomSheet(@NonNull Activity activity,
                                      @NonNull String sheetTitle,
                                      @Nullable List<NativeActionSheet.ActionItem> actions,
                                      @Nullable View customContentView,
                                      @Nullable Runnable onDismiss) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        this.title = sheetTitle;
        this.visible.set(true);

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                dismiss();

                BottomSheetDialog dialog = new BottomSheetDialog(activity);
                dialog.setCancelable(dismissible);

                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setPadding(48, 36, 48, 48);

                if (!sheetTitle.isEmpty()) {
                    TextView tvTitle = new TextView(activity);
                    tvTitle.setText(sheetTitle);
                    tvTitle.setTextSize(18);
                    tvTitle.setPadding(0, 0, 0, 24);
                    root.addView(tvTitle);
                }

                if (customContentView != null) {
                    root.addView(customContentView);
                }

                if (actions != null) {
                    for (NativeActionSheet.ActionItem item : actions) {
                        TextView btn = new TextView(activity);
                        btn.setText(item.getTitle());
                        btn.setTextSize(16);
                        btn.setPadding(0, 24, 0, 24);
                        btn.setOnClickListener(v -> {
                            dialog.dismiss();
                            if (item.getAction() != null) item.getAction().run();
                        });
                        root.addView(btn);
                    }
                }

                dialog.setContentView(root);
                dialog.setOnDismissListener(d -> {
                    visible.set(false);
                    expanded.set(false);
                    if (onDismiss != null) onDismiss.run();
                });

                activeDialog = dialog;
                dialog.show();
            }
        });
    }

    public boolean isVisible() {
        return visible.get();
    }

    public void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    public String getTitle() {
        return title;
    }

    public void setExpanded(boolean expanded) {
        this.expanded.set(expanded);
    }

    public boolean isExpanded() {
        return expanded.get();
    }

    public void setDismissible(boolean dismissible) {
        this.dismissible = dismissible;
        if (activeDialog != null) {
            activeDialog.setCancelable(dismissible);
        }
    }

    public boolean isDismissible() {
        return dismissible;
    }
}
