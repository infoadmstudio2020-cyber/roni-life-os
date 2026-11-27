package com.roni.library.bridge;

import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.router.NativeRouter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * B3 — pending requests must expire and emit REQUEST_TIMEOUT.
 */
public class BridgeTimeoutTest {

    private WebBridge bridge;
    private final AtomicReference<String> script = new AtomicReference<>();
    private final CountDownLatch releaseHandler = new CountDownLatch(1);

    @Before
    public void setUp() {
        NativeRouter.getInstance().initialize();
        WebBridge.getInstance().release();
        bridge = WebBridge.init(BridgeConfig.builder()
                .timeoutMs(200L)
                .requireOriginValidation(false)
                .build());
        bridge.setWebEvaluator(script::set);

        NativeRouter.getInstance().registerRoute("/slow", new IApiHandler() {
            @Override
            public String getPath() {
                return "/slow";
            }

            @Override
            public String getAction() {
                return "*";
            }

            @Override
            public NativeApiResponse handleRequest(NativeApiRequest request) {
                try {
                    releaseHandler.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return NativeApiResponse.success(request.getRequestId(), null);
            }
        });
    }

    @After
    public void tearDown() {
        releaseHandler.countDown();
        try {
            NativeRouter.getInstance().unregisterRoute("/slow");
        } catch (Throwable ignored) {
        }
        bridge.release();
    }

    @Test
    public void unansweredRequestEmitsTimeout() throws Exception {
        bridge.handleWebMessage(
                "{\"id\":\"t1\",\"type\":\"REQUEST\",\"action\":\"slow.run\",\"payload\":{}}");
        assertEquals(1, bridge.getPendingRequestCount());

        awaitScript("REQUEST_TIMEOUT");
        assertTrue("expected REQUEST_TIMEOUT, got: " + script.get(),
                script.get().contains("REQUEST_TIMEOUT"));
        assertEquals(0, bridge.getPendingRequestCount());
    }

    @Test
    public void lateSuccessAfterTimeoutIsNoOp() throws Exception {
        bridge.handleWebMessage(
                "{\"id\":\"t2\",\"type\":\"REQUEST\",\"action\":\"slow.run\",\"payload\":{}}");
        awaitScript("REQUEST_TIMEOUT");
        assertTrue(script.get().contains("REQUEST_TIMEOUT"));

        script.set(null);
        bridge.onSuccess(NativeApiResponse.success("t2", null));
        assertNull("late response must not be emitted", script.get());
    }

    private void awaitScript(String needle) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            String current = script.get();
            if (current != null && current.contains(needle)) {
                return;
            }
            Thread.sleep(25);
        }
    }
}
