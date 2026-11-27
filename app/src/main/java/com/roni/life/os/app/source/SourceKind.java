package com.roni.life.os.app.source;

/**
 * The two independent source-managed surfaces.
 *
 * <p>Splash and Bootstrap never share configuration, directories or managers — only the
 * mechanics in {@link SourceManager} and {@link SourceResolver}.</p>
 */
public enum SourceKind {

    SPLASH("splash", "Splash"),

    BOOTSTRAP("bootstrap", "Bootstrap");

    private final String key;
    private final String label;

    SourceKind(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    /** True when this surface has a native/web execution mode. */
    public boolean hasMode() {
        return this == BOOTSTRAP;
    }

    public static SourceKind fromKey(String key) {
        if (key != null) {
            for (SourceKind kind : values()) {
                if (kind.key.equalsIgnoreCase(key) || kind.name().equalsIgnoreCase(key)) {
                    return kind;
                }
            }
        }
        return SPLASH;
    }
}
