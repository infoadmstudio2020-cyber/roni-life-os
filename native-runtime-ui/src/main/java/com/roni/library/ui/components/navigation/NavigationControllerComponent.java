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
 * Functional Android UI implementation for NavigationController component in category Navigation.
 */
public class NavigationControllerComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Navigation.NAVIGATION_CONTROLLER,
                "NavigationController",
                "Native functional implementation of NavigationController component",
                true,
                50,
                false
        );
    }

    public NavigationControllerComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public NavigationControllerComponent() {
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

        // Non-visual navigation logic coordinator
        return null;
    
    }
}
