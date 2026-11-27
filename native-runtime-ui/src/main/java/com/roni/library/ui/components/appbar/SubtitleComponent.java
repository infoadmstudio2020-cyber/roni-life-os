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
 * Functional Android UI implementation for Subtitle component in category AppBar.
 */
public class SubtitleComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                AppBar.SUBTITLE,
                "Subtitle",
                "Native functional implementation of Subtitle component",
                true,
                50,
                true
        );
    }

    public SubtitleComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public SubtitleComponent() {
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

        androidx.appcompat.widget.AppCompatTextView tv = new androidx.appcompat.widget.AppCompatTextView(context);
        tv.setText("App Subtitle or description");
        tv.setTextSize(14f);
        tv.setTextColor(android.graphics.Color.parseColor("#64748B"));
        return tv;
    
    }
}
