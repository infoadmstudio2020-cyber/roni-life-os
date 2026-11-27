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
 * Functional Android UI implementation for ScreenOrientation component in category SystemUI.
 */
public class ScreenOrientationComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                SystemUI.SCREEN_ORIENTATION,
                "ScreenOrientation",
                "Native functional implementation of ScreenOrientation component",
                true,
                50,
                false
        );
    }

    public ScreenOrientationComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ScreenOrientationComponent() {
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
