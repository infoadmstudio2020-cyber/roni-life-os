package com.roni.life.os.app.runtime;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectSourceType;
import com.roni.life.os.app.project.SafPermissionManager;

import java.io.File;
import java.io.InputStream;

/**
 * প্রজেক্টের সোর্স প্রকৃতপক্ষে অ্যাক্সেসযোগ্য কি না তা যাচাই করে।
 *
 * <p>রিমোট URL-এর ক্ষেত্রে শুধু ফরম্যাট যাচাই হয় — নেটওয়ার্ক কল করা হয় না।</p>
 */
public final class ProjectSourceInspector {

    private ProjectSourceInspector() {
    }

    public static final class Inspection {
        private final boolean available;
        private final String message;

        Inspection(boolean available, String message) {
            this.available = available;
            this.message = message;
        }

        public boolean isAvailable() {
            return available;
        }

        public String getMessage() {
            return message;
        }
    }

    public static Inspection inspect(Context context, Project project) {
        if (project == null) {
            return new Inspection(false, "The project was not found.");
        }
        ProjectSourceType type = project.getSourceType();
        String path = project.getSourcePath() != null ? project.getSourcePath().trim() : "";

        if (type == null) {
            return new Inspection(false, "No source type is set.");
        }

        switch (type) {
            case ASSET:
                return inspectAsset(context, path);
            case ZIP_ASSET:
                return inspectAssetZip(context, path);
            case FILE:
                return inspectFile(path);
            case ZIP_FILE:
                return inspectZipFile(path);
            case REMOTE_URL:
                return inspectRemote(path);
            case INLINE_HTML:
                return inspectInline(project.getInlineHtml());
            case SAF_FOLDER:
                return inspectSafFolder(context, path);
            default:
                return new Inspection(false, "Unsupported source type: " + type.getKey());
        }
    }

    private static Inspection inspectAsset(Context context, String path) {
        if (path.isEmpty()) {
            return new Inspection(false, "The asset path is empty.");
        }
        try {
            AssetManager assets = context.getAssets();
            String normalized = normalize(path);
            String[] children = assets.list(normalized);
            if (children != null && children.length > 0) {
                return new Inspection(true, "Asset folder found.");
            }
            InputStream stream = assets.open(normalized);
            if (stream != null) {
                stream.close();
                return new Inspection(true, "Asset file found.");
            }
            return new Inspection(false, "Asset not found: " + normalized);
        } catch (Throwable throwable) {
            return new Inspection(false, "The asset could not be read: " + path);
        }
    }

    private static Inspection inspectAssetZip(Context context, String path) {
        if (path.isEmpty()) {
            return new Inspection(false, "The ZIP asset path is empty.");
        }
        try {
            InputStream stream = context.getAssets().open(normalize(path));
            if (stream != null) {
                stream.close();
                return new Inspection(true, "ZIP asset found.");
            }
            return new Inspection(false, "ZIP asset not found: " + path);
        } catch (Throwable throwable) {
            return new Inspection(false, "The ZIP asset could not be opened: " + path);
        }
    }

    private static Inspection inspectFile(String path) {
        if (path.isEmpty()) {
            return new Inspection(false, "The file path is empty.");
        }
        File file = new File(stripScheme(path));
        if (!file.exists()) {
            return new Inspection(false, "File not found: " + file.getAbsolutePath());
        }
        return new Inspection(true, file.isDirectory() ? "Folder found." : "File found.");
    }

    private static Inspection inspectZipFile(String path) {
        if (path.isEmpty()) {
            return new Inspection(false, "The ZIP file path is empty.");
        }
        File file = new File(stripScheme(path));
        if (!file.exists() || !file.isFile()) {
            return new Inspection(false, "ZIP file not found: " + file.getAbsolutePath());
        }
        return new Inspection(true, "ZIP file found.");
    }

    private static Inspection inspectRemote(String path) {
        if (path.isEmpty()) {
            return new Inspection(false, "The remote URL is empty.");
        }
        String lower = path.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return new Inspection(false, "The URL must start with http:// or https://.");
        }
        return new Inspection(true, "URL format is valid.");
    }

    private static Inspection inspectInline(String html) {
        if (html == null || html.trim().isEmpty()) {
            return new Inspection(false, "Inline HTML is empty.");
        }
        return new Inspection(true, "Inline HTML is ready.");
    }

    private static String normalize(String path) {
        String value = path;
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String stripScheme(String path) {
        if (path.startsWith("file://")) {
            return path.substring("file://".length());
        }
        if (path.startsWith("file:")) {
            return path.substring("file:".length());
        }
        return path;
    }

    /**
     * A SAF folder is usable only while its persisted grant is still held. Listing the tree
     * proves the grant is live, not merely recorded in the resolver.
     */
    private static Inspection inspectSafFolder(Context context, String path) {
        if (path.isEmpty()) {
            return new Inspection(false, "The SAF folder path is empty.");
        }
        Uri treeUri = SafPermissionManager.fromSourcePath(path);
        if (treeUri == null) {
            return new Inspection(false, "The SAF folder URI is not valid.");
        }
        if (!SafPermissionManager.hasPersisted(context.getContentResolver(), treeUri)) {
            return new Inspection(false,
                    "Folder access was revoked. Select the folder again to restore the grant.");
        }
        Cursor cursor = null;
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            cursor = context.getContentResolver().query(children,
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                    null, null, null);
            if (cursor == null) {
                return new Inspection(false,
                        "The folder could not be read. Select it again to restore access.");
            }
            return new Inspection(true, "SAF folder is accessible.");
        } catch (Throwable throwable) {
            return new Inspection(false, "The folder could not be read: " + throwable.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
