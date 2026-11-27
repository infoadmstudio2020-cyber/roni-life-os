package com.roni.life.os.app.quickball;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure ordering helpers for the Quick Ball panel.
 *
 * <p>Kept free of Android types so the migration and reordering rules can be unit tested on the
 * JVM. {@link QuickBallPreferences} is the only caller that persists the result.</p>
 */
public final class QuickBallLayout {

    private QuickBallLayout() {
    }

    /**
     * Reconciles a stored order with the currently registered ids.
     *
     * <p>Unknown ids are dropped, missing ids are appended in registration order, and duplicates
     * collapse to their first position. This is what makes newly registered actions appear
     * without wiping the user's arrangement.</p>
     */
    public static List<String> sanitize(List<String> registeredIds, List<String> storedOrder) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (storedOrder != null) {
            for (String id : storedOrder) {
                if (id != null && registeredIds.contains(id)) {
                    result.add(id);
                }
            }
        }
        for (String id : registeredIds) {
            result.add(id);
        }
        return new ArrayList<>(result);
    }

    /** Moves an id by {@code delta} positions, clamped to the list bounds. */
    public static List<String> move(List<String> order, String id, int delta) {
        List<String> result = new ArrayList<>(order);
        int from = result.indexOf(id);
        if (from < 0 || delta == 0) {
            return result;
        }
        int to = Math.max(0, Math.min(result.size() - 1, from + delta));
        if (to == from) {
            return result;
        }
        result.remove(from);
        result.add(to, id);
        return result;
    }

    /** Keeps only enabled ids, preserving the stored order. */
    public static List<String> enabledOrdered(List<String> order, Set<String> enabled) {
        List<String> result = new ArrayList<>();
        for (String id : order) {
            if (enabled == null || enabled.contains(id)) {
                result.add(id);
            }
        }
        return result;
    }

    public static String join(List<String> ids) {
        StringBuilder builder = new StringBuilder();
        for (String id : ids) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    public static List<String> split(String csv) {
        List<String> result = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) {
            return result;
        }
        for (String part : csv.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }
}
