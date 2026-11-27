package com.roni.library.devtools.inspector;

import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.contracts.logger.LogSource;

/**
 * The subset of Inspector configuration the collector needs.
 *
 * <p>An interface rather than a direct dependency on {@link InspectorSettings} so the collector
 * stays free of Android imports and can be unit-tested on the JVM.</p>
 */
public interface InspectorConfig {

    /** Master switch. When false nothing is collected. */
    boolean isEnabled();

    /** Entries below this level are discarded at ingest. */
    LogLevel getMinLevel();

    /** Ring buffer size. */
    int getMaxEntries();

    /** Per-source capture switch (web console, bridge, network, performance). */
    boolean isSourceEnabled(LogSource source);

    /** When false, messages are stored verbatim. Defaults to true. */
    boolean isRedactionEnabled();
}
