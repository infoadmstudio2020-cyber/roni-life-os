package com.roni.life.os.app.project;

import android.content.Context;

import com.roni.life.os.app.core.StorageLayout;
import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Restores a project version archived by {@link ProjectRegistrationService}.
 *
 * <p>An update never destroys the version that was working: the previous tree is moved to
 * {@code app/backups/<id>-<timestamp>} together with a {@code project.json} holding its registry
 * record. This class turns that archive back into a live project.</p>
 *
 * <h3>What can and cannot be restored</h3>
 *
 * <p>Restore works for <b>update</b> backups. The Keystore key is untouched by an update, so the
 * archived ciphertext still decrypts.</p>
 *
 * <p>Restore does <b>not</b> work for a <b>deleted</b> project. {@link ProjectDeletionService}
 * destroys the key on purpose — a surviving alias would let a later project that reuses the id
 * decrypt the old ciphertext. Deletion is therefore permanent, and the honest way back is to
 * re-import the source.</p>
 */
public final class ProjectRestoreService {

    /** Registry record written beside the archived files. */
    public static final String BACKUP_MANIFEST = "project.json";

    private static final String TAG = "ProjectRestore";

    private ProjectRestoreService() {
    }

    /**
     * @param projectId when non-null, only that project's backups are returned
     * @return archived versions, newest first; never null
     */
    public static List<File> listBackups(Context context, String projectId) {
        List<File> out = new ArrayList<>();
        if (context == null) {
            return out;
        }
        File[] entries = StorageLayout.backupsDir(context).listFiles();
        if (entries == null) {
            return out;
        }
        for (File entry : entries) {
            if (!entry.isDirectory()) {
                continue;
            }
            if (projectId != null && !entry.getName().startsWith(projectId + "-")) {
                continue;
            }
            out.add(entry);
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(right.lastModified(), left.lastModified());
            }
        });
        return out;
    }

    /**
     * @return the registry record stored in {@code backup}, or {@code null} when the archive has
     *         no manifest or it cannot be parsed
     */
    public static Project readBackup(File backup) {
        if (backup == null || !backup.isDirectory()) {
            return null;
        }
        File manifest = new File(backup, BACKUP_MANIFEST);
        if (!manifest.isFile()) {
            return null;
        }
        try {
            byte[] raw = readAll(manifest);
            return Project.decode(new String(raw, StandardCharsets.UTF_8));
        } catch (Throwable error) {
            AppLog.w(TAG, "Unreadable backup manifest: " + error.getMessage());
            return null;
        }
    }

    /**
     * Moves {@code backup} back into the projects root and re-registers it.
     *
     * <p>Refuses to overwrite a live project: a restore must never silently replace the version
     * the user is currently running.</p>
     *
     * @return true when the project is registered again
     */
    public static boolean restore(Context context, ProjectStore store, File backup) {
        if (context == null || store == null || backup == null || !backup.isDirectory()) {
            return false;
        }
        Project project = readBackup(backup);
        if (project == null || project.getId() == null || project.getId().trim().isEmpty()) {
            AppLog.w(TAG, "Backup has no usable manifest: " + backup.getName());
            return false;
        }
        try {
            File root = ProjectImporter.projectsRoot(context).getCanonicalFile();
            File target = new File(root, project.getId()).getCanonicalFile();
            if (!target.getPath().startsWith(root.getPath() + File.separator)) {
                AppLog.w(TAG, "Refusing to restore outside the projects root: " + target);
                return false;
            }
            if (target.exists()) {
                AppLog.w(TAG, "Refusing to restore over a live project: " + target);
                return false;
            }
            if (!backup.renameTo(target)) {
                copyDirectory(backup, target);
                ProjectImporter.deleteRecursively(backup);
            }
            // The tree is sealed with a key that is still in the Keystore; only the registry
            // entry was missing.
            store.save(project);
            ProjectVaultRuntime.onProjectUpdated(project.getId());
            AppLog.i(TAG, "Restored " + project.getId() + " from " + backup.getName());
            return true;
        } catch (Throwable error) {
            AppLog.w(TAG, "Restore failed: " + error.getMessage());
            return false;
        }
    }

    private static byte[] readAll(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[(int) Math.min(file.length(), 64 * 1024)];
            if (buffer.length == 0) {
                buffer = new byte[1024];
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // Nothing useful to do while unwinding.
            }
        }
    }

    private static void copyDirectory(File source, File target) throws IOException {
        if (source == null || target == null) {
            return;
        }
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("Cannot create " + target);
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyDirectory(child, new File(target, child.getName()));
                }
            }
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        InputStream in = new FileInputStream(source);
        try {
            OutputStream out = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }
}
