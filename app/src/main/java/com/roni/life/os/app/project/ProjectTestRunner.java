package com.roni.life.os.app.project;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Test operation: a read-only compatibility check.
 *
 * <p>Test never copies, never registers and never mutates the project. It answers one question —
 * "will this source actually load?" — by checking the configuration, the source location, the
 * entry document and the local asset references that document makes.</p>
 *
 * <p>Because it is read-only it also works on a draft that has never been registered, which is
 * exactly what the App screen needs before the user commits to Register.</p>
 */
public final class ProjectTestRunner {

    /** Only the first part of a document is scanned for references. */
    private static final int MAX_SCAN_BYTES = 256 * 1024;

    /** Ceiling on reported reference problems, so one broken page cannot flood the report. */
    private static final int MAX_REFERENCE_ISSUES = 12;

    private static final Pattern REFERENCE = Pattern.compile(
            "(?:src|href)\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private ProjectTestRunner() {
    }

    /** One line of the report. */
    public static final class Check {
        private final String label;
        private final boolean passed;
        private final boolean warning;
        private final String detail;

        Check(String label, boolean passed, boolean warning, String detail) {
            this.label = label;
            this.passed = passed;
            this.warning = warning;
            this.detail = detail != null ? detail : "";
        }

        public String getLabel() {
            return label;
        }

        public boolean isPassed() {
            return passed;
        }

        public boolean isWarning() {
            return warning;
        }

        public String getDetail() {
            return detail;
        }

        @Override
        public String toString() {
            String status = passed ? "PASS" : (warning ? "WARN" : "FAIL");
            return "[" + status + "] " + label + (detail.isEmpty() ? "" : " — " + detail);
        }
    }

    /** The full report. */
    public static final class Report {
        private final List<Check> checks;

        Report(List<Check> checks) {
            this.checks = Collections.unmodifiableList(checks);
        }

        public List<Check> getChecks() {
            return checks;
        }

        /** True when no check failed. Warnings do not fail a test. */
        public boolean isPassed() {
            for (Check check : checks) {
                if (!check.isPassed() && !check.isWarning()) {
                    return false;
                }
            }
            return true;
        }

        public int getFailureCount() {
            int count = 0;
            for (Check check : checks) {
                if (!check.isPassed() && !check.isWarning()) {
                    count++;
                }
            }
            return count;
        }

        public int getWarningCount() {
            int count = 0;
            for (Check check : checks) {
                if (check.isWarning()) {
                    count++;
                }
            }
            return count;
        }

        /** Multi-line text for a dialog. */
        public String toText() {
            StringBuilder builder = new StringBuilder();
            for (Check check : checks) {
                builder.append(check.toString()).append('\n');
            }
            return builder.toString().trim();
        }
    }

    /**
     * Runs every check against {@code project}.
     *
     * @param context  any context
     * @param project  the project or draft to test
     * @param existing other registered projects, used for the name-clash rule
     */
    public static Report run(Context context, Project project, List<Project> existing) {
        List<Check> checks = new ArrayList<>();
        if (project == null) {
            checks.add(new Check("Project", false, false, "No project was supplied."));
            return new Report(checks);
        }

        // 1. Configuration.
        ProjectValidator.Result validation = ProjectValidator.validate(project, existing);
        if (validation.isValid()) {
            checks.add(new Check("Configuration", true, false, "All required fields are present."));
        } else {
            for (String error : validation.getErrors()) {
                checks.add(new Check("Configuration", false, false, error));
            }
        }
        for (String warning : validation.getWarnings()) {
            checks.add(new Check("Configuration", true, true, warning));
        }

        // 2. Source.
        ProjectSourceType type = project.getSourceType();
        String entry = project.getEntryPoint() != null && !project.getEntryPoint().trim().isEmpty()
                ? project.getEntryPoint().trim() : "index.html";

        switch (type) {
            case REMOTE_URL:
                checkRemote(project, checks);
                break;
            case INLINE_HTML:
                checkInline(project, checks);
                break;
            case SAF_FOLDER:
                checkSafFolder(context, project, entry, checks);
                break;
            case FILE:
            case ZIP_FILE:
                checkLocalFolder(project, entry, checks);
                break;
            case ASSET:
            case ZIP_ASSET:
                checks.add(new Check("Source", true, true,
                        "Bundled asset sources are verified by the runtime at load time."));
                break;
            default:
                checks.add(new Check("Source", false, false, "Unsupported source type."));
                break;
        }

        // 3. Runtime compatibility.
        checks.add(new Check("Runtime", true, false,
                "JavaScript, DOM storage and relative module loading are enabled for this source."));

        return new Report(checks);
    }

    private static void checkRemote(Project project, List<Check> checks) {
        String url = project.getSourcePath() != null ? project.getSourcePath().trim() : "";
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            checks.add(new Check("Source", false, false,
                    "The remote URL must start with http:// or https://"));
            return;
        }
        if (lower.startsWith("http://")) {
            checks.add(new Check("Source", true, true,
                    "Plain HTTP may be blocked by the WebView on newer Android versions."));
        } else {
            checks.add(new Check("Source", true, false, "Secure remote URL."));
        }
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            checks.add(new Check("Source", false, false, "The URL has no host."));
        } else {
            checks.add(new Check("Host", true, false, host));
        }
    }

    private static void checkInline(Project project, List<Check> checks) {
        String html = project.getInlineHtml();
        if (html == null || html.trim().isEmpty()) {
            checks.add(new Check("Source", false, false, "The inline HTML is empty."));
            return;
        }
        String lower = html.toLowerCase(Locale.US);
        boolean hasDocument = lower.contains("<html") || lower.contains("<body")
                || lower.contains("<!doctype");
        checks.add(new Check("Source", true, hasDocument ? false : true,
                hasDocument ? "Inline document detected."
                        : "No <html> or <body> tag — the page may render as plain text."));
        checks.add(new Check("Size", true, false, html.length() + " characters"));
    }

    private static void checkLocalFolder(Project project, String entry, List<Check> checks) {
        String path = project.getSourcePath();
        if (path == null || path.trim().isEmpty()) {
            checks.add(new Check("Source", false, false, "The source path is empty."));
            return;
        }
        File root = new File(path.trim());
        if (!root.exists()) {
            checks.add(new Check("Source", false, false,
                    "The source folder no longer exists: " + root.getAbsolutePath()));
            return;
        }
        if (!root.isDirectory()) {
            checks.add(new Check("Source", true, false, "Single file source."));
            return;
        }
        checks.add(new Check("Source", true, false, root.getAbsolutePath()));

        File entryFile = new File(root, entry);
        if (entryFile.isFile()) {
            checks.add(new Check("Entry point", true, false, entry));
            scanReferences(entryFile, root, checks);
        } else {
            checks.add(new Check("Entry point", false, false,
                    "The entry document was not found: " + entry));
        }
    }

    private static void checkSafFolder(Context context, Project project, String entry,
                                       List<Check> checks) {
        Uri treeUri = SafPermissionManager.fromSourcePath(project.getSourcePath());
        if (treeUri == null) {
            checks.add(new Check("Source", false, false, "The folder location is missing."));
            return;
        }
        ContentResolver resolver = context != null ? context.getContentResolver() : null;
        if (resolver == null) {
            checks.add(new Check("Source", false, false, "No content resolver is available."));
            return;
        }
        if (!SafPermissionManager.hasPersisted(resolver, treeUri)) {
            checks.add(new Check("Permission", false, false,
                    "Access to this folder was revoked. Select the folder again."));
            return;
        }
        checks.add(new Check("Permission", true, false, "Persisted folder access is valid."));

        String documentId = findChildDocumentId(resolver, treeUri, entry);
        if (documentId == null) {
            checks.add(new Check("Entry point", false, false,
                    "The entry document was not found in the folder: " + entry));
        } else {
            checks.add(new Check("Entry point", true, false, entry));
        }
    }

    /** Looks up a direct child of the tree root by display name. */
    private static String findChildDocumentId(ContentResolver resolver, Uri treeUri, String name) {
        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId);
            String[] projection = {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
            };
            try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
                if (cursor == null) {
                    return null;
                }
                while (cursor.moveToNext()) {
                    String displayName = cursor.getString(1);
                    if (displayName != null && displayName.equalsIgnoreCase(name)) {
                        return cursor.getString(0);
                    }
                }
            }
        } catch (Throwable ignored) {
            // A provider that cannot be queried is reported as a missing entry point.
        }
        return null;
    }

    /** Checks that local {@code src}/{@code href} targets exist next to the entry document. */
    private static void scanReferences(File entryFile, File root, List<Check> checks) {
        String html = readHead(entryFile);
        if (html.isEmpty()) {
            return;
        }
        File base = entryFile.getParentFile() != null ? entryFile.getParentFile() : root;
        Matcher matcher = REFERENCE.matcher(html);
        int checked = 0;
        int missing = 0;
        while (matcher.find() && missing < MAX_REFERENCE_ISSUES) {
            String reference = matcher.group(1);
            if (reference == null) {
                continue;
            }
            String value = reference.trim();
            if (value.isEmpty() || value.startsWith("#") || value.startsWith("data:")
                    || value.startsWith("mailto:") || value.startsWith("javascript:")
                    || value.startsWith("//")) {
                continue;
            }
            String lower = value.toLowerCase(Locale.US);
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                continue;
            }
            int cut = value.indexOf('?');
            if (cut >= 0) {
                value = value.substring(0, cut);
            }
            cut = value.indexOf('#');
            if (cut >= 0) {
                value = value.substring(0, cut);
            }
            if (value.isEmpty()) {
                continue;
            }
            checked++;
            File target = new File(base, value);
            if (!target.exists()) {
                missing++;
                checks.add(new Check("Asset", false, false, "Missing: " + value));
            }
        }
        if (checked > 0 && missing == 0) {
            checks.add(new Check("Assets", true, false,
                    checked + " local reference(s) resolved."));
        }
    }

    private static String readHead(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[MAX_SCAN_BYTES];
            int read = in.read(buffer);
            if (read <= 0) {
                return "";
            }
            return new String(buffer, 0, read, "UTF-8");
        } catch (Throwable error) {
            return "";
        }
    }
}
