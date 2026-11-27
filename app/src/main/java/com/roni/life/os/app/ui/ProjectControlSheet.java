package com.roni.life.os.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.roni.life.os.app.R;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.quickball.NativeAction;
import com.roni.life.os.app.quickball.QuickBallPreferences;
import com.roni.life.os.app.quickball.QuickBallSettingsActivity;
import com.roni.life.os.app.runtime.ProjectSessionManager;

import java.util.List;

/**
 * Bottom sheet that manages a single running project.
 *
 * <p>Reachable from the overlay button on the project runtime. Offers the four lifecycle actions
 * (minimise, restart, settings, close) plus a switch list of the other live sessions.</p>
 */
public final class ProjectControlSheet {

    /** Actions the sheet can trigger. */
    public interface Listener {
        /** Send the project to the background, keeping its WebView alive. */
        void onMinimize();

        /** Recreate the runtime from scratch. */
        void onRestart();

        /** Open the per-project UI configuration editor. */
        void onSettings();

        /** Open a remote project in the browser, or share a local one as a ZIP. */
        void onOpenInBrowser();

        /** Destroy the runtime and free its session slot. */
        void onClose();

        /** Bring another running project to the foreground. */
        void onSwitchTo(String projectId);

        /**
         * Opens the Bootstrap management interface while keeping this project's runtime alive.
         *
         * <p>Default no-op so existing listeners stay source-compatible.</p>
         */
        default void onOpenBootstrap() {
        }
    }

    private static final int CLOSE_COLOR = 0xFFE5484D;

    private ProjectControlSheet() {
    }

    public static void show(final Activity activity,
                            final Project project,
                            final Listener listener) {
        if (activity == null || activity.isFinishing() || project == null) {
            return;
        }

        final BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = LayoutInflater.from(activity)
                .inflate(R.layout.sheet_project_control, null, false);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.sheet_title);
        TextView meta = view.findViewById(R.id.sheet_meta);
        LinearLayout actions = view.findViewById(R.id.sheet_actions);
        LinearLayout sessions = view.findViewById(R.id.sheet_sessions);
        TextView sessionsEmpty = view.findViewById(R.id.sheet_sessions_empty);

        title.setText(project.getName());

        ProjectSessionManager.Session current =
                findSession(project.getId());
        String state = current != null && current.getState()
                == ProjectSessionManager.State.MINIMIZED
                ? activity.getString(R.string.pm_state_minimized)
                : activity.getString(R.string.pm_state_active);
        meta.setText(project.getSourceType().getKey() + " · " + state);

        addAction(activity, actions,
                activity.getString(R.string.pm_sheet_minimize),
                activity.getString(R.string.pm_sheet_minimize_sub),
                ContextCompat.getColor(activity, R.color.text_primary),
                new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (listener != null) {
                            listener.onMinimize();
                        }
                    }
                });

        addAction(activity, actions,
                activity.getString(R.string.pm_sheet_restart),
                activity.getString(R.string.pm_sheet_restart_sub),
                ContextCompat.getColor(activity, R.color.text_primary),
                new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (listener != null) {
                            listener.onRestart();
                        }
                    }
                });

        addAction(activity, actions,
                activity.getString(R.string.pm_sheet_settings),
                activity.getString(R.string.pm_sheet_settings_sub),
                ContextCompat.getColor(activity, R.color.text_primary),
                new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (listener != null) {
                            listener.onSettings();
                        }
                    }
                });

        // Always available, even when the dashboard is disabled: the management interface is
        // reached from here instead of from startup.
        addAction(activity, actions,
                activity.getString(R.string.pm_sheet_bootstrap),
                activity.getString(R.string.pm_sheet_bootstrap_sub),
                ContextCompat.getColor(activity, R.color.text_primary),
                new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (listener != null) {
                            listener.onOpenBootstrap();
                        }
                    }
                });

        addAction(activity, actions,
                activity.getString(R.string.pm_sheet_browser),
                activity.getString(R.string.pm_sheet_browser_sub),
                ContextCompat.getColor(activity, R.color.text_primary),
                new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (listener != null) {
                            listener.onOpenInBrowser();
                        }
                    }
                });

        addAction(activity, actions,
                activity.getString(R.string.pm_sheet_close),
                activity.getString(R.string.pm_sheet_close_sub),
                CLOSE_COLOR,
                new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (listener != null) {
                            listener.onClose();
                        }
                    }
                });

        // ---- native actions (extensible registry) ----
        QuickBallPreferences quickBall = QuickBallPreferences.get(activity);
        List<NativeAction> nativeActions = quickBall.orderedActions(activity);
        if (!nativeActions.isEmpty()) {
            addSectionHeader(activity, actions, "Native tools");
            for (final NativeAction action : nativeActions) {
                addAction(activity, actions,
                        action.getLabel(),
                        action.getDescription(),
                        ContextCompat.getColor(activity, R.color.text_primary),
                        new Runnable() {
                            @Override
                            public void run() {
                                dialog.dismiss();
                                action.run(activity, project);
                            }
                        });
            }
        }
        addAction(activity, actions,
                "Customize Quick Ball",
                "Choose, reorder, rename and re-icon panel actions",
                ContextCompat.getColor(activity, R.color.text_primary),
                new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        activity.startActivity(new Intent(activity,
                                QuickBallSettingsActivity.class));
                    }
                });

        // ---- other running sessions ----
        int others = 0;
        List<ProjectSessionManager.Session> all =
                ProjectSessionManager.get().getSessions();
        for (final ProjectSessionManager.Session session : all) {
            if (session.getProjectId().equals(project.getId())) {
                continue;
            }
            others++;
            sessions.addView(sessionRow(activity, session, dialog, listener));
        }
        sessionsEmpty.setVisibility(others == 0 ? View.VISIBLE : View.GONE);

        dialog.show();
    }

    private static ProjectSessionManager.Session findSession(String projectId) {
        for (ProjectSessionManager.Session session
                : ProjectSessionManager.get().getSessions()) {
            if (session.getProjectId().equals(projectId)) {
                return session;
            }
        }
        return null;
    }

    private static View sessionRow(final Activity activity,
                                   final ProjectSessionManager.Session session,
                                   final BottomSheetDialog dialog,
                                   final Listener listener) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
        row.setBackgroundResource(selectableBackground(activity));

        TextView name = new TextView(activity);
        name.setText(session.getName());
        name.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
        name.setTextSize(14f);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView state = new TextView(activity);
        if (session.getState() == ProjectSessionManager.State.RECOVERABLE) {
            state.setText(activity.getString(R.string.pm_state_recoverable));
        } else if (session.getState() == ProjectSessionManager.State.MINIMIZED) {
            state.setText(activity.getString(R.string.pm_state_minimized));
        } else {
            state.setText(activity.getString(R.string.pm_state_active));
        }
        state.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary));
        state.setTextSize(11f);

        row.addView(name);
        row.addView(state);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                if (listener != null) {
                    listener.onSwitchTo(session.getProjectId());
                }
            }
        });

        return row;
    }

    private static void addSectionHeader(Activity activity,
                                         LinearLayout parent,
                                         String title) {
        TextView header = new TextView(activity);
        header.setText(title);
        header.setTextSize(12f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(ContextCompat.getColor(activity, R.color.brand_primary));
        header.setPadding(0, dp(activity, 18), 0, dp(activity, 4));
        parent.addView(header);
    }

    private static void addAction(Activity activity,
                                  LinearLayout parent,
                                  String title,
                                  String subtitle,
                                  int titleColor,
                                  final Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(activity, 12), 0, dp(activity, 12));
        row.setBackgroundResource(selectableBackground(activity));

        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextColor(titleColor);
        titleView.setTextSize(15f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);

        TextView subtitleView = new TextView(activity);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary));
        subtitleView.setTextSize(11.5f);

        row.addView(titleView);
        row.addView(subtitleView);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                action.run();
            }
        });

        parent.addView(row);
    }

    private static int selectableBackground(Activity activity) {
        android.util.TypedValue value = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density);
    }
}
