package com.roni.library.devtools.inspector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.contracts.logger.LogSource;

import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that the inspector consumes the existing logging pipeline instead of creating a
 * second one.
 *
 * <p>The important property is <b>exactly once</b>: one entry written to the logger must produce
 * one buffered entry, with the same message, level and source. A duplicate would mean the
 * inspector re-logs what it receives; a missing entry would mean it bypasses the logger.</p>
 */
public class InspectorLogSinkTest {

    private static final class Config implements InspectorConfig {
        boolean enabled = true;
        LogLevel minLevel = LogLevel.VERBOSE;
        int maxEntries = 100;
        boolean redaction = true;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public LogLevel getMinLevel() {
            return minLevel;
        }

        @Override
        public int getMaxEntries() {
            return maxEntries;
        }

        @Override
        public boolean isSourceEnabled(LogSource source) {
            return true;
        }

        @Override
        public boolean isRedactionEnabled() {
            return redaction;
        }
    }

    private RuntimeLogCollector collector;
    private Config config;
    private InspectorLogSink sink;

    @Before
    public void setUp() {
        collector = RuntimeLogCollector.get();
        collector.clear();
        collector.setProjectId("");
        config = new Config();
        collector.attachConfig(config);
        sink = new InspectorLogSink(collector);
    }

    @Test
    public void oneLogEntryProducesExactlyOneBufferedEntry() {
        sink.write(new LogEntry.Builder()
                .level(LogLevel.INFO)
                .tag("AppLog")
                .message("project opened")
                .build());

        assertEquals(1, collector.getRepository().size());
        assertEquals("project opened",
                collector.getRepository().snapshot().get(0).getMessage());
    }

    @Test
    public void sinkPreservesLevelTagAndSource() {
        sink.write(new LogEntry.Builder()
                .level(LogLevel.WARN)
                .tag("Storage")
                .source(LogSource.STORAGE)
                .message("slow write")
                .build());

        LogEntry stored = collector.getRepository().snapshot().get(0);
        assertEquals(LogLevel.WARN, stored.getLevel());
        assertEquals("Storage", stored.getTag());
        assertEquals(LogSource.STORAGE, stored.getSource());
    }

    @Test
    public void sinkAssignsAMonotonicId() {
        sink.write(new LogEntry.Builder().message("a").build());
        sink.write(new LogEntry.Builder().message("b").build());

        LogEntry first = collector.getRepository().snapshot().get(0);
        LogEntry second = collector.getRepository().snapshot().get(1);
        assertTrue(second.getId() > first.getId());
    }

    @Test
    public void disabledSinkStopsForwarding() {
        sink.setEnabled(false);
        sink.write(new LogEntry.Builder().message("dropped").build());

        assertEquals(0, collector.getRepository().size());
        assertFalse(sink.isEnabled());
    }

    @Test
    public void disposedSinkStopsForwarding() {
        sink.dispose();
        sink.write(new LogEntry.Builder().message("dropped").build());

        assertEquals(0, collector.getRepository().size());
        assertTrue(sink.isDisposed());
    }

    @Test
    public void sinkNameIsStableForDuplicateDetection() {
        assertEquals(InspectorLogSink.NAME, sink.getName());
        assertEquals("InspectorLogSink", sink.getName());
    }

    @Test
    public void sinkAppliesTheSameGateAsTheCollector() {
        config.minLevel = LogLevel.WARN;

        sink.write(new LogEntry.Builder().level(LogLevel.DEBUG).message("debug").build());
        sink.write(new LogEntry.Builder().level(LogLevel.ERROR).message("error").build());

        assertEquals(1, collector.getRepository().size());
        assertEquals("error", collector.getRepository().snapshot().get(0).getMessage());
    }

    @Test
    public void sinkRedactsSecretsBeforeBuffering() {
        sink.write(new LogEntry.Builder()
                .level(LogLevel.INFO)
                .message("token=abc123secret")
                .build());

        String message = collector.getRepository().snapshot().get(0).getMessage();
        assertFalse(message.contains("abc123secret"));
        assertTrue(message.contains(LogRedactor.MASK));
    }

    @Test
    public void sinkNeverThrowsOnNull() {
        sink.write(null);
        assertEquals(0, collector.getRepository().size());
    }
}
