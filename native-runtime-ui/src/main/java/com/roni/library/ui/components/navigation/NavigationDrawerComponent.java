package com.roni.library.ui.components.navigation;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Navigation;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for NavigationDrawer component in category Navigation.
 */
public class NavigationDrawerComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Navigation.NAVIGATION_DRAWER,
                "NavigationDrawer",
                "Native functional implementation of NavigationDrawer component",
                true,
                50,
                true
        );
    }

    public NavigationDrawerComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public NavigationDrawerComponent() {
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

        androidx.drawerlayout.widget.DrawerLayout dl = new androidx.drawerlayout.widget.DrawerLayout(context);
        android.widget.FrameLayout content = new android.widget.FrameLayout(context);
        dl.addView(content, new androidx.drawerlayout.widget.DrawerLayout.LayoutParams(
                androidx.drawerlayout.widget.DrawerLayout.LayoutParams.MATCH_PARENT,
                androidx.drawerlayout.widget.DrawerLayout.LayoutParams.MATCH_PARENT
        ));

        android.widget.LinearLayout drawer = new android.widget.LinearLayout(context);
        drawer.setOrientation(android.widget.LinearLayout.VERTICAL);
        drawer.setBackgroundColor(android.graphics.Color.WHITE);
        androidx.drawerlayout.widget.DrawerLayout.LayoutParams dlp = new androidx.drawerlayout.widget.DrawerLayout.LayoutParams(
                600, androidx.drawerlayout.widget.DrawerLayout.LayoutParams.MATCH_PARENT);
        dlp.gravity = android.view.Gravity.START;
        dl.addView(drawer, dlp);
        return dl;
    
    }
}
