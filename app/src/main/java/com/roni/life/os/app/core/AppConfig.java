package com.roni.life.os.app.core;

import com.roni.life.os.app.util.Codec;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Main Application-এর গ্লোবাল স্টার্টআপ কনফিগারেশন।
 *
 * <p>এখানে থাকা Splash ও Bootstrap ফ্ল্যাগ শুধুমাত্র <b>Main App</b> স্তরের।
 * প্রতিটি প্রজেক্টের নিজস্ব ফ্ল্যাগ {@code Project}-এ সংরক্ষিত থাকে।</p>
 */
public final class AppConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_SPLASH_DURATION_MS = 1600;
    public static final int MIN_SPLASH_DURATION_MS = 400;
    public static final int MAX_SPLASH_DURATION_MS = 6000;

    /** সম্পূর্ণ নেটিভ (Java) ড্যাশবোর্ড — ডিফল্ট এবং authoritative। */
    public static final String DASHBOARD_NATIVE = "NATIVE_DASHBOARD";

    /** WebView-ভিত্তিক ড্যাশবোর্ড — শুধুমাত্র অনুমোদিত bridge API ব্যবহার করে। */
    public static final String DASHBOARD_WEB = "WEB_DASHBOARD";

    private boolean splashEnabled = true;
    private boolean bootstrapEnabled = true;
    private int splashDurationMs = DEFAULT_SPLASH_DURATION_MS;
    private String defaultProjectId = "";
    private String lastOpenedProjectId = "";
    /** Explicitly selected SPLASH project. Empty means "resolve by policy". */
    private String activeSplashProjectId = "";
    /** Explicitly selected BOOTSTRAP project. Empty means "resolve by policy". */
    private String activeBootstrapProjectId = "";
    /** Explicitly selected default WEB application project. */
    private String defaultWebProjectId = "";
    private boolean telemetryEnabled = true;
    private boolean debugEnabled = false;
    private String themeMode = "SYSTEM";
    private String dashboardMode = DASHBOARD_NATIVE;
    private boolean seeded = false;

    // ==================== Splash presentation ====================

    /** Native Android splash rendered from XML. */
    public static final String SPLASH_NATIVE = "NATIVE";

    /** Splash rendered from the resolved web source ({@code system/splash}) inside a WebView. */
    public static final String SPLASH_WEB = "WEB";

    /** No splash at all — the app routes straight to its destination. */
    public static final String SPLASH_NONE = "NONE";

    /**
     * Version of the splash presentation defaults.
     *
     * <p>Records written before v1.3.1 carry no {@code splashModeVersion} key, so they migrate to
     * {@link #SPLASH_WEB} exactly once. After that the stored mode is the user's own choice and is
     * never overridden again.</p>
     */
    public static final int SPLASH_MODE_VERSION = 1;

    public static final String ANIM_NONE = "NONE";
    public static final String ANIM_FADE = "FADE";
    public static final String ANIM_SLIDE_UP = "SLIDE_UP";
    public static final String ANIM_SCALE = "SCALE";
    public static final String ANIM_PULSE = "PULSE";

    private String splashMode = SPLASH_WEB;
    private String splashLogoText = "";
    private String splashLogoDrawable = "";
    private int splashBackgroundColor = 0xFF080D1B;
    private int splashAccentColor = 0xFF6C5CE7;
    private String splashAnimation = ANIM_FADE;
    private boolean splashFullscreen = true;
    private boolean splashSkipOnTap = true;

    // ==================== Dashboard presentation ====================

    private boolean dashboardEnabled = true;
    private boolean dashboardToolbarEnabled = true;
    private boolean dashboardFabEnabled = true;
    private boolean dashboardShowStats = true;
    private boolean dashboardShowRecent = true;
    private boolean dashboardFullscreen = false;
    private int dashboardBackgroundColor = 0;
    private int dashboardAccentColor = 0;

    public boolean isSplashEnabled() {
        return splashEnabled;
    }

    public void setSplashEnabled(boolean splashEnabled) {
        this.splashEnabled = splashEnabled;
    }

    public boolean isBootstrapEnabled() {
        return bootstrapEnabled;
    }

    public void setBootstrapEnabled(boolean bootstrapEnabled) {
        this.bootstrapEnabled = bootstrapEnabled;
    }

    public int getSplashDurationMs() {
        return splashDurationMs;
    }

    public void setSplashDurationMs(int splashDurationMs) {
        this.splashDurationMs = Math.max(MIN_SPLASH_DURATION_MS,
                Math.min(MAX_SPLASH_DURATION_MS, splashDurationMs));
    }

    public String getDefaultProjectId() {
        return defaultProjectId;
    }

    public void setDefaultProjectId(String defaultProjectId) {
        this.defaultProjectId = defaultProjectId != null ? defaultProjectId : "";
    }

    public String getLastOpenedProjectId() {
        return lastOpenedProjectId;
    }

    public void setLastOpenedProjectId(String lastOpenedProjectId) {
        this.lastOpenedProjectId = lastOpenedProjectId != null ? lastOpenedProjectId : "";
    }

    // ==================== Role selection ====================

    private String activeWorkspaceProjectId = "";

    public String getActiveSplashProjectId() {
        return activeSplashProjectId;
    }

    public void setActiveSplashProjectId(String id) {
        this.activeSplashProjectId = id != null ? id : "";
    }

    public String getActiveBootstrapProjectId() {
        return activeBootstrapProjectId;
    }

    public void setActiveBootstrapProjectId(String id) {
        this.activeBootstrapProjectId = id != null ? id : "";
    }

    /** The single active Workspace project (a live folder reference). */
    public String getActiveWorkspaceProjectId() {
        return activeWorkspaceProjectId;
    }

    public void setActiveWorkspaceProjectId(String id) {
        this.activeWorkspaceProjectId = id != null ? id : "";
    }

    public String getDefaultWebProjectId() {
        return defaultWebProjectId;
    }

    public void setDefaultWebProjectId(String id) {
        this.defaultWebProjectId = id != null ? id : "";
    }

    public boolean isTelemetryEnabled() {
        return telemetryEnabled;
    }

    public void setTelemetryEnabled(boolean telemetryEnabled) {
        this.telemetryEnabled = telemetryEnabled;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public String getThemeMode() {
        return themeMode;
    }

    public void setThemeMode(String themeMode) {
        this.themeMode = themeMode != null ? themeMode : "SYSTEM";
    }

    public String getDashboardMode() {
        return dashboardMode;
    }

    public void setDashboardMode(String dashboardMode) {
        this.dashboardMode = DASHBOARD_WEB.equalsIgnoreCase(dashboardMode)
                ? DASHBOARD_WEB : DASHBOARD_NATIVE;
    }

    public boolean isWebDashboard() {
        return DASHBOARD_WEB.equals(dashboardMode);
    }

    public boolean isSeeded() {
        return seeded;
    }

    public void setSeeded(boolean seeded) {
        this.seeded = seeded;
    }

    // ==================== Splash accessors ====================

    public String getSplashMode() {
        return splashMode;
    }

    public void setSplashMode(String splashMode) {
        if (SPLASH_WEB.equalsIgnoreCase(splashMode)) {
            this.splashMode = SPLASH_WEB;
        } else if (SPLASH_NONE.equalsIgnoreCase(splashMode)) {
            this.splashMode = SPLASH_NONE;
        } else {
            this.splashMode = SPLASH_NATIVE;
        }
    }

    /** True when a splash should actually be rendered. */
    public boolean shouldShowSplash() {
        return splashEnabled && !SPLASH_NONE.equals(splashMode);
    }

    public boolean isWebSplash() {
        return SPLASH_WEB.equals(splashMode);
    }

    public String getSplashLogoText() {
        return splashLogoText;
    }

    public void setSplashLogoText(String splashLogoText) {
        this.splashLogoText = splashLogoText != null ? splashLogoText : "";
    }

    public String getSplashLogoDrawable() {
        return splashLogoDrawable;
    }

    public void setSplashLogoDrawable(String splashLogoDrawable) {
        this.splashLogoDrawable = splashLogoDrawable != null ? splashLogoDrawable : "";
    }

    public int getSplashBackgroundColor() {
        return splashBackgroundColor;
    }

    public void setSplashBackgroundColor(int splashBackgroundColor) {
        this.splashBackgroundColor = splashBackgroundColor;
    }

    public int getSplashAccentColor() {
        return splashAccentColor;
    }

    public void setSplashAccentColor(int splashAccentColor) {
        this.splashAccentColor = splashAccentColor;
    }

    public String getSplashAnimation() {
        return splashAnimation;
    }

    public void setSplashAnimation(String splashAnimation) {
        this.splashAnimation = splashAnimation != null ? splashAnimation : ANIM_FADE;
    }

    public boolean isSplashFullscreen() {
        return splashFullscreen;
    }

    public void setSplashFullscreen(boolean splashFullscreen) {
        this.splashFullscreen = splashFullscreen;
    }

    public boolean isSplashSkipOnTap() {
        return splashSkipOnTap;
    }

    public void setSplashSkipOnTap(boolean splashSkipOnTap) {
        this.splashSkipOnTap = splashSkipOnTap;
    }

    // ==================== Dashboard accessors ====================

    public boolean isDashboardEnabled() {
        return dashboardEnabled;
    }

    public void setDashboardEnabled(boolean dashboardEnabled) {
        this.dashboardEnabled = dashboardEnabled;
    }

    public boolean isDashboardToolbarEnabled() {
        return dashboardToolbarEnabled;
    }

    public void setDashboardToolbarEnabled(boolean dashboardToolbarEnabled) {
        this.dashboardToolbarEnabled = dashboardToolbarEnabled;
    }

    public boolean isDashboardFabEnabled() {
        return dashboardFabEnabled;
    }

    public void setDashboardFabEnabled(boolean dashboardFabEnabled) {
        this.dashboardFabEnabled = dashboardFabEnabled;
    }

    public boolean isDashboardShowStats() {
        return dashboardShowStats;
    }

    public void setDashboardShowStats(boolean dashboardShowStats) {
        this.dashboardShowStats = dashboardShowStats;
    }

    public boolean isDashboardShowRecent() {
        return dashboardShowRecent;
    }

    public void setDashboardShowRecent(boolean dashboardShowRecent) {
        this.dashboardShowRecent = dashboardShowRecent;
    }

    public boolean isDashboardFullscreen() {
        return dashboardFullscreen;
    }

    public void setDashboardFullscreen(boolean dashboardFullscreen) {
        this.dashboardFullscreen = dashboardFullscreen;
    }

    public int getDashboardBackgroundColor() {
        return dashboardBackgroundColor;
    }

    public void setDashboardBackgroundColor(int dashboardBackgroundColor) {
        this.dashboardBackgroundColor = dashboardBackgroundColor;
    }

    public int getDashboardAccentColor() {
        return dashboardAccentColor;
    }

    public void setDashboardAccentColor(int dashboardAccentColor) {
        this.dashboardAccentColor = dashboardAccentColor;
    }

    public String encode() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("splashEnabled", String.valueOf(splashEnabled));
        map.put("bootstrapEnabled", String.valueOf(bootstrapEnabled));
        map.put("splashDurationMs", String.valueOf(splashDurationMs));
        map.put("defaultProjectId", defaultProjectId);
        map.put("lastOpenedProjectId", lastOpenedProjectId);
        map.put("activeSplashProjectId", activeSplashProjectId);
        map.put("activeBootstrapProjectId", activeBootstrapProjectId);
        map.put("activeWorkspaceProjectId", activeWorkspaceProjectId);
        map.put("defaultWebProjectId", defaultWebProjectId);
        map.put("telemetryEnabled", String.valueOf(telemetryEnabled));
        map.put("debugEnabled", String.valueOf(debugEnabled));
        map.put("themeMode", themeMode);
        map.put("dashboardMode", dashboardMode);
        map.put("seeded", String.valueOf(seeded));
        map.put("splashMode", splashMode);
        map.put("splashModeVersion", String.valueOf(SPLASH_MODE_VERSION));
        map.put("splashLogoText", splashLogoText);
        map.put("splashLogoDrawable", splashLogoDrawable);
        map.put("splashBackgroundColor", String.valueOf(splashBackgroundColor));
        map.put("splashAccentColor", String.valueOf(splashAccentColor));
        map.put("splashAnimation", splashAnimation);
        map.put("splashFullscreen", String.valueOf(splashFullscreen));
        map.put("splashSkipOnTap", String.valueOf(splashSkipOnTap));
        map.put("dashboardEnabled", String.valueOf(dashboardEnabled));
        map.put("dashboardToolbarEnabled", String.valueOf(dashboardToolbarEnabled));
        map.put("dashboardFabEnabled", String.valueOf(dashboardFabEnabled));
        map.put("dashboardShowStats", String.valueOf(dashboardShowStats));
        map.put("dashboardShowRecent", String.valueOf(dashboardShowRecent));
        map.put("dashboardFullscreen", String.valueOf(dashboardFullscreen));
        map.put("dashboardBackgroundColor", String.valueOf(dashboardBackgroundColor));
        map.put("dashboardAccentColor", String.valueOf(dashboardAccentColor));
        return Codec.encode(map);
    }

    public static AppConfig decode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, String> map = Codec.decode(raw);
        if (map.isEmpty() || !map.containsKey("splashEnabled")) {
            // বৈধ কনফিগে splashEnabled থাকতেই হবে।
            return null;
        }
        AppConfig config = new AppConfig();
        config.splashEnabled = Codec.bool(map, "splashEnabled", true);
        config.bootstrapEnabled = Codec.bool(map, "bootstrapEnabled", true);
        config.splashDurationMs = Codec.integer(map, "splashDurationMs", DEFAULT_SPLASH_DURATION_MS);
        config.defaultProjectId = Codec.str(map, "defaultProjectId", "");
        config.lastOpenedProjectId = Codec.str(map, "lastOpenedProjectId", "");
        config.activeSplashProjectId = Codec.str(map, "activeSplashProjectId", "");
        config.activeBootstrapProjectId = Codec.str(map, "activeBootstrapProjectId", "");
        config.activeWorkspaceProjectId = Codec.str(map, "activeWorkspaceProjectId", "");
        config.defaultWebProjectId = Codec.str(map, "defaultWebProjectId", "");
        config.telemetryEnabled = Codec.bool(map, "telemetryEnabled", true);
        config.debugEnabled = Codec.bool(map, "debugEnabled", false);
        config.themeMode = Codec.str(map, "themeMode", "SYSTEM");
        config.setDashboardMode(Codec.str(map, "dashboardMode", DASHBOARD_NATIVE));
        config.seeded = Codec.bool(map, "seeded", false);

        // Splash presentation — every key is optional so older records still decode.
        // A record without splashModeVersion predates the bundled web splash, so it migrates to
        // SPLASH_WEB once; later records keep whatever mode the user selected.
        boolean legacySplashMode = !map.containsKey("splashModeVersion");
        config.setSplashMode(Codec.str(map, "splashMode", SPLASH_WEB));
        if (legacySplashMode) {
            config.splashMode = SPLASH_WEB;
        }
        config.splashLogoText = Codec.str(map, "splashLogoText", "");
        config.splashLogoDrawable = Codec.str(map, "splashLogoDrawable", "");
        config.splashBackgroundColor = Codec.integer(map, "splashBackgroundColor", 0xFF080D1B);
        config.splashAccentColor = Codec.integer(map, "splashAccentColor", 0xFF6C5CE7);
        config.setSplashAnimation(Codec.str(map, "splashAnimation", ANIM_FADE));
        config.splashFullscreen = Codec.bool(map, "splashFullscreen", true);
        config.splashSkipOnTap = Codec.bool(map, "splashSkipOnTap", true);

        // Dashboard presentation.
        config.dashboardEnabled = Codec.bool(map, "dashboardEnabled", true);
        config.dashboardToolbarEnabled = Codec.bool(map, "dashboardToolbarEnabled", true);
        config.dashboardFabEnabled = Codec.bool(map, "dashboardFabEnabled", true);
        config.dashboardShowStats = Codec.bool(map, "dashboardShowStats", true);
        config.dashboardShowRecent = Codec.bool(map, "dashboardShowRecent", true);
        config.dashboardFullscreen = Codec.bool(map, "dashboardFullscreen", false);
        config.dashboardBackgroundColor = Codec.integer(map, "dashboardBackgroundColor", 0);
        config.dashboardAccentColor = Codec.integer(map, "dashboardAccentColor", 0);

        return config;
    }

    @Override
    public String toString() {
        return "AppConfig{splash=" + splashEnabled + ", bootstrap=" + bootstrapEnabled
                + ", duration=" + splashDurationMs + "ms, dashboard=" + dashboardMode + "}";
    }
}
