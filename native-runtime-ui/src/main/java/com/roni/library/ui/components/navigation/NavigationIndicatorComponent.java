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
 * Functional Android UI implementation for NavigationIndicator component in category Navigation.
 */
public class NavigationIndicatorComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Navigation.NAVIGATION_INDICATOR,
                "NavigationIndicator",
                "Native functional implementation of NavigationIndicator component",
                true,
                50,
                true
        );
    }

    public NavigationIndicatorComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public NavigationIndicatorComponent() {
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

        android.view.View indicator = new android.view.View(context);
        indicator.setBackgroundColor(android.graphics.Color.parseColor("#0284C7"));
        indicator.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 8));
        return indicator;
    
    }
}
