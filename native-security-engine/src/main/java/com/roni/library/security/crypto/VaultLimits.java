package com.roni.library.security.crypto;

/**
 * Configurable ceilings for the vault pipeline.
 *
 * <p>Every limit is a hard bound on memory, not a hint. A resource that exceeds
 * {@link #getMaxResourceBytes()} is refused rather than buffered; a resource that exceeds
 * {@link #getMaxBufferedBytes()} is streamed chunk by chunk instead of being decrypted into a
 * single array.</p>
 *
 * <p>Values are {@code volatile} and settable at runtime so a device with a small heap can lower
 * them without a rebuild. Setters clamp to a sane range instead of throwing, because a bad value
 * from a config file must not brick the WebView.</p>
 */
public final class VaultLimits {

    /** Refuse to serve anything larger than this, decrypted. 64 MB. */
    public static final long DEFAULT_MAX_RESOURCE_BYTES = 64L * 1024L * 1024L;

    /** Above this, stream instead of buffering. 2 MB. */
    public static final long DEFAULT_MAX_BUFFERED_BYTES = 2L * 1024L * 1024L;

    /** Plaintext bytes per authenticated chunk. 64 KB. */
    public static final int DEFAULT_CHUNK_BYTES = 64 * 1024;

    private static final long MIN_RESOURCE_BYTES = 1024L * 1024L;
    private static final long MAX_RESOURCE_BYTES = 512L * 1024L * 1024L;
    private static final long MIN_BUFFERED_BYTES = 64L * 1024L;

    private static volatile long maxResourceBytes = DEFAULT_MAX_RESOURCE_BYTES;
    private static volatile long maxBufferedBytes = DEFAULT_MAX_BUFFERED_BYTES;
    private static volatile int chunkBytes = DEFAULT_CHUNK_BYTES;

    private VaultLimits() {
    }

    public static long getMaxResourceBytes() {
        return maxResourceBytes;
    }

    public static void setMaxResourceBytes(long value) {
        maxResourceBytes = clamp(value, MIN_RESOURCE_BYTES, MAX_RESOURCE_BYTES);
        if (maxBufferedBytes > maxResourceBytes) {
            maxBufferedBytes = maxResourceBytes;
        }
    }

    public static long getMaxBufferedBytes() {
        return maxBufferedBytes;
    }

    public static void setMaxBufferedBytes(long value) {
        maxBufferedBytes = clamp(value, MIN_BUFFERED_BYTES, maxResourceBytes);
    }

    public static int getChunkBytes() {
        return chunkBytes;
    }

    public static void setChunkBytes(int value) {
        chunkBytes = (int) clamp(value, VaultFormat.MIN_CHUNK_BYTES, VaultFormat.MAX_CHUNK_BYTES);
    }

    /** Restores the defaults. Used by tests and by a settings reset. */
    public static void reset() {
        maxResourceBytes = DEFAULT_MAX_RESOURCE_BYTES;
        maxBufferedBytes = DEFAULT_MAX_BUFFERED_BYTES;
        chunkBytes = DEFAULT_CHUNK_BYTES;
    }

    private static long clamp(long value, long min, long max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
