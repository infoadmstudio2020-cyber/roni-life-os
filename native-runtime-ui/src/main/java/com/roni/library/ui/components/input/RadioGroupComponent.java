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
 * Functional Android UI implementation for RadioGroup component in category Input.
 */
public class RadioGroupComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Input.RADIO_GROUP,
                "RadioGroup",
                "Native functional implementation of RadioGroup component",
                true,
                50,
                true
        );
    }

    public RadioGroupComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public RadioGroupComponent() {
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

        android.widget.RadioGroup rg = new android.widget.RadioGroup(context);
        rg.setOrientation(android.widget.RadioGroup.VERTICAL);
        return rg;
    
    }
}
