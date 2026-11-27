package com.roni.library.ui.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.roni.library.ui.manager.ComponentType;

import org.junit.Test;

/** Verifies that each preset produces the component set it advertises. */
public class ProjectUIConfigPresetsTest {

    @Test
    public void idsAreStableAndOrdered() {
        assertEquals(6, ProjectUIConfigPresets.ids().size());
        assertEquals(ProjectUIConfigPresets.MINIMAL, ProjectUIConfigPresets.ids().get(0));
        assertEquals(ProjectUIConfigPresets.EVERYTHING, ProjectUIConfigPresets.ids().get(5));
    }

    @Test
    public void minimalHasStatusBarOnlyAndNoDashboard() {
        ProjectUIConfig config = ProjectUIConfigPresets.minimal();

        assertEquals(ProjectUIConfigPresets.MINIMAL, config.getPresetId());
        assertTrue(config.isEnabled(ComponentType.STATUS_BAR));
        assertFalse(config.isEnabled(ComponentType.TOOLBAR));
        assertFalse(config.isEnabled(ComponentType.WEB_VIEW));
        assertFalse(config.getDashboard().isEnabled());
        assertFalse(config.getSplash().isEnabled());
    }

    @Test
    public void statusBarToolbarHasToolbarAndBottomNav() {
        ProjectUIConfig config = ProjectUIConfigPresets.statusBarToolbar();

        assertTrue(config.isEnabled(ComponentType.STATUS_BAR));
        assertTrue(config.isEnabled(ComponentType.TOOLBAR));
        assertTrue(config.isEnabled(ComponentType.BOTTOM_NAV));
        assertFalse(config.isEnabled(ComponentType.DRAWER));
        assertTrue(config.getDashboard().isToolbarEnabled());
        assertTrue(config.getDashboard().isBottomNavEnabled());
    }

    @Test
    public void fullscreenWebViewHidesSystemBars() {
        ProjectUIConfig config = ProjectUIConfigPresets.fullscreenWebView();

        assertTrue(config.isEnabled(ComponentType.WEB_VIEW));
        assertTrue(config.isEnabled(ComponentType.FULLSCREEN_IMMERSIVE));
        assertFalse(config.isEnabled(ComponentType.STATUS_BAR));
        assertFalse(config.getDashboard().isEnabled());
        assertTrue(config.getNavigation().isFullscreen());
        assertFalse(config.getNavigation().isStatusBarVisible());
    }

    @Test
    public void dashboardFullEnablesEveryChromeElement() {
        ProjectUIConfig config = ProjectUIConfigPresets.dashboardFull();

        assertTrue(config.getDashboard().isEnabled());
        assertTrue(config.getDashboard().isToolbarEnabled());
        assertTrue(config.getDashboard().isDrawerEnabled());
        assertTrue(config.getDashboard().isBottomNavEnabled());
        assertTrue(config.getDashboard().isFabEnabled());

        assertTrue(config.isEnabled(ComponentType.TOOLBAR));
        assertTrue(config.isEnabled(ComponentType.DRAWER));
        assertTrue(config.isEnabled(ComponentType.BOTTOM_NAV));
        assertTrue(config.isEnabled(ComponentType.FAB));
    }

    @Test
    public void webAppHasNoDashboardChrome() {
        ProjectUIConfig config = ProjectUIConfigPresets.webApp();

        assertFalse(config.getDashboard().isEnabled());
        assertTrue(config.isEnabled(ComponentType.WEB_VIEW));
        assertFalse(config.isEnabled(ComponentType.TOOLBAR));
        assertFalse(config.isEnabled(ComponentType.DRAWER));
    }

    @Test
    public void everythingEnablesEveryCanonicalComponent() {
        ProjectUIConfig config = ProjectUIConfigPresets.everything();

        for (ComponentType type : ComponentType.values()) {
            if (type == ComponentType.CUSTOM_CONTENT || type == ComponentType.SYSTEM_UI) {
                continue;
            }
            assertTrue("expected " + type + " to be enabled",
                    config.isEnabled(type));
        }
    }

    @Test
    public void unknownPresetFallsBackToMinimal() {
        ProjectUIConfig config = ProjectUIConfigPresets.create("does-not-exist");

        assertEquals(ProjectUIConfigPresets.MINIMAL, config.getPresetId());
    }

    @Test
    public void createReturnsAFreshInstanceEveryTime() {
        ProjectUIConfig first = ProjectUIConfigPresets.create(ProjectUIConfigPresets.MINIMAL);
        ProjectUIConfig second = ProjectUIConfigPresets.create(ProjectUIConfigPresets.MINIMAL);

        first.setEnabled(ComponentType.TOOLBAR, true);

        assertFalse(second.isEnabled(ComponentType.TOOLBAR));
    }

    @Test
    public void everyPresetIsValid() {
        for (String id : ProjectUIConfigPresets.ids()) {
            ProjectUIConfig config = ProjectUIConfigPresets.create(id);
            assertNotNull(id, config);
            assertTrue("preset " + id + " should be valid: "
                            + ProjectUIConfigValidator.validate(config),
                    ProjectUIConfigValidator.isValid(config));
        }
    }

    @Test
    public void labelsAreHumanReadable() {
        assertEquals("Custom", ProjectUIConfigPresets.label(null));
        assertEquals("Custom", ProjectUIConfigPresets.label("nope"));
        assertTrue(ProjectUIConfigPresets.label(ProjectUIConfigPresets.MINIMAL)
                .toLowerCase().contains("minimal"));
    }
}
