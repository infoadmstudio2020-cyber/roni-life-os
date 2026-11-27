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
 * Functional Android UI implementation for Tooltip component in category Overlay.
 */
public class TooltipComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Overlay.TOOLTIP,
                "Tooltip",
                "Native functional implementation of Tooltip component",
                true,
                50,
                true
        );
    }

    public TooltipComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public TooltipComponent() {
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

        android.widget.TextView tv = new android.widget.TextView(context);
        tv.setText("Helpful Tooltip");
        tv.setTextColor(android.graphics.Color.WHITE);
        tv.setBackgroundColor(android.graphics.Color.parseColor("#1E293B"));
        tv.setPadding(16, 8, 16, 8);
        return tv;
    
    }
}
