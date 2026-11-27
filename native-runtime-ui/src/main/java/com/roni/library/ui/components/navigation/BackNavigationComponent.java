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
 * Functional Android UI implementation for BackNavigation component in category Navigation.
 */
public class BackNavigationComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Navigation.BACK_NAVIGATION,
                "BackNavigation",
                "Native functional implementation of BackNavigation component",
                true,
                50,
                true
        );
    }

    public BackNavigationComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public BackNavigationComponent() {
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

        androidx.appcompat.widget.AppCompatImageButton btn = new androidx.appcompat.widget.AppCompatImageButton(context);
        btn.setImageResource(android.R.drawable.ic_menu_revert);
        btn.setContentDescription("Back Navigation");
        return btn;
    
    }
}
