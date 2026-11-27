package com.roni.library.devtools.inspector.ui;

/**
 * Pure state math for the inspector bottom sheet.
 *
 * <p>Kept free of Android imports so the three-state behaviour — heights, chrome visibility,
 * touch consumption and drag snapping — can be verified on the JVM instead of only on a
 * device. {@link RuntimeInspectorSheet} owns the views; this class owns the decisions.</p>
 */
public final class InspectorSheetState {

    /** The three resting positions of the sheet. */
    public enum State {
        COLLAPSED, HALF, EXPANDED
    }

    /** Collapsed panel height. Only the drag handle is visible inside it. */
    public static final int COLLAPSED_HEIGHT_DP = 34;

    /** Drag handle height. Must stay smaller than {@link #COLLAPSED_HEIGHT_DP}. */
    public static final int HANDLE_HEIGHT_DP = 26;

    private static final float HALF_RATIO = 0.45f;
    private static final float EXPANDED_RATIO = 0.92f;

    private InspectorSheetState() {
    }

    /** Panel height in pixels for {@code state}. */
    public static int heightFor(State state, int screenHeight, int collapsedHeight) {
        if (state == null) {
            return collapsedHeight;
        }
        switch (state) {
            case HALF:
                return (int) (screenHeight * HALF_RATIO);
            case EXPANDED:
                return (int) (screenHeight * EXPANDED_RATIO);
            case COLLAPSED:
            default:
                return collapsedHeight;
        }
    }

    /** True when the title bar, tabs, filters, list and footer are shown. */
    public static boolean showsChrome(State state) {
        return state != null && state != State.COLLAPSED;
    }

    /**
     * True when the sheet itself may intercept touches.
     *
     * <p>Collapsed, the panel must stay transparent to the page underneath: only the handle
     * consumes gestures, so the WebView keeps receiving taps and scrolls everywhere else.</p>
     */
    public static boolean consumesTouches(State state) {
        return showsChrome(state);
    }

    /**
     * Nearest resting state for a dragged height.
     *
     * <p>Ties resolve downward, so releasing exactly between two states settles on the
     * smaller one rather than jumping open.</p>
     */
    public static State nearest(int height, int collapsed, int half, int expanded) {
        State best = State.COLLAPSED;
        int bestDistance = Math.abs(height - collapsed);

        int distance = Math.abs(height - half);
        if (distance < bestDistance) {
            best = State.HALF;
            bestDistance = distance;
        }
        distance = Math.abs(height - expanded);
        if (distance < bestDistance) {
            best = State.EXPANDED;
        }
        return best;
    }

    /** Pixels left over below the handle when collapsed — painted transparent, not black. */
    public static int collapsedGapPx(int collapsedHeightPx, int handleHeightPx) {
        return Math.max(0, collapsedHeightPx - handleHeightPx);
    }
}
