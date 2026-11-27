package com.roni.library.contracts.automation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lifecycle snapshot of a registered native service. */
public final class ServiceStatus {

    private final String name;
    private final String state;
    private final String description;
    private final long startedAt;

    public ServiceStatus(String name, String state, String description, long startedAt) {
        this.name = name;
        this.state = state;
        this.description = description;
        this.startedAt = startedAt;
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }

    public String getDescription() {
        return description;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("state", state);
        map.put("description", description);
        map.put("startedAt", startedAt);
        return map;
    }
}
