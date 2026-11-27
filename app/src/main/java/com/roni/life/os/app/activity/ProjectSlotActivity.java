package com.roni.life.os.app.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.roni.life.os.app.R;
import com.roni.life.os.app.project.Project;
import com.roni.life.os.app.project.ProjectImporter;
import com.roni.life.os.app.project.ProjectRegistrationService;
import com.roni.life.os.app.project.ProjectSlot;
import com.roni.life.os.app.project.ProjectSourceType;
import com.roni.life.os.app.project.ProjectTestRunner;
import com.roni.life.os.app.project.SafPermissionManager;
import com.roni.life.os.app.runtime.ProjectLaunchRouter;
import com.roni.life.os.app.runtime.SafWebSourceProvider;
import com.roni.life.os.app.ui.ProjectEditDialog;
import com.roni.life.os.app.ui.SlotProjectAdapter;
import com.roni.life.os.app.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Management screen for one Dashboard slot.
 *
 * <p>One activity serves all four slots because the rules live in {@link ProjectSlot}, not in the
 * screen. The screen only decides which controls to show:</p>
 *
 * <ul>
 *   <li>single-project slots show the active project card plus a draft card;</li>
 *   <li>{@link ProjectSlot#APP} shows a list of registered projects plus a draft card;</li>
 *   <li>{@link ProjectSlot#WORKSPACE} replaces Register with "Use this folder", because a
 *       workspace is a reference and must never be registered as an app project.</li>
 * </ul>
 *
 * <p>Preview, Test and Register are three separate buttons backed by three separate services.
 * Preview never writes, Test never writes, and Register is the only action that copies a source
 * or touches the registry.</p>
 */
public class ProjectSlotActivity extends BaseActivity implements SlotProjectAdapter.Listener {

    /** Which slot this screen manages. */
    public static final String EXTRA_SLOT = "extra_slot";

    private ProjectSlot slot;
    private SlotProjectAdapter adapter;

    /**
     * Source types this screen can prompt for.
     *
     * <p>Bundled {@code ASSET} / {@code ZIP_ASSET} sources are registered programmatically (first
     * run seeding, bundled demos), so they are accepted by the slot rules but never offered here.</p>
     */
    private static final Set<ProjectSourceType> PICKABLE_SOURCES = EnumSet.of(
            ProjectSourceType.REMOTE_URL,
            ProjectSourceType.INLINE_HTML,
            ProjectSourceType.ZIP_FILE,
            ProjectSourceType.SAF_FOLDER);

    /** The project being configured but not yet registered. */
    private Project draft;
    /** Directory created by a ZIP import that must be cleaned up if the draft is discarded. */
    private File draftTempDir;
    /** The project a registration would replace. */
    private String replaceTargetId;

    private View currentCard;
    private View emptyState;
    private View draftCard;
    private View draftLabel;
    private View appHeader;
    private View appEmpty;
    private RecyclerView projectList;
    private TextView hint;

    private TextView currentName;
    private TextView currentStatus;
    private TextView currentSource;
    private TextView currentLocation;
    private TextView currentEntry;

    private TextView draftName;
    private TextView draftSource;
    private TextView draftLocation;
    private TextView draftRegister;

    private ActivityResultLauncher<Uri> folderPicker;
    private ActivityResultLauncher<String[]> zipPicker;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        slot = ProjectSlot.fromKey(getIntent() != null
                ? getIntent().getStringExtra(EXTRA_SLOT) : null);

        setContentView(R.layout.activity_project_slot);

        Toolbar toolbar = findViewById(R.id.slot_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(slot.getLabel());
        toolbar.setSubtitle(slot.isSingleProject()
                ? getString(R.string.slot_single_rule) : getString(R.string.slot_multi_rule));
        toolbar.setNavigationOnClickListener(view -> finish());

        bindViews();
        registerPickers();
        applySlotRules();
        refresh();
    }

    private void bindViews() {
        currentCard = findViewById(R.id.slot_current_card);
        emptyState = findViewById(R.id.slot_empty);
        draftCard = findViewById(R.id.slot_draft_card);
        draftLabel = findViewById(R.id.slot_draft_label);
        appHeader = findViewById(R.id.slot_app_header);
        appEmpty = findViewById(R.id.slot_app_empty);
        projectList = findViewById(R.id.slot_project_list);
        hint = findViewById(R.id.slot_hint);

        currentName = findViewById(R.id.slot_current_name);
        currentStatus = findViewById(R.id.slot_current_status);
        currentSource = findViewById(R.id.slot_current_source);
        currentLocation = findViewById(R.id.slot_current_location);
        currentEntry = findViewById(R.id.slot_current_entry);

        draftName = findViewById(R.id.slot_draft_name);
        draftSource = findViewById(R.id.slot_draft_source);
        draftLocation = findViewById(R.id.slot_draft_location);
        draftRegister = findViewById(R.id.slot_draft_register);

        adapter = new SlotProjectAdapter(this);
        projectList.setLayoutManager(new LinearLayoutManager(this));
        projectList.setAdapter(adapter);
        projectList.setNestedScrollingEnabled(false);

        findViewById(R.id.slot_setup).setOnClickListener(view -> chooseSource(null));
        findViewById(R.id.slot_add_project).setOnClickListener(view -> chooseSource(null));
        findViewById(R.id.slot_current_open).setOnClickListener(view -> openCurrent());
        findViewById(R.id.slot_current_preview).setOnClickListener(view -> previewCurrent());
        findViewById(R.id.slot_current_test).setOnClickListener(view -> testCurrent());
        findViewById(R.id.slot_current_configure).setOnClickListener(view -> configureCurrent());
        findViewById(R.id.slot_current_replace).setOnClickListener(view -> replaceCurrent());

        findViewById(R.id.slot_draft_preview).setOnClickListener(view -> previewDraft());
        findViewById(R.id.slot_draft_test).setOnClickListener(view -> testDraft());
        draftRegister.setOnClickListener(view -> confirmRegister());
        findViewById(R.id.slot_draft_discard).setOnClickListener(view -> discardDraft());
    }

    private void registerPickers() {
        folderPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(), this::onFolderPicked);
        zipPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::onZipPicked);
    }

    /** Applies the slot's static rules to the layout. */
    private void applySlotRules() {
        ((TextView) findViewById(R.id.slot_rule_title)).setText(slot.getLabel());
        ((TextView) findViewById(R.id.slot_rule_chip)).setText(slot.isSingleProject()
                ? getString(R.string.slot_single_rule) : getString(R.string.slot_multi_rule));
        ((TextView) findViewById(R.id.slot_rule_summary)).setText(slot.getRuleSummary());

        boolean multi = !slot.isSingleProject();
        UiUtils.setVisible(appHeader, multi);
        UiUtils.setVisible(projectList, multi);
        UiUtils.setVisible(findViewById(R.id.slot_current_label), !multi);

        if (slot.isReferenceOnly()) {
            draftRegister.setText(R.string.slot_action_save_reference);
            hint.setText(R.string.slot_workspace_hint);
            UiUtils.setVisible(hint, true);
        } else {
            draftRegister.setText(R.string.slot_action_register);
            UiUtils.setVisible(hint, false);
        }
    }

    // ==================== State ====================

    private void refresh() {
        List<Project> all = store().getAll();
        List<Project> mine = ProjectRegistrationService.projectsOf(all, slot);

        if (slot.isSingleProject()) {
            Project active = activeProject(mine);
            bindCurrent(active);
            UiUtils.setVisible(currentCard, active != null);
            UiUtils.setVisible(emptyState, active == null);
        } else {
            adapter.submit(mine);
            UiUtils.setVisible(appEmpty, mine.isEmpty());
            UiUtils.setVisible(projectList, !mine.isEmpty());
        }

        bindDraft();
    }

    /** The project this slot currently points at, falling back to the newest one. */
    private Project activeProject(List<Project> mine) {
        if (mine.isEmpty()) {
            return null;
        }
        String activeId = null;
        switch (slot) {
            case SPLASH:
                activeId = config().getActiveSplashProjectId();
                break;
            case DASHBOARD:
                activeId = config().getActiveBootstrapProjectId();
                break;
            case WORKSPACE:
                activeId = config().getActiveWorkspaceProjectId();
                break;
            default:
                break;
        }
        if (activeId != null && !activeId.isEmpty()) {
            for (Project project : mine) {
                if (activeId.equals(project.getId())) {
                    return project;
                }
            }
        }
        return mine.get(0);
    }

    private void bindCurrent(Project project) {
        if (project == null) {
            return;
        }
        currentName.setText(project.getName());
        currentStatus.setText(slot.isReferenceOnly()
                ? getString(R.string.slot_status_reference) : getString(R.string.slot_status_active));
        currentSource.setText("Source · " + project.getSourceType().getKey());
        currentLocation.setText("Location · " + describeLocation(project));
        currentEntry.setText("Entry point · " + entryOf(project));
    }

    private void bindDraft() {
        boolean has = draft != null;
        UiUtils.setVisible(draftCard, has);
        UiUtils.setVisible(draftLabel, has);
        if (!has) {
            return;
        }
        draftName.setText(draft.getName());
        draftSource.setText("Source · " + draft.getSourceType().getKey());
        draftLocation.setText("Location · " + describeLocation(draft));
    }

    private String entryOf(Project project) {
        String entry = project.getEntryPoint();
        return entry != null && !entry.trim().isEmpty() ? entry.trim() : "index.html";
    }

    private String describeLocation(Project project) {
        String path = project.getSourcePath();
        if (path == null || path.trim().isEmpty()) {
            return project.getSourceType() == ProjectSourceType.INLINE_HTML
                    ? "inline document" : "not set";
        }
        return path.trim();
    }

    // ==================== Source selection ====================

    /**
     * Shows the source types this slot accepts.
     *
     * @param replaceId the project a successful registration would replace, or {@code null}
     */
    private void chooseSource(@Nullable String replaceId) {
        replaceTargetId = replaceId;
        final List<ProjectSourceType> sources = new ArrayList<>();
        for (ProjectSourceType type : slot.getSupportedSources()) {
            if (PICKABLE_SOURCES.contains(type)) {
                sources.add(type);
            }
        }
        final String[] labels = new String[sources.size()];
        for (int i = 0; i < sources.size(); i++) {
            labels[i] = sourceLabel(sources.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.slot_choose_source)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onSourceChosen(sources.get(which));
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private String sourceLabel(ProjectSourceType type) {
        switch (type) {
            case REMOTE_URL:
                return getString(R.string.slot_source_remote);
            case ZIP_FILE:
                return getString(R.string.slot_source_zip);
            case INLINE_HTML:
                return getString(R.string.slot_source_inline);
            case SAF_FOLDER:
                return getString(R.string.slot_source_folder);
            default:
                return type.getKey();
        }
    }

    private void onSourceChosen(ProjectSourceType type) {
        switch (type) {
            case REMOTE_URL:
                promptRemoteUrl();
                break;
            case INLINE_HTML:
                promptInlineHtml();
                break;
            case ZIP_FILE:
                zipPicker.launch(new String[]{
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream"
                });
                break;
            case SAF_FOLDER:
                folderPicker.launch(null);
                break;
            default:
                toast("This source type is not supported here.");
                break;
        }
    }

    private void promptRemoteUrl() {
        final EditText input = new EditText(this);
        input.setHint(R.string.slot_remote_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        FrameLayout wrapper = padded(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.slot_remote_title)
                .setView(wrapper)
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String url = input.getText().toString().trim();
                        String lower = url.toLowerCase(Locale.US);
                        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                            toast(getString(R.string.slot_url_invalid));
                            return;
                        }
                        Project project = newDraft("Remote project");
                        project.setSourceType(ProjectSourceType.REMOTE_URL);
                        project.setSourcePath(url);
                        project.setEntryPoint("index.html");
                        showDraft(project, null);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void promptInlineHtml() {
        final EditText input = new EditText(this);
        input.setHint(R.string.slot_inline_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(6);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        FrameLayout wrapper = padded(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.slot_inline_title)
                .setView(wrapper)
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String html = input.getText().toString();
                        if (html.trim().isEmpty()) {
                            toast(getString(R.string.slot_html_empty));
                            return;
                        }
                        Project project = newDraft("Inline project");
                        project.setSourceType(ProjectSourceType.INLINE_HTML);
                        project.setInlineHtml(html);
                        project.setEntryPoint("index.html");
                        showDraft(project, null);
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private FrameLayout padded(View child) {
        FrameLayout wrapper = new FrameLayout(this);
        int padding = UiUtils.dp(this, 20);
        wrapper.setPadding(padding, UiUtils.dp(this, 8), padding, 0);
        wrapper.addView(child, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        return wrapper;
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
        Project project = newDraft(name);
        project.setSourceType(ProjectSourceType.SAF_FOLDER);
        project.setSourcePath(SafPermissionManager.toSourcePath(uri));
        project.setEntryPoint("index.html");
        showDraft(project, null);
    }

    private void onZipPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        final String name = zipDisplayName(uri);
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setMessage(R.string.slot_importing)
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            final ProjectImporter.Result result = ProjectImporter.importZip(
                    getApplicationContext(), uri, name);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.dismiss();
                if (!result.isSuccess()) {
                    toast(getString(R.string.import_failed, result.getMessage()));
                    return;
                }
                Project project = newDraft(name);
                project.setDescription("Imported archive");
                project.setSourceType(ProjectSourceType.FILE);
                project.setSourcePath(result.getDirectory().getAbsolutePath());
                project.setEntryPoint(result.getEntryPoint());
                showDraft(project, result.getDirectory());
            });
        }, "slot-zip-import").start();
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

    private Project newDraft(String name) {
        Project project = new Project();
        project.setName(name);
        project.setProjectType(slot.getProjectType());
        project.setDescription(slot.getLabel() + " project");
        return project;
    }

    private void showDraft(Project project, @Nullable File tempDir) {
        clearDraftTemp();
        draft = project;
        draftTempDir = tempDir;
        bindDraft();
        draftCard.requestFocus();
    }

    private void discardDraft() {
        clearDraftTemp();
        draft = null;
        replaceTargetId = null;
        bindDraft();
    }

    private void clearDraftTemp() {
        if (draftTempDir != null) {
            ProjectImporter.deleteRecursively(draftTempDir);
            draftTempDir = null;
        }
    }

    // ==================== Preview ====================

    private void previewCurrent() {
        Project project = activeProject(ProjectRegistrationService.projectsOf(store().getAll(), slot));
        if (project == null) {
            toast(getString(R.string.slot_preview_unavailable));
            return;
        }
        preview(project);
    }

    private void previewDraft() {
        if (draft == null) {
            return;
        }
        preview(draft);
    }

    /** Opens a transient preview. Nothing is registered and no session is created. */
    private void preview(Project project) {
        if (!slot.allowsPreview()) {
            toast(getString(R.string.slot_preview_unavailable));
            return;
        }
        Intent intent = new Intent(this, ProjectActivity.class);
        intent.putExtra(ProjectActivity.EXTRA_PREVIEW_PROJECT, project.encode());
        startActivity(intent);
    }

    // ==================== Test ====================

    private void testCurrent() {
        Project project = activeProject(ProjectRegistrationService.projectsOf(store().getAll(), slot));
        if (project == null) {
            return;
        }
        runTest(project);
    }

    private void testDraft() {
        if (draft == null) {
            return;
        }
        runTest(draft);
    }

    /** Runs the read-only compatibility check and shows the report. */
    private void runTest(final Project project) {
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setMessage(R.string.slot_test_running)
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            final ProjectTestRunner.Report report = ProjectTestRunner.run(
                    getApplicationContext(), project, store().getAll());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.dismiss();
                showReport(report);
            });
        }, "slot-test").start();
    }

    private void showReport(ProjectTestRunner.Report report) {
        String summary = report.isPassed()
                ? getString(R.string.slot_test_passed)
                : getString(R.string.slot_test_failed, report.getFailureCount());
        new AlertDialog.Builder(this)
                .setTitle(R.string.slot_test_title)
                .setMessage(summary + "\n\n" + report.toText())
                .setPositiveButton(R.string.dialog_ok, null)
                .show();
    }

    // ==================== Register ====================

    private void confirmRegister() {
        if (draft == null) {
            return;
        }
        if (!slot.allowsRegister() && !slot.isReferenceOnly()) {
            toast(getString(R.string.slot_register_unavailable));
            return;
        }
        boolean replacing = replaceTargetId != null && !replaceTargetId.isEmpty();
        int title = slot.isReferenceOnly() ? R.string.slot_action_save_reference
                : R.string.slot_register_title;
        int message = replacing ? R.string.slot_replace_message : R.string.slot_register_message;

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(slot.isReferenceOnly()
                                ? R.string.slot_action_save_reference : R.string.slot_action_register,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                performRegister();
                            }
                        })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void performRegister() {
        final Project target = draft;
        final String previousId = replaceTargetId;
        if (target == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setMessage(R.string.slot_importing)
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            final ProjectRegistrationService.Result result = slot.isReferenceOnly()
                    ? ProjectRegistrationService.saveReference(
                            getApplicationContext(), store(), target, slot, previousId)
                    : ProjectRegistrationService.register(
                            getApplicationContext(), store(), target, slot, previousId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                progress.dismiss();
                if (!result.isSuccess()) {
                    toast(getString(slot.isReferenceOnly()
                            ? R.string.slot_reference_failed
                            : R.string.slot_register_failed, result.getMessage()));
                    return;
                }
                clearDraftTemp();
                draft = null;
                replaceTargetId = null;
                toast(getString(slot.isReferenceOnly()
                        ? R.string.slot_reference_done
                        : R.string.slot_register_done, result.getProject().getName()));
                refresh();
            });
        }, "slot-register").start();
    }

    // ==================== Current project actions ====================

    private void openCurrent() {
        Project project = activeProject(ProjectRegistrationService.projectsOf(store().getAll(), slot));
        if (project == null) {
            toast(getString(R.string.slot_open_unavailable));
            return;
        }
        open(project);
    }

    private void open(Project project) {
        // The Dashboard slot is executed by the native bootstrap, never by a bare WebView
        // activity: BootstrapActivity owns the rendering-mode decision for BOOTSTRAP projects.
        if (slot == ProjectSlot.DASHBOARD) {
            Intent intent = new Intent(this, BootstrapActivity.class);
            intent.putExtra(BootstrapActivity.EXTRA_BOOTSTRAP_PROJECT_ID, project.getId());
            intent.putExtra(BootstrapActivity.EXTRA_MANUAL_LAUNCH, true);
            startActivity(intent);
            store().markOpened(project.getId());
            refresh();
            return;
        }

        ProjectLaunchRouter.Result result = ProjectLaunchRouter.open(this, project);
        if (result.isBlocked() && !result.getMessage().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.session_limit_title)
                    .setMessage(result.getMessage())
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show();
            return;
        }
        store().markOpened(project.getId());
        refresh();
    }

    private void configureCurrent() {
        Project project = activeProject(ProjectRegistrationService.projectsOf(store().getAll(), slot));
        if (project == null) {
            return;
        }
        configure(project);
    }

    private void configure(final Project project) {
        ProjectEditDialog.show(this, project, store().getAll(), new ProjectEditDialog.Listener() {
            @Override
            public void onSaved(Project updated) {
                store().save(updated);
                toast("Project updated");
                refresh();
            }
        });
    }

    private void replaceCurrent() {
        Project project = activeProject(ProjectRegistrationService.projectsOf(store().getAll(), slot));
        if (project == null) {
            chooseSource(null);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.slot_replace_title)
                .setMessage(R.string.slot_replace_message)
                .setPositiveButton(R.string.slot_action_replace,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                chooseSource(project.getId());
                            }
                        })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    // ==================== List callbacks ====================

    @Override
    public void onSlotProjectClicked(Project project) {
        open(project);
    }

    @Override
    public void onSlotProjectMenu(final Project project, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(Menu.NONE, 1, 1, R.string.slot_action_open);
        menu.getMenu().add(Menu.NONE, 2, 2, R.string.slot_action_preview);
        menu.getMenu().add(Menu.NONE, 3, 3, R.string.slot_action_test);
        menu.getMenu().add(Menu.NONE, 4, 4, R.string.slot_action_configure);
        menu.getMenu().add(Menu.NONE, 5, 5, R.string.slot_action_delete);
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        open(project);
                        return true;
                    case 2:
                        preview(project);
                        return true;
                    case 3:
                        runTest(project);
                        return true;
                    case 4:
                        configure(project);
                        return true;
                    case 5:
                        confirmDelete(project);
                        return true;
                    default:
                        return false;
                }
            }
        });
        menu.show();
    }

    private void confirmDelete(final Project project) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.slot_delete_title)
                .setMessage(R.string.slot_delete_message)
                .setPositiveButton(R.string.slot_action_delete,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ProjectRegistrationService.deleteProject(
                                        getApplicationContext(), store(), project.getId());
                                toast(getString(R.string.slot_delete_done));
                                refresh();
                            }
                        })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // A draft that was never registered must not leave an extracted archive behind.
        clearDraftTemp();
    }
}
