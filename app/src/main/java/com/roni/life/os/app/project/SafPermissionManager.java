package com.roni.life.os.app.project;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;

import com.roni.life.os.app.util.AppLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted Storage Access Framework permissions.
 *
 * <p>A folder picked with {@code ACTION_OPEN_DOCUMENT_TREE} is only readable until the process
 * dies unless the grant is persisted. Every SAF project therefore takes a persistable grant and
 * keeps the original tree URI — the folder is never copied into internal storage (EX-02).</p>
 */
public final class SafPermissionManager {

    private static final String TAG = "SafPermission";

    private static final int PERSIST_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private SafPermissionManager() {
    }

    /**
     * Persists read/write access to {@code treeUri}.
     *
     * @return {@code true} when the grant is persisted
     */
    public static boolean takePersistable(ContentResolver resolver, Uri treeUri) {
        if (resolver == null || treeUri == null) {
            return false;
        }
        try {
            resolver.takePersistableUriPermission(treeUri, PERSIST_FLAGS);
            return true;
        } catch (SecurityException e) {
            // Some providers only grant read access.
            try {
                resolver.takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                return true;
            } catch (SecurityException inner) {
                AppLog.w(TAG, "Unable to persist access to " + treeUri + ": " + inner.getMessage());
                return false;
            }
        } catch (Throwable t) {
            AppLog.w(TAG, "Unable to persist access to " + treeUri + ": " + t.getMessage());
            return false;
        }
    }

    /** @return {@code true} when a persisted grant exists for {@code treeUri} */
    public static boolean hasPersisted(ContentResolver resolver, Uri treeUri) {
        if (resolver == null || treeUri == null) {
            return false;
        }
        try {
            for (UriPermission permission : resolver.getPersistedUriPermissions()) {
                if (permission.getUri().equals(treeUri) && permission.isReadPermission()) {
                    return true;
                }
            }
        } catch (Throwable t) {
            AppLog.w(TAG, "Unable to read persisted permissions: " + t.getMessage());
        }
        return false;
    }

    /** Releases a persisted grant. The user's files are never touched. */
    public static void release(ContentResolver resolver, Uri treeUri) {
        if (resolver == null || treeUri == null) {
            return;
        }
        try {
            resolver.releasePersistableUriPermission(treeUri, PERSIST_FLAGS);
        } catch (Throwable ignored) {
            try {
                resolver.releasePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignoredAgain) {
                // Nothing was persisted.
            }
        }
    }

    /** @return every persisted tree URI, newest first */
    public static List<Uri> listPersisted(ContentResolver resolver) {
        List<Uri> result = new ArrayList<>();
        if (resolver == null) {
            return result;
        }
        try {
            for (UriPermission permission : resolver.getPersistedUriPermissions()) {
                result.add(permission.getUri());
            }
        } catch (Throwable t) {
            AppLog.w(TAG, "Unable to read persisted permissions: " + t.getMessage());
        }
        return result;
    }

    /** @return the project source path for a tree URI */
    public static String toSourcePath(Uri treeUri) {
        return treeUri != null ? treeUri.toString() : "";
    }

    /** @return the tree URI stored in a project source path, or {@code null} */
    public static Uri fromSourcePath(String sourcePath) {
        if (sourcePath == null || sourcePath.trim().isEmpty()) {
            return null;
        }
        try {
            Uri uri = Uri.parse(sourcePath.trim());
            return uri.getScheme() != null ? uri : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
