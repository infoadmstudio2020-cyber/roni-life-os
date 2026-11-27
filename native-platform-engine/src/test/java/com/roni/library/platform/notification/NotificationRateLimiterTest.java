package com.roni.library.platform.notification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.roni.library.contracts.notification.NotificationLimits;

import org.junit.Test;

/** Spam prevention: a fixed window per project, independent between projects. */
public class NotificationRateLimiterTest {

    @Test
    public void allowsUpToTheWindowLimit() {
        NotificationRateLimiter limiter = new NotificationRateLimiter(60_000L, 3);
        assertTrue(limiter.tryAcquire("p1", 1_000L));
        assertTrue(limiter.tryAcquire("p1", 1_001L));
        assertTrue(limiter.tryAcquire("p1", 1_002L));
        assertFalse(limiter.tryAcquire("p1", 1_003L));
        assertEquals(0, limiter.remaining("p1", 1_003L));
    }

    @Test
    public void windowSlidesAfterExpiry() {
        NotificationRateLimiter limiter = new NotificationRateLimiter(1_000L, 1);
        assertTrue(limiter.tryAcquire("p1", 0L));
        assertFalse(limiter.tryAcquire("p1", 500L));
        assertTrue(limiter.tryAcquire("p1", 1_001L));
    }

    @Test
    public void projectsAreIsolated() {
        NotificationRateLimiter limiter = new NotificationRateLimiter(60_000L, 1);
        assertTrue(limiter.tryAcquire("p1", 0L));
        assertFalse(limiter.tryAcquire("p1", 1L));
        assertTrue(limiter.tryAcquire("p2", 2L));
    }

    @Test
    public void defaultLimitsMatchTheContract() {
        NotificationRateLimiter limiter = new NotificationRateLimiter();
        for (int i = 0; i < NotificationLimits.RATE_MAX_PER_WINDOW; i++) {
            assertTrue(limiter.tryAcquire("p1", i));
        }
        assertFalse(limiter.tryAcquire("p1", NotificationLimits.RATE_MAX_PER_WINDOW));
    }
}
