package com.roni.library.ui.components.content;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Content;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for ContentContainer component in category Content.
 */
public class ContentContainerComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Content.CONTENT_CONTAINER,
                "ContentContainer",
                "Native functional implementation of ContentContainer component",
                true,
                50,
                true
        );
    }

    public ContentContainerComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ContentContainerComponent() {
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
return new android.widget.FrameLayout(context);
    }
}
