package com.roni.library.ui.web;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable descriptor for a web application source.
 *
 * <p>Supported sources: remote URL, APK asset, device file, ZIP archive,
 * inline HTML and content:// URI. The same descriptor is used by the
 * full-screen {@link WebViewHost} and by the in-shell WebView container.</p>
 */
public final class WebSource implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String title;
    private final WebSourceType type;
    private final String location;
    private final String entryPoint;
    private final String baseUrl;
    private final String html;
    private final boolean fromAssets;
    private final String virtualHost;
    private final Map<String, String> headers;

    private WebSource(Builder b) {
        this.id = b.id != null && !b.id.isEmpty() ? b.id : "src-" + UUID.randomUUID();
        this.title = b.title != null && !b.title.isEmpty() ? b.title : defaultTitle(b.type, b.location);
        this.type = b.type != null ? b.type : WebSourceType.REMOTE_URL;
        this.location = b.location != null ? b.location : "";
        this.entryPoint = b.entryPoint != null ? b.entryPoint : "";
        this.baseUrl = b.baseUrl != null ? b.baseUrl : "";
        this.html = b.html != null ? b.html : "";
        this.fromAssets = b.fromAssets;
        this.virtualHost = b.virtualHost != null ? b.virtualHost : "";
        this.headers = Collections.unmodifiableMap(new HashMap<>(b.headers));
    }

    private static String defaultTitle(WebSourceType type, String location) {
        if (location != null && !location.isEmpty()) return location;
        return type != null ? type.name() : "Web App";
    }

    public static Builder builder(WebSourceType type) {
        return new Builder(type);
    }

    public static WebSource remote(String url) {
        return remote(null, null, url);
    }

    public static WebSource remote(String id, String title, String url) {
        return builder(WebSourceType.REMOTE_URL).id(id).title(title).location(url).build();
    }

    public static WebSource asset(String assetPath) {
        return asset(null, null, assetPath);
    }

    public static WebSource asset(String id, String title, String assetPath) {
        return builder(WebSourceType.ASSET).id(id).title(title).location(assetPath).build();
    }

    public static WebSource file(String path) {
        return file(null, null, path);
    }

    public static WebSource file(String id, String title, String path) {
        return builder(WebSourceType.FILE).id(id).title(title).location(path).build();
    }

    /** ZIP archive bundled in APK assets. */
    public static WebSource zipAsset(String assetPath) {
        return zipAsset(null, null, assetPath);
    }

    public static WebSource zipAsset(String id, String title, String assetPath) {
        return builder(WebSourceType.ZIP).id(id).title(title).location(assetPath).fromAssets(true).build();
    }

    /** ZIP archive on the device file system. */
    public static WebSource zipFile(String path) {
        return zipFile(null, null, path);
    }

    public static WebSource zipFile(String id, String title, String path) {
        return builder(WebSourceType.ZIP).id(id).title(title).location(path).fromAssets(false).build();
    }

    public static WebSource inlineHtml(String html) {
        return inlineHtml(null, null, html);
    }

    public static WebSource inlineHtml(String id, String title, String html) {
        return builder(WebSourceType.INLINE_HTML).id(id).title(title).html(html).build();
    }

    public static WebSource contentUri(String uri) {
        return contentUri(null, null, uri);
    }

    public static WebSource contentUri(String id, String title, String uri) {
        return builder(WebSourceType.CONTENT_URI).id(id).title(title).location(uri).build();
    }

    /**
     * Detects the source type from a URL/path. Legacy
     * {@code file:///android_asset/...} URLs are mapped to {@link WebSourceType#ASSET}.
     */
    public static WebSource fromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return remote("about:blank");
        }
        String value = url.trim();
        if (value.startsWith("file:///android_asset/")) {
            return asset(value.substring("file:///android_asset/".length()));
        }
        if (value.startsWith("https://" + WebPaths.LEGACY_HOST + "/")) {
            return asset(value.substring(("https://" + WebPaths.LEGACY_HOST + "/").length()));
        }
        if (value.startsWith("file://")) {
            return file(value.substring("file://".length()));
        }
        if (value.startsWith("content://")) {
            return contentUri(value);
        }
        return remote(value);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public WebSourceType getType() { return type; }
    public String getLocation() { return location; }
    public String getHtml() { return html; }
    public String getBaseUrl() { return baseUrl; }
    public boolean isFromAssets() { return fromAssets; }

    /**
     * Virtual HTTPS host this source is served from, or {@code ""} to use the shared legacy host.
     *
     * <p>A per-project host is what keeps two projects from sharing {@code localStorage},
     * {@code IndexedDB}, cookies and service-worker scope.</p>
     */
    public String getVirtualHost() { return virtualHost; }
    public Map<String, String> getHeaders() { return new HashMap<>(headers); }
    public boolean isLocal() { return type != WebSourceType.REMOTE_URL; }

    /**
     * Entry document used for directory sources, ZIP archives and SPA fallbacks.
     */
    public String getEntryPoint() {
        if (entryPoint != null && !entryPoint.isEmpty()) return entryPoint;
        if (location != null) {
            if (location.endsWith("/")) return location + "index.html";
            if (location.endsWith(".html") || location.endsWith(".htm")) return location;
        }
        return "index.html";
    }

    public WebSource withEntryPoint(String entry) {
        return toBuilder().entryPoint(entry).build();
    }

    public WebSource withBaseUrl(String base) {
        return toBuilder().baseUrl(base).build();
    }

    public WebSource withTitle(String newTitle) {
        return toBuilder().title(newTitle).build();
    }

    public WebSource withHeader(String key, String value) {
        Builder b = toBuilder();
        b.header(key, value);
        return b.build();
    }

    /**
     * Serves this source from {@code host} instead of the shared legacy host.
     *
     * <p>Use {@link WebPaths#virtualHostFor(String)} to derive a stable host from a project id.</p>
     */
    public WebSource withVirtualHost(String host) {
        return toBuilder().virtualHost(host).build();
    }

    public Builder toBuilder() {
        Builder b = new Builder(type);
        b.id = id;
        b.title = title;
        b.location = location;
        b.entryPoint = entryPoint;
        b.baseUrl = baseUrl;
        b.html = html;
        b.fromAssets = fromAssets;
        b.virtualHost = virtualHost;
        b.headers.putAll(headers);
        return b;
    }

    @Override
    public String toString() {
        return "WebSource{type=" + type + ", location='" + location + "', entry='" + getEntryPoint() + "'}";
    }

    public static final class Builder {
        private String id;
        private String title;
        private final WebSourceType type;
        private String location = "";
        private String entryPoint = "";
        private String baseUrl = "";
        private String html = "";
        private boolean fromAssets;
        private String virtualHost = "";
        private final Map<String, String> headers = new HashMap<>();

        public Builder(WebSourceType type) {
            this.type = type;
        }

        public Builder id(String id) { this.id = id; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder location(String location) { this.location = location; return this; }
        public Builder entryPoint(String entryPoint) { this.entryPoint = entryPoint; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder html(String html) { this.html = html; return this; }
        public Builder fromAssets(boolean fromAssets) { this.fromAssets = fromAssets; return this; }
        public Builder virtualHost(String host) { this.virtualHost = host != null ? host : ""; return this; }
        public Builder header(String key, String value) {
            if (key != null && value != null) headers.put(key, value);
            return this;
        }
        public WebSource build() { return new WebSource(this); }
    }
}
