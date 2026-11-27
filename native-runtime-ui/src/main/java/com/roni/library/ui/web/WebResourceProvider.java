package com.roni.library.ui.web;

/**
 * Resolves virtual-origin request paths to local web resources.
 */
public interface WebResourceProvider {

    /**
     * @param path request path without the virtual origin (for example {@code css/app.css})
     * @return a resource, or {@link WebResource#notFound()} when missing
     */
    WebResource open(String path);

    /** Releases any open archive/file handles. */
    void close();
}
