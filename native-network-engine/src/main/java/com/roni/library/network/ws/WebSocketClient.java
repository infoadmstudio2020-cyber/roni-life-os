package com.roni.library.network.ws;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Minimal RFC 6455 WebSocket client.
 *
 * <p>The client opens a real TCP (or TLS) connection, performs the HTTP Upgrade handshake,
 * validates {@code Sec-WebSocket-Accept}, and then exchanges masked frames with the peer.
 * A dedicated daemon reader thread delivers inbound frames; writes are serialised on a
 * dedicated lock so concurrent senders cannot interleave frames.</p>
 *
 * <p>Supported: text (0x1), binary (0x2), close (0x8), ping (0x9), pong (0xA), 7/16/64-bit
 * payload lengths and fragmented messages. Extensions are not negotiated, so any frame with
 * RSV1..3 set is rejected.</p>
 *
 * @author infoadmstudio2020-cyber
 */
public class WebSocketClient {

    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int OPCODE_CONTINUATION = 0x0;
    private static final int OPCODE_TEXT = 0x1;
    private static final int OPCODE_BINARY = 0x2;
    private static final int OPCODE_CLOSE = 0x8;
    private static final int OPCODE_PING = 0x9;
    private static final int OPCODE_PONG = 0xA;

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final long MAX_FRAME_BYTES = 16L * 1024L * 1024L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String url;
    private final String host;
    private final int port;
    private final String path;
    private final boolean secure;
    private final WebSocketListener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ws-connect");
        t.setDaemon(true);
        return t;
    });

    private final Object writeLock = new Object();
    private final AtomicBoolean closedByClient = new AtomicBoolean(false);

    private volatile WebSocketState state = WebSocketState.CLOSED;
    private volatile Socket socket;
    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile Thread readerThread;
    private volatile CountDownLatch closeLatch = new CountDownLatch(1);
    private volatile int closeCode = 1006;
    private volatile String closeReason = "";

    public WebSocketClient(String url, WebSocketListener listener) {
        if (url == null) {
            throw new IllegalArgumentException("WebSocket url must not be null");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Malformed WebSocket url: " + url, e);
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.US) : "";
        if (!"ws".equals(scheme) && !"wss".equals(scheme)) {
            throw new IllegalArgumentException("Unsupported WebSocket scheme: " + scheme);
        }
        if (uri.getHost() == null || uri.getHost().isEmpty()) {
            throw new IllegalArgumentException("WebSocket url has no host: " + url);
        }
        this.url = url;
        this.secure = "wss".equals(scheme);
        this.host = uri.getHost();
        this.port = uri.getPort() > 0 ? uri.getPort() : (secure ? 443 : 80);
        String rawPath = uri.getRawPath();
        String rawQuery = uri.getRawQuery();
        StringBuilder p = new StringBuilder(rawPath == null || rawPath.isEmpty() ? "/" : rawPath);
        if (rawQuery != null && !rawQuery.isEmpty()) {
            p.append('?').append(rawQuery);
        }
        this.path = p.toString();
        this.listener = listener;
    }

    public String getUrl() {
        return url;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isSecure() {
        return secure;
    }

    public WebSocketState getState() {
        return state;
    }

    /**
     * Opens the socket and performs the upgrade handshake asynchronously.
     * A call while {@code CONNECTING} or {@code OPEN} is a no-op.
     */
    public synchronized void connect() {
        if (state == WebSocketState.OPEN || state == WebSocketState.CONNECTING) {
            return;
        }
        state = WebSocketState.CONNECTING;
        closedByClient.set(false);
        closeLatch = new CountDownLatch(1);
        closeCode = 1006;
        closeReason = "";
        executor.submit(() -> {
            try {
                doConnect();
            } catch (Throwable t) {
                fail(t);
            }
        });
    }

    private void doConnect() throws IOException {
        Socket raw = null;
        try {
            if (secure) {
                raw = SSLSocketFactory.getDefault().createSocket();
            } else {
                raw = new Socket();
            }
            raw.setTcpNoDelay(true);
            raw.connect(new InetSocketAddress(host, port), DEFAULT_CONNECT_TIMEOUT_MS);
            if (raw instanceof SSLSocket) {
                ((SSLSocket) raw).startHandshake();
            }

            InputStream in = raw.getInputStream();
            OutputStream out = raw.getOutputStream();

            byte[] keyBytes = new byte[16];
            RANDOM.nextBytes(keyBytes);
            String key = Base64.getEncoder().encodeToString(keyBytes);

            String hostHeader = (port == (secure ? 443 : 80)) ? host : host + ":" + port;
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + hostHeader + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "\r\n";
            out.write(request.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            String headerBlock = readHeaderBlock(in);
            String[] lines = headerBlock.split("\r\n");
            if (lines.length == 0) {
                throw new IOException("Empty handshake response");
            }
            String statusLine = lines[0];
            int status = parseStatusCode(statusLine);
            if (status != 101) {
                throw new IOException("Handshake rejected: " + statusLine.trim());
            }
            Map<String, String> headers = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                if (colon > 0) {
                    headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.US),
                            lines[i].substring(colon + 1).trim());
                }
            }
            String accept = headers.get("sec-websocket-accept");
            String expected = expectedAccept(key);
            if (accept == null || !accept.equals(expected)) {
                throw new IOException("Handshake rejected: invalid Sec-WebSocket-Accept");
            }

            this.socket = raw;
            this.input = in;
            this.output = out;
            this.state = WebSocketState.OPEN;

            Thread reader = new Thread(this::readLoop, "ws-reader-" + host);
            reader.setDaemon(true);
            this.readerThread = reader;
            reader.start();

            if (listener != null) {
                listener.onOpen(this);
            }
        } catch (Throwable t) {
            closeQuietly(raw);
            throw t instanceof IOException ? (IOException) t : new IOException(t);
        }
    }

    private static int parseStatusCode(String statusLine) throws IOException {
        String[] parts = statusLine.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IOException("Malformed status line: " + statusLine);
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IOException("Malformed status code: " + statusLine);
        }
    }

    private static String expectedAccept(String key) throws IOException {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key + WS_GUID).getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 unavailable", e);
        }
    }

    private static String readHeaderBlock(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("Connection closed during handshake");
            }
            buffer.write(b);
            if ((matched == 0 || matched == 2) && b == '\r') {
                matched++;
            } else if ((matched == 1 || matched == 3) && b == '\n') {
                matched++;
            } else {
                matched = b == '\r' ? 1 : 0;
            }
            if (buffer.size() > 64 * 1024) {
                throw new IOException("Handshake response headers too large");
            }
        }
        String block = buffer.toString(StandardCharsets.ISO_8859_1.name());
        return block.substring(0, block.length() - 4);
    }

    private void readLoop() {
        ByteArrayOutputStream fragments = new ByteArrayOutputStream();
        int fragmentOpcode = -1;
        try {
            while (state == WebSocketState.OPEN || state == WebSocketState.CLOSING) {
                Frame frame = readFrame(input);
                switch (frame.opcode) {
                    case OPCODE_TEXT:
                    case OPCODE_BINARY:
                        if (!frame.fin) {
                            fragmentOpcode = frame.opcode;
                            fragments.reset();
                            fragments.write(frame.payload);
                        } else {
                            deliver(frame.opcode, frame.payload);
                        }
                        break;
                    case OPCODE_CONTINUATION:
                        if (fragmentOpcode < 0) {
                            throw new IOException("Unexpected continuation frame");
                        }
                        fragments.write(frame.payload);
                        if (frame.fin) {
                            deliver(fragmentOpcode, fragments.toByteArray());
                            fragmentOpcode = -1;
                            fragments.reset();
                        }
                        break;
                    case OPCODE_PING:
                        try {
                            writeFrame(OPCODE_PONG, frame.payload);
                        } catch (IOException ignored) {
                            // Peer already gone; the read loop will surface it.
                        }
                        if (listener != null) {
                            listener.onPing(this, frame.payload);
                        }
                        break;
                    case OPCODE_PONG:
                        if (listener != null) {
                            listener.onPong(this, frame.payload);
                        }
                        break;
                    case OPCODE_CLOSE:
                        handlePeerClose(frame.payload);
                        return;
                    default:
                        throw new IOException("Unsupported opcode: " + frame.opcode);
                }
            }
        } catch (Throwable t) {
            if (state != WebSocketState.CLOSED) {
                fail(t);
            }
        }
    }

    private void deliver(int opcode, byte[] payload) {
        if (listener == null) {
            return;
        }
        if (opcode == OPCODE_TEXT) {
            listener.onMessage(this, new String(payload, StandardCharsets.UTF_8));
        } else {
            listener.onMessage(this, payload);
        }
    }

    private void handlePeerClose(byte[] payload) {
        int code = 1005;
        String reason = "";
        if (payload.length >= 2) {
            code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
            if (payload.length > 2) {
                reason = new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
            }
        }
        try {
            writeFrame(OPCODE_CLOSE, payload.length >= 2 ? payload : closePayload(code, reason));
        } catch (IOException ignored) {
            // Best effort echo.
        }
        closeCode = code;
        closeReason = reason;
        state = WebSocketState.CLOSED;
        closeLatch.countDown();
        closeQuietly(socket);
        if (listener != null) {
            listener.onClosed(this, code, reason);
        }
    }

    private void fail(Throwable t) {
        state = WebSocketState.CLOSED;
        closeLatch.countDown();
        closeQuietly(socket);
        if (listener != null) {
            listener.onFailure(this, t);
        }
    }

    /**
     * Sends a text frame. Returns {@code false} when the socket is not open or the write failed.
     */
    public boolean send(String text) {
        if (text == null || state != WebSocketState.OPEN) {
            return false;
        }
        try {
            writeFrame(OPCODE_TEXT, text.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            fail(e);
            return false;
        }
    }

    /**
     * Sends a binary frame. Returns {@code false} when the socket is not open or the write failed.
     */
    public boolean send(byte[] bytes) {
        if (bytes == null || state != WebSocketState.OPEN) {
            return false;
        }
        try {
            writeFrame(OPCODE_BINARY, bytes);
            return true;
        } catch (IOException e) {
            fail(e);
            return false;
        }
    }

    /**
     * Sends a ping frame with an empty payload.
     */
    public boolean sendPing() {
        if (state != WebSocketState.OPEN) {
            return false;
        }
        try {
            writeFrame(OPCODE_PING, new byte[0]);
            return true;
        } catch (IOException e) {
            fail(e);
            return false;
        }
    }

    /**
     * Sends a close frame, waits up to two seconds for the peer's close, then closes the socket.
     * Idempotent.
     */
    public void close(int code, String reason) {
        if (state == WebSocketState.CLOSED || state == WebSocketState.CLOSING) {
            return;
        }
        state = WebSocketState.CLOSING;
        closedByClient.set(true);
        if (listener != null) {
            listener.onClosing(this, code, reason);
        }
        try {
            writeFrame(OPCODE_CLOSE, closePayload(code, reason));
        } catch (IOException ignored) {
            // Socket may already be gone.
        }
        if (Thread.currentThread() != readerThread) {
            try {
                closeLatch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeQuietly(socket);
        state = WebSocketState.CLOSED;
        closeLatch.countDown();
        if (listener != null) {
            listener.onClosed(this, code, reason);
        }
    }

    private static byte[] closePayload(int code, String reason) {
        byte[] reasonBytes = reason != null ? reason.getBytes(StandardCharsets.UTF_8) : new byte[0];
        if (reasonBytes.length > 123) {
            byte[] trimmed = new byte[123];
            System.arraycopy(reasonBytes, 0, trimmed, 0, 123);
            reasonBytes = trimmed;
        }
        byte[] payload = new byte[2 + reasonBytes.length];
        payload[0] = (byte) ((code >> 8) & 0xFF);
        payload[1] = (byte) (code & 0xFF);
        System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
        return payload;
    }

    private void writeFrame(int opcode, byte[] payload) throws IOException {
        OutputStream out = this.output;
        if (out == null) {
            throw new IOException("WebSocket is not connected");
        }
        byte[] frame = encodeFrame(opcode, payload);
        synchronized (writeLock) {
            out.write(frame);
            out.flush();
        }
    }

    private static byte[] encodeFrame(int opcode, byte[] payload) {
        int length = payload.length;
        ByteArrayOutputStream frame = new ByteArrayOutputStream(length + 14);
        frame.write(0x80 | (opcode & 0x0F));
        if (length <= 125) {
            frame.write(0x80 | length);
        } else if (length <= 0xFFFF) {
            frame.write(0x80 | 126);
            frame.write((length >>> 8) & 0xFF);
            frame.write(length & 0xFF);
        } else {
            frame.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) {
                frame.write((int) (((long) length) >>> (8 * i)) & 0xFF);
            }
        }
        byte[] mask = new byte[4];
        RANDOM.nextBytes(mask);
        frame.write(mask, 0, 4);
        byte[] masked = new byte[length];
        for (int i = 0; i < length; i++) {
            masked[i] = (byte) (payload[i] ^ mask[i & 3]);
        }
        frame.write(masked, 0, length);
        return frame.toByteArray();
    }

    private static Frame readFrame(InputStream in) throws IOException {
        int b0 = readByte(in);
        int b1 = readByte(in);
        boolean fin = (b0 & 0x80) != 0;
        int rsv = b0 & 0x70;
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long length = b1 & 0x7F;

        if (rsv != 0) {
            throw new IOException("Protocol error: RSV bits set without a negotiated extension");
        }
        if (length == 126) {
            length = ((long) readByte(in) << 8) | readByte(in);
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte(in);
            }
        }
        if (opcode >= 0x8) {
            if (length > 125) {
                throw new IOException("Protocol error: control frame payload exceeds 125 bytes");
            }
            if (!fin) {
                throw new IOException("Protocol error: fragmented control frame");
            }
        }
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw new IOException("Protocol error: frame too large (" + length + " bytes)");
        }

        byte[] mask = null;
        if (masked) {
            mask = new byte[4];
            readFully(in, mask);
        }
        byte[] payload = new byte[(int) length];
        readFully(in, payload);
        if (mask != null) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i & 3]);
            }
        }
        return new Frame(fin, opcode, payload);
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException("WebSocket stream closed");
        }
        return b;
    }

    private static void readFully(InputStream in, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int read = in.read(target, offset, target.length - offset);
            if (read < 0) {
                throw new EOFException("WebSocket stream closed");
            }
            offset += read;
        }
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Nothing to do.
            }
        }
    }

    /**
     * @deprecated Test-only hook. Injects a text message into the listener path without a socket.
     */
    @Deprecated
    public void simulateIncomingMessage(String text) {
        if (state == WebSocketState.OPEN && listener != null) {
            executor.submit(() -> listener.onMessage(this, text));
        }
    }

    /**
     * @deprecated Test-only hook. Injects a binary message into the listener path without a socket.
     */
    @Deprecated
    public void simulateIncomingBinary(byte[] bytes) {
        if (state == WebSocketState.OPEN && listener != null) {
            executor.submit(() -> listener.onMessage(this, bytes));
        }
    }

    /**
     * @deprecated Test-only hook. Forces a failure without a socket.
     */
    @Deprecated
    public void simulateFailure(Throwable error) {
        state = WebSocketState.CLOSED;
        if (listener != null) {
            executor.submit(() -> listener.onFailure(this, error));
        }
    }

    public void shutdown() {
        close(1000, "Client shutdown");
        executor.shutdown();
    }

    private static final class Frame {
        final boolean fin;
        final int opcode;
        final byte[] payload;

        Frame(boolean fin, int opcode, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload;
        }
    }
}
