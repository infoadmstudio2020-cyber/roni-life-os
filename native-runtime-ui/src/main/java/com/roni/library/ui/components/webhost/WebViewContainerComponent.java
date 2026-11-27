package com.roni.library.ui.components.webhost;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.WebHost;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for WebViewContainer component in category WebHost.
 */
public class WebViewContainerComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                WebHost.WEB_VIEW_CONTAINER,
                "WebViewContainer",
                "Native functional implementation of WebViewContainer component",
                true,
                50,
                true
        );
    }

    public WebViewContainerComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public WebViewContainerComponent() {
        this(createDescriptor());
    }

    @Nullable
    @Override
    protected View onCreateView(@NonNull Context context, @Nullable ViewGroup parent) {
        if (!isVisual()) {
            return null;
        }
        return createSpecificView(context, parent);
    }

    @Nullable
    protected View createSpecificView(@NonNull Context context, @Nullable ViewGroup parent) {

        android.webkit.WebView wv = new android.webkit.WebView(context);
        wv.getSettings().setJavaScriptEnabled(true);
        wv.getSettings().setDomStorageEnabled(true);
        return wv;
    
    }
}
