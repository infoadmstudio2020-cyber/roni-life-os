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
 * Functional Android UI implementation for WebOfflineView component in category WebHost.
 */
public class WebOfflineViewComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                WebHost.WEB_OFFLINE_VIEW,
                "WebOfflineView",
                "Native functional implementation of WebOfflineView component",
                true,
                50,
                true
        );
    }

    public WebOfflineViewComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public WebOfflineViewComponent() {
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

        android.widget.LinearLayout ll = new android.widget.LinearLayout(context);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setGravity(android.view.Gravity.CENTER);
        android.widget.TextView tv = new android.widget.TextView(context);
        tv.setText("No Internet Connection");
        ll.addView(tv);
        return ll;
    
    }
}
