package com.roni.life.os.app.source;

/**
 * Contents of {@code splash-config.json} / {@code bootstrap-config.json}.
 *
 * <p>Pure Java so the codec can be tested on the JVM. Source selection and execution mode are
 * deliberately separate fields: a Bootstrap source can be {@code runtime} while the mode stays
 * {@code native}, and vice versa.</p>
 */
public final class SourceConfig {

    public static final String MODE_NATIVE = "native";
    public static final String MODE_WEB = "web";

    private SourceSlot activeSource = SourceSlot.DEFAULT;
    private boolean fallbackEnabled = true;
    private String activeMode = MODE_NATIVE;
    private String runtimeEntry = "index.html";
    private long updatedAt;

    public SourceConfig() {
    }

    public SourceSlot getActiveSource() {
        return activeSource;
    }

    public void setActiveSource(SourceSlot slot) {
        this.activeSource = slot != null ? slot : SourceSlot.DEFAULT;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean enabled) {
        this.fallbackEnabled = enabled;
    }

    public String getActiveMode() {
        return activeMode;
    }

    public void setActiveMode(String mode) {
        this.activeMode = MODE_WEB.equalsIgnoreCase(mode) ? MODE_WEB : MODE_NATIVE;
    }

    public boolean isWebMode() {
        return MODE_WEB.equals(activeMode);
    }

    public String getRuntimeEntry() {
        return runtimeEntry;
    }

    public void setRuntimeEntry(String entry) {
        this.runtimeEntry = entry != null && !entry.trim().isEmpty() ? entry.trim() : "index.html";
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    // ==================== Codec ====================

    public String encode() {
        return "{\n"
                + "  \"schemaVersion\": 1,\n"
                + "  \"activeSource\": " + quote(activeSource.getKey()) + ",\n"
                + "  \"fallbackEnabled\": " + fallbackEnabled + ",\n"
                + "  \"activeMode\": " + quote(activeMode) + ",\n"
                + "  \"runtimeEntry\": " + quote(runtimeEntry) + ",\n"
                + "  \"updatedAt\": " + updatedAt + "\n"
                + "}\n";
    }

    public static SourceConfig decode(String raw) {
        SourceConfig config = new SourceConfig();
        if (raw == null || raw.trim().isEmpty()) {
            return config;
        }
        config.activeSource = SourceSlot.fromKey(string(raw, "activeSource", "default"));
        config.fallbackEnabled = bool(raw, "fallbackEnabled", true);
        config.activeMode = MODE_WEB.equalsIgnoreCase(string(raw, "activeMode", MODE_NATIVE))
                ? MODE_WEB : MODE_NATIVE;
        config.runtimeEntry = string(raw, "runtimeEntry", "index.html");
        config.updatedAt = lng(raw, "updatedAt", 0L);
        return config;
    }

    private static boolean bool(String raw, String key, boolean fallback) {
        String value = string(raw, key, null);
        return value == null ? fallback : "true".equalsIgnoreCase(value);
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
