package com.roni.library.contracts.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** Long running operation tracked by the engine so the UI and bridge can poll progress. */
public final class FileOperation {

    public static final String STATE_RUNNING = "RUNNING";
    public static final String STATE_COMPLETED = "COMPLETED";
    public static final String STATE_FAILED = "FAILED";
    public static final String STATE_CANCELLED = "CANCELLED";

    private final String id;
    private final String type;
    private final String state;
    private final long processedBytes;
    private final long totalBytes;
    private final int processedItems;
    private final int totalItems;
    private final String message;
    private final long startedAt;
    private final long finishedAt;

    public FileOperation(@NonNull String id, @NonNull String type, @NonNull String state,
                         long processedBytes, long totalBytes, int processedItems, int totalItems,
                         @Nullable String message, long startedAt, long finishedAt) {
        this.id = id;
        this.type = type;
        this.state = state;
        this.processedBytes = processedBytes;
        this.totalBytes = totalBytes;
        this.processedItems = processedItems;
        this.totalItems = totalItems;
        this.message = message != null ? message : "";
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getType() {
        return type;
    }

    @NonNull
    public String getState() {
        return state;
    }

    public long getProcessedBytes() {
        return processedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public int getProcessedItems() {
        return processedItems;
    }

    public int getTotalItems() {
        return totalItems;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public boolean isFinished() {
        return !STATE_RUNNING.equals(state);
    }

    public int getProgressPercent() {
        if (totalBytes > 0) {
            return (int) Math.min(100L, processedBytes * 100L / totalBytes);
        }
        if (totalItems > 0) {
            return Math.min(100, processedItems * 100 / totalItems);
        }
        return isFinished() ? 100 : 0;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("type", type);
        map.put("state", state);
        map.put("processedBytes", processedBytes);
        map.put("totalBytes", totalBytes);
        map.put("processedItems", processedItems);
        map.put("totalItems", totalItems);
        map.put("progress", getProgressPercent());
        map.put("message", message);
        map.put("startedAt", startedAt);
        map.put("finishedAt", finishedAt);
        return map;
    }
}
