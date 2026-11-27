package com.roni.library.platform.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.filemanager.FileManagerErrorCodes;
import com.roni.library.contracts.filemanager.FileManagerLimits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Path policy for the file manager.
 *
 * <p>Only project-relative paths are accepted. Absolute paths, drive letters, {@code ..} segments,
 * control characters and over-long names are rejected before any I/O happens.</p>
 */
public final class FileManagerPaths {

    private static final String[] RESERVED_NAMES = {
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4",
            "LPT1", "LPT2", "LPT3", ".", ".."
    };

    private FileManagerPaths() {
    }

    /** @return a normalised relative path ({@code ""} for the project root) */
    @NonNull
    public static String normalize(@Nullable String raw) throws FileManagerException {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().replace('\\', '/');
        if (value.isEmpty() || "/".equals(value)) {
            return "";
        }
        if (value.length() > FileManagerLimits.MAX_PATH_LENGTH) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_PATH,
                    "Path exceeds " + FileManagerLimits.MAX_PATH_LENGTH + " characters");
        }
        if (value.startsWith("/") || value.startsWith("~")) {
            throw new FileManagerException(FileManagerErrorCodes.PATH_TRAVERSAL,
                    "Absolute paths are not allowed");
        }
        if (value.length() > 1 && value.charAt(1) == ':') {
            throw new FileManagerException(FileManagerErrorCodes.PATH_TRAVERSAL,
                    "Drive-qualified paths are not allowed");
        }
        List<String> segments = new ArrayList<>();
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new FileManagerException(FileManagerErrorCodes.PATH_TRAVERSAL,
                        "Parent traversal is not allowed");
            }
            validateName(segment);
            segments.add(segment);
        }
        return String.join("/", segments);
    }

    /** Validates a single file or folder name. */
    public static void validateName(@Nullable String name) throws FileManagerException {
        if (name == null || name.trim().isEmpty()) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_PATH,
                    "Name cannot be empty");
        }
        String value = name.trim();
        if (value.length() > FileManagerLimits.MAX_NAME_LENGTH) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_PATH,
                    "Name exceeds " + FileManagerLimits.MAX_NAME_LENGTH + " characters");
        }
        if (value.contains("/") || value.contains("\\")) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_PATH,
                    "Name cannot contain path separators");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new FileManagerException(FileManagerErrorCodes.INVALID_PATH,
                        "Name contains control characters");
            }
        }
        String upper = value.toUpperCase(Locale.US);
        for (String reserved : RESERVED_NAMES) {
            if (reserved.equals(upper)) {
                throw new FileManagerException(FileManagerErrorCodes.INVALID_PATH,
                        "Reserved name: " + value);
            }
        }
    }

    /** Validates a project identifier coming from the trusted host context. */
    @NonNull
    public static String requireProjectId(@Nullable String projectId) throws FileManagerException {
        if (projectId == null || projectId.trim().isEmpty()) {
            throw new FileManagerException(FileManagerErrorCodes.NO_ACTIVE_PROJECT,
                    "No project is bound to the file manager");
        }
        String value = projectId.trim();
        if (value.length() > 64) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_PATH,
                    "Project id is too long");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.';
            if (!allowed) {
                throw new FileManagerException(FileManagerErrorCodes.INVALID_PATH,
                        "Project id contains unsupported characters");
            }
        }
        if (".".equals(value) || "..".equals(value)) {
            throw new FileManagerException(FileManagerErrorCodes.INVALID_PATH,
                    "Invalid project id");
        }
        return value;
    }

    /** @return the parent of a normalised relative path, or {@code ""} at the root */
    @NonNull
    public static String parentOf(@NonNull String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash > 0 ? relativePath.substring(0, slash) : "";
    }

    /** @return the last segment of a normalised relative path */
    @NonNull
    public static String nameOf(@NonNull String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
    }

    /** Appends a child name to a parent path. */
    @NonNull
    public static String childOf(@NonNull String parent, @NonNull String name) {
        return parent.isEmpty() ? name : parent + "/" + name;
    }
}
