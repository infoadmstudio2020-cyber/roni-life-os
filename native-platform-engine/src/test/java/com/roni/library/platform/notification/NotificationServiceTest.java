package com.roni.library.platform.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.roni.library.contracts.notification.NotificationChannelSpec;
import com.roni.library.contracts.notification.NotificationPermissionState;
import com.roni.library.contracts.notification.NotificationPreferences;
import com.roni.library.contracts.notification.NotificationRecord;
import com.roni.library.contracts.notification.NotificationRequest;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Engine policy tests. The Android backend is replaced by a fake, so delivery policy, isolation
 * and rate limiting are verified on the JVM without a device.
 */
public class NotificationServiceTest {

    private static final class FakeBackend implements NotificationBackend {
        final List<String> posted = new ArrayList<>();
        final List<Integer> cancelled = new ArrayList<>();
        boolean supported = true;

        @Override
        public boolean isSupported() {
            return supported;
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
            posted.add(request.getProjectId() + "/" + request.getKey());
            return true;
        }

        @Override
        public void cancel(int notificationId) {
            cancelled.add(notificationId);
        }

        @Override
        public void cancelAll() {
        }
    }

    private static final class FakeScheduler implements NotificationScheduler {
        final List<Integer> scheduled = new ArrayList<>();

        @Override
        public boolean schedule(NotificationRequest request, int notificationId) {
            scheduled.add(notificationId);
            return true;
        }

        @Override
        public void cancel(int notificationId) {
            scheduled.remove(Integer.valueOf(notificationId));
        }

        @Override
        public void cancelAll() {
            scheduled.clear();
        }
    }

    private NotificationService newService(FakeBackend backend) {
        NotificationService service = new NotificationService(backend, new FakeScheduler(),
                new InMemoryNotificationStore());
        service.init();
        return service;
    }

    private static NotificationRequest request(String projectId, String key) {
        return new NotificationRequest.Builder()
                .projectId(projectId)
                .key(key)
                .title("Title " + key)
                .body("Body")
                .build();
    }

    @Test
    public void postStoresHistoryAndReachesTheBackend() throws Exception {
        FakeBackend backend = new FakeBackend();
        NotificationService service = newService(backend);

        NotificationRecord record = service.post(request("project-a", "k1"));

        assertNotNull(record);
        assertEquals("project-a", record.getProjectId());
        assertEquals(1, backend.posted.size());
        assertEquals(1, service.getHistory("project-a", false).size());
        assertEquals(1, service.getUnreadCount("project-a"));
    }

    @Test
    public void cancelRemovesTheNotificationFromTheBackend() throws Exception {
        FakeBackend backend = new FakeBackend();
        NotificationService service = newService(backend);
        service.post(request("project-a", "k1"));

        service.cancel("project-a", "k1");

        assertEquals(1, backend.cancelled.size());
        assertTrue(service.getHistory("project-a", false).isEmpty());
    }

    @Test
    public void anotherProjectCannotCancelTheNotification() throws Exception {
        FakeBackend backend = new FakeBackend();
        NotificationService service = newService(backend);
        service.post(request("project-a", "k1"));

        try {
            service.cancel("project-b", "k1");
            fail("cross-project cancel must be rejected");
        } catch (NotificationException expected) {
            assertNotNull(expected.getCode());
        }
        assertTrue(backend.cancelled.isEmpty());
        assertEquals(1, service.getHistory("project-a", false).size());
    }

    @Test
    public void blockedProjectIsSuppressed() throws Exception {
        FakeBackend backend = new FakeBackend();
        NotificationService service = newService(backend);
        service.setPreferences(NotificationPreferences.defaults().toBuilder()
                .blockProject("project-a", true).build());

        try {
            service.post(request("project-a", "k1"));
            fail("blocked project must be suppressed");
        } catch (NotificationException expected) {
            assertNotNull(expected.getCode());
        }
        assertTrue(backend.posted.isEmpty());
    }

    @Test
    public void mutedProjectStillDeliversButWithoutSound() throws Exception {
        FakeBackend backend = new FakeBackend();
        NotificationService service = newService(backend);
        service.setPreferences(NotificationPreferences.defaults().toBuilder()
                .muteProject("project-a", true).build());

        service.post(request("project-a", "k1"));

        assertEquals(1, backend.posted.size());
        assertFalse(service.getPreferences().isSoundAllowed("project-a"));
    }

    @Test
    public void rateLimitStopsFlooding() throws Exception {
        FakeBackend backend = new FakeBackend();
        NotificationService service = newService(backend);
        int allowed = 0;
        for (int i = 0; i < 40; i++) {
            try {
                service.post(request("project-a", "k" + i));
                allowed++;
            } catch (NotificationException expected) {
                break;
            }
        }
        assertTrue("expected the limiter to stop the flood", allowed < 40);
        assertEquals(allowed, backend.posted.size());
    }

    @Test
    public void historyIsScopedPerProject() throws Exception {
        FakeBackend backend = new FakeBackend();
        NotificationService service = newService(backend);
        service.post(request("project-a", "k1"));
        service.post(request("project-b", "k1"));

        assertEquals(1, service.getHistory("project-a", false).size());
        assertEquals(1, service.getHistory("project-b", false).size());
        assertEquals(2, service.getAllHistory(false).size());
    }

    @Test
    public void markReadUpdatesTheRecord() throws Exception {
        FakeBackend backend = new FakeBackend();
        NotificationService service = newService(backend);
        NotificationRecord record = service.post(request("project-a", "k1"));

        assertTrue(service.markRead("project-a", record.getId()));
        assertEquals(0, service.getUnreadCount("project-a"));
    }
}
