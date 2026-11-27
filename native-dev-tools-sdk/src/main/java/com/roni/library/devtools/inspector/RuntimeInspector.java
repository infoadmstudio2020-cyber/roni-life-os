package com.roni.library.devtools.inspector;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.devtools.inspector.ui.RuntimeInspectorSheet;
import com.roni.library.logger.NativeLogger;
import com.roni.library.logger.sink.ILogSink;

import java.util.List;

/**
 * Public entry point of the Runtime Inspector library.
 *
 * <p>Ownership: the inspector is a developer-tools component of the NativeRuntime ecosystem and
 * lives in {@code native-dev-tools-sdk}. It owns the buffer, the ingest gate, the filter, the
 * exporter and the overlay UI. It does <b>not</b> own logging: application logs arrive through
 * {@link InspectorLogSink}, a regular {@code ILogSink} registered on the existing
 * {@code NativeLogger}. There is exactly one logging pipeline in the ecosystem.</p>
 *
 * <p>Typical host integration:</p>
 * <pre>
 * RuntimeInspector.get().init(context);          // once, after the runtime bootstrap
 * RuntimeInspector.get().beginStartupTrace();
 * RuntimeInspector.get().onProjectOpened(id, name);
 * RuntimeInspectorSheet sheet = RuntimeInspector.get().install(activity);
 * sheet.onResume();
 * </pre>
 */
public final class RuntimeInspector {

    private static final RuntimeInspector INSTANCE = new RuntimeInspector();

    private volatile InspectorSettings settings;
    private volatile boolean initialized;

    private RuntimeInspector() {
    }

    public static RuntimeInspector get() {
        return INSTANCE;
    }

    /** Idempotent. Safe to call from {@code Application.onCreate} or an Activity. */
    public synchronized void init(@Nullable Context context) {
        if (context == null) {
            return;
        }
        try {
            if (settings == null) {
                settings = new InspectorSettings(context);
            }
            RuntimeLogCollector.get().attachConfig(settings);
            ensureSinkAttached();
            initialized = true;
        } catch (Throwable ignored) {
            // A broken preference file must not stop the host from starting.
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Nullable
    public InspectorSettings getSettings() {
        return settings;
    }

    @NonNull
    public RuntimeLogCollector getCollector() {
        return RuntimeLogCollector.get();
    }

    @NonNull
    public RuntimeLogRepository getRepository() {
        return RuntimeLogCollector.get().getRepository();
    }

    // ==================== Shared logging source ====================

    /**
     * Makes sure the inspector sink is registered on the existing logger.
     *
     * <p>Called on every {@link #init(Context)} because {@code NativeLogger.init(...)} rebuilds
     * its sink list: if the host re-initialises the logger after the inspector, the sink would
     * otherwise be dropped and the inspector would silently stop receiving application logs.</p>
     *
     * @return true when a sink is attached after the call
     */
    public boolean ensureSinkAttached() {
        try {
            NativeLogger logger = NativeLogger.getInstance();
            if (logger == null) {
                return false;
            }
            List<ILogSink> sinks = logger.getSinks();
            for (ILogSink sink : sinks) {
                if (sink instanceof InspectorLogSink) {
                    return true;
                }
            }
            logger.addSink(new InspectorLogSink(RuntimeLogCollector.get()));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when the inspector sink is currently registered on the logger. */
    public boolean isSinkAttached() {
        try {
            NativeLogger logger = NativeLogger.getInstance();
            if (logger == null) {
                return false;
            }
            for (ILogSink sink : logger.getSinks()) {
                if (sink instanceof InspectorLogSink) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // Reported as "not attached".
        }
        return false;
    }

    // ==================== Session context ====================

    /**
     * Called when a project runtime starts. Establishes the project id every subsequent entry
     * inherits, and optionally clears the buffer so a new session starts clean.
     */
    public void onProjectOpened(String projectId, String projectName) {
        RuntimeLogCollector collector = RuntimeLogCollector.get();
        collector.setProjectId(projectId);
        InspectorSettings current = settings;
        if (current != null && current.isClearOnNewSession()) {
            collector.clear();
        }
        collector.project("opened " + safe(projectName) + " (" + safe(projectId) + ")");
    }

    public void onProjectClosed(String projectId) {
        RuntimeLogCollector collector = RuntimeLogCollector.get();
        collector.project("closed " + safe(projectId));
        collector.setProjectId("");
    }

    /** Records a startup checkpoint under the current startup correlation id. */
    public void startup(String checkpoint, String detail) {
        RuntimeLogCollector.get().startup(checkpoint, detail);
    }

    /** Starts a new startup trace. Call once, as early as possible in the launch path. */
    public String beginStartupTrace() {
        return RuntimeLogCollector.get().newStartupCorrelationId();
    }

    // ==================== UI ====================

    /**
     * Installs the inspector overlay into {@code activity}'s content root.
     *
     * <p>Idempotent per Activity: a second call returns the existing instance instead of
     * stacking a second overlay (which would double every listener).</p>
     */
    @Nullable
    public RuntimeInspectorSheet install(@Nullable Activity activity) {
        return RuntimeInspectorSheet.install(activity);
    }

    // ==================== Diagnostics ====================

    /** Number of entries currently held by the shared buffer. */
    public int getBufferedCount() {
        return getRepository().size();
    }

    /** Records a host-defined diagnostic entry through the same ingest gate. */
    public void record(@Nullable LogEntry entry) {
        RuntimeLogCollector.get().accept(entry);
    }

    /** Detaches the sink and drops the buffer. The singleton stays usable afterwards. */
    public synchronized void release() {
        try {
            NativeLogger logger = NativeLogger.getInstance();
            if (logger != null) {
                for (ILogSink sink : logger.getSinks()) {
                    if (sink instanceof InspectorLogSink) {
                        logger.removeSink(sink);
                        sink.dispose();
                    }
                }
            }
        } catch (Throwable ignored) {
            // Release must never throw.
        }
        RuntimeLogCollector.get().getRepository().removeAllListeners();
        RuntimeLogCollector.get().clear();
        initialized = false;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
