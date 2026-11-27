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
 * Functional Android UI implementation for BottomNavigation component in category Navigation.
 */
public class BottomNavigationComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Navigation.BOTTOM_NAVIGATION,
                "BottomNavigation",
                "Native functional implementation of BottomNavigation component",
                true,
                50,
                true
        );
    }

    public BottomNavigationComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public BottomNavigationComponent() {
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
        bar.setWeightSum(3);
        bar.setBackgroundColor(android.graphics.Color.parseColor("#0F172A"));
        bar.setElevation(16f);
        bar.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 160));

        String[] tabs = {"Home", "Explore", "Profile"};
        int[] icons = {android.R.drawable.ic_menu_agenda, android.R.drawable.ic_menu_compass, android.R.drawable.ic_menu_myplaces};

        for (int i = 0; i < 3; i++) {
            android.widget.LinearLayout item = new android.widget.LinearLayout(context);
            item.setOrientation(android.widget.LinearLayout.VERTICAL);
            item.setGravity(android.view.Gravity.CENTER);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            item.setLayoutParams(lp);

            android.widget.ImageView iv = new android.widget.ImageView(context);
            iv.setImageResource(icons[i]);
            iv.setColorFilter(android.graphics.Color.WHITE);

            android.widget.TextView tv = new android.widget.TextView(context);
            tv.setText(tabs[i]);
            tv.setTextColor(android.graphics.Color.WHITE);
            tv.setTextSize(12f);

            item.addView(iv);
            item.addView(tv);
            bar.addView(item);
        }
        return bar;
    
    }
}
