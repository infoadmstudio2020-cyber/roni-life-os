package com.roni.library.bridge;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * B5 — release must reset state without nulling the singleton under a concurrent reader.
 */
public class BridgeLifecycleTest {

    private WebBridge bridge;

    @Before
    public void setUp() {
        WebBridge.getInstance().release();
        bridge = WebBridge.init(BridgeConfig.builder().build());
    }

    @After
    public void tearDown() {
        WebBridge.getInstance().release();
    }

    @Test
    public void releaseClearsState() {
        assertTrue(bridge.isInitialized());
        bridge.release();
        assertFalse(bridge.isInitialized());
        assertEquals(0, bridge.getPendingRequestCount());
    }

    @Test
    public void getInstanceAfterReleaseDoesNotThrow() {
        bridge.release();
        WebBridge fresh = WebBridge.getInstance();
        assertNotNull(fresh);
        assertTrue(fresh.isInitialized());
        assertNotSame(bridge, fresh);
    }

    @Test
    public void releaseIsIdempotent() {
        bridge.release();
        bridge.release();
        assertFalse(bridge.isInitialized());
    }

    @Test
    public void sdkReleaseOwnsSingletonLifecycle() {
        NativeWebBridgeSDK sdk = NativeWebBridgeSDK.getInstance();
        sdk.init(null, BridgeConfig.builder().build());
        assertTrue(sdk.isInitialized());
        sdk.release();
        assertFalse(sdk.isInitialized());
        assertNull(sdk.getBridge());
    }
}
