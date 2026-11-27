package com.roni.library.security.crypto;

/**
 * Standalone checks for {@link DecryptedResourceCache}.
 *
 * <p>Plain {@code main} rather than JUnit so it runs with nothing but a JDK:
 * {@code java DecryptedResourceCacheCheck}. Exits non-zero on the first failure.</p>
 */
public final class DecryptedResourceCacheCheck {

    private static int passed;

    public static void main(String[] args) {
        storesAndReturns();
        isolatesProjects();
        evictsLeastRecentlyUsed();
        refusesOversizedEntry();
        clearsOneProjectOnly();
        clearsEverything();
        System.out.println("DecryptedResourceCacheCheck: " + passed + " checks passed");
    }

    private static void storesAndReturns() {
        DecryptedResourceCache cache = new DecryptedResourceCache(1024);
        cache.put("p1", "/index.html", bytes(10));
        check(cache.get("p1", "/index.html") != null, "stored entry is returned");
        check(cache.get("p1", "/missing.html") == null, "unknown path returns null");
    }

    private static void isolatesProjects() {
        DecryptedResourceCache cache = new DecryptedResourceCache(1024);
        cache.put("p1", "/index.html", bytes(10));
        check(cache.get("p2", "/index.html") == null,
                "same path under another project is not visible");
    }

    private static void evictsLeastRecentlyUsed() {
        DecryptedResourceCache cache = new DecryptedResourceCache(30);
        cache.put("p1", "/a", bytes(10));
        cache.put("p1", "/b", bytes(10));
        cache.put("p1", "/c", bytes(10));
        cache.get("p1", "/a");            // /a becomes most recent, /b the eldest
        cache.put("p1", "/d", bytes(10)); // forces one eviction
        check(cache.get("p1", "/b") == null, "least recently used entry is evicted");
        check(cache.get("p1", "/a") != null, "recently used entry survives");
        check(cache.sizeBytes() <= 30, "cache stays within its budget");
    }

    private static void refusesOversizedEntry() {
        DecryptedResourceCache cache = new DecryptedResourceCache(16);
        cache.put("p1", "/big", bytes(64));
        check(cache.get("p1", "/big") == null, "entry larger than the budget is not cached");
        check(cache.size() == 0, "oversized entry does not evict others");
    }

    private static void clearsOneProjectOnly() {
        DecryptedResourceCache cache = new DecryptedResourceCache(1024);
        cache.put("p1", "/a", bytes(10));
        cache.put("p2", "/a", bytes(10));
        cache.clearProject("p1");
        check(cache.get("p1", "/a") == null, "closed project is cleared");
        check(cache.get("p2", "/a") != null, "other project is untouched");
    }

    private static void clearsEverything() {
        DecryptedResourceCache cache = new DecryptedResourceCache(1024);
        cache.put("p1", "/a", bytes(10));
        cache.put("p2", "/b", bytes(10));
        cache.clearAll();
        check(cache.size() == 0 && cache.sizeBytes() == 0, "clearAll empties the cache");
    }

    private static byte[] bytes(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) i;
        }
        return data;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
        passed++;
    }
}
