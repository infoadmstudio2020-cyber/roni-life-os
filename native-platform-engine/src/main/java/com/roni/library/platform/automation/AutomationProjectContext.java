package com.roni.library.platform.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Trusted project identity for the automation engines.
 *
 * <p>The host sets the active project when a workspace opens. Bridge payloads never carry
 * authority: a {@code projectId} field sent by a Web App is ignored.</p>
 */
public final class AutomationProjectContext {

    private static volatile String activeProject = "";

    private AutomationProjectContext() {
    }

    public static void setActiveProject(@Nullable String projectId) {
        if (projectId == null) {
            activeProject = "";
            return;
        }
        String trimmed = projectId.trim();
        activeProject = trimmed.isEmpty() ? "" : trimmed;
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
