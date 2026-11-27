package com.roni.life.os.app.filemanager;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.roni.library.contracts.filemanager.FileManagerErrorCodes;
import com.roni.library.contracts.filemanager.SearchQuery;
import com.roni.library.contracts.filemanager.StorageInfo;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.filemanager.FileManagerException;
import com.roni.library.platform.filemanager.FileManagerService;
import com.roni.library.storage.StorageEngine;
import com.roni.library.storage.preferences.EncryptedPreferences;
import com.roni.life.os.app.R;

import java.util.List;
import java.util.Map;

/** File manager preferences and SAF root management. */
public final class FileManagerSettingsActivity extends AppCompatActivity {

    private static final int REQUEST_SAF = 4302;
    private static final String PREFS = "filemanager_ui";

    private EncryptedPreferences preferences;
    private FileManagerService service;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        try {
            preferences = StorageEngine.getPreferences(PREFS);
        } catch (Throwable ignored) {
            preferences = null;
        }
        service = PlatformEngine.getInstance().getFileManagerService();

        Switch hidden = findViewById(R.id.settings_hidden);
        Switch grid = findViewById(R.id.settings_grid);
        Spinner sort = findViewById(R.id.settings_sort);
        TextView roots = findViewById(R.id.settings_saf_roots);
        TextView storage = findViewById(R.id.settings_storage);

        hidden.setChecked(preferences != null && preferences.getBoolean("hidden", false));
        grid.setChecked(preferences != null && preferences.getBoolean("grid", false));

        String[] sortOptions = {"Name", "Size", "Date", "Type"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, sortOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sort.setAdapter(adapter);
        String saved = preferences != null
                ? preferences.getString("sort", SearchQuery.SORT_NAME) : SearchQuery.SORT_NAME;
        sort.setSelection(Math.max(0, java.util.Arrays.asList(
                SearchQuery.SORT_NAME, SearchQuery.SORT_SIZE,
                SearchQuery.SORT_DATE, SearchQuery.SORT_TYPE).indexOf(saved)));

        hidden.setOnCheckedChangeListener((button, checked) -> put("hidden", checked));
        grid.setOnCheckedChangeListener((button, checked) -> put("grid", checked));
        sort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       android.view.View view, int position, long id) {
                String value = new String[]{SearchQuery.SORT_NAME, SearchQuery.SORT_SIZE,
                        SearchQuery.SORT_DATE, SearchQuery.SORT_TYPE}[position];
                if (preferences != null) {
                    preferences.putString("sort", value);
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        findViewById(R.id.settings_add_saf).setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try {
                startActivityForResult(intent, REQUEST_SAF);
            } catch (android.content.ActivityNotFoundException error) {
                Toast.makeText(this, "No document provider available",
                        Toast.LENGTH_SHORT).show();
            }
        });

        renderRoots(roots);
        renderStorage(storage);
    }

    private void renderRoots(@Nullable TextView view) {
        if (view == null || service == null) {
            return;
        }
        List<Map<String, Object>> safRoots = service.getSafRoots();
        if (safRoots.isEmpty()) {
            view.setText("No SAF folders added");
            return;
        }
        StringBuilder builder = new StringBuilder("SAF folders\n");
        for (Map<String, Object> root : safRoots) {
            builder.append("• ").append(String.valueOf(root.get("name"))).append('\n');
        }
        view.setText(builder.toString());
    }

    private void renderStorage(@Nullable TextView view) {
        if (view == null || service == null) {
            return;
        }
        try {
            StorageInfo info = service.getStorageInfo();
            view.setText("Free space: " + FileEntryAdapter.formatSize(info.getFreeBytes())
                    + "\nProject usage: " + FileEntryAdapter.formatSize(info.getProjectBytes()));
        } catch (FileManagerException error) {
            view.setText(error.getCode().equals(FileManagerErrorCodes.NO_ACTIVE_PROJECT)
                    ? "Open a project to see storage usage" : error.getMessage());
        }
    }

    private void put(String key, boolean value) {
        if (preferences != null) {
            preferences.putBoolean(key, value);
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
                // Read-only grants are still usable for import.
            }
            service.registerSafTree(treeUri);
            renderRoots(findViewById(R.id.settings_saf_roots));
            Toast.makeText(this, "SAF folder added", Toast.LENGTH_SHORT).show();
        }
    }
}
