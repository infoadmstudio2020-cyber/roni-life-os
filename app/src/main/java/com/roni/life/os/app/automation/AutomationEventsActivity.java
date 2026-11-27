package com.roni.life.os.app.automation;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.roni.library.contracts.automation.AutomationEvent;
import com.roni.library.platform.PlatformEngine;
import com.roni.library.platform.automation.AutomationException;
import com.roni.library.platform.automation.AutomationService;
import com.roni.life.os.app.R;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Read-only event log for the active project. */
public final class AutomationEventsActivity extends AppCompatActivity {

    private AutomationService service;
    private TextView log;
    private EditText filter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_automation_events);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Automation events");
        }
        toolbar.setNavigationOnClickListener(view -> finish());

        log = findViewById(R.id.event_log);
        filter = findViewById(R.id.event_filter);
        service = PlatformEngine.getInstance().getAutomationService();
        findViewById(R.id.event_refresh).setOnClickListener(view -> load());
        load();
    }

    private void load() {
        if (service == null) {
            log.setText("Automation engine unavailable");
            return;
        }
        try {
            List<AutomationEvent> events = service.getEvents(0L, 200);
            String topic = filter.getText().toString().trim();
            StringBuilder builder = new StringBuilder();
            for (AutomationEvent event : events) {
                if (!topic.isEmpty() && !event.getTopic().contains(topic)) {
                    continue;
                }
                builder.append('#').append(event.getSequence()).append(' ')
                        .append(DateFormat.getTimeInstance(DateFormat.MEDIUM)
                                .format(new Date(event.getTimestamp())))
                        .append(" [").append(event.getTopic()).append("] ")
                        .append(event.getPayload()).append('\n');
            }
            log.setText(builder.length() == 0 ? "No events recorded" : builder.toString());
        } catch (AutomationException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
