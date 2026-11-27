package com.roni.library.ui.system;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Group 1: System UI Controller & Subsystem Coordinator.
 * Coordinates all 4 System UI components:
 *  1. Status Bar
 *  2. System Navigation Bar
 *  3. Fullscreen / Immersive Mode
 *  4. Edge-to-Edge Layout
 *
 * Each component can be configured, accessed, and toggled separately or as a group.
 */
public class SystemUIGroup {

    private final SystemUIManager manager;

    public SystemUIGroup() {
        this(new SystemUIManager());
    }

    public SystemUIGroup(@NonNull SystemUIManager manager) {
        this.manager = manager;
    }

    public SystemUIManager getManager() {
        return manager;
    }

    // Component 1: Status Bar
    public void setStatusBar(@Nullable Activity activity, int color, boolean lightIcons) {
        manager.setStatusBar(activity, color, lightIcons);
    }

    public int getStatusBarColor() {
        return manager.getStatusBarColor();
    }

    public boolean isLightStatusBar() {
        return manager.isLightStatusBar();
    }

    // Component 2: System Navigation Bar
    public void setNavigationBar(@Nullable Activity activity, int color, boolean lightButtons) {
        manager.setNavigationBar(activity, color, lightButtons);
    }

    public int getNavigationBarColor() {
        return manager.getNavBarColor();
    }

    public boolean isLightNavigationBar() {
        return manager.isLightNavBar();
    }

    // Component 3: Fullscreen / Immersive Mode
    public void setFullscreen(@Nullable Activity activity, boolean enabled) {
        manager.setFullscreen(activity, enabled);
    }

    public boolean isFullscreen() {
        return manager.isFullscreen();
    }

    public void setImmersiveMode(@Nullable Activity activity, boolean enabled) {
        manager.setImmersiveMode(activity, enabled);
    }

    public boolean isImmersive() {
        return manager.isImmersive();
    }

    // Component 4: Edge-to-Edge Layout
    public void applyEdgeToEdge(@Nullable Activity activity, boolean enabled) {
        manager.applyEdgeToEdge(activity, enabled);
    }

    public boolean isEdgeToEdge() {
        return manager.isEdgeToEdge();
    }

    /**
     * Applies a unified System UI configuration profile to an activity.
     */
    public void applyProfile(@Nullable Activity activity,
                             int statusBarColor, boolean lightStatus,
                             int navBarColor, boolean lightNav,
                             boolean edgeToEdge, boolean fullscreen, boolean immersive) {
        if (edgeToEdge) {
            applyEdgeToEdge(activity, true);
        }
        setStatusBar(activity, statusBarColor, lightStatus);
        setNavigationBar(activity, navBarColor, lightNav);
        if (fullscreen) {
            setFullscreen(activity, true);
        }
        if (immersive) {
            setImmersiveMode(activity, true);
        }
    }
}
