package com.roni.library.platform.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.roni.library.contracts.automation.AutomationErrorCodes;
import com.roni.library.contracts.automation.BackgroundTask;

import org.junit.After;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class BackgroundTaskEngineTest {

    private final AutomationStore store =
            new AutomationStore(new AutomationStore.MemoryKeyValueStore());
    private final EventEngine events = new EventEngine(store);
    private final BackgroundTaskEngine engine = new BackgroundTaskEngine(store, events);

    @After
    public void tearDown() {
        engine.release();
    }

    @Test
    public void taskCompletesAndEmitsEvents() throws Exception {
        engine.registerHandler("echo", (task, sink) -> {
            sink.progress(50);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("echo", task.getName());
            return result;
        });

        BackgroundTask task = engine.submit("p1", "Echo", "echo", null, 1);
        BackgroundTask finished = awaitFinished("p1", task.getId());

        assertEquals(BackgroundTask.STATE_COMPLETED, finished.getState());
        assertEquals(100, finished.getProgress());
        assertTrue(events.replay("p1", 0L, 50).size() >= 2);
    }

    @Test
    public void failingTaskRetriesUntilSuccess() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        engine.registerHandler("flaky", (task, sink) -> {
            if (attempts.incrementAndGet() < 2) {
                throw new IllegalStateException("transient");
            }
            return new LinkedHashMap<>();
        });

        BackgroundTask task = engine.submit("p1", "Flaky", "flaky", null, 3);
        BackgroundTask finished = awaitFinished("p1", task.getId());

        assertEquals(BackgroundTask.STATE_COMPLETED, finished.getState());
        assertEquals(2, finished.getAttempts());
    }

    @Test
    public void taskFailsAfterMaxAttempts() throws Exception {
        engine.registerHandler("broken", (task, sink) -> {
            throw new IllegalStateException("always");
        });

        BackgroundTask task = engine.submit("p1", "Broken", "broken", null, 1);
        BackgroundTask finished = awaitFinished("p1", task.getId());

        assertEquals(BackgroundTask.STATE_FAILED, finished.getState());
        assertNotNull(finished.getLastError());
    }

    @Test
    public void cancelStopsRunningTask() throws Exception {
        engine.registerHandler("slow", (task, sink) -> {
            for (int i = 0; i < 100; i++) {
                if (sink.isCancelled()) {
                    throw new InterruptedException("cancelled");
                }
                Thread.sleep(20L);
            }
            return new LinkedHashMap<>();
        });

        BackgroundTask task = engine.submit("p1", "Slow", "slow", null, 1);
        Thread.sleep(80L);
        assertTrue(engine.cancel("p1", task.getId()));
        BackgroundTask finished = awaitFinished("p1", task.getId());

        assertEquals(BackgroundTask.STATE_CANCELLED, finished.getState());
    }

    @Test
    public void tasksAreIsolatedPerProject() throws Exception {
        engine.registerHandler("noop", (task, sink) -> new LinkedHashMap<>());
        BackgroundTask task = engine.submit("p1", "Noop", "noop", null, 1);
        awaitFinished("p1", task.getId());

        assertEquals(1, engine.list("p1").size());
        assertTrue(engine.list("p2").isEmpty());
        assertEquals(0, engine.clearFinished("p2"));
        assertEquals(1, engine.clearFinished("p1"));
    }

    @Test(expected = AutomationException.class)
    public void unknownTypeFailsFast() throws Exception {
        engine.submit("p1", "Unknown", "missing", null, 1);
    }

    @Test(expected = AutomationException.class)
    public void emptyProjectIsRejected() throws Exception {
        engine.submit("", "Noop", "noop", null, 1);
    }

    private BackgroundTask awaitFinished(String projectId, String taskId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            BackgroundTask task = engine.get(projectId, taskId);
            if (task != null && task.isFinished()) {
                return task;
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("Task did not finish: " + taskId);
    }
}
