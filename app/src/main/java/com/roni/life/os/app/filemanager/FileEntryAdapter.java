package com.roni.life.os.app.filemanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.roni.library.contracts.filemanager.FileCategory;
import com.roni.library.contracts.filemanager.FileEntry;
import com.roni.life.os.app.R;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** List/grid adapter for the file explorer with multi-selection support. */
public final class FileEntryAdapter extends RecyclerView.Adapter<FileEntryAdapter.Holder> {

    public interface Listener {
        void onOpen(@NonNull FileEntry entry);

        void onToggleSelection(@NonNull FileEntry entry);
    }

    private final List<FileEntry> items = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private final Listener listener;
    private boolean selectionMode;

    public FileEntryAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<FileEntry> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean value) {
        selectionMode = value;
        if (!value) {
            selected.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    @NonNull
    public Set<String> getSelected() {
        return new HashSet<>(selected);
    }

    public void clearSelection() {
        selected.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file_entry, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static String iconFor(@NonNull FileEntry entry) {
        if (entry.isDirectory()) {
            return "📁";
        }
        switch (entry.getCategory()) {
            case FileCategory.IMAGE:
                return "🖼";
            case FileCategory.VIDEO:
                return "🎬";
            case FileCategory.AUDIO:
                return "🎵";
            case FileCategory.DOCUMENT:
                return "📄";
            case FileCategory.ARCHIVE:
                return "🗜";
            default:
                return "📦";
        }
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    final class Holder extends RecyclerView.ViewHolder {

        private final TextView icon;
        private final TextView name;
        private final TextView meta;
        private final CheckBox check;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.file_icon);
            name = itemView.findViewById(R.id.file_name);
            meta = itemView.findViewById(R.id.file_meta);
            check = itemView.findViewById(R.id.file_check);
        }

        void bind(@NonNull final FileEntry entry) {
            icon.setText(iconFor(entry));
            name.setText(entry.getName());
            String date = DateFormat.getDateInstance(DateFormat.SHORT)
                    .format(new Date(entry.getModifiedAt()));
            meta.setText(entry.isDirectory()
                    ? entry.getChildCount() + " items · " + date
                    : formatSize(entry.getSizeBytes()) + " · " + date);
            check.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            check.setChecked(selected.contains(entry.getId()));
            itemView.setOnClickListener(view -> {
                if (selectionMode) {
                    toggle(entry);
                } else {
                    listener.onOpen(entry);
                }
            });
            itemView.setOnLongClickListener(view -> {
                toggle(entry);
                return true;
            });
        }

        private void toggle(@NonNull FileEntry entry) {
            if (selected.contains(entry.getId())) {
                selected.remove(entry.getId());
            } else {
                selected.add(entry.getId());
            }
            selectionMode = true;
            listener.onToggleSelection(entry);
            notifyDataSetChanged();
        }
    }
}
