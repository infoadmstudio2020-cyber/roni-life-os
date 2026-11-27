package com.roni.life.os.app.project;

import android.content.Context;

import com.roni.life.os.app.util.AppLog;

import java.io.File;

/**
 * Deletes a project completely: registry entry, owned files, cached plaintext and Keystore key.
 *
 * <p>Deletion is the one operation that must not leave a key behind. A surviving alias would let a
 * later project that reuses the id decrypt the old ciphertext, so {@link ProjectVaultRuntime}
 * destroys the key as part of the same call.</p>
 *
 * <p>Only directories inside the app's own projects root are removed. A SAF folder, a user file or
 * a remote URL is never touched — the canonical-path check makes that structural rather than
 * best-effort.</p>
 */
public final class ProjectDeletionService {

    private static final String TAG = "ProjectDelete";

    private ProjectDeletionService() {
    }

    /**
     * @return true when the registry entry was removed
     */
    public static boolean delete(Context context, ProjectStore store, String projectId) {
        if (store == null || projectId == null || projectId.trim().isEmpty()) {
            return false;
        }
        Project project = store.findById(projectId);
        if (project == null) {
            return false;
        }

        // 1. Plaintext out of RAM, then the key out of the Keystore.
        ProjectVaultRuntime.onProjectDeleted(projectId);

        // 2. Files this app owns.
        boolean filesRemoved = removeOwnedFiles(context, project);

        // 3. Registry entry last, so a crash mid-delete leaves a project that still resolves
        //    rather than a dangling directory nobody can reach.
        boolean removed = store.delete(projectId);

        AppLog.i(TAG, "Deleted " + projectId + " (files=" + filesRemoved + ", registry=" + removed + ")");
        return removed;
    }

    /**
     * Removes the project's directory when — and only when — it lives under the projects root.
     */
    private static boolean removeOwnedFiles(Context context, Project project) {
        if (context == null || project.getSourceType() != ProjectSourceType.FILE) {
            return false;
        }
        String path = project.getSourcePath();
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        try {
            File root = ProjectImporter.projectsRoot(context).getCanonicalFile();
            File candidate = new File(path).getCanonicalFile();
            if (!candidate.getPath().startsWith(root.getPath() + File.separator)) {
                AppLog.w(TAG, "Refusing to delete a path outside the projects root: " + path);
                return false;
            }
            ProjectImporter.deleteRecursively(candidate);
            return true;
        } catch (Throwable error) {
            AppLog.w(TAG, "Unable to remove project files: " + error.getMessage());
            return false;
        }
    }
}
