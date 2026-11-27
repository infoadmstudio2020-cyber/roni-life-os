package com.roni.life.os.app.project;

/**
 * How a {@link ProjectType#BOOTSTRAP} project renders its interface.
 *
 * <p>This is deliberately separate from {@link ProjectType}: a Bootstrap project is still a
 * Bootstrap project whether it draws native Android views or a WebView.</p>
 */
public enum BootstrapRenderingMode {

    /** Native Android Activity and views, driven by the existing NativeRuntimeUI integration. */
    NATIVE("native", "Native"),

    /** WebView content served through the existing WebViewHost / WebSourceResolver stack. */
    WEB("web", "Web");

    private final String key;
    private final String label;

    BootstrapRenderingMode(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public boolean isWeb() {
        return this == WEB;
    }

    /** Unknown or missing values fall back to {@link #NATIVE}, the pre-existing behaviour. */
    public static BootstrapRenderingMode fromKey(String key) {
        if (key != null) {
            for (BootstrapRenderingMode mode : values()) {
                if (mode.key.equalsIgnoreCase(key) || mode.name().equalsIgnoreCase(key)) {
                    return mode;
                }
            }
        }
        return NATIVE;
    }
}
