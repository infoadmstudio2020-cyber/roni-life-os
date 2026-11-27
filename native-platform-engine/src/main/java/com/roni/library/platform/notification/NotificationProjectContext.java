package com.roni.library.platform.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Native-side record of which project currently owns the foreground runtime.
 *
 * <p>This is the anchor of project isolation for bridge calls. A web page never supplies its own
 * project id — the native host sets it when a project is opened and clears it when the project is
 * closed. A request that arrives while no project is active is rejected, and a {@code projectId}
 * field inside a bridge payload is ignored entirely.</p>
 */
public final class NotificationProjectContext {

    private static volatile String activeProjectId = "";

    private NotificationProjectContext() {
    }

    /** Called by the host when a project runtime becomes active. */
    public static void setActiveProject(@Nullable String projectId) {
        activeProjectId = projectId != null ? projectId.trim() : "";
    }

    /** Called by the host when the project runtime is torn down. */
    public static void clear() {
        activeProjectId = "";
    }

    /** @return the active project id, or an empty string when no project is running */
    @NonNull
    public static String getActiveProject() {
        return activeProjectId;
    }

    public static boolean hasActiveProject() {
        return !activeProjectId.isEmpty();
    }
}
