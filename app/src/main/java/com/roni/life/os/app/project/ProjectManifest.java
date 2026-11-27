package com.roni.life.os.app.project;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Validates a project's {@code manifest.json} before the project is assigned an id or encrypted.
 *
 * <p>Deliberately dependency-free: no {@code org.json}, no Android. The manifest is a small,
 * flat document and the only field with security consequences is {@code entryPoint} — it becomes
 * a path the resource provider will open. Everything else is advisory metadata that the registry
 * overwrites with its own values.</p>
 *
 * <p>What is enforced:</p>
 * <ul>
 *   <li>the file, when present, must be a JSON object;</li>
 *   <li>{@code entryPoint} must be a relative path inside the project — no absolute path, no
 *       drive letter, no backslash, no {@code .} or {@code ..} segment, no NUL;</li>
 *   <li>{@code entryPoint} must resolve to an existing file;</li>
 *   <li>{@code id}, when present, must be a safe identifier. It is <b>not</b> trusted as the
 *       project id: the registry always assigns a fresh one, so a manifest cannot choose its own
 *       Keystore alias.</li>
 * </ul>
 */
public final class ProjectManifest {

    /** The manifest file name at the project root. */
    public static final String FILE_NAME = "manifest.json";

    /** Ceiling on the manifest size; a real one is a few hundred bytes. */
    public static final int MAX_BYTES = 64 * 1024;

    private ProjectManifest() {
    }

    /** Outcome of reading a manifest. */
    public static final class Result {
        private final boolean valid;
        private final String entryPoint;
        private final String message;

        Result(boolean valid, String entryPoint, String message) {
            this.valid = valid;
            this.entryPoint = entryPoint;
            this.message = message != null ? message : "";
        }

        public boolean isValid() {
            return valid;
        }

        /** The declared entry point, or {@code null} when the manifest did not declare one. */
        public String getEntryPoint() {
            return entryPoint;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Reads and validates {@code directory/manifest.json}.
     *
     * <p>A missing manifest is valid: the entry point then comes from
     * {@link ProjectImporter#detectEntryPoint(File)}.</p>
     */
    public static Result validate(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return new Result(false, null, "The project directory is missing.");
        }
        File manifest = new File(directory, FILE_NAME);
        if (!manifest.isFile()) {
            return new Result(true, null, "No manifest.json; the entry point will be detected.");
        }
        if (manifest.length() > MAX_BYTES) {
            return new Result(false, null, "manifest.json is larger than " + MAX_BYTES + " bytes.");
        }

        String text;
        try {
            text = read(manifest);
        } catch (IOException error) {
            return new Result(false, null, "manifest.json could not be read.");
        }

        String trimmed = text.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return new Result(false, null, "manifest.json is not a JSON object.");
        }

        String entryPoint = stringField(trimmed, "entryPoint");
        if (entryPoint != null) {
            if (!isSafeRelativePath(entryPoint)) {
                return new Result(false, null,
                        "manifest.json entryPoint is not a safe relative path: " + entryPoint);
            }
            File resolved = new File(directory, entryPoint);
            if (!resolved.isFile()) {
                return new Result(false, null,
                        "manifest.json entryPoint does not exist: " + entryPoint);
            }
        }

        String id = stringField(trimmed, "id");
        if (id != null && !isSafeId(id)) {
            return new Result(false, null, "manifest.json id is not a safe identifier: " + id);
        }

        return new Result(true, entryPoint, "manifest.json is valid.");
    }

    /**
     * @return true when {@code path} is a relative path that cannot escape its root
     */
    public static boolean isSafeRelativePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        if (path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0) {
            return false;
        }
        if (path.startsWith("/")) {
            return false;
        }
        if (path.length() >= 2 && path.charAt(1) == ':') {
            return false;
        }
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    /** @return true when {@code id} is a safe project identifier */
    public static boolean isSafeId(String id) {
        if (id == null || id.isEmpty() || id.length() > 64) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts a top-level string field without a JSON parser.
     *
     * <p>Only used for {@code entryPoint} and {@code id}, both of which are validated afterwards.
     * A malformed document simply yields {@code null} and the caller falls back to detection.</p>
     */
    private static String stringField(String json, String name) {
        String needle = "\"" + name + "\"";
        int from = 0;
        while (true) {
            int key = json.indexOf(needle, from);
            if (key < 0) {
                return null;
            }
            int colon = json.indexOf(':', key + needle.length());
            if (colon < 0) {
                return null;
            }
            int quote = json.indexOf('"', colon + 1);
            if (quote < 0) {
                return null;
            }
            // Reject a match that is actually a value, not a key: the character before the key
            // must be a structural one.
            int before = key - 1;
            while (before >= 0 && Character.isWhitespace(json.charAt(before))) {
                before--;
            }
            if (before >= 0 && json.charAt(before) != '{' && json.charAt(before) != ',') {
                from = key + needle.length();
                continue;
            }
            int end = json.indexOf('"', quote + 1);
            if (end < 0) {
                return null;
            }
            return json.substring(quote + 1, end);
        }
    }

    private static String read(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            byte[] buffer = new byte[(int) Math.min(file.length(), MAX_BYTES)];
            int read = 0;
            while (read < buffer.length) {
                int step = in.read(buffer, read, buffer.length - read);
                if (step < 0) {
                    break;
                }
                read += step;
            }
            return new String(buffer, 0, read, "UTF-8");
        } finally {
            in.close();
        }
    }
}
