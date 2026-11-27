package com.roni.library.ui.components.navigation;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Navigation;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for TabLayout component in category Navigation.
 */
public class TabLayoutComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Navigation.TAB_LAYOUT,
                "TabLayout",
                "Native functional implementation of TabLayout component",
                true,
                50,
                true
        );
    }

    public TabLayoutComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public TabLayoutComponent() {
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

        android.widget.LinearLayout strip = new android.widget.LinearLayout(context);
        strip.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        strip.setBackgroundColor(android.graphics.Color.parseColor("#0F172A"));
        strip.setPadding(16, 16, 16, 16);
        return strip;
    
    }
}
