package com.roni.library.security.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Decrypts a v2 sealed resource one authenticated chunk at a time.
 *
 * <p>The point of this class is the ordering guarantee: a chunk's GCM tag is verified by
 * {@code doFinal} <em>before</em> any of its plaintext is handed to the caller. A single-tag file
 * cannot offer that — the tag only verifies at the very end, so a streaming reader would have
 * already released unauthenticated bytes. Per-chunk tags make streaming safe.</p>
 *
 * <p>Memory is bounded by one chunk (64 KB by default) plus its ciphertext, regardless of how large
 * the resource is. Nothing is written to disk.</p>
 *
 * <p>Not thread-safe: one instance per request, consumed by one reader.</p>
 */
public final class SealedInputStream extends InputStream {

    private final InputStream source;
    private final String projectId;
    private final String path;
    private final int generation;
    private final SecretKey key;
    private final int chunkSize;
    private final int chunkCount;
    private final long plaintextLength;

    private int nextChunk;
    private byte[] buffer;
    private int position;
    private int limit;
    private boolean closed;

    SealedInputStream(InputStream source, String projectId, String path, int generation,
                      SecretKey key, int chunkSize, int chunkCount, long plaintextLength) {
        this.source = source;
        this.projectId = projectId;
        this.path = path;
        this.generation = generation;
        this.key = key;
        this.chunkSize = chunkSize;
        this.chunkCount = chunkCount;
        this.plaintextLength = plaintextLength;
    }

    /** @return the exact number of plaintext bytes this stream will produce */
    public long plaintextLength() {
        return plaintextLength;
    }

    @Override
    public int read() throws IOException {
        if (!ensureBuffered()) {
            return -1;
        }
        return buffer[position++] & 0xFF;
    }

    @Override
    public int read(byte[] target, int offset, int length) throws IOException {
        if (target == null) {
            throw new NullPointerException("target");
        }
        if (offset < 0 || length < 0 || length > target.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        if (length == 0) {
            return 0;
        }
        if (!ensureBuffered()) {
            return -1;
        }
        int take = Math.min(limit - position, length);
        System.arraycopy(buffer, position, target, offset, take);
        position += take;
        return take;
    }

    @Override
    public int available() {
        return limit - position;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        buffer = null;
        position = 0;
        limit = 0;
        source.close();
    }

    private boolean ensureBuffered() throws IOException {
        if (closed) {
            return false;
        }
        while (position >= limit) {
            if (nextChunk >= chunkCount) {
                return false;
            }
            loadChunk();
        }
        return true;
    }

    private void loadChunk() throws IOException {
        int index = nextChunk++;
        int plainLength = VaultFormat.chunkPlainLength(index, chunkCount, chunkSize, plaintextLength);

        byte[] iv = new byte[VaultFormat.IV_BYTES];
        readFully(iv, iv.length);
        byte[] body = new byte[plainLength + VaultFormat.TAG_BYTES];
        readFully(body, body.length);

        try {
            Cipher cipher = Cipher.getInstance(VaultCipher.TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(VaultFormat.TAG_BITS, iv));
            cipher.updateAAD(VaultFormat.aad(projectId, path, generation, index));
            // doFinal verifies the tag; nothing is released until it returns.
            buffer = cipher.doFinal(body);
        } catch (GeneralSecurityException error) {
            throw new IOException("Chunk " + index + " of '" + path + "' failed authentication",
                    error);
        }
        position = 0;
        limit = buffer.length;
    }

    private void readFully(byte[] target, int length) throws IOException {
        int read = 0;
        while (read < length) {
            int step = source.read(target, read, length - read);
            if (step < 0) {
                throw new IOException("Sealed resource '" + path + "' is truncated");
            }
            read += step;
        }
    }
}
