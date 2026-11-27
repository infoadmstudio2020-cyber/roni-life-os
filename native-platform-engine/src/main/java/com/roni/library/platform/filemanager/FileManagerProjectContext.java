package com.roni.library.platform.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The project that owns the foreground file manager surface.
 *
 * <p>Set by the host activity, never by a web page. Every bridge request is scoped to this value,
 * so a page cannot read, move or delete another project's files by sending a different id.</p>
 */
public final class FileManagerProjectContext {

    private static volatile String activeProject = "";

    private FileManagerProjectContext() {
    }

    /**
     * Binds the foreground project. Blank identifiers are ignored so a malformed host value can
     * never collapse several projects into one shared namespace.
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
