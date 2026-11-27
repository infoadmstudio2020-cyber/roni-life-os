package com.roni.life.os.app.automation;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.roni.library.contracts.automation.AlarmSpec;
import com.roni.library.contracts.automation.BackgroundTask;
import com.roni.library.contracts.automation.JobSpec;
import com.roni.library.contracts.automation.ServiceStatus;
import com.roni.library.contracts.automation.TransferProgress;
import com.roni.library.contracts.automation.TransferRequest;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.automation.AutomationException;
import com.roni.library.platform.automation.AutomationService;
import com.roni.life.os.app.R;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Consumer App dashboard for tasks, transfers, alarms, jobs and native services. */
public final class AutomationActivity extends AppCompatActivity {

    /** Optional section to reveal when opened from a Quick Ball shortcut. */
    public static final String EXTRA_SECTION = "extra_automation_section";

    private final Handler main = new Handler(Looper.getMainLooper());
    private AutomationService service;
    private TextView summary;
    private LinearLayout tasksContainer;
    private LinearLayout transfersContainer;
    private LinearLayout alarmsContainer;
    private LinearLayout jobsContainer;
    private LinearLayout servicesContainer;

    private final Runnable refreshTick = new Runnable() {
        @Override
        public void run() {
            refresh();
            main.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_automation);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Automation");
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        summary = findViewById(R.id.automation_summary);
        tasksContainer = findViewById(R.id.tasks_container);
        transfersContainer = findViewById(R.id.transfers_container);
        alarmsContainer = findViewById(R.id.alarms_container);
        jobsContainer = findViewById(R.id.jobs_container);
        servicesContainer = findViewById(R.id.services_container);

        service = PlatformEngine.getInstance().getAutomationService();
        if (service == null) {
            summary.setText("Automation engine unavailable");
            return;
        }

        findViewById(R.id.action_new_task).setOnClickListener(view -> promptTask());
        findViewById(R.id.action_new_alarm).setOnClickListener(view -> promptAlarm());
        findViewById(R.id.action_download).setOnClickListener(view -> promptDownload());
        findViewById(R.id.action_events).setOnClickListener(view ->
                startActivity(new Intent(this, AutomationEventsActivity.class)));
        findViewById(R.id.action_refresh).setOnClickListener(view -> refresh());
        findViewById(R.id.action_clear).setOnClickListener(view -> {
            try {
                int removed = service.clearTasks();
                toast("Removed " + removed + " finished task(s)");
                refresh();
            } catch (AutomationException error) {
                toast(error.getMessage());
            }
        });

        scrollToRequestedSection();
    }

    /** Reveals the section requested by a Quick Ball shortcut, if any. */
    private void scrollToRequestedSection() {
        String section = getIntent().getStringExtra(EXTRA_SECTION);
        if (section == null) {
            return;
        }
        final LinearLayout target;
        switch (section) {
            case "tasks":
                target = tasksContainer;
                break;
            case "transfers":
                target = transfersContainer;
                break;
            case "alarms":
                target = alarmsContainer;
                break;
            case "jobs":
                target = jobsContainer;
                break;
            case "services":
                target = servicesContainer;
                break;
            default:
                return;
        }
        target.post(() -> {
            android.view.View scroll = findViewById(R.id.automation_scroll);
            if (scroll instanceof android.widget.ScrollView) {
                ((android.widget.ScrollView) scroll).smoothScrollTo(0, target.getTop());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.post(refreshTick);
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(refreshTick);
        super.onPause();
    }

    private void refresh() {
        if (service == null) {
            return;
        }
        try {
            List<BackgroundTask> tasks = service.listTasks();
            List<TransferProgress> transfers = service.listTransfers();
            List<AlarmSpec> alarms = service.listAlarms();
            List<JobSpec> jobs = service.listJobs();
            List<ServiceStatus> services = service.listServices();

            summary.setText("Project: " + service.status().get("projectId")
                    + " · tasks " + tasks.size()
                    + " · transfers " + transfers.size()
                    + " · alarms " + alarms.size()
                    + " · jobs " + jobs.size()
                    + " · services " + services.size());

            tasksContainer.removeAllViews();
            for (final BackgroundTask task : tasks) {
                addRow(tasksContainer, task.getName(),
                        task.getState() + " · " + task.getProgress() + "% · "
                                + task.getType(),
                        task.isFinished() ? null : "Cancel",
                        task.isFinished() ? null : () -> cancelTask(task.getId()));
            }
            if (tasks.isEmpty()) {
                addEmpty(tasksContainer, "No background tasks yet");
            }

            transfersContainer.removeAllViews();
            for (final TransferProgress progress : transfers) {
                addRow(transfersContainer,
                        progress.getDirection() + " · " + progress.getLocalPath(),
                        progress.getState() + " · " + progress.getPercent() + "% · "
                                + formatSize(progress.getBytesTransferred()) + " / "
                                + formatSize(progress.getTotalBytes()),
                        progress.isFinished() ? null : "Cancel",
                        progress.isFinished() ? null : () -> cancelTransfer(progress.getId()));
            }
            if (transfers.isEmpty()) {
                addEmpty(transfersContainer, "No transfers yet");
            }

            alarmsContainer.removeAllViews();
            for (final AlarmSpec alarm : alarms) {
                addRow(alarmsContainer, alarm.getLabel(),
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(new Date(alarm.getTriggerAt()))
                                + (alarm.isRepeating() ? " · repeating" : " · one shot"),
                        "Cancel", () -> cancelAlarm(alarm.getId()));
            }
            if (alarms.isEmpty()) {
                addEmpty(alarmsContainer, "No alarms scheduled");
            }

            jobsContainer.removeAllViews();
            for (final JobSpec job : jobs) {
                addRow(jobsContainer, job.getName(),
                        job.getWorkerType() + (job.isPeriodic() ? " · periodic" : " · one shot")
                                + (job.requiresNetwork() ? " · network" : ""),
                        "Cancel", () -> cancelJob(job.getId()));
            }
            if (jobs.isEmpty()) {
                addEmpty(jobsContainer, "No scheduled jobs");
            }

            servicesContainer.removeAllViews();
            for (final ServiceStatus status : services) {
                addRow(servicesContainer, status.getName(),
                        status.getState() + " · " + status.getDescription(),
                        "RUNNING".equals(status.getState()) ? "Stop" : "Start",
                        () -> toggleService(status));
            }
            if (services.isEmpty()) {
                addEmpty(servicesContainer, "No native services registered");
            }
        } catch (AutomationException error) {
            summary.setText(error.getMessage());
        }
    }

    private void toggleService(@NonNull ServiceStatus status) {
        try {
            if ("RUNNING".equals(status.getState())) {
                service.stopService(status.getName());
            } else {
                service.startService(status.getName());
            }
            refresh();
        } catch (AutomationException error) {
            toast(error.getMessage());
        }
    }

    private void cancelTask(@NonNull String id) {
        try {
            service.cancelTask(id);
            refresh();
        } catch (AutomationException error) {
            toast(error.getMessage());
        }
    }

    private void cancelTransfer(@NonNull String id) {
        try {
            service.cancelTransfer(id);
            refresh();
        } catch (AutomationException error) {
            toast(error.getMessage());
        }
    }

    private void cancelAlarm(@NonNull String id) {
        try {
            service.cancelAlarm(id);
            refresh();
        } catch (AutomationException error) {
            toast(error.getMessage());
        }
    }

    private void cancelJob(@NonNull String id) {
        try {
            service.cancelJob(id);
            refresh();
        } catch (AutomationException error) {
            toast(error.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // dialogs
    // ------------------------------------------------------------------

    private void promptTask() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 8, 24, 8);
        EditText name = new EditText(this);
        name.setHint("Task name");
        EditText type = new EditText(this);
        type.setHint("Type: noop, echo, delay, event");
        type.setText("noop");
        layout.addView(name);
        layout.addView(type);
        new AlertDialog.Builder(this)
                .setTitle("New background task")
                .setView(layout)
                .setPositiveButton("Run", (dialog, which) -> {
                    try {
                        service.submitTask(name.getText().toString().trim(),
                                type.getText().toString().trim(), null, 2);
                        refresh();
                    } catch (AutomationException error) {
                        toast(error.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptAlarm() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 8, 24, 8);
        EditText label = new EditText(this);
        label.setHint("Alarm label");
        EditText seconds = new EditText(this);
        seconds.setHint("Seconds from now");
        seconds.setInputType(InputType.TYPE_CLASS_NUMBER);
        seconds.setText("60");
        layout.addView(label);
        layout.addView(seconds);
        new AlertDialog.Builder(this)
                .setTitle("New alarm")
                .setView(layout)
                .setPositiveButton("Schedule", (dialog, which) -> {
                    long delay;
                    try {
                        delay = Long.parseLong(seconds.getText().toString().trim());
                    } catch (NumberFormatException error) {
                        delay = 60L;
                    }
                    try {
                        service.setAlarm(new AlarmSpec.Builder()
                                .id("alarm-" + System.currentTimeMillis())
                                .label(label.getText().toString().trim().isEmpty()
                                        ? "Alarm" : label.getText().toString().trim())
                                .triggerAt(System.currentTimeMillis() + Math.max(1L, delay) * 1000L)
                                .exact(false)
                                .build());
                        refresh();
                    } catch (AutomationException error) {
                        toast(error.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptDownload() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 8, 24, 8);
        EditText url = new EditText(this);
        url.setHint("https://example.com/file.zip");
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        EditText path = new EditText(this);
        path.setHint("downloads/file.zip");
        layout.addView(url);
        layout.addView(path);
        new AlertDialog.Builder(this)
                .setTitle("Download file")
                .setView(layout)
                .setPositiveButton("Start", (dialog, which) -> {
                    try {
                        service.download(new TransferRequest.Builder()
                                .url(url.getText().toString().trim())
                                .localPath(path.getText().toString().trim())
                                .build());
                        refresh();
                    } catch (AutomationException error) {
                        toast(error.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------
    // rows
    // ------------------------------------------------------------------

    private void addRow(@NonNull LinearLayout container, @NonNull String title,
                        @NonNull String subtitle, @Nullable String actionLabel,
                        @Nullable Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(textParams);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14f);
        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(11f);
        texts.addView(titleView);
        texts.addView(subtitleView);
        row.addView(texts);

        if (actionLabel != null && action != null) {
            TextView actionView = new TextView(this);
            actionView.setText(actionLabel);
            actionView.setTextSize(12f);
            actionView.setPadding(16, 8, 8, 8);
            actionView.setOnClickListener(view -> action.run());
            row.addView(actionView);
        }
        container.addView(row);
    }

    private void addEmpty(@NonNull LinearLayout container, @NonNull String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextSize(12f);
        view.setPadding(0, 8, 0, 8);
        container.addView(view);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
    }

    private void toast(@Nullable String message) {
        if (message != null && !message.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }
}
