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
 * Functional Android UI implementation for WebLoadingView component in category WebHost.
 */
public class WebLoadingViewComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                WebHost.WEB_LOADING_VIEW,
                "WebLoadingView",
                "Native functional implementation of WebLoadingView component",
                true,
                50,
                true
        );
    }

    public WebLoadingViewComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public WebLoadingViewComponent() {
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

        android.widget.ProgressBar pb = new android.widget.ProgressBar(context);
        pb.setIndeterminate(true);
        return pb;
    
    }
}
