package com.roni.life.os.app.notification;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.widget.Toolbar;
import com.roni.library.contracts.notification.NotificationRecord;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.notification.NotificationService;
import com.roni.life.os.app.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Notification Center: every notification the runtime produced, across all projects.
 *
 * <p>Read state, dismissal and cancellation all go through {@link NotificationService}, so the
 * center never touches the Android notification manager directly.</p>
 */
public final class NotificationCenterActivity extends AppCompatActivity
        implements NotificationAdapter.Listener {

    private NotificationService service;
    private NotificationAdapter adapter;
    private RecyclerView list;
    private TextView empty;
    private boolean unreadOnly;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_center);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        service = PlatformEngine.getInstance().getNotificationService();
        list = findViewById(R.id.notification_list);
        empty = findViewById(R.id.notification_empty);

        adapter = new NotificationAdapter(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.filter_all).setOnClickListener(view -> {
            unreadOnly = false;
            refresh();
        });
        findViewById(R.id.filter_unread).setOnClickListener(view -> {
            unreadOnly = true;
            refresh();
        });
        findViewById(R.id.mark_all_read).setOnClickListener(view -> markAllRead());
        findViewById(R.id.clear_history).setOnClickListener(view -> clearHistory());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (service == null) {
            empty.setText("Notification engine is not available");
            empty.setVisibility(View.VISIBLE);
            adapter.submit(new ArrayList<>());
            return;
        }
        List<NotificationRecord> records = service.getAllHistory(false);
        List<NotificationRecord> visible = new ArrayList<>();
        for (NotificationRecord record : records) {
            if (!unreadOnly || !record.isRead()) {
                visible.add(record);
            }
        }
        adapter.submit(visible);
        empty.setText(unreadOnly ? "No unread notifications" : "No notifications yet");
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(visible.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void markAllRead() {
        if (service == null) {
            return;
        }
        int count = 0;
        for (String projectId : projectsInHistory()) {
            count += service.markAllRead(projectId);
        }
        Toast.makeText(this, count + " notification(s) marked as read", Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void clearHistory() {
        if (service == null) {
            return;
        }
        for (String projectId : projectsInHistory()) {
            service.clearHistory(projectId);
        }
        Toast.makeText(this, "Notification history cleared", Toast.LENGTH_SHORT).show();
        refresh();
    }

    @NonNull
    private Set<String> projectsInHistory() {
        Set<String> ids = new LinkedHashSet<>();
        if (service != null) {
            for (NotificationRecord record : service.getAllHistory(true)) {
                ids.add(record.getProjectId());
            }
        }
        return ids;
    }

    @Override
    public void onOpen(@NonNull NotificationRecord record) {
        if (service == null) {
            return;
        }
        service.markRead(record.getProjectId(), record.getId());
        new NotificationDeepLinkRouter(this).onNotificationOpened(record.getProjectId(),
                record.getDeepLink());
        refresh();
    }

    @Override
    public void onCancel(@NonNull NotificationRecord record) {
        if (service == null) {
            return;
        }
        try {
            service.cancel(record.getProjectId(), record.getKey());
        } catch (Throwable error) {
            Toast.makeText(this, "Unable to dismiss: " + error.getMessage(), Toast.LENGTH_SHORT).show();
        }
        refresh();
    }
}
