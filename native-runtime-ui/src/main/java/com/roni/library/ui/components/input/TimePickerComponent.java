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
 * Functional Android UI implementation for TimePicker component in category Input.
 */
public class TimePickerComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Input.TIME_PICKER,
                "TimePicker",
                "Native functional implementation of TimePicker component",
                true,
                50,
                true
        );
    }

    public TimePickerComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public TimePickerComponent() {
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

        android.widget.TimePicker tp = new android.widget.TimePicker(context);
        return tp;
    
    }
}
