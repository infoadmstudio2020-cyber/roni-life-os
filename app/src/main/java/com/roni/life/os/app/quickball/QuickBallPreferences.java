package com.roni.life.os.app.quickball;

import android.content.Context;

import com.roni.library.storage.StorageEngine;
import com.roni.library.storage.preferences.EncryptedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persisted Quick Ball preferences.
 *
 * <p>Stored through the existing {@link EncryptedPreferences} engine, so no new storage
 * dependency is introduced. Every read falls back to the pre-upgrade behaviour (all actions
 * visible, 48 dp ball, full opacity) when the store is unavailable, which keeps the panel usable
 * even if storage initialisation failed.</p>
 */
public final class QuickBallPreferences {

    private static final String STORE = "quickball_ui";
    private static final String KEY_VERSION = "version";
    private static final String KEY_ORDER = "order";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LABEL_PREFIX = "label.";
    private static final String KEY_ICON_PREFIX = "icon.";
    private static final String KEY_BALL_X = "ball_x";
    private static final String KEY_BALL_Y = "ball_y";
    private static final String KEY_BALL_HIDDEN = "ball_hidden";
    private static final String KEY_BALL_SIZE = "ball_size";
    private static final String KEY_BALL_OPACITY = "ball_opacity";
    private static final String KEY_LAYOUT = "layout";
    private static final String KEY_SHOW_LABELS = "show_labels";

    private static final int VERSION = 1;
    private static final int DEFAULT_BALL_SIZE = 48;

    private final EncryptedPreferences preferences;

    private QuickBallPreferences(EncryptedPreferences preferences) {
        this.preferences = preferences;
    }

    public static QuickBallPreferences get(Context context) {
        EncryptedPreferences store = null;
        try {
            store = StorageEngine.getPreferences(STORE);
        } catch (Throwable ignored) {
            // Fall back to defaults; the panel must still open.
        }
        QuickBallPreferences result = new QuickBallPreferences(store);
        result.migrateIfNeeded(context);
        return result;
    }

    /**
     * First run after the upgrade: every registered action is enabled in registration order,
     * which is exactly how the panel behaved before customization existed.
     */
    private void migrateIfNeeded(Context context) {
        if (preferences == null || preferences.getInt(KEY_VERSION, 0) >= VERSION) {
            return;
        }
        NativeActionRegistry.get().ensureDefaults(context);
        List<String> ids = NativeActionRegistry.get().getIds();
        preferences.putString(KEY_ORDER, QuickBallLayout.join(ids));
        preferences.putString(KEY_ENABLED, QuickBallLayout.join(ids));
        preferences.putInt(KEY_VERSION, VERSION);
        preferences.flush();
    }

    // ==================== actions ====================

    /** Enabled, available actions in the user's order, with custom labels and icons applied. */
    public List<NativeAction> orderedActions(Context context) {
        NativeActionRegistry registry = NativeActionRegistry.get();
        registry.ensureDefaults(context);

        List<NativeAction> all = registry.getAll();
        List<String> allIds = new ArrayList<>();
        for (NativeAction action : all) {
            allIds.add(action.getId());
        }

        List<String> order = QuickBallLayout.sanitize(allIds, storedOrder());
        Set<String> enabled = enabledIds(allIds);
        List<String> visible = QuickBallLayout.enabledOrdered(order, enabled);

        List<NativeAction> result = new ArrayList<>();
        for (String id : visible) {
            NativeAction action = registry.find(id);
            if (action == null || !action.isAvailable(context)) {
                continue;
            }
            result.add(action
                    .withLabel(getLabel(id, action.getLabel()))
                    .withIcon(getIcon(id, action.getIconRes())));
        }
        return result;
    }

    public boolean isEnabled(String id) {
        return enabledIds(NativeActionRegistry.get().getIds()).contains(id);
    }

    public void setEnabled(String id, boolean enabled) {
        if (preferences == null || id == null) {
            return;
        }
        Set<String> ids = enabledIds(NativeActionRegistry.get().getIds());
        if (enabled) {
            ids.add(id);
        } else {
            ids.remove(id);
        }
        preferences.putString(KEY_ENABLED, QuickBallLayout.join(new ArrayList<>(ids)));
        preferences.flush();
    }

    public void move(String id, int delta) {
        if (preferences == null) {
            return;
        }
        List<String> order = QuickBallLayout.sanitize(
                NativeActionRegistry.get().getIds(), storedOrder());
        preferences.putString(KEY_ORDER,
                QuickBallLayout.join(QuickBallLayout.move(order, id, delta)));
        preferences.flush();
    }

    public String getLabel(String id, String fallback) {
        if (preferences == null) {
            return fallback;
        }
        String value = preferences.getString(KEY_LABEL_PREFIX + id, "");
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public void setLabel(String id, String label) {
        if (preferences == null) {
            return;
        }
        if (label == null || label.trim().isEmpty()) {
            preferences.remove(KEY_LABEL_PREFIX + id);
        } else {
            preferences.putString(KEY_LABEL_PREFIX + id, label.trim());
        }
        preferences.flush();
    }

    public int getIcon(String id, int fallback) {
        if (preferences == null) {
            return fallback;
        }
        return preferences.getInt(KEY_ICON_PREFIX + id, fallback);
    }

    public void setIcon(String id, int iconRes) {
        if (preferences == null) {
            return;
        }
        preferences.putInt(KEY_ICON_PREFIX + id, iconRes);
        preferences.flush();
    }

    // ==================== appearance ====================

    public int getBallSizeDp() {
        return preferences == null
                ? DEFAULT_BALL_SIZE
                : preferences.getInt(KEY_BALL_SIZE, DEFAULT_BALL_SIZE);
    }

    public void setBallSizeDp(int dp) {
        if (preferences == null) {
            return;
        }
        preferences.putInt(KEY_BALL_SIZE, Math.max(36, Math.min(72, dp)));
        preferences.flush();
    }

    public float getBallOpacity() {
        int value = preferences == null
                ? 100
                : preferences.getInt(KEY_BALL_OPACITY, 100);
        return Math.max(30, Math.min(100, value)) / 100f;
    }

    public void setBallOpacity(int percent) {
        if (preferences == null) {
            return;
        }
        preferences.putInt(KEY_BALL_OPACITY, Math.max(30, Math.min(100, percent)));
        preferences.flush();
    }

    public boolean isGridLayout() {
        return preferences != null && "grid".equals(preferences.getString(KEY_LAYOUT, "list"));
    }

    public void setGridLayout(boolean grid) {
        if (preferences == null) {
            return;
        }
        preferences.putString(KEY_LAYOUT, grid ? "grid" : "list");
        preferences.flush();
    }

    public boolean isShowLabels() {
        return preferences == null || preferences.getBoolean(KEY_SHOW_LABELS, true);
    }

    public void setShowLabels(boolean show) {
        if (preferences == null) {
            return;
        }
        preferences.putBoolean(KEY_SHOW_LABELS, show);
        preferences.flush();
    }

    /** Restores the pre-upgrade behaviour: every action enabled, default order and appearance. */
    public void reset() {
        if (preferences == null) {
            return;
        }
        List<String> ids = NativeActionRegistry.get().getIds();
        preferences.putString(KEY_ORDER, QuickBallLayout.join(ids));
        preferences.putString(KEY_ENABLED, QuickBallLayout.join(ids));
        preferences.putInt(KEY_BALL_SIZE, DEFAULT_BALL_SIZE);
        preferences.putInt(KEY_BALL_OPACITY, 100);
        preferences.putString(KEY_LAYOUT, "list");
        preferences.putBoolean(KEY_SHOW_LABELS, true);
        for (String id : ids) {
            preferences.remove(KEY_LABEL_PREFIX + id);
            preferences.remove(KEY_ICON_PREFIX + id);
        }
        preferences.flush();
    }

    // ==================== internals ====================

    private List<String> storedOrder() {
        if (preferences == null) {
            return new ArrayList<>();
        }
        return QuickBallLayout.split(preferences.getString(KEY_ORDER, ""));
    }

    private Set<String> enabledIds(List<String> allIds) {
        if (preferences == null) {
            return new LinkedHashSet<>(allIds);
        }
        String stored = preferences.getString(KEY_ENABLED, "");
        if (stored == null || stored.isEmpty()) {
            return new LinkedHashSet<>(allIds);
        }
        return new LinkedHashSet<>(QuickBallLayout.split(stored));
    }

    /** Saved horizontal position of the floating button, or {@link Integer#MIN_VALUE}. */

    public int getBallX() {
        return preferences == null
                ? Integer.MIN_VALUE
                : (int) preferences.getLong(KEY_BALL_X, Integer.MIN_VALUE);
    }

    public void setBallX(int x) {
        if (preferences == null) return;

        preferences.putInt(KEY_BALL_X, x);
        preferences.flush();
    }

    /** Saved vertical position of the floating button, or Integer.MIN_VALUE. */
    public int getBallY() {
        return preferences == null
                ? Integer.MIN_VALUE
                : (int) preferences.getLong(KEY_BALL_Y, Integer.MIN_VALUE);
    }

    public void setBallY(int y) {
        if (preferences == null) return;

        preferences.putInt(KEY_BALL_Y, y);
        preferences.flush();
    }

    /** True when the button is parked half off-screen against a side edge. */
    public boolean isBallHidden() {
        return preferences != null
                && preferences.getBoolean(KEY_BALL_HIDDEN, false);
    }

    public void setBallHidden(boolean hidden) {
        if (preferences == null) return;

        preferences.putBoolean(KEY_BALL_HIDDEN, hidden);
        preferences.flush();
    }

}
