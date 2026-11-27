package com.roni.library.runtime;

import android.content.Context;

import com.roni.library.contracts.common.RuntimeState;
import com.roni.library.router.NativeRouter;
import com.roni.library.runtime.handler.RuntimeCommandHandler;
import com.roni.library.runtime.service.NativeRuntimeService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe singleton entry point for NativeRuntimeEngine adhering to API.md.
 *
 * <p>{@link #init(Context, NativeRuntimeEngineConfig)} is self-contained: it builds the service,
 * starts the loopback web server, registers the {@code /runtime} route with {@link NativeRouter}
 * and returns success only when every step completed.</p>
 */
public class NativeRuntimeEngine {

    private static final String ROUTE = "/runtime";

    private static volatile NativeRuntimeEngine instance;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile RuntimeState state = RuntimeState.UNINITIALIZED;
    private NativeRuntimeEngineConfig config;
    private NativeRuntimeService service;
    private RuntimeCommandHandler commandHandler;
    private WebViewConfigurator configurator;
    private WebResourceInterceptor interceptor;
    private CookieSyncManager cookieSyncManager;

    private NativeRuntimeEngine() {}

    public static NativeRuntimeEngine getInstance() {
        if (instance == null) {
            synchronized (NativeRuntimeEngine.class) {
                if (instance == null) {
                    instance = new NativeRuntimeEngine();
                }
            }
        }
        return instance;
    }

    public synchronized NativeRuntimeEngineResult<Boolean> init(Context context,
                                                                NativeRuntimeEngineConfig config) {
        if (initialized.get()) {
            return NativeRuntimeEngineResult.success(true);
        }
        this.config = config != null ? config : NativeRuntimeEngineConfig.defaultConfiguration();
        this.state = RuntimeState.INITIALIZING;

        try {
            this.service = new NativeRuntimeService();
            this.service.initialize();
            this.service.start();

            this.commandHandler = new RuntimeCommandHandler(this.service);
            this.configurator = new WebViewConfigurator(this.config);
            this.interceptor = new WebResourceInterceptor(this.service.getWebSourceResolver());
            this.cookieSyncManager = this.service.getCookieSyncManager();

            NativeRouter router = NativeRouter.getInstance();
            router.registerHandler(this.commandHandler);
            router.registerRoute(ROUTE, this.commandHandler);

            this.initialized.set(true);
            this.state = RuntimeState.READY;
            return NativeRuntimeEngineResult.success(true);
        } catch (Throwable t) {
            this.state = RuntimeState.ERROR;
            this.initialized.set(false);
            return NativeRuntimeEngineResult.failure(
                    t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName(), 500);
        }
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public RuntimeState getState() {
        return state;
    }

    public NativeRuntimeEngineConfig getConfig() {
        return config;
    }

    public NativeRuntimeService getService() {
        return service;
    }

    public RuntimeCommandHandler getCommandHandler() {
        return commandHandler;
    }

    public WebViewConfigurator getConfigurator() {
        checkInitialized();
        return configurator;
    }

    public WebResourceInterceptor getInterceptor() {
        checkInitialized();
        return interceptor;
    }

    public CookieSyncManager getCookieSyncManager() {
        checkInitialized();
        return cookieSyncManager;
    }

    /**
     * Unregisters the route, stops the service and resets state. The singleton is retained so a
     * later {@link #init} rebuilds the same instance.
     */
    public synchronized void release() {
        if (!initialized.get() && service == null) {
            return;
        }
        try {
            NativeRouter.getInstance().unregisterRoute(ROUTE);
        } catch (Throwable ignored) {
            // Router may not be initialised.
        }
        if (service != null) {
            try {
                service.stop();
            } catch (Throwable ignored) {
                // Continue releasing.
            }
            try {
                service.dispose();
            } catch (Throwable ignored) {
                // Continue releasing.
            }
            service = null;
        }
        commandHandler = null;
        configurator = null;
        interceptor = null;
        cookieSyncManager = null;
        config = null;
        initialized.set(false);
        state = RuntimeState.UNINITIALIZED;
    }

    private void checkInitialized() {
        if (!initialized.get()) {
            throw new IllegalStateException(
                    "NativeRuntimeEngine is not initialized. Call init(context, config) first.");
        }
    }
}
