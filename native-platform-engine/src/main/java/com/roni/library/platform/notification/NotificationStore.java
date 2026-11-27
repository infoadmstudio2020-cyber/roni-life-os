package com.roni.library.platform.notification;

import androidx.annotation.NonNull;

import com.roni.library.contracts.notification.NotificationPreferences;
import com.roni.library.contracts.notification.NotificationRecord;
import com.roni.library.contracts.notification.NotificationRequest;

import java.util.List;

/**
 * Persistence boundary for notification metadata.
 *
 * <p>The production implementation is backed by the existing
 * {@code com.roni.library.storage.preferences.EncryptedPreferences} of
 * {@code :native-storage-engine}; no second storage engine is introduced.</p>
 */
public interface NotificationStore {

    @NonNull
    List<NotificationRecord> loadHistory();

    void saveHistory(@NonNull List<NotificationRecord> records);

    @NonNull
    List<NotificationRequest> loadScheduled();

    void saveScheduled(@NonNull List<NotificationRequest> requests);

    @NonNull
    NotificationPreferences loadPreferences();

    void savePreferences(@NonNull NotificationPreferences preferences);

    /** @return a monotonically increasing record id */
    long nextId();
}
