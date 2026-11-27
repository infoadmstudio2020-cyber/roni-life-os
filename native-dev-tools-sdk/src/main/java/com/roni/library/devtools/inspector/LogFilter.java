package com.roni.library.devtools.inspector;

import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.contracts.logger.LogSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Combined, live filter for the Inspector.
 *
 * <p>All active criteria are ANDed: severity, source, project, module, text, tag, request id,
 * error-only, failed-only and a time range can be combined freely. An unset criterion is
 * ignored rather than treated as "match nothing".</p>
 *
 * <p>Matching is pure and allocation-light so it can run over a few thousand entries on the
 * main thread without a visible stall; the UI additionally debounces text input.</p>
 */
public final class LogFilter {

    private EnumSet<LogLevel> levels;
    private EnumSet<LogSource> sources;
    private String projectId = "";
    private String module = "";
    private String text = "";
    private String tag = "";
    private String requestId = "";
    private boolean errorsOnly;
    private boolean failedOnly;
    private long fromTimestamp = 0L;
    private long toTimestamp = Long.MAX_VALUE;

    public LogFilter setLevels(EnumSet<LogLevel> levels) {
        this.levels = levels;
        return this;
    }

    /** Convenience for the level chip row: {@code null} or empty means "all". */
    public LogFilter setLevel(LogLevel level) {
        if (level == null) {
            this.levels = null;
        } else {
            this.levels = EnumSet.of(level);
        }
        return this;
    }

    public LogFilter setSources(EnumSet<LogSource> sources) {
        this.sources = sources;
        return this;
    }

    /** Convenience for the tab selection: {@code null} means "all sources". */
    public LogFilter setSource(LogSource source) {
        if (source == null) {
            this.sources = null;
        } else {
            this.sources = EnumSet.of(source);
        }
        return this;
    }

    public LogFilter setProjectId(String projectId) {
        this.projectId = normalize(projectId);
        return this;
    }

    public LogFilter setModule(String module) {
        this.module = normalize(module);
        return this;
    }

    public LogFilter setText(String text) {
        this.text = normalize(text);
        return this;
    }

    public LogFilter setTag(String tag) {
        this.tag = normalize(tag);
        return this;
    }

    public LogFilter setRequestId(String requestId) {
        this.requestId = normalize(requestId);
        return this;
    }

    public LogFilter setErrorsOnly(boolean errorsOnly) {
        this.errorsOnly = errorsOnly;
        return this;
    }

    public LogFilter setFailedOnly(boolean failedOnly) {
        this.failedOnly = failedOnly;
        return this;
    }

    public LogFilter setTimeRange(long fromTimestamp, long toTimestamp) {
        this.fromTimestamp = Math.max(0L, fromTimestamp);
        this.toTimestamp = toTimestamp > 0L ? toTimestamp : Long.MAX_VALUE;
        return this;
    }

    public boolean isErrorsOnly() {
        return errorsOnly;
    }

    public boolean isFailedOnly() {
        return failedOnly;
    }

    public String getText() {
        return text;
    }

    /** True when at least one criterion is active — used to show the "filtered" badge. */
    public boolean isActive() {
        return (levels != null && !levels.isEmpty())
                || (sources != null && !sources.isEmpty())
                || !projectId.isEmpty()
                || !module.isEmpty()
                || !text.isEmpty()
                || !tag.isEmpty()
                || !requestId.isEmpty()
                || errorsOnly
                || failedOnly
                || fromTimestamp > 0L
                || toTimestamp != Long.MAX_VALUE;
    }

    /** Resets every criterion. */
    public LogFilter reset() {
        levels = null;
        sources = null;
        projectId = "";
        module = "";
        text = "";
        tag = "";
        requestId = "";
        errorsOnly = false;
        failedOnly = false;
        fromTimestamp = 0L;
        toTimestamp = Long.MAX_VALUE;
        return this;
    }

    /** True when {@code entry} satisfies every active criterion. */
    public boolean matches(LogEntry entry) {
        if (entry == null) {
            return false;
        }
        if (levels != null && !levels.isEmpty() && !levels.contains(entry.getLevel())) {
            return false;
        }
        if (sources != null && !sources.isEmpty() && !sources.contains(entry.getSource())) {
            return false;
        }
        if (errorsOnly && entry.getLevel().getPriority() < LogLevel.ERROR.getPriority()) {
            return false;
        }
        if (failedOnly && !entry.isFailure()) {
            return false;
        }
        if (!projectId.isEmpty() && !entry.getProjectId().equalsIgnoreCase(projectId)) {
            return false;
        }
        if (!module.isEmpty() && !contains(entry.getModule(), module)) {
            return false;
        }
        if (!tag.isEmpty() && !contains(entry.getTag(), tag)) {
            return false;
        }
        if (!requestId.isEmpty() && !contains(entry.getRequestId(), requestId)) {
            return false;
        }
        if (entry.getTimestamp() < fromTimestamp || entry.getTimestamp() > toTimestamp) {
            return false;
        }
        return text.isEmpty() || matchesText(entry, text);
    }

    /** Applies the filter to a snapshot, preserving order. */
    public List<LogEntry> apply(List<LogEntry> input) {
        List<LogEntry> result = new ArrayList<>();
        if (input == null) {
            return result;
        }
        for (LogEntry entry : input) {
            if (matches(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Free-text search across message, tag, module, project, request id and correlation id. */
    private static boolean matchesText(LogEntry entry, String needle) {
        return contains(entry.getMessage(), needle)
                || contains(entry.getTag(), needle)
                || contains(entry.getModule(), needle)
                || contains(entry.getProjectId(), needle)
                || contains(entry.getRequestId(), needle)
                || contains(entry.getCorrelationId(), needle)
                || contains(entry.getSource().getLabel(), needle);
    }

    private static boolean contains(String haystack, String needle) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        return haystack.toLowerCase(Locale.US).contains(needle);
    }

    private static String normalize(String value) {
        return value != null ? value.trim().toLowerCase(Locale.US) : "";
    }
}
