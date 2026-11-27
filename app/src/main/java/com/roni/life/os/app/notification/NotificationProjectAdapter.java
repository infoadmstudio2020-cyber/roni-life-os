package com.roni.life.os.app.notification;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.roni.life.os.app.R;

import java.util.ArrayList;
import java.util.List;

/** Per-project block / mute controls used by the notification settings screen. */
public final class NotificationProjectAdapter
        extends RecyclerView.Adapter<NotificationProjectAdapter.Holder> {

    public interface Listener {
        void onBlockChanged(@NonNull String projectId, boolean blocked);

        void onMuteChanged(@NonNull String projectId, boolean muted);
    }

    public static final class Row {
        public final String projectId;
        public final String name;
        public boolean blocked;
        public boolean muted;

        public Row(String projectId, String name, boolean blocked, boolean muted) {
            this.projectId = projectId;
            this.name = name;
            this.blocked = blocked;
            this.muted = muted;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;

    public NotificationProjectAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<Row> values) {
        rows.clear();
        rows.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification_project, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(rows.get(position));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    final class Holder extends RecyclerView.ViewHolder {

        private final TextView name;
        private final Switch block;
        private final Switch mute;

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.project_name);
            block = itemView.findViewById(R.id.project_block);
            mute = itemView.findViewById(R.id.project_mute);
        }

        void bind(@NonNull final Row row) {
            name.setText(row.name);
            block.setOnCheckedChangeListener(null);
            mute.setOnCheckedChangeListener(null);
            block.setChecked(row.blocked);
            mute.setChecked(row.muted);
            mute.setEnabled(!row.blocked);
            block.setOnCheckedChangeListener((buttonView, checked) -> {
                row.blocked = checked;
                listener.onBlockChanged(row.projectId, checked);
                notifyItemChanged(getAdapterPosition());
            });
            mute.setOnCheckedChangeListener((buttonView, checked) -> {
                row.muted = checked;
                listener.onMuteChanged(row.projectId, checked);
            });
        }
    }
}
