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
 * Functional Android UI implementation for NestedScrollContainer component in category Content.
 */
public class NestedScrollContainerComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Content.NESTED_SCROLL_CONTAINER,
                "NestedScrollContainer",
                "Native functional implementation of NestedScrollContainer component",
                true,
                50,
                true
        );
    }

    public NestedScrollContainerComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public NestedScrollContainerComponent() {
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

        androidx.core.widget.NestedScrollView nsv = new androidx.core.widget.NestedScrollView(context);
        nsv.setFillViewport(true);
        return nsv;
    
    }
}
