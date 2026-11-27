package com.roni.library.network.ws;

import org.junit.Assert;
import org.junit.Test;

import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lifecycle: idempotent connect/close, reconnect after close, state transitions.
 */
public class WebSocketLifecycleTest {

    private static final class CountingListener implements WebSocketListener {
        final AtomicInteger opens = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger failures = new AtomicInteger();
        volatile CountDownLatch openLatch = new CountDownLatch(1);

        @Override
        public void onOpen(WebSocketClient ws) {
            opens.incrementAndGet();
            openLatch.countDown();
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
            closes.incrementAndGet();
        }

        @Override
        public void onFailure(WebSocketClient ws, Throwable t) {
            failures.incrementAndGet();
        }
    }

    @Test
    public void connectTwiceIsANoOp() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            harness.client.connect();
            harness.client.connect();
            Thread.sleep(150);
            Assert.assertEquals(WebSocketState.OPEN, harness.client.getState());
            Assert.assertEquals(1, harness.listener.open.getCount() == 0 ? 1 : 0);
            Assert.assertTrue(harness.client.send("still-open"));
            TestWebSocketServer.Frame frame =
                    TestWebSocketServer.readFrame(harness.socket.getInputStream());
            Assert.assertEquals("still-open", frame.text());
        }
    }

    @Test
    public void closeTwiceIsSafe() throws Exception {
        WsTestHarness harness = WsTestHarness.open();
        harness.client.close(1000, "first");
        harness.client.close(1000, "second");
        Assert.assertEquals(WebSocketState.CLOSED, harness.client.getState());
        harness.close();
    }

    @Test
    public void closeBeforeConnectIsSafe() {
        WebSocketClient client = new WebSocketClient("ws://127.0.0.1:1/socket", null);
        client.close(1000, "never-opened");
        Assert.assertEquals(WebSocketState.CLOSED, client.getState());
        client.shutdown();
    }

    @Test
    public void connectAfterCloseOpensAFreshSocket() throws Exception {
        TestWebSocketServer server = new TestWebSocketServer();
        CountingListener listener = new CountingListener();
        WebSocketClient client = new WebSocketClient(server.getUrl(), listener);

        AtomicReference<Socket> firstSocket = new AtomicReference<>();
        AtomicReference<Socket> secondSocket = new AtomicReference<>();
        CountDownLatch accepted = new CountDownLatch(2);
        Thread serverThread = new Thread(() -> {
            try {
                Socket one = server.accept();
                server.handshake(one);
                firstSocket.set(one);
                accepted.countDown();

                Socket two = server.accept();
                server.handshake(two);
                secondSocket.set(two);
                accepted.countDown();
            } catch (Throwable ignored) {
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        client.connect();
        Assert.assertTrue(listener.openLatch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(WebSocketState.OPEN, client.getState());

        client.close(1000, "restart");
        Assert.assertEquals(WebSocketState.CLOSED, client.getState());

        listener.openLatch = new CountDownLatch(1);
        client.connect();
        Assert.assertTrue(listener.openLatch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(WebSocketState.OPEN, client.getState());
        Assert.assertEquals(2, listener.opens.get());
        Assert.assertTrue(accepted.await(5, TimeUnit.SECONDS));
        Assert.assertNotNull(firstSocket.get());
        Assert.assertNotNull(secondSocket.get());
        Assert.assertNotSame(firstSocket.get(), secondSocket.get());

        client.shutdown();
        server.close();
    }

    @Test
    public void stateStartsClosed() {
        WebSocketClient client = new WebSocketClient("ws://127.0.0.1:1/socket", null);
        Assert.assertEquals(WebSocketState.CLOSED, client.getState());
        client.shutdown();
    }
}
