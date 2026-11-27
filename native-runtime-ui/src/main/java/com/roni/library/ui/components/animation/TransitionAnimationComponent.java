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
 * Functional Android UI implementation for TransitionAnimation component in category Animation.
 */
public class TransitionAnimationComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Animation.TRANSITION_ANIMATION,
                "TransitionAnimation",
                "Native functional implementation of TransitionAnimation component",
                true,
                50,
                false
        );
    }

    public TransitionAnimationComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public TransitionAnimationComponent() {
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
