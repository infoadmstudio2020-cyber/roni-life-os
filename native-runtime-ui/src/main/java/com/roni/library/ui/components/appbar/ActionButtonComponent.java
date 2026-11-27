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
 * Functional Android UI implementation for ActionButton component in category AppBar.
 */
public class ActionButtonComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                AppBar.ACTION_BUTTON,
                "ActionButton",
                "Native functional implementation of ActionButton component",
                true,
                50,
                true
        );
    }

    public ActionButtonComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ActionButtonComponent() {
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

        androidx.appcompat.widget.AppCompatButton btn = new androidx.appcompat.widget.AppCompatButton(context);
        btn.setText("Action");
        btn.setTextColor(android.graphics.Color.WHITE);
        btn.setBackgroundColor(android.graphics.Color.parseColor("#0284C7"));
        return btn;
    
    }
}
