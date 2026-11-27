package com.roni.library.contracts.logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Serializable;

/**
 * Standard structured log entry model.
 *
 * <p>This is the single log data model of the ecosystem. The Runtime Inspector does not declare
 * its own entry type: it stores {@code LogEntry} instances and reads the optional diagnostic
 * dimensions below ({@link #getSource()}, {@link #getModule()}, {@link #getRequestId()},
 * {@link #getDirection()}, {@link #getStatus()}, {@link #getDurationMs()},
 * {@link #getProjectId()}). Those fields default to neutral values, so every existing producer
 * and sink keeps working unchanged.</p>
 *
 * @author infoadmstudio2020-cyber
 */
public final class LogEntry implements Serializable {

    private static final long serialVersionUID = 2L;

    /** Sentinel for "duration not measured". */
    public static final long NO_DURATION = -1L;

    /** Direction of a bridge message. */
    public static final String DIRECTION_WEB_TO_NATIVE = "WEB_TO_NATIVE";
    public static final String DIRECTION_NATIVE_TO_WEB = "NATIVE_TO_WEB";

    /** Outcome of a bridge or network operation. */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_REJECTED = "REJECTED";

    private final long id;
    private final long timestamp;
    private final LogLevel level;
    private final String tag;
    private final String message;
    private final String threadName;
    private final Throwable throwable;
    private final String correlationId;
    private final LogSource source;
    private final String module;
    private final String projectId;
    private final String requestId;
    private final String direction;
    private final String status;
    private final long durationMs;

    private LogEntry(Builder builder) {
        this.id = builder.id;
        this.timestamp = builder.timestamp > 0 ? builder.timestamp : System.currentTimeMillis();
        this.level = builder.level != null ? builder.level : LogLevel.INFO;
        this.tag = builder.tag != null ? builder.tag : "NativeRuntime";
        this.message = builder.message != null ? builder.message : "";
        this.threadName = builder.threadName != null
                ? builder.threadName : Thread.currentThread().getName();
        this.throwable = builder.throwable;
        this.correlationId = builder.correlationId != null ? builder.correlationId : "";
        this.source = builder.source != null ? builder.source : LogSource.NATIVE;
        this.module = builder.module != null ? builder.module : "";
        this.projectId = builder.projectId != null ? builder.projectId : "";
        this.requestId = builder.requestId != null ? builder.requestId : "";
        this.direction = builder.direction != null ? builder.direction : "";
        this.status = builder.status != null ? builder.status : "";
        this.durationMs = builder.durationMs;
    }

    public LogEntry(long timestamp,
                    @NonNull LogLevel level,
                    @NonNull String tag,
                    @NonNull String message,
                    @Nullable String threadName,
                    @Nullable Throwable throwable,
                    @Nullable String correlationId) {
        this(new Builder()
                .timestamp(timestamp)
                .level(level)
                .tag(tag)
                .message(message)
                .threadName(threadName)
                .throwable(throwable)
                .correlationId(correlationId));
    }

    public LogEntry(@NonNull LogLevel level, @NonNull String tag, @NonNull String message) {
        this(new Builder().level(level).tag(tag).message(message));
    }

    /** Record identity assigned by the consumer (the inspector assigns it at ingest). */
    public long getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @NonNull
    public LogLevel getLevel() {
        return level;
    }

    @NonNull
    public String getTag() {
        return tag;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @NonNull
    public String getThreadName() {
        return threadName;
    }

    @Nullable
    public Throwable getThrowable() {
        return throwable;
    }

    @NonNull
    public String getCorrelationId() {
        return correlationId;
    }

    @NonNull
    public LogSource getSource() {
        return source;
    }

    @NonNull
    public String getModule() {
        return module;
    }

    @NonNull
    public String getProjectId() {
        return projectId;
    }

    @NonNull
    public String getRequestId() {
        return requestId;
    }

    @NonNull
    public String getDirection() {
        return direction;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean hasDuration() {
        return durationMs >= 0L;
    }

    public boolean isFailure() {
        return STATUS_FAILED.equals(status) || STATUS_REJECTED.equals(status);
    }

    /** Returns a copy carrying {@code id}; returns {@code this} when it already matches. */
    @NonNull
    public LogEntry withId(long id) {
        if (this.id == id) {
            return this;
        }
        return new Builder(this).id(id).build();
    }

    @NonNull
    @Override
    public String toString() {
        return "[" + level.getShortName() + "/" + tag + "] " + message
                + (correlationId.isEmpty() ? "" : " (corr=" + correlationId + ")");
    }

    /** Mutable builder — the only way to create an entry with diagnostic dimensions. */
    public static final class Builder {

        private long id;
        private long timestamp = System.currentTimeMillis();
        private LogLevel level = LogLevel.INFO;
        private String tag = "NativeRuntime";
        private String message = "";
        private String threadName = Thread.currentThread().getName();
        private Throwable throwable;
        private String correlationId = "";
        private LogSource source = LogSource.NATIVE;
        private String module = "";
        private String projectId = "";
        private String requestId = "";
        private String direction = "";
        private String status = "";
        private long durationMs = NO_DURATION;

        public Builder() {
        }

        /** Copy constructor, used by {@link LogEntry#withId(long)} and by exporters. */
        public Builder(@NonNull LogEntry entry) {
            this.id = entry.id;
            this.timestamp = entry.timestamp;
            this.level = entry.level;
            this.tag = entry.tag;
            this.message = entry.message;
            this.threadName = entry.threadName;
            this.throwable = entry.throwable;
            this.correlationId = entry.correlationId;
            this.source = entry.source;
            this.module = entry.module;
            this.projectId = entry.projectId;
            this.requestId = entry.requestId;
            this.direction = entry.direction;
            this.status = entry.status;
            this.durationMs = entry.durationMs;
        }

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder level(@Nullable LogLevel level) {
            this.level = level;
            return this;
        }

        public Builder tag(@Nullable String tag) {
            this.tag = tag;
            return this;
        }

        public Builder message(@Nullable String message) {
            this.message = message;
            return this;
        }

        public Builder threadName(@Nullable String threadName) {
            this.threadName = threadName;
            return this;
        }

        public Builder throwable(@Nullable Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        public Builder correlationId(@Nullable String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder source(@Nullable LogSource source) {
            this.source = source;
            return this;
        }

        public Builder module(@Nullable String module) {
            this.module = module;
            return this;
        }

        public Builder projectId(@Nullable String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder requestId(@Nullable String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder direction(@Nullable String direction) {
            this.direction = direction;
            return this;
        }

        public Builder status(@Nullable String status) {
            this.status = status;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        @NonNull
        public LogEntry build() {
            return new LogEntry(this);
        }
    }
}
