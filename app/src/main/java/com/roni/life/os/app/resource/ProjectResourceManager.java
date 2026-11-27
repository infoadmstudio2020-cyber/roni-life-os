package com.roni.life.os.app.resource;

import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectStore;
import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns the resource lifecycle of every project: download once, verify always, reuse forever.
 *
 * <p>The contract is deliberately narrow. {@link #ensureReady} is the only entry point the runtime
 * needs, and it is idempotent:</p>
 *
 * <ol>
 *   <li>Rebuild the manifest from the project configuration.</li>
 *   <li>Re-verify every recorded resource against the real file on disk.</li>
 *   <li>Fetch only what is missing, corrupt or on a different version.</li>
 *   <li>Extract an archive only when its entry document is not already unpacked.</li>
 *   <li>Persist the state and mark the project ready.</li>
 * </ol>
 *
 * <p>When step 2 passes for every resource, steps 3 and 4 do nothing at all — no network call, no
 * extraction, no file copy. That is the "clean open" path.</p>
 *
 * <p>Two rules keep that path reachable in practice:</p>
 * <ul>
 *   <li>An archive whose content is already unpacked is never fetched again, even when its own
 *       record was lost — the project opens from the unpacked tree.</li>
 *   <li>A remote document is revalidated by ETag only when its record is older than
 *       {@link #DOCUMENT_REVALIDATE_MS}, so a normal open performs no network call at all.</li>
 * </ul>
 *
 * <p>Nothing here deletes project files or user data. {@link #release(String)} only drops the
 * in-memory claim; {@link #invalidate(String)} only forgets the record so the next open
 * re-verifies. Permanent deletion stays with the explicit unregister/delete flow.</p>
 */
public final class ProjectResourceManager {

    private static final String TAG = "ProjectResources";

    /** How long a remote document is trusted before its ETag is probed again. */
    static final long DOCUMENT_REVALIDATE_MS = 6L * 60L * 60L * 1000L;

    /** Progress callback; invoked on the calling thread. */
    public interface Listener {
        void onProgress(String message, int percent);
    }

    /** Outcome of {@link #ensureReady}. */
    public static final class Result {
        private final boolean ready;
        private final String message;
        private final File localRoot;
        private final String entryPoint;
        private final boolean fromCache;
        private final int downloaded;
        private final int verified;
        private final boolean refreshRequired;
        private final String versionTag;

        Result(boolean ready, String message, File localRoot, String entryPoint,
               boolean fromCache, int downloaded, int verified,
               boolean refreshRequired, String versionTag) {
            this.ready = ready;
            this.message = message != null ? message : "";
            this.localRoot = localRoot;
            this.entryPoint = entryPoint != null ? entryPoint : "index.html";
            this.fromCache = fromCache;
            this.downloaded = downloaded;
            this.verified = verified;
            this.refreshRequired = refreshRequired;
            this.versionTag = versionTag != null ? versionTag : "";
        }

        public boolean isReady() {
            return ready;
        }

        public String getMessage() {
            return message;
        }

        /** @return the unpacked directory to serve, or {@code null} when the source is used as-is */
        public File getLocalRoot() {
            return localRoot;
        }

        public String getEntryPoint() {
            return entryPoint;
        }

        /** @return {@code true} when nothing had to be fetched or extracted */
        public boolean isFromCache() {
            return fromCache;
        }

        public int getDownloaded() {
            return downloaded;
        }

        public int getVerified() {
            return verified;
        }

        /**
         * @return {@code true} when a remote document changed and the runtime must load it from
         *         the network once instead of serving the cached copy
         */
        public boolean isRefreshRequired() {
            return refreshRequired;
        }

        /** @return the new remote tag (ETag) when {@link #isRefreshRequired()} is set */
        public String getVersionTag() {
            return versionTag;
        }

        @Override
        public String toString() {
            return "Result{ready=" + ready + ", fromCache=" + fromCache
                    + ", downloaded=" + downloaded + ", verified=" + verified
                    + ", refreshRequired=" + refreshRequired + "}";
        }
    }

    private final ResourceFetcher fetcher;
    private final File resourcesRoot;
    private final ProjectResourceStore store;
    private final ProjectStore projectStore;

    public ProjectResourceManager(ResourceFetcher fetcher, File resourcesRoot,
                                  ProjectResourceStore store, ProjectStore projectStore) {
        this.fetcher = fetcher;
        this.resourcesRoot = resourcesRoot;
        this.store = store;
        this.projectStore = projectStore;
    }

    // ==================== Public API ====================

    /** Ensures every resource of {@code project} is present and verified. */
    public Result ensureReady(Project project) {
        return ensureReady(project, ResourceManifestBuilder.build(project), null);
    }

    /** Ensures every resource in {@code manifest} is present and verified. */
    public Result ensureReady(Project project, ResourceManifest manifest) {
        return ensureReady(project, manifest, null);
    }

    /**
     * Ensures every resource in {@code manifest} is present and verified.
     *
     * <p>Exposed so a caller (or a test) can supply a manifest directly; the runtime always uses
     * the project-derived one.</p>
     */
    public Result ensureReady(Project project, ResourceManifest manifest, Listener listener) {
        if (project == null) {
            return failure("The project was not found.");
        }
        if (manifest == null) {
            manifest = ResourceManifestBuilder.build(project);
        }
        String projectId = project.getId();
        long now = System.currentTimeMillis();

        ProjectResourceState state = store.load(projectId);
        if (state == null) {
            state = new ProjectResourceState(projectId);
        }

        // Nothing to prepare: the project reads straight from its source.
        if (manifest.isEmpty()) {
            state.markReady(manifest.getVersion(), now);
            store.save(state);
            syncMetadata(project, state);
            return new Result(true, "No resources to prepare.", null, entryPoint(project),
                    true, 0, 0, false, "");
        }

        File contentDir = new File(projectDir(projectId), "content");
        File entryFile = new File(contentDir, entryPoint(project));
        boolean contentReady = entryFile.isFile();

        // 1. Re-verify what is already recorded. Metadata alone is never trusted.
        List<ResourceSpec> pending = new ArrayList<>();
        Map<String, String> documentTags = new HashMap<>();
        boolean refreshRequired = false;
        int verified = 0;
        for (ResourceSpec spec : manifest.getSpecs()) {
            ResourceRecord record = state.get(spec.getId());

            // An archive that is already unpacked never needs to be fetched again, even when its
            // own record was lost: the project opens from the unpacked tree.
            if (spec.isExtract() && contentReady) {
                if (record == null) {
                    record = new ResourceRecord(spec.getId());
                    state.put(record);
                }
                record.setVersion(spec.getVersion());
                record.touchVerified(now);
                verified++;
                continue;
            }

            if (record == null || !record.isVerified()) {
                pending.add(spec);
                continue;
            }
            if (!spec.getVersion().equals(record.getVersion())) {
                // The manifest moved on: only this resource is refreshed.
                pending.add(spec);
                continue;
            }
            if (spec.isDocument()) {
                // A remote document is loaded by the WebView itself. Its ETag is probed only
                // when the record is stale, so a normal open performs no network call at all.
                boolean stale = now - record.getLastVerifiedAt() >= DOCUMENT_REVALIDATE_MS;
                String tag = stale ? fetcher.remoteTag(spec) : "";
                if (stale && !tag.isEmpty() && !tag.equalsIgnoreCase(record.getChecksum())) {
                    documentTags.put(spec.getId(), tag);
                    refreshRequired = true;
                    pending.add(spec);
                } else {
                    record.touchVerified(now);
                    verified++;
                }
                continue;
            }
            File file = new File(record.getLocalPath());
            ResourceVerifier.Result check = ResourceVerifier.verify(
                    file, record.getSizeBytes(), record.getChecksum());
            if (check.isOk()) {
                record.touchVerified(now);
                verified++;
            } else {
                record.mark(check == ResourceVerifier.Result.MISSING
                        ? ResourceRecord.Status.MISSING : ResourceRecord.Status.CORRUPT);
                AppLog.w(TAG, "Resource " + spec.getId() + " of " + projectId
                        + " failed verification: " + check);
                pending.add(spec);
            }
        }

        // 2. Fetch only the resources that are missing, corrupt or changed.
        int downloaded = 0;
        for (int index = 0; index < pending.size(); index++) {
            ResourceSpec spec = pending.get(index);
            notify(listener, "Preparing " + spec.getId() + "…",
                    percent(index, pending.size()));

            if (spec.isDocument()) {
                String tag = documentTags.get(spec.getId());
                state.put(ResourceRecord.verified(spec, "", 0L, tag != null ? tag : "", now));
                continue;
            }

            File destination = new File(projectDir(projectId), spec.getRelativePath());
            try {
                fetcher.fetch(spec, destination);
            } catch (Throwable error) {
                AppLog.e(TAG, "Unable to prepare " + spec.getId() + " for " + projectId, error);
                state.markIncomplete(manifest.getVersion(), now);
                store.save(state);
                syncMetadata(project, state);
                return failure("The resource could not be prepared: " + describe(error));
            }

            ResourceVerifier.Result check = ResourceVerifier.verify(
                    destination, spec.getSizeBytes(), spec.getChecksum());
            if (!check.isOk()) {
                state.markIncomplete(manifest.getVersion(), now);
                store.save(state);
                syncMetadata(project, state);
                return failure("The resource failed verification: " + check);
            }

            String checksum = spec.getChecksum() != null && !spec.getChecksum().isEmpty()
                    ? spec.getChecksum() : ResourceVerifier.sha256(destination);
            state.put(ResourceRecord.verified(spec, destination.getAbsolutePath(),
                    destination.length(), checksum, now));
            downloaded++;
        }

        // 3. Extract once. An already-unpacked entry document is reused untouched.
        File localRoot = null;
        boolean extracted = false;
        for (ResourceSpec spec : manifest.getSpecs()) {
            if (!spec.isExtract()) {
                continue;
            }
            ResourceRecord record = state.get(spec.getId());
            if (record == null || !record.isVerified()) {
                continue;
            }
            File entry = new File(contentDir, entryPoint(project));
            if (!entry.isFile()) {
                notify(listener, "Unpacking…", 90);
                try {
                    ArchiveExtractor.extract(new File(record.getLocalPath()), contentDir);
                    extracted = true;
                } catch (Throwable error) {
                    AppLog.e(TAG, "Unable to unpack " + spec.getId() + " for " + projectId, error);
                    state.markIncomplete(manifest.getVersion(), now);
                    store.save(state);
                    syncMetadata(project, state);
                    return failure("The archive could not be unpacked: " + describe(error));
                }
            }
            localRoot = contentDir;
        }

        // 4. Persist the verified state.
        state.retainOnly(ids(manifest));
        state.markReady(manifest.getVersion(), now);
        store.save(state);
        syncMetadata(project, state);

        boolean fromCache = pending.isEmpty() && !extracted;
        String message = fromCache
                ? "Resources reused from local storage."
                : "Resources prepared (" + downloaded + " fetched).";
        AppLog.i(TAG, "Project " + projectId + ": " + message);
        return new Result(true, message, localRoot, entryPoint(project), fromCache,
                downloaded, verified, refreshRequired, firstTag(documentTags));
    }

    /** @return the persisted state, or {@code null} when the project was never initialized */
    public ProjectResourceState state(String projectId) {
        return store.load(projectId);
    }

    /**
     * Releases the runtime's claim on a project's resources.
     *
     * <p>Deliberately does not touch the file system: closing or minimising a project must never
     * delete downloaded resources or user data.</p>
     */
    public void release(String projectId) {
        AppLog.i(TAG, "Released resources of " + projectId + " (files kept)");
    }

    /**
     * Forgets the record so the next open re-verifies and re-fetches what is missing.
     *
     * <p>Only an explicit user action (re-download, unregister) may call this. Files are left in
     * place so a failed re-download can still fall back to the previous working copy.</p>
     */
    public void invalidate(String projectId) {
        store.clear(projectId);
        AppLog.i(TAG, "Invalidated resource record of " + projectId);
    }

    // ==================== Helpers ====================

    private File projectDir(String projectId) {
        File dir = new File(resourcesRoot, projectId);
        if (!dir.exists() && !dir.mkdirs()) {
            AppLog.w(TAG, "Unable to create " + dir.getAbsolutePath());
        }
        return dir;
    }

    private static String entryPoint(Project project) {
        String entry = project != null ? project.getEntryPoint() : null;
        return entry != null && !entry.trim().isEmpty() ? entry.trim() : "index.html";
    }

    private static Set<String> ids(ResourceManifest manifest) {
        Set<String> ids = new HashSet<>();
        for (ResourceSpec spec : manifest.getSpecs()) {
            ids.add(spec.getId());
        }
        return ids;
    }

    private static String firstTag(Map<String, String> tags) {
        for (String tag : tags.values()) {
            if (tag != null && !tag.isEmpty()) {
                return tag;
            }
        }
        return "";
    }

    private static int percent(int index, int total) {
        if (total <= 0) {
            return 0;
        }
        return Math.min(95, (int) ((index * 90L) / total));
    }

    private static void notify(Listener listener, String message, int percent) {
        if (listener != null) {
            listener.onProgress(message, percent);
        }
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        return error.getMessage() != null ? error.getMessage()
                : error.getClass().getSimpleName();
    }

    private static Result failure(String message) {
        return new Result(false, message, null, "index.html", false, 0, 0, false, "");
    }

    /**
     * Mirrors the resource status into the project metadata so the dashboard can show it without
     * loading the full resource record. Written only when the status actually changes, to avoid
     * touching {@code updatedAt} on every open.
     */
    private void syncMetadata(Project project, ProjectResourceState state) {
        if (projectStore == null || project == null || state == null) {
            return;
        }
        String status = state.getStatus().getKey();
        boolean statusChanged = !status.equals(project.getResourceStatus());
        boolean firstInit = project.getResourceInitializedAt() == 0L
                && state.getInitializedAt() != 0L;
        project.setResourceLastVerifiedAt(state.getLastVerifiedAt());
        if (!statusChanged && !firstInit) {
            return;
        }
        project.setResourceStatus(status);
        project.setResourceInitializedAt(state.getInitializedAt());
        try {
            projectStore.save(project);
        } catch (Throwable error) {
            AppLog.w(TAG, "Unable to persist resource metadata: " + error.getMessage());
        }
    }
}
