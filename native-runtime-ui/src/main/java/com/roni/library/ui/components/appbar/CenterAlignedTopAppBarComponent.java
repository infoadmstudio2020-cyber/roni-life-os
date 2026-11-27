package com.roni.library.ui.components.appbar;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.AppBar;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for CenterAlignedTopAppBar component in category AppBar.
 */
public class CenterAlignedTopAppBarComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                AppBar.CENTER_ALIGNED_TOP_APP_BAR,
                "CenterAlignedTopAppBar",
                "Native functional implementation of CenterAlignedTopAppBar component",
                true,
                50,
                true
        );
    }

    public CenterAlignedTopAppBarComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public CenterAlignedTopAppBarComponent() {
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

        androidx.appcompat.widget.Toolbar tb = new androidx.appcompat.widget.Toolbar(context);
        android.widget.TextView titleTv = new android.widget.TextView(context);
        titleTv.setText("Centered Title");
        titleTv.setTextColor(android.graphics.Color.WHITE);
        titleTv.setTextSize(18f);
        titleTv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        androidx.appcompat.widget.Toolbar.LayoutParams lp = new androidx.appcompat.widget.Toolbar.LayoutParams(
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT,
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = android.view.Gravity.CENTER;
        tb.addView(titleTv, lp);
        tb.setBackgroundColor(android.graphics.Color.parseColor("#1A202C"));
        return tb;
    
    }
}
