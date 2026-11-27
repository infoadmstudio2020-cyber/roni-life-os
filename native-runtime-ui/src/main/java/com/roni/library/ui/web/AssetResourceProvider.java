package com.roni.library.ui.web;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/**
 * Serves a web application bundled in the APK assets directory.
 */
public final class AssetResourceProvider implements WebResourceProvider {

    private final AssetManager assets;
    private final String entryPoint;

    public AssetResourceProvider(Context context, String entryPoint) {
        Context app = context != null ? context.getApplicationContext() : null;
        this.assets = app != null ? app.getAssets() : null;
        this.entryPoint = normalize(entryPoint);
    }

    @Override
    public WebResource open(String path) {
        String p = normalize(path);
        WebResource resource = tryOpen(p);
        if (resource != null) return resource;

        if (!hasExtension(p)) {
            resource = tryOpen(join(p, "index.html"));
            if (resource != null) return resource;
        }

        if (!entryPoint.isEmpty() && !entryPoint.equals(p)) {
            resource = tryOpen(entryPoint);
            if (resource != null) return resource;
        }
        return WebResource.notFound();
    }

    private WebResource tryOpen(String path) {
        if (assets == null || path.isEmpty() || path.contains("..")) return null;
        try {
            InputStream stream = assets.open(path);
            return WebResource.of(stream, MimeTypes.fromPath(path), -1);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void close() {
        // AssetManager is owned by the application.
    }

    static String normalize(String path) {
        if (path == null) return "";
        String value = path.trim();
        try {
            value = Uri.decode(value);
        } catch (Throwable ignored) {
        }
        while (value.startsWith("/")) value = value.substring(1);
        return value;
    }

    static String join(String base, String child) {
        if (base == null || base.isEmpty()) return child != null ? child : "";
        if (child == null || child.isEmpty()) return base;
        return base.endsWith("/") ? base + child : base + "/" + child;
    }

    static boolean hasExtension(String path) {
        if (path == null) return false;
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash;
    }
}
