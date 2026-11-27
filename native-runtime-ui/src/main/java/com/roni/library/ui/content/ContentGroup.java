package com.roni.library.ui.content;

import android.app.Activity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebViewHost;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Group 3: Content Coordinator.
 * Coordinates all 4 Content components:
 *  12. WebView Container
 *  13. Custom View Container
 *  14. Fragment Container
 *  15. Multiple Content Containers (dynamic stacking, switching, or multi-panel)
 *
 * Supports standalone and multi-content modes.
 */
public class ContentGroup {

    private final ContentContainerManager containerManager;
    private final Map<String, View> namedViews = new ConcurrentHashMap<>();

    public ContentGroup() {
        this(new ContentContainerManager());
    }

    public ContentGroup(@NonNull ContentContainerManager containerManager) {
        this.containerManager = containerManager;
    }

    public ContentContainerManager getContainerManager() {
        return containerManager;
    }

    // Component 12: WebView Container
    public void showWebView(@NonNull Activity activity, @NonNull String url) {
        containerManager.showWebView(activity, url);
    }

    public void showWebView(@NonNull Activity activity, @NonNull WebSource source) {
        containerManager.showWebView(activity, source);
    }

    public void loadSource(@NonNull WebSource source) {
        WebViewContainer container = containerManager.getWebViewContainer();
        if (container != null) {
            container.loadSource(source);
        }
    }

    @Nullable
    public WebViewContainer getWebViewContainer() {
        return containerManager.getWebViewContainer();
    }

    @Nullable
    public WebViewHost getWebViewHost() {
        return containerManager.getWebViewHost();
    }

    public void loadUrl(@NonNull String url) {
        WebViewContainer container = containerManager.getWebViewContainer();
        if (container != null) {
            container.loadUrl(url);
        }
    }

    // Component 13: Custom View Container
    public void showCustomView(@NonNull View view) {
        containerManager.showCustomView(view);
    }

    // Component 14: Fragment Container
    public void showFragment(@NonNull FragmentActivity activity, @NonNull Fragment fragment) {
        containerManager.showFragment(activity.getSupportFragmentManager(), fragment,
                fragment.getClass().getSimpleName());
    }

    // Component 15: Multiple Content Containers
    public void registerNamedContent(@NonNull String name, @NonNull View view) {
        namedViews.put(name, view);
    }

    public void switchToNamedContent(@NonNull String name) {
        View target = namedViews.get(name);
        if (target != null) {
            containerManager.showCustomView(target);
        }
    }

    public boolean hasNamedContent(@NonNull String name) {
        return namedViews.containsKey(name);
    }

    @Nullable
    public FrameLayout getRootContainer() {
        return containerManager.getRootContainer();
    }
}
