package com.roni.library.contracts.logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Origin category of a {@link LogEntry}.
 *
 * <p>This is the categorization dimension the Runtime Inspector tabs are built on: each tab is
 * a source set and "ALL LOGS" is the union. It lives in the shared contracts module so the
 * inspector does not have to declare a second log model just to describe where an entry came
 * from.</p>
 *
 * @author infoadmstudio2020-cyber
 */
public enum LogSource {

    NATIVE("Native"),
    WEB("Web"),
    BRIDGE("Bridge"),
    NETWORK("Network"),
    STORAGE("Storage"),
    SECURITY("Security"),
    LIFECYCLE("Lifecycle"),
    PROJECT("Project"),
    PERFORMANCE("Performance");

    private final String label;

    LogSource(@NonNull String label) {
        this.label = label;
    }

    /** Human-readable label used by the inspector tabs and rows. */
    @NonNull
    public String getLabel() {
        return label;
    }

    /** Parses a stored or user-supplied name, falling back to {@link #NATIVE}. */
    @NonNull
    public static LogSource parse(@Nullable String value) {
        if (value == null) {
            return NATIVE;
        }
        String normalized = value.trim().toUpperCase(Locale.US);
        for (LogSource source : values()) {
            if (source.name().equals(normalized)) {
                return source;
            }
        }
        return NATIVE;
    }
}
