package com.roni.library.platform.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The project that currently owns the foreground camera surface.
 *
 * <p>Set by the host activity, never by a web page. Every bridge request is scoped to this value,
 * so a page cannot read or delete another project's media by sending a different id.</p>
 */
public final class CameraProjectContext {

    private static volatile String activeProject = "";

    private CameraProjectContext() {
    }

    /**
     * Binds the foreground project. Blank identifiers are ignored so a malformed host value can
     * never collapse several projects into one shared media bucket.
     */
    public static void setActiveProject(@Nullable String projectId) {
        if (projectId == null) {
            return;
        }
        String trimmed = projectId.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        activeProject = trimmed;
    }

    @NonNull
    public static String getActiveProject() {
        return activeProject;
    }

    public static boolean hasActiveProject() {
        return !activeProject.isEmpty();
    }

    public static void clear() {
        activeProject = "";
    }
}
