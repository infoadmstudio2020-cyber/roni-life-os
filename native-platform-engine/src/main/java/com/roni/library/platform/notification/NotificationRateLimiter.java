package com.roni.library.platform.notification;

import androidx.annotation.NonNull;

import com.roni.library.contracts.notification.NotificationLimits;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Sliding-window rate limiter, one window per project.
 *
 * <p>A web page that loops on {@code notification.post} must not be able to flood the shade.
 * The limiter is intentionally per project rather than global: one noisy project cannot starve
 * the others.</p>
 */
public final class NotificationRateLimiter {

    private final long windowMs;
    private final int maxPerWindow;
    private final Map<String, Deque<Long>> windows = new HashMap<>();

    public NotificationRateLimiter() {
        this(NotificationLimits.RATE_WINDOW_MS, NotificationLimits.RATE_MAX_PER_WINDOW);
    }

    public NotificationRateLimiter(long windowMs, int maxPerWindow) {
        this.windowMs = Math.max(1L, windowMs);
        this.maxPerWindow = Math.max(1, maxPerWindow);
    }

    /**
     * Records one attempt.
     *
     * @return {@code true} when the attempt is allowed, {@code false} when the project is over
     *         its budget for the current window
     */
    public synchronized boolean tryAcquire(@NonNull String projectId, long now) {
        Deque<Long> window = windows.get(projectId);
        if (window == null) {
            window = new ArrayDeque<>();
            windows.put(projectId, window);
        }
        long cutoff = now - windowMs;
        while (!window.isEmpty() && window.peekFirst() < cutoff) {
            window.pollFirst();
        }
        if (window.size() >= maxPerWindow) {
            return false;
        }
        window.addLast(now);
        return true;
    }

    /** @return remaining budget for the project in the current window */
    public synchronized int remaining(@NonNull String projectId, long now) {
        Deque<Long> window = windows.get(projectId);
        if (window == null) {
            return maxPerWindow;
        }
        long cutoff = now - windowMs;
        while (!window.isEmpty() && window.peekFirst() < cutoff) {
            window.pollFirst();
        }
        return Math.max(0, maxPerWindow - window.size());
    }

    /** Drops the window of one project (used when a project is closed). */
    public synchronized void reset(@NonNull String projectId) {
        windows.remove(projectId);
    }

    /** Drops every window. */
    public synchronized void resetAll() {
        windows.clear();
    }
}
