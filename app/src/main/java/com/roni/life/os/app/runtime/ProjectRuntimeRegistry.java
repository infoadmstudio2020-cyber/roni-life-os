package com.roni.life.os.app.runtime;

import android.app.Activity;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Process-local index of the live {@code ProjectActivity} instance for each project.
 *
 * <p>This is what makes a deterministic restore possible. {@code FLAG_ACTIVITY_REORDER_TO_FRONT}
 * matches by component, so with several project tasks open it can surface the wrong one; the
 * registry lets the router address the exact instance and its exact task id.</p>
 *
 * <p>References are weak and removed in {@code onDestroy}, so a finished project never leaks its
 * activity. After process death the map is empty — which is precisely how the router knows a
 * session must be rebuilt rather than restored.</p>
 */
public final class ProjectRuntimeRegistry {

    private static final Map<String, WeakReference<Activity>> LIVE = new HashMap<>();

    private ProjectRuntimeRegistry() {
    }

    public static synchronized void register(String projectId, Activity activity) {
        if (projectId == null || activity == null) {
            return;
        }
        LIVE.put(projectId, new WeakReference<>(activity));
    }

    public static synchronized void unregister(String projectId, Activity activity) {
        if (projectId == null) {
            return;
        }
        WeakReference<Activity> reference = LIVE.get(projectId);
        if (reference == null) {
            return;
        }
        Activity current = reference.get();
        if (current == null || current == activity) {
            LIVE.remove(projectId);
        }
    }

    /** @return the live activity for this project, or {@code null} */
    public static synchronized Activity get(String projectId) {
        if (projectId == null) {
            return null;
        }
        WeakReference<Activity> reference = LIVE.get(projectId);
        if (reference == null) {
            return null;
        }
        Activity activity = reference.get();
        if (activity == null) {
            LIVE.remove(projectId);
        }
        return activity;
    }

    public static synchronized boolean isLive(String projectId) {
        return get(projectId) != null;
    }

    /**
     * Destroys the runtime for one project only.
     *
     * @return {@code true} when a live instance was found and asked to finish
     */
    public static synchronized boolean finish(final String projectId) {
        final Activity activity = get(projectId);
        if (activity == null) {
            return false;
        }
        LIVE.remove(projectId);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    activity.finishAndRemoveTask();
                } catch (Throwable ignored) {
                    activity.finish();
                }
            }
        });
        return true;
    }

    /** Test seam. */
    public static synchronized void clear() {
        LIVE.clear();
    }
}
