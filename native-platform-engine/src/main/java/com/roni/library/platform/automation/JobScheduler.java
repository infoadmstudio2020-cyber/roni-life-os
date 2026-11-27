package com.roni.library.platform.automation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.gson.Gson;
import com.roni.library.contracts.automation.AutomationErrorCodes;
import com.roni.library.contracts.automation.AutomationLimits;
import com.roni.library.contracts.automation.JobSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Deferred and constraint based execution on top of AndroidX WorkManager.
 *
 * <p>WorkManager owns persistence and process death recovery; this class validates specs,
 * keeps the project scoped registry and publishes lifecycle events.</p>
 */
public final class JobScheduler {

    /** Seam so the scheduler can be exercised without WorkManager. */
    public interface Backend {
        boolean isAvailable();

        void enqueue(@NonNull String projectId, @NonNull JobSpec spec);

        void cancel(@NonNull String projectId, @NonNull String jobId);
    }

    private final AutomationStore store;
    private final EventEngine events;
    private final Backend backend;
    private final Map<String, JobSpec> jobs = new LinkedHashMap<>();
    private final Gson gson = new Gson();

    public JobScheduler(@NonNull AutomationStore store, @NonNull EventEngine events,
                        @Nullable Context context) {
        this(store, events, context == null ? new UnavailableBackend()
                : new WorkManagerBackend(context.getApplicationContext(), new Gson()));
    }

    /** Test seam: inject a backend without WorkManager. */
    public JobScheduler(@NonNull AutomationStore store, @NonNull EventEngine events,
                        @NonNull Backend backend) {
        this.store = store;
        this.events = events;
        this.backend = backend;
    }

    public boolean isAvailable() {
        return backend.isAvailable();
    }

    @NonNull
    public JobSpec schedule(@NonNull String projectId, @NonNull JobSpec spec)
            throws AutomationException {
        if (projectId.trim().isEmpty()) {
            throw new AutomationException(AutomationErrorCodes.NO_ACTIVE_PROJECT,
                    "No active project");
        }
        if (spec.getId().trim().isEmpty() || spec.getName().trim().isEmpty()) {
            throw AutomationException.invalid("Job id and name are required");
        }
        if (spec.getWorkerType().trim().isEmpty()) {
            throw AutomationException.invalid("A worker type is required");
        }
        if (spec.isPeriodic()
                && spec.getRepeatIntervalMs() < AutomationLimits.MIN_REPEAT_INTERVAL_MS) {
            throw AutomationException.invalid("Repeat interval must be at least 15 minutes");
        }
        List<JobSpec> existing = list(projectId);
        if (existing.size() >= AutomationLimits.MAX_JOBS) {
            throw new AutomationException(AutomationErrorCodes.JOB_LIMIT, "Job limit reached");
        }
        jobs.put(key(projectId, spec.getId()), spec);
        persist(projectId);
        if (backend.isAvailable()) {
            backend.enqueue(projectId, spec);
            events.emit(projectId, EventEngine.TOPIC_JOB, "scheduled", spec.toMap());
        } else {
            events.emit(projectId, EventEngine.TOPIC_JOB, "deferred", spec.toMap());
        }
        return spec;
    }

    public boolean cancel(@NonNull String projectId, @NonNull String jobId) {
        JobSpec spec = jobs.remove(key(projectId, jobId));
        if (spec == null) {
            return false;
        }
        backend.cancel(projectId, jobId);
        persist(projectId);
        events.emit(projectId, EventEngine.TOPIC_JOB, "cancelled", spec.toMap());
        return true;
    }

    @NonNull
    public List<JobSpec> list(@NonNull String projectId) {
        List<JobSpec> result = new ArrayList<>();
        for (Map.Entry<String, JobSpec> entry : jobs.entrySet()) {
            if (entry.getKey().startsWith(projectId + "|")) {
                result.add(entry.getValue());
            }
        }
        if (result.isEmpty()) {
            result.addAll(store.loadJobs(projectId));
        }
        return result;
    }

    public void restore(@NonNull String projectId) {
        for (JobSpec spec : store.loadJobs(projectId)) {
            jobs.put(key(projectId, spec.getId()), spec);
        }
    }

    private void persist(@NonNull String projectId) {
        store.saveJobs(projectId, snapshot(projectId));
    }

    @NonNull
    private List<JobSpec> snapshot(@NonNull String projectId) {
        List<JobSpec> result = new ArrayList<>();
        for (Map.Entry<String, JobSpec> entry : jobs.entrySet()) {
            if (entry.getKey().startsWith(projectId + "|")) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    @NonNull
    private static String key(@NonNull String projectId, @NonNull String jobId) {
        return projectId + "|" + jobId;
    }

    /** WorkManager backed implementation. */
    static final class WorkManagerBackend implements Backend {

        private final Context context;
        private final Gson gson;

        WorkManagerBackend(@NonNull Context context, @NonNull Gson gson) {
            this.context = context;
            this.gson = gson;
        }

        @Override
        public boolean isAvailable() {
            try {
                return WorkManager.getInstance(context) != null;
            } catch (Throwable error) {
                return false;
            }
        }

        @Override
        public void enqueue(@NonNull String projectId, @NonNull JobSpec spec) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(spec.requiresNetwork()
                            ? NetworkType.CONNECTED : NetworkType.NOT_REQUIRED)
                    .setRequiresCharging(spec.requiresCharging())
                    .setRequiresDeviceIdle(spec.requiresIdle())
                    .build();
            Data input = new Data.Builder()
                    .putString(AutomationWorker.KEY_PROJECT_ID, projectId)
                    .putString(AutomationWorker.KEY_JOB_ID, spec.getId())
                    .putString(AutomationWorker.KEY_WORKER_TYPE, spec.getWorkerType())
                    .putString(AutomationWorker.KEY_PAYLOAD, gson.toJson(spec.getPayload()))
                    .build();
            WorkManager workManager = WorkManager.getInstance(context);
            if (spec.isPeriodic()) {
                PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                        AutomationWorker.class, spec.getRepeatIntervalMs(), TimeUnit.MILLISECONDS)
                        .setConstraints(constraints)
                        .setInputData(input)
                        .build();
                workManager.enqueueUniquePeriodicWork(spec.getId(),
                        ExistingPeriodicWorkPolicy.UPDATE, request);
            } else {
                OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                        AutomationWorker.class)
                        .setConstraints(constraints)
                        .setInitialDelay(Math.max(0L, spec.getInitialDelayMs()),
                                TimeUnit.MILLISECONDS)
                        .setInputData(input)
                        .build();
                workManager.enqueueUniqueWork(spec.getId(), ExistingWorkPolicy.REPLACE, request);
            }
        }

        @Override
        public void cancel(@NonNull String projectId, @NonNull String jobId) {
            WorkManager.getInstance(context).cancelUniqueWork(jobId);
        }
    }

    /** Used when no Android context is available (unit tests, headless hosts). */
    static final class UnavailableBackend implements Backend {

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void enqueue(@NonNull String projectId, @NonNull JobSpec spec) {
        }

        @Override
        public void cancel(@NonNull String projectId, @NonNull String jobId) {
        }
    }
}
