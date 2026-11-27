package com.roni.life.os.app.resource;

import com.roni.life.os.app.util.Codec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persisted proof that one resource was fetched and verified on this device.
 *
 * <p>A record is a claim, not a guarantee: the manager re-checks the real file before it trusts
 * one. {@link Status#VERIFIED} therefore means "verified at {@link #getLastVerifiedAt()}", and the
 * file is re-verified on every open.</p>
 */
public final class ResourceRecord {

    /** Lifecycle of a single resource. */
    public enum Status {
        PENDING("pending"),
        DOWNLOADED("downloaded"),
        VERIFIED("verified"),
        MISSING("missing"),
        CORRUPT("corrupt");

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
            return PENDING;
        }
    }

    private final String id;
    private String localPath;
    private Status status;
    private long sizeBytes;
    private String checksum;
    private String version;
    private long lastVerifiedAt;

    public ResourceRecord(String id) {
        this.id = id != null ? id : "";
        this.localPath = "";
        this.status = Status.PENDING;
        this.sizeBytes = 0L;
        this.checksum = "";
        this.version = "";
        this.lastVerifiedAt = 0L;
    }

    public static ResourceRecord verified(ResourceSpec spec, String localPath,
                                          long sizeBytes, String checksum, long now) {
        ResourceRecord record = new ResourceRecord(spec.getId());
        record.localPath = localPath != null ? localPath : "";
        record.status = Status.VERIFIED;
        record.sizeBytes = sizeBytes;
        record.checksum = checksum != null ? checksum : "";
        record.version = spec.getVersion();
        record.lastVerifiedAt = now;
        return record;
    }

    public String getId() {
        return id;
    }

    public String getLocalPath() {
        return localPath;
    }

    public Status getStatus() {
        return status;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getVersion() {
        return version;
    }

    public long getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public boolean isVerified() {
        return status == Status.VERIFIED;
    }

    /** Marks the record as still valid without changing what it points at. */
    public void touchVerified(long now) {
        this.status = Status.VERIFIED;
        this.lastVerifiedAt = now;
    }

    public void mark(Status newStatus) {
        this.status = newStatus != null ? newStatus : Status.PENDING;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath != null ? localPath : "";
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = Math.max(0L, sizeBytes);
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum != null ? checksum : "";
    }

    public void setVersion(String version) {
        this.version = version != null ? version : "";
    }

    public void setLastVerifiedAt(long lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public String encode() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("path", localPath);
        map.put("status", status.getKey());
        map.put("size", String.valueOf(sizeBytes));
        map.put("checksum", checksum);
        map.put("version", version);
        map.put("verifiedAt", String.valueOf(lastVerifiedAt));
        return Codec.encode(map);
    }

    public static ResourceRecord decode(String raw) {
        Map<String, String> map = Codec.decode(raw);
        if (map.isEmpty() || !map.containsKey("id")) {
            return null;
        }
        ResourceRecord record = new ResourceRecord(Codec.str(map, "id", ""));
        record.localPath = Codec.str(map, "path", "");
        record.status = Status.fromKey(Codec.str(map, "status", Status.PENDING.getKey()));
        record.sizeBytes = Codec.lng(map, "size", 0L);
        record.checksum = Codec.str(map, "checksum", "");
        record.version = Codec.str(map, "version", "");
        record.lastVerifiedAt = Codec.lng(map, "verifiedAt", 0L);
        return record;
    }

    @Override
    public String toString() {
        return "ResourceRecord{id='" + id + "', status=" + status + ", version='" + version + "'}";
    }
}
