package com.roni.library.ui.web;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration for the NativeRuntime WebView host.
 */
public final class WebHostConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int CACHE_DEFAULT = -1;
    public static final int CACHE_ELSE_NETWORK = 1;
    public static final int CACHE_NO_CACHE = 2;
    public static final int CACHE_ONLY = 3;

    private final boolean javaScriptEnabled;
    private final boolean domStorageEnabled;
    private final boolean databaseEnabled;
    private final boolean allowFileAccess;
    private final boolean allowContentAccess;
    private final boolean allowZoom;
    private final boolean showProgressBar;
    private final boolean bridgeEnabled;
    private final String bridgeInterfaceName;
    private final boolean openExternalLinksInBrowser;
    private final Set<String> externalHosts;
    private final boolean mediaPlaybackRequiresUserGesture;
    private final int cacheMode;
    private final String userAgentSuffix;
    private final int backgroundColor;
    private final boolean mixedContentAllowed;
    private final boolean safeBrowsingEnabled;
    private final boolean debugLogging;
    private final boolean supportMultipleWindows;
    private final boolean fileChooserEnabled;
    private final boolean downloadsEnabled;
    private final ResourceDecoder resourceDecoder;
    private final int textZoom;
    private final int initialScale;
    private final boolean useWideViewPort;
    private final boolean loadWithOverviewMode;
    private final boolean hardwareAcceleration;
    private final boolean clearCacheOnDestroy;
    private final transient WebPermissionHandler permissionHandler;

    private WebHostConfig(Builder b) {
        this.javaScriptEnabled = b.javaScriptEnabled;
        this.domStorageEnabled = b.domStorageEnabled;
        this.databaseEnabled = b.databaseEnabled;
        this.allowFileAccess = b.allowFileAccess;
        this.allowContentAccess = b.allowContentAccess;
        this.allowZoom = b.allowZoom;
        this.showProgressBar = b.showProgressBar;
        this.bridgeEnabled = b.bridgeEnabled;
        this.bridgeInterfaceName = b.bridgeInterfaceName;
        this.openExternalLinksInBrowser = b.openExternalLinksInBrowser;
        this.externalHosts = Collections.unmodifiableSet(new HashSet<>(b.externalHosts));
        this.mediaPlaybackRequiresUserGesture = b.mediaPlaybackRequiresUserGesture;
        this.cacheMode = b.cacheMode;
        this.userAgentSuffix = b.userAgentSuffix;
        this.backgroundColor = b.backgroundColor;
        this.mixedContentAllowed = b.mixedContentAllowed;
        this.safeBrowsingEnabled = b.safeBrowsingEnabled;
        this.debugLogging = b.debugLogging;
        this.supportMultipleWindows = b.supportMultipleWindows;
        this.fileChooserEnabled = b.fileChooserEnabled;
        this.downloadsEnabled = b.downloadsEnabled;
        this.resourceDecoder = b.resourceDecoder;
        this.textZoom = b.textZoom;
        this.initialScale = b.initialScale;
        this.useWideViewPort = b.useWideViewPort;
        this.loadWithOverviewMode = b.loadWithOverviewMode;
        this.hardwareAcceleration = b.hardwareAcceleration;
        this.clearCacheOnDestroy = b.clearCacheOnDestroy;
        this.permissionHandler = b.permissionHandler;
    }

    public static Builder builder() { return new Builder(); }

    public static WebHostConfig defaultConfig() { return builder().build(); }

    public boolean isJavaScriptEnabled() { return javaScriptEnabled; }
    public boolean isDomStorageEnabled() { return domStorageEnabled; }
    public boolean isDatabaseEnabled() { return databaseEnabled; }
    public boolean isAllowFileAccess() { return allowFileAccess; }
    public boolean isAllowContentAccess() { return allowContentAccess; }
    public boolean isAllowZoom() { return allowZoom; }
    public boolean isShowProgressBar() { return showProgressBar; }
    public boolean isBridgeEnabled() { return bridgeEnabled; }
    public String getBridgeInterfaceName() { return bridgeInterfaceName; }
    public boolean isOpenExternalLinksInBrowser() { return openExternalLinksInBrowser; }
    public Set<String> getExternalHosts() { return externalHosts; }
    public boolean isExternalHost(String host) {
        return host != null && (externalHosts.contains(host) || externalHosts.contains("*"));
    }
    public boolean isMediaPlaybackRequiresUserGesture() { return mediaPlaybackRequiresUserGesture; }
    public int getCacheMode() { return cacheMode; }
    public String getUserAgentSuffix() { return userAgentSuffix; }
    public int getBackgroundColor() { return backgroundColor; }
    public boolean isMixedContentAllowed() { return mixedContentAllowed; }
    public boolean isSafeBrowsingEnabled() { return safeBrowsingEnabled; }
    public boolean isDebugLogging() { return debugLogging; }
    public boolean isSupportMultipleWindows() { return supportMultipleWindows; }
    public boolean isFileChooserEnabled() { return fileChooserEnabled; }
    public boolean isDownloadsEnabled() { return downloadsEnabled; }

    /** @return the decoder for encrypted projects, or {@code null} for plain sources */
    public ResourceDecoder getResourceDecoder() { return resourceDecoder; }
    public int getTextZoom() { return textZoom; }
    public int getInitialScale() { return initialScale; }
    public boolean isUseWideViewPort() { return useWideViewPort; }
    public boolean isLoadWithOverviewMode() { return loadWithOverviewMode; }
    public boolean isHardwareAcceleration() { return hardwareAcceleration; }

    /**
     * @return {@code true} when the HTTP cache must be wiped on host destruction. Project runtimes
     *         set this to {@code false} so their remote assets are not re-fetched on the next open.
     */
    public boolean isClearCacheOnDestroy() { return clearCacheOnDestroy; }

    /**
     * @return the configured web permission handler, or {@code null} (deny by default)
     */
    public WebPermissionHandler getPermissionHandler() {
        return permissionHandler;
    }

    public static final class Builder {
        private boolean javaScriptEnabled = true;
        private boolean domStorageEnabled = true;
        private boolean databaseEnabled = true;
        private boolean allowFileAccess = false;
        private boolean allowContentAccess = false;
        private boolean allowZoom = false;
        private boolean showProgressBar = true;
        private boolean bridgeEnabled = true;
        private String bridgeInterfaceName = "NativeRuntimeBridge";
        private boolean openExternalLinksInBrowser = true;
        private final Set<String> externalHosts = new HashSet<>();
        private boolean mediaPlaybackRequiresUserGesture = true;
        private int cacheMode = CACHE_DEFAULT;
        private WebPermissionHandler permissionHandler;
        private String userAgentSuffix = "";
        private int backgroundColor = 0xFFFFFFFF;
        private boolean mixedContentAllowed = false;
        private boolean safeBrowsingEnabled = true;
        private boolean debugLogging = false;
        private boolean supportMultipleWindows = false;
        private boolean fileChooserEnabled = true;
        private boolean downloadsEnabled = true;
        private ResourceDecoder resourceDecoder;
        private int textZoom = 100;
        private int initialScale = 0;
        private boolean useWideViewPort = true;
        private boolean loadWithOverviewMode = true;
        private boolean hardwareAcceleration = true;
        private boolean clearCacheOnDestroy = true;

        public Builder javaScriptEnabled(boolean enabled) { this.javaScriptEnabled = enabled; return this; }
        public Builder domStorageEnabled(boolean enabled) { this.domStorageEnabled = enabled; return this; }
        public Builder databaseEnabled(boolean enabled) { this.databaseEnabled = enabled; return this; }
        public Builder allowFileAccess(boolean enabled) { this.allowFileAccess = enabled; return this; }
        public Builder allowContentAccess(boolean enabled) { this.allowContentAccess = enabled; return this; }
        public Builder allowZoom(boolean enabled) { this.allowZoom = enabled; return this; }
        public Builder showProgressBar(boolean enabled) { this.showProgressBar = enabled; return this; }
        public Builder bridgeEnabled(boolean enabled) { this.bridgeEnabled = enabled; return this; }
        public Builder bridgeInterfaceName(String name) {
            if (name != null && !name.trim().isEmpty()) this.bridgeInterfaceName = name.trim();
            return this;
        }
        public Builder openExternalLinksInBrowser(boolean enabled) { this.openExternalLinksInBrowser = enabled; return this; }
        public Builder addExternalHost(String host) {
            if (host != null && !host.trim().isEmpty()) externalHosts.add(host.trim());
            return this;
        }
        public Builder mediaPlaybackRequiresUserGesture(boolean required) { this.mediaPlaybackRequiresUserGesture = required; return this; }
        public Builder cacheMode(int mode) { this.cacheMode = mode; return this; }
        public Builder permissionHandler(WebPermissionHandler handler) {
            this.permissionHandler = handler;
            return this;
        }
        public Builder userAgentSuffix(String suffix) { this.userAgentSuffix = suffix != null ? suffix : ""; return this; }
        public Builder backgroundColor(int color) { this.backgroundColor = color; return this; }
        public Builder mixedContentAllowed(boolean allowed) { this.mixedContentAllowed = allowed; return this; }
        public Builder safeBrowsingEnabled(boolean enabled) { this.safeBrowsingEnabled = enabled; return this; }
        public Builder debugLogging(boolean enabled) { this.debugLogging = enabled; return this; }
        public Builder supportMultipleWindows(boolean enabled) { this.supportMultipleWindows = enabled; return this; }
        public Builder fileChooserEnabled(boolean enabled) { this.fileChooserEnabled = enabled; return this; }
        public Builder downloadsEnabled(boolean enabled) { this.downloadsEnabled = enabled; return this; }

        /** Installs a decoder for encrypted projects. Leave unset for SAF and remote. */
        public Builder resourceDecoder(ResourceDecoder decoder) { this.resourceDecoder = decoder; return this; }
        public Builder textZoom(int zoom) { this.textZoom = zoom; return this; }
        public Builder initialScale(int scale) { this.initialScale = scale; return this; }
        public Builder useWideViewPort(boolean enabled) { this.useWideViewPort = enabled; return this; }
        public Builder loadWithOverviewMode(boolean enabled) { this.loadWithOverviewMode = enabled; return this; }
        public Builder hardwareAcceleration(boolean enabled) { this.hardwareAcceleration = enabled; return this; }

        /**
         * Keeps the WebView HTTP cache across host destruction.
         *
         * <p>Project runtimes set this to {@code false}: wiping the cache on every close is what
         * made a project re-fetch its remote assets on the next open. The cache is still cleared
         * by an explicit unregister/delete.</p>
         */
        public Builder clearCacheOnDestroy(boolean enabled) { this.clearCacheOnDestroy = enabled; return this; }

        public WebHostConfig build() { return new WebHostConfig(this); }
    }
}
