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
 * Functional Android UI implementation for RangeSlider component in category Input.
 */
public class RangeSliderComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Input.RANGE_SLIDER,
                "RangeSlider",
                "Native functional implementation of RangeSlider component",
                true,
                50,
                true
        );
    }

    public RangeSliderComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public RangeSliderComponent() {
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

        android.widget.SeekBar sb = new android.widget.SeekBar(context);
        sb.setMax(100);
        return sb;
    
    }
}
