package com.roni.life.os.app.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.roni.life.os.app.notification.NotificationCenterActivity;
import com.roni.life.os.app.notification.NotificationSettingsActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import androidx.appcompat.widget.SwitchCompat;
import com.roni.life.os.app.R;
import com.roni.life.os.app.core.AppConfig;
import com.roni.life.os.app.core.RONiLiFEOSStorageInitializer;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectImporter;
import com.roni.life.os.app.project.ProjectSourceType;
import com.roni.life.os.app.project.ProjectSlot;
import com.roni.life.os.app.project.ProjectStore;
import com.roni.life.os.app.project.ProjectValidator;
import com.roni.life.os.app.project.SafPermissionManager;
import com.roni.life.os.app.runtime.ProjectLaunchRouter;
import com.roni.life.os.app.runtime.ProjectSessionManager;
import com.roni.life.os.app.runtime.SessionSnapshotStore;
import com.roni.life.os.app.runtime.RuntimeBridge;
import com.roni.life.os.app.runtime.SafWebSourceProvider;
import com.roni.life.os.app.runtime.StartupManager;
import com.roni.life.os.app.runtime.BootstrapSource;
import com.roni.life.os.app.source.SourceConfig;
import com.roni.life.os.app.source.SourceConfigStore;
import com.roni.life.os.app.source.SourceKind;
import com.roni.life.os.app.source.SourceManagerDialog;
import com.roni.life.os.app.ui.HudCoreView;
import com.roni.life.os.app.ui.HudMeterView;
import com.roni.life.os.app.ui.ProjectAdapter;
import com.roni.life.os.app.ui.ProjectEditDialog;
import com.roni.life.os.app.ui.ProjectInfoDialog;
import com.roni.life.os.app.util.AppLog;
import com.roni.life.os.app.util.UiUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Native Dashboard — the authoritative control centre of RONiLiFEOS.
 *
 * <p>This activity is deliberately a native Android screen: it owns the project registry, the
 * SAF folder picker, ZIP import, session accounting and the dashboard-mode preference. The
 * optional web dashboard is a secondary surface that can only reach the system through the
 * approved bridge APIs; it never replaces this screen.</p>
 */
public class BootstrapActivity extends BaseActivity implements ProjectAdapter.Listener {

    /** Opens a specific BOOTSTRAP project instead of the dashboard. */
    public static final String EXTRA_BOOTSTRAP_PROJECT_ID = "extra_bootstrap_project_id";

    /** True when the user opened Bootstrap from Settings rather than from startup. */
    public static final String EXTRA_MANUAL_LAUNCH = "extra_manual_launch";

    /** True when this Activity must show only the dashboard, never a bootstrap project. */
    public static final String EXTRA_DASHBOARD_ONLY = "extra_dashboard_only";

    private static final int CLOSE_COLOR = 0xFF888888;
    private static final int ACCENT_COLOR = 0xFFFF4500;

    private RecyclerView projectList;
    private View emptyState;
    private TextView runtimeStatus;
    private View runtimeDot;
    private TextView dashboardModeChip;
    private TextView statProjects;
    private TextView statSessions;
    private TextView statEngines;
    private TextView statStorage;
    private TextView projectCount;
    private TextView recentEmpty;
    private View recentScroll;
    private LinearLayout recentContainer;
    private LinearLayout runningContainer;
    private TextView runningEmpty;
    private ProjectAdapter adapter;

    /** Runtime Inspector overlay. Null until the first resume. */
    private com.roni.library.devtools.inspector.ui.RuntimeInspectorSheet inspectorSheet;

    /**
     * Last startup-trace line already pushed into the inspector. Static on purpose: the log
     * repository is process-wide, so an Activity recreation must not replay the same lines.
     * Tracking the last line (not a boolean) still lets newly recorded events appear.
     */
    private static String startupTraceLastLine;

    /**
     * True when this screen was opened explicitly (Settings → Open Bootstrap) rather than by
     * automatic startup routing.
     *
     * <p>Management mode never auto-routes to a project, never changes the dashboard
     * preference, and returns to the project that was running when it is closed.</p>
     */
    private boolean managementMode;

    // Industrial HUD surfaces
    private HudCoreView hudCore;
    private HudMeterView hudMeter;
    private TextView statusRuntime;
    private TextView statusEngines;
    private TextView statusMode;
    private TextView statusUptime;
    private TextView heroLoad;
    private LinearLayout logContainer;
    private TextView logCount;
    private View statsRow;

    private final Handler uptimeHandler = new Handler(Looper.getMainLooper());
    private long bootAt;
    private final Runnable uptimeTick = new Runnable() {
        @Override
        public void run() {
            updateUptime();
            uptimeHandler.postDelayed(this, 1000L);
        }
    };

    private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), this::onFolderPicked);

    private final ActivityResultLauncher<String[]> zipPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onZipPicked);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent source = getIntent();
        boolean dashboardOnly = source != null
                && source.getBooleanExtra(EXTRA_DASHBOARD_ONLY, false);
        boolean manualLaunch = source != null
                && source.getBooleanExtra(EXTRA_MANUAL_LAUNCH, false);
        managementMode = manualLaunch;
        String bootstrapId = source != null
                ? source.getStringExtra(EXTRA_BOOTSTRAP_PROJECT_ID) : null;

        // A WEB-rendering Bootstrap project is hosted by its own WebView activity. A NATIVE one
        // renders here, on top of the dashboard.
        if (!dashboardOnly && bootstrapId != null && openWebBootstrap(bootstrapId, manualLaunch)) {
            return;
        }

        // Dashboard OFF must never be used as an automatic startup destination. An automatic
        // dashboard intent that arrives while the preference is off is refused here, at the
        // Activity boundary, so no caller can bypass the startup matrix.
        if (dashboardOnly && !manualLaunch && !config().isDashboardEnabled()) {
            AppLog.w("Refused an automatic dashboard launch while dashboardEnabled=false");
            openStartupProject();
            return;
        }

        // A project may run without a dashboard. In that case the app goes straight to the
        // configured entry point instead of showing a project manager the user disabled.
        // A manual launch (Settings → Open Bootstrap) always shows the management interface.
        if (!dashboardOnly && !manualLaunch && !config().isDashboardEnabled()
                && openStartupProject()) {
            return;
        }

        setContentView(R.layout.activity_bootstrap);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        applyDashboardConfig();

        if (managementMode) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle(R.string.bootstrap_management_subtitle);
            }
            attachManagementBackHandler();
        }

        projectList = findViewById(R.id.project_list);
        emptyState = findViewById(R.id.empty_state);
        runtimeStatus = findViewById(R.id.runtime_status);
        runtimeDot = findViewById(R.id.runtime_dot);
        dashboardModeChip = findViewById(R.id.dashboard_mode_chip);
        statProjects = findViewById(R.id.stat_projects);
        statSessions = findViewById(R.id.stat_sessions);
        statEngines = findViewById(R.id.stat_engines);
        statStorage = findViewById(R.id.stat_storage);
        projectCount = findViewById(R.id.project_count);
        recentEmpty = findViewById(R.id.recent_empty);
        recentScroll = findViewById(R.id.recent_scroll);
        recentContainer = findViewById(R.id.recent_container);
        runningContainer = findViewById(R.id.running_container);
        runningEmpty = findViewById(R.id.running_empty);

        hudCore = findViewById(R.id.hud_core);
        hudMeter = findViewById(R.id.hud_meter);
        statusRuntime = findViewById(R.id.status_runtime);
        statusEngines = findViewById(R.id.status_engines);
        statusMode = findViewById(R.id.status_mode);
        statusUptime = findViewById(R.id.status_uptime);
        heroLoad = findViewById(R.id.hero_load);
        logContainer = findViewById(R.id.log_container);
        logCount = findViewById(R.id.log_count);
        statsRow = findViewById(R.id.stats_row);
        bootAt = SystemClock.elapsedRealtime();

        // Sessions persisted by the previous process come back as recoverable entries: they are
        // listed so the user can reopen them, but they are never reported as live runtimes.
        restoreSessionSnapshots();

        adapter = new ProjectAdapter(this);
        projectList.setLayoutManager(new LinearLayoutManager(this));
        projectList.setAdapter(adapter);
        projectList.setNestedScrollingEnabled(false);

        ExtendedFloatingActionButton fab = findViewById(R.id.fab_new_project);
        fab.setOnClickListener(view -> createProject());

        findViewById(R.id.action_create).setOnClickListener(view -> createProject());
        findViewById(R.id.action_import).setOnClickListener(view -> pickZip());
        findViewById(R.id.action_folder).setOnClickListener(view -> pickFolder());
        findViewById(R.id.empty_action).setOnClickListener(view -> createProject());

        // Project section: one entry point per slot. Each slot owns its own rules, so the
        // dashboard only routes — it never mixes one slot's configuration with another's.
        findViewById(R.id.slot_card_splash).setOnClickListener(
                view -> openSlot(ProjectSlot.SPLASH));
        findViewById(R.id.slot_card_dashboard).setOnClickListener(
                view -> openSlot(ProjectSlot.DASHBOARD));
        findViewById(R.id.slot_card_app).setOnClickListener(
                view -> openSlot(ProjectSlot.APP));
        findViewById(R.id.slot_card_workspace).setOnClickListener(
                view -> openSlot(ProjectSlot.WORKSPACE));
    }

    /** Opens the management screen for one slot. */
    private void openSlot(ProjectSlot slot) {
        Intent intent = new Intent(this, ProjectSlotActivity.class);
        intent.putExtra(ProjectSlotActivity.EXTRA_SLOT, slot.getKey());
        startActivity(intent);
    }

    // ==================== Management mode ====================

    /**
     * In management mode Back returns to the project that was running, when there is one.
     *
     * <p>This is what makes "Settings → Open Bootstrap" non-destructive: the project task was
     * only minimised, so returning restores the same page instead of restarting the app. The
     * dashboard preference is never read or written here.</p>
     */
    private void attachManagementBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (returnToRunningProject()) {
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    /** @return true when a live project task was brought back to the front */
    private boolean returnToRunningProject() {
        for (ProjectSessionManager.Session session : ProjectSessionManager.get().getSessions()) {
            if (session.isLive() && ProjectLaunchRouter.restore(this, session.getProjectId())) {
                AppLog.i("Management: returned to project " + session.getProjectId());
                finish();
                return true;
            }
        }
        return false;
    }

    // ==================== Dashboard configuration ====================

    /**
     * Applies the app-level dashboard configuration to the inflated layout.
     *
     * <p>Every toggle is independent: hiding the toolbar does not hide the FAB, and hiding the
     * statistics does not hide the project list.</p>
     */
    private void applyDashboardConfig() {
        AppConfig config = config();

        View appBar = findViewById(R.id.app_bar);
        if (appBar != null) {
            UiUtils.setVisible(appBar, config.isDashboardToolbarEnabled());
        }

        View fab = findViewById(R.id.fab_new_project);
        if (fab != null) {
            UiUtils.setVisible(fab, config.isDashboardFabEnabled());
        }

        View stats = findViewById(R.id.stats_row);
        if (stats != null) {
            UiUtils.setVisible(stats, config.isDashboardShowStats());
        }

        View recentScroll = findViewById(R.id.recent_scroll);
        View recentEmpty = findViewById(R.id.recent_empty);
        if (!config.isDashboardShowRecent()) {
            UiUtils.setVisible(recentScroll, false);
            UiUtils.setVisible(recentEmpty, false);
        }

        View scroll = findViewById(R.id.dashboard_scroll);
        if (scroll != null && config.getDashboardBackgroundColor() != 0) {
            scroll.setBackgroundColor(config.getDashboardBackgroundColor());
        }

        if (config.isDashboardFullscreen()) {
            applyDashboardFullscreen();
        }
    }

    private void applyDashboardFullscreen() {
        Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(window, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    /**
     * Opens a WEB-rendering Bootstrap project in its own WebView host.
     *
     * @return true when the project was launched and this activity should stop
     */
    private boolean openWebBootstrap(String projectId, boolean manualLaunch) {
        Project project = store().findById(projectId);
        if (project == null || !project.isBootstrapProject() || !project.isWebBootstrap()) {
            return false;
        }
        Intent intent = new Intent(this, BootstrapWebActivity.class);
        intent.putExtra(BootstrapWebActivity.EXTRA_PROJECT_ID, project.getId());
        startActivity(intent);
        if (!manualLaunch) {
            finish();
        }
        return true;
    }

    /**
     * Opens the startup project and finishes this activity.
     *
     * @return true when a project was launched, false when the registry is empty.
     */
    private boolean openStartupProject() {
        // This host is already past the launcher, so the splash is never re-entered here.
        StartupManager.StartupPlan plan = StartupManager.resolveForHost(store());
        if (plan.isDashboard() || plan.getProject() == null) {
            // Dashboard disabled and no usable default project: never fall back to the
            // dashboard the user switched off — show the safe configuration screen instead.
            AppLog.w("Dashboard disabled but no launchable project — showing the fallback: "
                    + plan.getReason());
            Intent error = StartupManager.configErrorIntent(this, plan.getReason());
            error.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(error);
            finish();
            return true;
        }
        Intent intent = StartupManager.buildIntent(this, plan);
        startActivity(intent);
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        uptimeHandler.removeCallbacks(uptimeTick);
        uptimeHandler.post(uptimeTick);
        attachInspector();
    }

    /**
     * Installs the shared Runtime Inspector overlay in the native dashboard.
     *
     * <p>Reuses the exact same sheet, controller and repository as the Web Project Activity —
     * there is no second inspector and no second log pipeline. {@code install()} is idempotent
     * per Activity, so a configuration change re-attaches the existing overlay instead of
     * stacking a duplicate one.</p>
     */
    private void attachInspector() {
        try {
            com.roni.library.devtools.inspector.RuntimeInspector controller =
                    com.roni.library.devtools.inspector.RuntimeInspector.get();
            controller.init(this);
            controller.startup("DASHBOARD_INITIALIZATION", "native dashboard");
            if (inspectorSheet == null) {
                inspectorSheet = com.roni.library.devtools.inspector.ui.RuntimeInspectorSheet
                        .install(this);
            }
            if (inspectorSheet != null) {
                inspectorSheet.onResume();
            }
            replayStartupTrace(controller);
        } catch (Throwable ignored) {
            // The inspector is diagnostic only — it must never break the dashboard.
        }
    }

    /**
     * Pushes the persisted startup sequence into the inspector so the dashboard shows how the
     * app actually reached it. Runs once per process to avoid duplicate entries.
     */
    private void replayStartupTrace(
            com.roni.library.devtools.inspector.RuntimeInspector controller) {
        String trace = com.roni.life.os.app.runtime.StartupTrace.read(this);
        if (trace == null || trace.isEmpty()) {
            if (startupTraceLastLine == null) {
                controller.startup("STARTUP_TRACE", "unavailable — no startup recorded yet");
                startupTraceLastLine = "";
            }
            return;
        }
        String[] lines = trace.split("\n");
        int start = 0;
        if (startupTraceLastLine != null && !startupTraceLastLine.isEmpty()) {
            for (int i = lines.length - 1; i >= 0; i--) {
                if (startupTraceLastLine.equals(lines[i])) {
                    start = i + 1;
                    break;
                }
            }
        }
        for (int i = start; i < lines.length; i++) {
            if (lines[i] != null && !lines[i].trim().isEmpty()) {
                controller.startup("STARTUP_TRACE", lines[i]);
                startupTraceLastLine = lines[i];
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        uptimeHandler.removeCallbacks(uptimeTick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refresh();
    }

    // ==================== Dashboard state ====================

    private void refresh() {
        List<Project> projects = store().getAll();
        adapter.submit(projects);
        UiUtils.setVisible(emptyState, projects.isEmpty());
        UiUtils.setVisible(projectList, !projects.isEmpty());
        projectCount.setText(String.valueOf(projects.size()));

        statProjects.setText(String.valueOf(projects.size()));
        statSessions.setText(ProjectSessionManager.get().count()
                + "/" + ProjectSessionManager.MAX_SESSIONS);

        int totalEngines = 0;
        int readyEngines = 0;
        for (RuntimeBridge.EngineStatus status : RuntimeBridge.get().getEngineStatuses()) {
            totalEngines++;
            if (status.isInitialized()) {
                readyEngines++;
            }
        }
        statEngines.setText(readyEngines + "/" + totalEngines);
        statStorage.setText(core().isRuntimeReady() ? "INTERNAL" : "FALLBACK");

        boolean ready = core().isRuntimeReady();
        runtimeStatus.setText((ready ? getString(R.string.runtime_ready)
                : getString(R.string.runtime_limited))
                + " · " + RuntimeBridge.get().getRuntimeSummary()
                + " · " + sourceSummary());
        runtimeDot.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                ready ? R.color.state_success : R.color.state_warning)));

        String mode = config().isWebDashboard() ? "WEB" : "NATIVE";
        dashboardModeChip.setText(mode);
        if (statusMode != null) {
            statusMode.setText(mode);
        }
        if (statusRuntime != null) {
            statusRuntime.setText(ready ? R.string.hud_status_online : R.string.hud_status_limited);
        }
        if (statusEngines != null) {
            statusEngines.setText(getString(R.string.hud_engines, readyEngines + "/" + totalEngines));
        }

        float load = totalEngines == 0 ? 0f : (float) readyEngines / totalEngines;
        if (hudMeter != null) {
            hudMeter.setProgress(load);
        }
        if (hudCore != null) {
            hudCore.setLoad(load);
        }
        if (heroLoad != null) {
            heroLoad.setText(getString(R.string.hud_load_percent, Math.round(load * 100)));
        }

        updateUptime();
        renderNodeLog(readyEngines, totalEngines, projects.size(), ready);

        bindRecentProjects();
        renderRunningSessions();
        ProjectLaunchRouter.persist();
    }

    // ==================== HUD readouts ====================

    /** Ticks the instrument uptime readout once per second. */
    private void updateUptime() {
        if (statusUptime == null) {
            return;
        }
        long seconds = Math.max(0L, (SystemClock.elapsedRealtime() - bootAt) / 1000L);
        String clock = String.format(Locale.US, "%02d:%02d:%02d",
                seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        statusUptime.setText(getString(R.string.hud_uptime, clock));
    }

    /**
     * Renders the node log from real runtime state.
     *
     * <p>The console is a fixed six-line buffer: the newest state always replaces the oldest
     * line, so the panel never grows and never scrolls the dashboard.</p>
     */
    private void renderNodeLog(int readyEngines, int totalEngines, int projectCount,
                               boolean runtimeReady) {
        if (logContainer == null) {
            return;
        }
        logContainer.removeAllViews();

        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        List<String[]> lines = new ArrayList<>();
        lines.add(new String[]{stamp, "runtime", getString(R.string.hud_log_boot),
                getString(R.string.hud_log_ok)});
        lines.add(new String[]{stamp, "storage", getString(R.string.hud_log_storage),
                getString(R.string.hud_log_ok)});
        lines.add(new String[]{stamp, "registry",
                getString(R.string.hud_log_registry) + " · " + projectCount,
                getString(R.string.hud_log_ok)});
        lines.add(new String[]{stamp, "session", getString(R.string.hud_log_sessions),
                getString(R.string.hud_log_ok)});
        lines.add(new String[]{stamp, "engine",
                getString(R.string.hud_log_engines) + " · " + readyEngines + "/" + totalEngines,
                readyEngines == totalEngines
                        ? getString(R.string.hud_log_ok) : getString(R.string.hud_log_warn)});
        lines.add(new String[]{stamp, "runtime",
                runtimeReady ? getString(R.string.hud_log_ready)
                        : getString(R.string.hud_log_limited),
                runtimeReady ? getString(R.string.hud_log_ok) : getString(R.string.hud_log_warn)});

        for (String[] line : lines) {
            logContainer.addView(buildLogRow(line[0], line[1], line[2], line[3]));
        }
        if (logCount != null) {
            logCount.setText(String.format(Locale.US, "%02d/%02d", lines.size(), lines.size()));
        }
    }

    private View buildLogRow(String time, String tag, String message, String level) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = UiUtils.dp(this, 4);
        row.setLayoutParams(params);

        row.addView(logCell(time, 0, 54, R.color.hud_text_dim));
        row.addView(logCell(tag, 0, 58, R.color.hud_accent));
        row.addView(logCell(message, 1, 0, R.color.hud_text));
        row.addView(logCell(level, 0, 34, R.color.hud_text_dim));
        return row;
    }

    private TextView logCell(String text, int weight, int widthDp, int colorRes) {
        TextView cell = new TextView(this);
        cell.setText(text);
        cell.setTextSize(9f);
        cell.setTypeface(Typeface.MONOSPACE);
        cell.setSingleLine(true);
        cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.setTextColor(ContextCompat.getColor(this, colorRes));
        LinearLayout.LayoutParams params = weight > 0
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                : new LinearLayout.LayoutParams(UiUtils.dp(this, widthDp),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cell.setLayoutParams(params);
        return cell;
    }

    private void bindRecentProjects() {
        List<Project> recent = store().getRecent(4);
        recentContainer.removeAllViews();
        UiUtils.setVisible(recentScroll, !recent.isEmpty());
        UiUtils.setVisible(recentEmpty, recent.isEmpty());

        int horizontal = UiUtils.dp(this, 10);
        int vertical = UiUtils.dp(this, 7);
        int index = 0;
        for (final Project project : recent) {
            index++;
            TextView chip = new TextView(this);
            String label = String.format(Locale.US, "%02d  %s", index,
                    project.getName() == null ? "" : project.getName().toUpperCase(Locale.US));
            SpannableString styled = new SpannableString(label);
            styled.setSpan(new ForegroundColorSpan(ACCENT_COLOR), 0, 2,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            chip.setText(styled);
            chip.setTextSize(10f);
            chip.setTypeface(Typeface.MONOSPACE);
            chip.setLetterSpacing(0.06f);
            chip.setTextColor(ContextCompat.getColor(this, R.color.hud_text));
            chip.setBackgroundResource(R.drawable.bg_hud_chip);
            chip.setPadding(horizontal, vertical, horizontal, vertical);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(UiUtils.dp(this, 6));
            chip.setLayoutParams(params);
            chip.setOnClickListener(view -> runProject(project));
            recentContainer.addView(chip);
        }
    }

    // ==================== Running sessions ====================

    /**
     * Renders the live project sessions above the recent list.
     *
     * <p>This is the only place a minimised project can be resumed from, so it is rebuilt on
     * every {@link #refresh()} — the dashboard is the authoritative view of what is running.</p>
     */
    private void renderRunningSessions() {
        if (runningContainer == null) {
            return;
        }
        runningContainer.removeAllViews();

        // Every session that is not in the foreground: minimised runtimes whose WebView is still
        // alive, plus recoverable entries left over from a previous process.
        List<ProjectSessionManager.Session> sessions =
                ProjectSessionManager.get().getBackgroundSessions();
        UiUtils.setVisible(runningEmpty, sessions.isEmpty());
        UiUtils.setVisible(runningContainer, !sessions.isEmpty());

        for (final ProjectSessionManager.Session session : sessions) {
            runningContainer.addView(buildSessionRow(session));
        }
    }

    private View buildSessionRow(final ProjectSessionManager.Session session) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_hud_session);
        row.setPadding(0, 0, 0, 0);

        View rail = new View(this);
        rail.setBackgroundColor(ACCENT_COLOR);
        rail.setLayoutParams(new LinearLayout.LayoutParams(UiUtils.dp(this, 2),
                LinearLayout.LayoutParams.MATCH_PARENT));
        row.addView(rail);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = UiUtils.dp(this, 8);
        row.setLayoutParams(rowParams);

        TextView badge = new TextView(this);
        int badgeSize = UiUtils.dp(this, 32);
        badge.setLayoutParams(new LinearLayout.LayoutParams(badgeSize, badgeSize));
        String initial = session.getName() != null && !session.getName().isEmpty()
                ? session.getName().substring(0, 1).toUpperCase(Locale.US) : "?";
        badge.setText(initial);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(ACCENT_COLOR);
        badge.setTextSize(13f);
        badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        badge.setBackgroundResource(R.drawable.bg_hud_badge);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = UiUtils.dp(this, 12);
        text.setLayoutParams(textParams);

        TextView name = new TextView(this);
        name.setText(session.getName());
        name.setTextSize(12f);
        name.setTextColor(ContextCompat.getColor(this, R.color.hud_text));
        name.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView state = new TextView(this);
        state.setText(sessionStatus(session).toUpperCase(Locale.US));
        state.setTextSize(9f);
        state.setTypeface(Typeface.MONOSPACE);
        state.setTextColor(ContextCompat.getColor(this, R.color.hud_text_dim));
        state.setMaxLines(1);

        text.addView(name);
        text.addView(state);

        TextView restore = new TextView(this);
        restore.setText(R.string.hud_session_open);
        restore.setTextSize(9f);
        restore.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        restore.setLetterSpacing(0.12f);
        restore.setTextColor(ACCENT_COLOR);
        restore.setBackgroundResource(R.drawable.bg_hud_chip_accent);
        restore.setPadding(UiUtils.dp(this, 8), UiUtils.dp(this, 6),
                UiUtils.dp(this, 8), UiUtils.dp(this, 6));
        restore.setOnClickListener(view -> launchProject(session.getProjectId()));

        TextView close = new TextView(this);
        close.setText(R.string.hud_session_close);
        close.setTextSize(9f);
        close.setTypeface(Typeface.MONOSPACE);
        close.setLetterSpacing(0.12f);
        close.setTextColor(CLOSE_COLOR);
        close.setPadding(UiUtils.dp(this, 8), UiUtils.dp(this, 6),
                UiUtils.dp(this, 8), UiUtils.dp(this, 6));
        close.setOnClickListener(view -> confirmCloseSession(session));

        row.addView(badge);
        row.addView(text);
        row.addView(restore);
        row.addView(close);

        row.setOnClickListener(view -> launchProject(session.getProjectId()));
        return row;
    }

    /** Human-readable status line: state plus how long ago the project was last used. */
    private String sessionStatus(ProjectSessionManager.Session session) {
        String state;
        if (session.getState() == ProjectSessionManager.State.RECOVERABLE) {
            state = getString(R.string.pm_state_recoverable);
        } else if (session.getState() == ProjectSessionManager.State.MINIMIZED) {
            state = getString(R.string.pm_state_minimized);
        } else {
            state = getString(R.string.pm_state_active);
        }
        return state + " · " + getString(R.string.pm_session_last_active,
                formatLastActive(session.getLastActiveAt()));
    }

    private String formatLastActive(long timestamp) {
        if (timestamp <= 0L) {
            return getString(R.string.pm_session_never);
        }
        long delta = System.currentTimeMillis() - timestamp;
        if (delta < 60_000L) {
            return getString(R.string.pm_session_just_now);
        }
        if (delta < 3_600_000L) {
            return getString(R.string.pm_session_minutes_ago, delta / 60_000L);
        }
        if (delta < 86_400_000L) {
            return getString(R.string.pm_session_hours_ago, delta / 3_600_000L);
        }
        return new java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(timestamp));
    }

    /** Explicit close — the only dashboard action that destroys a session. */
    private void confirmCloseSession(final ProjectSessionManager.Session session) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.pm_session_close_title)
                .setMessage(getString(R.string.pm_session_close_message, session.getName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.pm_session_close, (dialog, which) -> {
                    ProjectLaunchRouter.close(this, session.getProjectId());
                    toast(getString(R.string.pm_session_closed, session.getName()));
                    refresh();
                })
                .show();
    }

    private void restoreSessionSnapshots() {
        try {
            ProjectSessionManager.get().restore(SessionSnapshotStore.forApp().load());
        } catch (Throwable ignored) {
            // Recovery is best effort — a corrupt snapshot must never block the dashboard.
        }
    }

    // ==================== Create / import / link ====================

    private void createProject() {
        ProjectEditDialog.show(this, null, store().getAll(), new ProjectEditDialog.Listener() {
            @Override
            public void onSaved(Project project) {
                store().save(project);
                // Applies the flag in both directions: setting a new default, and clearing
                // the old one when the user unticks the switch.
                store().syncDefault(project);
                core().track("project_created", null);
                toast("Project created: " + project.getName());
                refresh();
            }
        });
    }

    private void pickZip() {
        zipPicker.launch(new String[]{
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream"
        });
    }

    private void pickFolder() {
        folderPicker.launch(null);
    }

    private void onZipPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        final String displayName = zipDisplayName(uri);
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setMessage(R.string.import_progress)
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            final ProjectImporter.Result result = ProjectImporter.importZip(
                    getApplicationContext(), uri, displayName);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.dismiss();
                if (!result.isSuccess()) {
                    toast(getString(R.string.import_failed, result.getMessage()));
                    return;
                }
                Project project = new Project();
                project.setName(displayName);
                project.setDescription("Imported archive");
                project.setSourceType(ProjectSourceType.FILE);
                project.setSourcePath(result.getDirectory().getAbsolutePath());
                project.setEntryPoint(result.getEntryPoint());
                toast(getString(R.string.import_success, displayName));
                offerRegistration(project);
            });
        }, "zip-import").start();
    }

    private void onFolderPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        if (!SafPermissionManager.takePersistable(getContentResolver(), uri)) {
            toast(getString(R.string.folder_failed));
            return;
        }
        String name = SafWebSourceProvider.queryDisplayName(this, uri);
        if (name == null || name.trim().isEmpty()) {
            name = "Linked folder";
        }

        Project project = new Project();
        project.setName(name);
        project.setDescription("Linked SAF folder — files stay in place");
        project.setSourceType(ProjectSourceType.SAF_FOLDER);
        project.setSourcePath(SafPermissionManager.toSourcePath(uri));
        project.setEntryPoint("index.html");
        toast(getString(R.string.folder_linked, name));
        offerRegistration(project);
    }

    /**
     * Import and registration are separate operations: the user may preview an imported or
     * linked project without adding it to the registry.
     */
    private void offerRegistration(final Project project) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.register_title)
                .setMessage(R.string.register_message)
                .setPositiveButton(R.string.register_now, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        store().save(project);
                        core().track("project_registered", null);
                        toast("Registered: " + project.getName());
                        refresh();
                    }
                })
                .setNeutralButton(R.string.preview_only, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        previewProject(project);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private String zipDisplayName(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.trim().isEmpty()) {
            return "Imported project";
        }
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.trim().isEmpty() ? "Imported project" : name.trim();
    }

    // ==================== Run / preview ====================

    private void runProject(Project project) {
        ProjectValidator.Result validation = ProjectValidator.validate(project, store().getAll());
        if (!validation.isValid()) {
            toast(validation.getFirstError());
            return;
        }

        // The router owns the open/restore decision: a live session is brought back exactly as
        // it was, a stale one is rebuilt, and the cap is never resolved by evicting a project.
        ProjectLaunchRouter.Result result = ProjectLaunchRouter.open(this, project);
        if (result.isBlocked()) {
            showSessionLimit(result.getMessage());
            return;
        }

        store().markOpened(project.getId());
        core().track(result.isRestored() ? "project_restored" : "project_opened", null);
        refresh();
    }

    /**
     * Brings a project runtime to the foreground.
     *
     * <p>Delegated to {@link ProjectLaunchRouter}: a live session is restored by moving its own
     * document task to the front, so its WebView, page and navigation history are untouched.
     * Only a project without a live runtime is started fresh.</p>
     */
    private void launchProject(String projectId) {
        ProjectLaunchRouter.Result result = ProjectLaunchRouter.open(this, projectId);
        if (result.isBlocked() && !result.getMessage().isEmpty()) {
            showSessionLimit(result.getMessage());
        }
        refresh();
    }

    /** Launches a project without adding it to the registry. */
    private void previewProject(Project project) {
        Intent intent = new Intent(this, ProjectActivity.class);
        intent.putExtra(ProjectActivity.EXTRA_PREVIEW_PROJECT, project.encode());
        startActivity(intent);
    }

    private void showSessionLimit(String detail) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.session_limit_title)
                .setMessage(getString(R.string.session_limit_message)
                        + (detail != null && !detail.isEmpty() ? "\n\n" + detail : ""))
                .setPositiveButton(R.string.dialog_ok, null)
                .show();
    }

    // ==================== Registry actions ====================

    private void editProject(final Project project) {
        ProjectEditDialog.show(this, project, store().getAll(), new ProjectEditDialog.Listener() {
            @Override
            public void onSaved(Project updated) {
                store().save(updated);
                store().syncDefault(updated);
                core().track("project_updated", null);
                toast("Project updated");
                refresh();
            }
        });
    }

    private void duplicateProject(Project project) {
        Project copy = store().duplicate(project.getId(), project.getName() + " (copy)");
        if (copy == null) {
            toast("The project could not be duplicated");
            return;
        }
        core().track("project_duplicated", null);
        toast("Duplicated: " + copy.getName());
        refresh();
    }

    private void setDefaultProject(Project project) {
        ProjectStore.DefaultResult result = store().setDefaultProject(project.getId());
        if (!result.isSuccess()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_set_default)
                    .setMessage(result.getMessage())
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show();
            return;
        }
        toast("Default project: " + project.getName());
        refresh();
    }

    private void removeFromRegistry(final Project project) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove_registry_title)
                .setMessage(R.string.remove_registry_message)
                .setPositiveButton(R.string.action_remove_registry,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                store().removeFromRegistry(project.getId());
                                core().track("project_unregistered", null);
                                toast("Removed from registry: " + project.getName());
                                refresh();
                            }
                        })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void deleteProject(final Project project) {
        new AlertDialog.Builder(this)
                .setTitle("Delete project record?")
                .setMessage("\"" + project.getName() + "\" is removed from the registry. "
                        + "Files on disk are never deleted.")
                .setPositiveButton(R.string.action_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        store().delete(project.getId());
                        core().track("project_deleted", null);
                        toast("Project record deleted");
                        refresh();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    /** Opens the per-project UI configuration editor. */
    private void openProjectUIEditor(Project project) {
        Intent intent = new Intent(this, ProjectUIEditorActivity.class);
        intent.putExtra(ProjectUIEditorActivity.EXTRA_PROJECT_ID, project.getId());
        intent.putExtra(ProjectUIEditorActivity.EXTRA_PROJECT_NAME, project.getName());
        startActivity(intent);
    }

    private void showProjectInfo(Project project) {
        ProjectInfoDialog.show(this, project, new ProjectInfoDialog.Listener() {
            @Override
            public void onPreview(Project target) {
                previewProject(target);
            }

            @Override
            public void onRun(Project target) {
                runProject(target);
            }

            @Override
            public void onRemoveFromRegistry(Project target) {
                removeFromRegistry(target);
            }
        });
    }

    // ==================== Adapter callbacks ====================

    @Override
    public void onProjectClicked(Project project) {
        runProject(project);
    }

    @Override
    public void onProjectMenu(final Project project, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.action_run);
        menu.getMenu().add(0, 2, 1, R.string.action_preview);
        menu.getMenu().add(0, 3, 2, R.string.action_info);
        menu.getMenu().add(0, 4, 3, R.string.action_edit);
        menu.getMenu().add(0, 5, 4, R.string.action_duplicate);
        menu.getMenu().add(0, 6, 5, R.string.action_set_default);
        menu.getMenu().add(0, 9, 6, R.string.ui_editor_title);
        menu.getMenu().add(0, 7, 7, R.string.action_remove_registry);
        menu.getMenu().add(0, 8, 8, R.string.action_delete);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        runProject(project);
                        return true;
                    case 2:
                        previewProject(project);
                        return true;
                    case 3:
                        showProjectInfo(project);
                        return true;
                    case 4:
                        editProject(project);
                        return true;
                    case 5:
                        duplicateProject(project);
                        return true;
                    case 6:
                        setDefaultProject(project);
                        return true;
                    case 9:
                        openProjectUIEditor(project);
                        return true;
                    case 7:
                        removeFromRegistry(project);
                        return true;
                    case 8:
                        deleteProject(project);
                        return true;
                    default:
                        return false;
                }
            }
        });
        menu.show();
    }

    // ==================== Menu ====================

    /** One-line summary of the active Splash and Bootstrap sources. */
    private String sourceSummary() {
        SourceConfig splash = SourceConfigStore.load(this, SourceKind.SPLASH);
        BootstrapSource.Selection bootstrap = BootstrapSource.resolve(this);
        return "Splash " + splash.getActiveSource().getLabel()
                + " · Bootstrap " + bootstrap.getSourceLabel()
                + (bootstrap.isUsable() ? " (" + (bootstrap.isWebMode() ? "web" : "native") + ")" : "");
    }

    /** Re-runs storage initialisation and re-registers the default project. */
    private void repairStorage() {
        RONiLiFEOSStorageInitializer.Result result =
                RONiLiFEOSStorageInitializer.repair(this, store());
        refresh();
        android.widget.Toast.makeText(this, result.getMessage(),
                android.widget.Toast.LENGTH_LONG).show();
    }

    /** Opens the Default/Runtime source manager for Splash or Bootstrap. */
    private void showSourceManager() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_sources)
                .setItems(new String[]{"Splash sources", "Bootstrap sources"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                SourceManagerDialog.show(BootstrapActivity.this,
                                        which == 0 ? SourceKind.SPLASH : SourceKind.BOOTSTRAP);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The inspector owns its export result; forward before any other handler claims it.
        if (inspectorSheet != null
                && inspectorSheet.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        if (SourceManagerDialog.handleActivityResult(this, requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bootstrap, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_automation) {
            startActivity(new Intent(this,
                    com.roni.life.os.app.automation.AutomationActivity.class));
            return true;
        }
        if (id == R.id.action_file_manager) {
            startActivity(new Intent(this,
                    com.roni.life.os.app.filemanager.FileManagerActivity.class));
            return true;
        }
        if (id == R.id.action_notifications) {
            startActivity(new Intent(this, NotificationCenterActivity.class));
            return true;
        }
        if (id == R.id.action_notification_settings) {
            startActivity(new Intent(this, NotificationSettingsActivity.class));
            return true;
        }
        if (id == R.id.action_settings) {
            showAppSettings();
            return true;
        }
        if (id == R.id.action_open_bootstrap) {
            openBootstrapManually();
            return true;
        }
        if (id == R.id.action_downloads) {
            startActivity(new Intent(this, DownloadsActivity.class));
            return true;
        }
        if (id == R.id.action_repair_storage) {
            repairStorage();
            return true;
        }
        if (id == R.id.action_sources) {
            showSourceManager();
            return true;
        }
        if (id == R.id.action_diagnostics) {
            showDiagnostics();
            return true;
        }
        if (id == R.id.action_about) {
            showAbout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Opens the active Bootstrap project on explicit user request.
     *
     * <p>This is the only path that opens a Bootstrap project while the startup setting is off.
     * It never touches the Splash setting and never promotes the project to the default
     * application project.</p>
     */
    private void openBootstrapManually() {
        Project bootstrap = store().activeBootstrap();
        if (bootstrap == null) {
            toast(getString(R.string.bootstrap_none_registered));
            return;
        }
        if (bootstrap.isWebBootstrap()) {
            Intent intent = new Intent(this, BootstrapWebActivity.class);
            intent.putExtra(BootstrapWebActivity.EXTRA_PROJECT_ID, bootstrap.getId());
            startActivity(intent);
            return;
        }
        // A native Bootstrap project is this screen's own initialisation layer, so a manual
        // launch runs the checks and reports the outcome instead of stacking a second Activity.
        ProjectValidator.Result result = ProjectValidator.validate(bootstrap, store().getAll());
        String status = result.isValid()
                ? getString(R.string.bootstrap_ready)
                : result.getFirstError();
        new AlertDialog.Builder(this)
                .setTitle(bootstrap.getName())
                .setMessage(getString(R.string.bootstrap_manual_summary,
                        bootstrap.getBootstrapRenderingMode().getLabel(), status))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Dashboard mode plus the main-app startup switches. */
    private void showAppSettings() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_app_settings, null, false);
        final RadioGroup modeGroup = view.findViewById(R.id.group_dashboard_mode);
        final RadioButton nativeMode = view.findViewById(R.id.radio_dashboard_native);
        final RadioButton webMode = view.findViewById(R.id.radio_dashboard_web);
        final SwitchCompat dashboardSwitch = view.findViewById(R.id.switch_app_dashboard);
        final SwitchCompat splashSwitch = view.findViewById(R.id.switch_app_splash);
        final SwitchCompat bootstrapSwitch = view.findViewById(R.id.switch_app_bootstrap);
        final SwitchCompat telemetrySwitch = view.findViewById(R.id.switch_app_telemetry);
        final SwitchCompat debugSwitch = view.findViewById(R.id.switch_app_debug);

        final AppConfig current = config();
        nativeMode.setChecked(!current.isWebDashboard());
        webMode.setChecked(current.isWebDashboard());
        dashboardSwitch.setChecked(current.isDashboardEnabled());
        splashSwitch.setChecked(current.isSplashEnabled());
        bootstrapSwitch.setChecked(current.isBootstrapEnabled());
        telemetrySwitch.setChecked(current.isTelemetryEnabled());
        debugSwitch.setChecked(current.isDebugEnabled());

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(view)
                .setPositiveButton(R.string.dialog_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AppConfig updated = config();
                        updated.setDashboardMode(modeGroup.getCheckedRadioButtonId()
                                == R.id.radio_dashboard_web
                                ? AppConfig.DASHBOARD_WEB : AppConfig.DASHBOARD_NATIVE);
                        updated.setDashboardEnabled(dashboardSwitch.isChecked());
                        updated.setSplashEnabled(splashSwitch.isChecked());
                        updated.setBootstrapEnabled(bootstrapSwitch.isChecked());
                        updated.setTelemetryEnabled(telemetrySwitch.isChecked());
                        updated.setDebugEnabled(debugSwitch.isChecked());
                        core().saveConfig(updated);
                        AppLog.i("Startup settings updated: splashEnabled="
                                + updated.isSplashEnabled()
                                + " dashboardEnabled=" + updated.isDashboardEnabled());
                        toast("Settings saved");
                        refresh();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    /** Real state of every NativeRuntime engine plus the live session table. */
    private void showDiagnostics() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = UiUtils.dp(this, 20);
        container.setPadding(padding, padding, padding, padding);
        scroll.addView(container);

        TextView header = new TextView(this);
        header.setText(RuntimeBridge.get().getRuntimeSummary());
        header.setTextSize(14f);
        header.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        header.setPadding(0, 0, 0, UiUtils.dp(this, 12));
        container.addView(header);

        for (RuntimeBridge.EngineStatus status : RuntimeBridge.get().getEngineStatuses()) {
            TextView row = new TextView(this);
            String detail = status.getDetail().isEmpty() ? "" : " · " + status.getDetail();
            row.setText((status.isInitialized() ? "✓ " : "✗ ") + status.getName() + detail);
            row.setTextSize(13f);
            row.setTextColor(ContextCompat.getColor(this, status.isInitialized()
                    ? R.color.text_primary : R.color.state_warning));
            row.setPadding(0, UiUtils.dp(this, 6), 0, UiUtils.dp(this, 6));
            container.addView(row);
        }

        TextView sessionHeader = new TextView(this);
        sessionHeader.setText("Sessions (" + ProjectSessionManager.get().count() + "/"
                + ProjectSessionManager.MAX_SESSIONS + ")");
        sessionHeader.setTextSize(14f);
        sessionHeader.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        sessionHeader.setPadding(0, UiUtils.dp(this, 16), 0, UiUtils.dp(this, 6));
        container.addView(sessionHeader);

        List<ProjectSessionManager.Session> sessions = ProjectSessionManager.get().getSessions();
        if (sessions.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No project session is running.");
            empty.setTextSize(13f);
            empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            container.addView(empty);
        } else {
            for (ProjectSessionManager.Session session : sessions) {
                TextView row = new TextView(this);
                row.setText("• " + session.getName() + " · " + session.getState()
                        + (session.isPreview() ? " · preview" : ""));
                row.setTextSize(13f);
                row.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                row.setPadding(0, UiUtils.dp(this, 4), 0, UiUtils.dp(this, 4));
                container.addView(row);
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_diagnostics)
                .setView(scroll)
                .setPositiveButton(R.string.dialog_close, null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("RONi LiFE OS")
                .setMessage("A native Android system controller built on the NativeRuntime "
                        + "ecosystem.\n\n"
                        + "Three activities: SplashActivity, BootstrapActivity and "
                        + "ProjectActivity. The native dashboard is the authoritative control "
                        + "centre; the optional web dashboard runs inside the runtime container "
                        + "and can only use approved bridge APIs.")
                .setPositiveButton(R.string.dialog_close, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Close RONi LiFE OS?")
                .setMessage("The project manager will be closed. Running project sessions are "
                        + "released.")
                .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finishAffinity();
                    }
                })
                .setNegativeButton("Stay", null)
                .show();
    }
}
