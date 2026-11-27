package com.roni.library.contracts.automation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Snapshot of a transfer handled by the transfer engine. */
public final class TransferProgress {

    public static final String STATE_QUEUED = "QUEUED";
    public static final String STATE_RUNNING = "RUNNING";
    public static final String STATE_COMPLETED = "COMPLETED";
    public static final String STATE_FAILED = "FAILED";
    public static final String STATE_CANCELLED = "CANCELLED";

    private final String id;
    private final String direction;
    private final String state;
    private final long bytesTransferred;
    private final long totalBytes;
    private final String localPath;
    private final String error;
    private final long updatedAt;

    public TransferProgress(String id, String direction, String state, long bytesTransferred,
                            long totalBytes, String localPath, String error, long updatedAt) {
        this.id = id;
        this.direction = direction;
        this.state = state;
        this.bytesTransferred = bytesTransferred;
        this.totalBytes = totalBytes;
        this.localPath = localPath;
        this.error = error;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getDirection() {
        return direction;
    }

    public String getState() {
        return state;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getError() {
        return error;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public int getPercent() {
        if (totalBytes <= 0L) {
            return STATE_COMPLETED.equals(state) ? 100 : 0;
        }
        return (int) Math.min(100L, bytesTransferred * 100L / totalBytes);
    }

    public boolean isFinished() {
        return STATE_COMPLETED.equals(state)
                || STATE_FAILED.equals(state)
                || STATE_CANCELLED.equals(state);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("direction", direction);
        map.put("state", state);
        map.put("bytesTransferred", bytesTransferred);
        map.put("totalBytes", totalBytes);
        map.put("percent", getPercent());
        map.put("localPath", localPath);
        map.put("error", error);
        map.put("updatedAt", updatedAt);
        map.put("finished", isFinished());
        return map;
    }
}
