package com.roni.library.devtools.inspector;

import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.logger.sink.ILogSink;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The bridge between the existing log library and the Runtime Inspector.
 *
 * <p>This is the only place where application logs enter the inspector. It is a regular
 * {@link ILogSink} registered on {@code NativeLogger}, so the inspector consumes the ecosystem's
 * single logging source instead of maintaining a parallel pipeline: every entry the logger
 * accepts is fanned out here exactly once, and nothing is logged twice.</p>
 *
 * <p>The sink never throws and never blocks: a diagnostics failure must not be able to break a
 * log call.</p>
 */
public final class InspectorLogSink implements ILogSink {

    /** Stable sink name, used to detect an already-attached sink. */
    public static final String NAME = "InspectorLogSink";

    private final RuntimeLogCollector collector;
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public InspectorLogSink(RuntimeLogCollector collector) {
        this.collector = collector != null ? collector : RuntimeLogCollector.get();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void write(LogEntry entry) {
        if (entry == null || !enabled.get() || disposed.get()) {
            return;
        }
        try {
            collector.accept(entry);
        } catch (Throwable ignored) {
            // Diagnostics must never break the logger.
        }
    }

    @Override
    public void flush() {
        // Nothing is buffered here; the repository is the buffer.
    }

    @Override
    public boolean isEnabled() {
        return enabled.get() && !disposed.get();
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    @Override
    public void dispose() {
        disposed.set(true);
        enabled.set(false);
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}
