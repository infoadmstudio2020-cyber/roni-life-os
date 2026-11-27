package com.roni.library.runtime;

import com.roni.library.contracts.common.RuntimeState;
import com.roni.library.router.NativeRouter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Lifecycle: init is idempotent, release keeps the singleton, the /runtime route is registered.
 */
public class RuntimeEngineLifecycleTest {

    @Before
    @After
    public void tearDown() {
        NativeRuntimeEngine.getInstance().release();
    }

    @Test
    public void initMakesTheEngineReady() {
        NativeRuntimeEngine engine = NativeRuntimeEngine.getInstance();
        Assert.assertFalse(engine.isInitialized());

        NativeRuntimeEngineResult<Boolean> result =
                engine.init(null, NativeRuntimeEngineConfig.defaultConfiguration());

        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(engine.isInitialized());
        Assert.assertEquals(RuntimeState.READY, engine.getState());
        Assert.assertNotNull(engine.getService());
        Assert.assertNotNull(engine.getCommandHandler());
    }

    @Test
    public void initTwiceIsSafe() {
        NativeRuntimeEngine engine = NativeRuntimeEngine.getInstance();
        engine.init(null, NativeRuntimeEngineConfig.defaultConfiguration());
        NativeRuntimeEngineResult<Boolean> second =
                engine.init(null, NativeRuntimeEngineConfig.defaultConfiguration());

        Assert.assertTrue(second.isSuccess());
        Assert.assertTrue(engine.isInitialized());
    }

    @Test
    public void releaseResetsStateButKeepsTheSingleton() {
        NativeRuntimeEngine engine = NativeRuntimeEngine.getInstance();
        engine.init(null, NativeRuntimeEngineConfig.defaultConfiguration());
        engine.release();

        Assert.assertFalse(engine.isInitialized());
        Assert.assertEquals(RuntimeState.UNINITIALIZED, engine.getState());
        Assert.assertNull(engine.getService());
        Assert.assertSame(engine, NativeRuntimeEngine.getInstance());
    }

    @Test
    public void initAfterReleaseWorks() {
        NativeRuntimeEngine engine = NativeRuntimeEngine.getInstance();
        engine.init(null, NativeRuntimeEngineConfig.defaultConfiguration());
        engine.release();
        NativeRuntimeEngineResult<Boolean> result =
                engine.init(null, NativeRuntimeEngineConfig.defaultConfiguration());

        Assert.assertTrue(result.isSuccess());
        Assert.assertTrue(engine.isInitialized());
    }

    @Test
    public void runtimeRouteIsRegisteredAndUnregistered() {
        NativeRuntimeEngine engine = NativeRuntimeEngine.getInstance();
        engine.init(null, NativeRuntimeEngineConfig.defaultConfiguration());
        Assert.assertTrue(NativeRouter.getInstance().getRoutes().containsKey("/runtime"));

        engine.release();
        Assert.assertFalse(NativeRouter.getInstance().getRoutes().containsKey("/runtime"));
    }

    @Test
    public void accessorsThrowBeforeInit() {
        NativeRuntimeEngine engine = NativeRuntimeEngine.getInstance();
        try {
            engine.getConfigurator();
            Assert.fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("not initialized"));
        }
    }
}
