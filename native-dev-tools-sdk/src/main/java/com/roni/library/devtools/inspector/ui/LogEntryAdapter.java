package com.roni.library.devtools.inspector.ui;

import android.graphics.Color;

import com.roni.library.devtools.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.roni.library.contracts.logger.LogEntry;
import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.devtools.inspector.LogEntryFormatter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the unified log stream.
 *
 * <p>Selection is kept by entry id rather than by position so a live insert or an eviction
 * cannot silently move the selection onto a different entry.</p>
 */
public final class LogEntryAdapter extends RecyclerView.Adapter<LogEntryAdapter.RowHolder> {

    /** Row interaction callbacks. */
    public interface Callback {
        /** Tap: toggle selection when in selection mode, otherwise show details. */
        void onEntryClicked(LogEntry entry);

        /** Long press: copy the complete entry. */
        void onEntryLongClicked(LogEntry entry);
    }

    private final List<LogEntry> items = new ArrayList<>();
    private final Set<Long> selected = new LinkedHashSet<>();
    private final Callback callback;
    private boolean selectionMode;

    public LogEntryAdapter(Callback callback) {
        this.callback = callback;
        setHasStableIds(true);
    }

    /** Replaces the rendered list. Selection of entries that are gone is dropped. */
    public void submit(List<LogEntry> entries) {
        items.clear();
        if (entries != null) {
            items.addAll(entries);
        }
        if (!selected.isEmpty()) {
            Set<Long> alive = new LinkedHashSet<>();
            for (LogEntry entry : items) {
                if (selected.contains(entry.getId())) {
                    alive.add(entry.getId());
                }
            }
            selected.clear();
            selected.addAll(alive);
        }
        notifyDataSetChanged();
    }

    public int getItemCountInternal() {
        return items.size();
    }

    public LogEntry getItem(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    public List<LogEntry> getItems() {
        return new ArrayList<>(items);
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean enabled) {
        if (selectionMode == enabled) {
            return;
        }
        selectionMode = enabled;
        if (!enabled) {
            selected.clear();
        }
        notifyDataSetChanged();
    }

    public void toggleSelection(LogEntry entry) {
        if (entry == null) {
            return;
        }
        if (!selected.remove(entry.getId())) {
            selected.add(entry.getId());
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (LogEntry entry : items) {
            selected.add(entry.getId());
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selected.clear();
        notifyDataSetChanged();
    }

    public int getSelectedCount() {
        return selected.size();
    }

    /** Selected entries in display order. */
    public List<LogEntry> getSelectedEntries() {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : items) {
            if (selected.contains(entry.getId())) {
                result.add(entry);
            }
        }
        return result;
    }

    @Override
    public long getItemId(int position) {
        LogEntry entry = getItem(position);
        return entry != null ? entry.getId() : RecyclerView.NO_ID;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_entry, parent, false);
        return new RowHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowHolder holder, int position) {
        holder.bind(getItem(position), selected.contains(
                getItem(position) != null ? getItem(position).getId() : -1L));
    }

    /** One row. */
    final class RowHolder extends RecyclerView.ViewHolder {
        private final TextView time;
        private final TextView severity;
        private final TextView source;
        private final TextView module;
        private final TextView duration;
        private final TextView message;
        private final TextView detail;
        private final View row;

        RowHolder(@NonNull View itemView) {
            super(itemView);
            row = itemView;
            time = itemView.findViewById(R.id.log_time);
            severity = itemView.findViewById(R.id.log_severity);
            source = itemView.findViewById(R.id.log_source);
            module = itemView.findViewById(R.id.log_module);
            duration = itemView.findViewById(R.id.log_duration);
            message = itemView.findViewById(R.id.log_message);
            detail = itemView.findViewById(R.id.log_detail);
        }

        void bind(final LogEntry entry, boolean isSelected) {
            if (entry == null) {
                return;
            }
            time.setText(LogEntryFormatter.timeLabel(entry));
            severity.setText(shortLabel(entry.getLevel()));
            severity.setTextColor(colorFor(entry.getLevel()));
            severity.setBackgroundColor(chipColorFor(entry.getLevel()));
            source.setText(entry.getSource().getLabel());
            module.setText(entry.getModule());
            duration.setText(entry.hasDuration() ? entry.getDurationMs() + "ms" : "");

            message.setText(entry.getMessage());
            message.setTextColor(entry.getLevel().getPriority() >= LogLevel.WARN.getPriority()
                    ? colorFor(entry.getLevel())
                    : Color.parseColor("#E0E0E0"));

            String extra = buildDetail(entry);
            if (extra.isEmpty()) {
                detail.setVisibility(View.GONE);
            } else {
                detail.setVisibility(View.VISIBLE);
                detail.setText(extra);
            }

            row.setBackgroundColor(isSelected
                    ? Color.parseColor("#1A1A1A") : Color.parseColor("#020202"));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (callback != null) {
                        callback.onEntryClicked(entry);
                    }
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (callback != null) {
                        callback.onEntryLongClicked(entry);
                    }
                    return true;
                }
            });
        }

        private String buildDetail(LogEntry entry) {
            StringBuilder builder = new StringBuilder();
            if (!entry.getRequestId().isEmpty()) {
                builder.append("req=").append(entry.getRequestId());
            }
            if (!entry.getDirection().isEmpty()) {
                if (builder.length() > 0) {
                    builder.append("  ");
                }
                builder.append("dir=").append(entry.getDirection());
            }
            if (!entry.getStatus().isEmpty()) {
                if (builder.length() > 0) {
                    builder.append("  ");
                }
                builder.append("status=").append(entry.getStatus());
            }
            if (!entry.getCorrelationId().isEmpty()) {
                if (builder.length() > 0) {
                    builder.append("  ");
                }
                builder.append("corr=").append(entry.getCorrelationId());
            }
            String stack = LogEntryFormatter.stackTrace(entry);
            if (!stack.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(stack);
            }
            return builder.toString();
        }

        /**
         * Tag foreground. Each value is lightened relative to the raw palette so it clears
         * 4.5:1 against its own chip background at 10sp.
         */
        private int colorFor(LogLevel severity) {
            switch (severity) {
                case FATAL:
                case ERROR:
                    return Color.parseColor("#FF6A33");
                case WARN:
                    return Color.parseColor("#FFC233");
                case DEBUG:
                    return Color.parseColor("#6FB6FF");
                case VERBOSE:
                    return Color.parseColor("#A8A8A8");
                case INFO:
                default:
                    return Color.parseColor("#E0E0E0");
            }
        }

        /** Tag background: a dark tint of the severity hue, never a saturated fill. */
        private int chipColorFor(LogLevel severity) {
            switch (severity) {
                case FATAL:
                case ERROR:
                    return Color.parseColor("#2A0F08");
                case WARN:
                    return Color.parseColor("#2A2008");
                case DEBUG:
                    return Color.parseColor("#0A1A2A");
                case VERBOSE:
                case INFO:
                default:
                    return Color.parseColor("#141414");
            }
        }

        private String shortLabel(LogLevel severity) {
            return severity == LogLevel.VERBOSE ? "VERB" : severity.name();
        }
    }
}
