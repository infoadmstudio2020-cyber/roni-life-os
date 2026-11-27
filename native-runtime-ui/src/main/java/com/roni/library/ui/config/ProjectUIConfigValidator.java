package com.roni.library.ui.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.ui.manager.ComponentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates a {@link ProjectUIConfig} before it is persisted or rendered.
 *
 * <p>Validation is advisory: every problem reported here has a defined fallback in the mapper, so
 * an invalid configuration still starts the app. The editor surfaces the list to the user; the
 * runtime only logs it.</p>
 */
public final class ProjectUIConfigValidator {

    /** Severity of a validation finding. */
    public enum Severity {
        /** The configuration will not behave as the user expects. */
        ERROR,
        /** The configuration works but is probably a mistake. */
        WARNING
    }

    /** One validation finding. */
    public static final class Issue {
        private final Severity severity;
        private final String field;
        private final String message;

        Issue(Severity severity, String field, String message) {
            this.severity = severity;
            this.field = field;
            this.message = message;
        }

        public Severity getSeverity() {
            return severity;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }

        @NonNull
        @Override
        public String toString() {
            return severity + " · " + field + ": " + message;
        }
    }

    private ProjectUIConfigValidator() {
    }

    /** Returns every finding for {@code config}; empty when the configuration is sound. */
    @NonNull
    public static List<Issue> validate(@Nullable ProjectUIConfig config) {
        List<Issue> issues = new ArrayList<>();
        if (config == null) {
            issues.add(new Issue(Severity.ERROR, "config", "Configuration is missing."));
            return Collections.unmodifiableList(issues);
        }

        validateSplash(config, issues);
        validateDashboard(config, issues);
        validateComponents(config, issues);
        validateNavigation(config, issues);
        validateTheme(config, issues);

        return Collections.unmodifiableList(issues);
    }

    /** True when no {@link Severity#ERROR} finding exists. */
    public static boolean isValid(@Nullable ProjectUIConfig config) {
        for (Issue issue : validate(config)) {
            if (issue.getSeverity() == Severity.ERROR) {
                return false;
            }
        }
        return true;
    }

    /** First error message, or an empty string when the configuration is valid. */
    @NonNull
    public static String firstError(@Nullable ProjectUIConfig config) {
        for (Issue issue : validate(config)) {
            if (issue.getSeverity() == Severity.ERROR) {
                return issue.getMessage();
            }
        }
        return "";
    }

    // ==================== Rules ====================

    private static void validateSplash(ProjectUIConfig config, List<Issue> issues) {
        ProjectUIConfig.Splash splash = config.getSplash();
        if (!splash.isEnabled()) {
            return;
        }
        if (splash.getMode() == ProjectUIConfig.SplashMode.NONE) {
            issues.add(new Issue(Severity.WARNING, "splash.mode",
                    "Splash is enabled but its mode is NONE — it will be skipped."));
        }
        if (splash.getDurationMs() < ProjectUIConfig.Splash.MIN_DURATION_MS
                || splash.getDurationMs() > ProjectUIConfig.Splash.MAX_DURATION_MS) {
            issues.add(new Issue(Severity.ERROR, "splash.duration",
                    "Splash duration must be between "
                            + ProjectUIConfig.Splash.MIN_DURATION_MS + " and "
                            + ProjectUIConfig.Splash.MAX_DURATION_MS + " ms."));
        }
        if (splash.getMode() == ProjectUIConfig.SplashMode.WEB
                && splash.getLogoDrawable().isEmpty()
                && splash.getLogoText().isEmpty()) {
            issues.add(new Issue(Severity.WARNING, "splash.logo",
                    "Web splash has no logo text or drawable — only the background will show."));
        }
    }

    private static void validateDashboard(ProjectUIConfig config, List<Issue> issues) {
        ProjectUIConfig.Dashboard dashboard = config.getDashboard();
        if (!dashboard.isEnabled()) {
            return;
        }
        if (dashboard.getMode() == ProjectUIConfig.DashboardMode.WEB
                && dashboard.getWebSource().trim().isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "dashboard.webSource",
                    "Web dashboard is selected but no web source is configured."));
        }
        if (dashboard.isDrawerEnabled() && !config.isEnabled(ComponentType.DRAWER)) {
            issues.add(new Issue(Severity.WARNING, "dashboard.drawer",
                    "Drawer is enabled on the dashboard but the DRAWER component is off."));
        }
        if (dashboard.isBottomNavEnabled() && !config.isEnabled(ComponentType.BOTTOM_NAV)) {
            issues.add(new Issue(Severity.WARNING, "dashboard.bottomNav",
                    "Bottom navigation is enabled on the dashboard but the BOTTOM_NAV "
                            + "component is off."));
        }
        if (dashboard.isToolbarEnabled() && !config.isEnabled(ComponentType.TOOLBAR)) {
            issues.add(new Issue(Severity.WARNING, "dashboard.toolbar",
                    "Toolbar is enabled on the dashboard but the TOOLBAR component is off."));
        }
        if (dashboard.isFabEnabled() && !config.isEnabled(ComponentType.FAB)) {
            issues.add(new Issue(Severity.WARNING, "dashboard.fab",
                    "FAB is enabled on the dashboard but the FAB component is off."));
        }
    }

    private static void validateComponents(ProjectUIConfig config, List<Issue> issues) {
        if (config.getEnabledComponentCount() == 0) {
            issues.add(new Issue(Severity.ERROR, "components",
                    "No component is enabled — the project would render an empty screen."));
        }
        if (config.isEnabled(ComponentType.WEB_VIEW) && !config.getWebView().isEnabled()) {
            issues.add(new Issue(Severity.WARNING, "webView",
                    "WEB_VIEW component is on but the WebView container is disabled."));
        }
        if (config.isEnabled(ComponentType.BOTTOM_NAV)
                && config.isEnabled(ComponentType.NAVIGATION_RAIL)) {
            issues.add(new Issue(Severity.WARNING, "components",
                    "Bottom navigation and navigation rail are both enabled — only one should "
                            + "be used per layout."));
        }
        if (config.isEnabled(ComponentType.TAB_LAYOUT)
                && config.isEnabled(ComponentType.BOTTOM_NAV)) {
            issues.add(new Issue(Severity.WARNING, "components",
                    "Tabs and bottom navigation are both enabled — this usually duplicates "
                            + "navigation."));
        }
    }

    private static void validateNavigation(ProjectUIConfig config, List<Issue> issues) {
        ProjectUIConfig.Navigation navigation = config.getNavigation();
        if (navigation.isFullscreen() && navigation.isStatusBarVisible()
                && config.isEnabled(ComponentType.STATUS_BAR)) {
            issues.add(new Issue(Severity.WARNING, "navigation",
                    "Fullscreen is on while the status bar is visible — the status bar will be "
                            + "hidden at runtime."));
        }
        if (!navigation.isBackNavigationEnabled()
                && config.isEnabled(ComponentType.BACK_NAVIGATION)) {
            issues.add(new Issue(Severity.WARNING, "navigation.back",
                    "Back navigation is disabled in navigation settings but the "
                            + "BACK_NAVIGATION component is on."));
        }
        if (navigation.isImmersive() && !config.isEnabled(ComponentType.FULLSCREEN_IMMERSIVE)) {
            issues.add(new Issue(Severity.WARNING, "navigation.immersive",
                    "Immersive mode is requested but the FULLSCREEN_IMMERSIVE component is off."));
        }
    }

    private static void validateTheme(ProjectUIConfig config, List<Issue> issues) {
        ProjectUIConfig.Theme theme = config.getTheme();
        if (theme.getFontScale() < 0.75f || theme.getFontScale() > 1.6f) {
            issues.add(new Issue(Severity.ERROR, "theme.fontScale",
                    "Font scale must stay between 0.75 and 1.6."));
        }
        if (theme.getAnimationDurationMs() < 0 || theme.getAnimationDurationMs() > 2000) {
            issues.add(new Issue(Severity.ERROR, "theme.animation",
                    "Animation duration must stay between 0 and 2000 ms."));
        }
    }
}
