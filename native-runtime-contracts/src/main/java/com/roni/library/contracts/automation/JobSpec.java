package com.roni.library.contracts.automation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deferred or periodic work handed to the platform job scheduler. */
public final class JobSpec {

    private final String id;
    private final String name;
    private final String workerType;
    private final boolean requiresNetwork;
    private final boolean requiresCharging;
    private final boolean requiresIdle;
    private final long initialDelayMs;
    private final long repeatIntervalMs;
    private final int maxAttempts;
    private final Map<String, Object> payload;

    private JobSpec(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.workerType = builder.workerType;
        this.requiresNetwork = builder.requiresNetwork;
        this.requiresCharging = builder.requiresCharging;
        this.requiresIdle = builder.requiresIdle;
        this.initialDelayMs = builder.initialDelayMs;
        this.repeatIntervalMs = builder.repeatIntervalMs;
        this.maxAttempts = builder.maxAttempts;
        this.payload = Collections.unmodifiableMap(new LinkedHashMap<>(builder.payload));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWorkerType() {
        return workerType;
    }

    public boolean requiresNetwork() {
        return requiresNetwork;
    }

    public boolean requiresCharging() {
        return requiresCharging;
    }

    public boolean requiresIdle() {
        return requiresIdle;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public long getRepeatIntervalMs() {
        return repeatIntervalMs;
    }

    public boolean isPeriodic() {
        return repeatIntervalMs > 0L;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("workerType", workerType);
        map.put("requiresNetwork", requiresNetwork);
        map.put("requiresCharging", requiresCharging);
        map.put("requiresIdle", requiresIdle);
        map.put("initialDelayMs", initialDelayMs);
        map.put("repeatIntervalMs", repeatIntervalMs);
        map.put("periodic", isPeriodic());
        map.put("maxAttempts", maxAttempts);
        map.put("payload", payload);
        return map;
    }

    public static final class Builder {
        private String id = "";
        private String name = "";
        private String workerType = "generic";
        private boolean requiresNetwork;
        private boolean requiresCharging;
        private boolean requiresIdle;
        private long initialDelayMs;
        private long repeatIntervalMs;
        private int maxAttempts = 1;
        private Map<String, Object> payload = new LinkedHashMap<>();

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder name(String value) {
            this.name = value;
            return this;
        }

        public Builder workerType(String value) {
            this.workerType = value;
            return this;
        }

        public Builder requiresNetwork(boolean value) {
            this.requiresNetwork = value;
            return this;
        }

        public Builder requiresCharging(boolean value) {
            this.requiresCharging = value;
            return this;
        }

        public Builder requiresIdle(boolean value) {
            this.requiresIdle = value;
            return this;
        }

        public Builder initialDelayMs(long value) {
            this.initialDelayMs = value;
            return this;
        }

        public Builder repeatIntervalMs(long value) {
            this.repeatIntervalMs = value;
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

        public JobSpec build() {
            return new JobSpec(this);
        }
    }
}
