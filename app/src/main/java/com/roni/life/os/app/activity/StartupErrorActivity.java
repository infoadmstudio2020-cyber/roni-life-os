package com.roni.life.os.app.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.roni.life.os.app.R;
import com.roni.life.os.app.runtime.StartupManager;
import com.roni.life.os.app.runtime.StartupTrace;

/**
 * Safe fallback shown when the configuration cannot produce a startup destination.
 *
 * <p>Reached only when the dashboard is disabled <em>and</em> no usable default project exists.
 * It never opens the dashboard on its own — the management interface is available behind an
 * explicit user action, so a disabled dashboard can never appear as part of startup.</p>
 */
public class StartupErrorActivity extends AppCompatActivity {

    /** Diagnostic reason, surfaced in the console block. */
    public static final String EXTRA_REASON = "extra_startup_error_reason";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup_error);

        TextView reason = findViewById(R.id.startup_error_reason);
        String detail = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_REASON) : null;
        StringBuilder text = new StringBuilder();
        if (detail != null && !detail.trim().isEmpty()) {
            text.append(detail);
        }
        // The persisted startup sequence is the only evidence available when the app dies
        // before any UI appears, so it is shown right here.
        String trace = StartupTrace.read(this);
        if (!trace.isEmpty()) {
            text.append("\n\nStartup trace:\n").append(trace);
        }
        if (text.length() > 0) {
            reason.setText(text.toString());
        }

        findViewById(R.id.startup_error_open_bootstrap)
                .setOnClickListener(view -> openBootstrap());
        findViewById(R.id.startup_error_exit)
                .setOnClickListener(view -> finishAffinity());
    }

    /** Explicit user request — the only way the management interface opens from here. */
    private void openBootstrap() {
        Intent intent = StartupManager.dashboardIntent(this, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
