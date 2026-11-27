package com.roni.life.os.app.project;

import com.roni.library.security.crypto.ProjectVault;
import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The sealing step of the registration pipeline.
 *
 * <p>This is the only place that calls {@link ProjectVault#sealTree}. It exists so the ordering
 * guarantees live in one class instead of being spread across the importer and the registration
 * service:</p>
 *
 * <ol>
 *   <li>decide whether sealing is allowed at all ({@link SealDecision});</li>
 *   <li>write a {@code .vault.pending} marker <b>before</b> the first byte is encrypted;</li>
 *   <li>seal the tree in place;</li>
 *   <li>verify the {@code .vault} marker exists;</li>
 *   <li>remove the pending marker.</li>
 * </ol>
 *
 * <p>If the process dies between 2 and 5, the next attempt sees a pending marker without a sealed
 * marker and refuses the project. That is the only safe outcome: {@code sealTree} converts files
 * in place, so an interrupted pass leaves a tree where some files are ciphertext and some are
 * plaintext, with no record of which is which. Resuming would double-encrypt the already-converted
 * files; serving would hand the WebView a mix of both. Discarding is the only correct recovery.</p>
 *
 * <p>Double encryption is prevented twice over: {@link SealDecision} returns
 * {@code SKIP_ALREADY_SEALED} when the marker is present, and {@link ProjectVault#sealTree} itself
 * returns early on the same marker.</p>
 */
public final class ProjectSealer {

    private static final String TAG = "ProjectSealer";

    private ProjectSealer() {
    }

    /** Outcome of a sealing attempt. */
    public static final class Result {
        private final SealDecision.Action action;
        private final String message;

        Result(SealDecision.Action action, String message) {
            this.action = action;
            this.message = message != null ? message : "";
        }

        public SealDecision.Action getAction() {
            return action;
        }

        /** @return true when the project's bytes are encrypted at rest after this call */
        public boolean isSealed() {
            return SealDecision.isEncryptedAtRest(action);
        }

        /** @return true when nothing was done because there was nothing to do */
        public boolean isSkipped() {
            return SealDecision.isSkip(action);
        }

        /** @return true when the project must not be registered */
        public boolean isRefused() {
            return SealDecision.isRefusal(action);
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Seals {@code directory} under {@code projectId}.
     *
     * @param directory the extracted project tree
     * @param projectId the final, stable project id — also the Keystore alias suffix
     * @throws IllegalStateException when the project must not be registered
     * @throws IOException           when sealing started but did not complete
     */
    public static Result seal(File directory, String projectId) throws Exception {
        if (directory == null || projectId == null || projectId.trim().isEmpty()) {
            throw new IllegalArgumentException("directory and projectId are required");
        }

        boolean sealed = ProjectVault.isSealed(directory);
        boolean pending = ProjectVault.isSealInterrupted(directory);
        boolean hasFiles = hasSealableFiles(directory);
        SealDecision.Action action = SealDecision.decide(
                ProjectVault.isAvailable(), sealed, pending, hasFiles);

        switch (action) {
            case SKIP_ALREADY_SEALED:
                // A completed pass whose cleanup was interrupted: the tree is fine, the stale
                // marker is not.
                clearPending(directory);
                AppLog.i(TAG, "Project " + projectId + " is already sealed");
                return new Result(action, "Already sealed.");

            case SKIP_NO_FILES:
                AppLog.i(TAG, "Project " + projectId + " has no local files to seal");
                return new Result(action, "Nothing to seal.");

            case REFUSE_INTERRUPTED:
                throw new IllegalStateException(
                        "A previous sealing pass was interrupted. The project must be imported again.");

            case REFUSE_UNAVAILABLE:
                throw new IllegalStateException(
                        "Encryption is unavailable on this device (Android 6.0 or newer is required).");

            case SEAL:
            default:
                break;
        }

        writePending(directory, projectId);
        try {
            ProjectVault.sealTree(projectId, directory);
        } catch (Throwable error) {
            // The pending marker stays behind on purpose: the tree is now mixed and must be
            // discarded rather than served or re-sealed.
            AppLog.e(TAG, "Sealing failed for " + projectId, error);
            if (error instanceof Exception) {
                throw (Exception) error;
            }
            throw new IOException("Sealing failed: " + error);
        }

        if (!ProjectVault.isSealed(directory)) {
            throw new IOException("Sealing did not complete for " + projectId);
        }

        clearPending(directory);
        AppLog.i(TAG, "Sealed project " + projectId);
        return new Result(action, "Sealed.");
    }

    /**
     * Brings a project tree to a deterministic state after a crash.
     *
     * <p>Three outcomes, no ambiguity:</p>
     *
     * <ul>
     *   <li><b>sealed + pending</b> — the pass completed and only the cleanup died. The stale
     *       pending marker is removed; the tree is served normally.</li>
     *   <li><b>pending, not sealed</b> — the pass died mid-way. The tree is deleted. It cannot be
     *       resumed (no per-file record) and cannot be served (mixed plaintext and ciphertext).</li>
     *   <li><b>neither</b> — nothing to do.</li>
     * </ul>
     *
     * <p>Also removes leftover {@code .sealing} and {@code .rotating} temporaries, which a crash
     * during a single-file write can leave behind.</p>
     *
     * @return a short description of what was done, for logging
     */
    public static String recover(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return "missing";
        }
        int temporaries = deleteTemporaries(directory);
        if (ProjectVault.isSealInterrupted(directory)) {
            AppLog.w(TAG, "Recovering interrupted project: " + directory.getAbsolutePath());
            discardInterrupted(directory);
            return "discarded";
        }
        if (ProjectVault.isSealed(directory)) {
            File pending = new File(directory, ProjectVault.MARKER_PENDING);
            if (pending.isFile()) {
                clearPending(directory);
                return "cleaned";
            }
            return temporaries > 0 ? "cleaned" : "intact";
        }
        return temporaries > 0 ? "cleaned" : "unsealed";
    }

    /** @return the number of leftover temporary files removed */
    private static int deleteTemporaries(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return 0;
        }
        int removed = 0;
        for (File child : children) {
            if (child.isDirectory()) {
                removed += deleteTemporaries(child);
            } else if (child.isFile() && isTemporary(child.getName())) {
                if (child.delete()) {
                    removed++;
                }
            }
        }
        return removed;
    }

    private static boolean isTemporary(String name) {
        return name.endsWith(".sealing") || name.endsWith(".rotating");
    }

    /**
     * Deletes a tree whose sealing was interrupted.
     *
     * <p>Called by the registration service when {@link #seal} refuses. The tree is unrecoverable,
     * so removing it is the only way to avoid leaving a half-encrypted project on disk.</p>
     */
    public static void discardInterrupted(File directory) {
        if (directory == null) {
            return;
        }
        AppLog.w(TAG, "Discarding interrupted project tree: " + directory.getAbsolutePath());
        ProjectImporter.deleteRecursively(directory);
    }

    /** @return true when {@code directory} holds at least one file that would be sealed */
    public static boolean hasSealableFiles(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return false;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                if (hasSealableFiles(child)) {
                    return true;
                }
            } else if (child.isFile() && !isMarker(child.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMarker(String name) {
        return ProjectVault.MARKER_SEALED.equals(name) || ProjectVault.MARKER_PENDING.equals(name);
    }

    private static void writePending(File directory, String projectId) throws IOException {
        File marker = new File(directory, ProjectVault.MARKER_PENDING);
        FileOutputStream out = new FileOutputStream(marker);
        try {
            out.write((projectId + "\n" + System.currentTimeMillis()).getBytes("UTF-8"));
            out.flush();
        } finally {
            out.close();
        }
    }

    private static void clearPending(File directory) {
        File marker = new File(directory, ProjectVault.MARKER_PENDING);
        if (marker.isFile() && !marker.delete()) {
            AppLog.w(TAG, "Unable to remove the pending marker in " + directory.getAbsolutePath());
        }
    }
}
