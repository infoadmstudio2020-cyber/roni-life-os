package com.roni.library.ui.components.input;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.Input;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for TextField component in category Input.
 */
public class TextFieldComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Input.TEXT_FIELD,
                "TextField",
                "Native functional implementation of TextField component",
                true,
                50,
                true
        );
    }

    public TextFieldComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public TextFieldComponent() {
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

        androidx.appcompat.widget.AppCompatEditText et = new androidx.appcompat.widget.AppCompatEditText(context);
        et.setHint("Enter text...");
        et.setPadding(24, 24, 24, 24);
        return et;
    
    }
}
