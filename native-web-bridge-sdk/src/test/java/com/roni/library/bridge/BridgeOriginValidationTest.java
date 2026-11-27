package com.roni.library.bridge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * B1 — origin validation must gate every inbound message.
 */
public class BridgeOriginValidationTest {

    private WebBridge bridge;
    private final AtomicReference<String> script = new AtomicReference<>();

    @Before
    public void setUp() {
        WebBridge.getInstance().release();
        bridge = WebBridge.init(BridgeConfig.builder().build());
        bridge.setWebEvaluator(script::set);
    }

    @After
    public void tearDown() {
        bridge.release();
    }

    private static String ping(String id) {
        return "{\"id\":\"" + id + "\",\"type\":\"PING\",\"action\":\"ping\",\"payload\":{}}";
    }

    @Test
    public void allowedOriginDispatches() {
        bridge.setOriginProvider(() -> BridgeConfig.DEFAULT_ALLOWED_ORIGIN);
        bridge.handleWebMessage(ping("p1"));
        assertNotNull(script.get());
        assertTrue(script.get().contains("pong"));
        assertFalse(script.get().contains("ORIGIN_REJECTED"));
    }

    @Test
    public void disallowedOriginIsRejected() {
        bridge.setOriginProvider(() -> "https://evil.example");
        bridge.handleWebMessage(ping("p2"));
        assertNotNull(script.get());
        assertTrue(script.get().contains("ORIGIN_REJECTED"));
        assertFalse(script.get().contains("pong"));
    }

    @Test
    public void nullOriginIsRejected() {
        bridge.setOriginProvider(() -> null);
        bridge.handleWebMessage(ping("p3"));
        assertTrue(script.get().contains("ORIGIN_REJECTED"));
    }

    @Test
    public void missingProviderIsRejected() {
        bridge.clearOriginProvider();
        bridge.handleWebMessage(ping("p4"));
        assertTrue(script.get().contains("ORIGIN_REJECTED"));
    }

    @Test
    public void rejectedRequestNeverReachesRouter() {
        bridge.setOriginProvider(() -> "https://evil.example");
        bridge.handleWebMessage(
                "{\"id\":\"r1\",\"type\":\"REQUEST\",\"action\":\"device.readClipboard\",\"payload\":{}}");
        assertTrue(script.get().contains("ORIGIN_REJECTED"));
        assertFalse(script.get().contains("ROUTE_NOT_FOUND"));
        assertEquals(0, bridge.getPendingRequestCount());
    }

    @Test
    public void validationDisabledDispatches() {
        bridge.release();
        WebBridge permissive = WebBridge.init(
                BridgeConfig.builder().requireOriginValidation(false).build());
        AtomicReference<String> out = new AtomicReference<>();
        permissive.setWebEvaluator(out::set);
        permissive.handleWebMessage(ping("p5"));
        assertNotNull(out.get());
        assertTrue(out.get().contains("pong"));
        permissive.release();
    }

    @Test
    public void virtualOriginAllowedByDefault() {
        BridgeConfig config = BridgeConfig.defaultConfig();
        assertTrue(config.isOriginAllowed(BridgeConfig.DEFAULT_ALLOWED_ORIGIN));
        assertFalse(config.isOriginAllowed("https://evil.example"));
        assertFalse(config.isOriginAllowed(null));
        assertTrue(config.isOriginValidationRequired());
    }
}
