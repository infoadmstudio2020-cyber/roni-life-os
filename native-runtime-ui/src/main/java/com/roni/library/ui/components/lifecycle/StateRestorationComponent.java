package com.roni.library.ui.components.lifecycle;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Lifecycle;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for StateRestoration component in category Lifecycle.
 */
public class StateRestorationComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Lifecycle.STATE_RESTORATION,
                "StateRestoration",
                "Native functional implementation of StateRestoration component",
                true,
                50,
                false
        );
    }

    public StateRestorationComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public StateRestorationComponent() {
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
