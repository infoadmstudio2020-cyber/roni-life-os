package com.roni.library.ui.theme;

import android.app.Activity;
import android.graphics.Color;
import com.roni.library.contracts.theme.IThemeConfig;
import com.roni.library.theme.ThemeEngine;
import java.util.HashMap;
import java.util.Map;

/**
 * Bridges NativeRuntimeUI widgets with native-theme-engine's active color tokens
 * and coordinates runtime color propagation across Activities and UI views.
 */
public class UIThemeBridge {

    public static Map<String, Object> getActiveThemeColors() {
        Map<String, Object> colors = new HashMap<>();
        try {
            if (ThemeEngine.isInitialized()) {
                IThemeConfig config = ThemeEngine.getActiveTheme();
                if (config != null) {
                    colors.put("themeName", config.getThemeName());
                    colors.put("primaryColor", config.getPrimaryColor());
                    colors.put("surfaceColor", config.getSurfaceColor());
                    colors.put("textColor", config.getTextColor());
                    colors.put("isDark", config.isDarkMode());
                    return colors;
                }
            }
        } catch (Throwable ignored) {}

        // Fallback Material Design theme tokens
        colors.put("themeName", "Dark");
        colors.put("primaryColor", "#0284C7");
        colors.put("surfaceColor", "#0B0F19");
        colors.put("textColor", "#F8FAFC");
        colors.put("cardColor", "#0F172A");
        colors.put("isDark", true);
        return colors;
    }

    public static void applyThemeToActivity(Activity activity, Map<String, Object> colors) {
        if (activity == null || colors == null || activity.isFinishing()) return;

        activity.runOnUiThread(() -> {
            try {
                Object surface = colors.get("surfaceColor");
                if (surface instanceof String && activity.getWindow() != null) {
                    activity.getWindow().getDecorView().setBackgroundColor(Color.parseColor((String) surface));
                }
            } catch (Throwable ignored) {}
        });
    }
}
