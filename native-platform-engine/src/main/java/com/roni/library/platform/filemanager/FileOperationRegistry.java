package com.roni.library.platform.filemanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.filemanager.FileManagerLimits;
import com.roni.library.contracts.filemanager.FileOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks long running file operations so the UI and the bridge can poll and cancel them. */
public final class FileOperationRegistry {

    private static final class Entry {
        final String id;
        final String type;
        final long startedAt;
        final long totalBytes;
        final int totalItems;
        volatile String state = FileOperation.STATE_RUNNING;
        volatile long processedBytes;
        volatile int processedItems;
        volatile String message = "";
        volatile long finishedAt;
        volatile boolean cancelled;

        Entry(String id, String type, long totalBytes, int totalItems) {
            this.id = id;
            this.type = type;
            this.totalBytes = totalBytes;
            this.totalItems = totalItems;
            this.startedAt = System.currentTimeMillis();
        }

        FileOperation snapshot() {
            return new FileOperation(id, type, state, processedBytes, totalBytes,
                    processedItems, totalItems, message, startedAt, finishedAt);
        }
    }

    private final Map<String, Entry> operations = new ConcurrentHashMap<>();

    @NonNull
    public String start(@NonNull String type, long totalBytes, int totalItems) {
        prune();
        String id = UUID.randomUUID().toString();
        operations.put(id, new Entry(id, type, totalBytes, totalItems));
        return id;
    }

    public void progress(@NonNull String id, long processedBytes, int processedItems) {
        Entry entry = operations.get(id);
        if (entry != null) {
            entry.processedBytes = processedBytes;
            entry.processedItems = processedItems;
        }
    }

    public void complete(@NonNull String id, @Nullable String message) {
        Entry entry = operations.get(id);
        if (entry != null) {
            entry.state = FileOperation.STATE_COMPLETED;
            entry.message = message != null ? message : "";
            entry.finishedAt = System.currentTimeMillis();
        }
    }

    public void fail(@NonNull String id, @Nullable String message) {
        Entry entry = operations.get(id);
        if (entry != null) {
            entry.state = FileOperation.STATE_FAILED;
            entry.message = message != null ? message : "";
            entry.finishedAt = System.currentTimeMillis();
        }
    }

    public void cancel(@NonNull String id) {
        Entry entry = operations.get(id);
        if (entry != null && FileOperation.STATE_RUNNING.equals(entry.state)) {
            entry.cancelled = true;
            entry.state = FileOperation.STATE_CANCELLED;
            entry.message = "Cancelled by the user";
            entry.finishedAt = System.currentTimeMillis();
        }
    }

    public boolean isCancelled(@NonNull String id) {
        Entry entry = operations.get(id);
        return entry != null && entry.cancelled;
    }

    @Nullable
    public FileOperation get(@NonNull String id) {
        Entry entry = operations.get(id);
        return entry != null ? entry.snapshot() : null;
    }

    @NonNull
    public List<FileOperation> list() {
        List<FileOperation> values = new ArrayList<>();
        for (Entry entry : operations.values()) {
            values.add(entry.snapshot());
        }
        values.sort((left, right) -> Long.compare(right.getStartedAt(), left.getStartedAt()));
        return values;
    }

    private void prune() {
        if (operations.size() < FileManagerLimits.MAX_OPERATION_HISTORY) {
            return;
        }
        Map<String, Long> finished = new LinkedHashMap<>();
        for (Entry entry : operations.values()) {
            if (entry.finishedAt > 0) {
                finished.put(entry.id, entry.finishedAt);
            }
        }
        List<Map.Entry<String, Long>> ordered = new ArrayList<>(finished.entrySet());
        ordered.sort(Map.Entry.comparingByValue());
        int remove = Math.max(1, operations.size() - FileManagerLimits.MAX_OPERATION_HISTORY + 1);
        for (int i = 0; i < ordered.size() && i < remove; i++) {
            operations.remove(ordered.get(i).getKey());
        }
    }
}
