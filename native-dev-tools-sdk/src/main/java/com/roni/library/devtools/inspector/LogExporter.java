package com.roni.library.devtools.inspector;

import com.roni.library.contracts.logger.LogEntry;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Renders a log selection as TXT, JSON or CSV, with a metadata header.
 *
 * <p>Redaction is applied again here even though the collector already redacts on ingest. The
 * second pass is deliberate: export is the one path where data leaves the device, so it must
 * not depend on a single upstream guarantee. The live entries are never mutated — only the
 * rendered output is masked.</p>
 */
public final class LogExporter {

    /** Supported export formats. */
    public enum Format {
        TXT("txt", "text/plain"),
        JSON("json", "application/json"),
        CSV("csv", "text/csv");

        private final String extension;
        private final String mimeType;

        Format(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }

        public String getExtension() {
            return extension;
        }

        public String getMimeType() {
            return mimeType;
        }

        public static Format parse(String value) {
            if (value != null) {
                String normalized = value.trim().toUpperCase(Locale.US);
                for (Format format : values()) {
                    if (format.name().equals(normalized)) {
                        return format;
                    }
                }
            }
            return TXT;
        }
    }

    /** Header information included in every export. */
    public static final class Metadata {
        private String appVersion = "";
        private String sdkVersion = "";
        private String androidVersion = "";
        private String deviceModel = "";
        private String projectId = "";
        private long exportedAt = System.currentTimeMillis();
        private int logCount;

        public Metadata appVersion(String value) {
            this.appVersion = safe(value);
            return this;
        }

        public Metadata sdkVersion(String value) {
            this.sdkVersion = safe(value);
            return this;
        }

        public Metadata androidVersion(String value) {
            this.androidVersion = safe(value);
            return this;
        }

        public Metadata deviceModel(String value) {
            this.deviceModel = safe(value);
            return this;
        }

        public Metadata projectId(String value) {
            this.projectId = safe(value);
            return this;
        }

        public Metadata exportedAt(long value) {
            this.exportedAt = value;
            return this;
        }

        public Metadata logCount(int value) {
            this.logCount = value;
            return this;
        }

        public String getProjectId() {
            return projectId;
        }

        public int getLogCount() {
            return logCount;
        }

        public String getExportedAtLabel() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date(exportedAt));
        }
    }

    private LogExporter() {
    }

    /** Renders {@code entries} in {@code format}. Never returns {@code null}. */
    public static String export(List<LogEntry> entries, Format format, Metadata metadata) {
        Format target = format != null ? format : Format.TXT;
        Metadata meta = metadata != null ? metadata : new Metadata();
        int count = entries != null ? entries.size() : 0;
        meta.logCount(count);

        switch (target) {
            case JSON:
                return toJson(entries, meta);
            case CSV:
                return toCsv(entries, meta);
            default:
                return toText(entries, meta);
        }
    }

    /** Suggested file name, e.g. {@code roni-inspector-20261118-031500.txt}. */
    public static String suggestedFileName(Format format, long timestamp) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date(timestamp));
        return "roni-inspector-" + stamp + "." + (format != null ? format.getExtension() : "txt");
    }

    // ==================== Formats ====================

    private static String toText(List<LogEntry> entries, Metadata meta) {
        StringBuilder builder = new StringBuilder(512);
        builder.append("RONiLiFEOS Runtime Inspector — log export\n");
        appendTextMetadata(builder, meta);
        builder.append("------------------------------------------------------------\n");
        if (entries != null) {
            for (LogEntry entry : entries) {
                builder.append(LogRedactor.redact(LogEntryFormatter.toText(entry))).append('\n');
            }
        }
        return builder.toString();
    }

    private static void appendTextMetadata(StringBuilder builder, Metadata meta) {
        builder.append("App version   : ").append(orUnknown(meta.appVersion)).append('\n');
        builder.append("SDK version   : ").append(orUnknown(meta.sdkVersion)).append('\n');
        builder.append("Android       : ").append(orUnknown(meta.androidVersion)).append('\n');
        builder.append("Device        : ").append(orUnknown(meta.deviceModel)).append('\n');
        builder.append("Project       : ").append(orUnknown(meta.projectId)).append('\n');
        builder.append("Exported at   : ").append(meta.getExportedAtLabel()).append('\n');
        builder.append("Log count     : ").append(meta.logCount).append('\n');
    }

    private static String toJson(List<LogEntry> entries, Metadata meta) {
        StringBuilder builder = new StringBuilder(1024);
        builder.append("{\n  \"metadata\": {");
        builder.append("\"appVersion\":\"").append(json(meta.appVersion)).append("\",");
        builder.append("\"sdkVersion\":\"").append(json(meta.sdkVersion)).append("\",");
        builder.append("\"androidVersion\":\"").append(json(meta.androidVersion)).append("\",");
        builder.append("\"deviceModel\":\"").append(json(meta.deviceModel)).append("\",");
        builder.append("\"projectId\":\"").append(json(meta.projectId)).append("\",");
        builder.append("\"exportedAt\":\"").append(json(meta.getExportedAtLabel())).append("\",");
        builder.append("\"logCount\":").append(meta.logCount);
        builder.append("},\n  \"entries\": [");
        if (entries != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append("\n    ")
                        .append(LogRedactor.redact(LogEntryFormatter.toJson(entries.get(i))));
            }
        }
        builder.append("\n  ]\n}\n");
        return builder.toString();
    }

    private static String toCsv(List<LogEntry> entries, Metadata meta) {
        StringBuilder builder = new StringBuilder(1024);
        // Metadata as comment lines keeps the CSV parseable by a spreadsheet while still
        // carrying the report header the requirement asks for.
        builder.append("# RONiLiFEOS Runtime Inspector log export\n");
        builder.append("# appVersion=").append(meta.appVersion).append('\n');
        builder.append("# sdkVersion=").append(meta.sdkVersion).append('\n');
        builder.append("# androidVersion=").append(meta.androidVersion).append('\n');
        builder.append("# deviceModel=").append(meta.deviceModel).append('\n');
        builder.append("# projectId=").append(meta.projectId).append('\n');
        builder.append("# exportedAt=").append(meta.getExportedAtLabel()).append('\n');
        builder.append("# logCount=").append(meta.logCount).append('\n');
        builder.append(LogEntryFormatter.csvHeader()).append('\n');
        if (entries != null) {
            for (LogEntry entry : entries) {
                builder.append(LogRedactor.redact(LogEntryFormatter.toCsvRow(entry))).append('\n');
            }
        }
        return builder.toString();
    }

    // ==================== Helpers ====================

    private static String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String orUnknown(String value) {
        return value == null || value.isEmpty() ? "unknown" : value;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
