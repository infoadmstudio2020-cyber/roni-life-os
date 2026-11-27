package com.roni.life.os.app.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Canonical description of the RONiLiFEOS storage tree.
 *
 * <p>Pure Java on purpose: the initializer needs the same list the tests assert against, and
 * keeping it free of Android imports lets the whole contract run on the JVM.</p>
 *
 * <p>Every path is relative to the storage root
 * ({@code Android/data/<package>/files/RONiLiFEOS}).</p>
 */
public final class StorageSpec {

    /** Bumped when the required tree changes; recorded in {@code initialization.json}. */
    public static final int INIT_VERSION = 2;

    /** Id of the project generated on first launch. */
    public static final String DEFAULT_PROJECT_ID = "default";

    /** Directories that must exist for the tree to be considered complete. */
    public static final String[] REQUIRED_DIRS = {
            "system/splash/default",
            "system/splash/default/css",
            "system/splash/default/js",
            "system/splash/default/assets",
            "system/splash/runtime",
            "system/splash/runtime/css",
            "system/splash/runtime/js",
            "system/splash/runtime/assets",
            "system/splash/config",
            "system/bootstrap/default",
            "system/bootstrap/default/native",
            "system/bootstrap/default/web",
            "system/bootstrap/runtime",
            "system/bootstrap/runtime/native",
            "system/bootstrap/runtime/web",
            "system/bootstrap/config",
            "system/config",
            "system/database",
            "app/projects",
            "app/projects/default",
            "app/projects/default/ui",
            "app/projects/default/sources",
            "app/projects/default/config",
            "app/projects/default/resources",
            "app/config",
            "app/backups",
            "app/imports",
            "runtime/workspace",
            "runtime/cache",
            "runtime/sessions",
            "runtime/logs",
            "runtime/config",
            "shared/assets",
            "shared/themes",
            "shared/resources"
    };

    /**
     * Files that must exist for the tree to be considered complete.
     *
     * <p>{@code system/config/initialization.json} is deliberately absent: it is the marker
     * written <em>after</em> validation, so it can never be part of the validation itself.</p>
     */
    public static final String[] REQUIRED_FILES = {
            "system/splash/default/index.html",
            "system/splash/default/manifest.json",
            "system/splash/default/css/splash.css",
            "system/splash/default/js/splash.js",
            "system/splash/config/splash-config.json",
            "system/bootstrap/default/manifest.json",
            "system/bootstrap/default/web/index.html",
            "system/bootstrap/config/bootstrap-config.json",
            "system/config/app-config.json",
            "system/config/storage-config.json",
            "system/config/runtime-config.json",
            "system/database/schema.json",
            "app/config/project-registry.json",
            "app/config/active-project.json",
            "app/projects/default/manifest.json",
            "app/projects/default/ui/index.html",
            "app/projects/default/config/project-config.json",
            "runtime/config/runtime.json",
            "shared/themes/default-theme.json"
    };

    private StorageSpec() {
    }

    /** Relative paths of every required directory, in declaration order. */
    public static List<String> requiredDirectories() {
        List<String> paths = new ArrayList<>(REQUIRED_DIRS.length);
        Collections.addAll(paths, REQUIRED_DIRS);
        return paths;
    }

    /** Relative paths of every required file, in declaration order. */
    public static List<String> requiredFiles() {
        List<String> paths = new ArrayList<>(REQUIRED_FILES.length);
        Collections.addAll(paths, REQUIRED_FILES);
        return paths;
    }

    /** Required directories that are missing under {@code root}. */
    public static List<String> missingDirectories(File root) {
        List<String> missing = new ArrayList<>();
        if (root == null) {
            Collections.addAll(missing, REQUIRED_DIRS);
            return missing;
        }
        for (String path : REQUIRED_DIRS) {
            if (!new File(root, path).isDirectory()) {
                missing.add(path);
            }
        }
        return missing;
    }

    /** Required files that are missing (or empty) under {@code root}. */
    public static List<String> missingFiles(File root) {
        List<String> missing = new ArrayList<>();
        if (root == null) {
            Collections.addAll(missing, REQUIRED_FILES);
            return missing;
        }
        for (String path : REQUIRED_FILES) {
            File file = new File(root, path);
            if (!file.isFile() || file.length() == 0L) {
                missing.add(path);
            }
        }
        return missing;
    }

    /** True when every required directory and file exists under {@code root}. */
    public static boolean isComplete(File root) {
        return missingDirectories(root).isEmpty() && missingFiles(root).isEmpty();
    }
}
