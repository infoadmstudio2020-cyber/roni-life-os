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
 * Functional Android UI implementation for ActionSheet component in category Dialog.
 */
public class ActionSheetComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Dialog.ACTION_SHEET,
                "ActionSheet",
                "Native functional implementation of ActionSheet component",
                true,
                50,
                false
        );
    }

    public ActionSheetComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ActionSheetComponent() {
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
