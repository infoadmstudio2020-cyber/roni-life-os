package com.roni.library.ui.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Serves a web application from a directory on the device file system.
 */
public final class FileResourceProvider implements WebResourceProvider {

    private final File root;
    private final String entryPoint;

    public FileResourceProvider(File root, String entryPoint) {
        this.root = root;
        this.entryPoint = AssetResourceProvider.normalize(entryPoint);
    }

    @Override
    public WebResource open(String path) {
        String p = AssetResourceProvider.normalize(path);
        File file = resolve(p);
        if (file == null) return WebResource.notFound();

        if (file.isDirectory()) {
            file = new File(file, "index.html");
        }
        if (!file.exists() && !AssetResourceProvider.hasExtension(p)) {
            file = resolve(entryPoint);
        }
        if (file == null || !file.isFile()) return WebResource.notFound();

        try {
            return WebResource.of(new FileInputStream(file), MimeTypes.fromPath(file.getName()), file.length());
        } catch (IOException e) {
            return WebResource.notFound();
        }
    }

    private File resolve(String path) {
        try {
            File candidate = new File(root, path);
            String canonical = candidate.getCanonicalPath();
            String rootCanonical = root.getCanonicalPath();
            if (!canonical.equals(rootCanonical)
                    && !canonical.startsWith(rootCanonical + File.separator)) {
                return null;
            }
            return candidate;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void close() {
        // Nothing to release.
    }
}
