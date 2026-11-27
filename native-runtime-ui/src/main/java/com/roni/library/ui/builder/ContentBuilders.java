package com.roni.library.ui.builder;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Basic to Advanced Builders for Content Components (Group 3).
 * Components:
 * 12. WebView Container
 * 13. Custom View Container
 * 14. Fragment Container
 * 15. Multiple Content Containers
 */
public final class ContentBuilders {

    private ContentBuilders() {}

    // ==========================================
    // 12. WebView Container Config & Builder
    // ==========================================
    public static class WebViewConfig {
        private final boolean enabled;
        private final String initialUrl;
        private final boolean javascriptEnabled;
        private final boolean domStorageEnabled;
        private final boolean enableZoom;
        private final boolean showProgressBar;
        private final boolean supportPullToRefresh;
        private final int cacheMode;
        private final Map<String, String> customHeaders;

        private WebViewConfig(Builder b) {
            this.enabled = b.enabled;
            this.initialUrl = b.initialUrl;
            this.javascriptEnabled = b.javascriptEnabled;
            this.domStorageEnabled = b.domStorageEnabled;
            this.enableZoom = b.enableZoom;
            this.showProgressBar = b.showProgressBar;
            this.supportPullToRefresh = b.supportPullToRefresh;
            this.cacheMode = b.cacheMode;
            this.customHeaders = new HashMap<>(b.customHeaders);
        }

        public static Builder builder() { return new Builder(); }
        public static WebViewConfig basic(String initialUrl) {
            return builder().initialUrl(initialUrl).build();
        }

        public boolean isEnabled() { return enabled; }
        public String getInitialUrl() { return initialUrl; }
        public boolean isJavascriptEnabled() { return javascriptEnabled; }
        public boolean isDomStorageEnabled() { return domStorageEnabled; }
        public boolean isEnableZoom() { return enableZoom; }
        public boolean isShowProgressBar() { return showProgressBar; }
        public boolean isSupportPullToRefresh() { return supportPullToRefresh; }
        public int getCacheMode() { return cacheMode; }
        public Map<String, String> getCustomHeaders() { return customHeaders; }

        public static class Builder {
            private boolean enabled = true;
            private String initialUrl = "";
            private boolean javascriptEnabled = true;
            private boolean domStorageEnabled = true;
            private boolean enableZoom = false;
            private boolean showProgressBar = true;
            private boolean supportPullToRefresh = false;
            private int cacheMode = -1; // LOAD_DEFAULT
            private final Map<String, String> customHeaders = new HashMap<>();

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder initialUrl(String url) { this.initialUrl = url != null ? url : ""; return this; }
            public Builder javascriptEnabled(boolean js) { this.javascriptEnabled = js; return this; }
            public Builder domStorageEnabled(boolean dom) { this.domStorageEnabled = dom; return this; }
            public Builder enableZoom(boolean zoom) { this.enableZoom = zoom; return this; }
            public Builder showProgressBar(boolean show) { this.showProgressBar = show; return this; }
            public Builder supportPullToRefresh(boolean ptr) { this.supportPullToRefresh = ptr; return this; }
            public Builder cacheMode(int mode) { this.cacheMode = mode; return this; }
            public Builder addHeader(String key, String value) { this.customHeaders.put(key, value); return this; }
            public WebViewConfig build() { return new WebViewConfig(this); }
        }
    }

    // ==========================================
    // 13. Custom View Container Config & Builder
    // ==========================================
    public static class CustomViewConfig {
        private final boolean enabled;
        private final int layoutResId;
        private final int backgroundColor;

        private CustomViewConfig(Builder b) {
            this.enabled = b.enabled;
            this.layoutResId = b.layoutResId;
            this.backgroundColor = b.backgroundColor;
        }

        public static Builder builder() { return new Builder(); }
        public static CustomViewConfig basic(@LayoutRes int layoutResId) {
            return builder().layoutResId(layoutResId).build();
        }

        public boolean isEnabled() { return enabled; }
        public int getLayoutResId() { return layoutResId; }
        public int getBackgroundColor() { return backgroundColor; }

        public static class Builder {
            private boolean enabled = true;
            private int layoutResId = 0;
            private int backgroundColor = 0;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder layoutResId(@LayoutRes int resId) { this.layoutResId = resId; return this; }
            public Builder backgroundColor(int color) { this.backgroundColor = color; return this; }
            public CustomViewConfig build() { return new CustomViewConfig(this); }
        }
    }

    // ==========================================
    // 14. Fragment Container Config & Builder
    // ==========================================
    public static class FragmentContainerConfig {
        private final boolean enabled;
        private final int containerId;
        private final boolean enableAnimations;

        private FragmentContainerConfig(Builder b) {
            this.enabled = b.enabled;
            this.containerId = b.containerId;
            this.enableAnimations = b.enableAnimations;
        }

        public static Builder builder() { return new Builder(); }
        public static FragmentContainerConfig basic(@IdRes int containerId) {
            return builder().containerId(containerId).build();
        }

        public boolean isEnabled() { return enabled; }
        public int getContainerId() { return containerId; }
        public boolean isEnableAnimations() { return enableAnimations; }

        public static class Builder {
            private boolean enabled = true;
            private int containerId = 0;
            private boolean enableAnimations = true;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder containerId(@IdRes int id) { this.containerId = id; return this; }
            public Builder enableAnimations(boolean anim) { this.enableAnimations = anim; return this; }
            public FragmentContainerConfig build() { return new FragmentContainerConfig(this); }
        }
    }

    // ==========================================
    // 15. Multiple Content Containers Config & Builder
    // ==========================================
    public static class MultiContentConfig {
        private final boolean enabled;
        private final String defaultContentMode; // "CUSTOM", "WEBVIEW", "FRAGMENT"
        private final boolean keepStateInMemory;

        private MultiContentConfig(Builder b) {
            this.enabled = b.enabled;
            this.defaultContentMode = b.defaultContentMode;
            this.keepStateInMemory = b.keepStateInMemory;
        }

        public static Builder builder() { return new Builder(); }
        public static MultiContentConfig basic(String defaultMode) {
            return builder().defaultContentMode(defaultMode).build();
        }

        public boolean isEnabled() { return enabled; }
        public String getDefaultContentMode() { return defaultContentMode; }
        public boolean isKeepStateInMemory() { return keepStateInMemory; }

        public static class Builder {
            private boolean enabled = true;
            private String defaultContentMode = "CUSTOM";
            private boolean keepStateInMemory = true;

            public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
            public Builder defaultContentMode(String mode) { this.defaultContentMode = mode; return this; }
            public Builder keepStateInMemory(boolean keep) { this.keepStateInMemory = keep; return this; }
            public MultiContentConfig build() { return new MultiContentConfig(this); }
        }
    }

    // ==========================================
    // Complete Content Group Config & Builder
    // ==========================================
    public static class ContentGroupConfig {
        private final WebViewConfig webView;
        private final CustomViewConfig customView;
        private final FragmentContainerConfig fragmentContainer;
        private final MultiContentConfig multiContent;

        private ContentGroupConfig(Builder b) {
            this.webView = b.webView != null ? b.webView : WebViewConfig.builder().build();
            this.customView = b.customView != null ? b.customView : CustomViewConfig.builder().build();
            this.fragmentContainer = b.fragmentContainer != null ? b.fragmentContainer : FragmentContainerConfig.builder().build();
            this.multiContent = b.multiContent != null ? b.multiContent : MultiContentConfig.builder().build();
        }

        public static Builder builder() { return new Builder(); }

        public WebViewConfig getWebView() { return webView; }
        public CustomViewConfig getCustomView() { return customView; }
        public FragmentContainerConfig getFragmentContainer() { return fragmentContainer; }
        public MultiContentConfig getMultiContent() { return multiContent; }

        public static class Builder {
            private WebViewConfig webView;
            private CustomViewConfig customView;
            private FragmentContainerConfig fragmentContainer;
            private MultiContentConfig multiContent;

            public Builder webView(@NonNull WebViewConfig webView) { this.webView = webView; return this; }
            public Builder webView(@NonNull WebViewConfig.Builder builder) { this.webView = builder.build(); return this; }
            public Builder customView(@NonNull CustomViewConfig customView) { this.customView = customView; return this; }
            public Builder customView(@NonNull CustomViewConfig.Builder builder) { this.customView = builder.build(); return this; }
            public Builder fragmentContainer(@NonNull FragmentContainerConfig frag) { this.fragmentContainer = frag; return this; }
            public Builder fragmentContainer(@NonNull FragmentContainerConfig.Builder builder) { this.fragmentContainer = builder.build(); return this; }
            public Builder multiContent(@NonNull MultiContentConfig multi) { this.multiContent = multi; return this; }
            public Builder multiContent(@NonNull MultiContentConfig.Builder builder) { this.multiContent = builder.build(); return this; }
            public ContentGroupConfig build() { return new ContentGroupConfig(this); }
        }
    }
}
