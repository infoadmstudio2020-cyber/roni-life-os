package com.roni.library.network.monitor;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Connectivity state model.
 *
 * <p>When a {@link Context} is attached through {@link #init(Context)} the monitor registers a
 * real {@link ConnectivityManager.NetworkCallback} (API 24+) and derives the connection type and
 * bandwidth quality from {@link NetworkCapabilities}. Without a context — JVM unit tests, or a
 * host that never calls {@code init} — the monitor stays in its last known state and reports
 * {@link ConnectionType#NONE} / {@link BandwidthQuality#UNKNOWN} until
 * {@link #updateConnection(ConnectionType, BandwidthQuality)} is called explicitly.</p>
 */
public class NetworkMonitor {
    public interface NetworkStateListener {
        void onNetworkStateChanged(ConnectionType type, BandwidthQuality quality, boolean connected);
    }

    private volatile ConnectionType connectionType = ConnectionType.NONE;
    private volatile BandwidthQuality bandwidthQuality = BandwidthQuality.UNKNOWN;
    private final Set<String> capabilities = new HashSet<>();
    private final List<NetworkStateListener> listeners = new CopyOnWriteArrayList<>();

    private volatile ConnectivityManager connectivityManager;
    private volatile ConnectivityManager.NetworkCallback networkCallback;

    public NetworkMonitor() {
    }

    /**
     * Binds the platform connectivity service and starts observing real network changes.
     * Safe to call more than once; the previous callback is unregistered first.
     */
    public synchronized void init(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        ConnectivityManager cm =
                (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return;
        }
        release();
        this.connectivityManager = cm;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    applyCapabilities(caps);
                }

                @Override
                public void onLost(Network network) {
                    updateConnection(ConnectionType.NONE, BandwidthQuality.UNKNOWN);
                }
            };
            try {
                cm.registerNetworkCallback(new NetworkRequest.Builder().build(), callback);
                this.networkCallback = callback;
            } catch (Throwable ignored) {
                this.networkCallback = null;
            }
        }
        refreshFromPlatform();
    }

    /**
     * Reads the current platform state once. No-op when no context is attached.
     */
    public void refreshFromPlatform() {
        ConnectivityManager cm = connectivityManager;
        if (cm == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network active = cm.getActiveNetwork();
                NetworkCapabilities caps = active != null ? cm.getNetworkCapabilities(active) : null;
                if (caps == null) {
                    updateConnection(ConnectionType.NONE, BandwidthQuality.UNKNOWN);
                } else {
                    applyCapabilities(caps);
                }
            } else {
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                if (info == null || !info.isConnected()) {
                    updateConnection(ConnectionType.NONE, BandwidthQuality.UNKNOWN);
                } else {
                    updateConnection(mapLegacyType(info.getType()), BandwidthQuality.UNKNOWN);
                }
            }
        } catch (Throwable ignored) {
            // Platform unavailable — keep the last known state.
        }
    }

    private void applyCapabilities(NetworkCapabilities caps) {
        if (caps == null) {
            updateConnection(ConnectionType.NONE, BandwidthQuality.UNKNOWN);
            return;
        }
        ConnectionType type;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            type = ConnectionType.WIFI;
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            type = ConnectionType.CELLULAR;
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            type = ConnectionType.ETHERNET;
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            type = ConnectionType.VPN;
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            type = ConnectionType.BLUETOOTH;
        } else {
            type = ConnectionType.OTHER;
        }
        BandwidthQuality quality = BandwidthQuality.UNKNOWN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int downKbps = caps.getLinkDownstreamBandwidthKbps();
            quality = qualityForKbps(downKbps);
        }
        updateConnection(type, quality);
    }

    private static BandwidthQuality qualityForKbps(int kbps) {
        if (kbps <= 0) {
            return BandwidthQuality.UNKNOWN;
        }
        if (kbps >= BandwidthQuality.EXCELLENT.getMinKbps()) {
            return BandwidthQuality.EXCELLENT;
        }
        if (kbps >= BandwidthQuality.GOOD.getMinKbps()) {
            return BandwidthQuality.GOOD;
        }
        if (kbps >= BandwidthQuality.MODERATE.getMinKbps()) {
            return BandwidthQuality.MODERATE;
        }
        return BandwidthQuality.POOR;
    }

    private static ConnectionType mapLegacyType(int type) {
        switch (type) {
            case ConnectivityManager.TYPE_WIFI:
                return ConnectionType.WIFI;
            case ConnectivityManager.TYPE_MOBILE:
                return ConnectionType.CELLULAR;
            case ConnectivityManager.TYPE_ETHERNET:
                return ConnectionType.ETHERNET;
            case ConnectivityManager.TYPE_BLUETOOTH:
                return ConnectionType.BLUETOOTH;
            case ConnectivityManager.TYPE_VPN:
                return ConnectionType.VPN;
            default:
                return ConnectionType.OTHER;
        }
    }

    public ConnectionType getConnectionType() {
        return connectionType;
    }

    public boolean isConnected() {
        return connectionType != null && connectionType.isConnected();
    }

    public BandwidthQuality getBandwidthQuality() {
        return bandwidthQuality;
    }

    public Set<String> getCapabilities() {
        return Collections.unmodifiableSet(new HashSet<>(capabilities));
    }

    public void updateConnection(ConnectionType type, BandwidthQuality quality) {
        this.connectionType = type != null ? type : ConnectionType.NONE;
        this.bandwidthQuality = quality != null ? quality : BandwidthQuality.UNKNOWN;
        boolean connected = isConnected();
        for (NetworkStateListener listener : listeners) {
            try {
                listener.onNetworkStateChanged(this.connectionType, this.bandwidthQuality, connected);
            } catch (Throwable ignored) {}
        }
    }

    public void addListener(NetworkStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(NetworkStateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Unregisters the platform callback. Listeners are retained; call {@link #clear()} to drop them.
     */
    public synchronized void release() {
        ConnectivityManager cm = connectivityManager;
        ConnectivityManager.NetworkCallback callback = networkCallback;
        if (cm != null && callback != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (Throwable ignored) {
                // Already unregistered.
            }
        }
        this.networkCallback = null;
        this.connectivityManager = null;
    }

    public void clear() {
        listeners.clear();
    }
}
