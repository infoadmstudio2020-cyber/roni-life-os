package com.roni.life.os.app.resource;

import com.roni.life.os.app.project.KeyValueBackend;
import com.roni.life.os.app.util.AppLog;

/**
 * Persists one {@link ProjectResourceState} per project.
 *
 * <p>Storage key: {@code resources.<projectId>}. This reuses the application's existing
 * {@link KeyValueBackend} — the same backend {@code ProjectStore} and {@code ProjectUIConfigStore}
 * already write to — so no second storage architecture is introduced.</p>
 *
 * <p>Decoding is deliberately tolerant: a missing or corrupt record yields {@code null} and the
 * manager simply re-initializes, which is always safe because initialization is idempotent.</p>
 */
public class ProjectResourceStore {

    private static final String TAG = "ProjectResourceStore";
    private static final String KEY_PREFIX = "resources.";

    private final KeyValueBackend backend;

    public ProjectResourceStore(KeyValueBackend backend) {
        this.backend = backend;
    }

    public ProjectResourceState load(String projectId) {
        if (backend == null || projectId == null || projectId.trim().isEmpty()) {
            return null;
        }
        String raw = backend.get(key(projectId), null);
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return ProjectResourceState.decode(raw);
        } catch (Throwable error) {
            AppLog.w(TAG, "Unable to decode resource state for " + projectId + ": "
                    + error.getMessage());
            return null;
        }
    }

    public void save(ProjectResourceState state) {
        if (backend == null || state == null || state.getProjectId().isEmpty()) {
            return;
        }
        try {
            backend.put(key(state.getProjectId()), state.encode());
        } catch (Throwable error) {
            AppLog.e(TAG, "Unable to save resource state for " + state.getProjectId(), error);
        }
    }

    /** Removes the record. Only an explicit unregister/delete may call this. */
    public void clear(String projectId) {
        if (backend == null || projectId == null || projectId.trim().isEmpty()) {
            return;
        }
        backend.remove(key(projectId));
    }

    private static String key(String projectId) {
        return KEY_PREFIX + projectId;
    }
}
