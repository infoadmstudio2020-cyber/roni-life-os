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
 * Functional Android UI implementation for Toolbar component in category AppBar.
 */
public class ToolbarComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                AppBar.TOOLBAR,
                "Toolbar",
                "Native functional implementation of Toolbar component",
                true,
                50,
                true
        );
    }

    public ToolbarComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ToolbarComponent() {
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

        androidx.appcompat.widget.Toolbar toolbar = new androidx.appcompat.widget.Toolbar(context);
        toolbar.setTitle("App Toolbar");
        toolbar.setTitleTextColor(android.graphics.Color.WHITE);
        toolbar.setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"));
        toolbar.setElevation(8f);
        return toolbar;
    
    }
}
