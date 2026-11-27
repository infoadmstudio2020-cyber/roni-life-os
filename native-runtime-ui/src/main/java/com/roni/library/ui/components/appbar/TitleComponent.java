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
 * Functional Android UI implementation for Title component in category AppBar.
 */
public class TitleComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                AppBar.TITLE,
                "Title",
                "Native functional implementation of Title component",
                true,
                50,
                true
        );
    }

    public TitleComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public TitleComponent() {
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
        tv.setText("App Title");
        tv.setTextSize(20f);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setTextColor(android.graphics.Color.parseColor("#0F172A"));
        return tv;
    
    }
}
