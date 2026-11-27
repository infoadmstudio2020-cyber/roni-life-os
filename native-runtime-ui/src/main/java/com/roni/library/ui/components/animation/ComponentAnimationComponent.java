package com.roni.library.ui.components.animation;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Animation;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for ComponentAnimation component in category Animation.
 */
public class ComponentAnimationComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Animation.COMPONENT_ANIMATION,
                "ComponentAnimation",
                "Native functional implementation of ComponentAnimation component",
                true,
                50,
                false
        );
    }

    public ComponentAnimationComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public ComponentAnimationComponent() {
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
