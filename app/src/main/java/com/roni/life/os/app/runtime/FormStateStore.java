package com.roni.life.os.app.runtime;

import com.roni.life.os.app.project.KeyValueBackend;

import org.json.JSONObject;

/**
 * Persists uncommitted form input per project and per page.
 *
 * <p>Snapshots are keyed by {@code formstate.<projectId>.<urlHash>} and stored through the same
 * {@link KeyValueBackend} the project registry uses, so they survive process death without a
 * second storage mechanism.</p>
 *
 * <p>An empty snapshot removes the key instead of writing {@code "{}"} — that keeps the store
 * from accumulating dead entries every time a form is submitted and cleared.</p>
 */
public final class FormStateStore {

    private static final String KEY_PREFIX = "formstate.";

    /** Snapshots larger than this are dropped rather than bloating the key-value store. */
    private static final int MAX_LENGTH = 256 * 1024;

    private final KeyValueBackend backend;

    public FormStateStore(KeyValueBackend backend) {
        this.backend = backend;
    }

    /**
     * Stores a snapshot, or clears the entry when it holds no fields.
     *
     * @param projectId owning project
     * @param url       page the snapshot belongs to
     * @param snapshot  JSON produced by {@link FormStateScript#CAPTURE}
     */
    public void save(String projectId, String url, String snapshot) {
        if (projectId == null || projectId.trim().isEmpty()) {
            return;
        }
        if (snapshot == null || snapshot.length() > MAX_LENGTH) {
            return;
        }
        if (!hasFields(snapshot)) {
            clear(projectId, url);
            return;
        }
        try {
            backend.put(key(projectId, url), snapshot);
        } catch (Throwable ignored) {
            // Storage unavailable — the in-memory WebView still holds the live values.
        }
    }

    /**
     * @return the stored snapshot, or {@code null} when there is nothing to restore
     */
    public String load(String projectId, String url) {
        if (projectId == null || projectId.trim().isEmpty()) {
            return null;
        }
        try {
            return backend.get(key(projectId, url), null);
        } catch (Throwable t) {
            return null;
        }
    }

    public void clear(String projectId, String url) {
        if (projectId == null || projectId.trim().isEmpty()) {
            return;
        }
        try {
            backend.remove(key(projectId, url));
        } catch (Throwable ignored) {
            // Nothing to do.
        }
    }

    private static boolean hasFields(String snapshot) {
        try {
            JSONObject object = new JSONObject(snapshot);
            JSONObject fields = object.optJSONObject("fields");
            return fields != null && fields.length() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String key(String projectId, String url) {
        String safeUrl = url != null ? url : "";
        return KEY_PREFIX + projectId + "." + Integer.toHexString(safeUrl.hashCode());
    }
}
