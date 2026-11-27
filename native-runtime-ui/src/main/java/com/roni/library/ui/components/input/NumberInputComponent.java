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
 * Functional Android UI implementation for NumberInput component in category Input.
 */
public class NumberInputComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                Input.NUMBER_INPUT,
                "NumberInput",
                "Native functional implementation of NumberInput component",
                true,
                50,
                true
        );
    }

    public NumberInputComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public NumberInputComponent() {
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

        android.widget.LinearLayout ll = new android.widget.LinearLayout(context);
        ll.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        ll.setGravity(android.view.Gravity.CENTER_VERTICAL);

        androidx.appcompat.widget.AppCompatButton btnMinus = new androidx.appcompat.widget.AppCompatButton(context);
        btnMinus.setText("-");

        androidx.appcompat.widget.AppCompatEditText et = new androidx.appcompat.widget.AppCompatEditText(context);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setText("1");
        et.setGravity(android.view.Gravity.CENTER);

        androidx.appcompat.widget.AppCompatButton btnPlus = new androidx.appcompat.widget.AppCompatButton(context);
        btnPlus.setText("+");

        ll.addView(btnMinus);
        ll.addView(et);
        ll.addView(btnPlus);
        return ll;
    
    }
}
