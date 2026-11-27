package com.roni.life.os.app.project;

import com.roni.library.security.crypto.DecryptedResourceCache;
import com.roni.library.security.crypto.ProjectVault;
import com.roni.library.security.crypto.VaultCipher;
import com.roni.library.security.crypto.VaultFormat;
import com.roni.library.security.crypto.VaultLimits;
import com.roni.library.ui.web.ResourceDecoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Decodes an encrypted internal project for the WebView.
 *
 * <p>Installed only on {@code WebHostConfig} for internal encrypted projects. SAF folders and
 * remote HTTPS sources never get one, so their bytes pass through untouched.</p>
 *
 * <p>Three size regimes, chosen from the plaintext length in the sealed header — no guessing and
 * no buffering to find out:</p>
 *
 * <table>
 *   <tr><th>Plaintext size</th><th>Path</th><th>Memory</th></tr>
 *   <tr><td>≤ {@code maxBufferedBytes}</td><td>decrypt fully, verify every tag, cache, serve</td>
 *       <td>one resource</td></tr>
 *   <tr><td>≤ {@code maxResourceBytes}</td><td>stream chunk by chunk, each tag verified before
 *       release, not cached</td><td>one chunk</td></tr>
 *   <tr><td>&gt; {@code maxResourceBytes}</td><td>refused — the request becomes a 404</td>
 *       <td>none</td></tr>
 * </table>
 *
 * <p>Plaintext lives in {@link DecryptedResourceCache} — RAM only, never written to disk.</p>
 */
public final class VaultResourceDecoder implements ResourceDecoder {

    private final String projectId;
    private final int generation;
    private final DecryptedResourceCache cache;

    public VaultResourceDecoder(String projectId, int generation, DecryptedResourceCache cache) {
        this.projectId = projectId;
        this.generation = generation;
        this.cache = cache;
    }

    @Override
    public byte[] decode(String path, byte[] raw) throws Exception {
        String key = VaultFormat.normalizePath(path);
        byte[] cached = cache.get(projectId, key);
        if (cached != null) {
            return cached;
        }
        byte[] plain = ProjectVault.open(projectId, key, generation, raw);
        cache.put(projectId, key, plain);
        return plain;
    }

    @Override
    public InputStream decodeStream(String path, InputStream raw) throws Exception {
        String key = VaultFormat.normalizePath(path);

        byte[] cached = cache.get(projectId, key);
        if (cached != null) {
            closeQuietly(raw);
            return new ByteArrayInputStream(cached);
        }

        VaultCipher.SealedStream sealed;
        try {
            sealed = ProjectVault.openStream(projectId, key, generation, raw);
        } catch (Throwable error) {
            closeQuietly(raw);
            throw error;
        }

        long length = sealed.plaintextLength();
        if (length > VaultLimits.getMaxResourceBytes()) {
            sealed.close();
            throw new IOException("Resource '" + key + "' is " + length
                    + " bytes, over the " + VaultLimits.getMaxResourceBytes() + " byte limit");
        }

        if (length <= VaultLimits.getMaxBufferedBytes()) {
            byte[] plain;
            try {
                plain = readExactly(sealed.stream(), (int) length);
            } finally {
                sealed.close();
            }
            cache.put(projectId, key, plain);
            return new ByteArrayInputStream(plain);
        }

        // Too large to buffer: hand back the chunk-verifying stream. Each 64 KB block is
        // authenticated before it is released, so no unauthenticated plaintext escapes.
        return sealed.stream();
    }

    /** Drops this project's cached plaintext. Call on project close or update. */
    public void invalidate() {
        cache.clearProject(projectId);
    }

    public String getProjectId() {
        return projectId;
    }

    public int getGeneration() {
        return generation;
    }

    private static byte[] readExactly(InputStream in, int length) throws IOException {
        byte[] out = new byte[length];
        int read = 0;
        while (read < length) {
            int step = in.read(out, read, length - read);
            if (step < 0) {
                throw new IOException("Sealed resource ended after " + read + " of " + length
                        + " bytes");
            }
            read += step;
        }
        return out;
    }

    private static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (Throwable ignored) {
            // Nothing useful to do while unwinding.
        }
    }
}
