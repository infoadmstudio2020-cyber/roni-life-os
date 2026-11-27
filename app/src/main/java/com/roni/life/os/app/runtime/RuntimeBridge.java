package com.roni.life.os.app.runtime;

import android.content.Context;

import com.roni.library.analytics.AnalyticsEngine;
import com.roni.library.bridge.NativeWebBridgeSDK;
import com.roni.library.devtools.DevToolsSDK;
import com.roni.library.logger.NativeLogger;
import com.roni.library.network.NetworkEngine;
import com.roni.library.os.NativeRuntime;
import com.roni.library.os.NativeRuntimeResult;
import com.roni.library.os.RuntimeConfiguration;
import com.roni.library.os.bootstrap.NativeRuntimeBootstrap;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.router.NativeRouter;
import com.roni.library.runtime.NativeRuntimeEngine;
import com.roni.library.security.SecurityEngine;
import com.roni.library.service.NativeServiceSDK;
import com.roni.library.storage.StorageEngine;
import com.roni.library.theme.ThemeEngine;
import com.roni.library.ui.NativeRuntimeUI;
import com.roni.life.os.app.core.AppConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NativeRuntime ইকোসিস্টেমের সাথে অ্যাপের একক ইন্টিগ্রেশন পয়েন্ট।
 *
 * <p>এখানে ব্যবহৃত প্রতিটি মেথড লাইব্রেরির প্রকৃত পাবলিক API।
 * কোনো অনুমান করা সিগনেচার নেই।</p>
 */
public final class RuntimeBridge {

    private static final RuntimeBridge INSTANCE = new RuntimeBridge();

    private volatile boolean initialized;
    private volatile String lastError = "";
    private volatile long initDurationMs;
    private volatile NativeRuntimeBootstrap.BootstrapReport bootstrapReport;

    private RuntimeBridge() {
    }

    public static RuntimeBridge get() {
        return INSTANCE;
    }

    /**
     * NativeRuntime-কে একবার ইনিশিয়ালাইজ করে এবং ১০-ধাপের Bootstrap চালায়।
     * ব্যর্থ হলেও অ্যাপ চলবে — শুধু রানটাইম-নির্ভর ফিচার সীমিত হবে।
     */
    public synchronized boolean initialize(Context context, AppConfig config) {
        if (initialized) {
            return true;
        }
        long startedAt = System.currentTimeMillis();
        try {
            RuntimeConfiguration.Builder builder = RuntimeConfiguration.builder()
                    .appId(context.getPackageName())
                    .environment(config != null && config.isDebugEnabled() ? "DEBUG" : "PRODUCTION")
                    .debugEnabled(config != null && config.isDebugEnabled())
                    .logLevel(config != null && config.isDebugEnabled() ? "DEBUG" : "INFO")
                    .storageEncryptionEnabled(true)
                    .networkTimeoutMs(15000)
                    .maxNetworkRetries(3)
                    .telemetryEnabled(config == null || config.isTelemetryEnabled());

            NativeRuntimeResult<Boolean> result = NativeRuntime.getInstance().init(context, builder.build());
            this.bootstrapReport = NativeRuntime.getInstance().getBootstrapReport();
            this.initialized = result != null && result.isSuccess();
            if (!this.initialized) {
                this.lastError = result != null ? result.getErrorMessage() : "Unknown runtime error";
            }
        } catch (Throwable throwable) {
            this.initialized = false;
            this.lastError = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
        } finally {
            this.initDurationMs = System.currentTimeMillis() - startedAt;
        }
        return initialized;
    }

    public boolean isReady() {
        try {
            return initialized && NativeRuntime.getInstance().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public String getLastError() {
        return lastError;
    }

    public long getInitDurationMs() {
        return initDurationMs;
    }

    public NativeRuntimeBootstrap.BootstrapReport getBootstrapReport() {
        return bootstrapReport;
    }

    /** প্রতিটি ইঞ্জিনের প্রকৃত ইনিশিয়ালাইজেশন অবস্থা। */
    public List<EngineStatus> getEngineStatuses() {
        List<EngineStatus> statuses = new ArrayList<>();
        statuses.add(new EngineStatus("NativeRuntimeSDK", safeRuntimeInitialized(), runtimeState()));
        statuses.add(new EngineStatus("NativeRuntimeUI", safeUiInitialized(), ""));
        statuses.add(new EngineStatus("NativeRuntimeEngine", safeRuntimeEngineInitialized(), ""));
        statuses.add(new EngineStatus("NativeWebBridgeSDK", safeBridgeInitialized(), ""));
        statuses.add(new EngineStatus("NativeRouterSDK", safeRouterInitialized(), ""));
        statuses.add(new EngineStatus("NativeLoggerSDK", safeLoggerAvailable(), ""));
        statuses.add(new EngineStatus("NativeServiceSDK", safeServiceInitialized(), ""));
        statuses.add(new EngineStatus("NativeStorageEngine", safeStorageInitialized(), ""));
        statuses.add(new EngineStatus("NativeThemeEngine", safeThemeInitialized(), ""));
        statuses.add(new EngineStatus("NativePlatformEngine", safePlatformInitialized(), ""));
        statuses.add(new EngineStatus("NativeNetworkEngine", safeNetworkInitialized(), networkDetail()));
        statuses.add(new EngineStatus("NativeSecurityEngine", safeSecurityInitialized(), ""));
        statuses.add(new EngineStatus("NativeAnalyticsEngine", safeAnalyticsInitialized(), ""));
        statuses.add(new EngineStatus("NativeDevToolsSDK", safeDevToolsInitialized(), fpsDetail()));
        return statuses;
    }

    /** AnalyticsEngine-এ ইভেন্ট পাঠায় (ইঞ্জিন না থাকলে নীরবে উপেক্ষা করে)। */
    public void track(String eventName, Map<String, Object> params) {
        try {
            if (AnalyticsEngine.isInitialized()) {
                AnalyticsEngine.trackEvent(eventName, params != null ? params : new HashMap<String, Object>());
            }
        } catch (Throwable ignored) {
            // টেলিমেট্রি ব্যর্থতা অ্যাপের কার্যকারিতা প্রভাবিত করবে না।
        }
    }

    /** NativeLoggerSDK-তে লগ পাঠায়। */
    public void log(int priority, String message) {
        try {
            NativeLogger logger = NativeLogger.getInstance();
            if (logger == null) {
                return;
            }
            switch (priority) {
                case 3:
                    logger.d("RONiLiFEOS", message);
                    break;
                case 4:
                    logger.i("RONiLiFEOS", message);
                    break;
                case 5:
                    logger.w("RONiLiFEOS", message);
                    break;
                case 6:
                    logger.e("RONiLiFEOS", message);
                    break;
                default:
                    logger.i("RONiLiFEOS", message);
                    break;
            }
        } catch (Throwable ignored) {
            // লগিং কখনোই ক্র্যাশ করাবে না।
        }
    }

    public String getRuntimeSummary() {
        if (!isReady()) {
            return lastError.isEmpty() ? "Runtime not ready" : lastError;
        }
        NativeRuntimeBootstrap.BootstrapReport report = bootstrapReport;
        if (report == null) {
            return "Runtime ready";
        }
        return "Bootstrap " + report.getPassedCount() + "/" + report.getSteps().size()
                + " steps succeeded · " + report.getTotalDurationMs() + "ms";
    }

    // ==================== Safe accessors ====================

    private boolean safeRuntimeInitialized() {
        try {
            return NativeRuntime.getInstance().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String runtimeState() {
        try {
            return NativeRuntime.getInstance().getState() != null
                    ? NativeRuntime.getInstance().getState().name() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private boolean safeUiInitialized() {
        try {
            return NativeRuntimeUI.getInstance().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeRuntimeEngineInitialized() {
        try {
            return NativeRuntimeEngine.getInstance().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeBridgeInitialized() {
        try {
            return NativeWebBridgeSDK.getInstance().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeRouterInitialized() {
        try {
            return NativeRouter.getInstance().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeLoggerAvailable() {
        try {
            return NativeLogger.getInstance() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeServiceInitialized() {
        try {
            return NativeServiceSDK.getInstance().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeStorageInitialized() {
        try {
            return StorageEngine.isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeThemeInitialized() {
        try {
            return ThemeEngine.isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safePlatformInitialized() {
        try {
            return PlatformEngine.getInstance().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeNetworkInitialized() {
        try {
            return NetworkEngine.isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String networkDetail() {
        try {
            if (!NetworkEngine.isInitialized()) {
                return "";
            }
            return NetworkEngine.getNetworkMonitor().isConnected() ? "connected" : "disconnected";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private boolean safeSecurityInitialized() {
        try {
            return SecurityEngine.getInstance().isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeAnalyticsInitialized() {
        try {
            return AnalyticsEngine.isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean safeDevToolsInitialized() {
        try {
            return DevToolsSDK.isInitialized();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String fpsDetail() {
        try {
            if (!DevToolsSDK.isInitialized()) {
                return "";
            }
            double fps = DevToolsSDK.getFpsMonitor().getCurrentFps();
            return fps > 0 ? String.format(java.util.Locale.US, "%.0f FPS", fps) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** একটি ইঞ্জিনের স্ট্যাটাস সারি। */
    public static final class EngineStatus {
        private final String name;
        private final boolean initialized;
        private final String detail;

        EngineStatus(String name, boolean initialized, String detail) {
            this.name = name;
            this.initialized = initialized;
            this.detail = detail != null ? detail : "";
        }

        public String getName() {
            return name;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public String getDetail() {
            return detail;
        }
    }

    /** ডায়াগনস্টিকসের জন্য অপরিবর্তনীয় খালি তালিকা। */
    public static List<EngineStatus> emptyStatuses() {
        return Collections.emptyList();
    }
}
