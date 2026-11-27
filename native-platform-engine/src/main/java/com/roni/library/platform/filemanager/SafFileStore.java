package com.roni.library.platform.filemanager;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.filemanager.FileCategory;
import com.roni.library.contracts.filemanager.FileEntry;
import com.roni.library.contracts.filemanager.FileManagerErrorCodes;
import com.roni.library.storage.preferences.EncryptedPreferences;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SAF document tree access.
 *
 * <p>Only persisted tree grants are used. Documents are addressed by {@code treeId + documentId};
 * a content URI is never converted into a fabricated filesystem path. Providers that do not
 * support rename or delete surface {@code UNSUPPORTED_OPERATION} instead of failing silently.</p>
 */
public final class SafFileStore {

    public static final String ID_PREFIX = "saf:";
    private static final String PREFS_KEY = "saf_trees";

    private final ContentResolver resolver;
    @Nullable
    private final EncryptedPreferences preferences;

    public SafFileStore(@NonNull Context context, @Nullable EncryptedPreferences preferences) {
        this.resolver = context.getContentResolver();
        this.preferences = preferences;
    }

    /** Persists a tree grant and returns its stable id. */
    @NonNull
    public String registerTree(@NonNull Uri treeUri) {
        String id = Integer.toHexString(treeUri.toString().hashCode());
        Map<String, String> trees = readTrees();
        trees.put(id, treeUri.toString());
        writeTrees(trees);
        return id;
    }

    @NonNull
    public List<Map<String, Object>> listRoots() {
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map.Entry<String, String> entry : readTrees().entrySet()) {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("treeId", entry.getKey());
            root.put("uri", entry.getValue());
            root.put("name", displayName(entry.getKey(), treeDocumentId(entry.getValue())));
            roots.add(root);
        }
        return roots;
    }

    @Nullable
    public Uri treeUri(@NonNull String treeId) {
        String value = readTrees().get(treeId);
        return value != null ? Uri.parse(value) : null;
    }

    @NonNull
    public List<FileEntry> list(@NonNull String treeId, @Nullable String documentId)
            throws FileManagerException {
        Uri tree = requireTree(treeId);
        String parent = documentId != null && !documentId.isEmpty()
                ? documentId : treeDocumentId(tree.toString());
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent);
        List<FileEntry> entries = new ArrayList<>();
        try (Cursor cursor = resolver.query(childrenUri, new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        }, null, null, null)) {
            if (cursor == null) {
                throw new FileManagerException(FileManagerErrorCodes.SAF_PERMISSION_REQUIRED,
                        "The document provider did not return a cursor");
            }
            while (cursor.moveToNext()) {
                String docId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                long size = cursor.isNull(3) ? 0L : cursor.getLong(3);
                long modified = cursor.isNull(4) ? 0L : cursor.getLong(4);
                boolean directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                entries.add(new FileEntry.Builder()
                        .id(ID_PREFIX + treeId + ":" + docId)
                        .name(name)
                        .relativePath(docId)
                        .directory(directory)
                        .sizeBytes(size)
                        .modifiedAt(modified)
                        .mimeType(directory ? "inode/directory" : mime)
                        .category(directory ? FileCategory.OTHER : FileCategory.fromMime(mime))
                        .hidden(name != null && name.startsWith("."))
                        .provider("saf")
                        .build());
            }
        } catch (SecurityException error) {
            throw new FileManagerException(FileManagerErrorCodes.SAF_PERMISSION_REQUIRED,
                    "The SAF grant was revoked", error);
        }
        return entries;
    }

    @NonNull
    public InputStream openInput(@NonNull String treeId, @NonNull String documentId)
            throws FileManagerException {
        Uri uri = documentUri(treeId, documentId);
        try {
            InputStream input = resolver.openInputStream(uri);
            if (input == null) {
                throw new FileManagerException(FileManagerErrorCodes.IO_ERROR,
                        "The provider refused to open the document");
            }
            return input;
        } catch (SecurityException error) {
            throw new FileManagerException(FileManagerErrorCodes.SAF_PERMISSION_REQUIRED,
                    "The SAF grant was revoked", error);
        } catch (java.io.FileNotFoundException error) {
            throw new FileManagerException(FileManagerErrorCodes.NOT_FOUND,
                    "Document not found", error);
        }
    }

    @NonNull
    public OutputStream openOutput(@NonNull String treeId, @NonNull String documentId)
            throws FileManagerException {
        Uri uri = documentUri(treeId, documentId);
        try {
            OutputStream output = resolver.openOutputStream(uri, "w");
            if (output == null) {
                throw new FileManagerException(FileManagerErrorCodes.IO_ERROR,
                        "The provider refused to open the document for writing");
            }
            return output;
        } catch (SecurityException error) {
            throw new FileManagerException(FileManagerErrorCodes.SAF_PERMISSION_REQUIRED,
                    "The SAF grant was revoked", error);
        } catch (java.io.FileNotFoundException error) {
            throw new FileManagerException(FileManagerErrorCodes.NOT_FOUND,
                    "Document not found", error);
        }
    }

    @NonNull
    public FileEntry createDocument(@NonNull String treeId, @NonNull String parentDocumentId,
                                    @NonNull String name, @NonNull String mimeType)
            throws FileManagerException {
        FileManagerPaths.validateName(name);
        Uri parent = documentUri(treeId, parentDocumentId);
        try {
            Uri created = DocumentsContract.createDocument(resolver, parent, mimeType, name);
            if (created == null) {
                throw new FileManagerException(FileManagerErrorCodes.IO_ERROR,
                        "The provider refused to create the document");
            }
            String documentId = DocumentsContract.getDocumentId(created);
            return new FileEntry.Builder()
                    .id(ID_PREFIX + treeId + ":" + documentId)
                    .name(name)
                    .relativePath(documentId)
                    .directory(DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType))
                    .mimeType(mimeType)
                    .category(FileCategory.fromMime(mimeType))
                    .provider("saf")
                    .build();
        } catch (SecurityException error) {
            throw new FileManagerException(FileManagerErrorCodes.SAF_PERMISSION_REQUIRED,
                    "The SAF grant was revoked", error);
        } catch (java.io.FileNotFoundException error) {
            throw new FileManagerException(FileManagerErrorCodes.NOT_FOUND,
                    "Parent document not found", error);
        }
    }

    public void delete(@NonNull String treeId, @NonNull String documentId)
            throws FileManagerException {
        try {
            if (!DocumentsContract.deleteDocument(resolver, documentUri(treeId, documentId))) {
                throw new FileManagerException(FileManagerErrorCodes.UNSUPPORTED_OPERATION,
                        "This provider does not allow deletion");
            }
        } catch (SecurityException error) {
            throw new FileManagerException(FileManagerErrorCodes.SAF_PERMISSION_REQUIRED,
                    "The SAF grant was revoked", error);
        } catch (java.io.FileNotFoundException error) {
            throw new FileManagerException(FileManagerErrorCodes.NOT_FOUND,
                    "Document not found", error);
        }
    }

    @NonNull
    public FileEntry rename(@NonNull String treeId, @NonNull String documentId,
                            @NonNull String newName) throws FileManagerException {
        FileManagerPaths.validateName(newName);
        try {
            Uri renamed = DocumentsContract.renameDocument(resolver,
                    documentUri(treeId, documentId), newName);
            if (renamed == null) {
                throw new FileManagerException(FileManagerErrorCodes.UNSUPPORTED_OPERATION,
                        "This provider does not allow renaming");
            }
            String newId = DocumentsContract.getDocumentId(renamed);
            return new FileEntry.Builder()
                    .id(ID_PREFIX + treeId + ":" + newId)
                    .name(newName)
                    .relativePath(newId)
                    .provider("saf")
                    .build();
        } catch (UnsupportedOperationException error) {
            throw new FileManagerException(FileManagerErrorCodes.UNSUPPORTED_OPERATION,
                    "This provider does not allow renaming", error);
        } catch (SecurityException error) {
            throw new FileManagerException(FileManagerErrorCodes.SAF_PERMISSION_REQUIRED,
                    "The SAF grant was revoked", error);
        } catch (java.io.FileNotFoundException error) {
            throw new FileManagerException(FileManagerErrorCodes.NOT_FOUND,
                    "Document not found", error);
        }
    }

    @NonNull
    public String displayName(@NonNull String treeId, @NonNull String documentId) {
        Uri uri;
        try {
            uri = documentUri(treeId, documentId);
        } catch (FileManagerException error) {
            return documentId;
        }
        try (Cursor cursor = resolver.query(uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                return name != null ? name : documentId;
            }
        } catch (RuntimeException ignored) {
            // Fall through to the document id.
        }
        return documentId;
    }

    @NonNull
    private Uri documentUri(@NonNull String treeId, @NonNull String documentId)
            throws FileManagerException {
        return DocumentsContract.buildDocumentUriUsingTree(requireTree(treeId), documentId);
    }

    @NonNull
    private Uri requireTree(@NonNull String treeId) throws FileManagerException {
        Uri uri = treeUri(treeId);
        if (uri == null) {
            throw new FileManagerException(FileManagerErrorCodes.SAF_PERMISSION_REQUIRED,
                    "Unknown SAF tree: " + treeId);
        }
        return uri;
    }

    @NonNull
    private static String treeDocumentId(@NonNull String treeUri) {
        try {
            return DocumentsContract.getTreeDocumentId(Uri.parse(treeUri));
        } catch (RuntimeException error) {
            return "";
        }
    }

    @NonNull
    private Map<String, String> readTrees() {
        Map<String, String> trees = new LinkedHashMap<>();
        if (preferences == null) {
            return trees;
        }
        String raw = preferences.getString(PREFS_KEY, "");
        for (String line : raw.split("\n")) {
            int separator = line.indexOf('|');
            if (separator > 0 && separator < line.length() - 1) {
                trees.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return trees;
    }

    private void writeTrees(@NonNull Map<String, String> trees) {
        if (preferences == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : trees.entrySet()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(entry.getKey()).append('|').append(entry.getValue());
        }
        preferences.putString(PREFS_KEY, builder.toString());
    }
}
