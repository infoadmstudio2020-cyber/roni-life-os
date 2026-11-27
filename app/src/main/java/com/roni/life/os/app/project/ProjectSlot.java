package com.roni.life.os.app.project;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The four slots exposed by the Dashboard's Project Section.
 *
 * <p>A slot is a <em>management surface</em>, not a new project model. Each slot maps onto an
 * existing {@link ProjectType} and adds the rules that surface must enforce:</p>
 *
 * <ul>
 *   <li>{@link #SPLASH} — one active Splash project, executed by the native bootstrap.</li>
 *   <li>{@link #DASHBOARD} — one active Dashboard project, executed by the native bootstrap.</li>
 *   <li>{@link #APP} — any number of independent application projects.</li>
 *   <li>{@link #WORKSPACE} — one live folder reference, never registered as an app project.</li>
 * </ul>
 *
 * <p>No Android imports: every rule here is unit-testable on the JVM.</p>
 */
public enum ProjectSlot {

    /** Startup presentation. Single project, native bootstrap execution. */
    SPLASH("splash", "Splash", ProjectType.SPLASH, true, true, true,
            EnumSet.of(ProjectSourceType.REMOTE_URL, ProjectSourceType.ZIP_FILE,
                    ProjectSourceType.INLINE_HTML, ProjectSourceType.SAF_FOLDER,
                    ProjectSourceType.ASSET, ProjectSourceType.ZIP_ASSET)),

    /** Dashboard surface. Single project, native bootstrap execution. */
    DASHBOARD("dashboard", "Dashboard", ProjectType.BOOTSTRAP, true, true, true,
            EnumSet.of(ProjectSourceType.REMOTE_URL, ProjectSourceType.ZIP_FILE,
                    ProjectSourceType.INLINE_HTML, ProjectSourceType.SAF_FOLDER,
                    ProjectSourceType.ASSET, ProjectSourceType.ZIP_ASSET)),

    /** Application projects. Multiple independent projects, permanent registration. */
    APP("app", "App", ProjectType.WEB, false, true, true,
            EnumSet.of(ProjectSourceType.REMOTE_URL, ProjectSourceType.INLINE_HTML,
                    ProjectSourceType.SAF_FOLDER, ProjectSourceType.ZIP_FILE,
                    ProjectSourceType.ASSET, ProjectSourceType.ZIP_ASSET)),

    /**
     * Development workspace. Single project, folder reference only.
     *
     * <p>{@code allowsRegister} is {@code false} on purpose: a workspace is a live reference to
     * the user's own folder and must never be turned into a registered application project
     * behind their back.</p>
     */
    WORKSPACE("workspace", "Workspace", ProjectType.WORKSPACE, true, false, true,
            EnumSet.of(ProjectSourceType.SAF_FOLDER));

    private final String key;
    private final String label;
    private final ProjectType projectType;
    private final boolean singleProject;
    private final boolean allowsRegister;
    private final boolean allowsPreview;
    private final Set<ProjectSourceType> supportedSources;

    ProjectSlot(String key, String label, ProjectType projectType, boolean singleProject,
                boolean allowsRegister, boolean allowsPreview,
                Set<ProjectSourceType> supportedSources) {
        this.key = key;
        this.label = label;
        this.projectType = projectType;
        this.singleProject = singleProject;
        this.allowsRegister = allowsRegister;
        this.allowsPreview = allowsPreview;
        this.supportedSources = Collections.unmodifiableSet(supportedSources);
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    /** The project type this slot manages. */
    public ProjectType getProjectType() {
        return projectType;
    }

    /** True when at most one project may be active in this slot. */
    public boolean isSingleProject() {
        return singleProject;
    }

    /** True when the Register action is available in this slot. */
    public boolean allowsRegister() {
        return allowsRegister;
    }

    /** True when the Preview action is available in this slot. */
    public boolean allowsPreview() {
        return allowsPreview;
    }

    /** Source types this slot accepts. Never mix rules across slots. */
    public Set<ProjectSourceType> getSupportedSources() {
        return supportedSources;
    }

    public boolean supports(ProjectSourceType sourceType) {
        return sourceType != null && supportedSources.contains(sourceType);
    }

    /** True when the slot stores a live folder reference instead of a copied project. */
    public boolean isReferenceOnly() {
        return this == WORKSPACE;
    }

    /** Human-readable rule shown on the management screen. */
    public String getRuleSummary() {
        switch (this) {
            case SPLASH:
                return "One active Splash project. Runs through the native bootstrap.";
            case DASHBOARD:
                return "One active Dashboard project. Runs through the native bootstrap.";
            case APP:
                return "Any number of independent app projects. Register copies the source.";
            case WORKSPACE:
                return "One folder reference. Previewed in place, never registered.";
            default:
                return "";
        }
    }

    public static ProjectSlot fromKey(String key) {
        if (key != null) {
            for (ProjectSlot slot : values()) {
                if (slot.key.equalsIgnoreCase(key) || slot.name().equalsIgnoreCase(key)) {
                    return slot;
                }
            }
        }
        return APP;
    }

    /** The slot that manages {@code type}, or {@code null} when no slot does. */
    public static ProjectSlot forType(ProjectType type) {
        if (type == null) {
            return null;
        }
        for (ProjectSlot slot : values()) {
            if (slot.projectType == type) {
                return slot;
            }
        }
        return null;
    }
}
