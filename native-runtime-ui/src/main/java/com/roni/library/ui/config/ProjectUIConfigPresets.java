package com.roni.library.ui.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.ui.manager.ComponentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ready-made {@link ProjectUIConfig} presets.
 *
 * <p>Each preset is a complete, independent configuration. Applying a preset replaces the whole
 * configuration of one project and never touches another project.</p>
 */
public final class ProjectUIConfigPresets {

    public static final String MINIMAL = "minimal";
    public static final String STATUS_BAR_TOOLBAR = "status_bar_toolbar";
    public static final String FULLSCREEN_WEBVIEW = "fullscreen_webview";
    public static final String DASHBOARD_FULL = "dashboard_full";
    public static final String WEB_APP = "web_app";
    public static final String EVERYTHING = "everything";

    private ProjectUIConfigPresets() {
    }

    /** Stable, ordered list of preset ids for the editor. */
    @NonNull
    public static List<String> ids() {
        List<String> ids = new ArrayList<>();
        ids.add(MINIMAL);
        ids.add(STATUS_BAR_TOOLBAR);
        ids.add(FULLSCREEN_WEBVIEW);
        ids.add(DASHBOARD_FULL);
        ids.add(WEB_APP);
        ids.add(EVERYTHING);
        return Collections.unmodifiableList(ids);
    }

    /** Display label for a preset id; falls back to the id itself. */
    @NonNull
    public static String label(@Nullable String presetId) {
        if (presetId == null) {
            return "Custom";
        }
        switch (presetId) {
            case MINIMAL:
                return "Minimal (status bar only)";
            case STATUS_BAR_TOOLBAR:
                return "Status bar + toolbar";
            case FULLSCREEN_WEBVIEW:
                return "Fullscreen WebView";
            case DASHBOARD_FULL:
                return "Dashboard + drawer + bottom nav";
            case WEB_APP:
                return "Web app shell";
            case EVERYTHING:
                return "Everything enabled";
            default:
                return "Custom";
        }
    }

    /** Returns a fresh configuration for {@code presetId}; unknown ids yield {@link #MINIMAL}. */
    @NonNull
    public static ProjectUIConfig create(@Nullable String presetId) {
        String id = presetId != null ? presetId : MINIMAL;
        switch (id) {
            case STATUS_BAR_TOOLBAR:
                return statusBarToolbar();
            case FULLSCREEN_WEBVIEW:
                return fullscreenWebView();
            case DASHBOARD_FULL:
                return dashboardFull();
            case WEB_APP:
                return webApp();
            case EVERYTHING:
                return everything();
            case MINIMAL:
            default:
                return minimal();
        }
    }

    // ==================== Presets ====================

    /** Project A: status bar only, no toolbar, no dashboard chrome. */
    @NonNull
    public static ProjectUIConfig minimal() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.setPresetId(MINIMAL);
        config.getSplash().setEnabled(false);
        config.getDashboard().setEnabled(false);
        config.setEnabled(ComponentType.STATUS_BAR, true);
        config.setEnabled(ComponentType.BACK_NAVIGATION, true);
        config.setEnabled(ComponentType.UI_STATE_MANAGEMENT, true);
        config.setEnabled(ComponentType.COMPONENT_LIFECYCLE, true);
        return config;
    }

    /** Project B: status bar + toolbar + bottom navigation. */
    @NonNull
    public static ProjectUIConfig statusBarToolbar() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.setPresetId(STATUS_BAR_TOOLBAR);
        config.getSplash().setEnabled(true).setDurationMs(900);
        config.getDashboard().setEnabled(true)
                .setToolbarEnabled(true)
                .setBottomNavEnabled(true)
                .setDrawerEnabled(false)
                .setFabEnabled(false);
        config.setEnabled(ComponentType.STATUS_BAR, true);
        config.setEnabled(ComponentType.TOOLBAR, true);
        config.setEnabled(ComponentType.TOOLBAR_MENU, true);
        config.setEnabled(ComponentType.BOTTOM_NAV, true);
        config.setEnabled(ComponentType.WEB_VIEW, true);
        config.setEnabled(ComponentType.BACK_NAVIGATION, true);
        config.setEnabled(ComponentType.UI_STATE_MANAGEMENT, true);
        config.setEnabled(ComponentType.COMPONENT_LIFECYCLE, true);
        config.setEnabled(ComponentType.THEME_INTEGRATION, true);
        return config;
    }

    /** Project C: fullscreen WebView, no dashboard, no system bars. */
    @NonNull
    public static ProjectUIConfig fullscreenWebView() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.setPresetId(FULLSCREEN_WEBVIEW);
        config.getSplash().setEnabled(false);
        config.getDashboard().setEnabled(false);
        config.getNavigation().setFullscreen(true)
                .setImmersive(true)
                .setStatusBarVisible(false)
                .setNavigationBarVisible(false)
                .setEdgeToEdge(true);
        config.getWebView().setEnabled(true).setFullscreen(true);
        config.setEnabled(ComponentType.FULLSCREEN_IMMERSIVE, true);
        config.setEnabled(ComponentType.EDGE_TO_EDGE, true);
        config.setEnabled(ComponentType.WEB_VIEW, true);
        config.setEnabled(ComponentType.BACK_NAVIGATION, true);
        config.setEnabled(ComponentType.UI_STATE_MANAGEMENT, true);
        config.setEnabled(ComponentType.COMPONENT_LIFECYCLE, true);
        return config;
    }

    /** Project D: custom splash + native dashboard + drawer + bottom navigation. */
    @NonNull
    public static ProjectUIConfig dashboardFull() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.setPresetId(DASHBOARD_FULL);
        config.getSplash().setEnabled(true)
                .setMode(ProjectUIConfig.SplashMode.NATIVE)
                .setDurationMs(1400)
                .setAnimation(ProjectUIConfig.SplashAnimation.FADE)
                .setFullscreen(true);
        config.getDashboard().setEnabled(true)
                .setMode(ProjectUIConfig.DashboardMode.NATIVE)
                .setLayout(ProjectUIConfig.DashboardLayout.STANDARD)
                .setToolbarEnabled(true)
                .setDrawerEnabled(true)
                .setBottomNavEnabled(true)
                .setFabEnabled(true);
        config.setEnabled(ComponentType.STATUS_BAR, true);
        config.setEnabled(ComponentType.TOOLBAR, true);
        config.setEnabled(ComponentType.TOOLBAR_MENU, true);
        config.setEnabled(ComponentType.DRAWER, true);
        config.setEnabled(ComponentType.BOTTOM_NAV, true);
        config.setEnabled(ComponentType.FAB, true);
        config.setEnabled(ComponentType.WEB_VIEW, true);
        config.setEnabled(ComponentType.LOADING_OVERLAY, true);
        config.setEnabled(ComponentType.SNACKBAR, true);
        config.setEnabled(ComponentType.BACK_NAVIGATION, true);
        config.setEnabled(ComponentType.UI_STATE_MANAGEMENT, true);
        config.setEnabled(ComponentType.COMPONENT_LIFECYCLE, true);
        config.setEnabled(ComponentType.THEME_INTEGRATION, true);
        config.setEnabled(ComponentType.ANIMATION, true);
        return config;
    }

    /** Plain web-app shell: status bar + WebView + overlays. */
    @NonNull
    public static ProjectUIConfig webApp() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.setPresetId(WEB_APP);
        config.getSplash().setEnabled(true).setDurationMs(1000);
        config.getDashboard().setEnabled(false);
        config.getWebView().setEnabled(true).setFullscreen(true);
        config.setEnabled(ComponentType.STATUS_BAR, true);
        config.setEnabled(ComponentType.WEB_VIEW, true);
        config.setEnabled(ComponentType.LOADING_OVERLAY, true);
        config.setEnabled(ComponentType.TOAST, true);
        config.setEnabled(ComponentType.BACK_NAVIGATION, true);
        config.setEnabled(ComponentType.UI_STATE_MANAGEMENT, true);
        config.setEnabled(ComponentType.COMPONENT_LIFECYCLE, true);
        config.setEnabled(ComponentType.THEME_INTEGRATION, true);
        return config;
    }

    /** Every canonical component enabled — useful as a reference and for demos. */
    @NonNull
    public static ProjectUIConfig everything() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.setPresetId(EVERYTHING);
        config.getSplash().setEnabled(true);
        config.getDashboard().setEnabled(true)
                .setToolbarEnabled(true)
                .setDrawerEnabled(true)
                .setBottomNavEnabled(true)
                .setFabEnabled(true);
        for (ComponentType type : ComponentType.values()) {
            if (type == ComponentType.CUSTOM_CONTENT || type == ComponentType.SYSTEM_UI) {
                continue;
            }
            config.setEnabled(type, true);
        }
        return config;
    }
}
