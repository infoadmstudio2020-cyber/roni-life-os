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
 * Functional Android UI implementation for WindowInsets component in category SystemUI.
 */
public class WindowInsetsComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                SystemUI.WINDOW_INSETS,
                "WindowInsets",
                "Native functional implementation of WindowInsets component",
                true,
                50,
                true
        );
    }

    public WindowInsetsComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public WindowInsetsComponent() {
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

        android.view.View spacer = new android.view.View(context);
        spacer.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0));
        return spacer;
    
    }
}
