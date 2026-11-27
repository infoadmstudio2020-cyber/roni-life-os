package com.roni.library.bridge;

/**
 * Supplies the origin of the page currently loaded in the bridged WebView.
 *
 * <p>The bridge cannot read the WebView itself (it has no Android dependency), so the host
 * that owns the WebView must supply this. {@code WebViewHost} implements it by returning the
 * origin of {@code webView.getUrl()}.</p>
 *
 * <p>Returning {@code null} means "origin unknown" and is treated as untrusted.</p>
 */
@FunctionalInterface
public interface OriginProvider {

    /**
     * @return the current page origin, e.g. {@code "https://appassets.androidplatform.net"},
     *         or {@code null} when it cannot be determined.
     */
    String getCurrentOrigin();
}
