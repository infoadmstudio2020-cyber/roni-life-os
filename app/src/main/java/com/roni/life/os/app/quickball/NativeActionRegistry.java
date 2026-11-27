package com.roni.life.os.app.quickball;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.roni.life.os.app.R;
import com.roni.life.os.app.activity.BootstrapActivity;
import com.roni.life.os.app.activity.DownloadsActivity;
import com.roni.life.os.app.activity.ProjectUIEditorActivity;
import com.roni.life.os.app.automation.AutomationActivity;
import com.roni.life.os.app.automation.AutomationEventsActivity;
import com.roni.life.os.app.camera.CameraActivity;
import com.roni.life.os.app.camera.CameraSettingsActivity;
import com.roni.life.os.app.filemanager.FileManagerActivity;
import com.roni.life.os.app.filemanager.FileManagerSettingsActivity;
import com.roni.life.os.app.filemanager.StorageAnalyzerActivity;
import com.roni.life.os.app.notification.NotificationCenterActivity;
import com.roni.life.os.app.notification.NotificationSettingsActivity;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.runtime.ProjectBrowserLauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry of Quick Ball actions.
 *
 * <p>Engines and features register here instead of editing the Quick Ball core. Registration is
 * idempotent by id: a second registration of the same id is rejected, so a feature cannot appear
 * twice. The registry stores descriptors only — no Activity, Context or engine instance is
 * retained.</p>
 */
public final class NativeActionRegistry {

    private static final NativeActionRegistry INSTANCE = new NativeActionRegistry();

    private final Map<String, NativeAction> actions = new LinkedHashMap<>();
    private boolean defaultsRegistered;

    private NativeActionRegistry() {
    }

    public static NativeActionRegistry get() {
        return INSTANCE;
    }

    /**
     * Registers an action.
     *
     * @return {@code true} when added, {@code false} when the id already exists
     */
    public synchronized boolean register(NativeAction action) {
        if (action == null || actions.containsKey(action.getId())) {
            return false;
        }
        actions.put(action.getId(), action);
        return true;
    }

    public synchronized boolean unregister(String id) {
        return id != null && actions.remove(id) != null;
    }

    public synchronized NativeAction find(String id) {
        return id == null ? null : actions.get(id);
    }

    public synchronized List<NativeAction> getAll() {
        return new ArrayList<>(actions.values());
    }

    public synchronized List<NativeAction> getByCategory(NativeAction.Category category) {
        List<NativeAction> result = new ArrayList<>();
        for (NativeAction action : actions.values()) {
            if (action.getCategory() == category) {
                result.add(action);
            }
        }
        return result;
    }

    public synchronized List<String> getIds() {
        return new ArrayList<>(actions.keySet());
    }

    public synchronized int size() {
        return actions.size();
    }

    /** Test hook: clears every registration, including the built-in defaults. */
    public synchronized void reset() {
        actions.clear();
        defaultsRegistered = false;
    }

    /**
     * Registers the built-in native actions once. Safe to call from every panel open.
     */
    public synchronized void ensureDefaults(Context context) {
        if (defaultsRegistered) {
            return;
        }
        defaultsRegistered = true;

        register(new NativeAction.Builder("qb.inspector", "Runtime Inspector",
                (activity, project) -> openInspector(activity))
                .description("Logs, bridge traces and diagnostics")
                .icon(R.drawable.ic_diagnostics)
                .category(NativeAction.Category.RUNTIME)
                .build());

        register(new NativeAction.Builder("qb.quick_settings", "Quick Settings",
                (activity, project) -> activity.startActivity(
                        new Intent(activity, QuickBallSettingsActivity.class)))
                .description("Panel layout, size and action visibility")
                .icon(R.drawable.ic_settings)
                .category(NativeAction.Category.RUNTIME)
                .build());

        register(new NativeAction.Builder("qb.project_settings", "Project UI Settings",
                (activity, project) -> {
                    Intent intent = new Intent(activity, ProjectUIEditorActivity.class);
                    if (project != null) {
                        intent.putExtra(ProjectUIEditorActivity.EXTRA_PROJECT_ID,
                                project.getId());
                        intent.putExtra(ProjectUIEditorActivity.EXTRA_PROJECT_NAME,
                                project.getName());
                    }
                    activity.startActivity(intent);
                })
                .description("Theme, toolbar and overlay configuration")
                .icon(R.drawable.ic_settings)
                .category(NativeAction.Category.RUNTIME)
                .build());

        register(new NativeAction.Builder("qb.notifications", "Notification Center",
                (activity, project) -> activity.startActivity(
                        new Intent(activity, NotificationCenterActivity.class)))
                .description("Recent notifications for the active project")
                .icon(R.drawable.ic_info)
                .category(NativeAction.Category.SYSTEM)
                .build());

        register(new NativeAction.Builder("qb.notification_settings", "Notification Settings",
                (activity, project) -> activity.startActivity(
                        new Intent(activity, NotificationSettingsActivity.class)))
                .description("Channels, importance and quiet hours")
                .icon(R.drawable.ic_settings)
                .category(NativeAction.Category.SYSTEM)
                .build());

        register(new NativeAction.Builder("qb.downloads", "Downloads",
                (activity, project) -> activity.startActivity(
                        new Intent(activity, DownloadsActivity.class)))
                .description("Download history and active transfers")
                .icon(R.drawable.ic_refresh)
                .category(NativeAction.Category.FILES)
                .build());

        register(new NativeAction.Builder("qb.file_manager", "File Manager",
                (activity, project) -> {
                    Intent intent = new Intent(activity, FileManagerActivity.class);
                    if (project != null) {
                        intent.putExtra(FileManagerActivity.EXTRA_PROJECT_ID,
                                project.getId());
                    }
                    activity.startActivity(intent);
                })
                .description("Browse the project's private storage")
                .icon(R.drawable.ic_more_vert)
                .category(NativeAction.Category.FILES)
                .build());

        register(new NativeAction.Builder("qb.storage_analyzer", "Storage Analyzer",
                (activity, project) -> activity.startActivity(
                        new Intent(activity, StorageAnalyzerActivity.class)))
                .description("Largest files and cache usage")
                .icon(R.drawable.ic_info)
                .category(NativeAction.Category.FILES)
                .build());

        register(new NativeAction.Builder("qb.file_manager_settings", "File Manager Settings",
                (activity, project) -> activity.startActivity(
                        new Intent(activity, FileManagerSettingsActivity.class)))
                .description("Hidden files, grid and sort order")
                .icon(R.drawable.ic_settings)
                .category(NativeAction.Category.FILES)
                .build());

        register(new NativeAction.Builder("qb.camera", "Camera",
                (activity, project) -> {
                    Intent intent = new Intent(activity, CameraActivity.class);
                    if (project != null) {
                        intent.putExtra(CameraActivity.EXTRA_PROJECT_ID, project.getId());
                    }
                    activity.startActivity(intent);
                })
                .description("Capture photos and video into the project")
                .icon(R.drawable.ic_add)
                .category(NativeAction.Category.MEDIA)
                .build());

        register(new NativeAction.Builder("qb.camera_settings", "Camera Settings",
                (activity, project) -> activity.startActivity(
                        new Intent(activity, CameraSettingsActivity.class)))
                .description("Resolution, grid and save location")
                .icon(R.drawable.ic_settings)
                .category(NativeAction.Category.MEDIA)
                .build());

        register(new NativeAction.Builder("qb.automation", "Automation Dashboard",
                (activity, project) -> activity.startActivity(
                        new Intent(activity, AutomationActivity.class)))
                .description("Tasks, transfers, alarms, jobs and services")
                .icon(R.drawable.ic_settings)
                .category(NativeAction.Category.AUTOMATION)
                .build());

        register(sectionAction("qb.tasks", "Background Tasks", "tasks",
                "Queued and running background work", R.drawable.ic_refresh));
        register(sectionAction("qb.transfers", "Transfers", "transfers",
                "Download and upload progress", R.drawable.ic_refresh));
        register(sectionAction("qb.jobs", "Job Scheduler", "jobs",
                "Constraint-based deferred jobs", R.drawable.ic_refresh));
        register(sectionAction("qb.alarms", "Alarm Manager", "alarms",
                "Exact and repeating alarms", R.drawable.ic_refresh));
        register(sectionAction("qb.services", "Service Manager", "services",
                "Registered native services", R.drawable.ic_refresh));

        register(new NativeAction.Builder("qb.events", "Event Monitor",
                (activity, project) -> activity.startActivity(
                        new Intent(activity, AutomationEventsActivity.class)))
                .description("Live automation event stream")
                .icon(R.drawable.ic_info)
                .category(NativeAction.Category.AUTOMATION)
                .build());

        register(new NativeAction.Builder("qb.bootstrap", "Bootstrap Dashboard",
                (activity, project) -> activity.startActivity(
                        new Intent(activity, BootstrapActivity.class)))
                .description("Project manager and startup settings")
                .icon(R.drawable.ic_back)
                .category(NativeAction.Category.SYSTEM)
                .build());

        register(new NativeAction.Builder("qb.browser", "Open in Browser",
                (activity, project) -> {
                    if (project != null) {
                        ProjectBrowserLauncher.open(activity, project);
                    }
                })
                .description("Open a remote project outside the runtime")
                .icon(R.drawable.ic_info)
                .category(NativeAction.Category.SYSTEM)
                .build());
    }

    private static NativeAction sectionAction(String id, String label, String section,
                                              String description, int icon) {
        return new NativeAction.Builder(id, label, (activity, project) -> {
            Intent intent = new Intent(activity, AutomationActivity.class);
            intent.putExtra(AutomationActivity.EXTRA_SECTION, section);
            activity.startActivity(intent);
        })
                .description(description)
                .icon(icon)
                .category(NativeAction.Category.AUTOMATION)
                .build();
    }

    private static void openInspector(Activity activity) {
        try {
            com.roni.library.devtools.inspector.ui.RuntimeInspectorSheet sheet =
                    com.roni.library.devtools.inspector.ui.RuntimeInspectorSheet
                            .install(activity);
            if (sheet != null) {
                sheet.toggle();
            }
        } catch (Throwable ignored) {
            // Diagnostics must never break the host screen.
        }
    }

    /** Unmodifiable snapshot, used by tests and diagnostics. */
    public synchronized List<NativeAction> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(actions.values()));
    }
}
