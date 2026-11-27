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
 * Functional Android UI implementation for TopAppBar component in category AppBar.
 */
public class TopAppBarComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                AppBar.TOP_APP_BAR,
                "TopAppBar",
                "Native functional implementation of TopAppBar component",
                true,
                50,
                true
        );
    }

    public TopAppBarComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public TopAppBarComponent() {
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
        tb.setTitle("Top App Bar");
        tb.setTitleTextColor(android.graphics.Color.WHITE);
        tb.setBackgroundColor(android.graphics.Color.parseColor("#2D3748"));
        return tb;
    
    }
}
