package com.roni.life.os.app.runtime;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;

import com.roni.library.ui.web.WebSource;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;
import com.roni.life.os.app.project.SafPermissionManager;
import com.roni.life.os.app.util.AppLog;

import java.util.Locale;

/**
 * Prepares a Storage Access Framework folder for the web runtime.
 *
 * <p>The folder is served through its {@code content://} tree URI — never {@code file://} — so
 * the runtime keeps the provider's permission model and the user's files stay where they are.
 * The runtime resolves each relative request against the tree, which gives ES6 modules,
 * relative imports, {@code fetch()} and asset loading a single consistent origin.</p>
 */
public final class SafWebSourceProvider {

    private static final String TAG = "SafWebSource";

    private SafWebSourceProvider() {
    }

    /** Result of preparing a SAF project for execution. */
    public static final class Prepared {
        private final boolean ready;
        private final String message;
        private final WebSource source;
        private final String entryPoint;

        Prepared(boolean ready, String message, WebSource source, String entryPoint) {
            this.ready = ready;
            this.message = message != null ? message : "";
            this.source = source;
            this.entryPoint = entryPoint != null ? entryPoint : "index.html";
        }

        public boolean isReady() {
            return ready;
        }

        public String getMessage() {
            return message;
        }

        public WebSource getSource() {
            return source;
        }

        public String getEntryPoint() {
            return entryPoint;
        }
    }

    /**
     * Validates the persisted grant and builds the {@link WebSource} for a SAF project.
     */
    public static Prepared prepare(Context context, Project project) {
        if (context == null || project == null) {
            return new Prepared(false, "No project was supplied.", null, null);
        }
        if (project.getSourceType() != ProjectSourceType.SAF_FOLDER) {
            return new Prepared(false, "This project is not backed by a SAF folder.", null, null);
        }

        Uri treeUri = SafPermissionManager.fromSourcePath(project.getSourcePath());
        if (treeUri == null) {
            return new Prepared(false,
                    "The folder location is missing. Select the folder again.", null, null);
        }

        ContentResolver resolver = context.getContentResolver();
        if (!SafPermissionManager.hasPersisted(resolver, treeUri)) {
            // EX-02: the grant was revoked (app data cleared, provider uninstalled, user reset).
            return new Prepared(false,
                    "Access to this folder was revoked. Select the folder again to restore it.",
                    null, null);
        }

        String entry = project.getEntryPoint() != null && !project.getEntryPoint().trim().isEmpty()
                ? project.getEntryPoint().trim() : "index.html";

        WebSource source = WebSource.contentUri(project.getId(), project.getName(),
                treeUri.toString()).withEntryPoint(entry);

        AppLog.i(TAG, "Prepared SAF source for '" + project.getName() + "' entry=" + entry);
        return new Prepared(true, "", source, entry);
    }

    /**
     * Resolves the MIME type of a document inside the tree.
     *
     * <p>Falls back to the file extension and finally to {@code application/octet-stream} so a
     * missing provider type never breaks module loading (EX-04).</p>
     */
    public static String resolveMimeType(Context context, Uri documentUri) {
        if (documentUri == null) {
            return "application/octet-stream";
        }
        if (context != null) {
            try {
                String type = context.getContentResolver().getType(documentUri);
                if (type != null && !type.isEmpty()) {
                    return type;
                }
            } catch (Throwable ignored) {
                // Fall through to the extension.
            }
        }
        String path = documentUri.getLastPathSegment();
        if (path != null) {
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && dot < path.length() - 1) {
                String extension = path.substring(dot + 1).toLowerCase(Locale.US);
                String mapped = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (mapped != null && !mapped.isEmpty()) {
                    return mapped;
                }
                if ("js".equals(extension) || "mjs".equals(extension)) {
                    return "text/javascript";
                }
                if ("json".equals(extension)) {
                    return "application/json";
                }
                if ("css".equals(extension)) {
                    return "text/css";
                }
                if ("html".equals(extension) || "htm".equals(extension)) {
                    return "text/html";
                }
            }
        }
        return "application/octet-stream";
    }

    /**
     * @return {@code true} when {@code documentUri} belongs to the {@code treeUri} subtree
     */
    public static boolean isWithinTree(Uri treeUri, Uri documentUri) {
        if (treeUri == null || documentUri == null) {
            return false;
        }
        try {
            String treeId = DocumentsContract.getTreeDocumentId(treeUri);
            String documentId = DocumentsContract.getDocumentId(documentUri);
            if (treeId == null || documentId == null) {
                return false;
            }
            return documentId.equals(treeId) || documentId.startsWith(treeId + "/");
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * @return the display name of the tree root, or an empty string when unavailable
     */
    public static String queryDisplayName(Context context, Uri treeUri) {
        if (context == null || treeUri == null) {
            return "";
        }
        try (Cursor cursor = context.getContentResolver().query(treeUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                return name != null ? name : "";
            }
        } catch (Throwable t) {
            AppLog.w(TAG, "Unable to query the folder name: " + t.getMessage());
        }
        return "";
    }
}
