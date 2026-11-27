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
 * Functional Android UI implementation for ErrorState component in category Feedback.
 */
public class ErrorStateComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Feedback.ERROR_STATE,
                "ErrorState",
                "Native functional implementation of ErrorState component",
                true,
                50,
                true
        );
    }

    public ErrorStateComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ErrorStateComponent() {
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

        android.widget.LinearLayout ll = new android.widget.LinearLayout(context);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setGravity(android.view.Gravity.CENTER);
        ll.setPadding(48, 48, 48, 48);

        android.widget.TextView title = new android.widget.TextView(context);
        title.setText("Unable to Load Content");
        title.setTextSize(18f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(android.graphics.Color.parseColor("#EF4444"));

        androidx.appcompat.widget.AppCompatButton retry = new androidx.appcompat.widget.AppCompatButton(context);
        retry.setText("Retry Connection");
        retry.setBackgroundColor(android.graphics.Color.parseColor("#0284C7"));
        retry.setTextColor(android.graphics.Color.WHITE);

        ll.addView(title);
        ll.addView(retry);
        return ll;
    
    }
}
