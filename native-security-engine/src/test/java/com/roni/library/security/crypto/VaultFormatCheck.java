package com.roni.library.security.crypto;

import java.nio.charset.StandardCharsets;

/**
 * Standalone checks for {@link VaultFormat}.
 *
 * <p>Plain {@code main} rather than JUnit so it runs with nothing but a JDK. Exits non-zero on the
 * first failure.</p>
 */
public final class VaultFormatCheck {

    private static int passed;

    public static void main(String[] args) {
        normalizesPaths();
        countsChunks();
        sizesLastChunk();
        detectsMagic();
        roundTripsNumbers();
        bindsAadToEveryField();

        System.out.println("VaultFormatCheck: " + passed + " checks passed");
    }

    private static void normalizesPaths() {
        eq("js/app.js", VaultFormat.normalizePath("/js/app.js"), "leading slash stripped");
        eq("js/app.js", VaultFormat.normalizePath("js/app.js"), "relative path unchanged");
        eq("js/app.js", VaultFormat.normalizePath("js//app.js"), "duplicate slash collapsed");
        eq("js/app.js", VaultFormat.normalizePath("\\js\\app.js"), "backslashes normalised");
        eq("js/app.js", VaultFormat.normalizePath("  /js/app.js  "), "whitespace trimmed");
        eq("js", VaultFormat.normalizePath("js/"), "trailing slash stripped");
        eq("", VaultFormat.normalizePath(""), "empty stays empty");
        eq("", VaultFormat.normalizePath(null), "null becomes empty");
        eq("a/b/c", VaultFormat.normalizePath("///a///b///c///"), "repeated separators collapse");
    }

    private static void countsChunks() {
        eq(0, VaultFormat.chunkCountFor(0L, 64), "empty file has no chunks");
        eq(1, VaultFormat.chunkCountFor(1L, 64), "one byte is one chunk");
        eq(1, VaultFormat.chunkCountFor(64L, 64), "exact multiple is one chunk");
        eq(2, VaultFormat.chunkCountFor(65L, 64), "one byte over starts a second chunk");
        eq(2, VaultFormat.chunkCountFor(128L, 64), "two exact multiples are two chunks");
        eq(3, VaultFormat.chunkCountFor(129L, 64), "one byte over two chunks starts a third");
    }

    private static void sizesLastChunk() {
        eq(64, VaultFormat.chunkPlainLength(0, 2, 64, 100L), "first chunk is full");
        eq(36, VaultFormat.chunkPlainLength(1, 2, 64, 100L), "last chunk is the remainder");
        eq(64, VaultFormat.chunkPlainLength(0, 1, 64, 64L), "single full chunk");
        eq(0, VaultFormat.chunkPlainLength(0, 0, 64, 0L), "no chunks for an empty file");
    }

    private static void detectsMagic() {
        byte[] head = new byte[VaultFormat.HEADER_BYTES];
        System.arraycopy(VaultFormat.MAGIC, 0, head, 0, VaultFormat.MAGIC.length);
        check(VaultFormat.hasMagic(head), "magic is recognised");
        check(!VaultFormat.hasMagic(new byte[]{0x01, 0x02, 0x03, 0x04}), "other bytes are not magic");
        check(!VaultFormat.hasMagic(new byte[]{0x52}), "short buffer is not magic");
        check(!VaultFormat.hasMagic(null), "null is not magic");

        byte[] legacy = new byte[]{VaultFormat.LEGACY_VERSION, 0, 0, 0, 0};
        check(VaultFormat.isLegacy(legacy), "v1 blob is detected");
        check(!VaultFormat.isLegacy(head), "v2 blob is not legacy");
    }

    private static void roundTripsNumbers() {
        byte[] buffer = new byte[12];
        VaultFormat.writeInt(buffer, 0, 0x01020304);
        eq(0x01020304, VaultFormat.readInt(buffer, 0), "int round-trips");
        VaultFormat.writeInt(buffer, 0, -1);
        eq(-1, VaultFormat.readInt(buffer, 0), "negative int round-trips");

        VaultFormat.writeLong(buffer, 0, 0x0102030405060708L);
        eq(0x0102030405060708L, VaultFormat.readLong(buffer, 0), "long round-trips");
        VaultFormat.writeLong(buffer, 0, Long.MAX_VALUE);
        eq(Long.MAX_VALUE, VaultFormat.readLong(buffer, 0), "max long round-trips");
    }

    private static void bindsAadToEveryField() {
        byte[] base = VaultFormat.aad("p1", "a.js", 0, 0);
        check(!same(base, VaultFormat.aad("p2", "a.js", 0, 0)), "AAD binds the project id");
        check(!same(base, VaultFormat.aad("p1", "b.js", 0, 0)), "AAD binds the path");
        check(!same(base, VaultFormat.aad("p1", "a.js", 1, 0)), "AAD binds the generation");
        check(!same(base, VaultFormat.aad("p1", "a.js", 0, 1)), "AAD binds the chunk index");
        check(same(base, VaultFormat.aad("p1", "/a.js", 0, 0)),
                "AAD is stable across path normalisation");
        check(!same(VaultFormat.legacyAad("p1"), base), "legacy AAD differs from v2 AAD");
        eq("p1", new String(VaultFormat.legacyAad("p1"), StandardCharsets.UTF_8),
                "legacy AAD is the project id");
    }

    // ==================== harness ====================

    private static boolean same(byte[] a, byte[] b) {
        return java.util.Arrays.equals(a, b);
    }

    private static void eq(Object want, Object got, String label) {
        if (want == null ? got != null : !want.equals(got)) {
            fail(label + ": expected " + want + " but got " + got);
        }
        passed++;
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            fail(label);
        }
        passed++;
    }

    private static void fail(String message) {
        System.err.println("FAIL: " + message);
        System.exit(1);
    }
}
