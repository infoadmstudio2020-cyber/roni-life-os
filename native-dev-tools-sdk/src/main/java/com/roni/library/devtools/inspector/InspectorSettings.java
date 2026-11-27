package com.roni.library.devtools.inspector;

import android.content.Context;
import android.content.SharedPreferences;

import com.roni.library.contracts.logger.LogLevel;
import com.roni.library.contracts.logger.LogSource;

/**
 * Persisted Inspector configuration.
 *
 * <p>Kept in its own {@link SharedPreferences} file rather than in the host application's
 * configuration: the inspector is a developer tool, and a corrupt diagnostic preference must
 * never be able to break project loading or startup routing.</p>
 */
public final class InspectorSettings implements InspectorConfig {

    private static final String PREFS = "roni_inspector_settings";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_AUTO_SCROLL = "auto_scroll";
    private static final String KEY_MAX_ENTRIES = "max_entries";
    private static final String KEY_MIN_LEVEL = "min_level";
    private static final String KEY_WEB_CONSOLE = "web_console";
    private static final String KEY_BRIDGE = "bridge";
    private static final String KEY_NETWORK = "network";
    private static final String KEY_PERFORMANCE = "performance";
    private static final String KEY_REDACTION = "redaction";
    private static final String KEY_CLEAR_ON_SESSION = "clear_on_session";
    private static final String KEY_PERSIST = "persist_across_recreation";
    private static final String KEY_FLOATING_BUTTON = "floating_button";

    private final SharedPreferences prefs;

    public InspectorSettings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ==================== Defaults ====================

    @Override
    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply();
    }

    public boolean isAutoScroll() {
        return prefs.getBoolean(KEY_AUTO_SCROLL, true);
    }

    public void setAutoScroll(boolean value) {
        prefs.edit().putBoolean(KEY_AUTO_SCROLL, value).apply();
    }

    @Override
    public int getMaxEntries() {
        return prefs.getInt(KEY_MAX_ENTRIES, RuntimeLogRepository.DEFAULT_CAPACITY);
    }

    public void setMaxEntries(int value) {
        prefs.edit().putInt(KEY_MAX_ENTRIES, value).apply();
    }

    @Override
    public LogLevel getMinLevel() {
        return LogLevel.fromString(prefs.getString(KEY_MIN_LEVEL, LogLevel.VERBOSE.name()),
                LogLevel.VERBOSE);
    }

    public void setMinLevel(LogLevel value) {
        prefs.edit().putString(KEY_MIN_LEVEL,
                (value != null ? value : LogLevel.VERBOSE).name()).apply();
    }

    public boolean isWebConsoleEnabled() {
        return prefs.getBoolean(KEY_WEB_CONSOLE, true);
    }

    public void setWebConsoleEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_WEB_CONSOLE, value).apply();
    }

    public boolean isBridgeEnabled() {
        return prefs.getBoolean(KEY_BRIDGE, true);
    }

    public void setBridgeEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_BRIDGE, value).apply();
    }

    public boolean isNetworkEnabled() {
        return prefs.getBoolean(KEY_NETWORK, true);
    }

    public void setNetworkEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_NETWORK, value).apply();
    }

    public boolean isPerformanceEnabled() {
        return prefs.getBoolean(KEY_PERFORMANCE, true);
    }

    public void setPerformanceEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_PERFORMANCE, value).apply();
    }

    @Override
    public boolean isRedactionEnabled() {
        return prefs.getBoolean(KEY_REDACTION, true);
    }

    public void setRedactionEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_REDACTION, value).apply();
    }

    public boolean isClearOnNewSession() {
        return prefs.getBoolean(KEY_CLEAR_ON_SESSION, false);
    }

    public void setClearOnNewSession(boolean value) {
        prefs.edit().putBoolean(KEY_CLEAR_ON_SESSION, value).apply();
    }

    public boolean isPersistAcrossRecreation() {
        return prefs.getBoolean(KEY_PERSIST, true);
    }

    public void setPersistAcrossRecreation(boolean value) {
        prefs.edit().putBoolean(KEY_PERSIST, value).apply();
    }

    public boolean isFloatingButtonEnabled() {
        return prefs.getBoolean(KEY_FLOATING_BUTTON, false);
    }

    public void setFloatingButtonEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_FLOATING_BUTTON, value).apply();
    }

    /** True when {@code source} collection is switched on. */
    @Override
    public boolean isSourceEnabled(LogSource source) {
        if (source == null) {
            return true;
        }
        switch (source) {
            case WEB:
                return isWebConsoleEnabled();
            case BRIDGE:
                return isBridgeEnabled();
            case NETWORK:
                return isNetworkEnabled();
            case PERFORMANCE:
                return isPerformanceEnabled();
            default:
                return true;
        }
    }
}
