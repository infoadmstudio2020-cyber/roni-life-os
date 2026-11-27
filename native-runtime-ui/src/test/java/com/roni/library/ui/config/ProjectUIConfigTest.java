package com.roni.library.ui.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.roni.library.ui.manager.ComponentType;

import org.junit.Test;

/**
 * Unit tests for the per-project UI configuration model.
 *
 * <p>{@link ProjectUIConfig} deliberately has no Android dependencies, so these tests run on a
 * plain JVM without Robolectric.</p>
 */
public class ProjectUIConfigTest {

    @Test
    public void defaultsEnableOnlyTheWebShell() {
        ProjectUIConfig config = new ProjectUIConfig();

        assertTrue(config.isEnabled(ComponentType.STATUS_BAR));
        assertTrue(config.isEnabled(ComponentType.WEB_VIEW));
        assertTrue(config.isEnabled(ComponentType.BACK_NAVIGATION));

        assertFalse(config.isEnabled(ComponentType.TOOLBAR));
        assertFalse(config.isEnabled(ComponentType.DRAWER));
        assertFalse(config.isEnabled(ComponentType.BOTTOM_NAV));
        assertFalse(config.isEnabled(ComponentType.FAB));
        assertFalse(config.isEnabled(ComponentType.NAVIGATION_RAIL));
        assertFalse(config.isEnabled(ComponentType.TAB_LAYOUT));
    }

    @Test
    public void defaultsAreSplashOnAndDashboardOn() {
        ProjectUIConfig config = new ProjectUIConfig();

        assertTrue(config.getSplash().isEnabled());
        assertEquals(ProjectUIConfig.SplashMode.NATIVE, config.getSplash().getMode());
        assertTrue(config.getDashboard().isEnabled());
        assertEquals(ProjectUIConfig.DashboardMode.NATIVE, config.getDashboard().getMode());
    }

    @Test
    public void encodeDecodeRoundTripPreservesEveryField() {
        ProjectUIConfig original = new ProjectUIConfig();
        original.setPresetId("dashboard_full");
        original.getSplash()
                .setEnabled(true)
                .setMode(ProjectUIConfig.SplashMode.WEB)
                .setDurationMs(2500)
                .setLogoText("RONi LiFE")
                .setLogoDrawable("logo_splash")
                .setBackgroundColor(0xFF102030)
                .setAccentColor(0xFFAABBCC)
                .setAnimation(ProjectUIConfig.SplashAnimation.SLIDE_UP)
                .setFullscreen(false)
                .setShowProgress(false)
                .setSkipOnTap(false);
        original.getDashboard()
                .setEnabled(false)
                .setMode(ProjectUIConfig.DashboardMode.WEB)
                .setLayout(ProjectUIConfig.DashboardLayout.GRID)
                .setToolbarEnabled(false)
                .setDrawerEnabled(true)
                .setBottomNavEnabled(true)
                .setFabEnabled(false)
                .setFullscreen(true)
                .setShowStats(false)
                .setShowRecent(false)
                .setWebSource("https://example.test/dashboard");
        original.getTheme()
                .setMode(ProjectUIConfig.ThemeMode.DARK)
                .setPrimaryColor(0xFF112233)
                .setAccentColor(0xFF445566)
                .setFontScale(1.25f)
                .setAnimationDurationMs(450);
        original.getWebView()
                .setEnabled(false)
                .setFullscreen(false)
                .setJavaScriptEnabled(false)
                .setDomStorageEnabled(false)
                .setZoomEnabled(true)
                .setProgressBarEnabled(true);
        original.getNavigation()
                .setStatusBarVisible(false)
                .setNavigationBarVisible(false)
                .setFullscreen(true)
                .setImmersive(true)
                .setEdgeToEdge(false)
                .setBackNavigationEnabled(false)
                .setLightStatusBar(true)
                .setLightNavigationBar(true);
        original.setEnabled(ComponentType.TOOLBAR, true);
        original.setEnabled(ComponentType.DRAWER, true);
        original.component(ComponentType.TOOLBAR).setProperty("height", "56");
        original.component(ComponentType.TOOLBAR).setProperty("background", "#123456");

        ProjectUIConfig restored = ProjectUIConfig.decode(original.encode());

        assertEquals("dashboard_full", restored.getPresetId());
        assertEquals(ProjectUIConfig.SplashMode.WEB, restored.getSplash().getMode());
        assertEquals(2500, restored.getSplash().getDurationMs());
        assertEquals("RONi LiFE", restored.getSplash().getLogoText());
        assertEquals("logo_splash", restored.getSplash().getLogoDrawable());
        assertEquals(0xFF102030, restored.getSplash().getBackgroundColor());
        assertEquals(ProjectUIConfig.SplashAnimation.SLIDE_UP,
                restored.getSplash().getAnimation());
        assertFalse(restored.getSplash().isFullscreen());
        assertFalse(restored.getSplash().isSkipOnTap());

        assertFalse(restored.getDashboard().isEnabled());
        assertEquals(ProjectUIConfig.DashboardMode.WEB, restored.getDashboard().getMode());
        assertEquals(ProjectUIConfig.DashboardLayout.GRID, restored.getDashboard().getLayout());
        assertTrue(restored.getDashboard().isDrawerEnabled());
        assertEquals("https://example.test/dashboard",
                restored.getDashboard().getWebSource());

        assertEquals(ProjectUIConfig.ThemeMode.DARK, restored.getTheme().getMode());
        assertEquals(0xFF112233, restored.getTheme().getPrimaryColor());
        assertEquals(1.25f, restored.getTheme().getFontScale(), 0.001f);
        assertEquals(450, restored.getTheme().getAnimationDurationMs());

        assertFalse(restored.getWebView().isEnabled());
        assertTrue(restored.getWebView().isZoomEnabled());

        assertFalse(restored.getNavigation().isStatusBarVisible());
        assertTrue(restored.getNavigation().isImmersive());
        assertTrue(restored.getNavigation().isLightStatusBar());

        assertTrue(restored.isEnabled(ComponentType.TOOLBAR));
        assertTrue(restored.isEnabled(ComponentType.DRAWER));
        assertEquals("56", restored.component(ComponentType.TOOLBAR)
                .getProperty("height", ""));
        assertEquals("#123456", restored.component(ComponentType.TOOLBAR)
                .getProperty("background", ""));
    }

    @Test
    public void decodeOfEmptyOrNullReturnsDefaults() {
        assertNotNull(ProjectUIConfig.decode(null));
        assertNotNull(ProjectUIConfig.decode(""));
        assertTrue(ProjectUIConfig.decode(null).isEnabled(ComponentType.STATUS_BAR));
    }

    @Test
    public void decodeIgnoresUnknownKeys() {
        ProjectUIConfig config = ProjectUIConfig.decode(
                "v=1&futureKey=whatever&c.TOOLBAR=true&p.TOOLBAR.unknown=42");

        assertTrue(config.isEnabled(ComponentType.TOOLBAR));
        assertEquals("42", config.component(ComponentType.TOOLBAR)
                .getProperty("unknown", ""));
    }

    @Test
    public void splashDurationIsClampedToTheDocumentedRange() {
        ProjectUIConfig.Splash splash = new ProjectUIConfig.Splash();

        splash.setDurationMs(1);
        assertEquals(ProjectUIConfig.Splash.MIN_DURATION_MS, splash.getDurationMs());

        splash.setDurationMs(999999);
        assertEquals(ProjectUIConfig.Splash.MAX_DURATION_MS, splash.getDurationMs());
    }

    @Test
    public void fontScaleAndAnimationAreClamped() {
        ProjectUIConfig.Theme theme = new ProjectUIConfig.Theme();

        theme.setFontScale(0.1f);
        assertEquals(0.75f, theme.getFontScale(), 0.001f);

        theme.setFontScale(9f);
        assertEquals(1.6f, theme.getFontScale(), 0.001f);

        theme.setAnimationDurationMs(-50);
        assertEquals(0, theme.getAnimationDurationMs());

        theme.setAnimationDurationMs(99999);
        assertEquals(2000, theme.getAnimationDurationMs());
    }

    @Test
    public void copyIsIndependentOfTheOriginal() {
        ProjectUIConfig original = new ProjectUIConfig();
        original.setEnabled(ComponentType.TOOLBAR, true);
        original.getSplash().setLogoText("original");

        ProjectUIConfig copy = original.copy();
        copy.setEnabled(ComponentType.TOOLBAR, false);
        copy.getSplash().setLogoText("copy");
        copy.setEnabled(ComponentType.DRAWER, true);

        assertTrue(original.isEnabled(ComponentType.TOOLBAR));
        assertEquals("original", original.getSplash().getLogoText());
        assertFalse(original.isEnabled(ComponentType.DRAWER));

        assertFalse(copy.isEnabled(ComponentType.TOOLBAR));
        assertEquals("copy", copy.getSplash().getLogoText());
    }

    @Test
    public void toggleFlipsTheComponentState() {
        ProjectUIConfig config = new ProjectUIConfig();
        boolean before = config.isEnabled(ComponentType.FAB);

        config.toggle(ComponentType.FAB);

        assertEquals(!before, config.isEnabled(ComponentType.FAB));
    }

    @Test
    public void enabledComponentListMatchesTheFlags() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.setEnabled(ComponentType.TOOLBAR, true);
        config.setEnabled(ComponentType.DRAWER, true);

        assertTrue(config.getEnabledComponents().contains(ComponentType.TOOLBAR));
        assertTrue(config.getEnabledComponents().contains(ComponentType.DRAWER));
        assertEquals(config.getEnabledComponents().size(),
                config.getEnabledComponentCount());
    }

    @Test
    public void resetToDefaultsClearsCustomisation() {
        ProjectUIConfig config = new ProjectUIConfig();
        config.setPresetId("everything");
        config.setEnabled(ComponentType.TOOLBAR, true);
        config.getSplash().setLogoText("custom");

        config.resetToDefaults();

        assertEquals("", config.getPresetId());
        assertFalse(config.isEnabled(ComponentType.TOOLBAR));
        assertEquals("", config.getSplash().getLogoText());
    }

    @Test
    public void splashShouldRenderRespectsModeAndFlag() {
        ProjectUIConfig.Splash splash = new ProjectUIConfig.Splash();

        assertTrue(splash.shouldRender());

        splash.setMode(ProjectUIConfig.SplashMode.NONE);
        assertFalse(splash.shouldRender());

        splash.setMode(ProjectUIConfig.SplashMode.NATIVE).setEnabled(false);
        assertFalse(splash.shouldRender());
    }
}
