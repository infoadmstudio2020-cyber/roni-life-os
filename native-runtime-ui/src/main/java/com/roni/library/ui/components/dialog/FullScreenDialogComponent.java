package com.roni.library.ui.components.dialog;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Dialog;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for FullScreenDialog component in category Dialog.
 */
public class FullScreenDialogComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Dialog.FULL_SCREEN_DIALOG,
                "FullScreenDialog",
                "Native functional implementation of FullScreenDialog component",
                true,
                50,
                false
        );
    }

    public FullScreenDialogComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public FullScreenDialogComponent() {
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
return null;
    }
}
