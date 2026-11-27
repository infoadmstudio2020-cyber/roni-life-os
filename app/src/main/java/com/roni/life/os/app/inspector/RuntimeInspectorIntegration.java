package com.roni.life.os.app.inspector;

import com.roni.library.bridge.BridgeTraceListener;
import com.roni.library.bridge.WebBridge;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.devtools.inspector.RuntimeLogCollector;
import com.roni.library.ui.web.WebDiagnosticsListener;
import com.roni.library.ui.web.WebViewHost;

/**
 * Host-side glue between the runtime libraries and the Runtime Inspector.
 *
 * <p>This is the only inspector-related class left in the application, and it contains no
 * inspector logic: it implements the two observer interfaces the libraries already expose
 * ({@link WebDiagnosticsListener} on {@code WebViewHost}, {@link BridgeTraceListener} on
 * {@code WebBridge}) and forwards each event to the library collector.</p>
 *
 * <p>Why it lives here: the libraries must not depend on the host app, so they cannot register
 * themselves. The host is the only layer that knows both sides, which is exactly where a
 * composition root belongs.</p>
 *
 * <p>Every callback runs on the WebView thread and only appends to a bounded buffer — no I/O,
 * no formatting, no UI work — so page load and bridge latency are unaffected.</p>
 */
public final class RuntimeInspectorIntegration
        implements WebDiagnosticsListener, BridgeTraceListener {

    private static final RuntimeInspectorIntegration INSTANCE = new RuntimeInspectorIntegration();

    private RuntimeInspectorIntegration() {
    }

    public static RuntimeInspectorIntegration get() {
        return INSTANCE;
    }

    /** Registers the WebView diagnostics observer. Safe to call repeatedly. */
    public void attach(WebViewHost host) {
        if (host == null) {
            return;
        }
        try {
            host.setDiagnosticsListener(this);
        } catch (Throwable ignored) {
            // Diagnostics must never break the runtime.
        }
    }

    /** Registers the bridge trace observer. Safe to call repeatedly. */
    public void attach(WebBridge bridge) {
        if (bridge == null) {
            return;
        }
        try {
            bridge.setTraceListener(this);
        } catch (Throwable ignored) {
            // Diagnostics must never break the runtime.
        }
    }

    // ==================== WebDiagnosticsListener ====================

    @Override
    public void onConsoleMessage(int priority, String message, String sourceId, int lineNumber) {
        LogLevel level = fromAndroidPriority(priority);
        // The injected reporter funnels uncaught errors through console.error; keep them at
        // ERROR even if a page downgrades the level.
        if (message != null && (message.startsWith(RuntimeLogCollector.WEB_ERROR_PREFIX)
                || message.startsWith(RuntimeLogCollector.WEB_REJECTION_PREFIX))) {
            level = LogLevel.ERROR;
        }
        RuntimeLogCollector.get().web(level, message, sourceId, lineNumber);
    }

    @Override
    public void onPageEvent(String event, String url) {
        RuntimeLogCollector collector = RuntimeLogCollector.get();
        collector.webLifecycle(levelForPageEvent(event), event, url);
        if ("PAGE_FINISHED".equals(event)) {
            collector.startup("WEB_PAGE_READY", url);
        }
    }

    @Override
    public void onResourceError(String url, String description, boolean mainFrame) {
        RuntimeLogCollector.get().webLifecycle(
                mainFrame ? LogLevel.ERROR : LogLevel.WARN,
                "RESOURCE_ERROR " + (description != null ? description : ""),
                url);
    }

    // ==================== BridgeTraceListener ====================

    @Override
    public void onRequest(String requestId, String action, String route) {
        RuntimeLogCollector.get().bridgeRequest(requestId, action, route);
    }

    @Override
    public void onResponse(String requestId, String action, long durationMs, boolean success,
                           String error) {
        RuntimeLogCollector.get().bridgeResponse(requestId, action, "", durationMs, success, error);
    }

    @Override
    public void onRejected(String requestId, String action, String reason) {
        RuntimeLogCollector.get().bridgeRejected(requestId, action, reason);
    }

    @Override
    public void onEvent(String topic) {
        RuntimeLogCollector.get().bridgeEvent(topic);
    }

    // ==================== Mapping ====================

    /** Maps {@code android.util.Log} priorities onto the shared level enum. */
    static LogLevel fromAndroidPriority(int priority) {
        switch (priority) {
            case 2:
                return LogLevel.VERBOSE;
            case 3:
                return LogLevel.DEBUG;
            case 4:
                return LogLevel.INFO;
            case 5:
                return LogLevel.WARN;
            case 6:
                return LogLevel.ERROR;
            case 7:
                return LogLevel.FATAL;
            default:
                return LogLevel.INFO;
        }
    }

    private static LogLevel levelForPageEvent(String event) {
        if (event == null) {
            return LogLevel.DEBUG;
        }
        if ("RENDERER_GONE".equals(event)) {
            return LogLevel.ERROR;
        }
        if ("PAGE_FINISHED".equals(event)) {
            return LogLevel.INFO;
        }
        return LogLevel.DEBUG;
    }
}
