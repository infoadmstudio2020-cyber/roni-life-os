package com.roni.life.os.app.project;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Consumer startup matrix and back-navigation policy.
 *
 * <p>Pure JVM tests — no Android dependencies, so they run on every build.</p>
 */
public class ConsumerStartupRoutingTest {

    // ==================== startup matrix ====================

    @Test
    public void splashOnDashboardOn_startsAtSplash() {
        assertEquals(StartupDecision.ConsumerDestination.SPLASH,
                StartupDecision.resolveConsumer(true, true, true));
    }

    @Test
    public void splashOffDashboardOn_startsAtDashboard() {
        assertEquals(StartupDecision.ConsumerDestination.DASHBOARD,
                StartupDecision.resolveConsumer(false, true, true));
    }

    @Test
    public void splashOnDashboardOff_startsAtSplash() {
        assertEquals(StartupDecision.ConsumerDestination.SPLASH,
                StartupDecision.resolveConsumer(true, false, true));
    }

    @Test
    public void splashOffDashboardOff_startsAtDefaultProject() {
        assertEquals(StartupDecision.ConsumerDestination.DEFAULT_PROJECT,
                StartupDecision.resolveConsumer(false, false, true));
    }

    // ==================== splash hand-off ====================

    @Test
    public void splashHandsOffToDashboardWhenEnabled() {
        assertEquals(StartupDecision.ConsumerDestination.DASHBOARD,
                StartupDecision.resolveConsumerAfterSplash(true, true));
    }

    @Test
    public void splashHandsOffToDefaultProjectWhenDashboardDisabled() {
        assertEquals(StartupDecision.ConsumerDestination.DEFAULT_PROJECT,
                StartupDecision.resolveConsumerAfterSplash(false, true));
    }

    @Test
    public void splashNeverLoopsBackToItself() {
        for (boolean dashboard : new boolean[]{true, false}) {
            for (boolean project : new boolean[]{true, false}) {
                assertNotEquals(StartupDecision.ConsumerDestination.SPLASH,
                        StartupDecision.resolveConsumerAfterSplash(dashboard, project));
            }
        }
    }

    // ==================== configuration errors ====================

    @Test
    public void dashboardOffWithoutDefaultProject_isConfigurationError() {
        assertEquals(StartupDecision.ConsumerDestination.CONFIG_ERROR,
                StartupDecision.resolveConsumer(false, false, false));
    }

    @Test
    public void dashboardOffWithMissingProject_neverFallsBackToDashboard() {
        StartupDecision.ConsumerDestination destination =
                StartupDecision.resolveConsumer(false, false, false);
        assertNotEquals(StartupDecision.ConsumerDestination.DASHBOARD, destination);
        assertNotEquals(StartupDecision.ConsumerDestination.DEFAULT_PROJECT, destination);
    }

    @Test
    public void dashboardOnWithoutDefaultProject_stillShowsDashboard() {
        assertEquals(StartupDecision.ConsumerDestination.DASHBOARD,
                StartupDecision.resolveConsumer(false, true, false));
    }

    // ==================== back policy ====================

    @Test
    public void backReturnsToDashboardOnlyWhenEnabled() {
        assertTrue(StartupDecision.shouldReturnToDashboard(true));
        assertFalse(StartupDecision.shouldReturnToDashboard(false));
    }
}
