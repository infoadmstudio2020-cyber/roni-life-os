package com.roni.life.os.app.source;

/**
 * Which copy of a source is in play.
 *
 * <p>{@link #DEFAULT} ships inside the APK and is protected: the app copies it into storage on
 * first launch and never edits it afterwards. {@link #RUNTIME} is the user's own copy and is the
 * only one the management operations touch.</p>
 */
public enum SourceSlot {

    DEFAULT("default", "Default"),

    RUNTIME("runtime", "Runtime");

    private final String key;
    private final String label;

    SourceSlot(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public boolean isRuntime() {
        return this == RUNTIME;
    }

    public static SourceSlot fromKey(String key) {
        if (key != null) {
            for (SourceSlot slot : values()) {
                if (slot.key.equalsIgnoreCase(key) || slot.name().equalsIgnoreCase(key)) {
                    return slot;
                }
            }
        }
        return DEFAULT;
    }
}
