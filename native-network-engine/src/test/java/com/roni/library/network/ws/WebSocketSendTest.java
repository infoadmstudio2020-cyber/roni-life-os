package com.roni.library.network.ws;

import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Send semantics: state gating and frame integrity under concurrency.
 */
public class WebSocketSendTest {

    @Test
    public void sendBeforeConnectReturnsFalse() {
        WebSocketClient client = new WebSocketClient("ws://127.0.0.1:1/socket", null);
        Assert.assertFalse(client.send("nope"));
        Assert.assertFalse(client.send(new byte[]{1}));
        Assert.assertFalse(client.sendPing());
        client.shutdown();
    }

    @Test
    public void sendAfterCloseReturnsFalse() throws Exception {
        WsTestHarness harness = WsTestHarness.open();
        harness.client.close(1000, "bye");
        Assert.assertFalse(harness.client.send("nope"));
        Assert.assertFalse(harness.client.send(new byte[]{1}));
        harness.close();
    }

    @Test
    public void sendOnOpenWritesAFrameTheServerReads() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            Assert.assertTrue(harness.client.send("payload-1"));
            TestWebSocketServer.Frame frame =
                    TestWebSocketServer.readFrame(harness.socket.getInputStream());
            Assert.assertEquals("payload-1", frame.text());
        }
    }

    @Test
    public void concurrentSendsDoNotInterleave() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            final int senders = 8;
            final CountDownLatch start = new CountDownLatch(1);
            final CountDownLatch done = new CountDownLatch(senders);
            final List<String> payloads = new ArrayList<>();
            for (int i = 0; i < senders; i++) {
                payloads.add("message-" + i + "-" + repeat('x', 200));
            }

            for (int i = 0; i < senders; i++) {
                final String payload = payloads.get(i);
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                        harness.client.send(payload);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
                thread.setDaemon(true);
                thread.start();
            }
            start.countDown();
            Assert.assertTrue(done.await(5, TimeUnit.SECONDS));

            InputStream in = harness.socket.getInputStream();
            Set<String> received = new HashSet<>();
            for (int i = 0; i < senders; i++) {
                TestWebSocketServer.Frame frame = TestWebSocketServer.readFrame(in);
                Assert.assertEquals(0x1, frame.opcode);
                received.add(frame.text());
            }
            Assert.assertEquals(new HashSet<>(payloads), received);
        }
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
