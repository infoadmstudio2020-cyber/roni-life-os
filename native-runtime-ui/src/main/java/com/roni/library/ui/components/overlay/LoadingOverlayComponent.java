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
 * Functional Android UI implementation for LoadingOverlay component in category Overlay.
 */
public class LoadingOverlayComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Overlay.LOADING_OVERLAY,
                "LoadingOverlay",
                "Native functional implementation of LoadingOverlay component",
                true,
                50,
                true
        );
    }

    public LoadingOverlayComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public LoadingOverlayComponent() {
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

        android.widget.FrameLayout overlay = new android.widget.FrameLayout(context);
        overlay.setBackgroundColor(android.graphics.Color.parseColor("#80000000"));
        overlay.setClickable(true);
        overlay.setFocusable(true);
        android.widget.ProgressBar pb = new android.widget.ProgressBar(context);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER;
        overlay.addView(pb, lp);
        return overlay;
    
    }
}
