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
 * Functional Android UI implementation for LoadingIndicator component in category Feedback.
 */
public class LoadingIndicatorComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Feedback.LOADING_INDICATOR,
                "LoadingIndicator",
                "Native functional implementation of LoadingIndicator component",
                true,
                50,
                true
        );
    }

    public LoadingIndicatorComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public LoadingIndicatorComponent() {
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

        android.widget.ProgressBar pb = new android.widget.ProgressBar(context);
        pb.setIndeterminate(true);
        return pb;
    
    }
}
