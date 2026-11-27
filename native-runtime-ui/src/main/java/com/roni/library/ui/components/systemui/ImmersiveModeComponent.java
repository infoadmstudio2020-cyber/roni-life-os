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
 * Functional Android UI implementation for ImmersiveMode component in category SystemUI.
 */
public class ImmersiveModeComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                SystemUI.IMMERSIVE_MODE,
                "ImmersiveMode",
                "Native functional implementation of ImmersiveMode component",
                true,
                50,
                false
        );
    }

    public ImmersiveModeComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ImmersiveModeComponent() {
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
