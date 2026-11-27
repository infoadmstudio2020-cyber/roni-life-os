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
 * Functional Android UI implementation for LinearProgress component in category Feedback.
 */
public class LinearProgressComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Feedback.LINEAR_PROGRESS,
                "LinearProgress",
                "Native functional implementation of LinearProgress component",
                true,
                50,
                true
        );
    }

    public LinearProgressComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public LinearProgressComponent() {
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

        android.widget.ProgressBar pb = new android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        pb.setIndeterminate(true);
        pb.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 16));
        return pb;
    
    }
}
