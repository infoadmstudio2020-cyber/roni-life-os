package com.roni.life.os.app.source;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural validation for a source folder.
 *
 * <p>Pure Java: the resolver, the manager and the tests all use the same rules, so "valid" means
 * exactly one thing across the app.</p>
 */
public final class SourceValidator {

    /** Manifest every source folder must carry. */
    public static final String MANIFEST = "manifest.json";

    private SourceValidator() {
    }

    /** Result of one validation pass. */
    public static final class Validation {

        private final boolean valid;
        private final List<String> missing;
        private final String message;

        Validation(boolean valid, List<String> missing, String message) {
            this.valid = valid;
            this.missing = missing;
            this.message = message != null ? message : "";
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getMissing() {
            return missing;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return valid ? "valid" : "invalid: " + message;
        }
    }

    /**
     * Checks that {@code dir} exists, contains {@code entry} and carries a manifest.
     *
     * @param dir   source folder
     * @param entry entry document, relative to {@code dir}
     */
    public static Validation validate(File dir, String entry) {
        List<String> missing = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) {
            missing.add(".");
            return new Validation(false, missing, "The source folder does not exist.");
        }
        String entryName = entry != null && !entry.trim().isEmpty() ? entry.trim() : "index.html";
        File entryFile = new File(dir, entryName);
        if (!entryFile.isFile() || entryFile.length() == 0L) {
            missing.add(entryName);
        }
        File manifest = new File(dir, MANIFEST);
        if (!manifest.isFile() || manifest.length() == 0L) {
            missing.add(MANIFEST);
        }
        if (missing.isEmpty()) {
            return new Validation(true, missing, "Source is valid.");
        }
        return new Validation(false, missing, "Missing: " + join(missing));
    }

    /** True when the folder holds at least one file (used before promoting an import). */
    public static boolean hasContent(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        File[] children = dir.listFiles();
        return children != null && children.length > 0;
    }

    private static String join(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }
}
