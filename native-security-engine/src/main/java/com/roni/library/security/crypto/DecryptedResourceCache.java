package com.roni.library.security.crypto;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded in-memory cache for decrypted project resources.
 *
 * <p>Decrypting on every request would re-run AES/GCM for each image and font the page pulls, so
 * plaintext is held here instead — in RAM only, never on disk. Entries are keyed by
 * {@code projectId + '\0' + path}, which keeps projects isolated: one project can never read
 * another's cached bytes even if the paths collide.</p>
 *
 * <p>Two ceilings apply. {@link #maxBytes()} bounds the whole cache; {@link #maxEntryBytes()}
 * bounds a single entry, so one large resource cannot evict everything else and still leave the
 * cache thrashing. A resource above the entry ceiling is simply not cached — it is still served,
 * just decrypted again on the next request.</p>
 *
 * <p>Eviction is LRU by total byte size. All methods are synchronized; the WebView interceptor
 * calls in from several threads at once.</p>
 *
 * <p><b>Plaintext is not zeroed on eviction.</b> The array handed out by {@link #get} may still be
 * referenced by an in-flight {@code WebResourceResponse}; wiping it would corrupt that response.
 * Java also gives no guarantee that a {@code byte[]} is cleared before GC. The mitigation is
 * lifetime, not wiping: every lifecycle event clears the project's entries, and the cache is
 * bounded so plaintext cannot accumulate.</p>
 */
public final class DecryptedResourceCache {

    /** Default ceiling: enough for a page's CSS, JS and a few images. */
    public static final long DEFAULT_MAX_BYTES = 8L * 1024L * 1024L;

    /** Default per-entry ceiling. */
    public static final long DEFAULT_MAX_ENTRY_BYTES = 2L * 1024L * 1024L;

    private static final char SEPARATOR = '\u0000';

    private final long maxBytes;
    private final long maxEntryBytes;
    private final LinkedHashMap<String, byte[]> entries;
    private long bytes;
    private long hits;
    private long misses;
    private long evictions;

    public DecryptedResourceCache() {
        this(DEFAULT_MAX_BYTES, DEFAULT_MAX_ENTRY_BYTES);
    }

    public DecryptedResourceCache(long maxBytes) {
        this(maxBytes, Math.min(maxBytes, DEFAULT_MAX_ENTRY_BYTES));
    }

    public DecryptedResourceCache(long maxBytes, long maxEntryBytes) {
        this.maxBytes = maxBytes > 0L ? maxBytes : DEFAULT_MAX_BYTES;
        long entry = maxEntryBytes > 0L ? maxEntryBytes : DEFAULT_MAX_ENTRY_BYTES;
        this.maxEntryBytes = Math.min(entry, this.maxBytes);
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
    }

    /** @return the cached plaintext, or {@code null} when absent */
    public synchronized byte[] get(String projectId, String path) {
        byte[] value = entries.get(key(projectId, path));
        if (value == null) {
            misses++;
        } else {
            hits++;
        }
        return value;
    }

    /** @return true when the entry is present, without affecting the hit/miss counters */
    public synchronized boolean contains(String projectId, String path) {
        return entries.containsKey(key(projectId, path));
    }

    /**
     * Stores plaintext, evicting least-recently-used entries until it fits.
     *
     * <p>A resource larger than the per-entry ceiling is not cached at all: holding it would evict
     * everything else and still leave the cache unable to serve the next request.</p>
     */
    public synchronized void put(String projectId, String path, byte[] data) {
        if (data == null || data.length == 0 || data.length > maxEntryBytes) {
            return;
        }
        String key = key(projectId, path);
        byte[] previous = entries.remove(key);
        if (previous != null) {
            bytes -= previous.length;
        }
        while (bytes + data.length > maxBytes && !entries.isEmpty()) {
            Iterator<Map.Entry<String, byte[]>> it = entries.entrySet().iterator();
            if (!it.hasNext()) {
                break;
            }
            Map.Entry<String, byte[]> eldest = it.next();
            bytes -= eldest.getValue().length;
            it.remove();
            evictions++;
        }
        entries.put(key, data);
        bytes += data.length;
    }

    /** Drops every entry belonging to one project. Called when the project closes. */
    public synchronized void clearProject(String projectId) {
        String prefix = (projectId != null ? projectId : "") + SEPARATOR;
        Iterator<Map.Entry<String, byte[]>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, byte[]> entry = it.next();
            if (entry.getKey().startsWith(prefix)) {
                bytes -= entry.getValue().length;
                it.remove();
            }
        }
    }

    /** Drops everything. Called when the runtime shuts down. */
    public synchronized void clearAll() {
        entries.clear();
        bytes = 0L;
    }

    public synchronized long sizeBytes() {
        return bytes;
    }

    public synchronized int size() {
        return entries.size();
    }

    public long maxBytes() {
        return maxBytes;
    }

    public long maxEntryBytes() {
        return maxEntryBytes;
    }

    public synchronized long hits() {
        return hits;
    }

    public synchronized long misses() {
        return misses;
    }

    public synchronized long evictions() {
        return evictions;
    }

    /** @return a one-line summary for diagnostics */
    public synchronized String describe() {
        return "entries=" + entries.size() + " bytes=" + bytes + "/" + maxBytes
                + " entryMax=" + maxEntryBytes + " hits=" + hits + " misses=" + misses
                + " evictions=" + evictions;
    }

    private static String key(String projectId, String path) {
        return (projectId != null ? projectId : "") + SEPARATOR + (path != null ? path : "");
    }
}
