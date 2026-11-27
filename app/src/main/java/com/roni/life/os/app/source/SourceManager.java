package com.roni.life.os.app.source;

import android.content.Context;

import com.roni.life.os.app.resource.ArchiveExtractor;
import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lifecycle operations for the user-controlled Runtime source.
 *
 * <p>Every operation is scoped to one {@link SourceKind}, so Splash and Bootstrap can never
 * affect each other. The Default copy is read-only here: it is only ever read as a template or
 * as a fallback.</p>
 *
 * <p>Imports are staged in a sibling folder and promoted only after validation, so a broken
 * archive leaves the previous runtime source untouched.</p>
 */
public final class SourceManager {

    private static final String TAG = "SourceManager";
    private static final String STAGING = "runtime-staging";
    private static final int MAX_DEPTH = 16;

    private SourceManager() {
    }

    /** Outcome of one operation. */
    public static final class Result {

        private final boolean success;
        private final String message;

        Result(boolean success, String message) {
            this.success = success;
            this.message = message != null ? message : "";
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return (success ? "ok: " : "failed: ") + message;
        }
    }

    // ==================== Create / Import ====================

    /** Creates the runtime source as a copy of the default one. */
    public static Result create(Context context, SourceKind kind) {
        File source = SourceResolver.defaultDir(context, kind);
        File target = SourceResolver.runtimeDir(context, kind);
        if (!SourceValidator.hasContent(source)) {
            return new Result(false, "The default source is empty.");
        }
        try {
            int copied = copyTree(source, target, 0);
            if (copied == 0) {
                return new Result(false, "Nothing was copied.");
            }
            return validate(context, kind);
        } catch (IOException e) {
            AppLog.e(TAG, "Create failed", e);
            return new Result(false, "Create failed: " + e.getMessage());
        }
    }

    /** Imports a ZIP archive as the runtime source. */
    public static Result importZip(Context context, SourceKind kind, InputStream archive) {
        if (archive == null) {
            return new Result(false, "No archive supplied.");
        }
        return stageAndPromote(context, kind, archive, "Import");
    }

    /** Imports a local folder as the runtime source. */
    public static Result importFolder(Context context, SourceKind kind, File folder) {
        if (folder == null || !folder.isDirectory()) {
            return new Result(false, "The selected folder is not readable.");
        }
        File target = SourceResolver.runtimeDir(context, kind);
        File staging = stagingDir(context, kind);
        try {
            deleteRecursively(staging);
            if (!staging.mkdirs() && !staging.isDirectory()) {
                return new Result(false, "Unable to create the staging folder.");
            }
            if (copyTree(folder, staging, 0) == 0) {
                return new Result(false, "The selected folder is empty.");
            }
            return promote(context, kind, staging, "Import");
        } catch (IOException e) {
            deleteRecursively(staging);
            return new Result(false, "Import failed: " + e.getMessage());
        }
    }

    /** Replaces the runtime content with a new archive, keeping the configuration. */
    public static Result update(Context context, SourceKind kind, InputStream archive) {
        if (archive == null) {
            return new Result(false, "No archive supplied.");
        }
        return stageAndPromote(context, kind, archive, "Update");
    }

    /** Full replacement — identical mechanics to {@link #update}, kept as a distinct intent. */
    public static Result replace(Context context, SourceKind kind, InputStream archive) {
        return update(context, kind, archive);
    }

    // ==================== Validation / activation ====================

    /** Validates the runtime source without changing the active selection. */
    public static Result validate(Context context, SourceKind kind) {
        SourceConfig config = SourceConfigStore.load(context, kind);
        SourceValidator.Validation validation =
                SourceValidator.validate(SourceResolver.runtimeDir(context, kind),
                        config.getRuntimeEntry());
        return new Result(validation.isValid(), validation.getMessage());
    }

    /** Makes the runtime source active. Refuses when it does not validate. */
    public static Result activate(Context context, SourceKind kind) {
        Result validation = validate(context, kind);
        if (!validation.isSuccess()) {
            return new Result(false, "Cannot activate: " + validation.getMessage());
        }
        SourceConfig config = SourceConfigStore.load(context, kind);
        config.setActiveSource(SourceSlot.RUNTIME);
        return SourceConfigStore.save(context, kind, config)
                ? new Result(true, "Runtime source activated.")
                : new Result(false, "Unable to save the configuration.");
    }

    /** Switches back to the default source without touching runtime files. */
    public static Result deactivate(Context context, SourceKind kind) {
        SourceConfig config = SourceConfigStore.load(context, kind);
        config.setActiveSource(SourceSlot.DEFAULT);
        return SourceConfigStore.save(context, kind, config)
                ? new Result(true, "Default source activated.")
                : new Result(false, "Unable to save the configuration.");
    }

    /** Disconnects the runtime source but keeps its files on disk. */
    public static Result remove(Context context, SourceKind kind) {
        SourceConfig config = SourceConfigStore.load(context, kind);
        config.setActiveSource(SourceSlot.DEFAULT);
        config.setRuntimeEntry("index.html");
        return SourceConfigStore.save(context, kind, config)
                ? new Result(true, "Runtime source disconnected; files kept.")
                : new Result(false, "Unable to save the configuration.");
    }

    /** Deletes the runtime files and switches back to the default source. */
    public static Result delete(Context context, SourceKind kind) {
        File runtime = SourceResolver.runtimeDir(context, kind);
        deleteRecursively(runtime);
        if (!runtime.mkdirs() && !runtime.isDirectory()) {
            return new Result(false, "Unable to recreate the runtime folder.");
        }
        SourceConfig config = SourceConfigStore.load(context, kind);
        config.setActiveSource(SourceSlot.DEFAULT);
        SourceConfigStore.save(context, kind, config);
        return new Result(true, "Runtime source deleted.");
    }

    /** Returns to the default source and re-enables fallback. */
    public static Result reset(Context context, SourceKind kind) {
        SourceConfig config = SourceConfigStore.load(context, kind);
        config.setActiveSource(SourceSlot.DEFAULT);
        config.setFallbackEnabled(true);
        config.setRuntimeEntry("index.html");
        return SourceConfigStore.save(context, kind, config)
                ? new Result(true, "Reset to the default source.")
                : new Result(false, "Unable to save the configuration.");
    }

    /** Sets the execution mode (Bootstrap only). */
    public static Result setMode(Context context, SourceKind kind, String mode) {
        if (!kind.hasMode()) {
            return new Result(false, kind.getLabel() + " has no execution mode.");
        }
        SourceConfig config = SourceConfigStore.load(context, kind);
        config.setActiveMode(mode);
        return SourceConfigStore.save(context, kind, config)
                ? new Result(true, "Mode set to " + config.getActiveMode() + ".")
                : new Result(false, "Unable to save the configuration.");
    }

    /** Enables or disables fallback to the default source. */
    public static Result setFallback(Context context, SourceKind kind, boolean enabled) {
        SourceConfig config = SourceConfigStore.load(context, kind);
        config.setFallbackEnabled(enabled);
        return SourceConfigStore.save(context, kind, config)
                ? new Result(true, "Fallback " + (enabled ? "enabled" : "disabled") + ".")
                : new Result(false, "Unable to save the configuration.");
    }

    // ==================== Internals ====================

    private static Result stageAndPromote(Context context, SourceKind kind, InputStream archive,
                                          String label) {
        File staging = stagingDir(context, kind);
        try {
            deleteRecursively(staging);
            if (!staging.mkdirs() && !staging.isDirectory()) {
                return new Result(false, "Unable to create the staging folder.");
            }
            ArchiveExtractor.extract(archive, staging);
            if (!SourceValidator.hasContent(staging)) {
                deleteRecursively(staging);
                return new Result(false, "The archive is empty.");
            }
            return promote(context, kind, staging, label);
        } catch (Throwable error) {
            deleteRecursively(staging);
            AppLog.e(TAG, label + " failed", error);
            return new Result(false, label + " failed: " + error.getMessage());
        }
    }

    /** Validates the staged copy, then swaps it in. */
    private static Result promote(Context context, SourceKind kind, File staging, String label) {
        SourceConfig config = SourceConfigStore.load(context, kind);
        String entry = config.getRuntimeEntry();
        SourceValidator.Validation validation = SourceValidator.validate(staging, entry);
        if (!validation.isValid()) {
            deleteRecursively(staging);
            return new Result(false, label + " rejected: " + validation.getMessage());
        }
        File target = SourceResolver.runtimeDir(context, kind);
        try {
            deleteRecursively(target);
            if (!target.mkdirs() && !target.isDirectory()) {
                throw new IOException("Unable to create " + target.getAbsolutePath());
            }
            File[] children = staging.listFiles();
            if (children != null) {
                for (File child : children) {
                    File destination = new File(target, child.getName());
                    if (!child.renameTo(destination)) {
                        copyTree(child, destination, 0);
                        deleteRecursively(child);
                    }
                }
            }
            deleteRecursively(staging);
            return new Result(true, label + " complete.");
        } catch (IOException e) {
            deleteRecursively(staging);
            return new Result(false, label + " failed: " + e.getMessage());
        }
    }

    private static File stagingDir(Context context, SourceKind kind) {
        File runtime = SourceResolver.runtimeDir(context, kind);
        return new File(runtime.getParentFile(), STAGING);
    }

    private static int copyTree(File source, File target, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("Source tree is too deep.");
        }
        if (source.isDirectory()) {
            if (!target.isDirectory() && !target.mkdirs() && !target.isDirectory()) {
                throw new IOException("Unable to create " + target.getAbsolutePath());
            }
            int copied = 0;
            File[] children = source.listFiles();
            if (children != null) {
                for (File child : children) {
                    copied += copyTree(child, new File(target, child.getName()), depth + 1);
                }
            }
            return copied;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Unable to create " + parent.getAbsolutePath());
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
        return 1;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
