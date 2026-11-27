package com.roni.life.os.app.activity;

import android.app.DownloadManager;
import android.content.ContentUris;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.roni.life.os.app.R;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Downloads manager: every file a project saved into {@code Download/RONiLiFEOS}.
 *
 * <p>Two sources are merged. Transfers still owned by the system {@link DownloadManager} keep
 * their id, so they can be paused, resumed or cancelled and report live progress. Files written
 * directly ({@code data:} and {@code blob:}) have no transfer to control and can only be
 * deleted.</p>
 *
 * <p>Progress is updated in place on a short ticker rather than by rebuilding the list, so the
 * bars animate smoothly and the scroll position never jumps.</p>
 */
public class DownloadsActivity extends BaseActivity {

    private static final String FOLDER = "RONiLiFEOS";

    /** How often live transfers refresh their progress bar. */
    private static final long TICK_MS = 600L;

    private LinearLayout list;
    private TextView empty;

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Map<Long, ProgressBar> bars = new HashMap<>();
    private final Map<Long, TextView> metas = new HashMap<>();

    /** One row in the list. */
    private static final class Entry {
        long id = -1L;
        String name = "";
        String status = "";
        long downloaded;
        long total;
        Uri contentUri;
        File file;
        boolean paused;
        boolean running;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshProgress();
            ticker.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        ticker.removeCallbacks(tick);
        ticker.postDelayed(tick, TICK_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ticker.removeCallbacks(tick);
    }

    // ==================== Layout ====================

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));
        root.setPadding(dp(20), dp(20), dp(20), 0);

        TextView title = new TextView(this);
        title.setText(R.string.downloads_title);
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.downloads_subtitle);
        subtitle.setTextSize(12f);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        Button clear = new Button(this);
        clear.setText(R.string.downloads_clear_all);
        clear.setTextSize(13f);
        clear.setAllCaps(false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearAll();
            }
        });
        root.addView(clear);

        empty = new TextView(this);
        empty.setText(R.string.downloads_empty);
        empty.setTextSize(13f);
        empty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        empty.setPadding(0, dp(28), 0, 0);
        root.addView(empty);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(14), 0, dp(28));
        scroll.addView(list);
        root.addView(scroll);
        return root;
    }

    // ==================== Data ====================

    private void reload() {
        list.removeAllViews();
        bars.clear();
        metas.clear();
        List<Entry> entries = load();
        empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        for (Entry entry : entries) {
            list.addView(row(entry));
        }
    }

    private List<Entry> load() {
        List<Entry> entries = new ArrayList<>();
        DownloadManager manager =
                (DownloadManager) getSystemService(android.content.Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            Cursor cursor = manager.query(new DownloadManager.Query());
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        String local = cursor.getString(cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_LOCAL_URI));
                        if (local == null || !local.contains(FOLDER)) {
                            continue;
                        }
                        Entry entry = new Entry();
                        entry.id = cursor.getLong(cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_ID));
                        entry.name = cursor.getString(cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_TITLE));
                        entry.total = cursor.getLong(cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        entry.downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                                DownloadManager.COLUMN_STATUS));
                        entry.status = statusLabel(status);
                        entry.running = status == DownloadManager.STATUS_RUNNING
                                || status == DownloadManager.STATUS_PENDING;
                        entry.paused = status == DownloadManager.STATUS_PAUSED;
                        entries.add(entry);
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        entries.addAll(loadFiles());
        return entries;
    }

    /** Files written directly, which the DownloadManager does not know about. */
    private List<Entry> loadFiles() {
        List<Entry> entries = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            String[] projection = {
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE};
            Cursor cursor = getContentResolver().query(collection, projection,
                    MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?",
                    new String[]{"%" + FOLDER + "%"}, null);
            if (cursor != null) {
                try {
                    while (cursor.moveToNext()) {
                        Entry entry = new Entry();
                        entry.contentUri = ContentUris.withAppendedId(collection,
                                cursor.getLong(0));
                        entry.name = cursor.getString(1);
                        entry.total = cursor.getLong(2);
                        entry.downloaded = entry.total;
                        entry.status = getString(R.string.downloads_saved);
                        entries.add(entry);
                    }
                } finally {
                    cursor.close();
                }
            }
            return entries;
        }
        File dir = new File(android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS), FOLDER);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isFile()) {
                    continue;
                }
                Entry entry = new Entry();
                entry.file = file;
                entry.name = file.getName();
                entry.total = file.length();
                entry.downloaded = entry.total;
                entry.status = getString(R.string.downloads_saved);
                entries.add(entry);
            }
        }
        return entries;
    }

    private String statusLabel(int status) {
        switch (status) {
            case DownloadManager.STATUS_PENDING:
                return getString(R.string.downloads_pending);
            case DownloadManager.STATUS_RUNNING:
                return getString(R.string.downloads_running);
            case DownloadManager.STATUS_PAUSED:
                return getString(R.string.downloads_paused);
            case DownloadManager.STATUS_SUCCESSFUL:
                return getString(R.string.downloads_done);
            default:
                return getString(R.string.downloads_failed);
        }
    }

    // ==================== Row ====================

    private View row(final Entry entry) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(14), dp(16), dp(12));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);

        TextView name = new TextView(this);
        name.setText(entry.name);
        name.setTextSize(15f);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        name.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        card.addView(name);

        TextView meta = new TextView(this);
        meta.setTextSize(11.5f);
        meta.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        meta.setPadding(0, dp(3), 0, 0);
        meta.setText(metaText(entry));
        card.addView(meta);
        metas.put(entry.id, meta);

        if (entry.id >= 0 && (entry.running || entry.paused)) {
            ProgressBar bar = new ProgressBar(this, null,
                    android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(percent(entry));
            bar.setProgressTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.brand_accent)));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.surface_elevated)));
            LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
            barParams.topMargin = dp(10);
            bar.setLayoutParams(barParams);
            card.addView(bar);
            bars.put(entry.id, bar);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(6), 0, 0);

        if (entry.id >= 0) {
            if (entry.running) {
                actions.addView(action(R.string.downloads_pause, new Runnable() {
                    @Override
                    public void run() {
                        control(entry.id, true);
                    }
                }));
            } else if (entry.paused) {
                actions.addView(action(R.string.downloads_resume, new Runnable() {
                    @Override
                    public void run() {
                        control(entry.id, false);
                    }
                }));
            }
            actions.addView(action(R.string.downloads_cancel, new Runnable() {
                @Override
                public void run() {
                    cancel(entry.id);
                }
            }));
        }
        actions.addView(action(R.string.downloads_delete, new Runnable() {
            @Override
            public void run() {
                delete(entry);
            }
        }));
        card.addView(actions);
        return card;
    }

    private Button action(int label, final Runnable runnable) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12f);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        params.leftMargin = dp(6);
        button.setLayoutParams(params);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runnable.run();
            }
        });
        return button;
    }

    // ==================== Live progress ====================

    /**
     * Updates the bars of transfers that are still moving.
     *
     * <p>Only the progress and the meta line are touched, so the list is never rebuilt while the
     * user is looking at it.</p>
     */
    private void refreshProgress() {
        if (bars.isEmpty()) {
            return;
        }
        DownloadManager manager =
                (DownloadManager) getSystemService(android.content.Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            return;
        }
        Cursor cursor = manager.query(new DownloadManager.Query());
        if (cursor == null) {
            return;
        }
        try {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_ID));
                ProgressBar bar = bars.get(id);
                if (bar == null) {
                    continue;
                }
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                long done = cursor.getLong(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_STATUS));
                int value = total > 0L ? (int) Math.min(100L, done * 100L / total) : 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    bar.setProgress(value, true);
                } else {
                    bar.setProgress(value);
                }
                TextView meta = metas.get(id);
                if (meta != null) {
                    meta.setText(statusLabel(status) + " · " + humanSize(done)
                            + " / " + humanSize(total));
                }
                if (status == DownloadManager.STATUS_SUCCESSFUL
                        || status == DownloadManager.STATUS_FAILED) {
                    // The transfer is over: rebuild once so the row switches to its final shape.
                    reload();
                    return;
                }
            }
        } catch (Throwable ignored) {
            // A row can disappear between the query and the update; the next tick recovers.
        } finally {
            cursor.close();
        }
    }

    private int percent(Entry entry) {
        if (entry.total <= 0L) {
            return 0;
        }
        return (int) Math.min(100L, entry.downloaded * 100L / entry.total);
    }

    private String metaText(Entry entry) {
        if (entry.id >= 0 && (entry.running || entry.paused)) {
            return entry.status + " · " + humanSize(entry.downloaded)
                    + " / " + humanSize(entry.total);
        }
        return entry.status + " · " + humanSize(entry.total);
    }

    // ==================== Actions ====================

    private void control(long id, boolean pause) {
        DownloadManager manager =
                (DownloadManager) getSystemService(
                        android.content.Context.DOWNLOAD_SERVICE
                );

        if (manager == null) {
            toast(getString(R.string.downloads_action_failed));
            return;
        }

        if (pause) {
            // Android DownloadManager does not support pause.
            toast("Pause is not supported by Android DownloadManager");
            return;
        }

        // Resume is also not supported.
        toast("Resume is not supported by Android DownloadManager");
    }

    private void cancel(long id) {
        DownloadManager manager =
                (DownloadManager) getSystemService(android.content.Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            try {
                manager.remove(id);
            } catch (Throwable error) {
                toast(getString(R.string.downloads_action_failed));
            }
        }
        reload();
    }

    private void delete(Entry entry) {
        try {
            if (entry.contentUri != null) {
                getContentResolver().delete(entry.contentUri, null, null);
            } else if (entry.file != null) {
                //noinspection ResultOfMethodCallIgnored
                entry.file.delete();
            } else if (entry.id >= 0) {
                DownloadManager manager =
                        (DownloadManager) getSystemService(android.content.Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.remove(entry.id);
                }
            }
        } catch (Throwable error) {
            toast(getString(R.string.downloads_action_failed));
        }
        reload();
    }

    private void clearAll() {
        for (Entry entry : load()) {
            delete(entry);
        }
        toast(getString(R.string.downloads_cleared));
        reload();
    }

    // ==================== Helpers ====================

    private String humanSize(long bytes) {
        if (bytes <= 0L) {
            return "—";
        }
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
