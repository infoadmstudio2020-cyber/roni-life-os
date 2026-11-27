package com.roni.library.os.integration;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.roni.library.analytics.handler.AnalyticsCommandHandler;
import com.roni.library.analytics.service.NativeAnalyticsService;
import com.roni.library.bridge.BridgeConfig;
import com.roni.library.bridge.NativeWebBridgeSDK;
import com.roni.library.bridge.WebBridge;
import com.roni.library.contracts.api.IApiHandler;
import com.roni.library.contracts.theme.IThemeConfig;
import com.roni.library.devtools.handler.DevToolsCommandHandler;
import com.roni.library.devtools.service.NativeDevToolsService;
import com.roni.library.logger.handler.LoggerCommandHandler;
import com.roni.library.network.handler.NetworkCommandHandler;
import com.roni.library.network.service.NativeNetworkService;
import com.roni.library.platform.handler.PlatformCommandHandler;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.camera.CameraCommandHandler;
import com.roni.library.platform.notification.NotificationCommandHandler;
import com.roni.library.router.IRouter;
import com.roni.library.security.SecurityEngine;
import com.roni.library.security.SecurityEngineConfig;
import com.roni.library.security.handler.SecurityCommandHandler;
import com.roni.library.service.handler.ServiceCommandHandler;
import com.roni.library.theme.handler.ThemeCommandHandler;
import com.roni.library.theme.manager.ThemeManager;
import com.roni.library.theme.model.ThemeConfig;
import com.roni.library.theme.parser.ThemeParser;
import com.roni.library.ui.NativeRuntimeUI;
import com.roni.library.ui.NativeRuntimeUIConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers the nine native engine command handlers on the WebBridge router.
 *
 * <p>This is the single integration point between the native engines and the
 * JavaScript bridge. It creates no managers of its own: every handler is either
 * obtained from an existing singleton ({@link SecurityEngine#getCommandHandler()})
 * or constructed around an existing service class.</p>
 *
 * <p>Registration is idempotent. Calling {@link #install(Context)} twice returns
 * the same report and does not duplicate routes.</p>
 */
public final class NativeApiIntegration {

    /** Route prefixes owned by this integration. */
    public static final String ROUTE_THEME = "theme";
    public static final String ROUTE_SECURITY = "security";
    public static final String ROUTE_ANALYTICS = "analytics";
    public static final String ROUTE_DEVTOOLS = "devtools";
    public static final String ROUTE_NETWORK = "network";
    public static final String ROUTE_PLATFORM = "platform";
    public static final String ROUTE_NOTIFICATION = "notification";
    public static final String ROUTE_CAMERA = "camera";
    public static final String ROUTE_FILEMANAGER = "filemanager";
    public static final String ROUTE_AUTOMATION = "automation";
    public static final String ROUTE_LOGGER = "logger";
    public static final String ROUTE_SERVICE = "service";

    /** Owned by {@code NativeRuntimeUI}; never re-registered here. */
    public static final String ROUTE_STORAGE = "storage";

    public static final String STATUS_REGISTERED = "REGISTERED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_FAILED = "FAILED";

    private static final NativeApiIntegration INSTANCE = new NativeApiIntegration();

    private final AtomicBoolean installed = new AtomicBoolean(false);
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private volatile ThemeManager themeManager;
    private volatile SecurityCommandHandler securityHandler;
    private volatile WebBridge bridge;
    private volatile boolean devToolsEnabled;

    private NativeApiIntegration() {
    }

    public static NativeApiIntegration getInstance() {
        return INSTANCE;
    }

    /** One route registration outcome. */
    public static final class Entry {
        private final String route;
        private final String handlerClass;
        private final String status;
        private final String detail;

        Entry(String route, String handlerClass, String status, String detail) {
            this.route = route;
            this.handlerClass = handlerClass;
            this.status = status;
            this.detail = detail;
        }

        public String getRoute() {
            return route;
        }

        public String getHandlerClass() {
            return handlerClass;
        }

        public String getStatus() {
            return status;
        }

        public String getDetail() {
            return detail;
        }

        public boolean isRegistered() {
            return STATUS_REGISTERED.equals(status);
        }

        @Override
        public String toString() {
            return route + " -> " + handlerClass + " [" + status + "]"
                    + (detail == null || detail.isEmpty() ? "" : " " + detail);
        }
    }

    /** Immutable outcome of an {@link #install(Context)} call. */
    public static final class Report {
        private final List<Entry> entries;
        private final long timestamp;

        Report(List<Entry> entries) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.timestamp = System.currentTimeMillis();
        }

        public List<Entry> getEntries() {
            return entries;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public List<String> getRegisteredRoutes() {
            List<String> routes = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry.isRegistered()) {
                    routes.add(entry.getRoute());
                }
            }
            return routes;
        }

        public List<String> getFailures() {
            List<String> failures = new ArrayList<>();
            for (Entry entry : entries) {
                if (STATUS_FAILED.equals(entry.getStatus())) {
                    failures.add(entry.getRoute() + ": " + entry.getDetail());
                }
            }
            return failures;
        }

        public boolean isComplete() {
            return getFailures().isEmpty();
        }

        public String toSummary() {
            int registered = 0;
            int skipped = 0;
            int failed = 0;
            for (Entry entry : entries) {
                if (STATUS_REGISTERED.equals(entry.getStatus())) {
                    registered++;
                } else if (STATUS_SKIPPED.equals(entry.getStatus())) {
                    skipped++;
                } else {
                    failed++;
                }
            }
            return "registered=" + registered + " skipped=" + skipped + " failed=" + failed;
        }
    }

    public boolean isInstalled() {
        return installed.get();
    }

    public boolean isDevToolsEnabled() {
        return devToolsEnabled;
    }

    @Nullable
    public ThemeManager getThemeManager() {
        return themeManager;
    }

    @Nullable
    public SecurityCommandHandler getSecurityHandler() {
        return securityHandler;
    }

    public Map<String, Entry> getEntries() {
        synchronized (entries) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }
    }

    /**
     * Registers every engine handler that can be constructed safely.
     *
     * @param context application or activity context; may be {@code null} when the
     *                bridge is already initialised
     */
    @NonNull
    public Report install(@Nullable Context context) {
        return install(context, null);
    }

    /**
     * Registers every engine handler and attaches the biometric host when an
     * Activity is available.
     */
    @NonNull
    public synchronized Report install(@Nullable Context context, @Nullable Activity activity) {
        if (installed.get()) {
            return snapshot();
        }

        List<Entry> results = new ArrayList<>();
        WebBridge resolved = resolveBridge(context);
        if (resolved == null) {
            results.add(new Entry("*", "-", STATUS_FAILED, "WebBridge is not initialised"));
            return new Report(results);
        }
        this.bridge = resolved;

        IRouter router = resolved.getRouter();
        if (router == null) {
            results.add(new Entry("*", "-", STATUS_FAILED, "Router is not available"));
            return new Report(results);
        }

        NativeRuntimeUIConfig uiConfig = readUiConfig();
        this.devToolsEnabled = uiConfig != null && uiConfig.isDebugEnabled();

        registerTheme(router, uiConfig, results);
        registerSecurity(router, context, activity, results);
        registerAnalytics(router, context, results);
        registerDevTools(router, results);
        registerNetwork(router, results);
        registerPlatform(router, results);
        registerNotification(router, results);
        registerCamera(router, results);
        registerFileManager(router, results);
        registerAutomation(router, results);
        registerLogger(router, results);
        registerService(router, results);
        recordStorageOwnership(results);

        synchronized (entries) {
            entries.clear();
            for (Entry entry : results) {
                entries.put(entry.getRoute(), entry);
            }
        }
        installed.set(true);
        return new Report(results);
    }

    /** Re-attaches the biometric prompt host after an Activity change. */
    public void attachActivity(@Nullable Activity activity) {
        if (!(activity instanceof FragmentActivity)) {
            return;
        }
        try {
            SecurityEngine.getInstance().attachBiometricHost((FragmentActivity) activity);
        } catch (Throwable ignored) {
        }
    }

    private Report snapshot() {
        synchronized (entries) {
            return new Report(new ArrayList<>(entries.values()));
        }
    }

    @Nullable
    private WebBridge resolveBridge(@Nullable Context context) {
        try {
            NativeWebBridgeSDK sdk = NativeWebBridgeSDK.getInstance();
            if (!sdk.isInitialized()) {
                if (context == null) {
                    return null;
                }
                sdk.init(context, BridgeConfig.builder()
                        .interfaceName("NativeRuntimeBridge")
                        .build());
            }
            return sdk.getBridge();
        } catch (Throwable error) {
            return null;
        }
    }

    @Nullable
    private NativeRuntimeUIConfig readUiConfig() {
        try {
            return NativeRuntimeUI.getInstance().getConfig();
        } catch (Throwable error) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // theme
    // ------------------------------------------------------------------

    private void registerTheme(IRouter router, @Nullable NativeRuntimeUIConfig uiConfig,
                               List<Entry> results) {
        try {
            ThemeManager manager = new ThemeManager();
            seedTheme(manager, uiConfig);
            ThemeCommandHandler handler = new ThemeCommandHandler(manager);
            router.registerHandler(handler);
            manager.addListener(this::onThemeChanged);
            this.themeManager = manager;
            results.add(new Entry(ROUTE_THEME, handler.getClass().getSimpleName(),
                    STATUS_REGISTERED, "6 actions"));
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_THEME, ThemeCommandHandler.class.getSimpleName(),
                    STATUS_FAILED, describe(error)));
        }
    }

    /**
     * Seeds the single authoritative {@link ThemeManager} from the UI configuration.
     * After this call the manager — not the config object — owns runtime theme state.
     */
    private void seedTheme(ThemeManager manager, @Nullable NativeRuntimeUIConfig uiConfig) {
        if (uiConfig == null) {
            return;
        }
        try {
            String mode = uiConfig.getThemeMode();
            boolean dark = "DARK".equalsIgnoreCase(mode);
            if ("SYSTEM".equalsIgnoreCase(mode) || mode == null || mode.isEmpty()) {
                dark = manager.getSystemThemeObserver().isSystemDark();
            }
            Map<String, Object> seed = new HashMap<>();
            seed.put("themeName", "RuntimeConfig");
            seed.put("darkMode", dark);
            seed.put("primaryColor", uiConfig.getPrimaryColor());
            seed.put("backgroundColor", uiConfig.getBackgroundColor());
            seed.put("statusBarColor", uiConfig.getStatusBarColor());
            seed.put("navigationBarColor", uiConfig.getNavBarColor());
            ThemeConfig config = ThemeParser.parse(seed);
            manager.applyTheme(config);
        } catch (Throwable ignored) {
        }
    }

    private void onThemeChanged(IThemeConfig theme) {
        WebBridge current = this.bridge;
        if (current == null || theme == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("themeName", theme.getThemeName());
            payload.put("darkMode", theme.isDarkMode());
            payload.put("primaryColor", theme.getPrimaryColor());
            payload.put("surfaceColor", theme.getSurfaceColor());
            payload.put("textColor", theme.getTextColor());
            if (theme instanceof ThemeConfig) {
                ThemeConfig config = (ThemeConfig) theme;
                payload.put("themeMode", config.getThemeMode().name());
                payload.put("secondaryColor", config.getSecondaryColorHex());
                payload.put("backgroundColor", config.getBackgroundColorHex());
                payload.put("statusBarColor", config.getStatusBarColorHex());
                payload.put("navigationBarColor", config.getNavigationBarColorHex());
            }
            current.emitToWeb("theme.changed", payload);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // security
    // ------------------------------------------------------------------

    private void registerSecurity(IRouter router, @Nullable Context context,
                                  @Nullable Activity activity, List<Entry> results) {
        try {
            SecurityEngine engine = SecurityEngine.getInstance();
            if (!engine.isInitialized() && context != null) {
                engine.init(context, new SecurityEngineConfig.Builder()
                        .debugEnabled(devToolsEnabled)
                        .build());
            }
            if (activity instanceof FragmentActivity) {
                engine.attachBiometricHost((FragmentActivity) activity);
            }
            SecurityCommandHandler handler = engine.getCommandHandler();
            if (handler == null) {
                results.add(new Entry(ROUTE_SECURITY, SecurityCommandHandler.class.getSimpleName(),
                        STATUS_FAILED, "SecurityEngine returned no command handler"));
                return;
            }
            handler.setBiometricResultListener(this::onBiometricResult);
            router.registerHandler(handler);
            this.securityHandler = handler;
            results.add(new Entry(ROUTE_SECURITY, handler.getClass().getSimpleName(),
                    STATUS_REGISTERED, "11 actions"));
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_SECURITY, SecurityCommandHandler.class.getSimpleName(),
                    STATUS_FAILED, describe(error)));
        }
    }

    private void onBiometricResult(String status, String method, int errorCode, String message) {
        WebBridge current = this.bridge;
        if (current == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("status", status);
            payload.put("method", method);
            payload.put("errorCode", errorCode);
            payload.put("message", message);
            current.emitToWeb("security.biometric.result", payload);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // analytics
    // ------------------------------------------------------------------

    private void registerAnalytics(IRouter router, @Nullable Context context, List<Entry> results) {
        try {
            NativeAnalyticsService service = new NativeAnalyticsService(context, null);
            AnalyticsCommandHandler handler = new AnalyticsCommandHandler(service);
            router.registerHandler(handler);
            results.add(new Entry(ROUTE_ANALYTICS, handler.getClass().getSimpleName(),
                    STATUS_REGISTERED, "11 actions"));
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_ANALYTICS, AnalyticsCommandHandler.class.getSimpleName(),
                    STATUS_FAILED, describe(error)));
        }
    }

    // ------------------------------------------------------------------
    // devtools (debug builds only)
    // ------------------------------------------------------------------

    private void registerDevTools(IRouter router, List<Entry> results) {
        if (!devToolsEnabled) {
            results.add(new Entry(ROUTE_DEVTOOLS, DevToolsCommandHandler.class.getSimpleName(),
                    STATUS_SKIPPED, "debug disabled in NativeRuntimeUIConfig"));
            return;
        }
        try {
            NativeDevToolsService service = new NativeDevToolsService();
            DevToolsCommandHandler handler = new DevToolsCommandHandler(service);
            router.registerHandler(handler);
            results.add(new Entry(ROUTE_DEVTOOLS, handler.getClass().getSimpleName(),
                    STATUS_REGISTERED, "9 actions (debug only)"));
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_DEVTOOLS, DevToolsCommandHandler.class.getSimpleName(),
                    STATUS_FAILED, describe(error)));
        }
    }

    // ------------------------------------------------------------------
    // network
    // ------------------------------------------------------------------

    private void registerNetwork(IRouter router, List<Entry> results) {
        try {
            NativeNetworkService service = new NativeNetworkService();
            NetworkCommandHandler handler = new NetworkCommandHandler(service);
            router.registerHandler(handler);
            results.add(new Entry(ROUTE_NETWORK, handler.getClass().getSimpleName(),
                    STATUS_REGISTERED, "1 action (request)"));
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_NETWORK, NetworkCommandHandler.class.getSimpleName(),
                    STATUS_FAILED, describe(error)));
        }
    }

    // ------------------------------------------------------------------
    // platform
    // ------------------------------------------------------------------

    private void registerPlatform(IRouter router, List<Entry> results) {
        try {
            PlatformCommandHandler handler = new PlatformCommandHandler();
            router.registerHandler(handler);
            results.add(new Entry(ROUTE_PLATFORM, handler.getClass().getSimpleName(),
                    STATUS_REGISTERED, "15 actions"));
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_PLATFORM, PlatformCommandHandler.class.getSimpleName(),
                    STATUS_FAILED, describe(error)));
        }
    }

    // ------------------------------------------------------------------
    // notification
    // ------------------------------------------------------------------

    private void registerAutomation(IRouter router, List<Entry> results) {
        try {
            com.roni.library.platform.automation.AutomationCommandHandler handler =
                    PlatformEngine.getInstance().getAutomationCommandHandler();
            if (handler != null) {
                router.registerHandler(handler);
                results.add(new Entry(ROUTE_AUTOMATION,
                        handler.getClass().getSimpleName(), STATUS_REGISTERED, "26 actions"));
            } else {
                results.add(new Entry(ROUTE_AUTOMATION, "-", STATUS_FAILED,
                        "Automation engine is not available"));
            }
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_AUTOMATION, "-", STATUS_FAILED, describe(error)));
        }
    }

    private void registerFileManager(IRouter router, List<Entry> results) {
        try {
            com.roni.library.platform.filemanager.FileCommandHandler handler =
                    PlatformEngine.getInstance().getFileCommandHandler();
            if (handler != null) {
                router.registerHandler(handler);
                results.add(new Entry(ROUTE_FILEMANAGER,
                        handler.getClass().getSimpleName(), STATUS_REGISTERED, "file actions"));
            } else {
                results.add(new Entry(ROUTE_FILEMANAGER, "-", STATUS_FAILED,
                        "File manager engine is not available"));
            }
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_FILEMANAGER, "-", STATUS_FAILED, describe(error)));
        }
    }

    private void registerCamera(IRouter router, List<Entry> results) {
        try {
            CameraCommandHandler handler =
                    PlatformEngine.getInstance().getCameraCommandHandler();
            if (handler == null) {
                results.add(new Entry(ROUTE_CAMERA,
                        CameraCommandHandler.class.getSimpleName(),
                        STATUS_SKIPPED, "platform engine not initialized"));
                return;
            }
            router.registerHandler(handler);
            results.add(new Entry(ROUTE_CAMERA, handler.getClass().getSimpleName(),
                    STATUS_REGISTERED, "18 actions"));
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_CAMERA,
                    CameraCommandHandler.class.getSimpleName(),
                    STATUS_FAILED, describe(error)));
        }
    }

    private void registerNotification(IRouter router, List<Entry> results) {
        try {
            NotificationCommandHandler handler =
                    PlatformEngine.getInstance().getNotificationCommandHandler();
            if (handler == null) {
                results.add(new Entry(ROUTE_NOTIFICATION,
                        NotificationCommandHandler.class.getSimpleName(),
                        STATUS_SKIPPED, "platform engine not initialized"));
                return;
            }
            router.registerHandler(handler);
            results.add(new Entry(ROUTE_NOTIFICATION, handler.getClass().getSimpleName(),
                    STATUS_REGISTERED, "10 actions"));
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_NOTIFICATION,
                    NotificationCommandHandler.class.getSimpleName(),
                    STATUS_FAILED, describe(error)));
        }
    }

    // ------------------------------------------------------------------
    // logger
    // ------------------------------------------------------------------

    private void registerLogger(IRouter router, List<Entry> results) {
        try {
            LoggerCommandHandler handler = new LoggerCommandHandler();
            router.registerHandler(handler);
            results.add(new Entry(ROUTE_LOGGER, handler.getClass().getSimpleName(),
                    STATUS_REGISTERED, "4 actions"));
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_LOGGER, LoggerCommandHandler.class.getSimpleName(),
                    STATUS_FAILED, describe(error)));
        }
    }

    // ------------------------------------------------------------------
    // service
    // ------------------------------------------------------------------

    private void registerService(IRouter router, List<Entry> results) {
        try {
            ServiceCommandHandler handler = new ServiceCommandHandler();
            router.registerHandler(handler);
            results.add(new Entry(ROUTE_SERVICE, handler.getClass().getSimpleName(),
                    STATUS_REGISTERED, "2 actions"));
        } catch (Throwable error) {
            results.add(new Entry(ROUTE_SERVICE, ServiceCommandHandler.class.getSimpleName(),
                    STATUS_FAILED, describe(error)));
        }
    }

    // ------------------------------------------------------------------
    // storage — owned by NativeRuntimeUI
    // ------------------------------------------------------------------

    private void recordStorageOwnership(List<Entry> results) {
        results.add(new Entry(ROUTE_STORAGE, "NativeStorageCommandHandler",
                STATUS_SKIPPED,
                "owned by NativeRuntimeUI (project-scoped); StorageCommandHandler not registered"));
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** Test seam: forgets the installed state so a fresh install can be exercised. */
    void resetForTests() {
        installed.set(false);
        synchronized (entries) {
            entries.clear();
        }
        themeManager = null;
        securityHandler = null;
        bridge = null;
        devToolsEnabled = false;
    }

    /** Exposed for the verification harness. */
    @NonNull
    public static IApiHandler[] handlerTypes() {
        return new IApiHandler[]{
                new ThemeCommandHandler(new ThemeManager()),
                new SecurityCommandHandler(),
                new AnalyticsCommandHandler(new NativeAnalyticsService()),
                new DevToolsCommandHandler(new NativeDevToolsService()),
                new NetworkCommandHandler(new NativeNetworkService()),
                new PlatformCommandHandler(),
                new LoggerCommandHandler(),
                new ServiceCommandHandler()
        };
    }
}
