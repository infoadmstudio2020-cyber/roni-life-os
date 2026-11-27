package com.roni.library.ui.customization;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;

import com.roni.library.ui.manager.ComponentType;
import com.roni.library.ui.theme.UIThemeBridge;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Group 5: UI Customization Coordinator.
 * Coordinates all 5 UI Customization components:
 *  23. Theme Integration
 *  24. Background / Color
 *  25. Animation (fade, slide, scale)
 *  26. Visibility Controller (show, hide, toggle components)
 *  27. Dynamic Layout Controller (reorder, swap, insert views dynamically)
 *
 * Allows live theme propagation, smooth animations, and runtime layout morphing.
 */
public class CustomizationGroup {

    private final Map<ComponentType, View> componentViewMap = new ConcurrentHashMap<>();

    public CustomizationGroup() {}

    public void registerComponentView(@NonNull ComponentType type, @NonNull View view) {
        componentViewMap.put(type, view);
    }

    public void unregisterComponentView(@NonNull ComponentType type) {
        componentViewMap.remove(type);
    }

    // Component 23: Theme Integration
    public void applyTheme(@NonNull Activity activity, @NonNull Map<String, Object> themeTokens) {
        UIThemeBridge.applyThemeToActivity(activity, themeTokens);
    }

    public Map<String, Object> getActiveThemeTokens() {
        return UIThemeBridge.getActiveThemeColors();
    }

    // Component 24: Background / Color
    public void setBackgroundColor(@NonNull View view, int color) {
        view.setBackgroundColor(color);
    }

    public void setComponentBackgroundColor(@NonNull ComponentType type, int color) {
        View view = componentViewMap.get(type);
        if (view != null) {
            view.setBackgroundColor(color);
        }
    }

    // Component 25: Animation
    public void fadeIn(@NonNull View view, long durationMs) {
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(durationMs).setListener(null).start();
    }

    public void fadeOut(@NonNull View view, long durationMs) {
        view.animate().alpha(0f).setDuration(durationMs).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.GONE);
            }
        }).start();
    }

    // Component 26: Visibility Controller
    public void setVisibility(@NonNull ComponentType type, boolean visible) {
        setVisibility(type, visible, false);
    }

    public void setVisibility(@NonNull ComponentType type, boolean visible, boolean animate) {
        View view = componentViewMap.get(type);
        if (view == null) return;
        if (animate) {
            if (visible) {
                fadeIn(view, 250);
            } else {
                fadeOut(view, 250);
            }
        } else {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public boolean isVisible(@NonNull ComponentType type) {
        View view = componentViewMap.get(type);
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    public void toggleVisibility(@NonNull ComponentType type) {
        setVisibility(type, !isVisible(type));
    }

    // Component 27: Dynamic Layout Controller
    public void swapViews(@NonNull ViewGroup container, @NonNull View oldView, @NonNull View newView) {
        int index = container.indexOfChild(oldView);
        if (index >= 0) {
            container.removeViewAt(index);
            container.addView(newView, index);
        } else {
            container.addView(newView);
        }
    }
}
