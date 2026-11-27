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
 * Functional Android UI implementation for NavigationBadge component in category Navigation.
 */
public class NavigationBadgeComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Navigation.NAVIGATION_BADGE,
                "NavigationBadge",
                "Native functional implementation of NavigationBadge component",
                true,
                50,
                true
        );
    }

    public NavigationBadgeComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public NavigationBadgeComponent() {
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

        android.widget.TextView badge = new android.widget.TextView(context);
        badge.setText("1");
        badge.setTextColor(android.graphics.Color.WHITE);
        badge.setTextSize(11f);
        badge.setBackgroundColor(android.graphics.Color.RED);
        badge.setPadding(12, 4, 12, 4);
        return badge;
    
    }
}
