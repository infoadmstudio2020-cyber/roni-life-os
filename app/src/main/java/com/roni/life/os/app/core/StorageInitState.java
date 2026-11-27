package com.roni.life.os.app.core;

/**
 * The {@code system/config/initialization.json} marker.
 *
 * <p>Pure Java: the marker is written before the runtime exists, so it must not depend on any
 * engine, and a malformed file must degrade to "not initialised" rather than throw.</p>
 */
public final class StorageInitState {

    private final boolean initialized;
    private final int version;
    private final boolean firstRun;
    private final long initializedAt;
    private final int layoutVersion;
    private final String root;
    private final int copiedFiles;

    public StorageInitState(boolean initialized, int version, boolean firstRun, long initializedAt,
                            int layoutVersion, String root, int copiedFiles) {
        this.initialized = initialized;
        this.version = version;
        this.firstRun = firstRun;
        this.initializedAt = initializedAt;
        this.layoutVersion = layoutVersion;
        this.root = root != null ? root : "";
        this.copiedFiles = copiedFiles;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getVersion() {
        return version;
    }

    public boolean isFirstRun() {
        return firstRun;
    }

    public long getInitializedAt() {
        return initializedAt;
    }

    public int getLayoutVersion() {
        return layoutVersion;
    }

    public String getRoot() {
        return root;
    }

    public int getCopiedFiles() {
        return copiedFiles;
    }

    // ==================== Codec ====================

    public String encode() {
        return "{\n"
                + "  \"initialized\": " + initialized + ",\n"
                + "  \"version\": " + version + ",\n"
                + "  \"firstRun\": " + firstRun + ",\n"
                + "  \"initializedAt\": " + initializedAt + ",\n"
                + "  \"layoutVersion\": " + layoutVersion + ",\n"
                + "  \"copiedFiles\": " + copiedFiles + ",\n"
                + "  \"root\": " + quote(root) + "\n"
                + "}\n";
    }

    /** Parses a marker document; returns {@code null} when it cannot be understood. */
    public static StorageInitState decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return new StorageInitState(
                bool(raw, "initialized", false),
                integer(raw, "version", 0),
                bool(raw, "firstRun", false),
                lng(raw, "initializedAt", 0L),
                integer(raw, "layoutVersion", 0),
                string(raw, "root", ""),
                integer(raw, "copiedFiles", 0));
    }

    private static boolean bool(String raw, String key, boolean fallback) {
        String value = string(raw, key, null);
        return value == null ? fallback : "true".equalsIgnoreCase(value);
    }

    private static int integer(String raw, String key, int fallback) {
        String value = string(raw, key, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long lng(String raw, String key, long fallback) {
        String value = string(raw, key, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Returns the raw token for {@code key}: unquoted text, or the string contents. */
    private static String string(String raw, String key, String fallback) {
        String needle = "\"" + key + "\"";
        int at = raw.indexOf(needle);
        if (at < 0) {
            return fallback;
        }
        int colon = raw.indexOf(':', at + needle.length());
        if (colon < 0) {
            return fallback;
        }
        int i = colon + 1;
        while (i < raw.length() && Character.isWhitespace(raw.charAt(i))) {
            i++;
        }
        if (i >= raw.length()) {
            return fallback;
        }
        if (raw.charAt(i) == '"') {
            StringBuilder sb = new StringBuilder();
            i++;
            while (i < raw.length()) {
                char c = raw.charAt(i);
                if (c == '\\' && i + 1 < raw.length()) {
                    sb.append(raw.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    return sb.toString();
                }
                sb.append(c);
                i++;
            }
            return fallback;
        }
        int end = i;
        while (end < raw.length() && ",}\n\r".indexOf(raw.charAt(end)) < 0) {
            end++;
        }
        String token = raw.substring(i, end).trim();
        return token.isEmpty() ? fallback : token;
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else {
                out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }
}
