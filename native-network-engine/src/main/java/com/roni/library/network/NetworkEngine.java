package com.roni.library.network;

import android.content.Context;
import com.roni.library.network.http.HttpClientEngine;
import com.roni.library.network.monitor.NetworkMonitor;
import com.roni.library.network.service.NativeNetworkService;

public final class NetworkEngine {
    private NetworkEngine() {}

    public static NativeNetworkEngine getInstance() {
        return NativeNetworkEngine.getInstance();
    }

    public static void init(Context context, NativeNetworkEngineConfig config) {
        NativeNetworkEngine.getInstance().init(context, config);
    }

    public static void init(Context context) {
        NativeNetworkEngine.getInstance().init(context);
    }

    public static boolean isInitialized() {
        return NativeNetworkEngine.getInstance().isInitialized();
    }

    public static HttpClientEngine getHttpClient() {
        return NativeNetworkEngine.getInstance().getHttpClient();
    }

    public static NetworkMonitor getNetworkMonitor() {
        return NativeNetworkEngine.getInstance().getNetworkMonitor();
    }

    public static NativeNetworkService getService() {
        return NativeNetworkEngine.getInstance().getService();
    }

    public static void release() {
        NativeNetworkEngine.getInstance().release();
    }
}
