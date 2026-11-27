package com.roni.life.os.app.project;

import com.roni.library.security.crypto.DecryptedResourceCache;
import com.roni.library.security.crypto.ProjectVault;
import com.roni.library.ui.web.ResourceDecoder;

import java.io.File;

/**
 * Owns the process-wide decrypted-resource cache and decides which projects get a decoder.
 *
 * <p>Two rules keep the WebView pipeline honest:</p>
 *
 * <ul>
 *   <li><b>Only encrypted internal resources use a decoder.</b> {@link #decoderFor} returns
 *       {@code null} unless the project is a {@code FILE} source whose directory carries the
 *       {@code .vault} marker. A SAF folder, a remote URL, an APK asset and a legacy unsealed
 *       project all pass through untouched — their bytes are already plaintext and running them
 *       through AES/GCM would fail the tag check and 404 every request.</li>
 *   <li><b>Plaintext never outlives the project.</b> The cache is keyed by
 *       {@code projectId + '\0' + path}, so two projects with the same relative path cannot read
 *       each other's bytes, and every lifecycle event clears the project's entries.</li>
 * </ul>
 *
 * <p>The cache is a single instance for the process. It is bounded (8 MB by default) and holds
 * plaintext in RAM only — nothing is ever written back to disk.</p>
 */
public final class ProjectVaultRuntime {

    private static final DecryptedResourceCache CACHE = new DecryptedResourceCache();

    private ProjectVaultRuntime() {
    }

    /** The shared cache. Exposed for diagnostics and tests. */
    public static DecryptedResourceCache cache() {
        return CACHE;
    }

    /**
     * @return true when this project's files are encrypted at rest and must be decoded
     */
    public static boolean isVaulted(Project project) {
        if (project == null || project.getSourceType() != ProjectSourceType.FILE) {
            return false;
        }
        String path = project.getSourcePath();
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        return ProjectVault.isSealed(new File(path));
    }

    /**
     * @return a decoder for an encrypted internal project, or {@code null} when the project's
     *         bytes are already plaintext
     */
    public static ResourceDecoder decoderFor(Project project) {
        if (!isVaulted(project)) {
            return null;
        }
        File root = new File(project.getSourcePath());
        // The generation is read from the .vault marker, so a rotated project opens under the
        // key that actually sealed it rather than the one it started with.
        int generation = ProjectVault.currentGeneration(root);
        return new VaultResourceDecoder(project.getId(), generation, CACHE);
    }

    /**
     * Re-seals a project under a fresh key generation and drops its cached plaintext.
     *
     * <p>Called when a key may have been compromised. The cache is cleared first so no plaintext
     * produced by the old key survives the rotation.</p>
     */
    public static void rotateKey(Project project) throws Exception {
        if (!isVaulted(project)) {
            throw new IllegalStateException("Project is not sealed");
        }
        CACHE.clearProject(project.getId());
        ProjectVault.rotate(project.getId(), new File(project.getSourcePath()));
    }

    /** Drops a project's plaintext when its runtime closes. */
    public static void onProjectClosed(String projectId) {
        if (projectId != null) {
            CACHE.clearProject(projectId);
        }
    }

    /** Drops a project's plaintext when its files change. */
    public static void onProjectUpdated(String projectId) {
        if (projectId != null) {
            CACHE.clearProject(projectId);
        }
    }

    /**
     * Drops a project's plaintext and destroys its Keystore key.
     *
     * <p>Order matters: the cache is cleared first, so no plaintext survives in RAM after the key
     * that produced it is gone.</p>
     */
    public static void onProjectDeleted(String projectId) {
        if (projectId == null) {
            return;
        }
        CACHE.clearProject(projectId);
        ProjectVault.forget(projectId);
    }

    /** Drops every project's plaintext. Called when the runtime shuts down. */
    public static void clearAll() {
        CACHE.clearAll();
    }
}
