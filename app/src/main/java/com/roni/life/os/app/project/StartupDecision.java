package com.roni.life.os.app.project;

/**
 * The complete startup decision logic for the three-type project system.
 *
 * <p>No Android dependencies — every branch is unit-testable on the JVM.</p>
 *
 * <p><b>Independence rules.</b> Splash and Bootstrap are separate settings:</p>
 * <ul>
 *   <li>a Splash project never enables Bootstrap;</li>
 *   <li>a Bootstrap project never enables Splash;</li>
 *   <li>neither is ever treated as the default application project;</li>
 *   <li>a WEB project always launches through its own Project Activity.</li>
 * </ul>
 */
public final class StartupDecision {

    private StartupDecision() {
    }

    /** Where the application goes first. */
    public enum MainDestination {
        /** Show the Splash project, then continue to {@link SplashDestination}. */
        SPLASH,
        /** Open the Bootstrap project (native or web rendering). */
        BOOTSTRAP,
        /** Open the default WEB application project. */
        WEB,
        /** No usable project — show the project dashboard. */
        DASHBOARD,
        /** Dashboard is disabled and no usable default project exists. */
        CONFIG_ERROR
    }

    /** Where the Splash project hands off to. */
    public enum SplashDestination {
        BOOTSTRAP,
        WEB,
        DASHBOARD
    }

    /**
     * Consumer startup destination, driven purely by the two configuration flags.
     *
     * <p>This is the authoritative consumer matrix. It is deliberately independent of the
     * project-role system: a Splash or Dashboard being switched off must never prevent a
     * project from running.</p>
     */
    public enum ConsumerDestination {
        /** Show the splash screen, then continue to the post-splash destination. */
        SPLASH,
        /** Show the project dashboard. */
        DASHBOARD,
        /** Open the configured default project directly. */
        DEFAULT_PROJECT,
        /** Dashboard is off and no usable default project exists — show a safe fallback. */
        CONFIG_ERROR
    }

    /**
     * Resolves the consumer startup destination.
     *
     * <pre>
     * Splash | Dashboard | Startup
     * -------+-----------+---------------------------
     *  ON    |    ON     | Splash → Dashboard
     *  OFF   |    ON     | Dashboard
     *  ON    |    OFF    | Splash → Default Project
     *  OFF   |    OFF    | Default Project
     * </pre>
     *
     * @param splashEnabled     the splash setting is on
     * @param dashboardEnabled  the dashboard setting is on
     * @param hasDefaultProject a usable default project was resolved
     */
    public static ConsumerDestination resolveConsumer(boolean splashEnabled,
                                                      boolean dashboardEnabled,
                                                      boolean hasDefaultProject) {
        if (splashEnabled) {
            return ConsumerDestination.SPLASH;
        }
        return resolveConsumerAfterSplash(dashboardEnabled, hasDefaultProject);
    }

    /**
     * Resolves where the splash hands off.
     *
     * <p>Never returns {@link ConsumerDestination#SPLASH}, which is what makes a splash loop
     * impossible. When the dashboard is off the default project is the only legal destination;
     * a missing default project is a configuration error, never a random project.</p>
     */
    public static ConsumerDestination resolveConsumerAfterSplash(boolean dashboardEnabled,
                                                                 boolean hasDefaultProject) {
        if (dashboardEnabled) {
            return ConsumerDestination.DASHBOARD;
        }
        return hasDefaultProject
                ? ConsumerDestination.DEFAULT_PROJECT
                : ConsumerDestination.CONFIG_ERROR;
    }

    /**
     * Back-navigation policy for a project runtime whose web history is empty.
     *
     * @param dashboardEnabled whether the dashboard is available to return to
     * @return {@code true} when the dashboard should be opened, {@code false} when the native
     *         exit confirmation must be shown instead
     */
    public static boolean shouldReturnToDashboard(boolean dashboardEnabled) {
        return dashboardEnabled;
    }

    /** Per-project startup flow, retained for the existing project-level runner. */
    public enum ProjectStartup {
        SPLASH_THEN_BOOTSTRAP,
        BOOTSTRAP_ONLY,
        SPLASH_ONLY,
        DIRECT
    }

    /**
     * Resolves the first destination of the application.
     *
     * <pre>
     * Splash enabled + Splash project | Bootstrap at startup + Bootstrap project | Destination
     * --------------------------------+------------------------------------------+------------
     *            yes                  |                  yes                     | SPLASH
     *            yes                  |                  no                      | SPLASH
     *            no                   |                  yes                     | BOOTSTRAP
     *            no                   |                  no                      | WEB / DASHBOARD
     * </pre>
     *
     * @param splashEnabled      the splash setting is on
     * @param hasSplashProject   an active SPLASH project resolved
     * @param bootstrapAtStartup the active BOOTSTRAP project asked to open at startup
     * @param hasBootstrapProject an active BOOTSTRAP project resolved
     * @param hasWebProject      a default WEB application project resolved
     */
    public static MainDestination resolveMain(boolean splashEnabled,
                                              boolean hasSplashProject,
                                              boolean bootstrapAtStartup,
                                              boolean hasBootstrapProject,
                                              boolean hasWebProject) {
        if (splashEnabled && hasSplashProject) {
            return MainDestination.SPLASH;
        }
        return toMain(resolveAfterSplash(bootstrapAtStartup, hasBootstrapProject, hasWebProject));
    }

    /**
     * Resolves where the Splash project hands off.
     *
     * <p>Bootstrap is only entered when it is explicitly enabled <em>and</em> a Bootstrap
     * project exists. Otherwise the default WEB project wins, and the dashboard is the last
     * resort.</p>
     */
    public static SplashDestination resolveAfterSplash(boolean bootstrapAtStartup,
                                                       boolean hasBootstrapProject,
                                                       boolean hasWebProject) {
        if (bootstrapAtStartup && hasBootstrapProject) {
            return SplashDestination.BOOTSTRAP;
        }
        return hasWebProject ? SplashDestination.WEB : SplashDestination.DASHBOARD;
    }

    /** Maps a splash destination onto a main destination. */
    public static MainDestination toMain(SplashDestination destination) {
        if (destination == SplashDestination.BOOTSTRAP) {
            return MainDestination.BOOTSTRAP;
        }
        if (destination == SplashDestination.WEB) {
            return MainDestination.WEB;
        }
        return MainDestination.DASHBOARD;
    }

    /**
     * True when the Splash project should actually be rendered.
     *
     * <p>Both conditions are required: the setting must be on <em>and</em> a Splash project must
     * exist. A disabled splash skips the Activity entirely — it is never inflated.</p>
     */
    public static boolean shouldShowSplash(boolean splashEnabled, boolean hasSplashProject) {
        return splashEnabled && hasSplashProject;
    }

    /**
     * True when the Bootstrap project should open automatically.
     *
     * <p>Requires an explicit {@code bootstrapAtStartup} opt-in on the project itself. A
     * Bootstrap project is never opened just because it exists.</p>
     */
    public static boolean shouldAutoOpenBootstrap(boolean bootstrapAtStartup,
                                                  boolean hasBootstrapProject) {
        return bootstrapAtStartup && hasBootstrapProject;
    }

    /**
     * Project-Level Startup Matrix — retained for the existing per-project runner.
     *
     * <pre>
     * Project Splash | Project Bootstrap | Startup
     * ---------------+-------------------+--------------------------------------
     *      ON        |        ON         | Splash → Bootstrap → ProjectActivity
     *      OFF       |        ON         | Bootstrap → ProjectActivity
     *      ON        |        OFF        | Splash → ProjectActivity
     *      OFF       |        OFF        | ProjectActivity
     * </pre>
     */
    public static ProjectStartup resolveProject(boolean projectSplashEnabled,
                                                boolean projectBootstrapEnabled) {
        if (projectSplashEnabled && projectBootstrapEnabled) {
            return ProjectStartup.SPLASH_THEN_BOOTSTRAP;
        }
        if (projectBootstrapEnabled) {
            return ProjectStartup.BOOTSTRAP_ONLY;
        }
        if (projectSplashEnabled) {
            return ProjectStartup.SPLASH_ONLY;
        }
        return ProjectStartup.DIRECT;
    }

    /** True when the project splash should be shown. */
    public static boolean shouldShowProjectSplash(ProjectStartup startup) {
        return startup == ProjectStartup.SPLASH_THEN_BOOTSTRAP || startup == ProjectStartup.SPLASH_ONLY;
    }

    /** True when the project bootstrap process should run. */
    public static boolean shouldRunProjectBootstrap(ProjectStartup startup) {
        return startup == ProjectStartup.SPLASH_THEN_BOOTSTRAP || startup == ProjectStartup.BOOTSTRAP_ONLY;
    }
}
