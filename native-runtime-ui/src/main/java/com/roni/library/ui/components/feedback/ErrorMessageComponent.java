package com.roni.library.ui.components.feedback;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Feedback;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for ErrorMessage component in category Feedback.
 */
public class ErrorMessageComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Feedback.ERROR_MESSAGE,
                "ErrorMessage",
                "Native functional implementation of ErrorMessage component",
                true,
                50,
                true
        );
    }

    public ErrorMessageComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ErrorMessageComponent() {
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

        android.widget.LinearLayout banner = new android.widget.LinearLayout(context);
        banner.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        banner.setBackgroundColor(android.graphics.Color.parseColor("#FEE2E2"));
        banner.setPadding(32, 24, 32, 24);
        android.widget.TextView tv = new android.widget.TextView(context);
        tv.setText("✗ An error occurred. Please retry.");
        tv.setTextColor(android.graphics.Color.parseColor("#991B1B"));
        banner.addView(tv);
        return banner;
    
    }
}
