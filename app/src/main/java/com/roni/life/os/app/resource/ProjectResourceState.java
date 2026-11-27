package com.roni.life.os.app.resource;

import com.roni.life.os.app.util.Codec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The persisted resource state of one project.
 *
 * <p>{@link Status#READY} is only ever written after every required resource was verified against
 * the real file on disk. A project whose download was interrupted stays {@link Status#INCOMPLETE}
 * and is never reported as ready.</p>
 */
public final class ProjectResourceState {

    /** Project-level resource lifecycle. */
    public enum Status {
        NOT_INITIALIZED("not_initialized"),
        INITIALIZING("initializing"),
        READY("ready"),
        INCOMPLETE("incomplete"),
        FAILED("failed");

        private final String key;

        Status(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static Status fromKey(String key) {
            if (key != null) {
                for (Status status : values()) {
                    if (status.key.equalsIgnoreCase(key)) {
                        return status;
                    }
                }
            }
            return NOT_INITIALIZED;
        }
    }

    private final String projectId;
    private Status status;
    private String manifestVersion;
    private long initializedAt;
    private long lastVerifiedAt;
    private final Map<String, ResourceRecord> records = new LinkedHashMap<>();

    public ProjectResourceState(String projectId) {
        this.projectId = projectId != null ? projectId : "";
        this.status = Status.NOT_INITIALIZED;
        this.manifestVersion = "";
        this.initializedAt = 0L;
        this.lastVerifiedAt = 0L;
    }

    public String getProjectId() {
        return projectId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status != null ? status : Status.NOT_INITIALIZED;
    }

    public String getManifestVersion() {
        return manifestVersion;
    }

    public void setManifestVersion(String manifestVersion) {
        this.manifestVersion = manifestVersion != null ? manifestVersion : "";
    }

    public long getInitializedAt() {
        return initializedAt;
    }

    public void setInitializedAt(long initializedAt) {
        this.initializedAt = initializedAt;
    }

    public long getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(long lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public boolean isReady() {
        return status == Status.READY;
    }

    public ResourceRecord get(String id) {
        return id != null ? records.get(id) : null;
    }

    public void put(ResourceRecord record) {
        if (record != null && record.getId() != null) {
            records.put(record.getId(), record);
        }
    }

    public Collection<ResourceRecord> getRecords() {
        return new ArrayList<>(records.values());
    }

    public int size() {
        return records.size();
    }

    /** Drops records whose resource is no longer part of the manifest. */
    public void retainOnly(Collection<String> ids) {
        if (ids == null) {
            return;
        }
        List<String> stale = new ArrayList<>();
        for (String id : records.keySet()) {
            if (!ids.contains(id)) {
                stale.add(id);
            }
        }
        for (String id : stale) {
            records.remove(id);
        }
    }

    public void markReady(String manifestVersion, long now) {
        this.status = Status.READY;
        this.manifestVersion = manifestVersion != null ? manifestVersion : "";
        this.lastVerifiedAt = now;
        if (this.initializedAt == 0L) {
            this.initializedAt = now;
        }
    }

    public void markIncomplete(String manifestVersion, long now) {
        this.status = Status.INCOMPLETE;
        this.manifestVersion = manifestVersion != null ? manifestVersion : "";
        this.lastVerifiedAt = now;
    }

    public void markFailed(String manifestVersion, long now) {
        this.status = Status.FAILED;
        this.manifestVersion = manifestVersion != null ? manifestVersion : "";
        this.lastVerifiedAt = now;
    }

    public String encode() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("projectId", projectId);
        map.put("status", status.getKey());
        map.put("manifestVersion", manifestVersion);
        map.put("initializedAt", String.valueOf(initializedAt));
        map.put("lastVerifiedAt", String.valueOf(lastVerifiedAt));
        map.put("count", String.valueOf(records.size()));
        int index = 0;
        for (ResourceRecord record : records.values()) {
            map.put("r" + index, record.encode());
            index++;
        }
        return Codec.encode(map);
    }

    public static ProjectResourceState decode(String raw) {
        Map<String, String> map = Codec.decode(raw);
        if (map.isEmpty() || !map.containsKey("projectId")) {
            return null;
        }
        ProjectResourceState state = new ProjectResourceState(Codec.str(map, "projectId", ""));
        state.status = Status.fromKey(Codec.str(map, "status", Status.NOT_INITIALIZED.getKey()));
        state.manifestVersion = Codec.str(map, "manifestVersion", "");
        state.initializedAt = Codec.lng(map, "initializedAt", 0L);
        state.lastVerifiedAt = Codec.lng(map, "lastVerifiedAt", 0L);
        int count = Codec.integer(map, "count", 0);
        for (int index = 0; index < count; index++) {
            String encoded = map.get("r" + index);
            if (encoded == null) {
                continue;
            }
            ResourceRecord record = ResourceRecord.decode(encoded);
            if (record != null) {
                state.put(record);
            }
        }
        return state;
    }

    @Override
    public String toString() {
        return "ProjectResourceState{project='" + projectId + "', status=" + status
                + ", records=" + records.size() + "}";
    }
}
