package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.runtime.ProjectLaunchRouter;


import org.junit.Test;

/**
 * The open/restore decision table. Restoring must win over creating whenever a live runtime
 * exists, and the cap must block a new project rather than evict a running one.
 */
public class ProjectLaunchRouterTest {

    @Test
    public void liveSessionIsRestored() {
        assertEquals(ProjectLaunchRouter.Action.RESTORE,
                ProjectLaunchRouter.decide(true, true, 3, 5));
    }

    @Test
    public void liveSessionIsRestoredEvenAtTheCap() {
        assertEquals(ProjectLaunchRouter.Action.RESTORE,
                ProjectLaunchRouter.decide(true, true, 5, 5));
    }

    @Test
    public void staleSessionIsRebuilt() {
        assertEquals(ProjectLaunchRouter.Action.CREATE,
                ProjectLaunchRouter.decide(true, false, 3, 5));
    }

    @Test
    public void staleSessionIsRebuiltEvenAtTheCap() {
        // A recoverable session already owns its slot, so rebuilding it never needs a new one.
        assertEquals(ProjectLaunchRouter.Action.CREATE,
                ProjectLaunchRouter.decide(true, false, 5, 5));
    }

    @Test
    public void newProjectIsCreatedBelowTheCap() {
        assertEquals(ProjectLaunchRouter.Action.CREATE,
                ProjectLaunchRouter.decide(false, false, 4, 5));
    }

    @Test
    public void newProjectIsBlockedAtTheCap() {
        assertEquals(ProjectLaunchRouter.Action.BLOCKED,
                ProjectLaunchRouter.decide(false, false, 5, 5));
    }

    @Test
    public void blockedResultCarriesAMessage() {
        ProjectLaunchRouter.Result result = new ProjectLaunchRouter.Result(
                ProjectLaunchRouter.Action.BLOCKED, "All sessions are open.");

        assertTrue(result.isBlocked());
        assertFalse(result.isRestored());
        assertEquals("All sessions are open.", result.getMessage());
    }
}
