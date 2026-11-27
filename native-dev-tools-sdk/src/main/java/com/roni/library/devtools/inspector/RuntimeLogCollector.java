package com.roni.library.devtools.inspector;

import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.contracts.logger.LogSource;

/**
 * The single ingest gate of the Runtime Inspector.
 *
 * <p>Two kinds of producer converge here and nowhere else:</p>
 * <ul>
 *   <li><b>Application logs</b> — {@link InspectorLogSink} forwards every entry the existing
 *       {@code NativeLogger} accepts. The inspector never re-logs them, so there is exactly one
 *       logging pipeline and no duplicate entries.</li>
 *   <li><b>Runtime events</b> — the WebView, bridge, network and startup observers call the
 *       typed methods below. They are event sources, not loggers: they build a
 *       {@link LogEntry} and hand it to the same gate.</li>
 * </ul>
 *
 * <p>The gate applies the configured minimum level, the per-source switches and redaction
 * <b>before</b> the entry reaches the buffer, so a disabled source costs nothing and a secret
 * never enters memory. Every method is safe to call from any thread and never throws.</p>
 */
public final class RuntimeLogCollector {

    private static final RuntimeLogCollector INSTANCE = new RuntimeLogCollector();

    /** Marker prefixes the injected web error handler uses. */
    public static final String WEB_ERROR_PREFIX = "[JS-ERROR]";
    public static final String WEB_REJECTION_PREFIX = "[JS-REJECTION]";

    private final RuntimeLogRepository repository = new RuntimeLogRepository();

    private volatile InspectorConfig config;
    private volatile String projectId = "";
    private volatile String startupCorrelationId = "";

    private RuntimeLogCollector() {
    }

    public static RuntimeLogCollector get() {
        return INSTANCE;
    }

    /** Wires the persisted settings in. Applies the configured buffer size immediately. */
    public void attachConfig(InspectorConfig config) {
        this.config = config;
        if (config != null) {
            repository.setCapacity(config.getMaxEntries());
        }
    }

    public RuntimeLogRepository getRepository() {
        return repository;
    }

    public InspectorConfig getConfig() {
        return config;
    }

    // ==================== Session context ====================

    public void setProjectId(String projectId) {
        this.projectId = projectId != null ? projectId : "";
    }

    public String getProjectId() {
        return projectId;
    }

    /** Starts a new startup trace and returns its correlation id. */
    public String newStartupCorrelationId() {
        String id = "startup-" + Long.toHexString(System.currentTimeMillis());
        this.startupCorrelationId = id;
        return id;
    }

    public String getStartupCorrelationId() {
        return startupCorrelationId;
    }

    /** Clears the buffer — used by "clear logs" and by "clear on new session". */
    public void clear() {
        repository.clear();
    }

    // ==================== Ingest ====================

    /**
     * The single ingest point. Applies the settings gate, redaction and the record id, then
     * stores the entry in the shared buffer.
     *
     * @param entry entry produced by the logger sink or by one of the typed producers
     */
    public void accept(LogEntry entry) {
        if (entry == null) {
            return;
        }
        try {
            InspectorConfig current = config;
            if (current != null) {
                if (!current.isEnabled()) {
                    return;
                }
                if (!entry.getLevel().isLoggable(current.getMinLevel())) {
                    return;
                }
                if (!current.isSourceEnabled(entry.getSource())) {
                    return;
                }
            }
            String message = entry.getMessage();
            if (current == null || current.isRedactionEnabled()) {
                message = LogRedactor.redact(message);
            }
            LogEntry stored = new LogEntry.Builder(entry)
                    .id(repository.nextId())
                    .message(message)
                    .build();
            repository.add(stored);
        } catch (Throwable ignored) {
            // Diagnostics must never break the caller.
        }
    }

    // ==================== Web ====================

    /**
     * Records a WebView console message.
     *
     * @param sourceId the script URL reported by {@code ConsoleMessage.sourceId()}
     * @param line     the line number, or -1 when unknown
     */
    public void web(LogLevel level, String message, String sourceId, int line) {
        String location = "";
        if (sourceId != null && !sourceId.isEmpty()) {
            location = " (" + LogRedactor.redactUrl(sourceId)
                    + (line > 0 ? ":" + line : "") + ")";
        }
        accept(base(level, LogSource.WEB, message + location).module("WebView").build());
    }

    /** Records a page lifecycle event (started, finished, error, renderer gone). */
    public void webLifecycle(LogLevel level, String event, String url) {
        String detail = event + (url != null && !url.isEmpty()
                ? " " + LogRedactor.redactUrl(url) : "");
        accept(base(level, LogSource.WEB, detail).module("WebView").build());
    }

    // ==================== Bridge ====================

    /** Records a Web → Native request. The payload is never recorded. */
    public void bridgeRequest(String requestId, String action, String handler) {
        accept(base(LogLevel.DEBUG, LogSource.BRIDGE,
                "→ " + safe(action) + (handler != null && !handler.isEmpty()
                        ? " handler=" + handler : ""))
                .module("Bridge")
                .requestId(requestId)
                .direction(LogEntry.DIRECTION_WEB_TO_NATIVE)
                .status(LogEntry.STATUS_PENDING)
                .build());
    }

    /** Records the outcome of a bridge request. */
    public void bridgeResponse(String requestId, String action, String handler,
                               long durationMs, boolean success, String error) {
        String message = "← " + safe(action)
                + (success ? " ok" : " failed")
                + (error != null && !error.isEmpty() ? ": " + error : "");
        accept(base(success ? LogLevel.DEBUG : LogLevel.ERROR, LogSource.BRIDGE, message)
                .module("Bridge")
                .requestId(requestId)
                .direction(LogEntry.DIRECTION_NATIVE_TO_WEB)
                .status(success ? LogEntry.STATUS_SUCCESS : LogEntry.STATUS_FAILED)
                .durationMs(durationMs)
                .build());
    }

    /** Records a bridge message rejected before dispatch (origin, size, validation). */
    public void bridgeRejected(String requestId, String action, String reason) {
        accept(base(LogLevel.WARN, LogSource.BRIDGE,
                "rejected " + safe(action) + ": " + safe(reason))
                .module("Bridge")
                .requestId(requestId)
                .direction(LogEntry.DIRECTION_WEB_TO_NATIVE)
                .status(LogEntry.STATUS_REJECTED)
                .build());
    }

    /** Records a Native → Web event. */
    public void bridgeEvent(String topic) {
        accept(base(LogLevel.VERBOSE, LogSource.BRIDGE, "event " + safe(topic))
                .module("Bridge")
                .direction(LogEntry.DIRECTION_NATIVE_TO_WEB)
                .build());
    }

    // ==================== Network ====================

    public void network(String method, String url, int statusCode, long durationMs,
                        String failure) {
        boolean failed = failure != null && !failure.isEmpty();
        String message = safe(method) + " " + LogRedactor.redactUrl(url)
                + (statusCode > 0 ? " → " + statusCode : "")
                + (failed ? " failed: " + failure : "");
        accept(base(failed ? LogLevel.ERROR : LogLevel.DEBUG, LogSource.NETWORK, message)
                .module("Network")
                .status(failed ? LogEntry.STATUS_FAILED : LogEntry.STATUS_SUCCESS)
                .durationMs(durationMs)
                .build());
    }

    /** Records an offline/online transition. */
    public void networkState(boolean online) {
        accept(base(LogLevel.INFO, LogSource.NETWORK,
                online ? "network online" : "network offline").module("Network").build());
    }

    // ==================== Startup ====================

    /**
     * Records one startup checkpoint.
     *
     * <p>The checkpoint name is the contract the Inspector reconstructs the launch flow from,
     * so it is emitted verbatim and always carries the startup correlation id.</p>
     */
    public void startup(String checkpoint, String detail) {
        accept(base(LogLevel.INFO, LogSource.LIFECYCLE,
                safe(checkpoint) + (detail != null && !detail.isEmpty()
                        ? " " + detail : ""))
                .module("Startup")
                .tag(checkpoint)
                .correlationId(startupCorrelationId)
                .build());
    }

    // ==================== Other sources ====================

    public void lifecycle(String detail) {
        accept(base(LogLevel.DEBUG, LogSource.LIFECYCLE, detail).module("Lifecycle").build());
    }

    public void project(String detail) {
        accept(base(LogLevel.INFO, LogSource.PROJECT, detail).module("Project").build());
    }

    public void storage(String detail) {
        accept(base(LogLevel.DEBUG, LogSource.STORAGE, detail).module("Storage").build());
    }

    public void security(String detail) {
        accept(base(LogLevel.WARN, LogSource.SECURITY, detail).module("Security").build());
    }

    public void performance(String detail) {
        accept(base(LogLevel.INFO, LogSource.PERFORMANCE, detail).module("Performance").build());
    }

    // ==================== Internals ====================

    private LogEntry.Builder base(LogLevel level, LogSource source, String message) {
        return new LogEntry.Builder()
                .level(level)
                .source(source)
                .message(message)
                .projectId(projectId)
                .correlationId(startupCorrelationId);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
