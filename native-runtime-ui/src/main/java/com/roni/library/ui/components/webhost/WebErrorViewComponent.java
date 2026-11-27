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
 * Functional Android UI implementation for WebErrorView component in category WebHost.
 */
public class WebErrorViewComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                WebHost.WEB_ERROR_VIEW,
                "WebErrorView",
                "Native functional implementation of WebErrorView component",
                true,
                50,
                true
        );
    }

    public WebErrorViewComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public WebErrorViewComponent() {
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
        tv.setText("Failed to load webpage.");
        tv.setTextColor(android.graphics.Color.RED);
        ll.addView(tv);
        return ll;
    
    }
}
