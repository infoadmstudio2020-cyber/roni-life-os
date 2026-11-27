package com.roni.library.platform.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.automation.AutomationEvent;
import com.roni.library.contracts.automation.AutomationLimits;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

/**
 * Shared event infrastructure for the automation platform.
 *
 * <p>Events are delivered to in-process subscribers through an RxJava stream and persisted so
 * the bridge and the Consumer App can poll them by sequence number. Every event is scoped to
 * the project that produced it.</p>
 */
public final class EventEngine {

    public static final String TOPIC_TASK = "task";
    public static final String TOPIC_TRANSFER = "transfer";
    public static final String TOPIC_ALARM = "alarm";
    public static final String TOPIC_JOB = "job";
    public static final String TOPIC_SERVICE = "service";
    public static final String TOPIC_CUSTOM = "custom";

    private final Subject<AutomationEvent> subject =
            PublishSubject.<AutomationEvent>create().toSerialized();
    private final Map<String, Deque<AutomationEvent>> buffers = new ConcurrentHashMap<>();
    private final AutomationStore store;

    public EventEngine(@NonNull AutomationStore store) {
        this.store = store;
    }

    @NonNull
    public AutomationEvent publish(@NonNull String projectId, @NonNull String topic,
                                   @Nullable Map<String, Object> payload) {
        long sequence = store.nextSequence(projectId);
        AutomationEvent event = new AutomationEvent(
                UUID.randomUUID().toString(),
                projectId,
                topic,
                payload,
                System.currentTimeMillis(),
                sequence);
        Deque<AutomationEvent> buffer =
                buffers.computeIfAbsent(projectId, key -> new ArrayDeque<>());
        synchronized (buffer) {
            buffer.addLast(event);
            while (buffer.size() > AutomationLimits.MAX_EVENTS) {
                buffer.removeFirst();
            }
        }
        store.appendEvent(event);
        subject.onNext(event);
        return event;
    }

    /** Stream of every event for a project. */
    @NonNull
    public Observable<AutomationEvent> observe(@NonNull String projectId) {
        return subject.filter(event -> projectId.equals(event.getProjectId()));
    }

    /** Stream filtered by topic. */
    @NonNull
    public Observable<AutomationEvent> observeTopic(@NonNull String projectId,
                                                    @NonNull String topic) {
        return observe(projectId).filter(event -> topic.equals(event.getTopic()));
    }

    @NonNull
    public List<AutomationEvent> replay(@NonNull String projectId, long sinceSequence,
                                        int limit) {
        int bounded = Math.max(1, Math.min(limit, AutomationLimits.MAX_EVENTS));
        List<AutomationEvent> events = new ArrayList<>();
        Deque<AutomationEvent> buffer = buffers.get(projectId);
        if (buffer != null) {
            synchronized (buffer) {
                for (AutomationEvent event : buffer) {
                    if (event.getSequence() > sinceSequence) {
                        events.add(event);
                    }
                }
            }
        }
        if (events.isEmpty()) {
            events.addAll(store.loadEvents(projectId, sinceSequence, bounded));
        }
        if (events.size() > bounded) {
            return new ArrayList<>(events.subList(events.size() - bounded, events.size()));
        }
        return events;
    }

    public void clear(@NonNull String projectId) {
        Deque<AutomationEvent> buffer = buffers.get(projectId);
        if (buffer != null) {
            synchronized (buffer) {
                buffer.clear();
            }
        }
        store.clearEvents(projectId);
    }

    /** Convenience helper for engines that emit structured payloads. */
    @NonNull
    public AutomationEvent emit(@NonNull String projectId, @NonNull String topic,
                                @NonNull String action,
                                @Nullable Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        if (data != null) {
            payload.putAll(data);
        }
        return publish(projectId, topic, payload);
    }
}
