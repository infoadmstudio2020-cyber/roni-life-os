package com.roni.life.os.app.notification;

import android.os.Bundle;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.widget.Toolbar;
import com.roni.library.contracts.notification.NotificationChannelSpec;
import com.roni.library.contracts.notification.NotificationPreferences;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.notification.NotificationService;
import com.roni.life.os.app.R;
import com.roni.life.os.app.core.ApplicationCore;
import com.roni.life.os.app.project.Project;

import java.util.ArrayList;
import java.util.List;

/**
 * Notification settings: global switch, runtime permission, alert preferences, and per-project /
 * per-channel blocking. Every change is written through {@link NotificationService#setPreferences}
 * so the engine's delivery policy and the UI can never disagree.
 */
public final class NotificationSettingsActivity extends AppCompatActivity
        implements NotificationProjectAdapter.Listener, NotificationChannelAdapter.Listener {

    private NotificationService service;
    private NotificationProjectAdapter projectAdapter;
    private NotificationChannelAdapter channelAdapter;
    private TextView permissionStatus;
    private Switch globalSwitch;
    private Switch soundSwitch;
    private Switch vibrationSwitch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());

        service = PlatformEngine.getInstance().getNotificationService();

        permissionStatus = findViewById(R.id.permission_status);
        globalSwitch = findViewById(R.id.global_switch);
        soundSwitch = findViewById(R.id.sound_switch);
        vibrationSwitch = findViewById(R.id.vibration_switch);

        RecyclerView projectList = findViewById(R.id.project_list);
        projectAdapter = new NotificationProjectAdapter(this);
        projectList.setLayoutManager(new LinearLayoutManager(this));
        projectList.setAdapter(projectAdapter);

        RecyclerView channelList = findViewById(R.id.channel_list);
        channelAdapter = new NotificationChannelAdapter(this);
        channelList.setLayoutManager(new LinearLayoutManager(this));
        channelList.setAdapter(channelAdapter);

        findViewById(R.id.request_permission).setOnClickListener(view ->
                NotificationPermissionHelper.request(this));

        globalSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (service == null) {
                return;
            }
            service.setPreferences(service.getPreferences().toBuilder().globalEnabled(checked).build());
            service.setEnabled(checked);
        });
        soundSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (service != null) {
                service.setPreferences(service.getPreferences().toBuilder().soundEnabled(checked).build());
            }
        });
        vibrationSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (service != null) {
                service.setPreferences(service.getPreferences().toBuilder().vibrationEnabled(checked).build());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bind();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NotificationPermissionHelper.REQUEST_CODE) {
            bind();
        }
    }

    private void bind() {
        if (service == null) {
            permissionStatus.setText("Notification engine is not available");
            return;
        }
        NotificationPreferences preferences = service.getPreferences();
        globalSwitch.setOnCheckedChangeListener(null);
        soundSwitch.setOnCheckedChangeListener(null);
        vibrationSwitch.setOnCheckedChangeListener(null);
        globalSwitch.setChecked(preferences.isGlobalEnabled());
        soundSwitch.setChecked(preferences.isSoundEnabled());
        vibrationSwitch.setChecked(preferences.isVibrationEnabled());
        globalSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            service.setPreferences(service.getPreferences().toBuilder().globalEnabled(checked).build());
            service.setEnabled(checked);
        });
        soundSwitch.setOnCheckedChangeListener((buttonView, checked) ->
                service.setPreferences(service.getPreferences().toBuilder().soundEnabled(checked).build()));
        vibrationSwitch.setOnCheckedChangeListener((buttonView, checked) ->
                service.setPreferences(service.getPreferences().toBuilder().vibrationEnabled(checked).build()));

        permissionStatus.setText(NotificationPermissionHelper.describe(service.getPermissionState()));
        findViewById(R.id.request_permission).setVisibility(
                service.getPermissionState().canPost() ? View.GONE : View.VISIBLE);

        List<NotificationProjectAdapter.Row> rows = new ArrayList<>();
        for (Project project : ApplicationCore.get().getStore().getAll()) {
            rows.add(new NotificationProjectAdapter.Row(project.getId(), project.getName(),
                    !preferences.isProjectAllowed(project.getId()),
                    preferences.isProjectMuted(project.getId())));
        }
        projectAdapter.submit(rows);

        List<NotificationChannelAdapter.Row> channels = new ArrayList<>();
        for (NotificationChannelSpec channel : service.getChannels()) {
            channels.add(new NotificationChannelAdapter.Row(channel.getChannelId(),
                    channel.getName(), !preferences.isChannelAllowed("", channel.getChannelId())));
        }
        channelAdapter.submit(channels);
    }

    @Override
    public void onBlockChanged(@NonNull String projectId, boolean blocked) {
        if (service == null) {
            return;
        }
        service.setPreferences(service.getPreferences().toBuilder()
                .blockProject(projectId, blocked).build());
        Toast.makeText(this, blocked ? "Project blocked" : "Project allowed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMuteChanged(@NonNull String projectId, boolean muted) {
        if (service == null) {
            return;
        }
        service.setPreferences(service.getPreferences().toBuilder()
                .muteProject(projectId, muted).build());
    }

    @Override
    public void onChannelBlocked(@NonNull String channelId, boolean blocked) {
        if (service == null) {
            return;
        }
        String projectId = channelId.startsWith("p") && channelId.contains("_")
                ? channelId.substring(1, channelId.indexOf('_')) : "";
        service.setPreferences(service.getPreferences().toBuilder()
                .blockChannel(projectId, channelId, blocked).build());
    }
}
