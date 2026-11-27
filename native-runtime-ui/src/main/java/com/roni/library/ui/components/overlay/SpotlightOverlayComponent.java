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
 * Functional Android UI implementation for SpotlightOverlay component in category Overlay.
 */
public class SpotlightOverlayComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Overlay.SPOTLIGHT_OVERLAY,
                "SpotlightOverlay",
                "Native functional implementation of SpotlightOverlay component",
                true,
                50,
                true
        );
    }

    public SpotlightOverlayComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public SpotlightOverlayComponent() {
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

        android.widget.FrameLayout spotlight = new android.widget.FrameLayout(context);
        spotlight.setBackgroundColor(android.graphics.Color.parseColor("#B3000000"));
        return spotlight;
    
    }
}
