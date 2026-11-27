package com.roni.library.security.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The format layer of the vault: everything that turns bytes into a sealed blob and back, with no
 * dependency on Android.
 *
 * <p>Key <em>management</em> lives in {@link ProjectVault} (Android Keystore). Key <em>use</em>
 * lives here, which is what makes the format testable on a plain JVM with a software AES key —
 * the chunking, the AAD binding and the tag verification are identical either way.</p>
 *
 * <p>See {@link VaultFormat} for the byte layout and the reasoning behind v2.</p>
 */
public final class VaultCipher {

    static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private VaultCipher() {
    }

    /** Encrypts {@code plain} as a v2 blob bound to {@code projectId} and {@code path}. */
    public static byte[] seal(String projectId, String path, int generation, SecretKey key,
                              byte[] plain) throws GeneralSecurityException, IOException {
        byte[] data = plain != null ? plain : new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                VaultFormat.HEADER_BYTES + data.length + VaultFormat.TAG_BYTES * 2);
        sealStream(projectId, path, generation, key, new ByteArrayInputStream(data),
                data.length, out);
        return out.toByteArray();
    }

    /**
     * Decrypts a v2 blob, falling back to the legacy v1 format when the magic is absent.
     *
     * <p>The fallback is what keeps projects sealed before v2 readable. It is a read path only —
     * nothing is ever written as v1 again.</p>
     */
    public static byte[] open(String projectId, String path, int generation, SecretKey key,
                              byte[] sealed) throws GeneralSecurityException, IOException {
        if (sealed == null || sealed.length == 0) {
            throw new GeneralSecurityException("Sealed blob is empty");
        }
        if (!VaultFormat.hasMagic(sealed)) {
            return openLegacy(projectId, key, sealed);
        }
        if (sealed.length < VaultFormat.HEADER_BYTES) {
            throw new GeneralSecurityException("Sealed blob is truncated");
        }
        int chunkSize = VaultFormat.readInt(sealed, 5);
        int chunkCount = VaultFormat.readInt(sealed, 9);
        long plainLength = VaultFormat.readLong(sealed, 13);
        validateHeader(chunkSize, chunkCount, plainLength);
        if (plainLength > VaultLimits.getMaxResourceBytes()) {
            throw new GeneralSecurityException("Resource exceeds the "
                    + VaultLimits.getMaxResourceBytes() + " byte limit");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream((int) plainLength);
        int offset = VaultFormat.HEADER_BYTES;
        for (int index = 0; index < chunkCount; index++) {
            int plainChunk = VaultFormat.chunkPlainLength(index, chunkCount, chunkSize, plainLength);
            int bodyLength = plainChunk + VaultFormat.TAG_BYTES;
            if (offset + VaultFormat.IV_BYTES + bodyLength > sealed.length) {
                throw new GeneralSecurityException("Sealed blob is truncated at chunk " + index);
            }
            byte[] iv = new byte[VaultFormat.IV_BYTES];
            System.arraycopy(sealed, offset, iv, 0, VaultFormat.IV_BYTES);
            offset += VaultFormat.IV_BYTES;
            byte[] plain = decryptChunk(projectId, path, generation, key, index, iv,
                    sealed, offset, bodyLength);
            out.write(plain, 0, plain.length);
            offset += bodyLength;
        }
        return out.toByteArray();
    }

    /** Decrypts a v1 blob: single tag, AAD is the project id alone. */
    public static byte[] openLegacy(String projectId, SecretKey key, byte[] sealed)
            throws GeneralSecurityException, IOException {
        if (sealed == null || sealed.length <= 1 + VaultFormat.IV_BYTES) {
            throw new GeneralSecurityException("Sealed blob is truncated");
        }
        if (sealed[0] != VaultFormat.LEGACY_VERSION) {
            throw new GeneralSecurityException("Unsupported vault format " + sealed[0]);
        }
        byte[] iv = new byte[VaultFormat.IV_BYTES];
        System.arraycopy(sealed, 1, iv, 0, VaultFormat.IV_BYTES);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(VaultFormat.TAG_BITS, iv));
        cipher.updateAAD(VaultFormat.legacyAad(projectId));
        return cipher.doFinal(sealed, 1 + VaultFormat.IV_BYTES,
                sealed.length - 1 - VaultFormat.IV_BYTES);
    }

    /**
     * Encrypts {@code plain} into {@code out} as a v2 blob.
     *
     * <p>Memory is bounded by one chunk. {@code length} must be the exact plaintext length; it is
     * written into the header so a reader can size the last chunk without guessing.</p>
     */
    public static void sealStream(String projectId, String path, int generation, SecretKey key,
                                  InputStream plain, long length, OutputStream out)
            throws GeneralSecurityException, IOException {
        if (plain == null || out == null) {
            throw new IllegalArgumentException("plain and out are required");
        }
        if (length < 0L) {
            throw new IllegalArgumentException("length must not be negative");
        }
        int chunkSize = VaultLimits.getChunkBytes();
        int chunkCount = VaultFormat.chunkCountFor(length, chunkSize);

        byte[] header = new byte[VaultFormat.HEADER_BYTES];
        System.arraycopy(VaultFormat.MAGIC, 0, header, 0, VaultFormat.MAGIC.length);
        header[4] = VaultFormat.VERSION;
        VaultFormat.writeInt(header, 5, chunkSize);
        VaultFormat.writeInt(header, 9, chunkCount);
        VaultFormat.writeLong(header, 13, length);
        out.write(header);

        byte[] buffer = new byte[chunkSize];
        for (int index = 0; index < chunkCount; index++) {
            int want = VaultFormat.chunkPlainLength(index, chunkCount, chunkSize, length);
            readFully(plain, buffer, want);
            byte[] iv = CryptoHash.generateRandomBytes(VaultFormat.IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(VaultFormat.TAG_BITS, iv));
            cipher.updateAAD(VaultFormat.aad(projectId, path, generation, index));
            byte[] body = cipher.doFinal(buffer, 0, want);
            out.write(iv);
            out.write(body);
        }
        out.flush();
    }

    /**
     * Opens a v2 sealed stream.
     *
     * <p>The returned {@link SealedStream} exposes the plaintext length before any decryption, so
     * the caller can decide between buffering and streaming. The caller owns the returned stream
     * and must close it.</p>
     */
    public static SealedStream openStream(String projectId, String path, int generation,
                                          SecretKey key, InputStream sealed)
            throws GeneralSecurityException, IOException {
        if (sealed == null) {
            throw new IllegalArgumentException("sealed is required");
        }
        byte[] header = new byte[VaultFormat.HEADER_BYTES];
        try {
            readFully(sealed, header, VaultFormat.HEADER_BYTES);
        } catch (IOException error) {
            closeQuietly(sealed);
            throw new GeneralSecurityException("Sealed resource is truncated", error);
        }
        if (!VaultFormat.hasMagic(header)) {
            closeQuietly(sealed);
            throw new GeneralSecurityException("Not a sealed resource");
        }
        if (header[4] != VaultFormat.VERSION) {
            closeQuietly(sealed);
            throw new GeneralSecurityException("Unsupported vault format " + header[4]);
        }
        int chunkSize = VaultFormat.readInt(header, 5);
        int chunkCount = VaultFormat.readInt(header, 9);
        long plainLength = VaultFormat.readLong(header, 13);
        try {
            validateHeader(chunkSize, chunkCount, plainLength);
        } catch (GeneralSecurityException error) {
            closeQuietly(sealed);
            throw error;
        }
        return new SealedStream(new SealedInputStream(sealed, projectId, path, generation, key,
                chunkSize, chunkCount, plainLength), plainLength);
    }

    /** A sealed stream plus the plaintext length it will produce. */
    public static final class SealedStream implements java.io.Closeable {
        private final SealedInputStream stream;
        private final long plaintextLength;

        SealedStream(SealedInputStream stream, long plaintextLength) {
            this.stream = stream;
            this.plaintextLength = plaintextLength;
        }

        public long plaintextLength() {
            return plaintextLength;
        }

        public InputStream stream() {
            return stream;
        }

        @Override
        public void close() {
            closeQuietly(stream);
        }
    }

    // ==================== internals ====================

    private static byte[] decryptChunk(String projectId, String path, int generation, SecretKey key,
                                       int index, byte[] iv, byte[] source, int offset, int length)
            throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(VaultFormat.TAG_BITS, iv));
        cipher.updateAAD(VaultFormat.aad(projectId, path, generation, index));
        return cipher.doFinal(source, offset, length);
    }

    private static void validateHeader(int chunkSize, int chunkCount, long plainLength)
            throws GeneralSecurityException {
        if (chunkSize < VaultFormat.MIN_CHUNK_BYTES || chunkSize > VaultFormat.MAX_CHUNK_BYTES) {
            throw new GeneralSecurityException("Invalid chunk size " + chunkSize);
        }
        if (chunkCount < 0 || plainLength < 0L) {
            throw new GeneralSecurityException("Invalid sealed header");
        }
        if (VaultFormat.chunkCountFor(plainLength, chunkSize) != chunkCount) {
            throw new GeneralSecurityException("Sealed header is inconsistent");
        }
    }

    private static void readFully(InputStream in, byte[] target, int length) throws IOException {
        int read = 0;
        while (read < length) {
            int step = in.read(target, read, length - read);
            if (step < 0) {
                throw new IOException("Unexpected end of stream");
            }
            read += step;
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Throwable ignored) {
            // The caller is already unwinding.
        }
    }
}
