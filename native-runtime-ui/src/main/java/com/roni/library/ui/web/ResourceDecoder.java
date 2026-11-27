package com.roni.library.ui.web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Optional hook that turns stored bytes into servable bytes.
 *
 * <p>Only internal encrypted projects install one. SAF folders and remote HTTPS sources leave it
 * {@code null}, so their existing security policy is untouched — the interceptor serves their
 * bytes exactly as before.</p>
 */
public interface ResourceDecoder {

    /**
     * Buffered decode.
     *
     * @param path request path, already normalised by the provider
     * @param raw  bytes as stored
     * @return bytes to hand to the WebView
     * @throws Exception when the resource cannot be decoded; the request becomes a 404
     */
    byte[] decode(String path, byte[] raw) throws Exception;

    /**
     * Streaming decode, used by the interceptor so a large resource never has to be buffered.
     *
     * <p>The decoder takes ownership of {@code raw}: it must close it on every path, including
     * failure. The returned stream is owned by the caller (the WebView closes it).</p>
     *
     * <p>The default implementation buffers, which is correct but memory-hungry; implementations
     * that can stream should override it.</p>
     */
    default InputStream decodeStream(String path, InputStream raw) throws Exception {
        try {
            return new ByteArrayInputStream(decode(path, readAll(raw)));
        } finally {
            closeQuietly(raw);
        }
    }

    /** Reads a stream fully. Package-visible so implementations can reuse it. */
    static byte[] readAll(InputStream in) throws IOException {
        if (in == null) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    static void closeQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.close();
        } catch (Throwable ignored) {
            // The caller is already unwinding; a close failure changes nothing.
        }
    }
}
