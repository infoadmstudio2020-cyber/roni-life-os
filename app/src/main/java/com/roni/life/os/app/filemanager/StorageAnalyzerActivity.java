package com.roni.life.os.app.filemanager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.roni.library.contracts.filemanager.FileEntry;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.filemanager.FileManagerException;
import com.roni.library.platform.filemanager.FileManagerService;
import com.roni.life.os.app.R;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only storage analysis: usage, categories, large/old files, empty folders, duplicates. */
public final class StorageAnalyzerActivity extends AppCompatActivity {

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage_analyzer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Storage analyzer");
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        FileManagerService service = PlatformEngine.getInstance().getFileManagerService();
        if (service == null) {
            ((TextView) findViewById(R.id.analyzer_summary)).setText("Engine unavailable");
            return;
        }
        worker.execute(() -> {
            try {
                Map<String, Object> analysis = service.getStorageAnalysis();
                main.post(() -> render(analysis));
            } catch (FileManagerException error) {
                main.post(() -> ((TextView) findViewById(R.id.analyzer_summary))
                        .setText(error.getMessage()));
            }
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @SuppressWarnings("unchecked")
    private void render(Map<String, Object> analysis) {
        long total = number(analysis.get("totalBytes"));
        long free = number(analysis.get("freeBytes"));
        long used = number(analysis.get("usedBytes"));
        long project = number(analysis.get("projectBytes"));
        long app = number(analysis.get("appBytes"));

        ((TextView) findViewById(R.id.analyzer_summary)).setText(
                "Device: " + FileEntryAdapter.formatSize(used) + " used of "
                        + FileEntryAdapter.formatSize(total) + "\nFree: "
                        + FileEntryAdapter.formatSize(free) + "\nProject: "
                        + FileEntryAdapter.formatSize(project) + "\nApp storage: "
                        + FileEntryAdapter.formatSize(app));

        ProgressBar bar = findViewById(R.id.analyzer_bar);
        bar.setProgress(total > 0 ? (int) Math.min(100L, used * 100L / total) : 0);

        StringBuilder categories = new StringBuilder("By category\n");
        Object rawCategories = analysis.get("categories");
        if (rawCategories instanceof Map) {
            for (Map.Entry<String, Object> entry
                    : ((Map<String, Object>) rawCategories).entrySet()) {
                long bytes = entry.getValue() instanceof Number
                        ? ((Number) entry.getValue()).longValue() : 0L;
                categories.append(entry.getKey()).append(": ")
                        .append(FileEntryAdapter.formatSize(bytes)).append('\n');
            }
        }
        ((TextView) findViewById(R.id.analyzer_categories)).setText(categories.toString());

        ((TextView) findViewById(R.id.analyzer_large)).setText(
                describe((List<Map<String, Object>>) analysis.get("largeFiles")));
        ((TextView) findViewById(R.id.analyzer_old)).setText(
                describe((List<Map<String, Object>>) analysis.get("oldFiles")));
        ((TextView) findViewById(R.id.analyzer_empty)).setText(
                describe((List<Map<String, Object>>) analysis.get("emptyFolders")));

        StringBuilder duplicates = new StringBuilder();
        Object groups = analysis.get("duplicateGroups");
        if (groups instanceof List) {
            int index = 1;
            for (Object group : (List<Object>) groups) {
                duplicates.append("Group ").append(index++).append('\n');
                if (group instanceof List) {
                    for (Object item : (List<Object>) group) {
                        if (item instanceof Map) {
                            duplicates.append("  ")
                                    .append(((Map<String, Object>) item).get("relativePath"))
                                    .append('\n');
                        }
                    }
                }
            }
        }
        if (duplicates.length() == 0) {
            duplicates.append("No duplicates found");
        }
        ((TextView) findViewById(R.id.analyzer_duplicates)).setText(duplicates.toString());
    }

    private static String describe(@Nullable List<Map<String, Object>> entries) {
        if (entries == null || entries.isEmpty()) {
            return "None";
        }
        StringBuilder builder = new StringBuilder();
        for (Map<String, Object> entry : entries) {
            builder.append(String.valueOf(entry.get("relativePath"))).append(" · ")
                    .append(FileEntryAdapter.formatSize(number(entry.get("sizeBytes"))))
                    .append('\n');
        }
        return builder.toString();
    }

    private static long number(@Nullable Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
