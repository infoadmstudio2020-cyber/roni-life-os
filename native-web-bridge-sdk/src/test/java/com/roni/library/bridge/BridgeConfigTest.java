package com.roni.library.bridge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * B2 / B7 — interface-name default, singleton semantics, reconfigure and payload limits.
 */
public class BridgeConfigTest {

    private WebBridge bridge;

    @Before
    public void setUp() {
        WebBridge.getInstance().release();
        bridge = WebBridge.init(BridgeConfig.builder().build());
    }

    @After
    public void tearDown() {
        bridge.release();
    }

    @Test
    public void defaultInterfaceNameIsNativeRuntimeBridge() {
        assertEquals("NativeRuntimeBridge", BridgeConfig.defaultConfig().getInterfaceName());
        assertEquals("NativeRuntimeBridge", bridge.getConfig().getInterfaceName());
    }

    @Test
    public void initTwiceReturnsSameInstance() {
        WebBridge second = WebBridge.init(
                BridgeConfig.builder().interfaceName("OtherBridge").build());
        assertSame(bridge, second);
        assertEquals("NativeRuntimeBridge", second.getConfig().getInterfaceName());
    }

    @Test
    public void reconfigureDoesNotChangeInterfaceName() {
        bridge.reconfigure(BridgeConfig.builder()
                .interfaceName("Hacked")
                .timeoutMs(1234L)
                .debugLogging(true)
                .build());

        assertEquals("NativeRuntimeBridge", bridge.getConfig().getInterfaceName());
        assertEquals(1234L, bridge.getConfig().getTimeoutMs());
        assertTrue(bridge.getConfig().isDebugLogging());
    }

    @Test
    public void oversizedPayloadIsRejected() {
        bridge.release();
        WebBridge limited = WebBridge.init(BridgeConfig.builder()
                .maxMessageBytes(64)
                .requireOriginValidation(false)
                .build());
        AtomicReference<String> out = new AtomicReference<>();
        limited.setWebEvaluator(out::set);

        StringBuilder big = new StringBuilder(
                "{\"id\":\"x\",\"type\":\"PING\",\"action\":\"ping\",\"payload\":{\"d\":\"");
        for (int i = 0; i < 200; i++) {
            big.append('a');
        }
        big.append("\"}}");

        limited.handleWebMessage(big.toString());
        assertNotNull(out.get());
        assertTrue(out.get().contains("PAYLOAD_TOO_LARGE"));
        limited.release();
    }

    @Test
    public void defaultMaxMessageBytesIsOneMebibyte() {
        assertEquals(1_048_576, BridgeConfig.defaultConfig().getMaxMessageBytes());
    }
}
