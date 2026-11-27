package com.roni.library.platform.camera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.camera.CameraMediaItem;

/**
 * Line based encoding for the media index.
 *
 * <p>One record per line, fields separated by {@code |}. Values are escaped so a file name can
 * never break the record structure.</p>
 */
public final class CameraCodec {

    private static final String SEPARATOR = "|";
    private static final int FIELD_COUNT = 13;

    private CameraCodec() {
    }

    @NonNull
    public static String encode(@NonNull CameraMediaItem item) {
        StringBuilder builder = new StringBuilder();
        append(builder, item.getId());
        append(builder, item.getProjectId());
        append(builder, item.getType());
        append(builder, item.getFileName());
        append(builder, item.getRelativePath());
        append(builder, item.getThumbnailPath());
        append(builder, item.getMimeType());
        append(builder, String.valueOf(item.getSizeBytes()));
        append(builder, String.valueOf(item.getWidth()));
        append(builder, String.valueOf(item.getHeight()));
        append(builder, String.valueOf(item.getDurationMs()));
        append(builder, String.valueOf(item.getCreatedAt()));
        append(builder, item.getState());
        return builder.toString();
    }

    @Nullable
    public static CameraMediaItem decode(@Nullable String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] parts = split(line);
        if (parts.length < FIELD_COUNT) {
            return null;
        }
        try {
            return new CameraMediaItem.Builder()
                    .id(parts[0])
                    .projectId(parts[1])
                    .type(parts[2])
                    .fileName(parts[3])
                    .relativePath(parts[4])
                    .thumbnailPath(parts[5])
                    .mimeType(parts[6])
                    .sizeBytes(Long.parseLong(parts[7]))
                    .size(Integer.parseInt(parts[8]), Integer.parseInt(parts[9]))
                    .durationMs(Long.parseLong(parts[10]))
                    .createdAt(Long.parseLong(parts[11]))
                    .state(parts[12])
                    .build();
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static void append(StringBuilder builder, String value) {
        if (builder.length() > 0) {
            builder.append(SEPARATOR);
        }
        builder.append(escape(value));
    }

    @NonNull
    private static String escape(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("|", "\\p")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    @NonNull
    private static String unescape(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                if (next == 'p') {
                    builder.append('|');
                } else if (next == 'n') {
                    builder.append('\n');
                } else {
                    builder.append(next);
                }
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    @NonNull
    private static String[] split(@NonNull String line) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                current.append(c).append(line.charAt(++i));
            } else if (c == '|') {
                parts.add(unescape(current.toString()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(unescape(current.toString()));
        return parts.toArray(new String[0]);
    }
}
