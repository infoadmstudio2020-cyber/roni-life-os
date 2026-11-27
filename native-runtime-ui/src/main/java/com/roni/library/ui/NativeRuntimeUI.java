package com.roni.library.ui;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.roni.library.bridge.BridgeConfig;
import com.roni.library.bridge.NativeWebBridgeSDK;
import com.roni.library.bridge.WebBridge;
import com.roni.library.ui.handler.NativeDeviceCommandHandler;
import com.roni.library.ui.handler.NativeStorageCommandHandler;
import com.roni.library.ui.handler.NativeUICommandHandler;
import com.roni.library.ui.handler.NativeWebCommandHandler;
import com.roni.library.ui.host.NativeUIHost;
import com.roni.library.contracts.ui.IUIComponentRegistry;
import com.roni.library.contracts.ui.IUICompositionManager;
import com.roni.library.contracts.ui.IUIConfigurationManager;
import com.roni.library.contracts.ui.IUIEventDispatcher;
import com.roni.library.contracts.ui.IUIRenderer;
import com.roni.library.contracts.ui.IUIStateManager;
import com.roni.library.contracts.ui.IUIVisibilityManager;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe singleton entry point for NativeRuntimeUI adhering to API.md.
 */
public class NativeRuntimeUI {

    private static volatile NativeRuntimeUI instance;

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile boolean routesRegistered;
    private Context applicationContext;
    private WeakReference<Activity> activeActivityRef;
    private NativeRuntimeUIConfig config;
    private NativeUIHost uiHost;
    private NativeUICommandHandler commandHandler;
    private NativeWebCommandHandler webCommandHandler;
    private NativeDeviceCommandHandler deviceCommandHandler;
    private NativeStorageCommandHandler storageCommandHandler;
    private NativeToast toast;
    private LoadingIndicator loadingIndicator;
    private CustomBottomSheet bottomSheet;
    private NativeActionSheet actionSheet;

    private NativeRuntimeUI() {}

    public static NativeRuntimeUI getInstance() {
        if (instance == null) {
            synchronized (NativeRuntimeUI.class) {
                if (instance == null) {
                    instance = new NativeRuntimeUI();
                }
            }
        }
        return instance;
    }

    public synchronized NativeRuntimeUIResult<Boolean> init(@Nullable Context context, @Nullable NativeRuntimeUIConfig config) {
        if (initialized.get()) {
            if (context != null) {
                this.applicationContext = context.getApplicationContext();
            }
            if (config != null) {
                this.config = config;
                this.uiHost.updateConfiguration(config);
            }
            return NativeRuntimeUIResult.success(true);
        }
        if (context != null) {
            this.applicationContext = context.getApplicationContext();
        }
        this.config = config != null ? config : NativeRuntimeUIConfig.defaultConfiguration();

        if (this.uiHost == null) {
            this.uiHost = new NativeUIHost();
        }
        this.uiHost.updateConfiguration(this.config);

        if (this.commandHandler == null) {
            this.commandHandler = new NativeUICommandHandler(this.uiHost);
        }
        if (this.webCommandHandler == null) {
            this.webCommandHandler = new NativeWebCommandHandler(this.uiHost);
        }
        if (this.deviceCommandHandler == null) {
            this.deviceCommandHandler = new NativeDeviceCommandHandler(this.uiHost);
        }
        if (this.storageCommandHandler == null) {
            this.storageCommandHandler = new NativeStorageCommandHandler(this.uiHost);
        }
        registerBridgeRoutes(context);
        if (this.toast == null) {
            this.toast = new NativeToast();
        }
        if (this.loadingIndicator == null) {
            this.loadingIndicator = new LoadingIndicator();
        }
        if (this.bottomSheet == null) {
            this.bottomSheet = new CustomBottomSheet();
        }
        if (this.actionSheet == null) {
            this.actionSheet = new NativeActionSheet();
        }

        this.initialized.set(true);
        return NativeRuntimeUIResult.success(true);
    }

    @NonNull
    public synchronized View attachActivity(@NonNull Activity activity, @Nullable NativeRuntimeUIConfig config) {
        if (!initialized.get()) {
            init(activity, config);
        } else if (config != null) {
            this.config = config;
            this.uiHost.updateConfiguration(config);
        }
        this.activeActivityRef = new WeakReference<>(activity);
        return this.uiHost.getRootView() != null ? this.uiHost.getRootView() : new View(activity);
    }

    public synchronized void detachActivity(@NonNull Activity activity) {
        if (activeActivityRef != null && activeActivityRef.get() == activity) {
            activeActivityRef.clear();
            activeActivityRef = null;
        }
    }

    /**
     * @return the Activity currently hosting the runtime, or {@code null} when detached.
     *         Used by the integration layer to attach the biometric prompt host.
     */
    @Nullable
    public Activity getActiveActivity() {
        return activeActivityRef != null ? activeActivityRef.get() : null;
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public NativeRuntimeUIConfig getConfig() {
        return config;
    }


    public IUIComponentRegistry getRegistry() {
        checkInitialized();
        return uiHost.getRegistry();
    }

    public IUIConfigurationManager getConfigurationManager() {
        checkInitialized();
        return uiHost.getConfigurationManager();
    }

    public IUICompositionManager getCompositionManager() {
        checkInitialized();
        return uiHost.getCompositionManager();
    }

    public IUIVisibilityManager getVisibilityManager() {
        checkInitialized();
        return uiHost.getVisibilityManager();
    }

    public IUIStateManager getStateManager() {
        checkInitialized();
        return uiHost.getStateManager();
    }

    public IUIEventDispatcher getEventDispatcher() {
        checkInitialized();
        return uiHost.getEventDispatcher();
    }

    public IUIRenderer getRenderer() {
        checkInitialized();
        return uiHost.getRenderer();
    }

    public NativeUIHost getUiHost() {
        checkInitialized();
        return uiHost;
    }

    public NativeUICommandHandler getCommandHandler() {
        checkInitialized();
        return commandHandler;
    }

    /**
     * Binds the bridge to a Web Project so {@code storage.*} keys are namespaced per project.
     *
     * <p>Call this right after {@link #init(Context, NativeRuntimeUIConfig)} and before the
     * WebView loads. Passing {@code null} keeps the legacy shared namespace.</p>
     *
     * @param projectId stable project identifier (UUID or slug)
     */
    public void setProjectId(@Nullable String projectId) {
        if (this.uiHost != null) {
            this.uiHost.setProjectId(projectId);
        }
    }

    /**
     * @return the project id currently bound to the bridge, or {@code null} when unbound
     */
    @Nullable
    public String getProjectId() {
        return this.uiHost != null ? this.uiHost.getProjectId() : null;
    }

    @Nullable
    public WebBridge getWebBridge() {
        try {
            return NativeWebBridgeSDK.getInstance().getBridge();
        } catch (Throwable t) {
            return null;
        }
    }

    private void registerBridgeRoutes(@Nullable Context context) {
        if (routesRegistered) {
            return;
        }
        try {
            NativeWebBridgeSDK sdk = NativeWebBridgeSDK.getInstance();
            if (!sdk.isInitialized()) {
                sdk.init(context, BridgeConfig.builder()
                        .interfaceName("NativeRuntimeBridge")
                        .debugLogging(config != null && config.isDebugEnabled())
                        .build());
            }
            WebBridge bridge = sdk.getBridge();
            if (bridge != null && bridge.getRouter() != null) {
                bridge.getRouter().registerHandler(commandHandler);
                for (String action : NativeUICommandHandler.SUPPORTED_ACTIONS) {
                    bridge.getRouter().registerRoute(action, commandHandler);
                }
                bridge.getRouter().registerHandler(webCommandHandler);
                for (String action : NativeWebCommandHandler.SUPPORTED_ACTIONS) {
                    bridge.getRouter().registerRoute(action, webCommandHandler);
                }
                bridge.getRouter().registerHandler(deviceCommandHandler);
                for (String action : NativeDeviceCommandHandler.SUPPORTED_ACTIONS) {
                    bridge.getRouter().registerRoute(action, deviceCommandHandler);
                }
                bridge.getRouter().registerHandler(storageCommandHandler);
                for (String action : NativeStorageCommandHandler.SUPPORTED_ACTIONS) {
                    bridge.getRouter().registerRoute(action, storageCommandHandler);
                }
                routesRegistered = true;
            }
        } catch (Throwable ignored) {
        }
    }

    public NativeToast getToast() {
        checkInitialized();
        return toast;
    }

    public LoadingIndicator getLoadingIndicator() {
        checkInitialized();
        return loadingIndicator;
    }

    public CustomBottomSheet getBottomSheet() {
        checkInitialized();
        return bottomSheet;
    }

    public NativeActionSheet getActionSheet() {
        checkInitialized();
        return actionSheet;
    }

    public synchronized void release() {
        try {
            WebBridge bridge = NativeWebBridgeSDK.getInstance().getBridge();
            if (bridge != null && bridge.getRouter() != null) {
                bridge.getRouter().unregisterRoute("ui");
                for (String action : NativeUICommandHandler.SUPPORTED_ACTIONS) {
                    bridge.getRouter().unregisterRoute(action);
                }
                bridge.getRouter().unregisterRoute("web");
                for (String action : NativeWebCommandHandler.SUPPORTED_ACTIONS) {
                    bridge.getRouter().unregisterRoute(action);
                }
                bridge.getRouter().unregisterRoute("device");
                for (String action : NativeDeviceCommandHandler.SUPPORTED_ACTIONS) {
                    bridge.getRouter().unregisterRoute(action);
                }
                bridge.getRouter().unregisterRoute("storage");
                for (String action : NativeStorageCommandHandler.SUPPORTED_ACTIONS) {
                    bridge.getRouter().unregisterRoute(action);
                }
            }
        } catch (Throwable ignored) {
        }
        if (uiHost != null) {
            uiHost.reset();
            uiHost = null;
        }
        if (toast != null) {
            toast.dismiss();
            toast = null;
        }
        if (loadingIndicator != null) {
            loadingIndicator.hide();
            loadingIndicator = null;
        }
        if (bottomSheet != null) {
            bottomSheet.dismiss();
            bottomSheet = null;
        }
        if (actionSheet != null) {
            actionSheet.clear();
            actionSheet = null;
        }
        commandHandler = null;
        webCommandHandler = null;
        deviceCommandHandler = null;
        storageCommandHandler = null;
        config = null;
        routesRegistered = false;
        initialized.set(false);
    }

    private void checkInitialized() {
        if (!initialized.get()) {
            init(null, NativeRuntimeUIConfig.defaultConfiguration());
        }
    }
}
