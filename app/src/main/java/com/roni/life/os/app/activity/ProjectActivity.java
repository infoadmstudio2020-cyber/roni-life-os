package com.roni.life.os.app.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.roni.library.ui.NativeRuntimeUIConfig;
import com.roni.library.ui.config.ProjectUIConfig;
import com.roni.library.ui.config.ProjectUIConfigMapper;
import com.roni.library.ui.manager.ComponentType;
import com.roni.library.ui.manager.NativeUIComponentManager;
import com.roni.library.ui.web.WebSource;
import com.roni.library.ui.web.WebViewHost;
import com.roni.library.platform.automation.AutomationProjectContext;
import com.roni.library.platform.camera.CameraProjectContext;
import com.roni.library.platform.filemanager.FileManagerProjectContext;
import com.roni.library.platform.notification.NotificationProjectContext;
import com.roni.life.os.app.R;
import com.roni.life.os.app.camera.CameraActivity;
import com.roni.life.os.app.inspector.RuntimeInspectorIntegration;
import com.roni.life.os.app.core.ApplicationCore;
import com.roni.life.os.app.core.RONiLiFEOSStorageInitializer;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.DefaultProjectResolver;
import com.roni.life.os.app.runtime.FormStateScript;
import com.roni.life.os.app.runtime.FormStateStore;
import com.roni.life.os.app.ui.ProjectControlSheet;
import com.roni.life.os.app.ui.ProjectOverlayButton;
import com.roni.life.os.app.project.ProjectSourceType;
import com.roni.life.os.app.project.ProjectStore;
import com.roni.life.os.app.project.StartupDecision;
import com.roni.life.os.app.runtime.ProjectBootstrapRunner;
import com.roni.life.os.app.runtime.ProjectLaunchRouter;
import com.roni.life.os.app.runtime.ProjectRuntimeRegistry;
import com.roni.life.os.app.runtime.ProjectSessionManager;
import com.roni.life.os.app.runtime.ProjectBrowserLauncher;
import com.roni.life.os.app.runtime.SafWebSourceProvider;
import com.roni.life.os.app.runtime.StartupManager;
import com.roni.life.os.app.runtime.WebSourceFactory;
import com.roni.life.os.app.project.ProjectVaultRuntime;
import com.roni.library.ui.web.ResourceDecoder;
import com.roni.life.os.app.uiconfig.ProjectUIConfigStore;
import com.roni.life.os.app.util.AppLog;

/**
 * Runtime host for a single project.
 *
 * <p>Each project runs in its own document task, so several runtimes can stay alive at once:
 * minimising one task leaves every other project's WebView, page and navigation history intact.
 * The project-level bootstrap matrix still runs, but the project-level splash does not — the main
 * app splash is the only splash, and opening or restoring a project never shows a second one.</p>
 *
 * <p>A project can be launched either from the registry ({@link #EXTRA_PROJECT_ID}) or as a
 * transient preview ({@link #EXTRA_PREVIEW_PROJECT}) that is never registered as a session.</p>
 */
public class ProjectActivity extends com.roni.library.os.activity.NativeRuntimeActivity {

    public static final String EXTRA_PROJECT_ID = "extra_project_id";

    /** Window in which a second back press is treated as a duplicate of the first. */
    private static final long BACK_DEBOUNCE_MS = 400L;

    /** Encoded {@link Project} launched without registration. */
    public static final String EXTRA_PREVIEW_PROJECT = "extra_preview_project";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Project project;
    private ProjectUIConfig uiConfig;
    private StartupDecision.ProjectStartup startup;
    private WebSource pendingSource;
    private boolean bootstrapDone;
    private boolean loaded;
    private boolean preview;
    private boolean sessionRegistered;
    private boolean closing;
    private FormStateStore formStateStore;
    private boolean pendingRestore;
    private long lastBackAt;
    private boolean exitDialogShowing;

    /** Runtime Inspector overlay. Null until the first resume. */
    private com.roni.library.devtools.inspector.ui.RuntimeInspectorSheet inspectorSheet;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        project = resolveProject();

        if (project == null) {
            super.onCreate(savedInstanceState);
            showToastSafely("No project was found — returning to the project manager");
            goBootstrap();
            return;
        }

        startup = StartupDecision.resolveProject(project.isSplashEnabled(),
                project.isBootstrapEnabled());
        AppLog.i("ProjectActivity: " + project.getName() + " → " + startup
                + (preview ? " (preview)" : ""));
        com.roni.library.devtools.inspector.RuntimeInspector.get().startup(
                "PROJECT_RESOLUTION",
                "name=" + project.getName() + " source=" + project.getSourceType().getKey()
                        + " splash=" + project.isSplashEnabled()
                        + " bootstrap=" + project.isBootstrapEnabled()
                        + " decision=" + startup);

        super.onCreate(savedInstanceState);
        applyImmersiveFullScreen();

        // Runtime Inspector: establish the session context before anything else logs, so the
        // whole launch sequence is attributable to this project and this startup trace.
        com.roni.library.devtools.inspector.RuntimeInspector inspector =
                com.roni.library.devtools.inspector.RuntimeInspector.get();
        inspector.init(this);
        inspector.beginStartupTrace();
        inspector.startup("PROJECT_ACTIVITY_CREATE",
                "project=" + project.getId() + " preview=" + preview);
        inspector.onProjectOpened(project.getId(), project.getName());

        // A fresh instance has an empty DOM, so any snapshot for this page must be replayed.
        formStateStore = new FormStateStore(ApplicationCore.get().getStore().getBackend());
        pendingRestore = true;

        if (!preview) {
            ApplicationCore.get().getStore().markOpened(project.getId());
            ApplicationCore.get().track("project_runtime_started", null);
        } else {
            ApplicationCore.get().track("project_previewed", null);
        }

        if (preview) {
            // A preview is a transient look at a project, not a session: it must not consume a
            // slot, appear in the minimized list, or be restorable.
            sessionRegistered = false;
        } else {
            ProjectSessionManager.Registration registration = ProjectSessionManager.get()
                    .register(project.getId(), project.getName(), false,
                            project.getIconColor(), project.getSourceType().getKey());
            sessionRegistered = registration.isAccepted();
            if (!sessionRegistered) {
                showToastSafely(registration.getMessage());
            } else {
                // Bind this runtime to its own document task so the dashboard can restore it
                // directly instead of guessing by component name.
                ProjectSessionManager.get().attachTask(project.getId(), getTaskId());
                // A session restored from a previous process is still RECOVERABLE at this
                // point; this runtime is the one that makes it live again.
                ProjectSessionManager.get().activate(project.getId(), getTaskId());
                ProjectRuntimeRegistry.register(project.getId(), this);
            }
        }

        // The main app splash is the only splash. Opening or restoring a project never shows a
        // second one; a fresh runtime uses the lightweight loading overlay instead.
        if (StartupDecision.shouldRunProjectBootstrap(startup)) {
            runBootstrap();
        } else {
            bootstrapDone = true;
            pendingSource = resolveSource();
        }

        maybeLoadWebApp();

        attachOverlay();
        attachBackHandler();
    }

    // ==================== UI configuration ====================

    /**
     * Builds the runtime UI from this project's own configuration.
     *
     * <p>Every component the project disabled stays disabled — the mapper never enables anything
     * the configuration did not ask for. The only exception is the WebView container: this
     * activity exists to host a web source, so a project that disabled it would render nothing.
     * That case is logged and the container is re-enabled rather than showing a blank screen.</p>
     */
    @Override
    protected NativeRuntimeUIConfig onConfigureUI(@NonNull NativeRuntimeUIConfig.Builder builder) {
        String title = project != null ? project.getName() : "Project";
        String subtitle = project != null ? project.getSourceType().getKey() : "";

        builder.toolbarTitle(title).toolbarSubtitle(subtitle);

        uiConfig = loadUIConfig();
        AppLog.i("Project UI config: " + ProjectUIConfigMapper.describe(uiConfig));

        // Only an encrypted internal project installs a decoder. A SAF folder, a remote URL, an
        // APK asset or a legacy unsealed project resolves to null and is served byte-for-byte.
        ResourceDecoder decoder = ProjectVaultRuntime.decoderFor(project);
        if (decoder != null) {
            AppLog.i("Serving project " + project.getId() + " through the vault decoder");
        }

        NativeRuntimeUIConfig runtime = ProjectUIConfigMapper.apply(uiConfig, builder, decoder);

        if (!runtime.isWebViewEnabled()) {
            AppLog.w("Project disabled the WebView container — ProjectActivity requires it, "
                    + "re-enabling for this screen");
            builder.enableWebView(true);
            runtime = builder.build();
        }

        return runtime;
    }

    /** Loads this project's UI configuration, falling back to defaults on any failure. */
    private ProjectUIConfig loadUIConfig() {
        if (project == null) {
            return new ProjectUIConfig();
        }
        try {
            return ProjectUIConfigStore.forApp().load(project.getId());
        } catch (Throwable error) {
            AppLog.e("Unable to load UI config for " + project.getId(), error);
            return new ProjectUIConfig();
        }
    }

    // ==================== Project bootstrap ====================

    private void runBootstrap() {
        showLoadingSafely("Preparing project…");
        new ProjectBootstrapRunner().run(this, project, new ProjectBootstrapRunner.Callback() {
            @Override
            public void onStep(String step) {
                showLoadingSafely(step);
            }

            @Override
            public void onFinished(ProjectBootstrapRunner.Outcome outcome) {
                hideLoadingSafely();
                bootstrapDone = true;
                if (outcome.isSuccess()) {
                    pendingSource = outcome.getSource();
                    maybeLoadWebApp();
                } else {
                    showError(outcome.getMessage());
                }
            }
        });
    }

    /**
     * Resolves the web source, validating the persisted SAF grant first so a revoked folder
     * produces a clear message instead of a blank WebView (EX-02).
     */
    private WebSource resolveSource() {
        if (project.getSourceType() == ProjectSourceType.SAF_FOLDER) {
            SafWebSourceProvider.Prepared prepared = SafWebSourceProvider.prepare(this, project);
            if (!prepared.isReady()) {
                showError(prepared.getMessage());
                return null;
            }
            return prepared.getSource();
        }
        return WebSourceFactory.from(project);
    }

    private void maybeLoadWebApp() {
        if (loaded || !bootstrapDone || pendingSource == null) {
            return;
        }
        loaded = true;
        try {
            NativeUIComponentManager manager = getUIComponentManager();
            if (manager != null && manager.getContentContainerManager() != null) {
                // Re-attach the WebView container: the splash/error view replaced it, so calling
                // loadWebApp() alone would leave the WebView detached and invisible.
                manager.getContentContainerManager().showWebView(this, pendingSource);
            } else {
                loadWebApp(pendingSource);
            }
            AppLog.i("Loading web source: " + pendingSource);
        } catch (Throwable throwable) {
            showError("The web app could not be loaded: " + throwable.getMessage());
        }
    }

    // ==================== Error display ====================

    private void showError(String message) {
        View error = LayoutInflater.from(this).inflate(R.layout.view_project_error, null, false);
        TextView messageView = error.findViewById(R.id.error_message);
        String text = message != null && !message.isEmpty()
                ? message : "An unknown error occurred.";
        if (project != null) {
            String path = project.getSourcePath() != null ? project.getSourcePath() : "";
            text = text + "\n\n" + project.getSourceType().getKey()
                    + (path.isEmpty() ? "" : " · " + path);
        }
        messageView.setText(text);

        error.findViewById(R.id.error_retry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loaded = false;
                bootstrapDone = false;
                runBootstrap();
            }
        });
        error.findViewById(R.id.error_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                goBootstrap();
            }
        });

        showContentSafely(error);
    }

    // ==================== Navigation ====================

    private Project resolveProject() {
        Intent intent = getIntent();
        if (intent != null) {
            String encoded = intent.getStringExtra(EXTRA_PREVIEW_PROJECT);
            if (encoded != null && !encoded.isEmpty()) {
                Project transientProject = Project.decode(encoded);
                if (transientProject != null) {
                    preview = true;
                    return transientProject;
                }
            }
        }

        ProjectStore store = ApplicationCore.get().getStore();
        if (store == null) {
            return null;
        }
        String id = intent != null ? intent.getStringExtra(EXTRA_PROJECT_ID) : null;
        Project resolved = store.findById(id);
        if (resolved == null && (id == null || id.isEmpty())) {
            // No explicit id: this is a startup launch, so the authoritative default applies.
            // The resolver never falls back to the last opened project.
            DefaultProjectResolver.Resolution resolution = store.resolveDefaultProject();
            AppLog.i("PROJECT_LAUNCH: no id supplied — default resolution status="
                    + resolution.getStatus() + " resolvedId="
                    + (resolution.getProjectId() != null ? resolution.getProjectId() : "none"));
            resolved = resolution.getProject();
        }
        if (resolved == null && (id == null || id.isEmpty())) {
            // An interrupted first run or a cleared registry leaves the store empty. Repairing
            // is idempotent and never overwrites user files, so it is safe to try here.
            AppLog.w("ProjectActivity: registry is empty — repairing storage");
            RONiLiFEOSStorageInitializer.repair(this, store);
            resolved = store.resolveDefaultProject().getProject();
        }
        if (resolved == null && id != null && !id.isEmpty()) {
            // An explicit id that no longer exists must never be replaced by another project.
            AppLog.w("ProjectActivity: requested project is missing id=" + id);
        }
        if (resolved != null && !resolved.isWebProject()) {
            // Only WEB projects are application projects. A SPLASH or BOOTSTRAP project that
            // reaches this host is a routing bug, not something to render.
            AppLog.w("ProjectActivity refused a " + resolved.getProjectType()
                    + " project: " + resolved.getName());
            return null;
        }
        if (resolved != null) {
            AppLog.i("PROJECT_LAUNCH: projectId=" + resolved.getId()
                    + " sourceType=" + resolved.getSourceType().getKey()
                    + " entryPoint=" + (resolved.getEntryPoint().isEmpty()
                            ? "index.html (default)" : resolved.getEntryPoint())
                    + " result=SUCCESS");
            if (resolved.getSourceType() == ProjectSourceType.REMOTE_URL) {
                // A remote project cannot render without a network. Logged explicitly so an
                // offline blank screen is traceable to the project source, not to routing.
                AppLog.w("PROJECT_LAUNCH: project source is remote — offline launch will fail id="
                        + resolved.getId());
            }
        }
        return resolved;
    }

    /**
     * Returns to the project manager.
     *
     * <p>When the dashboard is already underneath this activity, finishing is enough — starting a
     * second instance would push a blank activity on top of the live dashboard, which is what the
     * user saw as a white screen. A new dashboard is created only when this activity is the task
     * root (a project opened straight from the startup router).</p>
     */
    private void goBootstrap() {
        if (!isDashboardEnabled()) {
            // Dashboard disabled — never open it implicitly. Show the safe fallback instead.
            Intent error = StartupManager.configErrorIntent(this, "project unavailable");
            error.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                startActivity(error);
            } catch (Throwable throwable) {
                AppLog.e("Unable to open the startup fallback", throwable);
            }
            finish();
            return;
        }
        // The dashboard always lives in the main task, never inside a project document task.
        Intent intent = new Intent(this, BootstrapActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (Throwable error) {
            AppLog.e("Unable to open the dashboard", error);
        }
        finish();
    }

    /**
     * Registers the back handler.
     *
     * <p>Uses the dispatcher rather than {@code onBackPressed()} so predictive back on API 33+
     * reaches the same code path. The order is fixed: WebView history first, dashboard second.
     * The runtime is never destroyed here — back is non-destructive, and explicit destruction
     * lives in the control sheet.</p>
     */
    private void attachBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });
    }

    private void handleBack() {
        long now = SystemClock.uptimeMillis();
        if (now - lastBackAt < BACK_DEBOUNCE_MS) {
            // A double tap must not walk two history entries or open two dialogs.
            return;
        }
        lastBackAt = now;

        // Rule A — web history wins. SPA routes and JS navigation are never interrupted.
        try {
            WebViewHost host = getWebViewHost();
            if (host != null && host.canGoBack()) {
                host.goBack();
                return;
            }
        } catch (Throwable ignored) {
            // WebView unavailable — fall through to the native policy.
        }

        // Rule B — history is empty.
        if (StartupDecision.shouldReturnToDashboard(isDashboardEnabled())) {
            minimizeToDashboard();
            return;
        }
        // Dashboard disabled: never open it, never exit silently.
        showRootExitDialog();
    }

    /** Reads the dashboard flag; a broken config must not trap the user inside the project. */
    private boolean isDashboardEnabled() {
        try {
            return ApplicationCore.get().getStore().loadConfig().isDashboardEnabled();
        } catch (Throwable error) {
            AppLog.e("Unable to read the dashboard configuration", error);
            return true;
        }
    }

    /**
     * Root-level exit confirmation for dashboard-disabled mode.
     *
     * <p>Guarded so rapid back presses cannot stack two dialogs. Cancelling leaves the runtime
     * exactly as it was.</p>
     */
    private void showRootExitDialog() {
        if (exitDialogShowing || isFinishing() || isDestroyed()) {
            return;
        }
        exitDialogShowing = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.exit_dialog_title)
                .setMessage(R.string.exit_dialog_message)
                .setPositiveButton(R.string.exit_dialog_minimize, (dialog, which) -> {
                    exitDialogShowing = false;
                    minimizeApp();
                })
                .setNegativeButton(R.string.exit_dialog_exit, (dialog, which) -> {
                    exitDialogShowing = false;
                    exitApp();
                })
                .setNeutralButton(R.string.exit_dialog_cancel, (dialog, which) ->
                        exitDialogShowing = false)
                .setOnCancelListener(dialog -> exitDialogShowing = false)
                .setOnDismissListener(dialog -> exitDialogShowing = false)
                .show();
    }

    /** Option 1 — keep the session and the WebView, send the whole app to the background. */
    private void minimizeApp() {
        captureFormState();
        if (sessionRegistered && project != null) {
            ProjectSessionManager.get().minimize(project.getId());
            ProjectLaunchRouter.persist();
        }
        moveTaskToBack(true);
    }

    /** Option 2 — release the runtime and close the app without corrupting session data. */
    private void exitApp() {
        closing = true;
        captureFormState();
        finishAndRemoveTask();
    }

    /**
     * Handles a re-launch onto this instance.
     *
     * <p>Each project owns its own document task, so a correctly routed intent always carries
     * this project's id. A different id means the intent was misrouted; the runtime is kept
     * intact rather than recreated, because recreating would destroy this project's WebView and
     * take its session down with it.</p>
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        String requested = intent != null
                ? intent.getStringExtra(EXTRA_PROJECT_ID) : null;
        String current = project != null ? project.getId() : null;

        // Each project owns its own document task, so a different project id here means the
        // intent was misrouted. Recreating would destroy this project's WebView and take the
        // other project's session down with it — ignore it and keep the runtime intact.
        if (requested != null && !requested.equals(current)) {
            AppLog.w("Ignoring intent for " + requested + " on the runtime of " + current);
        }
    }

    // ==================== Minimise / control sheet ====================

    /**
     * Sends the project to the background without destroying it.
     *
     * <p>The dashboard is reordered to the front rather than recreated, and this activity is
     * deliberately <em>not</em> finished: it stays in the back stack in {@code onStop()} with
     * its WebView intact, so resuming restores the same page and scroll position.</p>
     */
    private void minimizeToDashboard() {
        // Persist uncommitted input before the runtime leaves the foreground.
        captureFormState();

        // Bring the dashboard task forward. NEW_TASK + REORDER_TO_FRONT reuses the existing
        // dashboard when there is one and creates it on a cold start that went straight into a
        // project — either way this project's task is left intact behind it.
        Intent intent = new Intent(this, BootstrapActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (Throwable error) {
            AppLog.e("Unable to open the dashboard", error);
        }

        // Safety net: if the dashboard was already in front this is a no-op, otherwise it
        // guarantees this project's task is the one that goes to the background.
        moveTaskToBack(true);
    }

    /**
     * Opens the Bootstrap management interface without leaving the project runtime.
     *
     * <p>The project is minimised, not finished: its task stays alive behind the management
     * interface, so returning restores the same page, scroll position and session. The
     * dashboard preference is never modified here — enabling it stays an explicit user
     * decision inside Bootstrap.</p>
     */
    private void openBootstrapFromProject() {
        captureFormState();
        if (sessionRegistered && project != null) {
            ProjectSessionManager.get().minimize(project.getId());
            ProjectLaunchRouter.persist();
        }
        Intent intent = new Intent(this, BootstrapActivity.class);
        intent.putExtra(BootstrapActivity.EXTRA_MANUAL_LAUNCH, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (Throwable error) {
            AppLog.e("Unable to open the bootstrap interface", error);
        }
    }

    // ==================== Uncommitted form state ====================

    /**
     * Snapshots every user-editable form control and persists it for this project and page.
     *
     * <p>Called from {@link #onPause()} and from {@link #minimizeToDashboard()}. The callback is
     * asynchronous but the activity is only stopped — not destroyed — when minimising, so it
     * always lands.</p>
     */
    private void captureFormState() {
        if (project == null || formStateStore == null) {
            return;
        }
        final WebViewHost host = getWebViewHost();
        if (host == null) {
            return;
        }
        final String projectId = project.getId();
        try {
            host.evaluateJavascript(FormStateScript.CAPTURE,
                    new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            String snapshot = FormStateScript.unwrap(value);
                            if (snapshot == null) {
                                return;
                            }
                            formStateStore.save(projectId,
                                    FormStateScript.urlOf(snapshot), snapshot);
                        }
                    });
        } catch (Throwable ignored) {
            // WebView gone — nothing to capture.
        }
    }

    /**
     * Replays the stored snapshot once the page has had time to render.
     *
     * <p>The delay covers the gap between {@code onResume} and the WebView finishing its first
     * layout. The restore script only fills controls that are still empty, so running it early
     * or twice is harmless.</p>
     */
    private void scheduleRestore() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                restoreFormState();
            }
        }, 700L);
    }

    private void restoreFormState() {
        if (!pendingRestore || project == null || formStateStore == null) {
            return;
        }
        WebViewHost host = getWebViewHost();
        if (host == null) {
            return;
        }
        String snapshot = formStateStore.load(project.getId(), host.getCurrentUrl());
        if (snapshot == null) {
            pendingRestore = false;
            return;
        }
        String script = FormStateScript.restore(snapshot);
        if (script == null) {
            pendingRestore = false;
            return;
        }
        pendingRestore = false;
        try {
            host.evaluateJavascript(script, null);
        } catch (Throwable ignored) {
            // WebView gone — the snapshot stays for the next attempt.
        }
    }

    /** Adds the floating control button. Preview sessions stay clean. */
    private void attachOverlay() {
        if (preview) {
            return;
        }
        ProjectOverlayButton.attach(this, new ProjectOverlayButton.Listener() {
            @Override
            public void onTap() {
                openControlSheet();
            }

            @Override
            public void onLongPress() {
                minimizeToDashboard();
            }
        });
    }

    private void openControlSheet() {
        if (project == null) {
            return;
        }
        ProjectControlSheet.show(this, project, new ProjectControlSheet.Listener() {
            @Override
            public void onMinimize() {
                minimizeToDashboard();
            }

            @Override
            public void onRestart() {
                recreate();
            }

            @Override
            public void onSettings() {
                Intent intent = new Intent(ProjectActivity.this,
                        ProjectUIEditorActivity.class);
                intent.putExtra(ProjectUIEditorActivity.EXTRA_PROJECT_ID,
                        project.getId());
                intent.putExtra(ProjectUIEditorActivity.EXTRA_PROJECT_NAME,
                        project.getName());
                startActivity(intent);
            }

            @Override
            public void onOpenBootstrap() {
                openBootstrapFromProject();
            }

            @Override
            public void onOpenInBrowser() {
                ProjectBrowserLauncher.open(ProjectActivity.this, project);
            }

            @Override
            public void onClose() {
                closing = true;
                finishAndRemoveTask();
            }

            @Override
            public void onSwitchTo(String projectId) {
                ProjectLaunchRouter.open(ProjectActivity.this, projectId);
            }
        });
    }

    // ==================== Session lifecycle ====================

    @Override
    protected void onPause() {
        super.onPause();
        if (inspectorSheet != null) {
            inspectorSheet.onPause();
        }
        // Snapshot before the runtime can be torn down. The WebView is still alive here, so
        // this is the last reliable moment to read the DOM.
        captureFormState();
        if (sessionRegistered && project != null && !isFinishing()) {
            ProjectSessionManager.get().minimize(project.getId());
            ProjectLaunchRouter.persist();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Scope every bridge notification request to the project that is actually on screen.
        NotificationProjectContext.setActiveProject(project != null ? project.getId() : "");
        CameraProjectContext.setActiveProject(project != null ? project.getId() : "");
        FileManagerProjectContext.setActiveProject(project != null ? project.getId() : "");
        AutomationProjectContext.setActiveProject(project != null ? project.getId() : "");
        if (sessionRegistered && project != null) {
            ProjectSessionManager.get().resume(project.getId());
        }
        if (pendingRestore) {
            scheduleRestore();
        }
        attachInspector();
    }

    /**
     * Installs the Runtime Inspector overlay and routes WebView diagnostics into it.
     *
     * <p>Idempotent: the sheet is created once per Activity and re-attached on every resume,
     * which is what keeps the log stream alive across a configuration change without
     * duplicating listeners.</p>
     */
    private void attachInspector() {
        try {
            com.roni.library.devtools.inspector.RuntimeInspector controller =
                    com.roni.library.devtools.inspector.RuntimeInspector.get();
            controller.init(this);
            RuntimeInspectorIntegration.get().attach(getWebViewHost());
            controller.startup("WEBVIEW_INITIALIZATION",
                    "host=" + (getWebViewHost() != null ? "ready" : "absent"));
            if (inspectorSheet == null) {
                inspectorSheet = com.roni.library.devtools.inspector.ui.RuntimeInspectorSheet
                        .install(this);
            }
            if (inspectorSheet != null) {
                inspectorSheet.onResume();
            }
        } catch (Throwable ignored) {
            // The inspector is diagnostic only — it must never break the project runtime.
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        if (inspectorSheet != null
                && inspectorSheet.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ==================== Safe UI helpers ====================

    private void showContentSafely(View view) {
        try {
            if (getUIComponentManager() != null) {
                getUIComponentManager().showContent(view);
            }
        } catch (Throwable throwable) {
            AppLog.e("Unable to show content", throwable);
        }
    }

    private void showLoadingSafely(String message) {
        try {
            showLoading(message);
        } catch (Throwable ignored) {
            // Loading overlay unavailable.
        }
    }

    private void hideLoadingSafely() {
        try {
            hideLoading();
        } catch (Throwable ignored) {
            // Loading overlay unavailable.
        }
    }

    private void showToastSafely(String message) {
        try {
            showToast(message);
        } catch (Throwable ignored) {
            // Toast unavailable.
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (inspectorSheet != null) {
            inspectorSheet.release();
            inspectorSheet = null;
        }
        com.roni.library.devtools.inspector.RuntimeInspector.get()
                .onProjectClosed(project != null ? project.getId() : "");
        if (sessionRegistered && project != null) {
            // Plaintext must not outlive the runtime that decrypted it.
            ProjectVaultRuntime.onProjectClosed(project.getId());
            ProjectRuntimeRegistry.unregister(project.getId(), this);
            if (isFinishing() || closing) {
                // Explicit close: the only path allowed to free the session slot.
                ProjectSessionManager.get().close(project.getId());
            } else {
                // The system destroyed the runtime (memory pressure or task removal). The
                // session stays listed, but it is honestly reported as recoverable.
                ProjectSessionManager.get().markRecoverable(project.getId());
            }
            ProjectLaunchRouter.persist();
        }
        NotificationProjectContext.clear();
        CameraProjectContext.clear();
        FileManagerProjectContext.clear();
        AutomationProjectContext.clear();
        super.onDestroy();
    }

    /**
     * The project runtime owns the whole display: no status bar, no navigation bar, content
     * drawn edge to edge. Bars stay hidden until the user swipes them back in.
     */
    private void applyImmersiveFullScreen() {
        Window window = getWindow();

        // Per-project system bar configuration wins over the historical always-immersive
        // behaviour. A project that wants a visible status bar now gets one.
        ProjectUIConfig.Navigation navigation = uiConfig != null
                ? uiConfig.getNavigation() : null;
        boolean immersive = navigation == null
                || navigation.isFullscreen()
                || navigation.isImmersive()
                || (uiConfig != null
                && uiConfig.isEnabled(ComponentType.FULLSCREEN_IMMERSIVE));

        if (!immersive) {
            WindowCompat.setDecorFitsSystemWindows(window, true);
            window.setStatusBarColor(navigation != null
                    ? navigation.getStatusBarColor() : Color.TRANSPARENT);
            window.setNavigationBarColor(navigation != null
                    ? navigation.getNavigationBarColor() : Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars()
                            | WindowInsets.Type.navigationBars());
                }
            } else {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }
            return;
        }

        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        WindowCompat.setDecorFitsSystemWindows(window, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveFullScreen();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_project, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_automation) {
            startActivity(new Intent(this,
                    com.roni.life.os.app.automation.AutomationActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_file_manager) {
            Intent intent = new Intent(this,
                    com.roni.life.os.app.filemanager.FileManagerActivity.class);
            intent.putExtra(com.roni.life.os.app.filemanager.FileManagerActivity.EXTRA_PROJECT_ID,
                    project != null ? project.getId() : "");
            startActivity(intent);
            return true;
        }
        if (item.getItemId() == R.id.action_camera) {
            Intent intent = new Intent(this, CameraActivity.class);
            intent.putExtra(CameraActivity.EXTRA_PROJECT_ID,
                    project != null ? project.getId() : "");
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
