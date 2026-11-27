package com.roni.library.platform.automation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.contracts.automation.AutomationErrorCodes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

public class AutomationCommandHandlerTest {

    private AutomationService service;
    private AutomationCommandHandler handler;

    @Before
    public void setUp() {
        service = new AutomationService(
                new AutomationStore(new AutomationStore.MemoryKeyValueStore()));
        handler = new AutomationCommandHandler(service);
        AutomationProjectContext.setActiveProject("project-a");
    }

    @After
    public void tearDown() {
        AutomationProjectContext.clear();
        service.release();
    }

    @Test
    public void routeIsAutomation() {
        assertEquals("automation", handler.getPath());
    }

    @Test
    public void submitAndListTask() {
        NativeApiResponse submitted = handler.handleRequest(request("submitTask")
                .param("name", "Sync")
                .param("type", "noop")
                .build());
        assertTrue(submitted.isSuccess());
        assertNotNull(submitted.getData().get("id"));

        NativeApiResponse listed = handler.handleRequest(request("listTasks").build());
        assertTrue(listed.isSuccess());
        assertEquals(1, ((Number) listed.getData().get("count")).intValue());
    }

    @Test
    public void tasksAreScopedToActiveProject() {
        handler.handleRequest(request("submitTask").param("name", "A")
                .param("type", "noop").build());

        AutomationProjectContext.setActiveProject("project-b");
        NativeApiResponse listed = handler.handleRequest(request("listTasks").build());

        assertEquals(0, ((Number) listed.getData().get("count")).intValue());
    }

    @Test
    public void payloadProjectIdIsIgnored() {
        handler.handleRequest(request("submitTask")
                .param("name", "A")
                .param("type", "noop")
                .param("projectId", "attacker-project")
                .build());

        NativeApiResponse listed = handler.handleRequest(request("listTasks").build());
        assertEquals(1, ((Number) listed.getData().get("count")).intValue());
        AutomationProjectContext.setActiveProject("attacker-project");
        NativeApiResponse other = handler.handleRequest(request("listTasks").build());
        assertEquals(0, ((Number) other.getData().get("count")).intValue());
    }

    @Test
    public void unknownActionReturnsError() {
        NativeApiResponse response = handler.handleRequest(request("nope").build());
        assertFalse(response.isSuccess());
        assertEquals(AutomationErrorCodes.UNSUPPORTED, response.getError().getCode());
    }

    @Test
    public void missingProjectIsRejected() {
        AutomationProjectContext.clear();
        NativeApiResponse response = handler.handleRequest(request("submitTask")
                .param("name", "A").param("type", "noop").build());
        assertFalse(response.isSuccess());
        assertEquals(AutomationErrorCodes.NO_ACTIVE_PROJECT, response.getError().getCode());
    }

    @Test
    public void statusAndCapabilitiesAreReported() {
        NativeApiResponse status = handler.handleRequest(request("getStatus").build());
        assertTrue(status.isSuccess());
        assertEquals("project-a", status.getData().get("projectId"));

        NativeApiResponse capabilities =
                handler.handleRequest(request("getCapabilities").build());
        assertTrue(capabilities.isSuccess());
        assertEquals(Boolean.FALSE, capabilities.getData().get("speech"));
    }

    @Test
    public void alarmLifecycleThroughBridge() {
        NativeApiResponse scheduled = handler.handleRequest(request("setAlarm")
                .param("id", "alarm-1")
                .param("label", "Wake")
                .param("triggerAt", System.currentTimeMillis() + 60_000L)
                .build());
        assertTrue(scheduled.isSuccess());

        NativeApiResponse listed = handler.handleRequest(request("listAlarms").build());
        assertEquals(1, ((Number) listed.getData().get("count")).intValue());

        NativeApiResponse cancelled = handler.handleRequest(request("cancelAlarm")
                .param("alarmId", "alarm-1").build());
        assertTrue(cancelled.isSuccess());
        assertEquals(Boolean.TRUE, cancelled.getData().get("cancelled"));
    }

    @Test
    public void eventsAreReadableThroughBridge() {
        handler.handleRequest(request("publishEvent")
                .param("topic", "custom")
                .param("payload", Map.of("note", "hello"))
                .build());

        NativeApiResponse events = handler.handleRequest(request("getEvents")
                .param("sinceSequence", 0L).param("limit", 50L).build());
        assertTrue(events.isSuccess());
        assertTrue(((Number) events.getData().get("count")).intValue() >= 1);
    }

    private static NativeApiRequest.Builder request(String action) {
        return new NativeApiRequest.Builder("automation", action);
    }
}
