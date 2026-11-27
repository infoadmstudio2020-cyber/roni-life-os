package com.roni.library.os;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.analytics.AnalyticsEngine;
import com.roni.library.bridge.NativeWebBridgeSDK;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.contracts.common.RuntimeState;
import com.roni.library.devtools.DevToolsSDK;
import com.roni.library.logger.NativeLogger;
import com.roni.library.logger.NativeLoggerSDK;
import com.roni.library.network.NetworkEngine;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.router.NativeRouter;
import com.roni.library.runtime.NativeRuntimeEngine;
import com.roni.library.security.SecurityEngine;
import com.roni.library.service.NativeServiceSDK;
import com.roni.library.storage.StorageEngine;
import com.roni.library.theme.ThemeEngine;
import com.roni.library.ui.NativeRuntimeUI;
import com.roni.library.ui.host.NativeUIHost;
import com.roni.library.os.bootstrap.NativeRuntimeBootstrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Master Turnkey Facade and Host Activity Orchestrator for the NativeRuntime SDK.
 * Class: Layer 4 Master Orchestration (L4).
 */
public final class NativeRuntime {

    private static volatile NativeRuntime instance;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<RuntimeSession> currentSession = new AtomicReference<>(null);
    private final List<LifecycleObserver> observers = new CopyOnWriteArrayList<>();
    private final Map<String, Object> telemetryEvents = Collections.synchronizedMap(new HashMap<>());

    private volatile RuntimeConfiguration configuration;
    private volatile NativeRuntimeBootstrap.BootstrapReport bootstrapReport;
    private volatile RuntimeState state = RuntimeState.UNINITIALIZED;
    private final List<String> releaseErrors = new CopyOnWriteArrayList<>();

    private NativeRuntime() {}

    public static NativeRuntime getInstance() {
        if (instance == null) {
            synchronized (NativeRuntime.class) {
                if (instance == null) {
                    instance = new NativeRuntime();
                }
            }
        }
        return instance;
    }

    /**
     * Initializes NativeRuntime with default configuration.
     */
    public synchronized NativeRuntimeResult<Boolean> init(Context context) {
        return init(context, RuntimeConfiguration.builder().build());
    }

    /**
     * Initializes NativeRuntime with custom configuration and runs the 10-step coordinated bootstrap.
     */
    public synchronized NativeRuntimeResult<Boolean> init(Context context, RuntimeConfiguration config) {
        if (initialized.get()) {
            if (config != null && configsDiffer(this.configuration, config)) {
                try {
                    NativeLogger.getInstance().w("NativeRuntime",
                            "init() called again with a different configuration; "
                                    + "the original configuration is retained.");
                } catch (Throwable ignored) {
                    // Logging must never break initialisation.
                }
            }
            return NativeRuntimeResult.success(true);
        }

        try {
            this.configuration = config != null ? config : RuntimeConfiguration.builder().build();
            this.state = RuntimeState.INITIALIZING;

            // Run 10-step coordinated turnkey bootstrap sequence.
            // The UI layer is initialised by NativeRuntimeActivity, not here.
            this.bootstrapReport = NativeRuntimeBootstrap.execute(context, this.configuration);

            if (!bootstrapReport.isOverallSuccess()) {
                this.state = RuntimeState.ERROR;
                notifyError(500, "Turnkey bootstrap failed on one or more steps.");
                return NativeRuntimeResult.failure(500, "Bootstrap failed. Passed: " +
                        bootstrapReport.getPassedCount() + "/" + bootstrapReport.getSteps().size());
            }

            // Start master runtime session
            startSession();

            this.state = RuntimeState.READY;
            this.initialized.set(true);

            notifyInitialized();
            return NativeRuntimeResult.success(true);
        } catch (Throwable t) {
            this.state = RuntimeState.ERROR;
            notifyError(500, t.getMessage());
            return NativeRuntimeResult.failure(t);
        }
    }

    public synchronized void startSession() {
        RuntimeSession session = new RuntimeSession();
        session.markActive();
        this.currentSession.set(session);
        for (LifecycleObserver obs : observers) {
            try {
                obs.onSessionStarted(session);
            } catch (Throwable ignored) {}
        }
    }

    public synchronized void pauseSession() {
        RuntimeSession session = currentSession.get();

        if (session != null) {
            session.markPaused();
            this.state = RuntimeState.SUSPENDED;

            for (LifecycleObserver obs : observers) {
                try {
                    obs.onSessionPaused(session);
                } catch (Exception ignored) {
                    // Observer failure must not interrupt lifecycle dispatch.
                }
            }
        }
    }

    public synchronized void resumeSession() {
        RuntimeSession session = currentSession.get();
        if (session != null) {
            session.markActive();
            this.state = RuntimeState.READY;
            for (LifecycleObserver obs : observers) {
                try {
                    obs.onSessionResumed(session);
                } catch (Throwable ignored) {}
            }
        }
    }

    public synchronized void stopSession() {
        RuntimeSession session = currentSession.get();
        if (session != null) {
            session.markStopped();
            for (LifecycleObserver obs : observers) {
                try {
                    obs.onSessionStopped(session);
                } catch (Throwable ignored) {}
            }
        }
    }


    /**
     * Returns the aggregated NativeRuntimeUI subsystem.
     */
    public NativeRuntimeUI getUI() {
        return NativeRuntimeUI.getInstance();
    }

    /**
     * Returns the active NativeUIHost from NativeRuntimeUI.
     *
     * <p>The UI layer is initialised by {@code NativeRuntimeActivity.onCreate}. Before an
     * Activity attaches, this method returns {@code null} rather than throwing.</p>
     */
    @Nullable
    public NativeUIHost getUIHost() {
        try {
            return NativeRuntimeUI.getInstance().getUiHost();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Releases every subsystem in reverse bootstrap order.
     *
     * <p>A failure in one subsystem is recorded in {@link #getReleaseErrors()} and does not
     * prevent the remaining subsystems from being released. The singleton stays usable, so a
     * subsequent {@link #init(Context, RuntimeConfiguration)} rebuilds the whole stack.</p>
     */
    public synchronized void release() {
        stopSession();
        releaseErrors.clear();

        releaseSafely("NativeRuntimeUI", () -> NativeRuntimeUI.getInstance().release());
        releaseSafely("NativeRuntimeEngine", () -> NativeRuntimeEngine.getInstance().release());
        releaseSafely("NativeWebBridgeSDK", () -> NativeWebBridgeSDK.getInstance().release());
        releaseSafely("DevToolsSDK", () -> DevToolsSDK.getInstance().release());
        releaseSafely("ThemeEngine", () -> ThemeEngine.getInstance().release());
        releaseSafely("AnalyticsEngine", () -> AnalyticsEngine.getInstance().release());
        releaseSafely("NetworkEngine", () -> NetworkEngine.getInstance().release());
        releaseSafely("PlatformEngine", () -> PlatformEngine.getInstance().release());
        releaseSafely("StorageEngine", () -> StorageEngine.getInstance().release());
        releaseSafely("SecurityEngine", () -> SecurityEngine.getInstance().release());
        releaseSafely("NativeServiceSDK", () -> NativeServiceSDK.getInstance().release());

        this.initialized.set(false);
        this.currentSession.set(null);
        this.state = RuntimeState.UNINITIALIZED;
        this.telemetryEvents.clear();

        for (LifecycleObserver obs : observers) {
            try {
                obs.onReleased();
            } catch (Throwable ignored) {}
        }
        observers.clear();
    }

    private void releaseSafely(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            releaseErrors.add(name + ": " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
        }
    }

    /**
     * @return subsystem names whose release threw, in release order. Empty when clean.
     */
    @NonNull
    public List<String> getReleaseErrors() {
        return Collections.unmodifiableList(new ArrayList<>(releaseErrors));
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public RuntimeState getState() {
        return state;
    }

    public RuntimeConfiguration getConfiguration() {
        return configuration;
    }

    public RuntimeSession getSession() {
        return currentSession.get();
    }

    public NativeRuntimeBootstrap.BootstrapReport getBootstrapReport() {
        return bootstrapReport;
    }

    public boolean isHealthy() {
        return initialized.get() && state == RuntimeState.READY &&
                bootstrapReport != null && bootstrapReport.isOverallSuccess();
    }

    /**
     * Reports the real initialisation state of every subsystem.
     *
     * <p>Intended for diagnostics panels: each value is the subsystem's own
     * {@code isInitialized()} answer, never a derived guess.</p>
     */
    @NonNull
    public Map<String, Boolean> getEngineStates() {
        Map<String, Boolean> states = new LinkedHashMap<>();
        states.put("logger", safe(() -> NativeLoggerSDK.getInstance().isInitialized()));
        states.put("service", safe(() -> NativeServiceSDK.getInstance().isInitialized()));
        states.put("security", safe(() -> SecurityEngine.getInstance().isInitialized()));
        states.put("storage", safe(StorageEngine::isInitialized));
        states.put("platform", safe(() -> PlatformEngine.getInstance().isInitialized()));
        states.put("network", safe(NetworkEngine::isInitialized));
        states.put("analytics", safe(AnalyticsEngine::isInitialized));
        states.put("theme", safe(ThemeEngine::isInitialized));
        states.put("devtools", safe(DevToolsSDK::isInitialized));
        states.put("router", safe(() -> NativeRouter.getInstance().isInitialized()));
        states.put("bridge", safe(() -> NativeWebBridgeSDK.getInstance().isInitialized()));
        states.put("runtimeEngine", safe(() -> NativeRuntimeEngine.getInstance().isInitialized()));
        states.put("ui", safe(() -> NativeRuntimeUI.getInstance().isInitialized()));
        return states;
    }

    private static boolean safe(BooleanSupplier supplier) {
        try {
            return supplier.getAsBoolean();
        } catch (Throwable t) {
            return false;
        }
    }

    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    private static boolean configsDiffer(RuntimeConfiguration a, RuntimeConfiguration b) {
        if (a == null || b == null) {
            return a != b;
        }
        return !eq(a.getAppId(), b.getAppId())
                || !eq(a.getEnvironment(), b.getEnvironment())
                || a.isDebugEnabled() != b.isDebugEnabled()
                || !eq(a.getLogLevel(), b.getLogLevel())
                || a.isStorageEncryptionEnabled() != b.isStorageEncryptionEnabled()
                || a.getNetworkTimeoutMs() != b.getNetworkTimeoutMs()
                || a.getMaxNetworkRetries() != b.getMaxNetworkRetries()
                || a.isTelemetryEnabled() != b.isTelemetryEnabled();
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    public void addObserver(LifecycleObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(LifecycleObserver observer) {
        if (observer != null) {
            observers.remove(observer);
        }
    }


    /**
     * Standard RPC dispatcher delegating requests to the underlying router.
     *
     * <p>This is a real dispatch: the request travels through the same pipeline as a request
     * arriving from the JavaScript bridge, including interceptors and route matching.</p>
     */
    @NonNull
    public NativeApiResponse executeApiRequest(@Nullable NativeApiRequest request) {

        if (request == null) {
            return NativeApiResponse.error(
                    "req_unknown",
                    400,
                    new NativeApiError(
                            "INVALID_REQUEST",
                            "Null request object"
                    )
            );
        }

        if (!isInitialized()) {
            return NativeApiResponse.error(
                    request.getRequestId(),
                    503,
                    new NativeApiError(
                            "RUNTIME_NOT_INITIALIZED",
                            "NativeRuntime is not initialized"
                    )
            );
        }

        NativeRouter router = NativeRouter.getInstance();
        if (!router.isInitialized()) {
            return NativeApiResponse.error(
                    request.getRequestId(),
                    503,
                    new NativeApiError(
                            "ROUTER_UNAVAILABLE",
                            "NativeRouter is not initialized"
                    )
            );
        }

        return router.dispatchSync(request);
    }

    /**
     * Convenience overload that builds a {@link NativeApiRequest} and dispatches it.
     */
    @NonNull
    public NativeApiResponse executeApiRequest(@NonNull String path,
                                               @NonNull String action,
                                               @Nullable Map<String, Object> params) {
        NativeApiRequest request = new NativeApiRequest(
                "req_" + System.currentTimeMillis(),
                path,
                action,
                params != null ? params : Collections.emptyMap(),
                System.currentTimeMillis());
        return executeApiRequest(request);
    }

    /**
     * High-level telemetry dispatcher for host applications.
     */
    public void trackEvent(String eventName, Map<String, Object> params) {
        if (eventName != null) {
            telemetryEvents.put(eventName + "_" + System.currentTimeMillis(), params != null ? params : Collections.emptyMap());
        }
    }

    public Map<String, Object> getTelemetryEvents() {
        return new HashMap<>(telemetryEvents);
    }

    private void notifyInitialized() {
        for (LifecycleObserver obs : observers) {
            try {
                obs.onInitialized(this);
            } catch (Throwable ignored) {}
        }
    }

    private void notifyError(int code, String message) {
        for (LifecycleObserver obs : observers) {
            try {
                obs.onError(code, message);
            } catch (Throwable ignored) {}
        }
    }
}
