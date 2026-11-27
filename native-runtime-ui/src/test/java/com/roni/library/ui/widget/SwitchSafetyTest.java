package com.roni.library.ui.widget;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Regression tests for the {@code StaticLayout} null-text guard.
 *
 * <p>Only the pure decision function is covered here — {@link SwitchSafety#harden} needs a real
 * {@code SwitchCompat} and therefore an instrumented test. The decision function is the part
 * that actually determines whether the crash can happen, so it is the part worth pinning.</p>
 */
public class SwitchSafetyTest {

    @Test
    public void bothLabelsPresentKeepsShowTextEnabled() {
        assertFalse(SwitchSafety.needsShowTextDisabled("ON", "OFF"));
    }

    @Test
    public void nullTextOnDisablesShowText() {
        assertTrue(SwitchSafety.needsShowTextDisabled(null, "OFF"));
    }

    @Test
    public void nullTextOffDisablesShowText() {
        assertTrue(SwitchSafety.needsShowTextDisabled("ON", null));
    }

    @Test
    public void bothLabelsNullDisablesShowText() {
        assertTrue(SwitchSafety.needsShowTextDisabled(null, null));
    }

    @Test
    public void emptyLabelsAreSafe() {
        // An empty CharSequence is not null, so StaticLayout can measure it.
        assertFalse(SwitchSafety.needsShowTextDisabled("", ""));
    }

    @Test
    public void emptyOnWithNullOffIsStillUnsafe() {
        assertTrue(SwitchSafety.needsShowTextDisabled("", null));
    }
}
