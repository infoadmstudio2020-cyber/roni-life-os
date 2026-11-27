package com.roni.life.os.app.project;

import com.roni.life.os.app.core.AppConfig;
import com.roni.life.os.app.util.AppLog;

import java.util.ArrayList;
import java.util.List;

/**
 * The single authoritative default-project rule.
 *
 * <p>Before this class existed the default project was resolved from three competing sources
 * ({@code defaultWebProjectId}, the per-project {@code isDefault} flag and the legacy
 * {@code defaultProjectId} mirror), and the storage initializer rewrote the legacy mirror on
 * every launch. That is what made "Set as default" unreliable: the pointer the resolver read
 * first could still name the previous project.</p>
 *
 * <p><b>Rule.</b></p>
 * <ol>
 *   <li>{@code defaultWebProjectId} is the only authoritative value.</li>
 *   <li>The {@code isDefault} flag and {@code defaultProjectId} are legacy inputs. They are
 *       read only when the authoritative value is empty, and are migrated into it once.</li>
 *   <li>{@code lastOpenedProjectId} is never consulted. Opening a project must never change
 *       which project starts.</li>
 *   <li>No project is ever picked at random. When the configured project is missing or cannot
 *       be launched the resolution fails, so startup can show the configuration screen instead
 *       of silently opening something else.</li>
 * </ol>
 *
 * <p>No Android imports — every branch is unit-testable on the JVM.</p>
 */
public final class DefaultProjectResolver {

    private static final String TAG = "DefaultProject";

    /** Why a resolution succeeded or failed. */
    public enum Status {
        /** A launchable default project was resolved. */
        RESOLVED,
        /** Nothing is configured — the user has not chosen a default project yet. */
        NOT_CONFIGURED,
        /** The configured id no longer exists in the registry. */
        MISSING,
        /** The configured project exists but cannot be hosted as the application project. */
        NOT_LAUNCHABLE
    }

    /** The outcome of one resolution. */
    public static final class Resolution {
        private final Status status;
        private final Project project;
        private final String configuredId;
        private final String source;
        private final String detail;

        Resolution(Status status, Project project, String configuredId, String source,
                   String detail) {
            this.status = status;
            this.project = project;
            this.configuredId = configuredId != null ? configuredId : "";
            this.source = source != null ? source : "none";
            this.detail = detail != null ? detail : "";
        }

        public Status getStatus() {
            return status;
        }

        /** The resolved project, or {@code null} when the resolution failed. */
        public Project getProject() {
            return project;
        }

        public String getProjectId() {
            return project != null ? project.getId() : null;
        }

        /** The id that was configured, even when it no longer resolves. */
        public String getConfiguredId() {
            return configuredId;
        }

        /** Which input produced the result: {@code defaultWebProjectId}, a migrated legacy value. */
        public String getSource() {
            return source;
        }

        /** Human-readable failure reason, safe to show on the startup error screen. */
        public String getDetail() {
            return detail;
        }

        public boolean isResolved() {
            return status == Status.RESOLVED && project != null;
        }

        public boolean isFailure() {
            return !isResolved();
        }

        /** One-line description used by the startup error screen and the logs. */
        public String describe() {
            if (isResolved()) {
                return "default project " + project.getName() + " (" + project.getId() + ")";
            }
            return detail.isEmpty() ? status.name() : detail;
        }

        @Override
        public String toString() {
            return "Resolution{" + status + ", id=" + getProjectId() + ", source=" + source + "}";
        }
    }

    private DefaultProjectResolver() {
    }

    /**
     * Resolves the default application project.
     *
     * <p>Never throws: a broken record or a corrupt config produces a failed resolution, which
     * the startup router turns into the configuration screen.</p>
     */
    public static Resolution resolve(ProjectStore store) {
        if (store == null) {
            return new Resolution(Status.NOT_CONFIGURED, null, "", "none", "No project store.");
        }

        AppConfig config = store.loadConfig();
        String configured = trim(config.getDefaultWebProjectId());

        if (!configured.isEmpty()) {
            return resolveConfigured(store, configured, "defaultWebProjectId");
        }

        // Legacy input 1 — the per-project flag. Only read when the authoritative value is empty.
        List<Project> flagged = new ArrayList<>();
        for (Project project : store.getAll()) {
            if (project.isDefault()) {
                flagged.add(project);
            }
        }
        if (!flagged.isEmpty()) {
            Project adopted = flagged.get(0);
            if (flagged.size() > 1) {
                AppLog.w(TAG, "DEFAULT_PROJECT: " + flagged.size()
                        + " projects carry the legacy isDefault flag — repairing to "
                        + adopted.getId());
            }
            if (!isLaunchable(adopted)) {
                return new Resolution(Status.NOT_LAUNCHABLE, null, adopted.getId(),
                        "isDefault flag",
                        "The project marked as default cannot be launched: "
                                + describeProject(adopted));
            }
            adopt(store, adopted, "isDefault flag");
            return new Resolution(Status.RESOLVED, adopted, adopted.getId(),
                    "isDefault flag (migrated)", "");
        }

        // Legacy input 2 — the mirror written by older builds.
        String legacy = trim(config.getDefaultProjectId());
        if (!legacy.isEmpty()) {
            Resolution resolution = resolveConfigured(store, legacy, "defaultProjectId");
            if (resolution.isResolved()) {
                adopt(store, resolution.getProject(), "defaultProjectId");
                return new Resolution(Status.RESOLVED, resolution.getProject(), legacy,
                        "defaultProjectId (migrated)", "");
            }
            return resolution;
        }

        // Nothing configured. No project is adopted here on purpose: after the user deletes the
        // default project, silently opening a different one is the exact failure this rule
        // exists to prevent. Startup shows the configuration screen instead.
        return new Resolution(Status.NOT_CONFIGURED, null, "", "none",
                "No default project is configured.");
    }

    /** Validates one configured id without mutating anything. */
    private static Resolution resolveConfigured(ProjectStore store, String id, String source) {
        Project project = store.findById(id);
        if (project == null) {
            AppLog.w(TAG, "DEFAULT_PROJECT: configured id is missing id=" + id
                    + " source=" + source);
            return new Resolution(Status.MISSING, null, id, source,
                    "The selected default project no longer exists (" + id + ").");
        }
        if (!project.getProjectType().canBeDefaultApplication()) {
            AppLog.w(TAG, "DEFAULT_PROJECT: configured id is not launchable id=" + id
                    + " type=" + project.getProjectType());
            return new Resolution(Status.NOT_LAUNCHABLE, null, id, source,
                    "The selected default project is a " + project.getProjectType().getLabel()
                            + " project and cannot be opened as the application project.");
        }
        if (!isLaunchable(project)) {
            AppLog.w(TAG, "DEFAULT_PROJECT: configured id has no usable source id=" + id);
            return new Resolution(Status.NOT_LAUNCHABLE, null, id, source,
                    "The selected default project has no usable source: "
                            + describeProject(project));
        }
        return new Resolution(Status.RESOLVED, project, id, source, "");
    }

    /**
     * Writes a legacy selection into the authoritative pointer.
     *
     * <p>Idempotent: when the pointer already names this project nothing is written, so a normal
     * startup never touches the configuration.</p>
     */
    private static void adopt(ProjectStore store, Project project, String source) {
        if (store == null || project == null) {
            return;
        }
        AppConfig config = store.loadConfig();
        boolean changed = false;
        if (!project.getId().equals(config.getDefaultWebProjectId())) {
            config.setDefaultWebProjectId(project.getId());
            changed = true;
        }
        if (!project.getId().equals(config.getDefaultProjectId())) {
            config.setDefaultProjectId(project.getId());
            changed = true;
        }
        if (changed) {
            store.saveConfig(config);
            AppLog.i(TAG, "DEFAULT_PROJECT: migrated " + source + " → defaultWebProjectId="
                    + project.getId());
        }
    }

    /**
     * True when {@code project} can be hosted by {@code ProjectActivity}.
     *
     * <p>The product rule is the project type: only {@link ProjectType#WEB} projects are
     * application projects, so a Splash or Bootstrap project can never be the default. The
     * source is deliberately not validated here — a missing file is a runtime load error the
     * WebView reports, whereas refusing to start would turn a recoverable problem into a dead
     * app. An empty entry point is valid too: the runtime falls back to {@code index.html},
     * the same rule {@code ProjectValidator} applies.</p>
     */
    public static boolean isLaunchable(Project project) {
        return project != null
                && project.getProjectType() != null
                && project.getProjectType().canBeDefaultApplication()
                && project.getSourceType() != null;
    }

    /** Short, log-safe description of a project's launch configuration. */
    public static String describeProject(Project project) {
        if (project == null) {
            return "none";
        }
        String entry = trim(project.getEntryPoint());
        return "type=" + project.getProjectType()
                + " source=" + project.getSourceType().getKey()
                + " entryPoint=" + (entry.isEmpty() ? "index.html (default)" : entry);
    }

    private static String trim(String value) {
        return value != null ? value.trim() : "";
    }
}
