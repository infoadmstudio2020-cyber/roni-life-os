package com.roni.library.ui.web;

/**
 * Supported web application source types for the NativeRuntime WebView host.
 */
public enum WebSourceType {
    /** A remote http(s) URL. */
    REMOTE_URL,
    /** A page bundled inside the APK assets directory. */
    ASSET,
    /** A page or directory on the device file system. */
    FILE,
    /** A packaged web application inside a .zip archive (assets or file system). */
    ZIP,
    /** An in-memory HTML string. */
    INLINE_HTML,
    /** A content:// URI resolved by a ContentProvider. */
    CONTENT_URI
}
