package com.roni.life.os.app.runtime;

import com.roni.life.os.app.core.ApplicationCore;
import com.roni.life.os.app.project.KeyValueBackend;

import java.util.Collections;
import java.util.List;

/**
 * Persists the session list so a cold start can offer the projects that were open before the
 * process died.
 *
 * <p>This is a <em>recovery hint</em>, not a resurrection mechanism: restored entries are marked
 * {@link ProjectSessionManager.State#RECOVERABLE} and reopening one rebuilds the runtime from
 * scratch. Nothing here claims a WebView survived.</p>
 */
public final class SessionSnapshotStore {

    private static final String KEY = "sessions.snapshot.v1";

    private final KeyValueBackend backend;

    public SessionSnapshotStore(KeyValueBackend backend) {
        this.backend = backend;
    }

    /** Uses the app's shared key/value backend. */
    public static SessionSnapshotStore forApp() {
        try {
            return new SessionSnapshotStore(ApplicationCore.get().getStore().getBackend());
        } catch (Throwable error) {
            return new SessionSnapshotStore(null);
        }
    }

    /** Replaces the stored list with the current sessions. */
    public void save(List<SessionSnapshot> snapshots) {
        if (backend == null) {
            return;
        }
        try {
            String encoded = SessionSnapshot.encodeList(snapshots);
            if (encoded.isEmpty()) {
                backend.remove(KEY);
            } else {
                backend.put(KEY, encoded);
            }
        } catch (Throwable ignored) {
            // Storage unavailable — recovery is best effort.
        }
    }

    /** @return the stored sessions, never {@code null} */
    public List<SessionSnapshot> load() {
        if (backend == null) {
            return Collections.emptyList();
        }
        try {
            return SessionSnapshot.decodeList(backend.get(KEY, ""));
        } catch (Throwable error) {
            return Collections.emptyList();
        }
    }

    public void clear() {
        if (backend != null) {
            try {
                backend.remove(KEY);
            } catch (Throwable ignored) {
                // Nothing to clear.
            }
        }
    }
}
