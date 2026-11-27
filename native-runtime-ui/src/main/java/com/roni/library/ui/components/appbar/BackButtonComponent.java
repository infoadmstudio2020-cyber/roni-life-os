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
 * Functional Android UI implementation for BackButton component in category AppBar.
 */
public class BackButtonComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                AppBar.BACK_BUTTON,
                "BackButton",
                "Native functional implementation of BackButton component",
                true,
                50,
                true
        );
    }

    public BackButtonComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public BackButtonComponent() {
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

        androidx.appcompat.widget.AppCompatImageButton ib = new androidx.appcompat.widget.AppCompatImageButton(context);
        ib.setImageResource(android.R.drawable.ic_menu_revert);
        ib.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        ib.setContentDescription("Navigate Back");
        return ib;
    
    }
}
