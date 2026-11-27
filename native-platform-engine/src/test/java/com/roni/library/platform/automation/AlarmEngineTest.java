package com.roni.library.platform.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.roni.library.contracts.automation.AlarmSpec;

import org.junit.Test;

public class AlarmEngineTest {

    private final AutomationStore store =
            new AutomationStore(new AutomationStore.MemoryKeyValueStore());
    private final EventEngine events = new EventEngine(store);
    private final AlarmEngine engine = new AlarmEngine(store, events, null);

    @Test
    public void schedulePersistsAlarm() throws Exception {
        AlarmSpec spec = new AlarmSpec.Builder().id("a1").label("Wake")
                .triggerAt(System.currentTimeMillis() + 60_000L).build();

        AlarmSpec stored = engine.schedule("p1", spec);

        assertEquals("a1", stored.getId());
        assertEquals(1, engine.list("p1").size());
        assertEquals(1, store.loadAlarms("p1").size());
        assertTrue(engine.list("p2").isEmpty());
    }

    @Test
    public void cancelRemovesAlarm() throws Exception {
        engine.schedule("p1", new AlarmSpec.Builder().id("a2").label("Wake")
                .triggerAt(System.currentTimeMillis() + 60_000L).build());

        assertTrue(engine.cancel("p1", "a2"));
        assertTrue(engine.list("p1").isEmpty());
        assertFalse(engine.cancel("p1", "a2"));
    }

    @Test(expected = AutomationException.class)
    public void pastTriggerIsRejected() throws Exception {
        engine.schedule("p1", new AlarmSpec.Builder().id("a3").label("Past")
                .triggerAt(System.currentTimeMillis() - 10_000L).build());
    }

    @Test
    public void repeatingAlarmReschedulesAfterFiring() throws Exception {
        long triggerAt = System.currentTimeMillis() + 30_000L;
        engine.schedule("p1", new AlarmSpec.Builder().id("a4").label("Repeat")
                .triggerAt(triggerAt).intervalMs(60_000L).build());

        engine.onFired("p1", "a4");

        AlarmSpec next = engine.list("p1").get(0);
        assertTrue(next.getTriggerAt() > triggerAt);
        assertTrue(events.replay("p1", 0L, 20).size() >= 2);
    }

    @Test
    public void restoreRearmsPersistedAlarms() throws Exception {
        store.saveAlarms("p1", java.util.Collections.singletonList(
                new AlarmSpec.Builder().id("a5").label("Boot")
                        .triggerAt(System.currentTimeMillis() + 60_000L).build()));

        AlarmEngine fresh = new AlarmEngine(store, events, null);
        fresh.restore("p1");

        assertEquals(1, fresh.list("p1").size());
    }
}
