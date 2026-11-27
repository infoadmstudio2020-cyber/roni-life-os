package com.roni.library.devtools.inspector.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.roni.library.devtools.inspector.ui.InspectorSheetState.State;

import org.junit.Test;

/**
 * Verifies the three-state behaviour of the inspector sheet without a device.
 *
 * <p>Screen height is fixed at 2000px so the expected half/expanded values are exact.</p>
 */
public class InspectorSheetStateTest {

    private static final int SCREEN = 2000;
    private static final int COLLAPSED = 34;
    private static final int HALF = 900;
    private static final int EXPANDED = 1840;

    // ---------- collapsed geometry ----------

    @Test
    public void collapsedHeightStaysAt34dp() {
        assertEquals(34, InspectorSheetState.COLLAPSED_HEIGHT_DP);
    }

    @Test
    public void handleFitsInsideCollapsedPanel() {
        assertTrue("handle must not be clipped by the collapsed panel",
                InspectorSheetState.HANDLE_HEIGHT_DP < InspectorSheetState.COLLAPSED_HEIGHT_DP);
    }

    @Test
    public void collapsedPanelLeavesTransparentGapBelowHandle() {
        assertEquals(8, InspectorSheetState.collapsedGapPx(34, 26));
    }

    @Test
    public void collapsedGapNeverGoesNegative() {
        assertEquals(0, InspectorSheetState.collapsedGapPx(20, 26));
    }

    // ---------- heights ----------

    @Test
    public void collapsedHeightIsIndependentOfScreenSize() {
        assertEquals(COLLAPSED, InspectorSheetState.heightFor(State.COLLAPSED, SCREEN, COLLAPSED));
        assertEquals(COLLAPSED, InspectorSheetState.heightFor(State.COLLAPSED, 800, COLLAPSED));
    }

    @Test
    public void halfIs45PercentOfScreen() {
        assertEquals(900, InspectorSheetState.heightFor(State.HALF, SCREEN, COLLAPSED));
    }

    @Test
    public void expandedIs92PercentOfScreen() {
        assertEquals(1840, InspectorSheetState.heightFor(State.EXPANDED, SCREEN, COLLAPSED));
    }

    @Test
    public void nullStateFallsBackToCollapsed() {
        assertEquals(COLLAPSED, InspectorSheetState.heightFor(null, SCREEN, COLLAPSED));
    }

    // ---------- chrome visibility ----------

    @Test
    public void collapsedHidesAllChrome() {
        assertFalse(InspectorSheetState.showsChrome(State.COLLAPSED));
    }

    @Test
    public void halfAndExpandedShowChrome() {
        assertTrue(InspectorSheetState.showsChrome(State.HALF));
        assertTrue(InspectorSheetState.showsChrome(State.EXPANDED));
    }

    // ---------- touch handling ----------

    @Test
    public void collapsedDoesNotConsumeTouches() {
        assertFalse(InspectorSheetState.consumesTouches(State.COLLAPSED));
    }

    @Test
    public void openStatesConsumeTouches() {
        assertTrue(InspectorSheetState.consumesTouches(State.HALF));
        assertTrue(InspectorSheetState.consumesTouches(State.EXPANDED));
    }

    @Test
    public void touchConsumptionTracksChromeVisibility() {
        for (State state : State.values()) {
            assertEquals(state.name(),
                    InspectorSheetState.showsChrome(state),
                    InspectorSheetState.consumesTouches(state));
        }
    }

    // ---------- snapping ----------

    @Test
    public void snapsToExactRestingHeights() {
        assertEquals(State.COLLAPSED,
                InspectorSheetState.nearest(COLLAPSED, COLLAPSED, HALF, EXPANDED));
        assertEquals(State.HALF,
                InspectorSheetState.nearest(HALF, COLLAPSED, HALF, EXPANDED));
        assertEquals(State.EXPANDED,
                InspectorSheetState.nearest(EXPANDED, COLLAPSED, HALF, EXPANDED));
    }

    @Test
    public void snapsDownBelowCollapsed() {
        assertEquals(State.COLLAPSED,
                InspectorSheetState.nearest(0, COLLAPSED, HALF, EXPANDED));
    }

    @Test
    public void snapsUpAboveExpanded() {
        assertEquals(State.EXPANDED,
                InspectorSheetState.nearest(SCREEN, COLLAPSED, HALF, EXPANDED));
    }

    @Test
    public void tieBetweenCollapsedAndHalfResolvesDownward() {
        int midpoint = (COLLAPSED + HALF) / 2;
        assertEquals(State.COLLAPSED,
                InspectorSheetState.nearest(midpoint, COLLAPSED, HALF, EXPANDED));
    }

    @Test
    public void justAboveMidpointSnapsToHalf() {
        int midpoint = (COLLAPSED + HALF) / 2;
        assertEquals(State.HALF,
                InspectorSheetState.nearest(midpoint + 1, COLLAPSED, HALF, EXPANDED));
    }

    @Test
    public void justBelowHalfToExpandedMidpointStaysHalf() {
        int midpoint = (HALF + EXPANDED) / 2;
        assertEquals(State.HALF,
                InspectorSheetState.nearest(midpoint - 1, COLLAPSED, HALF, EXPANDED));
    }

    @Test
    public void justAboveHalfToExpandedMidpointSnapsToExpanded() {
        int midpoint = (HALF + EXPANDED) / 2;
        assertEquals(State.EXPANDED,
                InspectorSheetState.nearest(midpoint + 1, COLLAPSED, HALF, EXPANDED));
    }

    @Test
    public void everyDragHeightResolvesToAState() {
        for (int height = 0; height <= SCREEN; height += 7) {
            State state = InspectorSheetState.nearest(height, COLLAPSED, HALF, EXPANDED);
            assertTrue("height " + height + " produced " + state, state != null);
        }
    }
}
