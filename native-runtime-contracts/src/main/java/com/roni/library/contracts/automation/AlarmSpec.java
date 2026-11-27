package com.roni.library.contracts.automation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A time based trigger owned by a project. */
public final class AlarmSpec {

    private final String id;
    private final String label;
    private final long triggerAt;
    private final long intervalMs;
    private final boolean exact;
    private final Map<String, Object> payload;

    private AlarmSpec(Builder builder) {
        this.id = builder.id;
        this.label = builder.label;
        this.triggerAt = builder.triggerAt;
        this.intervalMs = builder.intervalMs;
        this.exact = builder.exact;
        this.payload = Collections.unmodifiableMap(new LinkedHashMap<>(builder.payload));
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public long getTriggerAt() {
        return triggerAt;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public boolean isRepeating() {
        return intervalMs > 0L;
    }

    public boolean isExact() {
        return exact;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("label", label);
        map.put("triggerAt", triggerAt);
        map.put("intervalMs", intervalMs);
        map.put("repeating", isRepeating());
        map.put("exact", exact);
        map.put("payload", payload);
        return map;
    }

    public static final class Builder {
        private String id = "";
        private String label = "";
        private long triggerAt;
        private long intervalMs;
        private boolean exact;
        private Map<String, Object> payload = new LinkedHashMap<>();

        public Builder id(String value) {
            this.id = value;
            return this;
        }

        public Builder label(String value) {
            this.label = value;
            return this;
        }

        public Builder triggerAt(long value) {
            this.triggerAt = value;
            return this;
        }

        public Builder intervalMs(long value) {
            this.intervalMs = value;
            return this;
        }

        public Builder exact(boolean value) {
            this.exact = value;
            return this;
        }

        public Builder payload(Map<String, Object> value) {
            this.payload = value == null ? new LinkedHashMap<>() : value;
            return this;
        }

        public AlarmSpec build() {
            return new AlarmSpec(this);
        }
    }
}
