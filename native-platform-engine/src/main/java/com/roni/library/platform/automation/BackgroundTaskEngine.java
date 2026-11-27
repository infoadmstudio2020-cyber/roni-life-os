package com.roni.library.platform.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.automation.AutomationErrorCodes;
import com.roni.library.contracts.automation.AutomationLimits;
import com.roni.library.contracts.automation.BackgroundTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates background work: submission, execution, retry, progress and cancellation.
 *
 * <p>Handlers run on a dedicated executor, never on the caller thread. Every state change is
 * persisted and published on the {@link EventEngine}.</p>
 */
public final class BackgroundTaskEngine {

    /** Executes one task. Implementations must be thread safe. */
    public interface TaskHandler {
        @NonNull
        Map<String, Object> execute(@NonNull BackgroundTask task, @NonNull ProgressSink sink)
                throws Exception;
    }

    /** Progress and cancellation channel handed to a handler. */
    public interface ProgressSink {
        void progress(int percent);

        boolean isCancelled();
    }

    private final AutomationStore store;
    private final EventEngine events;
    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, BackgroundTask> tasks = new ConcurrentHashMap<>();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService retries = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean released = new AtomicBoolean(false);

    public BackgroundTaskEngine(@NonNull AutomationStore store, @NonNull EventEngine events) {
        this.store = store;
        this.events = events;
        registerBuiltIns();
    }

    private void registerBuiltIns() {
        handlers.put("noop", (task, sink) -> {
            sink.progress(100);
            return Collections.singletonMap("ok", true);
        });
        handlers.put("echo", (task, sink) -> {
            sink.progress(50);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("echo", task.getPayload());
            sink.progress(100);
            return result;
        });
        handlers.put("delay", (task, sink) -> {
            long millis = number(task.getPayload().get("millis"), 1000L);
            long deadline = System.currentTimeMillis() + Math.min(millis, 60_000L);
            while (System.currentTimeMillis() < deadline) {
                if (sink.isCancelled()) {
                    throw new AutomationException(AutomationErrorCodes.OPERATION_CANCELLED,
                            "Task cancelled");
                }
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AutomationException(AutomationErrorCodes.OPERATION_CANCELLED,
                            "Task interrupted", error);
                }
            }
            sink.progress(100);
            return Collections.singletonMap("waitedMs", millis);
        });
        handlers.put("event", (task, sink) -> {
            String topic = String.valueOf(task.getPayload().get("topic"));
            events.emit(task.getProjectId(), topic.isEmpty() ? EventEngine.TOPIC_CUSTOM : topic,
                    "task.event", task.getPayload());
            sink.progress(100);
            return Collections.singletonMap("published", true);
        });
    }

    public void registerHandler(@NonNull String type, @NonNull TaskHandler handler) {
        handlers.put(type, handler);
    }

    @NonNull
    public BackgroundTask submit(@NonNull String projectId, @NonNull String name,
                                 @NonNull String type, @Nullable Map<String, Object> payload,
                                 int maxAttempts) throws AutomationException {
        if (projectId.trim().isEmpty()) {
            throw new AutomationException(AutomationErrorCodes.NO_ACTIVE_PROJECT,
                    "No active project");
        }
        if (name.trim().isEmpty() || name.length() > AutomationLimits.MAX_NAME_LENGTH) {
            throw AutomationException.invalid("Task name is required");
        }
        if (!handlers.containsKey(type)) {
            throw new AutomationException(AutomationErrorCodes.UNSUPPORTED,
                    "Unknown task type: " + type);
        }
        Map<String, Object> safePayload = payload == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        if (safePayload.toString().length() > AutomationLimits.MAX_PAYLOAD_BYTES) {
            throw AutomationException.invalid("Task payload is too large");
        }
        List<BackgroundTask> existing = list(projectId);
        if (existing.size() >= AutomationLimits.MAX_TASKS) {
            throw new AutomationException(AutomationErrorCodes.TASK_LIMIT,
                    "Task limit reached");
        }
        BackgroundTask task = new BackgroundTask.Builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .name(name)
                .type(type)
                .state(BackgroundTask.STATE_PENDING)
                .maxAttempts(Math.max(1, Math.min(maxAttempts, AutomationLimits.MAX_RETRIES)))
                .payload(safePayload)
                .createdAt(System.currentTimeMillis())
                .build();
        tasks.put(task.getId(), task);
        persist(projectId);
        events.emit(projectId, EventEngine.TOPIC_TASK, "queued", task.toMap());
        executor.execute(() -> run(task.getId()));
        return task;
    }

    private void run(@NonNull String taskId) {
        BackgroundTask task = tasks.get(taskId);
        if (task == null || task.isFinished()) {
            return;
        }
        if (cancelled.contains(taskId)) {
            finish(task, BackgroundTask.STATE_CANCELLED, null, null);
            return;
        }
        TaskHandler handler = handlers.get(task.getType());
        if (handler == null) {
            finish(task, BackgroundTask.STATE_FAILED, null, "No handler for " + task.getType());
            return;
        }
        BackgroundTask running = task.toBuilder()
                .state(BackgroundTask.STATE_RUNNING)
                .startedAt(System.currentTimeMillis())
                .attempts(task.getAttempts() + 1)
                .progress(0)
                .build();
        tasks.put(taskId, running);
        persist(running.getProjectId());
        events.emit(running.getProjectId(), EventEngine.TOPIC_TASK, "started", running.toMap());

        ProgressSink sink = new ProgressSink() {
            private int lastReported = -1;

            @Override
            public void progress(int percent) {
                int bounded = Math.max(0, Math.min(100, percent));
                if (bounded == lastReported) {
                    return;
                }
                lastReported = bounded;
                BackgroundTask current = tasks.get(taskId);
                if (current == null || current.isFinished()) {
                    return;
                }
                BackgroundTask updated = current.toBuilder().progress(bounded).build();
                tasks.put(taskId, updated);
                if (bounded % 10 == 0) {
                    persist(updated.getProjectId());
                }
                events.emit(updated.getProjectId(), EventEngine.TOPIC_TASK, "progress",
                        updated.toMap());
            }

            @Override
            public boolean isCancelled() {
                return cancelled.contains(taskId);
            }
        };

        try {
            Map<String, Object> result = handler.execute(running, sink);
            if (cancelled.contains(taskId)) {
                finish(running, BackgroundTask.STATE_CANCELLED, null, null);
                return;
            }
            finish(running, BackgroundTask.STATE_COMPLETED, result, null);
        } catch (Throwable error) {
            if (cancelled.contains(taskId)) {
                finish(running, BackgroundTask.STATE_CANCELLED, null, null);
                return;
            }
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            if (running.getAttempts() < running.getMaxAttempts()) {
                BackgroundTask retrying = running.toBuilder()
                        .state(BackgroundTask.STATE_PENDING)
                        .lastError(message)
                        .build();
                tasks.put(taskId, retrying);
                persist(retrying.getProjectId());
                events.emit(retrying.getProjectId(), EventEngine.TOPIC_TASK, "retrying",
                        retrying.toMap());
                long delay = Math.min(
                        AutomationLimits.RETRY_BASE_DELAY_MS * running.getAttempts(),
                        AutomationLimits.MAX_RETRY_DELAY_MS);
                retries.schedule(() -> executor.execute(() -> run(taskId)), delay,
                        TimeUnit.MILLISECONDS);
            } else {
                finish(running, BackgroundTask.STATE_FAILED, null, message);
            }
        }
    }

    private void finish(@NonNull BackgroundTask task, @NonNull String state,
                        @Nullable Map<String, Object> result, @Nullable String error) {
        BackgroundTask finished = task.toBuilder()
                .state(state)
                .progress(BackgroundTask.STATE_COMPLETED.equals(state) ? 100 : task.getProgress())
                .finishedAt(System.currentTimeMillis())
                .result(result)
                .lastError(error)
                .build();
        tasks.put(finished.getId(), finished);
        cancelled.remove(finished.getId());
        persist(finished.getProjectId());
        String action = BackgroundTask.STATE_COMPLETED.equals(state) ? "completed"
                : BackgroundTask.STATE_CANCELLED.equals(state) ? "cancelled" : "failed";
        events.emit(finished.getProjectId(), EventEngine.TOPIC_TASK, action, finished.toMap());
    }

    public boolean cancel(@NonNull String projectId, @NonNull String taskId) {
        BackgroundTask task = tasks.get(taskId);
        if (task == null || !projectId.equals(task.getProjectId()) || task.isFinished()) {
            return false;
        }
        cancelled.add(taskId);
        BackgroundTask cancelledTask = task.toBuilder()
                .state(BackgroundTask.STATE_CANCELLED)
                .finishedAt(System.currentTimeMillis())
                .build();
        tasks.put(taskId, cancelledTask);
        persist(projectId);
        events.emit(projectId, EventEngine.TOPIC_TASK, "cancelled", cancelledTask.toMap());
        return true;
    }

    @Nullable
    public BackgroundTask get(@NonNull String projectId, @NonNull String taskId) {
        BackgroundTask task = tasks.get(taskId);
        if (task == null || !projectId.equals(task.getProjectId())) {
            return null;
        }
        return task;
    }

    @NonNull
    public List<BackgroundTask> list(@NonNull String projectId) {
        List<BackgroundTask> result = new ArrayList<>();
        for (BackgroundTask task : tasks.values()) {
            if (projectId.equals(task.getProjectId())) {
                result.add(task);
            }
        }
        if (result.isEmpty()) {
            result.addAll(store.loadTasks(projectId));
        }
        result.sort((left, right) -> Long.compare(right.getCreatedAt(), left.getCreatedAt()));
        return result;
    }

    public int clearFinished(@NonNull String projectId) {
        int removed = 0;
        for (BackgroundTask task : new ArrayList<>(tasks.values())) {
            if (projectId.equals(task.getProjectId()) && task.isFinished()) {
                tasks.remove(task.getId());
                removed++;
            }
        }
        persist(projectId);
        return removed;
    }

    /** Restores persisted tasks after a process restart. */
    public void restore(@NonNull String projectId) {
        for (BackgroundTask task : store.loadTasks(projectId)) {
            if (!task.isFinished()) {
                BackgroundTask interrupted = task.toBuilder()
                        .state(BackgroundTask.STATE_FAILED)
                        .lastError("Interrupted by process restart")
                        .finishedAt(System.currentTimeMillis())
                        .build();
                tasks.put(interrupted.getId(), interrupted);
            } else {
                tasks.put(task.getId(), task);
            }
        }
        persist(projectId);
    }

    private void persist(@NonNull String projectId) {
        store.saveTasks(projectId, list(projectId));
    }

    public void release() {
        if (released.compareAndSet(false, true)) {
            executor.shutdownNow();
            retries.shutdownNow();
        }
    }

    private static long number(@Nullable Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }
}
