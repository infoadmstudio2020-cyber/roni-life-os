package com.roni.library.os.bootstrap;

import android.content.Context;

import com.roni.library.analytics.AnalyticsEngine;
import com.roni.library.analytics.NativeAnalyticsEngineConfig;
import com.roni.library.analytics.NativeAnalyticsEngineResult;
import com.roni.library.bridge.BridgeConfig;
import com.roni.library.bridge.NativeWebBridgeSDK;
import com.roni.library.bridge.NativeWebBridgeSDKResult;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.devtools.DevToolsSDK;
import com.roni.library.devtools.NativeDevToolsSDKConfig;
import com.roni.library.devtools.NativeDevToolsSDKResult;
import com.roni.library.logger.NativeLoggerSDK;
import com.roni.library.logger.NativeLoggerSDKConfig;
import com.roni.library.network.NativeNetworkEngineConfig;
import com.roni.library.network.NetworkEngine;
import com.roni.library.os.RuntimeConfiguration;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.PlatformEngineConfig;
import com.roni.library.router.NativeRouter;
import com.roni.library.runtime.NativeRuntimeEngine;
import com.roni.library.runtime.NativeRuntimeEngineConfig;
import com.roni.library.runtime.NativeRuntimeEngineResult;
import com.roni.library.security.SecurityEngine;
import com.roni.library.security.SecurityEngineConfig;
import com.roni.library.service.NativeServiceSDK;
import com.roni.library.service.NativeServiceSDKConfig;
import com.roni.library.storage.NativeStorageEngineConfig;
import com.roni.library.storage.NativeStorageEngineResult;
import com.roni.library.storage.StorageEngine;
import com.roni.library.theme.NativeThemeEngineConfig;
import com.roni.library.theme.NativeThemeEngineResult;
import com.roni.library.theme.ThemeEngine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 10-Step Coordinated Turnkey Bootstrapper for the NativeRuntime Ecosystem.
 * Orchestrates layers L0 through L4 in strict dependency order.
 *
 * <p>Every step invokes the real initialiser of the subsystem it names and records the
 * outcome that initialiser reported. A step is only marked successful when the subsystem
 * reports itself as initialised — the absence of an exception is not treated as success.</p>
 */
public final class NativeRuntimeBootstrap {

    public static final class StepReport implements Serializable {
        private static final long serialVersionUID = 2L;

        private final int stepNumber;
        private final String stepName;
        private final String layer;
        private final boolean success;
        private final boolean skipped;
        private final long durationMs;
        private final String details;

        public StepReport(int stepNumber, String stepName, String layer, boolean success,
                          long durationMs, String details) {
            this(stepNumber, stepName, layer, success, false, durationMs, details);
        }

        public StepReport(int stepNumber, String stepName, String layer, boolean success,
                          boolean skipped, long durationMs, String details) {
            this.stepNumber = stepNumber;
            this.stepName = stepName;
            this.layer = layer;
            this.success = success;
            this.skipped = skipped;
            this.durationMs = durationMs;
            this.details = details;
        }

        public static StepReport skipped(int stepNumber, String stepName, String layer, String reason) {
            return new StepReport(stepNumber, stepName, layer, false, true, 0L, reason);
        }

        public int getStepNumber() { return stepNumber; }
        public String getStepName() { return stepName; }
        public String getLayer() { return layer; }
        public boolean isSuccess() { return success; }
        public boolean isSkipped() { return skipped; }
        public long getDurationMs() { return durationMs; }
        public String getDetails() { return details; }

        @Override
        public String toString() {
            String status = skipped ? "SKIP" : (success ? "PASS" : "FAIL");
            return String.format("[%s] Step %d: %s (%s) in %dms (%s)",
                    status, stepNumber, stepName, layer, durationMs, details);
        }
    }

    public static final class BootstrapReport implements Serializable {
        private static final long serialVersionUID = 2L;

        private final boolean overallSuccess;
        private final long totalDurationMs;
        private final List<StepReport> steps;

        public BootstrapReport(boolean overallSuccess, long totalDurationMs, List<StepReport> steps) {
            this.overallSuccess = overallSuccess;
            this.totalDurationMs = totalDurationMs;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }

        public boolean isOverallSuccess() { return overallSuccess; }
        public long getTotalDurationMs() { return totalDurationMs; }
        public List<StepReport> getSteps() { return steps; }

        public int getPassedCount() {
            int c = 0;
            for (StepReport s : steps) {
                if (s.isSuccess()) c++;
            }
            return c;
        }

        public int getFailedCount() {
            int c = 0;
            for (StepReport s : steps) {
                if (!s.isSuccess() && !s.isSkipped()) c++;
            }
            return c;
        }

        public int getSkippedCount() {
            int c = 0;
            for (StepReport s : steps) {
                if (s.isSkipped()) c++;
            }
            return c;
        }
    }

    /** A single bootstrap step: a name plus the work that must actually run. */
    private interface StepAction {
        /** @return a factual detail string describing what was initialised. */
        String run() throws Exception;
    }

    private static final class Step {
        final int number;
        final String name;
        final String layer;
        final StepAction action;

        Step(int number, String name, String layer, StepAction action) {
            this.number = number;
            this.name = name;
            this.layer = layer;
            this.action = action;
        }
    }

    private NativeRuntimeBootstrap() {}

    /**
     * Executes the synchronized 10-step turnkey startup sequence.
     *
     * @param context Android context (may be null in pure JVM test environments)
     * @param config  Runtime configuration
     * @return Standardized bootstrap report
     */
    public static BootstrapReport execute(Context context, RuntimeConfiguration config) {
        return execute(context, config, false);
    }

    /**
     * Executes the synchronized 10-step turnkey startup sequence.
     *
     * @param context  Android context (may be null in pure JVM test environments)
     * @param config   Runtime configuration
     * @param failFast when true, the first failed step marks every remaining step as skipped
     * @return Standardized bootstrap report
     */
    public static BootstrapReport execute(Context context, RuntimeConfiguration config, boolean failFast) {
        long startTime = System.currentTimeMillis();
        final RuntimeConfiguration cfg = config != null
                ? config
                : RuntimeConfiguration.builder().build();

        List<Step> plan = buildPlan(context, cfg);
        List<StepReport> steps = new ArrayList<>(plan.size());
        boolean aborted = false;

        for (Step step : plan) {
            if (aborted) {
                steps.add(StepReport.skipped(step.number, step.name, step.layer,
                        "Skipped: an earlier step failed and failFast is enabled"));
                continue;
            }
            StepReport report = runStep(step);
            steps.add(report);
            if (!report.isSuccess() && failFast) {
                aborted = true;
            }
        }

        boolean overallSuccess = true;
        for (StepReport s : steps) {
            if (!s.isSuccess()) {
                overallSuccess = false;
                break;
            }
        }

        return new BootstrapReport(overallSuccess, System.currentTimeMillis() - startTime, steps);
    }

    private static StepReport runStep(Step step) {
        long stepStart = System.currentTimeMillis();
        try {
            String details = step.action.run();
            return new StepReport(step.number, step.name, step.layer, true,
                    System.currentTimeMillis() - stepStart, details);
        } catch (Throwable t) {
            String message = t.getMessage() != null ? t.getMessage() : "no message";
            return new StepReport(step.number, step.name, step.layer, false,
                    System.currentTimeMillis() - stepStart,
                    t.getClass().getSimpleName() + ": " + message);
        }
    }

    private static List<Step> buildPlan(final Context context, final RuntimeConfiguration cfg) {
        List<Step> plan = new ArrayList<>(10);

        // Step 1: L0 Universal Contracts & Foundation
        plan.add(new Step(1, "Contracts & ABI Foundation", "L0", () -> {
            String appId = cfg.getAppId();
            if (appId == null || appId.trim().isEmpty()) {
                throw new IllegalStateException("appId is empty");
            }
            return "appId=" + appId + ", environment=" + cfg.getEnvironment();
        }));

        // Step 2: L1 Logger Subsystem
        plan.add(new Step(2, "Logging Subsystem (NativeLoggerSDK)", "L1", () -> {
            LogLevel level = LogLevel.fromString(cfg.getLogLevel(), LogLevel.INFO);
            NativeLoggerSDKConfig loggerConfig = new NativeLoggerSDKConfig.Builder()
                    .debugEnabled(cfg.isDebugEnabled())
                    .minimumLevel(level)
                    .build();
            NativeLoggerSDK.getInstance().init(context, loggerConfig);
            if (!NativeLoggerSDK.getInstance().isInitialized()) {
                throw new IllegalStateException("NativeLoggerSDK reported not initialized");
            }
            return "level=" + level.name();
        }));

        // Step 3: L1 Service SDK Registry
        plan.add(new Step(3, "Service Directory (NativeServiceSDK)", "L1", () -> {
            NativeServiceSDK.getInstance().init(context, new NativeServiceSDKConfig.Builder()
                    .debugEnabled(cfg.isDebugEnabled())
                    .build());
            if (!NativeServiceSDK.getInstance().isInitialized()) {
                throw new IllegalStateException("NativeServiceSDK reported not initialized");
            }
            return "service registry ready";
        }));

        // Step 4: L2 Security Engine
        plan.add(new Step(4, "Security Engine (NativeSecurityEngine)", "L2", () -> {
            SecurityEngine.getInstance().init(context, new SecurityEngineConfig.Builder()
                    .debugEnabled(cfg.isDebugEnabled())
                    .build());
            if (!SecurityEngine.getInstance().isInitialized()) {
                throw new IllegalStateException("SecurityEngine reported not initialized");
            }
            return "state=" + SecurityEngine.getInstance().getState();
        }));

        // Step 5: L2 Storage Engine
        plan.add(new Step(5, "Storage Engine (NativeStorageEngine)", "L2", () -> {
            NativeStorageEngineConfig storageConfig = new NativeStorageEngineConfig.Builder()
                    .debugEnabled(cfg.isDebugEnabled())
                    .build();
            NativeStorageEngineResult<Boolean> result = StorageEngine.init(context, storageConfig);
            if (result == null || !result.isSuccess()) {
                throw new IllegalStateException("StorageEngine: "
                        + (result != null ? result.getErrorMessage() : "null result"));
            }
            return "encryption=" + cfg.isStorageEncryptionEnabled();
        }));

        // Step 6: L2 Platform Engine
        plan.add(new Step(6, "Platform Engine (NativePlatformEngine)", "L2", () -> {
            PlatformEngine.getInstance().init(context, new PlatformEngineConfig.Builder()
                    .debugEnabled(cfg.isDebugEnabled())
                    .build());
            if (!PlatformEngine.getInstance().isInitialized()) {
                throw new IllegalStateException("PlatformEngine reported not initialized");
            }
            return "state=" + PlatformEngine.getInstance().getState();
        }));

        // Step 7: L2 Network Engine
        plan.add(new Step(7, "Network Engine (NativeNetworkEngine)", "L2", () -> {
            NetworkEngine.init(context, new NativeNetworkEngineConfig.Builder()
                    .debugMode(cfg.isDebugEnabled())
                    .connectTimeoutMs(cfg.getNetworkTimeoutMs())
                    .readTimeoutMs(cfg.getNetworkTimeoutMs())
                    .build());
            if (!NetworkEngine.isInitialized()) {
                throw new IllegalStateException("NetworkEngine reported not initialized");
            }
            return "timeout=" + cfg.getNetworkTimeoutMs() + "ms, retries=" + cfg.getMaxNetworkRetries();
        }));

        // Step 8: L2 Analytics Engine
        plan.add(new Step(8, "Analytics Engine (NativeAnalyticsEngine)", "L2", () -> {
            NativeAnalyticsEngineResult<Void> result = AnalyticsEngine.init(context,
                    new NativeAnalyticsEngineConfig.Builder()
                            .debugEnabled(cfg.isDebugEnabled())
                            .build());
            if (result == null || !result.isSuccess()) {
                throw new IllegalStateException("AnalyticsEngine: "
                        + (result != null ? result.getErrorMessage() : "null result"));
            }
            return "telemetry=" + cfg.isTelemetryEnabled();
        }));

        // Step 9: L2 Theme Engine & DevTools
        plan.add(new Step(9, "Theme & DevTools (NativeThemeEngine & DevTools)", "L2", () -> {
            NativeThemeEngineResult<Boolean> theme = ThemeEngine.init(context,
                    new NativeThemeEngineConfig.Builder()
                            .debugEnabled(cfg.isDebugEnabled())
                            .build());
            if (theme == null || !theme.isSuccess()) {
                throw new IllegalStateException("ThemeEngine: "
                        + (theme != null ? theme.getErrorMessage() : "null result"));
            }
            NativeDevToolsSDKResult<Boolean> devTools = DevToolsSDK.init(context,
                    new NativeDevToolsSDKConfig.Builder()
                            .debugEnabled(cfg.isDebugEnabled())
                            .build());
            if (devTools == null || !devTools.isSuccess()) {
                throw new IllegalStateException("DevToolsSDK: "
                        + (devTools != null ? devTools.getErrorMessage() : "null result"));
            }
            return "theme registry + devtools bus ready";
        }));

        // Step 10: L3 & L4 Router, WebBridge, and Runtime Engine
        plan.add(new Step(10, "Router, Bridge & Runtime (L3/L4 Master)", "L3/L4", () -> {
            NativeRouter router = NativeRouter.getInstance();
            if (!router.isInitialized()) {
                router.initialize();
            }
            if (!router.isInitialized()) {
                throw new IllegalStateException("NativeRouter reported not initialized");
            }

            NativeWebBridgeSDKResult<?> bridge = NativeWebBridgeSDK.getInstance().init(context,
                    BridgeConfig.builder()
                            .interfaceName("NativeRuntimeBridge")
                            .debugLogging(cfg.isDebugEnabled())
                            .build());
            if (bridge == null || !bridge.isSuccess()) {
                throw new IllegalStateException("NativeWebBridgeSDK: "
                        + (bridge != null ? bridge.getErrorMessage() : "null result"));
            }

            NativeRuntimeEngineResult<Boolean> engine = NativeRuntimeEngine.getInstance().init(
                    context, NativeRuntimeEngineConfig.defaultConfiguration());
            if (engine == null || !engine.isSuccess()) {
                throw new IllegalStateException("NativeRuntimeEngine: "
                        + (engine != null ? engine.getErrorMessage() : "null result"));
            }

            return "router + bridge + runtime engine ready";
        }));

        return plan;
    }
}
