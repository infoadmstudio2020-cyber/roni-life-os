package com.roni.library.platform.notification;

import androidx.annotation.NonNull;

import com.roni.library.contracts.notification.NotificationPreferences;
import com.roni.library.contracts.notification.NotificationRecord;
import com.roni.library.contracts.notification.NotificationRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Volatile {@link NotificationStore} used when the storage engine is unavailable and by tests.
 *
 * <p>It keeps the engine fully functional for the lifetime of the process instead of failing
 * every notification when persistence cannot be opened.</p>
 */
public final class InMemoryNotificationStore implements NotificationStore {

    private final AtomicLong ids = new AtomicLong(1L);
    private List<NotificationRecord> history = new ArrayList<>();
    private List<NotificationRequest> scheduled = new ArrayList<>();
    private NotificationPreferences preferences = NotificationPreferences.defaults();

    @NonNull
    @Override
    public synchronized List<NotificationRecord> loadHistory() {
        return new ArrayList<>(history);
    }

    @Override
    public synchronized void saveHistory(@NonNull List<NotificationRecord> records) {
        history = new ArrayList<>(records);
    }

    @NonNull
    @Override
    public synchronized List<NotificationRequest> loadScheduled() {
        return new ArrayList<>(scheduled);
    }

    @Override
    public synchronized void saveScheduled(@NonNull List<NotificationRequest> requests) {
        scheduled = new ArrayList<>(requests);
    }

    @NonNull
    @Override
    public synchronized NotificationPreferences loadPreferences() {
        return preferences;
    }

    @Override
    public synchronized void savePreferences(@NonNull NotificationPreferences value) {
        preferences = value;
    }

    @Override
    public long nextId() {
        return ids.getAndIncrement();
    }
}
