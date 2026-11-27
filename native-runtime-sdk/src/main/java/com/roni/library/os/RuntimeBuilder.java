package com.roni.library.os;

import android.content.Context;

/**
 * Fluent builder for configuring and launching NativeRuntime.
 */
public final class RuntimeBuilder {
    private final Context context;
    private final RuntimeConfiguration.Builder configBuilder = RuntimeConfiguration.builder();

    public RuntimeBuilder(Context context) {
        this.context = context;
    }

    public static RuntimeBuilder with(Context context) {
        return new RuntimeBuilder(context);
    }

    public RuntimeBuilder appId(String appId) {
        configBuilder.appId(appId);
        return this;
    }

    public RuntimeBuilder environment(String env) {
        configBuilder.environment(env);
        return this;
    }

    public RuntimeBuilder debugEnabled(boolean debug) {
        configBuilder.debugEnabled(debug);
        return this;
    }

    public RuntimeBuilder logLevel(String level) {
        configBuilder.logLevel(level);
        return this;
    }

    public RuntimeBuilder storageEncryptionEnabled(boolean enabled) {
        configBuilder.storageEncryptionEnabled(enabled);
        return this;
    }

    public RuntimeBuilder networkTimeoutMs(int timeoutMs) {
        configBuilder.networkTimeoutMs(timeoutMs);
        return this;
    }

    public RuntimeBuilder maxNetworkRetries(int retries) {
        configBuilder.maxNetworkRetries(retries);
        return this;
    }

    public RuntimeBuilder telemetryEnabled(boolean enabled) {
        configBuilder.telemetryEnabled(enabled);
        return this;
    }

    public RuntimeBuilder addCustomProperty(String key, String value) {
        configBuilder.addCustomProperty(key, value);
        return this;
    }

    public NativeRuntime initialize() {
        RuntimeConfiguration config = configBuilder.build();
        NativeRuntime runtime = NativeRuntime.getInstance();
        runtime.init(context, config);
        return runtime;
    }

    /**
     * Initialises the runtime and returns the outcome instead of discarding it.
     *
     * <p>Prefer this over {@link #initialize()} when the caller needs to react to a failed
     * bootstrap.</p>
     */
    public NativeRuntimeResult<Boolean> initializeWithResult() {
        RuntimeConfiguration config = configBuilder.build();
        return NativeRuntime.getInstance().init(context, config);
    }
}
