package com.roni.life.os.app.project;

/**
 * The role a registered project plays in the application lifecycle.
 *
 * <p>A project type is <b>not</b> a rendering mode. A {@link #BOOTSTRAP} project may render
 * natively or through a WebView — that choice lives in {@link BootstrapRenderingMode}.</p>
 *
 * <ul>
 *   <li>{@link #SPLASH} — startup presentation only. Never the default application project.</li>
 *   <li>{@link #BOOTSTRAP} — initialisation and entry management. Never opens automatically
 *       unless {@code bootstrapAtStartup} is explicitly enabled.</li>
 *   <li>{@link #WEB} — the actual application project, hosted by {@code ProjectActivity}.</li>
 * </ul>
 */
public enum ProjectType {

    /** Startup presentation: logo, animation, branding, loading screen. */
    SPLASH("splash", "Splash"),

    /** Application initialisation and entry-management layer. */
    BOOTSTRAP("bootstrap", "Bootstrap"),

    /** Actual web application project. */
    WEB("web", "Web"),

    /**
     * Development workspace: a live reference to a user-selected folder.
     *
     * <p>A Workspace is never copied and never registered as an application project. It exists
     * only so the user can preview and test a folder in place while editing it.</p>
     */
    WORKSPACE("workspace", "Workspace");

    private final String key;
    private final String label;

    ProjectType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    /** True when this type is hosted by {@code ProjectActivity}. */
    public boolean isApplicationProject() {
        return this == WEB;
    }

    /** True when this type may be selected as the default application project. */
    public boolean canBeDefaultApplication() {
        return this == WEB;
    }

    /** True when only one project of this type may be active at a time. */
    public boolean isSingleProjectRole() {
        return this == SPLASH || this == BOOTSTRAP || this == WORKSPACE;
    }

    /**
     * Parses a stored key. Unknown or missing values fall back to {@link #WEB} so that
     * projects created before the type system existed keep working as application projects.
     */
    public static ProjectType fromKey(String key) {
        if (key != null) {
            for (ProjectType type : values()) {
                if (type.key.equalsIgnoreCase(key) || type.name().equalsIgnoreCase(key)) {
                    return type;
                }
            }
        }
        return WEB;
    }
}
