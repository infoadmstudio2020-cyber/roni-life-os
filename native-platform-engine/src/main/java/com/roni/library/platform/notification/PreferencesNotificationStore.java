package com.roni.library.platform.notification;

import androidx.annotation.NonNull;

import com.roni.library.contracts.notification.NotificationPreferences;
import com.roni.library.contracts.notification.NotificationRecord;
import com.roni.library.contracts.notification.NotificationRequest;
import com.roni.library.storage.preferences.EncryptedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link NotificationStore} backed by the existing
 * {@link EncryptedPreferences} of {@code :native-storage-engine}.
 *
 * <p>No new storage engine is introduced: notification metadata is just another encrypted
 * preference file, which means it inherits the same key management, backup rules and project
 * scoping as the rest of the runtime.</p>
 */
public final class PreferencesNotificationStore implements NotificationStore {

    private static final String KEY_HISTORY = "history";
    private static final String KEY_SCHEDULED = "scheduled";
    private static final String KEY_PREFERENCES = "preferences";
    private static final String KEY_NEXT_ID = "next_id";

    private final EncryptedPreferences preferences;

    public PreferencesNotificationStore(@NonNull EncryptedPreferences preferences) {
        this.preferences = preferences;
    }

    @NonNull
    @Override
    public List<NotificationRecord> loadHistory() {
        List<NotificationRecord> records = new ArrayList<>();
        String raw = preferences.getString(KEY_HISTORY, "");
        if (raw.isEmpty()) {
            return records;
        }
        for (String line : raw.split("\n", -1)) {
            NotificationRecord record = NotificationCodec.decodeRecord(line);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    @Override
    public void saveHistory(@NonNull List<NotificationRecord> records) {
        StringBuilder out = new StringBuilder();
        for (NotificationRecord record : records) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(NotificationCodec.encodeRecord(record));
        }
        preferences.putString(KEY_HISTORY, out.toString());
        preferences.flush();
    }

    @NonNull
    @Override
    public List<NotificationRequest> loadScheduled() {
        List<NotificationRequest> requests = new ArrayList<>();
        String raw = preferences.getString(KEY_SCHEDULED, "");
        if (raw.isEmpty()) {
            return requests;
        }
        for (String line : raw.split("\n", -1)) {
            NotificationRequest request = NotificationCodec.decodeRequest(line);
            if (request != null) {
                requests.add(request);
            }
        }
        return requests;
    }

    @Override
    public void saveScheduled(@NonNull List<NotificationRequest> requests) {
        StringBuilder out = new StringBuilder();
        for (NotificationRequest request : requests) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(NotificationCodec.encodeRequest(request));
        }
        preferences.putString(KEY_SCHEDULED, out.toString());
        preferences.flush();
    }

    @NonNull
    @Override
    public NotificationPreferences loadPreferences() {
        return NotificationCodec.decodePreferences(preferences.getString(KEY_PREFERENCES, ""));
    }

    @Override
    public void savePreferences(@NonNull NotificationPreferences value) {
        preferences.putString(KEY_PREFERENCES, NotificationCodec.encodePreferences(value));
        preferences.flush();
    }

    @Override
    public synchronized long nextId() {
        long id = preferences.getLong(KEY_NEXT_ID, 1L);
        preferences.putLong(KEY_NEXT_ID, id + 1L);
        preferences.flush();
        return id;
    }
}
