package com.roni.life.os.app.notification;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.roni.library.contracts.notification.NotificationRecord;
import com.roni.life.os.app.R;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** RecyclerView adapter for the Notification Center list. */
public final class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.Holder> {

    public interface Listener {
        void onOpen(@NonNull NotificationRecord record);

        void onCancel(@NonNull NotificationRecord record);
    }

    private final List<NotificationRecord> items = new ArrayList<>();
    private final Listener listener;
    private final DateFormat timeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    public NotificationAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<NotificationRecord> records) {
        items.clear();
        items.addAll(records);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
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

    final class Holder extends RecyclerView.ViewHolder {

        private final View dot;
        private final TextView title;
        private final TextView body;
        private final TextView meta;
        private final TextView cancel;

        Holder(@NonNull View itemView) {
            super(itemView);
            dot = itemView.findViewById(R.id.notification_dot);
            title = itemView.findViewById(R.id.notification_title);
            body = itemView.findViewById(R.id.notification_body);
            meta = itemView.findViewById(R.id.notification_meta);
            cancel = itemView.findViewById(R.id.notification_cancel);
        }

        void bind(@NonNull final NotificationRecord record) {
            title.setText(record.getTitle());
            body.setText(record.getBody());
            body.setVisibility(record.getBody().isEmpty() ? View.GONE : View.VISIBLE);
            dot.setVisibility(record.isRead() ? View.INVISIBLE : View.VISIBLE);
            String state = record.isCancelled() ? " · dismissed" : "";
            meta.setText(record.getProjectId() + " · " + record.getStyle().name() + " · "
                    + timeFormat.format(new Date(record.getPostedAt())) + state);
            itemView.setAlpha(record.isRead() ? 0.72f : 1f);
            itemView.setOnClickListener(view -> listener.onOpen(record));
            cancel.setOnClickListener(view -> listener.onCancel(record));
        }
    }
}
