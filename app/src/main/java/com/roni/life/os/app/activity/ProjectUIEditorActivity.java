package com.roni.life.os.app.activity;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import androidx.appcompat.widget.SwitchCompat;
import com.roni.library.ui.config.ProjectUIConfig;
import com.roni.library.ui.config.ProjectUIConfigPresets;
import com.roni.library.ui.config.ProjectUIConfigValidator;
import com.roni.library.ui.manager.ComponentType;
import com.roni.life.os.app.R;
import com.roni.life.os.app.uiconfig.ProjectUIConfigStore;
import com.roni.life.os.app.util.AppLog;
import com.roni.life.os.app.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-project UI configuration editor.
 *
 * <p>Edits a working copy of the project's {@link ProjectUIConfig}. Nothing is persisted until
 * Save is pressed, so leaving the screen discards every change. The editor never touches another
 * project's record — the store key is namespaced by project id.</p>
 */
public class ProjectUIEditorActivity extends BaseActivity {

    public static final String EXTRA_PROJECT_ID = "extra_ui_project_id";
    public static final String EXTRA_PROJECT_NAME = "extra_ui_project_name";

    private static final String[] SPLASH_MODES = {"NATIVE", "WEB", "NONE"};
    private static final String[] SPLASH_ANIMATIONS = {"NONE", "FADE", "SLIDE_UP", "SCALE", "PULSE"};
    private static final String[] DASHBOARD_MODES = {"NATIVE", "WEB"};
    private static final String[] DASHBOARD_LAYOUTS = {"STANDARD", "COMPACT", "GRID"};
    private static final String[] THEME_MODES = {"SYSTEM", "LIGHT", "DARK"};

    private String projectId;
    private ProjectUIConfigStore store;
    private ProjectUIConfig config;

    private SwitchCompat splashEnabled;
    private Spinner splashMode;
    private EditText splashDuration;
    private EditText splashLogo;
    private Spinner splashAnimation;
    private SwitchCompat splashFullscreen;
    private SwitchCompat splashSkip;

    private SwitchCompat dashboardEnabled;
    private Spinner dashboardMode;
    private Spinner dashboardLayout;
    private SwitchCompat dashboardToolbar;
    private SwitchCompat dashboardDrawer;
    private SwitchCompat dashboardBottomNav;
    private SwitchCompat dashboardFab;
    private SwitchCompat dashboardFullscreen;
    private SwitchCompat dashboardStats;
    private SwitchCompat dashboardRecent;

    private Spinner themeMode;
    private EditText themePrimary;
    private EditText themeAccent;
    private EditText themeAnimation;

    private LinearLayout componentContainer;
    private TextView componentSummary;
    private TextView validationSummary;

    private SwitchCompat webViewEnabled;
    private SwitchCompat webViewFullscreen;
    private SwitchCompat webViewJavaScript;
    private SwitchCompat webViewDom;
    private SwitchCompat webViewZoom;
    private SwitchCompat webViewProgress;

    private SwitchCompat navStatusBar;
    private SwitchCompat navNavBar;
    private SwitchCompat navFullscreen;
    private SwitchCompat navImmersive;
    private SwitchCompat navEdgeToEdge;
    private SwitchCompat navBack;

    // ==================== Lifecycle ====================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_ui_editor);

        projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        String projectName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);

        store = ProjectUIConfigStore.forApp();
        config = store.load(projectId);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(projectName != null && !projectName.isEmpty()
                ? projectName : getString(R.string.ui_editor_title));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        bindViews();
        buildPresets();
        buildComponents();
        populate();

        findViewById(R.id.button_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                save();
            }
        });
    }

    private void bindViews() {
        splashEnabled = findViewById(R.id.switch_splash_enabled);
        splashMode = findViewById(R.id.spinner_splash_mode);
        splashDuration = findViewById(R.id.edit_splash_duration);
        splashLogo = findViewById(R.id.edit_splash_logo);
        splashAnimation = findViewById(R.id.spinner_splash_animation);
        splashFullscreen = findViewById(R.id.switch_splash_fullscreen);
        splashSkip = findViewById(R.id.switch_splash_skip);

        dashboardEnabled = findViewById(R.id.switch_dashboard_enabled);
        dashboardMode = findViewById(R.id.spinner_dashboard_mode);
        dashboardLayout = findViewById(R.id.spinner_dashboard_layout);
        dashboardToolbar = findViewById(R.id.switch_dashboard_toolbar);
        dashboardDrawer = findViewById(R.id.switch_dashboard_drawer);
        dashboardBottomNav = findViewById(R.id.switch_dashboard_bottom_nav);
        dashboardFab = findViewById(R.id.switch_dashboard_fab);
        dashboardFullscreen = findViewById(R.id.switch_dashboard_fullscreen);
        dashboardStats = findViewById(R.id.switch_dashboard_stats);
        dashboardRecent = findViewById(R.id.switch_dashboard_recent);

        themeMode = findViewById(R.id.spinner_theme_mode);
        themePrimary = findViewById(R.id.edit_theme_primary);
        themeAccent = findViewById(R.id.edit_theme_accent);
        themeAnimation = findViewById(R.id.edit_theme_animation);

        componentContainer = findViewById(R.id.component_container);
        componentSummary = findViewById(R.id.component_summary);
        validationSummary = findViewById(R.id.validation_summary);

        webViewEnabled = findViewById(R.id.switch_webview_enabled);
        webViewFullscreen = findViewById(R.id.switch_webview_fullscreen);
        webViewJavaScript = findViewById(R.id.switch_webview_javascript);
        webViewDom = findViewById(R.id.switch_webview_dom);
        webViewZoom = findViewById(R.id.switch_webview_zoom);
        webViewProgress = findViewById(R.id.switch_webview_progress);

        navStatusBar = findViewById(R.id.switch_nav_status_bar);
        navNavBar = findViewById(R.id.switch_nav_nav_bar);
        navFullscreen = findViewById(R.id.switch_nav_fullscreen);
        navImmersive = findViewById(R.id.switch_nav_immersive);
        navEdgeToEdge = findViewById(R.id.switch_nav_edge_to_edge);
        navBack = findViewById(R.id.switch_nav_back);

        bindSpinner(splashMode, SPLASH_MODES);
        bindSpinner(splashAnimation, SPLASH_ANIMATIONS);
        bindSpinner(dashboardMode, DASHBOARD_MODES);
        bindSpinner(dashboardLayout, DASHBOARD_LAYOUTS);
        bindSpinner(themeMode, THEME_MODES);
    }

    private void bindSpinner(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    // ==================== Presets ====================

    private void buildPresets() {
        LinearLayout container = findViewById(R.id.preset_container);
        container.removeAllViews();

        for (final String presetId : ProjectUIConfigPresets.ids()) {
            MaterialButton chip = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            chip.setText(ProjectUIConfigPresets.label(presetId));
            chip.setTextSize(12f);
            chip.setAllCaps(false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(UiUtils.dp(this, 8));
            chip.setLayoutParams(params);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    config = ProjectUIConfigPresets.create(presetId);
                    populate();
                    toast(getString(R.string.ui_editor_preset_applied,
                            ProjectUIConfigPresets.label(presetId)));
                }
            });
            container.addView(chip);
        }
    }

    // ==================== Component list ====================

    private void buildComponents() {
        componentContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        ComponentType.Group currentGroup = null;
        for (final ComponentType type : ComponentType.values()) {
            if (type == ComponentType.CUSTOM_CONTENT || type == ComponentType.SYSTEM_UI) {
                continue;
            }

            if (type.getGroup() != currentGroup) {
                currentGroup = type.getGroup();
                TextView header = new TextView(this);
                header.setText(currentGroup.getDisplayName());
                header.setTextSize(12f);
                header.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
                header.setPadding(0, UiUtils.dp(this, 14), 0, UiUtils.dp(this, 4));
                componentContainer.addView(header);
            }

            View row = inflater.inflate(R.layout.item_component_config,
                    componentContainer, false);
            TextView name = row.findViewById(R.id.component_name);
            TextView meta = row.findViewById(R.id.component_meta);
            SwitchCompat toggle = row.findViewById(R.id.component_switch);

            name.setText(type.getDisplayName());
            meta.setText("#" + type.getComponentNumber() + " · " + type.name());

            toggle.setChecked(config.isEnabled(type));
            toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                config.setEnabled(type, isChecked);
                updateComponentSummary();
                updateValidation();
            });

            componentContainer.addView(row);
        }
        updateComponentSummary();
    }

    private void updateComponentSummary() {
        int enabled = config.getEnabledComponentCount();
        int total = ComponentType.values().length - 2;
        componentSummary.setText(getString(R.string.ui_editor_components_enabled, enabled, total));
    }

    // ==================== Populate / collect ====================

    private void populate() {
        ProjectUIConfig.Splash splash = config.getSplash();
        splashEnabled.setChecked(splash.isEnabled());
        splashMode.setSelection(indexOf(SPLASH_MODES, splash.getMode().getKey()));
        splashDuration.setText(String.valueOf(splash.getDurationMs()));
        splashLogo.setText(splash.getLogoText());
        splashAnimation.setSelection(indexOf(SPLASH_ANIMATIONS, splash.getAnimation().getKey()));
        splashFullscreen.setChecked(splash.isFullscreen());
        splashSkip.setChecked(splash.isSkipOnTap());

        ProjectUIConfig.Dashboard dashboard = config.getDashboard();
        dashboardEnabled.setChecked(dashboard.isEnabled());
        dashboardMode.setSelection(indexOf(DASHBOARD_MODES, dashboard.getMode().getKey()));
        dashboardLayout.setSelection(indexOf(DASHBOARD_LAYOUTS, dashboard.getLayout().getKey()));
        dashboardToolbar.setChecked(dashboard.isToolbarEnabled());
        dashboardDrawer.setChecked(dashboard.isDrawerEnabled());
        dashboardBottomNav.setChecked(dashboard.isBottomNavEnabled());
        dashboardFab.setChecked(dashboard.isFabEnabled());
        dashboardFullscreen.setChecked(dashboard.isFullscreen());
        dashboardStats.setChecked(dashboard.isShowStats());
        dashboardRecent.setChecked(dashboard.isShowRecent());

        ProjectUIConfig.Theme theme = config.getTheme();
        themeMode.setSelection(indexOf(THEME_MODES, theme.getMode().getKey()));
        themePrimary.setText(hex(theme.getPrimaryColor()));
        themeAccent.setText(hex(theme.getAccentColor()));
        themeAnimation.setText(String.valueOf(theme.getAnimationDurationMs()));

        ProjectUIConfig.WebView webView = config.getWebView();
        webViewEnabled.setChecked(webView.isEnabled());
        webViewFullscreen.setChecked(webView.isFullscreen());
        webViewJavaScript.setChecked(webView.isJavaScriptEnabled());
        webViewDom.setChecked(webView.isDomStorageEnabled());
        webViewZoom.setChecked(webView.isZoomEnabled());
        webViewProgress.setChecked(webView.isProgressBarEnabled());

        ProjectUIConfig.Navigation navigation = config.getNavigation();
        navStatusBar.setChecked(navigation.isStatusBarVisible());
        navNavBar.setChecked(navigation.isNavigationBarVisible());
        navFullscreen.setChecked(navigation.isFullscreen());
        navImmersive.setChecked(navigation.isImmersive());
        navEdgeToEdge.setChecked(navigation.isEdgeToEdge());
        navBack.setChecked(navigation.isBackNavigationEnabled());

        // Component switches are rebuilt so they reflect the new configuration.
        buildComponents();
        updateValidation();
    }

    private void collect() {
        ProjectUIConfig.Splash splash = config.getSplash();
        splash.setEnabled(splashEnabled.isChecked())
                .setMode(ProjectUIConfig.SplashMode.fromKey(
                        (String) splashMode.getSelectedItem()))
                .setDurationMs(parseInt(splashDuration.getText().toString(),
                        splash.getDurationMs()))
                .setLogoText(splashLogo.getText().toString())
                .setAnimation(ProjectUIConfig.SplashAnimation.fromKey(
                        (String) splashAnimation.getSelectedItem()))
                .setFullscreen(splashFullscreen.isChecked())
                .setSkipOnTap(splashSkip.isChecked());

        ProjectUIConfig.Dashboard dashboard = config.getDashboard();
        dashboard.setEnabled(dashboardEnabled.isChecked())
                .setMode(ProjectUIConfig.DashboardMode.fromKey(
                        (String) dashboardMode.getSelectedItem()))
                .setLayout(ProjectUIConfig.DashboardLayout.fromKey(
                        (String) dashboardLayout.getSelectedItem()))
                .setToolbarEnabled(dashboardToolbar.isChecked())
                .setDrawerEnabled(dashboardDrawer.isChecked())
                .setBottomNavEnabled(dashboardBottomNav.isChecked())
                .setFabEnabled(dashboardFab.isChecked())
                .setFullscreen(dashboardFullscreen.isChecked())
                .setShowStats(dashboardStats.isChecked())
                .setShowRecent(dashboardRecent.isChecked());

        ProjectUIConfig.Theme theme = config.getTheme();
        theme.setMode(ProjectUIConfig.ThemeMode.fromKey((String) themeMode.getSelectedItem()))
                .setPrimaryColor(parseColor(themePrimary.getText().toString(),
                        theme.getPrimaryColor()))
                .setAccentColor(parseColor(themeAccent.getText().toString(),
                        theme.getAccentColor()))
                .setAnimationDurationMs(parseInt(themeAnimation.getText().toString(),
                        theme.getAnimationDurationMs()));

        ProjectUIConfig.WebView webView = config.getWebView();
        webView.setEnabled(webViewEnabled.isChecked())
                .setFullscreen(webViewFullscreen.isChecked())
                .setJavaScriptEnabled(webViewJavaScript.isChecked())
                .setDomStorageEnabled(webViewDom.isChecked())
                .setZoomEnabled(webViewZoom.isChecked())
                .setProgressBarEnabled(webViewProgress.isChecked());

        ProjectUIConfig.Navigation navigation = config.getNavigation();
        navigation.setStatusBarVisible(navStatusBar.isChecked())
                .setNavigationBarVisible(navNavBar.isChecked())
                .setFullscreen(navFullscreen.isChecked())
                .setImmersive(navImmersive.isChecked())
                .setEdgeToEdge(navEdgeToEdge.isChecked())
                .setBackNavigationEnabled(navBack.isChecked());
    }

    // ==================== Validation ====================

    private void updateValidation() {
        List<ProjectUIConfigValidator.Issue> issues =
                ProjectUIConfigValidator.validate(config);
        if (issues.isEmpty()) {
            validationSummary.setText(R.string.ui_editor_validation_ok);
            validationSummary.setTextColor(ContextCompat.getColor(this, R.color.state_success));
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.ui_editor_validation_issues, issues.size()));
        for (ProjectUIConfigValidator.Issue issue : issues) {
            builder.append('\n').append("• ").append(issue.getMessage());
        }
        validationSummary.setText(builder.toString());
        validationSummary.setTextColor(ContextCompat.getColor(this, R.color.state_warning));
    }

    // ==================== Actions ====================

    private void save() {
        collect();
        if (!ProjectUIConfigValidator.isValid(config)) {
            toast(ProjectUIConfigValidator.firstError(config));
            updateValidation();
            return;
        }
        if (store.save(projectId, config)) {
            toast(getString(R.string.ui_editor_saved));
            finish();
        } else {
            toast("The configuration could not be saved");
        }
    }

    private void resetConfig() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.ui_editor_menu_reset)
                .setMessage(R.string.ui_editor_reset_confirm)
                .setPositiveButton(R.string.ui_editor_menu_reset,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                config = new ProjectUIConfig();
                                populate();
                                toast(getString(R.string.ui_editor_reset_done));
                            }
                        })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void exportConfig() {
        collect();
        final String payload = config.encode();
        final EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setText(payload);
        field.setSelection(payload.length());

        new AlertDialog.Builder(this)
                .setTitle(R.string.ui_editor_export_title)
                .setView(field)
                .setPositiveButton(R.string.ui_editor_copy,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                copyToClipboard(payload);
                            }
                        })
                .setNegativeButton(R.string.dialog_close, null)
                .show();
    }

    private void importConfig() {
        final EditText field = new EditText(this);
        field.setHint(R.string.ui_editor_import_hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        new AlertDialog.Builder(this)
                .setTitle(R.string.ui_editor_import_title)
                .setView(field)
                .setPositiveButton(R.string.ui_editor_menu_import,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String raw = field.getText().toString().trim();
                                try {
                                    if (!raw.contains("v=")) {
                                        toast(getString(R.string.ui_editor_import_failed));
                                        return;
                                    }
                                    ProjectUIConfig imported = ProjectUIConfig.decode(raw);
                                    config = imported;
                                    populate();
                                    toast(getString(R.string.ui_editor_import_done));
                                } catch (Throwable error) {
                                    AppLog.e("UI config import failed", error);
                                    toast(getString(R.string.ui_editor_import_failed));
                                }
                            }
                        })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void copyToClipboard(String text) {
        try {
            android.content.ClipboardManager manager =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(
                        android.content.ClipData.newPlainText("ui-config", text));
                toast(getString(R.string.ui_editor_copied));
            }
        } catch (Throwable error) {
            AppLog.e("Clipboard unavailable", error);
        }
    }

    // ==================== Menu ====================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.ui_editor_menu_save);
        menu.add(0, 2, 1, R.string.ui_editor_menu_reset);
        menu.add(0, 3, 2, R.string.ui_editor_menu_export);
        menu.add(0, 4, 3, R.string.ui_editor_menu_import);
        menu.add(0, 5, 4, R.string.ui_editor_menu_validate);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                save();
                return true;
            case 2:
                resetConfig();
                return true;
            case 3:
                exportConfig();
                return true;
            case 4:
                importConfig();
                return true;
            case 5:
                collect();
                updateValidation();
                toast(ProjectUIConfigValidator.isValid(config)
                        ? getString(R.string.ui_editor_validation_ok)
                        : ProjectUIConfigValidator.firstError(config));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // ==================== Helpers ====================

    private static int indexOf(String[] values, String value) {
        if (value != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equalsIgnoreCase(value)) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static int parseColor(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Color.parseColor(raw.trim());
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }

    private static String hex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }
}
