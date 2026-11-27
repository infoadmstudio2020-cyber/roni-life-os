package com.roni.library.devtools;

import android.content.Context;

import com.roni.library.devtools.inspector.RuntimeInspector;

/**
 * Public static facade for NativeDevToolsSDK complying with PROMPTFORNativeDevToolsSDK.txt.
 */
public final class DevToolsSDK {

    private DevToolsSDK() {}

    public static NativeDevToolsSDK getInstance() {
        return NativeDevToolsSDK.getInstance();
    }

    public static NativeDevToolsSDKResult<Boolean> init(Context context, NativeDevToolsSDKConfig config) {
        return NativeDevToolsSDK.getInstance().init(context, config);
    }

    public static boolean isInitialized() {
        return NativeDevToolsSDK.getInstance().isInitialized();
    }

    public static DebugDrawer getDebugDrawer() {
        return NativeDevToolsSDK.getInstance().getDebugDrawer();
    }

    public static NetworkInspector getNetworkInspector() {
        return NativeDevToolsSDK.getInstance().getNetworkInspector();
    }

    public static FpsMonitor getFpsMonitor() {
        return NativeDevToolsSDK.getInstance().getFpsMonitor();
    }

    public static CrashReporter getCrashReporter() {
        return NativeDevToolsSDK.getInstance().getCrashReporter();
    }

    /**
     * The Runtime Inspector library component.
     *
     * <p>Returns the process-wide inspector facade. It is available even when the rest of the
     * dev-tools SDK failed to initialise: a diagnostic tool must work on a broken runtime.</p>
     */
    public static RuntimeInspector getRuntimeInspector() {
        return NativeDevToolsSDK.getInstance().getRuntimeInspector();
    }

    public static void release() {
        NativeDevToolsSDK.getInstance().release();
    }
}
