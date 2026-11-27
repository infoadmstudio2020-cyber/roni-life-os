package com.roni.life.os.app.core;

import android.content.Context;

import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectStore;
import com.roni.life.os.app.util.AppLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

/**
 * Writes the human-readable JSON view of the storage tree.
 *
 * <p>{@code ProjectStore} remains the single source of truth — it owns the registry and the
 * app config. This class only mirrors that state onto disk so the layout described by
 * {@link StorageLayout} is inspectable and portable:</p>
 *
 * <pre>
 * system/config/app-config.json
 * system/config/storage-config.json
 * system/config/runtime-config.json
 * app/config/project-registry.json
 * app/config/active-project.json
 * app/projects/&lt;id&gt;/manifest.json
 * </pre>
 *
 * <p>Every write is best-effort: a failure is logged and never propagates, because a mirror
 * that cannot be written must not break the app that is already running from the store.</p>
 */
public final class StorageMirror {

    private static final String TAG = "StorageMirror";

    private final Context context;
    private final ProjectStore store;

    /** Guards against re-entry: {@code store.save()} can trigger a config write. */
    private boolean syncing;

    public StorageMirror(Context context, ProjectStore store) {
        this.context = context.getApplicationContext();
        this.store = store;
    }

    /** Rewrites every mirror file. Safe to call on any thread and as often as needed. */
    public void syncAll() {
        if (syncing) {
            return;
        }
        syncing = true;
        try {
            AppConfig config = store.loadConfig();
            write(StorageLayout.storageConfigFile(context), storageConfigJson());
            write(StorageLayout.appConfigFile(context), appConfigJson(config));
            write(StorageLayout.runtimeConfigFile(context), runtimeConfigJson(config));
            write(StorageLayout.projectRegistryFile(context), registryJson());
            write(StorageLayout.activeProjectFile(context), activeProjectJson(config));
            writeManifests();
        } catch (Throwable t) {
            AppLog.w(TAG, "Mirror sync failed: " + t.getMessage());
        } finally {
            syncing = false;
        }
    }

    // ==================== Documents ====================

    private String storageConfigJson() {
        return new Json()
                .begin()
                .put("layoutVersion", StorageLayout.LAYOUT_VERSION)
                .put("root", StorageLayout.ROOT)
                .put("projectsRoot", "app/projects")
                .put("resourcesRoot", "shared/resources")
                .put("registryFile", "app/config/project-registry.json")
                .put("activeProjectFile", "app/config/active-project.json")
                .put("appConfigFile", "system/config/app-config.json")
                .put("runtimeConfigFile", "system/config/runtime-config.json")
                .put("workspaceRoot", "runtime/workspace")
                .put("backupsRoot", "app/backups")
                .put("importsRoot", "app/imports")
                .put("updatedAt", System.currentTimeMillis())
                .end()
                .toString();
    }

    private String appConfigJson(AppConfig config) {
        return new Json()
                .begin()
                .put("splashEnabled", config.isSplashEnabled())
                .put("bootstrapEnabled", config.isBootstrapEnabled())
                .put("splashDurationMs", config.getSplashDurationMs())
                .put("splashMode", config.getSplashMode())
                .put("splashLogoText", config.getSplashLogoText())
                .put("splashLogoDrawable", config.getSplashLogoDrawable())
                .put("splashBackgroundColor", config.getSplashBackgroundColor())
                .put("splashAccentColor", config.getSplashAccentColor())
                .put("splashAnimation", config.getSplashAnimation())
                .put("splashFullscreen", config.isSplashFullscreen())
                .put("splashSkipOnTap", config.isSplashSkipOnTap())
                .put("dashboardMode", config.getDashboardMode())
                .put("dashboardEnabled", config.isDashboardEnabled())
                .put("dashboardToolbarEnabled", config.isDashboardToolbarEnabled())
                .put("dashboardFabEnabled", config.isDashboardFabEnabled())
                .put("dashboardShowStats", config.isDashboardShowStats())
                .put("dashboardShowRecent", config.isDashboardShowRecent())
                .put("dashboardFullscreen", config.isDashboardFullscreen())
                .put("dashboardBackgroundColor", config.getDashboardBackgroundColor())
                .put("dashboardAccentColor", config.getDashboardAccentColor())
                .put("themeMode", config.getThemeMode())
                .put("telemetryEnabled", config.isTelemetryEnabled())
                .put("debugEnabled", config.isDebugEnabled())
                .put("seeded", config.isSeeded())
                .put("updatedAt", System.currentTimeMillis())
                .end()
                .toString();
    }

    private String runtimeConfigJson(AppConfig config) {
        return new Json()
                .begin()
                .put("layoutVersion", StorageLayout.LAYOUT_VERSION)
                .put("workspaceRoot", StorageLayout.workspaceDir(context).getAbsolutePath())
                .put("cacheRoot", StorageLayout.cacheDir(context).getAbsolutePath())
                .put("sessionsRoot", StorageLayout.sessionsDir(context).getAbsolutePath())
                .put("logsRoot", StorageLayout.logsDir(context).getAbsolutePath())
                .put("configRoot", StorageLayout.runtimeConfigDir(context).getAbsolutePath())
                .put("debugEnabled", config.isDebugEnabled())
                .put("telemetryEnabled", config.isTelemetryEnabled())
                .put("updatedAt", System.currentTimeMillis())
                .end()
                .toString();
    }

    private String registryJson() {
        List<Project> projects = store.getAll();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"layoutVersion\": ").append(StorageLayout.LAYOUT_VERSION);
        sb.append(",\n  \"count\": ").append(projects.size());
        sb.append(",\n  \"updatedAt\": ").append(System.currentTimeMillis());
        sb.append(",\n  \"projects\": [");
        for (int i = 0; i < projects.size(); i++) {
            Project project = projects.get(i);
            sb.append(i == 0 ? "\n" : ",\n");
            sb.append("    {")
                    .append("\"id\": ").append(Json.quote(project.getId())).append(", ")
                    .append("\"name\": ").append(Json.quote(project.getName())).append(", ")
                    .append("\"type\": ").append(Json.quote(project.getProjectType().name())).append(", ")
                    .append("\"sourceType\": ").append(Json.quote(project.getSourceType().name())).append(", ")
                    .append("\"entryPoint\": ").append(Json.quote(project.getEntryPoint())).append(", ")
                    .append("\"directory\": ").append(Json.quote(
                            StorageLayout.projectDir(context, project.getId()).getAbsolutePath()))
                    .append("}");
        }
        sb.append(projects.isEmpty() ? "]\n}" : "\n  ]\n}");
        return sb.toString();
    }

    private String activeProjectJson(AppConfig config) {
        return new Json()
                .begin()
                .put("defaultProjectId", config.getDefaultProjectId())
                .put("lastOpenedProjectId", config.getLastOpenedProjectId())
                .put("activeSplashProjectId", config.getActiveSplashProjectId())
                .put("activeBootstrapProjectId", config.getActiveBootstrapProjectId())
                .put("activeWorkspaceProjectId", config.getActiveWorkspaceProjectId())
                .put("defaultWebProjectId", config.getDefaultWebProjectId())
                .put("updatedAt", System.currentTimeMillis())
                .end()
                .toString();
    }

    private void writeManifests() {
        for (Project project : store.getAll()) {
            if (project.getId() == null || project.getId().trim().isEmpty()) {
                continue;
            }
            StorageLayout.ensureProjectDirs(context, project.getId());
            write(StorageLayout.projectManifestFile(context, project.getId()), manifestJson(project));
        }
    }

    private String manifestJson(Project project) {
        return new Json()
                .begin()
                .put("id", project.getId())
                .put("name", project.getName())
                .put("description", project.getDescription())
                .put("type", project.getProjectType().name())
                .put("default", project.isDefault())
                .put("layoutVersion", StorageLayout.LAYOUT_VERSION)
                .putRaw("source", new Json()
                        .begin()
                        .put("type", project.getSourceType().name())
                        .put("path", project.getSourcePath())
                        .put("entryPoint", project.getEntryPoint())
                        .put("baseUrl", project.getBaseUrl())
                        .end()
                        .toString())
                .putRaw("splash", new Json()
                        .begin()
                        .put("enabled", project.isSplashEnabled())
                        .put("durationMs", project.getSplashDurationMs())
                        .put("sourceType", project.getSplashSourceType().name())
                        .put("sourcePath", project.getSplashSourcePath())
                        .put("transition", project.getSplashTransition())
                        .end()
                        .toString())
                .putRaw("bootstrap", new Json()
                        .begin()
                        .put("enabled", project.isBootstrapEnabled())
                        .put("renderingMode", project.getBootstrapRenderingMode().name())
                        .put("atStartup", project.isBootstrapAtStartup())
                        .end()
                        .toString())
                .putRaw("resources", new Json()
                        .begin()
                        .put("status", project.getResourceStatus())
                        .put("version", project.getResourceVersion())
                        .put("checksum", project.getResourceChecksum())
                        .put("initializedAt", project.getResourceInitializedAt())
                        .put("lastVerifiedAt", project.getResourceLastVerifiedAt())
                        .end()
                        .toString())
                .putRaw("timestamps", new Json()
                        .begin()
                        .put("createdAt", project.getCreatedAt())
                        .put("updatedAt", project.getUpdatedAt())
                        .put("lastOpenedAt", project.getLastOpenedAt())
                        .end()
                        .toString())
                .end()
                .toString();
    }

    // ==================== IO ====================

    private void write(File target, String content) {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            AppLog.w(TAG, "Cannot create " + parent.getAbsolutePath());
            return;
        }
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(target), "UTF-8");
            writer.write(content);
            writer.flush();
        } catch (IOException e) {
            AppLog.w(TAG, "Cannot write " + target.getName() + ": " + e.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // Nothing useful to do on close failure.
                }
            }
        }
    }

    // ==================== Minimal JSON writer ====================

    /** Tiny ordered JSON object builder — no external dependency, no reflection. */
    private static final class Json {

        private final StringBuilder sb = new StringBuilder();
        private boolean first = true;

        Json begin() {
            sb.append('{');
            first = true;
            return this;
        }

        Json put(String key, String value) {
            separator();
            sb.append(quote(key)).append(':').append(value == null ? "null" : quote(value));
            return this;
        }

        Json put(String key, long value) {
            separator();
            sb.append(quote(key)).append(':').append(value);
            return this;
        }

        Json put(String key, boolean value) {
            separator();
            sb.append(quote(key)).append(':').append(value);
            return this;
        }

        Json putRaw(String key, String rawJson) {
            separator();
            sb.append(quote(key)).append(':').append(rawJson);
            return this;
        }

        Json end() {
            sb.append('}');
            return this;
        }

        private void separator() {
            if (!first) {
                sb.append(',');
            }
            first = false;
        }

        static String quote(String value) {
            if (value == null) {
                return "null";
            }
            StringBuilder out = new StringBuilder(value.length() + 2);
            out.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"':
                        out.append("\\\"");
                        break;
                    case '\\':
                        out.append("\\\\");
                        break;
                    case '\n':
                        out.append("\\n");
                        break;
                    case '\r':
                        out.append("\\r");
                        break;
                    case '\t':
                        out.append("\\t");
                        break;
                    default:
                        if (c < 0x20) {
                            out.append(String.format("\\u%04x", (int) c));
                        } else {
                            out.append(c);
                        }
                }
            }
            out.append('"');
            return out.toString();
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
