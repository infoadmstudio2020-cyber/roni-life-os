package com.roni.library.ui.components.input;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Input;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for RadioButton component in category Input.
 */
public class RadioButtonComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Input.RADIO_BUTTON,
                "RadioButton",
                "Native functional implementation of RadioButton component",
                true,
                50,
                true
        );
    }

    public RadioButtonComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public RadioButtonComponent() {
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

        androidx.appcompat.widget.AppCompatRadioButton rb = new androidx.appcompat.widget.AppCompatRadioButton(context);
        rb.setText("Selection option");
        return rb;
    
    }
}
