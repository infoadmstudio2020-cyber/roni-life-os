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
 * Functional Android UI implementation for Snackbar component in category Feedback.
 */
public class SnackbarComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Feedback.SNACKBAR,
                "Snackbar",
                "Native functional implementation of Snackbar component",
                true,
                50,
                false
        );
    }

    public SnackbarComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public SnackbarComponent() {
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
return null; // Handled by Snackbar API
    }
}
