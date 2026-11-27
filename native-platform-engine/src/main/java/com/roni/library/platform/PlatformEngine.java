package com.roni.library.platform;

import android.content.Context;

import androidx.annotation.Nullable;

import com.roni.library.contracts.common.IDisposable;
import com.roni.library.contracts.common.RuntimeState;
import com.roni.library.logger.NativeLogger;
import com.roni.library.platform.handler.PlatformCommandHandler;
import com.roni.library.platform.notification.AlarmNotificationScheduler;
import com.roni.library.platform.notification.AndroidNotificationBackend;
import com.roni.library.platform.notification.InMemoryNotificationStore;
import com.roni.library.platform.notification.NotificationCommandHandler;
import com.roni.library.platform.camera.Camera2Backend;
import com.roni.library.platform.camera.CameraCommandHandler;
import com.roni.library.platform.camera.CameraMediaRepository;
import com.roni.library.platform.automation.AutomationCommandHandler;
import com.roni.library.platform.automation.AutomationService;
import com.roni.library.platform.camera.CameraService;
import com.roni.library.platform.filemanager.FileCommandHandler;
import com.roni.library.platform.filemanager.FileManagerRepository;
import com.roni.library.platform.filemanager.FileManagerService;
import com.roni.library.platform.filemanager.SafFileStore;
import com.roni.library.platform.notification.NotificationService;
import com.roni.library.platform.notification.NotificationStore;
import com.roni.library.platform.notification.PreferencesNotificationStore;
import com.roni.library.storage.StorageEngine;
import com.roni.library.storage.preferences.EncryptedPreferences;
import com.roni.library.platform.service.BatteryPlatformService;
import com.roni.library.platform.service.ClipboardPlatformService;
import com.roni.library.platform.service.DevicePlatformService;
import com.roni.library.platform.service.NativePlatformEngineService;
import com.roni.library.platform.service.NetworkPlatformService;
import com.roni.library.platform.service.StoragePlatformService;
import com.roni.library.platform.service.ToastNotificationService;
import com.roni.library.service.ServiceRegistry;
import com.roni.library.contracts.service.IService;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Primary public entry point and coordinator for NativePlatformEngine (Layer 2).
 * Orchestrates device specifications, battery, connectivity, storage, clipboard,
 * and haptic toast services with thread-safe lifecycle control.
 *
 * @author infoadmstudio2020-cyber
 */
public class PlatformEngine implements IDisposable {

    private static final String TAG = "PlatformEngine";
    private static volatile PlatformEngine sInstance;
    private static final Object INSTANCE_LOCK = new Object();

    private final AtomicReference<RuntimeState> runtimeState = new AtomicReference<>(RuntimeState.UNINITIALIZED);
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    private final DevicePlatformService deviceService = new DevicePlatformService();
    private final BatteryPlatformService batteryService = new BatteryPlatformService();
    private final NetworkPlatformService networkService = new NetworkPlatformService();
    private final StoragePlatformService storageService = new StoragePlatformService();
    private final ClipboardPlatformService clipboardService = new ClipboardPlatformService();
    private final ToastNotificationService toastService = new ToastNotificationService();
    private final PlatformCommandHandler commandHandler = new PlatformCommandHandler();
    private volatile CameraService cameraService;
    private volatile CameraCommandHandler cameraCommandHandler;
    private volatile FileManagerService fileManagerService;
    private volatile FileCommandHandler fileCommandHandler;
    private volatile AutomationService automationService;
    private volatile AutomationCommandHandler automationCommandHandler;

    private volatile NotificationService notificationService;
    private volatile NotificationCommandHandler notificationCommandHandler;

    private volatile PlatformEngineConfig config;
    private volatile Context appContext;

    public PlatformEngine() {
        initialize();
    }

    /**
     * Retrieves the singleton instance of PlatformEngine.
     */
    public static PlatformEngine getInstance() {
        if (sInstance == null) {
            synchronized (INSTANCE_LOCK) {
                if (sInstance == null) {
                    sInstance = new PlatformEngine();
                }
            }
        }
        return sInstance;
    }

    /**
     * Sets or overrides the singleton instance (e.g. for testing).
     */
    public static void setInstance(PlatformEngine engine) {
        synchronized (INSTANCE_LOCK) {
            sInstance = engine;
        }
    }

    /**
     * Initializes the engine with default configuration.
     */
    public synchronized void initialize() {
        if (runtimeState.get() == RuntimeState.READY) {
            return;
        }
        init(null, new PlatformEngineConfig.Builder().build());
    }

    /**
     * Initializes the platform engine with Android context and custom configuration.
     *
     * @param context Android Application Context
     * @param config  Configuration options
     */
    public synchronized void init(Context context, PlatformEngineConfig config) {
        runtimeState.set(RuntimeState.INITIALIZING);
        disposed.set(false);

        if (context != null) {
            this.appContext = context.getApplicationContext();
        }
        this.config = config != null ? config : new PlatformEngineConfig.Builder().build();

        // Initialize sub-services with context
        deviceService.init(this.appContext);
        batteryService.init(this.appContext);
        networkService.init(this.appContext);
        storageService.init(this.appContext);
        clipboardService.init(this.appContext);
        toastService.init(this.appContext);

        initNotifications();
        initCamera();
        initFileManager();
        initAutomation();

        // Register with Layer 1 ServiceRegistry
        try {
            NativePlatformEngineService service = new NativePlatformEngineService(this);
            ServiceRegistry.getInstance().registerService(
                    IService.class,
                    service
            );
        } catch (Throwable ignored) {
        }

        runtimeState.set(RuntimeState.READY);

        try {
            NativeLogger.getInstance().i(TAG, "PlatformEngine initialized and ready.");
        } catch (Throwable ignored) {
        }
    }

    public boolean isInitialized() {
        return runtimeState.get() == RuntimeState.READY;
    }

    public RuntimeState getState() {
        return runtimeState.get();
    }

    public PlatformEngineConfig getConfig() {
        return config;
    }

    public Context getAppContext() {
        return appContext;
    }

    public DevicePlatformService getDeviceService() {
        return deviceService;
    }

    public BatteryPlatformService getBatteryService() {
        return batteryService;
    }

    public NetworkPlatformService getNetworkService() {
        return networkService;
    }

    public StoragePlatformService getStorageService() {
        return storageService;
    }

    public ClipboardPlatformService getClipboardService() {
        return clipboardService;
    }

    public ToastNotificationService getToastService() {
        return toastService;
    }

    public PlatformCommandHandler getCommandHandler() {
        return commandHandler;
    }

    /**
     * @return the notification engine, or {@code null} before {@link #init(Context, PlatformEngineConfig)}
     */
    @Nullable
    public CameraService getCameraService() {
        return cameraService;
    }

    @Nullable
    public CameraCommandHandler getCameraCommandHandler() {
        return cameraCommandHandler;
    }

    @Nullable
    public FileManagerService getFileManagerService() {
        return fileManagerService;
    }

    @Nullable
    public FileCommandHandler getFileCommandHandler() {
        return fileCommandHandler;
    }

    @Nullable
    public AutomationService getAutomationService() {
        return automationService;
    }

    @Nullable
    public AutomationCommandHandler getAutomationCommandHandler() {
        return automationCommandHandler;
    }

    private void initAutomation() {
        try {
            if (appContext == null) {
                return;
            }
            AutomationService service = AutomationService.create(appContext);
            this.automationService = service;
            this.automationCommandHandler = new AutomationCommandHandler(service);
        } catch (Throwable ignored) {
            this.automationService = null;
            this.automationCommandHandler = null;
        }
    }

    private void initFileManager() {
        try {
            if (appContext == null) {
                return;
            }
            EncryptedPreferences preferences = null;
            try {
                preferences = StorageEngine.getPreferences("filemanager_meta");
            } catch (Throwable ignored) {
            }
            FileManagerRepository repository = new FileManagerRepository(appContext, preferences);
            SafFileStore safStore = new SafFileStore(appContext, preferences);
            FileManagerService service = new FileManagerService(repository, safStore);
            this.fileManagerService = service;
            this.fileCommandHandler = new FileCommandHandler(service);
        } catch (Throwable ignored) {
            this.fileManagerService = null;
            this.fileCommandHandler = null;
        }
    }

    private void initCamera() {
        try {
            if (appContext == null) {
                return;
            }
            Camera2Backend backend = new Camera2Backend(appContext);
            EncryptedPreferences preferences = null;
            try {
                preferences = StorageEngine.getPreferences("camera_media");
            } catch (Throwable ignored) {
            }
            CameraMediaRepository repository = new CameraMediaRepository(appContext, preferences);
            CameraService service = new CameraService(backend, repository);
            service.init();
            this.cameraService = service;
            this.cameraCommandHandler = new CameraCommandHandler(service);
        } catch (Throwable ignored) {
            this.cameraService = null;
            this.cameraCommandHandler = null;
        }
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }

    public NotificationCommandHandler getNotificationCommandHandler() {
        return notificationCommandHandler;
    }

    /**
     * Builds the notification engine on top of the existing storage engine.
     *
     * <p>Persistence is best-effort: when the encrypted preference store cannot be opened the
     * engine falls back to an in-memory store so notifications keep working for the lifetime of
     * the process instead of failing outright.</p>
     */
    private void initNotifications() {
        if (this.appContext == null) {
            return;
        }
        NotificationStore store;
        try {
            store = new PreferencesNotificationStore(StorageEngine.getPreferences("notifications"));
        } catch (Throwable error) {
            store = new InMemoryNotificationStore();
        }
        NotificationService service = new NotificationService(
                new AndroidNotificationBackend(this.appContext),
                new AlarmNotificationScheduler(this.appContext),
                store);
        service.init();
        this.notificationService = service;
        this.notificationCommandHandler = new NotificationCommandHandler(service);
    }

    /**
     * Releases all held platform resources and resets state.
     */
    public synchronized void release() {
        if (cameraService != null) {
            try {
                cameraService.release();
            } catch (Throwable ignored) {
            }
            cameraService = null;
            cameraCommandHandler = null;
        }
        if (fileManagerService != null) {
            try {
                fileManagerService.release();
            } catch (Throwable ignored) {
            }
            fileManagerService = null;
            fileCommandHandler = null;
        }
        if (automationService != null) {
            try {
                automationService.release();
            } catch (Throwable ignored) {
            }
            automationService = null;
            automationCommandHandler = null;
        }
        dispose();
    }

    @Override
    public synchronized void dispose() {
        if (disposed.compareAndSet(false, true)) {
            runtimeState.set(RuntimeState.TERMINATED);

            deviceService.release();
            batteryService.release();
            networkService.release();
            storageService.release();
            clipboardService.release();
            toastService.release();

            NotificationService service = notificationService;
            if (service != null) {
                service.release();
            }
            notificationService = null;
            notificationCommandHandler = null;

            appContext = null;
            config = null;

            synchronized (INSTANCE_LOCK) {
                if (sInstance == this) {
                    sInstance = null;
                }
            }
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}
