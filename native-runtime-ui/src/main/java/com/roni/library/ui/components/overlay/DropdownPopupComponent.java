package com.roni.library.ui.components.overlay;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Overlay;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for DropdownPopup component in category Overlay.
 */
public class DropdownPopupComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Overlay.DROPDOWN_POPUP,
                "DropdownPopup",
                "Native functional implementation of DropdownPopup component",
                true,
                50,
                false
        );
    }

    public DropdownPopupComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public DropdownPopupComponent() {
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
