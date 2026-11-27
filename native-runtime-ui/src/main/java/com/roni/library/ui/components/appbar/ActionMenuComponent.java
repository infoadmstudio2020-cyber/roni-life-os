package com.roni.library.ui.components.appbar;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.AppBar;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for ActionMenu component in category AppBar.
 */
public class ActionMenuComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                AppBar.ACTION_MENU,
                "ActionMenu",
                "Native functional implementation of ActionMenu component",
                true,
                50,
                true
        );
    }

    public ActionMenuComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ActionMenuComponent() {
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
        menu.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        menu.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        return menu;
    
    }
}
