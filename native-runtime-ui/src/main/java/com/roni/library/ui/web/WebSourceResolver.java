package com.roni.library.ui.web;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import java.io.File;

/**
 * Resolves a {@link WebSource} into a loadable URL plus an optional local resource provider.
 *
 * <p>Local sources are served from a secure virtual HTTPS origin so that fetch/XHR, service
 * workers, localStorage and modern web APIs work exactly as they do on a real web server.</p>
 *
 * <p><b>One origin per project.</b> The host comes from {@link WebSource#getVirtualHost()}, which
 * the app derives with {@link WebPaths#virtualHostFor(String)}. A shared host would give every
 * project the same {@code localStorage}, {@code IndexedDB}, cookie jar and service-worker scope,
 * letting one project read another's persisted data. Sources that do not set a host fall back to
 * {@link #VIRTUAL_HOST}, which keeps older callers working.</p>
 */
public final class WebSourceResolver {

    /** Legacy shared host, used when a source does not set its own. */
    public static final String VIRTUAL_HOST = WebPaths.LEGACY_HOST;

    /** Legacy shared origin. */
    public static final String VIRTUAL_ORIGIN = "https://" + VIRTUAL_HOST;

    public ResolvedSource resolve(Context context, WebSource source) {
        if (source == null) return ResolvedSource.error("WebSource is null");
        String host = effectiveHost(source);
        try {
            switch (source.getType()) {
                case REMOTE_URL:
                    return ResolvedSource.remote(source.getLocation());

                case CONTENT_URI: {
                    String location = source.getLocation();
                    Uri contentUri = parseUri(location);
                    if (isTreeUri(contentUri)) {
                        // A SAF tree points at a folder, not a document. Serving it through the
                        // virtual origin lets relative imports and modules resolve correctly,
                        // and nothing is copied out of the user's folder.
                        String entry = source.getEntryPoint();
                        return ResolvedSource.local(host, virtualUrl(host, entry),
                                new SafResourceProvider(context, contentUri, entry), false, true);
                    }
                    return ResolvedSource.content(location);
                }

                case INLINE_HTML: {
                    String base = source.getBaseUrl();
                    if (base == null || base.isEmpty()) base = WebPaths.originOf(host) + "/";
                    boolean fileAccess = base.startsWith("file://");
                    boolean contentAccess = base.startsWith("content://");
                    return ResolvedSource.inline(host, source.getHtml(), base,
                            new AssetResourceProvider(context, source.getEntryPoint()),
                            fileAccess, contentAccess);
                }

                case ASSET: {
                    String entry = source.getEntryPoint();
                    String initial = initialPath(source.getLocation(), entry);
                    return ResolvedSource.local(host, virtualUrl(host, initial),
                            new AssetResourceProvider(context, entry), false, false);
                }

                case ZIP: {
                    String entry = source.getEntryPoint();
                    ZipResourceProvider provider = new ZipResourceProvider(
                            context, source.getLocation(), source.isFromAssets(), entry);
                    return ResolvedSource.local(host, virtualUrl(host, entry), provider, false, false);
                }

                case FILE: {
                    String path = stripFileScheme(source.getLocation());
                    File file = new File(path);
                    if (!file.exists()) {
                        return ResolvedSource.error("File not found: " + path);
                    }
                    if (file.isDirectory()) {
                        String entry = source.getEntryPoint();
                        return ResolvedSource.local(host, virtualUrl(host, entry),
                                new FileResourceProvider(file, entry), false, false);
                    }
                    return ResolvedSource.local(host, Uri.fromFile(file).toString(), null, true, false);
                }

                default:
                    return ResolvedSource.error("Unsupported source type: " + source.getType());
            }
        } catch (Throwable t) {
            String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            return ResolvedSource.error(message);
        }
    }

    /** @return the source's own host, or the legacy shared host when it has none */
    public static String effectiveHost(WebSource source) {
        if (source == null) return VIRTUAL_HOST;
        String host = source.getVirtualHost();
        return host != null && !host.trim().isEmpty() ? host.trim() : VIRTUAL_HOST;
    }

    /** @return {@code https://<host>/<path>} */
    public static String virtualUrl(String host, String path) {
        return WebPaths.virtualUrl(host, path);
    }

    /** @return {@code https://<legacy host>/<path>} */
    public static String virtualUrl(String path) {
        return WebPaths.virtualUrl(VIRTUAL_HOST, path);
    }

    private static String initialPath(String location, String entry) {
        String value = AssetResourceProvider.normalize(location);
        if (value.isEmpty() || value.endsWith("/")) return entry;
        return value;
    }

    private static String stripFileScheme(String path) {
        if (path == null) return "";
        if (path.startsWith("file://")) return path.substring("file://".length());
        if (path.startsWith("file:")) return path.substring("file:".length());
        return path;
    }

    /**
     * Result of resolving a source.
     */
    public static final class ResolvedSource {

        private final String url;
        private final WebResourceProvider provider;
        private final boolean inline;
        private final String inlineHtml;
        private final String baseUrl;
        private final boolean allowFileAccess;
        private final boolean allowContentAccess;
        private final String errorMessage;
        private final String virtualHost;

        private ResolvedSource(String url, WebResourceProvider provider, boolean inline,
                               String inlineHtml, String baseUrl, boolean allowFileAccess,
                               boolean allowContentAccess, String errorMessage, String virtualHost) {
            this.url = url != null ? url : "about:blank";
            this.provider = provider;
            this.inline = inline;
            this.inlineHtml = inlineHtml != null ? inlineHtml : "";
            this.baseUrl = baseUrl != null ? baseUrl : "";
            this.allowFileAccess = allowFileAccess;
            this.allowContentAccess = allowContentAccess;
            this.errorMessage = errorMessage;
            this.virtualHost = virtualHost != null ? virtualHost : "";
        }

        static ResolvedSource remote(String url) {
            return new ResolvedSource(url, null, false, "", "", false, false, null, "");
        }

        static ResolvedSource content(String url) {
            return new ResolvedSource(url, null, false, "", "", false, true, null, "");
        }

        static ResolvedSource local(String host, String url, WebResourceProvider provider,
                                    boolean allowFileAccess, boolean allowContentAccess) {
            return new ResolvedSource(url, provider, false, "", "", allowFileAccess,
                    allowContentAccess, null, host);
        }

        static ResolvedSource inline(String host, String html, String baseUrl,
                                     WebResourceProvider provider, boolean allowFileAccess,
                                     boolean allowContentAccess) {
            return new ResolvedSource(baseUrl, provider, true, html, baseUrl, allowFileAccess,
                    allowContentAccess, null, host);
        }

        static ResolvedSource error(String message) {
            return new ResolvedSource("about:blank", null, false, "", "", false, false,
                    message != null ? message : "Unknown web source error", "");
        }

        public String getUrl() { return url; }
        public WebResourceProvider getProvider() { return provider; }
        public boolean isInline() { return inline; }
        public String getInlineHtml() { return inlineHtml; }
        public String getBaseUrl() { return baseUrl; }
        public boolean isAllowFileAccess() { return allowFileAccess; }
        public boolean isAllowContentAccess() { return allowContentAccess; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isError() { return errorMessage != null; }

        /**
         * @return the host this source is served from, or {@code ""} for remote/content sources
         *         that are not served through the virtual origin
         */
        public String getVirtualHost() { return virtualHost; }
    }

    private static Uri parseUri(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Uri.parse(value);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static boolean isTreeUri(Uri uri) {
        if (uri == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                return DocumentsContract.isTreeUri(uri);
            } catch (Throwable ignored) {
                // Fall through to the path check.
            }
        }
        String path = uri.getPath();
        return path != null && path.contains("/tree/");
    }
}
