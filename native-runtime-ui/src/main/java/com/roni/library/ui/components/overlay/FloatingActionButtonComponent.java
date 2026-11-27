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
 * Functional Android UI implementation for FloatingActionButton component in category Overlay.
 */
public class FloatingActionButtonComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Overlay.FLOATING_ACTION_BUTTON,
                "FloatingActionButton",
                "Native functional implementation of FloatingActionButton component",
                true,
                50,
                true
        );
    }

    public FloatingActionButtonComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public FloatingActionButtonComponent() {
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

        androidx.appcompat.widget.AppCompatImageButton fab = new androidx.appcompat.widget.AppCompatImageButton(context);
        fab.setImageResource(android.R.drawable.ic_input_add);
        fab.setBackgroundColor(android.graphics.Color.parseColor("#0284C7"));
        fab.setColorFilter(android.graphics.Color.WHITE);
        fab.setElevation(12f);
        return fab;
    
    }
}
