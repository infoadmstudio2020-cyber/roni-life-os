package com.roni.library.devtools.inspector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.contracts.logger.LogSource;

import org.junit.Test;

import java.util.List;

/**
 * Collector behaviour: the settings gate, redaction on ingest, and session context.
 *
 * <p>The collector is a process-wide singleton, so each test clears the buffer and re-attaches
 * a fresh config instead of relying on construction order.</p>
 */
public class RuntimeLogCollectorTest {

    /** Test double for {@link InspectorConfig}. */
    private static final class Config implements InspectorConfig {
        boolean enabled = true;
        LogLevel minLevel = LogLevel.VERBOSE;
        int maxEntries = 100;
        boolean web = true;
        boolean bridge = true;
        boolean network = true;
        boolean performance = true;
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
            switch (source) {
                case WEB:
                    return web;
                case BRIDGE:
                    return bridge;
                case NETWORK:
                    return network;
                case PERFORMANCE:
                    return performance;
                default:
                    return true;
            }
        }

        @Override
        public boolean isRedactionEnabled() {
            return redaction;
        }
    }

    private void nativeLog(LogLevel level, String tag, String message, Throwable throwable) {
        collector.accept(new LogEntry.Builder()
                .level(level).tag(tag).message(message).throwable(throwable).build());
    }

    private RuntimeLogCollector collector;
    private Config config;

    @Before
    public void setUp() {
        collector = RuntimeLogCollector.get();
        collector.clear();
        collector.setProjectId("");
        config = new Config();
        collector.attachConfig(config);
    }

    @Test
    public void disabledCollectorStoresNothing() {
        config.enabled = false;

        nativeLog(LogLevel.ERROR, "T", "boom", null);

        assertEquals(0, collector.getRepository().size());
    }

    @Test
    public void minimumLevelFiltersAtIngest() {
        config.minLevel = LogLevel.WARN;

        nativeLog(LogLevel.DEBUG, "T", "debug", null);
        nativeLog(LogLevel.INFO, "T", "info", null);
        nativeLog(LogLevel.WARN, "T", "warn", null);
        nativeLog(LogLevel.ERROR, "T", "error", null);

        List<LogEntry> entries = collector.getRepository().snapshot();
        assertEquals(2, entries.size());
        assertEquals("warn", entries.get(0).getMessage());
        assertEquals("error", entries.get(1).getMessage());
    }

    @Test
    public void disabledModuleIsNotCollected() {
        config.web = false;

        collector.web(LogLevel.INFO, "console.log", "app.js", 12);
        nativeLog(LogLevel.INFO, "T", "native still works", null);

        List<LogEntry> entries = collector.getRepository().snapshot();
        assertEquals(1, entries.size());
        assertEquals(LogSource.NATIVE, entries.get(0).getSource());
    }

    @Test
    public void secretsAreRedactedBeforeTheyEnterTheBuffer() {
        nativeLog(LogLevel.INFO, "Auth", "token=abc123secret", null);

        LogEntry entry = collector.getRepository().snapshot().get(0);
        assertFalse(entry.getMessage().contains("abc123secret"));
        assertTrue(entry.getMessage().contains(LogRedactor.MASK));
    }

    @Test
    public void redactionCanBeDisabledForLocalDebugging() {
        config.redaction = false;

        nativeLog(LogLevel.INFO, "Auth", "token=abc123secret", null);

        assertTrue(collector.getRepository().snapshot().get(0).getMessage()
                .contains("abc123secret"));
    }

    @Test
    public void entriesInheritTheCurrentProjectAndCorrelationId() {
        collector.setProjectId("project-a");
        String correlation = collector.newStartupCorrelationId();

        collector.startup("STARTUP_CONFIG", "splashEnabled=true");

        LogEntry entry = collector.getRepository().snapshot().get(0);
        assertEquals("project-a", entry.getProjectId());
        assertEquals(correlation, entry.getCorrelationId());
        assertEquals("STARTUP_CONFIG", entry.getTag());
        assertTrue(entry.getMessage().contains("splashEnabled=true"));
    }

    @Test
    public void bridgeRequestAndResponseShareTheRequestId() {
        collector.bridgeRequest("req-1", "ui.showToast", "UiHandler");
        collector.bridgeResponse("req-1", "ui.showToast", "UiHandler", 12L, true, null);

        List<LogEntry> entries = collector.getRepository().snapshot();
        assertEquals(2, entries.size());
        assertEquals("req-1", entries.get(0).getRequestId());
        assertEquals("req-1", entries.get(1).getRequestId());
        assertEquals(LogEntry.DIRECTION_WEB_TO_NATIVE, entries.get(0).getDirection());
        assertEquals(LogEntry.DIRECTION_NATIVE_TO_WEB, entries.get(1).getDirection());
        assertEquals(LogEntry.STATUS_SUCCESS, entries.get(1).getStatus());
        assertEquals(12L, entries.get(1).getDurationMs());
    }

    @Test
    public void failedBridgeCallIsAnErrorEntry() {
        collector.bridgeResponse("req-2", "storage.read", "StorageHandler", 5L, false,
                "NOT_FOUND");

        LogEntry entry = collector.getRepository().snapshot().get(0);
        assertEquals(LogLevel.ERROR, entry.getLevel());
        assertTrue(entry.isFailure());
        assertTrue(entry.getMessage().contains("NOT_FOUND"));
    }

    @Test
    public void rejectedBridgeCallIsRecordedAsRejected() {
        collector.bridgeRejected("req-3", "fs.delete", "ORIGIN_REJECTED");

        LogEntry entry = collector.getRepository().snapshot().get(0);
        assertEquals(LogEntry.STATUS_REJECTED, entry.getStatus());
        assertEquals(LogLevel.WARN, entry.getLevel());
    }

    @Test
    public void networkUrlIsRedacted() {
        collector.network("GET", "https://api.example.com/x?token=supersecret", 200, 30L, null);

        LogEntry entry = collector.getRepository().snapshot().get(0);
        assertFalse(entry.getMessage().contains("supersecret"));
        assertTrue(entry.getMessage().contains("GET"));
        assertTrue(entry.getMessage().contains("200"));
    }

    @Test
    public void networkFailureIsAnErrorEntry() {
        collector.network("POST", "https://api.example.com/y", 0, 1200L, "timeout");

        LogEntry entry = collector.getRepository().snapshot().get(0);
        assertEquals(LogLevel.ERROR, entry.getLevel());
        assertTrue(entry.isFailure());
        assertTrue(entry.getMessage().contains("timeout"));
    }

    @Test
    public void webConsoleMessageCarriesLocation() {
        collector.web(LogLevel.WARN, "deprecated API", "https://app.local/main.js", 42);

        LogEntry entry = collector.getRepository().snapshot().get(0);
        assertEquals(LogSource.WEB, entry.getSource());
        assertTrue(entry.getMessage().contains("deprecated API"));
        assertTrue(entry.getMessage().contains("main.js:42"));
    }

    @Test
    public void throwableProducesAStackTrace() {
        nativeLog(LogLevel.ERROR, "T", "crash",
                new IllegalStateException("bad state"));

        LogEntry entry = collector.getRepository().snapshot().get(0);
        assertNotNull(LogEntryFormatter.stackTrace(entry));
        assertTrue(LogEntryFormatter.stackTrace(entry).contains("IllegalStateException"));
    }

    @Test
    public void capacityFromConfigIsApplied() {
        config.maxEntries = 60;
        collector.attachConfig(config);

        assertEquals(60, collector.getRepository().getCapacity());
    }

    @Test
    public void collectorNeverThrowsOnHostileInput() {
        nativeLog(null, null, null, null);
        collector.web(null, null, null, -1);
        collector.bridgeRequest(null, null, null);
        collector.bridgeResponse(null, null, null, -1L, false, null);
        collector.network(null, null, -1, -1L, null);
        collector.startup(null, null);

        // Nothing crashed; whatever was accepted is still readable.
        assertNotNull(collector.getRepository().snapshot());
    }
}
