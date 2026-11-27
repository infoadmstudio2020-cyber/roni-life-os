package com.roni.library.platform.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.notification.NotificationActionSpec;
import com.roni.library.contracts.notification.NotificationErrors;
import com.roni.library.contracts.notification.NotificationLimits;
import com.roni.library.contracts.notification.NotificationRequest;
import com.roni.library.contracts.notification.NotificationStyle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Contract-level validation: every rejection must be a structured error, never an exception. */
public class NotificationRequestValidationTest {

    private static NotificationRequest.Builder valid() {
        return new NotificationRequest.Builder()
                .projectId("project-a")
                .key("order-1")
                .title("Order shipped")
                .body("Your order is on the way");
    }

    @Test
    public void validRequestPasses() {
        assertNull(valid().build().validate());
    }

    @Test
    public void emptyTitleIsRejected() {
        NativeApiError error = valid().title("").build().validate();
        assertNotNull(error);
        assertEquals(NotificationErrors.INVALID_TITLE, error.getCode());
    }

    @Test
    public void overlongTitleIsRejected() {
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < NotificationLimits.MAX_TITLE_LENGTH + 1; i++) {
            title.append('x');
        }
        NativeApiError error = valid().title(title.toString()).build().validate();
        assertNotNull(error);
        assertEquals(NotificationErrors.INVALID_TITLE, error.getCode());
    }

    @Test
    public void tooManyActionsAreRejected() {
        List<NotificationActionSpec> actions = new ArrayList<>();
        for (int i = 0; i < NotificationLimits.MAX_ACTIONS + 1; i++) {
            actions.add(new NotificationActionSpec("a" + i, "Action " + i, "", false));
        }
        NativeApiError error = valid().actions(actions).build().validate();
        assertNotNull(error);
        assertEquals(NotificationErrors.TOO_MANY_ACTIONS, error.getCode());
    }

    @Test
    public void javascriptDeepLinkIsRejected() {
        NativeApiError error = valid().deepLink("javascript:alert(1)").build().validate();
        assertNotNull(error);
        assertEquals(NotificationErrors.INVALID_DEEP_LINK, error.getCode());
    }

    @Test
    public void nonHttpImageIsRejected() {
        NativeApiError error = valid()
                .style(NotificationStyle.BIG_PICTURE)
                .imageUrl("file:///etc/passwd")
                .build()
                .validate();
        assertNotNull(error);
        assertEquals(NotificationErrors.INVALID_IMAGE, error.getCode());
    }

    @Test
    public void scheduleInThePastIsRejected() {
        NativeApiError error = valid()
                .scheduledAt(System.currentTimeMillis() - 60_000L)
                .build()
                .validate();
        assertNotNull(error);
        assertEquals(NotificationErrors.SCHEDULE_INVALID, error.getCode());
    }

    @Test
    public void scheduleBeyondMaximumIsRejected() {
        NativeApiError error = valid()
                .scheduledAt(System.currentTimeMillis()
                        + NotificationLimits.MAX_SCHEDULE_DELAY_MS + 60_000L)
                .build()
                .validate();
        assertNotNull(error);
        assertEquals(NotificationErrors.SCHEDULE_INVALID, error.getCode());
    }
}
