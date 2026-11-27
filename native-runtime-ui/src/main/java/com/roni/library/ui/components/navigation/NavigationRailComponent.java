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
 * Functional Android UI implementation for NavigationRail component in category Navigation.
 */
public class NavigationRailComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Navigation.NAVIGATION_RAIL,
                "NavigationRail",
                "Native functional implementation of NavigationRail component",
                true,
                50,
                true
        );
    }

    public NavigationRailComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public NavigationRailComponent() {
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

        android.widget.LinearLayout rail = new android.widget.LinearLayout(context);
        rail.setOrientation(android.widget.LinearLayout.VERTICAL);
        rail.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        rail.setBackgroundColor(android.graphics.Color.parseColor("#1E293B"));
        rail.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                180, android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        return rail;
    
    }
}
