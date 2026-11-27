package com.roni.life.os.app.runtime;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;

import com.roni.life.os.app.activity.ProjectActivity;
import com.roni.life.os.app.core.ApplicationCore;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.util.AppLog;

/**
 * The single entry point for opening a project.
 *
 * <p>Every caller — dashboard, launcher, control sheet, startup router — goes through here so the
 * open/restore decision is made in exactly one place:</p>
 *
 * <pre>
 * open(project)
 *   ├─ session exists and is live  → RESTORE  (move its task to the front, WebView untouched)
 *   ├─ session exists but is stale → CREATE   (rebuild the runtime, reuse the session slot)
 *   ├─ no session, under the cap   → CREATE   (new document task + new session)
 *   └─ no session, at the cap      → BLOCKED  (never evicts another project)
 * </pre>
 *
 * <p>Each project runs in its own document task. That is what allows several projects to stay
 * alive at once: minimising one task cannot destroy another task's WebView, and restoring is a
 * task reorder rather than an activity relaunch.</p>
 */
public final class ProjectLaunchRouter {

    /** What {@link #open} decided to do. */
    public enum Action {
        /** An existing live runtime was brought to the front. */
        RESTORE,
        /** A new runtime task was started. */
        CREATE,
        /** The session cap was reached; nothing was started. */
        BLOCKED
    }

    /** Outcome of an open request. */
    public static final class Result {
        private final Action action;
        private final String message;

        public Result(Action action, String message) {
            this.action = action;
            this.message = message != null ? message : "";
        }
        public Action getAction() {
            return action;
        }

        public String getMessage() {
            return message;
        }

        public boolean isBlocked() {
            return action == Action.BLOCKED;
        }

        public boolean isRestored() {
            return action == Action.RESTORE;
        }
    }

    private ProjectLaunchRouter() {
    }

    /**
     * Pure decision function.
     *
     * @param sessionExists whether the project already has a session entry
     * @param live          whether that session still has an in-memory runtime
     * @param sessionCount  current number of sessions
     * @param max           session cap
     */
    public static Action decide(boolean sessionExists, boolean live,
                                int sessionCount, int max) {
        if (sessionExists && live) {
            return Action.RESTORE;
        }
        if (!sessionExists && sessionCount >= max) {
            return Action.BLOCKED;
        }
        return Action.CREATE;
    }

    /** Opens a project by id, resolving it from the store. */
    public static Result open(Activity activity, String projectId) {
        if (activity == null || projectId == null || projectId.trim().isEmpty()) {
            return new Result(Action.BLOCKED, "A project id is required.");
        }
        Project project = null;
        try {
            project = ApplicationCore.get().getStore().findById(projectId);
        } catch (Throwable error) {
            AppLog.e("Unable to resolve project " + projectId, error);
        }
        if (project == null) {
            return new Result(Action.BLOCKED, "That project no longer exists.");
        }
        return open(activity, project);
    }

    /** Opens a project, restoring its live runtime when one exists. */
    public static Result open(Activity activity, Project project) {
        if (activity == null || project == null) {
            return new Result(Action.BLOCKED, "A project is required.");
        }
        if (activity.isFinishing()) {
            return new Result(Action.BLOCKED, "");
        }

        ProjectSessionManager manager = ProjectSessionManager.get();
        ProjectSessionManager.Session existing = manager.find(project.getId());
        boolean live = existing != null && existing.isLive()
                && ProjectRuntimeRegistry.isLive(project.getId());

        Action action = decide(existing != null, live, manager.count(),
                ProjectSessionManager.MAX_SESSIONS);

        if (action == Action.BLOCKED) {
            return new Result(Action.BLOCKED,
                    "All " + ProjectSessionManager.MAX_SESSIONS
                            + " sessions are open. Close one to continue.");
        }

        if (action == Action.RESTORE) {
            if (restore(activity, project.getId())) {
                return new Result(Action.RESTORE, "");
            }
            // The task disappeared between the check and the reorder — rebuild instead.
            action = Action.CREATE;
        }

        ProjectSessionManager.Registration registration = manager.register(
                project.getId(), project.getName(), false,
                project.getIconColor(), project.getSourceType().getKey());
        if (!registration.isAccepted()) {
            return new Result(Action.BLOCKED, registration.getMessage());
        }

        Intent intent = new Intent(activity, ProjectActivity.class);
        intent.putExtra(ProjectActivity.EXTRA_PROJECT_ID, project.getId());
        // One document task per project: several runtimes stay alive side by side.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        try {
            activity.startActivity(intent);
        } catch (Throwable error) {
            AppLog.e("Unable to start project " + project.getId(), error);
            manager.close(project.getId());
            return new Result(Action.BLOCKED, "The project could not be started.");
        }

        persist();
        return new Result(Action.CREATE, "");
    }

    /**
     * Brings an existing project task to the front without touching its WebView.
     *
     * @return {@code false} when no live runtime exists and the caller must create one
     */
    public static boolean restore(Activity activity, String projectId) {
        if (activity == null || projectId == null) {
            return false;
        }
        if (!ProjectRuntimeRegistry.isLive(projectId)) {
            return false;
        }
        ProjectSessionManager.Session session = ProjectSessionManager.get().find(projectId);
        if (session == null || !session.isLive()) {
            return false;
        }
        if (session.getTaskId() == ProjectSessionManager.NO_TASK) {
            return false;
        }

        ActivityManager activityManager = (ActivityManager)
                activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }
        try {
            activityManager.moveTaskToFront(session.getTaskId(),
                    ActivityManager.MOVE_TASK_NO_USER_ACTION);
            ProjectSessionManager.get().resume(projectId);
            persist();
            return true;
        } catch (Throwable error) {
            AppLog.e("Unable to restore task for " + projectId, error);
            return false;
        }
    }

    /**
     * Destroys exactly one project runtime and frees its session slot.
     *
     * <p>Other sessions are untouched — this is the only path that may remove a session.</p>
     */
    public static boolean close(Activity activity, String projectId) {
        if (projectId == null) {
            return false;
        }
        boolean finished = ProjectRuntimeRegistry.finish(projectId);
        ProjectSessionManager.get().close(projectId);
        persist();
        return finished;
    }

    /** Writes the current session list for cold-start recovery. */
    public static void persist() {
        try {
            SessionSnapshotStore.forApp().save(ProjectSessionManager.get().snapshot());
        } catch (Throwable ignored) {
            // Recovery is best effort.
        }
    }
}
