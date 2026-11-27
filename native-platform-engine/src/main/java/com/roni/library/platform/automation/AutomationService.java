package com.roni.library.platform.automation;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.automation.AlarmSpec;
import com.roni.library.contracts.automation.AutomationErrorCodes;
import com.roni.library.contracts.automation.AutomationEvent;
import com.roni.library.contracts.automation.BackgroundTask;
import com.roni.library.contracts.automation.JobSpec;
import com.roni.library.contracts.automation.ServiceStatus;
import com.roni.library.contracts.automation.TransferProgress;
import com.roni.library.contracts.automation.TransferRequest;
import com.roni.library.platform.filemanager.FileManagerRepository;
import com.roni.library.storage.StorageEngine;
import com.roni.library.storage.preferences.EncryptedPreferences;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facade for the automation platform: background tasks, jobs, alarms, transfers, events and
 * service lifecycle. Every operation is scoped to the trusted active project.
 */
public final class AutomationService {

    private static volatile AutomationService instance;

    private final AutomationStore store;
    private final EventEngine events;
    private final BackgroundTaskEngine tasks;
    private final JobScheduler jobs;
    private final AlarmEngine alarms;
    private final TransferEngine transfers;
    private final ServiceEngine services;

    public AutomationService(@NonNull Context context,
                             @Nullable EncryptedPreferences preferences) {
        this.store = new AutomationStore(preferences);
        this.events = new EventEngine(store);
        this.tasks = new BackgroundTaskEngine(store, events);
        this.jobs = new JobScheduler(store, events, context);
        this.alarms = new AlarmEngine(store, events, context);
        FileManagerRepository files = new FileManagerRepository(context, null);
        this.transfers = new TransferEngine(events, files);
        this.services = new ServiceEngine();
        AutomationWorker.setBridge((projectId, workerType, payload) -> {
            if (projectId.trim().isEmpty()) {
                return false;
            }
            try {
                tasks.submit(projectId, "Scheduled " + workerType, workerType, payload, 1);
                return true;
            } catch (AutomationException error) {
                return false;
            }
        });
        instance = this;
    }

    /** Test/headless constructor without Android services. */
    public AutomationService(@NonNull AutomationStore store) {
        this.store = store;
        this.events = new EventEngine(store);
        this.tasks = new BackgroundTaskEngine(store, events);
        this.jobs = new JobScheduler(store, events, (android.content.Context) null);
        this.alarms = new AlarmEngine(store, events, (android.content.Context) null);
        this.transfers = new TransferEngine(events, new FileManagerRepository(
                new java.io.File(System.getProperty("java.io.tmpdir", "."), "automation"),
                null));
        this.services = new ServiceEngine();
        instance = this;
    }

    @Nullable
    public static AutomationService peek() {
        return instance;
    }

    @NonNull
    public static AutomationService create(@NonNull Context context) {
        EncryptedPreferences preferences = null;
        try {
            preferences = StorageEngine.getPreferences("automation_meta");
        } catch (Throwable ignored) {
            // The store falls back to memory when encryption is unavailable.
        }
        return new AutomationService(context, preferences);
    }

    @NonNull
    public String project() throws AutomationException {
        String projectId = AutomationProjectContext.getActiveProject();
        if (projectId.isEmpty()) {
            throw new AutomationException(AutomationErrorCodes.NO_ACTIVE_PROJECT,
                    "No active project");
        }
        return projectId;
    }

    // ------------------------------------------------------------------
    // tasks
    // ------------------------------------------------------------------

    @NonNull
    public BackgroundTask submitTask(@NonNull String name, @NonNull String type,
                                     @Nullable Map<String, Object> payload, int maxAttempts)
            throws AutomationException {
        return tasks.submit(project(), name, type, payload, maxAttempts);
    }

    public boolean cancelTask(@NonNull String taskId) throws AutomationException {
        return tasks.cancel(project(), taskId);
    }

    @Nullable
    public BackgroundTask getTask(@NonNull String taskId) throws AutomationException {
        return tasks.get(project(), taskId);
    }

    @NonNull
    public List<BackgroundTask> listTasks() throws AutomationException {
        return tasks.list(project());
    }

    public int clearTasks() throws AutomationException {
        return tasks.clearFinished(project());
    }

    // ------------------------------------------------------------------
    // jobs
    // ------------------------------------------------------------------

    @NonNull
    public JobSpec scheduleJob(@NonNull JobSpec spec) throws AutomationException {
        return jobs.schedule(project(), spec);
    }

    public boolean cancelJob(@NonNull String jobId) throws AutomationException {
        return jobs.cancel(project(), jobId);
    }

    @NonNull
    public List<JobSpec> listJobs() throws AutomationException {
        return jobs.list(project());
    }

    // ------------------------------------------------------------------
    // alarms
    // ------------------------------------------------------------------

    @NonNull
    public AlarmSpec setAlarm(@NonNull AlarmSpec spec) throws AutomationException {
        return alarms.schedule(project(), spec);
    }

    public boolean cancelAlarm(@NonNull String alarmId) throws AutomationException {
        return alarms.cancel(project(), alarmId);
    }

    @NonNull
    public List<AlarmSpec> listAlarms() throws AutomationException {
        return alarms.list(project());
    }

    public void onAlarmFired(@NonNull String projectId, @NonNull String alarmId) {
        alarms.onFired(projectId, alarmId);
    }

    public int restoreAlarms() {
        int armed = 0;
        for (String projectId : store.knownProjects()) {
            armed += alarms.restore(projectId);
        }
        return armed;
    }

    // ------------------------------------------------------------------
    // transfers
    // ------------------------------------------------------------------

    @NonNull
    public TransferProgress download(@NonNull TransferRequest request) throws AutomationException {
        return transfers.start(project(), request);
    }

    @NonNull
    public TransferProgress upload(@NonNull TransferRequest request) throws AutomationException {
        return transfers.start(project(), request);
    }

    public boolean cancelTransfer(@NonNull String transferId) throws AutomationException {
        return transfers.cancel(project(), transferId);
    }

    @Nullable
    public TransferProgress getTransfer(@NonNull String transferId) throws AutomationException {
        return transfers.get(project(), transferId);
    }

    @NonNull
    public List<TransferProgress> listTransfers() throws AutomationException {
        return transfers.list(project());
    }

    // ------------------------------------------------------------------
    // events
    // ------------------------------------------------------------------

    @NonNull
    public AutomationEvent publishEvent(@NonNull String topic,
                                        @Nullable Map<String, Object> payload)
            throws AutomationException {
        return events.publish(project(), topic, payload);
    }

    @NonNull
    public List<AutomationEvent> getEvents(long sinceSequence, int limit)
            throws AutomationException {
        return events.replay(project(), sinceSequence, limit);
    }

    public void clearEvents() throws AutomationException {
        events.clear(project());
    }

    // ------------------------------------------------------------------
    // services
    // ------------------------------------------------------------------

    @NonNull
    public List<ServiceStatus> listServices() {
        return services.list();
    }

    @NonNull
    public ServiceStatus serviceStatus(@NonNull String name) throws AutomationException {
        return services.status(name);
    }

    public boolean startService(@NonNull String name) throws AutomationException {
        return services.start(name);
    }

    public boolean stopService(@NonNull String name) throws AutomationException {
        return services.stop(name);
    }

    // ------------------------------------------------------------------
    // status
    // ------------------------------------------------------------------

    @NonNull
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("projectId", AutomationProjectContext.getActiveProject());
        status.put("workManagerAvailable", jobs.isAvailable());
        try {
            status.put("tasks", listTasks().size());
            status.put("jobs", listJobs().size());
            status.put("alarms", listAlarms().size());
            status.put("transfers", listTransfers().size());
        } catch (AutomationException error) {
            status.put("tasks", 0);
            status.put("jobs", 0);
            status.put("alarms", 0);
            status.put("transfers", 0);
        }
        status.put("services", listServices().size());
        return status;
    }

    @NonNull
    public Map<String, Object> capabilities() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("backgroundTasks", true);
        capabilities.put("workManager", jobs.isAvailable());
        capabilities.put("alarms", true);
        capabilities.put("exactAlarms", android.os.Build.VERSION.SDK_INT < 31
                || canScheduleExactAlarms());
        capabilities.put("downloads", true);
        capabilities.put("uploads", true);
        capabilities.put("events", true);
        capabilities.put("services", true);
        capabilities.put("speech", false);
        return capabilities;
    }

    private boolean canScheduleExactAlarms() {
        return true;
    }

    public void release() {
        tasks.release();
        transfers.release();
        AutomationWorker.setBridge(null);
        if (instance == this) {
            instance = null;
        }
    }
}
