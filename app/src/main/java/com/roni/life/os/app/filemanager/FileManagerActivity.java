package com.roni.life.os.app.filemanager;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.roni.library.contracts.filemanager.FileCategory;
import com.roni.library.contracts.filemanager.FileEntry;
import com.roni.library.contracts.filemanager.FileManagerError;
import com.roni.library.contracts.filemanager.FileOperation;
import com.roni.library.contracts.filemanager.SearchQuery;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.filemanager.FileManagerException;
import com.roni.library.platform.filemanager.FileManagerProjectContext;
import com.roni.library.platform.filemanager.FileManagerService;
import com.roni.life.os.app.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Project scoped file explorer: browse, search, select, create, copy, move, delete, share. */
public final class FileManagerActivity extends AppCompatActivity
        implements FileEntryAdapter.Listener {

    public static final String EXTRA_PROJECT_ID = "extra_filemanager_project_id";

    private static final int REQUEST_SAF = 4301;

    private enum Mode { BROWSE, RECENT, FAVORITES, CATEGORY }

    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    private FileManagerService service;
    private FileEntryAdapter adapter;
    private RecyclerView list;
    private TextView breadcrumb;
    private TextView emptyText;
    private ProgressBar progress;
    private EditText searchInput;

    private String currentPath = "";
    private Mode mode = Mode.BROWSE;
    private String category = "";
    private String sortBy = SearchQuery.SORT_NAME;
    private boolean descending;
    private boolean includeHidden;
    private boolean gridMode;
    private final List<String> clipboard = new ArrayList<>();
    private boolean clipboardCut;
    private String activeOperationId = "";

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (service == null || activeOperationId.isEmpty()) {
                return;
            }
            FileOperation operation = service.getOperationStatus(activeOperationId);
            if (operation == null) {
                activeOperationId = "";
                progress.setVisibility(View.GONE);
                return;
            }
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(operation.getProgressPercent());
            if (operation.isFinished()) {
                activeOperationId = "";
                progress.setVisibility(View.GONE);
                if (!FileOperation.STATE_COMPLETED.equals(operation.getState())) {
                    toast(operation.getMessage());
                }
                load();
                return;
            }
            main.postDelayed(this, 400L);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        String projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        if (projectId != null && !projectId.isEmpty()) {
            FileManagerProjectContext.setActiveProject(projectId);
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("File manager");
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        list = findViewById(R.id.file_list);
        breadcrumb = findViewById(R.id.breadcrumb);
        emptyText = findViewById(R.id.empty_text);
        progress = findViewById(R.id.progress);
        searchInput = findViewById(R.id.search_input);

        adapter = new FileEntryAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        service = PlatformEngine.getInstance().getFileManagerService();
        if (service == null) {
            showEmpty("The file manager engine is unavailable");
            return;
        }

        findViewById(R.id.chip_all).setOnClickListener(view -> setMode(Mode.BROWSE, ""));
        findViewById(R.id.chip_images).setOnClickListener(view ->
                setMode(Mode.CATEGORY, FileCategory.IMAGE));
        findViewById(R.id.chip_videos).setOnClickListener(view ->
                setMode(Mode.CATEGORY, FileCategory.VIDEO));
        findViewById(R.id.chip_audio).setOnClickListener(view ->
                setMode(Mode.CATEGORY, FileCategory.AUDIO));
        findViewById(R.id.chip_documents).setOnClickListener(view ->
                setMode(Mode.CATEGORY, FileCategory.DOCUMENT));
        findViewById(R.id.chip_recent).setOnClickListener(view -> setMode(Mode.RECENT, ""));
        findViewById(R.id.chip_favorites).setOnClickListener(view -> setMode(Mode.FAVORITES, ""));

        findViewById(R.id.action_new_folder).setOnClickListener(view -> promptCreate(true));
        findViewById(R.id.action_new_file).setOnClickListener(view -> promptCreate(false));
        findViewById(R.id.action_paste).setOnClickListener(view -> paste());
        findViewById(R.id.action_analyzer).setOnClickListener(view ->
                startActivity(new Intent(this, StorageAnalyzerActivity.class)));
        findViewById(R.id.action_settings).setOnClickListener(view ->
                startActivity(new Intent(this, FileManagerSettingsActivity.class)));

        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            runSearch(searchInput.getText().toString());
            return true;
        });

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (service != null) {
            load();
        }
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(progressTick);
        super.onDestroy();
    }

    // ------------------------------------------------------------------
    // loading
    // ------------------------------------------------------------------

    private void setMode(@NonNull Mode newMode, @NonNull String newCategory) {
        mode = newMode;
        category = newCategory;
        if (newMode != Mode.BROWSE) {
            currentPath = "";
        }
        load();
    }

    private void load() {
        if (service == null) {
            return;
        }
        try {
            List<FileEntry> entries;
            switch (mode) {
                case RECENT:
                    entries = service.getRecent(50);
                    breadcrumb.setText("Recent files");
                    break;
                case FAVORITES:
                    entries = service.getFavorites();
                    breadcrumb.setText("Favorites");
                    break;
                case CATEGORY:
                    entries = service.search("", new SearchQuery.Builder()
                            .category(category)
                            .includeHidden(includeHidden)
                            .sortBy(sortBy)
                            .descending(descending)
                            .limit(200)
                            .build());
                    breadcrumb.setText(categoryLabel(category));
                    break;
                case BROWSE:
                default:
                    entries = service.listFiles(currentPath, includeHidden, sortBy, descending,
                            0, 200);
                    breadcrumb.setText("/" + currentPath);
                    break;
            }
            adapter.submit(entries);
            if (entries.isEmpty()) {
                showEmpty(mode == Mode.BROWSE ? "This folder is empty" : "Nothing here yet");
            } else {
                emptyText.setVisibility(View.GONE);
            }
        } catch (FileManagerException error) {
            showEmpty(error.getMessage());
        }
    }

    private void runSearch(@NonNull String query) {
        if (service == null) {
            return;
        }
        if (query.trim().isEmpty()) {
            load();
            return;
        }
        try {
            List<FileEntry> entries = service.search(currentPath, new SearchQuery.Builder()
                    .name(query)
                    .includeHidden(includeHidden)
                    .sortBy(sortBy)
                    .descending(descending)
                    .limit(200)
                    .build());
            adapter.submit(entries);
            breadcrumb.setText("Search: " + query);
            if (entries.isEmpty()) {
                showEmpty("No matches");
            } else {
                emptyText.setVisibility(View.GONE);
            }
        } catch (FileManagerException error) {
            showEmpty(error.getMessage());
        }
    }

    private static String categoryLabel(@NonNull String category) {
        switch (category) {
            case FileCategory.IMAGE:
                return "Images";
            case FileCategory.VIDEO:
                return "Videos";
            case FileCategory.AUDIO:
                return "Audio";
            case FileCategory.DOCUMENT:
                return "Documents";
            default:
                return "Files";
        }
    }

    private void showEmpty(@NonNull String message) {
        emptyText.setText(message);
        emptyText.setVisibility(View.VISIBLE);
        adapter.submit(new ArrayList<>());
    }

    // ------------------------------------------------------------------
    // navigation
    // ------------------------------------------------------------------

    @Override
    public void onOpen(@NonNull FileEntry entry) {
        if (entry.isDirectory()) {
            currentPath = entry.getRelativePath();
            mode = Mode.BROWSE;
            load();
            return;
        }
        Intent intent = new Intent(this, FileDetailsActivity.class);
        intent.putExtra(FileDetailsActivity.EXTRA_PATH, entry.getRelativePath());
        startActivity(intent);
    }

    @Override
    public void onToggleSelection(@NonNull FileEntry entry) {
        Set<String> selected = adapter.getSelected();
        if (selected.isEmpty()) {
            adapter.setSelectionMode(false);
            return;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(selected.size() + " selected");
        }
        showSelectionActions();
    }

    private void showSelectionActions() {
        final Set<String> selected = adapter.getSelected();
        if (selected.isEmpty()) {
            return;
        }
        String[] actions = {"Copy", "Move", "Delete", "Share", "Favorite", "Cancel"};
        new AlertDialog.Builder(this)
                .setTitle(selected.size() + " selected")
                .setItems(actions, (dialog, which) -> {
                    List<String> paths = new ArrayList<>(selected);
                    switch (which) {
                        case 0:
                            clipboard.clear();
                            clipboard.addAll(paths);
                            clipboardCut = false;
                            toast("Copied " + paths.size() + " item(s)");
                            clearSelection();
                            break;
                        case 1:
                            clipboard.clear();
                            clipboard.addAll(paths);
                            clipboardCut = true;
                            toast("Ready to move " + paths.size() + " item(s)");
                            clearSelection();
                            break;
                        case 2:
                            confirmDelete(paths);
                            break;
                        case 3:
                            share(paths.get(0));
                            clearSelection();
                            break;
                        case 4:
                            for (String path : paths) {
                                try {
                                    service.addFavorite(path);
                                } catch (FileManagerException error) {
                                    toast(error.getMessage());
                                }
                            }
                            clearSelection();
                            break;
                        default:
                            clearSelection();
                            break;
                    }
                })
                .show();
    }

    private void clearSelection() {
        adapter.clearSelection();
        adapter.setSelectionMode(false);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("File manager");
        }
    }

    // ------------------------------------------------------------------
    // mutations
    // ------------------------------------------------------------------

    private void promptCreate(boolean folder) {
        EditText input = new EditText(this);
        input.setHint(folder ? "Folder name" : "File name");
        new AlertDialog.Builder(this)
                .setTitle(folder ? "New folder" : "New file")
                .setView(input)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        return;
                    }
                    String path = currentPath.isEmpty() ? name : currentPath + "/" + name;
                    try {
                        if (folder) {
                            service.createFolder(path);
                        } else {
                            service.createFile(path, "");
                        }
                        load();
                    } catch (FileManagerException error) {
                        toast(error.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void paste() {
        if (clipboard.isEmpty() || service == null) {
            toast("Nothing to paste");
            return;
        }
        try {
            activeOperationId = clipboardCut
                    ? service.move(new ArrayList<>(clipboard), currentPath)
                    : service.copy(new ArrayList<>(clipboard), currentPath);
            clipboard.clear();
            main.post(progressTick);
        } catch (FileManagerException error) {
            toast(error.getMessage());
        }
    }

    private void confirmDelete(@NonNull List<String> paths) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + paths.size() + " item(s)?")
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        activeOperationId = service.delete(paths);
                        main.post(progressTick);
                    } catch (FileManagerException error) {
                        toast(error.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void share(@NonNull String path) {
        try {
            FileEntry entry = service.getFileInfo(path);
            File file = service.resolveForSharing(path);
            if (file == null || !file.exists()) {
                toast("File is not available");
                return;
            }
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(entry.getMimeType());
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share " + entry.getName()));
        } catch (FileManagerException error) {
            toast(error.getMessage());
        }
    }

    private void toast(@Nullable String message) {
        if (!TextUtils.isEmpty(message)) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAF && resultCode == RESULT_OK && data != null
                && data.getData() != null && service != null) {
            Uri treeUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // The provider may only grant read access.
            }
            service.registerSafTree(treeUri);
            toast("SAF folder added");
        }
    }
}
