package com.roni.library.ui.composition;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.IUIComponent;
import com.roni.library.contracts.ui.IUICompositionManager;
import com.roni.library.contracts.ui.UICategory;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UICompositionManager implements IUICompositionManager {

    private final Map<UICategory, ViewGroup> categoryContainers = new ConcurrentHashMap<>();

    public UICompositionManager() {}

    @Override
    public void compose(@NonNull ViewGroup rootView, @NonNull List<IUIComponent> components) {
        // Sort components by priority descending
        components.sort(Comparator.comparingInt((IUIComponent c) -> c.getDescriptor().getPriority()).reversed());

        for (IUIComponent comp : components) {
            if (!comp.isEnabled()) continue;
            
            // Skip non-visual components completely from layout composition
            if (!comp.isVisual()) {
                continue;
            }

            View view = comp.getView();
            if (view == null) {
                view = comp.render(rootView.getContext(), rootView);
            }

            if (view != null && view.getParent() == null) {
                ViewGroup target = getTargetContainer(comp.getType().getCategory(), rootView);
                if (target != null) {
                    target.addView(view);
                } else {
                    rootView.addView(view);
                }
            }
        }
    }

    private ViewGroup getTargetContainer(UICategory category, ViewGroup rootView) {
        ViewGroup target = categoryContainers.get(category);
        if (target != null) {
            return target;
        }

        // Secondary fallback mapping
        switch (category) {
            case INPUT:
            case WEB_HOST:
                return categoryContainers.get(UICategory.CONTENT);
            case FEEDBACK:
            case DIALOG:
                return categoryContainers.get(UICategory.OVERLAY);
            case SYSTEM_UI:
                return categoryContainers.get(UICategory.APP_BAR);
            default:
                return null;
        }
    }

    @Nullable
    @Override
    public ViewGroup getContainerForCategory(@NonNull UICategory category) {
        return categoryContainers.get(category);
    }

    @Override
    public void registerCustomContainer(@NonNull UICategory category, @NonNull ViewGroup container) {
        categoryContainers.put(category, container);
    }

    public void clear() {
        categoryContainers.clear();
    }
}
