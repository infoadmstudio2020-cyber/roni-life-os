package com.roni.library.devtools.inspector;

import com.roni.library.contracts.logger.LogEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded, thread-safe store for the unified log stream.
 *
 * <p>This is the single source of truth the Inspector renders. It is a ring buffer: once
 * {@link #getCapacity()} entries are held, the oldest is evicted on every insert, so a runaway
 * page cannot grow the heap without bound. Evictions are counted so the UI can say how many
 * entries were dropped instead of silently losing them.</p>
 *
 * <p>It stores the shared {@link LogEntry} model — the inspector does not declare a second log
 * data model. Listeners are notified <b>outside</b> the lock, so a slow UI callback can never
 * block a WebView console message or a bridge call.</p>
 */
public final class RuntimeLogRepository {

    /** Default ring size. Large enough for a startup trace, small enough to stay cheap. */
    public static final int DEFAULT_CAPACITY = 1000;

    /** Hard ceiling, so a settings value cannot be used to exhaust memory. */
    public static final int MAX_CAPACITY = 20000;

    /** Receives repository events. Implementations must be fast and must not block. */
    public interface Listener {
        void onLogAdded(LogEntry entry);

        void onLogsCleared();
    }

    private final Object lock = new Object();
    private final Deque<LogEntry> entries = new ArrayDeque<>();
    private final AtomicLong sequence = new AtomicLong(0L);
    private final AtomicLong total = new AtomicLong(0L);
    private final AtomicLong dropped = new AtomicLong(0L);
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private volatile int capacity;

    public RuntimeLogRepository() {
        this(DEFAULT_CAPACITY);
    }

    public RuntimeLogRepository(int capacity) {
        this.capacity = clampCapacity(capacity);
    }

    /** Reserves the next entry id. Ids are monotonic and never reused. */
    public long nextId() {
        return sequence.incrementAndGet();
    }

    /**
     * Appends an entry, evicting the oldest when the buffer is full.
     *
     * @return true when the entry was stored
     */
    public boolean add(LogEntry entry) {
        if (entry == null) {
            return false;
        }
        boolean evicted = false;
        synchronized (lock) {
            while (entries.size() >= capacity) {
                entries.pollFirst();
                evicted = true;
            }
            entries.addLast(entry);
            total.incrementAndGet();
            if (evicted) {
                dropped.incrementAndGet();
            }
        }
        notifyAdded(entry);
        return true;
    }

    /** Chronological snapshot. Safe to hand to a background thread. */
    public List<LogEntry> snapshot() {
        synchronized (lock) {
            return new ArrayList<>(entries);
        }
    }

    /** The most recent {@code limit} entries, oldest first. */
    public List<LogEntry> recent(int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }
        synchronized (lock) {
            int size = entries.size();
            int skip = Math.max(0, size - limit);
            List<LogEntry> result = new ArrayList<>(Math.min(limit, size));
            int index = 0;
            for (LogEntry entry : entries) {
                if (index++ >= skip) {
                    result.add(entry);
                }
            }
            return result;
        }
    }

    public int size() {
        synchronized (lock) {
            return entries.size();
        }
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int getCapacity() {
        return capacity;
    }

    /**
     * Resizes the ring. Shrinking evicts the oldest entries immediately so the buffer never
     * exceeds the configured maximum.
     */
    public void setCapacity(int capacity) {
        int target = clampCapacity(capacity);
        synchronized (lock) {
            this.capacity = target;
            while (entries.size() > target) {
                entries.pollFirst();
                dropped.incrementAndGet();
            }
        }
    }

    /** Number of entries evicted because the buffer was full. */
    public long getDroppedCount() {
        return dropped.get();
    }

    /** Total entries ever accepted, including evicted ones. */
    public long getTotalCount() {
        return total.get();
    }

    /** Removes every entry. Listeners are told so the UI can clear itself. */
    public void clear() {
        synchronized (lock) {
            entries.clear();
        }
        for (Listener listener : listeners) {
            try {
                listener.onLogsCleared();
            } catch (Throwable ignored) {
                // A broken listener must not break logging.
            }
        }
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /** Drops every listener — called when the host Activity is destroyed. */
    public void removeAllListeners() {
        listeners.clear();
    }

    public int getListenerCount() {
        return listeners.size();
    }

    private void notifyAdded(LogEntry entry) {
        for (Listener listener : listeners) {
            try {
                listener.onLogAdded(entry);
            } catch (Throwable ignored) {
                // A broken listener must not break logging.
            }
        }
    }

    private static int clampCapacity(int value) {
        if (value < 50) {
            return 50;
        }
        return Math.min(value, MAX_CAPACITY);
    }
}
