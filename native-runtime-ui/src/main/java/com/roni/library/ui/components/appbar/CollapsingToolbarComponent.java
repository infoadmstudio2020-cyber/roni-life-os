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
 * Functional Android UI implementation for CollapsingToolbar component in category AppBar.
 */
public class CollapsingToolbarComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                AppBar.COLLAPSING_TOOLBAR,
                "CollapsingToolbar",
                "Native functional implementation of CollapsingToolbar component",
                true,
                50,
                true
        );
    }

    public CollapsingToolbarComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public CollapsingToolbarComponent() {
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

        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        container.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 240));
        container.setBackgroundColor(android.graphics.Color.parseColor("#4A5568"));
        android.widget.TextView title = new android.widget.TextView(context);
        title.setText("Collapsing Header");
        title.setTextColor(android.graphics.Color.WHITE);
        title.setTextSize(20f);
        android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT);
        flp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
        flp.setMargins(48, 0, 0, 48);
        container.addView(title, flp);
        return container;
    
    }
}
