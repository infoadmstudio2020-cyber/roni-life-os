package com.roni.life.os.app.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import com.roni.life.os.app.util.AppLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Persisted startup trace.
 *
 * <p>Startup can fail before any UI exists — the process may even be killed while the
 * launcher activity is still resolving its destination. An in-memory buffer would be lost
 * in exactly that case, so every step is written to SharedPreferences and survives the
 * process death. The record is readable on the next launch and from the startup error
 * screen, which makes "the app closes right after the icon" diagnosable without a debugger.</p>
 */
public final class StartupTrace {

    private static final String PREFS = "startup_trace";
    private static final String KEY_LINES = "lines";
    private static final int MAX_LINES = 120;
    private static final String SEPARATOR = "\n";

    private StartupTrace() {
    }

    /** Appends one step. Never throws — tracing must not be able to break startup. */
    public static void record(Context context, String step, String detail) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date())
                + "  " + step
                + (detail != null && !detail.isEmpty() ? "  " + detail : "");
        AppLog.i("STARTUP_TRACE: " + line);
        if (context == null) {
            return;
        }
        try {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            List<String> lines = split(prefs.getString(KEY_LINES, ""));
            lines.add(line);
            while (lines.size() > MAX_LINES) {
                lines.remove(0);
            }
            StringBuilder builder = new StringBuilder();
            for (String item : lines) {
                if (builder.length() > 0) {
                    builder.append(SEPARATOR);
                }
                builder.append(item);
            }
            prefs.edit().putString(KEY_LINES, builder.toString()).apply();
        } catch (Throwable error) {
            AppLog.w("STARTUP_TRACE: unable to persist — " + error.getMessage());
        }
    }

    /** The recorded sequence, oldest first. Empty when nothing was recorded yet. */
    public static String read(Context context) {
        if (context == null) {
            return "";
        }
        try {
            return context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_LINES, "");
        } catch (Throwable error) {
            return "";
        }
    }

    /** Clears the record, e.g. before a deliberate cold-start test. */
    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        try {
            context.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_LINES).apply();
        } catch (Throwable error) {
            AppLog.w("STARTUP_TRACE: unable to clear — " + error.getMessage());
        }
    }

    private static List<String> split(String raw) {
        List<String> lines = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return lines;
        }
        for (String item : raw.split(SEPARATOR)) {
            if (!item.isEmpty()) {
                lines.add(item);
            }
        }
        return lines;
    }
}
