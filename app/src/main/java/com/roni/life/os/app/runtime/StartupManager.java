package com.roni.life.os.app.runtime;

import android.content.Context;
import android.content.Intent;

import com.roni.life.os.app.activity.BootstrapActivity;
import com.roni.life.os.app.activity.ProjectActivity;
import com.roni.life.os.app.activity.SplashActivity;
import com.roni.life.os.app.activity.StartupErrorActivity;
import com.roni.life.os.app.core.AppConfig;
import com.roni.life.os.app.core.ApplicationCore;
import com.roni.life.os.app.project.BootstrapRenderingMode;
import com.roni.life.os.app.project.DefaultProjectResolver;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectStore;
import com.roni.life.os.app.project.ProjectType;
import com.roni.life.os.app.project.StartupDecision;
import com.roni.life.os.app.util.AppLog;

/**
 * The single place that decides where the application starts and which Activity hosts it.
 *
 * <p>Every startup path goes through here — the launcher, the Splash hand-off, and the manual
 * "Open Bootstrap" action. Centralising it is what makes the three project types predictable:
 * the destination is always derived from the resolved roles, never from whichever Activity
 * happened to call {@code startActivity}.</p>
 *
 * <p><b>Loop protection.</b> A process-wide one-shot guard records the destination that was
 * already launched. A second identical launch in the same process is refused, which is what
 * stops Splash → Bootstrap → Splash cycles. The guard is reset when the application process
 * restarts, so a genuine cold start always works.</p>
 */
public final class StartupManager {

    private static final String TAG = "StartupManager";

    /** Destination already launched in this process, or {@code null}. */
    private static volatile String launchedDestination;

    private StartupManager() {
    }

    /** The resolved startup plan. */
    public static final class StartupPlan {
        private final StartupDecision.MainDestination destination;
        private final Project project;
        private final String reason;

        StartupPlan(StartupDecision.MainDestination destination, Project project, String reason) {
            this.destination = destination;
            this.project = project;
            this.reason = reason;
        }

        public StartupDecision.MainDestination getDestination() {
            return destination;
        }

        /** The project to open, or {@code null} for {@link StartupDecision.MainDestination#DASHBOARD}. */
        public Project getProject() {
            return project;
        }

        public String getProjectId() {
            return project != null ? project.getId() : null;
        }

        /** Human-readable explanation, surfaced in logs and the startup diagnostics panel. */
        public String getReason() {
            return reason;
        }

        public boolean isDashboard() {
            return destination == StartupDecision.MainDestination.DASHBOARD;
        }

        /** True when the destination is a Bootstrap project rendered through a WebView. */
        public boolean isWebBootstrap() {
            return destination == StartupDecision.MainDestination.BOOTSTRAP
                    && project != null
                    && project.getBootstrapRenderingMode() == BootstrapRenderingMode.WEB;
        }

        @Override
        public String toString() {
            return "StartupPlan{" + destination + ", project=" + getProjectId() + ", " + reason + "}";
        }
    }

    /**
     * Resolves the startup plan from persisted state.
     *
     * <p>Order of operations mirrors the specification: load configurations, resolve the enabled
     * Splash project, resolve the Bootstrap-at-startup setting, then fall back to the default
     * application project.</p>
     */
    public static StartupPlan resolve(ProjectStore store) {
        if (store == null) {
            return new StartupPlan(StartupDecision.MainDestination.CONFIG_ERROR, null, "no store");
        }

        // Repair stale pointers before reading them, so a deleted project cannot wedge startup.
        store.clearInvalidReferences();

        AppConfig config = store.loadConfig();
        DefaultProjectResolver.Resolution resolution = store.resolveDefaultProject();

        StartupDecision.ConsumerDestination destination = StartupDecision.resolveConsumer(
                config.isSplashEnabled(), config.isDashboardEnabled(), resolution.isResolved());

        logConfig(config);
        logDefault(resolution);

        if (destination == StartupDecision.ConsumerDestination.SPLASH) {
            Project splash = store.activeSplash();
            if (splash != null) {
                logDestination("SPLASH_ACTIVITY", splash.getId());
                return new StartupPlan(StartupDecision.MainDestination.SPLASH, splash,
                        "splash enabled");
            }
            // The native splash is the launcher, so a missing splash project simply means the
            // hand-off destination is the first screen.
            AppLog.w(TAG, "Splash is enabled but no Splash project is registered — skipping it");
            return resolveAfterSplash(store);
        }
        return planFor(destination, resolution);
    }

    /** Maps a consumer destination onto a launchable plan. */
    private static StartupPlan planFor(StartupDecision.ConsumerDestination destination,
                                       DefaultProjectResolver.Resolution resolution) {
        switch (destination) {
            case DASHBOARD:
                logDestination("DASHBOARD", null);
                return new StartupPlan(StartupDecision.MainDestination.DASHBOARD, null,
                        "dashboard enabled");
            case DEFAULT_PROJECT:
                Project project = resolution.getProject();
                logDestination("PROJECT_ACTIVITY", project != null ? project.getId() : null);
                return new StartupPlan(StartupDecision.MainDestination.WEB, project,
                        "default project");
            default:
                logDestination("STARTUP_ERROR", null);
                return new StartupPlan(StartupDecision.MainDestination.CONFIG_ERROR, null,
                        resolution.describe());
        }
    }

    /**
     * Resolves where the Splash project hands off.
     *
     * <p>Called by {@code SplashActivity} when its presentation finishes. It never returns
     * {@link StartupDecision.MainDestination#SPLASH}, which is what prevents a splash loop.</p>
     */
    public static StartupPlan resolveAfterSplash(ProjectStore store) {
        if (store == null) {
            return new StartupPlan(StartupDecision.MainDestination.CONFIG_ERROR, null, "no store");
        }
        store.clearInvalidReferences();

        AppConfig config = store.loadConfig();
        DefaultProjectResolver.Resolution resolution = store.resolveDefaultProject();

        StartupDecision.ConsumerDestination destination =
                StartupDecision.resolveConsumerAfterSplash(
                        config.isDashboardEnabled(), resolution.isResolved());

        logConfig(config);
        logDefault(resolution);
        return planFor(destination, resolution);
    }

    /**
     * Resolves the destination for an Activity that is already past the launcher.
     *
     * <p>Never returns {@code SPLASH}: a host that is already running must not re-enter the
     * splash, which is what keeps Bootstrap → Splash → Bootstrap from looping.</p>
     */
    public static StartupPlan resolveForHost(ProjectStore store) {
        return resolveAfterSplash(store);
    }

    // ==================== Diagnostics ====================

    private static void logConfig(AppConfig config) {
        AppLog.i("STARTUP_CONFIG: splashEnabled=" + config.isSplashEnabled()
                + " dashboardEnabled=" + config.isDashboardEnabled());
    }

    private static void logDefault(DefaultProjectResolver.Resolution resolution) {
        AppLog.i("DEFAULT_PROJECT: selectedId="
                + (resolution.getConfiguredId().isEmpty() ? "none" : resolution.getConfiguredId())
                + " resolvedId=" + (resolution.getProjectId() != null
                        ? resolution.getProjectId() : "none")
                + " source=" + resolution.getSource()
                + " status=" + resolution.getStatus()
                + " projectExists=" + (resolution.getStatus()
                        != DefaultProjectResolver.Status.MISSING)
                + " projectLaunchable=" + resolution.isResolved());
    }

    private static void logDestination(String destination, String projectId) {
        AppLog.i("STARTUP_DESTINATION: " + destination
                + (projectId != null ? " projectId=" + projectId : ""));
    }

    /**
     * Builds the Intent that hosts a plan.
     *
     * <p>Activity selection is driven by project type, never by the caller:</p>
     * <ul>
     *   <li>SPLASH → {@code SplashActivity}</li>
     *   <li>BOOTSTRAP + NATIVE → {@code BootstrapActivity}</li>
     *   <li>BOOTSTRAP + WEB → {@code BootstrapActivity} in web mode</li>
     *   <li>WEB → {@code ProjectActivity}</li>
     *   <li>DASHBOARD → {@code BootstrapActivity} in dashboard mode</li>
     * </ul>
     */
    public static Intent buildIntent(Context context, StartupPlan plan) {
        if (plan == null) {
            return configErrorIntent(context, "no startup plan");
        }
        switch (plan.getDestination()) {
            case SPLASH:
                return splashIntent(context, plan.getProject());
            case BOOTSTRAP:
                return bootstrapIntent(context, plan.getProject(), false);
            case WEB:
                if (plan.getProject() == null) {
                    // A WEB plan without a project is a resolver bug, not a dashboard request.
                    return configErrorIntent(context, "no default project");
                }
                return projectIntent(context, plan.getProject());
            case CONFIG_ERROR:
                return configErrorIntent(context, plan.getReason());
            default:
                return dashboardIntent(context);
        }
    }

    public static Intent splashIntent(Context context, Project project) {
        Intent intent = new Intent(context, SplashActivity.class);
        intent.putExtra(SplashActivity.EXTRA_PROJECT_SPLASH, true);
        if (project != null) {
            intent.putExtra(SplashActivity.EXTRA_PROJECT_ID, project.getId());
        }
        return intent;
    }

    public static Intent bootstrapIntent(Context context, Project project, boolean manual) {
        Intent intent = new Intent(context, BootstrapActivity.class);
        if (project != null) {
            intent.putExtra(BootstrapActivity.EXTRA_BOOTSTRAP_PROJECT_ID, project.getId());
        }
        intent.putExtra(BootstrapActivity.EXTRA_MANUAL_LAUNCH, manual);
        return intent;
    }

    public static Intent projectIntent(Context context, Project project) {
        Intent intent = new Intent(context, ProjectActivity.class);
        if (project != null) {
            intent.putExtra(ProjectActivity.EXTRA_PROJECT_ID, project.getId());
        }
        return intent;
    }

    /** Dashboard intent for automatic routing — refused while the dashboard is disabled. */
    public static Intent dashboardIntent(Context context) {
        return dashboardIntent(context, false);
    }

    /**
     * Builds a dashboard intent.
     *
     * @param manual {@code true} for an explicit user request (Settings → Open Bootstrap),
     *               which is always allowed; {@code false} for automatic routing, which is
     *               refused while the dashboard is disabled
     */
    public static Intent dashboardIntent(Context context, boolean manual) {
        if (!manual && !isDashboardEnabled(context)) {
            return configErrorIntent(context, "dashboard disabled");
        }
        Intent intent = new Intent(context, BootstrapActivity.class);
        intent.putExtra(BootstrapActivity.EXTRA_DASHBOARD_ONLY, true);
        intent.putExtra(BootstrapActivity.EXTRA_MANUAL_LAUNCH, manual);
        return intent;
    }

    /** True when the dashboard is enabled in the persisted configuration. */
    public static boolean isDashboardEnabled(Context context) {
        try {
            return ApplicationCore.get().getStore().loadConfig().isDashboardEnabled();
        } catch (Throwable error) {
            AppLog.e("Unable to read the dashboard configuration", error);
            return true;
        }
    }

    /** Safe fallback screen used when the configuration cannot produce a destination. */
    public static Intent configErrorIntent(Context context, String reason) {
        Intent intent = new Intent(context, StartupErrorActivity.class);
        intent.putExtra(StartupErrorActivity.EXTRA_REASON, reason);
        return intent;
    }

    /**
     * Resolves and launches the startup destination.
     *
     * @return the plan that was launched, or {@code null} when the launch was suppressed
     */
    public static StartupPlan launch(Context context, ProjectStore store) {
        StartupPlan plan = resolve(store);
        if (!tryLaunch(context, plan)) {
            return null;
        }
        return plan;
    }

    /**
     * Launches a plan unless the same destination was already launched in this process.
     *
     * @return {@code true} when the launch happened
     */
    public static boolean tryLaunch(Context context, StartupPlan plan) {
        if (context == null || plan == null) {
            return false;
        }
        String key = destinationKey(plan);
        if (key.equals(launchedDestination)) {
            AppLog.w(TAG, "Suppressed duplicate startup launch for " + key);
            return false;
        }
        launchedDestination = key;
        Intent intent = buildIntent(context, plan);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
        AppLog.i(TAG, "Launched " + plan);
        return true;
    }

    /** Stable identity of a destination, used by the loop guard. */
    private static String destinationKey(StartupPlan plan) {
        return plan.getDestination() + ":" + (plan.getProjectId() != null ? plan.getProjectId() : "-");
    }

    /** True when this exact destination was already launched in the current process. */
    public static boolean wasLaunched(StartupPlan plan) {
        return plan != null && destinationKey(plan).equals(launchedDestination);
    }

    /**
     * Clears the loop guard.
     *
     * <p>Called when the user explicitly returns to the dashboard or restarts the flow, so a
     * deliberate re-entry is not mistaken for a loop.</p>
     */
    public static void resetLaunchGuard() {
        launchedDestination = null;
    }

    /** Records a destination as launched without starting it — used by tests and diagnostics. */
    static void markLaunched(StartupPlan plan) {
        launchedDestination = plan != null ? destinationKey(plan) : null;
    }

    /** True when {@code project} is allowed to be hosted by {@code ProjectActivity}. */
    public static boolean isApplicationProject(Project project) {
        return project != null && project.getProjectType() == ProjectType.WEB;
    }
}
