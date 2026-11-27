package com.roni.library.ui.config;

import com.roni.library.ui.manager.ComponentType;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-project UI configuration.
 *
 * <p>One instance describes exactly which native UI components a single project wants, and how
 * each of them is styled. It is deliberately free of Android imports so it can be unit-tested on
 * the JVM and persisted through any {@code KeyValueBackend}.</p>
 *
 * <p>Pipeline: {@code ProjectUIConfig} → {@link ProjectUIConfigMapper} →
 * {@code NativeRuntimeUIConfig.Builder} → {@code NativeUIComponentManager} → native rendering.
 * A component that is disabled here is never instantiated by the component manager.</p>
 */
public final class ProjectUIConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int SCHEMA_VERSION = 1;

    /** Splash presentation mode. */
    public enum SplashMode {
        NATIVE("NATIVE"), WEB("WEB"), NONE("NONE");

        private final String key;

        SplashMode(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static SplashMode fromKey(String key) {
            if (key != null) {
                for (SplashMode mode : values()) {
                    if (mode.key.equalsIgnoreCase(key)) {
                        return mode;
                    }
                }
            }
            return NATIVE;
        }
    }

    /** Splash enter animation. */
    public enum SplashAnimation {
        NONE("NONE"), FADE("FADE"), SLIDE_UP("SLIDE_UP"), SCALE("SCALE"), PULSE("PULSE");

        private final String key;

        SplashAnimation(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static SplashAnimation fromKey(String key) {
            if (key != null) {
                for (SplashAnimation animation : values()) {
                    if (animation.key.equalsIgnoreCase(key)) {
                        return animation;
                    }
                }
            }
            return FADE;
        }
    }

    /** Dashboard presentation mode. */
    public enum DashboardMode {
        NATIVE("NATIVE"), WEB("WEB");

        private final String key;

        DashboardMode(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static DashboardMode fromKey(String key) {
            if (key != null) {
                for (DashboardMode mode : values()) {
                    if (mode.key.equalsIgnoreCase(key)) {
                        return mode;
                    }
                }
            }
            return NATIVE;
        }
    }

    /** Dashboard layout density. */
    public enum DashboardLayout {
        STANDARD("STANDARD"), COMPACT("COMPACT"), GRID("GRID");

        private final String key;

        DashboardLayout(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static DashboardLayout fromKey(String key) {
            if (key != null) {
                for (DashboardLayout layout : values()) {
                    if (layout.key.equalsIgnoreCase(key)) {
                        return layout;
                    }
                }
            }
            return STANDARD;
        }
    }

    /** Theme mode shared by splash, dashboard and project runtime. */
    public enum ThemeMode {
        SYSTEM("SYSTEM"), LIGHT("LIGHT"), DARK("DARK");

        private final String key;

        ThemeMode(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static ThemeMode fromKey(String key) {
            if (key != null) {
                for (ThemeMode mode : values()) {
                    if (mode.key.equalsIgnoreCase(key)) {
                        return mode;
                    }
                }
            }
            return SYSTEM;
        }
    }

    // ==================== Nested configuration blocks ====================

    /** Splash screen configuration for one project. */
    public static final class Splash implements Serializable {
        private static final long serialVersionUID = 1L;

        public static final int MIN_DURATION_MS = 200;
        public static final int MAX_DURATION_MS = 10000;
        public static final int DEFAULT_DURATION_MS = 1200;

        private boolean enabled = true;
        private SplashMode mode = SplashMode.NATIVE;
        private int durationMs = DEFAULT_DURATION_MS;
        private String logoText = "";
        private String logoDrawable = "";
        private int backgroundColor = 0xFF080D1B;
        private int accentColor = 0xFF6C5CE7;
        private SplashAnimation animation = SplashAnimation.FADE;
        private boolean fullscreen = true;
        private boolean showProgress = true;
        private boolean skipOnTap = true;

        public boolean isEnabled() {
            return enabled;
        }

        public Splash setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public SplashMode getMode() {
            return mode;
        }

        public Splash setMode(SplashMode mode) {
            this.mode = mode != null ? mode : SplashMode.NATIVE;
            return this;
        }

        public int getDurationMs() {
            return durationMs;
        }

        public Splash setDurationMs(int durationMs) {
            this.durationMs = Math.max(MIN_DURATION_MS, Math.min(MAX_DURATION_MS, durationMs));
            return this;
        }

        public String getLogoText() {
            return logoText;
        }

        public Splash setLogoText(String logoText) {
            this.logoText = logoText != null ? logoText : "";
            return this;
        }

        public String getLogoDrawable() {
            return logoDrawable;
        }

        public Splash setLogoDrawable(String logoDrawable) {
            this.logoDrawable = logoDrawable != null ? logoDrawable : "";
            return this;
        }

        public int getBackgroundColor() {
            return backgroundColor;
        }

        public Splash setBackgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public int getAccentColor() {
            return accentColor;
        }

        public Splash setAccentColor(int accentColor) {
            this.accentColor = accentColor;
            return this;
        }

        public SplashAnimation getAnimation() {
            return animation;
        }

        public Splash setAnimation(SplashAnimation animation) {
            this.animation = animation != null ? animation : SplashAnimation.FADE;
            return this;
        }

        public boolean isFullscreen() {
            return fullscreen;
        }

        public Splash setFullscreen(boolean fullscreen) {
            this.fullscreen = fullscreen;
            return this;
        }

        public boolean isShowProgress() {
            return showProgress;
        }

        public Splash setShowProgress(boolean showProgress) {
            this.showProgress = showProgress;
            return this;
        }

        public boolean isSkipOnTap() {
            return skipOnTap;
        }

        public Splash setSkipOnTap(boolean skipOnTap) {
            this.skipOnTap = skipOnTap;
            return this;
        }

        /** True when the splash should actually be rendered. */
        public boolean shouldRender() {
            return enabled && mode != SplashMode.NONE;
        }

        public Splash copy() {
            Splash copy = new Splash();
            copy.enabled = enabled;
            copy.mode = mode;
            copy.durationMs = durationMs;
            copy.logoText = logoText;
            copy.logoDrawable = logoDrawable;
            copy.backgroundColor = backgroundColor;
            copy.accentColor = accentColor;
            copy.animation = animation;
            copy.fullscreen = fullscreen;
            copy.showProgress = showProgress;
            copy.skipOnTap = skipOnTap;
            return copy;
        }
    }

    /** Dashboard configuration for one project. */
    public static final class Dashboard implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled = true;
        private DashboardMode mode = DashboardMode.NATIVE;
        private DashboardLayout layout = DashboardLayout.STANDARD;
        private boolean toolbarEnabled = true;
        private boolean drawerEnabled = false;
        private boolean bottomNavEnabled = false;
        private boolean fabEnabled = true;
        private boolean fullscreen = false;
        private boolean showStats = true;
        private boolean showRecent = true;
        private int backgroundColor = 0;
        private int accentColor = 0;
        private String webSource = "";

        public boolean isEnabled() {
            return enabled;
        }

        public Dashboard setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public DashboardMode getMode() {
            return mode;
        }

        public Dashboard setMode(DashboardMode mode) {
            this.mode = mode != null ? mode : DashboardMode.NATIVE;
            return this;
        }

        public DashboardLayout getLayout() {
            return layout;
        }

        public Dashboard setLayout(DashboardLayout layout) {
            this.layout = layout != null ? layout : DashboardLayout.STANDARD;
            return this;
        }

        public boolean isToolbarEnabled() {
            return toolbarEnabled;
        }

        public Dashboard setToolbarEnabled(boolean toolbarEnabled) {
            this.toolbarEnabled = toolbarEnabled;
            return this;
        }

        public boolean isDrawerEnabled() {
            return drawerEnabled;
        }

        public Dashboard setDrawerEnabled(boolean drawerEnabled) {
            this.drawerEnabled = drawerEnabled;
            return this;
        }

        public boolean isBottomNavEnabled() {
            return bottomNavEnabled;
        }

        public Dashboard setBottomNavEnabled(boolean bottomNavEnabled) {
            this.bottomNavEnabled = bottomNavEnabled;
            return this;
        }

        public boolean isFabEnabled() {
            return fabEnabled;
        }

        public Dashboard setFabEnabled(boolean fabEnabled) {
            this.fabEnabled = fabEnabled;
            return this;
        }

        public boolean isFullscreen() {
            return fullscreen;
        }

        public Dashboard setFullscreen(boolean fullscreen) {
            this.fullscreen = fullscreen;
            return this;
        }

        public boolean isShowStats() {
            return showStats;
        }

        public Dashboard setShowStats(boolean showStats) {
            this.showStats = showStats;
            return this;
        }

        public boolean isShowRecent() {
            return showRecent;
        }

        public Dashboard setShowRecent(boolean showRecent) {
            this.showRecent = showRecent;
            return this;
        }

        public int getBackgroundColor() {
            return backgroundColor;
        }

        public Dashboard setBackgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public int getAccentColor() {
            return accentColor;
        }

        public Dashboard setAccentColor(int accentColor) {
            this.accentColor = accentColor;
            return this;
        }

        public String getWebSource() {
            return webSource;
        }

        public Dashboard setWebSource(String webSource) {
            this.webSource = webSource != null ? webSource : "";
            return this;
        }

        public Dashboard copy() {
            Dashboard copy = new Dashboard();
            copy.enabled = enabled;
            copy.mode = mode;
            copy.layout = layout;
            copy.toolbarEnabled = toolbarEnabled;
            copy.drawerEnabled = drawerEnabled;
            copy.bottomNavEnabled = bottomNavEnabled;
            copy.fabEnabled = fabEnabled;
            copy.fullscreen = fullscreen;
            copy.showStats = showStats;
            copy.showRecent = showRecent;
            copy.backgroundColor = backgroundColor;
            copy.accentColor = accentColor;
            copy.webSource = webSource;
            return copy;
        }
    }

    /** Theme and animation configuration. */
    public static final class Theme implements Serializable {
        private static final long serialVersionUID = 1L;

        private ThemeMode mode = ThemeMode.SYSTEM;
        private int primaryColor = 0xFF6C5CE7;
        private int backgroundColor = 0;
        private int accentColor = 0xFF00B894;
        private int textColor = 0;
        private float fontScale = 1.0f;
        private int animationDurationMs = 300;

        public ThemeMode getMode() {
            return mode;
        }

        public Theme setMode(ThemeMode mode) {
            this.mode = mode != null ? mode : ThemeMode.SYSTEM;
            return this;
        }

        public int getPrimaryColor() {
            return primaryColor;
        }

        public Theme setPrimaryColor(int primaryColor) {
            this.primaryColor = primaryColor;
            return this;
        }

        public int getBackgroundColor() {
            return backgroundColor;
        }

        public Theme setBackgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public int getAccentColor() {
            return accentColor;
        }

        public Theme setAccentColor(int accentColor) {
            this.accentColor = accentColor;
            return this;
        }

        public int getTextColor() {
            return textColor;
        }

        public Theme setTextColor(int textColor) {
            this.textColor = textColor;
            return this;
        }

        public float getFontScale() {
            return fontScale;
        }

        public Theme setFontScale(float fontScale) {
            this.fontScale = Math.max(0.75f, Math.min(1.6f, fontScale));
            return this;
        }

        public int getAnimationDurationMs() {
            return animationDurationMs;
        }

        public Theme setAnimationDurationMs(int animationDurationMs) {
            this.animationDurationMs = Math.max(0, Math.min(2000, animationDurationMs));
            return this;
        }

        public Theme copy() {
            Theme copy = new Theme();
            copy.mode = mode;
            copy.primaryColor = primaryColor;
            copy.backgroundColor = backgroundColor;
            copy.accentColor = accentColor;
            copy.textColor = textColor;
            copy.fontScale = fontScale;
            copy.animationDurationMs = animationDurationMs;
            return copy;
        }
    }

    /** Per-component state: enabled flag plus free-form style properties. */
    public static final class Component implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled;
        private final Map<String, String> properties = new LinkedHashMap<>();

        public Component() {
        }

        public Component(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Component setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Map<String, String> getProperties() {
            return Collections.unmodifiableMap(properties);
        }

        public String getProperty(String key, String fallback) {
            String value = properties.get(key);
            return value != null ? value : fallback;
        }

        public int getIntProperty(String key, int fallback) {
            String value = properties.get(key);
            if (value == null) {
                return fallback;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException error) {
                return fallback;
            }
        }

        public Component setProperty(String key, String value) {
            if (key == null || key.trim().isEmpty()) {
                return this;
            }
            if (value == null) {
                properties.remove(key);
            } else {
                properties.put(key, value);
            }
            return this;
        }

        public Component clearProperties() {
            properties.clear();
            return this;
        }

        public Component copy() {
            Component copy = new Component(enabled);
            copy.properties.putAll(properties);
            return copy;
        }
    }

    /** WebView container configuration. */
    public static final class WebView implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean enabled = true;
        private boolean fullscreen = true;
        private boolean javaScriptEnabled = true;
        private boolean domStorageEnabled = true;
        private boolean zoomEnabled = false;
        private boolean progressBarEnabled = false;
        private int backgroundColor = 0;

        public boolean isEnabled() {
            return enabled;
        }

        public WebView setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public boolean isFullscreen() {
            return fullscreen;
        }

        public WebView setFullscreen(boolean fullscreen) {
            this.fullscreen = fullscreen;
            return this;
        }

        public boolean isJavaScriptEnabled() {
            return javaScriptEnabled;
        }

        public WebView setJavaScriptEnabled(boolean javaScriptEnabled) {
            this.javaScriptEnabled = javaScriptEnabled;
            return this;
        }

        public boolean isDomStorageEnabled() {
            return domStorageEnabled;
        }

        public WebView setDomStorageEnabled(boolean domStorageEnabled) {
            this.domStorageEnabled = domStorageEnabled;
            return this;
        }

        public boolean isZoomEnabled() {
            return zoomEnabled;
        }

        public WebView setZoomEnabled(boolean zoomEnabled) {
            this.zoomEnabled = zoomEnabled;
            return this;
        }

        public boolean isProgressBarEnabled() {
            return progressBarEnabled;
        }

        public WebView setProgressBarEnabled(boolean progressBarEnabled) {
            this.progressBarEnabled = progressBarEnabled;
            return this;
        }

        public int getBackgroundColor() {
            return backgroundColor;
        }

        public WebView setBackgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public WebView copy() {
            WebView copy = new WebView();
            copy.enabled = enabled;
            copy.fullscreen = fullscreen;
            copy.javaScriptEnabled = javaScriptEnabled;
            copy.domStorageEnabled = domStorageEnabled;
            copy.zoomEnabled = zoomEnabled;
            copy.progressBarEnabled = progressBarEnabled;
            copy.backgroundColor = backgroundColor;
            return copy;
        }
    }

    /** System bar and navigation behaviour. */
    public static final class Navigation implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean statusBarVisible = true;
        private boolean navigationBarVisible = true;
        private boolean fullscreen = false;
        private boolean immersive = false;
        private boolean edgeToEdge = true;
        private boolean backNavigationEnabled = true;
        private int statusBarColor = 0;
        private int navigationBarColor = 0;
        private boolean lightStatusBar = false;
        private boolean lightNavigationBar = false;

        public boolean isStatusBarVisible() {
            return statusBarVisible;
        }

        public Navigation setStatusBarVisible(boolean statusBarVisible) {
            this.statusBarVisible = statusBarVisible;
            return this;
        }

        public boolean isNavigationBarVisible() {
            return navigationBarVisible;
        }

        public Navigation setNavigationBarVisible(boolean navigationBarVisible) {
            this.navigationBarVisible = navigationBarVisible;
            return this;
        }

        public boolean isFullscreen() {
            return fullscreen;
        }

        public Navigation setFullscreen(boolean fullscreen) {
            this.fullscreen = fullscreen;
            return this;
        }

        public boolean isImmersive() {
            return immersive;
        }

        public Navigation setImmersive(boolean immersive) {
            this.immersive = immersive;
            return this;
        }

        public boolean isEdgeToEdge() {
            return edgeToEdge;
        }

        public Navigation setEdgeToEdge(boolean edgeToEdge) {
            this.edgeToEdge = edgeToEdge;
            return this;
        }

        public boolean isBackNavigationEnabled() {
            return backNavigationEnabled;
        }

        public Navigation setBackNavigationEnabled(boolean backNavigationEnabled) {
            this.backNavigationEnabled = backNavigationEnabled;
            return this;
        }

        public int getStatusBarColor() {
            return statusBarColor;
        }

        public Navigation setStatusBarColor(int statusBarColor) {
            this.statusBarColor = statusBarColor;
            return this;
        }

        public int getNavigationBarColor() {
            return navigationBarColor;
        }

        public Navigation setNavigationBarColor(int navigationBarColor) {
            this.navigationBarColor = navigationBarColor;
            return this;
        }

        public boolean isLightStatusBar() {
            return lightStatusBar;
        }

        public Navigation setLightStatusBar(boolean lightStatusBar) {
            this.lightStatusBar = lightStatusBar;
            return this;
        }

        public boolean isLightNavigationBar() {
            return lightNavigationBar;
        }

        public Navigation setLightNavigationBar(boolean lightNavigationBar) {
            this.lightNavigationBar = lightNavigationBar;
            return this;
        }

        public Navigation copy() {
            Navigation copy = new Navigation();
            copy.statusBarVisible = statusBarVisible;
            copy.navigationBarVisible = navigationBarVisible;
            copy.fullscreen = fullscreen;
            copy.immersive = immersive;
            copy.edgeToEdge = edgeToEdge;
            copy.backNavigationEnabled = backNavigationEnabled;
            copy.statusBarColor = statusBarColor;
            copy.navigationBarColor = navigationBarColor;
            copy.lightStatusBar = lightStatusBar;
            copy.lightNavigationBar = lightNavigationBar;
            return copy;
        }
    }

    // ==================== State ====================

    private int schemaVersion = SCHEMA_VERSION;
    private String presetId = "";
    private final Splash splash = new Splash();
    private final Dashboard dashboard = new Dashboard();
    private final Theme theme = new Theme();
    private final WebView webView = new WebView();
    private final Navigation navigation = new Navigation();
    private final Map<ComponentType, Component> components =
            new EnumMap<ComponentType, Component>(ComponentType.class);

    public ProjectUIConfig() {
        resetToDefaults();
    }

    // ==================== Accessors ====================

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getPresetId() {
        return presetId;
    }

    public ProjectUIConfig setPresetId(String presetId) {
        this.presetId = presetId != null ? presetId : "";
        return this;
    }

    public Splash getSplash() {
        return splash;
    }

    public Dashboard getDashboard() {
        return dashboard;
    }

    public Theme getTheme() {
        return theme;
    }

    public WebView getWebView() {
        return webView;
    }

    public Navigation getNavigation() {
        return navigation;
    }

    /** Returns the state of one component, creating a disabled entry when absent. */
    public Component component(ComponentType type) {
        Component component = components.get(type);
        if (component == null) {
            component = new Component(false);
            components.put(type, component);
        }
        return component;
    }

    public boolean isEnabled(ComponentType type) {
        Component component = components.get(type);
        return component != null && component.isEnabled();
    }

    public ProjectUIConfig setEnabled(ComponentType type, boolean enabled) {
        component(type).setEnabled(enabled);
        return this;
    }

    public ProjectUIConfig toggle(ComponentType type) {
        return setEnabled(type, !isEnabled(type));
    }

    /** Snapshot of every component state, keyed by type. */
    public Map<ComponentType, Component> getComponents() {
        return Collections.unmodifiableMap(components);
    }

    /** Component types that are currently enabled, in declaration order. */
    public java.util.List<ComponentType> getEnabledComponents() {
        java.util.List<ComponentType> enabled = new java.util.ArrayList<>();
        for (ComponentType type : ComponentType.values()) {
            if (isEnabled(type)) {
                enabled.add(type);
            }
        }
        return enabled;
    }

    public int getEnabledComponentCount() {
        return getEnabledComponents().size();
    }

    // ==================== Lifecycle ====================

    /** Restores the documented default configuration (status bar + web view only). */
    public ProjectUIConfig resetToDefaults() {
        schemaVersion = SCHEMA_VERSION;
        presetId = "";

        splash.setEnabled(true)
                .setMode(SplashMode.NATIVE)
                .setDurationMs(Splash.DEFAULT_DURATION_MS)
                .setLogoText("")
                .setLogoDrawable("")
                .setBackgroundColor(0xFF080D1B)
                .setAccentColor(0xFF6C5CE7)
                .setAnimation(SplashAnimation.FADE)
                .setFullscreen(true)
                .setShowProgress(true)
                .setSkipOnTap(true);

        dashboard.setEnabled(true)
                .setMode(DashboardMode.NATIVE)
                .setLayout(DashboardLayout.STANDARD)
                .setToolbarEnabled(true)
                .setDrawerEnabled(false)
                .setBottomNavEnabled(false)
                .setFabEnabled(true)
                .setFullscreen(false)
                .setShowStats(true)
                .setShowRecent(true)
                .setBackgroundColor(0)
                .setAccentColor(0)
                .setWebSource("");

        theme.setMode(ThemeMode.SYSTEM)
                .setPrimaryColor(0xFF6C5CE7)
                .setBackgroundColor(0)
                .setAccentColor(0xFF00B894)
                .setTextColor(0)
                .setFontScale(1.0f)
                .setAnimationDurationMs(300);

        webView.setEnabled(true)
                .setFullscreen(true)
                .setJavaScriptEnabled(true)
                .setDomStorageEnabled(true)
                .setZoomEnabled(false)
                .setProgressBarEnabled(false)
                .setBackgroundColor(0);

        navigation.setStatusBarVisible(true)
                .setNavigationBarVisible(true)
                .setFullscreen(false)
                .setImmersive(false)
                .setEdgeToEdge(true)
                .setBackNavigationEnabled(true)
                .setStatusBarColor(0)
                .setNavigationBarColor(0)
                .setLightStatusBar(false)
                .setLightNavigationBar(false);

        components.clear();
        for (ComponentType type : ComponentType.values()) {
            components.put(type, new Component(false));
        }
        // Defaults: a plain web-app shell.
        setEnabled(ComponentType.STATUS_BAR, true);
        setEnabled(ComponentType.WEB_VIEW, true);
        setEnabled(ComponentType.BACK_NAVIGATION, true);
        setEnabled(ComponentType.UI_STATE_MANAGEMENT, true);
        setEnabled(ComponentType.COMPONENT_LIFECYCLE, true);
        setEnabled(ComponentType.THEME_INTEGRATION, true);
        return this;
    }

    /** Deep copy — used by the editor so a cancelled edit never mutates the stored config. */
    public ProjectUIConfig copy() {
        ProjectUIConfig copy = new ProjectUIConfig();
        copy.schemaVersion = schemaVersion;
        copy.presetId = presetId;
        copyComponents(splash, copy.splash);
        copyComponents(dashboard, copy.dashboard);
        copyComponents(theme, copy.theme);
        copyComponents(webView, copy.webView);
        copyComponents(navigation, copy.navigation);
        copy.components.clear();
        for (Map.Entry<ComponentType, Component> entry : components.entrySet()) {
            copy.components.put(entry.getKey(), entry.getValue().copy());
        }
        return copy;
    }

    private static void copyComponents(Object source, Object target) {
        // Each nested block exposes copy(); this helper keeps copy() readable.
        if (source instanceof Splash && target instanceof Splash) {
            Splash from = (Splash) source;
            Splash to = (Splash) target;
            to.enabled = from.enabled;
            to.mode = from.mode;
            to.durationMs = from.durationMs;
            to.logoText = from.logoText;
            to.logoDrawable = from.logoDrawable;
            to.backgroundColor = from.backgroundColor;
            to.accentColor = from.accentColor;
            to.animation = from.animation;
            to.fullscreen = from.fullscreen;
            to.showProgress = from.showProgress;
            to.skipOnTap = from.skipOnTap;
        } else if (source instanceof Dashboard && target instanceof Dashboard) {
            Dashboard from = (Dashboard) source;
            Dashboard to = (Dashboard) target;
            to.enabled = from.enabled;
            to.mode = from.mode;
            to.layout = from.layout;
            to.toolbarEnabled = from.toolbarEnabled;
            to.drawerEnabled = from.drawerEnabled;
            to.bottomNavEnabled = from.bottomNavEnabled;
            to.fabEnabled = from.fabEnabled;
            to.fullscreen = from.fullscreen;
            to.showStats = from.showStats;
            to.showRecent = from.showRecent;
            to.backgroundColor = from.backgroundColor;
            to.accentColor = from.accentColor;
            to.webSource = from.webSource;
        } else if (source instanceof Theme && target instanceof Theme) {
            Theme from = (Theme) source;
            Theme to = (Theme) target;
            to.mode = from.mode;
            to.primaryColor = from.primaryColor;
            to.backgroundColor = from.backgroundColor;
            to.accentColor = from.accentColor;
            to.textColor = from.textColor;
            to.fontScale = from.fontScale;
            to.animationDurationMs = from.animationDurationMs;
        } else if (source instanceof WebView && target instanceof WebView) {
            WebView from = (WebView) source;
            WebView to = (WebView) target;
            to.enabled = from.enabled;
            to.fullscreen = from.fullscreen;
            to.javaScriptEnabled = from.javaScriptEnabled;
            to.domStorageEnabled = from.domStorageEnabled;
            to.zoomEnabled = from.zoomEnabled;
            to.progressBarEnabled = from.progressBarEnabled;
            to.backgroundColor = from.backgroundColor;
        } else if (source instanceof Navigation && target instanceof Navigation) {
            Navigation from = (Navigation) source;
            Navigation to = (Navigation) target;
            to.statusBarVisible = from.statusBarVisible;
            to.navigationBarVisible = from.navigationBarVisible;
            to.fullscreen = from.fullscreen;
            to.immersive = from.immersive;
            to.edgeToEdge = from.edgeToEdge;
            to.backNavigationEnabled = from.backNavigationEnabled;
            to.statusBarColor = from.statusBarColor;
            to.navigationBarColor = from.navigationBarColor;
            to.lightStatusBar = from.lightStatusBar;
            to.lightNavigationBar = from.lightNavigationBar;
        }
    }

    // ==================== Serialization ====================

    /**
     * Encodes the whole configuration into a single URL-encoded string.
     *
     * <p>Format: {@code key=value&key=value…}. Component entries use the prefix
     * {@code c.<TYPE>} for the flag and {@code p.<TYPE>.<key>} for properties, so the format stays
     * flat and forward-compatible: unknown keys are ignored on decode.</p>
     */
    public String encode() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("v", String.valueOf(schemaVersion));
        map.put("preset", presetId);

        map.put("s.enabled", String.valueOf(splash.enabled));
        map.put("s.mode", splash.mode.getKey());
        map.put("s.duration", String.valueOf(splash.durationMs));
        map.put("s.logo", splash.logoText);
        map.put("s.logoDrawable", splash.logoDrawable);
        map.put("s.bg", String.valueOf(splash.backgroundColor));
        map.put("s.accent", String.valueOf(splash.accentColor));
        map.put("s.anim", splash.animation.getKey());
        map.put("s.fullscreen", String.valueOf(splash.fullscreen));
        map.put("s.progress", String.valueOf(splash.showProgress));
        map.put("s.skipTap", String.valueOf(splash.skipOnTap));

        map.put("d.enabled", String.valueOf(dashboard.enabled));
        map.put("d.mode", dashboard.mode.getKey());
        map.put("d.layout", dashboard.layout.getKey());
        map.put("d.toolbar", String.valueOf(dashboard.toolbarEnabled));
        map.put("d.drawer", String.valueOf(dashboard.drawerEnabled));
        map.put("d.bottomNav", String.valueOf(dashboard.bottomNavEnabled));
        map.put("d.fab", String.valueOf(dashboard.fabEnabled));
        map.put("d.fullscreen", String.valueOf(dashboard.fullscreen));
        map.put("d.stats", String.valueOf(dashboard.showStats));
        map.put("d.recent", String.valueOf(dashboard.showRecent));
        map.put("d.bg", String.valueOf(dashboard.backgroundColor));
        map.put("d.accent", String.valueOf(dashboard.accentColor));
        map.put("d.webSource", dashboard.webSource);

        map.put("t.mode", theme.mode.getKey());
        map.put("t.primary", String.valueOf(theme.primaryColor));
        map.put("t.bg", String.valueOf(theme.backgroundColor));
        map.put("t.accent", String.valueOf(theme.accentColor));
        map.put("t.text", String.valueOf(theme.textColor));
        map.put("t.fontScale", String.valueOf(theme.fontScale));
        map.put("t.anim", String.valueOf(theme.animationDurationMs));

        map.put("w.enabled", String.valueOf(webView.enabled));
        map.put("w.fullscreen", String.valueOf(webView.fullscreen));
        map.put("w.js", String.valueOf(webView.javaScriptEnabled));
        map.put("w.dom", String.valueOf(webView.domStorageEnabled));
        map.put("w.zoom", String.valueOf(webView.zoomEnabled));
        map.put("w.progress", String.valueOf(webView.progressBarEnabled));
        map.put("w.bg", String.valueOf(webView.backgroundColor));

        map.put("n.statusBar", String.valueOf(navigation.statusBarVisible));
        map.put("n.navBar", String.valueOf(navigation.navigationBarVisible));
        map.put("n.fullscreen", String.valueOf(navigation.fullscreen));
        map.put("n.immersive", String.valueOf(navigation.immersive));
        map.put("n.edgeToEdge", String.valueOf(navigation.edgeToEdge));
        map.put("n.back", String.valueOf(navigation.backNavigationEnabled));
        map.put("n.statusColor", String.valueOf(navigation.statusBarColor));
        map.put("n.navColor", String.valueOf(navigation.navigationBarColor));
        map.put("n.lightStatus", String.valueOf(navigation.lightStatusBar));
        map.put("n.lightNav", String.valueOf(navigation.lightNavigationBar));

        for (Map.Entry<ComponentType, Component> entry : components.entrySet()) {
            ComponentType type = entry.getKey();
            Component component = entry.getValue();
            map.put("c." + type.name(), String.valueOf(component.isEnabled()));
            for (Map.Entry<String, String> property : component.getProperties().entrySet()) {
                map.put("p." + type.name() + "." + property.getKey(), property.getValue());
            }
        }

        return encodeMap(map);
    }

    /** Decodes a configuration produced by {@link #encode()}; never returns {@code null}. */
    public static ProjectUIConfig decode(String raw) {
        ProjectUIConfig config = new ProjectUIConfig();
        if (raw == null || raw.trim().isEmpty()) {
            return config;
        }
        Map<String, String> map = decodeMap(raw);
        if (map.isEmpty()) {
            return config;
        }

        config.schemaVersion = intValue(map, "v", SCHEMA_VERSION);
        config.presetId = stringValue(map, "preset", "");

        config.splash.enabled = boolValue(map, "s.enabled", true);
        config.splash.mode = SplashMode.fromKey(stringValue(map, "s.mode", "NATIVE"));
        config.splash.setDurationMs(intValue(map, "s.duration", Splash.DEFAULT_DURATION_MS));
        config.splash.logoText = stringValue(map, "s.logo", "");
        config.splash.logoDrawable = stringValue(map, "s.logoDrawable", "");
        config.splash.backgroundColor = intValue(map, "s.bg", 0xFF080D1B);
        config.splash.accentColor = intValue(map, "s.accent", 0xFF6C5CE7);
        config.splash.animation = SplashAnimation.fromKey(stringValue(map, "s.anim", "FADE"));
        config.splash.fullscreen = boolValue(map, "s.fullscreen", true);
        config.splash.showProgress = boolValue(map, "s.progress", true);
        config.splash.skipOnTap = boolValue(map, "s.skipTap", true);

        config.dashboard.enabled = boolValue(map, "d.enabled", true);
        config.dashboard.mode = DashboardMode.fromKey(stringValue(map, "d.mode", "NATIVE"));
        config.dashboard.layout = DashboardLayout.fromKey(stringValue(map, "d.layout", "STANDARD"));
        config.dashboard.toolbarEnabled = boolValue(map, "d.toolbar", true);
        config.dashboard.drawerEnabled = boolValue(map, "d.drawer", false);
        config.dashboard.bottomNavEnabled = boolValue(map, "d.bottomNav", false);
        config.dashboard.fabEnabled = boolValue(map, "d.fab", true);
        config.dashboard.fullscreen = boolValue(map, "d.fullscreen", false);
        config.dashboard.showStats = boolValue(map, "d.stats", true);
        config.dashboard.showRecent = boolValue(map, "d.recent", true);
        config.dashboard.backgroundColor = intValue(map, "d.bg", 0);
        config.dashboard.accentColor = intValue(map, "d.accent", 0);
        config.dashboard.webSource = stringValue(map, "d.webSource", "");

        config.theme.mode = ThemeMode.fromKey(stringValue(map, "t.mode", "SYSTEM"));
        config.theme.primaryColor = intValue(map, "t.primary", 0xFF6C5CE7);
        config.theme.backgroundColor = intValue(map, "t.bg", 0);
        config.theme.accentColor = intValue(map, "t.accent", 0xFF00B894);
        config.theme.textColor = intValue(map, "t.text", 0);
        config.theme.setFontScale(floatValue(map, "t.fontScale", 1.0f));
        config.theme.setAnimationDurationMs(intValue(map, "t.anim", 300));

        config.webView.enabled = boolValue(map, "w.enabled", true);
        config.webView.fullscreen = boolValue(map, "w.fullscreen", true);
        config.webView.javaScriptEnabled = boolValue(map, "w.js", true);
        config.webView.domStorageEnabled = boolValue(map, "w.dom", true);
        config.webView.zoomEnabled = boolValue(map, "w.zoom", false);
        config.webView.progressBarEnabled = boolValue(map, "w.progress", false);
        config.webView.backgroundColor = intValue(map, "w.bg", 0);

        config.navigation.statusBarVisible = boolValue(map, "n.statusBar", true);
        config.navigation.navigationBarVisible = boolValue(map, "n.navBar", true);
        config.navigation.fullscreen = boolValue(map, "n.fullscreen", false);
        config.navigation.immersive = boolValue(map, "n.immersive", false);
        config.navigation.edgeToEdge = boolValue(map, "n.edgeToEdge", true);
        config.navigation.backNavigationEnabled = boolValue(map, "n.back", true);
        config.navigation.statusBarColor = intValue(map, "n.statusColor", 0);
        config.navigation.navigationBarColor = intValue(map, "n.navColor", 0);
        config.navigation.lightStatusBar = boolValue(map, "n.lightStatus", false);
        config.navigation.lightNavigationBar = boolValue(map, "n.lightNav", false);

        for (ComponentType type : ComponentType.values()) {
            String flag = map.get("c." + type.name());
            if (flag != null) {
                config.component(type).setEnabled(parseBool(flag, false));
            }
        }
        String propertyPrefix = "p.";
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(propertyPrefix)) {
                continue;
            }
            String remainder = key.substring(propertyPrefix.length());
            int separator = remainder.indexOf('.');
            if (separator <= 0) {
                continue;
            }
            ComponentType type = componentTypeOf(remainder.substring(0, separator));
            if (type == null) {
                continue;
            }
            config.component(type).setProperty(remainder.substring(separator + 1), entry.getValue());
        }

        return config;
    }

    private static ComponentType componentTypeOf(String name) {
        for (ComponentType type : ComponentType.values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }

    // ==================== Codec helpers ====================

    private static String encodeMap(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(escape(entry.getKey())).append('=').append(escape(entry.getValue()));
        }
        return builder.toString();
    }

    private static Map<String, String> decodeMap(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            result.put(unescape(key), unescape(value));
        }
        return result;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException error) {
            return value;
        }
    }

    private static String unescape(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception error) {
            return value;
        }
    }

    private static String stringValue(Map<String, String> map, String key, String fallback) {
        String value = map.get(key);
        return value != null ? value : fallback;
    }

    private static boolean boolValue(Map<String, String> map, String key, boolean fallback) {
        String value = map.get(key);
        return value != null ? parseBool(value, fallback) : fallback;
    }

    private static boolean parseBool(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static int intValue(Map<String, String> map, String key, int fallback) {
        String value = map.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static float floatValue(Map<String, String> map, String key, float fallback) {
        String value = map.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return "ProjectUIConfig{preset='" + presetId + "', splash=" + splash.mode
                + "/" + splash.enabled + ", dashboard=" + dashboard.mode
                + "/" + dashboard.enabled + ", components=" + getEnabledComponentCount() + "}";
    }
}
