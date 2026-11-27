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
 * Functional Android UI implementation for ScrollContainer component in category Content.
 */
public class ScrollContainerComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Content.SCROLL_CONTAINER,
                "ScrollContainer",
                "Native functional implementation of ScrollContainer component",
                true,
                50,
                true
        );
    }

    public ScrollContainerComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ScrollContainerComponent() {
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

        android.widget.ScrollView sv = new android.widget.ScrollView(context);
        sv.setFillViewport(true);
        return sv;
    
    }
}
