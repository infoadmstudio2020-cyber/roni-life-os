package com.roni.library.ui.system;

import android.app.Activity;
import android.graphics.Color;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages System UI elements including Status Bar, Navigation Bar, Fullscreen,
 * Immersive Mode, and Edge-to-Edge window insets.
 */
public class SystemUIManager {

    private final AtomicBoolean fullscreen = new AtomicBoolean(false);
    private final AtomicBoolean immersive = new AtomicBoolean(false);
    private final AtomicBoolean edgeToEdge = new AtomicBoolean(false);

    private volatile int statusBarColor = Color.TRANSPARENT;
    private volatile int navBarColor = Color.TRANSPARENT;
    private volatile boolean lightStatusBar = false;
    private volatile boolean lightNavBar = false;

    public SystemUIManager() {}

    /**
     * Applies full Edge-to-Edge configuration to the target Activity window.
     */
    public void applyEdgeToEdge(Activity activity, boolean enabled) {
        this.edgeToEdge.set(enabled);
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;

        WindowCompat.setDecorFitsSystemWindows(window, !enabled);
        if (enabled) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    /**
     * Updates the status bar color and icon appearance (light or dark icons).
     */
    public void setStatusBar(Activity activity, int color, boolean lightIcons) {
        this.statusBarColor = color;
        this.lightStatusBar = lightIcons;
        if (activity == null) return;

        Window window = activity.getWindow();
        if (window == null) return;

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(color);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(lightIcons);
        }
    }

    /**
     * Updates the navigation bar color and button appearance.
     */
    public void setNavigationBar(Activity activity, int color, boolean lightButtons) {
        this.navBarColor = color;
        this.lightNavBar = lightButtons;
        if (activity == null) return;

        Window window = activity.getWindow();
        if (window == null) return;

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setNavigationBarColor(color);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightNavigationBars(lightButtons);
        }
    }

    /**
     * Toggles fullscreen mode, hiding or showing system status and navigation bars.
     */
    public void setFullscreen(Activity activity, boolean enabled) {
        this.fullscreen.set(enabled);
        if (activity == null) return;

        Window window = activity.getWindow();
        if (window == null) return;

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            if (enabled) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars());
            }
        }
    }

    /**
     * Configures sticky immersive mode for media or gaming experiences.
     */
    public void setImmersiveMode(Activity activity, boolean enabled) {
        this.immersive.set(enabled);
        if (activity == null) return;

        Window window = activity.getWindow();
        if (window == null) return;

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            if (enabled) {
                controller.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsetsCompat.Type.systemBars());
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars());
            }
        }
    }

    public boolean isFullscreen() {
        return fullscreen.get();
    }

    public boolean isImmersive() {
        return immersive.get();
    }

    public boolean isEdgeToEdge() {
        return edgeToEdge.get();
    }

    public int getStatusBarColor() {
        return statusBarColor;
    }

    public int getNavBarColor() {
        return navBarColor;
    }

    public boolean isLightStatusBar() {
        return lightStatusBar;
    }

    public boolean isLightNavBar() {
        return lightNavBar;
    }
}
