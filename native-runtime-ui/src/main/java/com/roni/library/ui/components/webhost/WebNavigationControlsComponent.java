package com.roni.library.ui.components.webhost;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.WebHost;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for WebNavigationControls component in category WebHost.
 */
public class WebNavigationControlsComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                WebHost.WEB_NAVIGATION_CONTROLS,
                "WebNavigationControls",
                "Native functional implementation of WebNavigationControls component",
                true,
                50,
                true
        );
    }

    public WebNavigationControlsComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public WebNavigationControlsComponent() {
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

        android.widget.LinearLayout bar = new android.widget.LinearLayout(context);
        bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(android.graphics.Color.parseColor("#0F172A"));
        androidx.appcompat.widget.AppCompatImageButton back = new androidx.appcompat.widget.AppCompatImageButton(context);
        back.setImageResource(android.R.drawable.ic_media_previous);
        androidx.appcompat.widget.AppCompatImageButton fwd = new androidx.appcompat.widget.AppCompatImageButton(context);
        fwd.setImageResource(android.R.drawable.ic_media_next);
        androidx.appcompat.widget.AppCompatImageButton refresh = new androidx.appcompat.widget.AppCompatImageButton(context);
        refresh.setImageResource(android.R.drawable.ic_menu_rotate);
        bar.addView(back);
        bar.addView(fwd);
        bar.addView(refresh);
        return bar;
    
    }
}
