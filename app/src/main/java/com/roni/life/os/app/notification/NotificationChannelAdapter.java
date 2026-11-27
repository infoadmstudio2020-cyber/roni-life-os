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

/** Per-channel block controls; channel ids are already project-scoped by the engine. */
public final class NotificationChannelAdapter
        extends RecyclerView.Adapter<NotificationChannelAdapter.Holder> {

    public interface Listener {
        void onChannelBlocked(@NonNull String channelId, boolean blocked);
    }

    public static final class Row {
        public final String channelId;
        public final String name;
        public boolean blocked;

        public Row(String channelId, String name, boolean blocked) {
            this.channelId = channelId;
            this.name = name;
            this.blocked = blocked;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;

    public NotificationChannelAdapter(@NonNull Listener listener) {
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
                .inflate(R.layout.item_notification_channel, parent, false);
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

        Holder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.channel_name);
            block = itemView.findViewById(R.id.channel_block);
        }

        void bind(@NonNull final Row row) {
            name.setText(row.name);
            block.setOnCheckedChangeListener(null);
            block.setChecked(row.blocked);
            block.setOnCheckedChangeListener((buttonView, checked) -> {
                row.blocked = checked;
                listener.onChannelBlocked(row.channelId, checked);
            });
        }
    }
}
