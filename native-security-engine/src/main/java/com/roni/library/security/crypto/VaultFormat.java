package com.roni.library.security.crypto;

import java.nio.charset.Charset;

/**
 * On-disk format for sealed project resources.
 *
 * <p>Two formats exist and both must stay readable:</p>
 *
 * <pre>
 * v1 (legacy)  [0x01][iv:12][ciphertext + tag:16]
 *              AAD = projectId
 *
 * v2 (current) [magic "RVLT":4][0x02][chunkSize:4][chunkCount:4]
 *              then chunkCount × ( [iv:12][ciphertext + tag:16] )
 *              AAD = projectId \n path \n 2 \n generation \n chunkIndex
 * </pre>
 *
 * <p>v2 exists because v1 authenticated only the project id. That left three holes: a sealed file
 * could be moved to another path inside the same project, one resource's ciphertext could replace
 * another's, and the format version itself was not covered by the tag. v2 puts the normalised
 * relative path, the format version, the key generation and the chunk index into the AAD, so all
 * four are authenticated.</p>
 *
 * <p>v2 is also chunked. A single GCM tag over a 200 MB file can only be verified after the whole
 * file has been decrypted, which forces the plaintext into memory before it is known to be
 * authentic. Per-chunk tags let a stream release each 64 KB block only after that block's tag
 * verifies, so memory stays bounded <em>and</em> unauthenticated plaintext is never exposed.</p>
 */
public final class VaultFormat {

    /** Legacy single-tag format. Read-only; never written. */
    public static final byte LEGACY_VERSION = 1;

    /** Current chunked format. */
    public static final byte VERSION = 2;

    /** {@code "RVLT"} — lets a sealed file be recognised without decrypting it. */
    public static final byte[] MAGIC = {0x52, 0x56, 0x4C, 0x54};

    public static final int IV_BYTES = 12;
    public static final int TAG_BITS = 128;
    public static final int TAG_BYTES = 16;

    /** magic(4) + version(1) + chunkSize(4) + chunkCount(4) + plainLength(8). */
    public static final int HEADER_BYTES = 21;

    /** A chunk smaller than this wastes a tag per KB; larger than this defeats the memory bound. */
    public static final int MIN_CHUNK_BYTES = 1024;
    public static final int MAX_CHUNK_BYTES = 1024 * 1024;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private VaultFormat() {
    }

    /**
     * Additional authenticated data for one v2 chunk.
     *
     * <p>Every field is length-unambiguous because the separator cannot appear in a project id
     * (UUID) or in a normalised path (NUL and newline are rejected by the manifest validator).</p>
     */
    public static byte[] aad(String projectId, String path, int generation, int chunkIndex) {
        String aad = (projectId != null ? projectId : "")
                + "\n" + normalizePath(path)
                + "\n" + VERSION
                + "\n" + generation
                + "\n" + chunkIndex;
        return aad.getBytes(UTF8);
    }

    /** AAD used by v1 files: the project id alone. */
    public static byte[] legacyAad(String projectId) {
        return (projectId != null ? projectId : "").getBytes(UTF8);
    }

    /**
     * Canonical form of a resource path, used on both sides of the AAD.
     *
     * <p>Sealing sees a filesystem-relative path ({@code js/app.js}); the WebView sees
     * {@code uri.getPath()} ({@code /js/app.js}). Both must reduce to the same string or every
     * request would fail its tag check.</p>
     */
    public static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String value = path.replace('\\', '/').trim();
        StringBuilder out = new StringBuilder(value.length());
        boolean previousSlash = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '/') {
                if (!previousSlash && out.length() > 0) {
                    out.append(c);
                }
                previousSlash = true;
            } else {
                out.append(c);
                previousSlash = false;
            }
        }
        String result = out.toString();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /** @return true when {@code head} starts with {@link #MAGIC} */
    public static boolean hasMagic(byte[] head) {
        if (head == null || head.length < MAGIC.length) {
            return false;
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (head[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** @return true when {@code head} looks like a legacy v1 blob */
    public static boolean isLegacy(byte[] head) {
        return head != null && head.length > 0 && head[0] == LEGACY_VERSION && !hasMagic(head);
    }

    public static int readInt(byte[] buffer, int offset) {
        return ((buffer[offset] & 0xFF) << 24)
                | ((buffer[offset + 1] & 0xFF) << 16)
                | ((buffer[offset + 2] & 0xFF) << 8)
                | (buffer[offset + 3] & 0xFF);
    }

    public static void writeInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value >>> 24);
        buffer[offset + 1] = (byte) (value >>> 16);
        buffer[offset + 2] = (byte) (value >>> 8);
        buffer[offset + 3] = (byte) value;
    }

    public static long readLong(byte[] buffer, int offset) {
        long value = 0L;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (buffer[offset + i] & 0xFFL);
        }
        return value;
    }

    public static void writeLong(byte[] buffer, int offset, long value) {
        for (int i = 7; i >= 0; i--) {
            buffer[offset + i] = (byte) value;
            value >>>= 8;
        }
    }

    /** @return number of chunks needed for {@code length} plaintext bytes */
    public static int chunkCountFor(long length, int chunkSize) {
        if (length <= 0L) {
            return 0;
        }
        return (int) ((length + chunkSize - 1L) / chunkSize);
    }

    /** @return plaintext size of chunk {@code index}, given the total and the chunk size */
    public static int chunkPlainLength(int index, int chunkCount, int chunkSize, long total) {
        if (chunkCount <= 0 || index < 0 || index >= chunkCount) {
            return 0;
        }
        if (index < chunkCount - 1) {
            return chunkSize;
        }
        long remainder = total - (long) chunkSize * (chunkCount - 1);
        return (int) Math.max(0L, remainder);
    }
}
