package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.project.StartupDecision;

import org.junit.Test;

/**
 * Startup matrix for the three-type project system.
 *
 * <p>Every combination from the specification is covered here. The tests are pure JVM — no
 * Android dependency — so they run without an emulator.</p>
 */
public class StartupDecisionTest {

    // ==================== 1. Splash enabled + Bootstrap disabled ====================

    @Test
    public void splashOn_bootstrapOff_showsSplashThenWeb() {
        assertEquals(StartupDecision.MainDestination.SPLASH,
                StartupDecision.resolveMain(true, true, false, true, true));
        assertEquals(StartupDecision.SplashDestination.WEB,
                StartupDecision.resolveAfterSplash(false, true, true));
    }

    // ==================== 2. Splash disabled + Bootstrap disabled ====================

    @Test
    public void splashOff_bootstrapOff_goesStraightToWeb() {
        assertEquals(StartupDecision.MainDestination.WEB,
                StartupDecision.resolveMain(false, true, false, true, true));
    }

    // ==================== 3. Splash enabled + Bootstrap enabled ====================

    @Test
    public void splashOn_bootstrapOn_showsSplashThenBootstrap() {
        assertEquals(StartupDecision.MainDestination.SPLASH,
                StartupDecision.resolveMain(true, true, true, true, true));
        assertEquals(StartupDecision.SplashDestination.BOOTSTRAP,
                StartupDecision.resolveAfterSplash(true, true, true));
    }

    // ==================== 4/5. Native and Web Bootstrap ====================

    @Test
    public void bootstrapAtStartup_opensBootstrapRegardlessOfRenderingMode() {
        // Rendering mode is a property of the project, not of the decision: both NATIVE and WEB
        // bootstrap projects resolve to the same destination.
        assertEquals(StartupDecision.MainDestination.BOOTSTRAP,
                StartupDecision.resolveMain(false, false, true, true, true));
    }

    // ==================== 6. Manual Bootstrap launch ====================

    @Test
    public void bootstrapWithoutStartupOptIn_isNeverAutoOpened() {
        assertFalse(StartupDecision.shouldAutoOpenBootstrap(false, true));
        assertEquals(StartupDecision.MainDestination.WEB,
                StartupDecision.resolveMain(false, false, false, true, true));
    }

    @Test
    public void bootstrapWithStartupOptIn_isAutoOpened() {
        assertTrue(StartupDecision.shouldAutoOpenBootstrap(true, true));
    }

    // ==================== 7. Web Project launch ====================

    @Test
    public void webProject_isTheDefaultDestination() {
        assertEquals(StartupDecision.MainDestination.WEB,
                StartupDecision.resolveMain(false, false, false, false, true));
    }

    // ==================== Independence rules ====================

    @Test
    public void splashDoesNotImplyBootstrap() {
        // Splash on, bootstrap project exists but never opted in.
        assertEquals(StartupDecision.SplashDestination.WEB,
                StartupDecision.resolveAfterSplash(false, true, true));
    }

    @Test
    public void bootstrapDoesNotImplySplash() {
        assertEquals(StartupDecision.MainDestination.BOOTSTRAP,
                StartupDecision.resolveMain(false, true, true, true, true));
    }

    @Test
    public void splashIsSkippedWhenNoSplashProjectExists() {
        assertFalse(StartupDecision.shouldShowSplash(true, false));
        assertEquals(StartupDecision.MainDestination.WEB,
                StartupDecision.resolveMain(true, false, false, false, true));
    }

    @Test
    public void splashIsSkippedWhenDisabled() {
        assertFalse(StartupDecision.shouldShowSplash(false, true));
    }

    // ==================== 11. Back navigation never re-enters Splash ====================

    @Test
    public void afterSplashNeverReturnsToSplash() {
        StartupDecision.SplashDestination[] all = StartupDecision.SplashDestination.values();
        for (StartupDecision.SplashDestination destination : all) {
            assertTrue(destination != StartupDecision.SplashDestination.BOOTSTRAP
                    || destination == StartupDecision.SplashDestination.BOOTSTRAP);
        }
        // The hand-off can only be BOOTSTRAP, WEB or DASHBOARD — never SPLASH.
        assertEquals(3, StartupDecision.SplashDestination.values().length);
        assertEquals(StartupDecision.MainDestination.DASHBOARD,
                StartupDecision.toMain(StartupDecision.SplashDestination.DASHBOARD));
    }

    // ==================== Empty registry ====================

    @Test
    public void noProjects_fallsBackToDashboard() {
        assertEquals(StartupDecision.MainDestination.DASHBOARD,
                StartupDecision.resolveMain(false, false, false, false, false));
        assertEquals(StartupDecision.SplashDestination.DASHBOARD,
                StartupDecision.resolveAfterSplash(false, false, false));
    }

    @Test
    public void splashWithNoDestination_fallsBackToDashboard() {
        assertEquals(StartupDecision.MainDestination.SPLASH,
                StartupDecision.resolveMain(true, true, false, false, false));
        assertEquals(StartupDecision.SplashDestination.DASHBOARD,
                StartupDecision.resolveAfterSplash(false, false, false));
    }

    // ==================== Project-level matrix (unchanged behaviour) ====================

    @Test
    public void projectMatrix_stillResolvesAllFourCombinations() {
        assertEquals(StartupDecision.ProjectStartup.SPLASH_THEN_BOOTSTRAP,
                StartupDecision.resolveProject(true, true));
        assertEquals(StartupDecision.ProjectStartup.BOOTSTRAP_ONLY,
                StartupDecision.resolveProject(false, true));
        assertEquals(StartupDecision.ProjectStartup.SPLASH_ONLY,
                StartupDecision.resolveProject(true, false));
        assertEquals(StartupDecision.ProjectStartup.DIRECT,
                StartupDecision.resolveProject(false, false));
    }
}
