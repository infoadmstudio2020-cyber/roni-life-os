package com.roni.library.devtools.inspector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.contracts.logger.LogSource;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core Inspector behaviour: ring buffer, filtering, redaction and export.
 *
 * <p>These are the parts that must be correct regardless of the UI, so they are covered
 * directly on the JVM without an Android device.</p>
 */
public class InspectorCoreTest {

    // ==================== Ring buffer ====================

    @Test
    public void repositoryEvictsOldestWhenFull() {
        RuntimeLogRepository repository = new RuntimeLogRepository(50);
        for (int i = 0; i < 120; i++) {
            repository.add(entry(i, LogLevel.INFO, LogSource.NATIVE, "m" + i));
        }

        assertEquals(50, repository.size());
        assertEquals(70, repository.getDroppedCount());
        assertEquals(120, repository.getTotalCount());

        List<LogEntry> snapshot = repository.snapshot();
        assertEquals(50, snapshot.size());
        // The oldest surviving entry is #70 (0-based), the newest is #119.
        assertEquals("m70", snapshot.get(0).getMessage());
        assertEquals("m119", snapshot.get(49).getMessage());
    }

    @Test
    public void shrinkingCapacityEvictsImmediately() {
        RuntimeLogRepository repository = new RuntimeLogRepository(200);
        for (int i = 0; i < 200; i++) {
            repository.add(entry(i, LogLevel.INFO, LogSource.NATIVE, "m" + i));
        }

        repository.setCapacity(50);

        assertEquals(50, repository.size());
        assertEquals("m150", repository.snapshot().get(0).getMessage());
    }

    @Test
    public void capacityIsClampedToSafeBounds() {
        RuntimeLogRepository repository = new RuntimeLogRepository(1);
        assertEquals(50, repository.getCapacity());

        repository.setCapacity(Integer.MAX_VALUE);
        assertEquals(RuntimeLogRepository.MAX_CAPACITY, repository.getCapacity());
    }

    @Test
    public void recentReturnsNewestEntriesInOrder() {
        RuntimeLogRepository repository = new RuntimeLogRepository(100);
        for (int i = 0; i < 10; i++) {
            repository.add(entry(i, LogLevel.INFO, LogSource.NATIVE, "m" + i));
        }

        List<LogEntry> recent = repository.recent(3);
        assertEquals(3, recent.size());
        assertEquals("m7", recent.get(0).getMessage());
        assertEquals("m9", recent.get(2).getMessage());
    }

    @Test
    public void listenersAreNotifiedAndRemoved() {
        RuntimeLogRepository repository = new RuntimeLogRepository(10);
        final AtomicInteger added = new AtomicInteger();
        final AtomicInteger cleared = new AtomicInteger();
        RuntimeLogRepository.Listener listener = new RuntimeLogRepository.Listener() {
            @Override
            public void onLogAdded(LogEntry entry) {
                added.incrementAndGet();
            }

            @Override
            public void onLogsCleared() {
                cleared.incrementAndGet();
            }
        };

        repository.addListener(listener);
        repository.addListener(listener); // duplicate registration must be ignored
        repository.add(entry(1, LogLevel.INFO, LogSource.NATIVE, "a"));
        repository.clear();

        assertEquals(1, added.get());
        assertEquals(1, cleared.get());
        assertEquals(1, repository.getListenerCount());

        repository.removeListener(listener);
        repository.add(entry(2, LogLevel.INFO, LogSource.NATIVE, "b"));
        assertEquals(1, added.get());
        assertEquals(0, repository.getListenerCount());
    }

    @Test
    public void aThrowingListenerDoesNotBreakCollection() {
        RuntimeLogRepository repository = new RuntimeLogRepository(10);
        repository.addListener(new RuntimeLogRepository.Listener() {
            @Override
            public void onLogAdded(LogEntry entry) {
                throw new IllegalStateException("boom");
            }

            @Override
            public void onLogsCleared() {
                throw new IllegalStateException("boom");
            }
        });

        assertTrue(repository.add(entry(1, LogLevel.INFO, LogSource.NATIVE, "a")));
        assertEquals(1, repository.size());
    }

    @Test
    public void concurrentWritersStayWithinCapacity() throws Exception {
        final RuntimeLogRepository repository = new RuntimeLogRepository(100);
        final int threads = 8;
        final int perThread = 500;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            final int threadIndex = t;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) {
                            repository.add(entry(i, LogLevel.DEBUG, LogSource.WEB,
                                    "t" + threadIndex + "-" + i));
                        }
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }
            });
        }

        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(100, repository.size());
        assertEquals(threads * perThread, repository.getTotalCount());
        assertEquals(threads * perThread - 100, repository.getDroppedCount());
    }

    // ==================== Filtering ====================

    @Test
    public void filterCombinesSeveritySourceAndText() {
        List<LogEntry> input = new ArrayList<>();
        input.add(entry(1, LogLevel.ERROR, LogSource.WEB, "TypeError: x is undefined"));
        input.add(entry(2, LogLevel.INFO, LogSource.WEB, "page ready"));
        input.add(entry(3, LogLevel.ERROR, LogSource.NATIVE, "TypeError in native"));
        input.add(entry(4, LogLevel.WARN, LogSource.BRIDGE, "slow call"));

        LogFilter filter = new LogFilter()
                .setLevel(LogLevel.ERROR)
                .setSource(LogSource.WEB)
                .setText("typeerror");

        List<LogEntry> result = filter.apply(input);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    public void filterErrorsOnlyExcludesWarnings() {
        List<LogEntry> input = new ArrayList<>();
        input.add(entry(1, LogLevel.WARN, LogSource.NATIVE, "warn"));
        input.add(entry(2, LogLevel.ERROR, LogSource.NATIVE, "error"));
        input.add(entry(3, LogLevel.FATAL, LogSource.NATIVE, "fatal"));

        List<LogEntry> result = new LogFilter().setErrorsOnly(true).apply(input);

        assertEquals(2, result.size());
    }

    @Test
    public void filterFailedOnlyMatchesFailedAndRejectedBridgeCalls() {
        List<LogEntry> input = new ArrayList<>();
        input.add(bridgeEntry(1, LogEntry.STATUS_SUCCESS));
        input.add(bridgeEntry(2, LogEntry.STATUS_FAILED));
        input.add(bridgeEntry(3, LogEntry.STATUS_REJECTED));

        List<LogEntry> result = new LogFilter().setFailedOnly(true).apply(input);

        assertEquals(2, result.size());
    }

    @Test
    public void filterMatchesRequestIdAndCorrelationId() {
        LogEntry entry = new LogEntry.Builder()
                .id(1)
                .level(LogLevel.INFO)
                .source(LogSource.BRIDGE)
                .requestId("req-42")
                .correlationId("corr-7")
                .message("call")
                .build();

        assertTrue(new LogFilter().setRequestId("req-42").matches(entry));
        assertTrue(new LogFilter().setText("corr-7").matches(entry));
        assertFalse(new LogFilter().setRequestId("req-99").matches(entry));
    }

    @Test
    public void filterRespectsProjectIsolation() {
        LogEntry a = new LogEntry.Builder()
                .id(1).level(LogLevel.INFO).source(LogSource.WEB)
                .projectId("project-a").message("hello").build();
        LogEntry b = new LogEntry.Builder()
                .id(2).level(LogLevel.INFO).source(LogSource.WEB)
                .projectId("project-b").message("hello").build();

        LogFilter filter = new LogFilter().setProjectId("project-a");

        assertTrue(filter.matches(a));
        assertFalse(filter.matches(b));
    }

    @Test
    public void filterRespectsTimeRange() {
        LogEntry entry = new LogEntry.Builder()
                .id(1).timestamp(1000L).level(LogLevel.INFO)
                .source(LogSource.NATIVE).message("x").build();

        assertTrue(new LogFilter().setTimeRange(500L, 1500L).matches(entry));
        assertFalse(new LogFilter().setTimeRange(1500L, 2000L).matches(entry));
    }

    @Test
    public void emptyFilterMatchesEverythingAndReportsInactive() {
        LogFilter filter = new LogFilter();
        assertFalse(filter.isActive());
        assertTrue(filter.matches(entry(1, LogLevel.VERBOSE, LogSource.NATIVE, "x")));
    }

    @Test
    public void resetClearsEveryCriterion() {
        LogFilter filter = new LogFilter()
                .setLevel(LogLevel.ERROR)
                .setSource(LogSource.WEB)
                .setText("abc")
                .setErrorsOnly(true);

        assertTrue(filter.isActive());
        filter.reset();
        assertFalse(filter.isActive());
        assertTrue(filter.matches(entry(1, LogLevel.VERBOSE, LogSource.NATIVE, "x")));
    }

    // ==================== Redaction ====================

    @Test
    public void redactsPasswordAndTokenAssignments() {
        String input = "login password=hunter2 token: abc123secret";
        String output = LogRedactor.redact(input);

        assertFalse(output.contains("hunter2"));
        assertFalse(output.contains("abc123secret"));
        assertTrue(output.contains("password="));
        assertTrue(output.contains(LogRedactor.MASK));
    }

    @Test
    public void redactsBearerCredentials() {
        String output = LogRedactor.redact("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig");
        assertFalse(output.contains("eyJhbGciOiJIUzI1NiJ9"));
    }

    @Test
    public void redactsSensitiveQueryParametersAndFragment() {
        String output = LogRedactor.redactUrl(
                "https://api.example.com/v1/data?token=abc123&page=2#access_token=zzz");

        assertFalse(output.contains("abc123"));
        assertFalse(output.contains("zzz"));
        assertTrue(output.contains("page=2"));
    }

    @Test
    public void keepsOrdinaryTextIntact() {
        String input = "Project demo loaded in 128ms";
        assertEquals(input, LogRedactor.redact(input));
    }

    @Test
    public void detectsSensitiveKeys() {
        assertTrue(LogRedactor.isSensitiveKey("access_token"));
        assertTrue(LogRedactor.isSensitiveKey("Password"));
        assertTrue(LogRedactor.isSensitiveKey("apiKey"));
        assertFalse(LogRedactor.isSensitiveKey("projectId"));
    }

    // ==================== Export ====================

    @Test
    public void exportsTextWithMetadataHeader() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, LogLevel.ERROR, LogSource.WEB, "boom"));

        String output = LogExporter.export(entries, LogExporter.Format.TXT,
                new LogExporter.Metadata()
                        .appVersion("1.2.3")
                        .androidVersion("14")
                        .deviceModel("Pixel")
                        .projectId("demo"));

        assertTrue(output.contains("App version   : 1.2.3"));
        assertTrue(output.contains("Android       : 14"));
        assertTrue(output.contains("Project       : demo"));
        assertTrue(output.contains("Log count     : 1"));
        assertTrue(output.contains("boom"));
    }

    @Test
    public void exportsValidJsonShape() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, LogLevel.INFO, LogSource.NATIVE, "hello \"world\""));

        String output = LogExporter.export(entries, LogExporter.Format.JSON,
                new LogExporter.Metadata().appVersion("1.0"));

        assertTrue(output.trim().startsWith("{"));
        assertTrue(output.trim().endsWith("}"));
        assertTrue(output.contains("\"metadata\""));
        assertTrue(output.contains("\"entries\""));
        // The quote inside the message must be escaped, not emitted raw.
        assertTrue(output.contains("hello \\\"world\\\""));
    }

    @Test
    public void exportsCsvWithHeaderAndQuotedFields() {
        List<LogEntry> entries = new ArrayList<>();
        entries.add(entry(1, LogLevel.WARN, LogSource.BRIDGE, "a,b"));

        String output = LogExporter.export(entries, LogExporter.Format.CSV,
                new LogExporter.Metadata().projectId("demo"));

        assertTrue(output.contains(LogEntryFormatter.csvHeader()));
        assertTrue(output.contains("\"a,b\""));
        assertTrue(output.contains("# projectId=demo"));
    }

    @Test
    public void exportRedactsSecretsEvenWhenTheEntryWasNotRedactedOnIngest() {
        LogEntry raw = new LogEntry.Builder()
                .id(1)
                .level(LogLevel.INFO)
                .source(LogSource.NETWORK)
                .message("GET https://api.example.com/x?token=supersecret")
                .build();

        List<LogEntry> entries = new ArrayList<>();
        entries.add(raw);

        String output = LogExporter.export(entries, LogExporter.Format.TXT,
                new LogExporter.Metadata());

        assertFalse(output.contains("supersecret"));
        // The live entry is untouched — only the exported copy is redacted.
        assertTrue(raw.getMessage().contains("supersecret"));
    }

    @Test
    public void suggestedFileNameUsesFormatExtension() {
        String name = LogExporter.suggestedFileName(LogExporter.Format.JSON, 0L);
        assertTrue(name.startsWith("roni-inspector-"));
        assertTrue(name.endsWith(".json"));
    }

    @Test
    public void exportHandlesNullAndEmptyInput() {
        assertNotNull(LogExporter.export(null, LogExporter.Format.TXT, null));
        assertNotNull(LogExporter.export(new ArrayList<LogEntry>(),
                LogExporter.Format.JSON, null));
        assertNotNull(LogExporter.export(new ArrayList<LogEntry>(),
                LogExporter.Format.CSV, null));
    }

    // ==================== Entry rendering ====================

    @Test
    public void entryRendersMetadataInTextAndJson() {
        LogEntry entry = new LogEntry.Builder()
                .id(7)
                .timestamp(1700000000000L)
                .level(LogLevel.ERROR)
                .source(LogSource.BRIDGE)
                .module("ui")
                .projectId("demo")
                .tag("NativeBridge")
                .message("call failed")
                .correlationId("corr-1")
                .requestId("req-1")
                .direction(LogEntry.DIRECTION_WEB_TO_NATIVE)
                .status(LogEntry.STATUS_FAILED)
                .durationMs(42L)
                .build();

        String text = LogEntryFormatter.toText(entry);
        assertTrue(text.contains("ERROR"));
        assertTrue(text.contains("BRIDGE"));
        assertTrue(text.contains("module=ui"));
        assertTrue(text.contains("req=req-1"));
        assertTrue(text.contains("duration=42ms"));
        assertTrue(text.contains("call failed"));

        String json = LogEntryFormatter.toJson(entry);
        assertTrue(json.contains("\"severity\":\"ERROR\""));
        assertTrue(json.contains("\"durationMs\":42"));
        assertTrue(json.contains("\"direction\":\"WEB_TO_NATIVE\""));

        assertTrue(entry.isFailure());
        assertTrue(entry.hasDuration());
        assertTrue(entry.getLevel().getPriority() >= LogLevel.WARN.getPriority());
    }

    @Test
    public void entryWithoutDurationReportsNoDuration() {
        LogEntry entry = entry(1, LogLevel.INFO, LogSource.NATIVE, "x");
        assertFalse(entry.hasDuration());
        assertTrue(LogEntryFormatter.toJson(entry).contains("\"durationMs\":null"));
    }

    @Test
    public void severityOrderingDrivesMinimumLevel() {
        assertTrue(LogLevel.ERROR.getPriority() >= LogLevel.WARN.getPriority());
        assertFalse(LogLevel.DEBUG.getPriority() >= LogLevel.INFO.getPriority());
        assertTrue(LogLevel.FATAL.getPriority() >= LogLevel.FATAL.getPriority());
        assertEquals(LogLevel.ERROR, LogLevel.fromAndroidPriority(6));
        assertEquals(LogLevel.WARN, LogLevel.fromAndroidPriority(5));
    }

    @Test
    public void severityAndSourceParseSafely() {
        assertEquals(LogLevel.WARN, LogLevel.fromString("warn", LogLevel.INFO));
        assertEquals(LogLevel.INFO, LogLevel.fromString("nonsense", LogLevel.INFO));
        assertEquals(LogLevel.INFO, LogLevel.fromString(null, LogLevel.INFO));
        assertEquals(LogSource.BRIDGE, LogSource.parse("bridge"));
        assertEquals(LogSource.NATIVE, LogSource.parse(null));
    }

    @Test
    public void enumSetsAreUsableForMultiSelectFilters() {
        EnumSet<LogLevel> severities = EnumSet.of(LogLevel.WARN, LogLevel.ERROR);
        LogFilter filter = new LogFilter().setLevels(severities);

        assertTrue(filter.matches(entry(1, LogLevel.ERROR, LogSource.NATIVE, "x")));
        assertFalse(filter.matches(entry(2, LogLevel.INFO, LogSource.NATIVE, "x")));
    }

    @Test
    public void nullEntryNeverMatches() {
        assertFalse(new LogFilter().matches(null));
        assertNull(LogRedactor.redact(null));
    }

    // ==================== Helpers ====================

    private static LogEntry entry(long id, LogLevel severity, LogSource source,
                                         String message) {
        return new LogEntry.Builder()
                .id(id)
                .level(severity)
                .source(source)
                .message(message)
                .build();
    }

    private static LogEntry bridgeEntry(long id, String status) {
        return new LogEntry.Builder()
                .id(id)
                .level(LogLevel.INFO)
                .source(LogSource.BRIDGE)
                .status(status)
                .message("bridge")
                .build();
    }
}
