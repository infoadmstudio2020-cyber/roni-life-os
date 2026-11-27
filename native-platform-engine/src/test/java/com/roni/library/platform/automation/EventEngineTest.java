package com.roni.library.platform.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.roni.library.contracts.automation.AutomationEvent;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.observers.TestObserver;

public class EventEngineTest {

    private EventEngine engine() {
        return new EventEngine(new AutomationStore(new AutomationStore.MemoryKeyValueStore()));
    }

    @Test
    public void publishAssignsIncreasingSequence() {
        EventEngine events = engine();
        AutomationEvent first = events.emit("p1", EventEngine.TOPIC_TASK, "started", null);
        AutomationEvent second = events.emit("p1", EventEngine.TOPIC_TASK, "finished", null);

        assertEquals(1L, first.getSequence());
        assertEquals(2L, second.getSequence());
        assertEquals("p1", first.getProjectId());
    }

    @Test
    public void replayReturnsOnlyNewerEvents() {
        EventEngine events = engine();
        events.emit("p1", EventEngine.TOPIC_TASK, "a", null);
        events.emit("p1", EventEngine.TOPIC_TASK, "b", null);
        events.emit("p1", EventEngine.TOPIC_TASK, "c", null);

        List<AutomationEvent> tail = events.replay("p1", 1L, 10);
        assertEquals(2, tail.size());
        assertEquals("b", tail.get(0).getPayload().get("action"));
    }

    @Test
    public void observeDeliversOnlyProjectEvents() {
        EventEngine events = engine();
        TestObserver<AutomationEvent> observer = events.observe("p1").test();

        events.emit("p2", EventEngine.TOPIC_TASK, "other", null);
        events.emit("p1", EventEngine.TOPIC_TASK, "mine", null);

        observer.assertValueCount(1);
        assertEquals("mine", observer.values().get(0).getPayload().get("action"));
        observer.dispose();
    }

    @Test
    public void payloadIsPreserved() {
        EventEngine events = engine();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bytes", 42);
        AutomationEvent event = events.emit("p1", EventEngine.TOPIC_TRANSFER, "progress", payload);

        assertEquals(42, ((Number) event.getPayload().get("bytes")).intValue());
    }

    @Test
    public void clearRemovesProjectEventsOnly() {
        EventEngine events = engine();
        events.emit("p1", EventEngine.TOPIC_TASK, "a", null);
        events.emit("p2", EventEngine.TOPIC_TASK, "b", null);

        events.clear("p1");
        assertTrue(events.replay("p1", 0L, 10).isEmpty());
        assertEquals(1, events.replay("p2", 0L, 10).size());
    }
}
