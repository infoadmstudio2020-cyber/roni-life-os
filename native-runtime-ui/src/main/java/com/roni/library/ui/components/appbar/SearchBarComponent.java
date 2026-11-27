package com.roni.library.ui.components.appbar;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.AppBar;
import com.roni.library.contracts.ui.IUIComponentDescriptor;
import com.roni.library.ui.base.AbstractUIComponent;
import com.roni.library.ui.registry.UIComponentDescriptor;

/**
 * Functional Android UI implementation for SearchBar component in category AppBar.
 */
public class SearchBarComponent extends AbstractUIComponent {

    public static IUIComponentDescriptor createDescriptor() {
        return new UIComponentDescriptor(
                AppBar.SEARCH_BAR,
                "SearchBar",
                "Native functional implementation of SearchBar component",
                true,
                50,
                true
        );
    }

    public SearchBarComponent(@NonNull IUIComponentDescriptor descriptor) {
        super(descriptor);
    }

    public SearchBarComponent() {
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

        androidx.appcompat.widget.SearchView sv = new androidx.appcompat.widget.SearchView(context);
        sv.setQueryHint("Search in app...");
        sv.setIconifiedByDefault(false);
        sv.setBackgroundColor(android.graphics.Color.WHITE);
        return sv;
    
    }
}
