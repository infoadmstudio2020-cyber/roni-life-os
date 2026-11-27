package com.roni.library.ui.components.overlay;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Overlay;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for FloatingMenu component in category Overlay.
 */
public class FloatingMenuComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Overlay.FLOATING_MENU,
                "FloatingMenu",
                "Native functional implementation of FloatingMenu component",
                true,
                50,
                true
        );
    }

    public FloatingMenuComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public FloatingMenuComponent() {
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

        android.widget.LinearLayout menu = new android.widget.LinearLayout(context);
        menu.setOrientation(android.widget.LinearLayout.VERTICAL);
        menu.setGravity(android.view.Gravity.END);
        return menu;
    
    }
}
