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
 * Functional Android UI implementation for ExtendedFloatingActionButton component in category Overlay.
 */
public class ExtendedFloatingActionButtonComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Overlay.EXTENDED_FLOATING_ACTION_BUTTON,
                "ExtendedFloatingActionButton",
                "Native functional implementation of ExtendedFloatingActionButton component",
                true,
                50,
                true
        );
    }

    public ExtendedFloatingActionButtonComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ExtendedFloatingActionButtonComponent() {
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

        androidx.appcompat.widget.AppCompatButton btn = new androidx.appcompat.widget.AppCompatButton(context);
        btn.setText("+ Create New");
        btn.setBackgroundColor(android.graphics.Color.parseColor("#0284C7"));
        btn.setTextColor(android.graphics.Color.WHITE);
        btn.setElevation(12f);
        return btn;
    
    }
}
