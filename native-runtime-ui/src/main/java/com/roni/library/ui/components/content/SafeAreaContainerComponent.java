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
 * Functional Android UI implementation for SafeAreaContainer component in category Content.
 */
public class SafeAreaContainerComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Content.SAFE_AREA_CONTAINER,
                "SafeAreaContainer",
                "Native functional implementation of SafeAreaContainer component",
                true,
                50,
                true
        );
    }

    public SafeAreaContainerComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public SafeAreaContainerComponent() {
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
        fl.setFitsSystemWindows(true);
        return fl;
    
    }
}
