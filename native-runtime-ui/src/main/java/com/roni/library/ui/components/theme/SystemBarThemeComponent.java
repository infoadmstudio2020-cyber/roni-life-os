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
 * Functional Android UI implementation for SystemBarTheme component in category Theme.
 */
public class SystemBarThemeComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Theme.SYSTEM_BAR_THEME,
                "SystemBarTheme",
                "Native functional implementation of SystemBarTheme component",
                true,
                50,
                false
        );
    }

    public SystemBarThemeComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public SystemBarThemeComponent() {
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
