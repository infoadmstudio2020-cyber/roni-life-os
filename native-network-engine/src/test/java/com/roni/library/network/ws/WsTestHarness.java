package com.roni.library.network.ws;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opens a client against a loopback {@link TestWebSocketServer} and exposes both ends.
 */
final class WsTestHarness implements Closeable {

    static final class RecordingListener implements WebSocketListener {
        final CountDownLatch open = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final CountDownLatch failure = new CountDownLatch(1);
        final AtomicReference<String> text = new AtomicReference<>();
        final AtomicReference<byte[]> binary = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicReference<Integer> closeCode = new AtomicReference<>();
        final CountDownLatch ping = new CountDownLatch(1);

        @Override
        public void onOpen(WebSocketClient ws) {
            open.countDown();
        }

        @Override
        public void onMessage(WebSocketClient ws, String value) {
            text.set(value);
        }

        @Override
        public void onMessage(WebSocketClient ws, byte[] bytes) {
            binary.set(bytes);
        }

        @Override
        public void onClosing(WebSocketClient ws, int code, String reason) {
        }

        @Override
        public void onClosed(WebSocketClient ws, int code, String reason) {
            closeCode.set(code);
            closed.countDown();
        }

        @Override
        public void onFailure(WebSocketClient ws, Throwable t) {
            error.set(t);
            failure.countDown();
        }

        @Override
        public void onPing(WebSocketClient ws, byte[] payload) {
            ping.countDown();
        }
    }

    final TestWebSocketServer server;
    final RecordingListener listener;
    final WebSocketClient client;
    final Socket socket;

    private WsTestHarness(TestWebSocketServer server, RecordingListener listener,
                          WebSocketClient client, Socket socket) {
        this.server = server;
        this.listener = listener;
        this.client = client;
        this.socket = socket;
    }

    static WsTestHarness open() throws Exception {
        TestWebSocketServer server = new TestWebSocketServer();
        RecordingListener listener = new RecordingListener();
        WebSocketClient client = new WebSocketClient(server.getUrl(), listener);

        AtomicReference<Socket> socketRef = new AtomicReference<>();
        AtomicReference<Throwable> serverError = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        Thread serverThread = new Thread(() -> {
            try {
                Socket socket = server.accept();
                server.handshake(socket);
                socketRef.set(socket);
            } catch (Throwable t) {
                serverError.set(t);
            } finally {
                ready.countDown();
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        client.connect();
        if (!listener.open.await(5, TimeUnit.SECONDS)) {
            server.close();
            throw new AssertionError("client did not open: "
                    + (serverError.get() != null ? serverError.get() : "timeout"));
        }
        if (!ready.await(5, TimeUnit.SECONDS) || socketRef.get() == null) {
            server.close();
            throw new AssertionError("server did not complete the handshake");
        }
        return new WsTestHarness(server, listener, client, socketRef.get());
    }

    @Override
    public void close() throws IOException {
        try {
            client.shutdown();
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            server.close();
        }
    }
}
