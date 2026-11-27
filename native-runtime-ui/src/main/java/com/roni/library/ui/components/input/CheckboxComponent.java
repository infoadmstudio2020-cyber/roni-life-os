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
 * Functional Android UI implementation for Checkbox component in category Input.
 */
public class CheckboxComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Input.CHECKBOX,
                "Checkbox",
                "Native functional implementation of Checkbox component",
                true,
                50,
                true
        );
    }

    public CheckboxComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public CheckboxComponent() {
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

        androidx.appcompat.widget.AppCompatCheckBox cb = new androidx.appcompat.widget.AppCompatCheckBox(context);
        cb.setText("Accept option");
        return cb;
    
    }
}
