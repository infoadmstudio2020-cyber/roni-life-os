package com.roni.life.os.app.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Verifies that a resource really exists on disk and still matches what was recorded.
 *
 * <p>Metadata alone is never trusted: a record that says "downloaded" is only believed after the
 * file passes this check. Size is compared first because it is free; the SHA-256 digest is only
 * computed when a checksum was recorded.</p>
 */
public final class ResourceVerifier {

    /** Outcome of a verification. */
    public enum Result {
        OK,
        MISSING,
        NOT_A_FILE,
        SIZE_MISMATCH,
        CHECKSUM_MISMATCH;

        public boolean isOk() {
            return this == OK;
        }
    }

    private static final int BUFFER_SIZE = 16 * 1024;

    private ResourceVerifier() {
    }

    public static Result verify(File file, long expectedSize, String expectedChecksum) {
        if (file == null || !file.exists()) {
            return Result.MISSING;
        }
        if (!file.isFile()) {
            return Result.NOT_A_FILE;
        }
        if (expectedSize > 0L && file.length() != expectedSize) {
            return Result.SIZE_MISMATCH;
        }
        if (expectedChecksum != null && !expectedChecksum.trim().isEmpty()) {
            String actual = sha256(file);
            if (actual == null || !actual.equalsIgnoreCase(expectedChecksum.trim())) {
                return Result.CHECKSUM_MISMATCH;
            }
        }
        return Result.OK;
    }

    /** @return the lowercase hex SHA-256 of {@code file}, or {@code null} when unreadable */
    public static String sha256(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try (InputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (Throwable error) {
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
