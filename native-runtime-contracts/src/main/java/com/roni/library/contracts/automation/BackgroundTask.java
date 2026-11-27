package com.roni.library.contracts.automation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A unit of work tracked by the background task engine. */
public final class BackgroundTask {

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_RUNNING = "RUNNING";
    public static final String STATE_COMPLETED = "COMPLETED";
    public static final String STATE_FAILED = "FAILED";
    public static final String STATE_CANCELLED = "CANCELLED";

    private final String id;
    private final String projectId;
    private final String name;
    private final String type;
    private final String state;
    private final int progress;
    private final int attempts;
    private final int maxAttempts;
    private final Map<String, Object> payload;
    private final long createdAt;
    private final long startedAt;
    private final long finishedAt;
    private final String lastError;
    private final Map<String, Object> result;

    private BackgroundTask(Builder builder) {
        this.id = builder.id;
        this.projectId = builder.projectId;
        this.name = builder.name;
        this.type = builder.type;
        this.state = builder.state;
        this.progress = builder.progress;
        this.attempts = builder.attempts;
        this.maxAttempts = builder.maxAttempts;
        this.payload = Collections.unmodifiableMap(new LinkedHashMap<>(builder.payload));
        this.createdAt = builder.createdAt;
        this.startedAt = builder.startedAt;
        this.finishedAt = builder.finishedAt;
        this.lastError = builder.lastError;
        this.result = Collections.unmodifiableMap(new LinkedHashMap<>(builder.result));
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getState() {
        return state;
    }

    public int getProgress() {
        return progress;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public boolean isFinished() {
        return STATE_COMPLETED.equals(state)
                || STATE_FAILED.equals(state)
                || STATE_CANCELLED.equals(state);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("projectId", projectId);
        map.put("name", name);
        map.put("type", type);
        map.put("state", state);
        map.put("progress", progress);
        map.put("attempts", attempts);
        map.put("maxAttempts", maxAttempts);
        map.put("payload", payload);
        map.put("createdAt", createdAt);
        map.put("startedAt", startedAt);
        map.put("finishedAt", finishedAt);
        map.put("lastError", lastError);
        map.put("result", result);
        map.put("finished", isFinished());
        return map;
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .projectId(projectId)
                .name(name)
                .type(type)
                .state(state)
                .progress(progress)
                .attempts(attempts)
                .maxAttempts(maxAttempts)
                .payload(payload)
                .createdAt(createdAt)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .lastError(lastError)
                .result(result);
    }

    public static final class Builder {
        private String id = "";
        private String projectId = "";
        private String name = "";
        private String type = "";
        private String state = STATE_PENDING;
        private int progress;
        private int attempts;
        private int maxAttempts = 1;
        private Map<String, Object> payload = new LinkedHashMap<>();
        private long createdAt;
        private long startedAt;
        private long finishedAt;
        private String lastError;
        private Map<String, Object> result = new LinkedHashMap<>();

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder projectId(String value) {
            this.projectId = value;
            return this;
        }

        public Builder name(String value) {
            this.name = value;
            return this;
        }

        public Builder type(String value) {
            this.type = value;
            return this;
        }

        public Builder state(String value) {
            this.state = value;
            return this;
        }

        public Builder progress(int value) {
            this.progress = value;
            return this;
        }

        public Builder attempts(int value) {
            this.attempts = value;
            return this;
        }

        public Builder maxAttempts(int value) {
            this.maxAttempts = value;
            return this;
        }

        public Builder payload(Map<String, Object> value) {
            this.payload = value == null ? new LinkedHashMap<>() : value;
            return this;
        }

        public Builder createdAt(long value) {
            this.createdAt = value;
            return this;
        }

        public Builder startedAt(long value) {
            this.startedAt = value;
            return this;
        }

        public Builder finishedAt(long value) {
            this.finishedAt = value;
            return this;
        }

        public Builder lastError(String value) {
            this.lastError = value;
            return this;
        }

        public Builder result(Map<String, Object> value) {
            this.result = value == null ? new LinkedHashMap<>() : value;
            return this;
        }

        public BackgroundTask build() {
            return new BackgroundTask(this);
        }
    }
}
