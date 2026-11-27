package com.roni.library.ui.overlay;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates global screen-blocking loading spinners and backdrop overlays,
 * supporting both state machine verification and live Android Dialog rendering.
 */
public class LoadingOverlayManager {

    private final AtomicBoolean showing = new AtomicBoolean(false);
    private volatile String text = "Loading...";
    private Dialog activeDialog;

    public LoadingOverlayManager() {}

    public void show(String text) {
        this.text = text != null ? text : "Loading...";
        this.showing.set(true);
    }

    public void hide() {
        this.showing.set(false);
        if (activeDialog != null && activeDialog.isShowing()) {
            try {
                activeDialog.dismiss();
            } catch (Throwable ignored) {}
        }
        activeDialog = null;
    }

    public void showNativeLoading(@NonNull Activity activity, @Nullable String message, boolean cancelable) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        this.text = message != null ? message : "Loading...";
        this.showing.set(true);

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                hide();

                Dialog dialog = new Dialog(activity);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setCancelable(cancelable);

                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setGravity(Gravity.CENTER);
                layout.setPadding(64, 48, 64, 48);

                // Rounded semi-transparent container
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(Color.parseColor("#CC1E293B"));
                bg.setCornerRadius(24f);
                layout.setBackground(bg);

                ProgressBar spinner = new ProgressBar(activity);
                spinner.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                layout.addView(spinner);

                if (text != null && !text.isEmpty()) {
                    TextView tv = new TextView(activity);
                    tv.setText(text);
                    tv.setTextColor(Color.WHITE);
                    tv.setTextSize(14f);
                    tv.setPadding(0, 24, 0, 0);
                    tv.setGravity(Gravity.CENTER);
                    layout.addView(tv);
                }

                dialog.setContentView(layout);
                dialog.setOnDismissListener(d -> showing.set(false));
                activeDialog = dialog;
                dialog.show();
                showing.set(true);
            }
        });
    }

    public void hideNativeLoading() {
        hide();
    }

    public boolean isShowing() {
        return showing.get();
    }

    public String getText() {
        return text;
    }
}
