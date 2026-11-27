package com.roni.library.network;

import androidx.annotation.NonNull;

/**
 * Configuration for NativeNetworkEngine.
 */
public final class NativeNetworkEngineConfig {

    private final boolean enabled;
    private final boolean debugMode;
    private final boolean debugEnabled;
    private final String tag;
    private final int timeoutMs;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int writeTimeoutMs;
    private final int maxRetries;

    private NativeNetworkEngineConfig(Builder builder) {
        this.enabled = builder.enabled;
        this.debugMode = builder.debugMode;
        this.debugEnabled = builder.debugEnabled;
        this.tag = builder.tag;
        this.timeoutMs = builder.timeoutMs;
        this.connectTimeoutMs = builder.connectTimeoutMs;
        this.readTimeoutMs = builder.readTimeoutMs;
        this.writeTimeoutMs = builder.writeTimeoutMs;
        this.maxRetries = builder.maxRetries;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public String getTag() {
        return tag;
    }

    /**
     * Overall request budget in milliseconds. Used as the default connect/read timeout when the
     * individual values are not set.
     */
    public int getTimeout() {
        return timeoutMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public int getWriteTimeoutMs() {
        return writeTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public static final class Builder {

        private boolean enabled = true;
        private boolean debugMode = false;
        private boolean debugEnabled = false;
        private String tag = "NativeNetworkEngine";
        private int timeoutMs = 30000;
        private int connectTimeoutMs = 30000;
        private int readTimeoutMs = 30000;
        private int writeTimeoutMs = 30000;
        private int maxRetries = 2;

        public Builder() {}

        @NonNull
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @NonNull
        public Builder debugMode(boolean debugMode) {
            this.debugMode = debugMode;
            return this;
        }

        @NonNull
        public Builder debugEnabled(boolean debugEnabled) {
            this.debugEnabled = debugEnabled;
            return this;
        }

        @NonNull
        public Builder tag(String tag) {
            this.tag = tag != null ? tag : "NativeNetworkEngine";
            return this;
        }

        @NonNull
        public Builder timeout(int timeoutMs) {
            if (timeoutMs <= 0) {
                throw new IllegalArgumentException("Timeout must be greater than zero");
            }
            this.timeoutMs = timeoutMs;
            return this;
        }

        @NonNull
        public Builder connectTimeoutMs(int timeout) {
            if (timeout <= 0) {
                throw new IllegalArgumentException(
                        "Connect timeout must be greater than zero");
            }
            this.connectTimeoutMs = timeout;
            return this;
        }

        @NonNull
        public Builder connectTimeout(int timeout) {
            return connectTimeoutMs(timeout);
        }

        @NonNull
        public Builder readTimeoutMs(int timeout) {
            if (timeout <= 0) {
                throw new IllegalArgumentException(
                        "Read timeout must be greater than zero");
            }
            this.readTimeoutMs = timeout;
            return this;
        }

        @NonNull
        public Builder readTimeout(int timeout) {
            return readTimeoutMs(timeout);
        }

        @NonNull
        public Builder writeTimeoutMs(int timeout) {
            if (timeout <= 0) {
                throw new IllegalArgumentException(
                        "Write timeout must be greater than zero");
            }
            this.writeTimeoutMs = timeout;
            return this;
        }

        @NonNull
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = Math.max(0, maxRetries);
            return this;
        }

        @NonNull
        public NativeNetworkEngineConfig build() {
            return new NativeNetworkEngineConfig(this);
        }
    }
}
