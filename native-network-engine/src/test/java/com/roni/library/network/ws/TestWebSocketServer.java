package com.roni.library.network.ws;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal RFC 6455 server used by the WebSocket unit tests. No external dependency.
 */
final class TestWebSocketServer implements Closeable {

    static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ServerSocket serverSocket;
    private volatile int responseStatus = 101;
    private volatile boolean corruptAccept = false;
    private volatile String lastRequest = "";

    TestWebSocketServer() throws IOException {
        serverSocket = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    String getUrl() {
        return "ws://127.0.0.1:" + getPort() + "/socket";
    }

    String getLastRequest() {
        return lastRequest;
    }

    void setResponseStatus(int status) {
        this.responseStatus = status;
    }

    void setCorruptAccept(boolean corrupt) {
        this.corruptAccept = corrupt;
    }

    Socket accept() throws IOException {
        Socket socket = serverSocket.accept();
        socket.setSoTimeout(5000);
        return socket;
    }

    /**
     * Reads the upgrade request and writes the handshake response.
     */
    Map<String, String> handshake(Socket socket) throws Exception {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        String request = readHeaderBlock(in);
        lastRequest = request;
        Map<String, String> headers = parseHeaders(request);
        String key = headers.get("sec-websocket-key");
        String accept = corruptAccept ? "invalid-accept-value" : expectedAccept(key);

        StringBuilder response = new StringBuilder();
        if (responseStatus == 101) {
            response.append("HTTP/1.1 101 Switching Protocols\r\n")
                    .append("Upgrade: websocket\r\n")
                    .append("Connection: Upgrade\r\n")
                    .append("Sec-WebSocket-Accept: ").append(accept).append("\r\n")
                    .append("\r\n");
        } else {
            response.append("HTTP/1.1 ").append(responseStatus).append(" Rejected\r\n")
                    .append("Content-Length: 0\r\n")
                    .append("\r\n");
        }
        out.write(response.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        return headers;
    }

    static String expectedAccept(String key) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest((key + GUID).getBytes(StandardCharsets.ISO_8859_1));
        return Base64.getEncoder().encodeToString(digest);
    }

    static String readHeaderBlock(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("closed during handshake");
            }
            buffer.write(b);
            if ((matched == 0 || matched == 2) && b == '\r') {
                matched++;
            } else if ((matched == 1 || matched == 3) && b == '\n') {
                matched++;
            } else {
                matched = b == '\r' ? 1 : 0;
            }
        }
        String block = buffer.toString(StandardCharsets.ISO_8859_1.name());
        return block.substring(0, block.length() - 4);
    }

    static Map<String, String> parseHeaders(String block) {
        Map<String, String> headers = new LinkedHashMap<>();
        String[] lines = block.split("\r\n");
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.US),
                        lines[i].substring(colon + 1).trim());
            }
        }
        return headers;
    }

    static final class Frame {
        final boolean fin;
        final int opcode;
        final boolean masked;
        final byte[] payload;

        Frame(boolean fin, int opcode, boolean masked, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.masked = masked;
            this.payload = payload;
        }

        String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    static Frame readFrame(InputStream in) throws IOException {
        int b0 = readByte(in);
        int b1 = readByte(in);
        boolean fin = (b0 & 0x80) != 0;
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long length = b1 & 0x7F;
        if (length == 126) {
            length = ((long) readByte(in) << 8) | readByte(in);
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte(in);
            }
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
        return new Frame(fin, opcode, masked, payload);
    }

    static void writeFrame(OutputStream out, int opcode, byte[] payload, boolean fin)
            throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write((fin ? 0x80 : 0x00) | (opcode & 0x0F));
        int length = payload.length;
        if (length <= 125) {
            frame.write(length);
        } else if (length <= 0xFFFF) {
            frame.write(126);
            frame.write((length >>> 8) & 0xFF);
            frame.write(length & 0xFF);
        } else {
            frame.write(127);
            for (int i = 7; i >= 0; i--) {
                frame.write((int) (((long) length) >>> (8 * i)) & 0xFF);
            }
        }
        frame.write(payload, 0, length);
        out.write(frame.toByteArray());
        out.flush();
    }

    static void writeText(OutputStream out, String text) throws IOException {
        writeFrame(out, 0x1, text.getBytes(StandardCharsets.UTF_8), true);
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException("stream closed");
        }
        return b;
    }

    private static void readFully(InputStream in, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int read = in.read(target, offset, target.length - offset);
            if (read < 0) {
                throw new EOFException("stream closed");
            }
            offset += read;
        }
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
    }
}
