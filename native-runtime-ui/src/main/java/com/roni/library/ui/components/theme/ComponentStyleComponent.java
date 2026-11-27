package com.roni.library.ui.components.theme;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Theme;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for ComponentStyle component in category Theme.
 */
public class ComponentStyleComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Theme.COMPONENT_STYLE,
                "ComponentStyle",
                "Native functional implementation of ComponentStyle component",
                true,
                50,
                false
        );
    }

    public ComponentStyleComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ComponentStyleComponent() {
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
