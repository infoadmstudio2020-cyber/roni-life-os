package com.roni.library.network.ws;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handshake behaviour: upgrade request shape, accept validation and rejection paths.
 */
public class WebSocketHandshakeTest {

    private TestWebSocketServer server;
    private WebSocketClient client;

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            client.shutdown();
        }
        if (server != null) {
            server.close();
        }
    }

    private static final class RecordingListener implements WebSocketListener {
        final CountDownLatch open = new CountDownLatch(1);
        final CountDownLatch failure = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onOpen(WebSocketClient ws) {
            open.countDown();
        }

        @Override
        public void onMessage(WebSocketClient ws, String text) {
        }

        @Override
        public void onMessage(WebSocketClient ws, byte[] bytes) {
        }

        @Override
        public void onClosing(WebSocketClient ws, int code, String reason) {
        }

        @Override
        public void onClosed(WebSocketClient ws, int code, String reason) {
        }

        @Override
        public void onFailure(WebSocketClient ws, Throwable t) {
            error.set(t);
            failure.countDown();
        }
    }

    @Test
    public void successfulHandshakeOpensTheSocket() throws Exception {
        server = new TestWebSocketServer();
        RecordingListener listener = new RecordingListener();
        client = new WebSocketClient(server.getUrl(), listener);

        Thread serverThread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                server.handshake(socket);
                Thread.sleep(500);
            } catch (Throwable ignored) {
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        client.connect();
        Assert.assertTrue("client should reach OPEN", listener.open.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(WebSocketState.OPEN, client.getState());

        String request = server.getLastRequest();
        Assert.assertTrue(request.startsWith("GET /socket HTTP/1.1"));
        Map<String, String> headers = TestWebSocketServer.parseHeaders(request);
        Assert.assertEquals("websocket", headers.get("upgrade"));
        Assert.assertEquals("Upgrade", headers.get("connection"));
        Assert.assertEquals("13", headers.get("sec-websocket-version"));
        Assert.assertNotNull(headers.get("sec-websocket-key"));
        Assert.assertEquals(24, headers.get("sec-websocket-key").length());
    }

    @Test
    public void rejectedHandshakeFailsClosed() throws Exception {
        server = new TestWebSocketServer();
        server.setResponseStatus(403);
        RecordingListener listener = new RecordingListener();
        client = new WebSocketClient(server.getUrl(), listener);

        Thread serverThread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                server.handshake(socket);
            } catch (Throwable ignored) {
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        client.connect();
        Assert.assertTrue(listener.failure.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(WebSocketState.CLOSED, client.getState());
        Assert.assertTrue(listener.error.get().getMessage().contains("403"));
    }

    @Test
    public void wrongAcceptHeaderFailsClosed() throws Exception {
        server = new TestWebSocketServer();
        server.setCorruptAccept(true);
        RecordingListener listener = new RecordingListener();
        client = new WebSocketClient(server.getUrl(), listener);

        Thread serverThread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                server.handshake(socket);
                Thread.sleep(300);
            } catch (Throwable ignored) {
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        client.connect();
        Assert.assertTrue(listener.failure.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(WebSocketState.CLOSED, client.getState());
        Assert.assertTrue(listener.error.get().getMessage().contains("Sec-WebSocket-Accept"));
    }

    @Test
    public void unsupportedSchemeIsRejectedAtConstruction() {
        try {
            new WebSocketClient("http://example.com", null);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("scheme"));
        }
    }

    @Test
    public void defaultPortsFollowTheScheme() {
        WebSocketClient plain = new WebSocketClient("ws://example.com/path", null);
        Assert.assertEquals(80, plain.getPort());
        Assert.assertFalse(plain.isSecure());

        WebSocketClient secure = new WebSocketClient("wss://example.com/path", null);
        Assert.assertEquals(443, secure.getPort());
        Assert.assertTrue(secure.isSecure());
    }
}
