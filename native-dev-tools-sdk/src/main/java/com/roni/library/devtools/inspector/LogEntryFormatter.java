package com.roni.library.devtools.inspector;

import com.roni.library.contracts.logger.LogEntry;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Renders a shared {@link LogEntry} for the inspector UI and for export.
 *
 * <p>This is a formatter, not a data model: the inspector stores the ecosystem's
 * {@code LogEntry} and only converts it to text/JSON/CSV at the edges. Keeping the rendering
 * here means the list row, the detail dialog and the exporter all agree on one format.</p>
 */
public final class LogEntryFormatter {

    private static final String TIME_PATTERN = "HH:mm:ss.SSS";

    private LogEntryFormatter() {
    }

    /** {@code HH:mm:ss.SSS} in the device time zone. */
    public static String timeLabel(LogEntry entry) {
        if (entry == null) {
            return "";
        }
        return new SimpleDateFormat(TIME_PATTERN, Locale.US)
                .format(new Date(entry.getTimestamp()));
    }

    /** Stack trace of the entry's throwable, or an empty string. */
    public static String stackTrace(LogEntry entry) {
        if (entry == null || entry.getThrowable() == null) {
            return "";
        }
        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);
        entry.getThrowable().printStackTrace(printer);
        printer.flush();
        return writer.toString();
    }

    /** One-line rendering used by the list row. */
    public static String headline(LogEntry entry) {
        if (entry == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(timeLabel(entry))
                .append(' ')
                .append(entry.getLevel().getShortName())
                .append('/')
                .append(entry.getSource().getLabel());
        if (!entry.getModule().isEmpty()) {
            builder.append(' ').append(entry.getModule());
        }
        builder.append(" — ").append(entry.getMessage());
        return builder.toString();
    }

    /** Complete entry with timestamp and metadata — "copy complete log entry". */
    public static String toText(LogEntry entry) {
        if (entry == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(timeLabel(entry)).append("] ")
                .append(entry.getLevel().name()).append(' ')
                .append(entry.getSource().name());
        if (!entry.getModule().isEmpty()) {
            builder.append(" module=").append(entry.getModule());
        }
        if (!entry.getProjectId().isEmpty()) {
            builder.append(" project=").append(entry.getProjectId());
        }
        if (!entry.getTag().isEmpty()) {
            builder.append(" tag=").append(entry.getTag());
        }
        if (!entry.getCorrelationId().isEmpty()) {
            builder.append(" corr=").append(entry.getCorrelationId());
        }
        if (!entry.getRequestId().isEmpty()) {
            builder.append(" req=").append(entry.getRequestId());
        }
        if (!entry.getDirection().isEmpty()) {
            builder.append(" dir=").append(entry.getDirection());
        }
        if (!entry.getStatus().isEmpty()) {
            builder.append(" status=").append(entry.getStatus());
        }
        if (entry.hasDuration()) {
            builder.append(" duration=").append(entry.getDurationMs()).append("ms");
        }
        builder.append('\n').append(entry.getMessage());
        String stack = stackTrace(entry);
        if (!stack.isEmpty()) {
            builder.append('\n').append(stack);
        }
        return builder.toString();
    }

    /** Structured JSON object. Escaping is hand-rolled so the type stays dependency-free. */
    public static String toJson(LogEntry entry) {
        if (entry == null) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder(256);
        builder.append('{');
        appendField(builder, "id", String.valueOf(entry.getId())).append(',');
        appendField(builder, "timestamp", String.valueOf(entry.getTimestamp())).append(',');
        appendField(builder, "time", timeLabel(entry)).append(',');
        appendField(builder, "severity", entry.getLevel().name()).append(',');
        appendField(builder, "source", entry.getSource().name()).append(',');
        appendField(builder, "module", entry.getModule()).append(',');
        appendField(builder, "projectId", entry.getProjectId()).append(',');
        appendField(builder, "tag", entry.getTag()).append(',');
        appendField(builder, "message", entry.getMessage()).append(',');
        appendField(builder, "correlationId", entry.getCorrelationId()).append(',');
        appendField(builder, "requestId", entry.getRequestId()).append(',');
        appendField(builder, "direction", entry.getDirection()).append(',');
        appendField(builder, "status", entry.getStatus()).append(',');
        builder.append("\"durationMs\":")
                .append(entry.hasDuration() ? String.valueOf(entry.getDurationMs()) : "null");
        String stack = stackTrace(entry);
        if (!stack.isEmpty()) {
            builder.append(',');
            appendField(builder, "stackTrace", stack);
        }
        builder.append('}');
        return builder.toString();
    }

    /** One CSV record, already quoted. */
    public static String toCsvRow(LogEntry entry) {
        if (entry == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(160);
        builder.append(csv(String.valueOf(entry.getId()))).append(',')
                .append(csv(String.valueOf(entry.getTimestamp()))).append(',')
                .append(csv(timeLabel(entry))).append(',')
                .append(csv(entry.getLevel().name())).append(',')
                .append(csv(entry.getSource().name())).append(',')
                .append(csv(entry.getModule())).append(',')
                .append(csv(entry.getProjectId())).append(',')
                .append(csv(entry.getTag())).append(',')
                .append(csv(entry.getCorrelationId())).append(',')
                .append(csv(entry.getRequestId())).append(',')
                .append(csv(entry.getDirection())).append(',')
                .append(csv(entry.getStatus())).append(',')
                .append(csv(entry.hasDuration() ? String.valueOf(entry.getDurationMs()) : ""))
                .append(',')
                .append(csv(entry.getMessage())).append(',')
                .append(csv(stackTrace(entry)));
        return builder.toString();
    }

    /** Column header matching {@link #toCsvRow(LogEntry)}. */
    public static String csvHeader() {
        return "id,timestamp,time,severity,source,module,projectId,tag,correlationId,"
                + "requestId,direction,status,durationMs,message,stackTrace";
    }

    // ==================== Helpers ====================

    private static StringBuilder appendField(StringBuilder builder, String name, String value) {
        builder.append('"').append(name).append("\":\"").append(escape(value)).append('"');
        return builder;
    }

    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(Locale.US, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        return out.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "\"\"";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
