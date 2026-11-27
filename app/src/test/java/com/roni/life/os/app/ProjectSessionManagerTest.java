package com.roni.life.os.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.roni.life.os.app.runtime.ProjectSessionManager;
import com.roni.life.os.app.runtime.SessionSnapshot;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-session rules: several projects stay alive at once, minimising never evicts another
 * project, and only an explicit close frees a session slot.
 */
public class ProjectSessionManagerTest {

    private ProjectSessionManager manager;

    @Before
    public void setUp() {
        ProjectSessionManager.reset();
        manager = ProjectSessionManager.get();
    }

    @Test
    public void registerCreatesActiveSession() {
        ProjectSessionManager.Registration registration =
                manager.register("a", "Project A", false);

        assertTrue(registration.isAccepted());
        assertTrue(registration.isCreated());
        assertEquals(1, manager.count());
        assertEquals(ProjectSessionManager.State.ACTIVE,
                manager.find("a").getState());
        assertTrue(manager.isLive("a"));
    }

    @Test
    public void registerRejectsBlankId() {
        assertFalse(manager.register("", "Nameless", false).isAccepted());
        assertFalse(manager.register(null, "Nameless", false).isAccepted());
        assertEquals(0, manager.count());
    }

    @Test
    public void registeringTheSameProjectTwiceDoesNotCreateASecondSession() {
        manager.register("a", "Project A", false);
        ProjectSessionManager.Registration second = manager.register("a", "Project A", false);

        assertTrue(second.isAccepted());
        assertFalse(second.isCreated());
        assertEquals(1, manager.count());
    }

    @Test
    public void minimisingOneProjectKeepsEveryOtherSessionAlive() {
        manager.register("a", "Project A", false);
        manager.register("b", "Project B", false);
        manager.register("c", "Project C", false);

        manager.minimize("a");
        manager.minimize("b");
        manager.minimize("c");

        assertEquals(3, manager.count());
        assertEquals(3, manager.minimizedCount());
        assertEquals(3, manager.liveCount());
        assertTrue(manager.isOpen("a"));
        assertTrue(manager.isOpen("b"));
        assertTrue(manager.isOpen("c"));
    }

    @Test
    public void restoringOneProjectLeavesTheOthersMinimised() {
        manager.register("a", "Project A", false);
        manager.register("b", "Project B", false);
        manager.register("c", "Project C", false);
        manager.minimize("a");
        manager.minimize("b");
        manager.minimize("c");

        assertTrue(manager.resume("b"));

        assertEquals(ProjectSessionManager.State.ACTIVE, manager.find("b").getState());
        assertEquals(ProjectSessionManager.State.MINIMIZED, manager.find("a").getState());
        assertEquals(ProjectSessionManager.State.MINIMIZED, manager.find("c").getState());
        assertEquals(3, manager.count());
    }

    @Test
    public void backgroundSessionsExcludeTheForegroundProject() {
        manager.register("a", "Project A", false);
        manager.register("b", "Project B", false);
        manager.minimize("a");

        List<ProjectSessionManager.Session> background = manager.getBackgroundSessions();

        assertEquals(1, background.size());
        assertEquals("a", background.get(0).getProjectId());
    }

    @Test
    public void sessionCapRejectsInsteadOfEvictingAnotherProject() {
        for (int index = 0; index < ProjectSessionManager.MAX_SESSIONS; index++) {
            assertTrue(manager.register("p" + index, "Project " + index, false).isAccepted());
        }
        manager.minimize("p0");

        ProjectSessionManager.Registration overflow =
                manager.register("extra", "Extra", false);

        assertFalse(overflow.isAccepted());
        assertNull(overflow.getEvictedProjectId());
        assertEquals(ProjectSessionManager.MAX_SESSIONS, manager.count());
        // The least-recently-used minimised session must still be there.
        assertTrue(manager.isOpen("p0"));
        assertFalse(manager.isOpen("extra"));
    }

    @Test
    public void reopeningAnExistingSessionIsAllowedAtTheCap() {
        for (int index = 0; index < ProjectSessionManager.MAX_SESSIONS; index++) {
            manager.register("p" + index, "Project " + index, false);
        }

        ProjectSessionManager.Registration existing = manager.register("p2", "Project 2", false);

        assertTrue(existing.isAccepted());
        assertFalse(existing.isCreated());
        assertEquals(ProjectSessionManager.MAX_SESSIONS, manager.count());
    }

    @Test
    public void closeRemovesOnlyTheRequestedSession() {
        manager.register("a", "Project A", false);
        manager.register("b", "Project B", false);
        manager.register("c", "Project C", false);

        manager.close("b");

        assertEquals(2, manager.count());
        assertTrue(manager.isOpen("a"));
        assertFalse(manager.isOpen("b"));
        assertTrue(manager.isOpen("c"));
    }

    @Test
    public void taskIdIsTrackedPerSession() {
        manager.register("a", "Project A", false);
        manager.register("b", "Project B", false);

        manager.attachTask("a", 41);
        manager.attachTask("b", 42);

        assertEquals(41, manager.find("a").getTaskId());
        assertEquals(42, manager.find("b").getTaskId());
    }

    @Test
    public void recoverableSessionIsListedButNotLive() {
        manager.register("a", "Project A", false);
        manager.attachTask("a", 41);

        manager.markRecoverable("a");

        ProjectSessionManager.Session session = manager.find("a");
        assertNotNull(session);
        assertEquals(ProjectSessionManager.State.RECOVERABLE, session.getState());
        assertFalse(session.isLive());
        assertFalse(manager.isLive("a"));
        assertEquals(ProjectSessionManager.NO_TASK, session.getTaskId());
        assertEquals(1, manager.count());
        assertEquals(1, manager.recoverableCount());
    }

    @Test
    public void activatingARecoverableSessionMakesItLiveAgain() {
        manager.register("a", "Project A", false);
        manager.markRecoverable("a");

        assertTrue(manager.activate("a", 77));

        assertTrue(manager.isLive("a"));
        assertEquals(77, manager.find("a").getTaskId());
        assertEquals(ProjectSessionManager.State.ACTIVE, manager.find("a").getState());
    }

    @Test
    public void resumeDoesNotResurrectARecoverableSession() {
        manager.register("a", "Project A", false);
        manager.markRecoverable("a");

        assertFalse(manager.resume("a"));
        assertEquals(ProjectSessionManager.State.RECOVERABLE, manager.find("a").getState());
    }

    @Test
    public void snapshotRoundTripRestoresSessionsAsRecoverable() {
        manager.register("a", "Project A", false, 0xFF112233, "asset");
        manager.register("b", "Project B", false, 0xFF445566, "url");
        manager.minimize("a");

        List<SessionSnapshot> snapshot = manager.snapshot();
        assertEquals(2, snapshot.size());

        ProjectSessionManager.reset();
        ProjectSessionManager fresh = ProjectSessionManager.get();
        int restored = fresh.restore(snapshot);

        assertEquals(2, restored);
        assertEquals(2, fresh.count());
        assertEquals(2, fresh.recoverableCount());
        assertEquals(0, fresh.liveCount());
        assertEquals("Project A", fresh.find("a").getName());
        assertEquals(0xFF112233, fresh.find("a").getIconColor());
        assertEquals("url", fresh.find("b").getSourceKey());
    }

    @Test
    public void restoreSkipsDuplicatesAndRespectsTheCap() {
        manager.register("a", "Project A", false);

        List<SessionSnapshot> snapshots = new ArrayList<>();
        snapshots.add(new SessionSnapshot("a", "Duplicate A", 1L, 0, "", false));
        for (int index = 0; index < ProjectSessionManager.MAX_SESSIONS + 3; index++) {
            snapshots.add(new SessionSnapshot("r" + index, "Recovered " + index,
                    1L, 0, "", false));
        }

        int restored = manager.restore(snapshots);

        assertEquals(ProjectSessionManager.MAX_SESSIONS - 1, restored);
        assertEquals(ProjectSessionManager.MAX_SESSIONS, manager.count());
        assertEquals("Project A", manager.find("a").getName());
    }

    @Test
    public void clearRecoverableKeepsLiveSessions() {
        manager.register("a", "Project A", false);
        manager.register("b", "Project B", false);
        manager.markRecoverable("b");

        manager.clearRecoverable();

        assertEquals(1, manager.count());
        assertTrue(manager.isOpen("a"));
        assertFalse(manager.isOpen("b"));
    }

    @Test
    public void sessionsAreOrderedByMostRecentActivity() {
        manager.register("a", "Project A", false);
        manager.register("b", "Project B", false);
        manager.resume("a");

        List<ProjectSessionManager.Session> sessions = manager.getSessions();

        assertEquals("a", sessions.get(0).getProjectId());
    }
}
