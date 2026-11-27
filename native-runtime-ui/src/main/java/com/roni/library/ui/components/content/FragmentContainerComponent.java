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
 * Functional Android UI implementation for FragmentContainer component in category Content.
 */
public class FragmentContainerComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Content.FRAGMENT_CONTAINER,
                "FragmentContainer",
                "Native functional implementation of FragmentContainer component",
                true,
                50,
                true
        );
    }

    public FragmentContainerComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public FragmentContainerComponent() {
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

        android.widget.FrameLayout fl = new android.widget.FrameLayout(context);
        fl.setId(android.view.View.generateViewId());
        return fl;
    
    }
}
