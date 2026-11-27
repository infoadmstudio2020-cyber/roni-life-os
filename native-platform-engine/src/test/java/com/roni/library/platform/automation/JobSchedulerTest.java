package com.roni.library.platform.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.roni.library.contracts.automation.AutomationErrorCodes;
import com.roni.library.contracts.automation.JobSpec;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class JobSchedulerTest {

    private static final class FakeBackend implements JobScheduler.Backend {
        final List<String> enqueued = new ArrayList<>();
        final List<String> cancelled = new ArrayList<>();
        boolean available = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void enqueue(String projectId, JobSpec spec) {
            enqueued.add(projectId + "|" + spec.getId());
        }

        @Override
        public void cancel(String projectId, String jobId) {
            cancelled.add(projectId + "|" + jobId);
        }
    }

    private final AutomationStore store =
            new AutomationStore(new AutomationStore.MemoryKeyValueStore());
    private final EventEngine events = new EventEngine(store);
    private final FakeBackend backend = new FakeBackend();
    private final JobScheduler scheduler = new JobScheduler(store, events, backend);

    @Test
    public void scheduleEnqueuesAndPersists() throws Exception {
        JobSpec spec = new JobSpec.Builder().id("job-1").name("Sync")
                .workerType("noop").requiresNetwork(true).build();

        JobSpec stored = scheduler.schedule("p1", spec);

        assertEquals("job-1", stored.getId());
        assertEquals(1, scheduler.list("p1").size());
        assertEquals(1, backend.enqueued.size());
        assertEquals(1, store.loadJobs("p1").size());
        assertTrue(scheduler.list("p2").isEmpty());
    }

    @Test
    public void cancelRemovesJob() throws Exception {
        scheduler.schedule("p1", new JobSpec.Builder().id("job-2").name("Sync")
                .workerType("noop").build());

        assertTrue(scheduler.cancel("p1", "job-2"));
        assertTrue(scheduler.list("p1").isEmpty());
        assertEquals(1, backend.cancelled.size());
        assertFalse(scheduler.cancel("p1", "job-2"));
    }

    @Test(expected = AutomationException.class)
    public void missingWorkerTypeIsRejected() throws Exception {
        scheduler.schedule("p1", new JobSpec.Builder().id("job-3").name("Bad")
                .workerType("").build());
    }

    @Test(expected = AutomationException.class)
    public void emptyProjectIsRejected() throws Exception {
        scheduler.schedule("", new JobSpec.Builder().id("job-4").name("Bad")
                .workerType("noop").build());
    }

    @Test
    public void unavailableBackendStillPersists() throws Exception {
        backend.available = false;
        scheduler.schedule("p1", new JobSpec.Builder().id("job-5").name("Later")
                .workerType("noop").build());

        assertFalse(scheduler.isAvailable());
        assertEquals(1, scheduler.list("p1").size());
    }
}
