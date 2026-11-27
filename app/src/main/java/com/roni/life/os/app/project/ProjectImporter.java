package com.roni.life.os.app.project;

import android.content.Context;
import android.net.Uri;

import com.roni.life.os.app.resource.ArchiveExtractor;
import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * Imports a ZIP archive into the app's private project workspace.
 *
 * <p>Security rules (EX-01) live in {@link ArchiveExtractor}: every entry name is validated before
 * anything is written, and a rejected archive is deleted entirely — no partial extraction is left
 * behind.</p>
 */
public final class ProjectImporter {

    private static final String TAG = "ProjectImporter";

    /** Hard ceiling on the number of entries, to stop archive bombs. */
    public static final int MAX_ENTRIES = ArchiveExtractor.MAX_ENTRIES;

    /** Hard ceiling on the uncompressed size, to stop archive bombs. */
    public static final long MAX_TOTAL_BYTES = ArchiveExtractor.MAX_TOTAL_BYTES;

    private ProjectImporter() {
    }

    /** Outcome of an import. */
    public static final class Result {
        private final boolean success;
        private final String message;
        private final File directory;
        private final String entryPoint;

        Result(boolean success, String message, File directory, String entryPoint) {
            this.success = success;
            this.message = message != null ? message : "";
            this.directory = directory;
            this.entryPoint = entryPoint != null ? entryPoint : "index.html";
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public File getDirectory() {
            return directory;
        }

        public String getEntryPoint() {
            return entryPoint;
        }
    }

    /** Root of every imported project: {@code files/RONiLiFEOS/app/projects}. */
    public static File projectsRoot(Context context) {
        return com.roni.life.os.app.core.StorageLayout.projectsDir(context);
    }

    /**
     * Root of every project's downloaded resources: {@code files/RONiLiFEOS/shared/resources}.
     *
     * <p>Kept beside {@link #projectsRoot(Context)} so all project storage lives under one
     * directory and a single explicit delete can remove it.</p>
     */
    public static File resourcesRoot(Context context) {
        return com.roni.life.os.app.core.StorageLayout.sharedResourcesDir(context);
    }

    /**
     * @return {@code true} when {@code name} is a safe relative archive path
     */
    public static boolean isSafeEntryName(String name) {
        return ArchiveExtractor.isSafeEntryName(name);
    }

    /**
     * Extracts {@code zipUri} into a fresh directory under {@link #projectsRoot(Context)}.
     *
     * @param context     any context
     * @param zipUri      a {@code content://} or {@code file://} ZIP URI
     * @param projectName used only for log messages
     */
    public static Result importZip(Context context, Uri zipUri, String projectName) {
        if (context == null || zipUri == null) {
            return new Result(false, "No archive was selected.", null, null);
        }

        File root = projectsRoot(context);
        File target = new File(root, UUID.randomUUID().toString());
        if (!target.mkdirs()) {
            return new Result(false, "Unable to create the project directory.", null, null);
        }

        int entries;
        long totalBytes;
        try (InputStream raw = context.getContentResolver().openInputStream(zipUri)) {
            if (raw == null) {
                deleteRecursively(target);
                return new Result(false, "The archive could not be opened.", null, null);
            }
            long[] counts = ArchiveExtractor.extract(raw, target);
            entries = (int) counts[0];
            totalBytes = counts[1];
        } catch (SecurityException e) {
            deleteRecursively(target);
            AppLog.w(TAG, "Import rejected for '" + projectName + "': " + e.getMessage());
            return new Result(false, e.getMessage(), null, null);
        } catch (Exception e) {
            deleteRecursively(target);
            AppLog.e(TAG, "Import failed for '" + projectName + "'", e);
            return new Result(false, "The archive could not be extracted: " + e.getMessage(),
                    null, null);
        }

        ProjectManifest.Result manifest = ProjectManifest.validate(target);
        if (!manifest.isValid()) {
            deleteRecursively(target);
            AppLog.w(TAG, "Import rejected for '" + projectName + "': " + manifest.getMessage());
            return new Result(false, manifest.getMessage(), null, null);
        }

        String entryPoint = manifest.getEntryPoint() != null
                ? manifest.getEntryPoint()
                : detectEntryPoint(target);
        AppLog.i(TAG, "Imported '" + projectName + "' → " + target.getAbsolutePath()
                + " (" + entries + " entries, " + totalBytes + " bytes)");
        return new Result(true, "Imported " + entries + " files.", target, entryPoint);
    }

    /**
     * Extracts {@code archive} into {@code target} using the same entry validation as
     * {@link #importZip}.
     *
     * <p>Used by the resource manager to unpack a downloaded archive exactly once.</p>
     */
    public static void extract(File archive, File target) throws Exception {
        ArchiveExtractor.extract(archive, target);
    }

    /**
     * Picks the most likely entry document: {@code index.html} at the root, then any root HTML
     * file, then the shallowest HTML file in the archive.
     */
    public static String detectEntryPoint(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return "index.html";
        }
        File index = new File(directory, "index.html");
        if (index.isFile()) {
            return "index.html";
        }
        String shallowest = null;
        int shallowestDepth = Integer.MAX_VALUE;
        String[] names = directory.list();
        if (names != null) {
            for (String name : names) {
                if (name.toLowerCase(Locale.US).endsWith(".html")) {
                    return name;
                }
            }
        }
        shallowest = findHtml(directory, directory, 0, shallowest, shallowestDepth);
        return shallowest != null ? shallowest : "index.html";
    }

    private static String findHtml(File root, File current, int depth,
                                   String best, int bestDepth) {
        File[] children = current.listFiles();
        if (children == null) {
            return best;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                best = findHtml(root, child, depth + 1, best, bestDepth);
                continue;
            }
            if (!child.getName().toLowerCase(Locale.US).endsWith(".html")) {
                continue;
            }
            if (depth < bestDepth) {
                String relative = child.getAbsolutePath()
                        .substring(root.getAbsolutePath().length() + 1)
                        .replace(File.separatorChar, '/');
                best = relative;
                bestDepth = depth;
            }
        }
        return best;
    }

    /**
     * Seals an imported project tree with {@link ProjectVault}.
     *
     * <p>Called after extraction, once the final project id is known — the id is the Keystore
     * alias, so it cannot be applied before registration assigns it.</p>
     *
     * <p>Delegates to {@link ProjectSealer}, which owns the ordering guarantees: the pending
     * marker is written before the first byte is encrypted, the sealed marker is verified before
     * the call returns, and an already-sealed tree is never encrypted twice.</p>
     *
     * @throws IllegalStateException when the project must not be registered
     * @throws java.io.IOException   when sealing started but did not complete
     */
    public static void sealProject(File directory, String projectId) throws Exception {
        if (directory == null || projectId == null || projectId.isEmpty()) {
            throw new IllegalArgumentException("directory and projectId are required");
        }
        ProjectSealer.Result result = ProjectSealer.seal(directory, projectId);
        if (result.isRefused()) {
            throw new IllegalStateException(result.getMessage());
        }
    }

    /** Deletes a directory tree; used to purge a rejected or failed import. */
    public static void deleteRecursively(File file) {
        ArchiveExtractor.deleteRecursively(file);
    }
}
