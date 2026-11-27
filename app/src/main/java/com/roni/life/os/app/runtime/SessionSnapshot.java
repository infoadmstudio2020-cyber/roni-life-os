package com.roni.life.os.app.runtime;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistable description of a project session.
 *
 * <p>Only data that survives process death is stored: identity, display metadata and the last
 * active timestamp. The Android task id and the WebView are deliberately absent — neither can
 * outlive the process, and pretending otherwise would let the dashboard offer a restore that
 * silently fails.</p>
 *
 * <p>Encoding is URL-encoded key/value pairs, the same scheme {@code Project} uses, so the record
 * stays readable and needs no Android or JSON dependency.</p>
 */
public final class SessionSnapshot {

    private static final String VERSION = "v=1";

    private final String projectId;
    private final String name;
    private final long lastActiveAt;
    private final int iconColor;
    private final String sourceKey;
    private final boolean preview;

    public SessionSnapshot(String projectId, String name, long lastActiveAt,
                           int iconColor, String sourceKey, boolean preview) {
        this.projectId = projectId;
        this.name = name != null ? name : "";
        this.lastActiveAt = lastActiveAt;
        this.iconColor = iconColor;
        this.sourceKey = sourceKey != null ? sourceKey : "";
        this.preview = preview;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public long getLastActiveAt() {
        return lastActiveAt;
    }

    public int getIconColor() {
        return iconColor;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public boolean isPreview() {
        return preview;
    }

    /** @return a single URL-encoded record, or {@code ""} when the id is missing */
    public String encode() {
        if (projectId == null || projectId.trim().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(VERSION);
        append(builder, "id", projectId);
        append(builder, "name", name);
        append(builder, "at", String.valueOf(lastActiveAt));
        append(builder, "color", String.valueOf(iconColor));
        append(builder, "src", sourceKey);
        append(builder, "preview", preview ? "1" : "0");
        return builder.toString();
    }

    /** @return the decoded record, or {@code null} when the payload is unusable */
    public static SessionSnapshot decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) {
            return null;
        }
        String id = null;
        String name = "";
        long at = 0L;
        int color = 0;
        String source = "";
        boolean preview = false;

        for (String pair : encoded.split("&")) {
            int split = pair.indexOf('=');
            if (split <= 0) {
                continue;
            }
            String key = pair.substring(0, split);
            String value = decodeValue(pair.substring(split + 1));
            if ("id".equals(key)) {
                id = value;
            } else if ("name".equals(key)) {
                name = value;
            } else if ("at".equals(key)) {
                at = parseLong(value, 0L);
            } else if ("color".equals(key)) {
                color = (int) parseLong(value, 0L);
            } else if ("src".equals(key)) {
                source = value;
            } else if ("preview".equals(key)) {
                preview = "1".equals(value);
            }
        }

        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        return new SessionSnapshot(id, name, at, color, source, preview);
    }

    /** Encodes a list as newline-separated records. */
    public static String encodeList(List<SessionSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (SessionSnapshot snapshot : snapshots) {
            String record = snapshot != null ? snapshot.encode() : "";
            if (record.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(record);
        }
        return builder.toString();
    }

    /** Decodes newline-separated records, skipping anything malformed. */
    public static List<SessionSnapshot> decodeList(String encoded) {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        if (encoded == null || encoded.trim().isEmpty()) {
            return snapshots;
        }
        for (String line : encoded.split("\n")) {
            SessionSnapshot snapshot = decode(line);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        return snapshots;
    }

    private static void append(StringBuilder builder, String key, String value) {
        builder.append('&').append(key).append('=').append(encodeValue(value));
    }

    private static String encodeValue(String value) {
        try {
            return URLEncoder.encode(value != null ? value : "", "UTF-8");
        } catch (UnsupportedEncodingException error) {
            return "";
        }
    }

    private static String decodeValue(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException error) {
            return value;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }
}
