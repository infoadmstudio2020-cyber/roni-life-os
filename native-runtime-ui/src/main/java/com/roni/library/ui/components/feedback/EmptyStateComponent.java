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
 * Functional Android UI implementation for EmptyState component in category Feedback.
 */
public class EmptyStateComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Feedback.EMPTY_STATE,
                "EmptyState",
                "Native functional implementation of EmptyState component",
                true,
                50,
                true
        );
    }

    public EmptyStateComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public EmptyStateComponent() {
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
        title.setText("No Records Available");
        title.setTextSize(18f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(android.graphics.Color.parseColor("#64748B"));

        android.widget.TextView desc = new android.widget.TextView(context);
        desc.setText("Your items or activity logs will appear here.");
        desc.setTextColor(android.graphics.Color.parseColor("#94A3B8"));

        ll.addView(title);
        ll.addView(desc);
        return ll;
    
    }
}
