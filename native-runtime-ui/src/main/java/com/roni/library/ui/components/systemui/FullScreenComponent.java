package com.roni.library.ui.components.systemui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.SystemUI;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for FullScreen component in category SystemUI.
 */
public class FullScreenComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                SystemUI.FULL_SCREEN,
                "FullScreen",
                "Native functional implementation of FullScreen component",
                true,
                50,
                false
        );
    }

    public FullScreenComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public FullScreenComponent() {
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
return null; // System window controller
    }
}
