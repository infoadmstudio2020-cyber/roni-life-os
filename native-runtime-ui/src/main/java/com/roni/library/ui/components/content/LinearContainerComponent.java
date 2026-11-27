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
 * Functional Android UI implementation for LinearContainer component in category Content.
 */
public class LinearContainerComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Content.LINEAR_CONTAINER,
                "LinearContainer",
                "Native functional implementation of LinearContainer component",
                true,
                50,
                true
        );
    }

    public LinearContainerComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public LinearContainerComponent() {
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
        return ll;
    
    }
}
