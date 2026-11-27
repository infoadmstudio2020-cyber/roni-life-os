package com.roni.life.os.app.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;
import com.roni.life.os.app.project.SafPermissionManager;
import com.roni.life.os.app.runtime.ProjectSessionManager;
import com.roni.life.os.app.util.UiUtils;

import java.text.DateFormat;
import java.util.Date;

/**
 * Read-only project information sheet with the registry actions.
 *
 * <p>Removing a project from the registry only drops the metadata entry — the user's files are
 * never deleted. Preview launches the project without registering it.</p>
 */
public final class ProjectInfoDialog {

    /** Actions offered by the sheet. */
    public interface Listener {
        void onPreview(Project project);

        void onRun(Project project);

        void onRemoveFromRegistry(Project project);
    }

    private ProjectInfoDialog() {
    }

    public static void show(final Activity activity, final Project project,
                            final Listener listener) {
        if (activity == null || project == null || activity.isFinishing()) {
            return;
        }

        ScrollView scroll = new ScrollView(activity);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = UiUtils.dp(activity, 20);
        container.setPadding(padding, padding, padding, padding);
        scroll.addView(container);

        addRow(activity, container, "Name", project.getName());
        addRow(activity, container, "Project ID", project.getId());
        addRow(activity, container, "Source type", project.getSourceType().getKey());
        addRow(activity, container, "Location", project.getSourcePath());
        addRow(activity, container, "Entry point", project.getEntryPoint());
        addRow(activity, container, "Splash", project.isSplashEnabled()
                ? project.getSplashDurationMs() + " ms" : "Disabled");
        addRow(activity, container, "Bootstrap", project.isBootstrapEnabled()
                ? "Enabled" : "Disabled");
        addRow(activity, container, "Default project", project.isDefault() ? "Yes" : "No");
        addRow(activity, container, "Created", format(project.getCreatedAt()));
        addRow(activity, container, "Last opened", project.getLastOpenedAt() > 0
                ? format(project.getLastOpenedAt()) : "Never");
        addRow(activity, container, "Session",
                ProjectSessionManager.get().isOpen(project.getId()) ? "Open" : "Not running");

        if (project.getSourceType() == ProjectSourceType.SAF_FOLDER) {
            Uri treeUri = SafPermissionManager.fromSourcePath(project.getSourcePath());
            boolean granted = treeUri != null && SafPermissionManager.hasPersisted(
                    activity.getContentResolver(), treeUri);
            addRow(activity, container, "Folder access", granted
                    ? "Granted (persisted)" : "Revoked — select the folder again");
        }

        if (project.getDescription() != null && !project.getDescription().isEmpty()) {
            addRow(activity, container, "Description", project.getDescription());
        }

        new AlertDialog.Builder(activity)
                .setTitle("Project information")
                .setView(scroll)
                .setPositiveButton("Preview", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.onPreview(project);
                        }
                    }
                })
                .setNeutralButton("Run", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.onRun(project);
                        }
                    }
                })
                .setNegativeButton("Remove from registry", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (listener != null) {
                            listener.onRemoveFromRegistry(project);
                        }
                    }
                })
                .show();
    }

    private static void addRow(Activity activity, LinearLayout container,
                               String label, String value) {
        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextSize(11f);
        labelView.setPadding(0, UiUtils.dp(activity, 8), 0, 0);
        labelView.setTextColor(activity.getResources().getColor(
                com.roni.life.os.app.R.color.text_secondary));
        container.addView(labelView);

        TextView valueView = new TextView(activity);
        valueView.setText(value != null && !value.isEmpty() ? value : "—");
        valueView.setTextSize(14f);
        valueView.setTextColor(activity.getResources().getColor(
                com.roni.life.os.app.R.color.text_primary));
        container.addView(valueView);
    }

    private static String format(long timestamp) {
        if (timestamp <= 0L) {
            return "—";
        }
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(timestamp));
    }
}
