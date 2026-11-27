package com.roni.life.os.app.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;

import com.roni.library.ui.NativeRuntimeUIConfig;
import com.roni.library.ui.builder.SystemUIBuilders;
import com.roni.library.ui.web.WebHostConfig;
import com.roni.life.os.app.R;
import com.roni.life.os.app.core.AppConfig;
import com.roni.life.os.app.core.ApplicationCore;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.StartupDecision;
import com.roni.life.os.app.runtime.ProjectLaunchRouter;
import com.roni.life.os.app.runtime.SplashWebSource;
import com.roni.life.os.app.runtime.StartupManager;
import com.roni.life.os.app.runtime.StartupTrace;
import com.roni.life.os.app.util.AppLog;

/**
 * Startup router and splash host.
 *
 * <p>The splash is fully optional. When it is disabled — or its mode is {@code NONE} — no view is
 * inflated and the activity routes to the next destination immediately, so a disabled splash can
 * never produce a blank frame or a startup delay.</p>
 *
 * <p>Two presentations are supported: a native XML splash and the bundled web splash rendered in a
 * WebView. The web splash is the default: it loads the resolved source from
 * {@code system/splash/default} or {@code system/splash/runtime} so the page is authored once as
 * HTML/CSS/JS, and falls back to the inline page when no source validates. Every presentation
 * honours the configured
 * logo, background, accent colour, animation, duration, fullscreen flag and tap-to-skip flag.</p>
 */
public class SplashActivity extends com.roni.library.os.activity.NativeRuntimeActivity {

    private static final String TAG = "SplashActivity";

    /** Opens this activity as a project splash: show it, then open {@link #EXTRA_PROJECT_ID}. */
    public static final String EXTRA_PROJECT_SPLASH = "extra_project_splash";
    /** Project whose splash is shown (and opened afterwards). */
    public static final String EXTRA_PROJECT_ID = "extra_project_id";

    private static final int DEFAULT_BACKGROUND = Color.rgb(8, 13, 27);
    private static final int DEFAULT_ACCENT = Color.rgb(108, 92, 231);

    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean navigationStarted;
    private WebHostConfig webHostConfig;
    private WebView splashWebView;
    private boolean projectSplash;
    private Project splashProject;

    // ==================== Lifecycle ====================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        projectSplash = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_PROJECT_SPLASH, false);
        if (projectSplash) {
            splashProject = resolveProjectExtra();
            if (splashProject == null) {
                // The project was deleted between launches: fall back to the normal startup.
                projectSplash = false;
            }
        }

        AppConfig config = appConfig();

        if (config == null || !config.shouldShowSplash()) {
            AppLog.i("Splash disabled — routing directly to the next destination");
            continueStartup();
            return;
        }

        applySplashSystemBars(config);

        if (config.isWebSplash()) {
            showWebSplash(config);
        } else {
            showNativeSplash(config);
        }
    }

    // ==================== Native splash ====================

    private void showNativeSplash(@NonNull AppConfig config) {
        View root = LayoutInflater.from(this)
                .inflate(R.layout.view_splash_native, null, false);

        int background = config.getSplashBackgroundColor() != 0
                ? config.getSplashBackgroundColor() : DEFAULT_BACKGROUND;
        int accent = config.getSplashAccentColor() != 0
                ? config.getSplashAccentColor() : DEFAULT_ACCENT;

        root.setBackgroundColor(background);

        View accentBar = root.findViewById(R.id.splash_accent);
        if (accentBar != null) {
            accentBar.setBackgroundColor(accent);
        }

        TextView logo = root.findViewById(R.id.splash_logo);
        if (logo != null) {
            String text = projectSplash && splashProject != null
                    ? splashProject.getName() : config.getSplashLogoText();
            logo.setText(text.isEmpty() ? getString(R.string.app_name) : text);
            logo.setTextColor(accent);
        }

        TextView subtitle = root.findViewById(R.id.splash_subtitle);
        if (subtitle != null) {
            subtitle.setTextColor(blend(accent, Color.WHITE, 0.55f));
        }

        TextView hint = root.findViewById(R.id.splash_hint);
        if (hint != null) {
            hint.setVisibility(config.isSplashSkipOnTap() ? View.VISIBLE : View.GONE);
        }

        View content = root.findViewById(R.id.splash_content);
        applyAnimation(content != null ? content : root, config);

        if (config.isSplashSkipOnTap()) {
            root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AppLog.i("Splash skipped by tap");
                    finishSplash();
                }
            });
        }

        showContentSafely(root);
        scheduleFinish(config.getSplashDurationMs());
    }

    // ==================== Web splash ====================

    private void showWebSplash(@NonNull AppConfig config) {
        int background = config.getSplashBackgroundColor() != 0
                ? config.getSplashBackgroundColor() : DEFAULT_BACKGROUND;
        int accent = config.getSplashAccentColor() != 0
                ? config.getSplashAccentColor() : DEFAULT_ACCENT;

        WebView webView = new WebView(this);
        splashWebView = webView;

        WebSettings settings = webView.getSettings();
        // The bundled page animates its own loading state, so JavaScript stays on. Everything it
        // loads is local: file access is enabled for the asset folder, network loads are blocked.
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setBackgroundColor(background);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        SplashWebSource.Source source = SplashWebSource.resolve(this);
        if (source != null) {
            AppLog.i(TAG, "Web splash: " + source);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    applyWebSplashConfig(view, config, background, accent);
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description,
                                            String failingUrl) {
                    // The asset copy is unreadable — keep the splash on screen with the inline page.
                    AppLog.w(TAG, "Web splash failed (" + errorCode + "): " + description);
                    view.loadDataWithBaseURL(null, buildSplashHtml(config, background, accent),
                            "text/html", "UTF-8", null);
                }
            });
            webView.loadUrl(source.getUrl());
        } else {
            AppLog.w(TAG, "No splash asset available — using the inline fallback");
            webView.loadDataWithBaseURL(null, buildSplashHtml(config, background, accent),
                    "text/html", "UTF-8", null);
        }

        if (config.isSplashSkipOnTap()) {
            webView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finishSplash();
                }
            });
        }

        showContentSafely(webView);
        scheduleFinish(config.getSplashDurationMs());
    }

    /**
     * Applies the configured colours, logo text and animation to the bundled splash page.
     *
     * <p>The page owns its layout and copy; only the values the user can configure are injected,
     * so the shipped design stays intact when a setting is left at its default.</p>
     */
    private void applyWebSplashConfig(@NonNull WebView view, @NonNull AppConfig config,
                                      int background, int accent) {
        StringBuilder css = new StringBuilder();
        css.append("body{background:").append(hex(background)).append(" !important;}");
        css.append(".brand-title span{color:").append(hex(accent)).append(" !important;}");
        css.append(".loading-progress{background:").append(hex(accent)).append(" !important;}");
        css.append(".logo-ring{border-color:").append(hex(accent)).append(" !important;}");
        if (AppConfig.ANIM_NONE.equals(config.getSplashAnimation())) {
            css.append("*,*::before,*::after{animation:none !important;")
                    .append("transition:none !important;}");
        }

        String logo = projectSplash && splashProject != null
                ? splashProject.getName() : config.getSplashLogoText();

        StringBuilder js = new StringBuilder("(function(){");
        js.append("var s=document.createElement('style');");
        js.append("s.textContent=").append(jsString(css.toString())).append(";");
        js.append("document.head.appendChild(s);");
        if (logo != null && !logo.trim().isEmpty()) {
            js.append("var t=document.querySelector('.brand-title');");
            js.append("if(t){t.textContent=").append(jsString(logo.trim())).append(";}");
        }
        js.append("})();");

        view.evaluateJavascript(js.toString(), null);
    }

    /** Inline HTML splash — the last resort when no bundled page can be read. */
    private String buildSplashHtml(@NonNull AppConfig config, int background, int accent) {
        String logo = config.getSplashLogoText();
        if (logo.isEmpty()) {
            logo = getString(R.string.app_name);
        }
        String animation = config.getSplashAnimation();
        String animationCss;
        if (AppConfig.ANIM_NONE.equals(animation)) {
            animationCss = "";
        } else if (AppConfig.ANIM_SLIDE_UP.equals(animation)) {
            animationCss = "@keyframes enter{from{opacity:0;transform:translateY(28px)}"
                    + "to{opacity:1;transform:translateY(0)}}";
        } else if (AppConfig.ANIM_SCALE.equals(animation)) {
            animationCss = "@keyframes enter{from{opacity:0;transform:scale(.86)}"
                    + "to{opacity:1;transform:scale(1)}}";
        } else if (AppConfig.ANIM_PULSE.equals(animation)) {
            animationCss = "@keyframes enter{0%{opacity:0;transform:scale(.94)}"
                    + "60%{opacity:1;transform:scale(1.04)}100%{transform:scale(1)}}";
        } else {
            animationCss = "@keyframes enter{from{opacity:0}to{opacity:1}}";
        }

        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>"
                + "html,body{margin:0;height:100%;background:" + hex(background) + ";"
                + "display:flex;align-items:center;justify-content:center;"
                + "font-family:-apple-system,Roboto,sans-serif;}"
                + ".wrap{text-align:center;animation:enter .55s ease-out both;}"
                + ".bar{width:56px;height:6px;border-radius:3px;background:" + hex(accent)
                + ";margin:0 auto 20px;}"
                + ".logo{font-size:30px;font-weight:700;letter-spacing:.12em;color:" + hex(accent)
                + ";}"
                + ".sub{margin-top:10px;font-size:13px;color:" + hex(blend(accent, Color.WHITE, 0.55f))
                + ";}"
                + ".dot{margin-top:28px;width:120px;height:3px;border-radius:2px;"
                + "background:rgba(255,255,255,.12);overflow:hidden;margin-left:auto;margin-right:auto;}"
                + ".dot i{display:block;width:40%;height:100%;background:" + hex(accent)
                + ";animation:slide 1.1s ease-in-out infinite;}"
                + "@keyframes slide{0%{transform:translateX(-100%)}100%{transform:translateX(320%)}}"
                + animationCss
                + "</style></head><body><div class=\"wrap\">"
                + "<div class=\"bar\"></div>"
                + "<div class=\"logo\">" + escapeHtml(logo) + "</div>"
                + "<div class=\"sub\">" + escapeHtml(getString(R.string.splash_subtitle)) + "</div>"
                + "<div class=\"dot\"><i></i></div>"
                + "</div></body></html>";
    }

    // ==================== Animation ====================

    private void applyAnimation(@NonNull View target, @NonNull AppConfig config) {
        String animation = config.getSplashAnimation();
        long duration = Math.min(600L, Math.max(120L, config.getSplashDurationMs() / 2L));

        if (AppConfig.ANIM_NONE.equals(animation)) {
            target.setAlpha(1f);
            return;
        }

        if (AppConfig.ANIM_SLIDE_UP.equals(animation)) {
            target.setAlpha(0f);
            target.setTranslationY(60f);
            target.animate().alpha(1f).translationY(0f)
                    .setDuration(duration)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
            return;
        }

        if (AppConfig.ANIM_SCALE.equals(animation)) {
            target.setAlpha(0f);
            target.setScaleX(0.86f);
            target.setScaleY(0.86f);
            target.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(duration)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
            return;
        }

        if (AppConfig.ANIM_PULSE.equals(animation)) {
            target.setAlpha(0f);
            target.animate().alpha(1f).setDuration(duration).start();
            target.postDelayed(new Runnable() {
                @Override
                public void run() {
                    target.animate().scaleX(1.05f).scaleY(1.05f)
                            .setDuration(420L)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    target.animate().scaleX(1f).scaleY(1f)
                                            .setDuration(420L).start();
                                }
                            }).start();
                }
            }, duration);
            return;
        }

        // FADE (default)
        target.setAlpha(0f);
        target.animate().alpha(1f).setDuration(duration).start();
    }

    // ==================== NativeRuntimeUI ====================

    @Override
    protected NativeRuntimeUIConfig onConfigureUI(
            @NonNull NativeRuntimeUIConfig.Builder builder) {

        AppConfig config = appConfig();
        int background = config != null && config.getSplashBackgroundColor() != 0
                ? config.getSplashBackgroundColor() : DEFAULT_BACKGROUND;
        boolean fullscreen = config == null || config.isSplashFullscreen();

        webHostConfig = WebHostConfig.builder()
                .javaScriptEnabled(true)
                .domStorageEnabled(false)
                .databaseEnabled(false)
                .allowFileAccess(false)
                .allowContentAccess(false)
                .allowZoom(false)
                .showProgressBar(false)
                .bridgeEnabled(false)
                .openExternalLinksInBrowser(true)
                .mediaPlaybackRequiresUserGesture(true)
                .cacheMode(WebHostConfig.CACHE_DEFAULT)
                .backgroundColor(background)
                .safeBrowsingEnabled(true)
                .hardwareAcceleration(true)
                .build();

        builder.toolbarTitle(getString(R.string.app_name))
                .toolbarSubtitle("Starting…")
                .enableToolbar(false)
                .enableSystemUI(!fullscreen)
                .statusBar(
                        SystemUIBuilders.StatusBarConfig.builder()
                                .enabled(!fullscreen)
                                .color(Color.TRANSPARENT)
                                .lightIcons(false)
                                .translucent(true)
                                .contrastEnforced(false)
                                .build()
                )
                .edgeToEdge(true)
                .fullScreenWeb(true)
                .enableDrawer(false)
                .enableBottomNav(false)
                .enableWebView(true)
                .themeMode("SYSTEM")
                .backgroundColor(background);

        return builder.build();
    }

    // ==================== Startup decision ====================

    private void scheduleFinish(int durationMs) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finishSplash();
            }
        }, Math.max(AppConfig.MIN_SPLASH_DURATION_MS, durationMs));
    }

    private void finishSplash() {
        if (navigationStarted) {
            return;
        }
        continueStartup();
    }

    private void continueStartup() {
        if (navigationStarted) {
            return;
        }
        navigationStarted = true;
        StartupTrace.record(this, "SPLASH_ENTER",
                "projectSplash=" + projectSplash + " splashProject="
                        + (splashProject != null ? splashProject.getId() : "none"));

        // A project splash exists only to hand over to its project.
        if (projectSplash) {
            if (splashProject != null && splashProject.isSplashProject()) {
                // A SPLASH-type project is a presentation, never an application host.
                // Dashboard OFF must never be bypassed by this hand-off: fall through to the
                // authoritative default project instead of assuming the dashboard.
                boolean dashboardEnabled = ApplicationCore.get() != null
                        && ApplicationCore.get().getStore() != null
                        && ApplicationCore.get().getStore().loadConfig().isDashboardEnabled();
                if (dashboardEnabled) {
                    openDashboard();
                } else {
                    Project fallback = ApplicationCore.get() != null
                            && ApplicationCore.get().getStore() != null
                            ? ApplicationCore.get().getStore().defaultProject() : null;
                    if (fallback != null) {
                        AppLog.i("Splash hand-off: dashboard disabled — opening default project "
                                + fallback.getId());
                        openProject(fallback);
                    } else {
                        openConfigError("no default project");
                    }
                }
            } else {
                openProject(splashProject);
            }
            return;
        }

        try {
            StartupManager.StartupPlan plan =
                    StartupManager.resolveAfterSplash(ApplicationCore.get().getStore());
            AppLog.i("Splash hand-off: " + plan);
            StartupTrace.record(this, "STARTUP_PLAN", String.valueOf(plan));
            StartupTrace.record(this, "STARTUP_DESTINATION", plan.getDestination().name());

            switch (plan.getDestination()) {
                case BOOTSTRAP:
                    openBootstrap(plan.getProject());
                    break;
                case WEB:
                    openProject(plan.getProject());
                    break;
                case CONFIG_ERROR:
                    openConfigError(plan.getReason());
                    break;
                default:
                    openDashboard();
                    break;
            }
        } catch (Throwable error) {
            AppLog.e("Startup decision failed", error);
            // A broken decision must never open a dashboard the user disabled.
            if (StartupManager.isDashboardEnabled(this)) {
                openDashboard();
            } else {
                openConfigError("startup decision failed: " + error.getMessage());
            }
        }
    }

    // ==================== Navigation ====================

    private void openBootstrap() {
        openBootstrap(null);
    }

    private void openBootstrap(Project bootstrap) {
        Intent intent = StartupManager.bootstrapIntent(this, bootstrap, false);
        // CLEAR_TASK: the dashboard replaces the whole root task, so a stale BootstrapActivity
        // from an earlier launch can never sit underneath the splash.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAndRemoveTask();
    }

    /** Opens the project dashboard. Used when no project can be resolved. */
    private void openDashboard() {
        StartupTrace.record(this, "OPEN_DASHBOARD", "dashboard enabled");
        Intent intent = StartupManager.dashboardIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAndRemoveTask();
    }

    /**
     * Dashboard disabled and no usable default project — show the safe fallback instead of
     * opening a dashboard the user switched off.
     */
    private void openConfigError(String reason) {
        StartupTrace.record(this, "CONFIG_ERROR", reason);
        Intent intent = StartupManager.configErrorIntent(this, reason);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAndRemoveTask();
    }

    /** Shows this project's splash, then opens the project. */
    private void openProjectSplash(Project project) {
        Intent intent = new Intent(this, SplashActivity.class);
        intent.putExtra(EXTRA_PROJECT_SPLASH, true);
        intent.putExtra(EXTRA_PROJECT_ID, project.getId());
        // A clean root task: the project splash must not inherit the dashboard underneath it.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        finishAndRemoveTask();
    }

    @Nullable
    private Project resolveProjectExtra() {
        try {
            String id = getIntent() != null
                    ? getIntent().getStringExtra(EXTRA_PROJECT_ID) : null;
            return id != null && !id.trim().isEmpty()
                    ? ApplicationCore.get().getStore().findById(id) : null;
        } catch (Throwable error) {
            AppLog.e("Unable to resolve the splash project", error);
            return null;
        }
    }

    private void openProject(Project project) {
        if (project == null) {
            // No project to open. Never fall back to a dashboard the user disabled.
            if (StartupManager.isDashboardEnabled(this)) {
                openBootstrap();
            } else {
                openConfigError("no default project");
            }
            return;
        }
        // The startup router uses the same open/restore path as the dashboard, so a project
        // that is already running is restored instead of being started a second time.
        ProjectLaunchRouter.Result result = ProjectLaunchRouter.open(this, project);
        AppLog.i("PROJECT_LAUNCH: projectId=" + project.getId()
                + " entryPoint=" + (project.getEntryPoint().isEmpty()
                        ? "index.html (default)" : project.getEntryPoint())
                + " result=" + (result.isBlocked() ? "BLOCKED" : "SUCCESS")
                + (result.isRestored() ? " (restored)" : ""));
        if (result.isBlocked()) {
            // The runtime refused to start (session cap or a failed start). Finishing here
            // would close the app with nothing on screen, so the failure is surfaced instead.
            openConfigError(result.getMessage().isEmpty()
                    ? "the default project could not be started" : result.getMessage());
            return;
        }
        // The project runs in its own document task. Removing the splash task means Back from
        // the project leaves the app instead of revealing a stale dashboard.
        finishAndRemoveTask();
    }

    // ==================== System bars ====================

    private void applySplashSystemBars(@NonNull AppConfig config) {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        WindowCompat.setDecorFitsSystemWindows(window, false);

        boolean fullscreen = config.isSplashFullscreen();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                if (fullscreen) {
                    controller.hide(WindowInsets.Type.statusBars()
                            | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars());
                    controller.hide(WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            if (fullscreen) {
                flags |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
    }

    // ==================== Helpers ====================

    @Nullable
    private AppConfig appConfig() {
        try {
            return ApplicationCore.get().getConfig();
        } catch (Throwable error) {
            return null;
        }
    }

    private void showContentSafely(View view) {
        try {
            if (getUIComponentManager() != null) {
                getUIComponentManager().showContent(view);
                return;
            }
        } catch (Throwable error) {
            AppLog.e("Unable to show splash content", error);
        }
        setContentView(view);
    }

    private static String hex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    /** Linear blend of two ARGB colours; {@code ratio} 0 returns {@code from}. */
    private static int blend(int from, int to, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * clamped);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * clamped);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * clamped);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * clamped);
        return Color.argb(a, r, g, b);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Quotes {@code value} as a JavaScript string literal. */
    private static String jsString(String value) {
        if (value == null) {
            return "''";
        }
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('\'');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    out.append("\\\\");
                    break;
                case '\'':
                    out.append("\\'");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '<':
                    out.append("\\u003c");
                    break;
                case '>':
                    out.append("\\u003e");
                    break;
                case '&':
                    out.append("\\u0026");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.append('\'').toString();
    }

    // ==================== Lifecycle ====================

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (splashWebView != null) {
            splashWebView.setWebViewClient(null);
            splashWebView.destroy();
            splashWebView = null;
        }
        super.onDestroy();
    }
}
