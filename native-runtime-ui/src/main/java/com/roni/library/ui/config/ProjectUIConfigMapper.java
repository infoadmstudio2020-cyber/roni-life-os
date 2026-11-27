package com.roni.library.ui.config;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.ui.NativeRuntimeUIConfig;
import com.roni.library.ui.manager.ComponentType;
import com.roni.library.ui.web.ResourceDecoder;
import com.roni.library.ui.web.WebHostConfig;

/**
 * Translates a {@link ProjectUIConfig} into the runtime {@link NativeRuntimeUIConfig}.
 *
 * <p>This is the only place where the per-project configuration meets the NativeRuntimeUI
 * builder. The contract is strict: a component that is disabled in the project configuration is
 * never enabled on the builder, so {@code NativeUIComponentManager} never instantiates it.</p>
 *
 * <p>The builder's legacy "empty means defaults" fallback is switched off here, otherwise a
 * project that deliberately enables nothing would silently receive a toolbar and a system UI
 * group.</p>
 */
public final class ProjectUIConfigMapper {

    /** Aliases that duplicate a canonical component and must not be toggled twice. */
    private static final ComponentType[] ALIASES = {
            ComponentType.CUSTOM_CONTENT,
            ComponentType.SYSTEM_UI
    };

    private ProjectUIConfigMapper() {
    }

    /** Builds a complete runtime configuration from scratch. */
    @NonNull
    public static NativeRuntimeUIConfig map(@Nullable ProjectUIConfig config) {
        return apply(config, NativeRuntimeUIConfig.builder());
    }

    /**
     * Applies {@code config} onto an existing builder and returns the built configuration.
     *
     * <p>Callers that need to set a toolbar title or a web source first should use this overload
     * and pass their pre-configured builder.</p>
     */
    @NonNull
    public static NativeRuntimeUIConfig apply(@Nullable ProjectUIConfig config,
                                              @Nullable NativeRuntimeUIConfig.Builder builder) {
        return apply(config, builder, null);
    }

    /**
     * Applies {@code config} onto an existing builder, installing {@code decoder} on the WebView.
     *
     * <p>The decoder is supplied by the caller rather than built here: this module cannot see the
     * app's {@code VaultResourceDecoder}, and it must not guess whether a project is encrypted.
     * A {@code null} decoder means "serve the bytes as stored", which is correct for SAF folders,
     * remote URLs, APK assets and legacy unsealed projects.</p>
     */
    @NonNull
    public static NativeRuntimeUIConfig apply(@Nullable ProjectUIConfig config,
                                              @Nullable NativeRuntimeUIConfig.Builder builder,
                                              @Nullable ResourceDecoder decoder) {
        ProjectUIConfig source = config != null ? config : new ProjectUIConfig();
        NativeRuntimeUIConfig.Builder target =
                builder != null ? builder : NativeRuntimeUIConfig.builder();

        target.defaultFallbackEnabled(false);

        applyComponents(source, target);
        applyNavigation(source, target);
        applyDashboard(source, target);
        applyTheme(source, target);
        applyWebView(source, target, decoder);

        return target.build();
    }

    // ==================== Components ====================

    private static void applyComponents(ProjectUIConfig config,
                                        NativeRuntimeUIConfig.Builder builder) {
        for (ComponentType type : ComponentType.values()) {
            if (isAlias(type)) {
                continue;
            }
            if (config.isEnabled(type)) {
                builder.enableComponent(type);
            } else {
                builder.disableComponent(type);
            }
        }
    }

    private static boolean isAlias(ComponentType type) {
        for (ComponentType alias : ALIASES) {
            if (alias == type) {
                return true;
            }
        }
        return false;
    }

    // ==================== System bars ====================

    private static void applyNavigation(ProjectUIConfig config,
                                        NativeRuntimeUIConfig.Builder builder) {
        ProjectUIConfig.Navigation navigation = config.getNavigation();

        boolean statusBar = config.isEnabled(ComponentType.STATUS_BAR)
                && navigation.isStatusBarVisible();
        boolean navBar = config.isEnabled(ComponentType.NAVIGATION_BAR)
                && navigation.isNavigationBarVisible();

        builder.statusBarColor(statusBar ? navigation.getStatusBarColor() : Color.TRANSPARENT)
                .lightStatusBar(navigation.isLightStatusBar())
                .navBarColor(navBar ? navigation.getNavigationBarColor() : Color.TRANSPARENT)
                .lightNavBar(navigation.isLightNavigationBar())
                .edgeToEdge(config.isEnabled(ComponentType.EDGE_TO_EDGE)
                        && navigation.isEdgeToEdge())
                .fullscreen(navigation.isFullscreen())
                .immersive(navigation.isImmersive());

        // A hidden bar must not stay enabled on the builder, otherwise the manager would
        // reserve insets for a bar the project asked to hide.
        if (!statusBar) {
            builder.disableComponent(ComponentType.STATUS_BAR);
        }
        if (!navBar) {
            builder.disableComponent(ComponentType.NAVIGATION_BAR);
        }
    }

    // ==================== Dashboard chrome ====================

    private static void applyDashboard(ProjectUIConfig config,
                                       NativeRuntimeUIConfig.Builder builder) {
        ProjectUIConfig.Dashboard dashboard = config.getDashboard();
        boolean dashboardOn = dashboard.isEnabled();

        builder.enableToolbar(dashboardOn
                        && dashboard.isToolbarEnabled()
                        && config.isEnabled(ComponentType.TOOLBAR))
                .enableDrawer(dashboardOn
                        && dashboard.isDrawerEnabled()
                        && config.isEnabled(ComponentType.DRAWER))
                .enableBottomNav(dashboardOn
                        && dashboard.isBottomNavEnabled()
                        && config.isEnabled(ComponentType.BOTTOM_NAV))
                .enableFab(dashboardOn
                        && dashboard.isFabEnabled()
                        && config.isEnabled(ComponentType.FAB));
    }

    // ==================== Theme ====================

    private static void applyTheme(ProjectUIConfig config,
                                   NativeRuntimeUIConfig.Builder builder) {
        ProjectUIConfig.Theme theme = config.getTheme();
        int background = theme.getBackgroundColor() != 0
                ? theme.getBackgroundColor()
                : config.getDashboard().getBackgroundColor();

        builder.themeMode(theme.getMode().getKey())
                .primaryColor(theme.getPrimaryColor())
                .backgroundColor(background)
                .animationDurationMs(theme.getAnimationDurationMs());
    }

    // ==================== WebView ====================

    private static void applyWebView(ProjectUIConfig config,
                                     NativeRuntimeUIConfig.Builder builder,
                                     ResourceDecoder decoder) {
        ProjectUIConfig.WebView webView = config.getWebView();
        boolean enabled = webView.isEnabled() && config.isEnabled(ComponentType.WEB_VIEW);

        builder.enableWebView(enabled)
                .fullScreenWeb(webView.isFullscreen())
                .webHostConfig(WebHostConfig.builder()
                        .javaScriptEnabled(webView.isJavaScriptEnabled())
                        .domStorageEnabled(webView.isDomStorageEnabled())
                        .databaseEnabled(webView.isDomStorageEnabled())
                        .allowFileAccess(true)
                        .allowContentAccess(true)
                        .allowZoom(webView.isZoomEnabled())
                        .showProgressBar(webView.isProgressBarEnabled())
                        .bridgeEnabled(true)
                        .openExternalLinksInBrowser(true)
                        .mediaPlaybackRequiresUserGesture(true)
                        .cacheMode(WebHostConfig.CACHE_ELSE_NETWORK)
                        // A project keeps its downloaded resources and HTTP cache between
                        // opens; only an explicit unregister clears them.
                        .clearCacheOnDestroy(false)
                        .backgroundColor(webView.getBackgroundColor())
                        .safeBrowsingEnabled(true)
                        .hardwareAcceleration(true)
                        // Only an encrypted internal project installs a decoder. Everything
                        // else passes null and is served byte-for-byte as stored.
                        .resourceDecoder(decoder)
                        .build());
    }

    // ==================== Diagnostics ====================

    /**
     * Human-readable summary of what the mapper produced. Used by the editor preview and by
     * {@code AppLog} when a project starts.
     */
    @NonNull
    public static String describe(@Nullable ProjectUIConfig config) {
        if (config == null) {
            return "no configuration";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("splash=").append(config.getSplash().isEnabled()
                ? config.getSplash().getMode().getKey() : "OFF");
        builder.append(", dashboard=").append(config.getDashboard().isEnabled()
                ? config.getDashboard().getMode().getKey() : "OFF");
        builder.append(", components=").append(config.getEnabledComponentCount());
        return builder.toString();
    }
}
