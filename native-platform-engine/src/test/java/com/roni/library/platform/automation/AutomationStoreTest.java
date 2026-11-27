package com.roni.library.platform.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.roni.library.contracts.automation.AlarmSpec;
import com.roni.library.contracts.automation.AutomationEvent;
import com.roni.library.contracts.automation.JobSpec;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class AutomationStoreTest {

    private AutomationStore store() {
        return new AutomationStore(new AutomationStore.MemoryKeyValueStore());
    }

    @Test
    public void jobsArePersistedPerProject() {
        AutomationStore store = store();
        JobSpec spec = new JobSpec.Builder().id("job-1").name("Sync")
                .workerType("noop").build();
        store.saveJobs("project-a", Collections.singletonList(spec));

        assertEquals(1, store.loadJobs("project-a").size());
        assertEquals("job-1", store.loadJobs("project-a").get(0).getId());
        assertTrue(store.loadJobs("project-b").isEmpty());
    }

    @Test
    public void alarmsArePersistedPerProject() {
        AutomationStore store = store();
        AlarmSpec spec = new AlarmSpec.Builder().id("alarm-1").label("Wake")
                .triggerAt(System.currentTimeMillis() + 60_000L).build();
        store.saveAlarms("project-a", Collections.singletonList(spec));

        assertEquals(1, store.loadAlarms("project-a").size());
        assertTrue(store.loadAlarms("project-b").isEmpty());
    }

    @Test
    public void eventsAreAppendedAndFilteredBySequence() {
        AutomationStore store = store();
        EventEngine events = new EventEngine(store);
        events.emit("project-a", EventEngine.TOPIC_TASK, "started", null);
        events.emit("project-a", EventEngine.TOPIC_TASK, "finished", null);

        List<AutomationEvent> all = store.loadEvents("project-a", 0L, 10);
        assertEquals(2, all.size());
        assertEquals(1L, all.get(0).getSequence());
        assertEquals(2L, all.get(1).getSequence());

        List<AutomationEvent> tail = store.loadEvents("project-a", 1L, 10);
        assertEquals(1, tail.size());
        assertEquals("finished", tail.get(0).getPayload().get("action"));
        assertTrue(store.loadEvents("project-b", 0L, 10).isEmpty());
    }

    @Test
    public void knownProjectsTracksSavedState() {
        AutomationStore store = store();
        store.saveJobs("project-a", Collections.singletonList(
                new JobSpec.Builder().id("j1").name("A").workerType("noop").build()));
        store.saveAlarms("project-b", Collections.singletonList(
                new AlarmSpec.Builder().id("a1").label("B")
                        .triggerAt(System.currentTimeMillis() + 60_000L).build()));

        List<String> projects = store.knownProjects();
        assertEquals(2, projects.size());
        assertTrue(projects.contains("project-a"));
        assertTrue(projects.contains("project-b"));
    }

    @Test
    public void sequenceIsMonotonicAndResetsOnClear() {
        AutomationStore store = store();
        EventEngine events = new EventEngine(store);
        events.emit("project-a", EventEngine.TOPIC_CUSTOM, "one", null);
        events.emit("project-a", EventEngine.TOPIC_CUSTOM, "two", null);
        assertEquals(3L, store.nextSequence("project-a"));

        store.clearEvents("project-a");
        assertTrue(store.loadEvents("project-a", 0L, 10).isEmpty());
        // The cursor stays monotonic so clients never replay stale sequences.
        assertEquals(4L, store.nextSequence("project-a"));
    }
}
