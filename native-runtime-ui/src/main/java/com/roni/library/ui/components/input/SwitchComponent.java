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
 * Functional Android UI implementation for Switch component in category Input.
 */
public class SwitchComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Input.SWITCH,
                "Switch",
                "Native functional implementation of Switch component",
                true,
                50,
                true
        );
    }

    public SwitchComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public SwitchComponent() {
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

        // SafeSwitchCompat hardens textOn/textOff in its constructor, before the first
        // measure pass. A bare SwitchCompat created without a style leaves both labels
        // null, which crashes StaticLayout inside onMeasure when showText is enabled.
        com.roni.library.ui.widget.SafeSwitchCompat sw =
                new com.roni.library.ui.widget.SafeSwitchCompat(context);
        sw.setText("Enable Feature");
        return sw;
    
    }
}
