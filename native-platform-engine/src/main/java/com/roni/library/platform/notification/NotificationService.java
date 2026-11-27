package com.roni.library.platform.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.notification.NotificationChannelSpec;
import com.roni.library.contracts.notification.NotificationErrors;
import com.roni.library.contracts.notification.NotificationLimits;
import com.roni.library.contracts.notification.NotificationPermissionState;
import com.roni.library.contracts.notification.NotificationPreferences;
import com.roni.library.contracts.notification.NotificationPriority;
import com.roni.library.contracts.notification.NotificationRecord;
import com.roni.library.contracts.notification.NotificationRequest;
import com.roni.library.contracts.notification.NotificationStyle;
import com.roni.library.contracts.notification.NotificationVisibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central notification engine.
 *
 * <p>Owns every policy decision: validation, project isolation, user preferences, rate limiting,
 * history, scheduling and lifecycle. The Android mechanics live behind {@link NotificationBackend}
 * and {@link NotificationScheduler}, which is what allows this class to run — and be tested — on a
 * plain JVM.</p>
 *
 * <p>Isolation rule: a notification is identified by {@code (projectId, key)}. Every mutating
 * operation resolves the record first and rejects the call when the record belongs to another
 * project, so a web page can never cancel or update a sibling project's notification even if it
 * guesses the key.</p>
 */
public final class NotificationService {

    /** Channel used when a request does not name one. */
    public static final String DEFAULT_CHANNEL = "default";

    private final NotificationBackend backend;
    private final NotificationScheduler scheduler;
    private final NotificationStore store;
    private final NotificationRateLimiter rateLimiter = new NotificationRateLimiter();
    private final List<NotificationListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, NotificationRecord> active = new ConcurrentHashMap<>();
    private final Map<Integer, NotificationRequest> scheduled = new ConcurrentHashMap<>();
    private final List<NotificationRecord> history = new ArrayList<>();

    private volatile NotificationPreferences preferences = NotificationPreferences.defaults();
    private volatile String appScheme = "";
    private volatile boolean initialized;
    private volatile boolean enabled = true;

    public NotificationService(@NonNull NotificationBackend backend,
                               @NonNull NotificationScheduler scheduler,
                               @NonNull NotificationStore store) {
        this.backend = backend;
        this.scheduler = scheduler;
        this.store = store;
    }

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------

    /** Loads persisted state and re-arms every scheduled notification that is still pending. */
    public synchronized void init() {
        if (initialized) {
            return;
        }
        preferences = store.loadPreferences();
        history.clear();
        history.addAll(store.loadHistory());
        active.clear();
        for (NotificationRecord record : history) {
            if (!record.isCancelled()) {
                active.put(key(record.getProjectId(), record.getKey()), record);
            }
        }
        scheduled.clear();
        for (NotificationRequest request : store.loadScheduled()) {
            scheduled.put(notificationId(request.getProjectId(), request.getKey()), request);
        }
        initialized = true;
        restoreScheduled();
    }

    /**
     * Re-arms pending alarms after a process restart.
     *
     * <p>A request whose time already passed while the process was dead is delivered immediately
     * when it is less than one hour late, and dropped otherwise: a burst of stale notifications
     * after a reboot is worse than losing them.</p>
     */
    public synchronized void restoreScheduled() {
        long now = System.currentTimeMillis();
        List<Integer> expired = new ArrayList<>();
        for (Map.Entry<Integer, NotificationRequest> entry : scheduled.entrySet()) {
            NotificationRequest request = entry.getValue();
            long delay = request.getScheduledAt() - now;
            if (delay > 0L) {
                scheduler.schedule(request, entry.getKey());
            } else if (delay > -3_600_000L) {
                expired.add(entry.getKey());
            } else {
                expired.add(entry.getKey());
            }
        }
        for (Integer id : expired) {
            NotificationRequest request = scheduled.remove(id);
            if (request == null) {
                continue;
            }
            long delay = request.getScheduledAt() - now;
            if (delay > -3_600_000L) {
                try {
                    deliver(request, true);
                } catch (NotificationException ignored) {
                    // A stale notification that can no longer be delivered is simply dropped.
                }
            }
        }
        persistScheduled();
    }

    /** Releases listeners and in-memory state; persisted data is kept. */
    public synchronized void release() {
        listeners.clear();
        active.clear();
        scheduled.clear();
        history.clear();
        rateLimiter.resetAll();
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /** Engine-level kill switch, independent of the user preference. */
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Scheme accepted for absolute deep links (for example {@code ronilife}). */
    public void setAppScheme(@Nullable String scheme) {
        this.appScheme = scheme != null ? scheme.trim().toLowerCase(Locale.US) : "";
    }

    // ------------------------------------------------------------------
    // delivery
    // ------------------------------------------------------------------

    /**
     * Posts or replaces a notification.
     *
     * @throws NotificationException when validation, preferences, permission or rate limiting
     *                               reject the request
     */
    @NonNull
    public NotificationRecord post(@NonNull NotificationRequest request) throws NotificationException {
        ensureReady();
        validate(request);
        return deliver(request, false);
    }

    /**
     * Updates an existing notification. The record must already exist and belong to the caller.
     */
    @NonNull
    public NotificationRecord update(@NonNull NotificationRequest request) throws NotificationException {
        ensureReady();
        validate(request);
        NotificationRecord existing = active.get(key(request.getProjectId(), request.getKey()));
        if (existing == null) {
            throw new NotificationException(NotificationErrors.NOT_FOUND,
                    "No active notification for key " + request.getKey());
        }
        if (!existing.getProjectId().equals(request.getProjectId())) {
            throw new NotificationException(NotificationErrors.FORBIDDEN,
                    "Notification belongs to another project");
        }
        return deliver(request, false);
    }

    /**
     * Cancels one notification owned by {@code projectId}.
     *
     * @return the cancelled record
     */
    @NonNull
    public NotificationRecord cancel(@NonNull String projectId, @NonNull String notificationKey)
            throws NotificationException {
        ensureReady();
        NotificationRecord record = active.get(key(projectId, notificationKey));
        if (record == null) {
            throw new NotificationException(NotificationErrors.NOT_FOUND,
                    "No active notification for key " + notificationKey);
        }
        if (!record.getProjectId().equals(projectId)) {
            throw new NotificationException(NotificationErrors.FORBIDDEN,
                    "Notification belongs to another project");
        }
        backend.cancel(notificationId(projectId, notificationKey));
        NotificationRecord cancelled = record.withCancelled(true);
        active.remove(key(projectId, notificationKey));
        replaceHistory(cancelled);
        persistHistory();
        notifyCancelled(cancelled);
        return cancelled;
    }

    /**
     * Cancels every notification owned by {@code projectId}.
     *
     * @return number of cancelled notifications
     */
    public int cancelAll(@NonNull String projectId) {
        ensureReadyQuietly();
        int count = 0;
        for (NotificationRecord record : new ArrayList<>(active.values())) {
            if (!record.getProjectId().equals(projectId)) {
                continue;
            }
            backend.cancel(notificationId(record.getProjectId(), record.getKey()));
            NotificationRecord cancelled = record.withCancelled(true);
            active.remove(key(record.getProjectId(), record.getKey()));
            replaceHistory(cancelled);
            notifyCancelled(cancelled);
            count++;
        }
        if (count > 0) {
            persistHistory();
        }
        return count;
    }

    /** Cancels every notification of every project (used when the user clears the shade). */
    public int cancelAll() {
        ensureReadyQuietly();
        int count = 0;
        for (NotificationRecord record : new ArrayList<>(active.values())) {
            backend.cancel(notificationId(record.getProjectId(), record.getKey()));
            NotificationRecord cancelled = record.withCancelled(true);
            active.remove(key(record.getProjectId(), record.getKey()));
            replaceHistory(cancelled);
            notifyCancelled(cancelled);
            count++;
        }
        if (count > 0) {
            persistHistory();
        }
        return count;
    }

    // ------------------------------------------------------------------
    // scheduling
    // ------------------------------------------------------------------

    /**
     * Queues a notification for later delivery.
     *
     * @return the notification id the alarm will use
     */
    public int schedule(@NonNull NotificationRequest request) throws NotificationException {
        ensureReady();
        validate(request);
        if (!request.isScheduled()) {
            throw new NotificationException(NotificationErrors.SCHEDULE_INVALID,
                    "scheduledAt is required");
        }
        checkPreferences(request);
        if (!rateLimiter.tryAcquire(request.getProjectId(), System.currentTimeMillis())) {
            throw new NotificationException(NotificationErrors.RATE_LIMITED,
                    "Too many notifications for this project");
        }
        if (scheduled.size() >= NotificationLimits.MAX_SCHEDULED) {
            throw new NotificationException(NotificationErrors.SCHEDULE_LIMIT,
                    "Scheduled queue is full");
        }
        int id = notificationId(request.getProjectId(), request.getKey());
        scheduled.put(id, request);
        persistScheduled();
        scheduler.schedule(request, id);
        return id;
    }

    /** Cancels a queued notification owned by {@code projectId}. */
    public boolean cancelScheduled(@NonNull String projectId, @NonNull String notificationKey) {
        ensureReadyQuietly();
        int id = notificationId(projectId, notificationKey);
        NotificationRequest request = scheduled.get(id);
        if (request == null || !request.getProjectId().equals(projectId)) {
            return false;
        }
        scheduled.remove(id);
        scheduler.cancel(id);
        persistScheduled();
        return true;
    }

    /**
     * Called by the alarm receiver when a scheduled notification is due.
     *
     * @return the delivered record, or {@code null} when the request is unknown or rejected
     */
    @Nullable
    public NotificationRecord onAlarmFired(int notificationId) {
        ensureReadyQuietly();
        NotificationRequest request = scheduled.remove(notificationId);
        if (request == null) {
            return null;
        }
        persistScheduled();
        try {
            return deliver(request, true);
        } catch (NotificationException error) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // history and read state
    // ------------------------------------------------------------------

    /** @return history visible to one project, newest first */
    @NonNull
    public List<NotificationRecord> getHistory(@NonNull String projectId, boolean includeCancelled) {
        ensureReadyQuietly();
        List<NotificationRecord> result = new ArrayList<>();
        synchronized (this) {
            for (NotificationRecord record : history) {
                if (!record.getProjectId().equals(projectId)) {
                    continue;
                }
                if (!includeCancelled && record.isCancelled()) {
                    continue;
                }
                result.add(record);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /** @return history of every project, newest first (consumer app only) */
    @NonNull
    public List<NotificationRecord> getAllHistory(boolean includeCancelled) {
        ensureReadyQuietly();
        List<NotificationRecord> result = new ArrayList<>();
        synchronized (this) {
            for (NotificationRecord record : history) {
                if (!includeCancelled && record.isCancelled()) {
                    continue;
                }
                result.add(record);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /** @return number of unread records, optionally scoped to one project */
    public int getUnreadCount(@Nullable String projectId) {
        ensureReadyQuietly();
        int count = 0;
        synchronized (this) {
            for (NotificationRecord record : history) {
                if (record.isRead() || record.isCancelled()) {
                    continue;
                }
                if (projectId != null && !record.getProjectId().equals(projectId)) {
                    continue;
                }
                count++;
            }
        }
        return count;
    }

    /** Marks one record as read; ownership is enforced. */
    public boolean markRead(@NonNull String projectId, long recordId) {
        ensureReadyQuietly();
        synchronized (this) {
            for (int i = 0; i < history.size(); i++) {
                NotificationRecord record = history.get(i);
                if (record.getId() != recordId) {
                    continue;
                }
                if (!record.getProjectId().equals(projectId)) {
                    return false;
                }
                history.set(i, record.withRead(true));
                persistHistory();
                return true;
            }
        }
        return false;
    }

    /** Marks every record of one project as read. */
    public int markAllRead(@NonNull String projectId) {
        ensureReadyQuietly();
        int changed = 0;
        synchronized (this) {
            for (int i = 0; i < history.size(); i++) {
                NotificationRecord record = history.get(i);
                if (record.getProjectId().equals(projectId) && !record.isRead()) {
                    history.set(i, record.withRead(true));
                    changed++;
                }
            }
        }
        if (changed > 0) {
            persistHistory();
        }
        return changed;
    }

    /** Removes one record from the history list (does not touch the shade). */
    public boolean removeFromHistory(@NonNull String projectId, long recordId) {
        ensureReadyQuietly();
        synchronized (this) {
            for (int i = 0; i < history.size(); i++) {
                NotificationRecord record = history.get(i);
                if (record.getId() == recordId && record.getProjectId().equals(projectId)) {
                    history.remove(i);
                    persistHistory();
                    return true;
                }
            }
        }
        return false;
    }

    /** Clears the history of one project. */
    public int clearHistory(@NonNull String projectId) {
        ensureReadyQuietly();
        int removed = 0;
        synchronized (this) {
            for (int i = history.size() - 1; i >= 0; i--) {
                if (history.get(i).getProjectId().equals(projectId)) {
                    history.remove(i);
                    removed++;
                }
            }
        }
        if (removed > 0) {
            persistHistory();
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // preferences, channels, permission
    // ------------------------------------------------------------------

    @NonNull
    public NotificationPreferences getPreferences() {
        return preferences;
    }

    /** Replaces the user preferences; the next request is evaluated against the new snapshot. */
    public synchronized void setPreferences(@NonNull NotificationPreferences value) {
        this.preferences = value;
        store.savePreferences(value);
    }

    @NonNull
    public NotificationPermissionState getPermissionState() {
        try {
            return backend.getPermissionState();
        } catch (Throwable error) {
            return NotificationPermissionState.UNKNOWN;
        }
    }

    public boolean areNotificationsEnabled() {
        try {
            return backend.areNotificationsEnabled();
        } catch (Throwable error) {
            return false;
        }
    }

    @NonNull
    public List<NotificationChannelSpec> getChannels() {
        try {
            return backend.getChannels();
        } catch (Throwable error) {
            return Collections.emptyList();
        }
    }

    public void ensureChannel(@NonNull NotificationChannelSpec spec) {
        try {
            backend.ensureChannel(spec);
        } catch (Throwable ignored) {
        }
    }

    /** @return the Android channel id used for a project's logical channel */
    @NonNull
    public static String channelIdFor(@NonNull String projectId, @Nullable String channelId) {
        String logical = channelId == null || channelId.trim().isEmpty() ? DEFAULT_CHANNEL : channelId.trim();
        StringBuilder safe = new StringBuilder(logical.length());
        for (int i = 0; i < logical.length() && safe.length() < 40; i++) {
            char c = Character.toLowerCase(logical.charAt(i));
            safe.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' ? c : '_');
        }
        return "p" + Integer.toHexString(projectId.hashCode()) + "_" + safe;
    }

    /** @return the stable Android notification id for a project-scoped key */
    public static int notificationId(@NonNull String projectId, @NonNull String notificationKey) {
        int hash = (projectId + "|" + notificationKey).hashCode() & 0x7FFFFFFF;
        return hash == 0 ? 1 : hash;
    }

    // ------------------------------------------------------------------
    // listeners
    // ------------------------------------------------------------------

    public void addListener(@Nullable NotificationListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@Nullable NotificationListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /** Dispatches a click to every listener; used by the intent receiver. */
    public void dispatchClick(@NonNull String projectId, @NonNull String notificationKey) {
        NotificationRecord record = active.get(key(projectId, notificationKey));
        if (record == null) {
            return;
        }
        NotificationRecord read = record.withRead(true);
        active.put(key(projectId, notificationKey), read);
        replaceHistory(read);
        persistHistory();
        for (NotificationListener listener : listeners) {
            try {
                listener.onNotificationClicked(read);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Dispatches an action tap to every listener. */
    public void dispatchAction(@NonNull String projectId, @NonNull String notificationKey,
                               @NonNull String actionId) {
        NotificationRecord record = active.get(key(projectId, notificationKey));
        if (record == null) {
            return;
        }
        for (NotificationListener listener : listeners) {
            try {
                listener.onNotificationAction(record, actionId);
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    @NonNull
    private NotificationRecord deliver(@NonNull NotificationRequest request, boolean fromSchedule)
            throws NotificationException {
        checkPreferences(request);
        if (!fromSchedule
                && !rateLimiter.tryAcquire(request.getProjectId(), System.currentTimeMillis())) {
            throw new NotificationException(NotificationErrors.RATE_LIMITED,
                    "Too many notifications for this project");
        }

        NotificationRequest effective = applyPreferences(request);
        int id = notificationId(effective.getProjectId(), effective.getKey());
        NotificationPermissionState permission = getPermissionState();
        boolean canPost = permission.canPost() && areNotificationsEnabled();

        if (canPost) {
            backend.ensureChannel(channelSpecFor(effective));
            backend.post(effective, id);
        }

        NotificationRecord record = new NotificationRecord(
                store.nextId(),
                effective.getProjectId(),
                effective.getKey(),
                effective.getTitle(),
                effective.getBody(),
                effective.getStyle(),
                effective.getChannelId(),
                effective.getPriority(),
                System.currentTimeMillis(),
                false,
                false,
                effective.getGroupKey(),
                effective.getDeepLink(),
                effective.getActions().size(),
                effective.isOngoing(),
                effective.getScheduledAt());

        active.put(key(record.getProjectId(), record.getKey()), record);
        replaceHistory(record);
        persistHistory();
        for (NotificationListener listener : listeners) {
            try {
                listener.onNotificationPosted(record);
            } catch (Throwable ignored) {
            }
        }

        if (!canPost) {
            // The record is kept so the Notification Center can show what was missed, but the
            // caller is told the truth: nothing reached the shade.
            throw new NotificationException(NotificationErrors.PERMISSION_DENIED,
                    "Notification permission is not granted");
        }
        return record;
    }

    private void checkPreferences(@NonNull NotificationRequest request) throws NotificationException {
        if (!enabled) {
            throw new NotificationException(NotificationErrors.DISABLED, "Notification engine is disabled");
        }
        if (!preferences.isGlobalEnabled()) {
            throw new NotificationException(NotificationErrors.DISABLED, "Notifications are disabled");
        }
        if (!preferences.isProjectAllowed(request.getProjectId())) {
            throw new NotificationException(NotificationErrors.PROJECT_BLOCKED,
                    "Notifications are blocked for this project");
        }
        if (!preferences.isChannelAllowed(request.getProjectId(), request.getChannelId())) {
            throw new NotificationException(NotificationErrors.CHANNEL_BLOCKED,
                    "This channel is blocked for this project");
        }
    }

    @NonNull
    private NotificationRequest applyPreferences(@NonNull NotificationRequest request) {
        boolean sound = request.isSound() && preferences.isSoundAllowed(request.getProjectId());
        boolean vibration = request.isVibration() && preferences.isVibrationAllowed(request.getProjectId());
        if (sound == request.isSound() && vibration == request.isVibration()) {
            return request;
        }
        return request.toBuilder().sound(sound).vibration(vibration).build();
    }

    @NonNull
    private NotificationChannelSpec channelSpecFor(@NonNull NotificationRequest request) {
        return new NotificationChannelSpec(
                channelIdFor(request.getProjectId(), request.getChannelId()),
                request.getChannelId().isEmpty() ? "General" : request.getChannelId(),
                "Notifications from " + request.getProjectId(),
                request.getPriority(),
                request.getVisibility(),
                request.isSound(),
                request.isVibration(),
                request.getPriority().getRank() >= NotificationPriority.HIGH.getRank());
    }

    private void validate(@NonNull NotificationRequest request) throws NotificationException {
        NativeApiError error = request.validate();
        if (error != null) {
            throw new NotificationException(error.getCode(), error.getMessage());
        }
        String link = request.getDeepLink();
        if (!link.isEmpty() && !link.startsWith("/")) {
            int colon = link.indexOf(':');
            String scheme = colon > 0 ? link.substring(0, colon).toLowerCase(Locale.US) : "";
            if (!appScheme.isEmpty() && !scheme.equals(appScheme)) {
                throw new NotificationException(NotificationErrors.INVALID_DEEP_LINK,
                        "deepLink scheme is not the app scheme");
            }
        }
    }

    private void ensureReady() throws NotificationException {
        if (!initialized) {
            init();
        }
        if (!backend.isSupported()) {
            throw new NotificationException(NotificationErrors.ENGINE_UNAVAILABLE,
                    "Notification backend is unavailable");
        }
    }

    private void ensureReadyQuietly() {
        if (!initialized) {
            try {
                init();
            } catch (Throwable ignored) {
            }
        }
    }

    private synchronized void replaceHistory(@NonNull NotificationRecord record) {
        for (int i = 0; i < history.size(); i++) {
            NotificationRecord existing = history.get(i);
            if (existing.getId() == record.getId()) {
                history.set(i, record);
                return;
            }
        }
        history.add(record);
        while (history.size() > NotificationLimits.MAX_HISTORY) {
            history.remove(0);
        }
    }

    private void persistHistory() {
        try {
            List<NotificationRecord> snapshot;
            synchronized (this) {
                snapshot = new ArrayList<>(history);
            }
            store.saveHistory(snapshot);
        } catch (Throwable ignored) {
        }
    }

    private void persistScheduled() {
        try {
            store.saveScheduled(new ArrayList<>(scheduled.values()));
        } catch (Throwable ignored) {
        }
    }

    private void notifyCancelled(@NonNull NotificationRecord record) {
        for (NotificationListener listener : listeners) {
            try {
                listener.onNotificationCancelled(record);
            } catch (Throwable ignored) {
            }
        }
    }

    @NonNull
    private static String key(@NonNull String projectId, @NonNull String notificationKey) {
        return projectId + "|" + notificationKey;
    }
}
