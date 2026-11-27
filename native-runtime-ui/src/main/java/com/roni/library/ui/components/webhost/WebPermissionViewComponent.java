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
 * Functional Android UI implementation for WebPermissionView component in category WebHost.
 */
public class WebPermissionViewComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                WebHost.WEB_PERMISSION_VIEW,
                "WebPermissionView",
                "Native functional implementation of WebPermissionView component",
                true,
                50,
                false
        );
    }

    public WebPermissionViewComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public WebPermissionViewComponent() {
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
return null;
    }
}
