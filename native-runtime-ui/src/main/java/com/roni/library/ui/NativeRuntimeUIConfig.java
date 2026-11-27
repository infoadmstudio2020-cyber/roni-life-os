package com.roni.library.ui;

import android.graphics.Color;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.contracts.ui.AppBar;
import com.roni.library.contracts.ui.Content;
import com.roni.library.contracts.ui.Dialog;
import com.roni.library.contracts.ui.Feedback;
import com.roni.library.contracts.ui.Navigation;
import com.roni.library.contracts.ui.Overlay;
import com.roni.library.contracts.ui.SystemUI;
import com.roni.library.contracts.ui.UICategory;
import com.roni.library.contracts.ui.UIComponentType;
import com.roni.library.ui.builder.ContentBuilders;
import com.roni.library.ui.builder.CustomizationBuilders;
import com.roni.library.ui.builder.InteractionBuilders;
import com.roni.library.ui.builder.NavigationBuilders;
import com.roni.library.ui.builder.OverlayBuilders;
import com.roni.library.ui.builder.SystemUIBuilders;
import com.roni.library.ui.manager.ComponentType;
import com.roni.library.ui.web.WebHostConfig;
import com.roni.library.ui.web.WebSource;
import java.io.Serializable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Universal & Comprehensive configuration for NativeRuntimeUI framework.
 * Supports both rich component builders (Toolbar, StatusBar, Drawer, BottomNav, Overlays)
 * and registry-driven Contract DSL (enable, enableCategory, disable).
 */
public class NativeRuntimeUIConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean debugEnabled;
    private final String tag;
    private final int timeout;
    private final int animationDurationMs;
    private final boolean bottomSheetDismissible;
    private final boolean autoDismissToasts;

    // Component Enablement
    private final Set<ComponentType> enabledComponents;
    private final boolean toolbarEnabled;
    private final String toolbarTitle;
    private final String toolbarSubtitle;
    private final boolean drawerEnabled;
    private final String drawerHeaderTitle;
    private final String drawerHeaderSubtitle;
    private final boolean bottomNavEnabled;
    private final boolean webViewEnabled;
    private final String webViewInitialUrl;
    private final WebSource webSource;
    private final boolean fullScreenWebHost;
    private final WebHostConfig webHostConfig;
    private final boolean fabEnabled;
    private final boolean systemUIEnabled;

    // System UI styling
    private final int statusBarColor;
    private final boolean lightStatusBar;
    private final int navBarColor;
    private final boolean lightNavBar;
    private final boolean fullscreen;
    private final boolean immersive;
    private final boolean edgeToEdge;

    // Customization & Theme
    private final String themeMode;
    private final int primaryColor;
    private final int backgroundColor;
    private final int contentLayoutResId;
    private final transient View customContentView;

    // Component Builder Configurations
    private final SystemUIBuilders.StatusBarConfig statusBarConfig;
    private final SystemUIBuilders.NavigationBarConfig navigationBarConfig;
    private final SystemUIBuilders.ImmersiveModeConfig immersiveModeConfig;
    private final SystemUIBuilders.EdgeToEdgeConfig edgeToEdgeConfig;
    private final NavigationBuilders.ToolbarConfig toolbarConfig;
    private final NavigationBuilders.DrawerConfig drawerConfig;
    private final NavigationBuilders.BottomNavConfig bottomNavConfig;
    private final NavigationBuilders.NavigationRailConfig navigationRailConfig;
    private final NavigationBuilders.TabLayoutConfig tabLayoutConfig;
    private final NavigationBuilders.FabConfig fabConfig;
    private final ContentBuilders.WebViewConfig webViewConfig;
    private final OverlayBuilders.DialogConfig dialogConfig;
    private final OverlayBuilders.BottomSheetConfig bottomSheetConfig;
    private final OverlayBuilders.ToastConfig toastConfig;
    private final OverlayBuilders.LoadingOverlayConfig loadingOverlayConfig;
    private final CustomizationBuilders.ThemeConfig themeConfig;
    private final InteractionBuilders.BackNavigationConfig backNavigationConfig;

    // Contract Registry mappings
    private final boolean globalDefault;
    private final Map<UICategory, Boolean> categoryDefaults;
    private final Map<UIComponentType, Boolean> componentOverrides;
    private final Map<UIComponentType, Map<String, Object>> componentProperties;

    private NativeRuntimeUIConfig(Builder builder) {
        this.debugEnabled = builder.debugEnabled;
        this.tag = builder.tag != null ? builder.tag : "NativeRuntimeUI";
        this.timeout = builder.timeout > 0 ? builder.timeout : 30000;
        this.animationDurationMs = builder.animationDurationMs > 0 ? builder.animationDurationMs : 300;
        this.bottomSheetDismissible = builder.bottomSheetDismissible;
        this.autoDismissToasts = builder.autoDismissToasts;

        this.enabledComponents = Collections.unmodifiableSet(EnumSet.copyOf(builder.enabledComponents));
        this.toolbarEnabled = builder.toolbarEnabled;
        this.toolbarTitle = builder.toolbarTitle;
        this.toolbarSubtitle = builder.toolbarSubtitle;
        this.drawerEnabled = builder.drawerEnabled;
        this.drawerHeaderTitle = builder.drawerHeaderTitle;
        this.drawerHeaderSubtitle = builder.drawerHeaderSubtitle;
        this.bottomNavEnabled = builder.bottomNavEnabled;
        this.webViewEnabled = builder.webViewEnabled;
        this.webViewInitialUrl = builder.webViewInitialUrl;
        this.webSource = builder.webSource;
        this.fullScreenWebHost = builder.fullScreenWebHost;
        this.webHostConfig = builder.webHostConfig;
        this.fabEnabled = builder.fabEnabled;
        this.systemUIEnabled = builder.systemUIEnabled;

        this.statusBarColor = builder.statusBarColor;
        this.lightStatusBar = builder.lightStatusBar;
        this.navBarColor = builder.navBarColor;
        this.lightNavBar = builder.lightNavBar;
        this.fullscreen = builder.fullscreen;
        this.immersive = builder.immersive;
        this.edgeToEdge = builder.edgeToEdge;

        this.themeMode = builder.themeMode;
        this.primaryColor = builder.primaryColor;
        this.backgroundColor = builder.backgroundColor;
        this.contentLayoutResId = builder.contentLayoutResId;
        this.customContentView = builder.customContentView;

        this.statusBarConfig = builder.statusBarConfig;
        this.navigationBarConfig = builder.navigationBarConfig;
        this.immersiveModeConfig = builder.immersiveModeConfig;
        this.edgeToEdgeConfig = builder.edgeToEdgeConfig;
        this.toolbarConfig = builder.toolbarConfig;
        this.drawerConfig = builder.drawerConfig;
        this.bottomNavConfig = builder.bottomNavConfig;
        this.navigationRailConfig = builder.navigationRailConfig;
        this.tabLayoutConfig = builder.tabLayoutConfig;
        this.fabConfig = builder.fabConfig;
        this.webViewConfig = builder.webViewConfig;
        this.dialogConfig = builder.dialogConfig;
        this.bottomSheetConfig = builder.bottomSheetConfig;
        this.toastConfig = builder.toastConfig;
        this.loadingOverlayConfig = builder.loadingOverlayConfig;
        this.themeConfig = builder.themeConfig;
        this.backNavigationConfig = builder.backNavigationConfig;

        this.globalDefault = builder.globalDefault;
        this.categoryDefaults = Collections.unmodifiableMap(new HashMap<>(builder.categoryDefaults));
        this.componentOverrides = Collections.unmodifiableMap(new HashMap<>(builder.componentOverrides));
        this.componentProperties = Collections.unmodifiableMap(new HashMap<>(builder.componentProperties));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static NativeRuntimeUIConfig defaultConfiguration() {
        return builder().build();
    }

    public boolean isDebugEnabled() { return debugEnabled; }
    public String getTag() { return tag; }
    public int getTimeout() { return timeout; }
    public int getAnimationDurationMs() { return animationDurationMs; }
    public boolean isBottomSheetDismissible() { return bottomSheetDismissible; }
    public boolean isAutoDismissToasts() { return autoDismissToasts; }

    public boolean isToolbarEnabled() { return toolbarEnabled; }
    public String getToolbarTitle() { return toolbarTitle; }
    public String getToolbarSubtitle() { return toolbarSubtitle; }
    public boolean isDrawerEnabled() { return drawerEnabled; }
    public String getDrawerHeaderTitle() { return drawerHeaderTitle; }
    public String getDrawerHeaderSubtitle() { return drawerHeaderSubtitle; }
    public boolean isBottomNavEnabled() { return bottomNavEnabled; }
    public boolean isWebViewEnabled() { return webViewEnabled; }
    public String getWebViewInitialUrl() { return webViewInitialUrl; }
    public WebSource getWebSource() { return webSource; }
    public boolean isFullScreenWebHost() { return fullScreenWebHost; }
    public WebHostConfig getWebHostConfig() { return webHostConfig; }
    public boolean isFabEnabled() { return fabEnabled; }
    public boolean isSystemUIEnabled() { return systemUIEnabled; }

    public int getStatusBarColor() { return statusBarColor; }
    public boolean isLightStatusBar() { return lightStatusBar; }
    public int getNavBarColor() { return navBarColor; }
    public boolean isLightNavBar() { return lightNavBar; }
    public boolean isFullscreen() { return fullscreen; }
    public boolean isImmersive() { return immersive; }
    public boolean isEdgeToEdge() { return edgeToEdge; }

    public String getThemeMode() { return themeMode; }
    public int getPrimaryColor() { return primaryColor; }
    public int getBackgroundColor() { return backgroundColor; }
    public int getContentLayoutResId() { return contentLayoutResId; }
    public View getCustomContentView() { return customContentView; }

    public boolean isComponentEnabled(ComponentType type) {
        return enabledComponents.contains(type);
    }

    public boolean isGroupEnabled(ComponentType.Group group) {
        for (ComponentType c : enabledComponents) {
            if (c.getGroup() == group) return true;
        }
        return false;
    }

    public Set<ComponentType> getEnabledComponents() {
        return enabledComponents;
    }

    // Builder Getters
    public SystemUIBuilders.StatusBarConfig getStatusBarConfig() { return statusBarConfig; }
    public SystemUIBuilders.NavigationBarConfig getNavigationBarConfig() { return navigationBarConfig; }
    public SystemUIBuilders.ImmersiveModeConfig getImmersiveModeConfig() { return immersiveModeConfig; }
    public SystemUIBuilders.EdgeToEdgeConfig getEdgeToEdgeConfig() { return edgeToEdgeConfig; }
    public NavigationBuilders.ToolbarConfig getToolbarConfig() { return toolbarConfig; }
    public NavigationBuilders.DrawerConfig getDrawerConfig() { return drawerConfig; }
    public NavigationBuilders.BottomNavConfig getBottomNavConfig() { return bottomNavConfig; }
    public NavigationBuilders.NavigationRailConfig getNavigationRailConfig() { return navigationRailConfig; }
    public NavigationBuilders.TabLayoutConfig getTabLayoutConfig() { return tabLayoutConfig; }
    public NavigationBuilders.FabConfig getFabConfig() { return fabConfig; }
    public ContentBuilders.WebViewConfig getWebViewConfig() { return webViewConfig; }
    public OverlayBuilders.DialogConfig getDialogConfig() { return dialogConfig; }
    public OverlayBuilders.BottomSheetConfig getBottomSheetConfig() { return bottomSheetConfig; }
    public OverlayBuilders.ToastConfig getToastConfig() { return toastConfig; }
    public OverlayBuilders.LoadingOverlayConfig getLoadingOverlayConfig() { return loadingOverlayConfig; }
    public CustomizationBuilders.ThemeConfig getThemeConfig() { return themeConfig; }
    public InteractionBuilders.BackNavigationConfig getBackNavigationConfig() { return backNavigationConfig; }

    // Contracts Registry methods
    public boolean getGlobalDefault() { return globalDefault; }
    @NonNull public Map<UICategory, Boolean> getCategoryDefaults() { return categoryDefaults; }
    @NonNull public Map<UIComponentType, Boolean> getComponentOverrides() { return componentOverrides; }
    @NonNull public Map<UIComponentType, Map<String, Object>> getComponentProperties() { return componentProperties; }

    public boolean isComponentEnabled(@NonNull UIComponentType type) {
        if (type == null) return false;
        Boolean override = componentOverrides.get(type);
        if (override != null) return override;
        Boolean catDefault = categoryDefaults.get(type.getCategory());
        if (catDefault != null) return catDefault;

        if (type == AppBar.TOOLBAR) return toolbarEnabled;
        if (type == Navigation.BOTTOM_NAVIGATION) return bottomNavEnabled;
        if (type == Navigation.NAVIGATION_DRAWER) return drawerEnabled;
        if (type == Overlay.FLOATING_ACTION_BUTTON || type == Overlay.FLOATING_MENU) return fabEnabled;
        if (type == Content.CONTENT_CONTAINER || type == Content.ROOT_CONTAINER) return true;
        if (type == SystemUI.STATUS_BAR) return systemUIEnabled;
        if (type == SystemUI.EDGE_TO_EDGE) return edgeToEdge;
        if (type == Feedback.TOAST) return true;
        if (type == Dialog.ALERT_DIALOG) return true;
        if (type == Overlay.LOADING_OVERLAY) return isComponentEnabled(ComponentType.LOADING_OVERLAY);
        return globalDefault;
    }

    public static class Builder {
        private final Set<ComponentType> enabledComponents = EnumSet.noneOf(ComponentType.class);
        private boolean debugEnabled = false;
        private String tag = "NativeRuntimeUI";
        private int timeout = 30000;
        private int animationDurationMs = 300;
        private boolean bottomSheetDismissible = true;
        private boolean autoDismissToasts = true;

        /**
         * When true (the historical behaviour) an empty component set is replaced by a
         * sensible default shell. Project-level configuration turns this off so a project
         * that deliberately enables nothing is not silently given a toolbar.
         */
        private boolean defaultFallbackEnabled = true;

        private boolean toolbarEnabled = true;
        private String toolbarTitle = "NativeRuntimeUI";
        private String toolbarSubtitle = "";
        private boolean drawerEnabled = false;
        private String drawerHeaderTitle = "Navigation";
        private String drawerHeaderSubtitle = "";
        private boolean bottomNavEnabled = false;
        private boolean webViewEnabled = false;
        private String webViewInitialUrl = "";
        private WebSource webSource = null;
        private boolean fullScreenWebHost = false;
        private WebHostConfig webHostConfig = null;
        private boolean fabEnabled = false;
        private boolean systemUIEnabled = true;

        private int statusBarColor = Color.TRANSPARENT;
        private boolean lightStatusBar = false;
        private int navBarColor = Color.BLACK;
        private boolean lightNavBar = false;
        private boolean fullscreen = false;
        private boolean immersive = false;
        private boolean edgeToEdge = false;

        private String themeMode = "SYSTEM";
        private int primaryColor = 0xFF1976D2;
        private int backgroundColor = 0xFFFFFFFF;
        private int contentLayoutResId = 0;
        private View customContentView = null;

        private SystemUIBuilders.StatusBarConfig statusBarConfig;
        private SystemUIBuilders.NavigationBarConfig navigationBarConfig;
        private SystemUIBuilders.ImmersiveModeConfig immersiveModeConfig;
        private SystemUIBuilders.EdgeToEdgeConfig edgeToEdgeConfig;
        private NavigationBuilders.ToolbarConfig toolbarConfig;
        private NavigationBuilders.DrawerConfig drawerConfig;
        private NavigationBuilders.BottomNavConfig bottomNavConfig;
        private NavigationBuilders.NavigationRailConfig navigationRailConfig;
        private NavigationBuilders.TabLayoutConfig tabLayoutConfig;
        private NavigationBuilders.FabConfig fabConfig;
        private ContentBuilders.WebViewConfig webViewConfig;
        private OverlayBuilders.DialogConfig dialogConfig;
        private OverlayBuilders.BottomSheetConfig bottomSheetConfig;
        private OverlayBuilders.ToastConfig toastConfig;
        private OverlayBuilders.LoadingOverlayConfig loadingOverlayConfig;
        private CustomizationBuilders.ThemeConfig themeConfig;
        private InteractionBuilders.BackNavigationConfig backNavigationConfig;

        private boolean globalDefault = false;
        private final Map<UICategory, Boolean> categoryDefaults = new HashMap<>();
        private final Map<UIComponentType, Boolean> componentOverrides = new HashMap<>();
        private final Map<UIComponentType, Map<String, Object>> componentProperties = new HashMap<>();

        public Builder debugEnabled(boolean debugEnabled) { this.debugEnabled = debugEnabled; return this; }
        public Builder tag(String tag) { this.tag = tag; return this; }
        public Builder timeout(int timeout) { this.timeout = timeout; return this; }
        public Builder animationDurationMs(int animationDurationMs) { this.animationDurationMs = animationDurationMs; return this; }
        public Builder bottomSheetDismissible(boolean bottomSheetDismissible) { this.bottomSheetDismissible = bottomSheetDismissible; return this; }
        public Builder autoDismissToasts(boolean autoDismissToasts) { this.autoDismissToasts = autoDismissToasts; return this; }

        /**
         * Controls the legacy "empty component set means defaults" fallback.
         * Defaults to {@code true} for backward compatibility; project-level
         * configuration sets it to {@code false}.
         */
        public Builder defaultFallbackEnabled(boolean enabled) {
            this.defaultFallbackEnabled = enabled;
            return this;
        }

        public Builder enableComponent(ComponentType type) {
            if (type != null) this.enabledComponents.add(type);
            return this;
        }

        public Builder disableComponent(ComponentType type) {
            if (type != null) this.enabledComponents.remove(type);
            return this;
        }

        public Builder enableGroup(ComponentType.Group group) {
            if (group != null) {
                for (ComponentType c : ComponentType.values()) {
                    if (c.getGroup() == group) this.enabledComponents.add(c);
                }
            }
            return this;
        }

        public Builder disableGroup(ComponentType.Group group) {
            if (group != null) {
                for (ComponentType c : ComponentType.values()) {
                    if (c.getGroup() == group) this.enabledComponents.remove(c);
                }
            }
            return this;
        }

        public Builder enableToolbar(boolean enabled) {
            this.toolbarEnabled = enabled;
            if (enabled) enableComponent(ComponentType.TOOLBAR);
            else disableComponent(ComponentType.TOOLBAR);
            return this;
        }

        public Builder toolbarTitle(String title) {
            this.toolbarTitle = title != null ? title : "";
            return this;
        }

        public Builder toolbarSubtitle(String subtitle) {
            this.toolbarSubtitle = subtitle != null ? subtitle : "";
            return this;
        }

        public Builder enableDrawer(boolean enabled) {
            this.drawerEnabled = enabled;
            if (enabled) {
                enableComponent(ComponentType.DRAWER);
            } else {
                disableComponent(ComponentType.DRAWER);
            }
            return this;
        }
        public Builder drawerHeaderTitle(String title) {
            this.drawerHeaderTitle = title != null ? title : "";
            return this;
        }

        public Builder drawerHeaderSubtitle(String subtitle) {
            this.drawerHeaderSubtitle = subtitle != null ? subtitle : "";
            return this;
        }

        public Builder enableBottomNav(boolean enabled) {
            this.bottomNavEnabled = enabled;
            if (enabled) {
                enableComponent(ComponentType.BOTTOM_NAV);
            } else {
                disableComponent(ComponentType.BOTTOM_NAV);
            }
            return this;
        }

        public Builder enableWebView(boolean enabled) {
            this.webViewEnabled = enabled;
            if (enabled) {
                enableComponent(ComponentType.WEB_VIEW);
            } else {
                disableComponent(ComponentType.WEB_VIEW);
            }
            return this;
        }

        public Builder webViewInitialUrl(String url) {
            this.webViewInitialUrl = url != null ? url : "";
            return this;
        }

        /**
         * Loads a web application in a full-screen WebView host (no toolbar, no bottom nav).
         * This is the recommended mode for standalone web apps.
         */
        public Builder webApp(@Nullable WebSource source) {
            this.webSource = source;
            this.fullScreenWebHost = true;
            this.webViewEnabled = true;
            enableComponent(ComponentType.WEB_VIEW);
            return this;
        }

        /**
         * Loads a web application inside the normal NativeRuntime shell
         * (toolbar / bottom navigation remain available).
         */
        public Builder webAppInShell(@Nullable WebSource source) {
            this.webSource = source;
            this.fullScreenWebHost = false;
            this.webViewEnabled = true;
            enableComponent(ComponentType.WEB_VIEW);
            return this;
        }

        /** Explicitly toggles full-screen web host mode. */
        public Builder fullScreenWeb(boolean enabled) {
            this.fullScreenWebHost = enabled;
            return this;
        }

        /** Custom WebView behaviour (JS, storage, bridge, external links, ...). */
        public Builder webHostConfig(@Nullable WebHostConfig config) {
            this.webHostConfig = config;
            return this;
        }

        public Builder enableFab(boolean enabled) {
            this.fabEnabled = enabled;
            if (enabled) {
                enableComponent(ComponentType.FAB);
            } else {
                disableComponent(ComponentType.FAB);
            }
            return this;
        }

        public Builder enableSystemUI(boolean enabled) {
            this.systemUIEnabled = enabled;
            if (enabled) enableGroup(ComponentType.Group.SYSTEM_UI);
            else disableGroup(ComponentType.Group.SYSTEM_UI);
            return this;
        }

        public Builder statusBarColor(int color) { this.statusBarColor = color; return this; }
        public Builder lightStatusBar(boolean light) { this.lightStatusBar = light; return this; }
        public Builder navBarColor(int color) { this.navBarColor = color; return this; }
        public Builder lightNavBar(boolean light) { this.lightNavBar = light; return this; }
        public Builder fullscreen(boolean fullscreen) { this.fullscreen = fullscreen; return this; }
        public Builder immersive(boolean immersive) { this.immersive = immersive; return this; }
        public Builder edgeToEdge(boolean edgeToEdge) { this.edgeToEdge = edgeToEdge; return this; }

        public Builder themeMode(String mode) { this.themeMode = mode != null ? mode : "SYSTEM"; return this; }
        public Builder primaryColor(int color) { this.primaryColor = color; return this; }
        public Builder backgroundColor(int color) { this.backgroundColor = color; return this; }
        public Builder contentLayoutResId(int resId) { this.contentLayoutResId = resId; return this; }
        public Builder customContentView(View view) { this.customContentView = view; return this; }

        public Builder statusBar(SystemUIBuilders.StatusBarConfig config) {
            this.statusBarConfig = config;
            if (config != null) {
                this.statusBarColor = config.getColor();
                this.lightStatusBar = config.isLightIcons();
                enableComponent(ComponentType.STATUS_BAR);
            }
            return this;
        }

        public Builder navigationBar(SystemUIBuilders.NavigationBarConfig config) {
            this.navigationBarConfig = config;
            if (config != null) {
                this.navBarColor = config.getColor();
                this.lightNavBar = config.isLightButtons();
                enableComponent(ComponentType.NAVIGATION_BAR);
            }
            return this;
        }

        public Builder immersiveMode(SystemUIBuilders.ImmersiveModeConfig config) {
            this.immersiveModeConfig = config;
            if (config != null) {
                this.immersive = config.isFullscreen();
                enableComponent(ComponentType.FULLSCREEN_IMMERSIVE);
            }
            return this;
        }

        public Builder edgeToEdge(SystemUIBuilders.EdgeToEdgeConfig config) {
            this.edgeToEdgeConfig = config;
            if (config != null) {
                this.edgeToEdge = config.isEnabled();
                enableComponent(ComponentType.EDGE_TO_EDGE);
            }
            return this;
        }

        public Builder toolbar(NavigationBuilders.ToolbarConfig config) {
            this.toolbarConfig = config;
            if (config != null) {
                this.toolbarTitle = config.getTitle();
                this.toolbarSubtitle = config.getSubtitle();
                this.toolbarEnabled = true;
                enableComponent(ComponentType.TOOLBAR);
            }
            return this;
        }

        public Builder drawer(NavigationBuilders.DrawerConfig config) {
            this.drawerConfig = config;
            if (config != null) {
                this.drawerHeaderTitle = config.getHeaderTitle();
                this.drawerHeaderSubtitle = config.getHeaderSubtitle();
                this.drawerEnabled = true;
                enableComponent(ComponentType.DRAWER);
            }
            return this;
        }

        public Builder bottomNav(NavigationBuilders.BottomNavConfig config) {
            this.bottomNavConfig = config;

            if (config != null) {
                this.bottomNavEnabled = true;
                enableComponent(ComponentType.BOTTOM_NAV);
            }

            return this;
        }

        public Builder navigationRail(NavigationBuilders.NavigationRailConfig config) {
            this.navigationRailConfig = config;
            if (config != null) enableComponent(ComponentType.NAVIGATION_RAIL);
            return this;
        }

        public Builder tabLayout(NavigationBuilders.TabLayoutConfig config) {
            this.tabLayoutConfig = config;
            if (config != null) enableComponent(ComponentType.TAB_LAYOUT);
            return this;
        }

        public Builder fab(NavigationBuilders.FabConfig config) {
            this.fabConfig = config;

            if (config != null) {
                this.fabEnabled = true;
                enableComponent(ComponentType.FAB);
            }

            return this;
        }

        public Builder webView(ContentBuilders.WebViewConfig config) {
            this.webViewConfig = config;

            if (config != null) {
                this.webViewInitialUrl = config.getInitialUrl();
                this.webViewEnabled = true;

                enableComponent(ComponentType.WEB_VIEW);
            }

            return this;
        }

        public Builder dialog(OverlayBuilders.DialogConfig config) {
            this.dialogConfig = config;
            if (config != null) enableComponent(ComponentType.ALERT_DIALOG);
            return this;
        }

        public Builder bottomSheet(OverlayBuilders.BottomSheetConfig config) {
            this.bottomSheetConfig = config;
            if (config != null) enableComponent(ComponentType.BOTTOM_SHEET);
            return this;
        }

        public Builder toast(OverlayBuilders.ToastConfig config) {
            this.toastConfig = config;
            if (config != null) enableComponent(ComponentType.TOAST);
            return this;
        }

        public Builder loadingOverlay(OverlayBuilders.LoadingOverlayConfig config) {
            this.loadingOverlayConfig = config;
            if (config != null) enableComponent(ComponentType.LOADING_OVERLAY);
            return this;
        }

        public Builder theme(CustomizationBuilders.ThemeConfig config) {
            this.themeConfig = config;

            if (config != null) {
                this.themeMode = config.isDark() ? "dark" : "light";

                this.primaryColor = Color.parseColor(
                        config.getPrimaryColor()
                );

                this.backgroundColor = Color.parseColor(
                        config.getSurfaceColor()
                );
            }

            return this;
        }

        public Builder backNavigation(InteractionBuilders.BackNavigationConfig config) {
            this.backNavigationConfig = config;
            if (config != null) enableComponent(ComponentType.BACK_NAVIGATION);
            return this;
        }

        // Contracts Fluent DSL support
        public Builder enable(@NonNull UIComponentType type) {
            if (type != null) {
                componentOverrides.put(type, true);

                // AppBar
                if (type == AppBar.TOOLBAR) {
                    enableToolbar(true);
                    enableComponent(ComponentType.TOOLBAR);
                }

                // Navigation
                else if (type == Navigation.BOTTOM_NAVIGATION) {
                    enableBottomNav(true);
                    enableComponent(ComponentType.BOTTOM_NAV);
                }
                else if (type == Navigation.NAVIGATION_DRAWER) {
                    enableDrawer(true);
                    enableComponent(ComponentType.DRAWER);
                }

                // Overlay
                else if (type == Overlay.FLOATING_ACTION_BUTTON
                        || type == Overlay.FLOATING_MENU) {
                    enableFab(true);
                    enableComponent(ComponentType.FAB);
                }
                else if (type == Overlay.LOADING_OVERLAY) {
                    enableComponent(ComponentType.LOADING_OVERLAY);
                }

                // Content
                else if (type == Content.CONTENT_CONTAINER) {
                    enableComponent(ComponentType.MULTI_CONTENT);
                }
                else if (type == Content.CUSTOM_VIEW_CONTAINER) {
                    enableComponent(ComponentType.CUSTOM_VIEW);
                }
                else if (type == Content.FRAGMENT_CONTAINER) {
                    enableComponent(ComponentType.FRAGMENT_CONTAINER);
                }
                else if (type == Content.WEB_CONTENT_CONTAINER) {
                    enableComponent(ComponentType.WEB_VIEW);
                }

                // System UI
                else if (type == SystemUI.STATUS_BAR) {
                    enableSystemUI(true);
                    enableComponent(ComponentType.STATUS_BAR);
                }
                else if (type == SystemUI.NAVIGATION_BAR) {
                    enableComponent(ComponentType.NAVIGATION_BAR);
                }
                else if (type == SystemUI.EDGE_TO_EDGE) {
                    edgeToEdge(true);
                    enableComponent(ComponentType.EDGE_TO_EDGE);
                }
                else if (type == SystemUI.FULL_SCREEN
                        || type == SystemUI.IMMERSIVE_MODE) {
                    enableComponent(ComponentType.FULLSCREEN_IMMERSIVE);
                }

                // Feedback
                else if (type == Feedback.TOAST) {
                    enableComponent(ComponentType.TOAST);
                }
                else if (type == Feedback.SNACKBAR) {
                    enableComponent(ComponentType.SNACKBAR);
                }

                // Dialog
                else if (type == Dialog.ALERT_DIALOG) {
                    enableComponent(ComponentType.ALERT_DIALOG);
                }
                else if (type == Dialog.BOTTOM_SHEET_DIALOG) {
                    enableComponent(ComponentType.BOTTOM_SHEET);
                }
            }

            return this;
        }

        public Builder disable(@NonNull UIComponentType type) {
            if (type != null) {
                componentOverrides.put(type, false);
                if (type == AppBar.TOOLBAR) enableToolbar(false);
                else if (type == Navigation.BOTTOM_NAVIGATION) enableBottomNav(false);
                else if (type == Navigation.NAVIGATION_DRAWER) enableDrawer(false);
                else if (type == Overlay.FLOATING_ACTION_BUTTON || type == Overlay.FLOATING_MENU) enableFab(false);
                else if (type == SystemUI.EDGE_TO_EDGE) edgeToEdge(false);
            }
            return this;
        }

        public Builder enableCategory(@NonNull UICategory category) {
            if (category != null) {
                categoryDefaults.put(category, true);
                if (category == UICategory.APP_BAR) enableToolbar(true);
                else if (category == UICategory.NAVIGATION) { enableBottomNav(true); enableDrawer(true); }
                else if (category == UICategory.SYSTEM_UI) enableSystemUI(true);
                else if (category == UICategory.OVERLAY) enableGroup(ComponentType.Group.OVERLAY);
            }
            return this;
        }

        public Builder disableCategory(@NonNull UICategory category) {
            if (category != null) {
                categoryDefaults.put(category, false);
                if (category == UICategory.APP_BAR) enableToolbar(false);
                else if (category == UICategory.NAVIGATION) { enableBottomNav(false); enableDrawer(false); }
                else if (category == UICategory.SYSTEM_UI) enableSystemUI(false);
            }
            return this;
        }

        public Builder setGlobalDefault(boolean enabled) {
            this.globalDefault = enabled;
            return this;
        }

        public Builder configureComponent(
                @NonNull UIComponentType type,
                @NonNull String key,
                @Nullable Object value) {

            if (type != null && key != null) {
                Map<String, Object> properties = this.componentProperties.get(type);

                if (properties == null) {
                    properties = new HashMap<>();
                    this.componentProperties.put(type, properties);
                }

                properties.put(key, value);
            }

            return this;
        }

        public NativeRuntimeUIConfig build() {
            if (defaultFallbackEnabled && enabledComponents.isEmpty()) {
                enableGroup(ComponentType.Group.SYSTEM_UI);
                // App Navigation
                enableComponent(ComponentType.TOOLBAR);
                // Content
                enableComponent(ComponentType.CUSTOM_VIEW);
            }
            return new NativeRuntimeUIConfig(this);
        }
    }
}
