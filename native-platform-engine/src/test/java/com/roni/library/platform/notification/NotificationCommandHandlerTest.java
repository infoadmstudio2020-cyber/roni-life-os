package com.roni.library.platform.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.contracts.notification.NotificationChannelSpec;
import com.roni.library.contracts.notification.NotificationPermissionState;
import com.roni.library.contracts.notification.NotificationRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bridge-level tests: project scoping and payload validation of the {@code /notification} route. */
public class NotificationCommandHandlerTest {

    private static final class FakeBackend implements NotificationBackend {
        final List<String> posted = new ArrayList<>();

        @Override
        public boolean isSupported() {
            return true;
        }

        @Override
        public NotificationPermissionState getPermissionState() {
            return NotificationPermissionState.GRANTED;
        }

        @Override
        public boolean areNotificationsEnabled() {
            return true;
        }

        @Override
        public void ensureChannel(NotificationChannelSpec spec) {
        }

        @Override
        public void deleteChannel(String channelId) {
        }

        @Override
        public List<NotificationChannelSpec> getChannels() {
            return new ArrayList<>();
        }

        @Override
        public boolean post(NotificationRequest request, int notificationId) {
            posted.add(request.getKey());
            return true;
        }

        @Override
        public void cancel(int notificationId) {
        }

        @Override
        public void cancelAll() {
        }
    }

    private NotificationCommandHandler handler;

    @Before
    public void setUp() {
        NotificationService service = new NotificationService(new FakeBackend(),
                new NotificationScheduler() {
                    @Override
                    public boolean schedule(NotificationRequest request, int notificationId) {
                        return true;
                    }

                    @Override
                    public void cancel(int notificationId) {
                    }

                    @Override
                    public void cancelAll() {
                    }
                }, new InMemoryNotificationStore());
        service.init();
        handler = new NotificationCommandHandler(service);
        NotificationProjectContext.clear();
    }

    @After
    public void tearDown() {
        NotificationProjectContext.clear();
    }

    private static NativeApiRequest request(String action, Map<String, Object> params) {
        return new NativeApiRequest("req-1", NotificationCommandHandler.ROUTE, action, params, 0L);
    }

    private static Map<String, Object> postParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("key", "k1");
        params.put("title", "Hello");
        params.put("body", "World");
        return params;
    }

    @Test
    public void requestWithoutActiveProjectIsRejected() {
        NativeApiResponse response = handler.handleRequest(request("post", postParams()));
        assertFalse(response.isSuccess());
    }

    @Test
    public void postSucceedsInsideAProject() {
        NotificationProjectContext.setActiveProject("project-a");
        NativeApiResponse response = handler.handleRequest(request("post", postParams()));
        assertTrue(response.isSuccess());
    }

    @Test
    public void invalidPayloadIsRejected() {
        NotificationProjectContext.setActiveProject("project-a");
        Map<String, Object> params = postParams();
        params.put("title", "");
        NativeApiResponse response = handler.handleRequest(request("post", params));
        assertFalse(response.isSuccess());
    }

    @Test
    public void crossProjectCancelIsRejected() {
        NotificationProjectContext.setActiveProject("project-a");
        assertTrue(handler.handleRequest(request("post", postParams())).isSuccess());

        NotificationProjectContext.setActiveProject("project-b");
        Map<String, Object> params = new HashMap<>();
        params.put("key", "k1");
        NativeApiResponse response = handler.handleRequest(request("cancel", params));
        assertFalse(response.isSuccess());
    }

    @Test
    public void statusReportsTheActiveProject() {
        NotificationProjectContext.setActiveProject("project-a");
        NativeApiResponse response = handler.handleRequest(request("status", new HashMap<>()));
        assertTrue(response.isSuccess());
        assertEquals("project-a", NotificationProjectContext.getActiveProject());
    }
}
