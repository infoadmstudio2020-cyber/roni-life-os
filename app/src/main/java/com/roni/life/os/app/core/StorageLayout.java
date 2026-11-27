package com.roni.life.os.app.core;

import android.content.Context;

import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Canonical on-device storage tree for RONiLiFEOS.
 *
 * <pre>
 * RONiLiFEOS/
 * ├── system/   splash, bootstrap, config, database
 * ├── app/      projects, config, backups, imports
 * ├── runtime/  workspace, cache, sessions, logs, config
 * └── shared/   assets, themes, resources
 * </pre>
 *
 * <p>This class owns <b>paths only</b>. It never decides what a project is and never
 * mutates the registry — {@code ProjectStore} stays the single source of truth. Every
 * accessor is idempotent and creates its directory on demand, so a caller can ask for a
 * path without first running {@link #provision(Context)}.</p>
 *
 * <p>Layout version 2 moved projects from {@code RONiLiFEOS/projects} to
 * {@code RONiLiFEOS/app/projects} and resources from {@code RONiLiFEOS/resources} to
 * {@code RONiLiFEOS/shared/resources}. {@link #provision(Context)} migrates an existing
 * install in place and never overwrites a file that already exists at the destination.</p>
 */
public final class StorageLayout {

    private static final String TAG = "StorageLayout";

    /** Root folder name under {@code Context#getFilesDir()}. */
    public static final String ROOT = "RONiLiFEOS";

    /** Bumped whenever the tree changes shape; recorded in {@code storage-config.json}. */
    public static final int LAYOUT_VERSION = 3;

    private StorageLayout() {
    }

    // ==================== Roots ====================

    /**
     * Storage root: {@code Android/data/<package>/files/RONiLiFEOS}.
     *
     * <p>App-specific external storage needs no runtime permission on any supported API level
     * and stays readable over USB, which is what the layout is for. When external storage is
     * unavailable (unmounted, or a device that reports none) the internal files directory is
     * used instead so the app still starts.</p>
     */
    public static File root(Context context) {
        File external = externalRoot(context);
        return external != null ? external : legacyRoot(context);
    }

    /** App-specific external files directory, or {@code null} when it is unavailable. */
    public static File externalRoot(Context context) {
        File base = context.getExternalFilesDir(null);
        return base != null ? new File(base, ROOT) : null;
    }

    /** Internal files directory — the pre-1.4 root, kept for migration and as a fallback. */
    public static File legacyRoot(Context context) {
        return new File(context.getFilesDir(), ROOT);
    }

    // ==================== system/ ====================

    public static File systemDir(Context context) {
        return ensure(new File(root(context), "system"));
    }

    public static File splashDir(Context context) {
        return ensure(new File(systemDir(context), "splash"));
    }

    public static File splashConfigDir(Context context) {
        return ensure(new File(splashDir(context), "config"));
    }

    public static File splashAssetsDir(Context context) {
        return ensure(new File(splashDir(context), "assets"));
    }

    public static File splashThemesDir(Context context) {
        return ensure(new File(splashDir(context), "themes"));
    }

    /** Protected built-in splash source, copied from the APK on first launch. */
    public static File splashDefaultDir(Context context) {
        return ensure(new File(splashDir(context), "default"));
    }

    /** User-controlled splash source. */
    public static File splashRuntimeDir(Context context) {
        return ensure(new File(splashDir(context), "runtime"));
    }

    public static File splashConfigFile(Context context) {
        return new File(splashConfigDir(context), "splash-config.json");
    }

    public static File bootstrapDir(Context context) {
        return ensure(new File(systemDir(context), "bootstrap"));
    }

    public static File bootstrapNativeDir(Context context) {
        return ensure(new File(bootstrapDir(context), "native"));
    }

    public static File bootstrapWebDir(Context context) {
        return ensure(new File(bootstrapDir(context), "web"));
    }

    public static File bootstrapConfigDir(Context context) {
        return ensure(new File(bootstrapDir(context), "config"));
    }

    /** Protected built-in bootstrap source, copied from the APK on first launch. */
    public static File bootstrapDefaultDir(Context context) {
        return ensure(new File(bootstrapDir(context), "default"));
    }

    /** User-controlled bootstrap source. */
    public static File bootstrapRuntimeDir(Context context) {
        return ensure(new File(bootstrapDir(context), "runtime"));
    }

    public static File bootstrapConfigFile(Context context) {
        return new File(bootstrapConfigDir(context), "bootstrap-config.json");
    }

    public static File systemConfigDir(Context context) {
        return ensure(new File(systemDir(context), "config"));
    }

    public static File databaseDir(Context context) {
        return ensure(new File(systemDir(context), "database"));
    }

    // ==================== app/ ====================

    public static File appDir(Context context) {
        return ensure(new File(root(context), "app"));
    }

    /** Authoritative root of every registered project. */
    public static File projectsDir(Context context) {
        return ensure(new File(appDir(context), "projects"));
    }

    public static File projectDir(Context context, String projectId) {
        return ensure(new File(projectsDir(context), safeSegment(projectId)));
    }

    /** The project generated on first launch ({@code app/projects/default}). */
    public static File defaultProjectDir(Context context) {
        return projectDir(context, StorageSpec.DEFAULT_PROJECT_ID);
    }

    public static File appConfigDir(Context context) {
        return ensure(new File(appDir(context), "config"));
    }

    public static File backupsDir(Context context) {
        return ensure(new File(appDir(context), "backups"));
    }

    public static File importsDir(Context context) {
        return ensure(new File(appDir(context), "imports"));
    }

    // ==================== runtime/ ====================

    public static File runtimeDir(Context context) {
        return ensure(new File(root(context), "runtime"));
    }

    public static File workspaceDir(Context context) {
        return ensure(new File(runtimeDir(context), "workspace"));
    }

    public static File cacheDir(Context context) {
        return ensure(new File(runtimeDir(context), "cache"));
    }

    public static File sessionsDir(Context context) {
        return ensure(new File(runtimeDir(context), "sessions"));
    }

    public static File logsDir(Context context) {
        return ensure(new File(runtimeDir(context), "logs"));
    }

    public static File runtimeConfigDir(Context context) {
        return ensure(new File(runtimeDir(context), "config"));
    }

    // ==================== shared/ ====================

    public static File sharedDir(Context context) {
        return ensure(new File(root(context), "shared"));
    }

    public static File sharedAssetsDir(Context context) {
        return ensure(new File(sharedDir(context), "assets"));
    }

    public static File sharedThemesDir(Context context) {
        return ensure(new File(sharedDir(context), "themes"));
    }

    /** Authoritative root of downloaded project resources. */
    public static File sharedResourcesDir(Context context) {
        return ensure(new File(sharedDir(context), "resources"));
    }

    // ==================== Files ====================

    public static File appConfigFile(Context context) {
        return new File(systemConfigDir(context), "app-config.json");
    }

    public static File storageConfigFile(Context context) {
        return new File(systemConfigDir(context), "storage-config.json");
    }

    /** Marker written after a successful first-launch initialisation. */
    public static File initializationFile(Context context) {
        return new File(systemConfigDir(context), "initialization.json");
    }

    public static File runtimeConfigFile(Context context) {
        return new File(systemConfigDir(context), "runtime-config.json");
    }

    public static File projectRegistryFile(Context context) {
        return new File(appConfigDir(context), "project-registry.json");
    }

    public static File activeProjectFile(Context context) {
        return new File(appConfigDir(context), "active-project.json");
    }

    public static File projectManifestFile(Context context, String projectId) {
        return new File(projectDir(context, projectId), "manifest.json");
    }

    // ==================== Provisioning ====================

    /**
     * Creates the whole tree and migrates a layout-1 install in place.
     *
     * <p>Safe to call on every start: existing directories are left untouched and the
     * migration only runs while the legacy folders still exist.</p>
     */
    public static void provision(Context context) {
        Context app = context.getApplicationContext();
        migrateInternalToExternal(app);
        migrateLegacy(app);

        systemDir(app);
        splashConfigDir(app);
        splashDefaultDir(app);
        ensure(new File(splashDefaultDir(app), "css"));
        ensure(new File(splashDefaultDir(app), "js"));
        ensure(new File(splashDefaultDir(app), "assets"));
        splashRuntimeDir(app);
        ensure(new File(splashRuntimeDir(app), "css"));
        ensure(new File(splashRuntimeDir(app), "js"));
        ensure(new File(splashRuntimeDir(app), "assets"));
        bootstrapConfigDir(app);
        bootstrapDefaultDir(app);
        ensure(new File(bootstrapDefaultDir(app), "native"));
        ensure(new File(bootstrapDefaultDir(app), "web"));
        bootstrapRuntimeDir(app);
        ensure(new File(bootstrapRuntimeDir(app), "native"));
        ensure(new File(bootstrapRuntimeDir(app), "web"));
        systemConfigDir(app);
        databaseDir(app);

        projectsDir(app);
        appConfigDir(app);
        backupsDir(app);
        importsDir(app);

        workspaceDir(app);
        cacheDir(app);
        sessionsDir(app);
        logsDir(app);
        runtimeConfigDir(app);

        sharedAssetsDir(app);
        sharedThemesDir(app);
        sharedResourcesDir(app);

        AppLog.i("Storage layout v" + LAYOUT_VERSION + " ready at " + root(app).getAbsolutePath());
    }

    /**
     * Creates the per-project skeleton: {@code manifest.json}, {@code ui}, {@code sources},
     * {@code config} and {@code resources}.
     *
     * <p>The project's actual web content still lives at the project root, so the runtime
     * server keeps resolving {@code index.html} exactly as before.</p>
     */
    public static void ensureProjectDirs(Context context, String projectId) {
        if (projectId == null || projectId.trim().isEmpty()) {
            return;
        }
        File dir = projectDir(context, projectId);
        ensure(new File(dir, "ui"));
        ensure(new File(dir, "sources"));
        ensure(new File(dir, "config"));
        ensure(new File(dir, "resources"));
    }

    // ==================== Migration ====================

    /**
     * Moves a pre-1.4 install from internal storage to app-specific external storage.
     *
     * <p>Runs at most once: it only acts while the external root is still empty and the
     * internal root still holds data. Nothing is ever overwritten — a name that already
     * exists at the destination is left alone and the internal copy is kept.</p>
     */
    private static void migrateInternalToExternal(Context context) {
        File external = externalRoot(context);
        File internal = legacyRoot(context);
        if (external == null || external.equals(internal) || !internal.isDirectory()) {
            return;
        }
        if (external.isDirectory() && hasChildren(external)) {
            return;
        }
        ensure(external);
        int moved = moveChildren(internal, external);
        if (moved > 0) {
            AppLog.i("Migrated " + moved + " entr(ies) to external storage: "
                    + external.getAbsolutePath());
        }
        // Only drop the old tree once it is empty; leftovers mean a skipped conflict.
        File[] remaining = internal.listFiles();
        if (remaining != null && remaining.length == 0) {
            //noinspection ResultOfMethodCallIgnored
            internal.delete();
        }
    }

    private static boolean hasChildren(File dir) {
        File[] children = dir.listFiles();
        return children != null && children.length > 0;
    }

    private static void migrateLegacy(Context context) {
        File root = root(context);

        File legacyProjects = new File(root, "projects");
        File newProjects = new File(new File(root, "app"), "projects");
        if (legacyProjects.isDirectory() && !legacyProjects.equals(newProjects)) {
            ensure(newProjects);
            int moved = moveChildren(legacyProjects, newProjects);
            //noinspection ResultOfMethodCallIgnored
            legacyProjects.delete();
            AppLog.i("Migrated " + moved + " project folder(s) to app/projects");
        }

        File legacyResources = new File(root, "resources");
        File newResources = new File(new File(root, "shared"), "resources");
        if (legacyResources.isDirectory() && !legacyResources.equals(newResources)) {
            ensure(newResources);
            int moved = moveChildren(legacyResources, newResources);
            //noinspection ResultOfMethodCallIgnored
            legacyResources.delete();
            AppLog.i("Migrated " + moved + " resource folder(s) to shared/resources");
        }
    }

    private static int moveChildren(File sourceDir, File targetDir) {
        File[] children = sourceDir.listFiles();
        if (children == null) {
            return 0;
        }
        int moved = 0;
        for (File child : children) {
            File target = new File(targetDir, child.getName());
            if (target.exists()) {
                // Never clobber a newer copy; leave the legacy folder for manual recovery.
                AppLog.w(TAG, "Migration skipped existing target: " + target.getName());
                continue;
            }
            if (child.renameTo(target)) {
                moved++;
                continue;
            }
            try {
                copyRecursive(child, target);
                deleteRecursive(child);
                moved++;
            } catch (IOException e) {
                AppLog.w(TAG, "Migration failed for " + child.getName() + ": " + e.getMessage());
            }
        }
        return moved;
    }

    private static void copyRecursive(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) {
                throw new IOException("Cannot create " + target.getAbsolutePath());
            }
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyRecursive(child, new File(target, child.getName()));
                }
            }
            return;
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

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    // ==================== Helpers ====================

    private static File ensure(File dir) {
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory()) {
            AppLog.w(TAG, "Unable to create directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    /** Keeps a project id from escaping its parent directory. */
    private static String safeSegment(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unknown";
        }
        return value.replace('/', '_').replace('\\', '_').replace("..", "_");
    }
}
