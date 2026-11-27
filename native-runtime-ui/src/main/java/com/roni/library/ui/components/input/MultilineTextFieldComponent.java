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
 * Functional Android UI implementation for MultilineTextField component in category Input.
 */
public class MultilineTextFieldComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Input.MULTILINE_TEXT_FIELD,
                "MultilineTextField",
                "Native functional implementation of MultilineTextField component",
                true,
                50,
                true
        );
    }

    public MultilineTextFieldComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public MultilineTextFieldComponent() {
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
        et.setHint("Enter multiline text...");
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(3);
        et.setMaxLines(6);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        return et;
    
    }
}
