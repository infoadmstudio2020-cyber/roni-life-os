package com.roni.library.platform.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.contracts.automation.AlarmSpec;
import com.roni.library.contracts.automation.AutomationErrorCodes;
import com.roni.library.contracts.automation.AutomationEvent;
import com.roni.library.contracts.automation.BackgroundTask;
import com.roni.library.contracts.automation.JobSpec;
import com.roni.library.contracts.automation.ServiceStatus;
import com.roni.library.contracts.automation.TransferProgress;
import com.roni.library.contracts.automation.TransferRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Native bridge route {@code /automation}.
 *
 * <p>The project always comes from {@link AutomationProjectContext}; a {@code projectId} in the
 * payload is ignored. Every action validates its input before touching an engine.</p>
 */
public final class AutomationCommandHandler implements IApiHandler {

    public static final String ROUTE = "automation";

    private final AutomationService service;

    public AutomationCommandHandler(@NonNull AutomationService service) {
        this.service = service;
    }

    @NonNull
    @Override
    public String getPath() {
        return ROUTE;
    }

    @NonNull
    @Override
    public String getAction() {
        return "*";
    }

    @NonNull
    @Override
    public NativeApiResponse handleRequest(@NonNull NativeApiRequest request) {
        String action = request.getAction() == null ? "" : request.getAction();
        try {
            switch (action) {
                case "getStatus":
                    return ok(request, service.status());
                case "getCapabilities":
                    return ok(request, service.capabilities());
                case "submitTask":
                    return submitTask(request);
                case "cancelTask":
                    return ok(request, single("cancelled",
                            service.cancelTask(string(request, "taskId", ""))));
                case "getTask":
                    return task(request, service.getTask(string(request, "taskId", "")));
                case "listTasks":
                    return tasks(request, service.listTasks());
                case "clearTasks":
                    return ok(request, single("removed", service.clearTasks()));
                case "scheduleJob":
                    return scheduleJob(request);
                case "cancelJob":
                    return ok(request, single("cancelled",
                            service.cancelJob(string(request, "jobId", ""))));
                case "listJobs":
                    return jobs(request, service.listJobs());
                case "setAlarm":
                    return setAlarm(request);
                case "cancelAlarm":
                    return ok(request, single("cancelled",
                            service.cancelAlarm(string(request, "alarmId", ""))));
                case "listAlarms":
                    return alarms(request, service.listAlarms());
                case "download":
                    return transfer(request, true);
                case "upload":
                    return transfer(request, false);
                case "cancelTransfer":
                    return ok(request, single("cancelled",
                            service.cancelTransfer(string(request, "transferId", ""))));
                case "getTransfer":
                    return transferStatus(request,
                            service.getTransfer(string(request, "transferId", "")));
                case "listTransfers":
                    return transfers(request, service.listTransfers());
                case "publishEvent":
                    return publishEvent(request);
                case "getEvents":
                    return events(request, service.getEvents(
                            number(request, "sinceSequence", 0L),
                            (int) number(request, "limit", 100L)));
                case "clearEvents":
                    service.clearEvents();
                    return ok(request, single("cleared", true));
                case "listServices":
                    return services(request, service.listServices());
                case "getServiceStatus":
                    return ok(request, service.serviceStatus(
                            string(request, "name", "")).toMap());
                case "startService":
                    return ok(request, single("started",
                            service.startService(string(request, "name", ""))));
                case "stopService":
                    return ok(request, single("stopped",
                            service.stopService(string(request, "name", ""))));
                default:
                    return failure(request, AutomationErrorCodes.UNSUPPORTED,
                            "Unknown action: " + action);
            }
        } catch (AutomationException error) {
            return failure(request, error.getCode(), error.getMessage());
        } catch (Throwable error) {
            return failure(request, AutomationErrorCodes.IO_ERROR,
                    error.getMessage() == null ? "Automation failure" : error.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // actions
    // ------------------------------------------------------------------

    @NonNull
    private NativeApiResponse submitTask(@NonNull NativeApiRequest request)
            throws AutomationException {
        BackgroundTask task = service.submitTask(
                string(request, "name", "Background task"),
                string(request, "type", "noop"),
                map(request, "payload"),
                (int) number(request, "maxAttempts", 1L));
        return ok(request, task.toMap());
    }

    @NonNull
    private NativeApiResponse scheduleJob(@NonNull NativeApiRequest request)
            throws AutomationException {
        JobSpec spec = new JobSpec.Builder()
                .id(string(request, "id", ""))
                .name(string(request, "name", "Scheduled job"))
                .workerType(string(request, "workerType", "noop"))
                .requiresNetwork(bool(request, "requiresNetwork", false))
                .requiresCharging(bool(request, "requiresCharging", false))
                .requiresIdle(bool(request, "requiresIdle", false))
                .initialDelayMs(number(request, "initialDelayMs", 0L))
                .repeatIntervalMs(number(request, "repeatIntervalMs", 0L))
                .maxAttempts((int) number(request, "maxAttempts", 1L))
                .payload(map(request, "payload"))
                .build();
        return ok(request, service.scheduleJob(spec).toMap());
    }

    @NonNull
    private NativeApiResponse setAlarm(@NonNull NativeApiRequest request)
            throws AutomationException {
        AlarmSpec spec = new AlarmSpec.Builder()
                .id(string(request, "id", ""))
                .label(string(request, "label", "Alarm"))
                .triggerAt(number(request, "triggerAt", 0L))
                .intervalMs(number(request, "intervalMs", 0L))
                .exact(bool(request, "exact", false))
                .payload(map(request, "payload"))
                .build();
        return ok(request, service.setAlarm(spec).toMap());
    }

    @NonNull
    private NativeApiResponse transfer(@NonNull NativeApiRequest request, boolean download)
            throws AutomationException {
        TransferRequest transfer = new TransferRequest.Builder()
                .id(string(request, "id", ""))
                .direction(download ? TransferRequest.DIRECTION_DOWNLOAD
                        : TransferRequest.DIRECTION_UPLOAD)
                .url(string(request, "url", ""))
                .localPath(string(request, "path", ""))
                .headers(stringMap(request, "headers"))
                .expectedBytes(number(request, "expectedBytes", 0L))
                .build();
        TransferProgress progress = download
                ? service.download(transfer) : service.upload(transfer);
        return ok(request, progress.toMap());
    }

    @NonNull
    private NativeApiResponse publishEvent(@NonNull NativeApiRequest request)
            throws AutomationException {
        AutomationEvent event = service.publishEvent(
                string(request, "topic", EventEngine.TOPIC_CUSTOM),
                map(request, "payload"));
        return ok(request, event.toMap());
    }

    // ------------------------------------------------------------------
    // serialisation
    // ------------------------------------------------------------------

    @NonNull
    private static NativeApiResponse task(@NonNull NativeApiRequest request,
                                          @Nullable BackgroundTask task) {
        if (task == null) {
            return failure(request, AutomationErrorCodes.TASK_NOT_FOUND, "Task not found");
        }
        return ok(request, task.toMap());
    }

    @NonNull
    private static NativeApiResponse tasks(@NonNull NativeApiRequest request,
                                           @NonNull List<BackgroundTask> tasks) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BackgroundTask task : tasks) {
            rows.add(task.toMap());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", rows);
        data.put("count", rows.size());
        return ok(request, data);
    }

    @NonNull
    private static NativeApiResponse jobs(@NonNull NativeApiRequest request,
                                          @NonNull List<JobSpec> jobs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JobSpec job : jobs) {
            rows.add(job.toMap());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", rows);
        data.put("count", rows.size());
        return ok(request, data);
    }

    @NonNull
    private static NativeApiResponse alarms(@NonNull NativeApiRequest request,
                                            @NonNull List<AlarmSpec> alarms) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AlarmSpec alarm : alarms) {
            rows.add(alarm.toMap());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", rows);
        data.put("count", rows.size());
        return ok(request, data);
    }

    @NonNull
    private static NativeApiResponse transfers(@NonNull NativeApiRequest request,
                                               @NonNull List<TransferProgress> transfers) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TransferProgress progress : transfers) {
            rows.add(progress.toMap());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", rows);
        data.put("count", rows.size());
        return ok(request, data);
    }

    @NonNull
    private static NativeApiResponse transferStatus(@NonNull NativeApiRequest request,
                                                    @Nullable TransferProgress progress) {
        if (progress == null) {
            return failure(request, AutomationErrorCodes.TRANSFER_NOT_FOUND,
                    "Transfer not found");
        }
        return ok(request, progress.toMap());
    }

    @NonNull
    private static NativeApiResponse events(@NonNull NativeApiRequest request,
                                            @NonNull List<AutomationEvent> events) {
        List<Map<String, Object>> rows = new ArrayList<>();
        long lastSequence = 0L;
        for (AutomationEvent event : events) {
            rows.add(event.toMap());
            lastSequence = Math.max(lastSequence, event.getSequence());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", rows);
        data.put("count", rows.size());
        data.put("lastSequence", lastSequence);
        return ok(request, data);
    }

    @NonNull
    private static NativeApiResponse services(@NonNull NativeApiRequest request,
                                              @NonNull List<ServiceStatus> services) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ServiceStatus status : services) {
            rows.add(status.toMap());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", rows);
        data.put("count", rows.size());
        return ok(request, data);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    @NonNull
    private static Map<String, Object> single(@NonNull String key, @Nullable Object value) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(key, value);
        return data;
    }

    @NonNull
    private static String string(@NonNull NativeApiRequest request, @NonNull String key,
                                 @NonNull String fallback) {
        Object value = request.getParameter(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static long number(@NonNull NativeApiRequest request, @NonNull String key,
                               long fallback) {
        Object value = request.getParameter(key);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static boolean bool(@NonNull NativeApiRequest request, @NonNull String key,
                                boolean fallback) {
        Object value = request.getParameter(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private static Map<String, Object> map(@NonNull NativeApiRequest request,
                                           @NonNull String key) {
        Object value = request.getParameter(key);
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private static Map<String, String> stringMap(@NonNull NativeApiRequest request,
                                                 @NonNull String key) {
        Map<String, String> result = new LinkedHashMap<>();
        Object value = request.getParameter(key);
        if (value instanceof Map) {
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                result.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    @NonNull
    private static NativeApiResponse ok(@NonNull NativeApiRequest request,
                                        @Nullable Map<String, Object> data) {
        return NativeApiResponse.success(request.getRequestId(), data);
    }

    @NonNull
    private static NativeApiResponse failure(@NonNull NativeApiRequest request,
                                             @NonNull String code, @Nullable String message) {
        return NativeApiResponse.error(request.getRequestId(),
                new NativeApiError(code, message == null ? code : message));
    }
}
