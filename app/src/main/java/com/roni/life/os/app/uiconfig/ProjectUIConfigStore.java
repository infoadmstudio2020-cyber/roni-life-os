package com.roni.life.os.app.uiconfig;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.ui.config.ProjectUIConfig;
import com.roni.library.ui.config.ProjectUIConfigPresets;
import com.roni.library.ui.config.ProjectUIConfigValidator;
import com.roni.life.os.app.core.ApplicationCore;
import com.roni.life.os.app.project.KeyValueBackend;
import com.roni.life.os.app.project.ProjectStore;
import com.roni.life.os.app.util.AppLog;

import java.util.List;

/**
 * Persists one {@link ProjectUIConfig} per project.
 *
 * <p>Storage key: {@code uiconfig.<projectId>}. Because the key is namespaced by project id, two
 * projects can never read each other's configuration — this is the isolation guarantee the brief
 * asks for.</p>
 *
 * <p>The store is deliberately tolerant: a missing, empty or corrupt record yields a fresh
 * default configuration instead of throwing, so a bad record can never block startup.</p>
 */
public class ProjectUIConfigStore {

    private static final String KEY_PREFIX = "uiconfig.";

    private final KeyValueBackend backend;

    public ProjectUIConfigStore(@Nullable KeyValueBackend backend) {
        this.backend = backend;
    }

    /** Store bound to the running application's storage backend. */
    @NonNull
    public static ProjectUIConfigStore forApp() {
        ProjectStore store = ApplicationCore.get().getStore();
        return new ProjectUIConfigStore(store != null ? store.getBackend() : null);
    }

    // ==================== Read / write ====================

    /** Loads the configuration for {@code projectId}; never returns {@code null}. */
    @NonNull
    public ProjectUIConfig load(@Nullable String projectId) {
        if (projectId == null || projectId.trim().isEmpty() || backend == null) {
            return new ProjectUIConfig();
        }
        String raw = backend.get(key(projectId), null);
        if (raw == null || raw.trim().isEmpty()) {
            return new ProjectUIConfig();
        }
        try {
            ProjectUIConfig config = ProjectUIConfig.decode(raw);
            List<ProjectUIConfigValidator.Issue> issues = ProjectUIConfigValidator.validate(config);
            for (ProjectUIConfigValidator.Issue issue : issues) {
                AppLog.w("UI config " + projectId + ": " + issue);
            }
            return config;
        } catch (Throwable error) {
            AppLog.e("Corrupt UI config for " + projectId + " — using defaults", error);
            return new ProjectUIConfig();
        }
    }

    /** Persists {@code config} for {@code projectId}. Returns false when storage is unavailable. */
    public boolean save(@Nullable String projectId, @Nullable ProjectUIConfig config) {
        if (projectId == null || projectId.trim().isEmpty() || config == null) {
            return false;
        }
        if (backend == null) {
            AppLog.w("UI config not saved: storage backend unavailable");
            return false;
        }
        try {
            backend.put(key(projectId), config.encode());
            return true;
        } catch (Throwable error) {
            AppLog.e("Unable to save UI config for " + projectId, error);
            return false;
        }
    }

    /** Removes the stored configuration so the project falls back to defaults. */
    public boolean reset(@Nullable String projectId) {
        if (projectId == null || projectId.trim().isEmpty() || backend == null) {
            return false;
        }
        return backend.remove(key(projectId));
    }

    /** True when a configuration record exists for {@code projectId}. */
    public boolean exists(@Nullable String projectId) {
        if (projectId == null || projectId.trim().isEmpty() || backend == null) {
            return false;
        }
        String raw = backend.get(key(projectId), null);
        return raw != null && !raw.trim().isEmpty();
    }

    // ==================== Presets ====================

    /** Applies a preset to {@code projectId} and persists it. */
    public boolean applyPreset(@Nullable String projectId, @Nullable String presetId) {
        ProjectUIConfig config = ProjectUIConfigPresets.create(presetId);
        return save(projectId, config);
    }

    // ==================== Import / export ====================

    /** Serialises the stored configuration for {@code projectId} for sharing. */
    @NonNull
    public String export(@Nullable String projectId) {
        return load(projectId).encode();
    }

    /**
     * Imports a configuration produced by {@link #export(String)}.
     *
     * <p>An unreadable payload is rejected and the existing record is left untouched.</p>
     */
    public boolean importFrom(@Nullable String projectId, @Nullable String raw) {
        if (projectId == null || projectId.trim().isEmpty()) {
            return false;
        }
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        ProjectUIConfig config;
        try {
            config = ProjectUIConfig.decode(raw);
        } catch (Throwable error) {
            AppLog.e("Unable to import UI config", error);
            return false;
        }
        if (!raw.contains("v=")) {
            // Every payload produced by encode() carries the schema marker. Without it the
            // string is not a configuration — refuse rather than overwrite the project.
            AppLog.w("Imported UI config has no schema marker — rejected");
            return false;
        }
        return save(projectId, config);
    }

    // ==================== Helpers ====================

    private static String key(String projectId) {
        return KEY_PREFIX + projectId;
    }
}
