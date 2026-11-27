package com.roni.library.devtools.inspector.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.roni.library.devtools.R;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.contracts.logger.LogSource;
import com.roni.library.devtools.inspector.InspectorSettings;
import com.roni.library.devtools.inspector.LogEntryFormatter;
import com.roni.library.devtools.inspector.LogExporter;
import com.roni.library.devtools.inspector.LogFilter;
import com.roni.library.devtools.inspector.RuntimeInspector;
import com.roni.library.devtools.inspector.RuntimeLogCollector;
import com.roni.library.devtools.inspector.RuntimeLogRepository;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;

import com.roni.library.devtools.inspector.ui.InspectorSheetState.State;
import java.util.List;

/**
 * The Runtime Inspector bottom sheet.
 *
 * <p>Lives as an overlay in the Activity's content root so it can be opened over a running
 * project without touching the WebView, its navigation history or its session. In the collapsed
 * state the sheet is a thin strip at the bottom and the rest of the overlay is
 * {@code not-touchable}, so page gestures reach the WebView exactly as before.</p>
 */
public final class RuntimeInspectorSheet extends FrameLayout {

    /** The three supported heights. */
    /** Request code used for the Storage Access Framework export picker. */
    public static final int REQUEST_EXPORT = 0x1A5B;

    private static final long REFRESH_INTERVAL_MS = 250L;

    private static final String[] TAB_LABELS = {
            "ALL LOGS", "NATIVE", "WEB", "BRIDGE", "NETWORK", "PROJECT", "PERFORMANCE"
    };

    private static final LogSource[] TAB_SOURCES = {
            null, LogSource.NATIVE, LogSource.WEB, LogSource.BRIDGE,
            LogSource.NETWORK, LogSource.PROJECT, LogSource.PERFORMANCE
    };

    private final Activity activity;
    private final RuntimeLogRepository repository;
    private final InspectorSettings settings;
    private final LogFilter filter = new LogFilter();
    /** Selected level chips. Empty means "all levels". */
    private final EnumSet<LogLevel> activeLevels = EnumSet.noneOf(LogLevel.class);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final LogEntryAdapter adapter;
    private final List<TextView> tabViews = new ArrayList<>();

    private RecyclerView list;
    private View emptyView;
    private TextView emptyHint;
    private TextView stateLabel;
    private TextView pauseLabel;
    private TextView autoScrollLabel;
    private TextView errorChip;
    private TextView warnChip;
    private TextView infoChip;
    private TextView allChip;
    private TextView statusLabel;
    private TextView selectionInfo;
    private EditText searchInput;

    private State state = State.COLLAPSED;
    private int screenHeight;
    private int collapsedHeight;
    private int halfHeight;
    private int expandedHeight;

    private boolean paused;
    private boolean autoScroll = true;
    private boolean dirty;
    private boolean refreshScheduled;
    private int activeTab;

    private LogExporter.Format pendingExportFormat = LogExporter.Format.TXT;
    private List<LogEntry> pendingExportEntries = new ArrayList<>();

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshScheduled = false;
            if (dirty) {
                dirty = false;
                refresh();
            }
        }
    };

    private final RuntimeLogRepository.Listener repositoryListener =
            new RuntimeLogRepository.Listener() {
                @Override
                public void onLogAdded(LogEntry entry) {
                    if (paused) {
                        return;
                    }
                    dirty = true;
                    scheduleRefresh();
                }

                @Override
                public void onLogsCleared() {
                    dirty = true;
                    scheduleRefresh();
                }
            };

    private RuntimeInspectorSheet(Activity activity) {
        super(activity);
        this.activity = activity;
        RuntimeInspector controller = RuntimeInspector.get();
        this.repository = controller.getRepository();
        this.settings = controller.getSettings();
        this.autoScroll = settings == null || settings.isAutoScroll();

        LayoutInflater.from(activity).inflate(R.layout.view_runtime_inspector, this, true);
        adapter = new LogEntryAdapter(new LogEntryAdapter.Callback() {
            @Override
            public void onEntryClicked(LogEntry entry) {
                if (adapter.isSelectionMode()) {
                    adapter.toggleSelection(entry);
                    updateSelectionInfo();
                } else {
                    showEntryDetails(entry);
                }
            }

            @Override
            public void onEntryLongClicked(LogEntry entry) {
                copyToClipboard(LogEntryFormatter.toText(entry), "Log entry copied");
            }
        });

        computeHeights();
        bindViews();
        buildTabs();
        applyState(State.COLLAPSED, false);
    }

    /**
     * Installs the sheet into {@code activity}'s content root and returns it.
     *
     * <p>Idempotent per Activity: a second call returns the existing instance instead of
     * stacking a second overlay (which would double every listener).</p>
     */
    public static RuntimeInspectorSheet install(Activity activity) {
        if (activity == null) {
            return null;
        }
        // The sheet is usable on its own: make sure settings and the logger sink exist even if
        // the host never called init() explicitly.
        RuntimeInspector.get().init(activity);
        ViewGroup root = activity.findViewById(android.R.id.content);
        if (root == null) {
            return null;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof RuntimeInspectorSheet) {
                return (RuntimeInspectorSheet) child;
            }
        }
        RuntimeInspectorSheet sheet = new RuntimeInspectorSheet(activity);
        root.addView(sheet, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // addView replaces the layout params, so the collapsed height must be re-applied
        // afterwards — otherwise the overlay would cover the whole page and swallow touches.
        sheet.applyState(State.COLLAPSED, false);
        // The parent has no measured height yet, so the first pass anchors against the raw
        // display height. Re-apply once laid out, otherwise the sheet can sit below the
        // content area and be clipped out of view.
        sheet.post(new Runnable() {
            @Override
            public void run() {
                sheet.applyState(sheet.state, false);
            }
        });
        return sheet;
    }

    // ==================== Setup ====================

    private void computeHeights() {
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        collapsedHeight = dp(InspectorSheetState.COLLAPSED_HEIGHT_DP);
        halfHeight = InspectorSheetState.heightFor(State.HALF, screenHeight, collapsedHeight);
        expandedHeight = InspectorSheetState.heightFor(State.EXPANDED, screenHeight, collapsedHeight);
    }

    private void bindViews() {
        list = findViewById(R.id.inspector_list);
        emptyView = findViewById(R.id.inspector_empty);
        emptyHint = findViewById(R.id.inspector_empty_hint);
        stateLabel = findViewById(R.id.inspector_state);
        pauseLabel = findViewById(R.id.inspector_pause);
        autoScrollLabel = findViewById(R.id.inspector_autoscroll);
        statusLabel = findViewById(R.id.inspector_status);
        selectionInfo = findViewById(R.id.inspector_selection_info);
        searchInput = findViewById(R.id.inspector_search);

        LinearLayoutManager manager = new LinearLayoutManager(activity);
        manager.setStackFromEnd(true);
        list.setLayoutManager(manager);
        list.setAdapter(adapter);
        list.setItemAnimator(null);

        findViewById(R.id.inspector_handle).setOnTouchListener(new DragListener());

        findViewById(R.id.inspector_pause).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                paused = !paused;
                pauseLabel.setText(paused ? "RESUME" : "PAUSE");
                pauseLabel.setTextColor(paused
                        ? Color.parseColor("#FF4500") : Color.parseColor("#E0E0E0"));
                if (!paused) {
                    dirty = true;
                    scheduleRefresh();
                }
            }
        });

        findViewById(R.id.inspector_autoscroll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autoScroll = !autoScroll;
                autoScrollLabel.setTextColor(autoScroll
                        ? Color.parseColor("#FF4500") : Color.parseColor("#888888"));
                if (settings != null) {
                    settings.setAutoScroll(autoScroll);
                }
                if (autoScroll) {
                    scrollToBottom();
                }
            }
        });

        findViewById(R.id.inspector_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                repository.clear();
                adapter.clearSelection();
                updateSelectionInfo();
            }
        });

        findViewById(R.id.inspector_export).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showExportDialog();
            }
        });

        findViewById(R.id.inspector_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettingsDialog();
            }
        });

        errorChip = findViewById(R.id.inspector_level_error);
        warnChip = findViewById(R.id.inspector_level_warn);
        infoChip = findViewById(R.id.inspector_level_info);
        allChip = findViewById(R.id.inspector_level_all);

        errorChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLevel(LogLevel.ERROR);
            }
        });
        warnChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLevel(LogLevel.WARN);
            }
        });
        infoChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLevel(LogLevel.INFO);
            }
        });
        allChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLevel(null);
            }
        });
        updateLevelChips();

        findViewById(R.id.inspector_select_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adapter.setSelectionMode(true);
                adapter.selectAll();
                updateSelectionInfo();
            }
        });

        findViewById(R.id.inspector_copy).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyCurrentSelection();
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter.setText(s != null ? s.toString() : "");
                refresh();
            }
        });

        autoScrollLabel.setTextColor(autoScroll
                ? Color.parseColor("#FF4500") : Color.parseColor("#888888"));
    }

    private void buildTabs() {
        LinearLayout container = findViewById(R.id.inspector_tabs);
        container.removeAllViews();
        tabViews.clear();
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int index = i;
            TextView tab = new TextView(activity);
            tab.setText(TAB_LABELS[i]);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            tab.setTypeface(android.graphics.Typeface.MONOSPACE);
            tab.setPadding(dp(10), dp(8), dp(10), dp(8));
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectTab(index);
                }
            });
            container.addView(tab);
            tabViews.add(tab);
        }
        selectTab(0);
    }

    private void selectTab(int index) {
        activeTab = index;
        filter.setSource(TAB_SOURCES[index]);
        for (int i = 0; i < tabViews.size(); i++) {
            TextView tab = tabViews.get(i);
            boolean active = i == index;
            tab.setTextColor(active
                    ? Color.parseColor("#FF4500") : Color.parseColor("#888888"));
            tab.setBackgroundColor(active
                    ? Color.parseColor("#0A0A0A") : Color.parseColor("#020202"));
        }
        refresh();
    }

    // ==================== State ====================

    /** Expands or collapses the sheet. */
    public void setState(State target) {
        applyState(target, true);
    }

    public State getState() {
        return state;
    }

    /** Toggles between collapsed and half-expanded — the floating-button behaviour. */
    public void toggle() {
        applyState(state == State.COLLAPSED ? State.HALF : State.COLLAPSED, true);
    }

    private void applyState(State target, boolean animate) {
        state = target;
        applyHeight(InspectorSheetState.heightFor(target, screenHeight, collapsedHeight));
        applyChromeVisibility(InspectorSheetState.showsChrome(target));
        stateLabel.setText(target.name());
        // Only the sheet itself may consume touches; the area above it must stay transparent
        // to the WebView underneath.
        boolean interactive = InspectorSheetState.consumesTouches(target);
        setClickable(interactive);
        setFocusable(interactive);
        if (target == State.COLLAPSED) {
            searchInput.clearFocus();
        }
        if (animate) {
            animate().alpha(1f).setDuration(120L).start();
        }
        if (target != State.COLLAPSED) {
            dirty = true;
            scheduleRefresh();
        }
    }

    /**
     * Collapsed shows the drag handle and nothing else.
     *
     * <p>The title bar, tabs, level chips, search, status, list and footer are removed from
     * layout rather than merely hidden behind the panel edge, so they cannot clip, overlap or
     * intercept a gesture meant for the page underneath.</p>
     *
     * <p>The sheet is a FrameLayout whose single child is the {@code inspector_root}
     * LinearLayout, so the traversal must start at that child — walking the sheet's own
     * children would hide the whole panel, handle included.</p>
     */
    private void applyChromeVisibility(boolean visible) {
        ViewGroup root = findViewById(R.id.inspector_root);
        if (root == null) {
            // Fail visible: a missing root must never leave the inspector invisible.
            return;
        }
        int visibility = visible ? View.VISIBLE : View.GONE;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child.getId() == R.id.inspector_handle) {
                continue;
            }
            child.setVisibility(visibility);
        }
        // Collapsed, the strip below the handle must not paint a black band over the page.
        root.setBackgroundColor(visible
                ? Color.parseColor("#020202") : Color.TRANSPARENT);
        setBackgroundColor(Color.TRANSPARENT);
    }

    private void applyHeight(int height) {
        // The overlay is bottom-anchored: the sheet grows upward and never covers the top of
        // the page unless fully expanded. Prefer the real parent height so insets are honoured.
        int available = getResources().getDisplayMetrics().heightPixels;
        if (getParent() instanceof View) {
            int parentHeight = ((View) getParent()).getHeight();
            if (parentHeight > 0) {
                available = parentHeight;
            }
        }
        // Never taller than the container, or the bottom of the panel is clipped away.
        height = Math.min(height, available);

        ViewGroup.LayoutParams params = getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, height);
        }
        params.height = height;
        setLayoutParams(params);
        setY(Math.max(0, available - height));
    }

    private void snapToNearest() {
        int current = getLayoutParams() != null ? getLayoutParams().height : collapsedHeight;
        applyState(InspectorSheetState.nearest(
                current, collapsedHeight, halfHeight, expandedHeight), true);
    }

    /** Drag handle: resizes the sheet, then snaps to the nearest state on release. */
    private final class DragListener implements View.OnTouchListener {
        private float startY;
        private int startHeight;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getRawY();
                    startHeight = getLayoutParams() != null
                            ? getLayoutParams().height : collapsedHeight;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int delta = (int) (startY - event.getRawY());
                    int target = Math.max(collapsedHeight,
                            Math.min(expandedHeight, startHeight + delta));
                    applyHeight(target);
                    // Reveal the panel as soon as the drag clears the collapsed height, so the
                    // user sees content while dragging instead of an empty black strip.
                    applyChromeVisibility(target > collapsedHeight);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    snapToNearest();
                    return true;
                default:
                    return false;
            }
        }
    }

    // ==================== Rendering ====================

    private void scheduleRefresh() {
        if (refreshScheduled) {
            return;
        }
        refreshScheduled = true;
        mainHandler.postDelayed(refreshTask, REFRESH_INTERVAL_MS);
    }

    private void refresh() {
        if (state == State.COLLAPSED) {
            return;
        }
        List<LogEntry> all = repository.snapshot();
        List<LogEntry> filtered = filter.apply(all);
        adapter.submit(filtered);
        updateStatus(all.size(), filtered.size());
        updateSelectionInfo();

        boolean empty = filtered.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyHint.setText(filter.isActive()
                ? "No entries match the current filters."
                : "Events appear here as the project runs.");

        if (autoScroll && !paused) {
            scrollToBottom();
        }
    }

    private void scrollToBottom() {
        int count = adapter.getItemCountInternal();
        if (count > 0) {
            list.scrollToPosition(count - 1);
        }
    }

    private void updateStatus(int total, int shown) {
        StringBuilder builder = new StringBuilder();
        builder.append(shown).append('/').append(total).append(" entries");
        long dropped = repository.getDroppedCount();
        if (dropped > 0) {
            builder.append("  dropped=").append(dropped);
        }
        builder.append("  buffer=").append(repository.size())
                .append('/').append(repository.getCapacity());
        if (paused) {
            builder.append("  PAUSED");
        }
        statusLabel.setText(builder.toString());
    }

    private void updateSelectionInfo() {
        int selected = adapter.getSelectedCount();
        if (selected == 0) {
            selectionInfo.setText(adapter.isSelectionMode() ? "selection mode" : "");
        } else {
            selectionInfo.setText(selected + " selected");
        }
    }

    /**
     * Toggles one level chip. {@code null} clears the selection, which means "all levels".
     *
     * <p>ERROR also pulls in FATAL: a fatal entry is an error the user must not lose by
     * filtering, and the chip row has no separate FATAL control.</p>
     */
    private void toggleLevel(LogLevel severity) {
        if (severity == null) {
            activeLevels.clear();
        } else if (!activeLevels.remove(severity)) {
            activeLevels.add(severity);
        }
        EnumSet<LogLevel> effective = EnumSet.noneOf(LogLevel.class);
        effective.addAll(activeLevels);
        if (effective.contains(LogLevel.ERROR)) {
            effective.add(LogLevel.FATAL);
        }
        filter.setLevels(effective);
        updateLevelChips();
        refresh();
    }

    private void updateLevelChips() {
        styleLevelChip(errorChip, activeLevels.contains(LogLevel.ERROR), "#FF6A33");
        styleLevelChip(warnChip, activeLevels.contains(LogLevel.WARN), "#FFC233");
        styleLevelChip(infoChip, activeLevels.contains(LogLevel.INFO), "#E0E0E0");
        styleLevelChip(allChip, activeLevels.isEmpty(), "#FF4500");
    }

    private void styleLevelChip(TextView chip, boolean active, String activeColor) {
        if (chip == null) {
            return;
        }
        chip.setTextColor(active
                ? Color.parseColor(activeColor) : Color.parseColor("#888888"));
        chip.setBackgroundColor(active
                ? Color.parseColor("#1A1A1A") : Color.TRANSPARENT);
    }

    // ==================== Copy ====================

    private void copyCurrentSelection() {
        List<LogEntry> selected = adapter.getSelectedEntries();
        if (selected.isEmpty()) {
            List<LogEntry> visible = adapter.getItems();
            if (visible.isEmpty()) {
                toast("Nothing to copy");
                return;
            }
            copyToClipboard(join(visible), "Copied " + visible.size() + " visible entries");
            return;
        }
        copyToClipboard(join(selected), "Copied " + selected.size() + " entries");
    }

    private String join(List<LogEntry> entries) {
        StringBuilder builder = new StringBuilder();
        for (LogEntry entry : entries) {
            builder.append(LogEntryFormatter.toText(entry)).append('\n');
        }
        return builder.toString();
    }

    private void copyToClipboard(String text, String confirmation) {
        try {
            ClipboardManager manager =
                    (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null) {
                toast("Clipboard unavailable");
                return;
            }
            manager.setPrimaryClip(ClipData.newPlainText("RONiLiFEOS log", text));
            toast(confirmation);
        } catch (Throwable error) {
            toast("Copy failed");
        }
    }

    private void showEntryDetails(final LogEntry entry) {
        if (entry == null) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(entry.getLevel().name() + " · " + entry.getSource().getLabel())
                .setMessage(LogEntryFormatter.toText(entry))
                .setPositiveButton("Copy entry", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        copyToClipboard(LogEntryFormatter.toText(entry), "Log entry copied");
                    }
                })
                .setNeutralButton("Copy message", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        copyToClipboard(entry.getMessage(), "Message copied");
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ==================== Export ====================

    private void showExportDialog() {
        final String[] labels = {
                "Copy to clipboard",
                "Share…",
                "Save as TXT",
                "Save as JSON",
                "Save as CSV"
        };
        new AlertDialog.Builder(activity)
                .setTitle("Export logs")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                copyExportPayload();
                                break;
                            case 1:
                                shareExportPayload();
                                break;
                            case 2:
                                pendingExportFormat = LogExporter.Format.TXT;
                                startExport();
                                break;
                            case 3:
                                pendingExportFormat = LogExporter.Format.JSON;
                                startExport();
                                break;
                            default:
                                pendingExportFormat = LogExporter.Format.CSV;
                                startExport();
                                break;
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Selection when there is one, otherwise everything currently visible. */
    private List<LogEntry> exportEntries() {
        List<LogEntry> selected = adapter.getSelectedEntries();
        return selected.isEmpty() ? filter.apply(repository.snapshot()) : selected;
    }

    private void copyExportPayload() {
        List<LogEntry> entries = exportEntries();
        if (entries.isEmpty()) {
            toast("No logs to copy");
            return;
        }
        copyToClipboard(join(entries), "Copied " + entries.size() + " entries");
    }

    private void shareExportPayload() {
        List<LogEntry> entries = exportEntries();
        if (entries.isEmpty()) {
            toast("No logs to share");
            return;
        }
        String payload = LogExporter.export(entries, LogExporter.Format.TXT, buildMetadata());
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "RONiLiFEOS runtime logs");
            intent.putExtra(Intent.EXTRA_TEXT, payload);
            activity.startActivity(Intent.createChooser(intent, "Share logs"));
        } catch (Throwable error) {
            toast("No app available to share");
        }
    }

    private void startExport() {
        pendingExportEntries = exportEntries();
        if (pendingExportEntries.isEmpty()) {
            toast("No logs to export");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(pendingExportFormat.getMimeType());
            intent.putExtra(Intent.EXTRA_TITLE,
                    LogExporter.suggestedFileName(pendingExportFormat,
                            System.currentTimeMillis()));
            activity.startActivityForResult(intent, REQUEST_EXPORT);
        } catch (Throwable error) {
            toast("No file picker available");
        }
    }

    /**
     * Must be called from the host Activity's {@code onActivityResult}.
     *
     * @return true when the result belonged to the inspector
     */
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_EXPORT) {
            return false;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            toast("Export cancelled");
            return true;
        }
        Uri target = data.getData();
        try {
            String payload = LogExporter.export(pendingExportEntries, pendingExportFormat,
                    buildMetadata());
            OutputStream stream = activity.getContentResolver().openOutputStream(target);
            if (stream == null) {
                toast("Cannot open destination");
                return true;
            }
            stream.write(payload.getBytes("UTF-8"));
            stream.flush();
            stream.close();
            toast("Exported " + pendingExportEntries.size() + " entries");
        } catch (Throwable error) {
            toast("Export failed: " + error.getMessage());
        }
        return true;
    }

    private LogExporter.Metadata buildMetadata() {
        String projectId = RuntimeLogCollector.get().getProjectId();
        return new LogExporter.Metadata()
                .appVersion(appVersion())
                .sdkVersion("SDK " + Build.VERSION.SDK_INT)
                .androidVersion(Build.VERSION.RELEASE)
                .deviceModel(Build.MANUFACTURER + " " + Build.MODEL)
                .projectId(projectId);
    }

    private String appVersion() {
        try {
            return activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    // ==================== Settings ====================

    private void showSettingsDialog() {
        if (settings == null) {
            toast("Settings unavailable");
            return;
        }
        final String[] labels = {
                "Inspector enabled",
                "Auto-scroll",
                "Web console capture",
                "Bridge diagnostics",
                "Network diagnostics",
                "Performance diagnostics",
                "Redact sensitive data",
                "Clear logs on new session",
                "Floating inspector button"
        };
        final boolean[] checked = {
                settings.isEnabled(),
                settings.isAutoScroll(),
                settings.isWebConsoleEnabled(),
                settings.isBridgeEnabled(),
                settings.isNetworkEnabled(),
                settings.isPerformanceEnabled(),
                settings.isRedactionEnabled(),
                settings.isClearOnNewSession(),
                settings.isFloatingButtonEnabled()
        };
        new AlertDialog.Builder(activity)
                .setTitle("Inspector settings")
                .setMultiChoiceItems(labels, checked,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which,
                                                boolean isChecked) {
                                checked[which] = isChecked;
                            }
                        })
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        settings.setEnabled(checked[0]);
                        settings.setAutoScroll(checked[1]);
                        settings.setWebConsoleEnabled(checked[2]);
                        settings.setBridgeEnabled(checked[3]);
                        settings.setNetworkEnabled(checked[4]);
                        settings.setPerformanceEnabled(checked[5]);
                        settings.setRedactionEnabled(checked[6]);
                        settings.setClearOnNewSession(checked[7]);
                        settings.setFloatingButtonEnabled(checked[8]);
                        autoScroll = checked[1];
                        autoScrollLabel.setTextColor(autoScroll
                                ? Color.parseColor("#FF4500") : Color.parseColor("#888888"));
                        toast("Settings applied");
                    }
                })
                .setNeutralButton("Buffer size", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showBufferSizeDialog();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showBufferSizeDialog() {
        final String[] sizes = {"500", "1000", "2000", "5000"};
        final int[] values = {500, 1000, 2000, 5000};
        new AlertDialog.Builder(activity)
                .setTitle("Maximum buffered logs")
                .setItems(sizes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        settings.setMaxEntries(values[which]);
                        repository.setCapacity(values[which]);
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==================== Lifecycle ====================

    /** Starts listening. Called when the host Activity resumes. */
    public void onResume() {
        repository.addListener(repositoryListener);
        dirty = true;
        scheduleRefresh();
    }

    /** Stops listening. Called when the host Activity pauses or is destroyed. */
    public void onPause() {
        repository.removeListener(repositoryListener);
        mainHandler.removeCallbacks(refreshTask);
        refreshScheduled = false;
    }

    /** Releases everything. Called from the host Activity's {@code onDestroy}. */
    public void release() {
        onPause();
        adapter.clearSelection();
    }

    // ==================== Helpers ====================

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private void toast(String message) {
        try {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}
