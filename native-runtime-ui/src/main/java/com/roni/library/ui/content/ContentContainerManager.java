package com.roni.library.ui.content;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.roni.library.ui.web.WebHostConfig;
import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebViewHost;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages Root, WebView, Custom Native Content, and Fragment container switching.
 */
public class ContentContainerManager {

    public enum ContentMode {
        CUSTOM_VIEW,
        WEBVIEW,
        FRAGMENT,
        EMPTY
    }

    private FrameLayout rootContainer;
    private WebViewContainer webViewContainer;
    private View customContentView;
    private final AtomicReference<ContentMode> currentMode = new AtomicReference<>(ContentMode.EMPTY);

    public ContentContainerManager() {
        this.rootContainer = null;
    }

    public ContentContainerManager(@NonNull Context context) {
        this.rootContainer = new FrameLayout(context);
        this.rootContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    @Nullable
    public FrameLayout getRootContainer() {
        return rootContainer;
    }

    /**
     * Ensures and returns the native WebViewContainer.
     */
    public synchronized WebViewContainer getOrCreateWebViewContainer(@NonNull Context context) {
        if (webViewContainer == null || webViewContainer.isDestroyed()) {
            webViewContainer = new WebViewContainer(context, WebHostConfig.defaultConfig());
        }
        return webViewContainer;
    }

    /**
     * Displays the native WebView container as the active content.
     */
    public synchronized void showWebView(@NonNull Context context, @Nullable String initialUrl) {
        showWebView(context, WebSource.fromUrl(initialUrl));
    }

    /**
     * Displays the native WebView container and loads any supported {@link WebSource}.
     */
    public synchronized void showWebView(@NonNull Context context, @Nullable WebSource source) {
        WebViewContainer container = getOrCreateWebViewContainer(context);
        if (rootContainer != null) {
            rootContainer.removeAllViews();
            rootContainer.addView(container);
        }
        currentMode.set(ContentMode.WEBVIEW);
        if (source != null) {
            container.loadSource(source);
        }
    }

    /**
     * Displays a custom Android View inside the content container.
     */
    public synchronized void showCustomView(@NonNull View view) {
        this.customContentView = view;
        if (rootContainer != null) {
            rootContainer.removeAllViews();
            rootContainer.addView(view);
        }
        currentMode.set(ContentMode.CUSTOM_VIEW);
    }

    /**
     * Hosts an Android Fragment inside the content container.
     */
    public synchronized void showFragment(@NonNull FragmentManager fragmentManager,
                                          @NonNull Fragment fragment,
                                          @Nullable String tag) {
        if (rootContainer == null) return;
        rootContainer.removeAllViews();
        if (rootContainer.getId() == View.NO_ID) {
            rootContainer.setId(View.generateViewId());
        }
        FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.replace(rootContainer.getId(), fragment, tag);
        ft.commitAllowingStateLoss();
        currentMode.set(ContentMode.FRAGMENT);
    }

    public ContentMode getCurrentMode() {
        return currentMode.get();
    }

    @Nullable
    public WebViewContainer getWebViewContainer() {
        return webViewContainer;
    }

    @Nullable
    public WebViewHost getWebViewHost() {
        return webViewContainer;
    }

    @Nullable
    public View getCustomContentView() {
        return customContentView;
    }

    public synchronized void release() {
        if (webViewContainer != null) {
            webViewContainer.destroy();
            webViewContainer = null;
        }
        if (rootContainer != null) {
            rootContainer.removeAllViews();
        }
        customContentView = null;
        currentMode.set(ContentMode.EMPTY);
    }
}
