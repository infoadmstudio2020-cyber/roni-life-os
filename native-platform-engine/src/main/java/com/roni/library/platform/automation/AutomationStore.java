package com.roni.library.platform.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.roni.library.contracts.automation.AlarmSpec;
import com.roni.library.contracts.automation.AutomationEvent;
import com.roni.library.contracts.automation.AutomationLimits;
import com.roni.library.contracts.automation.BackgroundTask;
import com.roni.library.contracts.automation.JobSpec;
import com.roni.library.storage.preferences.EncryptedPreferences;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence for automation metadata.
 *
 * <p>Records are stored as validated JSON through Gson inside the existing
 * {@link EncryptedPreferences} store, so no second storage engine is introduced. When the
 * encrypted store is unavailable the same API degrades to an in-memory map.</p>
 */
public final class AutomationStore {

    /** Minimal key/value seam so the store can be tested without Android crypto. */
    public interface KeyValueStore {
        @Nullable
        String get(@NonNull String key);

        void put(@NonNull String key, @NonNull String value);

        void remove(@NonNull String key);
    }

    private static final String TASKS = "tasks:";
    private static final String JOBS = "jobs:";
    private static final String ALARMS = "alarms:";
    private static final String EVENTS = "events:";
    private static final String SEQUENCE = "sequence:";
    private static final String PROJECTS = "projects";

    private static final Type MAP_LIST = new TypeToken<List<Map<String, Object>>>() {
    }.getType();

    private static final Type STRING_LIST = new TypeToken<List<String>>() {
    }.getType();

    private final Gson gson = new Gson();
    private final KeyValueStore store;

    public AutomationStore(@Nullable EncryptedPreferences preferences) {
        this(preferences == null ? new MemoryKeyValueStore() : new PreferencesKeyValueStore(preferences));
    }

    public AutomationStore(@NonNull KeyValueStore store) {
        this.store = store;
    }

    // ------------------------------------------------------------------
    // tasks
    // ------------------------------------------------------------------

    public synchronized void saveTasks(@NonNull String projectId,
                                       @NonNull List<BackgroundTask> tasks) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BackgroundTask task : tasks) {
            rows.add(task.toMap());
        }
        write(TASKS + projectId, rows);
        registerProject(projectId);
    }

    @NonNull
    public synchronized List<BackgroundTask> loadTasks(@NonNull String projectId) {
        List<BackgroundTask> tasks = new ArrayList<>();
        for (Map<String, Object> row : read(TASKS + projectId)) {
            tasks.add(taskFrom(row));
        }
        return tasks;
    }

    // ------------------------------------------------------------------
    // jobs
    // ------------------------------------------------------------------

    public synchronized void saveJobs(@NonNull String projectId, @NonNull List<JobSpec> jobs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JobSpec job : jobs) {
            rows.add(job.toMap());
        }
        write(JOBS + projectId, rows);
        registerProject(projectId);
    }

    @NonNull
    public synchronized List<JobSpec> loadJobs(@NonNull String projectId) {
        List<JobSpec> jobs = new ArrayList<>();
        for (Map<String, Object> row : read(JOBS + projectId)) {
            jobs.add(new JobSpec.Builder()
                    .id(string(row, "id"))
                    .name(string(row, "name"))
                    .workerType(string(row, "workerType"))
                    .requiresNetwork(bool(row, "requiresNetwork"))
                    .requiresCharging(bool(row, "requiresCharging"))
                    .requiresIdle(bool(row, "requiresIdle"))
                    .initialDelayMs(number(row, "initialDelayMs"))
                    .repeatIntervalMs(number(row, "repeatIntervalMs"))
                    .maxAttempts((int) number(row, "maxAttempts"))
                    .payload(map(row, "payload"))
                    .build());
        }
        return jobs;
    }

    // ------------------------------------------------------------------
    // alarms
    // ------------------------------------------------------------------

    public synchronized void saveAlarms(@NonNull String projectId,
                                        @NonNull List<AlarmSpec> alarms) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AlarmSpec alarm : alarms) {
            rows.add(alarm.toMap());
        }
        write(ALARMS + projectId, rows);
        registerProject(projectId);
    }

    @NonNull
    public synchronized List<AlarmSpec> loadAlarms(@NonNull String projectId) {
        List<AlarmSpec> alarms = new ArrayList<>();
        for (Map<String, Object> row : read(ALARMS + projectId)) {
            alarms.add(new AlarmSpec.Builder()
                    .id(string(row, "id"))
                    .label(string(row, "label"))
                    .triggerAt(number(row, "triggerAt"))
                    .intervalMs(number(row, "intervalMs"))
                    .exact(bool(row, "exact"))
                    .payload(map(row, "payload"))
                    .build());
        }
        return alarms;
    }

    // ------------------------------------------------------------------
    // events
    // ------------------------------------------------------------------

    public synchronized void appendEvent(@NonNull AutomationEvent event) {
        List<Map<String, Object>> rows = read(EVENTS + event.getProjectId());
        rows.add(event.toMap());
        while (rows.size() > AutomationLimits.MAX_EVENTS) {
            rows.remove(0);
        }
        write(EVENTS + event.getProjectId(), rows);
    }

    @NonNull
    public synchronized List<AutomationEvent> loadEvents(@NonNull String projectId,
                                                         long sinceSequence, int limit) {
        List<AutomationEvent> events = new ArrayList<>();
        for (Map<String, Object> row : read(EVENTS + projectId)) {
            long sequence = number(row, "sequence");
            if (sequence <= sinceSequence) {
                continue;
            }
            events.add(new AutomationEvent(
                    string(row, "id"),
                    string(row, "projectId"),
                    string(row, "topic"),
                    map(row, "payload"),
                    number(row, "timestamp"),
                    sequence));
            if (events.size() >= limit) {
                break;
            }
        }
        return events;
    }

    public synchronized void clearEvents(@NonNull String projectId) {
        store.remove(EVENTS + projectId);
    }

    /** Records a project that owns automation state, so boot restore can find it. */
    public synchronized void registerProject(@NonNull String projectId) {
        if (projectId.trim().isEmpty()) {
            return;
        }
        List<String> projects = knownProjects();
        if (!projects.contains(projectId)) {
            projects.add(projectId);
            store.put(PROJECTS, gson.toJson(projects));
        }
    }

    @NonNull
    public synchronized List<String> knownProjects() {
        String raw = store.get(PROJECTS);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<String> parsed = gson.fromJson(raw, STRING_LIST);
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Throwable error) {
            return new ArrayList<>();
        }
    }

    public synchronized long nextSequence(@NonNull String projectId) {
        String key = SEQUENCE + projectId;
        String raw = store.get(key);
        long value = 1L;
        if (raw != null) {
            try {
                value = Long.parseLong(raw) + 1L;
            } catch (NumberFormatException ignored) {
                value = 1L;
            }
        }
        store.put(key, String.valueOf(value));
        return value;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void write(@NonNull String key, @NonNull List<Map<String, Object>> rows) {
        store.put(key, gson.toJson(rows));
    }

    @NonNull
    private List<Map<String, Object>> read(@NonNull String key) {
        String raw = store.get(key);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> rows = gson.fromJson(raw, MAP_LIST);
            return rows == null ? new ArrayList<>() : rows;
        } catch (JsonSyntaxException error) {
            return new ArrayList<>();
        }
    }

    @NonNull
    private static BackgroundTask taskFrom(@NonNull Map<String, Object> row) {
        return new BackgroundTask.Builder()
                .id(string(row, "id"))
                .projectId(string(row, "projectId"))
                .name(string(row, "name"))
                .type(string(row, "type"))
                .state(string(row, "state"))
                .progress((int) number(row, "progress"))
                .attempts((int) number(row, "attempts"))
                .maxAttempts((int) number(row, "maxAttempts"))
                .payload(map(row, "payload"))
                .createdAt(number(row, "createdAt"))
                .startedAt(number(row, "startedAt"))
                .finishedAt(number(row, "finishedAt"))
                .lastError(row.get("lastError") == null ? null : String.valueOf(row.get("lastError")))
                .result(map(row, "result"))
                .build();
    }

    @NonNull
    private static String string(@NonNull Map<String, Object> row, @NonNull String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(@NonNull Map<String, Object> row, @NonNull String key) {
        Object value = row.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static boolean bool(@NonNull Map<String, Object> row, @NonNull String key) {
        Object value = row.get(key);
        return value instanceof Boolean && (Boolean) value;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private static Map<String, Object> map(@NonNull Map<String, Object> row,
                                           @NonNull String key) {
        Object value = row.get(key);
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        }
        return new LinkedHashMap<>();
    }

    /** Adapter over the existing encrypted preference store. */
    public static final class PreferencesKeyValueStore implements KeyValueStore {

        private final EncryptedPreferences preferences;

        public PreferencesKeyValueStore(@NonNull EncryptedPreferences preferences) {
            this.preferences = preferences;
        }

        @Nullable
        @Override
        public String get(@NonNull String key) {
            String value = preferences.getString(key, "");
            return value.isEmpty() ? null : value;
        }

        @Override
        public void put(@NonNull String key, @NonNull String value) {
            preferences.putString(key, value);
        }

        @Override
        public void remove(@NonNull String key) {
            preferences.remove(key);
        }
    }

    /** Fallback used when the encrypted store is not available. */
    public static final class MemoryKeyValueStore implements KeyValueStore {

        private final Map<String, String> values = new ConcurrentHashMap<>();

        @Nullable
        @Override
        public String get(@NonNull String key) {
            return values.get(key);
        }

        @Override
        public void put(@NonNull String key, @NonNull String value) {
            values.put(key, value);
        }

        @Override
        public void remove(@NonNull String key) {
            values.remove(key);
        }
    }
}
