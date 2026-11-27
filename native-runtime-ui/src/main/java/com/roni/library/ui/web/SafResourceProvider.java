package com.roni.library.ui.web;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.InputStream;

/**
 * Serves a web application that lives in a Storage Access Framework tree.
 *
 * <p>The tree is never copied and never exposed as {@code file://}. Every request path is mapped
 * to a document inside the tree and streamed through the provider, so relative imports, ES6
 * modules, stylesheets, images and {@code fetch()} all resolve against the virtual origin while
 * the user's files stay exactly where they are.</p>
 */
public final class SafResourceProvider implements WebResourceProvider {

    private final ContentResolver resolver;
    private final Uri treeUri;
    private final String treeId;
    private final String entryPoint;

    public SafResourceProvider(Context context, Uri treeUri, String entryPoint) {
        Context app = context != null ? context.getApplicationContext() : null;
        this.resolver = app != null ? app.getContentResolver() : null;
        this.treeUri = treeUri;
        this.treeId = safeTreeId(treeUri);
        this.entryPoint = AssetResourceProvider.normalize(entryPoint);
    }

    @Override
    public WebResource open(String path) {
        String normalized = AssetResourceProvider.normalize(path);
        WebResource resource = tryOpen(normalized);
        if (resource != null) {
            return resource;
        }

        if (!AssetResourceProvider.hasExtension(normalized)) {
            resource = tryOpen(AssetResourceProvider.join(normalized, "index.html"));
            if (resource != null) {
                return resource;
            }
        }

        if (!entryPoint.isEmpty() && !entryPoint.equals(normalized)) {
            resource = tryOpen(entryPoint);
            if (resource != null) {
                return resource;
            }
        }
        return WebResource.notFound();
    }

    private WebResource tryOpen(String path) {
        if (resolver == null || treeUri == null || treeId == null) {
            return null;
        }
        // Traversal is rejected before the document id is built, so a crafted request can never
        // escape the granted tree.
        if (path.isEmpty() || path.contains("..")) {
            return null;
        }
        try {
            String documentId = treeId + "/" + path;
            Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
            InputStream stream = resolver.openInputStream(documentUri);
            if (stream == null) {
                return null;
            }
            return WebResource.of(stream, MimeTypes.fromPath(path), -1);
        } catch (Throwable throwable) {
            return null;
        }
    }

    @Override
    public void close() {
        // The ContentResolver is owned by the application; streams are closed by the host.
    }

    private static String safeTreeId(Uri uri) {
        if (uri == null) {
            return null;
        }
        try {
            return DocumentsContract.getTreeDocumentId(uri);
        } catch (Throwable throwable) {
            return null;
        }
    }
}
