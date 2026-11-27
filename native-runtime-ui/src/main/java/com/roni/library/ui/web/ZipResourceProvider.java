package com.roni.library.ui.web;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Serves a packaged web application from a ZIP archive (APK assets or file system).
 */
public final class ZipResourceProvider implements WebResourceProvider {

    private final ZipFile zipFile;
    private final Map<String, ZipEntry> entries = new HashMap<>();
    private final String entryPoint;
    private final File tempFile;
    private final Object lock = new Object();

    public ZipResourceProvider(Context context, String zipPath, boolean fromAssets, String entryPoint)
            throws IOException {
        this.entryPoint = AssetResourceProvider.normalize(entryPoint);

        File archive;
        if (fromAssets) {
            archive = copyAssetToCache(context, zipPath);
            this.tempFile = archive;
        } else {
            archive = new File(stripFileScheme(zipPath));
            this.tempFile = null;
        }

        if (!archive.exists() || !archive.isFile()) {
            throw new IOException("ZIP archive not found: " + archive.getAbsolutePath());
        }

        this.zipFile = new ZipFile(archive);
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = enumeration.nextElement();
            if (entry.isDirectory()) continue;
            String name = AssetResourceProvider.normalize(entry.getName());
            if (!name.isEmpty()) entries.put(name, entry);
        }
    }

    @Override
    public WebResource open(String path) {
        String p = AssetResourceProvider.normalize(path);
        synchronized (lock) {
            WebResource resource = tryOpen(p);
            if (resource != null) return resource;

            if (!AssetResourceProvider.hasExtension(p)) {
                resource = tryOpen(AssetResourceProvider.join(p, "index.html"));
                if (resource != null) return resource;
            }

            if (!entryPoint.isEmpty() && !entryPoint.equals(p)) {
                resource = tryOpen(entryPoint);
                if (resource != null) return resource;
            }
            return WebResource.notFound();
        }
    }

    private WebResource tryOpen(String path) {
        ZipEntry entry = entries.get(path);
        if (entry == null) return null;
        try {
            InputStream stream = zipFile.getInputStream(entry);
            return WebResource.of(stream, MimeTypes.fromPath(path), entry.getSize());
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                zipFile.close();
            } catch (IOException ignored) {
            }
        }
        if (tempFile != null) {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
        }
    }

    private static File copyAssetToCache(Context context, String assetPath) throws IOException {
        if (context == null) throw new IOException("Context is null");
        File base = context.getCacheDir();
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, "native-runtime-web");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create cache directory: " + dir.getAbsolutePath());
        }
        File out = new File(dir, Integer.toHexString(String.valueOf(assetPath).hashCode()) + ".zip");
        if (out.exists() && out.length() > 0) return out;

        try (InputStream in = context.getAssets().open(assetPath);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                os.write(buffer, 0, read);
            }
        }
        return out;
    }

    private static String stripFileScheme(String path) {
        if (path == null) return "";
        if (path.startsWith("file://")) return path.substring("file://".length());
        if (path.startsWith("file:")) return path.substring("file:".length());
        return path;
    }
}
