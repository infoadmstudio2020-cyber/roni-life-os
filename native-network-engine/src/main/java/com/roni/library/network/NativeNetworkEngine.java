package com.roni.library.network;

import android.content.Context;

import com.roni.library.network.http.HttpClientEngine;
import com.roni.library.network.monitor.NetworkMonitor;
import com.roni.library.network.service.NativeNetworkService;

import java.util.concurrent.atomic.AtomicBoolean;

public class NativeNetworkEngine {
    private static volatile NativeNetworkEngine instance;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private NativeNetworkEngineConfig config;
    private NativeNetworkService networkService;
    private Context context;

    private NativeNetworkEngine() {}

    public static NativeNetworkEngine getInstance() {
        if (instance == null) {
            synchronized (NativeNetworkEngine.class) {
                if (instance == null) {
                    instance = new NativeNetworkEngine();
                }
            }
        }
        return instance;
    }

    public synchronized void init(Context context, NativeNetworkEngineConfig config) {
        if (initialized.get()) {
            return;
        }
        this.context = context != null ? context.getApplicationContext() : null;
        this.config = config != null ? config : NativeNetworkEngineConfig.builder().build();
        this.networkService = new NativeNetworkService();
        this.networkService.onInitialize();
        this.networkService.onStart();

        HttpClientEngine httpClient = this.networkService.getHttpClient();
        httpClient.configure(
                this.config.getConnectTimeoutMs(),
                this.config.getReadTimeoutMs(),
                this.config.getMaxRetries());

        NetworkMonitor monitor = this.networkService.getNetworkMonitor();
        if (this.context != null) {
            monitor.init(this.context);
        }

        this.initialized.set(true);
    }

    public void init(Context context) {
        init(context, NativeNetworkEngineConfig.builder().build());
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public NativeNetworkEngineConfig getConfig() {
        return config;
    }

    public NativeNetworkService getService() {
        return networkService;
    }

    public HttpClientEngine getHttpClient() {
        return networkService != null ? networkService.getHttpClient() : null;
    }

    public NetworkMonitor getNetworkMonitor() {
        return networkService != null ? networkService.getNetworkMonitor() : null;
    }

    /**
     * Stops the service and resets state. The singleton is retained so a later {@link #init}
     * rebuilds the same instance instead of creating a second one.
     */
    public synchronized void release() {
        if (!initialized.get()) {
            return;
        }
        if (networkService != null) {
            NetworkMonitor monitor = networkService.getNetworkMonitor();
            if (monitor != null) {
                monitor.release();
            }
            networkService.onStop();
            networkService.onDispose();
            networkService = null;
        }
        config = null;
        context = null;
        initialized.set(false);
    }
}
