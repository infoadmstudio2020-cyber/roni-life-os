package com.roni.library.bridge;

import com.roni.library.bridge.event.BridgeEvent;
import com.roni.library.bridge.event.BridgeEventBus;
import com.roni.library.bridge.event.IEventBus;
import com.roni.library.bridge.handler.BridgeCommandHandler;
import com.roni.library.bridge.js.JavaScriptBridgeScript;
import com.roni.library.bridge.js.NativeJavascriptInterface;
import com.roni.library.bridge.message.BridgeMessage;
import com.roni.library.bridge.message.BridgeMessageType;
import com.roni.library.bridge.serializer.IMessageSerializer;
import com.roni.library.bridge.serializer.JsonMessageSerializer;
import com.roni.library.contracts.api.IApiCallback;
import com.roni.library.contracts.api.NativeApiError;
import com.roni.library.contracts.api.NativeApiRequest;
import com.roni.library.contracts.api.NativeApiResponse;
import com.roni.library.logger.NativeLogger;
import com.roni.library.router.IRouter;
import com.roni.library.router.NativeRouter;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Primary Layer 3 Transport &amp; Bridge Orchestrator.
 *
 * <p>Every inbound message is origin-checked, size-checked and validated before it can reach
 * the native router. Outbound messages are JSON-quoted before being interpolated into the
 * WebView evaluation script.</p>
 */
public final class WebBridge {

    private static final String TAG = "NativeWebBridgeSDK";
    private static final long MIN_SWEEP_INTERVAL_MS = 100L;
    private static final long MAX_SWEEP_INTERVAL_MS = 5000L;

    private static volatile WebBridge sInstance;

    private final BridgeConfig config;
    private final IRouter router;
    private final IEventBus eventBus;
    private final IMessageSerializer serializer;
    private final NativeJavascriptInterface javascriptInterface;
    private final BridgeSecurityValidator securityValidator;
    private final BridgeCommandHandler commandHandler;

    private volatile Consumer<String> webEvaluator;
    private volatile OriginProvider originProvider;
    private volatile ScheduledExecutorService sweeper;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    /** Optional Runtime Inspector sink. Null means tracing is off. */
    private volatile BridgeTraceListener traceListener;

    /** Registers the Runtime Inspector trace sink. */
    public void setTraceListener(BridgeTraceListener listener) {
        this.traceListener = listener;
    }

    private WebBridge(BridgeConfig config) {
        this.config = config != null ? config : BridgeConfig.defaultConfig();
        this.router = NativeRouter.getInstance();
        this.eventBus = new BridgeEventBus();
        this.serializer = new JsonMessageSerializer();
        this.javascriptInterface = new NativeJavascriptInterface(this);
        this.securityValidator = new BridgeSecurityValidator(this.config);
        this.commandHandler = new BridgeCommandHandler(this);

        if (this.router != null) {
            try {
                this.router.registerHandler(this.commandHandler);
                this.router.registerRoute("/bridge", this.commandHandler);
                this.router.registerRoute("/bridge/dispatch", this.commandHandler);
            } catch (Throwable t) {
                logWarn("Failed to register bridge route with router: " + t.getMessage());
            }
        }
        startSweeper();
        initialized.set(true);
        logInfo("WebBridge initialized with interface: " + this.config.getInterfaceName());
    }

    public static WebBridge getInstance() {
        WebBridge local = sInstance;
        if (local == null || !local.isInitialized()) {
            synchronized (WebBridge.class) {
                local = sInstance;
                if (local == null || !local.isInitialized()) {
                    local = new WebBridge(BridgeConfig.defaultConfig());
                    sInstance = local;
                }
            }
        }
        return local;
    }

    /**
     * Returns the process-wide bridge, creating it on first call.
     *
     * <p>If an instance already exists with a different interface name, the existing instance
     * wins and a warning is logged: the name is already bound into the WebView.</p>
     */
    public static synchronized WebBridge init(BridgeConfig config) {
        if (sInstance == null || !sInstance.isInitialized()) {
            sInstance = new WebBridge(config);
            return sInstance;
        }
        if (config != null
                && !sInstance.config.getInterfaceName().equals(config.getInterfaceName())) {
            logWarnStatic("Bridge already initialised with interface '"
                    + sInstance.config.getInterfaceName() + "'; ignoring requested '"
                    + config.getInterfaceName() + "'.");
        }
        return sInstance;
    }

    public static boolean isInitialized() {
        WebBridge local = sInstance;
        return local != null && local.initialized.get();
    }

    public BridgeConfig getConfig() {
        return config;
    }

    public IRouter getRouter() {
        return router;
    }

    public IEventBus getEventBus() {
        return eventBus;
    }

    public IMessageSerializer getSerializer() {
        return serializer;
    }

    public NativeJavascriptInterface getJavascriptInterface() {
        return javascriptInterface;
    }

    public void setWebEvaluator(Consumer<String> evaluator) {
        this.webEvaluator = evaluator;
    }

    public void clearWebEvaluator() {
        this.webEvaluator = null;
    }

    /**
     * Attaches the provider that reports the current page origin.
     */
    public void setOriginProvider(OriginProvider provider) {
        this.originProvider = provider;
    }

    /**
     * Trusts {@code origin} for the lifetime of this bridge.
     *
     * <p>Called by the host when it loads a project's virtual origin, so a per-project host can
     * use the bridge without weakening the allow-list for every other origin.</p>
     */
    public void allowOrigin(String origin) {
        if (config != null) {
            config.addAllowedOrigin(origin);
        }
    }

    public void clearOriginProvider() {
        this.originProvider = null;
    }

    /**
     * @return true when origin validation is required by config <em>and</em> a provider is
     *         attached. Diagnostics only — {@link #handleWebMessage} fails closed regardless.
     */
    public boolean isOriginValidationEnabled() {
        return config.isOriginValidationRequired() && originProvider != null;
    }

    /**
     * Checks whether this WebBridge instance is initialized.
     */
    public boolean isInstanceInitialized() {
        return initialized.get();
    }
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }

    public String getBootstrapScript() {
        return JavaScriptBridgeScript.getInjectionScript(config.getInterfaceName());
    }

    /**
     * Applies new runtime settings without changing the injected interface name.
     */
    public void reconfigure(BridgeConfig newConfig) {
        if (newConfig == null) {
            return;
        }
        config.applyRuntimeSettings(newConfig);
        restartSweeper();
        logInfo("WebBridge reconfigured (interface name unchanged: "
                + config.getInterfaceName() + ")");
    }

    public void emitToWeb(String topic, Map<String, Object> data) {
        BridgeMessage eventMsg = BridgeMessage.event(topic, data);
        dispatchJs(serializer.serialize(eventMsg));
    }

    public void handleWebMessage(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return;
        }

        // B7 — reject oversized payloads before parsing them.
        int size = rawJson.getBytes(StandardCharsets.UTF_8).length;
        if (size > config.getMaxMessageBytes()) {
            logWarn("Rejected oversized bridge message: " + size + " bytes");
            traceRejected("unknown", "", "PAYLOAD_TOO_LARGE");
            dispatchJs(serializer.serialize(
                    BridgeMessage.error("unknown", "", "PAYLOAD_TOO_LARGE")));
            return;
        }

        logDebug("Received web message: " + rawJson);

        BridgeMessage message = serializer.deserialize(rawJson);

        // B1 — origin gate. Runs before PING, EVENT and REQUEST dispatch.
        if (config.isOriginValidationRequired()) {
            OriginProvider provider = this.originProvider;
            String origin = provider != null ? provider.getCurrentOrigin() : null;
            if (!securityValidator.isOriginValid(origin)) {
                logWarn("Rejected bridge message from origin: "
                        + (origin != null ? origin : "unknown"));
                traceRejected(message.getId(), message.getAction(), "ORIGIN_REJECTED");
                dispatchJs(serializer.serialize(BridgeMessage.error(
                        message.getId(),
                        message.getAction(),
                        "ORIGIN_REJECTED: " + (origin != null ? origin : "unknown"))));
                return;
            }
        }

        if (!securityValidator.isMessageValid(message)) {
            logWarn("Invalid message payload or missing action: " + rawJson);
            BridgeMessage errorMsg = BridgeMessage.error(
                    message.getId(), message.getAction(), "Security validation failed");
            dispatchJs(serializer.serialize(errorMsg));
            return;
        }

        if (message.getType() == BridgeMessageType.PING) {
            BridgeMessage pong = BridgeMessage.ping(message.getId());
            dispatchJs(serializer.serialize(pong));
            return;
        }

        if (message.getType() == BridgeMessageType.EVENT) {
            eventBus.publish(BridgeEvent.of(message.getAction(), message.getPayload(), "web"));
            traceEvent(message.getAction());
            return;
        }

        // REQUEST handling.
        // Accept all common action forms:
        //   "ui.showToast"  -> path "/ui", action "showToast"
        //   "/ui/showToast" -> path "/ui", action "showToast"
        //   "showToast"     -> path "/showToast", action "showToast"
        String rawAction = message.getAction();
        String path;
        String action;
        int dot = rawAction.indexOf('.');
        if (dot > 0) {
            path = rawAction.substring(0, dot);
            action = rawAction.substring(dot + 1);
        } else if (rawAction.startsWith("/") && rawAction.indexOf('/', 1) > 0) {
            int slash = rawAction.indexOf('/', 1);
            path = rawAction.substring(0, slash);
            action = rawAction.substring(slash + 1);
        } else {
            path = rawAction;
            action = rawAction;
        }
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        pendingRequests.put(message.getId(),
                new PendingRequest(rawAction, System.currentTimeMillis()));
        traceRequest(message.getId(), rawAction, normalizedPath);

        NativeApiRequest request = new NativeApiRequest(
                message.getId(),
                normalizedPath,
                action,
                message.getPayload(),
                message.getTimestamp()
        );

        if (router != null && router.canHandle(normalizedPath)) {
            router.dispatch(request, new IApiCallback() {
                @Override
                public void onResponse(NativeApiResponse response) {
                    onSuccess(response);
                }

                @Override
                public void onError(NativeApiError error) {
                    WebBridge.this.onError(message.getId(), error);
                }
            });
        } else {
            // No handler registered for this route. Fail loudly instead of echoing the
            // payload back as a success, which made typos look like working calls.
            logDebug("Route not found in NativeRouter: " + normalizedPath);
            onError(message.getId(), new NativeApiError(
                    "ROUTE_NOT_FOUND",
                    "No handler registered for route: " + normalizedPath,
                    rawAction));
        }
    }

    public void onSuccess(NativeApiResponse response) {
        if (response == null) {
            return;
        }
        String reqId = response.getRequestId();
        PendingRequest pending = pendingRequests.remove(reqId);
        if (pending == null) {
            // Duplicate, unknown, or already timed-out request: drop it.
            logDebug("Dropping response for unknown or expired request: " + reqId);
            return;
        }
        traceResponse(reqId, pending.action,
                System.currentTimeMillis() - pending.createdAtMs, true, null);
        BridgeMessage msg = BridgeMessage.response(reqId, pending.action, response.getData());
        dispatchJs(serializer.serialize(msg));
    }

    public void onError(NativeApiError error) {
        onError("unknown", error);
    }

    public void onError(String reqId, NativeApiError error) {
        PendingRequest pending = pendingRequests.remove(reqId);
        String action = pending != null ? pending.action : "";
        String errMsg = error != null ? error.getMessage() : "Unknown native error";
        traceResponse(reqId, action,
                pending != null ? System.currentTimeMillis() - pending.createdAtMs : -1L,
                false, errMsg);
        BridgeMessage msg = BridgeMessage.error(reqId, action, errMsg);
        dispatchJs(serializer.serialize(msg));
    }

    /**
     * Emits a script into the page.
     *
     * <p>The payload is wrapped with {@link JSONObject#quote(String)} so that quotes,
     * {@code </script>}, U+2028 and U+2029 cannot terminate the string literal and execute
     * attacker-controlled script.</p>
     */
    private void dispatchJs(String jsonString) {
        Consumer<String> evaluator = this.webEvaluator;
        if (evaluator == null) {
            return;
        }

        String quoted;
        try {
            quoted = JSONObject.quote(jsonString != null ? jsonString : "{}");
        } catch (Throwable t) {
            logWarn("Failed to quote bridge payload: " + t.getMessage());
            return;
        }

        String name = config.getInterfaceName();
        String script = "if (window['" + name + "Client'] && window['" +
                name + "Client']._handleNativeMessage) { window['" +
                name + "Client']._handleNativeMessage(" + quoted + "); }";
        try {
            evaluator.accept(script);
        } catch (Throwable t) {
            logWarn("Failed to execute JS in webEvaluator: " + t.getMessage());
        }
    }

    private void startSweeper() {
        long interval = Math.max(MIN_SWEEP_INTERVAL_MS,
                Math.min(config.getTimeoutMs(), MAX_SWEEP_INTERVAL_MS));
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "native-bridge-timeout");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::sweepTimeouts, interval, interval,
                TimeUnit.MILLISECONDS);
        this.sweeper = executor;
    }

    private void restartSweeper() {
        shutdownSweeper();
        startSweeper();
    }

    private void shutdownSweeper() {
        ScheduledExecutorService executor = this.sweeper;
        this.sweeper = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void sweepTimeouts() {
        try {
            long now = System.currentTimeMillis();
            long timeout = config.getTimeoutMs();
            for (Map.Entry<String, PendingRequest> entry : pendingRequests.entrySet()) {
                PendingRequest pending = entry.getValue();
                if (now - pending.createdAtMs < timeout) {
                    continue;
                }
                if (pendingRequests.remove(entry.getKey(), pending)) {
                    logWarn("Bridge request timed out: " + entry.getKey());
                    dispatchJs(serializer.serialize(
                            BridgeMessage.error(entry.getKey(), pending.action, "REQUEST_TIMEOUT")));
                }
            }
        } catch (Throwable t) {
            logWarn("Bridge timeout sweep failed: " + t.getMessage());
        }
    }

    private void logDebug(String msg) {
        if (config.isDebugLogging()) {
            try {
                NativeLogger.getInstance().d(TAG, msg);
            } catch (Throwable ignored) {
            }
        }
    }

    private void logInfo(String msg) {
        try {
            NativeLogger.getInstance().i(TAG, msg);
        } catch (Throwable ignored) {
        }
    }

    private void logWarn(String msg) {
        logWarnStatic(msg);
    }

    private static void logWarnStatic(String msg) {
        try {
            NativeLogger.getInstance().w(TAG, msg);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Resets this bridge. The singleton reference is owned by {@code NativeWebBridgeSDK};
     * this method does not null it, so a concurrent {@code getInstance()} cannot observe a
     * half-released instance.
     */
    public void release() {
        initialized.set(false);
        shutdownSweeper();
        pendingRequests.clear();
        eventBus.clear();
        webEvaluator = null;
        originProvider = null;
    }

    // ==================== Inspector tracing ====================

    private void traceRequest(String requestId, String action, String route) {
        BridgeTraceListener sink = traceListener;
        if (sink == null) return;
        try {
            sink.onRequest(requestId, action, route);
        } catch (Throwable ignored) {
            // Tracing must never break a bridge call.
        }
    }

    private void traceResponse(String requestId, String action, long durationMs,
                               boolean success, String error) {
        BridgeTraceListener sink = traceListener;
        if (sink == null) return;
        try {
            sink.onResponse(requestId, action, durationMs, success, error);
        } catch (Throwable ignored) {
        }
    }

    private void traceRejected(String requestId, String action, String reason) {
        BridgeTraceListener sink = traceListener;
        if (sink == null) return;
        try {
            sink.onRejected(requestId, action, reason);
        } catch (Throwable ignored) {
        }
    }

    private void traceEvent(String topic) {
        BridgeTraceListener sink = traceListener;
        if (sink == null) return;
        try {
            sink.onEvent(topic);
        } catch (Throwable ignored) {
        }
    }

    private static final class PendingRequest {
        final String action;
        final long createdAtMs;

        PendingRequest(String action, long createdAtMs) {
            this.action = action;
            this.createdAtMs = createdAtMs;
        }
    }
}
