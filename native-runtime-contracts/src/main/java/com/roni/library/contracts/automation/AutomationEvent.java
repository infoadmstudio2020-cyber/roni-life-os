package com.roni.library.contracts.automation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable event published on the shared event engine. */
public final class AutomationEvent {

    private final String id;
    private final String projectId;
    private final String topic;
    private final Map<String, Object> payload;
    private final long timestamp;
    private final long sequence;

    public AutomationEvent(String id, String projectId, String topic,
                           Map<String, Object> payload, long timestamp, long sequence) {
        this.id = id;
        this.projectId = projectId;
        this.topic = topic;
        this.payload = Collections.unmodifiableMap(
                payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload));
        this.timestamp = timestamp;
        this.sequence = sequence;
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getTopic() {
        return topic;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getSequence() {
        return sequence;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("projectId", projectId);
        map.put("topic", topic);
        map.put("payload", payload);
        map.put("timestamp", timestamp);
        map.put("sequence", sequence);
        return map;
    }
}
