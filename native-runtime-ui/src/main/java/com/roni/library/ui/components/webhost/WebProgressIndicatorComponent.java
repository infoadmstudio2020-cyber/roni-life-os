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
 * Functional Android UI implementation for WebProgressIndicator component in category WebHost.
 */
public class WebProgressIndicatorComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                WebHost.WEB_PROGRESS_INDICATOR,
                "WebProgressIndicator",
                "Native functional implementation of WebProgressIndicator component",
                true,
                50,
                true
        );
    }

    public WebProgressIndicatorComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public WebProgressIndicatorComponent() {
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

        android.widget.ProgressBar pb = new android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 8));
        return pb;
    
    }
}
