package com.roni.library.network.ws;

import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Framing: masking, extended lengths, fragmentation, control frames and close handling.
 */
public class WebSocketFrameTest {

    @Test
    public void clientFramesAreMaskedOnTheWire() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            Assert.assertTrue(harness.client.send("hello"));
            TestWebSocketServer.Frame frame =
                    TestWebSocketServer.readFrame(harness.socket.getInputStream());
            Assert.assertTrue("client frames must be masked", frame.masked);
            Assert.assertEquals(0x1, frame.opcode);
            Assert.assertEquals("hello", frame.text());
        }
    }

    @Test
    public void sixteenBitLengthRoundTrips() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 300; i++) {
                sb.append('a');
            }
            String payload = sb.toString();
            Assert.assertTrue(harness.client.send(payload));

            TestWebSocketServer.Frame frame =
                    TestWebSocketServer.readFrame(harness.socket.getInputStream());
            Assert.assertEquals(300, frame.payload.length);
            Assert.assertEquals(payload, frame.text());
        }
    }

    @Test
    public void sixtyFourBitLengthRoundTrips() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            byte[] payload = new byte[70_000];
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (i % 251);
            }
            Assert.assertTrue(harness.client.send(payload));

            TestWebSocketServer.Frame frame =
                    TestWebSocketServer.readFrame(harness.socket.getInputStream());
            Assert.assertEquals(0x2, frame.opcode);
            Assert.assertEquals(payload.length, frame.payload.length);
            Assert.assertArrayEquals(payload, frame.payload);
        }
    }

    @Test
    public void fragmentedTextMessageIsReassembled() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            OutputStream out = harness.socket.getOutputStream();
            TestWebSocketServer.writeFrame(out, 0x1, "frag-".getBytes(StandardCharsets.UTF_8), false);
            TestWebSocketServer.writeFrame(out, 0x0, "ment-".getBytes(StandardCharsets.UTF_8), false);
            TestWebSocketServer.writeFrame(out, 0x0, "ed".getBytes(StandardCharsets.UTF_8), true);

            long deadline = System.currentTimeMillis() + 3000;
            while (harness.listener.text.get() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            Assert.assertEquals("frag-ment-ed", harness.listener.text.get());
        }
    }

    @Test
    public void pingIsAnsweredWithPong() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            OutputStream out = harness.socket.getOutputStream();
            TestWebSocketServer.writeFrame(out, 0x9, "hb".getBytes(StandardCharsets.UTF_8), true);

            TestWebSocketServer.Frame pong =
                    TestWebSocketServer.readFrame(harness.socket.getInputStream());
            Assert.assertEquals(0xA, pong.opcode);
            Assert.assertEquals("hb", pong.text());
            Assert.assertTrue(harness.listener.ping.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void peerCloseTransitionsToClosedAndReportsCode() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            OutputStream out = harness.socket.getOutputStream();
            byte[] payload = new byte[]{0x03, (byte) 0xE8, 'b', 'y', 'e'};
            TestWebSocketServer.writeFrame(out, 0x8, payload, true);

            Assert.assertTrue(harness.listener.closed.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(Integer.valueOf(1000), harness.listener.closeCode.get());
            Assert.assertEquals(WebSocketState.CLOSED, harness.client.getState());
        }
    }

    @Test
    public void binaryMessageIsDelivered() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            byte[] payload = new byte[]{1, 2, 3, 4, 5};
            TestWebSocketServer.writeFrame(harness.socket.getOutputStream(), 0x2, payload, true);

            long deadline = System.currentTimeMillis() + 3000;
            while (harness.listener.binary.get() == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            Assert.assertArrayEquals(payload, harness.listener.binary.get());
        }
    }

    @Test
    public void reservedBitsAreRejected() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            OutputStream out = harness.socket.getOutputStream();
            // FIN + RSV1 + text, unmasked, empty payload.
            out.write(new byte[]{(byte) 0xC1, 0x00});
            out.flush();

            Assert.assertTrue(harness.listener.failure.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(WebSocketState.CLOSED, harness.client.getState());
        }
    }

    @Test
    public void oversizedControlFrameIsRejected() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            OutputStream out = harness.socket.getOutputStream();
            byte[] payload = new byte[200];
            TestWebSocketServer.writeFrame(out, 0x9, payload, true);

            Assert.assertTrue(harness.listener.failure.await(3, TimeUnit.SECONDS));
            Assert.assertEquals(WebSocketState.CLOSED, harness.client.getState());
        }
    }

    @Test
    public void closeFrameFromClientIsWritten() throws Exception {
        try (WsTestHarness harness = WsTestHarness.open()) {
            harness.client.close(1000, "done");
            TestWebSocketServer.Frame frame =
                    TestWebSocketServer.readFrame(harness.socket.getInputStream());
            Assert.assertEquals(0x8, frame.opcode);
            Assert.assertTrue(frame.payload.length >= 2);
            int code = ((frame.payload[0] & 0xFF) << 8) | (frame.payload[1] & 0xFF);
            Assert.assertEquals(1000, code);
        }
    }
}
