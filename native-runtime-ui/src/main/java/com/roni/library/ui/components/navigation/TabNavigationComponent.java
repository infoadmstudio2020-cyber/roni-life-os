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
 * Functional Android UI implementation for TabNavigation component in category Navigation.
 */
public class TabNavigationComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Navigation.TAB_NAVIGATION,
                "TabNavigation",
                "Native functional implementation of TabNavigation component",
                true,
                50,
                true
        );
    }

    public TabNavigationComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public TabNavigationComponent() {
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

        android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(context);
        android.widget.LinearLayout tabStrip = new android.widget.LinearLayout(context);
        tabStrip.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        tabStrip.setBackgroundColor(android.graphics.Color.parseColor("#334155"));
        hsv.addView(tabStrip);
        return hsv;
    
    }
}
