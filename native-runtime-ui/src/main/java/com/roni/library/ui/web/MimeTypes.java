package com.roni.library.ui.web;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal MIME type resolver for bundled web applications.
 */
public final class MimeTypes {

    private static final Map<String, String> TYPES = new HashMap<>();

    static {
        TYPES.put("html", "text/html");
        TYPES.put("htm", "text/html");
        TYPES.put("js", "application/javascript");
        TYPES.put("mjs", "application/javascript");
        TYPES.put("css", "text/css");
        TYPES.put("json", "application/json");
        TYPES.put("map", "application/json");
        TYPES.put("xml", "application/xml");
        TYPES.put("txt", "text/plain");
        TYPES.put("csv", "text/csv");
        TYPES.put("svg", "image/svg+xml");
        TYPES.put("png", "image/png");
        TYPES.put("jpg", "image/jpeg");
        TYPES.put("jpeg", "image/jpeg");
        TYPES.put("gif", "image/gif");
        TYPES.put("webp", "image/webp");
        TYPES.put("ico", "image/x-icon");
        TYPES.put("bmp", "image/bmp");
        TYPES.put("woff", "font/woff");
        TYPES.put("woff2", "font/woff2");
        TYPES.put("ttf", "font/ttf");
        TYPES.put("otf", "font/otf");
        TYPES.put("eot", "application/vnd.ms-fontobject");
        TYPES.put("wasm", "application/wasm");
        TYPES.put("mp3", "audio/mpeg");
        TYPES.put("wav", "audio/wav");
        TYPES.put("ogg", "audio/ogg");
        TYPES.put("mp4", "video/mp4");
        TYPES.put("webm", "video/webm");
        TYPES.put("pdf", "application/pdf");
        TYPES.put("zip", "application/zip");
    }

    private MimeTypes() {}

    public static String fromPath(String path) {
        if (path == null) return "application/octet-stream";
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "application/octet-stream";
        String ext = path.substring(dot + 1).toLowerCase(Locale.US);
        String type = TYPES.get(ext);
        return type != null ? type : "application/octet-stream";
    }
}
